package com.lovebrain.app.ui.home

import com.lovebrain.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CAP1（2026-10-06）：「捕获已开启」的四件判据 + 投递闸，穷举成能被摆出来的表。
 *
 * 这一族存在的原因就是用户那句原话——「显示已开启，但抓不到聊天内容」
 * （A1 取证 source-10 候选①：`guideFacts` 旧账不看 allowlist，服务侧空集却 fail-closed 全拒）。
 * CAP3（同日）又添了第二句原话——「第一次进入消息捕获页面，开关是打开的」，
 * 于是这张表除了"那一行念哪一句"，还要钉"那颗开关画哪一档"。
 * 判据收在 [captureTruthOf] 一处，页面与引导层都从这里派生；本表逐格钉：
 *
 * 1. **范围空必须念"范围未选"**，不许借"已开启"的句子（第一格红＝回退成旧 guideFacts 那本账）；
 * 2. **四件都真才算运行中**——缺任何一件都落回各自那格（每件单独缺一次）；
 * 3. 悬浮窗不在时**不算运行中**（候选③：抓到的内容会当场不记，界面就不许念"已开启"）；
 * 4. 六格六句互不相同——两格共用一句＝对用户少说一件事，句子映射也在本表里比一次；
 * 5. CAP3（D1）：那颗开关**画**的是四件的全称读数（`captureSwitchShowsOn`），悬浮窗不参与这一格；
 * 6. CAP3（意图轴）：`ApplySwitch` 那一笔只在"要去的那一档 ≠ 记着的那一档"时落
 *    （显示与意图各自一根轴，混成一根就是"拨一下自己弹回去"）。
 */
class CaptureTruthStateTest {

    private fun facts(
        granted: Boolean = true,
        switch: Boolean = true,
        consent: Boolean = true,
        scope: Boolean = true,
        floating: Boolean = true
    ) = CaptureTruthFacts(
        accessibilityGranted = granted,
        switchOn = switch,
        disclosureConfirmed = consent,
        scopeNonEmpty = scope,
        floatingRunning = floating
    )

    // ═══════════ 1. 范围空那一格 ═══════════

    @Test
    fun `an empty allowlist says scope-not-selected, never running`() {
        val state = captureTruthOf(facts(scope = false))
        assertEquals(CaptureTruthState.ScopeNotSelected, state)
        assertNotEquals(
            "空 allowlist 时服务侧 CapturePolicy 对每条事件都是 allowlist_empty 全拒，" +
                "界面念'已开启'就是那句谎（source-10 候选①的本体）",
            CaptureTruthState.Running, state
        )
        // 被拒路径的句子必须说得出"不会捕获任何内容"，而不是只报计数
        assertNotEquals(
            "范围未选与运行中共用一句 = 绿谎",
            captureStatusSentence(CaptureTruthState.Running),
            captureStatusSentence(CaptureTruthState.ScopeNotSelected)
        )
    }

    // ═══════════ 2. 四件都真才算运行中（每件单独缺一次）═══════════

    @Test
    fun `running requires all four gates true`() {
        assertEquals(CaptureTruthState.Running, captureTruthOf(facts()))
        assertEquals(CaptureTruthState.NoPermission, captureTruthOf(facts(granted = false)))
        assertEquals(CaptureTruthState.CaptureSwitchOff, captureTruthOf(facts(switch = false)))
        assertEquals(CaptureTruthState.DisclosurePending, captureTruthOf(facts(consent = false)))
        assertEquals(CaptureTruthState.ScopeNotSelected, captureTruthOf(facts(scope = false)))
    }

    /**
     * 反例（回退成什么会红）：把披露漏出判据（`Running = granted ∧ switch ∧ scope`）→
     * consent=false 那格会从 DisclosurePending 变成 Running，本表红；
     * 而真机上服务会先在 CONSENT_PENDING 把每条事件静默拦（source-10 候选④同款谎）。
     */
    @Test
    fun `consent-pending gets its own slot and its own sentence`() {
        val state = captureTruthOf(facts(consent = false))
        assertEquals(CaptureTruthState.DisclosurePending, state)
        assertNotEquals(
            captureStatusSentence(CaptureTruthState.Running),
            captureStatusSentence(state)
        )
    }

