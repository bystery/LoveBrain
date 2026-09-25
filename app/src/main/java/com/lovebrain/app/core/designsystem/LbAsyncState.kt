package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign

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
 * - **动作是一处操作，不是一行文字**：热区垫到见方，且带 `Role.Button`。这一条现在
 *   由 [LbTextAction] 那唯一一处实现负责——本文件过去自己画了一颗 `Box + Text`，
 *   与页面级那颗「跳过」是同一个形状、各写各的数，这一格把它并过去了。
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
            // 这颗以前是本文件自己画的 `Box + Text`，现在指回设计系统里那唯一一处
            // "文字动作"（`LbTextAction`）。三件事没变、只是不再重复：热区垫到见方、
            // Role.Button 只声明在外层盒上、短标签也要够宽（第一版只垫高度时"重试"
            // 量出 40x48dp，被自家测试测红过一次——那段记录搬去了 LbTextAction.kt）。
            // testTag 仍挂在**外层可点击盒**上：挪进里面的 Text 就成了"锚点找得到、
            // 按钮找不到"。
            LbTextAction(
                label = action.label,
                onClick = action.run,
                modifier = Modifier.testTag(LbAsyncTags.ACTION)
            )
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
