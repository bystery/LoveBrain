package com.lovebrain.app.model

/**
 * DRY: 单条改写操作选项的唯一定义。
 * UI 文案与 VM 指令映射共用此枚举，不在各处重复 listOf 字符串。
 *
 * [label] 是用户可见文案，[instruction] 是发给模型的改写指令关键词。
 *
 * 扩展预设选项——更短、更像我、别反问、更直接、更温柔。
 * 保留原有换一种说法/更自然。新增自定义改写支持（CUSTOM）。
 */
enum class RewriteCommand(val label: String, val instruction: String) {
    REPHRASE("换一种说法", "换一种说法"),
    SHORTER("更短", "更简短，保留关键否定、日期和承诺"),
    LIKE_ME("更像我", "更像我平时的说话方式"),
    NO_QUESTION("别反问", "不要用反问句，改为陈述"),
    DIRECT("更直接", "更直接，不绕弯子，但不变成指责"),
    GENTLER("更温柔", "更温柔，但不自动加接送、转账、陪伴等承诺"),
    NATURAL("更自然", "更自然口语化"),
    CUSTOM("自定义", "");

    companion object {
        /** UI 渲染用的预设选项列表（不含 CUSTOM，自定义单独处理） */
        val PRESET_LABELS: List<String> = entries.filter { it != CUSTOM }.map { it.label }

        /** UI 渲染用的选项列表（含自定义，顺序固定）——兼容旧引用 */
        val ALL_LABELS: List<String> = entries.map { it.label }

        /** 从 UI 文案反查指令 */
        fun fromLabel(label: String): RewriteCommand? =
            entries.firstOrNull { it.label == label }
    }
}
