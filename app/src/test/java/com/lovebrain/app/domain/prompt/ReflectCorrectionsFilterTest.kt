package com.lovebrain.app.domain.prompt

import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.port.InMemoryKnowledgePort
import com.lovebrain.app.domain.port.PromptSourcePort
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.MuteDuration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/**
 *  F3.3 画像 reflect 判据格：`buildReflectUserPrompt`（提取 prompt 一支，**不是**回复装配
 * 主函数）读源材料时必须消费纠正表——
 * - 被 MUTED 的画像条目不出现在 reflect 输入；
 * - 被 WRONG 纠正的画像旧值不回流，补正值以 `（已纠正：…）` 传导进 reflect；
 * - 未被纠正的画像/经验逐字保留。
 *
 * 夹具用一颗"一读就抛"的假资产口钉住"这一支不读资产"——真有人把资产读取加进这一支，
 * 测试当场炸，不是静默绿。
 */
class ReflectCorrectionsFilterTest {

    private val kb = "kb"

    private fun builderWith(
        corrections: Map<String, MemoryCorrection>
    ): PromptBuilder {
        val port = InMemoryKnowledgePort()
        port.seed(kb, "understand/me.md", "我喜欢周末爬山，不太爱熬夜\n")
        port.seed(kb, "understand/her.md", "她在杭州做设计，养了一只猫\n")
        port.seed(kb, "understand/warmth.md", "intimacy：55\ntrust：60\n")
        port.seed(kb, "memory/lessons.md", "# 批1\n- 她讨厌临时改时间\n")
        if (corrections.isNotEmpty()) port.seedCorrections(kb, corrections)
        return PromptBuilder(
            PromptSourcePort { path -> error("reflect 提取支不该读资产：$path") },
            port, null
        )
    }

    @Test
    fun `muted profile entry absent from reflect input`() = runBlocking {
        val prompt = builderWith(mapOf(
            "PROFILE:understand/her.md" to MemoryCorrection(
                memoryId = "PROFILE:understand/her.md",
                action = CorrectionAction.MUTED,
                muteDuration = MuteDuration.UNTIL_RESTORE,
                muteTimestamp = OffsetDateTime.now().toString()
            )
        )).buildReflectUserPrompt(kb)
        assertFalse("被 MUTED 的条目不得出现在 reflect 输入", prompt.contains("她在杭州做设计"))
        assertTrue("未纠正的 me.md 逐字保留", prompt.contains("我喜欢周末爬山"))
    }

    @Test
    fun `wrong profile value does not flow back and replacement transmits`() = runBlocking {
        val prompt = builderWith(mapOf(
            "PROFILE:understand/her.md" to MemoryCorrection(
                memoryId = "PROFILE:understand/her.md",
                action = CorrectionAction.WRONG,
                replacementText = "她在苏州做插画"
            )
        )).buildReflectUserPrompt(kb)
        assertFalse("被 WRONG 纠正的旧值不得按原文回流进画像重写", prompt.contains("她在杭州做设计"))
        assertTrue("补正值传导进 reflect", prompt.contains("（已纠正：她在苏州做插画）"))
    }

    @Test
    fun `corrected lessons file drops out of reflect input`() = runBlocking {
        val prompt = builderWith(mapOf(
            "LESSON:memory/lessons.md" to MemoryCorrection(
                memoryId = "LESSON:memory/lessons.md",
                action = CorrectionAction.WRONG
            )
        )).buildReflectUserPrompt(kb)
        assertFalse("被纠正的经验文件不再进 reflect", prompt.contains("她讨厌临时改时间"))
        assertFalse("经验段随之整段省略", prompt.contains("## 最近经验"))
    }

    @Test
    fun `without corrections reflect input is verbatim`() = runBlocking {
        val prompt = builderWith(emptyMap()).buildReflectUserPrompt(kb)
        assertTrue(prompt.contains("她在杭州做设计"))
        assertTrue(prompt.contains("她讨厌临时改时间"))
        assertTrue(prompt.contains("我喜欢周末爬山"))
    }
}
