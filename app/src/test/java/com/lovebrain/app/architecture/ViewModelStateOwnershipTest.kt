package com.lovebrain.app.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "状态不许住在 ViewModel 里"这条的结构门禁（独立复核 §5.2 第 6 步、§7 第二步完成定义
 * 第 1 条"LoveBrainViewModel 不再持有 feature 的内部状态"）。
 *
 * 为什么要有这把尺：这一轮把输入区那九颗 `MutableStateFlow` 搬进了
 * [com.lovebrain.app.feature.composer.ComposerStore]，VM 只留同名只读出口。
 * **搬家是可逆的**——下一个人图省事在 VM 里再写一颗 `private val _foo = MutableStateFlow(...)`
 * 就悄悄回到原状，而且不会有任何测试变红（既有行为测试读的是 VM 的出口，不关心里面谁持有）。
 *
 * 三格各判一件事，缺一不可：
 * 1. 搬走的那十颗不许回来（点名的那批，加上"VM 现在确实在通过 store 写"的反向判据）；
 * 2. 剩下的账**逐颗点名登记**，多一颗红、少一颗也红——与 lint 预算同一口径：
 *    还了债不改账本，账本就会随着重构腐烂，最后没人知道还剩多少；
 * 3. **这把尺看得见东西**：拿同一套扫描器扫一个确实满是自己 flow 的文件（`SetupViewModel`），
 *    扫不出东西就说明判据是死的，那种绿不算数。
 */
class ViewModelStateOwnershipTest {

    private fun appRoot(): File {
        val root = File("src/main/java/com/lovebrain/app")
        assertTrue("必须在 :app 模块下跑，找不到 $root", root.isDirectory)
        return root
    }

    /** 已经搬进 ComposerStore 的九颗状态（按 VM 里从前的私有字段名点名） */
    private val movedIntoComposer = listOf(
        "_panelState", "_messages", "_currentRole", "_editingIndex", "_ideaComposeMode",
        "_draftText", "_counselingDraft", "_panelMode", "_outputMode", "_showPlanPanel"
    )

    private fun sourceOf(rel: String): String = File(appRoot(), rel).readText()

    /**
     * 私有状态流声明：`private val _x = MutableStateFlow(…)` **与带类型参数的写法**
     * `private val _x = MutableStateFlow<KnowledgeBase?>(null)`。
     *
     * ⚠ 这一格头一版写的是 `MutableStateFlow\(`，只数得到 20 颗里的 10 颗——因为本仓库多数声明
     * 写成 `MutableStateFlow<类型>(初值)`，字面量 `MutableStateFlow(` 根本不存在。
     * 当场被下面那格的"降也红"抓住（登记 20、量到 10）才暴露。⇒ 数形状的尺必须先扫一遍现状
     * 并把数打印出来，不许照心象填（同一族坑：正则漏写法 = 尺瞎了）。
     */
    private fun privateFlowDecls(text: String): List<String> =
        Regex("""^\s*private val (_\w+)[^=]*=\s*[\w.]*MutableStateFlow[^\n]*\(""", RegexOption.MULTILINE)
            .findAll(text).map { it.groupValues[1] }.toList()

    @Test
    fun `composer state does not move back into the viewmodel`() {
        val text = sourceOf("viewmodel/LoveBrainViewModel.kt")
        val back = movedIntoComposer.filter { name ->
            Regex("""val\s+$name\s*(:[^=\n]*)?=\s*[\w.]*MutableStateFlow[^\n]*\(""").containsMatchIn(text)
        }
        assertTrue(
            "这 ${back.size} 颗状态又回到 ViewModel 里了：$back —— 它们的主人现在是 ComposerStore，" +
                "要加状态请加到那个文件里（VM 只留只读出口）",
            back.isEmpty()
        )
        // 反向也要成立：VM 确实在通过 store 写，而不是自己另开一条路
        assertTrue(
            "ViewModel 里已经没有 composer.accept(——写入是不是绕过 store 了？",
            text.contains("composer.accept(")
        )
    }

    /**
     * 还留在 ViewModel 里的私有状态流——**逐颗点名登记**（本次实测，不是心象）。
     *
     * 为什么登记名字而不是只登记一个数：只比数量的话，"涨了一颗 + 搬走一颗"数量不变、
     * 当场绿过去；而且真涨的时候报不出是谁（本仓库有过一次：报错点名了一个根本没错的名字）。
     *
     * 每一颗后面都跟着它该去的地方，就是这张表剩下的账：
     * `_generationRoundId/_inputChanged/_generationHistory/_currentVersionId` → 回复版本与 stale 判定
     * （§5.2 第 6 步剩下的那块）；`_activeKb/_kbNotice/_vectorUpdate/_vectorDelta/_currentVector/
     * _stageSuggestion/_profileReview` → 知识库与画像那一族；`_panelWarning/_activeTicket/_providerReady`
     * → 供应商与提示；`_resultMode/_onlyThisRound/_actualSentState` → 本轮提交；
     * `_intentConfig/_showIntentEditor` → 持续意图。
     */
    private val stillInViewModel = setOf(
        "_generationRoundId", "_inputChanged", "_generationHistory", "_currentVersionId",
        "_activeKb", "_profileReview", "_kbNotice", "_panelWarning", "_vectorUpdate",
        "_stageSuggestion", "_activeTicket", "_providerReady", "_currentVector", "_vectorDelta",
        "_usageStats", "_resultMode", "_onlyThisRound", "_actualSentState",
        "_intentConfig", "_showIntentEditor"
    )

    @Test
    fun `viewmodel private flows are exactly the registered ones`() {
        val measured = privateFlowDecls(sourceOf("viewmodel/LoveBrainViewModel.kt")).toSet()
        val grew = (measured - stillInViewModel).sorted()
        val repaid = (stillInViewModel - measured).sorted()
        assertTrue(
            "ViewModel 里冒出没登记过的私有状态流：$grew —— 新的可写状态请放进对应的 store" +
                "（输入区那族的主人是 ComposerStore）。确实只能放 VM，就在这里写上它为什么无处可去。",
            grew.isEmpty()
        )
        assertTrue(
            "这 ${repaid.size} 颗已经不归 ViewModel 了：$repaid —— 搬家成功就把清单改短，" +
                "并把实到清单同步进 KDoc。留着旧名字，下一个人就不知道还剩多少没搬。",
            repaid.isEmpty()
        )
    }

    @Test
    fun `the scanner actually sees private state flows`() {
        // 正向对照：拿一个确实满是自己 flow 的文件，证明判据不是恒真的空扫描
        val seen = privateFlowDecls(sourceOf("viewmodel/SetupViewModel.kt"))
        assertTrue(
            "扫描器在 SetupViewModel 里只看到 ${seen.size} 颗私有状态流——" +
                "那条正则大概是死的，上面那格通过不代表有牙",
            seen.isNotEmpty()
        )
    }
}
