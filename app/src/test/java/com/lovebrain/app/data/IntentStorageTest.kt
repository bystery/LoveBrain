package com.lovebrain.app.data

import android.content.Context
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import com.lovebrain.app.model.isIntentEffective
import com.lovebrain.app.model.KnowledgeBase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * : 持续意图存储测试。
 *
 * 验证点：
 * 1. 默认读取返回 IntentConfig()（enabled=false, text="", revision=0）
 * 2. 保存后读取正确
 * 3. 每次保存 revision 递增
 * 4. 关闭后 text 保留，enabled=false
 * 5. 重启后（新 repo 实例）读取仍有数据
 */
class IntentStorageTest {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private lateinit var root: File
    private lateinit var appScope: CoroutineScope
    private var activeKbName: String = "testkb"

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

    private fun File.sub(relativePath: String): File = File(this, relativePath).apply {
        parentFile?.mkdirs()
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("intent_storage").toFile()
        val dir = File(root, "testkb").apply { mkdirs() }
        File(dir, "moment").mkdirs()
        File(dir, "memory").mkdirs()
        File(dir, "understand").mkdirs()
        File(dir, "kb.json").writeText(json.encodeToString(KnowledgeBase.serializer(),
            KnowledgeBase(name = "testkb", displayName = "Test", updatedAt = "2026-09-16T09:00:00+08:00", active = true)))
    }

    @After
    fun tearDown() {
        // F01 那三格走的是存储替身，不建仓库、没有 appScope——lateinit 没上值就别 cancel
        if (::appScope.isInitialized) appScope.cancel()
        root.deleteRecursively()
    }

