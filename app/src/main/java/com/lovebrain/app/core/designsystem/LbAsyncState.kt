package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 动作热区下限（dp）——§6.1 对 `LbEmptyState` 明写的"动作热区 ≥48dp" */
private const val MIN_ACTION_HOT_ZONE_DP = 48

/** 自动化锚点：状态换了tag不换，文字换了tag也不换 */
object LbAsyncTags {
    const val LOADING = "lb_async_loading"
    const val MESSAGE = "lb_async_message"
    const val ACTION = "lb_async_action"
}

/** 语气：空态是中性灰，错误态要把话说到"这里坏了" */
enum class LbStateTone { Neutral, Error }

/**
 * 统一的空态/错误态版式：一段主说明 + 一个可选动作。
 *
 * 三条硬规定（都来自 §6.1 那张组件表，不是审美偏好）：
 * - **动作是一处操作，不是一行文字**：热区 ≥48dp，且带 `Role.Button`；
 *   前例见 `MessageList` 那个 224x23dp 的蓝字入口——被引导去点的地方点不到，等于没有入口。
 * - 说明文字带 `contentDescription` 语义锚点，读屏/截图两边都能定位。
 * - 不允许再出现"每页自己画一个居中 Text"：那正是 §6.3 要消灭的东西。
 */
@Composable
fun LbEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    action: ScreenAction? = null,
    tone: LbStateTone = LbStateTone.Neutral
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = AppTypography.bodyMedium,
            color = if (tone == LbStateTone.Error) Error else TextHint,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { contentDescription = message }
                .testTag(LbAsyncTags.MESSAGE)
        )
        if (action != null) {
            Spacer(Modifier.height(Spacing.md))
            // 标签只在可点击这颗 Box 上声明一次；文字在里面居中。
            // 宽也要垫：§6.5 的下限是 48×48，不是"高够就行"。第一版只写了 heightIn，
            // 结果短标签（"重试"）这颗量出来是 40x48dp——被自家组件测红了一次。
            Box(
                modifier = Modifier
                    .heightIn(min = MIN_ACTION_HOT_ZONE_DP.dp)
                    .widthIn(min = MIN_ACTION_HOT_ZONE_DP.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = action.run
                    )
                    .testTag(LbAsyncTags.ACTION),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = action.label,
                    style = AppTypography.labelLarge,
                    color = Primary,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }
        }
    }
}

/**
 * 一个目的地的四态渲染器——§6.3 那四类状态的唯一出口。
 *
 * 它**不判**该显示哪一格：状态由持有者算好传进来。
 * 理由见 [ScreenState] 的注释——判据一旦在两处各写一遍，两边就会慢慢长得不一样
 * （本轮在 `KnowledgeRepository.listAll` 上刚撞到过一次同样的形状）。
 */
@Composable
fun <T> LbAsyncState(
    state: ScreenState<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    when (state) {
        ScreenState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = Primary,
                modifier = Modifier
                    .size(Spacing.xl)
                    .testTag(LbAsyncTags.LOADING)
            )
        }
        is ScreenState.Empty -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LbEmptyState(message = state.message, action = state.action)
        }
        is ScreenState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LbEmptyState(message = state.message, action = state.retry, tone = LbStateTone.Error)
        }
        is ScreenState.Content -> content(state.value)
    }
}
