package com.lovebrain.app.feature.intent

import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import com.lovebrain.app.model.isIntentEffective
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 期限语义退役之后，**老数据与新写盘**之间那几条合同。
 *
 * 这一族以前钉的是"到期意图重开必须重算期限"（三档期限 + 已完成那一批规则）。规则本身退了，
 * 替代关系写在下面，一条都不许悄悄蒸发：
 * · 「重开必须重算期限，否则下一刻又被判回 EXPIRED」→ 换成
 *   **「任何一次落盘都不带期限」**（[save] 一律写 `expiryDate = ""` + `status = ACTIVE`），
 *   于是"重开即失效"那类缺陷再也没有输入可以触发它；
 * · 「只改正文不许续期」→ 换成**「正文逐字交回仓库，写侧只统一期限那三样」**；
 * · 「到期自动关」（`refreshForKb` 里那次改写）→ **整块退役**，本文件第一格钉住它不再回来：
 *   开着的一条意图不许被背后那次读盘改写，更不许被关掉。
 *
 * 判据：**开 且 正文非空 且 状态是活动档**（`model/Models.kt` 的 [isIntentEffective]）。
 * 第三句是 2026-10-15 书 §3（F02）加的防线：旧 `EXPIRED` / `COMPLETED` / `PAUSED` 就算带着
 * `enabled=true` 也不算数、不注入；日常路径上这类旧文件在唯一读口
 * （`KnowledgeIntentService.decode`）已被归一化成显示关闭（真盘三格由 `IntentStorageTest` 钉），
 * 这里①"到期不自动关"两格钉的是**控制器与时刻无关**（时钟不参与任何判决），
 * 文件级"过去时刻⇒关闭"归读口管，两层各有各的格子。
 *
 * ⚠ 未跑构建：假仓库 + `Dispatchers.Unconfined`，全部只依赖 JVM。
 */
class IntentExpiryReactivationTest {

    /** 仓库写回来的那一份，由 [controllerFor] 记账 */
    private class Fake(
        var stored: IntentConfig,
        /** 仓库真的落了哪几份（每次写盘一条），顺序就是到达顺序 */
        val writes: MutableList<IntentConfig> = mutableListOf()
    )

    /**
     * 一台控制器：读回 [Fake.stored]（建假仓库时灌进去的那一份），
     * 写盘把"仓库真正落下的那一份"记进 [Fake.writes]。
     * `Unconfined` 让 `scope.launch` 那段当场跑完。
     */
    private fun controllerFor(
        fake: Fake,
        onNotice: (String) -> Unit = {},
        onWarning: (String) -> Unit = {}
    ): IntentController = IntentController(
        scope = CoroutineScope(Dispatchers.Unconfined),
        readActiveKbName = { "小美" },
        readIntent = { fake.stored },
        writeIntent = { _, text, enabled, expiry, expiryDate, status ->
            fake.stored = IntentConfig(
                text = text,
                enabled = enabled,
                revision = fake.stored.revision + 1,
                expiry = expiry,
                expiryDate = expiryDate,
                status = status
            )
            fake.writes += fake.stored
            fake.stored
        },
        onNotice = onNotice,
        onWarning = onWarning
    )

    /** 老文件的样子：带着期限与终态那三样，正文与开关是真值 */
    private fun legacy(
        enabled: Boolean,
        status: IntentStatus,
        expiryDate: String = "2026-10-01 08:00",
        expiry: IntentExpiry = IntentExpiry.ONE_WEEK,
        text: String = "周末约她"
    ) = IntentConfig(
        text = text,
        enabled = enabled,
        expiry = expiry,
        expiryDate = expiryDate,
        status = status
    )

    // ─── ① 那条"到期自动关"必须已经消失：读盘不改写、也不关掉用户开着的那条 ───

