package com.lovebrain.app.feature.intent

import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.domain.IntentPolicy
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * §10.2「到期意图重新开启」这条原话的复测账。
 *
 * 用户合同那一行写的是两件事，缺一件都算没修：
 * · **重新启用到期意图时必须重算期限**——状态恢复成 ACTIVE 却**仍带入旧的到期时间**，
 *   下一次 `refreshForKb` 的 `shouldAutoExpire` 立刻把它判回 EXPIRED
 *   （"重新开启后立即失效"就是这一条形状）；
 * · **单纯修改正文必须继续保持原期限**——每存一次就按"此刻 + 一档"续期，意图永不过期。
 *
 * ## 三条腿，缺一不可
 * 1. **判据本身**（[IntentPolicy.saveDecision]）是纯函数，真值表在 `domain/IntentPolicyTest`；
 *    这里把它接进**真实保存链**（`IntentController.save` → 假仓库 → 落盘那一份配置）。
 * 2. **面板两处调用点确实问的是这一颗**（最后一格的源码形状尺）：判据写对了、页面各自再
 *    随手拼一遍 `wasTerminal`，下一轮漂移回来谁都不会红。
 * 3. **反向证人**（`the witness proves…` 那一格）：拿"旧缺陷态"那两种手写形状各跑一次，
 *    证明上面那些断言在那两种形状下**真的会红**，不是恒绿。
 *
 * ⚠ "重开之后会不会立刻失效"这一句为什么能证伪旧缺陷：旧写法把 `intentConfig.expiryDate`
 *   原样传下去 ⇒ 落盘那一份的到期时刻仍在过去 ⇒ [IntentPolicy.shouldAutoExpire] 数到 true。
 *   纯字符串比较，不需要设备、不需要模型。
 */
class IntentExpiryReactivationTest {

    /** 固定的"现在"：晚于下面那条旧到期时刻，早于任何一档重算出来的时刻 */
    private val now = "2026-10-06 09:00"

    /** 到期意图现场：到期时刻已经过去（`IntentController.refreshForKb` 到期时就是这样落盘的） */
    private fun expiredConfig(
        expiry: IntentExpiry = IntentExpiry.ONE_DAY,
        text: String = "周末约她"
    ) = IntentConfig(
        text = text,
        enabled = false,
        expiry = expiry,
        expiryDate = "2026-10-01 08:00",
        status = IntentStatus.EXPIRED
    )

    /**
     * 一台控制器：读回 [initial]，写盘把落盘那一份交回 [onWrite]。
     * `Unconfined` 让 `scope.launch` 里那段当场跑完；时钟固定成 [now]，
     * 所以"重算出来的到期时刻"是可以逐字断言的日期。
     */
    private fun controllerFor(
        initial: IntentConfig,
        onWrite: (IntentConfig) -> Unit
    ): IntentController {
        var stored = initial
        return IntentController(
            scope = CoroutineScope(Dispatchers.Unconfined),
            readActiveKbName = { "小美" },
            readIntent = { stored },
            writeIntent = { _, text, enabled, expiry, expiryDate, status ->
                stored = IntentConfig(
                    text = text,
                    enabled = enabled,
                    revision = stored.revision + 1,
                    expiry = expiry,
                    expiryDate = expiryDate,
                    status = status
                )
                onWrite(stored)
                stored
            },
            readNow = { now }
        )
    }

    /**
     * 面板 `PanelSettingsPage.onIntentChange` 那一格的镜像：同一份判据、同一次传参形状
     * （`recomputeExpiry` 为真才交空串，否则交已有时刻）。形状本身由最后一格钉着不许漂。
     */
    private fun saveFromSettings(
        controller: IntentController,
        current: IntentConfig,
        text: String,
        enabled: Boolean,
        expiry: IntentExpiry,
        callerRequestedRecompute: Boolean
    ) {
        val decision = IntentPolicy.saveDecision(
            current = current,
            nextEnabled = enabled,
            nextExpiry = expiry,
            callerRequestedRecompute = callerRequestedRecompute
        )
        controller.save(
            text, enabled, expiry,
            if (decision.recomputeExpiry) "" else current.expiryDate,
            decision.status
        )
    }

