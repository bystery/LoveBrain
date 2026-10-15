package com.lovebrain.app.ui.visual

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceBase
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.ui.panel.reply.ReplyInput
import com.lovebrain.app.ui.theme.LoveBrainTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.graphics.BitmapFactory
import java.io.File

/**
 * 回复输入行 1（《他》/《我》chip 段 → 输入框 → ➕ → 仅看本轮符号）的**屏幕级**实拍 + 像素尺。
 *
 * 为什么要有这一格：用户 2026-10-14 原话「《他》…《➕》就是这一行，现在每行的间隔都不一样」——
 * 行里每段之间的**可见**间隔是不是同一个数，语义树读不出来（➕ 的热区盒比可见胶囊大一圈，
 * bounds 量到的是热区不是屏上那一条），只有像素能回答。修之前实拍的读数是
 * **4 / 4 / 4 / 16 / 23**（chip→输入框 4、输入框→➕ 16、➕→符号 23）——那两张旧图就是这把尺的
 * 反向证人：它对"不均匀"真有牙。修法与读数见 `ReplyDimens.ADD_SIDE_DEAD` 的 KDoc；
 * 修后各段可见间隔 = SEGMENT_GAP(10) 同一个数，这一格把"同一数"钉成断言。
 *
 * 拍的是生产三档里的默认窗那一档（300dp 窗 − 左右各 [Spacing.xl] = 268dp 行宽），
 * 草稿非空（➕ 涂实心蓝、符号都在屏上），是这一行最满的形状。
 *
 * ⚠ 量的是**像素**不是 bounds：➕ 的盒是透明的，bounds 给的是 48 那格，
 * 屏上那颗 28 的蓝胶囊只有像素认得。抗锯齿让边缘各有 ±2px 的糊，所以判"同一数"
 * 用 ±3px 的容差，不判绝对 10（绝对值那半由 `ReplyInputWidthBudgetTest` 的账本钉）。
 * 复现宿主外框的边界与 `SettingsPageVisualBaselineTest` 同一条：主题长在面板根、
 * 行宽 = 窗 − 左右 [Spacing.xl]，这里照补，否则量到的是量具瞎。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "w300dp-h700dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplyInputRowCaptureTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun captureTheReplyRowAtProductionDefaultWidth() {
        val shot = File("build/outputs/roborazzi/reply_row_segment_gaps.png")
        rule.setContent {
            LoveBrainTheme {
                // 300dp 悬浮窗那一档：行可用宽 = 300 − 面板左右各 Spacing.xl = 268
                UiMatrix(300).RenderIn(LocalDensity.current.density) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceBase)
                            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
                    ) {
                        ReplyInput(
                            draftText = "草稿非空",
                            currentRole = ChatMessage.Role.ME,
                            editingIndex = -1,
                            onDraftChange = {},
                            onRoleChange = {},
                            onAdd = {},
                            onFocusChange = {},
                            onOnlyThisRoundChange = {}
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        // 在册流水线那张（record 脚本认这份默认命名）
        rule.onRoot().captureRoboImage()
        // 像素尺用的这一张：把宿主 decorView 自己画进一颗真 Bitmap——
        // captureRoboImage 在普通 testDebugUnitTest 跑里不落盘（那是 record 模式的事），
        // captureToImage 在 Robolectric 上会等一次永远不来的重绘（已知限制）；
        // 而 NATIVE 图形模式下 decorView.draw(Canvas) 画出来的是**真像素**，
        // 每一次普通跑都有，断言才能每次都咬合。
        val host = (rule as androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>).activity
        val decor = host.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(
            decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888
        )
        decor.draw(android.graphics.Canvas(bmp))
        java.io.FileOutputStream(shot).use { out ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("实拍没落盘：$shot", shot.isFile)

        // ── 像素尺：各段之间的可见间隔必须是同一个数 ──
        // android.jar 里没有 javax.imageio：解码走 Robolectric 那颗 BitmapFactory
        // （本格是 @GraphicsMode(NATIVE)，解的是真 PNG，不是影子空壳）
        val img = BitmapFactory.decodeFile(shot.absolutePath)
            ?: throw AssertionError("实拍读不回来：$shot")
        val w = img.width
        val h = img.height

        fun isBlue(c: Int): Boolean {
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            // 只认 Primary 那一档的**实心**蓝（b−r > 80）：输入胶囊那圈浅蓝描边
            // （b−r ≈ 55）不许混进来，否则蓝的段数就不是"两颗实心件"那一本账
            return b > 170 && b - r > 80 && b - g > 60
        }

        fun isWhite(c: Int): Boolean {
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            return r >= 243 && g >= 243 && b >= 243
        }

        // 找到行所在的那条横带（有蓝像素的行）
        var top = -1; var bottom = -1
        for (y in 0 until h) {
            var any = false
            for (x in 0 until w) if (isBlue(img.getPixel(x, y))) { any = true; break }
            if (any) { if (top < 0) top = y; bottom = y }
        }
        assertTrue("整张图里读不到蓝（选中 chip 与 ➕ 都该是实心蓝）——量具瞎或内容没画", top >= 0)

        // 逐列分类：蓝（chip 选中格与 ➕ 胶囊）、非白（其余胶囊的边与底）
        data class Col(val blue: Boolean, val ink: Boolean)
        val cols = (0 until w).map { x ->
            var blue = false; var ink = false
            for (y in top..bottom) {
                val c = img.getPixel(x, y)
                if (isBlue(c)) blue = true
                if (!isWhite(c)) ink = true
            }
            Col(blue, ink)
        }

        fun runs(pred: (Col) -> Boolean, minWidth: Int): List<IntRange> {
            val out = mutableListOf<IntRange>()
            var s = -1
            for (x in cols.indices) {
                if (pred(cols[x])) { if (s < 0) s = x }
                else if (s >= 0) { if (x - s >= minWidth) out += s until x; s = -1 }
            }
            if (s >= 0 && cols.size - s >= minWidth) out += s until cols.size
            return out
        }

        val blueRuns = runs({ it.blue }, 3)
        assertEquals(
            "实心蓝应当恰好两段（选中的角色 chip + ➕ 胶囊）；多了就是这一行长了第二颗实心件，实到 $blueRuns",
            2, blueRuns.size
        )
        val chipsAndInput = runs({ it.ink }, 3).filter { it.last < blueRuns[1].first }
        assertTrue(
            "➕ 左边该读出『chip 组 + 输入框』两段实体，实到 $chipsAndInput",
            chipsAndInput.size >= 2
        )
        val scopeRun = runs({ it.ink }, 3).filter { it.first > blueRuns[1].last }
        assertTrue("➕ 右边该读出范围符号那一段，实到 $scopeRun", scopeRun.isNotEmpty())

        // 可见间隔（像素边缘各带 ±2 的抗锯齿糊，判同一数用容差，不判绝对值）
        // chip 段的右沿 = **最后一颗角色 chip**（补充）那一 run 的右沿，不是第一颗的
        val gapChipToInput = chipsAndInput.last().first - chipsAndInput[chipsAndInput.size - 2].last - 1
        val gapInputToAdd = blueRuns[1].first - chipsAndInput.last().last - 1
        val gapAddToScope = scopeRun.first().first - blueRuns[1].last - 1
        val gaps = listOf(gapChipToInput, gapInputToAdd, gapAddToScope)
        assertTrue(
            "行 1 各段之间的可见间隔必须是同一个数（用户 2026-10-14 原话）；" +
                "实到 chip→输入 $gapChipToInput / 输入→➕ $gapInputToAdd / ➕→符号 $gapAddToScope" +
                "（修前实拍是 4 / 16 / 23，那一版在这一格红）",
            gaps.max() - gaps.min() <= 3
        )
        assertTrue(
            "三段间隔读数出了像素合理范围（全 0=贴死、全大=量具瞎）：$gaps",
            gaps.all { it in 4..16 }
        )
    }
}
