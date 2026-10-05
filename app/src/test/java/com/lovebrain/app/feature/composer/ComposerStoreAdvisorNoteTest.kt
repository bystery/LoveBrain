package com.lovebrain.app.feature.composer

import com.lovebrain.app.domain.GenerationFingerprints
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.model.IntentConfig
import com.lovebrain.app.model.MemoryCorrection
import com.lovebrain.app.model.buildGenerationInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * 等 的持有者级合同：**输入模式、捕获角色、编辑对象是三件事，备注不是消息**。
 *
 * 这一族为什么值得单独一格：`ComposerStore.ideaHintOf()` 原来是
 * `已提交的 IDEA 消息 + 无条件拼上的 _draftText`，而 `_draftText` 里躺着的东西
 * 由"此刻在写她/我/补充哪一种内容"决定——它不判断这一点，于是
 * 1) 编辑她的一句话时，那句话被当成军师备注发出去（同一句话进 prompt 两次）；
 * 2) 选完《想法》之后新增/捕获的消息被标成上一个角色（用户 2026-10-03 原话第 6 条）；
 * 3) 备注正文混进真实聊天集合，进而有资格被写进 recent/topic/画像事实。
 * 每一格下面都写着"什么反例会让它红"，改这三条中任何一条判据前请先看那行。
 *
 * 全 JVM：`ComposerStore` 不碰 Android、不碰持久层（持久化是构造参数里那三个回调），
 * 所以这里不需要 Robolectric，也不需要 mockk。
 */
class ComposerStoreAdvisorNoteTest {

    /** 内容变化计数——"备注变了 ⇒ 旧结果该标 stale"这条跨块判据由调用方实现，这里只数它被想到几次 */
    private var contentChanges = 0

    private fun newStore(): ComposerStore = ComposerStore(
        scope = CoroutineScope(Job()),
        onContentChanged = { contentChanges++ }
    )

    private fun idea(id: String, text: String) = ChatMessage(id = id, role = ChatMessage.Role.IDEA, content = text)

    // ═══════════ 六步链路（第6节第4条 验收用的那一条，按持有者能观察到的四个面逐段钉） ═══════════

