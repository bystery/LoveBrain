package com.lovebrain.app.ui.home

import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.RenderIn
import com.lovebrain.app.core.testing.ScrollScan
import com.lovebrain.app.core.testing.SemanticsProbe
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiMatrix
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.model.ProviderTicket
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 第6节第1条 归并的证据：`ProviderEditDialog` 从「自己画两层壳」并进设计系统的 `LbDialog`。
 *
 * 归并之前这里是 `Dialog` + `Card(shape = xl, containerColor = SurfaceCard)` 两层自画的壳，
 * 而标题「添加供应商 / 编辑供应商」画在表单本体里。现在标题坐 `title=` 槽、表单坐 `body=` 槽，
 * 这一层只转参数（`OddShapeOwnershipTest` 从此把这一颗判成委托壳，不再是异形）。
 *
 * 三格各判一件事，缺一不可：
 * 1. **语义树**：归并之后表单本体那棵树里**没有第二份标题**，而那颗本体确实挂上了（证人）；
 * 2. **语义树**：两颗出口「取消 / 保存修改」还在、说得出名字、热区过 第6节第5条 :531 的下限；
 * 3. **结构**：那两个说法此刻只在 `LbDialog` 的 title 槽里出现一次，这一层不再自己起浮层窗口。
 *
 * ⚠ 为什么标题只能"读结构 + 读本体那棵树"两半拼起来，不能一格读到底：
 * 这台仪器里「`Dialog` 窗口 + 文本框拿焦点」`waitForIdle` 永不返回（账本 第45节第1条，
 * 光标闪烁、条件渲染的 spinner 两条假设都已实测排除，`autoAdvance = false` 也试过）。
 * 所以**浮层窗口本体量不到**，标题画出来长什么样由 `LbDialogTest` 的
 * 「title and body are both readable nodes」那一格在组件侧钉住；
 * 这一格只证明"没被搬坏、没搬丢、没留两份"。读结构证明的是"没搬坏"，不是"长得对"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = UiProbeApplication::class,
    qualifiers = "sw600dp-w600dp-h1200dp-normal-long-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProviderDialogMergeTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val probe by lazy { SemanticsProbe(ctx.resources.displayMetrics.density) }

    /** 「保存修改」走资源（本机环境解析成英文），判据一律 getString 取，不抄中文 */
    private val saveChangesLabel: String get() = ctx.getString(R.string.provider_save_changes)

    private val threeModels = ProviderTicket(
        id = "t1",
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.example",
        model = "deepseek-chat",
        models = listOf("deepseek-chat", "deepseek-reasoner", "qwen-max"),
        thinkingMode = 1
    )

    /** 四条显式桩一条都不能省（relaxed 对泛型 StateFlow 交回假对象，见 `ProviderFormSemanticsTest`） */
    private fun fakeVm(): SetupViewModel {
        val vm = mockk<SetupViewModel>(relaxed = true)
        every { vm.formError } returns MutableStateFlow<String?>(null)
        every { vm.saving } returns MutableStateFlow(false)
        every { vm.getKeyMask(any()) } returns "sk-…3f9a"
        every { vm.globalThinking } returns 0
        return vm
    }

    private fun mountBody(ticket: ProviderTicket?) {
        val vm = fakeVm()
        rule.setContent {
            UiMatrix(360).RenderIn(LocalDensity.current.density) {
                ProviderFormBody(viewModel = vm, ticket = ticket, onDismiss = {})
            }
        }
        rule.waitForIdle()
    }

    /** 归并前标题就是这一格的第一行；现在它必须**不在这棵树里**，而本体又确实画出来了 */
    @Test
    fun `the merged form body draws no second copy of the title`() {
        mountBody(null)
        assertEquals(
            "挂载证人：表单第一行的标签应当恰好一个节点（0 个说明本体压根没画出来，" +
                "那会让下面那条断言假绿）",
            1, rule.onAllNodesWithText("供应商名称").fetchSemanticsNodes().size
        )
        listOf("添加供应商", "编辑供应商").forEach { title ->
            val found = rule.onAllNodesWithText(title).fetchSemanticsNodes().size
            assertEquals(
                "「$title」已经交给 LbDialog 的 title= 槽，表单本体里不该再画第二份" +
                    "——两份标题正是  要收掉的那件事（实到 $found 个节点）",
                0, found
            )
        }
    }

    @Test
    fun `the merged form still offers a labeled save and cancel at the touch floor`() {
        mountBody(threeModels)
        val seen = ScrollScan(rule, probe).toBottom("供应商表单（归并后）")
        val wanted = listOf("取消", saveChangesLabel)
        val missing = wanted.filter { !seen.containsKey(it) }
        assertTrue(
            "浮层换了所有者之后这两颗出口都得还在，没量到：$missing；实到：" + seen.keys.sorted(),
            missing.isEmpty()
        )
        val exits = wanted.map { seen.getValue(it) }
        val unlabeled = exits.filter { !it.labeled }
        assertTrue("出口得有可读名字：" + unlabeled.joinToString { it.describe() }, unlabeled.isEmpty())
        val offenders = exits.filter { it.tooSmall(probe.floorDp) }
        assertTrue(
            "出口的热区不到 ${probe.floorDp.toInt()}dp：" + offenders.joinToString { it.describe() },
            offenders.isEmpty()
        )
        val noRole = exits.filter { it.role != "Button" }
        assertTrue(
            "两颗出口都得报成按钮（换所有者不是丢角色的理由）：" +
                noRole.joinToString { it.describe() },
            noRole.isEmpty()
        )
    }

    /**
     * 结构那一半：标题此刻**只**在 title 槽里，而这一层不再自己起浮层。
     *
     * ⚠ 这台机器量不到浮层窗口（见文件头那段），所以这一格读源码。
     * 它挡住的三种坏法：把标题整个删掉、把标题又抄回表单本体、把壳换回自画的 `Dialog` + `Card`。
     */
    @Test
    fun `the shell hands the float to LbDialog and opens no window of its own`() {
        val file = File("src/main/java/com/lovebrain/app/ui/home/ProviderSection.kt")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/ui/home/ProviderSection.kt")
        assertTrue("$file 不在了——这一格会恒绿", file.isFile)
        val code = SourceScan.maskComments(file.readText(Charsets.UTF_8))

        // ① 全文件不再自己起浮层窗口（`\bDialog` 的词边界让 `LbDialog(` 与 `ProviderEditDialog(` 不误算）
        assertEquals(
            "归并后这一屏不该再有自画的浮层窗口，实到：" +
                Regex("""\bDialog\s*\(""").findAll(code).map { it.value }.toList(),
            0, Regex("""\bDialog\s*\(""").findAll(code).count()
        )
        assertTrue(
            "那扇窗口的外壳 import 也该跟着走（留着说明有人又自己开了一扇）",
            !code.contains("androidx.compose.ui.window.Dialog")
        )

        // ② 壳那一段：一次 LbDialog、标题走 title= 槽、两个说法各只出现一次。
        //   锚点只认**声明本身**、不认可见性：那颗从 `private` 换成 `internal` 是为了让
        //   `LbDialog` 的 body 槽能被这一层的调用点用起来，判据要盯的东西一句没变。
        //   锚点找不到时必须当场抛——`substringAfter` 找不到锚点会原样交回整份文件，
        //   于是"壳"变成"整颗文件"，"标题只出现一次"那两条就会红在没发生过的话上。
        fun slice(declaration: String, from: String, where: String): String {
            check(from.contains(declaration)) {
                "$where 的声明锚点「$declaration」在源码里读不到 ⇒ 那一层被改名或拆走了，" +
                    "这一格不能拿整份文件当那一段来数"
            }
            return from.substringAfter(declaration).substringBefore("\n@Composable")
        }
        val shell = slice("fun ProviderEditDialog(", code, "壳")
        assertTrue("壳扫不到 LbDialog——它已经不是委托壳了：" + shell, shell.contains("LbDialog("))
        assertTrue(
            "标题得交给 title= 槽（归并的全部意义就在这一个槽位上）：" + shell,
            Regex("""title\s*=\s*if \(ticket == null\)""").containsMatchIn(shell)
        )
        assertEquals("「添加供应商」在这一层只该出现一次", 1, Regex("添加供应商").findAll(shell).count())
        assertEquals("「编辑供应商」在这一层只该出现一次", 1, Regex("编辑供应商").findAll(shell).count())

        // ③ 表单本体那一段里一个字都不该再有（②的镜像：不是"这里数少了"，是"搬过去了"）
        val body = slice("fun ProviderFormBody(", code, "表单本体")
        assertTrue(
            "表单本体里又出现了标题文案：" +
                listOf("添加供应商", "编辑供应商").filter { body.contains(it) },
            !body.contains("添加供应商") && !body.contains("编辑供应商")
        )
    }
}
