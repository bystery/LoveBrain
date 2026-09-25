package com.lovebrain.app.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.lovebrain.app.R

/** 自动化锚点：文字会变，tag 不变 */
object LbStatusTags {
    const val BADGE = "lb_status_badge"
}

/**
 * §6.1 表里 `LbStatusBadge` 那一行：**状态的颜色和文案只在这里有一份**。
 *
 * 指导书列的名字是 Running / Hidden / Off / Error 四个，这里有两处偏离，都写清楚：
 *
 * 1. **多了第五个 `NoPermission`**。首页搬家前的旧代码把"未授权"与"未启动"分开显示
 *    （`HomeScreen` 里两个分支），而这两件事用户要做的完全不同：去系统授权 vs 点一下启动。
 *    为了对上表格里的四个名字把它们并成 `Off`，等于少说一件事——所以不并。
 * 2. **`Error` 改名叫 `WindowMissing`**。它不是"占位的第四态"，`advisorStatus` 里有真实生产者
 *    （见那边的注释）；名字改掉是因为同包里已经有一个颜色叫 `Error`（`Color.kt`），
 *    枚举项再叫 `Error` 会在构造参数位置上撞名。含义不变：服务活着，窗口没起来。
 *
 * 颜色沿用搬家前的现状（`Running`=Primary、`Hidden`/`Off`/`NoPermission`=Neutral300），
 * 只有新增的 `WindowMissing` 用 Error 色——那一档旧代码里根本没有，旧代码在那种情况下
 * 说的是"运行中"。
 */
enum class LbStatus(
    @param:StringRes val labelRes: Int,
    val color: Color
) {
    Running(R.string.status_running, Primary),
    Hidden(R.string.status_hidden, Neutral300),
    Off(R.string.status_off, Neutral300),
    NoPermission(R.string.status_no_permission, Neutral300),
    WindowMissing(R.string.status_window_missing, Error)
}

/**
 * 状态小胶囊：浅色底 + 状态词。底色 = 状态色 15% 透明，字 = 状态色，
 * 这个配方以前内联在 `AssistantStatusCard` 里（`statusColor.copy(alpha = 0.15f)`），
 * 于是"状态长什么样"由调用方交进来的一个 `Color` 决定——谁能保证两个调用方交的是同一个色？
 *
 * 两条语义是 §6.5 要的，不是装饰：
 * - `contentDescription`：胶囊一旦被父容器合并语义，孤立 `Text` 可能不被单独播报；
 * - `liveRegion = Polite`：**状态变了补播一句**。军师从"运行中"变成"已隐藏"时，
 *   用户不必自己去找这一格。
 *
 * `stringResource` 在 `semantics {}` **外面**解析——语义 lambda 可能延后执行，
 * 在里面现调资源是本仓库踩过一次的坑（JVM 语义树上报错的形式特别难读）。
 */
@Composable
fun LbStatusBadge(
    status: LbStatus,
    modifier: Modifier = Modifier
) {
    val label = stringResource(status.labelRes)
    Box(
        modifier = modifier
            .clip(LoveBrainShape.full)
            .background(status.color.copy(alpha = 0.15f))
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .semantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            }
            .testTag(LbStatusTags.BADGE)
    ) {
        Text(
            text = label,
            style = AppTypography.labelSmall,
            color = status.color,
            fontWeight = FontWeight.SemiBold
        )
    }
}
