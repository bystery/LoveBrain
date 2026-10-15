package com.lovebrain.app.core.designsystem

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 通用输入框那颗**字阶档**参数（`LbFieldInput` 的 `textStyle`）的两条判据：
 *
 * ① 调用点交进来的那一档，必须**同时**落到可编辑节点与 placeholder 两条链上；
 * ② 调用点不传它时，那一档必须仍是这颗件今天写死的那一颗（`AppTypography.bodyMedium`），
 *    让在册的每一个调用点一寸都不动。
 *
 * 为什么开这一档而不是把字阶换掉：这颗件同时被供应商表单、知识库编辑页、回复输入那一族用着，
 * 而全站的字阶等级表有它自己的主人——个别行要「输入的字比它的行标签还小一档」，
 * 不该成为动全表的理由。于是这一格要的形制是「给公共件加一档带默认值的参数」。
 *
 * ⚠ **量法与它的边界**（用的是仓库那把唯一的掩码源码尺 `core/testing/SourceScan.kt`，
 * 不新造第三种仪器）：语义树那一族读不出字号——`SemanticsProbe.Target` 只有 bounds 与语义栏，
 * 没有字阶这一栏；像素那一族（截图基线）量的是整屏形状，换一张图要人工过眼，钉不住
 * 「这两处必须同档」这一件结构性的事。所以这里判**落点颗数与派生形状**，三条尺各管一件：
 *  - 落点尺：两条链各恰一颗，且各自包在它该包的那颗控件里；
 *  - 点名尺：组件里除了参数默认值那一格，不许再有第二处直接点名字阶档（那就是「这一处不跟参数走」）；
 *  - 默认值尺：默认值那一格点的必须是今天那一颗档名。
 * 「某一档真的是 11sp」这件事仍归像素那一族，这一族判不到，也**不许**拿它冒充。
 *
 * ⚠ 三条尺必须同时在场，谁也替不了谁——下面那两格反例证人就是把每一条尺单独打瞎一次：
 * 默认档漂移那一种，落点尺与点名尺**都看不见**（两处派生照常、点档名的地方仍只剩默认值那一格），
 * 只有默认值那一句会红；反过来「参数声明了、组件体里没人读它」时默认值尺是绿的，
 * 红的是落点与点名。
 *
 * 所有比对都走**掩掉注释、统一行尾**之后的文本（与 `LbChipTierTest` 同一手法）：
 * 这颗件的说明里本来就写着档名与参数名，不掩平会自己抵消一次真违约。
 */
class LbFieldInputTextTierTest {

    /** 一处落点：哪条链、包着它的是哪颗控件、在第几行（失败信息要说得出行号，不许只说「不等」） */
    private data class TierRead(val chain: String, val callee: String, val line: Int)

    /** 参数默认值那一格的形状，顺带把档名取出来：`textStyle: TextStyle = AppTypography.<档>` */
    private val tierDecl = Regex("""\btextStyle\s*:\s*TextStyle\s*=\s*AppTypography\.(\w+)""")

    /** 任何被点名的字阶档：出现在哪儿，就说明那一处是自己定的档、不跟调用点走 */
    private val namedTier = Regex("""AppTypography\.\w+""")

    /** placeholder 那条链的形状：`Text(` 里的 `style = textStyle` */
    private val placeholderChain = Regex("""(?<![A-Za-z0-9_])style\s*=\s*textStyle\b""")

    /** 可编辑节点那条链的形状：`BasicTextField(` 里的 `textStyle = textStyle…`（copy 走的仍是这一档） */
    private val editableChain = Regex("""(?<![A-Za-z0-9_])textStyle\s*=\s*textStyle\b""")

    /**
     * 两条链各自的名字、形状与「它必须坐在哪颗控件里」。
     * 换个控件名下接同一档，量的就不是这一格了——所以 `callee` 也要判，不只判颗数。
     */
    private val chains = listOf(
        Chain(PLACEHOLDER_CHAIN, placeholderChain, "Text"),
        Chain(EDITABLE_CHAIN, editableChain, "BasicTextField")
    )

