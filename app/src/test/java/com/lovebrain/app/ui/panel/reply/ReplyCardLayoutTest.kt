package com.lovebrain.app.ui.panel.reply

import com.lovebrain.app.ReplyCardLayout
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplySchemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 回复卡片纵向排列这一族的账：默认纵向、横向保留现有摆法（几条失败信息里留着这条口径的旧编号，读作"默认纵向"即可）。
 *
 * 这一族**两半都有**：
 * - 纯函数那一半（枚举回落、八项上限）判的是"这颗解析口与这一排到底装了几张卡"；
 * - 源码结构那一半判的是"两档是不是**共用同一套卡逻辑**、方向有没有把别的东西一起换掉"。
 *
 * ⚠ 为什么结构那一半按"只有一处定义"来判，而不是去量像素：这一档的口径写得很死——
 * 纵向要换的只有**布局关系**，不是新增 UI 样式；颜色、边框、圆角、字号、按钮与按压缩放
 * 仍由 [SchemeCard] 与设计令牌拥有。这类"不许有第二份"的债，最便宜的证人就是**声明处计数**：
 * 有人为纵向档复制一张卡（或抄第二份尺寸常量），计数立刻从 1 变 2，当场红。
 * 每一格都另配**反向证人**（同一颗尺在坏形状下必须读不到、在好形状下必须读到），
 * 免得正则写坏了恒绿——扫到空集就当通过，正是"恒绿"那一族坏法。
 *
 * 行为那一半（切换方向后逐条还在、回调仍落在原 identity、筛选对得上原卡）在
 * `ResultAreaVerticalReadingTest`（Robolectric，真挂起来量）；
 * 热区下限那一半仍在 `ResultAreaTouchTargetsTest`（钉横向档）。
 */
class ReplyCardLayoutTest {

    // ═══ 1. 默认档＝纵向；脏值与"从没写过"都落回纵向（偏好侧唯一的解析口）═══

    @Test
    fun `the layout enum has exactly the two named tiers and vertical is the default`() {
        assertEquals("布局枚举只有纵向/横向两档，要加第三档先回来写清判据",
            listOf("VERTICAL", "HORIZONTAL"), ReplyCardLayout.entries.map { it.name })
        assertEquals("默认档必须是纵向（§3「默认开启」）", ReplyCardLayout.VERTICAL, ReplyCardLayout.DEFAULT)
        assertEquals("落盘键只有一个，读写不许再发明第二个", "reply_card_layout", ReplyCardLayout.PREF_KEY)
    }

    @Test
    fun `a dirty or missing raw falls back to vertical and never throws`() {
        // 坏实现证人：`valueOf(raw)` 会在脏值上抛；`?: HORIZONTAL` 会落错档；
        // 大小写/空白/旧名字都不该被读成"另一档"。
        val dirty = listOf(
            null, "", "   ", "vertical", "Vertical", "VERTICAL ", " HORIZONTAL",
            "HORIZON", "DIAGONAL", "WRAP", "0", "1", "true", "null", "纵向"
        )
        dirty.forEach { raw ->
            assertEquals("脏值 $raw 必须落回纵向（不许抛、也不许顺手落成横向）",
                ReplyCardLayout.VERTICAL, ReplyCardLayout.from(raw))
        }
        // 反向证人：合法名要真读得出来，否则上面那一串"全落纵向"是恒真的假绿
        assertEquals("横向档必须读得出来，否则这一格只是在测常量",
            ReplyCardLayout.HORIZONTAL, ReplyCardLayout.from("HORIZONTAL"))
    }

    // ═══ 2. 画侧默认纵向：入口的默认值不许翻成横向 ═══

    @Test
    fun `the drawing side defaults to vertical and never hands itself a horizontal default`() {
        val code = masked("ResultArea.kt")
        assertTrue(
            "ResultArea 的 cardLayout 默认值必须是纵向——§3 那句「默认开启」就落在这颗默认值上：" +
                "画侧没有这颗默认值，未接线的宿主就会拿到没人认领的一档",
            code.lines().any { "cardLayout: ReplyCardLayout = ReplyCardLayout.VERTICAL" in it }
        )
        assertFalse(
            "画侧不许出现 `cardLayout: ReplyCardLayout = ReplyCardLayout.HORIZONTAL` 这种默认：" +
                "偏好读不到时它会把这一屏静默退回横向（§3 要的默认纵向就是这么丢的）。" +
                "横向档只能由宿主显式传进来。",
            code.lines().any { "cardLayout: ReplyCardLayout = ReplyCardLayout.HORIZONTAL" in it }
        )
    }

