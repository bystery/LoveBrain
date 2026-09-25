package com.lovebrain.app.ui.panel

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone
import com.lovebrain.app.core.designsystem.LbModalSheet
import com.lovebrain.app.core.designsystem.LbModalSheetActions
import com.lovebrain.app.core.designsystem.LbModalSheetTitle
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
 * 先量后写：**这台仪器看不看得见自画浮层里的节点**。
 *
 * 看得见，才谈得上把 §6.5 那两把尺（48dp 下限、可交互节点要有可读名字）伸进浮层。
 * 这格本身不是守卫——它绿只证明"测得到"；断言在 `LbModalSheetTest`。
 *
 * **旧形状（`ui/panel/PanelModalHost.kt`，已退役进 `_temp/`）的实测值留在这里当"改之前"的证据**：
 *   「」 role=无 尺寸 360x1000dp   ← 遮罩自己是一颗无名 clickable 节点
 *   「PROBE_SHEET_TITLE」 331x84dp ← 卡片"拦截点击"的 clickable 把标题合并成了"一颗按钮"
 *   「取消」 disabled 尺寸 48x26dp  ← 低于 48dp 下限
 *   「」 disabled 尺寸 24x22dp      ← `confirmLabel = ""` 画出的无名按钮
 * 即：**4 个可交互节点里 2 个没有可读名字、2 个高度低于下限**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheetProbeTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private val probe by lazy { SemanticsProbe(app.resources.displayMetrics.density) }

    @Test
    fun `measure what the panel modal currently hands the user`() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LbModalSheet(onDismissRequest = {}) {
                    LbModalSheetTitle("PROBE_SHEET_TITLE")
                    Text("PROBE_SHEET_BODY")
                    LbModalSheetActions(
                        listOf(
                            LbDialogAction("取消", {}, tone = LbDialogActionTone.Muted),
                            LbDialogAction("确认", {})
                        )
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        val targets = probe.actionableTargets(rule, "面板弹层")
        println("PROBE 面板弹层可交互节点：")
        targets.forEach { println("   " + it.describe()) }
        val unlabeled = targets.filter { !it.labeled }
        println("PROBE 没有可读名字的节点数 = ${unlabeled.size}；" +
            "高度小于 48dp 的 = ${targets.count { it.heightDp + 0.5f < 48f }} / ${targets.size}")
        assertTrue("仪器看不见弹层里的节点就别往下写", targets.isNotEmpty())
    }
}
