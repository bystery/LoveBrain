package com.lovebrain.app.service

import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * CAP1（2026-10-06）：捕获服务"静默"三格的读数账（A1 取证 source-10 候选①③④）。
 *
 * 修的是可读性，**不是采集面**——所以这一族钉两件事：
 *
 * 1. 每条被闸住/被拒的路径都必须在进程内留一颗可核对的读数（与 capture_diag.log 同一时刻
 *    的另一本账），拒绝原因只有标签、没有任何正文；
 * 2. 被拒路径**不产生任何入库内容**：`EventBus.emitCapturedMessage` 在整个服务里只有
 *    一个调用点，钉死它只能住在"菜单确认 ∧ 悬浮窗在"的那一条 else 分支里；
 *    悬浮窗不在的分支必须**只记账不投递**（候选③：当场不记 + 状态说原因，不暂存、不补录、
 *    不自动拉起——回退成"顺手 emit 一下"或"暂存待补"都会红在这里）。
 *
 * 第 2 组用源码形状判（与仓库既有的架构尺同一手法：`SourceScan.maskComments` 剥注释，
 * 行号/偏移可信）；行为半段用 companion 读数直接拨。
 *
 * 逐格"回退成什么会红"：
 * - Deny 分支不 call `recordCaptureReject` → 格 2 红；
 * - `emitCapturedMessage` 多出一个调用点（比如给 serviceDown 分支加暂存重发）→ 格 3 红；
 * - serviceDown 分支里出现 emit（＝"悬浮窗没起也先把内容投出去"）→ 格 4 红；
 * - 把 SystemClock 默认参数换成读正文再记（reason 里带内容）——本表钉不了文案，钉的是
 *   读数只可能是标签：见格 1 用显式 reason 断言的两颗字段。
 */
@RunWith(RobolectricTestRunner::class)
// 本族只拨 companion 读数 + 读源码文件，不碰依赖容器；Robolectric 要走不 startKoin 的空壳 App。
// 清单默认那颗 App 在 onCreate 里无条件 startKoin，而 Koin 的 GlobalContext 是 JVM 静态的——
// 同沙箱第二个用例再建一次 Application 就炸在装配阶段（栈顶早于本类任何 @Before，用例里拦不住）。
// 读数账、形状判一颗没改：换的只是 Application，不是判据。
@Config(application = UiProbeApplication::class)
class CopyCaptureRejectLedgerTest {

    // ═══════════ 1. 读数本体：纯 companion，可摆 ═══════════

    @Test
    fun `reject reading stores only the reason label and the timestamp`() {
        CopyCaptureService.recordCaptureReject("allowlist_empty", uptimeMs = 42L)
        assertEquals("allowlist_empty", CopyCaptureService.lastRejectReason)
        assertEquals(42L, CopyCaptureService.lastRejectUptimeMs)

        CopyCaptureService.recordCaptureReject("consent_pending", uptimeMs = 43L)
        assertEquals("consent_pending", CopyCaptureService.lastRejectReason)
        assertEquals(43L, CopyCaptureService.lastRejectUptimeMs)
    }

    @Test
    fun `service-down drops are counted, never silently lost`() {
        val before = CopyCaptureService.serviceDownDropCount
        CopyCaptureService.recordServiceDownDrop(uptimeMs = 7L)
        CopyCaptureService.recordServiceDownDrop(uptimeMs = 9L)
        assertEquals("当场不记必须逐条计数（候选③的可见代价账）", before + 2, CopyCaptureService.serviceDownDropCount)
        assertEquals(9L, CopyCaptureService.lastDropUptimeMs)
    }

    // ═══════════ 2-4. 源码形状：拒绝留账 + 入库唯一出口钉在确认分支 ═══════════

    private fun serviceSourceMasked(): String {
        val f = File("src/main/java/com/lovebrain/app/service/CopyCaptureService.kt")
        assertTrue("尺接错了文件，找不到 $f —— 宁可红也不许空转", f.isFile)
        return SourceScan.maskComments(f.readText(Charsets.UTF_8))
    }

    @Test
    fun `every silent gate now records a readable reject`() {
        val src = serviceSourceMasked()
        // 三闸（allowlist 拒绝 / 开关 / 披露）都必须在各自分支里 call recordCaptureReject——
        // 尺按"分支头到下一条分支/返回"的切片判，防止 call 被挪到公共段冒充三格都记了。
        val denyBranch = sliceAfter(src, "if (decision is CapturePolicy.Decision.Deny) {")
        assertTrue("allowlist 拒绝不留读数：$denyBranch", denyBranch.contains("recordCaptureReject("))
        val switchBranch = sliceAfter(src, "if (!capEnabled) {")
        assertTrue("开关闸口不留读数：$switchBranch", switchBranch.contains("recordCaptureReject("))
        val consentBranch = sliceAfter(src, "if (consentVersion < CURRENT_DISCLOSURE_VERSION) {")
        assertTrue("披露闸口不留读数：$consentBranch", consentBranch.contains("recordCaptureReject("))
    }