    // ═══ 3. 两档共用同一套卡逻辑：方向只换容器，不换卡片、不换主人 ═══

    @Test
    fun `both tiers render the same single card implementation`() {
        val code = masked("ResultArea.kt")
        // 一张卡：横向与纵向都调同一颗 SchemeCard（分叉只在容器）。
        // 坏实现证人：为纵向档复制一条 SchemeCard 调用链 ⇒ 这里读到 2。
        assertEquals(
            "ResultArea.kt 里 `SchemeCard(` 只许出现一处（两档共用）",
            1,
            occurrences(code, "SchemeCard(")
        )
        // 卡片行：一处声明 + 两处调用（流式那一档、完成那一档）——方向不进第三处
        assertEquals(
            "SchemeCardsRow 应当是一处声明加两处调用（流式档与完成档）",
            3,
            occurrences(code, "SchemeCardsRow(")
        )
        // 方向这一颗只沿着"入口 → 卡片行 → 卡片 → 骨架屏"这一条链走，谁也不自己揣开关
        assertEquals(
            "`cardLayout = cardLayout` 只许这四颗透传点（流式档卡片行、完成档卡片行、骨架屏、SchemeCard）",
            4,
            occurrences(code, "cardLayout = cardLayout")
        )
        // 同一颗锚点两档共用（仪器那侧按这一个串找这一排，不新增第二个 tag）
        assertEquals(
            "两档必须共用同一颗 testTag(\"scheme_cards_row\")（横向一棵容器 + 纵向一棵容器 = 2 处）",
            2,
            occurrences(code, "testTag(\"scheme_cards_row\")")
        )
    }

    /**
     * 「切换布局不能重新请求 AI、重新付费或清空当前回复」这一条的**机制**那一半：
     * 方向不许当任何状态或副作用的 key。
     *
     * 为什么这格值得单独钉：形状上最容易犯的坏法是 `remember(cardLayout) { ... }` 或
     * `LaunchedEffect(cardLayout) { ... }`——换一次方向就把 `rowState`（展开态、卡内落点、
     * 自定义草稿与开合）连同筛选一起重建，用户看到的就成了"我刚才写的东西没了"。
     * 坏实现证人：谁给 rowState/筛选/动画记账加上方向这颗 key，这里当场读到一行红。
     */
    @Test
    fun `the direction never becomes a key of any state or effect owner`() {
        val code = masked("ResultArea.kt")
        val offenders = code.lines().filter {
            it.contains("cardLayout") &&
                (it.contains("remember(") || it.contains("LaunchedEffect(") || it.contains("rememberSchemeRowState("))
        }
        assertTrue(
            "方向不许当任何状态或副作用的 key（换方向换主人＝清了当前回复的展开态、草稿、筛选与动画记账）：" +
                offenders,
            offenders.isEmpty()
        )
        // 反向证人：这把尺看得见东西——轮次身份今天确实是 rowState 的 key（既有形状）
        assertTrue(
            "rowState 仍该按 generationRoundId 发；读不到说明这格的对象被删了，上一条就成了空转",
            code.lines().any { "rememberSchemeRowState(generationRoundId)" in it }
        )
        // 同一条判据在卡片那一侧：卡片行状态的暂存位也不许被方向切走
        assertTrue(
            "方向只作为实参往下传，不参与轮次身份：generationRoundId 仍是唯一的作废判据",
            code.lines().any { "SchemeRowStateStash.roundId == generationRoundId" in it }
        )
    }