    private data class Chain(val name: String, val shape: Regex, val owner: String)

    /**
     * 判据①：传进来的那一档真的落到了两处，而且组件体里没有第三处自己定了档。
     *
     * 反例（由 [the scan rejects a half wired tier] 逐颗喂给同一组尺）：
     * 只接 placeholder ⇒ 打的字仍吃写死那一档；只接可编辑节点 ⇒ placeholder 仍吃写死那一档。
     * 两种都是「半张脸」：同一行里灰字一个字号、打的字另一个字号。
     */
    @Test
    fun `the tier a caller passes reaches both the typed text and the placeholder`() {
        val code = fieldInputCode()
        val reads = tierReads(code)
        assertTrue(
            "字阶在这颗件里的落点该是两条链各一颗（placeholder 那颗 + 可编辑节点那颗），" +
                "实到 ${describe(reads)}——少一颗就是只接了一处，另一处仍吃默认档",
            reads.size == chains.size
        )
        chains.forEach { chain ->
            val read = reads.singleOrNull { it.chain == chain.name }
                ?: throw AssertionError(
                    "${chain.name} 这一处没接到参数上（只接另一处就是这个形状）：实到 ${describe(reads)}"
                )
            assertTrue(
                "${chain.name} 接的那一档必须坐在 ${chain.owner} 那一颗里面，" +
                    "实到 ${read.callee}（第 ${read.line} 行）：${describe(reads)}",
                read.callee == chain.owner
            )
        }
        val hardcoded = spotsOutsideTheParameter(code)
        assertTrue(
            "这颗件里除了参数默认值那一格，不许再有别处直接点名字阶档：实到第 " +
                hardcoded.joinToString("、") + " 行——那一处是绕开参数自己定的档，" +
                "调用点交进来的那一档到不了它（${describe(reads)}）",
            hardcoded.isEmpty()
        )
    }

    /**
     * 判据②：不传这一档时，它必须仍是今天那一颗档；新参数还得排在现有参数之后。
     *
     * 反例（由 [the scan rejects a drifted or unused default tier] 逐颗喂）：
     * 把默认值换成别的档 ⇒ 供应商表单、知识库编辑页、回复输入那一族在谁都没改调用点的前提下集体换脸；
     * 把新参数插到现有参数中间 ⇒ 位置实参整体错位，后面的调用点会认错对象。
     */
    @Test
    fun `omitting the tier keeps exactly the shape every existing caller draws today`() {
        val code = fieldInputCode()
        val declLines = tierDecl.findAll(code).map { SourceScan.lineOf(code, it.range.first) }.toList()
        assertTrue(
            "字阶档的默认值全文件只许写一处（写两处就等于有一条链不从这个默认值走），实到第 " +
                declLines.joinToString("、") + " 行",
            declLines.size == 1
        )
        assertTrue(
            "不传这一档时必须仍落在今天那一颗档上：默认值是 ${declaredDefault(code)}，要的是 $TODAY_TIER。" +
                "换掉它就是拿一格的需求去动全站每一个调用点——全站字阶有主人，不许在这儿换脸",
            declaredDefault(code) == TODAY_TIER
        )
        // 这颗件今天的调用点全是具名实参，参数表的顺序是它们唯一的形状护栏
        val newParamAt = code.indexOf("textStyle: TextStyle")
        val lastExistingAt = code.indexOf("onInputIntent: (() -> Unit)?")
        assertTrue(
            "新参数必须排在现有参数之后（实到 textStyle@$newParamAt、onInputIntent@$lastExistingAt）：" +
                "插到中间会把后面每一颗参数的位置实参整体错位",
            lastExistingAt >= 0 && newParamAt > lastExistingAt
        )
    }

