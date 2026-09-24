package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.KnowledgeBase
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
 * §5.3 画像格开拆之前先还的债：**读路径的 canonical 守门要覆盖所有 String 入口**。
 *
 * 独立复核 P0-03 末段的原话是"读取接口也必须走 safeKbFile，当前读取接口直接
 * `File(dir, relativePath)`，canonical boundary 并未覆盖所有 String 入口"。
 * 上一轮把公开读与无锁快速读并到 [KnowledgeDocumentStore.read] 一处，纠正记录（第五格）
 * 也改了；本轮量到同一个仓库里还剩四条"自己拼路径再 readText"的读：
 *  `getCurrentStage`（kb.json 的 stage）、`getTurnCount`（kb.json 的 turnCount）、
 *  `readIntent` / `readIntentUnlocked`（moment/intent.json），
 * 外加 `writeVectorUnlocked` 读 warmth.md 那一处：它的"库外"那一面黑盒量不到，
 * 但它有一个修之前测得出的红——旧布局（只有 `global/status.md`）的库读得到却**静默不写**，
 * 见 `writingVectorForALegacyLibraryActuallyLands`。
 *
 * 前三条都是**把库外的内容当本库内容返回给调用方**：画像与阶段是隐私数据，
 * 轮次数会被 [com.lovebrain.app.domain.PromptBuilder] 写进提示词、
 * 意图 revision 参与生成快照比对（`saveIntent` 把外面读到的 revision+1 直接返回），
 * 所以这不是"理论上的越界"，是能拿到值的那种。
 *
 * 每格都配一个**库内正对照**：同一份内容放在真库里必须读得到。
 * 没有正对照的"读不到"断言会因为"文件本来就解析不了"而假绿。
 */
class ProfileReadBoundaryTest {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private var activeKbName: String = "inside_kb"

    private fun newRepo(): KnowledgeRepository {
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = mockk<SecurePrefs>(relaxed = true)
        every { prefs.activeKbName } answers { activeKbName }
        every { prefs.activeKbName = any<String>() } answers { activeKbName = arg(0) }
        return KnowledgeRepository(
            knowledgeRoot = root,
            securePrefs = prefs,
            context = mockk<Context>(relaxed = true),
            appScope = appScope
        )
    }

    /** 写一个库，kb.json 里带上给定的 stage / turnCount */
    private fun seedKb(name: String, stage: String = "长期承诺期", turnCount: Int = 42) {
        val dir = File(root, name).apply { mkdirs() }
        File(dir, "moment").mkdirs()
        File(dir, "understand").mkdirs()
        File(dir, "kb.json").writeText(
            json.encodeToString(
                KnowledgeBase.serializer(),
                KnowledgeBase(
                    name = name,
                    displayName = name,
                    updatedAt = "2026-09-25T09:00:00+08:00",
                    stage = stage,
                    turnCount = turnCount,
                    active = true
                )
            ),
            Charsets.UTF_8
        )
    }

    private fun seedIntent(dir: File, text: String, revision: Int) {
        File(dir, "moment").mkdirs()
        File(dir, "moment/intent.json").writeText(
            json.encodeToString(
                IntentConfig.serializer(),
                IntentConfig(text = text, enabled = true, revision = revision)
            ),
            Charsets.UTF_8
        )
    }

    /** 造一个知识库根**外面**的目录，形状跟真库一样 */
    private fun makeOutsideDir(): File {
        val outside = File(root.parentFile, "profile_outside_" + System.nanoTime())
        outside.mkdirs()
        return outside
    }

    private lateinit var repo: KnowledgeRepository

    @Before
    fun setUp() {
        root = Files.createTempDirectory("profile_boundary").toFile()
        repo = newRepo()
    }

    @After
    fun tearDown() {
        appScope.cancel()
        root.deleteRecursively()
    }

    // ─────────────── kb.json 的两条读 ───────────────

    @Test
    fun stageOfAnInsideLibraryIsReadableAsAPositiveControl() = runBlocking {
        seedKb("inside_kb")
        assertEquals(
            "正对照：真库里的 stage 必须读得到，否则下面那格的「读不到」是假的",
            "长期承诺期", repo.getCurrentStage("inside_kb")
        )
    }

