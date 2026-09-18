package com.lovebrain.app.model

import com.lovebrain.app.domain.StageCatalog
import com.lovebrain.app.util.Jsons
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonNull

/**
 * P0-2/P0-9: 画像更新解析结果分类。
 *
 * - [SUCCESS]：JSON 完整且字段校验通过
 * - [EMPTY]：模型返回空内容
 * - [TRUNCATED]：JSON 不完整（括号未闭合）或 finish_reason=length
 * - [INVALID_JSON]：对象边界完整，但 JSON parser 失败（语法错误）
 * - [INVALID_SCHEMA]：JSON 语法正确，但字段类型/值/阶段不合法
 * - [PROVIDER_ERROR]：API 返回错误或网络异常
 */
enum class ProfileParseStatus {
    SUCCESS,
    EMPTY,
    TRUNCATED,
    INVALID_JSON,
    INVALID_SCHEMA,
    PROVIDER_ERROR
}

/**
 * P0-2: 画像更新解析结果——包含分类状态和 ProfileUpdate payload。
 *
 * 自动自愈逻辑通过 [status] 判断是否需要重试：
 * - [TRUNCATED] → 可重试
 * - [INVALID_JSON] → 可重试（可能因截断导致语法错误）
 * - [PROVIDER_ERROR] → 可重试
 * - [INVALID_SCHEMA] → 不可重试（schema 问题不会因重试改变）
 * - [EMPTY] → 可重试（可能偶发空响应）
 * - [SUCCESS] → 不需重试
 */
data class ProfileParseResult(
    val status: ProfileParseStatus,
    val profileUpdate: ProfileUpdate?,
    val rawContent: String,
    val finishReason: String? = null
) {
    /** 是否值得自动重试 */
    val shouldRetry: Boolean get() = status == ProfileParseStatus.TRUNCATED
        || status == ProfileParseStatus.INVALID_JSON
        || status == ProfileParseStatus.PROVIDER_ERROR
        || status == ProfileParseStatus.EMPTY

    /** 供 UI 展示的摘要（成功时用 profileUpdate.displaySummary，失败时用原始截断） */
    val displaySummary: String get() = profileUpdate?.displaySummary
        ?: rawContent.take(200)
}

/**
 * 统一画像更新解析与校验入口。
 *
 * 生成摘要展示和确认写入均使用同一个已验证的 ProfileUpdate 对象，
 * 不重复解析两遍不同口径的 raw。
 *
 * 校验规则以 [ProfileUpdateSchema] 为单一真源——prompt 和 parser 共用同一套字段定义，
 * 不在别处另行维护。详见 [ProfileUpdateSchema] 类文档。
 *
 * 截断、歧义或字段无效时 [valid]=false，不展示可确认按钮。
 */
