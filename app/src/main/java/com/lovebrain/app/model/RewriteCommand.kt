package com.lovebrain.app.model

/**
 * DRY: 单条改写操作选项的唯一定义。
 * UI 文案与 VM 指令映射共用此枚举，不在各处重复 listOf 字符串。
 *
 * 三个字段各有主人，别混用：
 * - [label] 是这枚选项的**稳定名字**：改写请求按它反查指令，在途身份与既有的
 *   `Loading(option)` 状态里存的也是它。换文案不许动这一栏。
 * - [displayLabel] 才是卡片调整区上那行字。
 * - [instruction] 是发给模型的改写指令关键词。
 *
 * 卡片调整区只列 [UI_OPTIONS] 那四项（更自然 / 换种说话 / 更温柔 / 自定义）。
 * 其余预设（更短 / 更像我 / 别反问 / 更直接）不再出现在界面上，但枚举项、[label]、
 * [instruction] 与声明顺序都原样保留：删掉的是多余展示，不是内部标识——
 * 反查、历史在途状态与按 ordinal 读回来的旧数据都还认这些项，
 * 删项或换序会把旧读数解成新选项。
 *
 * 自定义改写走 [CUSTOM] 那条独立入口（[instruction] 为空是刻意的：正文由用户输入）。
 */
enum class RewriteCommand(val label: String, val displayLabel: String, val instruction: String) {
    REPHRASE("换一种说法", "换种说话", "换一种说法"),
    SHORTER("更短", "更短", "更简短，保留关键否定、日期和承诺"),
    LIKE_ME("更像我", "更像我", "更像我平时的说话方式"),
    NO_QUESTION("别反问", "别反问", "不要用反问句，改为陈述"),
    DIRECT("更直接", "更直接", "更直接，不绕弯子，但不变成指责"),
    GENTLER("更温柔", "更温柔", "更温柔，但不自动加接送、转账、陪伴等承诺"),
    NATURAL("更自然", "更自然", "更自然口语化"),
    CUSTOM("自定义", "自定义", "");

    companion object {
        /**
         * 卡片调整区实际列出的四项——顺序就是屏幕上那一行的顺序。
         * [CUSTOM] 排在最后，点它才展开输入框与提交按钮。
         */
        val UI_OPTIONS: List<RewriteCommand> = listOf(NATURAL, REPHRASE, GENTLER, CUSTOM)

        /** [UI_OPTIONS] 的屏幕文案（读屏与测试都拿这一份对，别再抄第三份字符串） */
        val UI_OPTION_LABELS: List<String> = UI_OPTIONS.map { it.displayLabel }

        /** 调整区里那三颗预设胶囊（不含自定义） */
        val UI_PRESET_OPTIONS: List<RewriteCommand> = UI_OPTIONS.filter { it != CUSTOM }

        /**
         * 全部预设的稳定名字（不含自定义）。
         *
         * 界面已经不再整族列出这些项，但反查、旧读数与外部引用还在用，所以保留原样。
         */
        val PRESET_LABELS: List<String> = entries.filter { it != CUSTOM }.map { it.label }

        /** 选项列表（含自定义，顺序固定）——兼容旧引用 */
        val ALL_LABELS: List<String> = entries.map { it.label }

        /** 从稳定名字反查指令：预设走 [instruction]，自定义由调用方改走自定义入口 */
        fun fromLabel(label: String): RewriteCommand? =
            entries.firstOrNull { it.label == label }

        /** 从屏幕文案反查：先认屏幕上那行字，再退回稳定名字 */
        fun fromDisplayLabel(display: String): RewriteCommand? =
            entries.firstOrNull { it.displayLabel == display } ?: fromLabel(display)
    }
}
