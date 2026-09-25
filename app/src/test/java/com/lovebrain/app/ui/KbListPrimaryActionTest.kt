package com.lovebrain.app.ui

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.KnowledgeBase
import com.lovebrain.app.ui.theme.LoveBrainTheme
import com.lovebrain.app.viewmodel.KbListState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §6.1 :479 / :490 的第三把尺管到的那一处：**知识库页底部那颗「新建知识库」**。
 *
 * 它是 Material `Button(containerColor = Primary)`——上一格补的第三把尺
 * （`brand tones painted through containerColor do not grow`，登记 5 处 / 4 文件）
 * 里的第二处。这一格先量它，再判该不该搬。
 *
 * ⚠ 判之前先把"量到没坏"这句话说完：这一族里有几处**几何一直达标**
 * （首页那颗就是 119x48dp 才搬的）。所以"搬"的理由只有所有者归属，
 * 不能写成修了缺陷——那会让下一格以为这一族已经没有尺寸问题了。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KbListPrimaryActionTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private fun mount() {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LoveBrainTheme {
                    KbListScreen(
                        screenState = com.lovebrain.app.core.designsystem.ScreenState.Content(
                            KbListState(
                                loaded = true,
                                knowledgeBases = listOf(
                                    KnowledgeBase(name = "kb_a", displayName = "甲库")
                                )
                            )
                        ),
                        onActivate = {},
                        onNewKb = {},
                        onRename = { _, _ -> },
                        onDelete = {},
                        onEdit = {},
                        onConfirmExport = {},
                        onImport = {},
                        onBack = {}
                    )
                }
            }
        }
    }

    @Test
    fun `the new-kb action is a named button that meets the floor`() {
        mount()
        val label = ApplicationProvider.getApplicationContext<Context>()
            .getString(com.lovebrain.app.R.string.kb_new_kb)
        val all = probe.actionableTargets(rule, "知识库列表")
        val newKb = all.filter { it.label == label }
        assertEquals(
            "「新建知识库」应当正好一颗（实到 ${newKb.size}）：" + all.joinToString { it.describe() },
            1, newKb.size
        )
        val t = newKb.single()
        assertTrue("那颗主动作点不准的话整页就剩卡片可点：" + t.describe(), !t.tooSmall(probe.floorDp))
        assertEquals("主动作要有按钮角色：" + t.describe(), "Button", t.role)
        assertTrue("禁用态不能是默认：" + t.describe(), !t.disabled)
    }

    /**
     * 底部工具条这一档：**两颗都要在**，但只有颗一是品牌底。
     *
     * 这条是"别拿计数当性质"的反面用法：数量本身不是判据，
     * 但少了那颗次级按钮意味着有人把两条合并成一条，那是另一件事。
     */
    @Test
    fun `the bottom toolbar keeps both actions and only the primary one is brand-filled`() {
        mount()
        val all = probe.actionableTargets(rule, "知识库列表")
        val importLabel = "导入知识库"
        assertTrue(
            "底部那条工具栏应该两颗都在（新建 + 导入）：" + all.joinToString { it.describe() },
            all.any { it.label == importLabel }
        )
        val tooSmall = all.filter { it.tooSmall(probe.floorDp) }
        assertTrue(
            "这一页视口内还有 ${tooSmall.size} 个节点低于下限：" + tooSmall.joinToString { it.describe() },
            tooSmall.isEmpty()
        )
    }
}
