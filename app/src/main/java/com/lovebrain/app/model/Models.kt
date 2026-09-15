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

/** 一条回复方案（UI 渲染用；tag/title 硬编码补，AI 只输出 reply 文本） */
@Serializable
data class Scheme(
    val tag: String = "",        // A / B / C / D
    val title: String = "",      // 稳妥 / 直球 / 俏皮 / 温柔（G 批改名：原 推荐/渣男/调皮/暖男）
    val reply: String = ""       // 话术原文
)

/** 新格式 response 块：4 种风格回复（recommended/bad_boy/playful/warm） */
@Serializable
data class ReplySchemes(
    val recommended: String = "",
    @SerialName("bad_boy") val badBoy: String = "",
    val playful: String = "",
    val warm: String = ""
) {
    /** 映射为 UI 用的 4 条 Scheme（过滤空回复） */
    fun toSchemes(): List<Scheme> = listOf(
        Scheme(tag = "A", title = "推荐", reply = recommended),
        Scheme(tag = "B", title = "清醒", reply = badBoy),
        Scheme(tag = "C", title = "俏皮", reply = playful),
        Scheme(tag = "D", title = "温柔", reply = warm)
    ).filter { it.reply.isNotBlank() }
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
