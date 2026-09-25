package com.lovebrain.app.core.designsystem

import androidx.compose.ui.unit.dp

// 第三步-1：从 ui/theme/Theme.kt 切出来——token 是设计系统的部分，Material 主题包装才是 ui 的。
// ═══ 间距标尺（section/touch 无调用方已删）═══
object Spacing {
    val xs = 2.dp
    val sm = 4.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
    val xxl = 20.dp
    val xxxl = 24.dp
}
