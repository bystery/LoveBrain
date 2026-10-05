package com.lovebrain.app.core.designsystem

/**
 * 全局共享尺寸常量：被 3 个以上文件复用的尺寸才上提到这里，文件私有尺寸留在各文件的私有 object
 * （照抄 `PanelDimens` / `HeaderDimens` 体例）。
 *
 * 这里的数是设计 token：改动要走尺寸合同，页面不许就地抄一份。
 */
object AppDimens {
    /**
     * 无障碍触摸热区下限（dp）——全站**唯一**把下限写成字面量的地方，改这一处就是改全站。
     * Material 组件会自动补足到下限，自定义 `Box.clickable` 不会，所以自定义盒子一律取这颗常量。
     */
    const val TOUCH_TARGET_MIN_DP = 48
    /**
     * 通用**表单**输入框的**可见**外框高度（dp）——版式轴（眼睛看着对），不是热区轴。
     *
     * 热区不足要在**外层可点盒子**上补透明热区，不许连框带字把可见控件撑到 [TOUCH_TARGET_MIN_DP]：
     * 那会顺带抬高供应商弹窗、知识库编辑页与回复输入行。编辑节点自己只占这一档的版式高度，
     * "点框左右空白也能获焦"由 `CompactInput` 的外层盒子负责。
     *
     * 回复面板那一条输入框更窄，由调用方显式传值，不共用这一档。
     */
    const val INPUT_ROW_HEIGHT_DP = 36
    /**
     * 卡片内一排小操作（复制 / 赞 / 踩）的**紧凑热区**边长（dp）：**有意低于** [TOUCH_TARGET_MIN_DP]。
     * 158dp 宽的卡片排 3×48dp 的操作盒，正文就没了。命名说的是"卡片操作热区"，不是又一颗全局下限，
     * 别的场合不许拿它当尺子。
     */
    const val CARD_ACTION_HIT_DP = 28
    /**
     * 面板那一排**主动作**（生成 / 生成中 / 停止）的可见高度（dp）。
     *
     * **有意低于** [TOUCH_TARGET_MIN_DP] 的明写例外，只在面板工具条这一族生效；下限本身、页面主体、
     * 列表行、弹窗按钮与其它页面的主按钮照旧走 [TOUCH_TARGET_MIN_DP]。
     * 原因：整扇悬浮窗是一条紧凑工具条，同一列里上面是 30dp 模式栏、下面是 28dp 角色 chip 与
     * 28dp 调整胶囊，主按钮单独撑到 48 会把那一排挤出可视区。
     *
     * ⚠ 要让"点得到的面积"大于"看见的框"时，走两层写法（外层透明热区 + 内层可见形状），
     * 且 clickable 仍挂在带点击语义的那一层；这里买的是**整排的版式高度**，外面再包一圈 48dp
     * 等于把这一档作废。
     *
     * 数值只有主人读：`LbButtonHeightTier.PanelPrimaryAction`。页面不许自己抄这一颗。
     */
    const val PANEL_PRIMARY_ACTION_HEIGHT_DP = 40
    /**
     * 面板"已经有结果"那一排的**并列双出口**（重试 / 记入知识库）的可见高度（dp）。
     *
     * 同上一条那样是**明写的例外**，不动 [TOUCH_TARGET_MIN_DP]：这两颗不是引导下一步的主入口，
     * 而是同一份结果的两个出口，排在一行里各占一半宽。
     * 与 [INPUT_ROW_HEIGHT_DP] 同数但不同用途——那颗是输入框外框、这颗是按钮高度，
     * 合并它们会让下一个人调输入框时顺手动到按钮。
     *
     * 数值只有主人读：`LbButtonHeightTier.PanelResultActionPair`。
     */
    const val PANEL_RESULT_ACTION_HEIGHT_DP = 36
    /**
     * 行内小动作（知识库卡「编辑/导出」、供应商行）的**可见**最小高度（dp）。
     * 版式高度，不是下限，所以不写成 [TOUCH_TARGET_MIN_DP] 的别名；需要更大的可点面积时由外层盒子补。
     */
    const val ROW_ACTION_COMPACT_MIN_HEIGHT_DP = 32
    const val BORDER_WIDTH_DP = 1             // 细边框 / 分割线宽度
    const val ELEVATION_DEFAULT_DP = 2        // 默认阴影高度
    const val ELEVATION_MAX_DP = 4            // 阴影上限，超限即缺陷
    const val EMPTY_ICON_CONTAINER_DP = 48    // 空态图标容器尺寸（版式尺寸，与下限同数但不是下限）
    const val ARROW_SIZE_DP = 10              // Canvas 箭头尺寸
    const val ACTION_ICON_SIZE_DP = 18        // 小操作图标尺寸
    const val LOADING_SPINNER_SIZE_DP = 14    // 加载 spinner 尺寸
}
