package com.lovebrain.app.ui.kb

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.domain.OnboardingBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「生成中」只许有一个真源（`KnowledgeBaseViewModel.creating`），而且它**必须能回来**。
 *
 * 修之前这一屏自己 `remember` 一颗 `generating`：点「完成」先置真再回调，可回调那头
 * 未配模型 / 已在飞 / 一开始就失败这三条路都不会开建，也没有任何代码把它复位——
 * 用户看到的是选项全灰、底部永远"生成中"，刚答完的五步问卷再也交不出去。
 *
 * 这一格钉两件事：
 * 1. `creating = true` 时底部换到生成中那一档、选项禁用（"在忙"要看得见）；
 * 2. `creating` 回到 false 时**当场解锁**（失败/未配模型之后答案还在，可以直接重试）——
 *    本地那颗 if 若长回来，第二条立刻红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KbOnboardingWizardCreatingStateTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val probe by lazy { SemanticsProbe(ctx.resources.displayMetrics.density) }

    /** 生成中那一档的文案（这一屏尚未还债的内联中文，锚点按现有形状取） */
    private val generatingLabel = "生成中，点击取消"

    private fun mount(creating: MutableState<Boolean>) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                OnboardingScreen(
                    onDismiss = {},
                    onSkip = {},
                    onComplete = {},
                    onCancelGenerating = {},
                    creating = creating.value
                )
            }
        }
        rule.waitForIdle()
    }

    private fun textCount(text: String): Int =
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    private fun optionTargets(): List<SemanticsProbe.Target> =
        probe.actionableTargets(rule, "建库向导-创建态")
            .filter { it.role == "RadioButton" || it.role == "Checkbox" }

    @Test
    fun `the wizard follows the creation state and unlocks again when creation is not running`() {
        val creating = mutableStateOf(false)
        mount(creating)

        assertEquals("没开建时不该出现生成中那一档", 0, textCount(generatingLabel))
        val idle = optionTargets()
        assertTrue("首屏至少要量到一颗选项（否则这一格在空转）", idle.isNotEmpty())
        assertTrue(
            "没开建时选项必须可点：" + idle.joinToString { it.describe() },
            idle.none { it.disabled }
        )

        rule.runOnIdle { creating.value = true }
        rule.waitForIdle()
        assertEquals("开建之后底部要换成生成中那一档", 1, textCount(generatingLabel))
        // 选项卡在禁用档整颗摘掉点击语义（`OnboardingOptionCard` 只在 enabled 时挂 clickable），
        // 所以"忙"的读数是**可点选项为 0**，而题面与选项文字仍在树上
        assertEquals(
            "生成中选项不许还可点（否则同一趟会答进第二份答案）：" +
                optionTargets().joinToString { it.describe() },
            0, optionTargets().size
        )
        assertEquals(
            "生成中选项文字仍在树上（禁用不等于消失）",
            1, textCount(OnboardingBank.q1.options.first().text)
        )

        // 失败 / 未配模型 / 在飞返回：VM 那一格回落，这一屏必须跟着解锁
        rule.runOnIdle { creating.value = false }
        rule.waitForIdle()
        assertEquals("回落之后生成中那一档必须退场", 0, textCount(generatingLabel))
        val again = optionTargets()
        assertTrue("回落之后问卷还在（答案不许被这一趟带走）", again.isNotEmpty())
        assertEquals(
            "回落之后选项必须重新可点（本地那颗永不复位的 generating 会在这里红）：" +
                again.joinToString { it.describe() },
            idle.size, again.size
        )
        // 证人：第一题的题面一直没被生成态吃掉
        assertEquals("问卷题面仍在树上", 1, textCount(OnboardingBank.q1.title))
    }
}
