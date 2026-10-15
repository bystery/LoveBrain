package com.lovebrain.app.feature.intent

import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「保存意图」那一句回执的合同：**只在真正写盘成功之后发，失败绝不谎报成功**。
 *
 * 判据形状与 `feature/feedback/DislikeCaseSaveTest`、`viewmodel/ResultCardActionsOutcomeTest`
 * 同一族：
 * · 写盘真返回 ⇒ 恰好一条成功回执「已保存」（走 [onNotice] = 成功形态短句那一格，
 *   **不借** [onWarning] 的黄色警告那一格）；
 * · 写盘抛异常 ⇒ 只有那一句「保存失败，请重试」，编辑器不关、草稿留着，不发任何成功回执；
 * · 点下去就先说成功（回执早于 writeIntent 返回）——[events] 的到达顺序格会红；
 * · 屏幕已经换库（身份守卫没过）⇒ 晚到的那次落盘一个字都不报。
 *
 * 替代关系（旧断言 → 新断言，一条都不许悄悄蒸发）：
 * · 「只改正文」才发「已记录」→ 期限退役后**凡是没让开关跳变的保存都算这一支**，文案换成
 *   资源 `intent_saved_notice`（「已保存」）；「换档/完成不发回执」那几格随档位一起退，
 *   换成这里的「开关跳变只发开/关那一句、不蹭已保存」。
 * · 「文案与 `notice_recorded` 同形、不新开 key」→ 换成**「文案由资源交进来」**：
 *   本类没有 Context，宿主把 `intent_saved_notice` / `intent_save_failed_notice` 的值传进来，
 *   没接线时的兜底常量必须与资源逐字同形（两边各改一半由这两格红）。
 *
 * 另外钉住重复提交那一道门：一次 `save` 只开一条写协程，正在飞的时候第二句直接顶回去。
 */
class IntentSaveReceiptTest {

    private fun onConfig(
        text: String = "周末约她",
        enabled: Boolean = true
    ) = IntentConfig(text = text, enabled = enabled)

    /**
     * 一台可记录回执的控制器。[events] 按到达顺序记 `write` / `notice` / `warning`，
     * 于是"先落盘还是先报喜"不是靠读代码自证，是断言出来的。
     *
     * [gate] 非 null 时假仓库**挂在那颗门等待**（测"正在保存"那一道门要用）。
     */
    private class Harness(
        initial: IntentConfig,
        val failWrite: Boolean = false,
        /** 写成功后屏幕上换库（KB 身份守卫晚到判 false） */
        val kbSwitchedAway: Boolean = false,
        val gate: CompletableDeferred<Unit>? = null,
        val savedNoticeText: () -> String = { IntentController.INTENT_SAVED_NOTICE },
        val saveFailedNoticeText: () -> String = { IntentController.INTENT_SAVE_FAILED_NOTICE }
    ) {
        val events = mutableListOf<String>()
        val notices = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        /** 仓库**收到**的正文（在挂起点之前记，于是"第二句有没有落到仓库"是量出来的） */
        val texts = mutableListOf<String>()
        var savedCalls = 0
        var writeCalls = 0
        var stored = initial
        var lastWriteText: String? = null
        var lastWriteEnabled: Boolean? = null
        var lastWriteExpiryDate: String? = null
        var lastWriteStatus: IntentStatus? = null
        var activeKb = "小美"
        val controller: IntentController = run {
            lateinit var c: IntentController
            c = IntentController(
                scope = CoroutineScope(Dispatchers.Unconfined),
                readActiveKbName = { activeKb },
                readIntent = { stored },
                writeIntent = { _, text, enabled, expiry, expiryDate, status ->
                    writeCalls++
                    events += "write"
                    texts += text
                    gate?.await()
                    if (failWrite) throw IllegalStateException("disk on fire")
                    lastWriteText = text
                    lastWriteEnabled = enabled
                    lastWriteExpiryDate = expiryDate
                    lastWriteStatus = status
                    stored = IntentConfig(
                        text = text,
                        enabled = enabled,
                        revision = stored.revision + 1,
                        expiry = expiry,
                        expiryDate = expiryDate,
                        status = status
                    )
                    if (kbSwitchedAway) activeKb = "另一块库"
                    stored
                },
                onSaved = { savedCalls++; events += "saved" },
                onWarning = { warnings += it; events += "warning" },
                onNotice = { notices += it; events += "notice" },
                savedNoticeText = savedNoticeText,
                saveFailedNoticeText = saveFailedNoticeText
            )
            runBlocking { c.refreshForKb("小美") }
            c
        }
    }

