package com.lovebrain.app.data

import android.content.Context
import android.content.res.AssetManager
import com.lovebrain.app.model.KbName
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * seed 落盘的**字节基线**：把 `ensureInitialKnowledgeBase()` 与 `create()` 建库时写出的
 * 每一个文件按「相对路径 + SHA-256 + 字节数」列成清单，逐项与写死的期望对上。
 *
 * 为什么要有这一格，而且为什么它必须在搬运之前先跑一次：
 * `KnowledgeTxMutationEntryTest` 那把尺只数"谁在唯一写链上"，它**看不见**字节漂没漂——
 * 把 26 处裸写换成 `KnowledgeTx.write`，链上的数会乖乖变小，而内容写歪一位它一声不吭。
 * 所以"纯搬运"的判据得另有证人，而且证据顺序是：改动前跑一次取基线读数 → 改生产码 →
 * 再跑一次证明逐项一致。顺序反了（先改码再写测试）拿到的"期望"就是新行为，等于自己给自己判无罪。
 *
 * 期望表的两条写法约束：
 * - schema 正文**不内联副本**（仓库既有约定：见 `VectorLabelContractTest` 头注"禁止副本/内联"），
 *   而是与 test classpath 上那份真资产 `assets/schema/<name>.md` 的原始字节逐字节比——
 *   于是"把 me.md 写成 her.md"这种搬错名字的事故一定红，资产本身一个字也不会被碰到。
 * - 含时钟的两格（`kb.json` 的 `updatedAt`、`moment/topic.md` 的时间戳）先把时刻归一成 `<CLOCK>`
 *   再比；其余一切（库名、显示名、阶段、计数、active、JSON 的字段顺序与四空格缩进、有没有换行）逐字定死。
 *
 * 每格都往 stdout 打 `SEED-MANIFEST|<场景>|<相对路径>|<raw sha256>|<归一 sha256>|<字节数>`；
 * 改动前后的对照读数取 `build/test-results/.../TEST-*.xml` 的 system-out，逐行 diff。
 */
class KnowledgeSeedWriteBytesBaselineTest {

    /** 一个文件格的期望内容 */
    private sealed class Expected {
        /** 逐字节定死的文本 */
        data class Literal(val text: String) : Expected()

        /** 必须与 `assets/schema/<name>.md` 同一份字节 */
        data class Asset(val name: String) : Expected()

        /** 含时钟字段：归一后必须与这段模板逐字符相等 */
        data class ClockTemplate(val scrubbed: String) : Expected()
    }

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private var activeKbName: String = ""

