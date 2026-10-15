package com.lovebrain.app.feature.intent

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
 * "开/关 + 开在哪块库上"，以及"这条算不算开着"那一条判据。原来这些摊在 ViewModel 里一个
 * 一百行的区块上，其中"编辑器绑哪块库"还藏在**一颗裸 `private var`** 里——它和可见性
 * 是一对，拆开放就会长成"卡关了、绑定还在"，下一次保存写进一块已经不显示的库，
 * 而任何行为测试都不会红（结构门禁原本数不到那一支，这一族坑写在
 * `ViewModelStateOwnershipTest` 里）。
 *
 * 判据只有一条：**开 且 正文非空**（`model/Models.kt` 的 `isIntentEffective`）。
 * 期限、"已完成"、"已到期"、"已暂停"都退役了——开启之后不会有任何东西在背后把它关掉。
 *
 * 三条判据是真的离开了 ViewModel，不是换了个前缀：
 * · KB 身份守卫（读回来与保存之后都再核一次"当前激活库还是不是这块库"，晚到的结果不落屏）；
 * · 编辑器绑定**打开那一刻**的库，不读当前库；
 * · 写侧统一（每次落盘都是"活动态 + 不带期限"，见 [save]），读侧宽容（老文件带着旧字段
 *   也照样按开关说话，不迁移、不改写盘上的文件）。
 *
 * 一处**故意没修**的已知缺口，写在这里而不是留着让人猜：切库时的读取只挂在
 * `refreshKnowledgeBases` 那一条链上（由 ViewModel await [refreshForKb]）。这一族以前还会
 * 在读取时把到期的意图标成 EXPIRED 并关掉；判据退役后那条改写整块消失，
 * 于是"面板一直开着跨午夜，意图被背后悄悄关掉"这一类形状不再有来源。
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
    /**
     * 落盘一条意图，返回仓库写后的新配置（revision 已在仓库那一侧 +1）。
     *
     * 后三个形参（有效期档 / 到期时刻 / 状态）是**仓库端口那一侧的形状**，本类不再对它们
     * 做任何判定：每一次落盘都交"无期限"那一格（[IntentExpiry] 的默认档 + 空时刻 +
     * [IntentStatus.ACTIVE]），于是旧的期限语义不会从新数据里长回来。
     */
    private val writeIntent: suspend
        (kbName: String, text: String, enabled: Boolean, expiry: IntentExpiry, expiryDate: String, status: IntentStatus) -> IntentConfig,
    /** 屏上确认保存成功之后要做的事（"改盘要把当前结果标 stale"那条判据在生成账那一侧） */
    private val onSaved: () -> Unit = {},
    private val onWarning: (String) -> Unit = {},
    private val onNotice: (String) -> Unit = {},
    /** 读写失败的日志：只交出一句话，异常类型由调用方决定怎么落 */
    private val onLog: (String) -> Unit = {},
    /**
     * 「保存成功」那一句回执的文案：由宿主从资源 `intent_saved_notice` 交进来
     * （本类没有 Context，也拿不到资源表）。宿主没接线时退成 [INTENT_SAVED_NOTICE]，
     * 与盘上资源逐字同形——两边各改一半由测试红。
     */
    private val savedNoticeText: () -> String = { INTENT_SAVED_NOTICE },
    /** 「保存失败」那一句的文案：同上一颗，交自资源 `intent_save_failed_notice` */
    private val saveFailedNoticeText: () -> String = { INTENT_SAVE_FAILED_NOTICE }
) {

    private val _config = MutableStateFlow(IntentConfig())

    /** 面板那颗 chip 与编辑器读的是同一份；除本类之外没有写入口 */
    val config: StateFlow<IntentConfig> = _config.asStateFlow()

    private val _showEditor = MutableStateFlow(false)

    /** 意图编辑面板的可见性 */
    val showEditor: StateFlow<Boolean> = _showEditor.asStateFlow()

    private val _isSaving = MutableStateFlow(false)

    /**
     * 这一次写盘还在飞吗：为真时 [save] 就地拒绝，不再开第二条协程写同一份草稿。
     * 屏上那两颗保存键读这一颗把自己**禁用**（禁用而不是"再点一次再开一条"），
     * 宿主没接这一颗时 [save] 里那一道 `saveInFlight` 门仍然挡得住重复写盘。
     */
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _savedReceipts = MutableStateFlow(0)

    /**
     * 「已保存」这一句成功回执的**纪元票根**：正文真正落盘成功一次就跳一号（失败、以及
     * 只是开关跳变那两种都不跳——那两种发的是另外的话）。
     *
     * 为什么交一个"跳了几次"而不是交一个布尔：布尔要有人负责把它翻回 false，
     * 于是屏上会长出第二本"这次到底成没成"的账（谁翻、什么时候翻、翻不动怎么办），
     * 而这一格真正要的是"刚刚又成功了一笔"这一件事本身。读数单调向前，
     * 看它变了一格就是一次成功，不需要谁去复位它。
     *
     * 屏上那颗按钮拿它做"保存成功后自己亮一下"的即时反馈（与统一通知通道那句「已保存」
     * 同源同拍：两句都在 `writeIntent` 真返回并且过了身份守卫之后才发）。
     */
    val savedReceipts: StateFlow<Int> = _savedReceipts.asStateFlow()

    /**
     * 编辑器绑在**打开那一刻**的库上，不是当前库：切库之后再把内容写进新库就是写错人。
     * 关闭编辑器时这颗绑定**一起清掉**（见 [dismissEditor]）——存活的绑定只有"编辑器开着"
     * 这一种状态；下一次打开会在 [openEditor] 重新冻结。
     */
    private var editorKbName: String? = null

    /** [isSaving] 的同步镜像：`save()` 不是 suspend，得能在翻协程之前当场问住 */
    private var saveInFlight = false

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
     * 读回来的那份**原样落屏**：不再按到期时刻改写状态，也不再替用户关掉任何东西。
     * 老文件带着 EXPIRED / COMPLETED / PAUSED 也照读——那些字段已经不是判据（判据在
     * `model/Models.kt` 的 `isIntentEffective`），把它们当场洗掉等于改写用户写过的东西。
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
        // KB 身份守卫：只有当前激活库仍是这块库时，这一次读的结果才落到屏上
        if (readActiveKbName() == kbName) {
            _config.value = current
        }
    }

    /**
     * 保存这条意图。
     *
     * 绑的是编辑器打开那一刻的库（没有绑定才退回当前库，与搬之前同一个 `?:` 顺序）；
     * 落盘成功后还要再过一次身份守卫才改屏、才关编辑器、才把当前结果标 stale——守卫不过时
     * 这些**一个都不做**，晚到的那次保存只改盘不改脸。
     *
     * 写侧统一成"活动态 + 无期限"：`status = ACTIVE`、`expiryDate = ""`。后面三个形参是
     * 旧调用方的形状，保留只为让还没接线的宿主照样编译，本类一个字都不读它们
     * （宿主改完两条保存路径后应当从签名上摘掉）。
     *
     * 回执：
     * · enabled 真的跳变 ⇒ 「持续意图已开启」/「持续意图已关闭」；
     * · 没跳变（改正文、空文本再保存都算）⇒ [savedNoticeText] 那一句「已保存」。
     * 两句都**只在 `writeIntent` 真返回并且过了身份守卫之后**才发——点下去就先说成功是
     * 这一格禁止的形状。失败只发 [saveFailedNoticeText]（「保存失败，请重试」），
     * 编辑状态原样留着，不发任何成功回执。
     *
     * 重复提交：[saveInFlight] 为真时这一颗直接返回，不再开一条协程写同一份草稿。
     */
    fun save(
        text: String,
        enabled: Boolean
    ) {
        if (saveInFlight) return
        val kbName = editorKbName ?: readActiveKbName() ?: return
        val prevEnabled = _config.value.enabled
        saveInFlight = true
        _isSaving.value = true
        scope.launch {
            try {
                val updated = writeIntent(kbName, text, enabled, IntentExpiry.ONE_DAY, "", IntentStatus.ACTIVE)
                if (readActiveKbName() == kbName) {
                    _config.value = updated
                    if (enabled != prevEnabled) {
                        onNotice(if (enabled) INTENT_ENABLED_NOTICE else INTENT_DISABLED_NOTICE)
                    } else {
                        onNotice(savedNoticeText())
                        // 同一拍里给屏上那颗按钮一张纪元票根：正文真的落盘了，按钮才亮
                        _savedReceipts.value = _savedReceipts.value + 1
                    }
                    _showEditor.value = false
                    onSaved()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 持久化失败保留编辑状态，只给一句可以重试的话；不发任何成功回执
                onWarning(saveFailedNoticeText())
            } finally {
                saveInFlight = false
                _isSaving.value = false
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
         * 保存成功的回执文案：与盘上资源 `R.string.intent_saved_notice`（「已保存」）逐字同形。
         * 本类没有 Context，正常路径由宿主把资源交进来（[savedNoticeText]），这一颗只是没接线时的
         * 兜底；两边各改一半由测试红。
         */
        internal const val INTENT_SAVED_NOTICE = "已保存"

        /** 保存失败的回执文案：与资源 `R.string.intent_save_failed_notice` 逐字同形 */
        internal const val INTENT_SAVE_FAILED_NOTICE = "保存失败，请重试"

        /** 开关跳变那一句回执（沿用既有文案，正文编辑不再蹭这一句） */
        internal const val INTENT_ENABLED_NOTICE = "持续意图已开启"
        internal const val INTENT_DISABLED_NOTICE = "持续意图已关闭"
    }
}
