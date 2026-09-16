package com.lovebrain.app.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 一条回复方案（UI 渲染用；tag/title 硬编码补，AI 只输出 reply 文本）
 * F09-7: reply 为空表示该方向本轮不适合，UI 显示"本轮不适合"且不可复制。 */
@Serializable
data class Scheme(
    val tag: String = "",        // A / B / C / D
    val title: String = "",      // 稳妥 / 直球 / 俏皮 / 温柔（G 批改名：原 推荐/渣男/调皮/暖男）
    val reply: String = ""       // 话术原文；空 = 本轮不适合
)

/** 新格式 response 块：4 种风格回复（recommended/bad_boy/playful/warm）
 * F09-7: toSchemes 不再过滤空回复，固定返回 4 条（空 reply = 本轮不适合） */
@Serializable
data class ReplySchemes(
    val recommended: String = "",
    @SerialName("bad_boy") val badBoy: String = "",
    val playful: String = "",
    val warm: String = ""
) {
    /** 映射为 UI 用的 4 条 Scheme（F09-7: 不再过滤空回复，固定四个方向） */
    fun toSchemes(): List<Scheme> = listOf(
        Scheme(tag = "A", title = "推荐", reply = recommended),
        Scheme(tag = "B", title = "清醒", reply = badBoy),
        Scheme(tag = "C", title = "俏皮", reply = playful),
        Scheme(tag = "D", title = "温柔", reply = warm)
    )
}

/** 进行中事项（长持续时间话题追踪） */
@Serializable
data class OngoingItem(
    @SerialName("item") val name: String = "",  // 新格式字段名为 item；内部仍用 name
    val status: String = "",    // 新出现 / 进行中 / 已完成 / 已取消
    val state: String = ""      // 本轮最新状态节点（时间戳由代码打）
)

/** 场景事实（带来源关联）
 * F03: 每条 scene fact 必须关联自己的真实 sourceMessageIds，
 * 而不是只给整个 round 一个非空来源集合。
 * sourceIds 引用本轮 HER/ME 消息的 id；客户端逐条校验。
 *
 * 兼容旧格式：AI 可能返回纯字符串列表 `["事实1", "事实2"]`，
 * 此时 sourceIds 为空（标记为未核实来源）。 */
@Serializable(with = SceneFactSerializer::class)
data class SceneFact(
    val text: String = "",
    @SerialName("source_ids") val sourceIds: List<String> = emptyList()
)

/** F03: 自定义序列化器，兼容旧格式纯字符串和新格式带来源对象。
 * 反序列化时接受：
 * - "纯字符串" → SceneFact(text=..., sourceIds=[])
 * - {"text":"...","source_ids":[...]} → 完整 SceneFact
 * 序列化时始终输出对象格式。 */
object SceneFactSerializer : KSerializer<SceneFact> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SceneFact", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SceneFact) {
        // 始终序列化为对象格式
        val obj = buildJsonObject {
            put("text", value.text)
            if (value.sourceIds.isNotEmpty()) {
                put("source_ids", buildJsonArray {
                    value.sourceIds.forEach { add(it) }
                })
            }
        }
        encoder.encodeSerializableValue(JsonElement.serializer(), obj)
    }

    override fun deserialize(decoder: Decoder): SceneFact {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return SceneFact(text = decoder.decodeString())
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> SceneFact(text = element.content)
            is JsonObject -> {
                val text = element["text"]?.jsonPrimitive?.content ?: ""
                val sourceIds = element["source_ids"]?.let { srcEl ->
                    if (srcEl is JsonArray) srcEl.mapNotNull { id ->
                        (id as? JsonPrimitive)?.content
                    } else emptyList()
                } ?: emptyList()
                SceneFact(text = text, sourceIds = sourceIds)
            }
            else -> SceneFact()
        }
    }
}

/** 新格式 analysis 块（话题/场景/事项记录，不含展示型分析）
 * F03: scene_facts 改为 List<SceneFact>，每条携带来源 ID。
 * 兼容旧格式：如果 AI 返回纯字符串列表，sourceIds 为空（标记为未核实）。 */
@Serializable
data class ReplyAnalysis(
    val topic_status: String = "same",   // same / drift / new
    val topic_label: String = "",        // 当前话题标签+场景状态
    val scene_facts: List<SceneFact> = emptyList(),  // 当前场景关键事实（带来源）
    val ongoing: List<OngoingItem> = emptyList()  // 进行中事项（只报本轮有变化的）
)

