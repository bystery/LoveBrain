package com.lovebrain.app.ui.panel.reply

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.AppConfig
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * 输入行那三颗角色 chip 归进 `LbChip`（`Single` 一档）之后的读数。
 *
 * 这一族是**互斥单选**，所以规范位是 `Role.Tab` + `Selected`（与页头那三档模式同一写法），
 * 而 `✓` 前缀这一档**故意关掉**：三颗的字面量就是「她」「我」「补充」（ 改名后的那一颗），
 * `ComposerAddButtonGatingTest` 那一格数角色 chip 数数的就是这三个字，
 * 给它们加上对勾会把那一格改成"找不到 chip"——那是拿归并去动别人的判据。
 *
 * 这颗原来就是"热区与视觉分两层"的形状（外面 48 高可点、里面 28dp 胶囊；宽度轴原来
 * 也被外盒撑到 48，每颗白占 18dp——core 的 `widthFloor` 旋钮落了之后宽度回落到可见胶囊，
 * 高度轴仍是 48），归并后由 `LbChipStyle.layeredTouch` + `widthFloor=false` + `pillHeight`
 * 交出去。下面那格量的正是这件事：语义树里可点那颗报出来的高度必须是 48，而不是里面
 * 那颗胶囊的 28——这一族上一格踩过的坑就是"胶囊撑到 48"看着更达标，其实把输入行的高度改了。
 *
 * ═══ 这一族现在还管行 1 的宽度那一半（§7.1 / §4.3 / §2.2 第 3 条）══════
 *
 * 三颗角色 chip 是这一行最宽的固定件（可见宽 30dp 上下、高 48），所以"这一行装不装得下"这件事
 * 也只有在它们身上量得出来。下面那几格各钉一件事、各对一个坏实现：
 * · **正常宽那一档**（360）：§7.1 的视觉顺序 她→我→补充→输入框→＋→符号 不许换位，
 *   并且把 §4.3 那三轴分开量——可点胶囊高度轴过下限（宽度轴自 widthFloor 落地后为内容
 *   定宽，F03 的 18dp 占位已拆）、那颗范围符号没被再包一层 48dp 大方盒、剩余宽度确实全给了输入框；
 * · **项目最低支持宽那一档**：用的是窗口宽（`AppConfig.PANEL_MIN_W` 扣掉面板自己的左右内边距），
 *   不是屏幕宽——把 320/360 那一族屏幕读数拿来当这一行的档位就是 §4.3 禁的那种混法；
 * · **字号 1.3 那一档**：§7.1 要的那一次"较大系统字号至少做一次检查"（几何这一半）；
 * · 范围符号那一格：中文语义、两态都跟着传进来的那颗走、开与关有可见差异、不许退回彩色 emoji。
 *
 * 三档宽度里的"让位"只让上限：**可见尺寸与热区下限一寸不动**，多出来的那颗由这一段本来就
 * 有的横向滚动接住——所以每一格都另判一句"那颗仍在树上"，把某人改成 `if (够宽) 才画` 那种
 * "藏掉必需控件"的坏实现钉红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoleChipSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

    private val probe by lazy { SemanticsProbe(density) }

    private var lastRole: ChatMessage.Role? = null

    private fun mount(current: MutableState<ChatMessage.Role>) {
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = "在吗",
                    currentRole = current.value,
                    editingIndex = -1,
                    onDraftChange = {},
                    onRoleChange = { lastRole = it },
                    onAdd = {},
                    onFocusChange = {}
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    private fun tabs(what: String) =
        probe.laid(probe.actionableTargets(rule, what)).filter { it.role == "Tab" }

    /** 三颗都在、都报 Tab、都念得出自己的名字，而且**没有**被加上对勾 */
    @Test
    fun `the three role chips are tabs that name themselves without a check mark`() {
        mount(mutableStateOf(ChatMessage.Role.ME))
        val chips = tabs("输入行·角色 chip")
        assertEquals(
            "应当恰好三颗报 Tab：" + chips.joinToString { it.describe() },
            3, chips.size
        )
        assertEquals(
            // 等：第三颗从「想法」改成短标签「补充」（PRODUCT_SPEC 第3节 定死的命名）。
            // 它是"输入对象"这一轴，不是一种消息角色——对勾仍不许加在这三颗上（见类 KDoc）。
            "标签仍是那三个字，一字不加：" + chips.joinToString { it.describe() },
            setOf("她", "我", "补充"), chips.map { it.label }.toSet()
        )
        chips.forEach { assertTrue("${it.label} 读得出自己", it.labeled) }
    }

    /** `Selected` 两头都要量：起始那一颗 true，其余 false */
    @Test
    fun `exactly one role chip reports selected`() {
        mount(mutableStateOf(ChatMessage.Role.ME))
        val chips = tabs("输入行·选中态")
        val on = chips.filter { it.selected == true }
        assertEquals("该恰好一颗报选中：" + chips.joinToString { it.describe() }, 1, on.size)
        assertEquals("报选中的就是当前那个角色", "我", on.single().label)
        assertEquals(
            "另外两颗必须报未选中（把 selected 写死的坏实现红在这里）：" +
                chips.joinToString { it.describe() },
            2, chips.count { it.selected == false }
        )
    }

    /** 点一颗原本没选的：投递的角色对，`Selected` 也跟着挪 */
    @Test
    fun `tapping another role chip moves the selected flag onto it`() {
        val current: MutableState<ChatMessage.Role> = mutableStateOf(ChatMessage.Role.ME)
        mount(current)

        rule.onAllNodes(hasText("她"))[0].performClick()
        rule.mainClock.advanceTimeBy(16L)
        assertEquals("点击要落到 onRoleChange", ChatMessage.Role.HER, lastRole)

        // 生产里这一步是 VM 把 currentRole 换成 HER；测试用同一份状态接住它
        current.value = ChatMessage.Role.HER
        rule.mainClock.advanceTimeBy(16L)
        val chips = tabs("输入行·点后")
        assertEquals(
            "报选中的应换成刚点的那一颗：" + chips.joinToString { it.describe() },
            listOf("她"), chips.filter { it.selected == true }.map { it.label }
        )
        assertEquals("而且仍然恰好一颗", 1, chips.count { it.selected == true })
    }

    /**
     * 可点那颗自己过下限，而且量的必须是**外面那一层**：里面那颗胶囊仍是 28dp。
     *
     * 这一格是给"归并顺手把胶囊撑到 48"那种改法设的闸——它会让读数更漂亮，
     * 但用户看到的输入行会变高。
     *
     * 判据改写（2026-10-09，C 类·按新形状重写，出处 = 书 §20 F03 与 §7.1）：这一格原来
     * 两轴都钉 48——宽度轴那半钉的正是 F03 点名要拆的 18dp 幻影占位（"她/我"可见 30dp、
     * 外盒白撑到 48）。core 的 [com.lovebrain.app.core.designsystem.LbChipStyle.widthFloor]
     * 落地后，本族认领 `widthFloor=false`：**高度轴**仍是全站下限（外层盒 48，下面那格
     * 逐字钉住），**宽度轴改为内容定宽**、故意不钉上界（随文案与系统字号变化，钉死就会
     * 在字体档上假红）。宽度的故事由行级那几格管：不越出右缘、输入框吃余宽、极窄档一行。
     */
    @Test
    fun `each role chip's clickable box is the floor, not the pill`() {
        mount(mutableStateOf(ChatMessage.Role.IDEA))
        tabs("输入行·热区").forEach { chip ->
            assertTrue(
                "${chip.label} 的可点节点高度不到 ${probe.floorDp.toInt()}dp：" + chip.describe(),
                chip.heightDp + 0.5f >= probe.floorDp
            )
            assertEquals(
                "${chip.label} 的可点节点应当正好是那颗下限（胶囊在里面 28dp）：" + chip.describe(),
                48f, chip.heightDp, 0.6f
            )
        }
    }

    /**
     * 单行形制（1.3.1 的输入行）：三颗角色 chip、输入框、➕ 同在一排——
     * 各自的竖直中线重合。谁把输入行重新拆成两行，这里就红在位移上。
     * 判的是几何不是源码：读的是语义树里各颗自己的 boundsInRoot。
     */
    @Test
    fun `the chips, the input field and the add button share one single line`() {
        mount(mutableStateOf(ChatMessage.Role.HER))
        val input = probe.of(rule.onNode(hasSetTextAction()).fetchSemanticsNode())
        val inputMid = input.topDp + input.heightDp / 2f
        val add = probe.of(rule.onNodeWithContentDescription("添加").fetchSemanticsNode())
        tabs("输入行·同排").forEach { chip ->
            val mid = chip.topDp + chip.heightDp / 2f
            assertTrue(
                "${chip.label} 与输入框不在同一排：chip ${chip.describe()} 输入框 ${input.describe()}",
                abs(mid - inputMid) < 2f
            )
        }
        assertTrue(
            "➕ 与输入框不在同一排：➕ ${add.describe()} 输入框 ${input.describe()}",
            abs((add.topDp + add.heightDp / 2f) - inputMid) < 2f
        )
    }

    /**
     * 最窄档那一格（1.4.0 §10.1 验收原话：「验收一个正常宽度和当前支持的最窄宽度…均一行」，
     * 并且「不把输入缩到不可用」）。360 那一格量过同排，这一格把坐标换成 320，
     * 并把「仅看本轮」那颗符号一起拉进来量——最容易掉出这一行的就是它。
     *
     * ⚠ 320 是**屏幕**那一头的读数（`UiMatrix.WIDTHS_DP` 那一族），不是悬浮窗的窗口宽；
     * 项目真实的最窄支持档另有一格（`the row still fits every control on one line at the
     * project's narrowest supported window`，按 `AppConfig.PANEL_MIN_W` 推出来的 228）。
     * 这一格留着的价值就是它那把更高的尺（六颗正文）：屏幕档的 320 比悬浮窗最低档宽松，
     * 用更高的输入框下限量"较松窗宽下全可见档还给输入框多少"，与 228 那一格各钉一头。
     *
     * 三条判据都读几何，不读源码：
     * 1. 落在可视区里的那几颗（角色 chip 在窄档可能只剩两颗可见，第三颗靠这一段
     *    本来就有的横向滚动接住）与输入框、➕、符号仍在这条中线上；
     * 2. 没有一颗越过 320dp 容器的右缘（越界=被裁掉，等于没在一行里）；
     * 3. 输入框分到的宽度还容得下它自己的左右内边距 + 两颗正文——
     *    下限由令牌算出（`Spacing.lg`×2 + `AppTypography.bodyMedium.fontSize`×2），
     *    不写页面字面量，字号或留白改了它自己跟着变。
     */
    @Test
    fun `the whole row stays on one usable line at the narrowest supported width`() {
        val narrowWidthDp = 320
        rule.setContent {
            UiMatrix(narrowWidthDp).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = "在吗",
                    currentRole = ChatMessage.Role.HER,
                    editingIndex = -1,
                    onDraftChange = {},
                    onRoleChange = {},
                    onAdd = {},
                    onFocusChange = {},
                    onlyThisRound = true,
                    onOnlyThisRoundChange = {}
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        val input = probe.of(rule.onNode(hasSetTextAction()).fetchSemanticsNode())
        val add = probe.of(rule.onNodeWithContentDescription("添加").fetchSemanticsNode())
        val scope = probe.of(
            rule.onNodeWithTag(PANEL_ROUND_SCOPE_TEST_TAG, useUnmergedTree = true).fetchSemanticsNode()
        )
        val inputMid = input.topDp + input.heightDp / 2f

        val everyone = tabs("行1·最窄档") + listOf(input, add, scope)
        // 窄档不要求三颗角色 chip 都落在可视区内：chip 段的上限在窄档让位（见
        // ReplyDimens.ROLE_CHIPS_TIGHT_MAX_WIDTH_DP），第三颗由这一段本来就会的横向滚动接住。
        // 这一格因此只钉两件事：让位之后这一排**仍然是一排**，而且谁都没被裁到容器外。
        assertTrue(
            "最窄档连一颗角色 chip 都摆不出来：" + everyone.joinToString { it.describe() },
            everyone.size >= 4
        )
        everyone.forEach { t ->
            assertTrue(
                "${t.announced} 与输入框不在同一排：${t.describe()} 输入框 ${input.describe()}",
                abs((t.topDp + t.heightDp / 2f) - inputMid) < 2f
            )
            assertTrue(
                "${t.announced} 越出 ${narrowWidthDp}dp 容器（被裁掉就没在一行里）：${t.describe()}",
                t.leftDp >= 0f && t.leftDp + t.widthDp <= narrowWidthDp + 0.5f
            )
        }

        // 「可用」这一档是本件自己定的：§10.1 只说「不把输入缩到不可用」，没给数。
        // 取「左右内边距 + 正文六颗字」——六颗按 bodyMedium 那颗字号算，不写页面字面量，
        // 字号或留白改了它自己跟着动。改前实测（同一格、同一坐标）文字区只有 53dp，红在这里。
        val usableFloorDp = 2f * Spacing.lg.value + 6f * AppTypography.bodyMedium.fontSize.value
        assertTrue(
            "输入框被挤到不可用：${input.describe()}，可用下限 ${usableFloorDp}dp" +
                "（同行读数 ➕ ${add.describe()} 符号 ${scope.describe()}）",
            input.widthDp >= usableFloorDp
        )
    }

    // ═══════════ §7.1 固定顺序与空间分配：三档宽度各自钉一格 ═══════════

    /**
     * 把整排行 1 摆进指定那一格（宽度、字号都由调用点说）。
     *
     * ⚠ `setContent` 一格只能一次（本机踩过），所以每一格自己调这一颗、自己推一帧。
     */
    private fun mountRow(widthDp: Int, fontScale: Float = 1f, onlyThisRound: Boolean = true) {
        rule.setContent {
            UiMatrix(widthDp, fontScale = fontScale).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = "在吗",
                    currentRole = ChatMessage.Role.HER,
                    editingIndex = -1,
                    onDraftChange = {},
                    onRoleChange = {},
                    onAdd = {},
                    onFocusChange = {},
                    onlyThisRound = onlyThisRound,
                    onOnlyThisRoundChange = {}
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)
    }

    /**
     * 行 1 的三份读数：可见顺序上从左到右那几颗（chip 段里**看得见**的那些 + 输入框热区 + ➕ + 符号）。
     * 裁到 0x0 的那些由 [SemanticsProbe.laid] 筛掉并打进失败信息，不在这里静默消失。
     */
    private fun rowTargets(what: String): List<SemanticsProbe.Target> {
        val input = probe.of(rule.onNodeWithTag(PANEL_INPUT_TOUCH_TAG).fetchSemanticsNode())
        val add = probe.of(rule.onNodeWithContentDescription("添加").fetchSemanticsNode())
        val scope = probe.of(
            rule.onNodeWithTag(PANEL_ROUND_SCOPE_TEST_TAG, useUnmergedTree = true).fetchSemanticsNode()
        )
        return tabs(what) + listOf(input, add, scope)
    }

    /**
     * 项目最低支持宽度那一格（§7.1 末段"用常用窗口宽度及**项目最低支持宽度**验收"）。
     *
     * 这一格用的不是屏幕宽，是**窗口宽**：悬浮窗最窄那一档由 `AppConfig.PANEL_MIN_W` 说，
     * 面板自己左右各扣 `Spacing.xl`（`LoveBrainPanelScreen.kt:524`）才是这一行真正拿到的宽。
     * 把 320/360 那一族**屏幕**读数当成这一行的档位，就是 §4.3 禁的"三种尺寸混为一谈"。
     *
     * 判五件事，全是几何：
     * 1. 还在同一条中线上（一行，不换排）；
     * 2. 没有一颗越出这一行的右缘（越出去就是被裁掉 = 没在一行里，也谈不上"点得到"）；
     * 3. 相邻两颗不许互相压着（§7.1 极窄那行"不得遮住输入、把按钮叠在一起"——
     *    量的是**顺序上相邻**的两颗 left ≥ 前一颗 right，输入框排在 chip 段之后、➕ 之前）；
     * 4. 让位这一档**两颗完整可见、第三颗滑得到**（宽度轴幻影占位拆掉后，最低窗的余量
     *    够两颗整颗；那颗"补充"没被画掉、也没被 `if` 藏掉——"必需控件不许悄悄藏起来"与
     *    "极窄时先去冗余占位"两句同时成立，只有横滑那一种形状做得到）；
     * 5. 输入框那层热区仍然保住"看得见自己在打什么字"那份最小宽（下限由令牌算：
     *    它自己的左右内边距 `Spacing.lg`×2 + **两颗**正文 × 字号）。
     *    ⚠ 这一档与上面 320 那一格的"六颗"不是同一把尺：那一格量的是较松窗宽下**全可见档**
     *    还给输入框的余量，这一格钉的是 §7.1 极窄那一行只禁止"遮住输入"的下限。两把尺各说各的事。
     */
    @Test
    fun `the row still fits every control on one line at the project's narrowest supported window`() {
        // 窗口最低支持宽扣掉面板自己的左右内边距 = 这一行真正拿到的宽（两个数都从主人处读，不抄）
        val rowWidthDp = AppConfig.PANEL_MIN_W - 2 * Spacing.xl.value.toInt()
        mountRow(rowWidthDp)

        val input = probe.of(rule.onNodeWithTag(PANEL_INPUT_TOUCH_TAG).fetchSemanticsNode())
        val inputMid = input.topDp + input.heightDp / 2f
        val inRow = rowTargets("行1·最低支持宽")

        // 让位≠删掉：两颗滑出去的必须**仍在树上**（由这一段本来就有的
        // 横向滚动接住）。把"补充"用 if 藏起来这种坏实现红在这里——那是 §7.1 末段明令禁止的
        // "悄悄把某个必需控件藏掉"，和滑出去那两件事在语义树上分得很清。
        assertEquals("第三颗角色 chip 被从树上删掉了（不是让位）", 1,
            rule.onAllNodesWithText(ROLE_LABEL_SUPPLEMENT).fetchSemanticsNodes().size)
        assertTrue(
            "最低档连一颗角色 chip 都没摆出来：" + inRow.joinToString { it.describe() },
            inRow.isNotEmpty() && inRow.count { it.role == "Tab" } >= 1
        )
        // 最低档真的接在"两颗"这一级上（三档由 ReplyDimens.chipsCap 一处算，只吃常量与令牌；
        // 视口宽**从生产那颗函数现取**，测试不抄第二份 66/116/48）：
        // 行 228 的余量 76 落在 [66,116) ⇒ 两颗完整可见、第三颗滑出去接住。
        // ⚠ 语义 bounds 会被滚动视口**裁切**（本机探针实测：滑出半截的那颗，裁后右缘正好压在视口线上；
        // 完全滑出的那颗读成 0x0、被 `laid` 筛掉）。所以"完整可见"判 right **严格小于**视口右缘——
        // 压线的那颗算半截，不算完整。滚动范围本身改序后实测 105.6dp（注释在 ReplyInput 的 chipsSection）。
        val capDp = ReplyDimens.chipsCap(
            maxWidth = rowWidthDp.dp, fontScale = 1f, withAdd = true, withScope = true
        ).value
        val chipsHere = inRow.filter { it.role == "Tab" }
        assertEquals(
            "最低档应恰有两颗完整可见的角色 chip（第三颗滑得到；回到旧 48 档只剩一颗、" +
                "或错开到全可见档把输入框挤下限穿，都红在这里）：" + chipsHere.joinToString { it.describe() },
            2, chipsHere.count { it.leftDp >= -0.5f && it.leftDp + it.widthDp < capDp - 0.5f }
        )
        // 让位≠删掉：中间那颗必须**仍在树上**（半截可见就是活着滑得到的样子）——
        // bounds 会被裁，文本节点不会：这一句与上面那颗「补充」的文本格一起堵住"用 if 悄悄藏控件"。
        assertEquals(
            "「我」被从树上删掉了（那是藏控件，不是让位；§7.1 末句禁的就是这个）",
            1, rule.onAllNodesWithText(ROLE_LABEL_ME).fetchSemanticsNodes().size
        )

        inRow.forEach { t ->
            assertTrue(
                "${t.announced} 与输入框不在同一排：${t.describe()} 输入框 ${input.describe()}",
                abs((t.topDp + t.heightDp / 2f) - inputMid) < 2f
            )
            assertTrue(
                "${t.announced} 越出 ${rowWidthDp}dp 这一行：${t.describe()}",
                t.leftDp >= -0.5f && t.leftDp + t.widthDp <= rowWidthDp + 0.5f
            )
        }

        // 顺序与不重叠：按可见位置排一遍，相邻两颗不许互相压着（§7.1 极窄那行"不得遮住输入、
        // 把按钮叠在一起"）。被横滑裁掉的那些不在这里——[SemanticsProbe.laid] 已经把 0x0 的筛掉了，
        // 留下的每一颗都真在行上，所以两两之间必须留得下间距。
        val ordered = inRow.sortedBy { it.leftDp }
        for (i in 1 until ordered.size) {
            val prev = ordered[i - 1]
            val next = ordered[i]
            assertTrue(
                "第 ${i} 颗压在 ${i + 1} 颗上（把按钮叠在一起）：${prev.describe()} → ${next.describe()}",
                next.leftDp >= prev.leftDp + prev.widthDp - 0.5f
            )
        }

        val twoCharsFloorDp = 2f * Spacing.lg.value + 2f * AppTypography.bodyMedium.fontSize.value
        assertTrue(
            "输入框被挤到看不见自己在打什么字：${input.describe()}，下限 ${twoCharsFloorDp}dp" +
                "（同排读数 " + inRow.joinToString { it.describe() } + "）",
            input.widthDp >= twoCharsFloorDp
        )
    }

    /**
     * 档位判定本体的真值表（纯函数，不挂屏）：三档只看"扣完固定件与输入框下限后的余量"。
     *
     * 这一格是 L1 复核挑中的那个缺陷的直接证人：旧表第一档钉在"行宽 ≥ 360"，而生产最高
     * 窗宽的行只有 318 ⇒ 全可见档永远进不来，第三颗"补充"在所有受支持窗口里藏在横滑后面，
     * 违反 §7.1"不能悄悄把必需控件藏掉"。改表后按余量分档（116/66/兜底 48，推导在
     * `ReplyDimens` 各常量注释里，数都从主人处现取，测试不抄第二份）。
     * 红条件：谁把门槛改回按**行宽**比（`maxWidth >= 某常量`），行 318 那一格立刻从 116 掉档变红；
     * 谁把 116 改回 156（旧大方盒的账），行 268（默认窗）那一格变红。
     */
    @Test
    fun `the tier table keys off leftover room, not the old 360dp row gate`() {
        // 行 318 = 生产最高档（PANEL_MAX 350 − 面板左右各 Spacing.xl）：余量 166 ≥ 116 → 全可见档
        assertEquals(
            "行 318 该进三颗全可见档",
            ReplyDimens.ROLE_CHIPS_MAX_WIDTH_DP.toFloat(),
            ReplyDimens.chipsCap(318.dp, 1f, withAdd = true, withScope = true).value
        )
        // 行 268 = 默认窗（300−32）：余量 116 恰好踩线 → 仍全可见（旧表在这里给的是 96 两颗档）
        assertEquals(
            "默认窗宽该把第三颗还给屏上（旧 360 门槛正是把它藏掉的根因）",
            ReplyDimens.ROLE_CHIPS_MAX_WIDTH_DP.toFloat(),
            ReplyDimens.chipsCap(268.dp, 1f, withAdd = true, withScope = true).value
        )
        // 行 228 = 最低窗：余量 76 ∈ [66,116) → 两颗档
        assertEquals(
            "最低窗该停在两颗档",
            ReplyDimens.ROLE_CHIPS_TIGHT_MAX_WIDTH_DP.toFloat(),
            ReplyDimens.chipsCap(228.dp, 1f, withAdd = true, withScope = true).value
        )
        // 余量 < 66 → 48 兜底（生产窗到不了，安全网仍要在）
        assertEquals(
            "低于两颗档时兜底仍留一颗",
            48f,
            ReplyDimens.chipsCap(190.dp, 1f, withAdd = true, withScope = true).value
        )
    }

    /**
     * 正常宽度那一格：§7.1 的**视觉顺序**与三轴各自到位。
     *
     * 顺序判据（她 → 我 → 补充 → 输入框 → ＋ → 范围符号）就按左缘排；这一格同时把 §4.3 那三轴
     * 分开量，因为 §2.1 第 1 行点名的正是"小胶囊并不等于小占位"：
     * · **命中区**：角色 chip 判**高度轴**过全站那颗下限、➕ 两轴都过（宽度轴那 18dp 幻影占位
     *   已由 core 的 `widthFloor` 收掉，判据改写的出处与账在 `each role chip's clickable box…` 那格）；
     * · **布局占位**：那颗范围符号**没有**被再包一层 48dp 见方的容器（§4.3 明写不许拿这个
     *   解决一行输入，否则悬浮窗的紧凑档就作废了）；
     * · 三颗都排得下，说明这一档本来就不该让位。
     */
    @Test
    fun `at normal width the row keeps the book's order and each control keeps its own footprint`() {
        val normalWidthDp = 360
        mountRow(normalWidthDp)

        val inRow = rowTargets("行1·正常宽")
        assertEquals("正常宽度该把三颗角色 chip 都摆出来：" + inRow.joinToString { it.describe() },
            3, inRow.count { it.role == "Tab" })

        val input = probe.of(rule.onNodeWithTag(PANEL_INPUT_TOUCH_TAG).fetchSemanticsNode())
        val add = probe.of(rule.onNodeWithContentDescription("添加").fetchSemanticsNode())
        val scope = probe.of(
            rule.onNodeWithTag(PANEL_ROUND_SCOPE_TEST_TAG, useUnmergedTree = true).fetchSemanticsNode()
        )
        val chips = inRow.filter { it.role == "Tab" }.sortedBy { it.leftDp }
        val order = chips + listOf(input, add, scope)

        // ① 视觉顺序：书名点名的那一串，一颗都不许换位
        for (i in 1 until order.size) {
            assertTrue(
                "第 ${i} 颗与第 ${i + 1} 颗换了位置（§7.1 顺序 她→我→补充→输入框→＋→符号）：" +
                    order.joinToString(" → ") { it.describe() },
                order[i].leftDp >= order[i - 1].leftDp + 0.5f
            )
        }
        // ② 一行：竖直中线仍重合
        val inputMid = input.topDp + input.heightDp / 2f
        order.forEach { t ->
            assertTrue(
                "${t.announced} 与输入框不在同一排：${t.describe()} 输入框 ${input.describe()}",
                abs((t.topDp + t.heightDp / 2f) - inputMid) < 2f
            )
        }

        // ③ 命中区：角色胶囊判**高度轴**到全站下限（数从主人处读，不抄 48）；宽度轴自
        // widthFloor 认领后是内容定宽（F03 要拆的 18dp 幻影占位），判据改写见上面那格的 KDoc。
        // ➕ 不在让位档里，两轴仍都到下限。
        val floor = probe.floorDp
        chips.forEach { chip ->
            assertTrue(
                "${chip.label} 的命中区高度没到 ${floor}dp（§4.3 要分清的第三轴）：" + chip.describe(),
                chip.heightDp + 0.5f >= floor
            )
        }
        assertTrue("➕ 的命中区两轴没到 ${floor}dp：" + add.describe(), !add.tooSmall(floor))
        // ④ 占位：那颗符号不许再被包一层 48dp 见方（§4.3；缩小的是它自己的占用，不是别人的热区）
        assertTrue(
            "范围符号又被包回 48dp 见方大方盒（紧凑档作废）：" + scope.describe(),
            scope.widthDp + 0.5f < AppDimens.TOUCH_TARGET_MIN_DP.dp.value &&
                scope.heightDp + 0.5f < AppDimens.TOUCH_TARGET_MIN_DP.dp.value
        )
        // ⑤ 剩余宽度全给输入框：这一档下输入框必须比行里任何一颗固定件都宽
        order.filter { it !== input }.forEach { fixed ->
            assertTrue(
                "输入框没有吃到剩余宽度（比固定件 ${fixed.announced} 还窄）：${input.describe()} " +
                    fixed.describe(),
                input.widthDp >= fixed.widthDp
            )
        }
    }

    /**
     * §7.1 末段要的那一次"较大系统字号至少做一次检查"（1.3 倍，同一行、同一档窗口宽）。
     *
     * 这一格只判**这一档 JVM 等价得了的那半**：字号放大后行宽预算够不够、谁被挤出右缘、
     * 谁和谁叠住、输入框还剩多少。墨色对比与那颗符号在真实字体里长什么样不算在这格里——见本轮报告
     * "只能真机"那一段。
     */
    @Test
    fun `a larger system font keeps the row on one line without pushing anything out`() {
        val widthDp = 360
        val fontScale = 1.3f
        mountRow(widthDp, fontScale = fontScale)

        val inRow = rowTargets("行1·字号1.3")
        val input = probe.of(rule.onNodeWithTag(PANEL_INPUT_TOUCH_TAG).fetchSemanticsNode())
        val inputMid = input.topDp + input.heightDp / 2f
        assertTrue("字号 1.3 下这一排一颗都没摆出来：" + inRow.joinToString { it.describe() },
            inRow.size >= 3)
        inRow.forEach { t ->
            assertTrue(
                "${t.announced} 在字号 ${fontScale} 下掉出这一排：${t.describe()} 输入框 ${input.describe()}",
                abs((t.topDp + t.heightDp / 2f) - inputMid) < 2f
            )
            assertTrue(
                "${t.announced} 在字号 ${fontScale} 下越出 ${widthDp}dp 这一行：${t.describe()}",
                t.leftDp >= -0.5f && t.leftDp + t.widthDp <= widthDp + 0.5f
            )
        }
        val ordered = inRow.sortedBy { it.leftDp }
        for (i in 1 until ordered.size) {
            assertTrue(
                "字号 ${fontScale} 下两颗叠住了：${ordered[i - 1].describe()} → ${ordered[i].describe()}",
                ordered[i].leftDp >= ordered[i - 1].leftDp + ordered[i - 1].widthDp - 0.5f
            )
        }
        // 两颗正文那份下限，字号放大时跟着放大（与最低档那一格同一把尺）
        val twoCharsFloorDp =
            2f * Spacing.lg.value + 2f * AppTypography.bodyMedium.fontSize.value * fontScale
        assertTrue(
            "字号 ${fontScale} 把输入框挤到看不见字：${input.describe()}，下限 ${twoCharsFloorDp}dp",
            input.widthDp >= twoCharsFloorDp
        )
    }

    // ═══════════ §2.2 第 3 条：那颗范围符号不许再被读成"锁定窗口" ═══════════

    /**
     * 范围符号（在 ＋ 之后那颗）自己那一格：中文语义、开/关两态、以及"不是锁"。
     *
     * 四条判据，每条各对一个坏实现：
     * 1. **中文语义描述**：两态都必须在 contentDescription 里报出 [PANEL_ROUND_SCOPE_LABEL]
     *    那句全名（屏上只画一颗符号，读屏念得出"仅看本轮"才算说清楚；名字里也不许出现"锁"字，
     *    那就是这条要治的那个歧义）；
     * 2. **开关状态跟着传进来的那颗走**：Off 与 On 都量一次——写死 `selected = true` 的坏实现
     *    红在 Off 那一半（同一棵树里不许有第二本开关状态，状态真源在 `RoundStateStore`）；
     * 3. **开的时候有可见差异**：可见文案在两态之间必须不一样（`markSelectedWithCheck` 那个勾
     *    就是画给眼睛的那一半；把它摘掉又不同步改这条，红在这里）；
     * 4. **不许再用 emoji 那颗图形字符**：位平面外的 emoji 在 Android 上走彩色字形，
     *    `pill` 那一档为开/关准备的两套墨色在它身上一分都不显，而且 🔒 正是被点名读成
     *    "锁定窗口"的那一颗。退回 emoji 的坏实现红在第 4 句。
     *    ⚠ 这颗要长成"既有图标族"的那一颗线条图标还差 core 一颗槽（`LbChip` 只收 `label: String`），
     *    本格先把**方向**钉住：不是锁、不是彩色 emoji、名字说人话。
     */
    @Test
    fun `the scope symbol names itself in Chinese, follows the switch and is not a lock`() {
        val on = mutableStateOf(false)
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ReplyInput(
                    draftText = "在吗",
                    currentRole = ChatMessage.Role.HER,
                    editingIndex = -1,
                    onDraftChange = {},
                    onRoleChange = {},
                    onAdd = {},
                    onFocusChange = {},
                    onlyThisRound = on.value,
                    onOnlyThisRoundChange = { on.value = !on.value }
                )
            }
        }
        rule.mainClock.advanceTimeBy(16L)

        fun scopeNode() = rule.onNodeWithTag(PANEL_ROUND_SCOPE_TEST_TAG).fetchSemanticsNode()
        fun mergedLabel() = scopeNode().config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text.orEmpty()

        val offLabel = mergedLabel()
        val offDescs = scopeNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertEquals(
            "关着时那颗必须报 ToggleableState.Off（写死选中值就红在这里）",
            ToggleableState.Off, scopeNode().config.getOrNull(SemanticsProperties.ToggleableState)
        )
        assertTrue("关着时读屏没念出全名：" + offDescs.joinToString(), offDescs.contains(PANEL_ROUND_SCOPE_LABEL))
        assertTrue(
            "读屏名字里不许再有\"锁\"这个歧义（§2.2 第 3 条）：" + offDescs.joinToString(),
            offDescs.none { it.contains("锁") }
        )

        // 生产里这一步是宿主把 RoundStateStore 那颗翻上去；测试用同一份状态接住它
        on.value = true
        rule.mainClock.advanceTimeBy(16L)
        assertEquals(
            "开着时那颗必须报 ToggleableState.On",
            ToggleableState.On, scopeNode().config.getOrNull(SemanticsProperties.ToggleableState)
        )
        val onDescs = scopeNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertTrue("开着时读屏没念出全名：" + onDescs.joinToString(), onDescs.contains(PANEL_ROUND_SCOPE_LABEL))
        val onLabel = mergedLabel()
        assertTrue(
            "开着与关着可见文案一模一样（没有可辨的选中态）：'$offLabel' vs '$onLabel'",
            onLabel != offLabel
        )
        listOf(offLabel, onLabel).forEach { label ->
            assertTrue(
                "那颗符号画的是 emoji（彩色字形不吃选中墨色，而且锁容易被读成锁窗口）：'$label'",
                label.none { it.isHighSurrogate() }
            )
            assertTrue("可见那颗换掉了，不是书里定的 [PANEL_ROUND_SCOPE_GLYPH]：'$label'",
                label.contains(PANEL_ROUND_SCOPE_GLYPH))
        }
        // 一颗都不许被这颗挤掉：位置仍在 ＋ 之后（§7.1 顺序的最后一格）
        val add = probe.of(rule.onNodeWithContentDescription("添加").fetchSemanticsNode())
        val scope = probe.of(scopeNode())
        assertTrue(
            "范围符号必须排在 ＋ 之后：➕ ${add.describe()} 符号 ${scope.describe()}",
            scope.leftDp >= add.leftDp + add.widthDp - 1f
        )
    }
}
