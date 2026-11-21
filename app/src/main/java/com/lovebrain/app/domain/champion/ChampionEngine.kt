package com.lovebrain.app.domain.champion

import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.domain.port.Clock
import com.lovebrain.app.domain.port.SettingsStorePort
import com.lovebrain.app.model.DialogueMessage
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.StreamEvent
import com.lovebrain.app.util.TimeFmt
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * 冠军引擎模式（特征开关）。
 * 默认 [OFF]——不改变任何现有用户行为；每轮按冻结输入判定，轮中不改。
 */
enum class ChampionMode(val raw: String) {
    OFF("OFF"),
    /** 原链路照常出回复；冠军引擎并行生成只写 shadow_log，不 emit 不入 UI。 */
    SHADOW("SHADOW"),
    /** 主链路换 ChampionEngine，仅取 response.recommended。 */
    SINGLE_STYLE("SINGLE_STYLE"),
    /** 四风格 + directions 全接入。 */
    FULL("FULL"),
    /**
     * 主请求前做确定性装配（历史块注入；零模型调用）。
     * 输出契约守卫与轮次完全无关：只要注入了 [ChampionEngine]，原始返回文本在
     * parseReplyResponse 之前一律过 ContractGuard。
     */
    LIVE_AUTO("LIVE_AUTO");

    companion object {
        /** 脏值与未登记一律落 OFF（fail-closed：没写过/写错 = 没要冠军引擎）。 */
        fun fromRaw(s: String?): ChampionMode = entries.firstOrNull { it.raw == s } ?: OFF
    }
}

/**
 * LIVE_AUTO 本轮冻结的确定性装配结果：历史块。零模型调用的产物。
 * 未定/待确认事项块（UC）已整段删除——不保留策略位、不保留 UC 槽。
 */
data class ChampionLiveContext(
    /** 真实历史消息块（3600 CU 整条预算；空串 = 无内容不注入，不出空标题） */
    val historyBlock: String
)

/**
 * 冠军引擎 facade：
 * ChampionPromptAssembler + ChampionEventStore + ContractGuard + 同一 AiGateway 的组合体。
 *
 * SHADOW 的语义（事件时机对齐产品正式提交点）：
 * - 原链路完整照走（原 PromptBuilder + 原记忆管线 + 原渲染），用户看到的一切不变；
 * - 原链路请求发出**之后**（成功或失败都算）追加一次冠军生成：同一批输入装配冠军
 *   prompt（**当前窗口只装配不入库**——事件入库唯一入口是 [commitConfirmedTurn]，挂在
 *   产品「下一轮」正式提交点；shadow 回复**永不入库**）→ 过守卫 →
 *   结果与守卫元信息、prompt/history CU、usage、耗时整体写
 *   `champion/shadow_log.jsonl`（只记长度、变更类型与规则名，不落聊天正文）；
 * - **不 emit 任何 ReplyEvent、不进 UI、不触发 TopicRecorder 的 shadow 侧写入**；
 * - 失败只记日志，绝不影响原链路结果；超时用同一 generateTimeoutMs。
 */
