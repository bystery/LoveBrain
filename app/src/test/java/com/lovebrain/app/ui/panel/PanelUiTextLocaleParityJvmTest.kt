package com.lovebrain.app.ui.panel

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.lovebrain.app.R
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 面板主路径上每一条要念给用户的文案，中文与英文两边都必须有自己的说法（§6.5 第 ⑥ 栏）。
 *
 * 盯的是这种失败：`values-en` 里少了某个 key，Android **不报错**，直接回落到默认的
 * `values/strings.xml`（这个项目里那份是中文）。于是在英文环境里"英文没翻"和
 * "英文翻好了"长得一模一样，只断言当前语言永远看不见。
 * CI run 36019334520 红掉的 12 格是同一个坑的反方向：生产早就进了资源，测试还在等
 * 一句写死的中文。这一格把另一头也钉住。
 *
 * 与 app/src/androidTest/…/PanelUiTextLocaleParityTest.kt 是同一判据的两份：
 * 这一份本机就能跑、能被证伪（把 values-en 里任意一条删掉它必须红）；那一份在设备上跑，
 * 验的是真机的资源回落行为与这里一致。两份的键表要一起改。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = UiProbeApplication::class)
class PanelUiTextLocaleParityJvmTest {

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    /** (资源, 格式化参数个数) —— 与设备侧那份同一张表 */
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

    private fun resFor(tag: String): Resources {
        val cfg = Configuration(app.resources.configuration)
        cfg.setLocales(LocaleList.forLanguageTags(tag))
        return app.createConfigurationContext(cfg).resources
    }

    private fun text(tag: String, id: Int, vararg args: Any): String =
        if (args.isEmpty()) resFor(tag).getString(id) else resFor(tag).getString(id, *args)

    private fun hasHan(s: String): Boolean = s.any { it in '一'..'鿿' }

    /** 同一条文案在中英两边解析出同一个字符串 = 英文那侧根本没有词条 */
    @Test
    fun everyWatchedLabelHasItsOwnWordsInBothLanguages() {
        val silent = mutableListOf<String>()
        for ((id, argc) in underWatch) {
            val a = Array(argc) { 3 }
            val zh = text("zh", id, *a)
            val en = text("en", id, *a)
            assertTrue(
                "资源 id=$id 有一边解析成空白：zh=「$zh」 en=「$en」",
                zh.isNotBlank() && en.isNotBlank()
            )
            if (zh == en) silent += "id=$id 两边都是「$zh」"
        }
        assertTrue(
            "这些文案在中英两边解析出同一个字符串，说明英文那侧没有词条、系统静默回落到了" +
                "默认的中文文件（只断言当前语言永远看不见这种回落）：\n" + silent.joinToString("\n"),
            silent.isEmpty()
        )
    }

    /** 英文解析结果里不该还有汉字——这是"回落"最直接的形状 */
    @Test
    fun noWatchedLabelFallsBackToChineseUnderEnglish() {
        val stillChinese = underWatch.mapNotNull { (id, argc) ->
            val a = Array(argc) { 3 }
            val en = text("en", id, *a)
            if (hasHan(en)) "id=$id 在英文下是「$en」" else null
        }
        assertTrue("英文环境下这些文案仍是中文：\n" + stillChinese.joinToString("\n"), stillChinese.isEmpty())
    }

    /**
     * 生成中那条停止棒是**拼**出来的（模板 + 阶段词），所以它有两种各自塌法：
     * 模板回落了、或者阶段词回落了，拼出来都会半中半英。这里两头都查。
     */
    @Test
    fun theGeneratingBarComposesIntoAWholeSentenceInEachLanguage() {
        for (tag in listOf("zh", "en")) {
            val phase = text(tag, R.string.panel_phase_analysing)
            val bar = text(tag, R.string.panel_analysing_with_seconds, phase, 7)
            assertTrue(
                "$tag 下拼出的停止棒文案里不含那个语言的阶段词：「$bar」（阶段词=「$phase」）",
                bar.contains(phase)
            )
            assertTrue(
                "$tag 下拼出的文案里没有秒数：「$bar」——模板里的 %2\$d 大概被挪走了",
                bar.contains("7")
            )
        }
        val zhBar = text("zh", R.string.panel_analysing_with_seconds,
            text("zh", R.string.panel_phase_analysing), 7)
        val enBar = text("en", R.string.panel_analysing_with_seconds,
            text("en", R.string.panel_phase_analysing), 7)
        assertTrue("中英拼出同一条停止棒文案（「$zhBar」）= 模板或阶段词回落了", zhBar != enBar)
        assertTrue("英文那条停止棒文案里仍有汉字：「$enBar」", !hasHan(enBar))
    }
}
