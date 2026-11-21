package com.lovebrain.app.domain.champion

import com.lovebrain.app.domain.prompt.IntentIdeaBlock
import com.lovebrain.app.model.IntentConfig
import kotlinx.serialization.json.JsonObject

/**
 * 冠军 prompt 装配（Kotlin 移植）——逐字对齐既有参考实现
 * 段序与 parts 拼装均不在本移植内夹带改动。
 *
 * 段序严格固定（任何改动都是新变异，不得在本移植内夹带）：
 * 1. 【画像/初始事实(种子，可公开使用)】（persona/initial_kb 均非空才出现）
 * 2. 持续意图段（复用产品 [IntentIdeaBlock.buildIntentBlock]；关闭/缺省 → 空，不留旧目标）
 * 3. 军师备注段（复用 [IntentIdeaBlock.buildAdvisorNoteBlock]，本轮约束通道）
 * 4. 真实历史消息（[EventHistoryAssembler] 3600 CU 整条预算）
 * 5. 本轮输入（最高优先，整条不被裁；逐条一行 JSON，`id` 是本轮窗口别名、
 *    `source_id` 对齐稳定 uid，正文转义与产品 [ChatTranscriptBlock] 的注入防护同口径）
 * 6. 当前时间块（复用产品 `PromptBuilder.buildTimestampPrompt` 文案——经
 *    [timestampBlock] 供应商注入，本类不复制那份措辞）
 *
 * 未定/待确认事项段（原第 5 段）已整段删除：用户口径「未定块完全不要」，
 * 装配不再产生该段、也不再携带这一段的计量。
 *
 * system prompt = 冠军 Harness 的 slim system 资产（`champion/core_slim.md` 等四件，
 * 经产品 PromptSourcePort 读取，不改现有 AssetRegistry.CORE 四件）。
 */
class ChampionPromptAssembler(
    /** 当前时间块（产品 PromptBuilder.buildTimestampPrompt 的同一份真源） */
    private val timestampBlock: () -> String,
    /** 冠军 system 资产读取（PromptSourcePort 同一实现） */
    internal val promptSource: (path: String) -> String
) {

    data class ChampionSeed(
        val persona: JsonObject? = null,
        val initialKb: JsonObject? = null
    ) {
        val isEmpty: Boolean
            get() = (persona == null || persona.isEmpty()) &&
                (initialKb == null || initialKb.isEmpty())
    }

    data class ChampionPrompt(
        val prompt: String,
        /** 本轮窗口别名 m<i> → 稳定 uid（与产品 ReplySourceAliasMap 同通道上报用） */
        val sourceAliasMap: Map<String, Long>,
        val historyCu: Int
    )

    // ── system 资产（champion/ 下新文件，不动现有 AssetRegistry.CORE 四件） ──

    object ChampionAssets {
        const val CORE_SLIM = "engine/champion/core_slim.md"
        const val NATURALNESS = "engine/champion/naturalness_check.md"
        const val REDLINE = "engine/champion/redline.md"
        const val FORMAT = "engine/champion/format.md"

        val ALL = listOf(CORE_SLIM, NATURALNESS, REDLINE, FORMAT)
    }

    /** 冠军 system prompt：slim system 四件。 */
    fun buildSystemPrompt(): String = buildString {
        append(promptSource(ChampionAssets.CORE_SLIM))
        append("\n\n---\n\n")
        append(promptSource(ChampionAssets.NATURALNESS))
        append("\n\n---\n\n")
        append(promptSource(ChampionAssets.REDLINE))
        append("\n\n---\n\n")
        append(promptSource(ChampionAssets.FORMAT))
    }

    /**
     * 装配一条冠军 prompt。
     *
     * @param events 全量事件（uid 升序）
     * @param curWindow 本轮窗口事件（已在 [ChampionEventStore.append] 拿到稳定 uid）
     * @param turn 本轮轮次号（仅作调用方语义标注；未定段已删除，装配不再按轮次分支）
     * @param wallClock 分钟级时间戳（Clock 端口）；空 → 时间块不出现
     */
    fun assemble(
        events: List<ChampionEvent>,
        curWindow: List<ChampionEvent>,
        turn: Int,
        intent: IntentConfig,
        advisorNote: String,
        wallClock: String,
        seed: ChampionSeed = ChampionSeed()
    ): ChampionPrompt {
        val curUids = curWindow.map { it.uid }.toSet()
        // 历史 = 除本轮外的既有事件（当前消息已在独立段完整呈现，不在历史里重复/挤占）
        val priorEvents = events.filter { it.uid !in curUids }

        val parts = mutableListOf<String>()

        // 1) 画像 + 初始知识（同一允许的 seed，两臂都可用的可公开事实）
        val seedEntries = LinkedHashMap<String, JsonObject>()
        seed.persona?.takeIf { !it.isEmpty() }?.let { seedEntries["persona"] = it }
        seed.initialKb?.takeIf { !it.isEmpty() }?.let { seedEntries["initial_kb"] = it }
        if (seedEntries.isNotEmpty()) {
            val obj = ChampionJson.obj(seedEntries.mapValues { ChampionJson.fromOther(it.value) })
            parts += "【画像/初始事实(种子，可公开使用)】\n" + ChampionJson.dumps(obj)
        }

        // 2) 当前有效 intent（复用产品块，逐字同格式；关闭/缺省 → 空，不留旧目标）
        val intentBlock = IntentIdeaBlock.buildIntentBlock(intent).trim()
        if (intentBlock.isNotEmpty()) parts += intentBlock

        // 3) note（军师备注，与 intent 区分：只约束这一轮怎么想/怎么回，不是已说过的话）
        val noteBlock = IntentIdeaBlock.buildAdvisorNoteBlock(advisorNote).trim()
        if (noteBlock.isNotEmpty()) parts += noteBlock

        // 4) 历史真实消息（整段预算裁剪；含每条来源 uid 与 ts，来源可区分）
        val hist = EventHistoryAssembler.budgetHistory(priorEvents, EventHistoryAssembler.MAX_EVENT_HISTORY_CU)
        parts += "# 【真实历史消息：仅实际发送；模型回复是草稿，不在此列】\n" + hist.body

        // 5) 当前窗口（最高优先，整条不被裁）：逐条 m-alias + speaker + ts + 文本
        val curLines = mutableListOf<String>()
        val sourceAliasMap = LinkedHashMap<String, Long>()
        for ((i, m) in curWindow.withIndex()) {
            val alias = "m$i"
            sourceAliasMap[alias] = m.uid
            val speaker = if (m.role == "HER") "PARTNER" else "USER"
            curLines += ChampionJson.dumps(
                ChampionJson.obj(linkedMapOf(
                    "id" to ChampionJson.JStr(alias),
                    "source_id" to ChampionJson.JNum(m.uid.toString()),
                    "speaker" to ChampionJson.JStr(speaker),
                    "ts" to ChampionJson.JStr(m.ts),
                    "text" to ChampionJson.JStr(m.text)
                ))
            )
        }
        parts += "# 本轮输入(最高优先，整条不被裁；id=来源ID，PARTNER=对方/USER=本人)\n" +
            "<chat>\n" + curLines.joinToString("\n") + "\n</chat>"

        // 6) 当前时间（复用产品块，逐字同格式；两臂都拿到同一 clock）
        val clock = wallClock.trim()
        if (clock.isNotEmpty()) parts += timestampBlock().trim()

        return ChampionPrompt(
            prompt = parts.joinToString("\n\n"),
            sourceAliasMap = sourceAliasMap,
            historyCu = hist.usedCu
        )
    }
}