    /**
     * 先选她→添加她消息→选补充→输入提醒→提交→（编辑与再生成见后两格）。
     *
     * 反例（这一格红给它）：`addMessage` 把"补充"提交的内容当第三条消息追加进 `_messages`——
     * 那时 messages 会是 2 条、第二条角色是 IDEA，界面上就多出一块可拖可删的"我的想法"卡。
     */
    @Test
    fun supplementGoesIntoTheNoteAndNeverIntoTheRealChatSet() {
        val store = newStore()
        // ① 选她（旧通道：宿主投的是 SetCurrentRole）
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.HER))
        // ② 添加她消息
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "今天真的好累"))
        // ③ 选补充
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.IDEA))
        // ④ 输入提醒（还没提交）
        store.accept(ComposerStore.Intent.SetDraft("我其实知道她加班，别再问她忙不忙"))
        // ⑤ 提交
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.IDEA, "我其实知道她加班，别再问她忙不忙"))

        assertEquals("真实聊天集合只许那一条她的消息：" + store.messagesNow, 1, store.messagesNow.size)
        assertEquals(
            "列表里不许出现任何 IDEA 行（它不是一种消息角色）：" + store.messagesNow.map { it.role },
            ChatMessage.Role.HER, store.messagesNow.single().role
        )
        assertEquals("备注正文住在独立字段", "我其实知道她加班，别再问她忙不忙", store.noteTextNow)
        assertEquals("备注就是发出去的那一份", "我其实知道她加班，别再问她忙不忙", store.ideaHint())
        assertEquals("提交后输入框清空", "", store.draftTextNow)
        assertEquals("选补充之后自动捕获仍归她", ChatMessage.Role.HER, store.currentRoleNow)
        assertEquals("输入对象是补充", ComposerInputKind.SUPPLEMENT, store.inputKindNow)
        assertEquals("编辑对象不是任何一条消息", ComposerEditingTarget.None, store.editingTargetNow)
        assertEquals(-1, store.editingIndexNow)
    }

    /**
     * ⑥ 再选我→生成：四个面各归各位。
     *
     * 反例：切回她/我时把输入框里那半句备注留在原地，下一次算备注又把它拼一遍；
     * 或者反过来——切到《补充》时把上一段她/我的草稿吞进备注，于是"她说的话"变成
     * "我让军师注意：她说的话"。
     */
    @Test
    fun switchingBackToAMeRoleKeepsNoteAndCaptureSeparate() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "今天真的好累"))
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.IDEA))
        store.accept(ComposerStore.Intent.SubmitNote("先听我说完，不要只替她解释"))
        // ⑥ 选"我"
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.ME))

        assertEquals("备注不会因为切回我就变成一条消息", 1, store.messagesNow.size)
        assertEquals("备注还在", "先听我说完，不要只替她解释", store.noteTextNow)
        assertEquals("捕获角色跟着用户的手工选择走", ChatMessage.Role.ME, store.currentRoleNow)
        assertEquals("输入对象也切回我", ComposerInputKind.ME, store.inputKindNow)

        // 生成用的两个出口：真实对话 + 备注，互不掺
        val snapshot = store.messageSnapshot()
        assertEquals("快照只有那一条真实消息", 1, snapshot.size)
        assertEquals("备注进 directive 通道", "先听我说完，不要只替她解释", store.ideaHintOf(snapshot))
    }

    /** 自动捕获在《补充》亮着时也必须落进真实聊天（这就是原话第 6 条那个"标成上一个角色"） */
    @Test
    fun capturedMessageLandsAsRealChatWhileSupplementChipIsSelected() {
        val store = newStore()
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.ME))
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.IDEA))

        // `FloatingService.addClipIfNew` 的写法：捕获永远交 currentRole（∈ {HER, ME}）进来
        store.accept(ComposerStore.Intent.AddMessage(store.currentRoleNow, "刚捕获回来的一句"))

        assertEquals("捕获的那句是真实消息", 1, store.messagesNow.size)
        assertEquals("角色是捕获角色我，不是想法", ChatMessage.Role.ME, store.messagesNow.single().role)
        assertEquals("备注仍然空的", "", store.noteTextNow)
        assertEquals("捕获角色没被《补充》移动", ChatMessage.Role.ME, store.currentRoleNow)
        assertNotEquals(
            "捕获角色永远不许变成 IDEA",
            ChatMessage.Role.IDEA, store.currentRoleNow
        )
    }

    // ═══════════ 草稿归属：HER/ME 草稿绝不进 directive，补充草稿才进 ═══════════

    /**
     * 反例：`ideaHintOf()` 无条件 `+ _draftText`（修之前的原样）——
     * 用户在《她》里打了一句还没提交，那句就被当成"我让军师注意"发给军师了。
     */
    @Test
    fun herAndMeDraftsNeverBecomeAdvisorNote() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "在吗"))
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.HER))
        store.accept(ComposerStore.Intent.SetDraft("周末要见面吗"))

        val snapshot = store.messageSnapshot()
        assertEquals("她/我的未提交草稿不进备注", "", store.ideaHintOf(snapshot))
        assertFalse(
            "真实对话快照也不许混进未提交草稿（那条消息本身没被改）",
            snapshot.any { it.content == "周末要见面吗" }
        )

        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.SUPPLEMENT))
        store.accept(ComposerStore.Intent.SetDraft("别只替她解释"))
        assertTrue(
            "补充模式的未提交草稿必须进备注——否则用户写了等于没写",
            store.ideaHintOf(store.messageSnapshot()).contains("别只替她解释")
        )
    }

    /**
     * 编辑一条真实消息：草稿只应用到那一条**一次**，不再同步拼进备注（第6节第2条 点名的修法）。
     *
     * 反例：`messageSnapshot()` 应用完草稿，`ideaHintOf()` 又把同一条 `_draftText` 拼进备注
     * ⇒ 发给军师的 prompt 里这句话出现两次（一次在 <chat> 围栏里、一次在备注段里）。
     */
    @Test
    fun editingARealMessageAppliesOnceAndLeavesTheNoteAlone() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "周末要见面吗"))
        val herId = store.messagesNow.single().id
        store.accept(ComposerStore.Intent.SetEditingIndex(0))
        store.accept(ComposerStore.Intent.SetDraft("周末别见面了"))

        assertEquals("编辑对象按身份认，不是按下标猜", ComposerEditingTarget.RealMessage(herId), store.editingTargetNow)
        val snapshot = store.messageSnapshot()
        assertEquals("那句话被应用到正在编辑的那一条", "周末别见面了", snapshot.single().content)
        assertEquals("同一次编辑没有第二份落进备注", "", store.ideaHintOf(snapshot))

        // 提交编辑（宿主走 UpdateMessage）
        store.accept(ComposerStore.Intent.UpdateMessage(0, ChatMessage.Role.HER, "周末别见面了"))
        assertEquals("列表里就那一条", 1, store.messagesNow.size)
        assertEquals("正文改好了", "周末别见面了", store.messagesNow.single().content)
        assertEquals("角色还是她（编辑不改捕获身份）", ChatMessage.Role.HER, store.messagesNow.single().role)
        assertEquals("备注仍空", "", store.noteTextNow)
        assertEquals("提交后编辑位归零", ComposerEditingTarget.None, store.editingTargetNow)
    }

    /**
     * 点灰字编辑备注 → 提交：正文被**替换**，不是追加第二份。
     *
     * 反例：编辑走的是 `AddMessage` 那条"追加一行"的路，于是"别再问她忙不忙"
     * 和"别再问她忙不忙（改过的）"同时在 directive 里，模型读到两句互相的话。
     */
    @Test
    fun editingTheNoteReplacesItsBodyInsteadOfStackingASecondCopy() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "今天真的好累"))
        store.accept(ComposerStore.Intent.SubmitNote("别再问她忙不忙"))
        // 点灰字
        store.accept(ComposerStore.Intent.BeginNoteEdit)
        assertEquals("输入框接的是完整正文（展示侧省略过）", "别再问她忙不忙", store.draftTextNow)
        assertEquals("编辑对象是备注本体", ComposerEditingTarget.Note, store.editingTargetNow)

        store.accept(ComposerStore.Intent.SetDraft("别再问她忙不忙，她今天加班"))
        assertEquals(
            "编辑期间 directive 用的就是新正文，且旧的一份不在里面",
            "别再问她忙不忙，她今天加班",
            store.ideaHint()
        )

        store.accept(ComposerStore.Intent.SubmitNote("别再问她忙不忙，她今天加班"))
        assertEquals("提交后仍是那一份", "别再问她忙不忙，她今天加班", store.noteTextNow)
        assertEquals(
            "同一条备注不可能被注入两次",
            1, store.ideaHint().split("别再问她忙不忙").size - 1
        )
        assertEquals("编辑位交还", ComposerEditingTarget.None, store.editingTargetNow)
    }

    /** 第二次写一条新提醒是追加一行（"本轮一份备注，可多次编辑补充"） */
    @Test
    fun aSecondSupplementAppendsALineInsteadOfOverwritingTheFirst() {
        val store = newStore()
        store.accept(ComposerStore.Intent.SubmitNote("我现在不想约见面"))
        store.accept(ComposerStore.Intent.SubmitNote("也别提她前任"))
        assertEquals("我现在不想约见面\n也别提她前任", store.noteTextNow)
    }

    /**
     * 备注为空 = 什么都没有：不显示空标题、不进 directive。
     *
     * 反例：`buildAdvisorNoteBlock`/灰字行给空正文也画一段标题 ⇒ 屏上出现"我让军师注意："孤零零一行。
     */
    @Test
    fun noNoteMeansNoTextAnywhere() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "在吗"))
        assertEquals("", store.ideaHint())
        assertEquals("", store.ideaHintOf(store.messageSnapshot()))
    }

    // ═══════════ 指纹与冻结：备注变了就不许再命中旧结果 ═══════════

    /**
     * 反例：备注只改 `_replyNote` 而 `ideaHint()` 不读它 ⇒
     * `GenerationFingerprints.inputOf` 那一维纹丝不动，改过备注仍命中的回复。
     */
    @Test
    fun noteChangeMovesTheInputFingerprint() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "在吗"))
        val before = GenerationFingerprints.inputOf(store.messagesNow, store.ideaHint(), "kb", false, 0)

        store.accept(ComposerStore.Intent.SubmitNote("别只替她解释"))
        val after = GenerationFingerprints.inputOf(store.messagesNow, store.ideaHint(), "kb", false, 0)

        assertNotEquals("备注原文必须进输入指纹", before, after)
        assertEquals(
            "同一份输入重复算要给出同一个数（否则'输入没变就别重生成'永远命不中）",
            after, GenerationFingerprints.inputOf(store.messagesNow, store.ideaHint(), "kb", false, 0)
        )
        assertTrue("备注变化被算作内容变化（旧结果标 stale 的触发点）", contentChanges > 0)
    }

    /**
     * 生成启动时备注是**冻结的那一份字符串**，不读生成途中变化的实时输入。
     *
     * 反例：把 `store::ideaHint` 这类 getter 传进生成链——生成到一半用户改备注，
     * 发出去的 prompt 与事后记账的上下文就不是同一份。
     */
    @Test
    fun noteIsFrozenAtGenerationStartAndLiveEditsDoNotRetroactivelyChangeIt() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "在吗"))
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.ME, "在的"))
        store.accept(ComposerStore.Intent.SubmitNote("先听我说完"))

        val frozenMessages = store.messageSnapshot()
        val frozenHint = store.ideaHintOf(frozenMessages)
        val input = buildGenerationInput(
            requestId = "r1",
            messages = frozenMessages,
            userHint = frozenHint,
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
        // 生成途中用户继续改实时输入
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.SUPPLEMENT))
        store.accept(ComposerStore.Intent.SetDraft("生成途中改的"))

        assertEquals("冻结进去的 directive 不跟着实时输入漂", "先听我说完", input.replyDirective.text)
        assertEquals("冻结的对话也不跟着漂", 2, input.dialogue.size)
        assertTrue(
            "实时那一份确实变了（证明这一格测的不是两份同样的常量）",
            store.ideaHint().contains("生成途中改的")
        )
    }

    // ═══════════ 生命周期：跨页暂存、成功后清、失败保留、切对象不带 ═══════════

    /**
     * 本轮成功收尾（[ComposerStore.Intent.ConsumeMessages]）把备注一起清掉；
     * 失败的那一轮走不到这条路径，所以备注天然保留。
     *
     * 反例：清位在 `stopGeneration` 也做 ⇒ 用户点了停止，辛苦写的提醒没了。
     */
    @Test
    fun noteIsClearedOnRoundCommitAndKeptWhenTheRoundDidNotCommit() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "在吗"))
        val herId = store.messagesNow.single().id
        store.accept(ComposerStore.Intent.SubmitNote("别只替她解释"))

        // 失败/停止：没有任何 ConsumeMessages
        assertEquals("没收尾就留着", "别只替她解释", store.noteTextNow)

        store.accept(ComposerStore.Intent.ConsumeMessages(setOf(herId)))
        assertEquals("本轮消息被消耗掉", 0, store.messagesNow.size)
        assertEquals("本轮备注一起清位", "", store.noteTextNow)
        assertEquals("", store.ideaHint())
    }

    /**
     * 切对象：A 的备注不跟去 B，切回 A 又拿回来。
     *
     * 反例：备注是全局单槽 ⇒ 换到"她 B"那一块库生成时，directive 里还带着"她 A"的提醒，
     * 而用户在 B 上什么都没写。
     */
    @Test
    fun noteIsPerSubjectAndNeverTravelsAcrossSubjects() {
        val store = newStore()
        store.accept(ComposerStore.Intent.SetSubject("A"))
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "A 的那句"))
        store.accept(ComposerStore.Intent.SubmitNote("A 的提醒"))

        store.accept(ComposerStore.Intent.SetSubject("B"))
        assertEquals("B 对象看不到 A 的备注", "", store.ideaHint())
        assertEquals("真实消息不被切对象这条路径偷偷删掉（那是主线程的事）", 1, store.messagesNow.size)

        store.accept(ComposerStore.Intent.SetSubject("A"))
        assertEquals("切回 A 拿回自己那一份", "A 的提醒", store.ideaHint())
    }

    /** 普通切模式（面板档/输出档）不清备注——只有"本轮收尾"和"换对象"两类事件会动它 */
    @Test
    fun switchingModeOrOutputDoesNotTouchTheNote() {
        val store = newStore()
        store.accept(ComposerStore.Intent.SubmitNote("先听我说完"))
        store.accept(ComposerStore.Intent.SetPanelMode(1))
        store.accept(ComposerStore.Intent.SetOutputMode(1))
        store.accept(ComposerStore.Intent.SetPanelState(com.lovebrain.app.model.PanelState.KEYBOARD))
        assertEquals("跨回复/谈心切页与切模式都要还看得见", "先听我说完", store.noteTextNow)
    }

    /**
     * `SetPanelMode` 的**值域门**：面板只有两页（回复=0 / 谈心=1），这颗 reducer 不许把界外值
     * 写进 `panelMode`，也不许把界外值送进 `savePanelMode`。夹法与同文件
     * [ComposerStore.Intent.RestorePanelMode] 那道 `in 0..1` 门同一口径（0..1 这一对上下界）。
     *
     * 反例（reducer 不夹 ⇒ 这一格红，两处分别红在不同句上）：
     * `is Intent.SetPanelMode -> { _panelMode.value = intent.mode; savePanelMode(intent.mode) }`
     * 1. 投 2 ⇒ `panelMode.value == 2`：这颗是"哪一页在上面"的**唯一真 owner**，界外值让它指向
     *    一页不存在的第三页。宿主页画的时候先过 `panelModeToPage`（2 → 0），于是投值方以为在谈心、
     *    画面却是回复——正是 第12节第2条 要禁的"两本账对不上"；而任何一处把 mode 当页索引直用的话，
     *    `PanelPagePager` 的槽位循环 `0 until pageCount` 配不上 currentPage=2，两页被
     *    `(index - currentPage)` 一起推出屏外 ⇒ **面板画空页**。第一句断言当场红在这里。
     * 2. 那个 2 同时被 `savePanelMode` 写进盘 ⇒ 下次冷启动 `RestorePanelMode` 只收 0..1、把它丢掉，
     *    盘上"用户停在谈心"与内存里"回复那一面"静默分叉，没有任何一处报错。
     *    第二句断言（进盘的必须是夹过的那一颗）红在这里：只夹内存不夹盘等于把同一个 bug 挪到下次启动。
     * 3. 投 -1 与投 2 是同一颗门的两侧，所以两侧各钉一格：-1 → 0、2 → 1（落回最近的那一档）。
     *    只夹一侧（`coerceAtMost` / `coerceAtLeast` 单用）红在其中一半上。
     *
     * 界内值（0/1）必须**原样过**这道门——否则这一格会退化成"永远回回复"那种更坏的实现而没人拦。
     */
    @Test
    fun setPanelModeClampsIntoTheTwoPageDomainAndPersistsTheClampedValue() {
        val saved = mutableListOf<Int>()
        val store = ComposerStore(
            scope = CoroutineScope(Job()),
            savePanelMode = { saved.add(it) }
        )

        // 界内：0 ↔ 1 原样落，作为"夹子没把合法值一起削掉"的正控制
        store.accept(ComposerStore.Intent.SetPanelMode(1))
        assertEquals("合法值 1 原样落", 1, store.panelMode.value)
        store.accept(ComposerStore.Intent.SetPanelMode(0))
        assertEquals("合法值 0 原样落", 0, store.panelMode.value)

        // 界外：越上界落回 1、越下界落回 0
        store.accept(ComposerStore.Intent.SetPanelMode(2))
        assertEquals("投 2 落回 0..1（离界外最近的那一档 = 谈心）：" + store.panelMode.value, 1, store.panelMode.value)
        store.accept(ComposerStore.Intent.SetPanelMode(-1))
        assertEquals("投 -1 落回 0..1（回复）：" + store.panelMode.value, 0, store.panelMode.value)
        assertEquals(
            "界外值不许进内存，也不许进盘：进盘的必须是夹过的那一颗",
            listOf(1, 0, 1, 0), saved
        )

        // 另一道门（进程内恢复）同样不许界外值进内存——它是**丢弃**而不是夹，
        // 所以从 1 投界外值之后仍停在 1；这一句钉的是"两道门口径一致：界外值进不来"
        store.accept(ComposerStore.Intent.SetPanelMode(1))
        store.accept(ComposerStore.Intent.RestorePanelMode(5))
        assertEquals("RestorePanelMode 那道 in 0..1 门同样拦住界外值（丢弃，不改档）", 1, store.panelMode.value)
    }

    // ═══════════ 过渡期：残留的 Role.IDEA 行只折一次，且不进对话 ═══════════

    /**
     * 反例：同一条 IDEA 文本既留在对话快照（`buildGenerationInput` 会把它并进 directive）
     * 又被备注通道写一遍 ⇒ directive 里同一句话出现两次。
     *
     * 这一格还钉着第二条更安静的错：摘掉 IDEA 行与把它折进备注**必须是同一次读**。
     * 旧写法先 `messageSnapshot()` 摘行、再拿摘过的列表算备注，过渡期正文就在这一步
     * 静默丢掉（对话里没有、备注里也没有——用户写的提醒一个字都不发出去）。
     */
    @Test
    fun legacyIdeaRowIsFoldedIntoTheDirectiveExactlyOnceAndNeverIntoDialogue() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "在吗"))
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.ME, "在的"))

        // 旧设备/旧调用形状：列表里还躺着一条 IDEA 行（生产写入路径已经不再产生它）
        val legacy = store.messagesNow + idea("i1", "别太刻意")
        assertEquals("过渡期残留行折进备注正文", "别太刻意", store.ideaHintOf(legacy))

        // 冻结输入对：对话与备注出自同一次读，摘行不丢正文
        val frozen = store.freezeRoundInput(legacy)
        assertFalse("快照里没有 IDEA 行（真实聊天集合只留 HER/ME）", frozen.messages.any { it.role == ChatMessage.Role.IDEA })
        assertEquals("真实对话是那两条", 2, frozen.messages.size)
        assertEquals("同一份冻结里备注正文还在——摘行与折备注是同一次读", "别太刻意", frozen.note)

        val input = buildGenerationInput(
            requestId = "r2",
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
        assertEquals(
            "directive 里那句补充只出现一次",
            1, input.replyDirective.text.split("别太刻意").size - 1
        )
        assertEquals("对话只有她那两句", 2, input.dialogue.size)

        // 对照组：把 legacy 那份**原样**交进生成边界，就会看到两次——
        // 这一行不是在测生产路径，是在钉"为什么必须先过 freezeRoundInput/messageSnapshot"。
        val doubled = buildGenerationInput(
            requestId = "r3",
            messages = legacy,
            userHint = store.ideaHintOf(legacy),
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
        assertEquals(
            "绕过冻结对的写法会把同一句补充注入两遍（这条就是那一格的哨兵）",
            2, doubled.replyDirective.text.split("别太刻意").size - 1
        )
    }

    /**
     * 列表变动后编辑对象按身份重算：删掉别人不该把编辑位贴到顶上来的那条。
     *
     * 反例：`removeMessageById` 之后仍拿旧下标当编辑对象 ⇒ 下一次保存把 A 的话写进 B。
     */
    @Test
    fun editingTargetSurvivesListChangesByIdentity() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "第一条"))
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.ME, "第二条"))
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "第三条"))
        val thirdId = store.messagesNow[2].id

        store.accept(ComposerStore.Intent.SetEditingIndex(2))
        store.accept(ComposerStore.Intent.RemoveMessageById(store.messagesNow[0].id))

        assertEquals("编辑对象还是那一条（按 id 认）", ComposerEditingTarget.RealMessage(thirdId), store.editingTargetNow)
        assertEquals("镜像下标跟着重算", 1, store.editingIndexNow)

        store.accept(ComposerStore.Intent.RemoveMessageById(thirdId))
        assertEquals("正在编辑的那条没了 → 编辑位归零", ComposerEditingTarget.None, store.editingTargetNow)
        assertEquals(-1, store.editingIndexNow)
    }

    /**
     * 备注不参与聊天重排：拖消息动不了它，也不会把它拖进消息顺序里。
     *
     * 反例：把备注当 `_messages` 里的一行 ⇒ 第5节 那条"备注在列表尾部单独渲染，拖拽不把备注
     * 移动进真实聊天顺序"的禁令当场破。
     */
    @Test
    fun reorderingMessagesCannotMoveTheNoteIntoTheChatOrder() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "第一条"))
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.ME, "第二条"))
        store.accept(ComposerStore.Intent.SubmitNote("我让军师注意的东西"))

        store.accept(ComposerStore.Intent.ReorderMessages(0, 1))

        assertEquals("顺序换了还是那两条真实消息", listOf(ChatMessage.Role.ME, ChatMessage.Role.HER), store.messagesNow.map { it.role })
        assertEquals("备注没变成第三条消息", 2, store.messagesNow.size)
        assertEquals("备注正文没被重排动过", "我让军师注意的东西", store.noteTextNow)
    }

    /** 三颗 chip 与旧角色通道的对应关系（UI 退回旧通道时不许串位） */
    @Test
    fun legacyRoleChannelMapsToInputKindWithoutMovingCapture() {
        val store = newStore()
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.ME))
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.IDEA))
        assertEquals("补充", ComposerInputKind.SUPPLEMENT, store.inputKindNow)
        assertEquals("旧投影字段仍为 true（宿主还在读它）", true, store.ideaComposeModeNow)
        assertEquals("捕获角色没动", ChatMessage.Role.ME, store.currentRoleNow)

        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.HER))
        assertEquals(ComposerInputKind.HER, store.inputKindNow)
        assertFalse(store.ideaComposeModeNow)
        assertEquals(ChatMessage.Role.HER, store.currentRoleNow)
    }

    /** 进《补充》之前输入框里那段还没提交的 she/我 话术，先归位到真实聊天，绝不借备注通道 */
    @Test
    fun pendingRealDraftIsRehomedWhenEnteringSupplement() {
        val store = newStore()
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.HER))
        store.accept(ComposerStore.Intent.SetDraft("今天真的好累"))

        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.SUPPLEMENT))

        assertEquals("那句话进了真实聊天", 1, store.messagesNow.size)
        assertEquals(ChatMessage.Role.HER, store.messagesNow.single().role)
        assertEquals("备注是空的——她说的话不是给军师的补充", "", store.noteTextNow)
        assertEquals("输入框让位给备注", "", store.draftTextNow)
    }

    // ═══════════ 宿主真实点法：《补充》亮着时点气泡"编辑"（面板不会把 chip 切回去） ═══════════

    /**
     * 逐字复刻 `LoveBrainPanelScreen` 的两处调用形状：
     * `onEdit = { setEditingIndex(index); setDraft(msg.content) }`（**不动 chip**）、
     * `onAdd = { updateMessage(editingIndex, composeRole, text) }`（composeRole 在补充档就是 IDEA）。
     *
     * 反例（旧写法真会红的两条，都在修之前那条路径上）：
     * ① `updateMessage` 第一行就 `if (role == IDEA) commitSupplement()` ⇒ 用户改的是她的那句话，
     *    实际改掉的是备注，消息原文一个字没动；
     * ② `messageSnapshot` 拿 `_inputKind.realRole`（补充档给 null）当门槛而直接 return ⇒
     *    编辑中的那句既没进对话快照，`effectiveNoteText` 又只看 chip 把它并进备注 ⇒
     *    发给军师的 prompt 里"她说的话"变成"我让军师注意：她说的话"（双写：一次编辑两个落点）。
     */
    @Test
    fun editingAMessageWhileTheSupplementChipIsLitLandsOnlyOnThatMessage() {
        val store = newStore()
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "周末要见面吗"))
        // 用户选了《补充》（chip 亮补充、捕获角色仍归她）
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.IDEA))
        assertEquals(ComposerInputKind.SUPPLEMENT, store.inputKindNow)
        // 然后点她那条气泡的"编辑"——面板只做这两步，不会把 chip 切回《她》
        store.accept(ComposerStore.Intent.SetEditingIndex(0))
        store.accept(ComposerStore.Intent.SetDraft("周末别见面了"))

        val snapshot = store.messageSnapshot()
        assertEquals("编辑中的那句落回它自己那条消息", "周末别见面了", snapshot.single().content)
        assertEquals(
            "落回时用的是那条消息自己的身份 HER，不是备注、也不是 IDEA",
            ChatMessage.Role.HER, snapshot.single().role
        )
        assertEquals("同一次编辑不许同时进备注", "", store.ideaHintOf(snapshot))

        // 点＋：宿主交来的 role 就是 composeRole = IDEA（补充档亮着）
        store.accept(ComposerStore.Intent.UpdateMessage(0, ChatMessage.Role.IDEA, "周末别见面了"))
        assertEquals("列表仍只有那一条", 1, store.messagesNow.size)
        assertEquals("消息正文真的被改掉了", "周末别见面了", store.messagesNow.single().content)
        assertEquals("角色没被改成 IDEA（备注不是一种消息角色）", ChatMessage.Role.HER, store.messagesNow.single().role)
        assertEquals("备注没被这次编辑写过", "", store.noteTextNow)
        assertEquals("编辑位交还", ComposerEditingTarget.None, store.editingTargetNow)
        // 这一句是真有牙的：草稿没被提交消费掉的话，补充档下 `ideaHint()` 会把它并进备注，
        // 于是刚改完她的一句话，屏上灰字就变成了"我让军师注意：周末别见面了"
        assertEquals("提交把那句草稿消费掉了（补充档亮着也不许滑进备注）", "", store.draftTextNow)
        assertEquals("所以备注仍是空的", "", store.ideaHint())

        // 双写的最终哨兵：整条生成边界上那句话只许出现一次
        val input = buildGenerationInput(
            requestId = "r-edit",
            messages = store.messageSnapshot(),
            userHint = store.ideaHint(),
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
        assertEquals("那句话在对话里恰好一次", 1, input.dialogue.count { it.text == "周末别见面了" })
        assertFalse("directive 里不许出现这句话（它是她说的，不是给军师的补充）", input.replyDirective.text.contains("周末别见面了"))
    }

    /**
     * 只有备注、没有真实聊天：备注仍是备注，一条消息都不许多出来。
     *
     * 反例：`commitSupplement` 顺手 `_messages += ChatMessage(ME, text)` ⇒
     * `TopicRecorder` 那道 `role == HER || role == ME` 的过滤（domain/TopicRecorder.kt:116）
     * 就会把"别太刻意"当成用户真说过的话写进 recent/话题，画像事实也从此有资格读它。
     */
    @Test
    fun aNoteAloneNeverBecomesAMessageTheRecorderWouldTreatAsRealChat() {
        val store = newStore()
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.IDEA))
        store.accept(ComposerStore.Intent.SubmitNote("别太刻意"))

        assertEquals("真实聊天集合仍是空的", emptyList<ChatMessage>(), store.messagesNow)
        assertEquals("快照也空（没有 HER/ME 就不该有对话行）", 0, store.messageSnapshot().size)
        assertEquals("备注正文在", "别太刻意", store.ideaHint())

        val input = buildGenerationInput(
            requestId = "r-note-only",
            messages = store.messageSnapshot(),
            userHint = store.ideaHint(),
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
        assertEquals("对话一条都没有（备注没被塞成 ME 消息）", 0, input.dialogue.size)
        assertEquals("备注只走 directive 那一条通道", "别太刻意", input.replyDirective.text)
        // 哨兵：按"HER/ME 过滤"跑一遍真实记录链的那道判据，确认它拦不住的是"被写成消息的备注"
        assertEquals(
            "若哪天备注被当成 ME 消息提交，这一格立刻红",
            0, store.messagesNow.count { it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME }
        )
    }

    /**
     * 同一句提醒被"已提交 + 输入框里再打一遍"撞上时只留一份；不同句仍是两行。
     *
     * 反例：`mergeNote` 写成 `listOf(first, second).joinToString("\n")` 不做按行去重 ⇒
     * directive 里"别太刻意"出现两遍，模型读到的是用户强调过两次。
     * 后半段的对照组（不同文本必须给两行）是这一格的哨兵：去重判据松成"存在即可"就红。
     */
    @Test
    fun theSameReminderIsNotInjectedTwiceButDifferentOnesStillStack() {
        val store = newStore()
        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.IDEA))
        store.accept(ComposerStore.Intent.SubmitNote("别太刻意"))
        // 用户没点灰字，而是直接又打了一遍同一句（还没提交）
        store.accept(ComposerStore.Intent.SetDraft("别太刻意"))

        assertEquals("同一句补充只有一份", "别太刻意", store.ideaHint())
        assertEquals(
            "directive 里那句话出现两遍",
            1, store.ideaHint().split("别太刻意").size - 1
        )

        // 对照组：换一句就必须是两行（"本轮一份备注，可多次编辑补充"）
        store.accept(ComposerStore.Intent.SetDraft("也别提她前任"))
        assertEquals("不同提醒各占一行、顺序在先的在前", "别太刻意\n也别提她前任", store.ideaHint())
    }
}