    /**
     * 这颗件的行为清册：加这一档不许顺手带走任何一颗。
     *
     * 五态表、36dp 可见框与 48dp 热区分层、尾部槽、禁用仍报 Disabled、placeholder 转读屏名、
     * 密码遮蔽、编辑意图那两条上报链、单行/多行由 `maxLines` 说——每一族按落点颗数钉着，
     * 掉哪一族就点名是哪一族、第几行。颗数是这颗件今天的实扫读数，不是抄来的。
     */
    @Test
    fun `adding the text tier knob does not take any other behaviour of this field with it`() {
        val code = fieldInputCode()
        val lost = BEHAVIOUR_LEDGER.filter { entry ->
            lineNumbers(entry.shape, code).size != entry.expected
        }
        assertTrue(
            "字阶档这一改动带走了这颗件的行为：" + lost.joinToString { entry ->
                "「${entry.name}」该 ${entry.expected} 颗，实到第 " +
                    lineNumbers(entry.shape, code).joinToString("、").ifEmpty { "哪儿都没有" } + " 行"
            } + "（这颗件的其余形制一寸不许因为加了一档参数而退）",
            lost.isEmpty()
        )
    }

    /**
     * 反例证人（判据①）：**只接一处**的两种写法必须被落点尺与点名尺一起拒掉。
     *
     * 这一格是给上面那把尺验电的：没有它，「落点该有 2 颗」可能红在一件没发生过的事上，
     * 也可能因为尺瞎了而恒绿。末尾那颗正向对照把「尺与对象对得上」钉住，上面几句才不是空转。
     */
    @Test
    fun `the scan rejects a half wired tier`() {
        val onlyPlaceholder = fakeFieldInput(
            placeholderSite = "style = textStyle",
            editableSite = "textStyle = AppTypography.bodyMedium.copy(color = visual.textColor)"
        )
        assertTrue(
            "只接 placeholder 的写法必须被落点尺看出只有一颗（它若报 2 颗，这把尺就是瞎的）：" +
                describe(tierReads(onlyPlaceholder)),
            tierReads(onlyPlaceholder).size == 1
        )
        assertTrue(
            "只接 placeholder 时，可编辑节点那一处仍在点名字阶档，点名尺必须看见它：" +
                spotsOutsideTheParameter(onlyPlaceholder),
            spotsOutsideTheParameter(onlyPlaceholder).isNotEmpty()
        )

        val onlyEditable = fakeFieldInput(
            placeholderSite = "style = AppTypography.bodyMedium",
            editableSite = "textStyle = textStyle.copy(color = visual.textColor)"
        )
        assertTrue(
            "只接可编辑节点的写法同理，落点尺只该报一颗，实到 " + describe(tierReads(onlyEditable)),
            tierReads(onlyEditable).size == 1
        )
        assertTrue(
            "只接可编辑节点时，placeholder 那一处仍在点名字阶档，点名尺必须看见它：" +
                spotsOutsideTheParameter(onlyEditable),
            spotsOutsideTheParameter(onlyEditable).isNotEmpty()
        )

        val code = fieldInputCode()
        assertTrue(
            "正向对照：真组件这两把尺分别是 2 颗与 0 颗，实到 " + describe(tierReads(code)) +
                " 与 " + spotsOutsideTheParameter(code) + "——对不上就是尺与对象分家了，上面四句全成空转",
            tierReads(code).size == 2 && spotsOutsideTheParameter(code).isEmpty()
        )
    }

