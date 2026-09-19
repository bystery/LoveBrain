package com.lovebrain.app.model

import com.lovebrain.app.domain.StageCatalog

/**
 * 画像更新 JSON Schema 单一真源。
 *
 * Parser、Prompt（normal / strict / compact）、Test 全部引用此对象，
 * 禁止在别处另行维护字段规则文案。
 *
 * P0-8 / P1-4: normal / strict / compact 描述全部由同一套结构化 field spec 生成，
 * 不再手写三段重复字段规则。
 *
 * 字段规则：
 * - me/her/warmth：可选字符串。缺失=本次不更新该项；
 *   存在时必须为非空字符串（空串/数字/布尔/对象/数组=无效）。
 * - stage_changed：可选布尔。缺失=false。
 * - new_stage：stage_changed=true 时必须为合法非空字符串且在 [StageCatalog] 白名单内；
 *   stage_changed=false 或缺失时允许缺失/空。
 * - observations：可选字符串数组。元素必须为字符串。
 * - message_to_user：可选字符串。允许空字符串。
 *
 * 至少一个画像字段（me/her/warmth）非空才视为有效更新。
 */
object ProfileUpdateSchema {

    /** Schema 字段定义——prompt 和 parser 共同引用 */
    const val FIELD_ME = "me"
    const val FIELD_HER = "her"
    const val FIELD_WARMTH = "warmth"
    const val FIELD_STAGE_CHANGED = "stage_changed"
    const val FIELD_NEW_STAGE = "new_stage"
    const val FIELD_OBSERVATIONS = "observations"
    const val FIELD_MESSAGE_TO_USER = "message_to_user"

    /** 所有合法字段名 */
    val ALL_FIELDS = listOf(
        FIELD_ME, FIELD_HER, FIELD_WARMTH,
        FIELD_STAGE_CHANGED, FIELD_NEW_STAGE,
        FIELD_OBSERVATIONS, FIELD_MESSAGE_TO_USER
    )

    /** 合法阶段列表（引用 StageCatalog，不另建白名单） */
    val VALID_STAGES: List<String> get() = StageCatalog.ALL

    // ═══ P1-4: 统一 presence 语义 ═══

    /**
     * 字段存在性语义——替代之前的 required + optional 两个互补 Boolean。
     * - [OPTIONAL]: 可选，缺失=不更新该项
     * - [REQUIRED]: 必填
     * - [CONDITIONAL]: 条件必填——存在性取决于其他字段的值
     */
    enum class Presence {
        OPTIONAL,
        REQUIRED,
        CONDITIONAL
    }

    /** 单个字段的规范描述 */
    private data class FieldSpec(
        val name: String,
        val type: String,          // "字符串", "布尔值", "字符串数组"
        val presence: Presence,   // 存在性语义
        val note: String?,        // 附加说明
        val constraint: String?   // 额外约束
    )

    /** 结构化字段定义——所有 prompt 变体均从此生成 */
    private val fieldSpecs: List<FieldSpec> = listOf(
        FieldSpec(FIELD_ME, "字符串", Presence.OPTIONAL, "更新后的'我的画像'", "存在时必须为非空字符串"),
        FieldSpec(FIELD_HER, "字符串", Presence.OPTIONAL, "更新后的'她的画像'", "存在时必须为非空字符串"),
        FieldSpec(FIELD_WARMTH, "字符串", Presence.OPTIONAL, "更新后的'关系温度描述'", "存在时必须为非空字符串"),
        FieldSpec(FIELD_STAGE_CHANGED, "布尔值", Presence.OPTIONAL, "是否建议调整阶段", "缺失=false"),
        FieldSpec(FIELD_NEW_STAGE, "字符串", Presence.CONDITIONAL, "新阶段名", "stage_changed=true 时必填，合法阶段：${VALID_STAGES.joinToString("/")}"),
        FieldSpec(FIELD_OBSERVATIONS, "字符串数组", Presence.OPTIONAL, "待验证观察", null),
        FieldSpec(FIELD_MESSAGE_TO_USER, "字符串", Presence.OPTIONAL, "给用户的摘要消息", "允许空字符串")
    )

