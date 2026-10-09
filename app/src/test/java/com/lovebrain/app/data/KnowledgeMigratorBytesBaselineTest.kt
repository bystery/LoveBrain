package com.lovebrain.app.data

import android.content.Context
import android.content.res.AssetManager
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 迁移器落盘字节的**基线清单**（搬运前后必须逐项一致）。
 *
 * 为什么另有这一格而不够用 `KnowledgeMigratorLegacyTest`：那些格子钉的是「内容对不对」
 * （归档里还有原文、链压到 10 条、用户编辑过的不覆盖），它们看不见
 * 「同一个字节的形状漂没漂」。把迁移器的写从「自己拼 File 再裸写」换成
 * 「kbName + 相对路径走守门写」时，`KnowledgeTxMutationEntryTest` 那把尺的计数会乖乖变小，
 * 而内容写歪一位、路径少一层目录、少落一个文件，它一声不吭。
 *
 * 证据顺序按 `KnowledgeSeedWriteBytesBaselineTest` 头注那条规矩来：
 * **改动前**先跑一次取清单读数（2026-09-27 本机实测，四个场景共 54 格文件），
 * 把读数写死成下面四张期望表，再改生产码，再复跑逐项比对。
 * 顺序反了（先改码再取期望）拿到的「期望」就是新行为，等于自己给自己判无罪。
 *
 * 三处含时钟的地方先归一成 `<CLOCK>` 再比（`moment/topic.md` 的时间戳、
 *  头部的备份时间、kb.json 的 `updatedAt`）；其余逐字定死。
 * `moment/plan.md` 从 schema 资产来，所以这里的 `Context.assets` 接到 test classpath 上
 * 那**同一份**文件的原始字节——钉的是生产真正会落盘的那份内容，不是「读不到资产」的空串。
 *
 * 每个场景同时判「文件集合恰好等于期望那几格」：迁移多落一格（比如给不该动的库补了文件）
 * 与少落一格都直接红，光比内容看不住「多写」。
 */
class KnowledgeMigratorBytesBaselineTest {

    /**
     * 一格文件的基线读数。
     *
     * [raw] 是改动前实测的原始字节 SHA，只作留档不参与断言——归档头部那一行带时刻，
     * 原始 SHA 每跑都不同，拿它判等于埋一格随机红。断言用 [scrubbed]（归一时刻后的 SHA）
     * 与 [bytes]（字节数，盯住时刻那一段的宽度）两把。
     */
    private data class Row(val raw: String, val scrubbed: String, val bytes: Int)

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope

    private fun assetBytes(path: String): ByteArray {
        val res = ClassLoader.getSystemClassLoader().getResourceAsStream(path)
            ?: error("资产 $path 不在 test classpath 上——凡引用 schema 的那几格都取不到字节")
        return res.use { it.readBytes() }
    }

