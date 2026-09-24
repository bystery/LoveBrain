package com.lovebrain.app.data

import com.lovebrain.app.model.KnowledgeBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 画像格（§5.3 第六格）的**格级**测试。
 *
 * 与 [ProfileReadBoundaryTest] 的分工：那边量的是真仓库 + 真文件系统上的边界行为，
 * 这里量的是搬进来的这几条规则本身——阶段白名单拒绝时不许落盘、向量的 "/100" 后缀要留、
 * 没变化就不写、修订号对"路径 + 内容"双向取指纹。
 *
 * 夹具 `FakeProfileStorage` 刻意**只给内存里的文件表**，并且把 canonical 守门的判据
 * 照抄一份（库名带 `..` 或分隔符就当读不到）：格子里任何一处想绕开 `storage.read`
 * 直接摸磁盘，都没有第二条路可摸——`StorageBoundaryOwnershipTest` 那三条棘轮再从源码形状兜一次。
 */
class KnowledgeProfileStoreTest {

    private class FakeProfileStorage(
        /** kbName → (相对路径 → 内容) */
        val files: MutableMap<String, MutableMap<String, String>> = mutableMapOf(),
        val metas: MutableMap<String, KnowledgeBase> = mutableMapOf()
    ) : ProfileStorage {
        val notes = mutableListOf<String>()
        val readAttempts = mutableListOf<String>()
        var timestampCalls = 0
        /** true = 这一次元数据事务里写不进去（模拟只读保护 / 库缺失 / JSON 坏掉） */
        var metaWriteFails = false
        /** 事务开了几次：拒绝路径必须一次都不开 */
        var metaTransactions = 0

        /** 与文档格同一判据：非法库名当"读不到"，不给第二条路 */
        private fun guarded(kbName: String): Boolean =
            kbName.isNotBlank() && !kbName.contains("..") && !kbName.contains("/") && !kbName.contains("\\")

        override fun note(message: String) {
            notes += message
        }

        override fun timestamp(): String {
            timestampCalls += 1
            return "2026-09-25T%02d:00:00+08:00".format(timestampCalls)
        }

        override fun read(kbName: String, relativePath: String): String {
            readAttempts += "$kbName/$relativePath"
            if (!guarded(kbName)) return ""
            return files[kbName]?.get(relativePath) ?: ""
        }

        override fun writeUnlocked(kbName: String, relativePath: String, content: String) {
            if (!guarded(kbName)) return
            files.getOrPut(kbName) { mutableMapOf() }[relativePath] = content
        }

        override fun metaOf(kbName: String): KnowledgeBase? =
            if (guarded(kbName)) metas[kbName] else null

        override fun writeMetaTransaction(kbName: String, block: ProfileTx.() -> Unit) {
            metaTransactions += 1
            if (!guarded(kbName) || metaWriteFails) return
            val tx = ProfileTx { transform ->
                val current = metas[kbName] ?: return@ProfileTx false
                metas[kbName] = transform(current)
                true
            }
            tx.block()
        }
    }

    private lateinit var storage: FakeProfileStorage
    private lateinit var store: KnowledgeProfileStore

    private val kb = "kb1"

    @Before
    fun setUp() {
        storage = FakeProfileStorage()
        store = KnowledgeProfileStore(storage)
    }

    private fun put(path: String, content: String) {
        storage.files.getOrPut(kb) { mutableMapOf() }[path] = content
    }

    private fun get(path: String): String? = storage.files[kb]?.get(path)

    private fun meta(stage: String = "", turnCount: Int = 0) {
        storage.metas[kb] = KnowledgeBase(
            name = kb, displayName = "示例", updatedAt = "旧时间", stage = stage, turnCount = turnCount
        )
    }

    // ─────────────── 阶段 ───────────────

    @Test
    fun stageOfReadsMetaAndDefaultsToBlank() {
        assertNull("夹具没配 kb.json 时 metaOf 给 null", storage.metaOf(kb))
        assertEquals("", store.stageOf(kb))
        meta(stage = "稳定期")
        assertEquals("稳定期", store.stageOf(kb))
    }

