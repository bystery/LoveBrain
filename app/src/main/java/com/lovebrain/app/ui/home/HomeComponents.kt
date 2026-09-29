package com.lovebrain.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.LbButtonState
import com.lovebrain.app.core.designsystem.LbTextAction
import com.lovebrain.app.core.designsystem.LbTextActionGlyph
import com.lovebrain.app.core.designsystem.LbTextActionTone
import com.lovebrain.app.core.designsystem.LbPrimaryButton
import com.lovebrain.app.core.designsystem.LbStatusBadge
import com.lovebrain.app.core.designsystem.LbRowState
import com.lovebrain.app.core.designsystem.LbRowTags
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Neutral300
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.PrimaryDark
import com.lovebrain.app.core.designsystem.PrimaryLight
import com.lovebrain.app.core.designsystem.PrimarySubtle
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary

// ═════════════════════════════════════════════════════════════
// 首页统一组件集合
// ═════════════════════════════════════════════════════════════

/** 首页页面目的地——根级导航 */
sealed class HomeDestination {
data object Home : HomeDestination()
data object FeedbackCases : HomeDestination()
data object About : HomeDestination()
data object Providers : HomeDestination()
data object Usage : HomeDestination()
data object CaptureApps : HomeDestination()

companion object {
    /** Saver for rememberSaveable */
    val Saver = androidx.compose.runtime.saveable.Saver<HomeDestination, String>(
        save = { it::class.simpleName ?: "Home" },
        restore = { name ->
            when (name) {
                "FeedbackCases" -> FeedbackCases
                "About" -> About
                "Providers" -> Providers
                "Usage" -> Usage
                "CaptureApps" -> CaptureApps
                else -> Home
            }
        }
    )
}
}

/**
 * 首页自动化锚点（§6.2 四段结构 + §6.5 视觉基线都靠它定位）。
 *
 * 为什么不按文案查：那四段的判据是**结构**（谁在第几段、一颗还是两颗主按钮、
 * 三等分是不是真的三等分），拿中文当锚点的话，改一句文案就把结构守卫弄红，
 * 而结构其实没动——那正是本仓库反复写过的"文字会变，tag 不会"。
 */
object LbHomeTags {
    const val SECTION = "lb_home_section"
    const val ABOUT = "lb_home_about"
    const val STATUS_CARD = "lb_home_status_card"
    const val PRIMARY_BUTTON = "lb_home_primary_button"
    const val HIDE_BUTTON = "lb_home_hide_button"
    const val ACTION_CARD = "lb_home_action_card"
    const val SETTING_ROW = "lb_home_setting_row"
    const val METRIC_CELL = "lb_home_metric_cell"
}

/**
 * 首页顶部那颗 About 入口。
 *
 * 它从 `core/designsystem/LbTopBar` 里搬回来：尾部动作是**页面**的决定
 * （首页是 About，二级页是返回），而"关于"这个锚点也只对这一页有意义。
 *
 * 形状本身不是页面的决定，所以它现在走 [LbTextAction] 的图标档。旧的那一份
 * 是 `Box(48).clip(full).clickable {}` 里塞一枚 20dp 箭头，本机语义树读到的形状是
 * **热区够（48x48）但 `role=无`**，名字写死成内联中文 `"关于"`——
 * 英文环境下 `values-en` 早就翻成 "About" 了，读屏仍念中文。
 * 这两样现在由那一颗组件统一保证，这一处只留页级才有的 testTag。
 */
@Composable
fun HomeAboutEntry(onNavigateAbout: () -> Unit) {
    LbTextAction(
        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        description = stringResource(R.string.a11y_home_about),
        tone = LbTextActionTone.Muted,            // 墨色仍是 TextHint，一档没换
        glyph = LbTextActionGlyph.Inline,         // 字形仍是 20dp，外观不变
        onClick = onNavigateAbout,
        modifier = Modifier.testTag(LbHomeTags.ABOUT)
    )
}

