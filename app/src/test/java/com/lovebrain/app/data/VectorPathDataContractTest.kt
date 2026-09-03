package com.lovebrain.app.data

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * pathData 契约单测（2026-09-03 图标 pathData 截断事故回归防线）：
 * res 下各 drawable 目录（drawable、drawable-v24 等限定符目录）的 XML 中，每条
 * android:pathData 必须语法完整——以 M/m 移动命令开头、命令参数个数符合 SVG path 语法、
 * 无非法字符残留。
 *
 * 事故背景：14 个矢量图标共 17 条 pathData 曾整体丢失开头 M 命令；
 * AAPT2 不校验 pathData 语法，构建绿灯但设备端渲染失败——本测试补上这道编译期防线。
 *
 * 取数方式：直接读源目录 src/main/res（Gradle 单测工作目录=模块目录，
 * 另备仓库根/上级目录两个候选），与 VectorLabelContractTest「资产从源头取、禁副本」原则一致。
 * 校验逻辑与 .workbuddy/tmp/validate_and_render.py 的 validate_path 同源。
 *
 * 注：KDoc 内禁写 drawable 加星加斜杠式通配符——「星杠」会被当作块注释终止符截断注释。
 */
class VectorPathDataContractTest {

    private companion object {
        /** 每个命令要求的参数个数（Z/z 为 0；隐式重复命令按倍数校验） */
        val REQUIRED_ARGS: Map<Char, Int> = mapOf(
            'M' to 2, 'm' to 2, 'L' to 2, 'l' to 2,
            'H' to 1, 'h' to 1, 'V' to 1, 'v' to 1,
            'C' to 6, 'c' to 6, 'S' to 4, 's' to 4,
            'Q' to 4, 'q' to 4, 'T' to 2, 't' to 2,
            'A' to 7, 'a' to 7, 'Z' to 0, 'z' to 0
        )

        /** 命令字母或数字（含小数/负号/科学计数法） */
        val TOKEN = Regex("[MmLlHhVvCcSsQqTtAaZz]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")

        /** pathData 合法分隔符：空白与逗号 */
        val SEPARATORS = charArrayOf(' ', '\t', '\r', '\n', ',')
    }

    /** 主契约：res 下各 drawable 目录的 XML，每条 pathData 语法完整 */
    @Test
    fun `every drawable pathData is syntactically complete`() {
        val resDir = locateResDir()
        val xmlFiles = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("drawable") }
            .orEmpty()
            .flatMap { dir -> dir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }.orEmpty().toList() }
        assertTrue(
            "res/ 下未找到 drawable 目录——user.dir=${System.getProperty("user.dir")}",
            xmlFiles.isNotEmpty()
        )

        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val violations = mutableListOf<String>()
        var checked = 0
        for (file in xmlFiles) {
            val doc = try {
                factory.newDocumentBuilder().parse(file)
            } catch (e: Exception) {
                violations += "${file.name}: XML 解析失败（${e.message}）"
                continue
            }
            val nodes = doc.getElementsByTagName("*")
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                val pd = el.getAttributeNS("http://schemas.android.com/apk/res/android", "pathData")
                if (pd.isNullOrBlank()) continue
                checked++
                validate(pd)?.let { reason -> violations += "${file.name}: $reason" }
            }
        }
        assertTrue("未扫到任何 pathData——drawable 结构异常？", checked > 0)
        assertTrue(
            "pathData 契约被破坏（${violations.size} 条）：\n  " + violations.joinToString("\n  "),
            violations.isEmpty()
        )
    }

    /** 校验器自检：历史损坏样本必须被拦截，已知正常样本必须通过 */
    @Test
    fun `validator rejects historical corruption and accepts known good paths`() {
        // 坑#1 实锤损坏形态：开头 M 命令丢失（ic_close 原损坏串）
        assertInvalid(",6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 19,12z")
        // 参数个数不足（L 需 2 的倍数）
        assertInvalid("M1,2 L3")
        // 非法字符残留（X 不是合法命令）
        assertInvalid("M1,2X3,4")
        // 已知正常样本：ic_model_dropdown（重建版）与 ic_close / Material 圆环原始串
        assertValid("M7,10l5,5 5,-5z")
        assertValid("M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 19,12z")
        assertValid("M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z")
    }

    // ---------- 内部实现 ----------

    /** 返回违规原因；null = 通过 */
    private fun validate(pathData: String): String? {
        val s = pathData.trim()
        if (s.isEmpty()) return "pathData 为空"
        if (s[0] !in "Mm") return "不以 M/m 开头（开头片段=${s.take(20)}）"
        val leftover = TOKEN.replace(s, "").filter { it !in SEPARATORS }
        if (leftover.isNotEmpty()) return "含非法字符「$leftover」"
        val tokens = TOKEN.findAll(s).map { it.value }.toList()
        var i = 0
        while (i < tokens.size) {
            val cmd = tokens[i][0]
            val need = REQUIRED_ARGS[cmd] ?: return "未知命令「$cmd」"
            i++
            if (need == 0) continue
            var count = 0
            while (i < tokens.size && tokens[i][0] !in REQUIRED_ARGS) {
                count++
                i++
            }
            if (count == 0 || count % need != 0) return "命令 $cmd 的参数数 $count 不是 $need 的正整数倍"
        }
        return null
    }

    private fun assertValid(pd: String) {
        val reason = validate(pd)
        assertTrue("样本应通过校验却被拒：$pd → $reason", reason == null)
    }

    private fun assertInvalid(pd: String) {
        assertTrue("样本应被拦截却通过：$pd", validate(pd) != null)
    }

    private fun locateResDir(): File = sequenceOf(
        File("src/main/res"),         // Gradle 单测默认工作目录 = 模块目录
        File("app/src/main/res"),     // 工作目录 = 仓库根
        File("../app/src/main/res")   // 工作目录位于模块子目录的情形
    ).firstOrNull { it.isDirectory }
        ?: error("定位不到 app/src/main/res——user.dir=${System.getProperty("user.dir")}")
}