    @Test
    fun stageOutsideTheKnowledgeRootIsNeverRead() = runBlocking {
        val outside = makeOutsideDir()
        try {
            // 同一个文件，唯一区别是它在知识库根外面还是在根里面
            val insideCopy = File(root, "shadow_kb").also { it.mkdirs() }
            File(outside, "kb.json").writeText(
                json.encodeToString(
                    KnowledgeBase.serializer(),
                    KnowledgeBase(name = "x", stage = "分手冷却期", turnCount = 7)
                ),
                Charsets.UTF_8
            )
            File(outside, "kb.json").copyTo(File(insideCopy, "kb.json"))
            assertEquals(
                "前置：内容本身要能被读出来（放在根里就必须读到）",
                "分手冷却期", repo.getCurrentStage("shadow_kb")
            )

            val leaked = repo.getCurrentStage("../${outside.name}")
            assertEquals(
                "带 .. 的库名把库外那份 kb.json 的 stage 读进来了：实到「$leaked」。" +
                    "阶段是隐私数据，不能靠调用方自觉传对名字",
                "", leaked
            )
        } finally {
            outside.deleteRecursively()
        }
    }

    @Test
    fun turnCountOfAnInsideLibraryIsReadableAsAPositiveControl() = runBlocking {
        seedKb("inside_kb", turnCount = 42)
        assertEquals("正对照：真库里的 turnCount 必须读得到", 42, repo.getTurnCount("inside_kb"))
    }

    @Test
    fun turnCountOutsideTheKnowledgeRootIsNeverRead() = runBlocking {
        val outside = makeOutsideDir()
        try {
            File(outside, "kb.json").writeText(
                json.encodeToString(
                    KnowledgeBase.serializer(),
                    KnowledgeBase(name = "x", stage = "", turnCount = 99)
                ),
                Charsets.UTF_8
            )
            val leaked = repo.getTurnCount("../${outside.name}")
            assertEquals(
                "库外 kb.json 的 turnCount 被当成本库轮次返回了：实到 $leaked。" +
                    "PromptBuilder 会把这个数写进提示词、TopicRecorder 会把它当 inputRevision",
                0, leaked
            )
        } finally {
            outside.deleteRecursively()
        }
    }

    // ─────────────── moment/intent.json 的两条读 ───────────────

    @Test
    fun intentOfAnInsideLibraryIsReadableAsAPositiveControl() = runBlocking {
        seedKb("inside_kb")
        seedIntent(File(root, "inside_kb"), text = "把话说开", revision = 6)
        val inside = repo.readIntent("inside_kb")
        assertEquals("正对照：真库里的意图文本要读得到", "把话说开", inside.text)
        assertEquals("正对照：真库里的意图 revision 要读得到", 6, inside.revision)
    }

    @Test
    fun intentOutsideTheKnowledgeRootIsNeverRead() = runBlocking {
        val outside = makeOutsideDir()
        try {
            seedKb("inside_kb")   // 真库存在，免得"读不到"是因为库根本没有
            seedIntent(outside, text = "库外的私密意图", revision = 6)
            val leaked = repo.readIntent("../${outside.name}")
            assertEquals(
                "带 .. 的库名把库外的 intent.json 读回来了：实到「${leaked.text}」/ revision=${leaked.revision}",
                "", leaked.text
            )
            assertEquals("库外那份的 revision 也不该被带回来", 0, leaked.revision)
        } finally {
            outside.deleteRecursively()
        }
    }

    /**
     * `saveIntent` 这条更硬：它把"外面读到的 revision + 1"**当成返回值交出去**，
     * 而生成侧靠 revision 判断快照是不是旧的（第五格 `getCorrectionsRevision` 同族问题）。
     */
    @Test
    fun savingIntentDoesNotCarryAnOutsideRevisionIntoTheResult() = runBlocking {
        val outside = makeOutsideDir()
        try {
            seedKb("inside_kb")
            seedIntent(outside, text = "库外的私密意图", revision = 6)

            val saved = repo.saveIntent("../${outside.name}", text = "新意图", enabled = true)

            assertEquals(
                "库外的 revision 被推算进本次保存结果了：实到 revision=${saved.revision}。" +
                    "库不在根内时应按「当前没有意图」算，即 0 → 1",
                1, saved.revision
            )
            val stillThere = File(File(outside, "moment"), "intent.json").readText(Charsets.UTF_8)
            assertTrue(
                "库外那份文件不该被这次调用改动：$stillThere",
                stillThere.contains("库外的私密意图")
            )
        } finally {
            outside.deleteRecursively()
        }
    }

