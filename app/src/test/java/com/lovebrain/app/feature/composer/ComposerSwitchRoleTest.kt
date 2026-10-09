package com.lovebrain.app.feature.composer

import com.lovebrain.app.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 横滑"向内"那一侧的持有者级合同（指导书 §8.1 方向映射的下半场）。
 *
 * UI 那一侧只说"这一条要换边"（交的是稳定 id，见 `MessageRowSwipeDeleteTest`）；
 * 这里判的是**换边到底改了什么、没改什么**：
 * · 只改这一条的角色，id / 正文 / 时间戳 / 列表顺序原样保留（同一条数据换了说话人，
 *   不是删一条再建一条——§8.1 那句"不能先消失再新建"在数据这一面的读数）；
 * · §8.1 点名的"不能连带改变"三件事一个字都不动：输入框当前角色（inputKind）、
 *   捕获默认角色（currentRole）、正在编辑的对象（editingTarget / editingIndex / 草稿）；
 * · 角色转换不弹确认框 ⇒ 这里没有"待确认"的中间状态，投一次就落一次；
 * · 一次意图只落一次动作，且必须 mark stale（`onContentChanged` 与状态写入同帧，
 *   漏这一处就得到一个静默的旧回复——说话人变了，拼给模型的 prompt 也就变了）。
 *
 * **计数器口径（钉 stale 的那两格读的是差值，不是累计值）**
 *
 * [contentChanges] 接的是 `ComposerStore` 的**全部内容变更钩子**（构造参数 `onContentChanged`，
 * `ComposerStore.kt:101`），换边只是它众多来源之一：草稿 `:418`、新增 `:446`、改写 `:479`/`:492`、
 * 删除 `:502`/`:519`、换边 `:553`、重排 `:565`、输入对象折备注 `:623`、点灰字编辑备注 `:663`、
 * 提交补充 `:732`、清备注 `:744`、换对象 `:770` 都往同一格加一（`consumeMessages` `:568` 刻意不加）。
 * 所以「这次换边叫了几次」**只能读换边前后的差**；直接拿累计值去比 1/2，量到的是
 * 「播种 + 换边」的总和，而不是意图次数。本文件下面两格
 * （[switchingRoleLeavesComposerAxesCaptureRoleAndTheEditedMessageUntouched]、
 * [anUnknownIdIsANoOpInsteadOfAPositionFallback]）从一开始就是差值写法，
 * 上面前两格原先写成累计写法，于是得到
 * `expected:<1> but was:<4>`（4 = [seeded] 里三句 `AddMessage` 各叫一次 `:446`×3 + 换边 `:553`×1）
 * 与 `expected:<2> but was:<5>`（5 = 同样的 3 + 两次换边各 1）——多出来的那几笔全在播种那一头，
 * 实现这一侧一次手势只叫一次（`switchMessageRoleById` `ComposerStore.kt:535-554` 里
 * 只有 `:553` 这一处 `onContentChanged()`，无循环、不借道 `updateMessage`/`applyEditedList`）。
 *
 * 全 JVM：`ComposerStore` 不碰 Android、不碰持久层，所以不需要 Robolectric 也不需要 mockk
 * （与 `ComposerStoreAdvisorNoteTest` 同一口径）。
 */
class ComposerSwitchRoleTest {

    /**
     * 自本测实例建立以来的**累计**读数（JUnit 每个 @Test 新实例，所以测与测之间不串）。
     * 见类 KDoc 的「计数器口径」：判一次手势叫了几次，用 `contentChanges - 手势前的快照`。
     */
    private var contentChanges = 0

    private fun newStore(): ComposerStore = ComposerStore(
        scope = CoroutineScope(Job()),
        onContentChanged = { contentChanges++ }
    )

    private fun switch(store: ComposerStore, id: String) =
        store.accept(ComposerStore.Intent.SwitchMessageRole(id))

