package com.lovebrain.app.feature.intent

import com.lovebrain.app.domain.IntentPolicy
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 持续意图那一段**行为**的持有者：搬的是行为块，不是字段。
 *
 * 接管三样：屏上那份意图配置（唯一的两个写口是 [refreshForKb] 与 [save]）、编辑器
 * "开/关 + 开在哪块库上"，以及到期改写这条判据本身。原来这些摊在 ViewModel 里一个
 * 一百行的区块上，其中"编辑器绑哪块库"还藏在**一颗裸 `private var`** 里——它和可见性
 * 是一对，拆开放就会长成"卡关了、绑定还在"，下一次保存写进一块已经不显示的库，
 * 而任何行为测试都不会红（结构门禁原本数不到那一支，这一族坑写在
 * `ViewModelStateOwnershipTest` 里）。
 *
 * 三条判据是真的离开了 ViewModel，不是换了个前缀：
 * · 到期改写（[IntentPolicy.shouldAutoExpire] 命中时把盘上那条一起标成 EXPIRED 并关掉）；
 * · KB 身份守卫（读回来与保存之后都再核一次"当前激活库还是不是这块库"，晚到的结果不落屏）；
 * · 编辑器绑定**打开那一刻**的库，不读当前库。
 *
 * 一处**故意没修**的已知缺口，写在这里而不是留着让人猜：到期检测只挂在"切库"那一条链上
 * （由 ViewModel 的 `refreshKnowledgeBases` await [refreshForKb]）。以前有个
 * `refreshIntentConfig()` 声称"面板可见时也刷"，但它全仓零调用者，已随死代码删掉。
 * ⇒ 面板一直开着跨午夜，那条 ONE_DAY 意图要等到下一次切库才会被标成 EXPIRED。
 * 要不要把刷新一路接回面板可见处，是单独的功能决定，不属于这次搬家。
 *
 * 读写仓库一律靠注入：`feature` 不许 import `data`（包边界由 `PackageDependencyTest`
 * 在看着），所以本类不知道背后是哪块仓库。两条写盘路径的线程形状也与搬之前一致：
 * 仓库那侧自己 `withContext(Dispatchers.IO)`，本类不再套一层。
 */