class ChampionEngine(
    private val gateway: AiGateway,
    private val eventStore: ChampionEventStore,
    private val assembler: ChampionPromptAssembler,
    private val guard: ContractGuard,
    private val settings: SettingsStorePort,
    private val clock: Clock,
    /**
     * shadow 的 fire-and-forget 执行口：`(suspend () -> Unit) -> Unit`。
     * 按 SingleOwnerContractTest，domain 不许接 CoroutineScope / 自己 launch——
     * 执行域的所有权归 app 层（di 注入 applicationScope 的同一份），domain 只交任务。
     */
    private val shadowExecutor: (suspend () -> Unit) -> Unit,
    private val rootDir: File
) {

    companion object {
        const val SHADOW_LOG_FILE = "shadow_log.jsonl"
        private val json = Json { encodeDefaults = true }
    }

    fun mode(): ChampionMode {
        val m = ChampionMode.fromRaw(settings.championEngineMode)
        // 模式诚实化：SINGLE_STYLE/FULL 尚未接线——主请求 prompt 仍是原 PromptBuilder，
        // 枚举里保留了档位名，但**没有可用实现**。读到一律按 OFF 处理并记日志，
        // 不得借未接线的档位宣称冠军主链路可用。
        if (m == ChampionMode.SINGLE_STYLE || m == ChampionMode.FULL) {
            com.lovebrain.app.util.L.w("championEngineMode=$m 未接线（not wired），按 OFF 处理")
            return ChampionMode.OFF
        }
        return m
    }

    /**
     * contactId×story 白名单（`champion_engine_scope`）：
     * 条目格式 `<contactId>|<story>`，`*` = 全体；**空集 = 全体**。
     */
    fun scopeAllows(contactId: String, story: String): Boolean {
        val scope = settings.championEngineScope
        if (scope.isEmpty()) return true
        if ("*" in scope) return true
        val key = "$contactId|$story"
        return key in scope
    }

    /** 有效模式：mode 非空且 scope 命中才生效；否则按 OFF（不改变现有行为）。 */
    fun effectiveMode(contactId: String, story: String): ChampionMode {
        val m = mode()
        if (m == ChampionMode.OFF) return ChampionMode.OFF
        return if (scopeAllows(contactId, story)) m else ChampionMode.OFF
    }

    data class ShadowOutcome(
        val ok: Boolean,
        val logLine: JsonObject,
        val error: String? = null
    )

    /**
     * 主链路守卫入口（**无条件**：与轮次/档位/onlyThisRound 均无关）：修复原始返回文本的
     * JSON 契约（零模型调用；审计随 guard 走）。返回完整 [ContractGuard.GuardOutcome]——
     * 调用方据此读取 `meta.directions_padded` 等守卫自述信息（只消费，不改写本方法语义）。
     */
    fun repairAndAudit(rawText: String, requestId: String, contactId: String): ContractGuard.GuardOutcome =
        guard.repairAndAudit(
            rawText,
            buildJsonObject {
                put("requestId", requestId)
                put("stage", "main")
                put("contact_id", contactId)
            }
        )

    /**
     * GenerationEngine 侧的 shadow 入口：接冻结的 [com.lovebrain.app.model.DialogueMessage]
     * （PARTNER→HER / USER→ME），轮次号取事件库已有最大 turn+1，时间戳按分钟级
     * `yyyy-MM-dd HH:mm` 落（与 Clock 端口同格式）。
     */
    /** shadow 装配侧的角色白名单：非 HER/ME 的输入在装配前就拒（与 store.append 同口径）。 */
    private fun requireRealRoleForShadow(role: String, text: String) {
        // 拒绝原因只带角色与长度：聊天正文不进异常路径（异常最终会写进 shadow 日志）。
        check(role == "HER" || role == "ME") {
            "champion shadow 收到非真实消息（role=$role, len=${text.length}），拒绝装配"
        }
    }

    /** shadow：原链路结果（成功或失败都算）照常 emit；冠军生成 fire-and-forget。 */
    fun launchShadow(
        requestId: String,
        contactId: String,
        story: String,
        dialogue: List<com.lovebrain.app.model.DialogueMessage>,
        intent: IntentConfig,
        advisorNote: String,
        originalReply: String,
        timeoutMs: Long,
        providerConfig: com.lovebrain.app.model.ProviderRequestConfig?,
        seed: ChampionPromptAssembler.ChampionSeed = ChampionPromptAssembler.ChampionSeed()
    ) {
        val msgs = dialogue.map { m ->
            Triple(
                if (m.speaker == com.lovebrain.app.model.DialogueSpeaker.PARTNER) "HER" else "ME",
                m.text,
                // 每条 ts 会进影子 prompt 的 `ts` 字段：设备语言是 th_TH 时 SimpleDateFormat
                // 会给出佛历年份（2569），同一份输入在两台设备上拼出两份 prompt。
                TimeFmt.format(m.timestamp)
            )
        }
        launchShadow(
            requestId = requestId,
            contactId = contactId,
            story = story,
            turn = -1,   // 由内部再取 nextTurn
            messages = msgs,
            intent = intent,
            advisorNote = advisorNote,
            originalReply = originalReply,
            timeoutMs = timeoutMs,
            providerConfig = providerConfig,
            seed = seed
        )
    }

    /**
     * LIVE_AUTO 本轮确定性装配（零模型调用，不启动协程/SHADOW）。
     *
     * 职责：读唯一 eventStore → [EventHistoryAssembler.budgetHistory] 装配 3600 CU
     * 完整历史 → 当前轮消息只用于按 msgUid 排除、**不 append/不落盘/不入 shadow_log**
     * （事件入库唯一入口仍是 [commitConfirmedTurn]）→ 返回历史块。
     * 未定/待确认事项块与分域策略已整段删除（用户口径：未定块完全不要）。
     *
     * [currentTurn] 是**请求轮次口径**（committedTurnCount + 1）——与
     * PromptBuilder.selectOngoingForInjection 的同名参数（已提交轮数原值，selector 冷却口径）
     * 语义不同，禁止互传。方法内不读设置/时钟（全部状态由调用方冻结传入）。
     *
     * priorEvents = events(key).filter { msgUid !in 当前轮 dialogue 的 id 集 }——当前轮
     * 临时消息按 msgUid（= ChatMessage.id）排除，防重复生成边缘重计。
     * 异常不吞（由 GenerationEngine runCatching 兜底，CancellationException 原样上抛）。
     */
    suspend fun prepareLiveContext(
        contactId: String,
        story: String,
        currentTurn: Int,
        dialogue: List<DialogueMessage>
    ): ChampionLiveContext? {
        val key = "${contactId}__${story}"
        val committed = eventStore.events(key)
        val currentMsgUids = dialogue.mapTo(HashSet()) { it.id }
        val priorEvents = committed.filter { it.msgUid !in currentMsgUids }
        // 历史块：3600 CU 整条预算；body 为空时 historicalBlock 恒为空串（无内容不出空标题）
        val hist = EventHistoryAssembler.budgetHistory(priorEvents, EventHistoryAssembler.MAX_EVENT_HISTORY_CU)
        val historyBlock = if (hist.body.isEmpty()) ""
        else "# 【真实历史消息：仅实际发送；模型回复是草稿，不在此列】\n" + hist.body
        return ChampionLiveContext(
            historyBlock = historyBlock
        )
    }

    /**
     * 正式提交点：把**已确认**的一轮真实消息写入事件库。
     *
     * 调用点 = 产品正式提交历史的同一事务点（`LoveBrainViewModel.nextRound` 里
     * `topicRecorder.record` 成功之后）——不是生成路径。shadow 生成（[shadowTurn]）
     * **只装配不落库**：用户删掉重来、放弃的输入在生成路径上不产生任何持久痕迹；
     * 只有用户点「下一轮」确认提交，这里才入库。
     *
     * [modeSnapshot]：生产链路传本轮冻结的 Champion 模式快照
     * （ReplyGenerationContext.championMode = effectiveMode().raw）。非 null 时门禁 =
     * fromRaw(modeSnapshot) 经 [scopeAllows] 判定，**不再回读设置**——生成中途改模式
     * 不影响本轮提交门禁。null（旧调用方/旧测试）→ 原样走现行 [effectiveMode] 门禁。
     * OFF（含脏值 fail-closed）→ emptyList() 零 IO。
     *
     * [turn] < 0 时按事件库 nextTurn 取（与 shadow 同一取号口径：已确认历史最大 turn+1，
     * 同一轮多次生成/重试取到同一个号）；生产链路传 topicRecorder.record 成功后的
     * knowledgeRepo.getTurnCount(kbName)，读失败传 -1 走 nextTurn 兜底。去重身份 =
     * 每条消息的稳定 [ChampionMessage.msgUid]：同一 pending 轮重试携带同一
     * msgUid（同一 ChatMessage.id）→ uid 复用只记一次；不同消息（含同分钟同内容的重复文本）
     * msgUid 不同 → 各成一条事件，永不误合。
     */
    suspend fun commitConfirmedTurn(
        contactId: String,
        story: String,
        messages: List<ChampionMessage>,
        modeSnapshot: String? = null,
        turn: Int = -1
    ): List<ChampionEvent> {
        // OFF 零副作用门禁：事件入库也必须过「有效模式+作用域」这道门。
        // 有效模式 OFF（默认未开启、脏值、scope 未命中、SINGLE_STYLE/FULL 未接线被 mode()
        // 收敛为 OFF）时**零 IO**：不读事件库、不分配 uid、不建 champion/events 目录，直接返回空。
        // modeSnapshot != null（生产链路冻结快照）时按快照判定，不回读设置；OFF/脏值/
        // scope 未命中同样零 IO。调用方（LoveBrainViewModel 提交点）有同一道前置检查——
        // 这里是兜底双保险，保证任何入口都绕不开 OFF 边界。
        val allowed = if (modeSnapshot != null) {
            ChampionMode.fromRaw(modeSnapshot) != ChampionMode.OFF && scopeAllows(contactId, story)
        } else {
            effectiveMode(contactId, story) != ChampionMode.OFF
        }
        if (!allowed) return emptyList()
        val key = "${contactId}__${story}"
        val resolvedTurn = if (turn < 0) eventStore.nextTurn(key) else turn
        return eventStore.append(key, messages, resolvedTurn)
    }

    /**
     * 同步执行一轮 shadow（供测试与 [launchShadow] 复用）。
     * 任何异常都收敛为 ok=false 的日志行，绝不向调用方抛。
     * [turn] < 0 时按事件库 nextTurn 取。
     *
     * **本路径不写事件库**。事件入库唯一入口是 [commitConfirmedTurn]
     * （产品「下一轮」正式提交点）。当前窗口消息以未持久化的 pending 事件参与装配：
     * pending uid = 已入库最大 uid + 1 + 序号，与随后 [commitConfirmedTurn] 真正入库时
     * `uidSeq` 分配到的 uid 一致（store 不追加时 uidSeq == maxUid+1；窗口全是新消息时
     * 按序号一一对应。若窗口里含已提交过的消息，正式入库按 msgUid 复用其旧 uid，
     * pending 序号 uid 与之偏离——shadow 日志的 source_id 只对全新窗口承诺对齐），
     * 放弃的输入则不留任何痕迹。
     */
    suspend fun shadowTurn(
        requestId: String,
        contactId: String,
        story: String,
        turn: Int,
        messages: List<Triple<String, String, String>>,   // (HER|ME, text, ts)
        intent: IntentConfig,
        advisorNote: String,
        originalReply: String,
        timeoutMs: Long,
        providerConfig: ProviderRequestConfig?,
        seed: ChampionPromptAssembler.ChampionSeed = ChampionPromptAssembler.ChampionSeed()
    ): ShadowOutcome {
        val t0 = clock.epochMs()
        val wallClock = clock.wallClock()
        try {
            val key = "${contactId}__${story}"
            val resolvedTurn = if (turn < 0) eventStore.nextTurn(key) else turn
            // 当前窗口只装配、不入库；历史 = 已确认事件（不含任何未提交输入）
            val committed = eventStore.events(key)
            val pendingBase = (committed.maxOfOrNull { it.uid } ?: -1L) + 1L
            val cur = messages.mapIndexed { i, (role, text, ts) ->
                requireRealRoleForShadow(role, text)
                ChampionEvent(uid = pendingBase + i, role = role, text = text, ts = ts, turn = resolvedTurn)
            }
            val prompt = assembler.assemble(
                events = committed,
                curWindow = cur,
                turn = resolvedTurn,
                intent = intent,
                advisorNote = advisorNote,
                wallClock = wallClock,
                seed = seed
            )
            val system = assembler.buildSystemPrompt()
            val generated = collect(
                gateway.generateStream(system, prompt.prompt, config = providerConfig),
                timeoutMs
            )
            val outcome = guard.repairAndAudit(
                generated.text,
                buildJsonObject {
                    put("requestId", requestId)
                    put("stage", "shadow")
                    put("turn", resolvedTurn)
                }
            )
            val elapsed = clock.epochMs() - t0
            val log = buildJsonObject {
                put("ts", wallClock)
                put("requestId", requestId)
                put("contact_id", contactId)
                put("story", story)
                put("mode", ChampionMode.SHADOW.raw)
                put("turn", resolvedTurn)
                put("prompt_chars", prompt.prompt.length)
                put("history_cu", prompt.historyCu)
                put("source_alias_map", buildJsonObject {
                    prompt.sourceAliasMap.forEach { (k, v) -> put(k, v) }
                })
                // 隐私面最小化：两侧回复正文一律不落日志，只留字符量与守卫规则名。
                put("original_chars", originalReply.length)
                put("shadow_chars", outcome.text.length)
                put("guard_changed", outcome.changed)
                put("guard_meta", outcome.meta)
                put("generate_ok", generated.ok)
                put("generate_error", generated.error ?: "")
                put("prompt_tokens", generated.promptTokens)
                put("completion_tokens", generated.completionTokens)
                put("elapsed_ms", elapsed)
                put("identity", ChampionIdentity.identityJson(assembler))
            }
            return ShadowOutcome(ok = generated.ok, logLine = log, error = generated.error)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return ShadowOutcome(
                ok = false,
                logLine = buildJsonObject {
                    put("ts", wallClock)
                    put("requestId", requestId)
                    put("contact_id", contactId)
                    put("story", story)
                    put("mode", ChampionMode.SHADOW.raw)
                    put("turn", if (turn < 0) -1 else turn)
                    put("original_chars", originalReply.length)
                    put("shadow_error", e.message ?: e.javaClass.simpleName)
                    put("elapsed_ms", clock.epochMs() - t0)
                },
                error = e.message
            )
        }
    }

    /** fire-and-forget：结果与失败都只落 shadow_log，绝不向上传播。 */
    fun launchShadow(
        requestId: String,
        contactId: String,
        story: String,
        turn: Int,
        messages: List<Triple<String, String, String>>,
        intent: IntentConfig,
        advisorNote: String,
        originalReply: String,
        timeoutMs: Long,
        providerConfig: ProviderRequestConfig?,
        seed: ChampionPromptAssembler.ChampionSeed = ChampionPromptAssembler.ChampionSeed()
    ) {
        shadowExecutor {
            runCatching { ChampionIdentity.writeIdentityFile(rootDir, assembler) }
            val outcome = shadowTurn(
                requestId, contactId, story, turn, messages, intent,
                advisorNote, originalReply, timeoutMs, providerConfig, seed
            )
            runCatching { appendShadowLog(outcome.logLine) }
        }
    }

    /** shadow_log 追加（jsonl，一行一轮；落盘失败不影响生成链路）。 */
    fun appendShadowLog(line: JsonObject) {
        rootDir.mkdirs()
        // 行内容由 shadowTurn 生成时已去正文（只有长度/规则名），此处不再二次加工。
        File(rootDir, SHADOW_LOG_FILE).appendText(json.encodeToString(JsonObject.serializer(), line) + "\n")
    }

    private data class Collected(
        val text: String,
        val ok: Boolean,
        val error: String?,
        val promptTokens: Int?,
        val completionTokens: Int?
    )

    private suspend fun collect(flow: Flow<StreamEvent>, timeoutMs: Long): Collected {
        val buf = StringBuilder()
        var full = ""
        var error: String? = null
        var promptTokens: Int? = null
        var completionTokens: Int? = null
        kotlinx.coroutines.withTimeout(timeoutMs) {
            flow.collect { e ->
                when (e) {
                    is StreamEvent.Chunk -> buf.append(e.text)
                    is StreamEvent.Complete -> {
                        full = e.fullText
                        e.usage?.let {
                            promptTokens = it.promptTokens
                            completionTokens = it.completionTokens
                        }
                    }
                    is StreamEvent.Error -> {
                        error = e.failure.message
                        if (e.partialText.isNotBlank()) full = e.partialText
                    }
                }
            }
        }
        val text = full.ifBlank { buf.toString() }
        return Collected(text, error == null && text.isNotBlank(), error, promptTokens, completionTokens)
    }
}

