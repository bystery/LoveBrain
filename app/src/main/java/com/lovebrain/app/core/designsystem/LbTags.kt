package com.lovebrain.app.core.designsystem

/**
 * 统一组件自己的自动化锚点（§6.1 那张表里的组件）。
 *
 * 上一格装 §6.2 四段守卫时这些 tag 叫 `lb_home_*`——那是"住在 core 却叫 home"：
 * 锚点属于组件，不属于某一个页面。这一格把它们改回组件名，页面专属的锚点
 * （军师状态卡、About 入口）留在 `ui/home` 那边。
 */
object LbTags {
    const val SECTION = "lb_section"
    const val ACTION_CARD = "lb_action_card"
    const val SETTING_ROW = "lb_setting_row"
    const val METRIC_CELL = "lb_metric_cell"
}
