package com.lovebrain.app.ui.panel.reply

import com.lovebrain.app.ReplyCardLayout
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.testing.TouchTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 回归测试：SchemeCardPresentationState 状态推导逻辑。
 *
 * 验证：
 * - 状态优先级正确（Rewriting > RewriteError > RewriteDone > Adjusting > Collapsed）
 *
 * 原来这一族里还有 Recording / Recognizing 两个语音态、长按录音的手势阶段枚举
 * 与麦克风权限事件，随语音模式一起删除。
 * - 每个 sealed class 子类型可正确区分
 * - 状态不是互斥的——但推导规则保证同一时刻只有一个生效
 */
class SchemeCardPresentationStateTest {

    @Test
    fun `Collapsed is the default state`() {
        val state = SchemeCardPresentationState.Collapsed
        assertEquals("Collapsed", state::class.simpleName)
    }

    @Test
    fun `all states are distinct`() {
        val states = listOf(
            SchemeCardPresentationState.Collapsed,
            SchemeCardPresentationState.Adjusting,
            SchemeCardPresentationState.Rewriting,
            SchemeCardPresentationState.RewriteError("error"),
            SchemeCardPresentationState.RewriteDone
        )
        // All should be distinct
        assertEquals(states.size, states.toSet().size)
    }

    @Test
    fun `RewriteError carries message`() {
        val state = SchemeCardPresentationState.RewriteError("network error")
        assertEquals("network error", state.message)
    }

    @Test
    fun `RewriteDone is different from Collapsed`() {
        assertNotEquals(
            SchemeCardPresentationState.RewriteDone as SchemeCardPresentationState,
            SchemeCardPresentationState.Collapsed as SchemeCardPresentationState
        )
    }