/**
 * 运行时身份登记（[ChampionIdentity.PORT_IDENTITY_FILE] 与每条 shadow 记录共用一份字段）：
 * ①prompt 资产指纹（AssetFingerprints.hashOf，产品既有机制；源码不含任何预置指纹值）；
 * ②冠军候选口径（单一生产候选）、装配接线状态、历史预算与提交点。
 *
 * 每条 shadow/live 记录携带 [identityJson]，落盘身份文件与之同源，
 * 便于对照「运行配置 == 评估配置」。字段口径变动时递增 [PORT_VERSION]。
 */
object ChampionIdentity {

    /** 身份登记格式版本：字段/口径变动时递增。 */
    const val PORT_VERSION = "1.0"

    /** 生产冠军候选名：全仓唯一常量，所有登记处都引用它（没有分域或历史候选）。 */
    const val ARENA_CANDIDATE = "window_expand_b_norecap_guard"

    const val PORT_IDENTITY_FILE = "PORT_IDENTITY.json"

    /** 资产指纹（产品 AssetFingerprints 算法，四件 slim 资产按拼装顺序）。 */
    fun assetHash(assembler: ChampionPromptAssembler): String {
        val entries = ChampionPromptAssembler.ChampionAssets.ALL.map {
            it to assembler.promptSource(it)
        }
        return com.lovebrain.app.domain.AssetFingerprints.hashOf(entries)
    }

