package com.lovebrain.app.domain.champion

import com.lovebrain.app.domain.port.AiGateway
import com.lovebrain.app.domain.port.Clock
import com.lovebrain.app.domain.port.SettingsStorePort
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.ProviderRequestConfig
import com.lovebrain.app.model.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 冠军引擎模式（T4 Kotlin 移植 §3 特征开关）。
 * 默认 [OFF]——不改变任何现有用户行为；每轮按冻结输入判定，轮中不改。
 */
enum class ChampionMode(val raw: String) {
    OFF("OFF"),
    /** 原链路照常出回复；冠军引擎并行生成只写 shadow_log，不 emit 不入 UI。 */
    SHADOW("SHADOW"),
    /** S2：主链路换 ChampionEngine，仅取 response.recommended。 */
    SINGLE_STYLE("SINGLE_STYLE"),
    /** S3：四风格+directions 全接入。 */
    FULL("FULL");

    companion object {
        /** 脏值与未登记一律落 OFF（fail-closed：没写过/写错 = 没要冠军引擎）。 */
        fun fromRaw(s: String?): ChampionMode = entries.firstOrNull { it.raw == s } ?: OFF

        /** 主链路是否需要过守卫（S2/S3；守卫插在 parseReplyResponse 之前）。 */
        fun isMainPathGuarded(m: ChampionMode): Boolean = m == SINGLE_STYLE || m == FULL
    }
}

