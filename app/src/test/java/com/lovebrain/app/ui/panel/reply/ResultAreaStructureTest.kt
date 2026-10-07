package com.lovebrain.app.ui.panel.reply

import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyCompleteness
import com.lovebrain.app.model.ReplySchemes
import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    /** 四风格齐全 + 四条方向（null=合法「本轮不适合」，缺位/空白=没生成；两种都仍保留卡位） */
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
    fun `the merged list keeps all eight schemes including blank direction cards`() {
        // 方向只活两条（一位输出 null、一位是空白串）⇒ 两张空卡都保留，但**成因不同**：
        // null 那一张念「本轮不适合」，空白串那一张念「未生成」（§11.2 三因分家，
        // 卡片文案见 SchemeEmptyCardReasonTest）；这里判的是卡位不许被删
        val merged = mergedSchemesInRoundOrder(
            responseOf(directions = listOf("F 的话术", null, "X 的话术", "   "))
        )
        assertEquals("四风格 + 四方向 = 8 张卡（空回复 = 本轮不适合，不删）", 8, merged.size)
        assertEquals(
            listOf("STYLE:A", "STYLE:B", "STYLE:C", "STYLE:D",
                "DIRECTION:F", "DIRECTION:E", "DIRECTION:X", "DIRECTION:S"),
            merged.map { it.identity.key }
        )
    }

    @Test
    fun `the merged list reads both groups instead of only the style one`() {
        // 反向证人：风格四条全空时，这一排不能是空的——只读 response.schemes 的实现会在这里红
        // 空回复仍保留（四风格没有合法 null 这一档，空就是没生成 ⇒ 卡念「未生成」），
        // 不删——与方向的空卡同一判据：卡位属于八项宇宙
        val onlyDirections = mergedSchemesInRoundOrder(
            responseOf(styles = listOf("", "", "", ""))
        )
        assertEquals(
            listOf("STYLE:A", "STYLE:B", "STYLE:C", "STYLE:D",
                "DIRECTION:F", "DIRECTION:E", "DIRECTION:X", "DIRECTION:S"),
            onlyDirections.map { it.identity.key }
        )
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

    // ═══ §11.2 八项提示语：三个成因各归各，合法 null 不占缺项那一格 ═══
    //
    // 判的是 replyCompletenessNotice 这颗纯函数的读数（提示条那一侧），与卡片文案分家：
    // 「本轮不适合」由空卡自己念（见 SchemeEmptyCardReasonTest），提示条只说**真没生成**与**重复凑数**。
    // 旧混判态下这几格会红在：合法 null 被写进「未生成 N/8 项」那一句里。

    private fun poolOf(
        styles: List<String> = listOf("推荐的话", "清醒的话", "俏皮的话", "温柔的话"),
        directions: List<String?> = listOf("跟进的话", "展开的话", "表达的话", "转向的话")
    ) = LoveBrainResponse(
        response = ReplySchemes(
            recommended = styles.getOrNull(0) ?: "",
            badBoy = styles.getOrNull(1) ?: "",
            playful = styles.getOrNull(2) ?: "",
            warm = styles.getOrNull(3) ?: ""
        ),
        directions = directions
    )

    /** 八项齐全 → 不画提示条 */
    @Test
    fun `a complete round draws no incomplete notice`() {
        assertNull(replyCompletenessNotice(poolOf().replyCompleteness))
    }

    /**
     * 同一轮里一颗合法 null（方向「展开」）+ 一颗真空缺（风格「清醒」）：
     * 提示语只许点名「清醒」，「展开」由那张空卡自己说「本轮不适合」。
     */
    @Test
    fun `the notice names true misses only and keeps legal nulls out of it`() {
        val notice = replyCompletenessNotice(
            poolOf(
                styles = listOf("推荐的话", "", "俏皮的话", "温柔的话"),
                directions = listOf("跟进的话", null, "表达的话", "转向的话")
            ).replyCompleteness
        )
        assertTrue("真空缺必须说得出，实到 $notice", notice != null && notice.contains("清醒"))
        assertTrue("合法 null 不许冒缺项：$notice", notice != null && !notice.contains("展开"))
        assertTrue("数量要按整池八项说，不是局部数：$notice", notice != null && notice.contains("1/8"))
        assertTrue("给的是既有的再生成出口：$notice", notice != null && notice.endsWith("，可重新生成"))
    }

    /** 重复凑数单独说，与缺项各占一段；两因同时存在时不互相吞掉 */
    @Test
    fun `duplicated bodies get their own clause beside the missing one`() {
        val dupOnly = replyCompletenessNotice(
            poolOf(
                styles = listOf("同一句话", "同一句话", "俏皮的话", "温柔的话"),
                directions = listOf("跟进的话", "展开的话", "表达的话", "转向的话")
            ).replyCompleteness
        )
        assertTrue("只重复时也该有提示：$dupOnly", dupOnly != null && dupOnly.contains("重复"))
        assertTrue("重复也要按八项宇宙数：$dupOnly", dupOnly != null && dupOnly.contains("2 项正文重复"))

        val both = replyCompletenessNotice(
            ReplyCompleteness.Incomplete(
                missingLabels = listOf("清醒"),
                duplicatedLabels = listOf("推荐", "俏皮"),
                notSuitableLabels = listOf("展开"),
                totalItems = 8
            )
        )
        assertTrue("两因要各说一段：$both", both != null && both.contains("未生成 1/8 项：清醒"))
        assertTrue("两因要各说一段：$both", both != null && both.contains("2 项正文重复：推荐、俏皮"))
        assertTrue("不适合那份不进提示：$both", both != null && !both.contains("展开"))
    }

    /** 只有合法 null 时（读数被手工拼出来）不画提示条——卡片自己会说不适合 */
    @Test
    fun `legal not-suitable alone never becomes an incomplete notice`() {
        assertNull(
            replyCompletenessNotice(
                ReplyCompleteness.Incomplete(
                    missingLabels = emptyList(),
                    duplicatedLabels = emptyList(),
                    notSuitableLabels = listOf("展开", "转向"),
                    totalItems = 8
                )
            )
        )
        // 反向证人：同一份读数里塞进一颗真空缺，提示就必须出现（证明上一行不是恒真）
        assertTrue(
            replyCompletenessNotice(
                ReplyCompleteness.Incomplete(
                    missingLabels = listOf("转向"),
                    duplicatedLabels = emptyList(),
                    notSuitableLabels = listOf("展开"),
                    totalItems = 8
                )
            ) != null
        )
    }

    /** 全池八项这条线：合并列表与完整性读数用的是同一个宇宙 */
    @Test
    fun `the merged row and the completeness reading share the same eight-item universe`() {
        val r = poolOf(directions = listOf("跟进的话", null, "表达的话"))
        assertEquals("合并列表仍是八张卡位", 8, mergedSchemesInRoundOrder(r).size)
        val c = r.replyCompleteness
        c as ReplyCompleteness.Incomplete
        assertEquals("读数里的宇宙也是八项", 8, c.totalItems)
        assertEquals("缺位那一位算缺项（跟进/展开/表达都有，转向缺）", listOf("转向"), c.missingLabels)
        assertEquals("合法 null 那一位算不适合", listOf("展开"), c.notSuitableLabels)
    }

}
