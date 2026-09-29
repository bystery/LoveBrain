package com.lovebrain.app.ui.theme

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.SurfaceBase
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * §6.5 第⑤栏「浅色/深色；不支持深色就明确锁浅色」的**锁**（复核账本 :155 那一行下的判语是
 * "需要写死『锁浅色』，而不是做半套主题"）。
 *
 * 为什么这一格到今天还是空的：产品**故意**只有浅色（`Theme.kt` 只建 `lightColorScheme`，
 * `isSystemInDarkTheme` / `dynamicColor` / `values-night` 全仓零命中）。指导书接受"二选一"，
 * 但"选了哪一个"这件事没有一个字符守着——**下次谁加回 night 不会红**。这一档就是把决定变成闸。
 *
 * ⚠ **前两格是源码级/资源级判据**（读文件、数目录），它们**不是** UI 行为断言。
 * 指导书 §9 第 4 条反对的正是"把 grep 计数当 UI 合同"（本仓库 `ProductionUiContractTest`
 * 顶部自己就留了这条教训），所以这两格按规矩**在这里标明级别**，并且配了后面三格渲染级判据：
 * ③ 先证明"夜"这个请求真的送到了组合里（不自证这一条，后面两格就是空过），
 * ④ 再读 `LoveBrainTheme` 实际解析出来的 `MaterialTheme.colorScheme`，
 * ⑤ 最后按**像素**验一次"画到屏幕上的那一块确实是亮色底"。
 * 三格判的是同一句话的三个层次，任何一层读不到数都抛，不静默。
 *
 * 关于 ③④⑤ 的 `@Config`：类级那一串是**照抄** `PanelHeaderSemanticsTest` /
 * `UsageExtremeValuesSemanticsTest` 的（同一台仪器、同一个 600dp 窗口，不另起炉灶），
 * 只在夜档那三格多带一个 `-night` 词——这一档要的前提就是"系统在要求深色"。
 * 不用 `CompositionLocalProvider(LocalConfiguration …)` 自己伪造：那等于测试把答案喂进去，
 * 生产真去读系统配置时反而没人判。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LightThemeLockTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    /** 从组合里读出来的读数。刻意**不是** MutableState：测试要的是「读」，往组合里写 state 会自己招一次重组 */
    private var reading: SchemeReading? = null

    /** 组合里实际解析到的那一份配色 + 系统当时到底有没有要求深色 */
    private data class SchemeReading(
        val systemWantsDark: Boolean,
        val background: Color,
        val surface: Color,
        val onSurface: Color,
        val surfaceVariant: Color,
        val primary: Color
    )

    // ═══════════ 源码级 / 资源级（KDoc 已标明：这两格不是 UI 行为断言）═══════════

    /**
     * 测试的工作目录有两种可能（仓库根 / `app` 模块目录），两种都认；
     * 认不到就**抛**——"文件没找到"绝不能当成"没有暗色代码"而判绿。
     */
    private fun appRoot(): File {
        if (File("app", "src/main").isDirectory) return File("app")
        if (File("src/main").isDirectory) return File(".")
        throw AssertionError("找不到 app/src/main——这台尺根本没接到仓库上，不能让它空过")
    }

    private fun themeSource(): String {
        val f = File(appRoot(), "src/main/java/com/lovebrain/app/ui/theme/Theme.kt")
        assertTrue("Theme.kt 不在预期位置：$f", f.isFile)
        return f.readText()
    }

    /**
     * **源码级**判据（第⑤栏的第一半）：`Theme.kt` 里除了亮色方案以外一个方案都不许建。
     *
     * 判的是掩掉注释之后的代码——`Theme.kt` 与 `Color.kt` 里都写着"暗色已删"这种话，
     * 不掩注释的话，"把注释写长一点"就能骗过这把尺（也会让它反过来红在文档上）。
     */
    @Test
    fun `the theme source builds no dark and no dynamic scheme`() {
        val code = SourceScan.maskComments(themeSource())

        listOf(
            "darkColorScheme", "isSystemInDarkTheme", "dynamicColor",
            "dynamicLightColorScheme", "dynamicDarkColorScheme", "DayNight", "uiMode"
        ).forEach { forbidden ->
            assertTrue(
                "Theme.kt 的代码里出现了「$forbidden」。§6.5 第⑤栏锁的是「浅色一种」：" +
                    "要么整站做深色并把 12 格矩阵与对比度一起补上，要么把这半句拿掉。" +
                    "（注释已掩掉，命中就说明它是代码不是文档）",
                !code.contains(forbidden)
            )
        }
        assertTrue("Theme.kt 没有用 lightColorScheme( 建方案", code.contains("lightColorScheme("))
        assertTrue(
            "LoveBrainTheme 没把自己建的那份方案交给 MaterialTheme（那它就是在用 MaterialTheme 的默认档，" +
                "而默认档会跟系统 uiMode 走）",
            Regex("MaterialTheme\\s*\\([\\s\\S]{0,200}?colorScheme\\s*=\\s*loveBrainColorScheme\\(\\)")
                .containsMatchIn(code)
        )
    }

    /**
     * **资源级**判据（第⑤栏的第二半）：没有 night 资源目录，XML 主题写死 Light。
     *
     * 这一半不是多余的：Compose 那一侧的色板锁住了，XML 那一侧还能从
     * `Theme.Material.DayNight` 的窗口底色漏出深色（状态栏、Dialog 底色、
     * 还没进 Compose 的那一小截窗口都会跟着变）。
     */
    @Test
    fun `there is no night resource folder and the xml theme is explicitly light`() {
        val res = File(appRoot(), "src/main/res")
        assertTrue("找不到资源目录 $res", res.isDirectory)

        val nightDirs = res.listFiles()
            ?.filter { it.isDirectory && Regex("^values.*night").containsMatchIn(it.name) }
            ?: emptyList()
        assertTrue(
            "出现了 night 资源目录：${nightDirs.map { it.name }}。§6.5 第⑤栏锁浅色——" +
                "加 night 目录就得同时把深色矩阵（320/360/412/600 × 1.0/1.3/2.0）与对比度一起补上，" +
                "而不是先放一份没人看的 XML",
            nightDirs.isEmpty()
        )

        val styleFiles = File(res, "values").listFiles()
            ?.filter { it.isFile && (it.name.startsWith("themes") || it.name.startsWith("styles")) }
            ?: emptyList()
        check(styleFiles.isNotEmpty()) { "values 目录里没有 themes/styles，这台尺读不到数" }
        val parents = styleFiles.flatMap { f ->
            Regex("""<style\s+name="Theme[^"]*"[^>]*parent="([^"]+)"""")
                .findAll(f.readText()).map { it.groupValues[1] to f.name }.toList()
        }
        check(parents.isNotEmpty()) { "没有任何带 parent 的 Theme.* 样式，读不到父主题" }
        parents.forEach { (parent, file) ->
            assertTrue(
                "$file 里 Theme 的父档是「$parent」——带 Dark/DayNight 的父档会让窗口那一侧跟着系统变深，" +
                    "而本产品的决定是只出浅色（§6.5 第⑤栏）",
                !parent.contains("DayNight") && !parent.contains("Dark")
            )
            assertTrue("$file 里 Theme 的父档「$parent」没写死 Light 档", parent.contains("Light"))
        }
    }

    // ═══════════ 渲染级（这三格才是 UI 行为）═══════════

    /**
     * 夜档的哨兵格：先证明"系统要深色"这个请求**真的到了组合里**。
     *
     * 没有这一格，后面两格随时会空过——`-night` 那个词万一没进 `uiMode`
     * （qualifier 串写错、compose 测试宿主自己覆盖了配置……），后面两格就会在
     * "系统根本没要求深色"的前提下判"深色没生效"，那种绿最难发现。
     * 判据形状与 `PanelHeaderSemanticsTest` 第一格同一句话：**先证明仪表量到了东西**。
     */
    @Test
    @Config(qualifiers = "sw600dp-w600dp-h1200dp-normal-long-night-mdpi")
    fun `the night request really reaches the composition`() {
        mountThemeProbe()
        val read = requireNotNull(reading) { "组合没跑起来，读不到任何配色" }
        assertTrue(
            "@Config 带了 -night，但组合里的 isSystemInDarkTheme() 仍是 false ⇒ " +
                "夜请求没到位，本文件后面两格就没有牙齿（先修这台仪器，别去动生产码）",
            read.systemWantsDark
        )
    }

    /**
     * **渲染级**判据（第⑤栏的正题）：系统在要深色，app 解析到的仍然是那一套亮色板。
     *
     * 读的是 `LoveBrainTheme` 里 `MaterialTheme.colorScheme` 的真实值——所有 M3 默认色
     * （`Card` 底色、`AlertDialog`、`TextButton` 的字色……）都从这一条链取，
     * 所以这一条说的就是"屏幕上会是什么色"，不是"源码里写了什么"。
     */
    @Test
    @Config(qualifiers = "sw600dp-w600dp-h1200dp-normal-long-night-mdpi")
    fun `under a night request the app still resolves the light scheme`() {
        mountThemeProbe()
        val read = requireNotNull(reading) { "组合没跑起来，读不到任何配色" }
        assertTrue("夜请求没到位，这一格就是在空过（哨兵格会说明为什么）", read.systemWantsDark)

        assertEquals("background 不是 SurfaceBase（浅底）", SurfaceBase, read.background)
        assertEquals("surface 不是 SurfaceCard（卡片比背景白一档）", SurfaceCard, read.surface)
        assertEquals("onSurface 不是 TextPrimary（深字）", TextPrimary, read.onSurface)
        assertEquals("surfaceVariant 不是 SurfaceInset", SurfaceInset, read.surfaceVariant)
        assertEquals("primary 不是 Primary", Primary, read.primary)
        // 上面五条钉的是「就是这套 token」；下面三条钉的是**性质**——
        // 就算有人把 token 换掉，夜档里也不许翻成深底浅字（二选一里选定的那一个）。
        assertTrue("夜档下解析到的背景亮度只有 ${read.background.luminance()}，已经是深色底",
            read.background.luminance() > 0.5f)
        assertTrue("夜档下解析到的卡片亮度只有 ${read.surface.luminance()}，已经是深色底",
            read.surface.luminance() > 0.5f)
        assertTrue(
            "夜档下 onSurface 亮度 ${read.onSurface.luminance()} 不比卡片暗 = 深底浅字那一套回来了",
            read.onSurface.luminance() < read.surface.luminance()
        )
    }

    /**
     * 挂 [LoveBrainTheme]：把解析到的配色抄进 [reading]，同时画一块**真的会涂底色**的盒，
     * 让像素那一格有东西可采。宽度走 `UiMatrix`（600dp 那一档，与类级 qualifier 串一致），
     * 不把尺寸常量写死在判据里。
     */
    private fun mountThemeProbe(matrix: UiMatrix = UiMatrix(600)) {
        reading = null
        rule.setContent {
            val deviceDensity = LocalDensity.current.density
            matrix.RenderIn(deviceDensity) {
                LoveBrainTheme {
                    val scheme = MaterialTheme.colorScheme
                    reading = SchemeReading(
                        systemWantsDark = isSystemInDarkTheme(),
                        background = scheme.background,
                        surface = scheme.surface,
                        onSurface = scheme.onSurface,
                        surfaceVariant = scheme.surfaceVariant,
                        primary = scheme.primary
                    )
                    Box(
                        Modifier
                            .testTag(SURFACE_TAG)
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(scheme.background)
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private companion object {
        /** 只在本文件里用的锚点：那块涂了主题底色的盒 */
        const val SURFACE_TAG = "lb_theme_surface_probe"
    }
}
