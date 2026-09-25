package com.lovebrain.app.core.designsystem

/**
 * 全局共享尺寸常量（/：复用方 ≥3 的尺寸上提全局， 规则）。
 *
 * 纯视觉令牌化：数值逐位不变，仅外放命名；文件私有尺寸仍留在各文件的
 * 私有 object（照抄 PanelDimens/HeaderDimens 体例）。
 */
object AppDimens {
    /**
     * 无障碍触摸区下限（dp）——**全站唯一一处把"下限"这个数写成字面量 48 的地方**。
     *
     * 所有可点击元素——包括自定义 Box.clickable——的热区都必须 ≥ 此值。
     * Material 组件会自动补足，自定义盒子不会，所以统一取这个常量。
     *
     * 为什么要收成一颗：这颗数原先在 17 个 `const val … = 48` 里各写了一遍
     * （本机实扫：`core/designsystem` 8 颗、`ui/common` 1 颗、页面私有 object 8 颗），
     * 于是"抬下限"这件事在本仓库里不可执行——改一处只会让其余 16 处悄悄不一致，
     * 而 :596 那条验收线（"无小于 48dp 热区"）是全站口径，不是逐组件口径。
     * 其余那些名字（[LB_PRIMARY_MIN_HEIGHT_DP]、[LB_SHEET_ACTION_MIN_DP]、
     * [LB_DIALOG_ACTION_MIN_DP]…）**保留各自的语义化命名**，但值一律写成
     * `= AppDimens.TOUCH_TARGET_MIN_DP`：读代码时仍看得出"这是哪一颗的下限"，
     * 而数只有一个来源。由 `TouchFloorSingleOwnerTest` 钉住，不许长出第二处字面量。
     */
    const val TOUCH_TARGET_MIN_DP = 48
    /**
     * 输入行/按钮行统一高度。36 → **48** 不是审美调整，是 §6.5 那条硬规定：
     * "所有 clickable/toggleable bounds ≥48×48dp"（指导书 :531、验收线 :596"无小于 48dp 热区"）。
     * 起因是捕获范围页第一次被整屏量了一遍：那颗搜索框真正带点击/编辑语义的节点
     * 只有 **288x15dp**——外面那层 Box 就算有 36dp 也没用，手指信的是里面那颗。
     * 所以这一档抬到 48，并且两颗输入框的**可编辑节点自己**也垫到 TOUCH_TARGET_MIN_DP（见 CompactInput / PanelTextInput）。
     *
     * 既然这个数**来自**下限而不是版式自选，它就写成下限的别名而不是再抄一遍 48。
     */
    const val INPUT_ROW_HEIGHT_DP = TOUCH_TARGET_MIN_DP
    const val BORDER_WIDTH_DP = 1             // 细边框/分割线宽度（≥3 文件）
    const val ELEVATION_DEFAULT_DP = 2        // 默认阴影高度
    const val ELEVATION_MAX_DP = 4            // 阴影上限（，超限即缺陷）
    const val EMPTY_ICON_CONTAINER_DP = 48    // 空态图标容器尺寸（3 文件）
    const val ARROW_SIZE_DP = 10              // Canvas 箭头尺寸（3 文件）
    const val ACTION_ICON_SIZE_DP = 18        // 小操作图标尺寸（KbEdit + KnowledgeBase 4 处）
    const val LOADING_SPINNER_SIZE_DP = 14    // 加载 spinner 尺寸（3 文件 5 处）
}
