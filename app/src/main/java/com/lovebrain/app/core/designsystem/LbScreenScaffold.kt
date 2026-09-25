package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * §6.1 表里最后一行：`LbScreenScaffold` —— 页面背景、安全区、顶部栏、统一水平边距。
 *
 * ## 它收的是哪四件事
 *
 * 搬之前这几件事是各页自己拼的，本机量到的是（`ScreenScaffoldFrameTest`，
 * 同一台仪器、360dp 挂载槽，取最左边那颗可点节点的左边缘）：
 *
 * | 页面 | 今天的外框 | 量到的水平边距 |
 * |---|---|---|
 * | 捕获范围（走 `ScreenPage`） | `Column.fillMaxSize.background(SurfaceBase).padding(xxxl)` | **24dp** |
 * | 供应商（自己拼 `Column`） | 同上，只是没走 `ScreenPage` | **24dp** |
 * | 首次引导（又一套根） | `Box…background…systemBarsPadding` 套 `Column.padding(xl)` | **16dp** |
 *
 * 也就是说"统一水平边距"这半句在改之前**不成立**：同一个 App 里两种档。
 * 顺带还量到首次引导那颗主按钮是 **328x34dp**，够不到 §6.5 :531 的 48dp
 * ——那是另一格的事，这里先记账不顺手改（见交接单）。
 *
 * ## 为什么 insets 默认是关的
 *
 * 全 App 12 个内容根里只有 2 个加了 `systemBarsPadding`，而**没有任何 Activity 做
 * edge-to-edge**（三个 Activity 的 onCreate 只设 `FLAG_SECURE`）。在不 edge-to-edge 的
 * 窗口里系统栏本来就不穿透，那两处 padding 在真机上是加成还是加了两遍，
 * **只有设备或 CI 能定**——本机量不出来（Robolectric 给的 insets 是 0）。
 *
 * 所以这一格**不改任何一页的 inset 行为**：默认 `false`，今天有 padding 的那一页
 * 显式传 `true`。等真机确认之后，把决定收在这一个参数上改一次，而不是回去各页散着改。
 * 这条边界写在账本里，不当已解决。
 *
 * ## 谁不在这儿
 *
 * 悬浮面板与气泡跑在 `TYPE_APPLICATION_OVERLAY` 窗口里（`FloatingService` 建那两个窗口），
 * 没有系统栏、也不该被页面的边距档管着——它们不走这里。
 */

/** 内容最大宽度——窄屏铺满，宽屏（折叠/平板/桌面模式）不把一行字拉到 900dp */
const val LB_SCREEN_MAX_WIDTH_DP = 600

/** 统一的水平边距档。各页不许再自带 padding(horizontal = …) */
val LB_SCREEN_HORIZONTAL_MARGIN: Dp = Spacing.xxxl

@Composable
fun LbScreenScaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    handlesSystemBarInsets: Boolean = false,
    horizontalMargin: Dp = LB_SCREEN_HORIZONTAL_MARGIN,
    maxContentWidth: Dp? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val frame = modifier
        .fillMaxSize()
        .background(SurfaceBase)
        .then(if (handlesSystemBarInsets) Modifier.systemBarsPadding() else Modifier)
        .then(if (maxContentWidth != null) Modifier.widthIn(max = maxContentWidth) else Modifier)

    if (topBar == null) {
        // 没有页头的页（首次引导那种）：外框还是同一个所有者，只是少那一格
        Column(modifier = frame.padding(horizontal = horizontalMargin)) { content() }
        return
    }

    Column(modifier = frame) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalMargin)) { topBar() }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = horizontalMargin),
            content = content
        )
    }
}
