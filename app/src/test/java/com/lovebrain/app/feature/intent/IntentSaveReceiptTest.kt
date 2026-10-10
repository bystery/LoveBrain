package com.lovebrain.app.feature.intent

import com.lovebrain.app.domain.IntentPolicy
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * request2 §四「意图保存反馈」：**只改正文并保存时，屏上要有成功回执**。
 *
 * 判据形状与 `feature/feedback/DislikeCaseSaveTest`、`viewmodel/ResultCardActionsOutcomeTest`
 * 同一族——回执跟着**真实落盘结果**走：
 * · 写盘真返回 ⇒ 恰好一条成功回执（短句「已记录」，走 [onNotice] = Knowledge 成功格，
 *   **不借** [onWarning] 的黄色警告那一格）；
 * · 写盘抛异常 ⇒ 只有错误回执且说清下一步（含「重试」），**绝不谎报成功**；
 * · 点下去就先说成功（回执早于 writeIntent 返回）——[events] 的到达顺序格会红。
 *
 * 同时钉住两件事**不许**被这一拍带偏：
 * · 保存不重算期限（§10.2 已有判据：重算只跟着重新启用/换档，这里复测回执那一支没碰它）；
 * · 回执不改变正文与启用态——[IntentController.config] 落成的仍是仓库写后的那一份。
 *
 * ⚠ 未跑构建：本文件与被测类都只依赖 JVM（假仓库 + Unconfined），断言全部可被坏实现打破。
 */
/**
 * 固定的"现在"：与已有到期时刻拉开距离，"每存一次就续期"的坏实现会当场露形。
 *
 * 它是**文件级**的一颗常数，不是测试类的成员：`Harness` 是嵌套类（不是 `inner`），
 * 看不见外层实例的属性——第一版把 `now` 写成类成员，编译就红在 `readNow = { now }` 那一行。
 */
private const val FIXED_NOW = "2026-10-06 09:00"

class IntentSaveReceiptTest {

    /** 类内仍用 `now` 这个名字读那同一颗常数（第二份字面量在这儿不存在） */
    private val now: String = FIXED_NOW

    private fun activeConfig(
        text: String = "周末约她",
        expiry: IntentExpiry = IntentExpiry.ONE_DAY,
        expiryDate: String = "2026-10-08 20:00",
        status: IntentStatus = IntentStatus.ACTIVE
    ) = IntentConfig(
        text = text,
        enabled = true,
        expiry = expiry,
        expiryDate = expiryDate,
        status = status
    )

