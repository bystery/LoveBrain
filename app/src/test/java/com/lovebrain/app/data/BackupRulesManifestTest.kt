package com.lovebrain.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 备份出口的配置尺（P2）：`AndroidManifest.xml` 必须同时引用两份规则，且两份规则的排除名单一致。
 *
 * 为什么钉在源码形状上：`allowBackup=false` 挡不住 Android 12+ 的换机迁移口径，
 * 系统要的是 manifest 里的 `android:dataExtractionRules`（API 31+）与旧版
 * `android:fullBackupContent`（API 26–30）。这两颗属性任一缺失，被排除的库/日志就少一道门。
 *
 * 钉四件事，逐格标"回退成什么就红"：
 * 1. manifest 仍是 `allowBackup="false"`，且两颗属性分别指向 `@xml/data_extraction_rules`
 *    与 `@xml/backup_rules`；引用名写错（或文件被改名）时定位失败即红。
 * 2. `backup_rules.xml` 的 `<full-backup-content>` 排除集恰好等于 `requiredExclusions`：
 *    删一条就红；多塞一条也会红（防止把必要配置顺手挡掉）。
 * 3. `data_extraction_rules.xml` 的 `<cloud-backup>` 与 `<device-transfer>` 两段各自排除全集——
 *    只写云备份、漏 D2D 迁移那一段就红。
 * 4. 两份规则都不许出现 `<include>`：白名单会把未列出的配置排除在备份外，正是升级异常那一族。
 *
 * 判"有没有某条 exclude"前**先剥 XML 注释**：两份文件的头注释里写着 `knowledge`、`cloud-backup`
 * 这些词，不剥会对全文自证空转。反向证人先证明这把尺在"注释里写了、元素没写"上数不到，
 * 才允许它去判真文件。
 *
 * 为什么放在 data 包而不是 architecture：`-Pci-lite`（push/PR 门禁）把 architecture 整包
 * 移出（app/build.gradle.kts §13 注释），这道尺要每次 push 都跑；排除名单本身就是 data 层
 * 的落盘名（knowledge / champion / feedback / 两份日志 / SecurePrefs），归这里也顺。
 */
class BackupRulesManifestTest {

    /** 排除名单真源：domain/path，路径名与落盘常量一一对应（见两份规则文件的注释）。 */
    private val requiredExclusions = setOf(
        "file/knowledge",              // KnowledgeRepository 根：记忆/画像/聊天归档/库快照
        "file/champion",               // ChampionEngine 根：events、shadow_log、contract_guard_audit
        "file/feedback",               // FeedbackCaseRepository：含真实消息的诊断快照
        "file/lb_log.txt",             // util/L.init
        "file/capture_diag.log",       // CopyCaptureService.DIAG_FILE
        "root/cache",                  // 导入/导出暂存（cacheDir 落在数据根下）
        "sharedpref/lovebrain_secure_prefs.xml",      // SecurePrefs 加密主存
        "sharedpref/lovebrain_prefs_fallback.xml"     // SecurePrefs 降级明文存
    )

    // ═══════════ 定位与解析（剥注释后再判） ═══════════

    private fun locate(relativeToModule: String): File {
        val candidates = listOf("", "../", "app/").map { File(it + relativeToModule) }
        return candidates.firstOrNull { it.isFile }
            ?: throw AssertionError(
                "定位不到 $relativeToModule — user.dir=${System.getProperty("user.dir")}，候选=$candidates"
            )
    }

    private fun stripXmlComments(xml: String): String =
        Regex("<!--[\\s\\S]*?-->").replace(xml) { " " }

    private fun manifestMasked(): String =
        stripXmlComments(locate("src/main/AndroidManifest.xml").readText(Charsets.UTF_8))

    private fun attr(masked: String, name: String): String? =
        Regex("android:$name=\"([^\"]*)\"").find(masked)?.groupValues?.get(1)

    /** 取 `<tag ...>...</tag>` 内部（已剥注释）。 */
    private fun section(masked: String, tag: String): String? =
        Regex("<$tag\\b[^>]*>([\\s\\S]*?)</$tag>").find(masked)?.groupValues?.get(1)

