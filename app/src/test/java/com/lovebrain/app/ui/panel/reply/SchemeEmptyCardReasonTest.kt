package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.model.SchemeSource
import com.lovebrain.app.model.ReplyDirection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §11.2 三因分家的**空卡文案那一半**：八项池里每一张空卡念的是自己那一条成因。
 *
 * 空卡有两张脸，由 `Scheme.notSuitable` 决定（数据侧分家判据在
 * `LoveBrainResponse.directionSchemes`，提示语那一半在 [replyCompletenessNotice]）：
 *
 * | 这一位在 JSON 里是什么 | notSuitable | 卡片念 |
 * |---|---|---|
 * | 输出了 null（协议允许，format.md directions 那一节） | true | 「本轮不适合」 |
 * | 这一位没出现（数组比四短 / 整个字段缺失） | false | 「未生成」 |
 * | 出现了但是空白串 | false | 「未生成」 |
 * | 四风格里空白（四风格没有合法 null 这一档） | false | 「未生成」 |
 *
 * ⚠ 旧混判态这几格会红在**两张脸互换**上：null 被当成没生成（少一张「不适合」）、
 * 空白串被当成不适合（多一张「不适合」）；操作行那格的计数也从 5 变 8。
 * 整池那格按**八项宇宙**取数，不挑单张卡，免得拿局部数冒充全体。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SchemeEmptyCardReasonTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val notSuitableText: String get() = ctx.getString(R.string.scheme_not_suitable)
    private val notGeneratedText: String get() = ctx.getString(R.string.scheme_not_generated)

    /**
     * 八项池：四风格里「清醒」空白（没生成）；四方向里跟进有正文、展开=null（合法不适合）、
     * 表达=空白串（没生成），转向那一位**根本没出现**（数组只有三项 = 缺位，也没生成）。
     * ⇒ 屏幕上读到的必须是 1 张「本轮不适合」+ 3 张「未生成」（空白串与缺位各占一张），
     * 而不是旧混判态那种「空卡一律不适合」。
     */
    private val pool: List<Scheme> = LoveBrainResponse(
        response = ReplySchemes(
            recommended = "BODY_A",
            badBoy = "",
            playful = "BODY_C",
            warm = "BODY_D"
        ),
        // 四方向只给三位：跟进有正文、展开=null、表达=空白串；转向那一位缺位
        directions = listOf("BODY_F", null, "   ")
    ).mergedEightItems

    private fun cardOf(key: String): Scheme = pool.first { it.identity.key == key }

    private fun mount(vararg schemes: Scheme) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                Column {
                    schemes.forEach { scheme ->
                        Box(modifier = Modifier.testTag(scheme.identity.key)) {
                            SchemeCollapsedBlock(
                                isEmpty = scheme.reply.isBlank(),
                                reply = scheme.reply,
                                bodyColor = TextPrimary,
                                feedback = SchemeFeedback.NONE,
                                onCopy = {},
                                onFeedback = {},
                                identityKey = scheme.identity.key,
                                notSuitable = scheme.notSuitable,
                                modifier = Modifier.size(140.dp, 70.dp)
                            )
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /** 数据侧的形状先钉住：八项宇宙、四张空卡里只有一张是合法 null */
    @Test
    fun `the pool holds eight slots of which one is a legal null and three are ungenerated`() {
        assertEquals("四风格 + 四方向 = 8 个卡位", 8, pool.size)
        val empties = pool.filter { it.reply.isBlank() }
        assertEquals(4, empties.size)
        assertEquals(listOf("展开"), empties.filter { it.notSuitable }.map { it.title })
        assertEquals(
            listOf("清醒", "表达", "转向"),
            empties.filter { !it.notSuitable }.map { it.title }
        )
        assertEquals(
            "转向那一位是缺位（数组只有三项），不是模型输出的 null",
            false,
            cardOf("DIRECTION:S").notSuitable
        )
        assertEquals(ReplyDirection.SHIFT, ReplyDirection.byTag(cardOf("DIRECTION:S").tag))
        assertEquals(SchemeSource.DIRECTION, cardOf("DIRECTION:S").source)
    }

    /** 合法 null 的卡：只念「本轮不适合」，不念「未生成」 */
    @Test
    fun `the legal null card says not suitable and nothing else`() {
        mount(cardOf("DIRECTION:E"))
        rule.onNodeWithText(notSuitableText).assertExists()
        rule.onNodeWithText(notGeneratedText).assertDoesNotExist()
    }

    /** 方向位的空白串：念「未生成」——旧混判态把它念成了「不适合」 */
    @Test
    fun `the blank direction card says not generated`() {
        mount(cardOf("DIRECTION:X"))
        rule.onNodeWithText(notGeneratedText).assertExists()
        rule.onNodeWithText(notSuitableText).assertDoesNotExist()
    }

    /** 方向位缺位：同样念「未生成」 */
    @Test
    fun `the absent direction card says not generated`() {
        mount(cardOf("DIRECTION:S"))
        rule.onNodeWithText(notGeneratedText).assertExists()
        rule.onNodeWithText(notSuitableText).assertDoesNotExist()
    }

    /** 四风格没有合法 null 这一档：空白就是没生成 */
    @Test
    fun `a blank style card says not generated`() {
        mount(cardOf("STYLE:B"))
        rule.onNodeWithText(notGeneratedText).assertExists()
        rule.onNodeWithText(notSuitableText).assertDoesNotExist()
    }

    /** 有正文的卡：正文在位，两张空卡文案都不出现 */
    @Test
    fun `a card with a body shows its own body and neither placeholder`() {
        mount(cardOf("STYLE:A"), cardOf("DIRECTION:F"))
        rule.onNodeWithText("BODY_A").assertExists()
        rule.onNodeWithText("BODY_F").assertExists()
        rule.onNodeWithText(notSuitableText).assertDoesNotExist()
        rule.onNodeWithText(notGeneratedText).assertDoesNotExist()
    }

    /**
     * 整池读数（带宇宙的数量判据）：一张「本轮不适合」+ 三张「未生成」，
     * 而操作行只属于有正文的那四张——空卡不画复制/赞/踩。
     */
    @Test
    fun `the whole pool shows one not-suitable card three not-generated cards and four action rows`() {
        mount(*pool.toTypedArray())
        rule.onAllNodesWithText(notSuitableText).assertCountEquals(1)
        rule.onAllNodesWithText(notGeneratedText).assertCountEquals(3)

        val copyName = ctx.getString(R.string.panel_copy)
        val likeName = ctx.getString(R.string.a11y_scheme_like)
        val dislikeName = ctx.getString(R.string.a11y_scheme_dislike)
        rule.onAllNodesWithContentDescription(copyName).assertCountEquals(4)
        rule.onAllNodesWithContentDescription(likeName).assertCountEquals(4)
        rule.onAllNodesWithContentDescription(dislikeName).assertCountEquals(4)

        // 正文四张都在位（证明上面那句「空卡没有动作」不是因为整屏没画）
        listOf("BODY_A", "BODY_C", "BODY_D", "BODY_F").forEach { body ->
            rule.onAllNodesWithText(body).assertCountEquals(1)
        }
        rule.onNodeWithTag("DIRECTION:E").assertExists()
    }
}