    /** 面板 `IntentEditorDialog.onSave` 那一格的镜像（浮层自己交来的 expiryDate 一律不用） */
    private fun saveFromEditor(
        controller: IntentController,
        current: IntentConfig,
        text: String,
        enabled: Boolean,
        expiry: IntentExpiry,
        editorStatus: IntentStatus
    ) {
        val decision = IntentPolicy.saveDecision(
            current = current,
            nextEnabled = enabled,
            nextExpiry = expiry,
            nextStatus = editorStatus
        )
        controller.save(
            text, enabled, expiry,
            if (decision.recomputeExpiry) "" else current.expiryDate,
            decision.status
        )
    }

    // ─── ① 到期意图重新开启：重算期限 + 恢复 ACTIVE ─────────────────────

    @Test
    fun `re-enabling an expired intent writes a future deadline and the ACTIVE status`() {
        val current = expiredConfig()
        var stored = current
        val controller = controllerFor(current) { stored = it }

        saveFromSettings(
            controller, current, text = "周末约她", enabled = true,
            expiry = IntentExpiry.ONE_DAY, callerRequestedRecompute = false
        )

        val written = stored
        assertEquals("重新启用要恢复 ACTIVE", IntentStatus.ACTIVE, written.status)
        assertTrue("重新启用要 enabled", written.enabled)
        assertEquals(
            "重新启用必须按此刻重算期限（旧时刻 " + current.expiryDate + " 已经过去）",
            "2026-10-07 09:00", written.expiryDate
        )
        // 这一句才是"重开后立即失效"那条原话的直接证伪：落盘那一份不该一读就被判过期
        assertFalse(
            "重开后落盘那份仍会被判过期 = 立即失效，实到到期时刻 " + written.expiryDate,
            IntentPolicy.shouldAutoExpire(written, now)
        )
    }

    @Test
    fun `re-enabling through the editor also reactivates, it used to keep the stale status`() {
        val current = expiredConfig(expiry = IntentExpiry.ONE_WEEK)
        var stored = current
        val controller = controllerFor(current) { stored = it }

        // 浮层里 `editStatus` 一直带着传进来的旧 status（EXPIRED）——旧实现原样落盘
        saveFromEditor(
            controller, current, text = "周末约她", enabled = true,
            expiry = IntentExpiry.ONE_WEEK, editorStatus = IntentStatus.EXPIRED
        )

        assertEquals("浮层里重开也要恢复 ACTIVE", IntentStatus.ACTIVE, stored.status)
        assertEquals("2026-10-13 09:00", stored.expiryDate)
        assertFalse(IntentPolicy.shouldAutoExpire(stored, now))
    }

    @Test
    fun `a completed intent that is switched back on comes back with a fresh deadline`() {
        val current = IntentConfig(
            text = "先把这阵子聊稳", enabled = false, expiry = IntentExpiry.ONE_HOUR,
            expiryDate = "2026-10-05 10:00", status = IntentStatus.COMPLETED
        )
        var stored = current
        saveFromSettings(
            controllerFor(current) { stored = it }, current,
            text = "先把这阵子聊稳", enabled = true,
            expiry = IntentExpiry.ONE_HOUR, callerRequestedRecompute = false
        )

        assertEquals(IntentStatus.ACTIVE, stored.status)
        assertEquals("2026-10-06 10:00", stored.expiryDate)
    }

    /**
     * 同一句"重开即失效"的另一条来路：意图**关着跨过了到期时刻**。
     * `shouldAutoExpire` 对未启用的意图不判过期，所以盘上仍是 `ACTIVE + 2026-10-04 08:00`；
     * 重新拨开时若照原样带走那条时刻，下一次读盘就被标成 EXPIRED。
     * ⇒ 判据把"任何一次关 → 开"都算重新启用，不只是终态那一档。
     */
    @Test
    fun `an intent whose deadline passed while it was switched off is recomputed too`() {
        val current = IntentConfig(
            text = "周末约她", enabled = false, expiry = IntentExpiry.ONE_DAY,
            expiryDate = "2026-10-04 08:00", status = IntentStatus.ACTIVE
        )
        var stored = current
        saveFromSettings(
            controllerFor(current) { stored = it }, current,
            text = "周末约她", enabled = true,
            expiry = IntentExpiry.ONE_DAY, callerRequestedRecompute = false
        )

        assertEquals("2026-10-07 09:00", stored.expiryDate)
        assertEquals(IntentStatus.ACTIVE, stored.status)
        assertFalse("关着过期后重开仍带入旧时刻 = 重开即失效",
            IntentPolicy.shouldAutoExpire(stored, now))
    }

