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

    /**
     * 主动作"生成中 = 点它就是停止"那颗条的锚点。
     *
     * 值故意仍是 `generation_stop_action`（不跟着改名）：设备侧选择器与 CI 脚本按这个字面量找节点，
     * 改值等于把那条链一起换掉，而这一格只想换**归属**（从 `ui/panel/reply` 的常量搬进设计系统）。
     * 这正是"文字会变，tag 不会"要防的那件事反过来用——tag 也别随手改。
     */
    const val PRIMARY_STOP = "generation_stop_action"
}