    // ═══════════ 3. 候选③：悬浮窗不在不算运行中 ═══════════

    @Test
    fun `capture is not called running while the floating window is down`() {
        // 四件全真、悬浮窗不在 ⇒ 长按确认到的内容当场不记——"已开启"三个字在这格就是谎
        assertEquals(CaptureTruthState.FloatingNotStarted, captureTruthOf(facts(floating = false)))
        // 但更早的缺件仍然先报各自的地基格：悬浮窗状态不许盖掉权限/开关/披露/范围的报账
        assertEquals(CaptureTruthState.NoPermission, captureTruthOf(facts(granted = false, floating = false)))
        assertEquals(CaptureTruthState.ScopeNotSelected, captureTruthOf(facts(scope = false, floating = false)))
    }

    // ═══════════ 4. 顺序与句子：一格一出口 ═══════════

    /** 全 32 行穷举：每行判出且只判出一格；Running 当且仅当五颗全真。 */
    @Test
    fun `the full table derives exactly one slot per input row`() {
        var runningRows = 0
        for (granted in booleanArrayOf(true, false)) {
            for (switch in booleanArrayOf(true, false)) {
                for (consent in booleanArrayOf(true, false)) {
                    for (scope in booleanArrayOf(true, false)) {
                        for (floating in booleanArrayOf(true, false)) {
                            val f = facts(granted, switch, consent, scope, floating)
                            val s = captureTruthOf(f)
                            assertEquals(
                                "$f 判出 $s",
                                s, captureTruthOf(f)   // 纯函数：同输入同输出
                            )
                            if (s == CaptureTruthState.Running) runningRows++
                            if (granted && switch && consent && scope && floating) {
                                assertEquals("五颗全真只能是运行中：$f", CaptureTruthState.Running, s)
                            } else if (granted && switch && consent && scope) {
                                assertEquals("四件真而悬浮窗不在不许念已开启", CaptureTruthState.FloatingNotStarted, s)
                            } else {
                                assertNotEquals("缺件格不许借运行中", CaptureTruthState.Running, s)
                            }
                        }
                    }
                }
            }
        }
        assertEquals("运行中在全表里恰好出现一次（五颗全真那一行）", 1, runningRows)
    }

    /** 六格六句：任何两格不许共用一句（共用就等于把缺的那件事少说一遍）。 */
    @Test
    fun `every slot has its own distinct sentence`() {
        val sentences = CaptureTruthState.entries.map { captureStatusSentence(it) }
        assertEquals("六格", 6, sentences.size)
        assertEquals("六句互不相同", 6, sentences.toSet().size)
        // 运行中那句必须自带"长按"约束的读数（候选②处置：不改事件面，先说清只有长按才抓）
        assertEquals(R.string.home_capture_status_on, captureStatusSentence(CaptureTruthState.Running))
        // 范围未选那句必须与"整机没有可授权的 App"分开——两件事不是一格
        assertNotEquals(R.string.capture_apps_none_to_authorize, R.string.capture_apps_row_subtitle_none)
        // 哨兵：尺没空转（上面全是资源 id 比对，本行钉"确实解析到了非零的 id"）
        assertTrue(sentences.all { it != 0 })
    }

    // ═══════════ 5. CAP3（D1）：开关那一格**画**的是有效态，不是偏好位 ═══════════

