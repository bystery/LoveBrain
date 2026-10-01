package com.lovebrain.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbScreenScaffold
import com.lovebrain.app.core.designsystem.LbTopBar
import com.lovebrain.app.core.designsystem.LbTopBarLevel
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.viewmodel.SetupViewModel
import com.lovebrain.app.viewmodel.costReadout

/**
 * 使用概览详情页——累计统计指标。
 *
 * §6.3 那四格在这一屏**没有对应的流**，判过所以不接 `LbAsyncState`：六个数全部走
 * `SetupViewModel` 那一族同步属性（`totalGenerateCount` … `adoptRate`，实现是
 * `get() = securePrefs.…`，底下是 `prefs.getInt(key, 0)` 与 `toDoubleOrNull() ?: 0.0`），
 * 组合期一次读完就有答案——没有"第一次数据还没到"的窗口，也没有会失败的读
 * （坏值自己退成 0，不往外抛）。而"全 0"在这屏是**一个答案**（这台机器还没生成过），
 * 不是成功但为空：画一张空态图等于把"你还没用过"说成"这里读不到东西"。
 * 这屏的数归数据层那一侧的 `SecurePrefs` 计数字段，页只负责把数念出来。
 * ⚠ 同一族属性**没被观察**（不是 `StateFlow`），计数字段变了这一屏不会自己重组——
 *   那是"这里没有流"的另一半证据，也是它自己的账，不归 §6.3 这一格动。
 */
@Composable
fun UsageDetailScreen(
    viewModel: SetupViewModel,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    // §6.1 :478：外框（整屏底色 + 统一水平边距）交回 `LbScreenScaffold`。
    // 改之前这一页自己拼 `Column(fillMaxSize).verticalScroll().padding(horizontal = xxxl)`，
    // 与首页同一族手拼外框；24dp 那一档与脚手架恰好同数却不是同一个所有者。
    // 页头仍留在滚动柱子里（与首页同款"整页一起滚"），不交去 topBar= 槽。
    LbScreenScaffold {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // §6.1 :479：与"关于"页同一族手拼页头，那颗返回钮的 contentDescription 实测是空串。
            LbTopBar(
                title = "使用概览",
                level = LbTopBarLevel.Page,
                onBack = onBack
            )

            Card(
                shape = LoveBrainShape.lg,
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text("累计统计", style = AppTypography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("生成次数：${viewModel.totalGenerateCount}", style = AppTypography.bodyMedium, color = TextSecondary)
                    Text("复制次数：${viewModel.totalCopyCount}", style = AppTypography.bodyMedium, color = TextSecondary)
                    Text("采用次数：${viewModel.totalAdoptCount}", style = AppTypography.bodyMedium, color = TextSecondary)
                    Text("改写次数：${viewModel.totalRewriteCount}", style = AppTypography.bodyMedium, color = TextSecondary)
                    // 与首页那一格**同一颗判据**（`viewmodel/UsageStats.kt` 的 `costReadout`）。
                    // 一笔可计价记录都没有 ⇒ 念「—」；以前 `< 0.01` 一律念「￥0」，
                    // 对拿不到 usage / 没有价格表的自定义 Provider 就是把"不知道"说成"免费"。
                    val costStr = costReadout(
                        yuan = viewModel.totalCostYuan,
                        unknownText = stringResource(R.string.cost_unknown),
                        belowCentText = stringResource(R.string.cost_below_cent, HOME_COST_CURRENCY),
                        amountText = { HOME_COST_CURRENCY + String.format("%.2f", it) }
                    )
                    Text(
                        stringResource(R.string.cost_stated_row, costStr),
                        style = AppTypography.bodyMedium, color = TextSecondary
                    )
                    val rateStr = if (viewModel.totalGenerateCount > 0) "${(viewModel.adoptRate * 100).toInt()}%" else "—"
                    Text("采用率：$rateStr", style = AppTypography.bodyMedium, color = TextSecondary)
                }
            }
        }
    }
}
