package com.lovebrain.app.ui.theme

/**
 * 全局共享尺寸常量（/：复用方 ≥3 的尺寸上提全局， 规则）。
 *
 * 纯视觉令牌化：数值逐位不变，仅外放命名；文件私有尺寸仍留在各文件的
 * 私有 object（照抄 PanelDimens/HeaderDimens 体例）。
 */
object AppDimens {
    const val INPUT_ROW_HEIGHT_DP = 36        // 输入行/按钮行统一高度（： 上提，≥3 复用方）
    const val BORDER_WIDTH_DP = 1             // 细边框/分割线宽度（≥3 文件）
    const val ELEVATION_DEFAULT_DP = 2        // 默认阴影高度
    const val ELEVATION_MAX_DP = 4            // 阴影上限（，超限即缺陷）
    const val EMPTY_ICON_CONTAINER_DP = 48    // 空态图标容器尺寸（3 文件）
    const val ARROW_SIZE_DP = 10              // Canvas 箭头尺寸（3 文件）
    const val ACTION_ICON_SIZE_DP = 18        // 小操作图标尺寸（KbEdit + KnowledgeBase 4 处）
    const val LOADING_SPINNER_SIZE_DP = 14    // 加载 spinner 尺寸（3 文件 5 处）
}
