package com.lovebrain.app.model

import com.lovebrain.app.domain.StageCatalog

/**
 * 画像更新 JSON Schema 单一真源。
 *
 * Parser、Prompt（normal / strict / compact）、Test 全部引用此对象，
 * 禁止在别处另行维护字段规则文案。
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

    /**
     * 供 prompt 使用的 schema 文案——所有 prompt 引用此方法，不自行编造。
     *
     * 明确描述：
     * - me/her/warmth 可选，存在时必须非空
     * - 缺失字段 = 不更新该项
     */
    fun schemaDescriptionForPrompt(): String = buildString {
        appendLine("JSON 对象字段规则：")
        appendLine("- me: 字符串（可选）。更新后的'我的画像'。缺失=不更新。存在时必须为非空字符串。")
        appendLine("- her: 字符串（可选）。更新后的'她的画像'。缺失=不更新。存在时必须为非空字符串。")
        appendLine("- warmth: 字符串（可选）。更新后的'关系温度描述'。缺失=不更新。存在时必须为非空字符串。")
        appendLine("- stage_changed: 布尔值（可选，缺失=false）。是否建议调整阶段。")
        appendLine("- new_stage: 字符串。stage_changed=true 时必填，必须为合法阶段名：${VALID_STAGES.joinToString("/")}。")
        appendLine("- observations: 字符串数组（可选）。待验证观察。")
        appendLine("- message_to_user: 字符串（可选）。给用户的摘要消息，允许空字符串。")
        appendLine()
        appendLine("重要：me/her/warmth 至少提供一个（不能全部缺失）。缺失字段表示不更新该项，不要传空字符串。")
    }

    /**
     * 供 strict prompt 使用的精简 schema 文案。
     */
    fun strictSchemaForPrompt(): String = buildString {
        appendLine("JSON 对象字段：")
        appendLine("- me: 非空字符串（可选，缺失=不更新）")
        appendLine("- her: 非空字符串（可选，缺失=不更新）")
        appendLine("- warmth: 非空字符串（可选，缺失=不更新）")
        appendLine("- stage_changed: 布尔值（可选，缺失=false）")
        appendLine("- new_stage: 字符串（stage_changed=true 时必填，合法阶段：${VALID_STAGES.joinToString("/")}）")
        appendLine("- observations: 字符串数组（可选）")
        appendLine("- message_to_user: 字符串（可选，允许空串）")
        appendLine()
        appendLine("至少提供 me/her/warmth 中的一个。不要用空字符串表示'无更新'——直接省略该字段。")
    }

    /**
     * 供 compact/repair prompt 使用的最小化 schema 文案。
     *
     * P0-4: 不再设计“伪合法 fallback JSON”。
     * compact schema 只描述真正合法的画像更新——me/her/warmth 至少提供一个。
     * 达到最大重试次数仍失败时，由 Kotlin/Coordinator 产生 typed failure，
     * 不要求 AI 伪造画像字段来满足 parser。
     */
    fun compactSchemaForPrompt(): String = buildString {
        appendLine("修复要求：")
        appendLine("1. 确保 JSON 语法正确（括号闭合、逗号正确、字符串用双引号）")
        appendLine("2. me/her/warmth 存在时必须为非空字符串；不需要更新的字段直接省略")
        appendLine("3. me/her/warmth 至少提供一个（不能全部省略）")
        appendLine("4. stage_changed 必须是布尔值")
        appendLine("5. observations 必须是字符串数组")
        appendLine("6. 不要使用 Markdown 围栏")
        appendLine("7. 不要输出解释文字")
        appendLine()
        appendLine("如果确实无法生成合法的画像更新，直接输出空字符串——")
        appendLine("系统会将其判定为生成失败，不会强制要求你伪造字段。")
    }
}