    /**
     * 一台可记录回执的控制器。[events] 按到达顺序记 `write` / `notice` / `warning`，
     * 于是"先落盘还是先报喜"这种事不是靠读代码自证，是断言出来的。
     */
    private class Harness(
        initial: IntentConfig,
        val failWrite: Boolean = false,
        /** 写成功后屏幕上换库（KB 身份守卫晚到判 false） */
        val kbSwitchedAway: Boolean = false
    ) {
        val events = mutableListOf<String>()
        val notices = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var savedCalls = 0
        var stored = initial
        var lastWriteExpiryDate: String? = null
        var lastWriteText: String? = null
        var lastWriteEnabled: Boolean? = null
        var activeKb = "小美"
        val controller: IntentController = run {
            // 先把屏上那份灌进 config 流：refreshForKb 走读口，这里用真实链路喂初值
            lateinit var c: IntentController
            c = IntentController(
                scope = CoroutineScope(Dispatchers.Unconfined),
                readActiveKbName = { activeKb },
                readIntent = { stored },
                writeIntent = { _, text, enabled, expiry, expiryDate, status ->
                    events += "write"
                    if (failWrite) throw IllegalStateException("disk on fire")
                    lastWriteText = text
                    lastWriteEnabled = enabled
                    lastWriteExpiryDate = expiryDate
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
                readNow = { FIXED_NOW }
            )
            kotlinx.coroutines.runBlocking { c.refreshForKb("小美") }
            c
        }
    }

    /** 设置页「保存」那颗的形状：正文换了、enabled/档/状态原样，已有 expiryDate 原样带回 */
    private fun Harness.saveBodyOnly(text: String) {
        controller.save(text, true, stored.expiry, stored.expiryDate, stored.status)
    }

    // ─── ① 保存成功 ⇒ 恰好一条成功回执，不弹原因 ─────────────────────

    @Test
    fun `saving only the body emits exactly one success receipt after the write returns`() {
        val h = Harness(activeConfig())
        h.saveBodyOnly("周末约她吃饭")

        assertEquals("成功回执恰好一条", listOf(IntentController.INTENT_BODY_SAVED_NOTICE), h.notices)
        assertEquals("成功这一支不许弹任何原因/警告", emptyList<String>(), h.warnings)
        assertEquals("回执必须在 writeIntent 真返回之后才发，不许点下去先报喜",
            listOf("write", "notice", "saved"), h.events)
    }

    @Test
    fun `the receipt reuses notice_recorded verbatim and is a short non-warning line`() {
        val xml = File("src/main/res/values/strings.xml").takeIf { it.isFile }
            ?: File("app/src/main/res/values/strings.xml")
        assertTrue("找不到 $xml——这一格会扫了个空集恒绿", xml.isFile)
        val recorded = Regex("""<string name="notice_recorded">([^<]+)</string>""")
            .find(xml.readText(Charsets.UTF_8))?.groupValues?.get(1)
        assertEquals("文案必须与盘上资源 notice_recorded 逐字同形，不新开 key",
            "已记录", recorded)
        assertEquals(recorded, IntentController.INTENT_BODY_SAVED_NOTICE)
        assertTrue("成功回执是短句，不许拖着一句解释", IntentController.INTENT_BODY_SAVED_NOTICE.length <= 4)
    }

    // ─── ② 落盘失败 ⇒ 只有错误回执，不谎报成功 ──────────────────────

    @Test
    fun `a failed write emits the retry warning and no success receipt`() {
        val h = Harness(activeConfig(), failWrite = true)
        h.saveBodyOnly("周末约她吃饭")

        assertEquals("失败不许发任何成功回执", emptyList<String>(), h.notices)
        assertEquals("错误回执恰好一条", 1, h.warnings.size)
        assertTrue("错误回执要说清下一步（保留内容 + 重试）",
            h.warnings.single().contains("失败") && h.warnings.single().contains("重试"))
        assertFalse("落盘失败不许走到 stale 标记那一步", h.savedCalls > 0)
    }

    @Test
    fun `a late save that lost the KB identity guard stays silent`() {
        val h = Harness(activeConfig(), kbSwitchedAway = true)
        h.saveBodyOnly("周末约她吃饭")

        assertEquals("屏幕已换库：晚到的落盘不报成功也不改脸", emptyList<String>(), h.notices)
        assertEquals("守卫没过时不关编辑器/不标 stale", 0, h.savedCalls)
    }

    // ─── ③ 保存不重算期限：回执这一拍不许顺手续期 ────────────────────

    @Test
    fun `the receipt branch passes the existing deadline through verbatim`() {
        val current = activeConfig(expiryDate = "2026-10-08 20:00")
        val h = Harness(current)
        h.saveBodyOnly("周末约她吃饭")

        assertEquals("只改正文交出去的到期时刻必须逐字不动",
            "2026-10-08 20:00", h.lastWriteExpiryDate)
        assertEquals("落盘那份的期限仍是原时刻", "2026-10-08 20:00", h.stored.expiryDate)
        // 反向证人：坏实现（回执那支顺手传空串触发重算）会在这里红——此刻+一天 ≠ 原时刻
        assertEquals("证人：这个 now 重算出来的是另一条时刻",
            "2026-10-07 09:00", IntentPolicy.effectiveExpiryDate(IntentExpiry.ONE_DAY, "", now))
    }

    // ─── ④ 回执不改变正文与启用态；也只有这一支发回执 ────────────────

    @Test
    fun `the receipt leaves text and enabled exactly as the repo wrote them`() {
        val h = Harness(activeConfig())
        h.saveBodyOnly("周末约她吃饭")

        assertEquals("周末约她吃饭", h.controller.config.value.text)
        assertEquals(true, h.controller.config.value.enabled)
        assertEquals("回执只是通知位的副作用，不另开一份正文",
            h.controller.config.value, h.stored)
    }

    @Test
    fun `toggling and completing do not double-send the body receipt`() {
        // 拨开关那一支：只发既有的开关回执，不混进「已记录」
        val toggle = Harness(activeConfig())
        toggle.controller.save("周末约她", false, toggle.stored.expiry, toggle.stored.expiryDate, toggle.stored.status)
        assertEquals(listOf("持续意图已关闭"), toggle.notices)

        // 「完成」那一支：expiry 跳到 COMPLETED 档，不是只改正文，不发「已记录」
        val complete = Harness(activeConfig())
        complete.controller.save("周末约她", true, IntentExpiry.COMPLETED, "", IntentStatus.ACTIVE)
        assertEquals("完成档不发正文回执（开关没跳变、档跳了）", emptyList<String>(), complete.notices)
    }
}
