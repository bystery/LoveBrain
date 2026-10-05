package com.lovebrain.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * CAP2（2026-10-06，档位 D）· `res/xml/accessibility_config.xml` 的送达面形状尺。
 *
 * 钉三件事，逐格标"回退成什么就红"：
 *
 * 1. `accessibilityFlags` 必须含 `flagIncludeNotImportantViews`（候选②：微信类
 *    `importantForAccessibility=no` 的气泡长按事件此前可能在框架层就被过滤）。
 *    → 把 XML 改回 `accessibilityFlags="flagDefault"` 就红。
 * 2. 事件类型仍是"长按 + 窗口状态"两枚，**没有**新增 `typeWindowContentChanged`
 *    （明确不做 C：持续读屏）。→ 谁手滑加了 CONTENT_CHANGED 就红。
 * 3. XML 里**不许写死 `android:packageNames`**（包名一律来自代码层唯一真源
 *    `SecurePrefs.captureAllowedPackages`）。→ 有人把微信/QQ 样例塞进 XML 就红。
 *
 * 判"有 flag / 没有 CONTENT_CHANGED"用的是**属性值切片**，且**先剥 XML 注释再数**——
 * 文件头注释里明写了 `flagIncludeNotImportantViews` 这颗词，若不剥注释、直接对全文
 * `contains` 判据就自证为空转（注释命中 ≠ 真配置命中）。反向证人（合成串）先证明这把尺
 * 在"注释里写了但属性没配"上数得到 0，才允许它去判真文件。
 */
class CaptureAccessibilityConfigShapeTest {

    /** 剥掉 XML 注释 `<!-- ... -->`（本仓库的 SourceScan.maskComments 只认 Kotlin 注释，不认这一族）。 */
    private fun stripXmlComments(xml: String): String =
        Regex("<!--[\\s\\S]*?-->").replace(xml) { " " }

    /** 取某个 `android:xxx="..."` 属性值；不存在返回 null。已假定注释被剥掉。 */
    private fun attr(masked: String, name: String): String? =
        Regex("""android:$name="([^"]*)"""").find(masked)?.groupValues?.get(1)

    /** 一把 flag：把 `accessibilityFlags` 的属性值按 `|` 拆成 flag 名集合。 */
    private fun flags(maskedXml: String): Set<String> =
        attr(maskedXml, "accessibilityFlags").orEmpty()
            .split('|').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    // ═══════════ 反向证人：先证明这把尺数得到 0/1，才让它碰真文件 ═══════════

    @Test
    fun `the flag judge returns 0 on a synthetic config that omits the flag`() {
        val synthetic = """
            <?xml version="1.0" encoding="utf-8"?>
            <accessibility-service
                android:accessibilityEventTypes="typeViewLongClicked|typeWindowStateChanged"
                android:accessibilityFlags="flagDefault" />
        """.trimIndent()
        assertFalse(
            "尺没牙：合成串里根本没有 flagIncludeNotImportantViews，却被判成有",
            flags(stripXmlComments(synthetic)).contains("flagIncludeNotImportantViews")
        )
    }

    @Test
    fun `the flag judge returns 1 on a synthetic config that sets the flag`() {
        val synthetic = """
            <accessibility-service android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews" />
        """.trimIndent()
        val f = flags(stripXmlComments(synthetic))
        assertTrue("尺没牙：合成串明明配了 flag 却数不到", f.contains("flagIncludeNotImportantViews"))
        assertEquals("两枚 flag 都该在: $f", setOf("flagDefault", "flagIncludeNotImportantViews"), f)
    }

    @Test
    fun `the flag judge ignores a flag mentioned only inside an xml comment`() {
        // 反向证人第三格：把那颗词只写进注释、属性仍是 flagDefault ⇒ 尺必须仍判"没有"。
        // 这一格专治"直接对全文 contains 判据"的自证空转——文件头注释恰恰提了这颗词。
        val decoy = """
            <!-- 审计说明: 恢复 flagIncludeNotImportantViews，由包名收窄承担 -->
            <accessibility-service android:accessibilityFlags="flagDefault" />
        """.trimIndent()
        assertFalse(
            "尺被注释里的字样骗了——必须剥注释后只看属性值",
            flags(stripXmlComments(decoy)).contains("flagIncludeNotImportantViews")
        )
    }

    // ═══════════ 真文件三格 ═══════════

    private fun realConfigMasked(): String {
        val candidates = listOf(
            File("src/main/res/xml/accessibility_config.xml"),
            File("app/src/main/res/xml/accessibility_config.xml"),
            File("../app/src/main/res/xml/accessibility_config.xml")
        )
        val f = candidates.firstOrNull { it.isFile }
        if (f == null) {
            fail("定位不到 accessibility_config.xml——user.dir=${System.getProperty("user.dir")}，候选=$candidates")
        }
        assertTrue("accessibility_config.xml 不是文件: $f", f!!.isFile)
        return stripXmlComments(f.readText(Charsets.UTF_8))
    }

    @Test
    fun `real config restores flagIncludeNotImportantViews alongside flagDefault`() {
        val f = flags(realConfigMasked())
        assertTrue("送达面 flag 缺 flagIncludeNotImportantViews（候选②回归）: $f",
            f.contains("flagIncludeNotImportantViews"))
        assertTrue("flagDefault 不该被丢掉: $f", f.contains("flagDefault"))
    }

    @Test
    fun `real config does not add continuous screen reading (no window content changed event)`() {
        val ev = attr(realConfigMasked(), "accessibilityEventTypes").orEmpty()
        assertFalse("明确不做 C：不许新增 typeWindowContentChanged（持续读屏）: $ev",
            ev.contains("typeWindowContentChanged"))
        assertTrue("长按事件仍在: $ev", ev.contains("typeViewLongClicked"))
        assertTrue("窗口状态事件仍在: $ev", ev.contains("typeWindowStateChanged"))
    }

    @Test
    fun `real config never hardcodes package names in xml`() {
        val masked = realConfigMasked()
        assertFalse("包名一律来自代码层 captureAllowedPackages，不许写死进 XML",
            Regex("""android:packageNames=""").containsMatchIn(masked))
    }
}
