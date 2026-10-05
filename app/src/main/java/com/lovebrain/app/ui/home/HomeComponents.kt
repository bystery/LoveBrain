package com.lovebrain.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbTextAction
import com.lovebrain.app.core.designsystem.LbTextActionSize
import com.lovebrain.app.core.designsystem.LbTextActionTone
import com.lovebrain.app.core.designsystem.LbTriangleGlyph
import com.lovebrain.app.core.designsystem.LbTriangleGlyphShape
import com.lovebrain.app.core.designsystem.LbTriangleGlyphTone
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.Warning
import com.lovebrain.app.core.designsystem.WarningBg
import com.lovebrain.app.core.designsystem.rememberPressScale

// ═════════════════════════════════════════════════════════════
// 首页这一屏自己的组件：一条状态卡（hero 档）+ 仅黄灯时那一行缺项。
// 四入口不在这里——它们直接是设计系统 `LbActionCard` 的紧凑档，页面不再套一层。
//
// 依据基线 v1 §1（F2 首页族）+ §3.3/3.4/3.9/3.10 与 §4 的首页裁决（candidate-a 方向）：
// **精简不等于空**。这一屏的层级由三件事买出来，一件都不靠新增文案或新增状态：
// ① hero 状态卡浮起来（`Xl` 24 圆角 + `ELEVATION_DEFAULT` 2 + 四边等距 16），
//    灯旁边那格不再是一整块空 `Box`，而是屏幕上直接写出来的**状态名**（就是 `lampDescription`
//    那四个既有短句，不新造第五个状态）；
// ② 入口卡静在那儿（`Lg` 16 + 内 12 + 无阴影）——描边管静息、阴影管浮起，两档差值一眼读得出；
// ③ 缺项那一行有容器、有字号档，并且自带一颗**能点的去处**（设计系统现有的 `LbTextAction`
//    行内胶囊档，不新第四种动作写法）。
// ═════════════════════════════════════════════════════════════

/**
 * 首页自己的版式尺寸。
 *
 * ⚠ 三轴分离（§3.1）：下面两颗都是**可见尺寸**那一轴（灯 12dp、字形 20dp）。
 * 触摸热区另写一处，走全站那一条 [AppDimens.TOUCH_TARGET_MIN_DP]，
 * 不给可见控件垫高再叠 padding——那正是"整站一起变大"的成因。
 *
 * 两颗数与 1.3.1 逐字同值，本轮一个字没动：动的是它们**之间**那一格（原来是空 `Box`，
 * 现在是状态名文字），不是尺寸。
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
 *
 * 本轮加的两颗（各管一件读得出来的事，不是给旧锚点补同义词）：
 * - [LAMP]：灯现在只是**装饰**（状态名改由旁边那颗可见文字说），语义树里再没有一条
 *   `contentDescription` 指着它，所以判"灯在哪、可不可点"必须靠 tag。旧判据是拿
 *   `HOME_LAMP_*` 那句读屏名找它的——那一句话今天仍然在屏幕上，只是换了所有者：
 *   同屏出现"点的 contentDescription + 旁边同一串文字"会让读屏把状态念两遍。
 * - [SETUP_ACTION]：缺项行那颗可点去处，判"黄字旁边到底有没有出口"。
 */
object LbHomeTags {
    const val STATUS_CARD = "lb_home_status_card"
    const val LAMP = "lb_home_status_lamp"
    const val CONTROL = "lb_home_control"
    const val SETUP_HINT = "lb_home_setup_hint"
    const val SETUP_ACTION = "lb_home_setup_action"
    const val ENTRY_KNOWLEDGE = "lb_home_entry_knowledge"
    const val ENTRY_FEEDBACK = "lb_home_entry_feedback"
    const val ENTRY_CAPTURE = "lb_home_entry_capture"
    const val ENTRY_PROVIDER = "lb_home_entry_provider"
}

/**
 * 最上面那一条 hero 状态卡：左边一颗小灯 + 状态名，右边 ▶ / ■。
 *
 * 交进来的只有 [render]——它是 [AdvisorStatus] 一次派生的产物。页面这里**不再判任何条件**：
 * 灯色、形状、点下去算开始还是算停止、那一行黄字念什么，全在 `render()` 那一处。
 *
 * ## 为什么这一层仍是自画（异形账本在册）
 *
 * 这一格要的是"一行里两处独立语义"：灯只读、只有右边那颗可点。
 * [com.lovebrain.app.core.designsystem.LbSettingRow] 交的是"一行一处操作 + 尾部弱动作"那种横排，
 * 它的状态点只有就绪/未就绪两档色；[com.lovebrain.app.core.designsystem.LbActionCard]
 * 把整张卡并成一处操作（第二处要么被吞进卡的名字，要么在卡里再叠一层 clickable）。
 * 形状那一半本轮已经交回设计系统（[LbTriangleGlyph]）；留在页面的是**这一格的排布**，
 * 它不是第二颗按钮、也不是第二套圆角语言。
 */
