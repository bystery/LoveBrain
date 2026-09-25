package com.lovebrain.app.core.designsystem

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.1 `LbStatusBadge`：状态词与颜色只有这一张表，且**读屏听得出这是一枚状态、
 * 状态变了会补播**（§6.5 第②栏）。
 *
 * 三件事各自都有反面：
 * - 五档各有一句中文 + 一句英文（`values-en` 少一条就静默回落到中文，所以两边逐档比）；
 * - 五档的**说法互不相同**（"未启动"与"未授权"若并成一句，卡片就少告诉用户一件事）；
 * - 胶囊带 `contentDescription` 与 `liveRegion = Polite`——后者是"从运行中变成已隐藏时
 *   TalkBack 自己会念"，少了它，视障用户只能主动去找那一格。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LbStatusBadgeTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private val badge: MutableState<LbStatus> = mutableStateOf(LbStatus.Running)

    private fun resFor(tag: String): Resources {
        val cfg = Configuration(app.resources.configuration)
        cfg.setLocales(LocaleList.forLanguageTags(tag))
        return app.createConfigurationContext(cfg).resources
    }

    private fun mountBadge() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LbStatusBadge(status = badge.value)
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    @Test
    fun `the badge carries a spoken name and announces changes`() {
        mountBadge()
        val node = rule.onNodeWithTag(LbStatusTags.BADGE)
            .fetchSemanticsNode("状态胶囊不在语义树上（tag=${LbStatusTags.BADGE}）")

        val expected = app.getString(LbStatus.Running.labelRes)
        assertEquals(
            "胶囊自己没带上状态词，读屏只会念一段未知文本",
            listOf(expected),
            node.config.getOrNull(SemanticsProperties.ContentDescription)
        )
        assertEquals(
            "状态没有 liveRegion：从「运行中」变成「已隐藏」时，TalkBack 不会补播，" +
                "视障用户得自己去找那一格",
            LiveRegionMode.Polite,
            node.config.getOrNull(SemanticsProperties.LiveRegion)
        )
    }

    @Test
    fun `switching status switches the spoken word`() {
        mountBadge()
        val first = app.getString(LbStatus.Running.labelRes)

        rule.runOnIdle { badge.value = LbStatus.Hidden }
        rule.waitForIdle()
        val second = rule.onNodeWithTag(LbStatusTags.BADGE).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("+")

        assertNotEquals("换了 badge 却没换词——说明这一格读的是缓存而不是 status 参数", first, second)
        assertEquals(app.getString(LbStatus.Hidden.labelRes), second)
    }

    @Test
    fun `every status has its own words in both languages`() {
        val zh = resFor("zh")
        val en = resFor("en")
        LbStatus.values().forEach { status ->
            val a = zh.getString(status.labelRes)
            val b = en.getString(status.labelRes)
            assertTrue("$status 有一边是空的：zh=$a en=$b", a.isNotBlank() && b.isNotBlank())
            assertNotEquals(
                "$status 中英两边解析出同一句 ⇒ values-en 根本没有这条，系统静默回落到中文",
                a, b
            )
            assertTrue("$status 在英文下仍是中文：$b", b.none { it in '一'..'鿿' })
        }
        val labels = LbStatus.values().map { zh.getString(it.labelRes) }
        assertEquals(
            "五档状态词必须互不相同（并档就少说一件事）：" + labels,
            labels.size, labels.distinct().size
        )
    }

    @Test
    fun `the color table is one place and distinguishes the new state`() {
        assertEquals(Primary, LbStatus.Running.color)
        assertEquals(Error, LbStatus.WindowMissing.color)
        assertNotEquals(
            "WindowMissing 若与运行中同色，就等于把一次失败画成一切正常",
            LbStatus.Running.color, LbStatus.WindowMissing.color
        )
    }
}