    /** 三行：她 / 我 / 她（中间那一条是绝大多数格子的操作对象） */
    private fun seeded(store: ComposerStore): List<ChatMessage> {
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "在吗"))
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.ME, "在的"))
        store.accept(ComposerStore.Intent.AddMessage(ChatMessage.Role.HER, "那今天几点下班"))
        return store.messagesNow
    }

    /**
     * 只换这一条的说话人，其余四样（id、正文、时间戳、顺序）逐位对齐。
     *
     * 反例：走 `UpdateMessage` 那条旧通道（它按编辑对象落点、还会清草稿与编辑位）⇒ 后面那两格红；
     * 反例：新建一颗 `ChatMessage(role = …, content = …)` 而不 copy ⇒ 第一句红（id 变了，
     *   UI 那侧的条目 key 跟着变，气泡就是"先消失再新建"那个形状）；
     * 反例：默认 timestamp 重新取一次 ⇒ 第三句红（必要元数据丢了）；
     * 反例：把这条摘掉再 append 到尾部 ⇒ 第四句红（顺序动了，prompt 里的对话顺序就不是用户看到的）；
     * 反例：改完不 `onContentChanged` ⇒ 最后一句红（差值 0，旧结果被当成仍然有效）；
     * 反例：换边顺手多叫了几次（例如借道 `updateMessage` 落点、再补一次清草稿/清编辑位各叫一次）
     *   ⇒ 最后一句红（差值 ≥2，用户看到结果被反复清掉再重发）。
     */
    @Test
    fun switchingRoleFlipsOnlyThatRowAndKeepsIdentityContentOrderAndMetadata() {
        val store = newStore()
        val before = seeded(store)
        val target = before[1]
        // 口径：先取基线。播种那三句 AddMessage 已经各叫过一次 onContentChanged（ComposerStore.kt:446），
        // 它们不是这一颗意图的次数；这一格钉的是「换边这一步的增量」。
        val changesBeforeSwitch = contentChanges

        switch(store, target.id)

        val after = store.messagesNow
        assertEquals("换边不增不减条目：" + after.size, before.size, after.size)
        assertEquals("id 序列必须逐位相同（同一条数据，不是删了重建）：" + after.map { it.id },
            before.map { it.id }, after.map { it.id })
        assertEquals("正文一个字都不动：" + after.map { it.content },
            before.map { it.content }, after.map { it.content })
        assertEquals("时间戳这类必要元数据原样保留：" + after.map { it.timestamp },
            before.map { it.timestamp }, after.map { it.timestamp })
        assertEquals("只有那一条换了边：" + after.map { it.role }, ChatMessage.Role.HER, after[1].role)
        assertEquals("整列角色逐位对齐（换错行当场红）：" + after.map { it.role },
            listOf(ChatMessage.Role.HER, ChatMessage.Role.HER, ChatMessage.Role.HER),
            after.map { it.role })
        assertEquals("它前面那条仍是她：" + after[0].role, ChatMessage.Role.HER, after[0].role)
        assertEquals("它后面那条仍是她：" + after[2].role, ChatMessage.Role.HER, after[2].role)
        // 这条断言钉的是「这一次换边手势，恰好让旧结果作废一次」——不多（反向证人：多发即差值 ≥2 就红）、
        // 不少（漏标即差值 0 也红）。
        // 原先写成 `assertEquals(1, contentChanges)` 是量具口径错，不是实现多发：contentChanges 是
        // store 全部内容变更钩子的**累计**计数（ComposerStore.kt:101；草稿 :418、新增 :446、
        // 改写 :479/:492、删除 :502/:519、换边 :553、重排 :565、输入对象折备注 :623 都进同一格），
        // 期望值 1 却拿累计值去比，于是把 seeded() 那三次 AddMessage（:446×3）也算进这一格，
        // 读出 4 = 3 次播种 + 1 次换边。真多发的形状由差值继续当场红。
        assertEquals(
            "内容变了就得让旧结果判过时（这一次意图只叫一次，读换边前后的差）",
            changesBeforeSwitch + 1, contentChanges
        )
    }

    /**
     * 连投两次回到原样，且两次都算数（不因为"看着像同一个 id"就吞掉第二次）。
     *
     * 反例：实现写成"一律置 ME"或"一律置 HER"⇒ 第二句红；
     * 反例：把这颗意图做成幂等的"打开确认框"式一次性动作 ⇒ 第一句红（§8.1：角色转换不弹确认框）。
     *
     * 两次手势各量各的差值：第二次被吞 ⇒ 第二组差值 0 红；任一次多叫 ⇒ 对应那组差值 ≥2 红。
     */
    @Test
    fun switchingTwiceGoesOverAndBack() {
        val store = newStore()
        val target = seeded(store)[0]   // 她
        val changesAtSeed = contentChanges   // 基线：播种那三次 AddMessage 不记在换边头上

        switch(store, target.id)
        val changesAfterFirst = contentChanges
        assertEquals("她 → 我", ChatMessage.Role.ME, store.messagesNow[0].role)
        assertEquals("第一次换边各标一次 stale（差值恰好 1）",
            changesAtSeed + 1, changesAfterFirst)

        switch(store, target.id)
        assertEquals("再投一次 → 回到她", ChatMessage.Role.HER, store.messagesNow[0].role)
        // 这条钉「两次手势 = 两次作废，一次不多一次不少」。原期望值 2 也是累计口径错：
        // 读出 5 = 播种 :446×3 + 两次换边各 :553×1；差值写法把播种那三笔隔离在基线里。
        assertEquals("第二次换边也各标了一次 stale（差值再 +1，合计两次）",
            changesAfterFirst + 1, contentChanges)
    }

    /**
     * 换边不许顺带动输入行那三轴——§8.1 原话："不能连带改变输入框当前角色、捕获默认角色
     * 或正在编辑的其他消息"。
     *
     * 这一格里三轴都故意摆在**与目标那条相反**的位置上（输入对象=补充、捕获=她、
     * 正在编辑的是**另一条**消息并且带着没提交的草稿），于是任何"借道 updateMessage /
     * setCurrentRole / applyInputKind"的写法都会在这一格红：
     * · 借 `UpdateMessage` 落点 ⇒ 草稿被消费清空、编辑位归 None ⇒ 两句红；
     * · 顺手把捕获角色也换成新的那一方 ⇒ 捕获那句红（用户只是改了历史的一条，
     *   下一条自动捕获的消息却被标成了另一方，正是 2026-10-03 原话第 6 条那个形状）；
     * · 顺手把 inputKind 切回她/我 ⇒ 输入对象那句红（《补充》亮着的时候换边，
     *   输入框里那段备注草稿会被当成消息话术）。
     */
    @Test
    fun switchingRoleLeavesComposerAxesCaptureRoleAndTheEditedMessageUntouched() {
        val store = newStore()
        val before = seeded(store)
        val swipedRow = before[0]           // 被滑的是第一条（她）
        val editedRow = before[2]           // 正在编辑的是**另一条**

        store.accept(ComposerStore.Intent.SetCurrentRole(ChatMessage.Role.ME))
        store.accept(ComposerStore.Intent.SetInputKind(ComposerInputKind.SUPPLEMENT))
        store.accept(ComposerStore.Intent.SetEditingIndex(2))
        store.accept(ComposerStore.Intent.SetDraft("这句还没提交"))
        val contentChangesBefore = contentChanges

        switch(store, swipedRow.id)

        assertEquals("捕获默认角色不许被换边带着走", ChatMessage.Role.ME, store.currentRoleNow)
        assertEquals("输入框当前对象（补充）不许被换边带着走",
            ComposerInputKind.SUPPLEMENT, store.inputKindNow)
        assertEquals("编辑对象仍是那一条，没被挪也没被清",
            ComposerEditingTarget.RealMessage(editedRow.id), store.editingTargetNow)
        assertEquals("编辑位仍是那一条的位置（列表长度没变）", 2, store.editingIndexNow)
        assertEquals("正在编辑那条的草稿一个字都不掉", "这句还没提交", store.draftTextNow)
        assertEquals("被编辑的那条正文没被换边走形：" + store.messagesNow[2],
            ChatMessage.Role.HER, store.messagesNow[2].role)
        assertEquals("换边只算一次内容变化（不多不少）",
            contentChangesBefore + 1, contentChanges)
    }

    /**
     * 编辑中的那一条**自己**被换边：身份仍成立、草稿仍在。
     *
     * 这一格里被编辑的就是右列那一条（`me`），换边之后它应当变成左列（`her`），
     * 而编辑对象仍按 id 认得住它。
     * 反例：换边之后把编辑对象清成 None（"这条被动过就退出编辑"）⇒ 第二句红，
     * 用户看到的是"我改个说话人，输入框里正在打的那句话没了"。
     */
    @Test
    fun switchingTheEditingRowsOwnRoleKeepsTheEditingTargetAlive() {
        val store = newStore()
        val before = seeded(store)
        val editedRow = before[1]           // 正在编辑的就是要换边那一条
        store.accept(ComposerStore.Intent.SetEditingIndex(1))
        store.accept(ComposerStore.Intent.SetDraft("这句还没提交的话"))

        switch(store, editedRow.id)

        assertEquals("我 → 她（换的就是这一条）", ChatMessage.Role.HER, store.messagesNow[1].role)
        assertEquals("编辑对象认的还是同一条（按 id，不按位置猜）",
            ComposerEditingTarget.RealMessage(editedRow.id), store.editingTargetNow)
        assertEquals("草稿仍在", "这句还没提交的话", store.draftTextNow)
    }

    /**
     * 认不到这颗 id（这一轮已被消耗、或已被删除）⇒ 什么都不做，**绝不按位置猜一条顶上**。
     *
     * 反例：把落点退回下标（`indexOf` 找不到就用 0）⇒ 第一句红（用户滑的是这条、
     * 改掉的是另一条，正是本轮反复点名的"用上一个位置猜对象"）；
     * 反例：找不到也标一次 stale ⇒ 第二句红。
     */
    @Test
    fun anUnknownIdIsANoOpInsteadOfAPositionFallback() {
        val store = newStore()
        val before = seeded(store)
        val changesBefore = contentChanges

        switch(store, "never-existed")

        assertEquals("列表一字不动：" + store.messagesNow.map { it.role },
            before.map { it.role }, store.messagesNow.map { it.role })
        assertEquals("没改任何东西就不该标 stale", changesBefore, contentChanges)
    }
}
