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
import com.lovebrain.app.ui.panel.reply.RecordSentDialog
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

    /**
     * 第二把探尺：**没并进 LbModalSheet 之前**，`RecordSentDialog` 自己那套遮罩与两颗按钮长什么样。
     * 迁移会改变这一格量到的东西，所以**先跑一次把旧值抄在这里留档**（账本 §30.2）：
     *   「记录实际发送」 360x1000dp ← 遮罩自己是一颗 clickable 节点，还把标题合并成了自己的名字
     *   「粘贴或输入你实际发送的话」 304x56dp ← 输入框，这一颗是合格的
     *   「取消」 **28x19dp**、「确认已发送并记录」 **96x19dp** ← 裸 Text + padding(vertical = xs)，
     *     比 §29 那套面板浮层的 26dp 还矮一半
     * （写这段注释之前我先填过 66x20/152x20 两个"看着像"的数——那没量过，已换成实跑输出。）
     *
     * **并进 LbModalSheet 之后重跑同一格**：
     *   「粘贴或输入你实际发送的话」 307x56dp | 「取消」 **48x48dp** |
     *   「确认已发送并记录」 **121x48dp**（此时正文为空，所以它带 disabled —— 这是刻意的：
     *   旧形状那里它长得能点、点下去被 `if (text.isNotBlank())` 静默吞掉）
     *   ⇒ 那颗 360x1000 的整屏"按钮"没了，两颗出口都到下限。
     */
    @Test
    fun `measure what the record-sent float currently hands the user`() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                RecordSentDialog(prefill = "", saving = false, onConfirm = {}, onDismiss = {})
            }
        }
        rule.mainClock.advanceTimeBy(16L)
        val targets = probe.actionableTargets(rule, "记录实际发送")
        println("PROBE 记录实际发送：" + targets.joinToString(" | ") { it.describe() })
        assertTrue("量不到节点就别往下写", targets.isNotEmpty())
    }

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
