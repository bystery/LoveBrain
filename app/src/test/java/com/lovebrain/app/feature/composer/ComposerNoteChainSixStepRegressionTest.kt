package com.lovebrain.app.feature.composer

import com.lovebrain.app.domain.GenerationFingerprints
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.buildGenerationInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第6节第3条 末段那条验收链路的**回归格**：先选她 → 加她消息 → 选补充 → 输入提醒 → 提交 →
 * 点提醒编辑 → 再选我 → 生成，四个面（真实对话 / directive / 捕获角色 / 界面）逐格核互不串位。
 *
 * 与同族 `ComposerStoreAdvisorNoteTest` 的分工：那一族按"单条判据"横着切（每条判据一格，
 * 便于定位是谁的规则坏了）；这一族按**用户的手续**竖着切——同一条链路从头走到尾，
 * 每一步都同时断言四个面。 报的原始症状（"选完想法，下一条消息的标识还是上一个角色"）
 * 恰恰只在"连着走"的时候看得见：任何一步单独测都能过，串起来才会露出谁后写谁赢。
 *
 * ## 这一族的断言必须能被坏实现打破（"红一次才算数"）
 * 每个 @Test 的 KDoc 里写着 `MUT-x`，那是**实测过的变异**：把生产改回该条历史错误形状，
 * 这一格确实变红。跑法与逐格红名单见 ；测的是 2026-10-04
 * 工作树上的 `ComposerStore` + `GenerationFingerprints`（离线 JVM 编译直跑，不经 Gradle）：
 *
 * | 变异（把生产改回坏实现） | 改动点 | 实测结果 |
 * | --- | --- | --- |
 * | MUT-1a | `effectiveNoteText` 去掉"非补充档不拼草稿"那道闸 | 3 红：本链路格 + HER/ME 草稿格 + 同族那一格 |
 * | MUT-1b | 去掉"编辑对象是真实消息就不动备注"那道闸 | 2 红：《补充》亮着时编辑那两格 |
 * | MUT-1c | 两道闸一起去掉＝旧 `ideaHintOf()` 无条件拼草稿 | 7 红（含本链路格与"编辑只落一次"那格） |
 * | MUT-2 | `applyInputKind` 让捕获角色跟着输入对象写（三轴合回一颗） | 7 红（含链路格与两格自动捕获） |
 * | MUT-3 | `commitSupplement` 不再 `onContentChanged()` | 1 红：过时判定那一格 |
 * | MUT-4 | 指纹的 `RoundInput.advisorNote` 传空（"备注只是展示用"） | 2 红：键那一维 + "只有一个算法" |
 * | MUT-5 | 冻结时先摘 IDEA 行、再拿摘过的列表算备注（修之前的形状） | 1 红：过渡期正文静默丢掉那一格 |
 * | MUT-6 | `updateMessage` 按 `composeRole`（chip）路由，不按编辑对象 | 2 红 |
 * | MUT-7 | 快照在《补充》档亮着时跳过草稿落回（`realRole ?: return`） | 2 红 |
 * | MUT-8 | 进《补充》时把未提交的她/我草稿留在输入框不搬走 | 3 红：归位格 + HER/ME 草稿格 + 同族那一格 |
 * | MUT-9 | 提交"补充"当成第三条消息追加进列表 | 2 红：链路格第⑤步 + 同族那一格 |
 * | MUT-10 | 冻结快照不应用编辑中的草稿（`messageSnapshot` 只读旧列表） | 5 红（含"判过时必须重算同一对"那格） |
 *
 * 基准（无变异）41 格全绿；上面 12 行每一行都跑过、都红过（红格名单逐条打印在
 * ）。写新格子时请照这个格式留下 `MUT` 标记——
 * 没有对应可破点的断言，就只是在描述现状。
 */
class ComposerNoteChainSixStepRegressionTest {

    private var contentChanges = 0

    private fun newStore(): ComposerStore = ComposerStore(
        scope = CoroutineScope(Job()),
        onContentChanged = { contentChanges++ }
    )

    /** 生成边界上四个面的读数——断言只用这一颗，避免"测了 store 却没测发出去的那份" */
    private class Faces(
        val store: ComposerStore,
        val frozen: ComposerStore.FrozenRoundInput
    ) {
        val input = buildGenerationInput(
            requestId = "chain",
            messages = frozen.messages,
            userHint = frozen.note,
            knowledgeBase = null,
            intentConfig = IntentConfig(),
            corrections = emptyMap<String, MemoryCorrection>(),
            correctionsRevision = 0,
            onlyThisRound = false,
            aggressive = false,
            providerIdentity = null,
            kbProfile = "",
            kbRevision = "",
            promptAssetHash = "h"
        )
    }