    /**
     * 反例证人（判据②）：默认档漂移、以及「参数声明了但组件体里没人读它」，各被哪一把尺抓住。
     *
     * ⚠ 这一格还如实登记一件不好听的事实：**默认档漂移时落点尺与点名尺都是绿的**，
     * 所以那一句等值断言不是装饰，是这一条判据唯一的牙。反过来「声明了却没人读」时等值断言抓不到，
     * 红的是落点与点名——两把尺谁也替不了谁，少一把就有一种坏形制能溜过去。
     */
    @Test
    fun `the scan rejects a drifted or unused default tier`() {
        val drifted = fakeFieldInput(
            placeholderSite = "style = textStyle",
            editableSite = "textStyle = textStyle.copy(color = visual.textColor)",
            defaultTier = "labelMedium"
        )
        assertTrue(
            "默认值漂到别的档时，落点尺与点名尺照旧是绿的（这一句登记的就是这个事实，不许假装看不见）：" +
                describe(tierReads(drifted)) + " / " + spotsOutsideTheParameter(drifted),
            tierReads(drifted).size == 2 && spotsOutsideTheParameter(drifted).isEmpty()
        )
        assertTrue(
            "默认档漂移必须由默认值那一句抓住：实到 ${declaredDefault(drifted)}，要的是 $TODAY_TIER",
            declaredDefault(drifted) != TODAY_TIER && declaredDefault(drifted) == "labelMedium"
        )

        val ignored = fakeFieldInput(
            placeholderSite = "style = textStyleBase",
            editableSite = "textStyle = textStyleBase.copy(color = visual.textColor)",
            localAlias = "val textStyleBase = AppTypography.bodyMedium"
        )
        assertTrue(
            "参数声明了、组件体里却没人读它（退成一颗本地常量）：落点尺必须报 0 颗，实到 " +
                describe(tierReads(ignored)),
            tierReads(ignored).isEmpty()
        )
        assertTrue(
            "同一种写法里那颗本地常量仍在点名字阶档，点名尺必须看见它：" + spotsOutsideTheParameter(ignored),
            spotsOutsideTheParameter(ignored).isNotEmpty()
        )
        assertTrue(
            "而这种退化的默认值仍是今天那一颗——等值断言抓不到它，只能靠上面两把尺：" +
                declaredDefault(ignored),
            declaredDefault(ignored) == TODAY_TIER
        )
    }

    // ═══════════════════ 尺的实现（三条，全部走掩过注释、统一行尾之后的文本） ═══════════════════

    /** 两条链的落点，各自配上「包着它的那次组合调用」的名字（按括号栈认，与 `SourceScan.enclosingCall` 同一形状） */
    private fun tierReads(code: String): List<TierRead> =
        chains.flatMap { chain ->
            chain.shape.findAll(code).map { read ->
                val open = SourceScan.enclosingCall(code, read.range.first)
                TierRead(
                    chain = chain.name,
                    callee = if (open == null) "读不到包它的调用" else calleeOf(code, open.first),
                    line = SourceScan.lineOf(code, read.range.first)
                )
            }
        }.toList()

    /** 参数默认值之外**每一处**点名字阶档的行号：非空就等于有一处不跟调用点走 */
    private fun spotsOutsideTheParameter(code: String): List<Int> {
        val declRanges = tierDecl.findAll(code).map { it.range }.toList()
        return namedTier.findAll(code)
            .filter { at -> declRanges.none { range -> at.range.first in range } }
            .map { SourceScan.lineOf(code, it.range.first) }
            .toList()
    }

    private fun declaredDefault(code: String): String? = tierDecl.find(code)?.groupValues?.get(1)

    /** 从左括号往回取调用名——与 `SourceScan.enclosingCall` 认名字的规则逐字同一，两处才对得上 */
    private fun calleeOf(code: String, open: Int): String {
        val head = code.substring(0, open).trimEnd()
        var i = head.length
        while (i > 0 && (head[i - 1].isLetterOrDigit() || head[i - 1] == '_')) i--
        return head.substring(i)
    }

    private fun lineNumbers(pattern: Regex, code: String): List<Int> =
        pattern.findAll(code).map { SourceScan.lineOf(code, it.range.first) }.toList()

    private fun describe(reads: List<TierRead>): String =
        if (reads.isEmpty()) "一颗都没接到参数上"
        else reads.joinToString { "${it.chain}→${it.callee}(第${it.line}行)" }

    /**
     * 坏形制共用的假组件：**只有参数默认值那一格与两条链的写法可变**，其余照抄真件。
     *
     * 它必须长得像真件（`Text(` 与 `BasicTextField(` 两颗都在），否则落点尺在这里量到的
     * 就不是它要量的那件事——反例证人喂进去的东西若连控件都没有，拒掉它不算牙。
     */
    private fun fakeFieldInput(
        placeholderSite: String,
        editableSite: String,
        defaultTier: String = "bodyMedium",
        localAlias: String = ""
    ): String = SourceScan.maskComments(
        """
        @Composable
        fun LbFieldInput(
            value: String,
            onValueChange: (String) -> Unit,
            placeholder: String,
            onInputIntent: (() -> Unit)? = null,
            textStyle: TextStyle = AppTypography.$defaultTier
        ) {
            $localAlias
            if (value.isEmpty()) {
                Text(text = placeholder, $placeholderSite, maxLines = 1)
            }
            BasicTextField(value = value, onValueChange = onValueChange, $editableSite)
        }
        """.trimIndent()
    ).replace("\r\n", "\n")

