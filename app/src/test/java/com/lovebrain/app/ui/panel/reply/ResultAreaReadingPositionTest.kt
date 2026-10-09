package com.lovebrain.app.ui.panel.reply

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 流式档 → 完成档那一跳的**阅读位置**账（书 §13.2「结束后保留结果位置与当前阅读位置」、
 * §16.1 第 5 条同一根因）。L1 复核挑中的形状：两个分支原来各自
 * `verticalScroll(rememberScrollState())`——分支一切换就换了一颗 ScrollState，
 * 用户正往下读、结果一落地弹回顶部。现在两档共用 `when` 之前那唯一一颗 `readScroll`。
 *
 * ⚠ **为什么这一族是结构尺而不是行为尺**：本机试过把 swipeUp 注进那根纵向滚动柱——
 * 手势注入到滚动容器这一族在本 JVM 判不稳（`MessageRowDragFollowTest` 头注②记过同一件事，
 * 第一版行为格就是这么红的：`swipeUp` 之后读数不动，量不到"滚了"这一步）。
 * 所以这里按该族既有分工写"结构那一半"：**两档必须引用同一颗主人**；
 * "位置真的没弹回"那半归真机录屏（§16.3 第二条对照路径本来就要求实屏）。
 *
 * 红条件：谁把某一档改回就地 `rememberScrollState()`（= 旧形状回来），第二格当场红；
 * 谁把共用那颗删了/改名，两格都红在计数上。这把尺证明的是"那一族坏写法没有回来"。
 */
class ResultAreaReadingPositionTest {

    private val source: String by lazy {
        val f = File("src/main/java/com/lovebrain/app/ui/panel/reply/ResultArea.kt")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel/reply/ResultArea.kt")
        assertTrue("找不到生产源码：$f（这把尺没有对象，不许静默空过）", f.isFile)
        SourceScan.maskComments(f.readText())
    }

    private fun countWith(needle: String): Int = source.lines().count { needle in it }

    @Test
    fun `the shared scroll owner is declared exactly once above the branches`() {
        val decl = countWith("val readScroll = rememberScrollState()")
        assertEquals("`readScroll` 这唯一颗主人必须恰好声明一次（在 `when` 之前），实到 $decl 处", 1, decl)
    }

    @Test
    fun `no branch keeps a private scroll owner anymore`() {
        // 流式/完成两根柱都必须吃共用那颗；全文件只许留下**骨架那一颗**私有主人
        // （`CoreLoadingIndicator` 的占位滚动，不在流式→完成这一跳的链上，属另一件事）。
        val shared = countWith("verticalScroll(readScroll)")
        assertEquals("流式/完成两根柱都该挂共用主人，实到 $shared 处（应为 2）", 2, shared)
        val privates = countWith("verticalScroll(rememberScrollState())")
        assertEquals(
            "分支里不许再出现私有 rememberScrollState()（旧形状回来就红在这里）；" +
                "全文件只许骨架那一颗，实到 $privates 处",
            1, privates
        )
    }
}
