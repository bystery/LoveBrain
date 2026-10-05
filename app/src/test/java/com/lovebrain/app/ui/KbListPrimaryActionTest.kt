package com.lovebrain.app.ui

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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
 * 第6节第1条 :479 / :490 的第三把尺管到的那一处：**知识库页底部那颗「新建知识库」**。
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

    /**
     * 卡片行内动作这一族：**原判据量的是旧母版的两种自绘透明盒，2026-10-06 L1b 作废其档位**。
     *
     * 原判据（此处留档再判废）：旧 `KnowledgeBaseActivity.kt#KbCard` 第一行的改名与删除是两颗
     * "横向 48、纵向吃行版式那一行字高（v1.3.1 量到 22dp）"的自绘透明盒，当时按逐族显式传档
     * 给了 `rowActionHeightDp = 22f` 一把特尺。作废理由：基线 v1.1 §3.5/§3.6 把行内动作收进
     * `LbTextAction` 的 `RowCapsule` 一档（可见 32 / **热区两轴 48**），页面自绘盒这个形状已经
     * 不存在——22 那一档从此没有可量的对象；留着特尺等于给"把盒垫回行高"留门。
     * 新判据回退成什么会红：① 任一动作退回自绘盒（某一轴热区 < 全站下限 48）；
     * ② 改名/编辑/导出/删除四件事少任何一件（label 数不到那颗）；③ 有人把第四颗动作塞进
     * `actions` 被公共件截掉——"重命名"整颗从树上消失，这一格红。
     * （旧档"甲库"那颗 label 来自"点标题行改名"的合并读屏名，现在改名是一颗有自己名字的
     * 胶囊，标题只是标题——这条回退成旧写法时下面按 label 找胶囊的断言会红，不会假绿。）
     */
    private var activateFired = 0

    private fun mount(active: Boolean = false) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                LoveBrainTheme {
                    KbListScreen(
                        screenState = com.lovebrain.app.core.designsystem.ScreenState.Content(
                            KbListState(
                                loaded = true,
                                knowledgeBases = listOf(
                                    KnowledgeBase(name = "kb_a", displayName = "甲库")
                                ),
                                activeName = if (active) "kb_a" else null
                            )
                        ),
                        onActivate = { activateFired++ },
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
     * 2026-10-06 L1b：卡片行内两颗旧自绘盒（「甲库」改名 +「删除知识库」图标）已被
     * `RowCapsule` 一族替代，这一格不再逐族传 22dp 特尺——页面上**每一个**可交互节点
     * 都按全站下限量。回退成什么会红：任何一颗动作退回"可见与热区混在一层"的旧写法、
     * 或两条按钮并成一条。
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
        val tooSmall = probe.laid(all).filter { it.tooSmall(probe.floorDp) }
        assertTrue(
            "这一页视口内还有 ${tooSmall.size} 个节点低于全站下限（两轴都要 ${probe.floorDp.toInt()}dp；" +
                "旧档那把 22dp 特尺已随自绘透明盒退场）：" + tooSmall.joinToString { it.describe() },
            tooSmall.isEmpty()
        )
    }

    /**
     * 卡上四件事一颗都不许少：重命名 / 编辑 / 导出 / 删除，每颗恰有一颗自己名字的胶囊。
     *
     * 「编辑」「删除」的读屏名来自仓里**既有**资源 `a11y_action_edit` / `a11y_action_delete`
     * （判据跟着资源走，不在测试里抄第二份中文）。回退成什么会红：
     * ① 第四颗动作（重命名）被"收进三颗"这句话偷偷删掉——数不到那颗 label；
     * ② 删除那颗换了语气档或并名——`destructive` 档的 label 就是资源里那个「删除」，
     *    改回旧的图标盒（contentDescription「删除知识库」）这一格当场数不到。
     */
    @Test
    fun `all four library actions are present as named capsules`() {
        mount()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val editLabel = context.getString(com.lovebrain.app.R.string.a11y_action_edit)
        val deleteLabel = context.getString(com.lovebrain.app.R.string.a11y_action_delete)
        // 这两颗的名字 2026-10-06 起也走资源（`a11y_action_export` / `a11y_action_rename`，
        // 字面量预算那一栏的债）：判据必须跟着读资源，**不许抄中文**——这台 JVM 解析的是英文，
        // 抄中文的那一句当场假红（W8 实测：树上念的是 Export / Rename）。
        val exportLabel = context.getString(com.lovebrain.app.R.string.a11y_action_export)
        val renameLabel = context.getString(com.lovebrain.app.R.string.a11y_action_rename)
        val labels = probe.laid(probe.actionableTargets(rule, "知识库列表·卡上动作")).map { it.label }
        listOf(editLabel, exportLabel, deleteLabel, renameLabel).forEach { want ->
            assertEquals(
                "卡上「$want」应当正好一颗（实到 ${labels.count { it == want }}）：" + labels.joinToString(),
                1, labels.count { it == want }
            )
        }
    }

    /**
     * 整卡一处操作 = 激活：非当前库可点、报 Button、点了真落回调。
     * 旧档靠 `clickable(enabled = !isActive)` 让当前库也挂着一颗点不动的活按钮——
     * 新档把那一格交给公共件（`onClick = null` 时不挂 `Role.Button`），所以拆成两格各判一边。
     *
     * ⚠ 2026-10-06 L1b 后本格判据重写（C 档：不是链断，是点击点的形状变了）：
     * 接线现场——`KnowledgeBaseActivity.kt:434` `onClick = if (isActive) null else onActivate`、
     * `LbListCard.kt:186-190` 把 clickable 挂在卡底自己那一层——都在，激活链没断。
     * 红的真因：基线 §3.6 把行内动作收进 `RowCapsule`（热区**两轴 48**），这张矮卡上四颗胶囊
     * （动作行三颗 + detail「重命名」）的 48dp 热区盖住了卡底的几何中心；
     * `performClick` 走中心点注入，中心落在子胶囊热区上就被那颗动作正当吃掉——
     * "点中心落了 0 次"是新形状的事实，不是整卡失效的证据。
     * 牙一颗没丢，拆成两枚点各判一件事：
     *  ① 真点打在标题那一行（卡体真空区，绝不会被胶囊热区盖住）：
     *     谁把 clickable 从卡底摘了/链断了 ⇒ 注入无人接住，这一句红（0 次）；
     *     注：merged 树里 clickable 会合并子孙（标题那颗节点不再单独可见），所以取 unmerged 树。
     *  ② CARD 节点自己那份 OnClick 动作用 `performSemanticsAction` 直接触发：
     *     谁把"整卡可点"只挂到某个子节点上 ⇒ 动作解析到那颗子件（编辑/重命名被按下去，
     *     激活一动不动），这一句红；
     *  ③ 两枚点各落**恰好**一次（+1、+2 逐次对数）⇒ 双订阅当场红；
     *  ④ 「把 onClick 恒传（当前库能把自己再激活一次）」仍由另一格
     *     `the active library card is not announced as a dead button` 判红。
     * 回退成旧写法会红：任何一步少落或多落，数字都对不上。
     */
    @Test
    fun `an inactive library card is one actionable thing`() {
        mount(active = false)
        val before = activateFired
        // ① 卡体真空区的真点：标题那一行
        rule.onNodeWithTag(
            com.lovebrain.app.core.designsystem.LbListCardTags.TITLE,
            useUnmergedTree = true
        ).performClick()
        rule.waitForIdle()
        assertEquals(
            "点卡体真空区（标题那一行）应当落一次激活回调——0 意味着整卡的 clickable 不在卡底上" +
                "或链断了（中心点那一版红是几何：48dp 胶囊热区盖住了矮卡中心，不是这条链）",
            before + 1, activateFired
        )
        // ② 整卡那份点击语义归 CARD 节点自己：直接触发它的 OnClick 动作
        rule.onNodeWithTag(com.lovebrain.app.core.designsystem.LbListCardTags.CARD)
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals(
            "整卡的 OnClick 必须归卡底自己且恰好再落一次激活——" +
                "谁把整卡可点并到子胶囊上，这一句接到的就是编辑/重命名而不是激活",
            before + 2, activateFired
        )
    }

    /** 当前在用的库：整卡不报按钮、也没有点击动作，但卡上的动作一颗不少（见上一格 KDoc 的取舍） */
    @Test
    fun `the active library card is not announced as a dead button`() {
        mount(active = true)
        val card = probe.of(
            rule.onNodeWithTag(com.lovebrain.app.core.designsystem.LbListCardTags.CARD)
                .fetchSemanticsNode("当前库的卡画没了")
        )
        assertEquals("当前在用的库整卡不许报成按钮：" + card.describe(), "无", card.role)
        val labels = probe.laid(probe.actionableTargets(rule, "知识库列表·当前库")).map { it.label }
        // 同一颗名字：`a11y_action_rename`（中英两份），不抄中文字面量。
        val renameLabel = ApplicationProvider.getApplicationContext<Context>()
            .getString(com.lovebrain.app.R.string.a11y_action_rename)
        assertTrue("当前库的重命名胶囊不许跟着整卡一起消失：" + labels.joinToString(),
            labels.any { it == renameLabel })
    }
}