    /** 白名单外一律拒，而且**连事务都不该开**——只 log 不写才是"拒绝"的意思 */
    @Test
    fun setStageRefusesAnythingOutsideTheWhitelist() {
        meta(stage = "稳定期")

        assertFalse(store.setStage(kb, "瞎写的阶段"))
        assertEquals(0, storage.metaTransactions)
        assertEquals("稳定期", storage.metas.getValue(kb).stage)
        assertEquals(
            "拒绝要留痕，且痕里要说清是谁拒的",
            1, storage.notes.count { it.contains("拒绝非白名单阶段") && it.contains("updateStage") }
        )
    }

    @Test
    fun setStageWritesNormalizedValueAndFreshTimestamp() {
        meta(stage = "稳定期")

        assertTrue(store.setStage(kb, " 热恋 "))

        val after = storage.metas.getValue(kb)
        assertEquals("缺「期」后缀的阶段要归一化后再落盘", "热恋期", after.stage)
        assertEquals("updatedAt 要取画像格那次 timestamp 调用", "2026-09-25T01:00:00+08:00", after.updatedAt)
        assertEquals(1, storage.metaTransactions)
    }

    /** 空串按"没什么可写"处理：不开事务、也不记拒绝日志（与拆出前一致） */
    @Test
    fun blankStageIsANoOpWithoutComplaint() {
        meta(stage = "稳定期")

        assertFalse(store.setStage(kb, "   "))

        assertEquals(0, storage.metaTransactions)
        assertEquals("稳定期", storage.metas.getValue(kb).stage)
        assertTrue("空白不该被记成一次拒绝：${storage.notes}", storage.notes.isEmpty())
    }

    /** 写不进去（只读保护 / 库缺失）时必须返回 false——报成功却没写是最坏的一种假绿 */
    @Test
    fun setStageReportsFalseWhenTheTransactionCouldNotWrite() {
        meta(stage = "稳定期")
        storage.metaWriteFails = true

        assertFalse("事务写不进时要报 false", store.setStage(kb, "热恋期"))
        assertEquals("稳定期", storage.metas.getValue(kb).stage)
    }

    // ─────────────── 状态向量 ───────────────

    @Test
    fun vectorOfDefaultsEveryMissingDimensionToFifty() {
        put(KnowledgeProfileStore.WARMTH_FILE, "## 当前状态\n- 亲密度：77/100\n")

        val v = store.vectorOf(kb)

        assertEquals(5, v.size)
        assertEquals(77, v.getValue("intimacy"))
        assertEquals("解析不到的维度给中性值而不是 0", 50, v.getValue("trust"))
    }

    @Test
    fun setVectorKeepsTheSuffixAndTouchesOnlyGivenDimensions() {
        put(
            KnowledgeProfileStore.WARMTH_FILE,
            "## 当前状态\n- 亲密度：77/100\n- 信任度：待评估/100\n- 承诺度：61/100\n" +
                "- 激情：40/100\n- 安全感：55/100\n"
        )

        assertTrue(store.setVector(kb, mapOf("intimacy" to 31, "trust" to 66)))

        val warmth = get(KnowledgeProfileStore.WARMTH_FILE) ?: error("没写回文件")
        assertTrue("占位值也要被替换，且 /100 后缀留着：$warmth", warmth.contains("信任度：66/100"))
        assertTrue(warmth.contains("亲密度：31/100"))
        assertTrue("没给的维度不许被动", warmth.contains("承诺度：61/100"))
        assertTrue(warmth.contains("激情：40/100") && warmth.contains("安全感：55/100"))
    }

    /** 文件空/缺失时**不要**凭空造一个 warmth.md：静默不写，与读那一侧同一把尺 */
    @Test
    fun setVectorWritesNothingWhenTheWarmthFileIsBlankOrAbsent() {
        assertFalse(store.setVector(kb, mapOf("intimacy" to 10)))
        assertNull("库不存在时不该被创建出来", get(KnowledgeProfileStore.WARMTH_FILE))

        put(KnowledgeProfileStore.WARMTH_FILE, "   ")
        assertFalse(store.setVector(kb, mapOf("intimacy" to 10)))
        assertEquals("空白文件原样留着", "   ", get(KnowledgeProfileStore.WARMTH_FILE))
    }

