package com.lovebrain.app.ui.kb

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 建库向导从 `ui/KnowledgeBaseActivity.kt` 搬进 `ui/kb/` 之后的**归位合同**（工单 W-D）。
 *
 * ⚠ 这一族读的是**结构**，不是行为：它证明"这次搬家没被搬坏、也没有被搬回去"，
 * 证明不了"这一屏长得对"——后者归 `KbOnboardingWizardSemanticsTest` 那棵语义树判。
 * 之所以还值得单写一格，理由与 `ui/panel/reply/ResultAreaOwnershipTest` 同一句：
 * 搬家最常见的坏法不是编译不过（那当场就红），而是几周后有人把向导重新内联回 Activity，
 * 于是同一个变化理由再次住进两个文件，而语义树那几格照样全绿。
 *
 * 每条判据都配一条反向证人（"这把尺真的看得见东西"），免得正则写坏了恒绿。
 */
class KbOnboardingWizardOwnershipTest {

    private val kbDir: File by lazy {
        File("src/main/java/com/lovebrain/app/ui/kb").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app/ui/kb")
    }

    private val pageFile: File by lazy {
        File("src/main/java/com/lovebrain/app/ui/KnowledgeBaseActivity.kt").takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/ui/KnowledgeBaseActivity.kt")
    }

    private fun kotlinFiles(dir: File): List<File> =
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().also {
            assertTrue("扫到 0 个 .kt——路径接错了，这一格会恒绿：$dir", it.isNotEmpty())
        }

    /** 剥注释再数：这些文件的 KDoc 里写"以前这里是……"是常态，不能当成又长了一份 */
    private fun codeOf(f: File): String = SourceScan.maskComments(f.readText(Charsets.UTF_8))

    private fun declCount(code: String, name: String): Int =
        Regex("\\bfun\\s+$name\\s*\\(").findAll(code).count()

    @Test
    fun `the wizard is declared exactly once and its home is ui-kb`() {
        val found = kotlinFiles(kbDir)
            .map { it.name to declCount(codeOf(it), "OnboardingScreen") }
            .filter { it.second > 0 }
        assertEquals(
            "建库向导应当只声明一处、且在 ui/kb/KbOnboardingWizard.kt，实到 $found",
            listOf("KbOnboardingWizard.kt" to 1), found
        )
        // 反向证人：页面那份确实还在**调用**它（不是整块被删空造成的"页面里没有声明"）
        val page = codeOf(pageFile)
        assertTrue("KnowledgeBaseActivity.kt 不再调用向导了——搬家搬成了断线", page.contains("OnboardingScreen("))
        assertEquals(
            "页面里不许再声明第二颗向导", 0, declCount(page, "OnboardingScreen")
        )
    }

    @Test
    fun `the wizard keeps its own state and the page keeps none of it`() {
        val wizard = codeOf(File(kbDir, "KbOnboardingWizard.kt"))
        val page = codeOf(pageFile)
        // 这些是向导的局部状态与它自己的接线，搬家时必须整族一起走
        val tells = listOf("currentStep", "showCustomInput", "maxSelectionToast", "OnboardingSchemaBuilder")
        val leftBehind = tells.filter { page.contains(it) }
        assertTrue("向导的局部状态还残留在页面里：$leftBehind", leftBehind.isEmpty())
        // 反向证人：这一族在向导那一份里全都还在
        val missing = tells.filter { !wizard.contains(it) }
        assertTrue("向导那一份里丢了这几样（尺在动真代码之前先自己红一下）：$missing", missing.isEmpty())
    }

    @Test
    fun `the kb dimens table has one home and still points at the global floor`() {
        val uiRoot: File = File("src/main/java/com/lovebrain/app/ui").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app/ui")
        val declarations = kotlinFiles(uiRoot).flatMap { f ->
            Regex("\\bPRIMARY_ACTION_HEIGHT_DP\\s*=[^=\\n]*")
                .findAll(codeOf(f))
                .map { f.name to it.value.trim() }
        }.toList()
        assertEquals(
            "知识库那一族的大按钮高度只许有一处定义，且必须指回全局下限而不是自己抄一个 48，实到 $declarations",
            listOf("KbDimens.kt" to "PRIMARY_ACTION_HEIGHT_DP = AppDimens.TOUCH_TARGET_MIN_DP"),
            declarations
        )
        // 反向证人：两家都还在读它（页面那颗「导入知识库」+ 向导进度条）
        assertTrue(
            "页面必须还在读 KbDimens（等号是删引用删出来的假绿）",
            codeOf(pageFile).contains("KbDimens.PRIMARY_ACTION_HEIGHT_DP")
        )
        // 向导那三颗大按钮归 `LbPrimaryButton` 之后，向导读 KbDimens 的那一处是进度条
        // （`PROGRESS_BAR_HEIGHT_DP`）——只要它还在读这一族尺寸表，就证明它没被搬回 Activity。
        assertTrue(
            "向导必须还在读 KbDimens",
            codeOf(File(kbDir, "KbOnboardingWizard.kt")).contains("KbDimens.PROGRESS_BAR_HEIGHT_DP")
        )
    }
}