/** DeepSeek 返回的完整结构（单次调用：response + analysis） */
@Serializable
data class LoveBrainResponse(
    val response: ReplySchemes = ReplySchemes(),
    val analysis: ReplyAnalysis = ReplyAnalysis()
) {
    /** UI 兼容访问器：4 条方案 */
    val schemes: List<Scheme> get() = response.toSchemes()
}

/** 面板状态机 */
enum class PanelState {
    KEYBOARD,          // S1 标准键盘
    AI_LOADING,    // S3 生成中
    AI_RESULT      // S4 方案展示
}

/** 生成结果（成功或失败） */
sealed class GenerateResult {
    data class Success(val response: LoveBrainResponse) : GenerateResult()
    data class Error(val message: String) : GenerateResult()
}

/** 流式生成事件 */
sealed class StreamEvent {
    /** 增量文本块 */
    data class Chunk(val text: String) : StreamEvent()
    /** 流完成，附带完整累积文本 */
    data class Complete(val fullText: String) : StreamEvent()
    /** 错误（含已累积的部分文本） */
    data class Error(val message: String, val partialText: String) : StreamEvent()
}

/** 用户对方案的反馈 */
enum class SchemeFeedback {
    NONE, LIKED, DISLIKED
}

/** 主动发起/润色：单条可直接发送的开场 */
@Serializable
data class ProactiveOption(
    val text: String = "",   // 可直接复制发送的消息
    val angle: String = ""   // 切入角度，一句话
)

/** F07: 持续意图配置（每个知识库一份，存储于 moment/intent.json）
 *
 * 默认关闭（enabled=false），无预设恋爱目标。
 * 跨面板关闭、重启和正常下一轮保留；关闭时保留最后文本但不注入。
 * revision 在每次保存时递增，用于生成时冻结快照识别旧请求。 */
@Serializable
data class IntentConfig(
    val text: String = "",       // 自由文本意图
    val enabled: Boolean = false, // 默认关闭
    val revision: Int = 0        // 每次保存递增，用于快照识别
)

// ═══════════ F09: 本轮参考与记忆可纠正 ═══════════

/** F09: 记忆引用类型 — 画像/场景/事项/经验 */
enum class MemoryKind {
    PROFILE,    // 画像（me/her/warmth）
    SCENE,      // 场景事实（scene.md）
    ONGOING,    // 进行中事项（plan.md）
    LESSON      // 经验教训（lessons.md）
}

/** F09: 记忆引用 — 来自实际注入 Prompt 的裁剪后记忆片段。
 *
 * 每条 MemoryRef 对应最终 prompt 中实际注入的一段知识库内容。
 * 用户可基于此发起纠正操作；纠正绑定 memoryId 参与下一次 PromptBuilder 过滤。
 * unknown legacy 内容整段引用，不伪装为精确事实。 */
@Serializable
data class MemoryRef(
    val id: String,                  // 稳定唯一标识（kind+sourcePath+内容hash前8位）
    val kbId: String,                // 所属知识库
    val kind: MemoryKind,            // 画像/场景/事项/经验
    val text: String,                // 实际注入的文本片段
    val sourcePath: String,          // 源文件相对路径（如 understand/me.md）
    val sourceIds: List<String> = emptyList(),  // 关联的消息 ID（场景事实）
    val evidenceTime: String = "",   // 证据时间戳（场景链条目时间）
    val revision: Int = 0            // 生成时快照 revision（防迟到覆盖）
)

/** F09: 纠正操作类型 */
enum class CorrectionAction {
    WRONG,          // 这条不对 — 停止可信注入，允许补正确内容
    FINISHED,       // 这件事结束了 — 退出活跃事项、保留历史（只对事项可用）
    MUTED,          // 暂时别再提 — 保留事实，不作为主动续聊素材
    WRONG_PERSON    // 这不是她 — 先隔离，选目标库后再迁移
}

/** F09: 人工纠正记录 — 持久化于 memory/corrections.json
 *
 * 每条记录绑定 memoryId，参与下一次 PromptBuilder 过滤。
 * 所有操作可撤销（删除记录即恢复）、重启有效、不调用模型。
 * revision 在每次保存时递增，后台旧任务不能覆盖新 revision。 */
@Serializable
data class MemoryCorrection(
    val memoryId: String,            // 对应 MemoryRef.id
    val action: CorrectionAction,    // 纠正类型
    val replacementText: String = "",// WRONG 时的补正内容
    val targetKbId: String = "",     // WRONG_PERSON 时的目标库
    val revision: Int = 0,           // 保存时递增
    val updatedAt: String = ""       // 时间戳
)
