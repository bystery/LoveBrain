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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * `LbScreenScaffold` —— 页面背景、安全区、顶部栏、统一水平边距。
 *
 * 这四件事只在这里做一次，各页不许再自己拼根节点、也不许再自带 `padding(horizontal = …)`：
 * 边距档只有一个主人（[LB_SCREEN_HORIZONTAL_MARGIN]）。
 *
 * ## 为什么 insets 默认是关的
 *
 * 没有任何 Activity 做 edge-to-edge（三个 Activity 的 onCreate 只设 `FLAG_SECURE`），
 * 这种窗口里系统栏本来就不穿透，再叠一层 `systemBarsPadding` 是加成还是加了两遍
 * **只有真机或 CI 能定**（Robolectric 给的 insets 是 0，本机量不出来）。
 *
 * 所以这里**不改任何一页的 inset 行为**：默认 `false`，今天带 padding 的那一页显式传 `true`。
 * 真机确认之后，决定收在这一个参数上改一次，而不是回去各页散着改。
 *
 * ## 谁不在这儿
 *
 * 悬浮面板与气泡跑在 `TYPE_APPLICATION_OVERLAY` 窗口里（`service/OverlayWindowHost` 那一套，
 * 气泡 `OverlayBubbleWindow`、面板 `OverlayPanelWindow` 各调一次），
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
