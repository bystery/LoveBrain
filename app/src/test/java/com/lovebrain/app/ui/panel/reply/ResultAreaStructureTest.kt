package com.lovebrain.app.ui.panel.reply

import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结果区纯状态模型测试——验证信息架构不变量。
 *
 * 这里原本还钉着长按录音手势的 reducer 路径与语音两事件 rendezvous 提交决策，
 * 随语音模式一起删除。
 *
 * 这些测试只覆盖 JVM 级别的纯函数/状态模型逻辑：
 * - SchemeIdentity 区分 STYLE/DIRECTION（数据模型）
 * - 一条横向列表怎么把两组已生成方案接起来（mergedSchemesInRoundOrder）
 * - 卡片调整区的展开态集合怎么增删（toggleRewriteExpansion）
 * - v1.3.1 theme token 文件存在
 *
 * 后两组的判据写在**纯函数**上而不是写在屏幕字符串上：展开态与合并顺序现在都有唯一的
 * 算法去处，坏实现（"只留当前那一张"、"只读风格那一组"）在这里当场红，
 * 不需要挂 Compose、也不需要有人去 grep 源码形状。
 *
 * 不再通过读取源文件字符串来"验证"UI 结构——
 * Compose layout/interaction 覆盖在 androidTest (ResultAreaInteractionTest) 中。
 * 禁止以"函数名已经替换"作为 UI 结构验收。
 */
class ResultAreaStructureTest {

    // ═══ SchemeIdentity 数据模型：STYLE/DIRECTION key 区分 ═══

    @Test
    fun `STYLE and DIRECTION SchemeIdentity keys are different`() {
        val styleA = com.lovebrain.app.model.SchemeIdentity(
            com.lovebrain.app.model.SchemeSource.STYLE, "A"
        )
        val dirF = com.lovebrain.app.model.SchemeIdentity(
            com.lovebrain.app.model.SchemeSource.DIRECTION, "F"
        )
        assertNotEquals("STYLE and DIRECTION must have different keys",
            styleA.key, dirF.key)
    }

    @Test
    fun `STYLE and DIRECTION Schemes have different sources`() {
        val styleScheme = Scheme(
            tag = "A", title = "推荐", reply = "text",
            source = SchemeSource.STYLE
        )
        val dirScheme = Scheme(
            tag = "F", title = "跟进", reply = "text",
            source = SchemeSource.DIRECTION
        )
        assertNotEquals(styleScheme.source, dirScheme.source)
        assertNotEquals(styleScheme.identity.key, dirScheme.identity.key)
    }

    // ═══ v1.3.1 token 不变 ═══

    @Test
    fun `theme token files exist and are stable`() {
        // 这一格只判"文件在不在"。以前它的注释写着"SHA 校验由 CI 层完成"——那是假的：
        // grep .github/workflows 里没有任何一处算 theme 文件的哈希，CI 不算 token 的 SHA。
        // 注释承诺一道不存在的闸，比没有闸更坏（下一个人会以为已经有人看着），所以改口。
        //
        // 第三步-1 把 token 整体迁进 core/designsystem 之后，这里判的东西也变了：
        // 不只是"文件在"，而是"token 在 core、Material 包装留在 ui"——方向反了就是 第5节第1条 的破口。
        val tokenDir = java.io.File("src/main/java/com/lovebrain/app/core/designsystem")
        assertTrue("designsystem 目录应存在", tokenDir.exists())
        listOf("Color.kt", "Dimens.kt", "Type.kt", "Spacing.kt", "Shapes.kt").forEach { file ->
            assertTrue("token 文件 $file 应在 core/designsystem", java.io.File(tokenDir, file).exists())
        }
        val themeDir = java.io.File("src/main/java/com/lovebrain/app/ui/theme")
        assertTrue("ui/theme 应仍存在（Material 主题包装）", themeDir.exists())
        assertTrue("Theme.kt 留在 ui/theme", java.io.File(themeDir, "Theme.kt").exists())
        // 反向：token 不许又长回 ui 下面（迁移被 revert 一半是最难发现的那种坏法）
        listOf("Color.kt", "Dimens.kt", "Type.kt").forEach { file ->
            assertTrue("token 文件 $file 不许再回到 ui/theme", !java.io.File(themeDir, file).exists())
        }
    }

    // ═══ 一条横向列表：两组已生成方案按原有顺序接起来 ═══
    //
    // 判的是"这一排要渲染哪几条、按什么顺序、带着什么身份"。展示分组（风格一排/方向一排、
    // 点着切）已经删掉了，但**来源标识必须原样跟着走**——反馈、改写、撤销、缓存都按
    // `STYLE:A` / `DIRECTION:F` 这种带来源的身份寻址，抹平来源就会把它们绑到错的卡上。

    /** 四风格齐全 + 四条方向（null/空白 = 本轮不存在那一条） */
    private fun responseOf(
        styles: List<String> = listOf("A 的话术", "B 的话术", "C 的话术", "D 的话术"),
        directions: List<String?> = listOf("F 的话术", "E 的话术", "X 的话术", "S 的话术")
    ) = LoveBrainResponse(
        response = ReplySchemes(
            recommended = styles.getOrNull(0) ?: "",
            badBoy = styles.getOrNull(1) ?: "",
            playful = styles.getOrNull(2) ?: "",
            warm = styles.getOrNull(3) ?: ""
        ),
        directions = directions
    )

