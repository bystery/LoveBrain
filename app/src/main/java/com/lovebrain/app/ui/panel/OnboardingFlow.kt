package com.lovebrain.app.ui.panel

import com.lovebrain.app.core.designsystem.rememberPressScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.lovebrain.app.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*

/**
 * 新手引导流程——可跳过的短流程。
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

    // §6.1 表里 `LbScreenScaffold` 那一行说的四件事，这一页原先自己拼了三层：
    // `Box(fillMaxSize).background(SurfaceBase).systemBarsPadding()` 套
    // `Column(fillMaxSize).padding(Spacing.xl)`。本机量到它的水平边距是 **16dp**，
    // 而另外两页是 **24dp**——"统一水平边距"这半句在改之前不成立，这一格把它收齐。
    // insets 保持它原本有的那一份（显式传 true，不靠默认），真机上够不够、
    // 会不会加两遍仍只能等设备定，账本 §36 记着这条边界。
    LbScreenScaffold(handlesSystemBarInsets = true) {
            // 顶部——跳过。它**不能**换成 LbPrimaryButton：那一页的主动作已经有一颗，
            // §6.1 要的是"页面唯一主动作"，把跳过也做成实心大按钮反而更糟。
            // 本机量到它原本是 **38x25dp**（:531 下限 48dp）。外观仍是"一行弱化的文字"，
            // 但热区/角色/按压这三件事不再由本页自己写一遍——上一格这里只能"借浮层那颗
            // 下限常量垫高度"（48 在页面级无处可依），现在设计系统有了 `LbTextAction`，
            // 弱化那一档的语气（labelMedium + TextHint）与原样式逐位相同。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                LbTextAction(
                    label = stringResource(R.string.onboarding_skip),
                    onClick = onSkip,
                    tone = LbTextActionTone.Muted
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
/**
 * 首次引导每一步的主动作——走 `LbPrimaryButton`。
 *
 * 之前它是一颗自己拼的 `Box.fillMaxWidth.background(Primary).clickable.padding(md)`，
 * 本机量到 **312x34dp**（§6.5 :531 的下限是 48dp）。形状本身和
 * `LbPrimaryButton` 一模一样（整宽、品牌底、白字加粗），也就是 :490
 * "只在一个页面看起来不一样的按钮"的标准样本——它不是看起来不一样，
 * 它是**同一个语义长出了第二份实现**，连热区都各修各的。
 */
private fun OnboardingButton(text: String, onClick: () -> Unit) {
    LbPrimaryButton(
        state = LbButtonState.Idle,
        label = text,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
}
