package com.lovebrain.app.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 用户可见字面量的预算（ ）。
 *
 * 报告原话：静态计数至少还有 `Text("中文...")` 101 处、`contentDescription = "中文..."` 10 处，
 * 并规定"新 UI 架构必须禁止 production composable 新增直接用户可见字面量"。
 *
 * 本轮没有能力把 101 处全搬进 strings.xml（那是阶段三与 `Lb*` 组件一起做的工作），
 * 但**先把闸装上**是有意义的：不做闸，阶段三每写一个新组件都会顺手再塞几条中文，
 * 到时候还是 101 → 150，"文案收口"永远在下一轮。
 *
 * 与 [PackageDependencyTest] 同一套棘轮语义：超出预算 = 新增债；
 * 实测低于预算 = 债还掉了但没落账，也红。
 */
class UiStringLiteralBudgetTest {

    private enum class Kind(val label: String, val anchor: Regex) {
        /** `Text(` ——整段实参里任何带中文的字符串字面量 */
        TEXT("Text 可见文案", Regex("""\bText\s*\(""")),

        /** `contentDescription =` ——赋值右边那段表达式里带中文的字面量 */
        DESC("contentDescription", Regex("""\bcontentDescription\s*=\s*""")),

        /**
         * `stateDescription =` ——读屏公告"现在收起/展开"那一句。
         *
         * 这是第二个盲区：它既不是 `Text(` 也不是 `contentDescription`，
         * 但同样是**用户听得见的话**（`SuggestPanel` 与 `CounselingPanel` 那两处折叠控件
         * 原本写着内联中文，英文环境下照样念中文，而两把尺都看不见）。
         * 起点就是 0——本轮把锦囊那处搬进资源了，谈心那处还欠着，见下方交接行。
         */
        STATE("stateDescription", Regex("""\bstateDescription\s*=\s*""")),

        /**
         * `Lb…(` ——设计系统组件的**具名实参**里写的中文。
         *
         * 这一栏是 第6节第1条 搬家逼出来的：`HomeTopBar` 通用化成 `LbTopBar(title =, subtitle =)` 之后，
         * 「帮你更自然地表达」从 `Text("…")` 里挪进了组件参数，而 TEXT 那把尺的锚点是 `Text(`，
         * 于是实扫从 247 掉到 246。**那不是我搬走了一处硬编码**，同一条字符串还在原处，
         * 只是尺看不见了。按本文件自己的规矩（"搬掉两处而 DESC 一个没动"那次），
         * 计数变了必须先证明变化是真的，才许动预算数字。
         *
         * 口径：整段实参里带中文的字面量，**减去已被前三栏区间覆盖的那些**——
         * 所以 `LbCard { Text("中文") }` 只在 TEXT 记一次，不会两栏重复入账。
         *
         * 边界（仍然欠着的那半）：只认 `Lb` 前缀的设计系统组件。页面自造的子组件
         * （`StatCell(label = "…")` 这种）依旧在盲区里， 第4节 有这一行。
         */
        COMPONENT("组件实参里的可见文案", Regex("""\bLb\w+\s*\(""")),
    }

