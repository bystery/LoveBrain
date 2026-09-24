package com.lovebrain.app.ui.panel

import com.lovebrain.app.R
import com.lovebrain.app.testing.UiText
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * 设备侧的中英文那一格（§6.5 第 ⑥ 栏）：面板主路径上每个要念给用户听的文案，
 * 中文与英文两边都必须真的有自己的说法。
 *
 * 为什么单独立一格，而不是"当前语言下能取到就行"：Android 取不到当前语言的翻译时
 * **不报错**，它回落到默认的 `values/strings.xml`（这个项目里那份是中文）。
 * 于是在英文设备上，"英文没翻"这件事长得和"英文翻好了"一模一样——只断言当前语言
 * 永远看不见。CI run 36019334520 那 12 格红就是因为生产已经进了资源、测试还在等
 * 一句写死的中文；这一格盯的是反方向的那半同一个坑：生产自己哪天把英文丢了。
 *
 * 这一格不启动 Activity，只查资源：跑得起、失败时说得清是哪条文案在哪个语言塌了。
 * 真机渲染出来是不是这句，由各页那些走资源定位的用例负责。
 */
@RunWith(JUnit4::class)
class PanelUiTextLocaleParityTest {

    /** (资源, 至少带几个格式化参数) —— 两种语言下都要用同样的参数去解析 */
    private val underWatch = listOf(
        R.string.panel_generate_reply to 0,
        R.string.panel_generate_reply_with_count to 1,
        R.string.panel_generate_opening to 0,
        R.string.panel_retry to 0,
        R.string.panel_save_to_kb to 0,
        R.string.panel_stop to 0,
        R.string.panel_collapse to 0,
        R.string.panel_mode_reply to 0,
        R.string.panel_mode_suggest to 0,
        R.string.panel_mode_counseling to 0,
        R.string.panel_mode_proactive to 0,
        R.string.proactive_empty_send_one to 0,
        R.string.proactive_empty_turn_off to 0,
        R.string.panel_phase_analysing to 0,
        R.string.panel_phase_drafting to 0,
        R.string.panel_phase_deep_analysing to 0
    )

    private fun args(n: Int): Array<Any> = Array(n) { 3 }

    @Test
    fun everyWatchedLabelResolvesToItsOwnWordsInBothLanguages() {
        val silentFallbacks = mutableListOf<String>()
        for ((id, argc) in underWatch) {
            val zh = UiText.inTag("zh", id, *args(argc))
            val en = UiText.inTag("en", id, *args(argc))
            assertTrue("资源 id=$id 在某一边解析成空白", zh.isNotBlank() && en.isNotBlank())
            if (zh == en) {
                silentFallbacks += "id=$id 两边都是「$zh」"
            }
        }
        assertTrue(
            "这些文案在中文与英文下解析出同一个字符串——多半是英文那侧没有词条，" +
                "系统静默回落到了默认的中文文件（这种回落只断言当前语言是看不见的）：\n" +
                silentFallbacks.joinToString("\n"),
            silentFallbacks.isEmpty()
        )
    }

    /**
     * 生成中那条停止棒的模板必须真的能被拼成一条整串匹配的正则。
     *
     * 它是设备侧唯一"文字会随秒数变"的断言锚点；模板里的占位符一旦被人挪走，
     * 拼出来的模式会配不上任何文案，那三格就又变回"永远红"或者"永远绿"。
     */
    @Test
    fun theGeneratingBarTemplateStillBuildsAMatchingPattern() {
        for (tag in listOf("zh", "en")) {
            // 阶段词也取那个语言下的真实资源：随便编一个词配不上模式，只能说明我拼错了
            val phase = UiText.inTag(tag, R.string.panel_phase_analysing)
            val rendered = UiText.inTag(tag, R.string.panel_analysing_with_seconds, phase, 7)
            val pattern = UiText.generatingBarPattern(tag)
            assertTrue(
                "模板 $tag 下渲染出「$rendered」，配不上自己拼出来的模式 $pattern",
                pattern.matches(rendered)
            )
        }
    }
}
