package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// 第三步-1：从 ui/theme/Theme.kt 切出来（与 Spacing.kt 同一批；文件名跟着内容走，别叫 Spacing.kt 却装着形状）。
// ═══ 圆角标尺（柔和化：大圆角更显专业温和）═══
object LoveBrainShape {
    val sm = RoundedCornerShape(6.dp)
    val md = RoundedCornerShape(10.dp)
    val lg = RoundedCornerShape(16.dp)
    val xl = RoundedCornerShape(24.dp)
    val full = RoundedCornerShape(999.dp)
}
