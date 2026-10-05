package com.lovebrain.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.Warning
import com.lovebrain.app.core.designsystem.rememberPressScale

// ═════════════════════════════════════════════════════════════
// 首页这一屏自己的组件：一条状态卡 + 仅黄灯时那一行小字。
// 四入口不在这里——它们直接是设计系统 `LbActionCard` 的紧凑档，页面不再套一层。
// ═════════════════════════════════════════════════════════════

/**
 * 首页自己的版式尺寸。
 *
 * ⚠ 三轴分离：下面两颗都是**可见尺寸**那一轴（灯 12dp、字形 20dp）。
 * 触摸热区另写一处，走全站那一条 [AppDimens.TOUCH_TARGET_MIN_DP]，
 * 不给可见控件垫高再叠 padding——那正是"整站一起变大"的成因。
 */
private object HomeDimens {
    const val LAMP_VISIBLE_DP = 12            // 那颗小灯
    const val GLYPH_VISIBLE_DP = 20           // ▶ / ■ 的字形
}

/**
 * 首页专属的自动化锚点。
 *
 * 不按文案查的理由：这一屏的判据是**结构**（谁在哪一格、黄字那一行是不是只在黄灯时存在、
 * 四格是不是同一颗组件），拿中文当锚点的话改一句文案就把结构守卫弄红，而结构一个字没动。
 * 四入口的卡底用共用组件自己的锚点 [com.lovebrain.app.core.designsystem.LbTags.ACTION_CARD]。
 */
object LbHomeTags {
    const val STATUS_CARD = "lb_home_status_card"
    const val CONTROL = "lb_home_control"
    const val SETUP_HINT = "lb_home_setup_hint"
    const val ENTRY_KNOWLEDGE = "lb_home_entry_knowledge"
    const val ENTRY_FEEDBACK = "lb_home_entry_feedback"
    const val ENTRY_CAPTURE = "lb_home_entry_capture"
    const val ENTRY_PROVIDER = "lb_home_entry_provider"
}

/**
 * 最上面那一条状态卡：左边一颗小灯，右边 ▶ / ■。
 *
 * 交进来的只有 [AdvisorRender]——它是 [AdvisorStatus] 一次派生的产物。页面这里**不再判任何条件**：
 * 灯色、形状、点下去算开始还是算停止、那一行黄字念什么，全在 `render()` 那一处。
 *
 * ## 为什么这一层仍是自画（异形账本在册）
 *
 * 这一格要的是"一行里两处独立语义"：灯只读、只有右边那颗可点。
 * [com.lovebrain.app.core.designsystem.LbSettingRow] 交的是"一行一处操作 + 尾部弱动作"那种横排，
 * 它的状态点只有就绪/未就绪两档色；[com.lovebrain.app.core.designsystem.LbActionCard]
 * 把整张卡并成一处操作（第二处要么被吞进卡的名字，要么在卡里再叠一层 clickable）。
 * 而"三角/方块"这两个图形在设计系统里没有主人——为一个页面把它抬进 core 就是
 * "只在一页长得不一样"反着来的那条形状。
 */
@Composable
internal fun AssistantStatusCard(
    render: AdvisorRender,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = LoveBrainShape.lg,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = modifier
            .fillMaxWidth()
            .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
            .testTag(LbHomeTags.STATUS_CARD)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.xl, end = Spacing.md, top = Spacing.md, bottom = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            AdvisorLampDot(lamp = render.lamp, description = render.lampDescription)
            // 灯与控件之间那一段空白就是这一行：灯在左、控件在右，中间不摆别的东西
            Box(modifier = Modifier.weight(1f))
            AdvisorControlButton(
                control = render.control,
                description = render.controlDescription,
                // 形状与动作取自同一份派生物：画着三角就只会走 onPlay，画着方块就只会走 onStop
                onClick = if (render.control == AdvisorControl.Play) onPlay else onStop
            )
        }
    }
}

/** 左半边那颗灯：12dp 可见圆点，颜色只从 [AdvisorLamp.color] 那一处来（红/黄/绿） */
@Composable
private fun AdvisorLampDot(lamp: AdvisorLamp, description: String) {
    Box(
        modifier = Modifier
            .size(HomeDimens.LAMP_VISIBLE_DP.dp)
            .clip(CircleShape)
            .background(lamp.color)
            // 灯是这一格里唯一说"现在到哪一步"的东西，读屏得念得出；名字来自派生物
            .semantics { contentDescription = description }
    )
}

/**
 * 右半边那颗控件：热区垫到全站 48dp 那一档，**可见字形仍是 20dp**。
 *
 * 用户指定的形状（朝右三角 / 方块）用图形画，不拿文字按钮凑；
 * 名字与角色挂在带 `clickable` 的那一层盒子上，读屏念"开始军师服务/停止军师服务"。
 */
@Composable
private fun AdvisorControlButton(
    control: AdvisorControl,
    description: String,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "homeAdvisorControl")
    Box(
        modifier = Modifier
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.md)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = description }
            .testTag(LbHomeTags.CONTROL),
        contentAlignment = Alignment.Center
    ) {
        when (control) {
            AdvisorControl.Play -> AdvisorPlayGlyph()
            AdvisorControl.Stop -> AdvisorStopGlyph()
        }
    }
}

/** 朝右的实心三角：尖朝右就是用户说的那个"开始" */
@Composable
private fun AdvisorPlayGlyph() {
    Canvas(modifier = Modifier.size(HomeDimens.GLYPH_VISIBLE_DP.dp)) {
        val triangle = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(0f, size.height)
            close()
        }
        drawPath(triangle, Primary)
    }
}

/** 实心方块：点它就是把军师收摊（取消检查 + 停服务与悬浮窗） */
@Composable
private fun AdvisorStopGlyph() {
    Box(
        modifier = Modifier
            .size(HomeDimens.GLYPH_VISIBLE_DP.dp)
            .background(Primary, LoveBrainShape.sm)
    )
}

/**
 * 仅黄灯时那一行小号黄字：念的是"到底哪一步没做"。
 *
 * ⚠ 就是一行，不是一段说明：不写额外操作指引、不写异常堆栈、不写 URL 候选、不写状态机参数；
 * 多个缺项用 ` · ` 短分隔（在 `AdvisorMissing.describe` 那一处拼）。太长就自然换行——
 * 砍掉任何一条缺项都是骗人，所以这里不限 `maxLines`。
 */
@Composable
internal fun HomeSetupHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm)
            .testTag(LbHomeTags.SETUP_HINT),
        style = AppTypography.labelSmall,
        color = Warning,
        fontWeight = FontWeight.Medium
    )
}