    @Test
    fun `the visual tokens of the card are still defined exactly once`() {
        val code = masked("SchemeCard.kt")
        listOf(
            ".background(cardBg)" to "卡片底色",
            ".border(effectiveBorderWidth.dp, effectiveBorderColor, LoveBrainShape.lg)" to "卡片描边（赞/踩/改写中那一档粗细）",
            ".shadow(AppDimens.ELEVATION_DEFAULT_DP.dp, LoveBrainShape.lg)" to "卡片投影",
            "SchemeCollapsedBlock(" to "默认态展示块",
            "SchemeAdjustingBlock(" to "调整态展示块",
            "SchemeRewritingBlock(" to "改写中展示块"
        ).forEach { (needle, what) ->
            assertEquals(
                "$what 只许有一处定义——纵向档是「同一种卡的另一种摆法」，不是第二张卡：" +
                    "这里读到 2 就是有人复制了卡片内容层（这一版明令禁止）",
                1, occurrences(code, needle)
            )
        }
        // 字号那一档仍只住在 SchemeTextDimens（这一族没动它，留着是防"纵向档顺手调大字号"）
        assertEquals("正文字号只许一处定义", 1, occurrences(code, "BODY_FONT_SIZE = 13.sp"))
        assertEquals("正文行高只许一处定义", 1, occurrences(code, "BODY_LINE_HEIGHT = 18.sp"))
    }

    // ═══ 4. 尺寸语义只有一颗主人：158/150 不换数，也不抄第二份常量 ═══

    @Test
    fun `the card size has one source read two ways, not a second set of constants`() {
        val code = masked("SchemeCard.kt")
        // 声明处各一颗：没有 VERTICAL_CARD_WIDTH_DP 那种新常量
        assertEquals("卡宽只许一处声明", 1, occurrences(code, "const val CARD_WIDTH_DP"))
        assertEquals("卡高只许一处声明", 1, occurrences(code, "const val CARD_HEIGHT_DP"))
        // 替代关系（判据跟着新形状重钉，不是"存在就行"）：
        // 158 这一颗被读**两处**——声明 + `widthFor` 里横向档那条固定宽（纵向档读的是铺满，不是第二个数）；
        // 150 这一颗被读**三处**——声明 + `heightFor` 里横向档的固定高 + 纵向档的 heightIn(min=…)。
        // 于是"纵向档另抄一颗宽度/高度"这种坏形状在这两格当场红。
        assertEquals("158 只许被读两处（声明 + 横向档固定宽），实到 " + occurrences(code, "CARD_WIDTH_DP"),
            2, occurrences(code, "CARD_WIDTH_DP"))
        assertEquals("150 只许被读三处（声明 + 横向档固定高 + 纵向档下限），实到 " + occurrences(code, "CARD_HEIGHT_DP"),
            3, occurrences(code, "CARD_HEIGHT_DP"))
        assertEquals("固定宽只许出现在 widthFor 那一颗分叉里", 1, occurrences(code, ".width(CARD_WIDTH_DP.dp)"))
        assertEquals("固定高只许出现在 heightFor 的横向档那一支", 1, occurrences(code, ".height(CARD_HEIGHT_DP.dp)"))
        assertEquals(
            "纵向档的高度必须是「同一颗 150 当下限」（heightIn(min = CARD_HEIGHT_DP.dp)），" +
                "不许换成第二颗数、也不许写成无边界的自适应（那会拿到外层那条无限高约束）",
            1, occurrences(code, "heightIn(min = CARD_HEIGHT_DP.dp)")
        )
        // 三颗分叉口各只有一颗主人
        assertEquals("宽度分叉只许一处定义", 1, occurrences(code, "fun widthFor("))
        assertEquals("高度分叉只许一处定义", 1, occurrences(code, "fun heightFor("))
        assertEquals(
            "卡内容量的分叉判据只许一处定义", 1, occurrences(code, "fun hasBoundedContentHeight(")
        )
        // 卡片自己只算一次分叉判据（不许在三个展示块里各自再判断一次方向）
        assertEquals(
            "SchemeCard 里 `hasBoundedContentHeight(cardLayout)` 只许调用一处",
            1,
            occurrences(code, "hasBoundedContentHeight(cardLayout)")
        )
        // 填满固定高度的那一句只属于有界那一档（纵向档若还 fillMaxSize()，量到的是无限高）
        assertEquals("fillMaxSize() 只许出现在有界档那一支", 1, occurrences(code, "fillMaxSize()"))
    }

