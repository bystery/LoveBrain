package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 八项没生成齐时，提示条里那颗「重试」**真的把回调打出去**（指导书 第13节第1条：
 * 「应坦诚显示未完整生成与可用的重试」）。
 *
 * 为什么单独挂这一颗：`ResultArea` 的 `onRetry` 参数在 2026-10-08 之前一直是**有槽无行为**——
 * 宿主 `LoveBrainPanelScreen.kt:835` 把 `viewModel.retryCurrentReply()` 传进来了，
 * 提示条自己却只写了一句「…，可重新生成」，画面里没有任何能点的东西。
 * 那句话是空承诺，而空承诺在这一屏属于第5节第2条点名的「只有描述、没有对应动作」。
 * 本轮把出口做成控件，这一格钉的就是"控件在场且一次点击恰好发一次"。
 *
 * 判据全部读**几何之外的语义**与回调计数，不读源码字符串：
 * 1. 提示条里有一颗带重试文案的可点节点（文案从资源取，不写死字面量——标签改了它跟着改）；
 * 2. 点一次 → 回调恰好一次；再点一次 → 两次。一次点击发两发请求同样是回归
 *    （第13节第2条「命令只发一次」、第16节第1条第 2 项「不重复发请求」）；
 * 3. 提示文字本体仍在场，出口不是把说明换掉了。
 *
 * 坏实现怎么红：把 `ReplyIncompleteNotice` 退回只画一行 `Text`（本轮改之前的形状），
 * 第 1 条的节点数就是 0；把 `onClick` 写成同时打两下宿主，第 2 条红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw360dp-w360dp-h640dp-long-xhdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplyIncompleteNoticeRetryTest {

    @get:Rule
    val rule = createComposeRule()

    /** 资源里的重试文案——不在测试里抄第二份字面量 */
    private val retryLabel: String by lazy {
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.action_retry)
    }

    private val noticeText = "未生成 1/8 项：清醒"

    @Test
    fun `the incomplete notice carries a real retry control that fires once per tap`() {
        var calls = 0
        rule.setContent { ReplyIncompleteNotice(text = noticeText, onRetry = { calls++ }) }
        rule.waitForIdle()

        val controls = rule.onAllNodes(hasText(retryLabel)).fetchSemanticsNodes()
        assertEquals(
            "提示条必须给出一颗真能点的重试出口（有描述没动作＝本轮要修的那一格）",
            1,
            controls.size
        )

        rule.onAllNodes(hasText(retryLabel))[0].performClick()
        assertEquals("一次点击只发一次再生成请求", 1, calls)

        rule.onAllNodes(hasText(retryLabel))[0].performClick()
        assertEquals("第二次点击同样只发一次", 2, calls)

        // 说明本身不许被出口顶掉：两者同屏在场
        assertEquals(
            "缺了哪几项的说明仍要在场",
            1,
            rule.onAllNodes(hasText(noticeText)).fetchSemanticsNodes().size
        )
    }
}