    /**
     * 反向证人（旧形状）：旧实现在这条链上判 `shouldAutoExpire` 命中，
     * 于是**落盘一次**（enabled=false + EXPIRED）再把关掉的配置摆上屏——
     * "一直开着"的新语义背后悄悄把用户意图关掉，正是这一格要挡的形状。
     *
     * ⚠ 这一格钉的是**控制器不持时钟**：直接交给控制器的配置（内存路径）绝不因
     * `expiryDate` 被背后改写或关掉。文件级"过去的时刻⇒读回关闭"归唯一读口管
     * （F02 书 §3 第 3 条，`IntentStorageTest` 真盘三格钉着），两层判据、两层格子。
     */
    @Test
    fun `reading a kb with a deadline that already passed leaves the intent switched on`() {
        val current = legacy(enabled = true, status = IntentStatus.ACTIVE, expiryDate = "2020-01-01 00:00")
        val fake = Fake(current)
        val controller = controllerFor(fake)

        runBlocking { controller.refreshForKb("小美") }

        assertEquals("读盘那一路不许写盘，实到写盘 ${fake.writes.size} 次", 0, fake.writes.size)
        assertTrue("控制器不看时钟：过期时刻不许在它背后把开着的那条关掉", controller.config.value.enabled)
        assertEquals("屏上那份仍是交回来的原样", current, controller.config.value)
        assertTrue("内存路径的判决不看日期（看日期的那一刀在 decode 读口）",
            controller.config.value.isIntentEffective)
    }

    /**
     * 旧终态那三样**不再无害**（F02，2026-10-15 书 §3）：`enabled=true` 配上
     * COMPLETED/EXPIRED/PAUSED 的配置——不管它是从哪条路进到消费者眼前的——
     * 判据说不算、提示词段是空、盘上数据仍不许被背后改写。
     * 坏实现两个方向都有牙：把第三句防线摘掉（旧"终态无害"形状）⇒ 第二句红；
     * 让防线顺手改写屏上那份 ⇒ 第一句红。
     */
    @Test
    fun `a legacy terminal status never injects even when the switch field says on`() {
        for (status in listOf(IntentStatus.EXPIRED, IntentStatus.COMPLETED, IntentStatus.PAUSED)) {
            val current = legacy(enabled = true, status = status)
            val fake = Fake(current)
            val controller = controllerFor(fake)

            runBlocking { controller.refreshForKb("小美") }

            assertEquals("读 $status 那一份不许顺手改写盘上数据", current, controller.config.value)
            assertFalse("$status 带着 enabled=true 也不算有效（升级不许唤醒注入）",
                controller.config.value.isIntentEffective)
            assertEquals(
                "$status 那份一个字都不许进提示词（用户点过\"已完成\"的就是这一形状）",
                "", com.lovebrain.app.domain.prompt.IntentIdeaBlock.buildIntentBlock(controller.config.value)
            )
        }
    }

    // ─── ② 旧终态重开：照样生效，且落盘一律"活动态 + 无期限" ───────────

    @Test
    fun `switching an expired record back on writes an active record carrying no deadline`() {
        val current = legacy(enabled = false, status = IntentStatus.EXPIRED)
        val fake = Fake(current)
        val controller = controllerFor(fake)

        controller.save("周末约她", enabled = true)

        val written = fake.writes.single()
        assertTrue("重开就是开着", written.enabled)
        assertEquals("新落盘一律活动态", IntentStatus.ACTIVE, written.status)
        assertEquals("新落盘不再带任何到期时刻", "", written.expiryDate)
        assertTrue("重开之后这条必须算数", written.isIntentEffective)
    }

    @Test
    fun `a completed or paused legacy record comes back on the same switch`() {
        for (status in listOf(IntentStatus.COMPLETED, IntentStatus.PAUSED)) {
            val current = legacy(enabled = false, status = status)
            val fake = Fake(current)
            controllerFor(fake).save("周末约她", enabled = true)

            val written = fake.writes.single()
            assertEquals("$status 的重开也落活动态", IntentStatus.ACTIVE, written.status)
            assertEquals("$status 的重开不留期限", "", written.expiryDate)
            assertTrue("$status 重开后开关起效", written.isIntentEffective)
        }
    }

    /**
     * 替代关系（旧形状 → 新形状）：这一格原来钉的是「**宿主仍传旧四份到期决议**时落盘不变」，
     * 判据来自那时还能从调用点交进来的 status / expiryDate。签名收成一个 `(正文, 开关)` 之后，
     * 那条输入通道本身没了，等价的那件事换成 **写侧自己统一落**：
     * 宿主只能交正文与开关，而屏上那一份还带着旧期限与旧终态时，交回仓库的必须是
     * `ACTIVE` + 空到期时刻，而不是把 `config.value` 里那三样原样续下去。
     * 坏实现（`writeIntent(kbName, text, enabled, cfg.expiry, cfg.expiryDate, cfg.status)`，
     * 也就是"沿用屏上那一份"）在这里当场红——这颗假仓库记的正是控制器**交出去**的那三样。
     */
    @Test
    fun `the write side lands an active deadline-free record over a legacy screen state`() {
        val current = legacy(enabled = true, status = IntentStatus.COMPLETED, expiryDate = "2026-10-01 08:00")
        val fake = Fake(current)
        val controller = controllerFor(fake)

        controller.save("周末约她吃饭", enabled = true)

        val written = fake.writes.single()
        assertEquals("到期时刻不许从屏上那份旧数据里续回来", "", written.expiryDate)
        assertEquals("状态不许从屏上那份旧数据里长成 COMPLETED", IntentStatus.ACTIVE, written.status)
        assertEquals("正文仍逐字交回仓库", "周末约她吃饭", written.text)
    }

