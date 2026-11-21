package com.lovebrain.app.feature.composer

import com.lovebrain.app.model.ChatMessage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批量导入的持有者级合同（外部审计 F3 第一条：非原子批量提交）。
 *
 * 旧写法让 `importBatchMessages` 逐条投 [ComposerStore.Intent.AddMessage]，几十条导入
 * 就触发几十次列表重建 + 几十次 `onContentChanged`（stale 判定钩子），中间态没有任何人
 * 消费。`Intent.AddMessages` 把"一次批量"当一次写入，这里判三件事：
 *
 * · **一次批量恰好一次通知**——`onContentChanged` 的增量是 1，不是条数（漏标 0 也红）；
 * · **校验失败零追加，且是全有或全无**——空批量、以及混进任何非法草稿的批量，都是
 *   "整批拒绝、零追加、零通知"，不允许"滤掉坏项、余项照导"的部分导入，也不允许
 *   "清一次列表再还原"的中间态形状；
 * · **与既有单条路径行为一致**——批量不比逐条多特权：同一条批量逐条拆开投与整批投，
 *   最终列表的（角色、正文）序列逐位相同（无缩进正文）；判空判据同（单条空正文 no-op，
 *   批量里有一条非法就整批不落），正文口径按 A 路要求保真——批量只裁块级首尾空行，
 *   不削正文行首缩进（单条路径沿用旧 trim）。
 *
 * 非法草稿（IDEA / 空正文）在 [BatchImportDraft] 构造期就被硬挡——那一格也在这里钉住：
   "校验"的第一道防线在类型上，store 侧的整批拒绝只是同一判据的镜像。
 *
 * 全 JVM：`ComposerStore` 不碰 Android、不碰持久层，不需要 Robolectric 也不需要 mockk
 * （与 `ComposerSwitchRoleTest` 同一口径）。
 */
class ComposerStoreBatchImportTest {

    /** 自本测实例建立以来的累计读数；判"这一次批量叫了几次"一律读差值（见 ComposerSwitchRoleTest 的计数器口径） */
    private var contentChanges = 0

    private fun newStore(): ComposerStore = ComposerStore(
        scope = CoroutineScope(Job()),
        onContentChanged = { contentChanges++ }
    )

    private fun draft(role: ChatMessage.Role, content: String) = BatchImportDraft(role, content)

    private fun draftList(vararg pairs: Pair<ChatMessage.Role, String>) =
        pairs.map { BatchImportDraft(it.first, it.second) }

    // ═══════════ 判据格 ═══════════

    /** 几十条一次落：列表按预览顺序逐条追加，且 `onContentChanged` 的增量恰好 1（一次通知，不是 N 次） */
    @Test
    fun aBatchAppendsInOrderAndNotifiesExactlyOnce() {
        val store = newStore()
        val batch = draftList(
            ChatMessage.Role.HER to "在吗",
            ChatMessage.Role.ME to "在的",
            ChatMessage.Role.HER to "今天几点下班",
            ChatMessage.Role.ME to "六点，怎么了",
            ChatMessage.Role.HER to "一起吃饭？"
        )
        val before = contentChanges

        store.accept(ComposerStore.Intent.AddMessages(batch))

        val after = store.messagesNow
        assertEquals("整批一次落进真实对话通道（条目数 = 预览条数）", batch.size, after.size)
        assertEquals("顺序即预览顺序，前后文上下文因此保持", batch.map { it.content }, after.map { it.content })
        assertEquals("角色逐位对齐", batch.map { it.role }, after.map { it.role })
        assertTrue("每条都是 HER/ME 真实消息，没有一条被折进军师备注通道", after.all { it.role != ChatMessage.Role.IDEA })
        assertEquals(
            "一次批量恰好一次通知（外部审计 F3：几十条导入不再逐条触发几十次列表更新）",
            before + 1, contentChanges
        )
    }