    /** 设置页与编辑浮层那两颗「保存」现在的形状：只有正文与开关 */
    private fun Harness.saveBody(text: String = "周末约她吃饭", enabled: Boolean = true) {
        controller.save(text, enabled)
    }

    // ─── ① 保存成功 ⇒ 恰好一条「已保存」，不弹原因 ────────────────────

    @Test
    fun `saving emits exactly one success receipt after the write returns`() {
        val h = Harness(onConfig())
        h.saveBody()

        assertEquals("成功回执恰好一条", listOf(IntentController.INTENT_SAVED_NOTICE), h.notices)
        assertEquals("成功这一支不许弹任何原因/警告", emptyList<String>(), h.warnings)
        assertEquals("回执必须在 writeIntent 真返回之后才发，不许点下去先报喜",
            listOf("write", "notice", "saved"), h.events)
        assertEquals(1, h.writeCalls)
        assertEquals("交回仓库的是这一稿正文", "周末约她吃饭", h.lastWriteText)
        assertEquals("开关按点下的那一格交", true, h.lastWriteEnabled)
    }

    @Test
    fun `the success copy comes from the resource intent_saved_notice`() {
        val saved = resourceString("intent_saved_notice")
        assertEquals("资源那一格就是「已保存」，不是旧的「已记录」", "已保存", saved)
        assertEquals(
            "没接线时的兜底常量必须与资源逐字同形（两边各改一半这一格红）",
            saved, IntentController.INTENT_SAVED_NOTICE
        )
        assertTrue("成功回执是短句，不许拖着一句解释", IntentController.INTENT_SAVED_NOTICE.length <= 4)
    }

    @Test
    fun `the failure copy comes from the resource intent_save_failed_notice`() {
        val failed = resourceString("intent_save_failed_notice")
        assertEquals("资源那一格说的是保存失败 + 重试", "保存失败，请重试", failed)
        assertEquals(failed, IntentController.INTENT_SAVE_FAILED_NOTICE)
        assertTrue("失败文案得给出下一步", failed.contains("重试"))
    }

    /** 宿主把资源交进来这一条真要用上：注入的文案覆盖兜底常量（签名改错的形状会在这里红） */
    @Test
    fun `the host supplied copies are the ones that reach the notice board`() {
        val h = Harness(
            onConfig(),
            savedNoticeText = { "宿主给的已保存" },
            saveFailedNoticeText = { "宿主给的失败" }
        )
        h.saveBody()
        assertEquals(listOf("宿主给的已保存"), h.notices)

        val failed = Harness(
            onConfig(), failWrite = true,
            savedNoticeText = { "宿主给的已保存" },
            saveFailedNoticeText = { "宿主给的失败" }
        )
        failed.saveBody()
        assertEquals(emptyList<String>(), failed.notices)
        assertEquals(listOf("宿主给的失败"), failed.warnings)
    }

    // ─── ② 落盘失败 ⇒ 只有错误回执，草稿与编辑器都留着 ────────────────

    @Test
    fun `a failed write emits the retry line and no success receipt`() {
        val h = Harness(onConfig(), failWrite = true)
        h.saveBody()

        assertEquals("失败不许发任何成功回执", emptyList<String>(), h.notices)
        assertEquals("错误回执恰好一条", listOf(IntentController.INTENT_SAVE_FAILED_NOTICE), h.warnings)
        assertFalse("落盘失败不许走到 stale 标记那一步", h.savedCalls > 0)
    }

    @Test
    fun `a failed write keeps the editor open so the draft is not lost`() {
        val h = Harness(onConfig())
        h.controller.openEditor()
        val failed = Harness(onConfig(), failWrite = true)
        failed.controller.openEditor()

        h.saveBody()
        failed.saveBody()

        assertTrue("成功那一次才关编辑器", !h.controller.showEditor.value)
        assertTrue("失败那一拍编辑器必须还开着，草稿才回得来", failed.controller.showEditor.value)
        assertEquals("失败不许改屏上那份配置", onConfig(), failed.controller.config.value)
    }

    @Test
    fun `a late save that lost the KB identity guard stays silent`() {
        val h = Harness(onConfig(), kbSwitchedAway = true)
        h.saveBody()

        assertEquals("屏幕已换库：晚到的落盘不报成功也不改脸", emptyList<String>(), h.notices)
        assertEquals("守卫没过时不关编辑器/不标 stale", 0, h.savedCalls)
    }

    // ─── ③ 开关跳变那一句不走「已保存」 ──────────────────────────────