    // ─── ③ 关掉：文本不丢，只是不算 ─────────────────────────────────

    @Test
    fun `switching off keeps the saved text and just stops counting`() {
        val current = legacy(enabled = true, status = IntentStatus.ACTIVE, expiryDate = "")
        val fake = Fake(current)
        val controller = controllerFor(fake)

        controller.save("周末约她", enabled = false)

        val written = fake.writes.single()
        assertEquals("关掉那一次正文不许被清空", "周末约她", written.text)
        assertFalse(written.enabled)
        assertFalse("关着的意图不算", written.isIntentEffective)
        assertEquals("重开时回填的仍是那句", "周末约她", controller.config.value.text)
    }

    @Test
    fun `saving an empty body while switched on counts as nothing but is still persisted`() {
        val current = legacy(enabled = true, status = IntentStatus.ACTIVE, expiryDate = "", text = "周末约她")
        val fake = Fake(current)
        val controller = controllerFor(fake)

        controller.save("   ", enabled = true)

        val written = fake.writes.single()
        assertTrue("开关仍开着", written.enabled)
        assertFalse("空正文不算有效——不注入，但也没被偷偷关掉", written.isIntentEffective)
        assertEquals("屏上那条仍是刚写回去的", written, controller.config.value)
    }

    // ─── ④ 接线形状：期限那套判据不许从这一族里长回来 ─────────────────

    /**
     * 三处消费者必须读**同一颗**判据。
     *
     * 为什么还要一格源码尺：判据本身（[isIntentEffective]）写对了，`buildIntentBlock` 与
     * `selectForInjection` 各自再补一遍 status 守卫，行为测试只在一处喂终态夹具时不会红
     * ——那正是"一处注入、一处检索拒绝"的来路。
     * 证人（第二半）：这把尺对合成形状确实数得到，不是扫空集。
     */
    @Test
    fun the_injection_and_the_retrieval_read_the_very_same_predicate() {
        val consumers = listOf(
            "domain/prompt/IntentIdeaBlock.kt",
            "domain/OngoingContextSelector.kt"
        )
        for (relative in consumers) {
            val file = appSource(relative)
            assertTrue("找不到 $file——这一格会扫了个空集恒绿", file.isFile)
            val code = SourceScan.maskComments(file.readText(Charsets.UTF_8))
            // import 那一行也写着这个名字，判据的次数要把它摘掉再数
            val decisions = code.countOccurrencesOf("isIntentEffective") -
                code.countOccurrencesOf("model.isIntentEffective")
            assertEquals(
                "$relative 只许问一次那颗唯一的判据（实到 $decisions 处）",
                1, decisions
            )
            // 退役的三样不许在这两处重新变成判据
            for (retired in listOf("IntentStatus.PAUSED", "IntentStatus.COMPLETED", "IntentStatus.EXPIRED", "expiryDate")) {
                assertEquals(
                    "$relative 里不该再按『$retired』判定（旧判据的复活形状）",
                    0, code.countOccurrencesOf(retired)
                )
            }
        }

        // 到期改写这一族整块退役：控制器不再问 IntentPolicy，也没有那颗时钟
        val controller = appSource("feature/intent/IntentController.kt")
        assertTrue("找不到 $controller——这一格会扫了个空集恒绿", controller.isFile)
        val controllerCode = SourceScan.maskComments(controller.readText(Charsets.UTF_8))
        for (retired in listOf("IntentPolicy", "shouldAutoExpire", "effectiveExpiryDate", "effectiveStatus", "readNow")) {
            assertEquals(
                "IntentController 里不该再出现『$retired』（到期自动关与期限校验的来路）",
                0, controllerCode.countOccurrencesOf(retired)
            )
        }

        // 证人：上面两把尺都能数到"复活写法"，不是恒绿
        val witness = "if (cfg.status == IntentStatus.EXPIRED) return \"\"\n" +
            "val now = readNow()\n" + "IntentPolicy.shouldAutoExpire(cfg, now)\n"
        assertEquals(1, witness.countOccurrencesOf("IntentStatus.EXPIRED"))
        assertEquals(1, witness.countOccurrencesOf("shouldAutoExpire"))
        assertEquals(1, witness.countOccurrencesOf("readNow"))
        // 判据那把尺：import 行确实被摘掉了，第二次判定仍数得到（恒绿写法在这里红）
        val singleUse = "import com.lovebrain.app.model.isIntentEffective\nval on = cfg.isIntentEffective"
        assertEquals(
            "一次 import + 一次判定应数成 1",
            1, singleUse.countOccurrencesOf("isIntentEffective") - singleUse.countOccurrencesOf("model.isIntentEffective")
        )
        val doubledUp = singleUse + "\nif (cfg.isIntentEffective && cfg.status != IntentStatus.PAUSED) inject()"
        assertEquals(
            "判据被补第二遍时要数成 2（这一格红说明上面那把尺恒绿）",
            2, doubledUp.countOccurrencesOf("isIntentEffective") - doubledUp.countOccurrencesOf("model.isIntentEffective")
        )
    }