    /** 空批量：零追加、零通知（不是"清一次列表再还原"的中间态形状） */
    @Test
    fun anEmptyBatchIsANoOpWithZeroNotifications() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "已有一条"))
        val before = contentChanges

        store.accept(ComposerStore.Intent.AddMessages(emptyList()))

        assertEquals("列表一字不动", 1, store.messagesNow.size)
        assertEquals("空批量不该标 stale（没有任何内容变化）", before, contentChanges)
    }

    /**
     * 校验失败零追加（store 侧镜像格）：逐条路径上空正文是 no-op（ComposerStore.addMessage
     * 的 isBlank 判据），批量路径上"有一条非法就整批不落"必须给出同样的零追加、零通知。
     * 能进到这一格的空白草稿只能来自绕过构造期的方式，所以这里用单条路径做反向证人。
     */
    @Test
    fun singleBlankContentStaysANoOpMirroringTheBatchValidation() {
        val store = newStore()
        val before = contentChanges

        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "   "))

        assertEquals("单条空正文 no-op：批量与逐条共用同一套判空的镜像", 0, store.messagesNow.size)
        assertEquals("零通知", before, contentChanges)
    }

    /**
     * 批级空白项（store 侧镜像格）：`[HER, "  "]`——合法项与空白项同批，整批拒绝。
     *
     * 构造期 require 已挡空白正文，能进到 store 的空白项只能来自旁路（这里用 mockk 模拟
     * 未来反序列化）。删掉 store 的 `isBlank` 判据，这一格会把空白项与合法项一起落库，
     * 或落进"滤掉坏项、余项照导"的残缺对话——两种都必须红。空白项放批首、批尾各试一次。
     */
    @Test
    fun aBatchWithAnyBlankDraftIsRejectedWholeWithZeroWritesAndNotifications() {
        val blank = mockk<BatchImportDraft>()
        every { blank.role } returns ChatMessage.Role.HER
        every { blank.content } returns "  "

        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "已有一条"))
        val before = contentChanges

        store.accept(
            ComposerStore.Intent.AddMessages(
                listOf(BatchImportDraft(ChatMessage.Role.HER, "真实的一条"), blank)
            )
        )
        assertEquals("空白项在批尾也整批拒绝，合法项不许先落", 1, store.messagesNow.size)
        assertEquals("已有一条", store.messagesNow[0].content)
        assertEquals("整批拒绝 = 零通知", before, contentChanges)

        store.accept(
            ComposerStore.Intent.AddMessages(
                listOf(blank, BatchImportDraft(ChatMessage.Role.HER, "真实的一条"))
            )
        )
        assertEquals("空白项在批首同样整批拒绝", 1, store.messagesNow.size)
        assertEquals("整批拒绝 = 零通知", before, contentChanges)
    }

    /** 整批都是绕过构造期的空白项：整批拒绝 = 零追加、零通知（与空批量同一形状） */
    @Test
    fun anAllBlankBypassedBatchIsANoOpWithZeroNotifications() {
        val blankHer = mockk<BatchImportDraft>()
        every { blankHer.role } returns ChatMessage.Role.HER
        every { blankHer.content } returns "  "
        val blankMe = mockk<BatchImportDraft>()
        every { blankMe.role } returns ChatMessage.Role.ME
        every { blankMe.content } returns "\n\t"

        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "已有一条"))
        val before = contentChanges

        store.accept(ComposerStore.Intent.AddMessages(listOf(blankHer, blankMe)))

        assertEquals("零追加", 1, store.messagesNow.size)
        assertEquals("零通知", before, contentChanges)
    }

    /**
     * A 路正文保真：批量只裁块级首尾空行，正文行首缩进与行尾空格原样写入。
     *
     * 解析器（`BatchTranscriptParser`）刻意不 trim 正文行；store 若沿用逐条路径的
     * `trim()`，这里削掉的是用户要发出去的字，且端到端没有任何一层能补回来。
     */
    @Test
    fun aBatchPreservesBodyIndentationAndStripsOnlyBlankEdgeLines() {
        val store = newStore()
        val before = contentChanges

        store.accept(
            ComposerStore.Intent.AddMessages(
                draftList(
                    ChatMessage.Role.HER to "  在吗\n在的  ",
                    ChatMessage.Role.ME to "\n\n收到\n\n"
                )
            )
        )

        assertEquals("行首缩进与行尾空格原样保留（不 trim 正文）", "  在吗\n在的  ", store.messagesNow[0].content)
        assertEquals("只裁块级首尾空行，块内正文不动", "收到", store.messagesNow[1].content)
        assertEquals("整批仍只通知一次", before + 1, contentChanges)
    }

    /** 非法草稿（IDEA / 空白正文）在构造期就被拒——"校验"的第一道防线在类型上 */
    @Test
    fun illegalDraftsAreRejectedAtConstructionSoTheyNeverReachTheStore() {
        try {
            BatchImportDraft(ChatMessage.Role.IDEA, "想冒充对话的备注")
            throw AssertionError("IDEA 是军师备注通道，批量导入在构造期就该编译期挡住（KNOWN_FAILURES F1/F3 边界）")
        } catch (expected: IllegalArgumentException) {
            // 构造期硬挡：想传 IDEA 根本构造不出来，而不是运行时悄悄改道
        }
        try {
            BatchImportDraft(ChatMessage.Role.HER, "   ")
            throw AssertionError("空白正文不该能构造出草稿：它进 store 前就该被挡住")
        } catch (expected: IllegalArgumentException) {
        }
    }

    /** 整批投与逐条投的最终列表逐位一致：批量不比逐条多任何特权，两条漏斗同一份账 */
    @Test
    fun batchAndPerItemPathsEndInIdenticalLists() {
        val pairs = listOf(
            ChatMessage.Role.HER to "早上好",
            ChatMessage.Role.ME to "早",
            ChatMessage.Role.HER to "中午一起吃饭吗"
        )

        val batchStore = newStore()
        batchStore.accept(ComposerStore.Intent.AddMessages(draftList(*pairs.toTypedArray())))

        val perItemStore = newStore()
        pairs.forEach { (role, content) ->
            perItemStore.accept(ComposerStore.Intent.AddMessage(role, content))
        }

        assertEquals(
            "整批一次落与逐条落，最终（角色、正文）序列逐位相同",
            perItemStore.messagesNow.map { it.role to it.content },
            batchStore.messagesNow.map { it.role to it.content }
        )
    }

    /** 批量追加不触碰编辑位/草稿/备注/捕获角色：追加不改变任何既有条目的下标与身份 */
    @Test
    fun aBatchAppendLeavesEditingTargetDraftNoteAndCaptureRoleUntouched() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "已有的那条"))
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.ME))
        store.accept(ComposerStore.Intent.SubmitNote("本轮备注"))
        store.accept(ComposerStore.Intent.SetEditingIndex(0))
        store.accept(ComposerStore.Intent.SetDraft("这句还没提交"))
        val before = contentChanges

        store.accept(
            ComposerStore.Intent.AddMessages(draftList(ChatMessage.Role.ME to "新导入的一条"))
        )

        assertEquals("追加在尾部，已有条目不受影响", 2, store.messagesNow.size)
        assertEquals("已有那条的正文原样", "已有的那条", store.messagesNow[0].content)
        assertEquals("新条在尾部", "新导入的一条", store.messagesNow[1].content)
        assertEquals("编辑对象仍是那条旧消息（追加不改变既有条目的身份）",
            ComposerEditingTarget.RealMessage(store.messagesNow[0].id), store.editingTargetNow)
        assertEquals("编辑位下标不变（追加在尾部，前面的下标不漂移）", 0, store.editingIndexNow)
        assertEquals("正在编辑那条的草稿一个字都不掉", "这句还没提交", store.draftTextNow)
        assertEquals("军师备注不被批量导入碰（KNOWN_FAILURES F1/F3 边界）", "本轮备注", store.noteTextNow)
        assertEquals("捕获默认角色不被批量导入带着走", ChatMessage.Role.ME, store.currentRoleNow)
        assertEquals("追加确实标了一次 stale（差值恰好 1）", before + 1, contentChanges)
    }

    /**
     * 全有或全无：混进一条非法草稿就整批拒绝——**合法项也不许跟着落库**。
     *
     * 构造期 require 是第一道防线，store 侧校验是同一判据的镜像。这里用 mockk 绕过
     * 构造器造出一条 IDEA 草稿（模拟未来反序列化/内部重构等旁路），并且把坏项放在批首、
     * 批尾各试一次：旧写法是"滤掉坏项、余项照导"，于是 [IDEA, HER] 会静默落进一条
     * 用户没确认过的残缺对话；现在两条都必须整批不动。
     */
    @Test
    fun aBatchWithAnyIllegalDraftIsRejectedWholeSoNoLegalDraftLeaksIn() {
        val bypassed = mockk<BatchImportDraft>()
        every { bypassed.role } returns ChatMessage.Role.IDEA
        every { bypassed.content } returns "想冒充对话的备注"

        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "已有一条"))
        val before = contentChanges

        // 坏项在批尾：[HER, IDEA]
        store.accept(
            ComposerStore.Intent.AddMessages(
                listOf(BatchImportDraft(ChatMessage.Role.HER, "真实的一条"), bypassed)
            )
        )
        assertEquals("坏项在批尾也整批拒绝，合法项不许先落", 1, store.messagesNow.size)
        assertEquals("已有一条", store.messagesNow[0].content)
        assertEquals("整批拒绝 = 零通知", before, contentChanges)

        // 坏项在批首：[IDEA, HER]
        store.accept(
            ComposerStore.Intent.AddMessages(
                listOf(bypassed, BatchImportDraft(ChatMessage.Role.HER, "真实的一条"))
            )
        )
        assertEquals("坏项在批首同样整批拒绝", 1, store.messagesNow.size)
        assertEquals("整批拒绝 = 零通知", before, contentChanges)
        assertEquals("备注通道不被批量导入污染", "", store.noteTextNow)

        // 拒绝不是"卡死"：紧接着的合法批仍然整批落库
        store.accept(
            ComposerStore.Intent.AddMessages(
                draftList(
                    ChatMessage.Role.HER to "重新导入的一条",
                    ChatMessage.Role.ME to "我的一条"
                )
            )
        )
        assertEquals("合法批全部落库", 3, store.messagesNow.size)
        assertEquals("重新导入的一条", store.messagesNow[1].content)
        assertEquals("我的一条", store.messagesNow[2].content)
        assertEquals("合法批恰好一次通知", before + 1, contentChanges)
    }

    /** 整批都是绕过构造期的 IDEA：整批拒绝 = 零追加、零通知（与空批量同一形状）。 */
    @Test
    fun anAllIdeaBypassedBatchIsANoOpWithZeroNotifications() {
        val bypassed = mockk<BatchImportDraft>()
        every { bypassed.role } returns ChatMessage.Role.IDEA
        every { bypassed.content } returns "想冒充对话的备注"

        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "已有一条"))
        val before = contentChanges

        store.accept(ComposerStore.Intent.AddMessages(listOf(bypassed)))

        assertEquals("零追加", 1, store.messagesNow.size)
        assertEquals("零通知", before, contentChanges)
    }
}