    private fun faces(store: ComposerStore) = Faces(store, store.freezeRoundInput())

    // ══════════════════════════ 全链路 ══════════════════════════

    /**
     * 六步走完再选我生成：四个面各自停在各自的位置。
     *
     * MUT-1a/1c（`effectiveNoteText` 去掉 `_inputKind`/`editingTarget` 两道闸，退回旧写法
     * "已提交 IDEA 行 + 无条件拼 `_draftText`"）：第②步之后任何一次"她/我"的击键都会
     * 渗进 directive，本轮在第⑦步（选我之前输入框里那句"我周末要加班"）红。
     * MUT-6（`updateMessage` 改成"chip 亮补充就先写备注"，即按 `composeRole` 路由）：
     * 第⑥步红——点灰字编辑时 `composeRole` 就是 IDEA，正文被追加成第二份而不是替换。
     * MUT-2（`applyInputKind` 里给 `_currentRole` 也写一份，即"选补充=想法角色"）：
     * 第③步之后 `currentRoleNow` 变成 IDEA 的投影，本轮在"捕获角色仍归她"那一断言红，
     * 而第⑧步的真实对话里会多出用户没说过的一句。
     */
    @Test
    fun sixStepChainKeepsFourFacesSeparateEndToEnd() {
        val store = newStore()

        // ── ① 选她（旧通道：宿主投 SetCurrentRole；UI 三颗 chip 读 inputKind） ──
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.HER))
        assertEquals(ComposerInputKind.HER, store.inputKindNow)
        assertEquals(ChatMessage.Role.HER, store.currentRoleNow)
        assertFalse(store.ideaComposeModeNow)
        assertEquals(ComposerEditingTarget.None, store.editingTargetNow)

        // ── ② 加她消息（走＋：宿主 AddMessage(composeRole=HER)） ──
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "今天真的好累"))
        assertEquals(1, store.messagesNow.size)
        assertEquals(ChatMessage.Role.HER, store.messagesNow.single().role)
        assertEquals("真实消息不占备注通道", "", store.noteTextNow)
        assertEquals("", store.ideaHint())

        // ── ③ 选补充：只动输入对象，捕获角色纹丝不动（原话第 6 条的那个断点） ──
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.IDEA))
        assertEquals("输入对象=补充", ComposerInputKind.SUPPLEMENT, store.inputKindNow)
        assertEquals("旧投影字段跟着真值", true, store.ideaComposeModeNow)
        assertEquals("捕获角色仍是她——自动捕获回来的下一句还归她", ChatMessage.Role.HER, store.currentRoleNow)
        assertEquals("真实对话没被切档动过", listOf(ChatMessage.Role.HER), store.messagesNow.map { it.role })

        // ── ④ 输入提醒（未提交）：只有补充档的草稿才进备注，灰字与 directive 读同一颗 ──
        store.accept(ComposerStore.Intent.SetDraft("我其实知道她今天加班，别再问她忙不忙"))
        assertEquals("未提交的补充已经在备注正文里（写了就得发出去）",
            "我其实知道她今天加班，别再问她忙不忙", store.ideaHint())
        assertEquals("备注本体还没收到提交", "", store.noteTextNow)
        assertEquals("对话仍只有她那一句", 1, store.messagesNow.size)

        // ── ⑤ 提交（旧通道 AddMessage(IDEA)，与新通道 SubmitNote 等价） ──
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.IDEA, "我其实知道她今天加班，别再问她忙不忙"))
        assertEquals("正文落到备注本体", "我其实知道她今天加班，别再问她忙不忙", store.noteTextNow)
        assertEquals("输入框清空", "", store.draftTextNow)
        assertEquals("备注不是一种消息角色：列表里没有 IDEA 行",
            listOf(ChatMessage.Role.HER), store.messagesNow.map { it.role })
        assertEquals("编辑位交还", ComposerEditingTarget.None, store.editingTargetNow)
        assertEquals("捕获角色仍是她", ChatMessage.Role.HER, store.currentRoleNow)

        // ── ⑥ 点提醒编辑：输入框接完整正文，编辑对象=备注，不是"上一个角色猜出来的那条" ──
        store.accept(ComposerStore.Intent.BeginNoteEdit)
        assertEquals("编辑对象是本轮备注", ComposerEditingTarget.Note, store.editingTargetNow)
        assertEquals("灰字省略过，编辑不省略：拿到的是完整正文",
            "我其实知道她今天加班，别再问她忙不忙", store.draftTextNow)
        assertEquals("编辑备注期间旧下标不许指向任何一条消息", -1, store.editingIndexNow)
        store.accept(ComposerStore.Intent.SetDraft("我其实知道她今天加班，别再问她忙不忙；也别提她前任"))
        assertEquals("编辑中的 directive 用的就是新正文，旧的不在里面",
            "我其实知道她今天加班，别再问她忙不忙；也别提她前任", store.ideaHint())
        store.accept(ComposerStore.Intent.SubmitNote("我其实知道她今天加班，别再问她忙不忙；也别提她前任"))
        assertEquals("提交是替换，不是再叠一份同义的话",
            1, store.noteTextNow.split("别再问她忙不忙").size - 1)

        // 第⑥步之后先起一次生成，确认"编辑备注"没顺手改掉任何一条真实消息
        val afterNoteEdit = faces(store)
        assertEquals("真实对话仍是她那一句", 1, afterNoteEdit.frozen.messages.size)
        assertEquals("她那一句一个字没动", "今天真的好累", afterNoteEdit.frozen.messages.single().content)
        assertEquals("directive 只有备注那一份",
            "我其实知道她今天加班，别再问她忙不忙；也别提她前任", afterNoteEdit.input.replyDirective.text)
        assertFalse("她说的话不许出现在 directive 里",
            afterNoteEdit.input.replyDirective.text.contains("今天真的好累"))
        assertEquals("对话里只有一句真话", 1, afterNoteEdit.input.dialogue.size)

        // ── ⑦ 再选我：把输入框（此刻是补充档）里未提交的那半句折回备注，绝不留给"我"那条消息 ──
        store.accept(ComposerStore.Intent.SetDraft("别只替她解释"))
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.ME))
        assertEquals("捕获角色跟着手工选择走", ChatMessage.Role.ME, store.currentRoleNow)
        assertEquals("输入对象也切回我", ComposerInputKind.ME, store.inputKindNow)
        assertEquals("未提交的补充折回备注本体",
            "我其实知道她今天加班，别再问她忙不忙；也别提她前任\n别只替她解释", store.noteTextNow)
        assertEquals("折回后输入框让位——它不会再冒充我说的话", "", store.draftTextNow)
        assertEquals("真实对话仍只有她那一句", 1, store.messagesNow.size)
        store.accept(ComposerStore.Intent.SetDraft("我周末要加班"))

        // ── ⑧ 生成：四面各归各位 ──
        val f = faces(store)
        assertEquals("真实对话=她那一句（我那句没提交的话不算消息，也不进备注）",
            1, f.frozen.messages.size)
        assertEquals(ChatMessage.Role.HER, f.frozen.messages.single().role)
        assertEquals("她那句原文", "今天真的好累", f.frozen.messages.single().content)
        assertEquals("directive=备注全文（含刚折回的两句）",
            "我其实知道她今天加班，别再问她忙不忙；也别提她前任\n别只替她解释", f.input.replyDirective.text)
        assertFalse("MUT-1a 的哨兵：《我》档里没提交的草稿绝不能渗进 directive",
            f.input.replyDirective.text.contains("我周末要加班"))
        assertFalse("她那句真话没被并成备注",
            f.input.replyDirective.text.contains("今天真的好累"))
        assertEquals("对话恰好一条真实消息", 1, f.input.dialogue.size)
        assertEquals("捕获角色仍是最后手工选的那一方", ChatMessage.Role.ME, store.currentRoleNow)
        assertEquals("编辑对象既不是消息也不是备注", ComposerEditingTarget.None, store.editingTargetNow)
        assertEquals("界面三颗 chip 停在《我》", ComposerInputKind.ME, store.inputKindNow)
    }

    /**
     * 同一批手续之后自动捕获回来一句：它落在真实聊天、按捕获角色，不进备注、也不打断编辑位。
     *
     * MUT-2（把 `_currentRole` 与输入对象合回同一颗）最直接的形状就是 `FloatingService`
     * 交来的那句被标成 IDEA 或被吞进备注——这两条在本轮分别红。
     */
    @Test
    fun capturedMessageAfterTheWholeChainStillLandsAsRealChat() {
        val store = newStore()
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.HER))
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "今天真的好累"))
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.IDEA))
        store.accept(ComposerStore.Intent.SubmitNote("别再问她忙不忙"))
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.ME))
        // 捕获就发生在《补充》亮着的时候——原话第 6 条的真实时序
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.IDEA))

        // `FloatingService.addClipIfNew` 的写法：捕获永远交 currentRole（∈ {HER, ME}）进来
        store.accept(ComposerStore.Intent.AddMessage(store.currentRoleNow, "刚捕获回来的一句"))

        assertEquals("捕获那句是第三条真实消息", 2, store.messagesNow.size)
        assertEquals(ChatMessage.Role.ME, store.messagesNow.last().role)
        assertEquals("备注没被捕获污染", "别再问她忙不忙", store.noteTextNow)
        assertNotEquals("捕获角色永远不许变成 IDEA", ChatMessage.Role.IDEA, store.currentRoleNow)
        val f = faces(store)
        assertEquals("对话两句", 2, f.input.dialogue.size)
        assertEquals("directive 仍只有备注那一句", "别再问她忙不忙", f.input.replyDirective.text)
    }

    // ════════════ MUT-1a/1c：HER/ME 草稿也拼进 replyDirective ════════════

    /**
     * 坏实现 MUT-1a/1c：`effectiveNoteText` 只看"有没有草稿"，不看这段草稿属于哪一档
     * （旧 `ideaHintOf()` 的无条件 `+ _draftText`）。
     *
     * 可破点写死三条，缺一不可：
     * - 《她》档未提交的草稿：不进 directive，也不进对话（消息本身没被改）；
     * - 《我》档未提交的草稿：同上；
     * - 《补充》档未提交的草稿：**必须**进 directive（否则用户写了等于没写）。
     * 三条放同一格是因为坏实现往往只破其中一条就自称修好了。
     */
    @Test
    fun onlySupplementDraftsReachTheDirectiveHerAndMeDraftsNeverDo() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "在吗"))

        // 《她》档：打了一句没点＋
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.HER))
        store.accept(ComposerStore.Intent.SetDraft("周末要见面吗"))
        var f = faces(store)
        assertEquals("她的未提交草稿不进 directive", "", f.input.replyDirective.text)
        assertFalse("也不混进真实对话", f.input.dialogue.any { it.text == "周末要见面吗" })

        // 《我》档：同一段草稿换了说话人，仍然是"没提交的话术"
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.ME))
        store.accept(ComposerStore.Intent.SetDraft("我周末要加班"))
        f = faces(store)
        assertEquals("我的未提交草稿不进 directive", "", f.input.replyDirective.text)
        assertFalse("也不混进真实对话", f.input.dialogue.any { it.text == "我周末要加班" })

        // 《补充》档：这一次它就是备注本体，必须发出去
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.SUPPLEMENT))
        store.accept(ComposerStore.Intent.SetDraft("先听我说完，不要只替她解释"))
        f = faces(store)
        assertTrue("补充档的未提交草稿必须进 directive",
            f.input.replyDirective.text == "先听我说完，不要只替她解释")
        // 进《补充》时那段《我》的话术被送回**真实对话**（不是被丢、也不是并进备注）：
        // 这一条是 MUT-8 的判据——坏实现把草稿留在输入框里，下一次算备注就把它拼进 directive，
        // 于是"我说的话"变成"我让军师注意：我说的话"，而真实对话里根本没有那句。
        assertEquals("真实对话是她那句 + 归位的我那句", 2, f.input.dialogue.size)
        assertEquals("归位那句仍按《我》落，不是备注",
            "我周末要加班", f.input.dialogue.last().text)
        assertEquals(com.lovebrain.app.model.DialogueSpeaker.USER, f.input.dialogue.last().speaker)
        assertFalse("它没有借备注通道混进去",
            f.input.replyDirective.text.contains("我周末要加班"))

        // 切回《我》：那段补充折回备注本体，不留在输入框里等着被当成我说的话
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.ME))
        f = faces(store)
        assertEquals("折回后仍是备注正文", "先听我说完，不要只替她解释", f.input.replyDirective.text)
        assertEquals("真实对话没有因为这次切档又冒出新的一句（还是她那句 + 归位的我那句）",
            2, f.input.dialogue.size)
    }

    /**
     * MUT-8（进《补充》时不把输入框里那段她/我的话术搬走、留在原地）：
     * 而不是让它在下一次算备注时并进 directive。
     *
     * 实测 MUT-8 红在本轮：`ideaHint()` 里冒出"今天真的好累"，
     * 而真实对话里没有那句——用户屏上看见的绿气泡与发给军师的对话就不是一回事。
     */
    @Test
    fun enteringSupplementRehomesThePendingRealDraftInsteadOfMergingItIntoTheNote() {
        val store = newStore()
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.HER))
        store.accept(ComposerStore.Intent.SetDraft("今天真的好累"))

        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.SUPPLEMENT))

        assertEquals("那句话落到真实聊天", 1, store.messagesNow.size)
        assertEquals(ChatMessage.Role.HER, store.messagesNow.single().role)
        assertEquals("备注是空的——她说的话不是给军师的补充", "", store.noteTextNow)
        assertEquals("输入框让位", "", store.draftTextNow)
        val f = faces(store)
        assertEquals("对话里有那句", 1, f.input.dialogue.size)
        assertEquals("directive 里没有那句", "", f.input.replyDirective.text)
    }

    // ══════════ MUT-1b/1c/6/7：编辑 HER 消息时既改原文又拼备注 ══════════

    /**
     * 坏实现 MUT-1c（两道闸一起去掉＝旧 `ideaHintOf()` 无条件拼草稿）：编辑一条真实消息时
     * 两个落点同时发生——快照把草稿写进那一条，备注又拼上同一段 `_draftText`。
     * 实测红名单：MUT-1c ⇒ 本轮红；MUT-1b（只去掉"编辑对象是消息"那道闸）在《她》档下还被
     * 第二道闸挡着，所以它红的是下面《补充》亮着那一格，不是本轮——两道的顺序都是有用的。
     *
     * 断言按"一句话只许出现一次、且只出现在对话里"来钉：
     * 备注里出现那句 ⇒ directive 红；对话里没有那句 ⇒ 快照红；提交后备注被写过 ⇒ 红。
     */
    @Test
    fun editingHerMessageRewritesThatMessageOnlyAndNeverTheNote() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "周末要见面吗"))
        store.accept(ComposerStore.Intent.SubmitNote("别太刻意"))
        val herId = store.messagesNow.single().id

        store.accept(ComposerStore.Intent.SetEditingIndex(0))
        assertEquals("编辑对象按稳定 ID 认", ComposerEditingTarget.RealMessage(herId), store.editingTargetNow)
        store.accept(ComposerStore.Intent.SetDraft("周末别见面了"))

        // 编辑进行中的那一次生成
        var f = faces(store)
        assertEquals("草稿只应用到那一条", "周末别见面了", f.frozen.messages.single().content)
        assertEquals("备注保持原样，一次编辑不得有第二个落点", "别太刻意", f.input.replyDirective.text)
        assertFalse("正在编辑的那句不许同时变成军师备注",
            f.input.replyDirective.text.contains("周末别见面了"))
        assertEquals("对话一句", 1, f.input.dialogue.size)

        // 提交（宿主 UpdateMessage 的旧下标形状）
        store.accept(ComposerStore.Intent.UpdateMessage(0, ChatMessage.Role.HER, "周末别见面了"))
        assertEquals("正文改掉", "周末别见面了", store.messagesNow.single().content)
        assertEquals("角色还是她", ChatMessage.Role.HER, store.messagesNow.single().role)
        assertEquals("备注没被这次编辑写过", "别太刻意", store.noteTextNow)
        assertEquals("编辑位交还", ComposerEditingTarget.None, store.editingTargetNow)
        f = faces(store)
        assertEquals("生成边界上那句话只出现在对话里", 1, f.input.dialogue.count { it.text == "周末别见面了" })
        assertFalse(f.input.replyDirective.text.contains("周末别见面了"))
    }

    /**
     * MUT-6/MUT-7 的可达触发条件：《补充》亮着时点她那条气泡的"编辑"（宿主不会把 chip 切回去）。
     * 编辑对象是消息 ⇒ 落点是那条消息；chip 说了不算。
     *
     * 坏实现（`updateMessage` 按 `composeRole` 路由、`realRole==null` 就跳过快照写入）在这一格
     * 会**同时**红两条：消息正文没变（用户以为改好了）、备注被写成那句消息。
     */
    @Test
    fun editingHerMessageWhileSupplementChipIsLitStillLandsOnTheMessage() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "周末要见面吗"))
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.IDEA))
        assertEquals(ComposerInputKind.SUPPLEMENT, store.inputKindNow)

        store.accept(ComposerStore.Intent.SetEditingIndex(0))
        store.accept(ComposerStore.Intent.SetDraft("周末别见面了"))

        val f = faces(store)
        assertEquals("落回它自己那条消息", "周末别见面了", f.frozen.messages.single().content)
        assertEquals("用的是那条消息自己的身份 HER", ChatMessage.Role.HER, f.frozen.messages.single().role)
        assertEquals("备注一个字都没被写过", "", f.input.replyDirective.text)

        // 点＋：宿主交来的 role 就是 composeRole = IDEA
        store.accept(ComposerStore.Intent.UpdateMessage(0, ChatMessage.Role.IDEA, "周末别见面了"))
        assertEquals("消息真的被改掉", "周末别见面了", store.messagesNow.single().content)
        assertEquals("没被改成 IDEA 行", ChatMessage.Role.HER, store.messagesNow.single().role)
        assertEquals("备注仍是空的", "", store.noteTextNow)
        assertEquals("那句草稿被这次提交消费掉了", "", store.draftTextNow)
        assertEquals("所以灰字也不会变成'我让军师注意：周末别见面了'", "", store.ideaHint())
    }

    // ══════════════ MUT-2：切补充时改掉 captureRole ══════════════

    /**
     * 坏实现 MUT-2：输入对象与捕获角色共用一颗（`_currentRole = IDEA`），谁后写谁赢。
     *
     * 从她、从我两个起点各走一遍"选补充"，捕获角色必须**分别**停在 HER / ME；
     * 只测一个起点的话，把默认值当捕获角色的实现也能蒙过一半。
     */
    @Test
    fun choosingSupplementNeverRewritesTheCaptureRoleFromEitherSide() {
        val store = newStore()

        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.HER))
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.SUPPLEMENT))
        assertEquals("从《她》切补充：捕获角色仍是她", ChatMessage.Role.HER, store.currentRoleNow)
        assertNotEquals(ChatMessage.Role.IDEA, store.currentRoleNow)

        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.ME))
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.SUPPLEMENT))
        assertEquals("从《我》切补充：捕获角色仍是我", ChatMessage.Role.ME, store.currentRoleNow)

        // 旧角色通道（宿主与 FloatingService 还在投的那颗）同一条判据
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.HER))
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.IDEA))
        assertEquals("IDEA 只读成输入对象，不落捕获角色", ChatMessage.Role.HER, store.currentRoleNow)
        assertEquals(ComposerInputKind.SUPPLEMENT, store.inputKindNow)
        assertTrue("旧投影字段仍是宿主与捕获链读的那一颗", store.ideaComposeModeNow)

        // 切回她/我：捕获角色跟着回来，输入对象跟着回来，备注不偷偷变成真实消息
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.ME))
        assertEquals(ChatMessage.Role.ME, store.currentRoleNow)
        assertEquals(ComposerInputKind.ME, store.inputKindNow)
        assertFalse(store.ideaComposeModeNow)
        assertEquals("没有备注就没有消息", emptyList<ChatMessage>(), store.messagesNow)
    }

    /**
     * MUT-2 的用户可见后果，单独钉一格：切过补充之后**自动捕获**回来的那句仍是真实聊天。
     * 这一格是原话第 6 条的正面复现（"下一个选想法，长按这个消息前面的标识就是《她》"）。
     */
    @Test
    fun autoCaptureKeepsItsOwnRoleAcrossSupplementSwitches() {
        val store = newStore()
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.ME))
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.SUPPLEMENT))

        // 捕获链按 currentRole 交进来（FloatingService 的唯一写法）
        fun capture(content: String) = store.accept(
            ComposerStore.Intent.AddMessage(store.currentRoleNow, content)
        )
        capture("我刚到")
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.SUPPLEMENT))
        capture("你也太晚了")

        assertEquals("捕获角色没被《补充》挪走，所以那句仍落进真实消息", 2, store.messagesNow.size)
        assertEquals("都按捕获角色《我》落", listOf(ChatMessage.Role.ME, ChatMessage.Role.ME), store.messagesNow.map { it.role })
        assertEquals("备注没被捕获写过", "", store.noteTextNow)
        val f = faces(store)
        assertEquals("对话两句", 2, f.input.dialogue.size)
        assertEquals("directive 空", "", f.input.replyDirective.text)
    }

    // ══════════════ 第6节第2条 末段：备注 ∈ 指纹/缓存键 + 生成启动即冻结 ══════════════

    /**
     * 备注原文是本轮输入指纹的一维：改备注 ⇒ 键变了 ⇒ 旧结果判过时；
     * 同一份输入重复算 ⇒ 同一个键（否则"输入没变就别重生成"永远命不中）。
     *
     * 坏实现：`ComposerStore` 把备注写进一颗 `ideaHint()` 不读的新字段（第6节第1条 点名禁的
     * "第二个可写想法状态"）——那时这一维永远不变，本轮红。
     * 另一颗坏形状也在这里红：把 `RoundInput.advisorNote` 传成 `""`（"备注只是展示用"）。
     */
    @Test
    fun noteTextIsADimensionOfTheInputKeySoNoteEditsMakeTheOldResultStale() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "在吗"))
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.SUPPLEMENT))

        val before = GenerationFingerprints.inputOf(store.freezeRoundInput().asRoundInput("kb"))
        // 逐次写入分别数：判据是"这一次备注写入自己就通知了内容变化"，
        // 不是"这一路走下来总共通知过几次"——后者在 commitSupplement 漏调 onContentChanged
        // 的实现里照样能靠旁边那次 AddMessage/SetDraft 蒙过去（MUT-3 实测就是这样漏网的）。
        val c0 = contentChanges
        store.accept(ComposerStore.Intent.SubmitNote("别只替她解释"))
        val afterFirst = GenerationFingerprints.inputOf(store.freezeRoundInput().asRoundInput("kb"))
        assertEquals("提交备注这一次写入自己就触发过时判定", c0 + 1, contentChanges)

        val c1 = contentChanges
        store.accept(ComposerStore.Intent.SetDraft("别只替她解释；也别提她前任"))
        val afterSecond = GenerationFingerprints.inputOf(store.freezeRoundInput().asRoundInput("kb"))
        assertEquals("补充档里未提交的半句同样触发", c1 + 1, contentChanges)

        assertNotEquals("加一句备注必须算作这一轮输入变了", before, afterFirst)
        assertNotEquals("编辑备注同样算变（补充档里未提交的那半句也算，用户已经写了）", afterFirst, afterSecond)
        assertEquals("同一份输入重复算得同一个键",
            afterSecond, GenerationFingerprints.inputOf(store.freezeRoundInput().asRoundInput("kb")))
        assertNotEquals("备注原文相同但对象不同不算同一轮（KB 身份那一维）",
            afterSecond, GenerationFingerprints.inputOf(store.freezeRoundInput().asRoundInput("other-kb")))
        // 反面对照：同一段文字写在《她》档里（没提交的话术）不许拨动备注那一维——
        // 少了这条对照，"任何一次击键都算变"的坏实现也能蒙过前三条。
        val noteKey = GenerationFingerprints.inputOf(store.freezeRoundInput().asRoundInput("kb"))
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.HER))
        store.accept(ComposerStore.Intent.SetDraft("别只替她解释；也别提她前任"))
        assertEquals("HER 档的未提交草稿不算备注改动（它属于那条消息）",
            noteKey, GenerationFingerprints.inputOf(store.freezeRoundInput().asRoundInput("kb")))
        val clearedFrom = contentChanges
        store.accept(ComposerStore.Intent.ClearNote)
        assertEquals("清备注这一次写入也自己触发过时判定", clearedFrom + 1, contentChanges)
        assertNotEquals("清掉备注同样算变——不许继续命中带备注的旧回复",
            noteKey, GenerationFingerprints.inputOf(store.freezeRoundInput().asRoundInput("kb")))
    }

    /**
     * 键只有一个来源：带类型的入口与位置参数入口算出同一个数。
     *
     * 这一格防的是"顺手新造第二份指纹"——两份算法一旦分叉，
     * 发起生成时冻的键与事后判过时的键就永远对不上，用户会看到"什么都没改却说明输入变了"，
     * 或者反过来"改了备注却还命中旧回复"。
     */
    @Test
    fun thereIsExactlyOneInputKeyAlgorithm() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "在吗"))
        store.accept(ComposerStore.Intent.SubmitNote("别只替她解释"))
        val frozen = store.freezeRoundInput()

        val typed = GenerationFingerprints.inputOf(frozen.asRoundInput("kb", intentRevision = 3))
        val positional = GenerationFingerprints.inputOf(
            frozen.messages, frozen.note, "kb", false, 3
        )
        assertEquals("两个入口同一条算法", typed, positional)
        assertEquals("备注那一维用的就是同一个段名", "note", GenerationFingerprints.NOTE_SECTION_KEY)
        assertNotEquals("而且那一维真的在算：备注为空与带备注不是同一个键",
            typed, GenerationFingerprints.inputOf(frozen.messages, "", "kb", false, 3))
    }

    /**
     * 生成启动时冻结的是**那一份字符串**，生成途中改实时输入不会回头改已发出的请求。
     *
     * 坏实现：把 `store::ideaHint` 这类 getter（或整个 store）交进生成链——
     * 流式过程中用户改备注，发出去的 prompt 与事后记账的上下文就不是同一份。
     * 本轮的哨兵是最后两条"实时那一份确实变了"的断言：它证明前两条不是两份相同常量的自证。
     */
    @Test
    fun noteAndDialogueAreFrozenAtStartAndLiveEditsCannotReachBackIntoThem() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "在吗"))
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.ME, "在的"))
        store.accept(ComposerStore.Intent.SubmitNote("先听我说完"))

        val frozen = store.freezeRoundInput()
        val keyAtStart = GenerationFingerprints.inputOf(frozen.asRoundInput("kb"))
        val input = buildGenerationInput(
            requestId = "r-frozen",
            messages = frozen.messages,
            userHint = frozen.note,
            knowledgeBase = null,
            intentConfig = IntentConfig(),
            corrections = emptyMap<String, MemoryCorrection>(),
            correctionsRevision = 0,
            onlyThisRound = false,
            aggressive = false,
            providerIdentity = null,
            kbProfile = "",
            kbRevision = "",
            promptAssetHash = "h"
        )

        // 生成途中用户继续动实时输入：改备注、加消息、重排
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.SUPPLEMENT))
        store.accept(ComposerStore.Intent.SetDraft("生成途中改的"))
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "生成途中加的一句"))
        store.accept(ComposerStore.Intent.ReorderMessages(0, 1))

        assertEquals("冻结的 directive 不跟着实时输入漂", "先听我说完", input.replyDirective.text)
        assertEquals("冻结的对话仍是两句", 2, input.dialogue.size)
        assertEquals("冻结的键仍是那一个", keyAtStart, GenerationFingerprints.inputOf(frozen.asRoundInput("kb")))
        assertEquals("已冻结的列表仍是当时那两句", 2, frozen.messages.size)
        // 哨兵：实时那一份真的变了——所以"标过时"与"不复活"两件事测的是同一个判据
        assertNotEquals("实时键已经变了（旧结果据此判过时）",
            keyAtStart, GenerationFingerprints.inputOf(store.freezeRoundInput().asRoundInput("kb")))
        assertTrue("实时备注确实漂了", store.ideaHint().contains("生成途中改的"))
    }

    /**
     * 判过时的那一次重算，必须用**同一颗冻结出口**，不能拿"实时列表 + 实时备注"另算一套。
     *
     * 为什么这一格值得单独钉：`messageSnapshot()` 会把正在编辑的那句草稿落到对应消息上，
     * 而 `_messages` 里还是旧文。发起生成用前者、判过时用后者（VM 现在就是
     * `computeInputFingerprint(composer.messagesNow, composer.ideaHint(), …)`），
     * 于是用户一个字都没动，刚拿到的结果却被盖上"输入已变化"——
     * 这条错方向与"改了备注却不判过时"同样贵，只是它红在体验上而不是红在花销上。
     *
     * 断言给的是**修法**而不是现象：`freezeRoundInput()` 重算两次给同一个键，
     * 而 `messagesNow` 那一套与冻结键不同——所以两侧都必须走 [ComposerStore.freezeRoundInput]。
     * （接线点见  N1/N2：`LoveBrainViewModel.kt:928-941` 与 `:682-693`。）
     */
    @Test
    fun staleRecomputationMustReuseTheSameFrozenPairInsteadOfTheRawLiveList() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "周末要见面吗"))
        store.accept(ComposerStore.Intent.SubmitNote("别太刻意"))
        store.accept(ComposerStore.Intent.SetEditingIndex(0))
        store.accept(ComposerStore.Intent.SetDraft("周末别见面了"))

        val frozen = store.freezeRoundInput()
        val frozenKey = GenerationFingerprints.inputOf(frozen.asRoundInput("kb"))
        assertEquals("同一份冻结重算两次仍是同一个键",
            frozenKey, GenerationFingerprints.inputOf(store.freezeRoundInput().asRoundInput("kb")))
        assertNotEquals("拿实时列表另算一套就会得到另一个数——这正是'没改东西却判过时'的形状",
            frozenKey,
            GenerationFingerprints.inputOf(
                GenerationFingerprints.RoundInput(store.messagesNow, store.ideaHint(), "kb", false, 0)
            ))
    }

    /** 缓存键与 prompt 用的是同一份冻结正文：两边算出同一个数，不存在"界面一套、AI 一套" */
    @Test
    fun theKeyAndThePromptReadTheSameFrozenNote() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "在吗"))
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.SUPPLEMENT))
        store.accept(ComposerStore.Intent.SetDraft("别太刻意"))

        val frozen = store.freezeRoundInput()
        val input = buildGenerationInput(
            requestId = "r-same",
            messages = frozen.messages,
            userHint = frozen.note,
            knowledgeBase = null,
            intentConfig = IntentConfig(),
            corrections = emptyMap<String, MemoryCorrection>(),
            correctionsRevision = 0,
            onlyThisRound = false,
            aggressive = false,
            providerIdentity = null,
            kbProfile = "",
            kbRevision = "",
            promptAssetHash = "h"
        )
        assertEquals("键里那一维 == 发出去的那一段", frozen.note, input.replyDirective.text)
        assertEquals("未提交的补充也算已写：一个字都不丢", "别太刻意", input.replyDirective.text)
        assertEquals("展示读的是同一颗真源", store.ideaHint(), frozen.note)
    }

    /** [ComposerStore.FrozenRoundInput] → 指纹入参的最小适配：绑死"备注来自这份冻结"这条判据 */
    private fun ComposerStore.FrozenRoundInput.asRoundInput(
        kbName: String,
        onlyThisRound: Boolean = false,
        intentRevision: Int = 0
    ) = GenerationFingerprints.RoundInput(
        messages = messages,
        advisorNote = note,
        kbName = kbName,
        onlyThisRound = onlyThisRound,
        intentRevision = intentRevision
    )
}