    @Test
    fun `re-picking the validity period on an expired intent also recomputes`() {
        val current = expiredConfig()
        var stored = current
        saveFromSettings(
            controllerFor(current) { stored = it }, current,
            text = "周末约她", enabled = true,
            expiry = IntentExpiry.ONE_WEEK, callerRequestedRecompute = true
        )

        assertEquals(IntentStatus.ACTIVE, stored.status)
        assertEquals("2026-10-13 09:00", stored.expiryDate)
    }

    // ─── ② 单纯修改正文：期限逐字不变 ─────────────────────────────────

    @Test
    fun `editing only the text keeps the original deadline verbatim`() {
        val current = IntentConfig(
            text = "周末约她", enabled = true, expiry = IntentExpiry.ONE_DAY,
            expiryDate = "2026-10-08 20:00", status = IntentStatus.ACTIVE
        )
        var stored = current
        saveFromSettings(
            controllerFor(current) { stored = it }, current,
            text = "周末约她吃饭", enabled = true,
            expiry = IntentExpiry.ONE_DAY, callerRequestedRecompute = false
        )

        assertEquals("只改正文不许动到期时刻", "2026-10-08 20:00", stored.expiryDate)
        assertEquals(IntentStatus.ACTIVE, stored.status)
        assertEquals("周末约她吃饭", stored.text)
    }

    @Test
    fun `an active intent edited through the editor keeps its deadline too`() {
        val current = IntentConfig(
            text = "周末约她", enabled = true, expiry = IntentExpiry.ONE_WEEK,
            expiryDate = "2026-10-12 07:30", status = IntentStatus.ACTIVE
        )
        var stored = current
        saveFromEditor(
            controllerFor(current) { stored = it }, current,
            text = "周末约她", enabled = true,
            expiry = IntentExpiry.ONE_WEEK, editorStatus = IntentStatus.ACTIVE
        )

        assertEquals("编辑浮层只改正文也不许续期", "2026-10-12 07:30", stored.expiryDate)
    }

    @Test
    fun `switching off keeps the deadline and never rewrites a terminal status`() {
        val current = expiredConfig()
        var stored = current
        saveFromSettings(
            controllerFor(current) { stored = it }, current,
            text = "周末约她", enabled = false,
            expiry = IntentExpiry.ONE_DAY, callerRequestedRecompute = false
        )

        assertEquals("2026-10-01 08:00", stored.expiryDate)
        assertEquals("关着的那一格不许被洗成活动态", IntentStatus.EXPIRED, stored.status)
    }

    @Test
    fun `marking completed is never overridden by the reactivation rule`() {
        // 到期意图重开的同时又被点成「已完成」：完成是状态动作，压过"恢复 ACTIVE"
        val current = expiredConfig()
        var stored = current
        saveFromEditor(
            controllerFor(current) { stored = it }, current,
            text = "周末约她", enabled = true,
            expiry = IntentExpiry.COMPLETED, editorStatus = IntentStatus.COMPLETED
        )

        assertEquals(IntentStatus.COMPLETED, stored.status)
        assertEquals("COMPLETED 不带到期时刻", "", stored.expiryDate)
    }

    // ─── ③ 反向证人：旧缺陷态这两种写法都会让上面的断言红 ───────────────

