package com.lovebrain.app.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.theme.*

/**
 * F12: 新手引导流程——可跳过的短流程。
 *
 * 1. 演示消息体验角色（预置结果，不伪装在线生成）
 * 2. 添加供应商、模型与 Key（复用现有连接测试）
 * 3. 手动粘贴一句真实输入，生成第一条可复制回复
 * 4. 再按需配置悬浮窗、捕获权限与个人档案
 *
 * 已有用户不强制重走，也不重置权限和供应商。
 */
@Composable
fun OnboardingFlow(
    onSkip: () -> Unit,
    onComplete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBase)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.xl)
        ) {
            // 顶部——跳过按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val (skipInteraction, skipScale) = rememberPressScale(0.96f, "onbSkip")
                Text(
                    text = "跳过",
                    style = AppTypography.labelMedium,
                    color = TextHint,
                    modifier = Modifier
                        .graphicsLayer { scaleX = skipScale; scaleY = skipScale }
                        .clickable(interactionSource = skipInteraction, indication = null, onClick = onSkip)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            when (currentStep) {
                0 -> OnboardingStep0(
                    onNext = { currentStep = 1 }
                )
                1 -> OnboardingStep1(
                    onNext = { currentStep = 2 },
                    onOpenSettings = onOpenSettings
                )
                2 -> OnboardingStep2(
                    onNext = { currentStep = 3 }
                )
                else -> OnboardingStep3(
                    onComplete = onComplete,
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }
}

/** 步骤 0：演示消息体验 */
@Composable
private fun OnboardingStep0(onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "欢迎来到 LoveBrain",
            style = AppTypography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "这是一个帮你回消息的军师。先看看演示效果——以下消息和回复都是预置的，不是在线生成。",
            style = AppTypography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(Spacing.xl))

        // 演示对话
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(Spacing.lg)
        ) {
            Column {
                Text("她：今天好累啊，感觉整个人都被掏空了", style = AppTypography.bodyMedium, color = TextPrimary)
                Spacer(Modifier.height(Spacing.sm))
                Text("推荐：辛苦了，先歇会儿，跟我说说。", style = AppTypography.bodyMedium, color = Primary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(Spacing.xs))
                Text("清醒：累了就歇着，别硬撑。", style = AppTypography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(Spacing.xs))
                Text("俏皮：又被掏空了？那赶紧躺平。", style = AppTypography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(Spacing.xs))
                Text("温柔：我在，你先休息。", style = AppTypography.bodySmall, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "你可以复制任意一条，也可以点击卡片修改。接下来配置你的 AI 模型。",
            style = AppTypography.labelSmall,
            color = TextHint
        )

        Spacer(Modifier.weight(1f))

        OnboardingButton(text = "下一步：配置模型", onClick = onNext)
    }
}

/** 步骤 1：添加供应商、模型与 Key */
@Composable
private fun OnboardingStep1(
    onNext: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "配置 AI 模型",
            style = AppTypography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "LoveBrain 需要一个 AI 模型来生成回复。你可以在设置页添加供应商（如 DeepSeek）、模型名称和 API Key。",
            style = AppTypography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(Spacing.xl))

        OnboardingButton(text = "去设置页配置", onClick = onOpenSettings)

        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "配置完成后回来继续。",
            style = AppTypography.labelSmall,
            color = TextHint
        )

        Spacer(Modifier.weight(1f))

        OnboardingButton(text = "已配置，下一步", onClick = onNext)
    }
}

/** 步骤 2：手动粘贴真实输入 */
@Composable
private fun OnboardingStep2(onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "试试生成回复",
            style = AppTypography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "打开悬浮窗面板，粘贴一句对方说的话（或你自己的话），然后点击生成。你会看到四条不同风格的回复。",
            style = AppTypography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(Spacing.xl))

        // 演示入口提示
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PrimaryLight)
                .padding(Spacing.lg)
        ) {
            Text(
                text = "提示：在首页点击「启动悬浮窗」，然后在任意聊天 App 中打开面板即可使用。",
                style = AppTypography.labelMedium,
                color = PrimaryDark
            )
        }

        Spacer(Modifier.weight(1f))

        OnboardingButton(text = "我试过了，下一步", onClick = onNext)
    }
}

/** 步骤 3：按需配置 */
@Composable
private fun OnboardingStep3(
    onComplete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "更多配置（可选）",
            style = AppTypography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "以下配置可以让 LoveBrain 更好地帮你：",
            style = AppTypography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(Spacing.md))

        ConfigItem(text = "开启消息捕获——自动抓取对方消息") {
            onOpenSettings()
        }
        Spacer(Modifier.height(Spacing.sm))
        ConfigItem(text = "创建知识库——记录你和她的情况") {
            onOpenSettings()
        }
        Spacer(Modifier.height(Spacing.sm))
        ConfigItem(text = "配置个人档案——让回复更贴合你的语气") {
            onOpenSettings()
        }

        Spacer(Modifier.weight(1f))

        OnboardingButton(text = "完成", onClick = onComplete)
    }
}

@Composable
private fun ConfigItem(text: String, onClick: () -> Unit) {
    val (interaction, scale) = rememberPressScale(0.96f, "cfgItem")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = AppTypography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "→",
            style = AppTypography.bodyMedium,
            color = TextHint
        )
    }
}

@Composable
private fun OnboardingButton(text: String, onClick: () -> Unit) {
    val (interaction, scale) = rememberPressScale(0.96f, "onbBtn")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Primary)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AppTypography.labelLarge,
            color = androidx.compose.ui.graphics.Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}
