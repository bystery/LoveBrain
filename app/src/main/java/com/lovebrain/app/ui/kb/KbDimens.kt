package com.lovebrain.app.ui.kb

import com.lovebrain.app.core.designsystem.AppDimens

/**
 * 知识库这一族页面（`ui/KnowledgeBaseActivity.kt` 的列表页 + 本包的建库向导）共用的尺寸常量
 * （令牌化那一轮的产物：数值不变，仅外放命名）。
 *
 * 它原本是 `KnowledgeBaseActivity.kt` 里的 `private object KbDimens`——一家在用，写在哪家都行。
 * 向导 [com.lovebrain.app.ui.kb.OnboardingScreen] 搬进本包之后读它变成了**两家**，于是这张表
 * 跟着搬到它自己的那一格：值一个都没动，只是所有者从「某一张页面」换成「这一族页面」。
 * 同形先例见 `ui/panel/reply/ResultArea.kt` 的 `internal object ResultDimens`。
 */
internal object KbDimens {
    // 这一档量的是**可见高度**：旧版列表底部那一排「新建知识库 / 导入知识库」与向导底部那颗
    // 大按钮本来就是 48dp 高，可见与热区同数，所以这里没有"看得见 40、点得到 48"那两层。
    // 数值仍指回全局那一颗下限：改下限要动的页面一起动，别在这里抄一遍 48。
    //
    // ⚠ 这一颗**不是**本轮 KB 膨胀的来源：v1.3.1 的这两条大按钮本来就是 48dp。
    // 谁把它当"过度约束"缩掉，就是把用户没抱怨过的东西改坏了。
    const val PRIMARY_ACTION_HEIGHT_DP = AppDimens.TOUCH_TARGET_MIN_DP   // 新建/导入/完成大按钮高度
    const val EDIT_ICON_SIZE_DP = 14          // 重命名小铅笔图标
    const val ONBOARDING_SPINNER_SIZE_DP = 18 // 问卷生成中按钮内转圈尺寸
    const val PROGRESS_BAR_HEIGHT_DP = 4     // 问卷答题进度条高度

    /**
     * 这一族**不在这里开新数**，是故意的。
     *
     * 卡片第一行那两颗小动作（改名、删除）被写成两条边都垫到 48 的**可见盒**
     * （改名盒 `heightIn/widthIn(min = 48)`、删除盒 `Box.size(48)`），整张卡片的第一行因此
     * 凭空高了一截——用户说的"卡片变得非常大"量的就是它。现在收回 v1.3.1 的形状：
     *  - **纵向不要新高度**：行高只由名字那一行字给；两颗点击盒用 `fillMaxHeight()` 吃
     *    **已经存在**的那一行高（外层 `Row.height(IntrinsicSize.Min)` 让它有得可吃）；
     *  - **横向**在调用点直接写 `AppDimens.TOUCH_TARGET_MIN_DP.dp` 当最小宽度：买的是透明余量，
     *    没有底色也没有边框，屏幕上看不见任何新东西。
     *
     * 为什么既不写一颗 `CARD_HIT_MIN_WIDTH_DP = 44`、也不写成 `val … : Dp`：
     * `ui/UiLayerDependencyContractTest.kt` 那把尺按"写在可点链上、低于全局下限的每一颗数"逐颗登记
     * 主人（`subFloorNumbers`），而它认的形状是 `某颗名字.dp`。换成 `: Dp` 的别名就等于从尺面前
     * 走开——那是把量具弄瞎当干净。指回 core 那一颗：既不用抄新数、也不用改那张表。
     */
}