    /**
     * 卡片的**两层**（视觉 / 热区）与 §3 之后新增的**两档**（横向 / 纵向）各钉各的。
     *
     * ⚠ 判据跟着新形状改过一轮（原来这里只说"卡高是固定值"）：
     * 纵向档不是第二张卡，而是**同一颗数换一种读法**——
     * - [SchemeCardDimens.CARD_WIDTH_DP]（158）：横向档的固定宽；纵向档读的是"铺满整行"，
     *   所以这一颗在新档里**不被读**（不是被替换成第二颗常量）。
     * - [SchemeCardDimens.CARD_HEIGHT_DP]（150）：横向档的固定高，同时是纵向档的**下限**
     *   （卡片按内容长高，再矮也不矮过这一档）。
     * 于是"150 是固定值"这句旧判据的替代关系是：**150 在两档都得 ≥ 卡高，且只有横向档把它当等号**。
     * 两档各自量到几处、有没有人抄第二份尺寸常量，由 `ReplyCardLayoutTest` 按声明处计数钉
     * （那里判"只有一处定义"，这一格判"数与档对不对"）——这把尺不许松成"存在就行"。
     */
    @Test
    fun `SchemeCardDimens keeps the visual layer and the hotspot layer apart`() {
        // 这一档原先判 `3 * 全站下限 = 144`，把卡宽当成了热区的债主，158 当场被它判红。
        // 外观归外观、下限归下限：卡内那一族走自己的紧凑档，而卡片宽度不动。
        assertEquals("卡宽仍是 1.3.1 基线（横向档的固定宽；纵向档读铺满，不抄第二颗数）",
            158, SchemeCardDimens.CARD_WIDTH_DP)
        assertEquals(
            "卡高仍是那颗 150：横向档＝固定高，纵向档＝下限（同一颗数两种读法，没有第二份常量）",
            150, SchemeCardDimens.CARD_HEIGHT_DP
        )
        // 方向分叉本身要能判对错（纯函数，坏实现＝两档对调 ⇒ 这里当场红）
        assertTrue(
            "横向档卡高固定 ⇒ 内容层该用 weight + 卡内滚动兜住溢出",
            SchemeCardDimens.hasBoundedContentHeight(ReplyCardLayout.HORIZONTAL)
        )
        assertTrue(
            "纵向档卡按内容长高 ⇒ 内容层不许再拿无限高度去 weight/再嵌一条滚动",
            !SchemeCardDimens.hasBoundedContentHeight(ReplyCardLayout.VERTICAL)
        )
        assertEquals(6, SchemeCardDimens.TAG_TO_BODY_GAP_DP)
        assertEquals(6, SchemeCardDimens.TAG_HPAD_DP)
        assertEquals(3, SchemeCardDimens.TAG_VPAD_DP)
        // 热区层：卡内这一族小动作走本轮明写的紧凑档（[TouchTier.CARD_ACTION]，13dp 字形配 28dp 见方盒），
        // 它**有意低于**全站那颗下限——允许它低的理由只有下面那条几何证人，所以两样一起钉：
        // 把单颗抬回 48（三颗 144 撑爆 142 净宽、挤掉正文）与把它缩成别的数（点不到）都判红。
        assertEquals(
            "卡内三颗动作各按 " + TouchTier.CARD_ACTION.toInt() + "dp 见方那一档",
            3 * TouchTier.CARD_ACTION.toInt(), SchemeCardDimens.ACTION_ROW_WIDTH_DP
        )
        assertEquals(
            "单颗外盒不是卡内那一档", TouchTier.CARD_ACTION.toInt(), SchemeCardDimens.ACTION_BOX_DP
        )
        // 几何证人（这才是"可以低于 48"的唯一依据）：三颗合计必须真的放得进卡片自己的内容宽。
        // 内容宽 = 卡宽 - 左右各一处卡片内边距（同一个令牌，不抄第二份数）。
        // ⚠ 这条理由只对**横向档**说话（158dp 那张固定宽的卡）；纵向档宽度铺满、内容宽更大，
        // 但按钮一档**不跟着变**——§3 明写新档只换布局、不换按钮与热区，所以两档共用这三颗 28dp。
        val contentWidthDp =
            SchemeCardDimens.CARD_WIDTH_DP - 2 * Spacing.md.value.toInt()
        assertTrue(
            "三颗合计 " + SchemeCardDimens.ACTION_ROW_WIDTH_DP + "dp 放不进 " + contentWidthDp +
                "dp 卡内净宽（卡宽 " + SchemeCardDimens.CARD_WIDTH_DP + "、左右内边距各 " +
                Spacing.md.value.toInt() + "）⇒ 这一排要么出卡被裁成假热区，要么就得把卡片加宽，" +
                "紧凑档不成立",
            SchemeCardDimens.ACTION_ROW_WIDTH_DP <= contentWidthDp
        )
        // 字形仍坐在自己的盒里（13dp 的字不许配比它还小的点击盒）
        assertTrue(
            "字形 " + SchemeCardDimens.ACTION_GLYPH_SIZE_DP + "dp 装不进 " +
                SchemeCardDimens.ACTION_BOX_DP + "dp 的盒",
            SchemeCardDimens.ACTION_GLYPH_SIZE_DP <= SchemeCardDimens.ACTION_BOX_DP
        )
        // 两条轴没被混成一条：这一档**确实低于**全站下限（本轮只放行卡内这一族），
        // 而 [AppDimens.TOUCH_TARGET_MIN_DP] 自己仍写着 48，别处照旧按它判
        assertTrue(
            "单颗外盒 " + SchemeCardDimens.ACTION_BOX_DP + "dp 已经不低于全站下限 " +
                AppDimens.TOUCH_TARGET_MIN_DP + "dp ⇒ 这一档就不是'卡内紧凑档'了",
            SchemeCardDimens.ACTION_BOX_DP < AppDimens.TOUCH_TARGET_MIN_DP
        )
    }

    @Test
    fun `copy tick is a glance and never claims the message was sent`() {
        assertTrue("对钩显示时长不在'看一眼就收回'那一档", SchemeCopyFeedback.TICK_MS in 400L..2500L)
        val src = sourceOf("ui/panel/reply/SchemeCollapsedBlock.kt") +
            sourceOf("ui/panel/reply/SchemeCard.kt")
        for (banned in listOf("ActualSent", "RecordSent", "markSent", "onConfirmSent", "recordSent")) {
            assertTrue("$banned 不该出现在卡片里：复制不等于已发送", banned !in src)
        }
    }

    @Test
    fun `body type size is the v131 pair`() {
        val src = sourceOf("ui/panel/reply/SchemeCard.kt")
        assertTrue("正文字号漂了", Regex("BODY_FONT_SIZE\\s*=\\s*13\\.sp").containsMatchIn(src))
        assertTrue("正文行高漂了", Regex("BODY_LINE_HEIGHT\\s*=\\s*18\\.sp").containsMatchIn(src))
    }

    private fun sourceOf(rel: String): String {
        val root = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")
        return File(root, rel).readText(Charsets.UTF_8)
    }
}