    @Test
    fun `the merged list keeps style schemes first and direction schemes right after them`() {
        val merged = mergedSchemesInRoundOrder(responseOf())
        assertEquals(
            "一条列表的顺序必须是四风格在前、四方向在后（组内保持模型给的固定位置，不重排）",
            listOf(
                "STYLE:A", "STYLE:B", "STYLE:C", "STYLE:D",
                "DIRECTION:F", "DIRECTION:E", "DIRECTION:X", "DIRECTION:S"
            ),
            merged.map { it.identity.key }
        )
    }

    @Test
    fun `the merged list shows exactly as many items as exist and no placeholders`() {
        // 方向只活两条（一条 null、一条空白串）⇒ 这一排应当是 4 + 2，不是 4 + 4 张占位卡
        val merged = mergedSchemesInRoundOrder(
            responseOf(directions = listOf("F 的话术", null, "X 的话术", "   "))
        )
        assertEquals("存在的项数是 ${merged.size}，应为 6", 6, merged.size)
        assertEquals(
            listOf("STYLE:A", "STYLE:B", "STYLE:C", "STYLE:D", "DIRECTION:F", "DIRECTION:X"),
            merged.map { it.identity.key }
        )
        assertTrue("不许把没有内容的项塞进来占位", merged.all { it.reply.isNotBlank() })
    }

    @Test
    fun `the merged list reads both groups instead of only the style one`() {
        // 反向证人：风格四条全空时，这一排不能是空的——只读 response.schemes 的实现会在这里红
        val onlyDirections = mergedSchemesInRoundOrder(
            responseOf(styles = listOf("", "", "", ""))
        )
        assertEquals(
            listOf("DIRECTION:F", "DIRECTION:E", "DIRECTION:X", "DIRECTION:S"),
            onlyDirections.map { it.identity.key }
        )
        assertTrue("两组都空时就该什么都没有，不造占位卡",
            mergedSchemesInRoundOrder(responseOf(styles = listOf("", "", "", ""),
                directions = listOf(null, null, null, null))).isEmpty())
    }

    @Test
    fun `the merged list keeps source and identity of every item and never collides`() {
        val merged = mergedSchemesInRoundOrder(responseOf())
        val keys = merged.map { it.identity.key }
        assertEquals("一条列表里的身份不许重复（重复会让赞/改写绑到别的卡上）",
            keys.size, keys.toSet().size)
        assertEquals("来源字段必须原样留着", listOf(SchemeSource.STYLE, SchemeSource.DIRECTION),
            merged.map { it.source }.distinct())
        // 同 tag 不同来源也必须是两个身份（数据层那条判据在 model 那 8 格里，这里只核合并没把它抹平）
        val sameTagBothSources = listOf(
            Scheme(tag = "A", reply = "a", source = SchemeSource.STYLE),
            Scheme(tag = "A", reply = "a2", source = SchemeSource.DIRECTION)
        )
        assertNotEquals(sameTagBothSources[0].identity.key, sameTagBothSources[1].identity.key)
    }

    // ═══ 卡片调整区：展开态是一张集合，不是"当前那一张" ═══

    @Test
    fun `two cards can stay expanded at the same time`() {
        val afterFirst = toggleRewriteExpansion(
            expanded = emptySet(), identityKey = "STYLE:A", rewriteState = null
        ).first
        val afterSecond = toggleRewriteExpansion(
            expanded = afterFirst, identityKey = "STYLE:B", rewriteState = null
        ).first
        assertEquals(
            "展开第二张不许把第一张收掉（旧约束'同一时间只展开一张卡'已删除）",
            setOf("STYLE:A", "STYLE:B"), afterSecond
        )
    }

    @Test
    fun `collapsing one card leaves the other expanded`() {
        val both = setOf("STYLE:A", "STYLE:B")
        val (next, clearRequested) = toggleRewriteExpansion(
            expanded = both, identityKey = "STYLE:A", rewriteState = RewriteState.Done("新正文")
        )
        assertEquals("收起那一张只许去掉那一张", setOf("STYLE:B"), next)
        assertTrue("收起这一步不许清改写结果（清只在重新进入展开态时发生）", !clearRequested)
    }

    @Test
    fun `re-entering a card that still carries a rewrite result asks to clear it first`() {
        assertTrue(
            "带着 Done 结果重新进入展开态 ⇒ 先清掉旧结果",
            toggleRewriteExpansion(emptySet(), "STYLE:A", RewriteState.Done("旧的新正文")).second
        )
        assertTrue(
            "带着 Error 结果同理",
            toggleRewriteExpansion(emptySet(), "STYLE:A", RewriteState.Error("超时")).second
        )
        assertTrue(
            "改写进行中不许清（那会丢掉正在等的这一次）",
            !toggleRewriteExpansion(emptySet(), "STYLE:A", RewriteState.Loading("更自然")).second
        )
        assertTrue(
            "没有改写状态时也没什么可清",
            !toggleRewriteExpansion(emptySet(), "STYLE:A", null).second
        )
    }

}