    // ─────────────── 阶段标签行 ───────────────

    @Test
    fun stageLineRewriteKeepsTheOldValueAsHistory() {
        val rewritten = store.rewriteStageLine(
            "## 当前状态\n- 阶段标签：热恋期\n- 亲密度：77/100\n", "稳定期"
        )
        assertTrue("旧阶段要留成历史注释：$rewritten", rewritten.contains("稳定期；过去曾经是热恋期"))
    }

    @Test
    fun stageLineRewriteAcceptsTheShortVariant() {
        val rewritten = store.rewriteStageLine("## 当前状态\n- 阶段：热恋期\n", "破冰期")
        assertTrue("「- 阶段：」这种简写也要认：$rewritten", rewritten.contains("破冰期；过去曾经是热恋期"))
    }

    @Test
    fun stageLineRewriteInsertsUnderTheCurrentStateHeaderWhenMissing() {
        val rewritten = store.rewriteStageLine("## 概览\n正文\n## 当前状态\n- 亲密度：77/100\n", "破冰期")
        val headerIndex = rewritten.indexOf("## 当前状态")
        val lineIndex = rewritten.indexOf("- 阶段标签：破冰期")
        assertTrue("没有标签行时要插在「当前状态」节首行：$rewritten", lineIndex > headerIndex)
        assertTrue("节里原有的内容不能被顶掉", rewritten.contains("- 亲密度：77/100"))
    }

    @Test
    fun stageLineRewritePrependsWhenTheHeaderIsAlsoMissing() {
        val rewritten = store.rewriteStageLine("- 亲密度：77/100\n", "破冰期")
        assertTrue(rewritten.startsWith("- 阶段标签：破冰期\n"))
    }

    /** 同一阶段重复写应当**不落盘**：否则每次画像更新都白排一次备份节流 */
    @Test
    fun setWarmthStageLabelDoesNotWriteWhenNothingChanged() {
        put(KnowledgeProfileStore.WARMTH_FILE, "## 当前状态\n- 阶段标签：热恋期\n")
        val before = get(KnowledgeProfileStore.WARMTH_FILE)

        assertFalse(store.setWarmthStageLabel(kb, "热恋期"))
        assertEquals(before, get(KnowledgeProfileStore.WARMTH_FILE))
    }

    @Test
    fun setWarmthStageLabelWritesThroughTheSingleWriteChain() {
        put(KnowledgeProfileStore.WARMTH_FILE, "## 当前状态\n- 阶段标签：热恋期\n")

        assertTrue(store.setWarmthStageLabel(kb, "稳定期"))
        assertTrue(get(KnowledgeProfileStore.WARMTH_FILE)!!.contains("稳定期；过去曾经是热恋期"))

        storage.files.clear()
        assertFalse("文件不在时不凭空造", store.setWarmthStageLabel(kb, "破冰期"))
    }

    /** 非白名单的阶段不许把标签写进 warmth 文件 */
    @Test
    fun setWarmthStageLabelRefusesNonWhitelistWithoutTouchingTheFile() {
        put(KnowledgeProfileStore.WARMTH_FILE, "## 当前状态\n- 阶段标签：热恋期\n")
        val before = get(KnowledgeProfileStore.WARMTH_FILE)

        assertFalse(store.setWarmthStageLabel(kb, "自定义阶段"))
        assertEquals(before, get(KnowledgeProfileStore.WARMTH_FILE))
        assertEquals(1, storage.notes.count { it.contains("updateWarmthStageLabel 拒绝非白名单阶段") })
    }

    // ─────────────── 画像正文与内容修订 ───────────────

    @Test
    fun profileTextConcatenatesTheFourSourcesInAFixedOrder() {
        put("understand/style.md", "S")
        put("understand/warmth.md", "W")
        put("understand/her.md", "H")
        put("understand/me.md", "M")

        assertEquals("M\nH\nW\nS\n", store.profileText(kb))
    }