    @Test
    fun `each presentation block owns exactly one height fork and keeps the bounded default`() {
        listOf(
            "SchemeCollapsedBlock.kt",
            "SchemeAdjustingBlock.kt",
            "SchemeRewritingBlock.kt"
        ).forEach { name ->
            val code = masked(name)
            assertEquals(
                "$name 的分叉开关只许一颗、且默认 true（现有把单块挂起来量的测试仍是原来那一档）",
                1, occurrences(code, "contentHeightBounded: Boolean = true")
            )
            assertEquals(
                "$name 的卡内滚动只许一处（纵向档不挂它，两档共用同一份实现）",
                1, occurrences(code, "verticalScroll(rememberScrollState())")
            )
        }
    }

    // ═══ 5. 阅读位置的主人：两档共用外层那一条，纵向不嵌无限高懒列表 ═══

    @Test
    fun `the vertical tier reuses the outer scroll and embeds no infinite lazy column`() {
        val code = masked("ResultArea.kt")
        assertEquals(
            "全文件只许两颗 ScrollState 主人：共用的 `readScroll` + 骨架屏那一颗私有占位。" +
                "纵向档若要再嵌一条自己的滚动（= 第三颗），就是这一版点名禁止的那条坏法：" +
                "「在已有 verticalScroll 里再嵌一条滚动」，实到 " + occurrences(code, "rememberScrollState()"),
            2, occurrences(code, "rememberScrollState()")
        )
        assertEquals(
            "`verticalScroll(` 只许三处：流式档与完成档那两根共用 readScroll 的柱 + 骨架那一柱；" +
                "纵向档那一列卡片必须挂在外层柱里，实到 " + occurrences(code, "verticalScroll("),
            3, occurrences(code, "verticalScroll(")
        )
        assertEquals(
            "ResultArea.kt 里不许出现 LazyColumn：外层已经是无限高滚动，" +
                "里面再嵌一棵无限高懒列表就是 §3 明令禁止的坏法（纵向一列用普通 Column）",
            0, occurrences(code, "LazyColumn")
        )
    }

    // ═══ 6. 「最多八条」这条上限本来就由八项宇宙给足：不许新造截断逻辑 ═══