    /** 反向证人：新判据本身不是恒真——四种输入各给一个明确的判决 */
    @Test
    fun `the single predicate says no to every shape that is not on with a body`() {
        assertTrue(IntentConfig(text = "周末约她", enabled = true).isIntentEffective)
        assertFalse("开着但没正文 ⇒ 不算", IntentConfig(text = "   ", enabled = true).isIntentEffective)
        assertFalse("关着有正文 ⇒ 不算（正文只是留着待回填）", IntentConfig(text = "周末约她", enabled = false).isIntentEffective)
        assertFalse("两者都没有 ⇒ 不算", IntentConfig().isIntentEffective)
        assertFalse(
            "旧终态 + 关着 ⇒ 依然不算（这一格防的是『把旧 status 当开关读』）",
            legacy(enabled = false, status = IntentStatus.EXPIRED).isIntentEffective
        )
        assertFalse(
            "F02 防线：开着 + 旧 COMPLETED ⇒ 不算（书 §3『至少旧终态不能注入』）",
            legacy(enabled = true, status = IntentStatus.COMPLETED).isIntentEffective
        )
    }

    /**
     * 时钟只活在夹具里：这个测试文件不许被测代码"顺手"读真实时间（第一格的形状）。
     *
     * 替代关系：原来这一格靠一颗注入进控制器的假时钟（`readNow`）来证明"判决与时钟无关"，
     * 那颗参数已经随期限语义一起从签名上摘掉（摘的动作由下面那把源码尺
     * `the_injection_and_the_retrieval_read_the_very_same_predicate` 钉着），注入点没了，
     * 等价的形状就换成**同一份夹具只换到期时刻**：
     * 未来与过去两个时刻必须给出同一个判决、同一个"零次落盘"。
     * 反向证人：判决一旦重新去读那个时刻（`expiryDate` 回到判据里，或读 `System.currentTimeMillis()`
     * 之类），两条里必有一条翻面或产生一次改写，这一格当场红。
     */
    @Test
    fun `the fixture clock is not what decides validity`() {
        val moments = listOf("2099-12-31 09:00", "2020-01-01 00:00")
        assertEquals("两个时刻必须是两个不同的输入，否则这一格在扫同一份夹具", 2, moments.distinct().size)
        val verdicts = moments.map { expiryDate ->
            val current = legacy(enabled = true, status = IntentStatus.ACTIVE, expiryDate = expiryDate)
            val fake = Fake(current)
            val controller = controllerFor(fake)
            runBlocking { controller.refreshForKb("小美") }
            assertEquals(
                "$expiryDate 那一份不许被背后那次读盘改写（实到写盘 ${fake.writes.size} 次）",
                0, fake.writes.size
            )
            controller.config.value.isIntentEffective
        }
        assertTrue("未来与过去的时刻给同一个判决，实到 $verdicts", verdicts.all { it })
        assertEquals("两个时刻读回来的判决都必须是『算数』", listOf(true, true), verdicts)
    }

    private fun appSource(relative: String): File =
        File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?.resolve(relative)
            ?: File("app/src/main/java/com/lovebrain/app/$relative")

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
}