    @Test
    fun profileTextSkipsBlankSources() {
        put("understand/me.md", "  ")
        put("understand/her.md", "H\n")
        assertEquals("H\n", store.profileText(kb))
    }

    /** 修订号是"冻结输入"的身份：内容变要变，内容不动也不能变 */
    @Test
    fun contentRevisionFollowsContent() {
        put("understand/me.md", "甲")
        put("understand/her.md", "乙")
        val first = store.contentRevision(kb)
        assertEquals("同样内容两次取号要相等", first, store.contentRevision(kb))
        assertEquals("对外只截 16 位十六进制", 16, first.length)
        assertTrue(first.matches(Regex("^[0-9a-f]{16}$")))

        put("understand/me.md", "甲改")
        assertNotEqualsQuietly(first, store.contentRevision(kb), "内容变了修订号必须变")
    }

    /**
     * 修订号是**内容**的指纹，不是库名的指纹：内容相同的两个库必须同号，
     * 清单里多出一条内容必须换号。
     *
     * 为什么值得单独一格：这个号会进"冻结输入"参与身份比对。若有人把它修成
     * "混进库名/路径的号"（为撞号问题下手时很容易这么干），同一份内容换个库名就换号，
     * 复制库、改名都会造成一次假的新修订。
     *
     * ⚠ 这一格从前叫"一份带分隔符字节的文件不许冒充两份文件"，用来支持
     * "路径也喂进摘要"这个断言。把路径盐整段删掉之后它照样绿：条目数固定、顺序固定、
     * 每条后面跟一个分隔符，再往内容里塞分隔符只会多出一个字节，构造不出撞号。
     * 所以路径盐留在代码里当纵深防御，但**不再声称被测试支持**。
     * 别把这条改回去，除非先造出一个真反例。
     */
    @Test
    fun contentRevisionIsAContentFingerprintNotALibraryNameFingerprint() {
        storage.files["alpha"] = mutableMapOf("understand/me.md" to "同一段画像")
        storage.files["beta"] = mutableMapOf("understand/me.md" to "同一段画像")

        val alpha = store.contentRevision("alpha")
        val beta = store.contentRevision("beta")

        assertEquals("内容一样的两个库必须同号（号里不许混进库名）", alpha, beta)

        storage.files["beta"] = (storage.files.getValue("beta") + mapOf("moment/recent.md" to "近况"))
            .toMutableMap()
        assertTrue(
            "清单里多出一条内容，号必须变",
            alpha != store.contentRevision("beta")
        )
    }

    /**
     * 画像格**只有** storage.read 这一条取内容的路：读了几次、读的是谁，全在夹具的账上。
     *
     * ⚠ 这格量的是"能力用没用"，不是"越界会不会被挡"——真文件系统的边界行为在
     * [ProfileReadBoundaryTest] 里量（那边是仓库 + 临时目录）。内存夹具上任何直接摸磁盘的
     * 写法都只会读到空，所以这里靠"读次数与库名可核对"来证明没有第二条路，
     * 再由 `StorageBoundaryOwnershipTest` 从源码形状那一侧兜住"不许自己拼路径"。
     */
    @Test
    fun everyContentReadGoesThroughTheStorageCapability() {
        put("understand/me.md", "M")
        val other = "kb2"

        assertEquals("", store.profileText(other))
        assertEquals(
            "四份正文各读一次，且都带着调用方给的库名",
            listOf("$other/understand/me.md", "$other/understand/her.md",
                "$other/understand/warmth.md", "$other/understand/style.md"),
            storage.readAttempts
        )
        assertEquals("库没有 kb.json 时阶段是空串", "", store.stageOf(other))
        assertEquals(
            "全空的库与另一个全空的库，修订号应当相同",
            store.contentRevision("kb3"), store.contentRevision(other)
        )
    }

    private fun assertNotEqualsQuietly(a: String, b: String, message: String) {
        assertTrue("$message（两次都是 $a）", a != b)
    }
}