    /** 一行以内、含中日韩字符的字符串字面量 */
    private val HAN_LITERAL = Regex(""""[^"\n]*[\p{IsHan}][^"\n]*"""")

    /**
     * 从锚点往后截"这一个表达式"的范围。
     *
     * 为什么不是正则一行搞定：`Text(text = if (proactiveActive) "中文A" else "中文B", …)`
     * 这种写法里，锚点和字面量之间隔着一层带括号的判断条件——一条只看"锚点后面紧跟引号"的
     * 正则永远数不到它。本轮从 `MessageList` 里搬走两处中文时就是这么发现的：
     * **它们当时根本不在这把尺的计数里**，搬完了预算数字一个字都不用改，那笔账就成了假账。
     * 所以这里按括号配对取范围，而不是按"紧跟不紧跟"。
     */
    private fun expressionAt(text: String, from: Int, kind: Kind): String {
        val anchorIsCall = kind == Kind.TEXT || kind == Kind.COMPONENT
        var depth = if (anchorIsCall) 1 else 0
        var i = from
        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    if (depth == 0) return text.substring(from, i)
                    depth--
                    // 只有锚点本身是"函数调用"（Text(）时，配对回到 0 才是这段实参的结束。
                    // 赋值型锚点（contentDescription = / stateDescription =）不能在这里收尾：
                    // `contentDescription = if (expanded) "收起" else "展开"` 的 if 条件括号会把切片
                    // 截断，于是那两处中文又数不到了——本轮第一版就栽在这行上（预算报 DESC 没变，
                    // 而我刚搬掉了两处，这个"没变"本身就是线索）。
                    if (anchorIsCall && depth == 0) return text.substring(from, i)
                }
                ',' -> if (depth == 0 && !anchorIsCall) return text.substring(from, i)
                '\n' -> if (!anchorIsCall && i + 1 < text.length &&
                    text[i + 1] != '+' && text[i + 1] != '"'
                ) {
                    // 赋值换行且下一行不是续着写的字符串/拼接 → 表达式到此为止
                    if (depth == 0) return text.substring(from, i)
                }
            }
            i++
        }
        return text.substring(from)
    }

    private fun countHanLiterals(text: String, kind: Kind): Int = when (kind) {
        Kind.COMPONENT -> {
            // 前三栏已经覆盖的字符区间（半开区间 [start, end)）：
            // 落进去的字面量已经入过账，这里只记漏下来的
            val covered: List<Pair<Int, Int>> = Kind.values()
                .filter { it != Kind.COMPONENT }
                .flatMap { other ->
                    other.anchor.findAll(text).map { m ->
                        val from = m.range.last + 1
                        from to (from + expressionAt(text, from, other).length)
                    }
                }
            // **按字符区间去重**再计数：`LbDialog(title = …, confirm = LbDialogAction(…))`
            // 这种嵌套调用，外层实参切片与内层切片会各含同一条字符串一次。
            // 按锚点求和的话，"把一颗按钮换成两颗 Lb 组件嵌套"就会凭空涨几笔——
            // 涨的是量具的重复计数，不是债（第26节第3条 那次 247→246 的教训反过来同样成立）。
            val ranges = LinkedHashSet<Pair<Int, Int>>()
            Kind.COMPONENT.anchor.findAll(text).forEach { m ->
                val from = m.range.last + 1
                HAN_LITERAL.findAll(expressionAt(text, from, Kind.COMPONENT)).forEach { lit ->
                    val start = from + lit.range.first
                    val end = from + lit.range.last + 1
                    if (covered.none { (lo, hi) -> lo <= start && end <= hi }) ranges += start to end
                }
            }
            ranges.size
        }
        else -> kind.anchor.findAll(text).sumOf { match ->
            HAN_LITERAL.findAll(expressionAt(text, match.range.last + 1, kind)).count()
        }
    }

    /**
     * 实测基线。**数字来自这把尺对 app/src/main 的一次实扫**，不是照抄的 101。
     *
     * 三把尺的关系（都留档，不然下一次又有人拿最小的那个数当全量）：
     * - 的 **101**：只数 `Text("中文`，是下界；
     * - 的正则 **209**：多认 `Text(text = "中文…")` 与跨行写法，仍看不见
     *   `Text(text = if (…) "中文" else "中文")`，还是下界；
     * - 换成按括号配对取整段实参 → TEXT **254**；搬掉 4 处后 **250**。
     * - 第6节第1条 把知识库页那张自造空态卡换成共用组件，又搬掉 3 处（标题、指路文案、底部那颗
     *   "新建知识库"）→ **247**。DESC / STATE 两栏这次没动。
     * - 第6节第1条 把五颗首页组件搬进 core/designsystem 时，TEXT 掉到 **246**——
     *   **这一条不是还债**：掉的那处是「帮你更自然地表达」，它只是从 `Text("…")`
     *   变成了 `LbTopBar(subtitle = "…")`，字符串一个字没动，是锚点 `Text(` 看不见它了。
     *   所以同一次加了 COMPONENT 这一栏（起点 16，全仓实扫），246 + 1 对得上旧的 247。
     * - 第6节第1条 收浮层（11 处 `AlertDialog` → `LbDialog`）之后 **TEXT 209 / COMPONENT 59**。
     *   这次两栏一起动，账要摊开说清：
     *   ① 37 处从 TEXT 挪进 COMPONENT（`Text("取消")` → `LbDialogAction("取消")`），
     *   246 − 37 = 209、16 + 37 = 53 —— 这 37 处**一条都没还**，只是换了形状；
     *   ② COMPONENT 另外 +6 是**以前两栏都看不见的**：写在 `TextButton(onClick = { … })` 里的
     *   提示语（"清空失败，请重试"、"文件已被后台修改，请重新打开"、"没有可用的分享应用"…），
     *   它们搬进 `LbDialogAction(onClick = { … })` 之后才落进实参切片。总数 262 → 268，
     *   **涨的 6 条是量具新看见的既有债，不是这次新塞的**（与 209 → 254 那次同一回事）；
     *   ③ 这一栏改成**按字符区间去重**再计数：嵌套调用（`LbDialog(…, confirm = LbDialogAction(…))`）
     *   外层与内层切片各含同一条字符串一次，按锚点求和会虚报（同一次实扫 85 vs 去重后 59）。
     *   不做这件事的话，"把一颗按钮拆成两颗 Lb 组件"都会让数字涨，那涨的是量具自己。
     * - 第6节第1条 的 Sheet 半边（`PanelModalHost`/`Title`/`Actions` → `LbModalSheet*`）之后
     *   **COMPONENT 59 → 66（+7）、TEXT 不动**。这 7 条全是**既有的**用户可见文案：
     *   「暂停时长」「标记为错误」「取消」×3、「确认」「保存」——原先写在 `PanelModalTitle("…")`
     *   与 `PanelModalActions(confirmLabel = "…")` 这类**非 `Lb` 锚点**里（"取消"还是旧组件的默认实参），
     *   三把老尺一条都看不见；换名进设计系统之后才被量到 ⇒ 涨的是量具新看见的既有债。
     * - 第6节第4条 第一刀（`RecordSentDialog` 与导出 Loading 的自画遮罩并进 `LbModalSheet`）之后
     *   **TEXT 209 → 205、COMPONENT 66 → 69、DESC 仍 12**。摊开说：
     *   ① 3 条从 TEXT 进 COMPONENT（「记录实际发送」「取消」「确认已发送并记录」从 `Text("…")`
     *   变成 `LbModalSheetTitle(…)` / `LbDialogAction(label = …)`）——换形状，没还债；
     *   ② 1 条**真的还掉了**：那颗输入框的提示语进了 `R.string.panel_record_sent_hint`（zh+en）。
     *   起因是守卫量到它"既没文案也没 contentDescription"，而修读屏名要让同一句话用两次——
     *   源码里写两遍是新的维护债，进资源才是收口。合计 275 → 274，减的就是这一条。
     * - 第6节第5条 输入框读屏名字那一格（第31节）再搬掉 **3 条**：TEXT 205 → **202**。
     *   这 3 条是屏幕上真实存在的说明文字（「补充说明（可选）」「你期望怎么回？（可选）」
     *   「输入正确内容（可选，留空仅停用）」）——为了让学生输入框共用同一句话才进 strings.xml
     *   （zh + en 各一份）⇒ 真还掉的债，不是换形状躲开锚点。同格新增的另外 2 条资源
     *   （`kb_edit_editor_hint`、`scheme_custom_hint`）只给读屏用、屏幕上不画，所以不减 TEXT。
     * - 第6节第4条 第三刀（纠正中心从内联展开区改成 `LbModalSheet`）之后
     *   **TEXT 202 → 199、COMPONENT 69 → 72，四栏合计 283 → 283 一个字没变**。
     *   摊开说：这**不是还债，是换桶**。「记忆纠正中心」「关闭」「撤销」三条从
     *   `Text(text = "…")` 搬进 `LbModalSheetTitle(…)` / `LbDialogAction(label = …)`
     *   ——文案原样，只是所有者从"页面自己画"变成"设计系统的动作词表"，
     *   而这正是 第6节第1条/第6节第4条 要的方向，所以涨的是 COMPONENT 那一栏。
     *   另外这一格还**真的删掉了 3 条**中文（`[本轮] / [今天] / [恢复]` 三份手抄，
     *   改成复用纠正浮层那份跟着 enum 走的 `durationLabel()`），但它们在删之前
     *   就**不在任何一栏里**——写在 `when` 分支上的字面量，`Text(` 和 `Lb*(` 两个锚点
     *   都看不见。所以合计不动：**"合计没变"这次是真没变，不是尺子在漏**
     *   （区分这两件事的办法就是上面这笔逐条对上，而不是只看总数）。
     * - 第6节第1条 把五颗首页组件搬进 core/designsystem 时，TEXT 掉到 **246**——
     *   **这一条不是还债**：掉的那处是「帮你更自然地表达」，它只是从 `Text("…")`
     *   变成了 `LbTopBar(subtitle = "…")`，字符串一个字没动，是锚点 `Text(` 看不见它了。
     *   所以同一次加了 COMPONENT 那一栏（起点 **16**，全仓实扫），246 + 1 那条落进新栏 =
     *   原来的 247，总数一笔没少。以后再把文案搬进组件参数，涨的是 COMPONENT，照样红。
     *
     * - 第6节第4条 第四刀（点踩原因面板改成 `LbModalSheet`）之后又是同一笔账：
     *   **TEXT 199 → 194、COMPONENT 72 → 77，四栏合计仍 283**。
     *   这 5 条就是「这条回复哪里不满意？」进 `LbModalSheetTitle`、
     *   「跳过」「保存反馈」「去改消息」「去纠正记忆」进 `LbDialogAction`——
     *   一字没动，从"页面自己画的 `Text(`"换进"设计系统的动作词表"。
     *   顺手补一句免得下次对账对不上：那一屏的 chip 文案（「理解错误」「角色错」…）
     *   是写在 `CategoryChipRow(label = "…")` 这种**普通函数实参**上的，
     *   `Text(` 与 `Lb*(` 两个锚点从来都看不见它，所以它既没进 TEXT 也没进 COMPONENT，
     *   这一格前后都一样——**不是又漏了，是它本来就在这把尺的射程之外**。
     *
     * - 第6节第1条 页头归一（四式收成一颗 `LbTopBar`）之后：
     *   **TEXT 194 → 192、DESC 12 → 11、COMPONENT 77 → 80，四栏合计仍 283**。
     *   这一笔**不是三栏各自都对得上**，要说清：
     *   ① DESC 那 −1 是**真还掉的**——`ScreenHeader` 里 `contentDescription = "返回"`
     *     那串硬编码中文进了 `R.string.common_back`（zh + en）。它是本轮少数几条
     *     "改完确实少了一条中文字面量"的，英文环境从此念 "Back" 不念中文。
     *   ② 三页标题（「关于」「使用概览」「模型供应商」）从 `Text("…")` 搬进
     *     `LbTopBar(title = "…")`，所以 COMPONENT +3；可 TEXT 只降了 2。
     *     ⇒ **有一条我没能归给它原来的那一栏**：按锚点配对，那一条在改之前
     *     既不在 TEXT 也不在 COMPONENT（改之后才落进 `Lb*(` 的实参切片里）。
     *     我没有现扫证据能指认是哪一条，所以**只写"有一条没归上"**，不编一个解释。
     *     总数仍然 283 不动，是因为 ① 少的那一条正好抵掉它。
     *   ③ 结论与前两格同一句：换尺让数字变大不是债涨了；而"合计没动"这件事
     *     每次都要这样逐栏对上才许写，对不上就写下对不上在哪一栏。
     *
     * 注意上面那串 254 / 250 / 247 是**历史**，不是现在值：现在值只有一处真源，
     * 就是下面 `budget` 里那几个数（`the budget still reflects reality` 那格保证两边不一致时报红）。
     * 所以别往这段说明里续抄数字——历史可以记，读数一律看常量。
     *
     * DESC 这一栏要单独记一笔：本轮第一次改尺时**赋值型锚点的切片被内层括号截断了**——
     * `contentDescription = if (expanded) "收起" else "展开"` 里那对 if 条件括号提前收尾，
     * 于是"搬掉两处中文之后 DESC 一个没变"。那个"没变"本身就是尺子还在漏的证据。
     * 修好之后 DESC = **12**（而修之前那次量到的 10 同样是下界）。
     *
     * 结论：换尺让数字变大不是"债涨了"，是量到了以前漏的。棘轮照旧只许往下走。
     */

    /*
     * ============================================================================
     * 已登记例外（正式账本 · 档 B「登记例外，零生产改」，主线程已拍板）
     * ============================================================================
     *
     * 先说清这一格凭什么算"登记"：本尺的硬判据**只有**"四个锚点射程内的中文字面量 ≤ 预算"
     * （`user visible string literals do not grow` / `the budget still reflects reality` 两格），
     * 它从不要求"一切中文必须进 strings.xml"。下面两族的中文全部写在 enum 构造参、顶层 const
     * 与 `when` 分支上，四个锚点（`Text(`、`contentDescription =`、`stateDescription =`、`Lb…(`）
     * 一条都数不到它们 ⇒ 登记不改变任何一栏读数，push 前门 RC 恒为 0。
     * 本文件没有白名单数据结构可挂，注释账本就是这仓的例外登记形态；上一格  里
     * "仅注释提过、不是正式免检"的那两处，自本轮起升为正式条目。
     *
     * ──  · AdvisorStatus（Context-free 结构性例外）─────────────────────────
     * 现场：app/src/main/java/com/lovebrain/app/ui/home/AdvisorStatus.kt——
     *   `AdvisorMissing(label = "…")` 8 颗 enum 构造参中文（:40-64）、7 颗顶层 const（:124-137），
     *   `describe()` 用 " · " 运行时 JOIN（:62）拼成 `AdvisorRender.hint: String`（:82）。
     * 例外理由：合同规定 describe()/render() 是 **Context-free** 纯函数——plain-JVM 测试现场
     *   没有 Robolectric 就拿不到 Context，`HomeStatusViewModelTest`、`AdvisorStatusTest`、
     *   `HomeStatusDiProbeTest` 三颗 JVM 用例逐字钉着 `render().hint` 的中文。改 `@StringRes`
     *   不是搬字符串：要重写 AdvisorRender 数据模型（hint → hintRes/hintParts）+ 这 3 颗测试，
     *   " · " 拼接与 null-不画语义易搬断，还丢掉 JVM 侧最终句逐字判据（没做）。
     * 债声明：英文环境仍念中文，是真 i18n 缺陷而非账面问题；日后真做国际化走档 A
     *   （约 5 生产 + 3 测试 + 2 资源、新增 15 键），不许只降 TEXT 预算数字了事（账本 第48节第3条）。
     * ：
     * 状态： 完成-本机已验（有已登记例外）。
     *
     * ──  · durationLabel 拼接族（族级例外）──────────────────────────────────
     * 现场：`ui/panel/reply/MemoryCorrectionFlow.kt` `durationLabel()` 三颗 `when` 分支中文
     *   （:179-181「仅本轮 / 今天剩余 / 直到手动恢复」）+ `ui/panel/reply/CorrectionCenter.kt`
     *   `centerLabel()` 四颗（:187-190「不对 / 已结束 / 暂时别提 / 不是她」），
     *   在 CorrectionCenter.kt:166 拼成「暂时别提·今天剩余」。
     * 例外理由：这是**跨两文件的拼接族**，判据只许整族走。逐颗搬会出两种红：
     *   ① `CorrectionRecordRowSemanticsTest` :107/:154 逐字钉着拼接串，搬一半必断言红；
     *   ② 只搬时长侧，UI 直接生成「暂时别提·Skip for…」半中半英标签——那是用户可见缺陷，
     *     不是测试债（档 C 已因此否决）。
     *   `when` 分支上的字面量本就不在四把尺射程 ⇒ 单独搬零预算收益；族级免死批注（
     *   那一格与  COMPONENT 账）自此条目起为权威留痕。日后国际化走档 A：
     *   一次覆盖七颗 + 三处测试锚点（含 `MemoryCorrectionFlowTest` :366/:425），不许拆半。
     * ：
     * 状态： 完成-本机已验（族级例外）。
     *
     * ⚠ 边界：这两条登记的是**现存字面量**的免迁，不是这几个文件的免检——同样的中文若哪天
     *   落进四个锚点射程（比如在消费点内联 `Text("…·…")` 拼好的串），照旧进账、照旧红。
     */

    // ⚠ 这四个数是**剥掉注释之后**量的（`maskComments`）。没剥之前是 191 / 80，
    // 多出来的 3 条 TEXT、2 条 COMPONENT 全在注释里——其中一条就是本轮新写的
    // `MiniSwitchRow` KDoc 里那句「原来这里画的是 Text("思考模式")」，
    // 它正好抵掉本轮真还掉的那一处搬家。按形状认的尺不剥注释，就会这样自己吃自己。
    private val budget = mapOf(
        // 187 → **185**：表单那颗「保存 / 保存修改」归 `LbPrimaryButton` 时顺手搬进资源
        // （两条中文一起走 `values` + `values-en`）。⚠ 这处的账不好核：搬进 `Lb…()` 的实参
        // 之后 COMPONENT 那栏一度涨到 80——**同一条债从 TEXT 挪到 COMPONENT 不算还债**，
        // 只有换成资源才是。两栏一起看才不会把搬家当成还债（坑表 88 那一族的另一面）。
        // 185 → **183**：问卷那两颗主动作的标签（「下一步」「完成，AI 生成画像」）
        // 归 `LbPrimaryButton` 时先变成 COMPONENT 栏的 80>78，再搬进资源才真的还掉——
        // **同一条债换抽屉不算还**（账本 第48节第3条）。
        // 182 → **178**：三处「点击重试」+ 一处「去设置」并进资源（账本 第51节）。
        // ⚠ 三处「点击重试」原来在三个文件里各写一遍、还各自带一条写着"热区 ≥24dp"的注释——
        //   同一句话抄三遍，连"多少算达标"都各自抄了一遍。
        // 178 → **174**：反馈案例页归一 `LbScreenScaffold`/`LbTopBar` 时那四句进资源（账本 第58节）
        //   ——页头标题、「(N条)」、两颗导出标签。⚠ 这一笔是**真还**，不是换桶：
        //   搬进 `LbTopBar(title = "…")` 只会把它们从 TEXT 挪到 COMPONENT（同一格 COMPONENT 没涨），
        //   只有换成 `stringResource` 才算少一条中文字面量。
        // 174 → **170**：第6节第1条 组件归并那两颗（`ProviderEditDialog` → `LbDialog`、
        //   `IntentEditorDialog` → `LbModalSheet`）。⚠ **这一笔不是还债，是换桶**：
        //   「添加供应商 / 编辑供应商」从表单本体的 `Text(…)` 抬进 `LbDialog(title = …)`，
        //   那段说明文字从 `Text(…)` 抬进 `LbModalSheetTitle(…)`——四条字面量（那句说明里带
        //   转义引号，`HAN_LITERAL` 按字符切成两条，改前改后都是两条，笔数没动）
        //   一个字没改，只是从 TEXT 那一栏落进 COMPONENT 那一栏。
        // 170 → **169**：上面那一笔归并真正合进主干之后**复测**的结果，比预测少一条。
        //   少的这一条不是还掉的：`OngoingSection` 那颗折叠标题也换了桶（TEXT 6 → 5、
        //   COMPONENT 0 → 1），写预测那一版时它还在另一只 worktree 里、没进这一栏。
        //   ⇒ 逐文件实测（HEAD `ba3a20b` vs 工作区，剥注释）：TEXT −5 =
        //     `ProviderSection` −2、`SuggestPanel` −2、`OngoingSection` −1，
        //     **五条全部落进 COMPONENT，一条都没少**——换桶不是还债，这一栏降 5 不代表
        //     债少了 5，别拿它当战果（坑表 88 那一族）。
        //   ⚠ 交叉核对：另用一把独立写的 Python 尺（`_temp/bucket_clone.py`，同样剥注释、
        //     同样按括号配对取实参、同样按字符区间去重）在同一棵树上量到 **169 / 87**，
        //     与本文件 Kotlin 尺的读数逐字相同 ⇒ 这两栏的差值不是某一把尺自己抖出来的。
        // 169 → **168**：纠正中心那句空态说明从自己画的 `Text("…")` 抬进 `LbEmptyState(message = …)`。
        //   **换桶，不是还债**（同一条字面量一个字没改，只是从 TEXT 那栏落进 COMPONENT 那栏）；
        //   主动发那一处交的是 `PanelStrings.PROACTIVE_EMPTY_HINT`（标识符，不是字面量），所以两栏都不动。
        //   ⇒ 交叉核对：独立 Python 尺 `_temp/bucket_clone.py` 在同一棵树上报 **168 / 88**，与本尺逐字相同。
        // 168 → **166**：提示条归并时把两处内联中文接上资源（`panel_input_changed`、
        //   `a11y_close_notice` 那颗关闭钮——**资源里中英两份一直都在、从没被引用过**，
        //   于是英文环境读屏念中文）。这一笔是**真还**，不是换桶。
        // 166 → **165**：`KbEditActivity` 接 第6节第3条 四态时，卡内那颗自画空态的文案进了
        // `values` + `values-en` 两份资源（账本 第78节）。⚠ 这一笔是**真还**不是换桶：
        // 同一次改动里 COMPONENT 一栏没跟着涨（版式交 `LbAsyncState` 画，文案不再写在页面的 `Text(` 里）。
        // 165 → **162**：第6节第1条 三处胶囊归并进 `LbChip`（IntentExpiryChip /「清空重聊」/「继续追问」），
        //   三处中文标签从 `Text("…")` 落进 `LbChip(label = …)`——**换桶不是还债**
        //   （字面量一个字没改，只是从 TEXT 那栏落进 COMPONENT 那栏；COMPONENT +3 = TEXT −3，合计 0）。
        // 161 → **160**：外部复核 （累计费用把"未知"说成"免费"）把「使用概览」详情页那一行
        //   从 `Text("累计花费：$costStr")` 换成 `Text(stringResource(R.string.cost_stated_row, …))`。
        //   **真还一条**（zh + en 两份资源都给了），不是换桶：同一格里 COMPONENT 没跟着涨。
        //   ⚠ 归属：改动前同一棵树实扫是绿的 161/6/0/93（那棵树已含另一拍在改的
        //   ProviderSection / SetupViewModel / AppConfig），改完读到 160/6/0/92，
        //   两条差值一一对上本轮的两次搬家（这里 −1 TEXT、`HomeScreen` 那颗标签 −1 COMPONENT）。
        //   另一拍若再动中文字面量，那两个数由那一批自己续账。
        // 160 → **157**：语音模式整体删除（`SchemeRecordingBlock.kt` 整块消失、
        //   `SchemeCard.kt` 的手势注释与 `LoveBrainPanelScreen.kt` 的两条麦克风提示与引导语半句一起走）。
        //   这一档是**真删**：那些字面量所属的界面不再存在，不是搬家，所以 COMPONENT 一栏没跟着动
        //   （实测 92 → 92）。
        // 157 → **149**：⚠ 这一格不是我本轮动的——本机工作区里首页/面板那一族先行的搬家
        //   （逐文件对 HEAD 实扫）：`MemoryRefsFeed` −1、`RecordSentFlow`（整块删除）−2、
        //   `SchemeAdjustingBlock` −2、`SchemeRewriteResultBlock` −3 = −8，四条一一对得上那四个文件。
        //   登记在这里只因为这把尺量的是**当前工作区**，别把它当成上一批交账的读数。
        // 149 → **125**：使用概览页、关于页、反馈案例页这三屏的内联中文一次搬清（zh + en 两份同进）。
        //   逐文件实测（同一把尺，剥注释）：`UsageDetailScreen` −6（页内那五行统计标签 + 卡标题
        //   「累计统计」）、`AboutScreen` −5（版本行、副标语、「隐私说明」、隐私那两句、「诊断信息」）、
        //   `FeedbackCasesScreen` −13（补充、未知模型、期望版本、本轮想法、意图、上下文模式、
        //   真实对话标题、对话行的「对方」「我」、记忆引用、版本行、导出预览那两句提示）。
        //   **三条都是真还不是换桶**：搬完这三个文件的 TEXT 读数都是 0，COMPONENT 也没跟着涨。
        //   ⚠ 另外有六条本来就不在这四把尺的射程里、本轮一起进了资源：`statusDisplayName` 里
        //   CaseStatus 那四个词（写在普通函数的 `when` 分支上，`Text(` 与 `Lb*(` 都看不见它），
        //   以及 `saveError` 那两处赋值（「保存失败：无法打开输出流」、「保存失败：%1$s」）。
        //   它们和「对方」「我」一样，英文环境下读屏念的一直是中文——**账上少 24 条不等于屏上少了
        //   24 条**，这里降的是量到的那部分，没量到的那六条这次也一并清了。
        // 125 → **101**：（删过程说明/内部参数/重复帮助）+ 等 文案族那两批（）
        //   把剩下的内联中文接进 `values` + `values-en` 两份资源，本轮实扫 TEXT = **101**。
        //   ⚠ 这一笔是**真还**不是换桶：同一批 COMPONENT 那栏也跟着降（67 → 50），
        //   两栏一起降才说明债真的少了——只有一栏降、另一栏涨的那种是搬家（坑表 88 那一族）。
        // 101 → **95**：等 文案族本轮（）从 src/main 搬掉 6 条内联中文。
        //   逐文件实扫（剥注释）：`AccessibilityDisclosureDialog` −5（披露那五段正文进
        //   `capture_disclosure_*`，zh+en 各一份）、`MemoryCorrectionFlow` −1（那颗输入框占位
        //   「输入正确的内容」进 `memory_wrong_input_placeholder`）。**真还**：同一批 COMPONENT
        //   那栏跟着降 8（见下），两栏一起降才成立。
        //   ⚠ 本轮还有两处**没动**、也不在本栏射程里：① `AdvisorStatus.kt` 那族黄字/灯名、
        //   ② `durationLabel()` 与 `CorrectionCenter.centerLabel()` 的拼接族——两处均已升为
        //   本文件顶部的正式登记条目（ Context-free 结构性例外 /  族级例外，
        //   各指回  ）。射程结论不变：字面量写在 enum 构造参、顶层 const 与
        //   `when` 分支上，四个锚点从来都看不见；逐颗搬还会破
        //   `CorrectionRecordRowSemanticsTest` 的逐字断言、生成半中半英标签。
        // 95 → **90**（W7 2026-10-06，四栏合计 90+4+0+42 = 136，比上一版 95+6+0+42 = 143 少 7 条）：
        //   本轮 L1b/M2c 把两颗页面上的自画件交回公共件（`LbListCard` / `LbFormField`+`LbFieldInput`），
        //   文案因此从 `Text(` 那一栏落进 `Lb…(` 那一栏——**换桶不是还债**，所以 COMPONENT 那一栏
        //   当场从 42 涨到 53、这一栏掉到 90，两栏一起看才是真相。这一栏的数字跟着实到走（降 5），
        //   那一栏的 11 条则**逐颗搬进资源**（zh + en 同拍，见下面 COMPONENT 那一段），
        //   搬完 COMPONENT 实测回到 42：棘轮一格没抬，还掉的是 TEXT 2 + DESC 2 + 净新增 4 条。
        // 90 → **85**（持续意图入口归一：`SettingsIntentEntry.kt` 新文件 + `IntentEditorSheet.kt` 重构）。
        //   两处所有 `Text("中文…")` 与 `Lb…("中文…")` 里的内联中文一次搬清（zh + en 同拍，16 个新 key）。
        //   **真还**：搬完这两个文件的 TEXT 读数都是 0，COMPONENT 也没跟着涨。摊开说：
        //   ① `SettingsIntentEntry.kt`（新文件）原来 5 条 TEXT（「意图」「有效期」+ 介绍浮层那段
        //      带转义引号的长文按 `HAN_LITERAL` 切成 3 条）→ 0；
        //   ② `IntentEditorSheet.kt` 原来有 5 条 TEXT（「启用」「有效期」「输入日期…」「标记为已完成」
        //      「输入你的持续意图…」），重构先删掉后 3 条（日期输入区与状态操作区整块移除），
        //      本轮再把剩下 2 条接上资源 → 0。两文件合计 TEXT −5（相对预算基线 90）。
        Kind.TEXT to 84,
        // 11 → **10**：面板引导卡片那颗关闭按钮的 `contentDescription` 原来是**内联中文**
        // 「关闭使用提示」，而 `a11y_close_onboarding` 中英两份资源**一直都在、从没被引用**过
        // ⇒ 英文环境下读屏念中文（面板整屏第一次量到 label=「关闭使用提示」，同屏其它按钮已是
        // "Collapse panel"）。接上资源是真的还了一处，不是换桶。
        // 10 → **9**：同一笔——`contentDescription = "关闭通知"` 接上 `a11y_close_notice`。
        // 9 → **6**（图标型动作归并进 `LbTextAction` 的图标档）：三处内联中文的
        //   `contentDescription` 接上 `res/values` + `res/values-en` 两份资源
        //   （新增 11 条 `a11y_*` key，中英逐条对齐：134 / 134）。这是**真还**：
        //   英文环境里读屏以前念的是中文，而屏幕上完全看不出来。
        // 6 → 实测 **7** → **6**：涨的那一颗是新页面带进来的内联中文——
        //   `ReplyInput.AdvisorNoteLine` 那行灰字挂的 `contentDescription = "编辑军师备注"`
        //   （屏幕上只有那行字，这一句是读屏唯一听得见的"点了会干什么"，英文环境念中文）。
        //   修法是**接上 `a11y_edit_advisor_note`（zh + en 各一份）而不是把预算抬到 7**：
        //   棘轮只许往下走，抬预算等于让闸认输。
        //   ⚠ 同屏那颗 `if (isEditing) "保存修改" else "添加"` 在本栏记 **2 条**（隔着条件括号
        //   的两支都算，见 `expressionAt` 那段），是既有欠账，本轮没动它。
        // 6 → **4**（W7 2026-10-06）：知识库母版页那两颗内联中文的 `contentDescription`
        //   （HEAD 上在第 436 / 471 行：「重命名」「删除知识库」，挂在页面自画的那两颗图标动作上）消失了——
        //   动作行整颗交回 `LbListCard`，名字改读资源（`a11y_action_edit` / `a11y_action_delete` /
        //   本轮新增的 `a11y_action_rename`，zh + en 两份都在盘上）。**真还**：搬完 `KnowledgeBaseActivity.kt`
        //   这一文件的 DESC 实到 0，而同批 COMPONENT 那一栏先被换成"组件实参里的内联中文"（17→22），
        //   再把那 11 颗逐颗搬进资源才回到 42——两栏一起降才算还债，见下面那一栏那一段。
        Kind.DESC to 4,
        Kind.STATE to 0,
        // 78 → **77**：`KbEditScreen` 那四条保存/冲突提示搬进资源。
        // ⚠ 这一栏上一格还涨过一次（78→81）：`LbPrimaryButton` 的锚点按括号配对取实参，
        //   整段 `onClick = { … }` 都进了射程，于是**一直存在、从没被数过**的三条内联中文
        //   当场被照出来。正确反应是把它们搬进资源，**不是把表填到 81**（坑表 95/98）。
        // 77 → **76**：首页入口卡片那颗 `LbActionCard(title = "反馈案例")` 接上页面自己那份
        //   `feedback_cases_title`（账本 第58节）——同一句话在"入口"和"页头"各写一遍，
        //   迟早漂；这一条是真的少了一条字面量。
        //   ⚠ 旁边那两条（「知识库」「她的专属记忆」）**没**跟着搬：它们目前只有这一份，
        //   没有第二处要合并；搬一半的目的是"消重复"，不是把这一栏的数字做小。
        // 76 → **80**（预测值，随 TEXT 那一笔一起写的）→ 实测 **87**：+11 而不是 +4。
        //   逐文件对 HEAD 复算，这 +11 分成两笔，性质完全不同，必须分开记：
        //   ① **+5 是换桶**：上面 TEXT 少的那五条落进来的（`ProviderSection` 两条标题进
        //     `LbDialog(title = …)`、`SuggestPanel` 两条进 `LbModalSheet(…)`/`LbDialogAction(…)`、
        //     `OngoingSection` 一条进 `LbSection(…)`）。字面量一个字没多，抽屉换了。
        //   ② **+6 是这把尺第一次看得见**：面板顶部那条使用统计原本写作
        //     `UsageStatCell("今日", …)` ——**页面自造的子组件不在这把尺的锚点里**
        //     （本栏 KDoc 早就承认的这一半个盲区：只认 `Lb` 前缀）。归并进 `LbMetric(label = …)`
        //     之后那六条（今日 / 本次 / 首字 / 累计 ×2，加 `"${次数}次"` 那条带"次"的数值串）
        //     才进了账。⇒ **这一栏涨 6 不是债涨 6，是账本终于记全了**；
        //     把它们搬进 `res/values` 之前，它们会一直算在这里。
        //   ③ `LbMetricGrid.kt` 里 −3、`HomeScreen.kt` 里 +3：同一批标签从**设计系统**退回**调用方**
        //     （那一版把首页的词表写死进了组件，等于让设计系统认识某一页——本轮归并顺手造出的
        //     新债，已删掉那条旧口；这一笔是净 0，只是债的主人换了）。
        //   ⚠ 新增字面量条数 = **0**：逐文件比对 HEAD 与工作区的中文串**集合**，
        //     全仓只多两条，且都在 `LbMetricGrid` 的 `require(…)` 里给开发者看，
        //     不在四个锚点的射程内。⇒ 这一栏从 76 涨到 87 的十一个字，
        //     **没有一个是新写的文案**，是搬家 + 补账。
        // 87 → **88**：上面那一句落进来的那一栏（TEXT −1 / COMPONENT +1，四栏合计不动）。
        //   新增用户可见字面量 **0 条**：本轮生产改动只有 `ProviderSection` 的一行 `heightIn(min = …)` 与两处所有者换人。
        // 88 → **89**：图标档那颗转进组件实参的一条标签（TEXT 少一处、这一栏多一处，合计没动
        //   ⇒ 换桶不是还债）。⚠ 两把尺同读数：本文件实扫 166/6/0/89，
        //   独立 Python 尺 `_temp/bucket_clone.py` 同一棵树也是 166/6/0/89。
        // 89 → **92**：第6节第1条 三处胶囊归并进 `LbChip`（`SuggestPanel` 的 IntentExpiryChip、
        //   `CounselingPanel` 的「清空重聊」与「继续追问」）——三处中文标签从页面自画
        //   落进 `LbChip(label = …)` 的组件实参，是**换桶不是还债**（字面量一个字没改，只是
        //   从 TEXT/自画 那栏落进 COMPONENT 那栏）。三处都是已有文案搬主人，零新增用户可见字面量。
        // 93 → **92**：把首页那格「累计花费」的标签换成 `stringResource(R.string.cost_stated_label)`
        //   （值那一半换成共用判据 `costReadout`，里面不再有中文）——**真还一条**，不是换桶。
        //   面板顶部那条只把标签的字面量从「累计」换成「已统计」（同栏同数，一条没还）：
        //   那一条小字整排都还是内联中文，属既有欠账，本轮不做全仓 i18n 清洗。
        // 92 → **81**：⚠ 同样不是我本轮动的（与上面 TEXT 那一格同时记）：
        //   `HomeScreen` −8、`RecordSentFlow`（整块删除）−3。
        // 81 → **67**：与 TEXT 那一格同一笔搬家，这一栏降 14 条，全是**真还**（换成
        //   `stringResource`，不是从 TEXT 抬进组件实参）：
        //   ① `UsageDetailScreen` −1、`AboutScreen` −1：两页的页名从 `LbTopBar(title = "…")`
        //     接上资源。关于页那一个词**没有新开 key**——首页入口那行与那颗箭头的读屏名字早就用
        //     `a11y_home_about`，页头再抄一份就是同一句话第三处（同一笔账见首页入口卡那次）。
        //   ② `FeedbackCasesScreen` −12：导出那一族三扇浮层（预览/导出失败/操作失败）的标题、
        //     动作词与「分享到」「没有可用的分享应用」。**三处「关闭」原来各写一遍**，
        //     现在走 `feedback_close` 一份。
        //   ③ 搬完这三个文件，四栏读数都是 0：这一屏已经没有内联的用户可见中文（注释里的不算，
        //     本文件那格 `maskComments` 的教训反过来同样成立）。
        //   ⚠ 这一栏从此看不见的那六条（CaseStatus 四词 + `saveError` 两句）本来就没记过账，
        //     所以这一栏的降幅里没有它们——别拿 −14 当"还清了三屏"的证据。
        // 67 → **50**：与上面 TEXT 那一格（125 → 101）同一笔账的另一半。
        //   两栏**一起降**（−17 / −24）才说明这是真还：换成 `stringResource` 的那些年月，
        //   这一栏涨过的那些格子（换桶）一次都没降下来过。
        // 50 → **42**：与上面 TEXT 那一格（101 → 95）同一笔账的另一半（等 ）。
        //   `AccessibilityDisclosureDialog` −3（标题进 `capture_disclosure_title`、"同意并继续"
        //   进 `capture_disclosure_agree`、"取消"复用既有 `a11y_action_cancel`）、
        //   `MemoryCorrectionFlow` −5（"暂停时长"/"标记为错误"进各自 sheet title 资源，
        //   两颗"取消"复用 `a11y_action_cancel`、"确认"复用 `a11y_action_confirm`）。
        //   ⚠ 撞车就复用既有短词 key，不另造第二份；两栏一起降 = 真还（TEXT −6 / COMPONENT −8）。
        // 42 → **53** → **42**（W7 2026-10-06，本轮唯一一次"这一栏涨过"，涨完当天就还回去）：
        //   涨的 11 颗全部来自本轮换骨架的两个文件，逐文件对 HEAD 复算（第二把尺与本文件同判据，
        //   两把尺在同一棵树上读数一致 90/4/0/53 才敢动这张表）：
        //   ① `KnowledgeBaseActivity.kt` +5：「当前使用」「阶段：X」「已对话 N 轮」（L1b 把 meta 从一个
        //     整句拆成两段）、「重命名」（原来挂在自画图标的 contentDescription 上，DESC 那一栏的 −1
        //     就是它）、「导出」（动作行归公共件后新落进 `LbListCardAction.secondary(…)`）。
        //   ② `ui/home/ProviderSection.kt` +6：M2c 把裸 `Text(label)` 上提成 `LbFormField.label =` /
        //     `LbFieldInput(placeholder =)`，于是「供应商名称」「接口地址（自动补全）」从 TEXT 栏
        //     落进这一栏（**换桶，不是还债**——同一批 TEXT 那栏 −3 就是证据），
        //     另有「名称」「留空保留原 Key」「模型名称」×2 是新落进来的四颗。
        //   还法：11 颗逐颗接 `stringResource`，中英同拍新增 10 个 key（「模型名称」两处共用一颗，
        //     所以 11 颗字面量对应 10 个 key：`kb_card_in_use` / `kb_card_stage` / `kb_card_turns` /
        //     `a11y_action_rename` / `a11y_action_export` / `provider_form_name_label` /
        //     `provider_form_name_placeholder` / `provider_form_base_url_label` /
        //     `provider_form_key_placeholder` / `provider_form_model_name_label`）。
        //   搬完实扫：COMPONENT **53 → 42**（与预算一字不差，**没有抬表**）、TEXT 90、DESC 4、STATE 0。
        //   ⚠ 这一栏买到的教训：换骨架的活干完必须当场把落进组件实参的中文接上资源，
        //     否则下一轮这把尺只剩"抬数字认输"一条路——而抬表是不许的。
        // 42 → **37**（持续意图入口归一，与 TEXT 那一格同一笔的另一半）。
        //   `IntentEditorSheet.kt` 在预算基线 42 时就已有 5 条 COMPONENT（`LbModalSheetTitle`
        //   那句带转义引号的长文按 `HAN_LITERAL` 切成 3 条 + `LbDialogAction("取消"/"保存")` 2 条
        //   去重后），本轮把它们与重构新增的 3 条（`LbFormField("意图内容")` / `LbFieldInput(placeholder)`
        //   / 超限 error 那条）一起接上资源 → 0。`SettingsIntentEntry.kt`（新文件）的 4 条
        //   COMPONENT（`LbFormField` / `LbFieldInput` / `LbModalSheetTitle("持续意图")` /
        //   `LbDialogAction("知道了")`）也一并搬进资源 → 0。**真还**：两栏一起降（TEXT −5 /
        //   COMPONENT −5），且两文件搬完 COMPONENT 读数都是 0。
        Kind.COMPONENT to 38
    )

    /**
     * 把注释**按字符换成空格**（保住偏移量，后面的区间去重还要用原坐标）。
     *
     * ⚠ 这一层是这一格补上的，起因很具体：我把 `Text("思考模式")` 从调用点搬进资源之后,
     * 又在 `MiniSwitchRow` 的 KDoc 里写了「原来这两样散在调用点：`Text("思考模式")` 画在左边」,
     * 于是 TEXT **一格没动**——尺把注释里那串当成了一处真文案，正好抵掉那笔真还债。
     * 四个锚点（`Text(`、`contentDescription =`、`stateDescription =`、`Lb…(`）
     * 全都按源码语法位置匹配，**注释里出现同样的形状就会命中**，
     * 而 KDoc 恰恰最容易写"原来这里长什么样"。
     * ⇒ 只要尺是按形状认的，注释就必须先剥；不剥的话，一次搬家可以被自己写的说明书抵消掉。
     */
    private fun maskComments(src: String): String {
        val out = src.toCharArray()
        var i = 0
        val n = out.size
        while (i < n) {
            val c = out[i]
            if (c == '"') {
                // 字符串字面量整体跳过（里面出现的 // 不是注释）
                i++
                while (i < n && out[i] != '"') {
                    if (out[i] == '\\') i++
                    i++
                }
                i++
                continue
            }
            if (c == '/' && i + 1 < n && out[i + 1] == '/') {
                while (i < n && out[i] != '\n') { out[i] = ' '; i++ }
                continue
            }
            if (c == '/' && i + 1 < n && out[i + 1] == '*') {
                var depth = 1
                out[i] = ' '; out[i + 1] = ' '
                i += 2
                while (i < n && depth > 0) {
                    if (i + 1 < n && out[i] == '/' && out[i + 1] == '*') {
                        depth++; out[i] = ' '; out[i + 1] = ' '; i += 2; continue
                    }
                    if (i + 1 < n && out[i] == '*' && out[i + 1] == '/') {
                        depth--; out[i] = ' '; out[i + 1] = ' '; i += 2; continue
                    }
                    if (out[i] != '\n') out[i] = ' '
                    i++
                }
                continue
            }
            i++
        }
        return String(out)
    }

    private fun countIn(root: File, kind: Kind): Int {
        if (!root.isDirectory) return -1
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sumOf { f -> countHanLiterals(maskComments(f.readText(Charsets.UTF_8)), kind) }
    }

    private fun mainRoot(): File {
        val root = File("src/main")
        assertTrue("必须在 :app 模块下跑，找不到 $root", root.isDirectory)
        return root
    }

    /** 扫描器接错目录时会"0 条 = 通过"，所以先证明它看得见东西 */
    @Test
    fun `the scanner actually finds the known literals`() {
        val found = countIn(mainRoot(), Kind.TEXT)
        assertTrue("扫到 $found 条 Text 中文字面量——路径或正则坏了", found > 50)
    }

    @Test
    fun `user visible string literals do not grow`() {
        val grew = Kind.values().mapNotNull { kind ->
            val now = countIn(mainRoot(), kind)
            val allowed = budget.getValue(kind)
            if (now > allowed) "${kind.label}: 实测 $now > 预算 $allowed" else null
        }
        assertTrue(
            "新增了用户可见的字面量。请放进 res/values/strings.xml" +
                "（本项目已收成单一中文资源，不再维护 values-en）：\n  " + grew.joinToString("\n  "),
            grew.isEmpty()
        )
    }

    @Test
    fun `the budget still reflects reality`() {
        val stale = Kind.values().mapNotNull { kind ->
            val now = countIn(mainRoot(), kind)
            val allowed = budget.getValue(kind)
            if (now < allowed) "${kind.label}: 实测 $now < 预算 $allowed（还掉了就来把数字改小）" else null
        }
        assertTrue(
            "预算允许虚高的话，它会慢慢烂回去。" +
                "本轮把 101 处搬掉了一些，就必须把这里的数字一起改小：\n  " + stale.joinToString("\n  "),
            stale.isEmpty()
        )
    }

    /**
     * 反向证明：临时目录里造两个文件，一个带中文 Text、一个只用字符串资源，
     * 要求前者被数到、后者为 0。没有这一格，正则是不是恒真没人知道。
     */
    @Test
    fun `the counters count what they claim and nothing else`() {
        val tmp = java.nio.file.Files.createTempDirectory("literal").toFile()
        try {
            File(tmp, "A.kt").writeText(
                """
                package x
                import androidx.compose.material3.Text
                @Composable fun A() { Text("你好"); Text(text = "再说一次") }
                """.trimIndent(), Charsets.UTF_8
            )
            File(tmp, "B.kt").writeText(
                """
                package x
                import androidx.compose.material3.Text
                @Composable fun B() {
                    Text(stringResource(R.string.panel_retry))
                    Image(contentDescription = stringResource(R.string.cd_close))
                }
                """.trimIndent(), Charsets.UTF_8
            )
            assertEquals("两个中文字面量都应被数到", 2, countIn(tmp, Kind.TEXT))
            assertEquals("走 stringResource 的不许被数进来", 0, countIn(tmp, Kind.DESC))

            // ⚠ 注释里出现锚点形状，**一条都不许数**。
            // 这一格是本轮（搬「思考模式」那次）补的：我把 `Text("思考模式")` 搬进资源,
            // 然后在 KDoc 里写"原来这里画的是 `Text("思考模式")`"——TEXT 一格没动，
            // 那笔真还债被我自己写的说明书抵消了。按形状认的尺必须先剥注释。
            File(tmp, "D.kt").writeText(
                """
                package x
                // 原来这里画的是 Text("注释里的中文")，现在搬进资源了
                @Composable fun D() {
                    /** 见 Text("第二处注释中文") 那条 */
                    Text(stringResource(R.string.panel_retry))
                }
                """.trimIndent(), Charsets.UTF_8
            )
            val onlyComments = java.nio.file.Files.createTempDirectory("literal_cmt").toFile()
            File(tmp, "D.kt").copyTo(File(onlyComments, "D.kt"), overwrite = true)
            assertEquals(
                "注释里的 Text(…) 形状被当成了用户可见文案 ⇒ 一次真还债可以被自己的 KDoc 抵消掉",
                0, countIn(onlyComments, Kind.TEXT)
            )
            // 同一份文本不剥注释时必须数到 2 —— 证明上面那条 0 是"剥掉了"，
            // 不是"锚点在这份输入上压根不响"（恒绿的另一种形状）。
            assertEquals(
                "锚点本身对这两串是响的：不剥注释时应数到 2",
                2, countHanLiterals(File(onlyComments, "D.kt").readText(Charsets.UTF_8), Kind.TEXT)
            )

            File(tmp, "C.kt").writeText(
                """
                package x
                val mod = Modifier.semantics { contentDescription = "关闭按钮" }
                """.trimIndent(), Charsets.UTF_8
            )
            assertEquals("contentDescription 里的中文也要被数到", 1, countIn(tmp, Kind.DESC))

            // 赋值型锚点 + 内层条件括号：第一版的切片在这里提前收尾，把两处中文漏成了 0。
            // 没这一格的话，"刚搬掉两处而预算一个没动"这种矛盾根本不会被发现。
            File(tmp, "E.kt").writeText(
                """
                package x
                val mod = Modifier.semantics { contentDescription = if (open) "收起" else "展开" }
                """.trimIndent(), Charsets.UTF_8
            )
            assertEquals(
                "隔着 if 条件括号的 contentDescription 也要数到（C 的 1 处 + E 的 2 处）",
                3, countIn(tmp, Kind.DESC)
            )

            // 这一格是给"尺子换过"这件事兜底的：锚点与字面量之间隔着一层带括号的判断条件，
            // 旧那条只看紧跟引号的正则在这里是瞎的——真出过事（见 expressionAt 的注释）。
            File(tmp, "D.kt").writeText(
                """
                package x
                import androidx.compose.material3.Text
                @Composable fun D() {
                    Text(
                        text = if (expanded) "已经展开" else "点击展开",
                        color = Color.Blue
                    )
                }
                """.trimIndent(), Charsets.UTF_8
            )
            assertEquals(
                "隔着一层 if 的两处中文也必须数到（A 里 2 处 + D 里 2 处）",
                4, countIn(tmp, Kind.TEXT)
            )

            // COMPONENT 这一栏是新加的，两头都要有反例：
            //  ① 具名实参里的中文必须看见（搬家那次就是从这里漏出去的）；
            //  ② 同一颗组件的 content 槽里套着 Text 时，那两处只能记一次（记 TEXT 名下）。
            File(tmp, "F.kt").writeText(
                """
                package x
                import androidx.compose.material3.Text
                @Composable fun F() {
                    LbTopBar(
                        title = "页面标题",
                        trailing = { Text("里面" + "那颗字") }
                    )
                }
                """.trimIndent(), Charsets.UTF_8
            )
            assertEquals(
                "组件具名实参里的中文要入账，套在里面的 Text 不许重复记",
                1, countIn(tmp, Kind.COMPONENT)
            )
            assertEquals(
                "F 里 Text 的两处仍归 TEXT 栏（4 + 2）",
                6, countIn(tmp, Kind.TEXT)
            )

            // 恒真的另一种形状：如果锚点其实是 `Text(`，那 F 的 COMPONENT 会报 0——
            // 上面那格因此同时是"锚点写错成什么样"的探测器。
            File(tmp, "G.kt").writeText(
                """
                package x
                @Composable fun G() { OtherCell(label = "别人的组件不算") }
                """.trimIndent(), Charsets.UTF_8
            )
            assertEquals(
                "非 Lb 前缀的组件仍在盲区里：这一格写明它「不数」，而不是假装数到了",
                1, countIn(tmp, Kind.COMPONENT)
            )

            // 去重这一支必须有牙：`LbDialog(title = "外层标题", confirm = LbDialogAction("内层按钮"))`
            // 里，外层实参切片同时含两条串、内层切片又含 "内层按钮" 一次。
            // 按锚点求和 ⇒ 这里会数到 3（合计 4）；按字符区间去重 ⇒ 数到 2（合计 3）。
            File(tmp, "H.kt").writeText(
                """
                package x
                @Composable fun H() {
                    LbDialog(
                        title = "外层标题",
                        confirm = LbDialogAction(label = "内层按钮", onClick = {})
                    )
                }
                """.trimIndent(), Charsets.UTF_8
            )
            assertEquals(
                "嵌套的两层 Lb 实参不许把同一条字符串记两遍（F 1 条 + H 2 条）",
                3, countIn(tmp, Kind.COMPONENT)
            )
        } finally {
            tmp.deleteRecursively()
        }
    }
}
