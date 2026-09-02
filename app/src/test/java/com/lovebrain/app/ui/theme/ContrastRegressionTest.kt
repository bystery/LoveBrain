package com.lovebrain.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.pow

/**
 *  对比度回归测试（R8 批  ；基线 上必红 → 修后绿）。
 *
 * 纯 JVM、零新依赖：
 * - 直接读 `Color.kt` 源文件正则解析 `Color.hsl(h, s, l)` 三元组（不触碰 Compose Color 类）；
 * - 测试内自含 HSL→RGB→sRGB 线性化→相对亮度→对比度公式（WCAG 2.1 相对亮度法）。
 *
 * 断言修后组合全部 ≥4.5:1（WCAG AA 正文标准）：
 * - TextHint vs SurfaceCard / SurfaceInset / PrimaryLight
 * - Success vs SurfaceCard；白字 on Success
 * - Warning vs SurfaceCard；Warning vs WarningBg
 */
class ContrastRegressionTest {

    private val colorSource: String by lazy {
        // 测试工作目录 = app/
        File("src/main/java/com/lovebrain/app/ui/theme/Color.kt").readText()
    }

    /** 解析 `val <name>: Color get() = Color.hsl(h, s, l)`，返回 (h, s, l)；h 为 THEME_HUE 时按 230f 计。 */
    private fun hsl(name: String): Triple<Float, Float, Float> {
        val regex = Regex(
            """val\s+${Regex.escape(name)}:\s*Color\s+get\(\)\s*=\s*Color\.hsl\(([^,]+),\s*([\d.]+)f,\s*([\d.]+)f\)"""
        )
        val match = regex.find(colorSource)
            ?: error("$name 的 Color.hsl 三元组未解析到——Color.kt 格式是否变更？")
        val hue = match.groupValues[1].trim().let { if (it == "THEME_HUE") 230f else it.removeSuffix("f").toFloat() }
        val sat = match.groupValues[2].toFloat()
        val light = match.groupValues[3].toFloat()
        return Triple(hue, sat, light)
    }

    /** HSL → RGB（各分量 0..1）。标准公式。 */
    private fun hslToRgb(h: Float, s: Float, l: Float): Triple<Double, Double, Double> {
        val ld = l.toDouble()
        val sd = s.toDouble()
        val c = (1 - abs(2 * ld - 1)) * sd
        val hp = (h % 360f).toDouble() / 60.0
        val x = c * (1 - abs(hp % 2 - 1))
        val (r1, g1, b1) = when {
            hp < 1 -> Triple(c, x, 0.0)
            hp < 2 -> Triple(x, c, 0.0)
            hp < 3 -> Triple(0.0, c, x)
            hp < 4 -> Triple(0.0, x, c)
            hp < 5 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        val m = ld - c / 2
        return Triple(r1 + m, g1 + m, b1 + m)
    }

    /** sRGB 分量线性化（WCAG 定义）。 */
    private fun linearize(c: Double): Double =
        if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    /** WCAG 相对亮度。 */
    private fun luminance(hsl: Triple<Float, Float, Float>): Double {
        val (r, g, b) = hslToRgb(hsl.first, hsl.second, hsl.third)
        return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
    }

    /** WCAG 对比度 (L1+0.05)/(L2+0.05)，恒 ≥1。 */
    private fun contrast(a: Triple<Float, Float, Float>, b: Triple<Float, Float, Float>): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertContrastAtLeast(fgName: String, bgName: String, fg: Triple<Float, Float, Float>, bg: Triple<Float, Float, Float>) {
        val ratio = contrast(fg, bg)
        assertTrue(
            "$fgName on $bgName 对比度 %.2f:1 < 4.5（WCAG AA 正文标准）".format(ratio),
            ratio >= 4.5
        )
    }

    /**
     * 单方法覆盖全部 7 个修后组合（ 口径：本批新增用例恰 1）。
     * 断言组合：
     * - TextHint vs SurfaceCard / SurfaceInset / PrimaryLight
     * - Success vs SurfaceCard；白字 on Success
     * - Warning vs SurfaceCard；Warning vs WarningBg
     */
    @Test
    fun r8_contrast_regression() {
        val textHint = hsl("TextHint")
        val success = hsl("Success")
        val warning = hsl("Warning")
        val surfaceCard = hsl("SurfaceCard")
        val surfaceInset = hsl("SurfaceInset")
        val primaryLight = hsl("PrimaryLight")
        val warningBg = hsl("WarningBg")

        assertContrastAtLeast("TextHint", "SurfaceCard", textHint, surfaceCard)
        assertContrastAtLeast("TextHint", "SurfaceInset", textHint, surfaceInset)
        assertContrastAtLeast("TextHint", "PrimaryLight", textHint, primaryLight)
        assertContrastAtLeast("Success", "SurfaceCard", success, surfaceCard)
        // 白字 on Success（实心状态底上的白字）
        val white = Triple(0f, 0f, 1f)
        val whiteOnSuccess = contrast(white, success)
        assertTrue(
            "白字 on Success 对比度 %.2f:1 < 4.5".format(whiteOnSuccess),
            whiteOnSuccess >= 4.5
        )
        assertContrastAtLeast("Warning", "SurfaceCard", warning, surfaceCard)
        assertContrastAtLeast("Warning", "WarningBg", warning, warningBg)
    }
}