/**
 * 冠军引擎 facade（T4 Kotlin 移植 §3.2）：
 * ChampionPromptAssembler + ChampionEventStore + ContractGuard + 同一 AiGateway 的组合体。
 *
 * S1 shadow 的语义（规格书 §3.2；事件时机按审计 R2-1 对齐产品正式提交点）：
 * - 原链路完整照走（原 PromptBuilder + 原记忆管线 + 原渲染），用户看到的一切不变；
 * - 原链路请求发出**之后**（成功或失败都算）追加一次冠军生成：同一批输入装配冠军
 *   prompt（**当前窗口只装配不入库**——事件入库唯一入口是 [commitConfirmedTurn]，挂在
 *   产品「下一轮」正式提交点；shadow 回复**永不入库**）→ 过守卫 →
 *   结果与 guardMeta、prompt/history/uc CU、usage、耗时整体写
 *   `champion/shadow_log.jsonl`（含两侧输出与装配差异）；
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
        // 审计 R2-3（模式诚实化）：S2/S3 尚未接线——主请求 prompt 仍是原 PromptBuilder，
        // 枚举里保留了档位名，但**没有可用实现**。读到 SINGLE_STYLE/FULL 一律按 OFF
        // 处理并记日志，不得借未接线的档位宣称冠军主链路可用（W1/R2 显式登记）。
        if (m == ChampionMode.SINGLE_STYLE || m == ChampionMode.FULL) {
            com.lovebrain.app.util.L.w(
                "championEngineMode=$m 未接线（S2/S3 not wired，见 PORT_IDENTITY.json 登记），按 OFF 处理"
            )
            return ChampionMode.OFF
        }
        return m
    }

    /**
     * contactId×story 白名单（规格书 §3.1 `champion_engine_scope`）：
     * 条目格式 `<contactId>|<story>`，`*` = 全体；**空集 = 全体**（规格书原文）。
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

    /** S2/S3 主链路守卫入口：修复原始返回文本的 JSON 契约（零模型调用；审计随 guard 走）。 */
    fun repairAndAudit(rawText: String, requestId: String, contactId: String): String =
        guard.repairAndAudit(
            rawText,
            buildJsonObject {
                put("requestId", requestId)
                put("stage", "main")
                put("contact_id", contactId)
            }
        ).text

    /**
     * GenerationEngine 侧的 shadow 入口：接冻结的 [com.lovebrain.app.model.DialogueMessage]
     * （PARTNER→HER / USER→ME），轮次号取事件库已有最大 turn+1，时间戳按分钟级
     * `yyyy-MM-dd HH:mm` 落（与 Clock 端口同格式）。
     */
    /** shadow 装配侧的角色白名单：非 HER/ME 的输入在装配前就拒（与 store.append 同口径）。 */
    private fun requireRealRoleForShadow(role: String, text: String) {
        check(role == "HER" || role == "ME") {
            "champion shadow 收到非真实消息（role=$role），拒绝装配 text=${text.take(20)}…"
        }
    }

    /** S1 shadow：原链路结果（成功或失败都算）照常 emit；冠军生成 fire-and-forget。 */
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
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(m.timestamp))
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
     * 正式提交点（T4 §1.1 / 审计 R2-1）：把**已确认**的一轮真实消息写入事件库。
     *
     * 调用点 = 产品正式提交历史的同一事务点（`LoveBrainViewModel.nextRound` 里
     * `topicRecorder.record` 成功之后）——不是生成路径。shadow 生成（[shadowTurn]）
     * **只装配不落库**：用户删掉重来、放弃的输入在生成路径上不产生任何持久痕迹；
     * 只有用户点「下一轮」确认提交，这里才入库。
     *
     * [turn] < 0 时按事件库 nextTurn 取（与 shadow 同一取号口径：已确认历史最大 turn+1，
     * 同一轮多次生成/重试取到同一个号）。去重身份 = 每条消息的稳定 [ChampionMessage.msgUid]
     * （审计 F2-1）：同一 pending 轮重试携带同一 msgUid（同一 ChatMessage.id）→ uid 复用
     * 只记一次；不同消息（含同分钟同内容的重复文本）msgUid 不同 → 各成一条事件，永不误合。
     */
    suspend fun commitConfirmedTurn(
        contactId: String,
        story: String,
        messages: List<ChampionMessage>,
        turn: Int = -1
    ): List<ChampionEvent> {
        // 审计 S1-P0-1（OFF 零副作用门禁）：事件入库也必须过「有效模式+作用域」这道门。
        // 有效模式 OFF（默认未开启、脏值、scope 未命中、S2/S3 未接线被 mode() 收敛为 OFF）
        // 时**零 IO**：不读事件库、不分配 uid、不建 champion/events 目录，直接返回空。
        // 当前唯一放行档位 = 已接线的 SHADOW；未来 S2/S3 真正接线后 mode() 自然放行，
        // 本门禁无需再改。调用方（LoveBrainViewModel 提交点）有同一道前置检查——这里是
        // 兜底双保险，保证任何入口都绕不开 OFF 边界。
        if (effectiveMode(contactId, story) == ChampionMode.OFF) return emptyList()
        val key = "${contactId}__${story}"
        val resolvedTurn = if (turn < 0) eventStore.nextTurn(key) else turn
        return eventStore.append(key, messages, resolvedTurn)
    }

    /**
     * 同步执行一轮 shadow（供测试与 [launchShadow] 复用）。
     * 任何异常都收敛为 ok=false 的日志行，绝不向调用方抛。
     * [turn] < 0 时按事件库 nextTurn 取。
     *
     * 审计 R2-1：**本路径不写事件库**。事件入库唯一入口是 [commitConfirmedTurn]
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
            // 当前窗口只装配、不入库（R2-1）；历史 = 已确认事件（不含任何未提交输入）
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
                put("uc_cu", prompt.ucCu)
                put("uc_items", prompt.ucItems.size)
                put("uc_item_texts", JsonArray(prompt.ucItems.map { kotlinx.serialization.json.JsonPrimitive(it.text) }))
                put("source_alias_map", buildJsonObject {
                    prompt.sourceAliasMap.forEach { (k, v) -> put(k, v) }
                })
                put("original_reply", originalReply)
                put("shadow_reply", outcome.text)
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
                    put("original_reply", originalReply)
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
 * PORT_IDENTITY.json（规格书 §3.4 custody 身份对账）：
 * ①移植代码指纹（champion 包源文件 SHA-256 表，移植时按验证过的源文件实测登记）；
 * ②prompt 资产指纹（AssetFingerprints.hashOf 同产品既有机制）；
 * ③arena 侧被验证版本（window_expand_b_norecap_uc_guard 机制文件 SHA-256 表）。
 *
 * 每条 shadow/live 记录携带 [identityJson]；S1→S2、S2→S3 放行前核对
 * 「运行身份 == 评过身份」，不一致即阻断升级并登记（升级通道本身走 custody 注册）。
 */
object ChampionIdentity {

    const val PORT_VERSION = "F2-DEDUP-20261106"

