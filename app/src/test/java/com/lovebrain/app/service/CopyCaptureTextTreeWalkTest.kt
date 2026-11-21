package com.lovebrain.app.service

import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 整树取文的共享遍历（审计项 1）：长按取"最长文本"与菜单取"全部文本"共用
 * [walkNodeTreeDepthFirst]，预算与节点账只能有一份。
 *
 * 为什么用假节点：`AccessibilityNodeInfo` 在 JVM/Robolectric 里造不出可 `getChild` 的真树，
 * 所以遍历是泛型壳。这里钉三件事：
 * 1. 节点数 / 深度 / 毫秒三道闸与旧的两棵递归同口径（4 层、256 节点、100ms）；
 * 2. 入参 root 永不回收，`getChild` 取到的子节点在**其子树访问完之后**恰好还一次；
 * 3. 一次遍历可同时喂两种输出（最长文本 + 全量拼接），不必为形状不同再扫第二遍。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = UiProbeApplication::class)
class CopyCaptureTextTreeWalkTest {

    private class FakeNode(val text: String? = null, val description: String? = null) {
        val children = mutableListOf<FakeNode>()
        fun add(vararg nodes: FakeNode): FakeNode = apply { children.addAll(nodes) }
    }

    private class Account {
        val visited = mutableListOf<FakeNode>()
        val obtained = mutableListOf<FakeNode>()
        val released = mutableListOf<FakeNode>()
        val trace = mutableListOf<String>()
    }

    private fun walk(
        root: FakeNode,
        maxDepth: Int = 4,
        maxNodes: Int = 256,
        budgetMs: Long = 100L,
        now: () -> Long = { 0L },
        account: Account = Account(),
        visit: (FakeNode) -> Unit = {}
    ): Account {
        walkNodeTreeDepthFirst(
            root = root,
            maxDepth = maxDepth,
            maxNodes = maxNodes,
            budgetMs = budgetMs,
            childCount = { it.children.size },
            childAt = { node, index ->
                node.children.getOrNull(index)?.also { account.obtained.add(it) }
            },
            visit = { node ->
                account.visited.add(node)
                account.trace.add("visit ${node.text ?: "?"}")
                visit(node)
            },
            release = { node ->
                account.released.add(node)
                account.trace.add("release ${node.text ?: "?"}")
            },
            now = now
        )
        return account
    }

    private fun assertNodeAccount(root: FakeNode, account: Account) {
        assertEquals("取到的节点必须恰好还一次", account.obtained.size, account.released.size)
        assertTrue(
            "每个取到的节点都还一次",
            account.obtained.all { node -> account.released.count { it === node } == 1 }
        )
        assertTrue("入参 root 归系统，绝不回收", account.released.none { it === root })
    }

    @Test
    fun `node budget caps visits and still recycles every fetched child`() {
        val root = FakeNode("root")
        repeat(200) { root.add(FakeNode("c$it")) }

        val account = walk(root, maxNodes = 120)

        assertEquals("节点预算只限制访问数：root + 119 个子节点", 120, account.visited.size)
        // 第 120 个孩子的 getChild 在预算耗尽后被跳过，不会多取
        assertEquals(119, account.obtained.size)
        assertNodeAccount(root, account)
    }

    @Test
    fun `depth budget counts the root as depth zero`() {
        var leaf = FakeNode("d6")
        repeat(5) { depth -> leaf = FakeNode("d$depth").add(leaf) }

        val account = walk(leaf, maxDepth = 4)

        assertEquals("4 层预算 = root(d0) … d4", 5, account.visited.size)
        assertNodeAccount(leaf, account)
    }

    @Test
    fun `deadline stops descending but still recycles the fetched child`() {
        val root = FakeNode("root").add(FakeNode("a"), FakeNode("b"))
        var clockCalls = 0
        // 前三次（预算初始化 / root 入口 / 第一个孩子入口）都在预算内，之后过期
        val now = { if (clockCalls++ < 3) 0L else 200L }

        val account = walk(root, budgetMs = 100L, now = now)

        assertEquals("过期后不再访问子节点", 1, account.visited.size)
        assertEquals("已经取到手的那个孩子仍要归还", 1, account.obtained.size)
        assertNodeAccount(root, account)
    }

    @Test
    fun `children are released after their subtree in post order`() {
        val grandChild = FakeNode("g")
        val child = FakeNode("c").add(grandChild)
        val root = FakeNode("r").add(child)

        val account = walk(root)

        assertEquals(
            listOf("visit r", "visit c", "visit g", "release g", "release c"),
            account.trace
        )
        assertNodeAccount(root, account)
    }

    @Test
    fun `one walk feeds both longest-text and all-text outputs`() {
        val longText = FakeNode("这是一条完整的消息正文")
        val withDescription = FakeNode(text = "复制", description = "转发")
        val root = FakeNode("消息").add(longText, withDescription)

        var longest: String? = null
        val joined = StringBuilder()
        val account = walk(root) { node ->
            val text = node.text?.trim()
            val best = longest
            if (!text.isNullOrEmpty() && (best == null || text.length > best.length)) longest = text
            if (!text.isNullOrEmpty()) joined.append(text).append('|')
            val description = node.description?.trim()
            if (!description.isNullOrEmpty()) joined.append(description).append('|')
        }

        assertEquals("最长文本那一路", longText.text, longest)
        assertTrue("全量拼接那一路含菜单关键词", joined.contains("复制|") && joined.contains("转发|"))
        assertEquals("两种输出共用同一次访问，节点数不变", 3, account.visited.size)
        assertNodeAccount(root, account)
    }

    @Test
    fun `both collectors go through the one shared walk`() {
        val file = File("src/main/java/com/lovebrain/app/service/CopyCaptureService.kt")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/service/CopyCaptureService.kt")
        assertTrue("尺接错了文件，找不到 $file", file.isFile)
        val src = SourceScan.maskComments(file.readText(Charsets.UTF_8))

        // 声明 1 处 + 两个取文适配器各 1 处 = 3（多选点选扫描已整删）；再长出新递归实现才是违规
        assertEquals(
            "遍历只许这一份实现（声明 + 两个取文适配器）",
            3,
            Regex("""\bwalkNodeTreeDepthFirst\(""").findAll(src).count()
        )
        assertFalse(
            "旧的 Bounded 私有递归不许回来",
            src.contains("collectTextFromChildrenBounded") || src.contains("collectAllTextFromTreeBounded")
        )
    }
}
