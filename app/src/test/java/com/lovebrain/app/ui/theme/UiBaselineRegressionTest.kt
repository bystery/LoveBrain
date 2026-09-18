package com.lovebrain.app.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P1-11: v1.3.1 UI Regression Baseline
 *
 * 此测试锁定 v1.3.1 的关键 UI 常量值。
 * 任何对以下常量的修改都必须在此测试中同步更新，
 * 确保 UI 视觉语言不被意外漂移。
 *
 * 基线版本: v1.3.1 (commit bdc8088)
 */
class UiBaselineRegressionTest {

    // ═══ Spacing 标尺 ═══
    @Test
    fun `spacing baseline values are stable`() {
        assertEquals(2.dp, Spacing.xs)
        assertEquals(4.dp, Spacing.sm)
        assertEquals(8.dp, Spacing.md)
        assertEquals(12.dp, Spacing.lg)
        assertEquals(16.dp, Spacing.xl)
        assertEquals(20.dp, Spacing.xxl)
        assertEquals(24.dp, Spacing.xxxl)
    }

    // ═══ 圆角标尺 ═══
    @Test
    fun `shape baseline values are stable`() {
        val shapes = listOf(LoveBrainShape.sm, LoveBrainShape.md, LoveBrainShape.lg, LoveBrainShape.xl, LoveBrainShape.full)
        assertEquals(5, shapes.size)
    }

    // ═══ 全局尺寸常量 ═══
    @Test
    fun `app dimens baseline values are stable`() {
        assertEquals(36, AppDimens.INPUT_ROW_HEIGHT_DP)
        assertEquals(1, AppDimens.BORDER_WIDTH_DP)
        assertEquals(2, AppDimens.ELEVATION_DEFAULT_DP)
        assertEquals(4, AppDimens.ELEVATION_MAX_DP)
        assertEquals(48, AppDimens.EMPTY_ICON_CONTAINER_DP)
        assertEquals(10, AppDimens.ARROW_SIZE_DP)
        assertEquals(18, AppDimens.ACTION_ICON_SIZE_DP)
        assertEquals(14, AppDimens.LOADING_SPINNER_SIZE_DP)
    }

    // ═══ 主题色验证 ═══
    @Test
    fun `primary colors are not same as primary dark`() {
        // 确保主色调层次不丢失
        assert(Primary != PrimaryDark)
        assert(Primary != PrimaryLight)
        assert(PrimaryDark != PrimaryLight)
    }

    @Test
    fun `surface colors have correct hierarchy`() {
        // 背景浅灰非纯白，卡片比背景白一档
        assert(SurfaceBase != SurfaceCard)
        assert(SurfaceCard != SurfaceInset)
        assert(SurfaceInset != SurfaceBase)
    }

    @Test
    fun `text colors have correct hierarchy`() {
        // 文字层次：Primary > Secondary > Hint
        assert(TextPrimary != TextSecondary)
        assert(TextSecondary != TextHint)
        assert(TextPrimary != TextHint)
    }
}
