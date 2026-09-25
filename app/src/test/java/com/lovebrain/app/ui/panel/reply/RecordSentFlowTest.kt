package com.lovebrain.app.ui.panel.reply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `RecordSentFlow` 的不变量——**对着持有者本身量**，不经过界面。
 *
 * 为什么不写成 UI 格：这一屏承诺的是"失败时浮层留着、用户已经敲进去的正文还在"。
 * 那句话以前只存在于 `LoveBrainPanelScreen` 中段那几行 `var` 的写法里，
 * 要验它得把整块面板（连 ViewModel 与悬浮窗环境）接进仪器——本机接不进去。
 * 收进持有者之后，它就是四个可以直接调用的方法，于是这条承诺第一次可测。
 *
 * 另有两格是"死路"回归：上一格量到的两条缺陷（`NO_KB` 没人接、重复失败不再发射）
 * 后果都是 `saving` 解不开，而浮层的取消与遮罩都挂在 `enabled = !saving` 上。
 * 那两格守的是**持有者这一侧不能再制造同样的死路**。
 */
class RecordSentFlowTest {

    @Test
    fun `open starts from a clean slate`() {
        val flow = RecordSentFlow()
        assertFalse("默认不该开着", flow.isOpen)
        flow.open(schemeIdentityKey = "STYLE:B/DIRECTION:F", prefill = "候选正文")
        assertTrue(flow.isOpen)
        assertTrue("open 之后绝不该已经是保存中", !flow.saving)
        assertEquals("候选正文", flow.draft)
        assertEquals("STYLE:B/DIRECTION:F", flow.schemeKey)
    }

    /**
     * 上一轮留下的 `saving = true` 不能把新一轮一出生就锁死。
     *
     * ⚠ 第一版这一格是 `open → beginSaving → saveRejected → cancel → open`，
     * 探针 Z4（把 `open` 里那句 `saving = false` 删掉）**照样全绿**——
     * 因为 `saveRejected` 早就把锁放开了，走到第二次 `open` 时 `saving` 本来就是 false，
     * 那一格从头到尾没构造出它声称在防的那个状态。
     *
     * 现在直接构造它：`open → beginSaving → 再 open`。
     * 界面上这一跳今天走不到（浮层的遮罩把结果行盖住了），所以它守的是
     * **持有者自己的公开 API 不许制造死路**：万一 VM 那次根本没吐结果
     * （上一格量到的 `NO_KB` 与重复失败就是这种），重新打开必须是干净的一轮，
     * 而不是一出生就 `enabled = !saving` 全灰。
     */
    @Test
    fun `a failed attempt cannot leak into the next one`() {
        val flow = RecordSentFlow()
        flow.open()
        flow.beginSaving()
        assertTrue("前提：这一轮确实停在保存中", flow.saving)

        flow.open(prefill = "新一轮")
        assertTrue("重新打开必须不再是保存中", !flow.saving)
        assertTrue("重新打开该是开着的", flow.isOpen)
        assertEquals("重新打开要带新草稿", "新一轮", flow.draft)

        // 上一轮失败之后重新打开，同样不能残留
        val other = RecordSentFlow()
        other.open()
        other.beginSaving()
        other.saveRejected()
        other.cancel()
        other.open()
        assertTrue("失败→取消→再开 也不能残留保存中", !other.saving)
        assertEquals("取消之后重新打开是空白草稿", "", other.draft)
    }

    /** 承诺的那半句：被拒之后草稿还在，而且锁放开了（用户能改、能重试、能取消） */
    @Test
    fun `rejection keeps the draft and releases the lock`() {
        val flow = RecordSentFlow()
        flow.open()
        flow.editDraft("  我改过的话  ")
        flow.beginSaving()

        flow.saveRejected()
        assertTrue("浮层要留着", flow.isOpen)
        assertEquals("用户敲进去的正文一个字都不许丢", "  我改过的话  ", flow.draft)
        assertFalse("锁必须放开，否则取消与遮罩都点不动，用户会被关在里面", flow.saving)
    }

    /** 记成了才关，并且清空——别把这一轮的正文留给下一轮 */
    @Test
    fun `recording closes and clears`() {
        val flow = RecordSentFlow()
        flow.open(schemeIdentityKey = "k", prefill = "话")
        flow.beginSaving()
        flow.recorded()

        assertFalse(flow.isOpen)
        assertFalse(flow.saving)
        assertEquals("", flow.draft)
        assertNull(flow.schemeKey)
    }

    /** 保存中不许从外面关掉——与浮层那个 `dismissable = !saving` 同一个口径，两处都得守 */
    @Test
    fun `cancel is inert while saving`() {
        val flow = RecordSentFlow()
        flow.open(prefill = "正文")
        flow.beginSaving()

        flow.cancel()
        assertTrue("保存中关不掉：那样『到底写进去没有』就成了猜不透的问题", flow.isOpen)
        assertEquals("正文", flow.draft)

        flow.saveRejected()
        flow.cancel()
        assertFalse("失败之后取消就该生效", flow.isOpen)
        assertEquals("关掉要清草稿", "", flow.draft)
    }

    /** 保存中不接受编辑（输入框那边 `enabled = !saving`，状态这一侧不能留后门） */
    @Test
    fun `the draft cannot be edited while saving`() {
        val flow = RecordSentFlow()
        flow.open(prefill = "原话")
        flow.beginSaving()
        flow.editDraft("偷偷改掉")
        assertEquals("保存中的编辑请求要被忽略", "原话", flow.draft)
        flow.saveRejected()
        flow.editDraft("现在可以改了")
        assertEquals("现在可以改了", flow.draft)
    }

    /**
     * 反空跑：上面那几格断言的多是"某个值**没**变"。
     * 万一哪天 `saving` 变成常量，`assertFalse`/`assertTrue` 会全绿，
     * 所以要有一格把"真的会动"跑一遍——同一条路径上把每一步的观察值都记下来比对。
     *
     * ⚠ 第一版这里写的是"该有 5 种不同组合"，那是我**数错**的：
     * `beginSaving` 与随后被忽略的 `editDraft` 本来就是同一个状态（正是要断言它没变），
     * 所以六个快照去重后是 4 种，不是 5 种。改成把 4 个状态**逐个列出来**比，
     * 而不是比一个我算出来的计数——列出来之后谁改错了语义都会对上号。
     */
    @Test
    fun `the observed flags really move`() {
        val flow = RecordSentFlow()
        val seen = mutableListOf<Triple<Boolean, Boolean, String>>()
        fun snap() { seen += Triple(flow.isOpen, flow.saving, flow.draft) }

        snap()                          // 1 初始
        flow.open(prefill = "a")
        snap()                          // 2 开着、没在保存、草稿 a
        flow.beginSaving()
        snap()                          // 3 保存中
        flow.editDraft("ignored")
        snap()                          // 4 与 3 相同：保存中的编辑被吞掉
        flow.saveRejected()
        snap()                          // 5 锁放开、浮层与草稿都留着
        flow.editDraft("b")
        snap()                          // 6 现在能改了

        assertEquals(
            "六个快照逐个对",
            listOf(
                Triple(false, false, ""),
                Triple(true, false, "a"),
                Triple(true, true, "a"),
                Triple(true, true, "a"),
                Triple(true, false, "a"),
                Triple(true, false, "b")
            ),
            seen
        )
        assertTrue("状态确实动过（不是一串常量）", seen.distinct().size >= 4)
    }
}
