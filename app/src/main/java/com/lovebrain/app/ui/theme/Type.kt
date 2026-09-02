package com.lovebrain.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 32.sp, letterSpacing = (-0.2).sp
    ),
    headlineLarge = TextStyle(
        fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp, letterSpacing = (-0.2).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp, letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp, letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp, fontWeight = FontWeight.Medium,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.Normal,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp, fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Normal,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Medium,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontSize = 11.sp, fontWeight = FontWeight.Medium,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp, fontWeight = FontWeight.Medium,
        lineHeight = 14.sp
    )
)

// ═══ Markdown 正文阅读规格（ 令牌化：谈心流式/结果与知识库预览共用规格；数值为现行规格，仅外放命名）═══
val MarkdownBodyFontSize = 13.sp
val MarkdownBodyLineHeight = 22.sp