@Composable
internal fun AssistantStatusCard(
    render: AdvisorRender,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = LoveBrainShape.xl,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        // 浮起来那一档：基线 v1 §3.4 只给首页 hero（与浮层/弹窗）这一档阴影。
        // 于是这一格**不再自己描边**——描边管静息（入口卡那一档）、阴影管浮起，
        // 同一张卡上双叠是 §3.4 明令禁止的那一种。
        elevation = CardDefaults.cardElevation(
            defaultElevation = AppDimens.ELEVATION_DEFAULT_DP.dp
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(LbHomeTags.STATUS_CARD)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            AdvisorLampDot(lamp = render.lamp)
            // 灯旁边那一格原来是整块空 Box（weight 1f 什么也不画）——现在它说得出这一档是谁：
            // 文字就是 `render.lampDescription` 那四个既有短句，六档状态各归其一，不新造第五个状态。
            // 只此一行：次级说明那半行要的是新文案（`res/values` 的写入权不在这一席），
            // 没有真源可指就不画，宁可少一行字也不编一行字。
            Text(
                text = render.lampDescription,
                modifier = Modifier.weight(1f),
                style = AppTypography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            AdvisorControlButton(
                control = render.control,
                description = render.controlDescription,
                // 形状与动作取自同一份派生物：画着三角就只会走 onPlay，画着方块就只会走 onStop
                onClick = if (render.control == AdvisorControl.Play) onPlay else onStop
            )
        }
    }
}

/**
 * 左半边那颗灯：12dp 可见圆点，颜色只从 [AdvisorLamp.color] 那一处来（红/黄/绿）。
 *
 * 它**不再带 contentDescription**：同一个名字现在写在旁边那颗可见文字上，
 * 两处都声明会被读屏念两遍（`SemanticsProbe.isDuplicatedAnnouncement` 那一族）。
 * 灯仍然是"读数不是第二颗按钮"：它没有 clickable，也没有角色。
 */
@Composable
private fun AdvisorLampDot(lamp: AdvisorLamp) {
    Box(
        modifier = Modifier
            .size(HomeDimens.LAMP_VISIBLE_DP.dp)
            .clip(CircleShape)
            .background(lamp.color)
            .testTag(LbHomeTags.LAMP)
    )
}

/**
 * 右半边那颗控件：热区垫到全站 48dp 那一档，**可见字形仍是 20dp**（两轴各自有主人）。
 *
 * 形状交回设计系统那颗 [LbTriangleGlyph]：▶ 与 ■ 出自同一颗件、同一个圆化比例
 * （§3.10：边长 × 0.18，20dp → 3.6dp），三个角真圆，不再是三条 `lineTo` 拼出来的尖 Path。
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
        LbTriangleGlyph(
            sizeDp = HomeDimens.GLYPH_VISIBLE_DP.dp,
            tone = LbTriangleGlyphTone.Accent,
            shape = when (control) {
                AdvisorControl.Play -> LbTriangleGlyphShape.TriangleRight
                AdvisorControl.Stop -> LbTriangleGlyphShape.StopSquare
            }
        )
    }
}

/**
 * 仅黄灯时那一行：一个有底的小容器 + 一颗能点的去处。
 *
 * ⚠ 就是一行，不是一段说明：不写额外操作指引、不写异常堆栈、不写 URL 候选、不写状态机参数；
 * 多个缺项用 ` · ` 短分隔（在 `AdvisorMissing.describe` 那一处拼，文案真源在 `AdvisorStatus`）。
 * 太长就自然换行——砍掉任何一条缺项都是骗人，所以这里不限 `maxLines`。
 *
 * ## 为什么这次给它加了容器
 *
 * 旧形状是一颗完全裸奔的 `Text`：没有底、没有边、没有出口，用户看到的就是"log 输出"。
 * 现在走"状态色配浅底"那一条既有语言（`WarningBg` 底 + `Warning` 字 + `Md` 10 圆角，
 * 与设计系统轻通知那一档同一配方），字阶从 `labelSmall` 10/14 抬到 `bodySmall` 12/18——
 * 它是**要用户读完去办事**的一句话，不是行脚注。
 * 同一档圆角（10）已经是这一屏既有的一档（入口卡里那块图标底），所以这一格没有长第四种圆角。
 *
 * 出口那颗走 [LbTextAction] 的行内胶囊档（[LbTextActionSize.RowCapsule]：可见 32、热区仍 48 两轴），
 * 不在页面自画 `Box + clickable`。`actionLabel == null` 就是这一档缺项**根本没有去处可给**
 * （"服务没起来""连接还没检查过"那一族），此时只画文字、不硬造一颗假按钮。
 */
@Composable
internal fun HomeSetupHint(
    text: String,
    actionLabel: String?,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.md)
            .background(WarningBg)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .testTag(LbHomeTags.SETUP_HINT),
            style = AppTypography.bodySmall,
            color = Warning,
            fontWeight = FontWeight.Medium
        )
        if (actionLabel != null && onAction != null) {
            LbTextAction(
                label = actionLabel,
                onClick = onAction,
                modifier = Modifier.testTag(LbHomeTags.SETUP_ACTION),
                tone = LbTextActionTone.Accent,
                size = LbTextActionSize.RowCapsule
            )
        }
    }
}
