package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 旧库结构迁移（`KnowledgeMigrator`）的行为基线。
 *
 * 迁移代码原先埋在 `KnowledgeRepository` 里，只有 v1→v3 的两条路径被间接测到。
 * 把它抽成独立类时，这份测试是"语义没被搬坏"的证据：
 * 1. 首次迁移只在目标为空时复制旧数据，用户已经编辑过的新文件绝不覆盖；
 * 2. 旧阶段枚举（无"期"）要同时改 kb.json 与 warmth.md 的阶段标签行；
 * 3. plan.md 里重复堆积的状态链要归档原文并压缩，且重跑无害；
 * 4. 解析不了的 plan.md 原样保留，不猜、不删。
 */
class KnowledgeMigratorLegacyTest {

    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private lateinit var repo: KnowledgeRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("kb_migrator").toFile()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repo = KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = mockk<SecurePrefs>(relaxed = true),
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    private fun kbDir(name: String = "kb") = File(root, name)

    private fun write(path: String, content: String, name: String = "kb") {
        val f = File(kbDir(name), path)
        f.parentFile?.mkdirs()
        f.writeText(content, Charsets.UTF_8)
    }

    private fun read(path: String, name: String = "kb"): String {
        val f = File(kbDir(name), path)
        return if (f.exists()) f.readText(Charsets.UTF_8) else "<missing>"
    }

    /** 按老版本 kb.json 形状落盘——阶段可以是被淘汰的六选一 */
    private fun writeKbJson(stage: String, name: String = "kb") {
        val dir = kbDir(name).apply { mkdirs() }
        File(dir, "kb.json").writeText(
            Json.encodeToString(
                KnowledgeBase.serializer(),
                KnowledgeBase(name = name, displayName = name, stage = stage)
            ),
            Charsets.UTF_8
        )
    }

    // ─── 1. v1 旧库：global/recent/general 搬进三层结构 ───────────

    @Test
    fun `a v1 library is copied into the three-layer layout and stamped CURRENT`() {
        writeKbJson(stage = "破冰期")
        write("global/me.md", "我的旧画像")
        write("global/her.md", "她的旧画像")
        write("global/status.md", "- 阶段标签：破冰期\n- 亲密度：55/100\n")
        write("recent/chatlog.md", "聊天记录")
        write("general/lessons.md", "经验")
        write("general/moments.md", "重要时刻")
        write("general/details.md", "细节")

        runBlocking { repo.migrateIfNeeded("kb") }

        assertEquals("我的旧画像", read("understand/me.md"))
        assertEquals("她的旧画像", read("understand/her.md"))
        assertEquals("- 阶段标签：破冰期\n- 亲密度：55/100\n", read("understand/warmth.md"))
        assertEquals("聊天记录", read("moment/recent.md"))
        assertEquals("经验", read("memory/lessons.md"))
        assertTrue(read("memory/archive.md").contains("重要时刻"))
        assertTrue(read("memory/archive.md").contains("细节"))
        assertTrue(read("moment/topic.md").contains("正在聊"))
        assertEquals("3", read(".schema_version").trim())
        // v3 必备文件一次补齐，后续引擎不必再判空
        listOf("moment/scene.md", "moment/plan.md", "memory/raw_chat.md", "memory/topic_log.md")
            .forEach { assertTrue("$it missing after migration", File(kbDir(), it).exists()) }
    }

    @Test
    fun `files the user already edited are never overwritten by migration`() {
        writeKbJson(stage = "破冰期")
        write("global/me.md", "旧画像内容")
        write("understand/me.md", "用户新编辑的画像")
        write("understand/you.md", "旧的\u201c她\u201d文件")
        write("understand/her.md", "用户新写的她")

        runBlocking { repo.migrateIfNeeded("kb") }

        assertEquals("用户新编辑的画像", read("understand/me.md"))
        assertEquals("用户新写的她", read("understand/her.md"))
        // you.md 不能盖掉已有内容的 her.md，但也不该被改名吃掉
        assertTrue(File(kbDir(), "understand/you.md").exists())
    }

    // ─── 2. 旧阶段枚举 → 九阶段标签 ───────────────────────────────

    @Test
    fun `a legacy stage label is rewritten in both kb json and warmth md`() {
        writeKbJson(stage = "初识")
        write("understand/warmth.md", "## 当前状态\n- 阶段标签：初识\n- 亲密度：40/100\n")

        runBlocking { repo.migrateIfNeeded("kb") }

        assertEquals("初识期", runBlocking { repo.getCurrentStage("kb") })
        val warmth = read("understand/warmth.md")
        assertTrue("warmth 未更新：$warmth", warmth.contains("阶段标签：初识期"))
        assertTrue("维度行被迁移破坏：$warmth", warmth.contains("亲密度"))
    }

    @Test
    fun `an already normalized stage is left untouched`() {
        writeKbJson(stage = "热恋期")
        write("understand/warmth.md", "## 当前状态\n- 阶段标签：热恋期\n")

        runBlocking { repo.migrateIfNeeded("kb") }

        assertEquals("热恋期", runBlocking { repo.getCurrentStage("kb") })
        assertEquals("## 当前状态\n- 阶段标签：热恋期\n", read("understand/warmth.md"))
    }

    // ─── 3. plan.md 状态链归档 + 压缩 ─────────────────────────────