    /**
     * 全 32 行穷举 `captureSwitchShowsOn`：**四件（权限 ∧ 意图 ∧ 披露 ∧ 范围）全真才画开**，
     * 第五次读数（悬浮窗）不参与这一格。
     *
     * 病灶（用户 2026-10-06 原话）：「第一次进入消息捕获页面的时候，会发现开关是打开的……
     * 我关闭了重新拨开才触发了长文」——显示直读 `captureEnabled`，而那颗偏好位默认 true。
     *
     * 反例（每条都是一格红）：
     * - 显示又去直读偏好位（＝改前形状）→ "缺权限 / 缺同意 / 缺范围还默认开"那三行当场红；
     * - 把悬浮窗也加成显示的第五件 → `floating = false` 而四件全真的那两行红
     *   （那一格用户的意图明明是开着的，把显示拽下去，他拨一下反而把意图写成关＝新问题）；
     * - 拿显示当意图回写（第二本账）→ 这张表判不出任何事，红的是屏幕那两格
     *   （`CaptureAppsScreenTruthTest` 的意图轴格）。
     */
    @Test
    fun `the switch shows on exactly when the four facts are true, floating aside`() {
        var shownOn = 0
        var shownOff = 0
        for (granted in booleanArrayOf(true, false)) {
            for (switch in booleanArrayOf(true, false)) {
                for (consent in booleanArrayOf(true, false)) {
                    for (scope in booleanArrayOf(true, false)) {
                        for (floating in booleanArrayOf(true, false)) {
                            val f = facts(granted, switch, consent, scope, floating)
                            val showsOn = captureSwitchShowsOn(f)
                            assertEquals(
                                "纯函数：同输入同输出（$f）", showsOn, captureSwitchShowsOn(f)
                            )
                            if (showsOn) shownOn++ else shownOff++
                            if (granted && switch && consent && scope) {
                                assertTrue("四件全真必须画开（悬浮窗只改句子）：$f", showsOn)
                            } else {
                                assertFalse("缺任何一件都不许画开：$f", showsOn)
                            }
                        }
                    }
                }
            }
        }
        // 悬浮窗两档各画开一次，其余 30 行全画关（哨兵：防空转、也防"顺手放宽成三件"）
        assertEquals("画开的行数恰好是 2（四件真 × 悬浮窗两档）", 2, shownOn)
        assertEquals(30, shownOff)
        // 反向证人：这一族不是"存在即可"——默认那副全真读数必须判开，缺一件必须判关
        assertTrue(captureSwitchShowsOn(facts()))
        assertFalse(captureSwitchShowsOn(facts(granted = false)))
        assertFalse(captureSwitchShowsOn(facts(consent = false)))
        assertFalse(captureSwitchShowsOn(facts(scope = false)))
        assertFalse(captureSwitchShowsOn(facts(switch = false)))
    }

    /** 显示这一格与"那一句"必须是两件事：悬浮窗不在时行念缺件、开关脸不许跟着掉 */
    @Test
    fun `the delivery reading changes the sentence but never the switch face`() {
        val floatingDown = facts(floating = false)
        assertEquals(CaptureTruthState.FloatingNotStarted, captureTruthOf(floatingDown))
        assertTrue("状态不是 Running，开关却必须仍画开（意图旗标是真的）", captureSwitchShowsOn(floatingDown))
        // 反过来：显示开**不**等于状态允许念"已开启"
        assertNotEquals(CaptureTruthState.Running, captureTruthOf(floatingDown))
    }

    // ═══════════ 6. CAP3（意图轴）：`ApplySwitch` 那一笔到底要不要落 ═══════════

    /**
     * 显示与意图分成两根轴之后，写口（只有 `toggleCapture()`，它是翻、不是设）必须按差别落笔。
     * 四格全穷举。反例：无条件翻 ⇒ (current = 开, target = 开) 那格会把用户刚拨的"开"写成关；
     * 反例：永不写 ⇒ 另两格红（开关真的按不动）。
     */
    @Test
    fun `the intent is written only when the requested tier differs from the recorded one`() {
        assertEquals(true, captureIntentNeedsWrite(currentIntent = false, targetIntent = true))
        assertEquals(true, captureIntentNeedsWrite(currentIntent = true, targetIntent = false))
        assertEquals(false, captureIntentNeedsWrite(currentIntent = true, targetIntent = true))
        assertEquals(false, captureIntentNeedsWrite(currentIntent = false, targetIntent = false))
    }
}