    @Test
    fun `toggling emits the switch line and never doubles up the saved line`() {
        val off = Harness(onConfig(enabled = true))
        off.saveBody(enabled = false)
        assertEquals(listOf(IntentController.INTENT_DISABLED_NOTICE), off.notices)

        val on = Harness(onConfig(enabled = false))
        on.saveBody(enabled = true)
        assertEquals(listOf(IntentController.INTENT_ENABLED_NOTICE), on.notices)
    }

    /** 旧断言「换档 / 完成不发正文回执」的替代：正文与开关都没跳变的那一次只发一句「已保存」 */
    @Test
    fun `saving the same enabled state again is a body save, not a switch line`() {
        val h = Harness(onConfig(enabled = true))
        h.saveBody("周末约她吃饭", enabled = true)
        assertEquals(listOf(IntentController.INTENT_SAVED_NOTICE), h.notices)
        assertEquals("一次 save 只开一条写协程", 1, h.writeCalls)
    }

    /**
     * 替代关系：这一格原来钉的是「旧调用形状还会把期限与状态传进来时，回执与落盘都不许被它带偏」。
     * `save` 现在只收 `(正文, 开关)`，那份输入通道没了，等价的那件事换成 **写侧自己统一落**：
     * 屏幕上那份配置带着旧期限与旧终态（宿主读回来的老文件就是这形状）时，
     * 交回仓库的仍是空到期时刻 + `ACTIVE`，回执也仍是那一句「已保存」。
     * 坏实现（落盘时把屏上那一份的 expiryDate/status 续下去）在这里红。
     */
    @Test
    fun `a legacy screen state on disk still lands without a deadline and with one receipt`() {
        val h = Harness(
            IntentConfig(
                text = "周末约她",
                enabled = true,
                expiry = IntentExpiry.ONE_WEEK,
                expiryDate = "2026-10-01 08:00",
                status = IntentStatus.COMPLETED
            )
        )
        h.controller.save("周末约她吃饭", true)

        assertEquals(listOf(IntentController.INTENT_SAVED_NOTICE), h.notices)
        assertEquals("落盘不带期限", "", h.lastWriteExpiryDate)
        assertEquals("落盘一律活动态", IntentStatus.ACTIVE, h.lastWriteStatus)
    }

    // ─── ④ 重复提交：正在保存时第二句顶回去，而不是再开一条协程 ───────

    @Test
    fun `a second save while one is in flight does not open a second write`() {
        val gate = CompletableDeferred<Unit>()
        val h = Harness(onConfig(), gate = gate)

        h.controller.save("第一次", enabled = true) // 挂在仓库那一侧没回来
        assertEquals("第一次确实已经在飞", 1, h.writeCalls)
        assertTrue("正在保存的读数得交给屏上那颗按钮", h.controller.isSaving.value)

        h.controller.save("第二次", enabled = true)
        assertEquals("第二句不该再开一条写协程", 1, h.writeCalls)
        assertEquals("被顶回去的那一句根本没落到仓库", listOf("第一次"), h.texts)

        gate.complete(Unit)
        assertEquals("门开后那一次正常收尾，只发一条回执", listOf(IntentController.INTENT_SAVED_NOTICE), h.notices)
        assertFalse("收尾后读数落回 false，下一次点保存仍然点得动", h.controller.isSaving.value)
    }

    /** 反向证人：上面那格不是恒绿——同一条链上真开第二条协程时 `writeCalls` 会数到 2 */
    @Test
    fun `the in flight witness counts a second write when there is no gate`() {
        val gate = CompletableDeferred<Unit>()
        val h = Harness(onConfig(), gate = gate)
        h.controller.save("第一次", enabled = true)
        gate.complete(Unit)
        h.controller.save("第二次", enabled = true)

        assertEquals("没有『正在保存』这道门时就是旧的两连写（证人：这把尺数得到 2）", 2, h.writeCalls)
        assertEquals("两稿正文都落了盘——上面那格的 1 次不是恒绿", listOf("第一次", "第二次"), h.texts)
    }

    private fun resourceString(name: String): String {
        val xml = File("src/main/res/values/strings.xml").takeIf { it.isFile }
            ?: File("app/src/main/res/values/strings.xml")
        assertTrue("找不到 $xml——这一格会扫了个空集恒绿", xml.isFile)
        val value = Regex("""<string name="$name">([^<]+)</string>""")
            .find(xml.readText(Charsets.UTF_8))?.groupValues?.get(1)
        assertTrue("$name 不在资源表里", value != null)
        return value!!
    }
}