    /** 把一段里的 `<exclude>` 收成 `domain/path` 集合；缺属性记成 `null` 让断言直接红。 */
    private fun exclusions(maskedSection: String): Set<String> =
        Regex("<exclude\\b[^>]*>").findAll(maskedSection).map { m ->
            val domain = Regex("domain=\"([^\"]*)\"").find(m.value)?.groupValues?.get(1)
            val path = Regex("path=\"([^\"]*)\"").find(m.value)?.groupValues?.get(1)
            "$domain/$path"
        }.toSet()

    private fun ruleFile(name: String): String =
        stripXmlComments(locate("src/main/res/xml/$name").readText(Charsets.UTF_8))

    // ═══════════ 反向证人：尺先数得到 0/1，才许碰真文件 ═══════════

    @Test
    fun `the exclusion judge ignores paths mentioned only inside an xml comment`() {
        val decoy = """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- <exclude domain="file" path="knowledge" /> 示例，真配置不含 -->
            <full-backup-content>
                <exclude domain="file" path="lb_log.txt" />
            </full-backup-content>
        """.trimIndent()
        val got = exclusions(stripXmlComments(decoy))
        assertEquals("尺被注释里的 exclude 骗了: $got", setOf("file/lb_log.txt"), got)
    }

    @Test
    fun `the judge reports the missing entry on an incomplete rule set`() {
        val synthetic = stripXmlComments(
            """<full-backup-content><exclude domain="file" path="lb_log.txt" /></full-backup-content>"""
        )
        val missing = requiredExclusions - exclusions(synthetic)
        assertTrue("尺没牙：排除不全却报缺失为空", missing.isNotEmpty())
        assertTrue("缺的必须点名 knowledge 那条: $missing", missing.contains("file/knowledge"))
    }

    // ═══════════ 真文件四格 ═══════════

    @Test
    fun `manifest keeps backups off and references both rule files`() {
        val manifest = manifestMasked()
        assertEquals("allowBackup 必须仍是 false（本次只补显式规则，没有放开备份）",
            "false", attr(manifest, "allowBackup"))
        assertEquals("回退点：dataExtractionRules 缺失或指向别的文件",
            "@xml/data_extraction_rules", attr(manifest, "dataExtractionRules"))
        assertEquals("回退点：fullBackupContent 缺失或指向别的文件",
            "@xml/backup_rules", attr(manifest, "fullBackupContent"))
        assertTrue("manifest 引用的 data_extraction_rules.xml 不存在",
            locate("src/main/res/xml/data_extraction_rules.xml").isFile)
        assertTrue("manifest 引用的 backup_rules.xml 不存在",
            locate("src/main/res/xml/backup_rules.xml").isFile)
    }

    @Test
    fun `manifest declares no second backup entry point`() {
        val manifest = manifestMasked()
        assertNull("出现了 backupAgent：备份通路会绕开两份规则", attr(manifest, "backupAgent"))
        assertNull("出现了 fullBackupOnly：会强行整包备份，本次口径不采用", attr(manifest, "fullBackupOnly"))
    }

    @Test
    fun `legacy full-backup-content excludes exactly the private paths`() {
        val xml = ruleFile("backup_rules.xml")
        val section = section(xml, "full-backup-content")
        assertNotNull("backup_rules.xml 缺 <full-backup-content> 根元素（系统会当作空规则）", section)
        assertEquals("full-backup-content 的排除集与真源不一致（删/加一条都红）",
            requiredExclusions, exclusions(section!!))
        assertFalse("不许写 <include>：白名单会把未列出的必要配置排除在备份外",
            Regex("<include\\b").containsMatchIn(xml))
    }

    @Test
    fun `api 31 rules cover cloud backup and device transfer with the same exclusions`() {
        val xml = ruleFile("data_extraction_rules.xml")
        val cloud = section(xml, "cloud-backup")
        val transfer = section(xml, "device-transfer")
        assertNotNull("data_extraction_rules.xml 缺 <cloud-backup> 段", cloud)
        assertNotNull("data_extraction_rules.xml 缺 <device-transfer> 段（D2D 换机就漏这道门）", transfer)
        assertEquals("cloud-backup 的排除集与真源不一致",
            requiredExclusions, exclusions(cloud!!))
        assertEquals("device-transfer 的排除集与真源不一致",
            requiredExclusions, exclusions(transfer!!))
        assertFalse("不许写 <include>：与 backup_rules.xml 同一口径",
            Regex("<include\\b").containsMatchIn(xml))
    }
}
