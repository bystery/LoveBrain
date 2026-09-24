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
     */
    fun generatingBarPattern(tag: String? = null): Regex {
        val template = resolve(tag, R.string.panel_analysing_with_seconds)
        val phases = listOf(
            R.string.panel_phase_analysing,
            R.string.panel_phase_drafting,
            R.string.panel_phase_deep_analysing
        ).joinToString("|") { Regex.escape(resolve(tag, it)) }
        return Regex(
            Regex.escape(template)
                .replace(Regex.escape("%1\$s"), phases)
                .replace(Regex.escape("%2\$d"), """\d+""")
        )
    }

    /**
     * 停止棒文案里可当"恒定锚点"的字面片段（模板最后一个占位符之后的部分）。
     *
     * 只为兼容一处旧写法：Compose 1.6.8 的 ui-test 没有正则版 finder，得先用一段固定
     * 文字把节点捞出来、再对整串做正则校验。新代码请直接用 GENERATE_STOP_TEST_TAG 定位。
     */
    fun generatingBarAnchor(): String {
        val template = current(R.string.panel_analysing_with_seconds)
        val tail = template.substringAfterLast("%2\$d")
            .ifBlank { template.substringBefore("%1\$s") }
        if (tail.isBlank()) {
            throw AssertionError("模板「$template」里找不到任何可定位的字面片段")
        }
        return tail
    }
}
