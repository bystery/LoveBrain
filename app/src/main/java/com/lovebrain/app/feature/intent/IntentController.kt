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
 * ⇒ 面板一直开着跨午夜，那条 TODAY 意图要等到下一次切库才会被标成 EXPIRED。
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
    /** "今天"只取一次：填日期与判过期用两个时钟，跨午夜那一下会给出两个不同的今天 */
    private val readToday: () -> String = { com.lovebrain.app.util.TimeFmt.today() }
) {

    private val _config = MutableStateFlow(IntentConfig())

    /** 面板那颗 chip 与编辑器读的是同一份；除本类之外没有写入口 */
    val config: StateFlow<IntentConfig> = _config.asStateFlow()

    private val _showEditor = MutableStateFlow(false)

    /** 意图编辑面板的可见性 */
    val showEditor: StateFlow<Boolean> = _showEditor.asStateFlow()

    /**
     * 编辑器绑在**打开那一刻**的库上，不是当前库：切库之后再把内容写进新库就是写错人。
     * 关闭编辑器时这颗绑定不清掉（与搬之前一致）——下一次打开会重新覆盖它。
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
        val finalConfig = if (IntentPolicy.shouldAutoExpire(current, readToday())) {
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
     */
    fun save(
        text: String,
        enabled: Boolean,
        expiry: IntentExpiry = IntentExpiry.UNTIL_DONE,
        expiryDate: String = "",
        status: IntentStatus = IntentStatus.ACTIVE
    ) {
        val kbName = editorKbName ?: readActiveKbName() ?: return
        // TODAY 一律自动写成当天，不依赖 UI 填；同一次保存里所有日期判定共用这一个今天
        val today = readToday()
        val effectiveExpiryDate = IntentPolicy.effectiveExpiryDate(expiry, expiryDate, today)
        // DATE 类型严格校验——空 / 格式不对 / 过去日期配 ACTIVE 都当场拒绝
        IntentPolicy.validateSave(expiry, effectiveExpiryDate, status, today)?.let { reason ->
            onWarning(reason)
            return
        }
        scope.launch {
            try {
                val updated = writeIntent(kbName, text, enabled, expiry, effectiveExpiryDate, status)
                if (readActiveKbName() == kbName) {
                    _config.value = updated
                    onNotice(if (enabled) "持续意图已开启" else "持续意图已关闭")
                    _showEditor.value = false
                    onSaved()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 持久化失败保留编辑状态，只给一句可以重试的话
                onWarning("意图保存失败，内容已保留，请重试")
            }
        }
    }

    /** 打开编辑器：先冻结"开在哪块库"，再翻可见性——顺序反了会绑到切库之后的那本库 */
    fun openEditor() {
        editorKbName = readActiveKbName()
        _showEditor.value = true
    }

    /** 关掉编辑器：只收可见性，不动那份库绑定（见 [editorKbName]，与搬之前一致） */
    fun dismissEditor() {
        _showEditor.value = false
    }
}