    /** 字段必须满足的跨字段约束 */
    private const val CROSS_FIELD_CONSTRAINT = "me/her/warmth 至少提供一个（不能全部缺失）。缺失字段表示不更新该项，不要传空字符串。"

    /**
     * 从 Presence 生成存在性描述前缀。
     * - OPTIONAL → 可选，缺失=不更新
     * - REQUIRED → 必填
     * - CONDITIONAL → 条件必填
     */
    private fun Presence.toLabel(): String = when (this) {
        Presence.OPTIONAL -> "可选"
        Presence.REQUIRED -> "必填"
        Presence.CONDITIONAL -> "条件必填"
    }

    /**
     * 从 Presence 生成缺失语义说明。
     * - OPTIONAL → 缺失=不更新
     * - REQUIRED → 不可缺失
     * - CONDITIONAL → 条件不满足时可缺失
     */
    private fun Presence.toMissingBehavior(): String? = when (this) {
        Presence.OPTIONAL -> "缺失=不更新"
        Presence.REQUIRED -> "不可缺失"
        Presence.CONDITIONAL -> null
    }

    /** 从 field spec 生成单行描述（normal prompt 用）——统一使用 Presence 语义 */
    private fun FieldSpec.toLine(): String = buildString {
        append("- $name: $type（${presence.toLabel()}")
        if (note != null) {
            append("，$note")
        }
        val missing = presence.toMissingBehavior()
        if (missing != null) {
            append("，$missing")
        }
        if (constraint != null) {
            append("，$constraint")
        }
        append("）")
    }

    /**
     * 供 prompt 使用的 schema 文案——由结构化 field spec 生成。
     */
    fun schemaDescriptionForPrompt(): String = buildString {
        appendLine("JSON 对象字段规则：")
        for (spec in fieldSpecs) {
            appendLine(spec.toLine())
        }
        appendLine()
        appendLine("重要：$CROSS_FIELD_CONSTRAINT")
    }

    /**
     * 供 strict prompt 使用的精简 schema 文案——同一套 field spec + Presence 语义。
     */
    fun strictSchemaForPrompt(): String = buildString {
        appendLine("JSON 对象字段：")
        for (spec in fieldSpecs) {
            append("- ${spec.name}: ${spec.type}（${spec.presence.toLabel()}")
            if (spec.note != null) append("，${spec.note}")
            val missing = spec.presence.toMissingBehavior()
            if (missing != null) append("，$missing")
            if (spec.constraint != null) append("，${spec.constraint}")
            appendLine("）")
        }
        appendLine()
        appendLine("至少提供 me/her/warmth 中的一个。不要用空字符串表示'无更新'——直接省略该字段。")
    }

    /**
     * 供 compact/repair prompt 使用的最小化 schema 文案。
     *
     * P1-4: 不再手写 me/her/warmth/stage_changed/observations 规则。
     * 由同一套 fieldSpecs + CROSS_FIELD_CONSTRAINT 生成。
     */
    fun compactSchemaForPrompt(): String = buildString {
        appendLine("修复要求：")
        appendLine("1. 确保 JSON 语法正确（括号闭合、逗号正确、字符串用双引号）")
        // 由 fieldSpecs + Presence 语义生成字段约束——不再手写
        for (spec in fieldSpecs) {
            val parts = mutableListOf<String>()
            parts.add(spec.type)
            parts.add(spec.presence.toLabel())
            val missing = spec.presence.toMissingBehavior()
            if (missing != null) parts.add(missing)
            if (spec.constraint != null) parts.add(spec.constraint)
            appendLine("${fieldSpecs.indexOf(spec) + 2}. ${spec.name}: ${parts.joinToString("；")}")
        }
        appendLine("${fieldSpecs.size + 2}. $CROSS_FIELD_CONSTRAINT")
        appendLine("${fieldSpecs.size + 3}. 不要使用 Markdown 围栏")
        appendLine("${fieldSpecs.size + 4}. 只输出 JSON 对象本身，不要输出解释文字")
    }
}