    // ─────────────── warmth.md 的那一条（不外露，用棘轮兜） ───────────────

    /**
     * `writeVectorUnlocked` 读 warmth.md 用的也是裸路径。它与其余四处的**区别**：
     * 读到的内容不外露，落盘仍走唯一写链（那里过守门），所以"库名带 .. "这一面
     * 黑盒量不到差别。
     *
     * ⚠ 下面这格在修**之前**跑过，它是绿的：我留着它是防回归，不当本轮证据用。
     * 本轮真正的证据是 [StorageBoundaryOwnershipTest] 里那条计数棘轮。
     */
    @Test
    fun vectorWriteStillRefusesToTouchAnOutsideLibrary() = runBlocking {
        val outside = makeOutsideDir()
        try {
            seedKb("inside_kb")
            File(outside, "understand").mkdirs()
            val outsideWarmth = File(outside, "understand/warmth.md")
            outsideWarmth.writeText("- 当前状态\n- 亲密度：77/100\n", Charsets.UTF_8)

            repo.writeVector("../${outside.name}", mapOf("intimacy" to 12))

            val after = outsideWarmth.readText(Charsets.UTF_8)
            assertTrue(
                "库外的 warmth.md 被改写了（这不是本库的文件）：$after",
                after.contains("亲密度：77/100")
            )
            assertEquals(
                "本库没被这次调用牵连",
                50, repo.readVector("inside_kb").getValue("intimacy")
            )
        } finally {
            outside.deleteRecursively()
        }
    }

    /**
     * 但这一处改动并非完全没有行为差别——差别在**旧布局**那一侧，而且是修之前测得出的红：
     *
     * `readVector` 走公开读，有 `understand/warmth.md → global/status.md` 的回退，
     * 所以旧库里"五维现在是多少"一直读得到；`writeVectorUnlocked` 走裸路径，读不到旧位置，
     * 于是**静默不写**。同一个库、同一份内容，两个入口给两个答案——
     * 阶段标签那条（`updateWarmthStageLabelUnlocked`）用的却是公开读，会写。
     * 归到守门读之后三者同一把尺。
     */
    @Test
    fun writingVectorForALegacyLibraryActuallyLands() = runBlocking {
        val dir = File(root, "legacy_kb").apply { mkdirs() }
        File(dir, "kb.json").writeText(
            json.encodeToString(
                KnowledgeBase.serializer(),
                KnowledgeBase(name = "legacy_kb", displayName = "旧布局", active = true)
            ),
            Charsets.UTF_8
        )
        File(dir, "global").mkdirs()
        File(dir, "global/status.md").writeText(
            "## 当前状态\n- 亲密度：77/100\n- 信任度：60/100\n",
            Charsets.UTF_8
        )
        assertEquals(
            "正对照：读那一侧本来就透过回退看得见旧文件，所以「看不见」不能当不写的理由",
            77, repo.readVector("legacy_kb").getValue("intimacy")
        )

        repo.writeVector("legacy_kb", mapOf("intimacy" to 31))

        val landed = File(File(dir, "understand"), "warmth.md")
        assertTrue(
            "改过的向量没落到新路径上——写那一侧静默不干活（阶段标签那条可不是这么写的）",
            landed.exists()
        )
        val text = landed.readText(Charsets.UTF_8)
        assertTrue("新内容里要见到 31，且保留 /100 后缀：$text", text.contains("亲密度：31/100"))
        assertEquals(
            "读写两侧同一把尺：改完再读必须是新值",
            31, repo.readVector("legacy_kb").getValue("intimacy")
        )
        assertEquals("其余四维不该被牵连", 60, repo.readVector("legacy_kb").getValue("trust"))
    }
}
