package com.lovebrain.app.core.designsystem

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 先量后写：这台 JVM 语义树仪器**看不看得见 AlertDialog 里的节点**？
 *
 * 为什么要单独有一格：§6.5 那句"所有可点击节点 ≥48dp"要收到浮层上，
 * 得先知道本机能测到哪儿。看不见就别写"已验"，看得见才谈得上守卫——
 * 这格不是守卫，是**探尺**，它绿只证明仪器看得见，不证明任何生产行为。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DialogProbeTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }

    @Test
    fun `the jvm instrument can see into an AlertDialog`() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("PROBE_TITLE") },
                    text = { Text("PROBE_BODY") },
                    confirmButton = {
                        TextButton(
                            onClick = {},
                            modifier = Modifier.testTag("PROBE_CONFIRM")
                        ) { Text("PROBE_CONFIRM_LABEL") }
                    }
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        val found = rule.onAllNodesWithText("PROBE_CONFIRM_LABEL").fetchSemanticsNodes()
        assertTrue(
            "仪器看不见对话框里的节点 ⇒ §6.5 那把尺对浮层就是瞎的，只能等设备（先量清楚再决定怎么写）",
            found.isNotEmpty()
        )
        val targets = probe.actionableTargets(rule, "对话框")
        assertTrue("对话框里至少该量到那颗按钮：" + targets.joinToString { it.describe() },
            targets.isNotEmpty())
        println("PROBE 对话框按钮实测：" + targets.joinToString { it.describe() })
    }
}