data class ProfileUpdate(
    /** 原始 JSON 对象（已通过括号扫描提取） */
    val rawJson: String,
    /** 是否通过完整校验 */
    val valid: Boolean,
    /** 校验失败原因 */
    val error: String?,
    /** 三画像内容（非空字符串或 null 表示不更新） */
    val me: String?,
    val her: String?,
    val warmth: String?,
    /** 阶段是否变化 */
    val stageChanged: Boolean,
    /** 新阶段名 */
    val newStage: String?,
    /** 待验证观察列表 */
    val observations: List<String>,
    /** 给用户的摘要消息 */
    val messageToUser: String?,
    /** 用于展示的摘要文本 */
    val displaySummary: String
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = false }
        private const val PROFILE_FALLBACK_LIMIT = 200

        /**
         * P0-2: 带 finish_reason 的解析入口——返回分类结果 [ProfileParseResult]。
         *
         * 判定顺序：
         * 1. finishReason == "length" → TRUNCATED（即使 content 看似完整也标截断）
         * 2. content 为空 → EMPTY
         * 3. Jsons.extractJsonObject 返回 null（括号不配对）→ TRUNCATED
         * 4. JSON 解析失败 → INVALID_JSON（对象边界完整但语法错误）
         * 5. 字段校验失败 → INVALID_SCHEMA
         * 6. 全通过 → SUCCESS
         */
        fun parseWithStatus(raw: String, finishReason: String? = null): ProfileParseResult {
            // 1. finish_reason=length 明确截断
            if (finishReason == "length") {
                return ProfileParseResult(
                    status = ProfileParseStatus.TRUNCATED,
                    profileUpdate = null,
                    rawContent = raw,
                    finishReason = finishReason
                )
            }

            // 2. 空内容
            if (raw.isBlank()) {
                return ProfileParseResult(
                    status = ProfileParseStatus.EMPTY,
                    profileUpdate = null,
                    rawContent = raw,
                    finishReason = finishReason
                )
            }

            // 3-4. 提取完整 JSON 对象（处理代码围栏、外围文字、字符串内括号等）
            val jsonStr = Jsons.extractJsonObject(raw)
            if (jsonStr == null) {
                // 无法提取完整对象 = 截断
                return ProfileParseResult(
                    status = ProfileParseStatus.TRUNCATED,
                    profileUpdate = ProfileUpdate(
                        rawJson = raw,
                        valid = false,
                        error = "无法提取完整JSON对象（可能被截断或格式不完整）",
                        me = null, her = null, warmth = null,
                        stageChanged = false, newStage = null,
                        observations = emptyList(), messageToUser = null,
                        displaySummary = raw.take(PROFILE_FALLBACK_LIMIT)
                    ),
                    rawContent = raw,
                    finishReason = finishReason
                )
            }

            // 5-6. 走原有 parse 逻辑
            // P0-9: 区分 INVALID_JSON 和 INVALID_SCHEMA
            val profileUpdate = try {
                parseFromJson(jsonStr, raw)
            } catch (e: Exception) {
                // JSON parser 抛异常 → INVALID_JSON
                return ProfileParseResult(
                    status = ProfileParseStatus.INVALID_JSON,
                    profileUpdate = ProfileUpdate(
                        rawJson = jsonStr,
                        valid = false,
                        error = "JSON解析失败：${e.message}",
                        me = null, her = null, warmth = null,
                        stageChanged = false, newStage = null,
                        observations = emptyList(), messageToUser = null,
                        displaySummary = jsonStr.take(PROFILE_FALLBACK_LIMIT)
                    ),
                    rawContent = raw,
                    finishReason = finishReason
                )
            }
            val status = if (profileUpdate.valid) ProfileParseStatus.SUCCESS
                         else ProfileParseStatus.INVALID_SCHEMA

            return ProfileParseResult(
                status = status,
                profileUpdate = profileUpdate,
                rawContent = raw,
                finishReason = finishReason
            )
        }

        /**
         * 从模型原文构建 ProfileUpdate。
         * 接受纯JSON、单个json代码围栏及可明确提取的单个对象。
         * 截断、歧义或字段无效时 valid=false。
         */
        fun parse(raw: String): ProfileUpdate {
            // 步骤1：提取完整 JSON 对象（处理代码围栏、外围说明文字、字符串内括号等）
            val jsonStr = Jsons.extractJsonObject(raw)
                ?: return ProfileUpdate(
                    rawJson = raw,
                    valid = false,
                    error = "无法提取完整JSON对象（可能被截断或格式不完整）",
                    me = null, her = null, warmth = null,
                    stageChanged = false, newStage = null,
                    observations = emptyList(), messageToUser = null,
                    displaySummary = raw.take(PROFILE_FALLBACK_LIMIT)
                )
            // P0-9: parseFromJson 现在 JSON 解析失败时抛异常
            return try {
                parseFromJson(jsonStr, raw)
            } catch (e: Exception) {
                ProfileUpdate(
                    rawJson = jsonStr,
                    valid = false,
                    error = "JSON解析失败：${e.message}",
                    me = null, her = null, warmth = null,
                    stageChanged = false, newStage = null,
                    observations = emptyList(), messageToUser = null,
                    displaySummary = jsonStr.take(PROFILE_FALLBACK_LIMIT)
                )
            }
        }

        /**
         * P0-2/P0-9: 从已提取的 JSON 字符串构建 ProfileUpdate（内部方法）。
         * 调用方已通过 Jsons.extractJsonObject 提取了完整 JSON 对象。
         *
         * P0-9: JSON 解析失败时抛出异常（由 parseWithStatus 捕获并分类为 INVALID_JSON）。
         * 字段校验失败时返回 valid=false（由 parseWithStatus 分类为 INVALID_SCHEMA）。
         */
        private fun parseFromJson(jsonStr: String, @Suppress("UNUSED_PARAMETER") raw: String): ProfileUpdate {
            // P0-9: JSON parser 失败时抛出异常，由调用方区分 INVALID_JSON 和 INVALID_SCHEMA
            val parsed: JsonObject = json.parseToJsonElement(jsonStr).jsonObject

            // 步骤3：逐字段类型校验（按字段不同规则区分）
            val errors = mutableListOf<String>()

            // 画像正文：必须为合法非空字符串
            val meContent = extractNonBlankStringField(parsed, "me", errors)
            val herContent = extractNonBlankStringField(parsed, "her", errors)
            val warmthContent = extractNonBlankStringField(parsed, "warmth", errors)

            val stageChanged = extractBooleanField(parsed, "stage_changed", errors)

            // new_stage：只在 stage_changed=true 时要求合法非空且属于阶段枚举
            val newStage = if (stageChanged == true) {
                extractStageField(parsed, "new_stage", errors)
            } else {
                // stage_changed=false 或缺失：new_stage 允许缺失/空，不校验
                extractOptionalStringField(parsed, "new_stage")
            }

            val observations = extractStringArrayField(parsed, "observations", errors)

            // message_to_user：允许空字符串（reflect 模板无变化时为空）
            val messageToUser = extractOptionalStringField(parsed, "message_to_user")

            // 至少一个画像字段非空才有效
            val hasProfileUpdate = meContent != null || herContent != null || warmthContent != null
            if (!hasProfileUpdate && errors.isEmpty()) {
                errors.add("缺少有效的画像字段（me/her/warmth）")
            }

            val valid = errors.isEmpty()
            val displaySummary = buildDisplaySummary(
                messageToUser, stageChanged, newStage, observations
            )

            return ProfileUpdate(
                rawJson = jsonStr,
                valid = valid,
                error = if (errors.isEmpty()) null else errors.joinToString("; "),
                me = meContent,
                her = herContent,
                warmth = warmthContent,
                stageChanged = stageChanged ?: false,
                newStage = newStage,
                observations = observations,
                messageToUser = messageToUser,
                displaySummary = displaySummary
            )
        }

        /**
         * 提取必填非空字符串字段（画像正文用）。
         * 缺失=不更新（null）；存在但 null/空串/数字/布尔/对象=无效。
         * 严格检查 isString：JsonNull/数字/布尔不被当字符串接受。
         */
        private fun extractNonBlankStringField(
            obj: JsonObject,
            key: String,
            errors: MutableList<String>
        ): String? {
            val element = obj[key] ?: return null // 缺失字段不报错
            return when (element) {
                is JsonPrimitive -> {
                    if (element.isString) {
                        val content = element.content
                        if (content.isBlank()) {
                            errors.add("$key 为空字符串")
                            null
                        } else {
                            content
                        }
                    } else {
                        // 数字、布尔等非字符串 JsonPrimitive
                        errors.add("$key 应为字符串，实际为${element.content}")
                        null
                    }
                }
                is JsonObject -> {
                    errors.add("$key 应为字符串，实际为对象")
                    null
                }
                is JsonArray -> {
                    errors.add("$key 应为字符串，实际为数组")
                    null
                }
                else -> {
                    errors.add("$key 类型异常")
                    null
                }
            }
        }

        /**
         * 提取可选字符串字段（message_to_user 等允许空串的字段用）。
         * 缺失=不设置（null）；存在且为字符串=返回内容（含空串）；
         * 存在但 null/数字/布尔/对象=无效。
         */
        private fun extractOptionalStringField(
            obj: JsonObject,
            key: String
        ): String? {
            val element = obj[key] ?: return null
            return when (element) {
                is JsonPrimitive -> {
                    if (element.isString) element.content else null
                }
                else -> null
            }
        }

        /**
         * 提取阶段字段（stage_changed=true 时使用）。
         * P1-02: 不再另造白名单，统一使用 StageCatalog.normalize 归一化校验。
         * stage_changed=true 但字段缺失/空/非字符串/不在八阶段白名单内均报条件校验错误。
         */
        private fun extractStageField(
            obj: JsonObject,
            key: String,
            errors: MutableList<String>
        ): String? {
            val element = obj[key]
            if (element == null) {
                // P1-02: stage_changed=true 时字段缺失必须报错，不能静默 return null
                errors.add("$key 缺失（stage_changed=true 时必须指定阶段）")
                return null
            }
            return when (element) {
                is JsonPrimitive -> {
                    if (!element.isString) {
                        errors.add("$key 应为字符串，实际为${element.content}")
                        null
                    } else {
                        val content = element.content.trim()
                        if (content.isBlank()) {
                            errors.add("$key 为空字符串（stage_changed=true 时必须指定阶段）")
                            null
                        } else {
                            // P1-02: 统一使用 StageCatalog 归一化，不再用重复白名单
                            val normalized = StageCatalog.normalize(content)
                            if (normalized == null) {
                                errors.add("$key 不是合法阶段：$content（合法阶段见 StageCatalog）")
                                null
                            } else {
                                normalized
                            }
                        }
                    }
                }
                is JsonObject -> {
                    errors.add("$key 应为字符串，实际为对象")
                    null
                }
                is JsonArray -> {
                    errors.add("$key 应为字符串，实际为数组")
                    null
                }
                else -> {
                    errors.add("$key 类型异常")
                    null
                }
            }
        }

        /** 提取布尔字段 */
        private fun extractBooleanField(
            obj: JsonObject,
            key: String,
            errors: MutableList<String>
        ): Boolean? {
            val element = obj[key] ?: return null
            return when (element) {
                is JsonPrimitive -> {
                    try {
                        element.boolean
                    } catch (e: Exception) {
                        errors.add("$key 应为布尔值")
                        null
                    }
                }
                else -> {
                    errors.add("$key 应为布尔值，实际为其他类型")
                    null
                }
            }
        }

        /** 提取字符串数组字段：元素必须为 isString 的 JsonPrimitive，不接受 null/数字/布尔 */
        private fun extractStringArrayField(
            obj: JsonObject,
            key: String,
            errors: MutableList<String>
        ): List<String> {
            val element = obj[key] ?: return emptyList()
            return when (element) {
                is JsonArray -> {
                    element.mapNotNull { item ->
                        when (item) {
                            is JsonPrimitive -> {
                                if (item.isString) item.content else {
                                    errors.add("$key 数组元素应为字符串，实际为${item.content}")
                                    null
                                }
                            }
                            else -> {
                                errors.add("$key 数组元素应为字符串")
                                null
                            }
                        }
                    }
                }
                else -> {
                    errors.add("$key 应为数组，实际为其他类型")
                    emptyList()
                }
            }
        }

        private fun buildDisplaySummary(
            messageToUser: String?,
            stageChanged: Boolean?,
            newStage: String?,
            observations: List<String>
        ): String = buildString {
            if (!messageToUser.isNullOrBlank()) {
                append(messageToUser).append("\n\n")
            }
            if (stageChanged == true && !newStage.isNullOrBlank()) {
                append("【阶段调整建议】→ $newStage\n\n")
            }
            if (observations.isNotEmpty()) {
                append("待验证观察：\n")
                observations.forEach { append("· $it\n") }
            }
        }.trim().ifBlank { "画像更新建议已生成，点击确认写入。" }
    }
}
