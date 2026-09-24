package com.lovebrain.app.core.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * §6.5 要求的视觉矩阵坐标：一个「多宽、多大字」的组合。
 *
 * 复核在 §6.5 里点名要覆盖 320/360/412/600dp × 字体 1.0/1.3/2.0 × 中英文，
 * 而仓库此前只有一条通道能测这些：instrumentation（要 emulator）。本机没有 system image，
 * 于是"大字体下会不会裁切"这类问题在本机永远是"没测过"。这里把它变成 JVM 能跑的坐标。
 *
 * 一个诚实的边界：这里换的是**被测子树的布局约束**（下方 [RenderIn] 用固定尺寸的 Box
 *  constrain 子节点），Robolectric 的窗口本身固定在 @Config 声明的那一格。
 * 也就是说 "320dp" 这一格测的是"这棵子树被要求在 320dp 内摆好"，
 * 不是"这台设备的屏幕是 320dp"。真机窗口尺寸的矩阵归截图工具那一格。
 */
data class UiMatrix(
    val widthDp: Int,
    val heightDp: Int = 1000,
    val fontScale: Float = 1f,
    val note: String = ""
) {
    val id: String get() = "${widthDp}dp-font${(fontScale * 100).toInt()}"

    companion object {
        /** §6.5 点名的四个宽度 */
        val WIDTHS_DP = listOf(320, 360, 412, 600)

        /** §6.5 点名的三档字体倍率 */
        val FONT_SCALES = listOf(1.0f, 1.3f, 2.0f)

        /** 全矩阵：4 宽 × 3 字 = 12 格 */
        val FULL: List<UiMatrix> = WIDTHS_DP.flatMap { w ->
            FONT_SCALES.map { f -> UiMatrix(widthDp = w, fontScale = f) }
        }

        /**
         * 每格都必须存在的"最坏组合"：最小宽度 + 最大字体。
         * 用于"每个屏幕都用矩阵两端各测一遍"的紧凑门禁（12 格全跑的地方另说）。
         */
        val EXTREMES: List<UiMatrix> = listOf(
            UiMatrix(widthDp = 320, fontScale = 1.0f, note = "最窄 + 标准字"),
            UiMatrix(widthDp = 320, fontScale = 2.0f, note = "最窄 + 最大字（最坏组合）"),
            UiMatrix(widthDp = 600, fontScale = 2.0f, note = "最宽 + 最大字")
        )
    }
}

/**
 * 在 [density]（设备 px/dp）与这格的 fontScale 下渲染。
 *
 * 只覆盖 `LocalDensity` 的 fontScale，density 沿用设备值——这样断言里
 * "px ÷ density = dp" 的换算与 Compose 内部用的仍是同一把尺。
 *
 * 名字大写不是风格问题：ComposableNaming 这条 lint 要求"发射内容的 Unit 函数"必须大写开头
 * （成员写成 `renderIn` 报一次，改成扩展写成 `renderIn` 还是报一次——本机实测），
 * 而 lint 预算那条闸不许新增债，所以叫 [RenderIn]。
 */
@Composable
fun UiMatrix.RenderIn(density: Float, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalDensity provides Density(density = density, fontScale = fontScale)
    ) {
        Box(
            modifier = Modifier.size(widthDp.dp, heightDp.dp),
            contentAlignment = Alignment.TopStart
        ) { content() }
    }
}