    /**
     * 候选③的形状账：
     * - `EventBus.emitCapturedMessage(` 全文件恰好 1 个调用点；
     * - 它必须落在悬浮窗在跑的那条 else 分支里，`instance == null` 分支**只许**
     *   计数 + diag + 清 pending。
     */
    @Test
    fun `rejected and dropped paths hold no delivery to the knowledge chain`() {
        val src = serviceSourceMasked()
        val emit = "EventBus.emitCapturedMessage("
        assertEquals("入库内容唯一的出口必须恰好一个调用点", 1, Regex(Regex.escape(emit)).findAll(src).count())

        val downHead = "if (FloatingService.instance == null) {"
        val at = src.indexOf(downHead)
        assertTrue("尺读不到候选③那一格（分支形状被改了要连这本账一起改）", at >= 0)
        val downBranch = src.substring(at, src.indexOf("} else {", at))
        assertFalse("悬浮窗不在时不许投递任何东西：$downBranch", downBranch.contains(emit))
        assertTrue("悬浮窗不在必须记一笔可核对的读数", downBranch.contains("recordServiceDownDrop("))
        assertTrue("diag 那一本账保持不动（真机分诊第一读数源）", downBranch.contains("serviceDown|dropped"))
        assertNotNull("当场不记之后仍要清 pending（旧话自己冒出来的那味药不能倒掉）",
            downBranch.indexOf("clearPending(").takeIf { it >= 0 })
    }

    /**
     * 开关那一道闸的**兜底值**必须是关（2026-10-06 CAP3/CAP4：用户报"第一次进来开关就是开的"，
     * 根因是 `SecurePrefs.captureEnabled` 的默认 true；同一颗键在最后一道闸上的兜底若留 `?: true`，
     * 等于把那份"默认开"又埋深一层——存储读不到时（Keystore 降级、首帧、异常）系统会当成"用户要捕"）。
     *
     * 2026-10-08 Q39①：偏好实例从容器注入（非空 `by inject()`），服务不再自造可空的第二实例，
     * 旧的本地兜底 `?: false` 失去存在理由。**判据没有放松，是搬了家**——
     * "没写过就是关"的默认由 SecurePrefs 的 fail-closed getter 持有（数据侧证人：
     * `SettingsStorePortContractTest` 空存储读到 false、显式写原样读回）。本格改钉两件：
     * - 正向：开关那一行**直连非空真源**（`securePrefs.captureEnabled`），不许再长出 `?.` 逃生口
     *   或任何本地 elvis 改写；
     * - 反向：整个服务源码（剥注释后）**不许出现任何** `?: true`——
     *   这条不是装饰：注释里写"以前是 true"不会被数到（`maskComments` 先剥），
     *   有人把别的布尔兜底写成 `?: true` 当场红，逼他明说这一颗为什么默认开。
     */
    @Test
    fun `the switch gate fails closed when the store cannot be read`() {
        val src = serviceSourceMasked()
        val at = src.indexOf("val capEnabled = securePrefs.captureEnabled")
        assertTrue("尺读不到开关那一道闸的读数行（换了写法要连这本账一起改）", at >= 0)
        val line = src.substring(at, src.indexOf('\n', at))
        assertTrue("开关读数行不许再带可空逃生口（?.）: $line", !line.contains("?."))
        assertTrue("开关读数行不许有本地 elvis 兜底改写: $line", !line.contains("?:"))
        assertEquals("服务里不许再有第二处 `?: true` 兜底（每一颗默认开的布尔都要单独认账）：",
            0, Regex("""\?\s*:\s*true""").findAll(src).count())
    }

    /** 从分支头往下取一片（到下一个顶层 `}` 收口前）；只用于形状判，不装精确解析 */
    private fun sliceAfter(src: String, header: String): String {
        val at = src.indexOf(header)
        assertTrue("尺读不到分支头「$header」", at >= 0)
        val from = at + header.length
        // 找这一段里第一个后面还跟着非空白内容的单独一行 "        }"（分支收口）
        val closeIdx = Regex("\n\\s{8}\\}").find(src.substring(from))
        return src.substring(from, from + (closeIdx?.range?.first ?: 600))
    }
}