    private const val LIVE_PROMPT_SOURCE = "LoveBrain PromptBuilder"

    /** 候选口径与轮次无关：每条回复都过契约守卫，无未定块。 */
    private const val LIVE_CANDIDATE_NOTE = "turn-independent: contract guard on every reply"

    const val HISTORY_BUDGET_CU = 3600
    private const val ONLY_THIS_ROUND = "BYPASS_CHAMPION_CONTEXT"

    fun identityJson(assembler: ChampionPromptAssembler): JsonObject = buildJsonObject {
        put("port_version", PORT_VERSION)
        put("champion_asset_hash", assetHash(assembler))
        put("arena_candidate", ARENA_CANDIDATE)
        put("live_candidate", "$ARENA_CANDIDATE ($LIVE_CANDIDATE_NOTE)")
        put("default_mode", "LIVE_AUTO")
        put("wired_modes", JsonArray(listOf(
            JsonPrimitive("OFF"), JsonPrimitive("SHADOW"), JsonPrimitive("LIVE_AUTO"))))
        put("live_prompt_source", LIVE_PROMPT_SOURCE)
        put("history_budget_cu", HISTORY_BUDGET_CU)
        put("history_commit_point", "LoveBrainViewModel.nextRound -> commitConfirmedTurn")
        put("only_this_round", ONLY_THIS_ROUND)
        // 模式诚实化：SINGLE_STYLE/FULL 枚举保留但无实现，读到按 OFF 处理并记日志。
        put("unwired_modes", "SINGLE_STYLE/FULL: NOT_WIRED — 按 OFF 处理")
    }

    /** 落盘身份文件（与 [identityJson] 同源；幂等，每次覆盖为当前身份）。 */
    fun writeIdentityFile(rootDir: File, assembler: ChampionPromptAssembler) {
        rootDir.mkdirs()
        val obj = buildJsonObject {
            put("written_at", TimeFmt.now())
            identityJson(assembler).forEach { (k, v) -> put(k, v) }
        }
        File(rootDir, PORT_IDENTITY_FILE)
            .writeText(Json.encodeToString(JsonObject.serializer(), obj), Charsets.UTF_8)
    }
}
