package com.lovebrain.app.domain

import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.domain.prompt.IntentIdeaBlock
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 持续意图的**一条判据、两处消费**——提示词注入那一侧（`domain/prompt/IntentIdeaBlock.buildIntentBlock`）
 * 与进行中事项检索授权那一侧（[OngoingContextSelector.selectForInjection]）对同一份输入
 * 必须给同一个判决。
 *
 * 这一族的旧形状正是两处各写各的：注入那一侧看 `expiryDate < now` 与 status，
 * 检索那一侧只看 status，谁都不看另一边的东西。于是同一份"上周设的一天期、现在开着"的意图
 * 会**一边注入、一边拒绝推进事项**，用户看到的是"开关明明开着，军师时灵时不灵"。
 * 现在两处都只问 [IntentConfig] 的那颗判据：**开 且 正文非空**。
 *
 * ## 有牙在哪儿
 * · 矩阵里那行「开着的 + 早已过去的 `expiryDate`」是**反例钉**：旧实现下注入给 false、
 *   检索给 true，这一格当场红；把任一侧偷偷加回 status/期限守卫，判决就分叉。
 * · 事项名里的关键词（见面/安排）**只出现在意图正文**，夹具里的消息与 `replyDirective` 都碰不到它，
 *   所以"检索授权"这件事除了意图没有别的来路——判据一失效，`eligible` 立刻空。
 * · 反向对照：关掉开关的同一份输入必须两处都不动（否则整格就成了"两处都恒真"）。
 */
class IntentInjectionAgreementTest {

    private val plan = "# 事项计划\n\n## 进行中\n见面安排|进行中|[2026-09-15 10:00]约定（当前）\n"

    /** 只有意图正文提到这件事：夹具里的消息与 `replyDirective` 都给不出这个关键词 */
    private val intentBody = "这周把见面的日子定下来"

    private fun selectorWith(intent: IntentConfig): OngoingContextSelector {
        val repo = mockk<KnowledgeRepository>(relaxed = true)
        coEvery { repo.readPlanActive("kb") } returns plan
        coEvery { repo.readFile("kb", "moment/ongoing_cooldown.json") } returns ""
        coEvery { repo.readIntent("kb") } returns intent
        return OngoingContextSelector(repo)
    }

    /** 夹具里的消息与 `replyDirective`（这里给的是 null）都跟"见面"无关——检索授权只能来自意图那一条 */
    private fun context(intent: IntentConfig) = OngoingContextSelector.SelectionContext(
        messages = listOf(
            ChatMessage(id = "m1", role = ChatMessage.Role.HER, content = "今天加班到好晚"),
            ChatMessage(id = "m2", role = ChatMessage.Role.ME, content = "辛苦啦")
        ),
        currentTurn = 1,
        currentTime = "2026-09-17 12:00",
        replyDirective = null,
        effectiveIntent = intent
    )

    /** 一行的期望判决 + 那份输入 */
    private data class Row(val label: String, val config: IntentConfig, val expectedValid: Boolean)

    private fun verdicts(row: Row): Pair<Boolean, Boolean> {
        val injected = IntentIdeaBlock.buildIntentBlock(row.config).isNotBlank()
        val authorized = runBlocking {
            selectorWith(row.config).selectForInjection("kb", context(row.config)).eligibleItems.isNotEmpty()
        }
        return injected to authorized
    }

    @Test
    fun `both consumers give the very same verdict on the same config`() {
        val rows = listOf(
            Row(
                "开着 + 有正文 = 有效",
                IntentConfig(text = intentBody, enabled = true, status = IntentStatus.ACTIVE),
                expectedValid = true
            ),
            Row(
                "开着 + 旧的过去期限 = 仍然有效（退役的是期限，不是那句正文）",
                IntentConfig(
                    text = intentBody, enabled = true, status = IntentStatus.ACTIVE,
                    expiry = IntentExpiry.ONE_WEEK, expiryDate = "2020-01-01 00:00"
                ),
                expectedValid = true
            ),
            Row(
                "开着 + 旧 EXPIRED = 有效",
                IntentConfig(
                    text = intentBody, enabled = true, status = IntentStatus.EXPIRED,
                    expiry = IntentExpiry.ONE_DAY, expiryDate = "2020-01-01 00:00"
                ),
                expectedValid = true
            ),
            Row(
                "开着 + 旧 PAUSED = 有效",
                IntentConfig(text = intentBody, enabled = true, status = IntentStatus.PAUSED),
                expectedValid = true
            ),
            Row(
                "开着 + 旧 COMPLETED = 有效",
                IntentConfig(
                    text = intentBody, enabled = true, status = IntentStatus.COMPLETED,
                    expiry = IntentExpiry.COMPLETED
                ),
                expectedValid = true
            ),
            Row(
                "关着 + 有正文 = 无效（文本留着，但不说话）",
                IntentConfig(text = intentBody, enabled = false),
                expectedValid = false
            ),
            Row(
                "开着 + 空正文 = 无效",
                IntentConfig(text = "   ", enabled = true),
                expectedValid = false
            ),
            Row(
                "什么都没有 = 无效",
                IntentConfig(),
                expectedValid = false
            )
        )

        for (row in rows) {
            val (injected, authorized) = verdicts(row)
            assertEquals("${row.label}：两处判决必须一致（注入 $injected / 检索 $authorized）", injected, authorized)
            assertEquals("${row.label}：期望的是 ${row.expectedValid} 那一格", row.expectedValid, injected)
        }
    }

    /**
     * 注入的那一段是真内容，不是"有个非空串就行"：正文逐字带过去，两处用的是同一句正文。
     *
     * 这一格挡的是"注入侧返回一个占位串、检索侧照旧授权"这种两边都算数却谁都没真读正文的形状。
     */
    @Test
    fun `the injected block carries the intent body verbatim`() {
        val config = IntentConfig(text = "  这周把见面的日子定下来  ", enabled = true)
        val block = IntentIdeaBlock.buildIntentBlock(config)

        assertTrue("注入段得带上那句正文：$block", block.contains("这周把见面的日子定下来"))
        assertTrue("段落要标出自己是谁：$block", block.startsWith("【持续意图】"))
        val authorized = runBlocking {
            selectorWith(config).selectForInjection("kb", context(config)).eligibleItems.map { it.name }
        }
        assertEquals("检索授权推进的就是那件被正文提到的事项", listOf("见面安排"), authorized)
    }

    /** 关掉开关之后：既不进提示词，也不再替事项说话——但正文一个字都没被改写 */
    @Test
    fun `switching off silences both sides without touching the stored body`() {
        val stored = IntentConfig(text = intentBody, enabled = true)
        val off = stored.copy(enabled = false)

        assertEquals("", IntentIdeaBlock.buildIntentBlock(off))
        val authorized = runBlocking {
            selectorWith(off).selectForInjection("kb", context(off)).eligibleItems
        }
        assertTrue("关着的意图不许授权检索：$authorized", authorized.isEmpty())
        assertFalse("正文仍留在盘上那份配置里，等用户重开回填", off.text.isBlank())
    }
}
