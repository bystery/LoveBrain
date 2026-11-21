package com.lovebrain.app.service

import com.lovebrain.app.core.testing.UiProbeApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 节点访问账（审计项 1）：`hasCredentialNode` 这条路径上，`getChild` 取到的每个节点都必须归还。
 *
 * 为什么用假节点：`AccessibilityNodeInfo` 在 JVM/Robolectric 里造不出可 `getChild` 的真树，
 * 所以遍历被抽成 [scanNodesBounded] 泛型壳，用假节点钉三件事：
 * 1. 取了多少就还多少（每个恰好还一次），且**入参 root 永不回收**（它归系统/调用方）；
 * 2. 命中提前结束时，已经取进队列但还没访问的节点同样要还；
 * 3. 父节点在还之前必须先取完子节点——否则真机上等于对已释放连接调 `getChild`。
 *
 * 回退成"只回收当层子节点"或"遍历中顺手 recycle 父节点"都会在这里红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = UiProbeApplication::class)
class CopyCaptureNodeScanTest {

    private class FakeNode(val credential: Boolean = false) {
        val children = mutableListOf<FakeNode>()
        var recycled = 0

        fun add(vararg nodes: FakeNode): FakeNode = apply { children.addAll(nodes) }
    }

    private class Accounting {
        val obtained = mutableListOf<FakeNode>()
        val released = mutableListOf<FakeNode>()
        var parentReadAfterRelease = 0
        var visits = 0
    }

    private fun scan(root: FakeNode, maxNodes: Int = 120, account: Accounting): Boolean =
        scanNodesBounded(
            root = root,
            maxNodes = maxNodes,
            childCount = { it.children.size },
            childAt = { node, index ->
                if (node.recycled > 0) account.parentReadAfterRelease++
                node.children.getOrNull(index)?.also { account.obtained.add(it) }
            },
            visit = { node ->
                account.visits++
                node.credential
            },
            release = { node ->
                node.recycled++
                account.released.add(node)
            }
        )

    private fun assertBalanced(root: FakeNode, account: Accounting) {
        assertEquals("取到的节点数必须等于归还数", account.obtained.size, account.released.size)
        assertTrue("每个取到的节点都要还", account.released.all { it.recycled == 1 })
        assertEquals("入参 root 归系统，绝不回收", 0, root.recycled)
        assertEquals("父节点不能在取子节点前被回收", 0, account.parentReadAfterRelease)
    }

    @Test
    fun `every obtained node is recycled exactly once`() {
        val leafA = FakeNode()
        val leafB = FakeNode()
        val branch = FakeNode().add(leafA, leafB)
        val root = FakeNode().add(branch, FakeNode())

        val account = Accounting()
        assertFalse(scan(root, account = account))

        assertBalanced(root, account)
        assertTrue(account.released.containsAll(listOf(leafA, leafB, branch)))
    }

    @Test
    fun `early hit still recycles already queued nodes`() {
        val credential = FakeNode(credential = true)
        val queuedSibling = FakeNode()
        val child = FakeNode().add(credential, queuedSibling)
        val root = FakeNode().add(child, FakeNode())

        val account = Accounting()
        assertTrue(scan(root, account = account))

        assertBalanced(root, account)
        // 命中后队列里剩下的节点（兄弟/未访问子树）也必须还
        assertTrue(account.released.contains(queuedSibling))
    }

    @Test
    fun `node budget caps visits but never leaks the fetched surplus`() {
        val root = FakeNode()
        repeat(200) { root.add(FakeNode()) }

        val account = Accounting()
        assertFalse(scan(root, maxNodes = 120, account = account))

        assertBalanced(root, account)
        // 预算只限制访问数；超出预算而被取进队列的节点仍然归还
        assertEquals(120, account.visits)
        assertEquals(200, account.obtained.size)
        assertEquals(200, account.released.size)
    }

    @Test
    fun `child reads happen before the parent is released`() {
        val grandChild = FakeNode()
        val child = FakeNode().add(grandChild)
        val root = FakeNode().add(child)

        val account = Accounting()
        assertFalse(scan(root, account = account))

        assertBalanced(root, account)
        assertEquals(2, account.obtained.size)
    }
}
