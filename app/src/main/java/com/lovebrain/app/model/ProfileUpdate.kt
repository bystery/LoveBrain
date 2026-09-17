package com.lovebrain.app.model

import com.lovebrain.app.util.Jsons
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 统一画像更新解析与校验入口。
 *
 * 生成摘要展示和确认写入均使用同一个已验证的 ProfileUpdate 对象，
 * 不重复解析两遍不同口径的 raw。
 *
 * 校验规则：
 * - me/her/warmth 为合法非空字符串（可缺失但不接受 null/空串/对象）
 * - stage_changed 为布尔（可缺失）
 * - new_stage 为字符串（可缺失）
 * - observations 为字符串数组（可缺失，元素必须为字符串）
 * - message_to_user 为字符串（可缺失）
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

            // 步骤2：解析 JSON
            val parsed: JsonObject = try {
                json.parseToJsonElement(jsonStr).jsonObject
            } catch (e: Exception) {
                return ProfileUpdate(
                    rawJson = jsonStr,
                    valid = false,
                    error = "JSON解析失败：${e.message}",
                    me = null, her = null, warmth = null,
                    stageChanged = false, newStage = null,
                    observations = emptyList(), messageToUser = null,
                    displaySummary = jsonStr.take(PROFILE_FALLBACK_LIMIT)
                )
            }

            // 步骤3：逐字段类型校验
            val errors = mutableListOf<String>()

            val meContent = extractStringField(parsed, "me", errors)
            val herContent = extractStringField(parsed, "her", errors)
            val warmthContent = extractStringField(parsed, "warmth", errors)

            val stageChanged = extractBooleanField(parsed, "stage_changed", errors)
            val newStage = extractStringField(parsed, "new_stage", errors)

            val observations = extractStringArrayField(parsed, "observations", errors)
            val messageToUser = extractStringField(parsed, "message_to_user", errors)

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

        /** 提取字符串字段，null/空串/对象/数组均记入 errors */
        private fun extractStringField(
            obj: JsonObject,
            key: String,
            errors: MutableList<String>
        ): String? {
            val element = obj[key] ?: return null // 缺失字段不报错
            return when (element) {
                is JsonPrimitive -> {
                    val content = element.content
                    if (content.isBlank()) {
                        errors.add("$key 为空字符串")
                        null
                    } else {
                        content
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

        /** 提取字符串数组字段 */
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
                            is JsonPrimitive -> item.content
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