    /** 只补一个 v2 标记：迁移器据此走"版本够新但仍要迁 plan"的分支 */
    private fun seedV2Plan(plan: String) {
        writeKbJson(stage = "稳定期")
        write(".schema_version", "2")
        write("moment/plan.md", plan)
    }

    @Test
    fun `repeated plan states are archived then collapsed instead of growing forever`() {
        val chain = (1..4).joinToString("→") { "已订机票" } + "→酒店还没定"
        seedV2Plan("# 事项计划\n\n## 进行中\n周末旅行~成都行 | 待定 | $chain\n\n## 已结束\n旧事项 | 完成 | A→B→B→B\n")

        runBlocking { repo.migrateIfNeeded("kb") }

        val plan = read("moment/plan.md")
        assertTrue("状态链未压缩：$plan", plan.contains("已订机票→酒店还没定"))
        assertFalse("仍有连续重复：$plan", plan.contains("已订机票→已订机票"))
        assertTrue("事项名被迁坏：$plan", plan.contains("周末旅行~成都行"))
        assertTrue("结束节丢失：$plan", plan.contains("## 已结束"))
        // 原文必须留在归档里，压缩不许变成丢数据
        val archive = read("memory/plan_archive_v2.md")
        assertTrue("归档未保留原文：$archive", archive.contains(chain))
        assertEquals("3", read(".schema_version").trim())
    }

    @Test
    fun `an over long chain keeps only the ten most recent states`() {
        val chain = (1..20).joinToString("→") { "状态$it" }
        seedV2Plan("## 进行中\n事项 | 进行中 | $chain\n")

        runBlocking { repo.migrateIfNeeded("kb") }

        val numbers = Regex("状态\\d+")
            .findAll(read("moment/plan.md"))
            .map { it.value.removePrefix("状态").toInt() }
            .toList()
        assertEquals("应保留 10 条：$numbers", 10, numbers.size)
        assertEquals("应保留最近 10 条：$numbers", (11..20).toList(), numbers)
        assertTrue(read("memory/plan_archive_v2.md").contains("状态1→状态2"))
    }

    @Test
    fun `an unparseable plan is kept verbatim and still marked migrated`() {
        val freeText = "用户在 plan.md 里写的自由文本，不是事项表格\n第二行\n"
        seedV2Plan(freeText)

        runBlocking { repo.migrateIfNeeded("kb") }

        assertEquals("未知格式被改写", freeText, read("moment/plan.md"))
        assertEquals("3", read(".schema_version").trim())
    }

    @Test
    fun `running migration twice does not re-archive or re-rewrite plan`() {
        val chain = "同一条状态→同一条状态→同一条状态"
        seedV2Plan("## 进行中\n事项 | 待定 | $chain\n")

        runBlocking { repo.migrateIfNeeded("kb") }
        val planAfterFirst = read("moment/plan.md")
        val archiveAfterFirst = read("memory/plan_archive_v2.md")

        runBlocking { repo.migrateIfNeeded("kb") }

        assertEquals(planAfterFirst, read("moment/plan.md"))
        assertEquals(archiveAfterFirst, read("memory/plan_archive_v2.md"))
        assertEquals(1, Regex("plan 结构迁移 v2 备份").findAll(read("memory/plan_archive_v2.md")).count())
    }

    // ─── 4. 拆类不得把"同一时刻只有一个写者"拆成两把锁 ──────────────

    @Test
    fun `only the repository declares a file mutex in the knowledge persistence layer`() {
        val srcRoot = File("src/main/java/com/lovebrain/app/data")
            .takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app/data")
        assertTrue("missing source dir: $srcRoot", srcRoot.isDirectory)

        val knowledgeFiles = srcRoot.listFiles()
            ?.filter { it.isFile && it.extension == "kt" }
            ?.filter { it.name.startsWith("Knowledge") || it.name.startsWith("Kb") }
            .orEmpty()
        assertTrue("knowledge persistence sources not found", knowledgeFiles.isNotEmpty())

        val owners = knowledgeFiles
            .filter { Regex("""=\s*[\w.]*Mutex\(""").containsMatchIn(it.readText(Charsets.UTF_8)) }
            .map { it.name }
        // 迁移/文本/打包传输任何一处再建一把锁，"单写者"就变成两把互不认识的锁
        assertEquals(listOf("KnowledgeRepository.kt"), owners)

        val migratorSrc = File(srcRoot, "KnowledgeMigrator.kt").readText(Charsets.UTF_8)
        assertFalse(
            "KnowledgeMigrator must not lock by itself — the repository holds fileMutex around it",
            Regex("withLock|run\\s*\\{\\s*mutex").containsMatchIn(migratorSrc)
        )
    }

    @Test
    fun `migration and public writes serialize on that single mutex without deadlocking`() {
        writeKbJson(stage = "初识")
        write("understand/warmth.md", "## 当前状态\n- 阶段标签：初识\n")
        write("global/me.md", "旧画像")

        runBlocking {
            withTimeout(10_000) {
                val job = launch(Dispatchers.IO) { repo.writeFile("kb", "moment/recent.md", "并发写入") }
                repo.migrateIfNeeded("kb")
                job.join()
            }
        }

        // 迁移跑完且并发写没被吞掉——两件事都完成才说明抽类没有引入第二把锁或锁倒置
        assertEquals("初识期", runBlocking { repo.getCurrentStage("kb") })
        assertEquals("并发写入", read("moment/recent.md"))
    }
}
