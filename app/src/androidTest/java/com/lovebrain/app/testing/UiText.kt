package com.lovebrain.app.testing

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R

/**
 * 设备上"用户实际看到的文案"该从哪儿取。
 *
 * CI run 36019334520 的 ui-test 里有 12 格的失败只有一句话：
 * `断言「已显示」失败 —— 节点不存在（fetchSemanticsNode 失败）`。
 * 不是面板没渲染：同一文件里只用 `hasClickAction()` 定位、不认文字的那格是绿的。
 * 真正的原因是**测试把中文文案写死在自己代码里，而生产早就改成读资源**了——
 * 模拟器是英文环境，生产渲染 "Generate reply"，测试还在找 "生成回复"，
 * 于是永远找不到节点。
 *
 * 所以取文案只有一个合法来源：让生产那套资源在当前设备配置下自己解析。
 * 这不是把断言放宽——它反而更严：资源缺这个 key、或某个语言没翻译静默回落到
 * 默认（中文）那份，这两种情况以前一路静默绿，现在会红。
 */
object UiText {

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    /** 当前设备配置下生产会渲染出来的那句文案（带参数的格式串也走这里）。
     *
     * 无参时必须走 getString(id)：带空 vararg 的那条重载会对文本执行
     * String.format，而 "生成回复 · %1$d条消息" 这种模板没有实参就会当场
     * MissingFormatArgumentException——这个只能在本机编译不到的地方炸，所以写在这。
     */
    fun current(id: Int, vararg args: Any): String = resolve(null, id, *args)

    /**
     * 指定语言标签下的文案。
     *
     * 用来盯"某个语言没翻，于是静默回落到默认（中文）那份"：只看当前配置是看不出来的，
     * 英文设备上一回落就变成中文，测试还照样绿。
     */
    fun inTag(tag: String, id: Int, vararg args: Any): String = resolve(tag, id, *args)

    /** tag 为 null 就按设备当前配置解析；否则临时套上那个语言再取 */
    private fun resolve(tag: String?, id: Int, vararg args: Any): String {
        val res = if (tag == null) {
            app.resources
        } else {
            val cfg = Configuration(app.resources.configuration)
            cfg.setLocales(LocaleList.forLanguageTags(tag))
            app.createConfigurationContext(cfg).resources
        }
        val raw = if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
        if (raw.isBlank()) {
            throw AssertionError(
                "资源 id=$id${tag?.let { "（语言 $it）" } ?: ""} 解析成空白——节点不可能有这句话"
            )
        }
        return raw
    }

    /**
     * 生成中那条停止棒的**整串**匹配模式。
     *
     * 生产把它拼成 `panel_analysing_with_seconds(阶段词, 秒数)`，阶段词又随秒数在
     * 三个资源之间换。所以模式也从资源现拼：模板里的 `%1$s` 换成三个阶段词之一、
     * `%2$d` 换成任意秒数，其余字符按字面量转义。这样英文环境能匹配
     * "Reading the conversation · 3s, tap to stop"，中文环境能匹配 "分析对话 · 3 点击停止"，
     * 而模板被人改动时它会当场红。
     *
     * ⚠ **原来那一版是坏的**，而且是 CI run 36199686779 三条红的同一个根因
     * （`PanelUiTextLocaleParityTest.theGeneratingBarTemplateStillBuildsAMatchingPattern`、
     * `ReplyPrimaryActionsTest.generating_showsProductionLoadingStopAffordanceAndCallsOnStopOnce`、
     * `OverlayGenerateSmokeTest.stopDuringGeneration_cancelsCurrentRequestAndReturnsToIdle`）。
     * 原来的写法是 `Regex.escape(template).replace(Regex.escape("%1\\$s"), phases)`：
     * `String.replace` 是**字面量**替换，而 `Regex.escape("%1\\$s")` 交回去的是
     * `\Q%1$s\E` 这 8 个字符——转义后的模板里根本没有这串东西 ⇒ 替换从未发生，
     * 发出去的是一条把 `%1$s` 当字面量去匹配的 regex，**永远配不上任何真实文案**。
     * 一句话教训：**拿 `Regex.escape` 的产物去找占位符，就是把正则当字符串用了**；
     * 而它唯一的线索是那句"配不上自己拼出来的模式"。
     *
     * 现在按段拼：三段字面量（占位符之前、两个占位符之间、之后）**各自只转义一次**，
     * 中间插入要展开的那两截。占位符找不到就当场抛——不许退化成"静默失配"。
     */
    fun generatingBarPattern(tag: String? = null): Regex {
        val template = resolve(tag, R.string.panel_analysing_with_seconds)
        val phases = listOf(
            R.string.panel_phase_analysing,
            R.string.panel_phase_drafting,
            R.string.panel_phase_deep_analysing
        ).joinToString("|") { Regex.escape(resolve(tag, it)) }

        val i1 = template.indexOf(PHASE_TOKEN)
        val i2 = template.indexOf(SECONDS_TOKEN)
        check(i1 >= 0 && i2 > i1) {
            "模板 `$template`（语言 ${tag ?: "当前"}）里找不到有序的两个占位符 " +
                "$PHASE_TOKEN / $SECONDS_TOKEN —— 停止棒的文案形状被改过了，" +
                "这条整串匹配不能再凭旧假设拼"
        }
        val pre = Regex.escape(template.substring(0, i1))
        val mid = Regex.escape(template.substring(i1 + PHASE_TOKEN.length, i2))
        val post = Regex.escape(template.substring(i2 + SECONDS_TOKEN.length))
        return Regex("$pre($phases)$mid\\d+$post")
    }

    /** 资源里的位置参数写法（`$` 在 Kotlin 字符串里必须转义，值本身就是 `%1$s`） */
    private const val PHASE_TOKEN = "%1\$s"
    private const val SECONDS_TOKEN = "%2\$d"
}