    private fun responseOf(
        styles: List<String> = listOf("甲的话术", "乙的话术", "丙的话术", "丁的话术"),
        directions: List<String?> = listOf("戊的话术", "己的话术", "庚的话术", "辛的话术")
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
    fun `the merged set is capped at eight by the model itself so no truncation is needed`() {
        val full = mergedSchemesInRoundOrder(responseOf())
        assertEquals("四风格 + 四方向 = 八项宇宙", 8, full.size)
        // 越界输入也不长：模型多吐的方向位不新增卡位（固定四档由 ReplyDirection.ALL 决定）
        val over = mergedSchemesInRoundOrder(responseOf(directions = (1..20).map { "第 $it 条" }))
        assertEquals("方向超出四档也不许冒出第九张卡", 8, over.size)
        assertEquals("多出来的方向位被丢掉而不是接在后面", over.map { it.identity.key }.take(8), full.map { it.identity.key })
        // 空回复也仍是八格卡位（缺项自己念「未生成」，卡位不许被删）
        val empty = mergedSchemesInRoundOrder(responseOf(styles = List(4) { "" }, directions = List(4) { "" }))
        assertEquals("全空也保留八张卡位", 8, empty.size)
        assertTrue("任何一档的卡数都不许多于八（所以纵向档不需要截断）", full.size <= 8 && over.size <= 8)
    }

    @Test
    fun `no truncation logic was invented for the eight card ceiling`() {
        val code = masked("ResultArea.kt")
        listOf("take(8)", "MAX_VISIBLE", "MAX_CARDS", "subList(", "VERTICAL_VISIBLE_LIMIT").forEach { banned ->
            assertFalse(
                "「最多八条」这条上限归八项宇宙自己管（上一格已经数过），画侧不许新造截断逻辑：$banned 出现了",
                code.contains(banned)
            )
        }
    }

    // ═══ 7. 设置页那一格：两档都是**选项**，不是开 / 关 ═══

    /**
     * 「回复卡片 [上下 | 左右]」这一行的形状判据（替代关系：旧形状是"组名 + 一行
     * 『回复卡片纵向排列』+ 一颗开关"，把一个方向说成了"开不开"）。
     *
     * 每一颗计数各挡一种回退：
     * · `LbChip(` 恰两颗 ⇒ 长回开关（0 颗）、或有人把方向摊成三档（3 颗）都红；
     * · `LbChipInteraction.Single` 与 `LbChipStyles.segmented` 各两颗 ⇒ 选中态仍走互斥单选那一族、
     *   可见尺寸仍由设计系统那一颗分段档拥有（页面自己 `copy` 一档出来就是第四种形状）；
     * · `onVerticalChange(` 恰一处 ⇒ 写口仍然只有一条，而且交的是方向；
     * · 开关那一族（`MiniSwitch` / `Switch(` / `toggleable` / `checked =`）与 `LbSettingRow`、
     *   旧的组名与「纵向排列」两句文案 **一颗都不许在**；
     * · 这一行也不再自带卡壳（整组一张外框由页面那一侧拥有）。
     *
     * ⚠ 这里判的是来路与形状。"点下去真的换了摆法、只亮一档、旧文案不在屏上"那一半在
     * `SettingsReplyLayoutEntryTest`（挂起来读语义树）。
     */
    @Test
    fun `the settings row renders the two directions as two options and nothing else`() {
        val code = maskedSettings("SettingsReplyLayoutEntry.kt")
        listOf(
            "LbChip(" to "两档各一颗芯片",
            "LbChipInteraction.Single" to "两颗都交出互斥单选的选中语义",
            "LbChipStyles.segmented" to "两颗都走设计系统那一颗分段档",
            "onVerticalChange(" to "唯一的写口",
            "selected =" to "两档各自的选中判据"
        ).forEach { (needle, what) ->
            assertEquals("$what：这一行只该有两档，实到 " + occurrences(code, needle) + " 处（$needle）",
                2, occurrences(code, needle))
        }
        // 这一版的新形状（对照原型换算表 .segments）：**一条浅底轨道 + 轨道里几格透明文字**，底与描边归
        // 轨道、格子自己静息不画底。10-11 那一版错画成"两格各自带描边的独立盒"（用户判"根本不一样"那一件），
        // 这一族的两把尺各挡一种坏法：
        // · 整行只许**一条** `SettingsSegmentedPill` 轨道容器——两格各画各的盒子那种画法根本没有这颗
        //   共享轨道，计数 0 当场红；长出第二条也红（一格一条轨道 = 又拆回独立按钮）。
        // · 两格都走**透明格档** `LbChipStyles.segmentedPill`（不画底不画描边）——退回带描边的
        //   `LbChipStyles.segmented`（10-11 那一版那颗坏档）时 `segmentedPill` 读数掉到 0，当场红。
        assertEquals(
            "整行只许一条分段轨道容器（[两格各自一盒] 那种坏形状根本没有这颗共享轨道）：实到 " +
                occurrences(code, "SettingsSegmentedPill(") + " 处",
            1, occurrences(code, "SettingsSegmentedPill(")
        )
        assertEquals(
            "两格都走设计系统那颗**透明格子**档 segmentedPill（底与描边归轨道，不各自一盒）：实到 " +
                occurrences(code, "LbChipStyles.segmentedPill") + " 处",
            2, occurrences(code, "LbChipStyles.segmentedPill")
        )
        // 行版式与标签的挤压方向：行根走族里那颗行骨架、标签吃掉剩余宽度（窄窗时缩的是标签不是格子）。
        assertTrue("行根要走 Modifier.settingsEntryRow()（族里那颗行骨架），而不是自带卡壳或裸 Row",
            code.contains("settingsEntryRow()"))
        assertTrue("行标签必须吃掉剩余宽度（Modifier.weight(1f)），否则窄窗挤掉的是分段命中区",
            code.contains("Modifier.weight(1f)"))
        listOf(
            "R.string.settings_reply_layout_vertical" to "「上下」那一档的名字",
            "R.string.settings_reply_layout_horizontal" to "「左右」那一档的名字",
            "R.string.settings_reply_card_row_label" to "这一行自己的标签"
        ).forEach { (needle, what) ->
            assertEquals("$what：名字住资源，这一格里只许引用一次（实到 " +
                occurrences(code, needle) + " 处，$needle）", 1, occurrences(code, needle))
        }
        listOf(
            "MiniSwitch" to "开关那一族",
            "Switch(" to "开关那一族",
            "toggleable" to "切换语义（把选项说成开/关）",
            "checked =" to "开关的选中判据",
            "LbSettingRow" to "旧那一行的行壳（组名 + 文案 + 尾部开关）",
            "R.string.settings_card_vertical_label" to "旧文案「回复卡片纵向排列」",
            "R.string.settings_group_reply" to "旧组名那一行（组名现在住在页外框外面）",
            "settingsEntryCard()" to "行自己画的卡壳（卡套卡的另一半来源）"
        ).forEach { (needle, what) ->
            assertFalse(
                "这一格里不许再有$what：看见「$needle」就是旧形状长回来了",
                code.contains(needle)
            )
        }
    }

    /**
     * 屏上两档 ↔ 偏好那一颗枚举：只有两档、默认纵向，而且布尔与枚举的桥只有一座。
     *
     * 这一格钉的是"设置页这一行**不**持有方向真值"：它只交布尔，枚举与落盘归宿主。
     * 反例：在这一行里再写一次 `ReplyCardLayout`（第二座桥）、或宿主那侧把布尔当成第三档，
     * 都会在"这一行读到 0 处 / 宿主读到 1 处"这两句上红。
     */
    @Test
    fun `the row keeps the direction truth in the enum owned by the host`() {
        val row = maskedSettings("SettingsReplyLayoutEntry.kt")
        assertEquals("方向的真值不住在这一行：这里不许出现第二次枚举判断",
            0, occurrences(row, "ReplyCardLayout"))
        val host = maskedHost("LoveBrainPanelScreen.kt")
        assertTrue(
            "布尔 ↔ 枚举那座桥必须在宿主那一侧（点上下 = VERTICAL、点左右 = HORIZONTAL），扫不到说明" +
                "要么桥被拆了、要么搬去了这一行",
            host.lines().any { "if (vertical) ReplyCardLayout.VERTICAL else ReplyCardLayout.HORIZONTAL" in it }
        )
        assertTrue(
            "行的读数也从那一颗枚举回来（默认纵向就是枚举那颗 DEFAULT）：扫不到就是宿主自己揣了一份方向",
            host.lines().any { "surface.cardLayout == ReplyCardLayout.VERTICAL" in it }
        )
        assertEquals("写盘口在宿主那一侧恰一处", 1, occurrences(host, "viewModel.setReplyCardLayout("))
        // 反向证人：这把尺不是扫空集
        assertTrue("这一屏的两档就是枚举的两档，一档不多一档不少",
            ReplyCardLayout.entries.size == 2 && ReplyCardLayout.DEFAULT == ReplyCardLayout.VERTICAL)
    }

    // ═══ 夹具 ═══

    /** 设置页那一族的源码（形状判据要跨目录读：这一行的主人住在 `ui/panel/settings`） */
    private fun settingsSourceFile(name: String): File {
        val dir = File("src/main/java/com/lovebrain/app/ui/panel/settings").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel/settings")
        val f = File(dir, name)
        assertTrue("找不到生产源码：$f（这把尺没有对象，不许静默空过）", f.isFile)
        return f
    }

    private fun maskedSettings(name: String): String =
        SourceScan.maskComments(settingsSourceFile(name).readText(Charsets.UTF_8))

    /** 宿主接线那一颗文件（`ui/panel`）：只读形状，不改它 */
    private fun maskedHost(name: String): String {
        val dir = File("src/main/java/com/lovebrain/app/ui/panel").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel")
        val f = File(dir, name)
        assertTrue("找不到宿主接线：$f（这一格的桥就没有对象了）", f.isFile)
        return SourceScan.maskComments(f.readText(Charsets.UTF_8))
    }

    private fun sourceFile(name: String): File {
        val dir = File("src/main/java/com/lovebrain/app/ui/panel/reply").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app/ui/panel/reply")
        val f = File(dir, name)
        assertTrue("找不到生产源码：$f（这把尺没有对象，不许静默空过）", f.isFile)
        return f
    }

    /** 剥掉注释再数：这些文件的 KDoc 里写"以前这里是……"是常态，不能当成又长了一份 */
    private fun masked(name: String): String = SourceScan.maskComments(
        sourceFile(name).readText(Charsets.UTF_8)
    )

    private fun occurrences(code: String, needle: String): Int = code.lines().count { needle in it }
}