    /**
     * champion 包源文件 SHA-256 表（LF 归一，与 AssetFingerprints.canonical 同口径）。
     * 本表是移植批次的**登记值**——源文件再改动时必须更新版本并重走对拍。
     */
    // 2026-11-06 移植批实测；F2 批更新 ChampionEvent.kt（审计 F2-1 去重身份改 msgUid）实测值
    val CHAMPION_SOURCE_SHA256: Map<String, String> = mapOf(
        "ChampionEvent.kt" to
            "6c0b1d255f6874a5fb27b4870579ebb0b63b89c74769db5cce1c29a64c272e07",
        "EventHistoryAssembler.kt" to
            "78ba82b340667d082cba7f343e34b0da413a1ce5eb713ab3e92165fbb34d7b6a",
        "UncertainBlockDetector.kt" to
            "316009b6c93c21967609e1f52c305296b6eda525218cd149b19352e19ee85f60",
        "ChampionPromptAssembler.kt" to
            "a695a3564b962bbcb0f670110e829f1fd4901ba817ea0d99163814b00a5732ab",
        "ContractGuard.kt" to
            "40916e7e3ac7456722f3e59b15ffd5e14d513f4e5106fcdbbf2ca9a29381995d"
        // ChampionEngine.kt（含本表）不自嵌自hash——其 SHA-256 登记于
        // W1_CHAMPION_ENGINE_IMPL.md §1（移植批次实测值），升级时随 custody 重新登记
    )

    /** arena 被验证候选机制文件 SHA-256 表（LF 归一；2026-11-06 实测登记）。 */
    val ARENA_MECHANISM_SHA256: Map<String, String> = mapOf(
        "mechanism/mechanism.py" to
            "2e2f00e5dae6d5e5e8089b424615b612e436d7a239747c78cfce75839e3171a2",
        "mechanism/contract_guard.py" to
            "c26c68c080838f07e1d006ed020c7a4695c94bddb16dcadadb018a14f116ec74",
        "mechanism/uncertain_rules.py" to
            "6d5ce394619a63a634111530c5453f89a53b9df9db2d308dd8d5c46639a9818b",
        "mechanism/_vendor/output_repair.py" to
            "3d95d42e3f932bf21c8ac9770dc1488203066df28127830482e72f7b401c3be4",
        "mechanism/_vendor/normalize_reply_contract.py" to
            "427fd5a6f845e22ae963cb23617b1c7d8ff1c4e6e2961867d0b6fe18a905e5ba"
    )

    /** 资产指纹（产品 AssetFingerprints 算法，四件 slim 资产按拼装顺序）。 */
    fun assetHash(assembler: ChampionPromptAssembler): String {
        val entries = ChampionPromptAssembler.ChampionAssets.ALL.map {
            it to assembler.promptSource(it)
        }
        return com.lovebrain.app.domain.AssetFingerprints.hashOf(entries)
    }

    fun identityJson(assembler: ChampionPromptAssembler): JsonObject = buildJsonObject {
        put("port_version", PORT_VERSION)
        put("champion_asset_hash", assetHash(assembler))
        put("arena_candidate", "window_expand_b_norecap_uc_guard")
    }

    /** 落盘 PORT_IDENTITY.json（含完整三 hash 表；幂等，每次覆盖为当前身份）。 */
    fun writeIdentityFile(rootDir: File, assembler: ChampionPromptAssembler) {
        rootDir.mkdirs()
        val obj = buildJsonObject {
            put("port_version", PORT_VERSION)
            put("written_at", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
            put("champion_asset_hash", assetHash(assembler))
            put("arena_candidate", "window_expand_b_norecap_uc_guard")
            // 审计 R2-3（模式诚实化）：显式登记接线状态。可用档位只有 OFF/SHADOW；
            // SINGLE_STYLE/FULL 枚举保留但无实现，读到按 OFF 处理并记日志。
            put("wired_modes", JsonArray(listOf(JsonPrimitive("OFF"), JsonPrimitive("SHADOW"))))
            put("s2_s3_status", "NOT_WIRED — 主请求 prompt 未切 ChampionPromptAssembler，" +
                "SINGLE_STYLE/FULL 按 OFF 处理（登记于 W1_CHAMPION_ENGINE_IMPL.md 与 " +
                "R2_CHAMPION_PERSISTENCE_IDENTITY_FIX.md，接线后更新本登记并走 custody 重验）")
            put("event_commit_point", "LoveBrainViewModel.nextRound → commitConfirmedTurn " +
                "（审计 R2-1：shadow 生成路径不写事件库）")
            put("champion_source_sha256", buildJsonObject {
                CHAMPION_SOURCE_SHA256.forEach { (k, v) -> put(k, v) }
            })
            put("arena_mechanism_sha256", buildJsonObject {
                ARENA_MECHANISM_SHA256.forEach { (k, v) -> put(k, v) }
            })
        }
        File(rootDir, "PORT_IDENTITY.json")
            .writeText(Json.encodeToString(JsonObject.serializer(), obj), Charsets.UTF_8)
    }
}
