package com.lovebrain.app.model

/**
 * DRY: 单条改写操作选项的唯一定义。
 * UI 文案与 VM 指令映射共用此枚举，不在各处重复 listOf 字符串。
 *
 * [label] 是用户可见文案，[instruction] 是发给模型的改写指令关键词。
 */
enum class RewriteCommand(val label: String, val instruction: String) {
    REPHRASE("换一种说法", "换一种说法"),
    NATURAL("更自然", "更自然"),
    SHORTER("更简短", "更简短"),
    GENTLER("更温柔", "更温柔");

    companion object {
        /** UI 渲染用的选项列表（顺序固定） */
        val ALL_LABELS: List<String> = entries.map { it.label }

        /** 从 UI 文案反查指令 */
        fun fromLabel(label: String): RewriteCommand? =
            entries.firstOrNull { it.label == label }
    }
}