    /** 生产那颗件的源码（剥注释、统一行尾）：这一族源码尺的物件 */
    private fun fieldInputCode(): String {
        val rel = "core/designsystem/LbFieldInput.kt"
        val file = File("src/main/java/com/lovebrain/app/$rel").takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/$rel")
        assertTrue("找不到 $file——路径接错了这一族尺会恒绿", file.isFile)
        val code = SourceScan.maskComments(file.readText(Charsets.UTF_8)).replace("\r\n", "\n")
        // 反向证人：这把尺看得见这个文件本身（空串=扫了个寂寞，下面每一格都会恒绿）
        assertTrue("$rel 读出来是空的，这把尺已经看不见东西", code.isNotBlank())
        return code
    }

    private companion object {
        const val PLACEHOLDER_CHAIN = "placeholder 那一档"
        const val EDITABLE_CHAIN = "可编辑节点那一档"

        /** 这颗件今天写死的那一档：默认值与它逐字同一颗，既有调用点才一寸不动 */
        const val TODAY_TIER = "bodyMedium"

        /** 清册里的一族：它在管什么、认它的形状、该有的颗数 */
        private data class Ledger(val name: String, val shape: Regex, val expected: Int)

        /**
         * 这颗件的行为清册（颗数 = 今天对这份源码的实扫读数）。
         * 这一族注释里点到的名字一律在掩过注释的文本上判，改注释不会动到这里的数。
         */
        val BEHAVIOUR_LEDGER = listOf(
            Ledger(
                "五态表那一列状态到形状的映射",
                Regex("""LbFieldVisualState\.(Resting|Focused|Filled|Error|Disabled)\s*->"""),
                5
            ),
            Ledger(
                "热区层垫到全站下限那一句",
                Regex("""heightIn\(min\s*=\s*AppDimens\.TOUCH_TARGET_MIN_DP\.dp\)"""),
                1
            ),
            Ledger(
                "可见框那一档仍由视觉层自己读",
                Regex("""height\(AppDimens\.INPUT_ROW_HEIGHT_DP\.dp\)"""),
                1
            ),
            Ledger(
                "尾部槽只在这里读一次",
                Regex("""AppDimens\.INPUT_TRAILING_SLOT_DP"""),
                1
            ),
            Ledger(
                "点空档获焦时上报编辑意图",
                Regex("""onInputIntent\?\.invoke\(\)"""),
                1
            ),
            Ledger(
                "触碰输入区上报编辑意图那条链",
                Regex("""if\s*\(\s*onInputIntent\s*!=\s*null\s*\)"""),
                1
            ),
            Ledger(
                "上面那条链自己发出的那一颗",
                Regex("""(?<![A-Za-z0-9_])onInputIntent\(\)"""),
                1
            ),
            Ledger(
                "placeholder 转可编辑节点的读屏名",
                Regex("""contentDescription\s*=\s*placeholder"""),
                1
            ),
            Ledger(
                "禁用仍在树上并多报一个 Disabled",
                Regex("""SemanticsProperties\.Disabled"""),
                1
            ),
            Ledger(
                "密码遮蔽",
                Regex("""PasswordVisualTransformation\(\)"""),
                1
            ),
            Ledger(
                "状态到形状只有这一处映射",
                Regex("""lbFieldVisual\(state\)"""),
                1
            ),
            Ledger(
                "单行与多行仍由 maxLines 说",
                Regex("""singleLine\s*=\s*maxLines\s*==\s*1"""),
                1
            )
        )
    }
}