/**
 * 悬浮军师状态卡——唯一主卡（§6.2 首页四段里的第 2 段）。
 *
 * 交进来的是**一份** `AdvisorStatus`，不是 (statusText, statusColor) 两个参数：
 * 旧签名允许调用方把"运行中"配成 Neutral300，编译器不拦、判据也不在一处。
 * 现在文案与颜色都由 `LbStatus` 那一张表决定，这一颗组件只负责摆。
 *
 * ## 它仍在异形账本里：这一层"表达了什么不同语义"（§6.1 末句要求逐处点名判读）
 *
 * `OddShapeOwnershipTest` 把它登记成 HAND_DRAWN，这是那一条要的"写明它为什么不是异形归并的漏网"。
 * 判的是**现有主人表达不了**，不是"懒得搬"：
 *
 * - `LbActionCard` —— 整张卡合并成**一处**操作，而 §6.2 第 2 段这一段里有**两处**独立出口
 *   （主按钮 + 只在可隐藏时出现的右上角那颗）。交给它，第二处要么被吞进卡的名字里
 *   （读屏把「打开军师」念成卡片的名字），要么在卡里再叠一层 `clickable` ——那正是
 *   `CaptureAppRowSemanticsTest` 盯着的"一颗变成两颗可点"的形状。签名里也没有状态胶囊槽与主动作槽。
 * - `LbSettingRow` —— 一行一处操作 + 一颗状态点 + 尾部弱动作，管的是"设置项"那种横排；
 *   这一段要的是居中的一段说明加一整颗主按钮，塞不进它的槽位。
 * - [com.lovebrain.app.core.designsystem.LbSection] —— 只管标题与内容的关系，
 *   它的 KDoc 自己写明"区块的底色与圆角不在这里"。
 * - 卡底那一档（`LoveBrainShape.xl` + `PrimaryLight` + `PrimarySubtle` 描边）全仓只此一处，
 *   而且它走的是 `containerColor` 那扇门 ⇒ 记在
 *   `UiLayerDependencyContractTest > brand tones painted through containerColor` 的 `home/HomeComponents.kt` 那一行。
 *   现在就把它抬进设计系统，等于为**一个**页面新增一颗只有一页用的组件——那是 §6.1 末句反着的形状。
 *
 * 这一层已经**不是**原样：那颗主按钮先前是自己画的 Material `Button`（品牌底从 `containerColor`
 * 进来，从没进过 :490 那份清单），已交回 [LbPrimaryButton]；状态胶囊先前自己写
 * `statusColor.copy(alpha = 0.15f)` 画一遍，已交回 [LbStatusBadge]；四段的判据先前是五份平行
 * `when`，已收成 [advisorStatus] 一份快照。剩下的这一层只持有"把这三样摆进同一张卡、
 * 并在角上挂第二颗出口"这个形状本身。等第二个页面真要同一张卡时，那一格连着把
 * `containerColor` 那笔账改小——不是在这一格先造主人。
 */
@Composable
internal fun AssistantStatusCard(
    status: AdvisorStatus,
    onButtonClick: () -> Unit,
    onHideClick: (() -> Unit)? = null
) {
    val description = stringResource(status.descriptionRes)
    val buttonText = stringResource(status.buttonRes)
    Card(
        shape = LoveBrainShape.xl,
        colors = CardDefaults.cardColors(containerColor = PrimaryLight),
        modifier = Modifier
            .fillMaxWidth()
            .border(AppDimens.BORDER_WIDTH_DP.dp, PrimarySubtle, LoveBrainShape.xl)
            .testTag(LbHomeTags.STATUS_CARD)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 右上次级隐藏图标（不另起一行）
            if (onHideClick != null) {
                // 形状走图标档；旧那一份是 `Box(48).clickable {}`（`role=无`、名字是内联中文）。
                // ⚠ 归并带走两处外观，都如实记在这儿：
                // ① 墨色从 `PrimaryDark` 落进词表里的 [LbTextActionTone.Accent]（`Primary`）——
                //    图标档没有 `tint` 旋钮，而为一个页面加一档颜色就是"不许只在一页长得不一样"那条拦的事；
                // ② 字形从 18dp 进到图标档那一档的 20dp（与首页那颗 About 箭头同数）。
                // 热区（48x48）与点击行为一个字没动，动的只有这一层色与这两个 dp。
                LbTextAction(
                    icon = Icons.Filled.Close,
                    description = stringResource(R.string.a11y_home_hide_bubble),
                    tone = LbTextActionTone.Accent,
                    glyph = LbTextActionGlyph.Inline,
                    onClick = onHideClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.md)
                        .testTag(LbHomeTags.HIDE_BUTTON)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 状态行：标题 + Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "悬浮军师",
                        style = AppTypography.titleLarge,
                        color = PrimaryDark,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(Spacing.md))
                    // 状态胶囊：配方（状态色 15% 底 + 状态色字 + 读屏语义）在 LbStatusBadge 里，
                    // 这里不再自己 `statusColor.copy(alpha = 0.15f)` 画一遍。
                    LbStatusBadge(status = status.badge)
                }
                Spacer(Modifier.height(Spacing.lg))
                // 状态说明
                Text(
                    description,
                    style = AppTypography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.lg))
                // 唯一主按钮。
                //
                // 原来这里是 Material `Button(colors = buttonColors(containerColor = Primary))`
                // + `.height(48.dp)`——本机语义树实量 **119x48dp、role=Button、名字来自资源**，
                // 也就是说**它几何一直是合格的**。所以这一笔不是修缺陷，是**归所有者**：
                // §6.1 :479 把"页面唯一主动作"这颗交给了 `LbPrimaryButton`，
                // 而 :490 禁的正是"同一语义在某一页长成另一样"。两处差别（`labelLarge`+SemiBold
                // vs `titleMedium`+Bold、无阴影、无触感、没有四态可表达）**不表达任何不同语义**。
                // 搬完之后首页也第一次能画"禁用/进行中"那两态（以前只能画 Idle）。
                LbPrimaryButton(
                    state = LbButtonState.Idle,
                    label = buttonText,
                    onClick = onButtonClick,
                    modifier = Modifier.testTag(LbHomeTags.PRIMARY_BUTTON)
                )
            }
        }
    }
}