class IntentController(
    private val scope: CoroutineScope,
    /** 当前激活库的名字：KB 身份守卫与"编辑器开在哪块库"都读这一个值 */
    private val readActiveKbName: () -> String?,
    /** 读回某块库的意图配置 */
    private val readIntent: suspend (kbName: String) -> IntentConfig,
    /** 落盘一条意图，返回仓库写后的新配置（revision 已在仓库那一侧 +1） */
    private val writeIntent: suspend
        (kbName: String, text: String, enabled: Boolean, expiry: IntentExpiry, expiryDate: String, status: IntentStatus) -> IntentConfig,
    /** 屏上确认保存成功之后要做的事（"改盘要把当前结果标 stale"那条判据在生成账那一侧） */
    private val onSaved: () -> Unit = {},
    private val onWarning: (String) -> Unit = {},
    private val onNotice: (String) -> Unit = {},
    /** 读写失败的日志：只交出一句话，异常类型由调用方决定怎么落 */
    private val onLog: (String) -> Unit = {},
    /** "现在"只取一次：填到期时刻与判过期用同一个时钟，跨小时/跨午夜那一下会给出同一个 now */
    private val readNow: () -> String = { com.lovebrain.app.util.TimeFmt.now() }
) {

    private val _config = MutableStateFlow(IntentConfig())

    /** 面板那颗 chip 与编辑器读的是同一份；除本类之外没有写入口 */
    val config: StateFlow<IntentConfig> = _config.asStateFlow()

    private val _showEditor = MutableStateFlow(false)

    /** 意图编辑面板的可见性 */
    val showEditor: StateFlow<Boolean> = _showEditor.asStateFlow()

    /**
     * 编辑器绑在**打开那一刻**的库上，不是当前库：切库之后再把内容写进新库就是写错人。
     * 关闭编辑器时这颗绑定**一起清掉**（见 [dismissEditor]）——存活的绑定只有"编辑器开着"
     * 这一种状态；下一次打开会在 [openEditor] 重新冻结。
     */
    private var editorKbName: String? = null

    /** 生成指纹只要这一个读数：意图 revision 一变，的旧结果就该标 stale */
    fun currentRevision(): Int = _config.value.revision

    /** 切库复位：上一块库的意图不许泄漏到新库，屏上先空着，等新库那份被读回来 */
    fun resetForKbSwitch() {
        _config.value = IntentConfig()
    }

    /**
     * 绑定库名刷新——suspend 函数，由调用方在结构化协程里 await（不再 fire-and-forget，
     * 那两个 sibling 协程交错执行正是切库串库的来源）。
     *
     * 读失败只是不动屏上内容，不崩 scope；取消按原样重抛（这一支不许被下面的 `Exception` 吞掉）。
     */
    suspend fun refreshForKb(kbName: String) {
        val current = try {
            readIntent(kbName)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            onLog("refreshIntentForKb read failed: ${e::class.simpleName}")
            return
        }
        // 自动到期检测——判定规则在 IntentPolicy（可离线单测）；改写失败不改变屏上那份判定
        val finalConfig = if (IntentPolicy.shouldAutoExpire(current, readNow())) {
            val expired = current.copy(status = IntentStatus.EXPIRED)
            try {
                // 到期同时把这条关掉（enabled=false），屏上那份仍用本地 copy 的 revision——两条都是搬之前的原样
                writeIntent(kbName, current.text, false, current.expiry, current.expiryDate, IntentStatus.EXPIRED)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                onLog("refreshIntentForKb expire save failed: ${e::class.simpleName}")
            }
            expired
        } else {
            current
        }
        // KB 身份守卫：只有当前激活库仍是这块库时，这一次读的结果才落到屏上
        if (readActiveKbName() == kbName) {
            _config.value = finalConfig
        }
    }

    /**
     * 保存这条意图。
     *
     * 绑的是编辑器打开那一刻的库（没有绑定才退回当前库，与搬之前同一个 `?:` 顺序）；
     * 校验不过就地拒绝、编辑状态原样留着；落盘成功后还要再过一次身份守卫才改屏、
     * 才关编辑器、才把当前结果标 stale——守卫不过时这些**一个都不做**，晚到的那次保存
     * 只改盘不改脸。
     *
     * 到期时刻与完成状态由 [IntentPolicy] 算：时间档（ONE_HOUR/ONE_DAY/ONE_WEEK）用
     * 保存时刻 + 对应时长；COMPLETED 档把 status 改成 COMPLETED。UI 不再填日期。
     *
     * "已开启/已关闭"通知只在 enabled 真的跳变时发一次——文本/有效期编辑不该每按一键就弹一条。
     *
     * **只改正文这一支要有成功回执**（request2 §四「意图保存反馈」）：设置页那颗「保存」与
     * 失焦落盘走的都是这条链，以前落盘成功屏上一个字都不出，用户感觉不到"存住了"。
     * 现在写盘真返回、且过了 KB 身份守卫之后，经同一颗 [onNotice]（宿主接到
     * `NoticeBoard.Channel.Knowledge` = 成功形态短句、3 秒自动消失，**不是**黄色警告那一格）
     * 发一句「已记录」——文案复用盘上既有资源 `R.string.notice_recorded`（本类没有 Context，
     * 与 VM 里 `reportDislikeCaseSave` 同一先例：同形字面量，不新开 key）。
     * 失败那一支不变：仍只走 [onWarning] 的「意图保存失败，内容已保留，请重试」，
     * 成功回执**绝不早于** `writeIntent` 真返回——点下去就先说成功是这一格禁止的形状。
     * 换档/完成/拨开关都不是这一支：期限档变了或 enabled 跳变了，就不会重复弹这句「已记录」。
     */
    fun save(
        text: String,
        enabled: Boolean,
        expiry: IntentExpiry = IntentExpiry.ONE_DAY,
        expiryDate: String = "",
        status: IntentStatus = IntentStatus.ACTIVE
    ) {
        val kbName = editorKbName ?: readActiveKbName() ?: return
        val now = readNow()
        val effectiveExpiryDate = IntentPolicy.effectiveExpiryDate(expiry, expiryDate, now)
        val effectiveStatus = IntentPolicy.effectiveStatus(expiry, status)
        IntentPolicy.validateSave(expiry, effectiveExpiryDate, effectiveStatus, now)?.let { reason ->
            onWarning(reason)
            return
        }
        val prevEnabled = _config.value.enabled
        // 「只改正文」这一支的判据：enabled／有效期档／状态三者都没动，动的只有正文。
        // 换档与「完成」改 expiry/status、开关改 enabled，都不算这一支，不会多弹一句「已记录」。
        val bodyOnlySave = enabled == prevEnabled &&
            expiry == _config.value.expiry && effectiveStatus == _config.value.status
        scope.launch {
            try {
                val updated = writeIntent(kbName, text, enabled, expiry, effectiveExpiryDate, effectiveStatus)
                if (readActiveKbName() == kbName) {
                    _config.value = updated
                    if (enabled != prevEnabled) {
                        onNotice(if (enabled) "持续意图已开启" else "持续意图已关闭")
                    } else if (bodyOnlySave) {
                        // 回执只在 writeIntent 真返回之后发；文案 = `R.string.notice_recorded` 的同形字面量
                        onNotice(INTENT_BODY_SAVED_NOTICE)
                    }
                    _showEditor.value = false
                    onSaved()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 持久化失败保留编辑状态，只给一句可以重试的话；不发任何成功回执
                onWarning("意图保存失败，内容已保留，请重试")
            }
        }
    }

    /** 打开编辑器：先冻结"开在哪块库"，再翻可见性——顺序反了会绑到切库之后的那本库 */
    fun openEditor() {
        editorKbName = readActiveKbName()
        _showEditor.value = true
    }

    /** 关掉编辑器：可见性与那份库绑定**一起收**。绑定只在 [openEditor] 那一刻有意义，
     *  留着它，下次 save()（退出动画里那颗保存键、或任何漏接线的调用方）会把草稿写进
     *  用户已经离开的那本库——绑定清空后 save() 退回 [readActiveKbName]，目标回到"此刻真的开着的那本"。 */
    fun dismissEditor() {
        _showEditor.value = false
        editorKbName = null
    }

    companion object {
        /**
         * 「只改正文」落盘成功那一句回执的文案：与盘上资源 `R.string.notice_recorded`（「已记录」）
         * 逐字同形——本类没有 Context，沿用 VM `reportDislikeCaseSave` 的先例（同形字面量、不新开 key、
         * 不写进 strings.xml）。改资源那句的人要同步这里，两边各改一半由测试红。
         */
        internal const val INTENT_BODY_SAVED_NOTICE = "已记录"
    }
}
