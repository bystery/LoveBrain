package com.lovebrain.app.ui.panel.reply

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.test.junit4.createComposeRule
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 输入框交给 IME 的会话契约：聚焦后启动的输入会话必须带 MULTI_LINE（imeOptions.singleLine=false）。
 *
 * 为什么单测要钉这一颗：真机 IME 在单行编辑器（EditorInfo 缺 TYPE_TEXT_FLAG_MULTI_LINE）里
 * 粘贴时会把换行拍平成空格，整段微信转录落到 addDraftAuto 成了一行、拆不出条。
 * Robolectric 的 performTextInput 绕过 IME，量不到这件事——所以这里直接录下框架递给
 * TextInputService 的 imeOptions，断言 singleLine=false。singleLine=true 那一版在这一格红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplyInputImeContractTest {

    @get:Rule
    val rule = createComposeRule()

    /** 录下框架启动输入会话时递来的 imeOptions；其余平台动作空实现（本格只量会话参数） */
    private class RecordingPlatformInput : PlatformTextInputService {
        var imeOptions: ImeOptions? = null
        var starts = 0

        override fun startInput(
            value: TextFieldValue,
            imeOptions: ImeOptions,
            onEditCommand: (List<EditCommand>) -> Unit,
            onImeActionPerformed: (ImeAction) -> Unit
        ) {
            this.imeOptions = imeOptions
            starts++
        }

        override fun stopInput() {}
        override fun showSoftwareKeyboard() {}
        override fun hideSoftwareKeyboard() {}
        override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {}
    }

    @Test
    fun focusedFieldStartsAnImeSessionWithTheMultiLineFlag() {
        val recorder = RecordingPlatformInput()
        val focusRequester = FocusRequester()
        rule.setContent {
            CompositionLocalProvider(LocalTextInputService provides TextInputService(recorder)) {
                UiMatrix(360).RenderIn(LocalDensity.current.density) {
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    PanelTextInput(
                        value = "",
                        onValueChange = {},
                        placeholder = "输入她说的话…",
                        focusRequester = focusRequester
                    )
                }
            }
        }
        rule.waitForIdle()
        var pumpedMs = 0L
        while (recorder.starts == 0 && pumpedMs < 2_000L) {
            rule.mainClock.advanceTimeBy(16L)
            pumpedMs += 16
        }

        assertTrue("聚焦输入框后必须启动一次 IME 输入会话（否则量不到递给 IME 的参数）", recorder.starts >= 1)
        val imeOptions = recorder.imeOptions!!
        assertFalse(
            "IME 会话必须是多行（singleLine=false → MULTI_LINE），真机粘贴才保留换行；" +
                "singleLine=true 会让 IME 把转录拍平成一行",
            imeOptions.singleLine
        )
        assertTrue(
            "动作键保持 Done（硬件回车/IME 动作键行为不因换行修复而改变）",
            imeOptions.imeAction == ImeAction.Done
        )
    }
}