/**
 * Python `json.dumps(..., ensure_ascii=False)` 等价的序列化入口（实现与转义口径
 * 在 [ContractGuard] 的 MiniJson 里；这里只做转发别名，避免两处转义规则漂移）。
 * prompt 装配（种子/本轮行）与守卫重写**必须**同一套转义，否则对拍逐字节不同。
 */
internal object ChampionJson {
    fun JStr(v: String) = ContractGuard.JStr(v)
    fun JNum(v: String) = ContractGuard.JNum(v)
    fun obj(entries: Map<String, ContractGuard.JVal>) = ContractGuard.JObj(
        LinkedHashMap(entries)
    )
    fun fromOther(o: JsonObject): ContractGuard.JVal {
        val entries = LinkedHashMap<String, ContractGuard.JVal>()
        for ((k, v) in o) {
            entries[k] = when {
                v is kotlinx.serialization.json.JsonNull -> ContractGuard.JNull
                v is kotlinx.serialization.json.JsonPrimitive ->
                    if (v.isString) ContractGuard.JStr(v.content) else ContractGuard.JNum(v.content)
                v is kotlinx.serialization.json.JsonArray ->
                    ContractGuard.JArr(v.map { fromOtherElement(it) }.toMutableList())
                v is JsonObject -> fromOther(v)
                else -> ContractGuard.JNull
            }
        }
        return ContractGuard.JObj(entries)
    }
    private fun fromOtherElement(v: kotlinx.serialization.json.JsonElement): ContractGuard.JVal =
        when (v) {
            is kotlinx.serialization.json.JsonNull -> ContractGuard.JNull
            is kotlinx.serialization.json.JsonPrimitive ->
                if (v.isString) ContractGuard.JStr(v.content) else ContractGuard.JNum(v.content)
            is kotlinx.serialization.json.JsonArray ->
                ContractGuard.JArr(v.map { fromOtherElement(it) }.toMutableList())
            is JsonObject -> fromOther(v)
        }

    fun dumps(v: ContractGuard.JVal): String {
        // ContractGuard.dumps 是实例方法但完全无状态；这里挂一颗共享实例
        return shared.dumps(v)
    }
    private val shared = ContractGuard()
}
