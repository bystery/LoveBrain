package com.lovebrain.app.ui.theme

import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════
// 暗色模式完全删除，全站固定亮色。
// 原则：背景浅灰非纯白，卡片比背景白一档；强调色降饱和；状态色配浅底。
// ══════════════════════════════════════════════════════════════

// ═══ 主题色（固定 DeepSeek 浅蓝风格，色相 230，不可修改；改色链已删）═══
private const val THEME_HUE = 230f

// ═══ 主色：固定 DeepSeek 浅蓝（亮色值）═══
val Primary: Color get() = Color.hsl(THEME_HUE, 0.72f, 0.60f)
val PrimaryDark: Color get() = Color.hsl(THEME_HUE, 0.66f, 0.46f)
val PrimaryLight: Color get() = Color.hsl(THEME_HUE, 0.85f, 0.93f)
val PrimarySubtle: Color get() = Color.hsl(THEME_HUE, 0.60f, 0.82f)

// ═══ 中性色标（亮色：900 最浅背景 → 50 最深文本；Neutral900 已删，无调用方）═══
val Neutral800: Color get() = Color.hsl(250f, 0.10f, 0.90f)
val Neutral700: Color get() = Color.hsl(250f, 0.09f, 0.84f)
val Neutral600: Color get() = Color.hsl(250f, 0.09f, 0.78f)
val Neutral500: Color get() = Color.hsl(250f, 0.08f, 0.72f)
val Neutral400: Color get() = Color.hsl(250f, 0.07f, 0.62f)
val Neutral300: Color get() = Color.hsl(250f, 0.06f, 0.52f)
val Neutral200: Color get() = Color.hsl(250f, 0.06f, 0.40f)
val Neutral100: Color get() = Color.hsl(250f, 0.05f, 0.22f)
val Neutral50: Color get() = Color.hsl(250f, 0.04f, 0.08f)

// ═══ 语义色（亮色版）═══
val Success: Color get() = Color.hsl(152f, 0.55f, 0.30f)
val SuccessBg: Color get() = Color.hsl(152f, 0.30f, 0.90f)
val SuccessBorder: Color get() = Color.hsl(152f, 0.40f, 0.70f)
val Warning: Color get() = Color.hsl(38f, 0.80f, 0.30f)
val WarningBg: Color get() = Color.hsl(38f, 0.50f, 0.90f)
val Error: Color get() = Color.hsl(0f, 0.70f, 0.48f)
val ErrorBg: Color get() = Color.hsl(0f, 0.30f, 0.92f)

// ═══ 表面色（亮色浅灰分层）═══
val SurfaceBase: Color get() = Color.hsl(250f, 0.10f, 0.96f)
val SurfaceCard: Color get() = Color.hsl(250f, 0.06f, 0.99f)
val SurfaceInset: Color get() = Color.hsl(250f, 0.08f, 0.92f)

// ═══ 文字层次（亮色偏冷深灰；TextHint 与 TextSecondary 保持层级差）═══
val TextPrimary: Color get() = Color.hsl(250f, 0.10f, 0.12f)
val TextSecondary: Color get() = Color.hsl(250f, 0.08f, 0.32f)
val TextHint: Color get() = Color.hsl(250f, 0.06f, 0.42f)

// ═══ 边框 ═══
val Border: Color get() = Color.hsl(250f, 0.06f, 0.82f)
val BorderLight: Color get() = Color.hsl(250f, 0.05f, 0.88f)

// ═══ 五维向量色（ 令牌化：HSL 参数逐位照抄自 LoveBrainPanelScreen 内联值）═══
val VectorIntimacy: Color get() = Color.hsl(225f, 0.65f, 0.38f)
val VectorTrust: Color get() = Color.hsl(160f, 0.50f, 0.30f)
val VectorCommitment: Color get() = Color.hsl(260f, 0.40f, 0.34f)
val VectorPassion: Color get() = Color.hsl(33f, 0.75f, 0.35f)
val VectorSecurity: Color get() = Color.hsl(358f, 0.70f, 0.36f)

// ═══ 方案标签（四色 TagA-D 体系已删，方案卡统一 Primary 色系）═══

// ═══ 兼容旧引用（已删：PanelBg/CardBg/BgInset 无调用方）═══

// ═══ 预设主题色方案（已删：选择器 UI 早已移除，THEME_PRESETS/ThemePreset 无调用方）═══
