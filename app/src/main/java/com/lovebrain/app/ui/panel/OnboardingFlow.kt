package com.lovebrain.app.ui.panel

import androidx.compose.foundation.background
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
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*

/**
 * 新手引导的**介绍层**——一张 LoveBrain 介绍页，可跳过（指导书 §9.1）。
 *
 * §9.1 的口径是"保留一张介绍页，后续都在**实际 App 主页面**用遮罩/高亮/箭头指向模型配置、
 * 无障碍入口、消息捕获"，**不再造第二、第三、第四张教学页**：
 * 1. 这一页只做一件事——用预置结果演示消息体验（不伪装在线生成），三条出口交给宿主；
 * 2. 真正的"配模型 / 给无障碍 / 开捕获"三步由首页罩子 `ui/home/HomeCoachMarks.kt` 在场时指，
 *    缺项与权限回来续接（`SetupViewModel.currentGuideCursor` 从真状态派生）。
 *
 * 已有用户不强制重走，也不重置权限和供应商。
 *
 * ## 两件事在这一格里改了（用户原话第 14 条）
 *
 * 1. **三条出口不再等于"引导结束"**：宿主 `SetupActivity` 把它们分别落成
 *    稍后 / 派生游标 / 清掉稍后，介绍层之外的引导以首页罩子形态继续（`ui/home/HomeCoachMarks.kt`）。
 *    这一颗 composable 只管把回调交出去，不写任何持久状态。
 * 2. **步号不再只住在内存里**：原先 `var currentStep by remember { mutableStateOf(0) }`
 *    在翻页、旋转与 Activity 重建之后一律回到第 0 格（这正是"一翻页就没了"的另一半）。
 *    现在步号归宿主（[currentStep] / [onStepChange]），宿主流转 `savedInstanceState`
 *    并在进程被杀之前把同一步落进 `SettingsStorePort`（那颗键要主线程加，见交接单 §4）。
 *
 * @param currentStep 现在第几格；单页那一档恒 0，越界由这一处钳回合法档，UI 不判第二本账
 * @param onStepChange 换步的唯一出口——**这一层不再自己 remember 步号**（今天一张页，没人调它）
 * @param onOpenSettings 宿主 `GuideExit.OpenSettings` 那条出口的入口。介绍层这一张页
 *   **没有**点它的按钮了（`8ef9a16` 撤掉"去设置"那颗就是为治"点去设置跳最后"），
 *   配置动作从介绍层收起后由首页罩子接手——签名留着是因为出口与落盘那本账还在。
 */
@Composable
fun OnboardingFlow(
    onSkip: () -> Unit,
    onComplete: () -> Unit,
    onOpenSettings: () -> Unit,
    currentStep: Int = 0,
    onStepChange: (Int) -> Unit = {}
) {
    val step = currentStep.coerceIn(0, LAST_STEP)

    // 第6节第1条 表里 `LbScreenScaffold` 那一行说的四件事，这一页原先自己拼了三层：
    // `Box(fillMaxSize).background(SurfaceBase).systemBarsPadding()` 套
    // `Column(fillMaxSize).padding(Spacing.xl)`。本机量到它的水平边距是 **16dp**，
    // 而另外两页是 **24dp**——"统一水平边距"这半句在改之前不成立，这一格把它收齐。
    // insets 保持它原本有的那一份（显式传 true，不靠默认），真机上够不够、
    // 会不会加两遍仍只能等设备定，账本 第36节 记着这条边界。
    LbScreenScaffold(handlesSystemBarInsets = true) {
            // 顶部——跳过。它**不能**换成 LbPrimaryButton：那一页的主动作已经有一颗，
            // 第6节第1条 要的是"页面唯一主动作"，把跳过也做成实心大按钮反而更糟。
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

            // §9.1「保留一张 LoveBrain 介绍页」：这一层今天**只有一张页**（`LAST_STEP = 0`）。
            // 原先这里套了一格 `AnimatedContent(targetState = step)`，而 body 永远画同一张页
            // ——那是一段永不发生的过渡，正是 §9 顶句"真实渐进，不是写个动画函数"点的那种形状，
            // 所以换成 `key(step)`：只给重组身份，不冒充入场动画。
            // 底下那三张旧页（`OnboardingStep1/2/3`）与 `ConfigItem` 已删：`8ef9a16`/`504cb65` 把
            // 这一层收成一张页之后它们再没有入口画（`OnboardingFlow` 只调 `OnboardingStep0`）。
            // 删页与改表同拍：`UiLayerDependencyContractTest` 的 surfacesLedger 里
            // `panel/OnboardingFlow.kt` 那一行（额度 1、实到 2 当场红）押的就是这两张死页里的
            // `.background(PrimaryLight)`；页删干净之后实到 0，按该账本"清零就删行"的规矩一并删行。
            // ⚠ 那两处品牌浅底**从没画到用户面前**（宿主只渲染步骤 0），所以删掉的不是可见功能；
            //   仍在用的介绍页那一盒是非品牌的 `SurfaceCard`，这次没动它。
            key(step) {
                OnboardingStep0(onComplete = onComplete)
            }
    }
}

/** 介绍层最后一格的号（越界钳制与宿主那面同一把尺） */
private const val LAST_STEP = 0

/** 步骤 0：一张介绍页——演示消息体验，完成后进入首页引导 */
@Composable
private fun OnboardingStep0(onComplete: () -> Unit) {
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
            text = "你可以复制任意一条，也可以点击卡片修改。完成后进入首页，会有箭头引导你配置模型。",
            style = AppTypography.labelSmall,
            color = TextHint
        )

        Spacer(Modifier.weight(1f))

        OnboardingButton(text = "完成，去配置模型", onClick = onComplete)
    }
}

@Composable
/**
 * 首次引导每一步的主动作——走 `LbPrimaryButton`。
 *
 * 之前它是一颗自己拼的 `Box.fillMaxWidth.background(Primary).clickable.padding(md)`，
 * 本机量到 **312x34dp**（第6节第5条 :531 的下限是 48dp）。形状本身和
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