    /**
     * 仓库的 `loadSchema` 经 `context.assets` 读真资产；这里把 AssetManager 接到
     * test classpath 上那份**同一文件**的原始字节（接线方式照抄 `PromptAssemblyOrderContractTest`），
     * 这样基线钉住的是生产环境真正会落盘的那份内容，不是"测试里恰好读不到资产"的空串。
     */
    private fun newRepo(): KnowledgeRepository {
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.activeKbName } answers { activeKbName }
        every { prefs.activeKbName = any<String>() } answers { activeKbName = arg(0) }
        val assets = mockk<AssetManager>()
        every { assets.open(any<String>()) } answers { assetBytes(firstArg<String>()).inputStream() }
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.assets } returns assets
        return KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = prefs,
            context = ctx,
            appScope = appScope
        )
    }

    private fun assetBytes(path: String): ByteArray {
        val res = ClassLoader.getSystemClassLoader().getResourceAsStream(path)
            ?: error("资产 $path 未在 test classpath 上——检查 build.gradle.kts sourceSets.test.resources.srcDir")
        return res.use { it.readBytes() }
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("seed_bytes").toFile()
        activeKbName = ""
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    // ═══════════ 清单工具 ═══════════

    /**
     * 把时刻归一成 `<CLOCK>`。ISO-8601 带时区（`updatedAt`）与 `yyyy-MM-dd HH:mm`（话题行）两种格式都算。
     * 只认这两个形状，不许顺手把别的数字也抹掉——抹多了"内容写歪"也能过。
     */
    private fun scrubClock(text: String): String = text
        .replace(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[+-]\d{2}:\d{2}"""), CLOCK)
        .replace(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}"""), CLOCK)

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /** 目录里所有普通文件的相对路径（含 `.开头` 的隐藏文件与残留 `.tmp`——那些正是要看的） */
    private fun relativeFiles(dir: File): List<String> =
        dir.walkTopDown().filter { it.isFile && it != dir }
            .map { it.relativeTo(dir).path.replace(File.separatorChar, '/') }
            .toList().sorted()

    /**
     * 一格文件的读数 + 断言。
     *
     * 缺文件、多文件、内容漂一个字节，三种都直接红；通过时也照样打清单行，
     * 免得"绿的但什么都没数"——清单行是改动前后 diff 的唯一来源。
     */
    private fun checkFile(scene: String, dir: File, relativePath: String, expected: Expected) {
        val file = File(dir, relativePath)
        assertTrue("$scene：$relativePath 没落盘（清单：${relativeFiles(dir)}）", file.isFile)
        val bytes = file.readBytes()
        val text = String(bytes, Charsets.UTF_8)
        val scrubbed = scrubClock(text)
        val want = when (expected) {
            is Expected.Literal -> scrubClock(expected.text)
            is Expected.Asset -> scrubClock(String(assetBytes("schema/${expected.name}.md"), Charsets.UTF_8))
            is Expected.ClockTemplate -> expected.scrubbed
        }
        assertEquals("$scene：$relativePath 的字节不是期望那份", want, scrubbed)
        println(
            "SEED-MANIFEST|$scene|$relativePath|${sha256(bytes)}|${sha256(scrubbed.toByteArray(Charsets.UTF_8))}|${bytes.size}"
        )
    }

    /** 整张清单：期望表逐格比，再比"文件集合恰好等于期望集合"——少 seed 一格与多落一格都拦得住 */
    private fun checkManifest(scene: String, dir: File, expected: List<Pair<String, Expected>>) {
        println("SEED-MANIFEST-START|$scene|expected=${expected.size}")
        expected.forEach { (path, exp) -> checkFile(scene, dir, path, exp) }
        assertEquals(
            "$scene：落盘文件集合不是期望那 13 个（实测 ${relativeFiles(dir)}）",
            expected.map { it.first }.sorted(),
            relativeFiles(dir)
        )
        println("SEED-MANIFEST-END|$scene|files=${relativeFiles(dir).size}")
    }

    // ═══════════ 期望表（2026-09-27 改动**前**实测基线；搬运之后必须一字不改地照样成立）═══════════

    /** 两个 seed 段共同的 13 个相对路径 */
    private val seedPaths = listOf(
        "kb.json",
        "understand/me.md",
        "understand/her.md",
        "understand/warmth.md",
        "moment/topic.md",
        "moment/recent.md",
        "moment/scene.md",
        "moment/plan.md",
        "memory/lessons.md",
        "memory/raw_chat.md",
        "memory/raw_topic.md",
        "memory/raw_scene.md",
        "memory/counseling_log.md"
    )

    /**
     * kb.json 的期望文本。
     *
     * 换行**显式写成 \n**而不是用三引号多行串：源文件是 CRLF 的话，多行串里会带上 \r，
     * 而 `Json { prettyPrint = true }` 落盘的是 \n——那种"期望"自己带脏字节，比对就废了。
     *
     * `turnCount` / `topicCount` 不在场不是漏写：这份 Json 配置没开 `encodeDefaults`，
     * 等于默认值的字段整个不编出来（2026-09-27 改动前实测基线）。
     */
    private fun kbJson(name: String, displayName: String) =
        "{\n" +
            "    \"name\": \"$name\",\n" +
            "    \"displayName\": \"$displayName\",\n" +
            "    \"updatedAt\": \"$CLOCK\",\n" +
            "    \"stage\": \"待确定\",\n" +
            "    \"active\": true\n" +
            "}"

    /** `ensureInitialKnowledgeBase` 造的默认库：画像与记忆层是真·空内容，只有 plan 取 schema */
    private fun defaultKbManifest(): List<Pair<String, Expected>> = listOf(
        "kb.json" to Expected.ClockTemplate(kbJson("default", "默认知识库")),
        "understand/me.md" to Expected.Literal(""),
        "understand/her.md" to Expected.Literal(""),
        "understand/warmth.md" to Expected.Literal(""),
        "moment/topic.md" to Expected.ClockTemplate("- [$CLOCK] 正在聊：（等待第一次对话）"),
        "moment/recent.md" to Expected.Literal(""),
        "moment/scene.md" to Expected.Literal(""),
        "moment/plan.md" to Expected.Asset("plan"),
        "memory/lessons.md" to Expected.Literal(""),
        "memory/raw_chat.md" to Expected.Literal(""),
        "memory/raw_topic.md" to Expected.Literal(""),
        "memory/raw_scene.md" to Expected.Literal(""),
        "memory/counseling_log.md" to Expected.Literal("")
    )

    /** `create` 造的库：13 格里除 kb.json 外全部取 assets/schema/ */
    private fun createdKbManifest(name: String, displayName: String): List<Pair<String, Expected>> = listOf(
        "kb.json" to Expected.ClockTemplate(kbJson(name, displayName)),
        "understand/me.md" to Expected.Asset("me"),
        "understand/her.md" to Expected.Asset("her"),
        "understand/warmth.md" to Expected.Asset("warmth"),
        "moment/topic.md" to Expected.Asset("topic"),
        "moment/recent.md" to Expected.Asset("recent"),
        "moment/scene.md" to Expected.Asset("scene"),
        "moment/plan.md" to Expected.Asset("plan"),
        "memory/lessons.md" to Expected.Asset("lessons"),
        "memory/raw_chat.md" to Expected.Asset("raw_chat"),
        "memory/raw_topic.md" to Expected.Asset("raw_topic"),
        "memory/raw_scene.md" to Expected.Asset("raw_scene"),
        "memory/counseling_log.md" to Expected.Asset("counseling_log")
    )

    // ═══════════ 断言 ═══════════

    /** 首次启动那条路径：默认库 13 个文件的字节清单 */
    @Test
    fun `ensureInitialKnowledgeBase seeds exactly the baseline bytes`() = runBlocking {
        newRepo().ensureInitialKnowledgeBase()
        val dir = File(root, "default")
        assertTrue("默认库目录没建出来", dir.isDirectory)
        checkManifest("ensure", dir, defaultKbManifest())
        // 目录形状也是字节的一部分：三层子目录都得在
        listOf("understand", "moment", "memory").forEach {
            assertTrue("$it/ 应存在", File(dir, it).isDirectory)
        }
        assertEquals("默认库应被记为激活库", "default", activeKbName)
        assertEquals("完成标记的内容", "done", File(root, ".kb_initialized").readText())
    }

    /** 用户新建那条路径：13 个文件全部来自 assets/schema/ */
    @Test
    fun `create seeds exactly the baseline bytes`() = runBlocking {
        val repo = newRepo()
        val kb = repo.create("Seed KB-1", "显示名")
        assertEquals("库名要先过 sanitizer", "seedkb-1", kb.name)
        val dir = File(root, "seedkb-1")
        checkManifest("create", dir, createdKbManifest("seedkb-1", "显示名"))
        listOf("understand", "moment", "memory").forEach {
            assertTrue("$it/ 应存在", File(dir, it).isDirectory)
        }
    }

    /** 显示名为空时回落成库名——这条 fallback 也落在 kb.json 的字节里，得钉住 */
    @Test
    fun `create with blank display name seeds the name fallback bytes`() = runBlocking {
        val repo = newRepo()
        repo.create("fallback", "   ")
        // 显示名为空时 displayName 回落成库名，其余 12 格与上面那张表同一份字节
        val expected = createdKbManifest("fallback", "fallback")
        checkManifest("create-blank-display", File(root, "fallback"), expected)
    }

    /**
     * 搬运的前置扫描：两个 seed 段用到的每一对（库名，相对路径）都得能过 [KnowledgeTx] 那道路径守门。
     *
     * 这条是"能不能安全搬"的正面证据——守门认不下任何一格，搬过去就是静默不落盘。
     * 库名取两条路径各自真实的形状：`ensureInitialKnowledgeBase` 写死 `default`，
     * `create` 的名字是 sanitizer 出来的（含中文、下划线、连字符、数字这些合法形状）。
     */
    @Test
    fun `every seed write slot passes the tx path guard`() {
        val repo = newRepo()
        val names = listOf("default", "seedkb-1", "我的库_a", "a".repeat(100))
        val offenders = names.flatMap { name ->
            seedPaths.mapNotNull { path ->
                val ok = repo.toKbName(name) != null && repo.toKbPath(path) != null
                if (ok) null else "$name/$path"
            }
        }
        assertEquals("有 seed 槽位过不了 safeKbFile，搬过去会静默不写：$offenders", emptyList<String>(), offenders)
    }

    /**
     * `create` 那 13 处**今天为什么还留在裸写**的证人，钉的是现行真实行为：
     *
     * `create` 的 sanitizer 只按字符集过滤、不限长度，而 [KbName] 卡 100 字符——
     * 两者形状不一致。于是 101 字符以上的库名今天照样 13 格全落盘，
     * 换成 `KnowledgeTx.write` 就变成"守门拒掉 → 一个字节都不写 → 目录空着 → 库里查不到这个库"，
     * 而 seed 段没人读 `write` 的返回值，连报错都不会有。
     *
     * ⚠ 谁哪天把 create 那 13 处搬上链，这一格一定红。那时两条路都得**显式**走：
     * 要么在 `create` 入口补上长度判定（那是改行为，单独判断、单独落账），
     * 要么放宽 [KbName]（把守门改松，绝不允许顺手做）。
     */
    @Test
    fun `create still seeds bytes for names the tx guard would reject`() = runBlocking {
        val repo = newRepo()
        val tooLongForTheGuard = "a".repeat(101)
        assertTrue(
            "前提变了：KbName 现在不再卡 100 字符，这条'形状不一致'的证据失效了",
            runCatching { KbName(tooLongForTheGuard) }.isFailure
        )
        repo.create(tooLongForTheGuard, "长名库")
        checkManifest("create-over-long-name", File(root, tooLongForTheGuard), createdKbManifest(
            tooLongForTheGuard, "长名库"
        ))
    }

    private companion object {
        const val CLOCK = "<CLOCK>"
    }
}