    @Test
    fun `the witness proves these assertions are not vacuously green`() {
        val current = expiredConfig()

        // 缺陷态 A：重新启用时把旧到期时刻原样传下去（状态改对了、期限没重算）
        val carryOldDeadline = IntentPolicy.effectiveExpiryDate(
            current.expiry, current.expiryDate, now
        )
        val resurrectedButStale = current.copy(
            enabled = true, status = IntentStatus.ACTIVE, expiryDate = carryOldDeadline
        )
        assertTrue(
            "证人失效：带着旧期限落盘居然不再被判过期，那第一格断言就是恒绿",
            IntentPolicy.shouldAutoExpire(resurrectedButStale, now)
        )
        assertEquals(
            "证人：缺陷态 A 落盘的时刻正是那条已过去的旧时刻",
            "2026-10-01 08:00", carryOldDeadline
        )

        // 缺陷态 B：每次保存都传空串触发重算（只改正文也被续期）
        val alwaysRecomputed = IntentPolicy.effectiveExpiryDate(IntentExpiry.ONE_DAY, "", now)
        assertNotEquals(
            "证人：缺陷态 B 把已有时刻换成了此刻加一天，只改正文逐字不变那一格才抓得住",
            "2026-10-08 20:00", alwaysRecomputed
        )
    }

    // ─── 接线形状：面板两处保存口必须真的问这一颗判据 ────────────────────

    /**
     * 判据住在 `IntentPolicy` 里还不够——**页面得真的去问它**。
     * 两处保存口（设置页 `onIntentChange` / 编辑浮层 `onSave`）各自再随手拼一遍终态判定，
     * 就是下一轮漂移回来的入口，所以这一格按形状数。
     */
    @Test
    fun bothPanelSaveDoorsAskThePolicyInsteadOfRecomputingTheRuleThemselves() {
        val panel = File(appRoot, "ui/panel/LoveBrainPanelScreen.kt")
        assertTrue("找不到 $panel——这一格会扫了个空集恒绿", panel.isFile)
        // 注释里写着 `IntentPolicy.saveDecision(` 不算接了线
        val code = SourceScan.maskComments(panel.readText(Charsets.UTF_8))

        val decisionNeedle = "IntentPolicy.saveDecision("
        val decisionCalls = code.countOccurrencesOf(decisionNeedle)
        assertEquals(
            "面板两处保存口各问一次判据（设置页 + 编辑浮层），实到 $decisionCalls 处",
            2, decisionCalls
        )

        val expiryArg = Regex("if\\s*\\(\\s*\\w+\\.recomputeExpiry\\s*\\)\\s*\"\"")
            .findAll(code).toList()
        assertEquals(
            "交空串只由 `recomputeExpiry` 决定，两处各写一次，实到 ${expiryArg.size} 处",
            2, expiryArg.size
        )
        expiryArg.forEach { match ->
            val tail = code.substring(match.range.last + 1, minOf(match.range.last + 200, code.length))
            assertTrue(
                "空串那一支之后必须紧跟「否则交已有时刻」：${tail.take(80)}",
                Regex("""^\s*else\s+\w+\.expiryDate""").containsMatchIn(tail.trimStart())
            )
        }

        // 旧的那二本账不许还留在页面上：终态判定与 `mustRecomputeExpiry` 都该搬进判据
        assertEquals(
            "页面里不该再自己拼终态判定（`wasTerminal` / `mustRecomputeExpiry`），实到 " +
                Regex("mustRecomputeExpiry|wasTerminal").findAll(code).count() + " 处",
            0, Regex("mustRecomputeExpiry|wasTerminal").findAll(code).count()
        )

        // 反向证人：这几把尺对合成形状确实数得到，不是扫空集
        val witness = "val wasTerminal = s == EXPIRED; " +
            "save(IntentPolicy.saveDecision(a, b).status, " +
            "if (decision.recomputeExpiry) \"\" else current.expiryDate) // mustRecomputeExpiry"
        assertEquals(1, witness.countOccurrencesOf(decisionNeedle))
        assertEquals(1, Regex("if\\s*\\(\\s*\\w+\\.recomputeExpiry\\s*\\)\\s*\"\"").findAll(witness).count())
        assertTrue(
            "证人：旧二本账的写法（`wasTerminal` / `mustRecomputeExpiry`）必须被那把尺数到",
            Regex("mustRecomputeExpiry|wasTerminal").findAll(witness).count() >= 2
        )
    }

    private fun String.countOccurrencesOf(needle: String): Int {
        var i = 0
        var n = 0
        while (true) {
            val at = indexOf(needle, i)
            if (at < 0) return n
            n++
            i = at + needle.length
        }
    }

    private val appRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")
}