    private fun newRepo(): KnowledgeRepository {
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val assets = mockk<AssetManager>()
        every { assets.open(any<String>()) } answers { assetBytes(firstArg<String>()).inputStream() }
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.assets } returns assets
        return KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = mockk<SecurePrefs>(relaxed = true),
            context = ctx,
            appScope = appScope
        )
    }

    /** 见 [FixedClockZone]：CI 跑在 UTC、本机在 +08:00，时刻串一长一短，哈希与字节数会两头漂 */
    private var savedZone: java.util.TimeZone? = null

    @Before
    fun setUp() {
        savedZone = FixedClockZone.install()
        root = Files.createTempDirectory("kb_migrator_bytes").toFile()
    }

    @After
    fun tearDown() {
        // 仓库构造时会在 appScope 上挂备份调度，不取消就是漏一个协程
        if (::appScope.isInitialized) appScope.cancel()
        root.deleteRecursively()
        FixedClockZone.restore(savedZone)
        savedZone = null
    }

    private val kbDir get() = File(root, "kb")

    private fun write(path: String, content: String) {
        val f = File(kbDir, path)
        f.parentFile?.mkdirs()
        f.writeText(content, Charsets.UTF_8)
    }

    private fun writeKbJson(stage: String) {
        kbDir.mkdirs()
        File(kbDir, "kb.json").writeText(
            Json.encodeToString(
                KnowledgeBase.serializer(),
                KnowledgeBase(name = "kb", displayName = "基线库", stage = stage)
            ),
            Charsets.UTF_8
        )
    }

    // ═══════════ 清单工具 ═══════════

    /**
     * 只认这两种形状的时刻，不许顺手把别的数字也抹掉——
     * 「ISO-8601 带时区」是 `timestamp()`/kb.json 的那一把，「yyyy-MM-dd HH:mm」是 `topic.md` 那一把。
     */
    private fun scrubClock(text: String): String = text
        .replace(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:[+-]\d{2}:\d{2}|Z)"""), CLOCK)
        .replace(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}"""), CLOCK)

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun manifest(): List<String> =
        kbDir.walkTopDown().filter { it.isFile && it != kbDir }
            .map { it.relativeTo(kbDir).path.replace(File.separatorChar, '/') }
            .toList().sorted()

    /**
     * 逐格比基线 + 判文件集合恰好相等；通过时也照样打清单行——
     * 清单行是「改动前后 diff」的唯一来源，绿了却没数等于没测。
     */
    private fun checkManifest(scenario: String, expected: Map<String, Row>) {
        println("MIG-MANIFEST-START|$scenario|expected=${expected.size}")
        val actual = manifest()
        assertTrue(
            "$scenario 的期望表是空的——尺指错了地方比不量更骗人",
            expected.isNotEmpty()
        )
        assertEquals("$scenario：落盘文件集合不是基线那 ${expected.size} 格", expected.keys.sorted(), actual)
        actual.forEach { p ->
            val want = expected[p] ?: error("$scenario：$p 不在基线表里")
            val bytes = File(kbDir, p).readBytes()
            val text = String(bytes, Charsets.UTF_8)
            val scrub = sha256(scrubClock(text).toByteArray(Charsets.UTF_8))
            // 判「归一时刻后的 SHA + 字节数」两把，不判原始 SHA：归档头部那一行本来就带时刻，
            // 原始 SHA 每次跑都不同，拿它当期望等于埋一格随机红。字节数能盯住宽度，
            // 归一 SHA 盯住其余每一个字节。
            assertEquals("$scenario：$p 归一时刻后仍漂（内容形状变了）", want.scrubbed, scrub)
            assertEquals("$scenario：$p 的字节数漂了", want.bytes, bytes.size)
            println("MIG-MANIFEST|$scenario|$p|${sha256(bytes)}|$scrub|${bytes.size}")
        }
        println("MIG-MANIFEST-END|$scenario|files=${actual.size}")
    }

    // ═══════════ 期望表（2026-09-27 改动**前**实测；搬运之后必须逐项照样成立）═══════════

    /** v1 旧库：global/recent/general 整套搬进三层结构 + 补齐 v3 文件 + plan 归档重写 */
    private fun v1FullBaseline(): Map<String, Row> = mapOf(
        ".schema_version" to Row("4e07408562bedb8b60ce05c1decfe3ad16b72230967de01f640b7e4729b49fce", "4e07408562bedb8b60ce05c1decfe3ad16b72230967de01f640b7e4729b49fce", 1),
        "general/details.md" to Row("5ce4ed5694c358ff1adaa178c026c06f9836302a624f9f60c0b2865bbf83c7bb", "5ce4ed5694c358ff1adaa178c026c06f9836302a624f9f60c0b2865bbf83c7bb", 6),
        "general/lessons.md" to Row("309a86950a1ede63f99b43ede0b1150db7b53be9444ef71fb3f51586672deeff", "309a86950a1ede63f99b43ede0b1150db7b53be9444ef71fb3f51586672deeff", 6),
        "general/moments.md" to Row("f5a4d3df86a7a77cb1793b5e4cd131f27d1891d549de95f2515a14f3696d4914", "f5a4d3df86a7a77cb1793b5e4cd131f27d1891d549de95f2515a14f3696d4914", 12),
        "global/her.md" to Row("38352898d5c14937d387942c20a7ffedf7cdb5137b42bb129d68f644412a8a93", "38352898d5c14937d387942c20a7ffedf7cdb5137b42bb129d68f644412a8a93", 15),
        "global/me.md" to Row("b0e520421de29f128f3e33288b644f8e2cd9451f3e0e08ab8885c4ffc98647c2", "b0e520421de29f128f3e33288b644f8e2cd9451f3e0e08ab8885c4ffc98647c2", 15),
        "global/status.md" to Row("d88e32a976353b840e9fcb525ef1214cddc69c01ee4a29476930ee7057b689da", "d88e32a976353b840e9fcb525ef1214cddc69c01ee4a29476930ee7057b689da", 48),
        "kb.json" to Row("e2410af56494ad2fbf2c015792fee002c32cd567120b8de30c7c8fbe27791d43", "e2410af56494ad2fbf2c015792fee002c32cd567120b8de30c7c8fbe27791d43", 59),
        "memory/archive.md" to Row("d1fc47026446c82252b353f0de775634db07c7c9389b87abf4a66eb9b9dd1312", "d1fc47026446c82252b353f0de775634db07c7c9389b87abf4a66eb9b9dd1312", 20),
        "memory/counseling_log.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/lessons.md" to Row("309a86950a1ede63f99b43ede0b1150db7b53be9444ef71fb3f51586672deeff", "309a86950a1ede63f99b43ede0b1150db7b53be9444ef71fb3f51586672deeff", 6),
        // 2026-10-09 模板清理重录（K34：schema 模板剔除系统残留注释，先取改前读数再实测改后读数）：
        // plan_archive_v2 的归档头带时钟，raw 本就每跑漂、不参与断言（见 Row KDoc），故以 scrubbed 口径留档；
        // 旧读数 scrubbed=d8104096…|880B（764B 旧模板那一代），新读数 scrubbed=50c7efe1…|159B（本机定向跑实测）。
        "memory/plan_archive_v2.md" to Row("50c7efe11f608684ae2fba1f69be652bca709fd2e1e946bbd8365c6b51bc849f", "50c7efe11f608684ae2fba1f69be652bca709fd2e1e946bbd8365c6b51bc849f", 159),
        "memory/raw_chat.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/raw_scene.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/raw_topic.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/reflect_history.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/topic_log.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        // 2026-10-09 模板清理重录（K34）：v1 库缺 moment/plan.md ⇒ 迁移器补落 schema 模板 ⇒ 随模板走。
        // 旧读数 5249947e…|167B（764B 旧模板那一代归一后的量），新读数＝LF 化新模板 43B/0c67181d…（与 fresh-lib 同一对数字，同一来源）。
        "moment/plan.md" to Row("0c67181d5a230fcc120e0f6acc7fe8e109e0e996fdbad71f8cc265bb13d646ea", "0c67181d5a230fcc120e0f6acc7fe8e109e0e996fdbad71f8cc265bb13d646ea", 43),
        "moment/recent.md" to Row("b790c050c004ee912bbf81335d24380b5151614cdf053d0d28f75d4c99434a7a", "b790c050c004ee912bbf81335d24380b5151614cdf053d0d28f75d4c99434a7a", 12),
        "moment/scene.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "moment/topic.md" to Row("eec4ae8e3d7201cce1f880a347ea4432d8472f3ec6c524f809f4fc6f2c7a60c0", "4d3eebf93d57d2beef1cda81f36d2d0606c9b4fab88cbbe626d36821fff282ff", 60),
        "recent/chatlog.md" to Row("b790c050c004ee912bbf81335d24380b5151614cdf053d0d28f75d4c99434a7a", "b790c050c004ee912bbf81335d24380b5151614cdf053d0d28f75d4c99434a7a", 12),
        "understand/her.md" to Row("38352898d5c14937d387942c20a7ffedf7cdb5137b42bb129d68f644412a8a93", "38352898d5c14937d387942c20a7ffedf7cdb5137b42bb129d68f644412a8a93", 15),
        "understand/me.md" to Row("b0e520421de29f128f3e33288b644f8e2cd9451f3e0e08ab8885c4ffc98647c2", "b0e520421de29f128f3e33288b644f8e2cd9451f3e0e08ab8885c4ffc98647c2", 15),
        "understand/warmth.md" to Row("d88e32a976353b840e9fcb525ef1214cddc69c01ee4a29476930ee7057b689da", "d88e32a976353b840e9fcb525ef1214cddc69c01ee4a29476930ee7057b689da", 48),
    )

    /** v2 库 + 膨胀 plan.md + 裸「格式」说明行：补文件、包注释、归档、压缩同场发生 */
    private fun v2BloatedPlanBaseline(): Map<String, Row> = mapOf(
        ".schema_version" to Row("4e07408562bedb8b60ce05c1decfe3ad16b72230967de01f640b7e4729b49fce", "4e07408562bedb8b60ce05c1decfe3ad16b72230967de01f640b7e4729b49fce", 1),
        "kb.json" to Row("74cd6123dcac2d028b8e682416212641121779dd77a2701d49c47fbd21c36439", "74cd6123dcac2d028b8e682416212641121779dd77a2701d49c47fbd21c36439", 59),
        "memory/counseling_log.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/plan_archive_v2.md" to Row("712fc24dfc5599885e230d06ca0920be644cb2f8d7969f16e9fd5480c0de418d", "a1a053c2eb3d54c34b4ade2eae293e8aed5c631fdf4ff01197e204ad0be5be6b", 323),
        "memory/raw_chat.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/raw_scene.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/raw_topic.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/reflect_history.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "moment/plan.md" to Row("267aebd0bc5ee11966f0d9666782405e23fec989571da53badbac84b8ebf44ae", "267aebd0bc5ee11966f0d9666782405e23fec989571da53badbac84b8ebf44ae", 135),
        "moment/scene.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
    )

    /** 全新库（有目录、无 marker、无 global）：补 v3 文件 + 落 CURRENT */
    private fun freshLibBaseline(): Map<String, Row> = mapOf(
        ".schema_version" to Row("4e07408562bedb8b60ce05c1decfe3ad16b72230967de01f640b7e4729b49fce", "4e07408562bedb8b60ce05c1decfe3ad16b72230967de01f640b7e4729b49fce", 1),
        "kb.json" to Row("af825f5e6d577368df244ccb5716bec0edc617ac7053486fd187ace01db9e9b8", "af825f5e6d577368df244ccb5716bec0edc617ac7053486fd187ace01db9e9b8", 59),
        "memory/counseling_log.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/raw_chat.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/raw_scene.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/raw_topic.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/reflect_history.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        // 2026-10-09 模板清理重录（K34）：fresh-lib 的 plan.md 逐字来自资产（无时钟，raw==scrubbed）。
        // 旧读数 ab07ce51…|764B；资产改后 48B(CRLF, 3bfc86ae…)，迁移器按 LF 落盘 ⇒ 43B/0c67181d…（本机实测）。
        "moment/plan.md" to Row("0c67181d5a230fcc120e0f6acc7fe8e109e0e996fdbad71f8cc265bb13d646ea", "0c67181d5a230fcc120e0f6acc7fe8e109e0e996fdbad71f8cc265bb13d646ea", 43),
        "moment/scene.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
    )

    /** 解析不了的 plan.md：原文进归档、正文一字不动、仍标记为已迁移 */
    private fun v2UnparseableBaseline(): Map<String, Row> = mapOf(
        ".schema_version" to Row("4e07408562bedb8b60ce05c1decfe3ad16b72230967de01f640b7e4729b49fce", "4e07408562bedb8b60ce05c1decfe3ad16b72230967de01f640b7e4729b49fce", 1),
        "kb.json" to Row("74cd6123dcac2d028b8e682416212641121779dd77a2701d49c47fbd21c36439", "74cd6123dcac2d028b8e682416212641121779dd77a2701d49c47fbd21c36439", 59),
        "memory/counseling_log.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/plan_archive_v2.md" to Row("e668f782ead067d314f5e13ed7d682ad26a53f5ba8e7bd5d844efbe6ef0f1b72", "6a9977d2d25c1fd353e207ab9df6cd79cdc0d7c0943f9b9b9d3a1b66afc78004", 187),
        "memory/raw_chat.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/raw_scene.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/raw_topic.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "memory/reflect_history.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
        "moment/plan.md" to Row("09f1129a91521aed18a22602c6645f382b0a563498b760343d42adc581fbc9cb", "09f1129a91521aed18a22602c6645f382b0a563498b760343d42adc581fbc9cb", 71),
        "moment/scene.md" to Row("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0),
    )

    // ═══════════ 四个迁移场景 ═══════════

    @Test
    fun `a v1 library migration writes exactly the baseline bytes`() {
        writeKbJson(stage = "破冰期")
        write("global/me.md", "我的旧画像")
        write("global/her.md", "她的旧画像")
        write("global/status.md", "- 阶段标签：破冰期\n- 亲密度：55/100\n")
        write("recent/chatlog.md", "聊天记录")
        write("general/lessons.md", "经验")
        write("general/moments.md", "重要时刻")
        write("general/details.md", "细节")

        val repo = newRepo()
        runBlocking { repo.migrateIfNeeded("kb") }
        checkManifest("v1-full", v1FullBaseline())
    }

    @Test
    fun `a v2 library with a bloated plan writes exactly the baseline bytes`() {
        writeKbJson(stage = "稳定期")
        write(".schema_version", "2")
        write(
            "moment/plan.md",
            "格式：事项名 | 状态 | 状态链\n" +
                "# 事项计划\n\n## 进行中\n周末旅行~成都行 | 待定 | 已订机票→已订机票→酒店还没定\n\n" +
                "## 已结束\n旧事项 | 完成 | A→B→B→B\n"
        )
        val repo = newRepo()
        runBlocking { repo.migrateIfNeeded("kb") }
        checkManifest("v2-bloated-plan", v2BloatedPlanBaseline())
    }

    @Test
    fun `a brand new library writes exactly the baseline bytes`() {
        writeKbJson(stage = "待确定")
        listOf("understand", "moment", "memory").forEach { File(kbDir, it).mkdirs() }
        val repo = newRepo()
        runBlocking { repo.migrateIfNeeded("kb") }
        checkManifest("fresh-lib", freshLibBaseline())
    }

    @Test
    fun `a v2 library with an unparseable plan writes exactly the baseline bytes`() {
        writeKbJson(stage = "稳定期")
        write(".schema_version", "2")
        write("moment/plan.md", "用户在 plan.md 里写的自由文本，不是事项表格\n第二行\n")
        val repo = newRepo()
        runBlocking { repo.migrateIfNeeded("kb") }
        checkManifest("v2-unparseable-plan", v2UnparseableBaseline())
    }

    // ═══════════ 迁移写了哪些「库名 + 相对路径」——守门口径的正面证据 ═══════════

    /**
     * 迁移器每一条写都必须报得出「库名 + 相对路径」，不许再交出一个 `File`。
     *
     * 判的是**生产码形状**而不是行为：端口上那道 `atomicWrite(target: File, …)` 一旦被人加回来，
     * 「谁都能带一个库外的 File 走进来」这件事就又成立了，而行为测试要等到真越界那次才发现。
     */
    @Test
    fun `the migrator only asks for writes by kb name plus relative path`() {
        val src = File("src/main/java/com/lovebrain/app/data/KnowledgeMigrator.kt").takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/data/KnowledgeMigrator.kt")
        assertTrue("扫不到 $src——这条会恒绿，比不测更坏", src.isFile)
        val text = src.readText(Charsets.UTF_8)
        val bare = Regex("""io\.atomicWrite\(""").findAll(text).count()
        val guarded = Regex("""io\.atomicWriteAt\(""").findAll(text).count()
        assertEquals("迁移器里不许再有把 File 交给端口的裸写（只许 atomicWriteAt）", 0, bare)
        assertEquals("迁移器的 14 处写全都要走守门口，少了就是顺手删了功能", 14, guarded)
    }

    private companion object {
        const val CLOCK = "<CLOCK>"
    }
}
