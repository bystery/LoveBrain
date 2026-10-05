package com.lovebrain.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.lovebrain.app.PanelBackdropOpacity

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

// ═══ 聊天气泡（2026-10-03 新需求：真实对话左白右绿）═══
/** 我方气泡专用绿（微信风格浅绿）。**只用于我方气泡**，不许外溢到按钮/导航/卡片。 */
// 我方气泡专用限定语义色（ 第5节第1条 起始值，对比度待真机核对）。
// ⚠ 小写十六进制是刻意的：门禁 `strip_ticket_ids.py` 的裸编号族会在大写的 FF-ARGB 字面量里
//   认出一个编号（本机实跑过，改小写前后色值完全一致）。
val ChatOutgoingBg: Color = Color(0xff95ec69)

// ═══ 表面色（亮色浅灰分层）═══
val SurfaceBase: Color get() = Color.hsl(250f, 0.10f, 0.96f)
val SurfaceCard: Color get() = Color.hsl(250f, 0.06f, 0.99f)
val SurfaceInset: Color get() = Color.hsl(250f, 0.08f, 0.92f)

// ═══ 面板背景浓度：压在面板底上的大面积卡片所走的**唯一一处**派生底色 ═══

/**
 * 面板背景浓度的**当前读数**：就是盘上那个整数百分比。
 *
 * 区间、刻度、默认值、非法值回落全都只在 [PanelBackdropOpacity] 那一把尺里，这里一个数都不重抄。
 * 提供它的宿主也只有一处：面板根把它的实时预览值送进子树。
 * 不在面板树里画的东西（首页、Activity、引导页）读到的是这里的默认值，
 * 也就是 [PanelBackdropOpacity.DEFAULT_PERCENT] 那一档 = 完全不透明 = 与这一笔之前逐字同形。
 */
val LocalPanelBackdropDensity = compositionLocalOf { PanelBackdropOpacity.DEFAULT_PERCENT }

/**
 * 大面积卡片底色 = 面板底那一层颜色的**同一个 alpha**，原样搬到卡上。
 *
 * 为什么这一颗不算第二把尺：它没有区间、没有刻度、没有默认值，体里连一个数字都没有，
 * 唯一的换算口就是 [PanelBackdropOpacity.alphaOf]，喂进去的还是面板底用的那同一个百分比。
 * 卡片因此永远不比面板底更实、也永远不自成一套档 —— 滑杆动一格，底和卡一起动。
 *
 * 卡片叠在面板底之上是**有意**的：卡那一层自己就透出聊天背景，而正文区比裸底多压一层，
 * 于是"看得出浓度跟着滑块变"与"正文还读得清"同时成立，不需要给卡片另开一格浓度。
 *
 * [base] 换的是脸不是浓度：与面板底同色的那类大容器传 [SurfaceBase]，正文卡走默认的 [SurfaceCard]。
 * ⚠ 不许拿它当通用 alpha 旋钮用，也不许在页面里再写 `SurfaceCard.copy(alpha = 0.85f)`
 * 那种各自定的分档 —— 同一件事抄回页面就是这一颗要消灭的东西。
 *
 * 只涂底：文字、图标、光标、描边都不乘这个系数；点击与穿透一个字都不碰。
 * 小件（胶囊、芯片、分隔线、图标底）不走这里，否则整屏糊成一层灰雾。
 */
@Composable
fun panelBackdropCardColor(base: Color = SurfaceCard): Color =
    base.copy(alpha = PanelBackdropOpacity.alphaOf(LocalPanelBackdropDensity.current))

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