    // ════════════════════════════════════════════════════════════════
    // Test 1: 默认读取返回空配置
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `default_read_returns_empty_config`() = runBlocking {
        val repo = newRepo()
        val intent = repo.readIntent("testkb")

        assertEquals("默认 text 应为空", "", intent.text)
        assertFalse("默认 enabled 应为 false", intent.enabled)
        assertEquals("默认 revision 应为 0", 0, intent.revision)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 2: 保存后读取正确
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `save_then_read_correct`() = runBlocking {
        val repo = newRepo()
        repo.saveIntent("testkb", "先恢复轻松交流", true)

        val intent = repo.readIntent("testkb")
        assertEquals("text 应匹配", "先恢复轻松交流", intent.text)
        assertTrue("enabled 应为 true", intent.enabled)
        assertEquals("revision 应为 1", 1, intent.revision)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 3: 每次保存 revision 递增
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `revision_increments_on_each_save`() = runBlocking {
        val repo = newRepo()

        val r1 = repo.saveIntent("testkb", "意图1", true)
        assertEquals("第一次保存 revision=1", 1, r1.revision)

        val r2 = repo.saveIntent("testkb", "意图2", true)
        assertEquals("第二次保存 revision=2", 2, r2.revision)

        val r3 = repo.saveIntent("testkb", "意图3", false)
        assertEquals("第三次保存 revision=3", 3, r3.revision)

        val intent = repo.readIntent("testkb")
        assertEquals("最终 revision 应为 3", 3, intent.revision)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 4: 关闭后 text 保留，enabled=false
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `close_keeps_text_disables_enabled`() = runBlocking {
        val repo = newRepo()
        repo.saveIntent("testkb", "先恢复轻松交流", true)
        repo.saveIntent("testkb", "先恢复轻松交流", false)

        val intent = repo.readIntent("testkb")
        assertEquals("关闭后 text 应保留", "先恢复轻松交流", intent.text)
        assertFalse("关闭后 enabled 应为 false", intent.enabled)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 5: 重启后读取仍有数据
    // ════════════════════════════════════════════════════════════════

    // ════════════════════════════════════════════════════════════════
    // F01（2026-10-15 书 §2）：写门挡下＝失败，不许当成功交回
    // ════════════════════════════════════════════════════════════════

    /**
     * 写动作返回 **false**（只读/schema 过新/非法路径被挡下的真形状——`IntentTx.write`
     * 的 KDoc 写明"一个字节都没落"，它不抛）时，`save` 不许把新配置当成功返回。
     * 坏实现＝本轮之前的形状：note 一句然后 `return updated`——控制器认"正常返回＝落盘成功"，
     * 于是屏幕念「已保存」、成功回执加一票，盘上还是旧配置。
     * 用替身直接把门拨到 false（书 §2 指定的形状），不造十种文件系统故障矩阵。
     */
    @Test
    fun `a declined write at the intent gate throws instead of returning a fake success`() {
        val storage = mockk<IntentStorage>()
        var noted: String? = null
        every { storage.read(any(), any()) } returns ""
        every { storage.note(any()) } answers { noted = arg(0) }
        every { storage.runWrite(any(), any()) } answers {
            val block: IntentTx.() -> Unit = lastArg()
            block.invoke(IntentTx { _, _ -> false })
        }
        val service = KnowledgeIntentService(storage)

        val outcome = runCatching {
            service.save("testkb", "先恢复轻松交流", true,
                IntentExpiry.ONE_DAY, "", IntentStatus.ACTIVE)
        }
        val thrown = outcome.exceptionOrNull()
        assertTrue(
            "写门挡下时 save 必须以异常传出失败（旧形状静默 return updated＝假成功）；实到 $outcome",
            thrown is java.io.IOException
        )
        assertTrue("异常那句要指得回这块库：${thrown!!.message}",
            thrown.message!!.contains("testkb"))
        assertTrue("留痕那句还要说清为什么没落盘（观测通道不撤）", noted?.contains("未落盘") == true)
    }

    /**
     * 正向对照：同一颗门返回 **true** 时照旧把 revision+1 的新配置交回——
     * 这一格证明上一格不是"把 save 改成恒抛"的假绿。
     */
    @Test
    fun `an accepted write at the intent gate still returns the bumped config`() {
        val storage = mockk<IntentStorage>()
        every { storage.read(any(), any()) } returns ""
        every { storage.runWrite(any(), any()) } answers {
            val block: IntentTx.() -> Unit = lastArg()
            block.invoke(IntentTx { _, _ -> true })
        }
        val service = KnowledgeIntentService(storage)

        val saved = service.save("testkb", "先恢复轻松交流", true,
            IntentExpiry.ONE_DAY, "", IntentStatus.ACTIVE)
        assertEquals("写成功照旧返回 revision+1 的新配置", 1, saved.revision)
        assertEquals("先恢复轻松交流", saved.text)
    }

    /**
     * 链那一半（书 §2「再通过 IntentController 证明」）：把真仓库测试替身接进**真服务**、
     * 真服务接进**真控制器**——写门挡下时屏上只有「保存失败，请重试」一票，成功回执零票、
     * 票根不跳、编辑器留着、isSaving 复位、屏上配置纹丝不动。
     * 旧形状在这格会把「已保存」涂出来（服务假成功返回 updated）。
     */
    @Test
    fun `the real service declining a write surfaces only the retry line through the controller`() {
        val storage = mockk<IntentStorage>()
        every { storage.read(any(), any()) } returns ""
        // note 必须有牙：上一版没桩这颗，只读分支先撞 MockKException 被控制器的
        // catch 顺手接住——两版形状下这格都"绿"，绿在夹具短路上而不是绿在行为上。
        every { storage.note(any()) } returns Unit
        every { storage.runWrite(any(), any()) } answers {
            val block: IntentTx.() -> Unit = lastArg()
            block.invoke(IntentTx { _, _ -> false })
        }
        val service = KnowledgeIntentService(storage)
        val controller = com.lovebrain.app.feature.intent.IntentController(
            scope = CoroutineScope(Dispatchers.Unconfined),
            readActiveKbName = { "testkb" },
            readIntent = { service.read(it) },
            writeIntent = { kb, text, enabled, expiry, expiryDate, status ->
                service.save(kb, text, enabled, expiry, expiryDate, status)
            },
        )
        runBlocking { controller.refreshForKb("testkb") }
        val receiptsBefore = controller.savedReceipts.value
        controller.openEditor()
        controller.save("周末约她吃饭", enabled = true)

        assertEquals("写门挡下：屏上配置不许先涂成新稿", IntentConfig(), controller.config.value)
        assertEquals("成功票根不跳号", receiptsBefore, controller.savedReceipts.value)
        assertTrue("失败那一拍编辑器必须还开着，草稿才回得来", controller.showEditor.value)
        assertFalse("isSaving 必须 finally 复位，下一次保存点得动", controller.isSaving.value)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 5: 重启后读取仍有数据
    // ════════════════════════════════════════════════════════════════

    // ════════════════════════════════════════════════════════════════
    // F02（2026-10-15 书 §3）：历史终态在唯一读口被归一化成关闭（文本保留）
    // ════════════════════════════════════════════════════════════════

    /** 把一份"旧版写出来的 intent.json"直接摆上盘（走真文件、真 decode，不造故障矩阵） */
    private fun seedLegacyIntent(kb: String, config: IntentConfig) {
        val old = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; prettyPrint = true }
        File(File(File(root, kb), "moment"), "intent.json").apply { parentFile!!.mkdirs() }
            .writeText(old.encodeToString(IntentConfig.serializer(), config), Charsets.UTF_8)
    }

    /**
     * 旧版"已完成"的形状（enabled=true + status=COMPLETED + expiry=COMPLETED）：
     * 升级后**不许**悄悄醒过来——读回显示关闭，正文一个字不丢，revision 不跳（归一化不是一次保存），
     * 盘上 JSON 不被改写（书 §11：序列化兼容先于整洁）。
     * 坏实现＝本轮之前的读侧（终态无害）：第一句当场红。
     */
    @Test
    fun `a legacy completed intent reads back switched off with its text kept`() = runBlocking {
        val legacy = IntentConfig(
            text = "先把见家长的事定下来", enabled = true, revision = 4,
            expiry = IntentExpiry.COMPLETED, expiryDate = "2026-09-30 18:00",
            status = IntentStatus.COMPLETED
        )
        seedLegacyIntent("testkb", legacy)
        val before = File(File(File(root, "testkb"), "moment"), "intent.json").readText(Charsets.UTF_8)

        val read = newRepo().readIntent("testkb")

        assertFalse("旧 COMPLETED 读回必须显示关闭（升级不许唤醒注入）", read.enabled)
        assertEquals("正文原样保留（用户点开就看得到自己写过什么）", legacy.text, read.text)
        assertEquals("归一化不是一次保存：revision 不许跳", 4, read.revision)
        assertFalse("而且这条现在不算有效", read.isIntentEffective)
        assertEquals("盘上那份 JSON 一个字都不许多/少", before,
            File(File(File(root, "testkb"), "moment"), "intent.json").readText(Charsets.UTF_8))
    }

    /** 旧 EXPIRED / PAUSED 与配对 expiry=COMPLETED 同一条口径：读回关闭、文本保留 */
    @Test
    fun `legacy expired and paused files read back switched off`() = runBlocking {
        for (pair in listOf(
            IntentStatus.EXPIRED to IntentExpiry.ONE_DAY,
            IntentStatus.PAUSED to IntentExpiry.ONE_WEEK,
            IntentStatus.ACTIVE to IntentExpiry.COMPLETED   // 旧形状配对：只靠 expiry 记"已完成"的那批
        )) {
            val (status, expiry) = pair
            seedLegacyIntent("testkb", IntentConfig(
                text = "把旅行计划定下来", enabled = true, revision = 2,
                expiry = expiry, expiryDate = "", status = status
            ))
            val read = newRepo().readIntent("testkb")
            assertFalse("$status/$expiry 那份读回必须显示关闭", read.enabled)
            assertEquals("$status/$expiry 那份正文不许丢", "把旅行计划定下来", read.text)
        }
    }

    /**
     * 书 §3 第 3 条：旧 `status=ACTIVE` 但到期时刻**已过去**的文件——保守视为已关闭，
     * 免得失效的意图升级后突然复活；解析不出的脏时刻**不杀**（归一化不替脏数据编造关闭）；
     * 未来的时刻照旧开着。三档时刻都过真盘真 decode。
     */
    @Test
    fun `a legacy ACTIVE intent past its stored deadline reads back off, future stays on, garbage is not killed`() = runBlocking {
        seedLegacyIntent("testkb", IntentConfig(
            text = "周末约她", enabled = true, revision = 1,
            expiry = IntentExpiry.ONE_WEEK, expiryDate = "2020-01-01 00:00",
            status = IntentStatus.ACTIVE
        ))
        assertFalse("过去的到期时刻：读回关闭（书点名不要留永不执行的隐藏定时器）",
            newRepo().readIntent("testkb").enabled)

        seedLegacyIntent("testkb", IntentConfig(
            text = "周末约她", enabled = true, revision = 1,
            expiry = IntentExpiry.ONE_WEEK, expiryDate = "2099-12-31 09:00",
            status = IntentStatus.ACTIVE
        ))
        assertTrue("未来的到期时刻照旧开着", newRepo().readIntent("testkb").enabled)

        seedLegacyIntent("testkb", IntentConfig(
            text = "周末约她", enabled = true, revision = 1,
            expiry = IntentExpiry.ONE_WEEK, expiryDate = "下周三吧",
            status = IntentStatus.ACTIVE
        ))
        assertTrue("脏值判不了'过去没有'就当不可比——不许编造一次关闭", newRepo().readIntent("testkb").enabled)
    }

    /**
     * 书 §3 第 4 条：被归一化关闭之后，**用户主动重开**落的就是标准新格式
     * （ACTIVE + 空时刻），此后读回照常开着——旧字段留在文件里但不再控制行为。
     */
    @Test
    fun `a deliberate re-enable after normalization lands the standard new shape`() = runBlocking {
        seedLegacyIntent("testkb", IntentConfig(
            text = "先把见家长的事定下来", enabled = true, revision = 4,
            expiry = IntentExpiry.COMPLETED, status = IntentStatus.COMPLETED
        ))
        val repo = newRepo()
        assertFalse("旧终态先被读口关进来", repo.readIntent("testkb").enabled)

        repo.saveIntent("testkb", "先把见家长的事定下来", true)

        val after = repo.readIntent("testkb")
        assertTrue("用户主动拨开之后必须算数", after.enabled)
        assertEquals("重开一律落活动态", IntentStatus.ACTIVE, after.status)
        assertEquals("重开不留期限时刻", "", after.expiryDate)
        assertTrue("重开这条有效（正文也还是那句）", after.isIntentEffective)
        assertEquals("先把见家长的事定下来", after.text)
    }

    @Test
    fun `persisted_across_restart`() = runBlocking {
        val repo1 = newRepo()
        repo1.saveIntent("testkb", "持久化意图", true)

        // 模拟重启：创建新 repo 实例
        appScope.cancel()
        val repo2 = newRepo()
        val intent = repo2.readIntent("testkb")

        assertEquals("重启后 text 应保留", "持久化意图", intent.text)
        assertTrue("重启后 enabled 应保留", intent.enabled)
        assertEquals("重启后 revision 应保留", 1, intent.revision)
    }
}
