package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 列表卡（页面族 F1 的"列表卡"那一档，基线 v1.1 §1 表 F1 行 + §3.4 + §3.6）。
 *
 * ## 它替代的是什么
 *
 * 母版是知识库一级页那张卡（`ui/KnowledgeBaseActivity.kt`）：白卡 + 一行标题
 * （`titleMedium` SemiBold 单行）+ 一行 `labelSmall` 元信息 + 行内 `RowActionButton`。
 * 那张卡今天**住在页面里**（异形账本 `KnowledgeBaseActivity.kt#KbCard`，母版本轮只读），
 * 于是每一页想复用它的版式就得再抄一遍壳——已踩案例页就是抄第二遍的那一处
 * （旧 `FeedbackCasesScreen.kt:301-335`：自绘 `Card` + `shadow(4)` + 正文当标题）。
 * 这一颗把壳的所有者交回设计系统：页面只交出**内容**，拿不到形状、字阶、行数、阴影与动作写法。
 *
 * ## 五个槽，各有各的硬档（基线 §3.6，D1 §③-9）
 *
 * | 槽 | 参数 | 档 | 谁被钉住 |
 * | --- | --- | --- | --- |
 * | 1 标题 | [title] | `titleMedium`15 SemiBold、**单行 + ellipsis**、必填 | 页面传不进 `maxLines`/字阶 |
 * | 2 状态 | [status] | 6dp 点 + `bodySmall`12 字，**点与字同一行、同进同退** | 颜色走 [LbRowState] 那张表 |
 * | 3 摘要 | [summary] | `bodyMedium`13、**最多 2 行** + ellipsis | 行数上限在组件里 |
 * | 4 元信息 | [meta] | `labelSmall`10、多条用 `｜` **合成一行** | 分隔符主人见 [lbMetaLine] |
 * | 5 动作行 | [actions] | ≤3 颗 [LbTextActionSize.RowCapsule]（可见 32 / 热区 48），删除恒 Destructive | 语气只能从三颗具名档里挑 |
 *
 * 三件没有旋钮的事，是这一颗存在的理由：
 * 1. **卡底 = `Border` 1dp + 无阴影**（基线 §3.4：描边管静息、阴影管浮起）。这里不许出现
 *    `.shadow(`——`ELEVATION_MAX` 那一档只属于 Sheet/弹窗/浮层与首页 hero。合同由
 *    `LbListCardContractTest` 按源码形状钉着（JVM 读不出像素，这一族的真实观感仍要截图）。
 * 2. **卡高由内容给**：本文件没有一处给卡体写 `heightIn(min = …)`。整颗组件的高是
 *    "槽 1 + 它下面真存在的那几槽"加出来的，空白槽不画、也不留幽灵文本节点（与
 *    `LbSettingRow` 的 subtitle/statusText 同一口径）。
 * 3. **动作只有一个所有者**（基线 §3.5）：这一行的每一颗都转进 [LbTextAction]，
 *    页面既拿不到 `Box(size(48)).clickable`，也拿不到 `TextButton + heightIn(min = …)`。
 *
 * [detail] 是第五槽之外唯一的补充位，也是母版没有的那一个概念：已踩案例要把
 * "展开看全文与上下文"画在**同一张卡里**。不给它一个槽，页面就得把展开内容画到卡外，
 * 那张白卡会当场裂成两层底——所以宁可多一个具名参数，也不少那一层容器。
 * 页面在这里交出的是 composable，不是形状旋钮：卡底、间距、字阶仍然一件都传不进来。
 */

/** 自动化锚点：槽位住在哪一颗，语义树上要认得出（文字会变，tag 不变） */
object LbListCardTags {
    const val CARD = "lb_list_card"
    const val TITLE = "lb_list_card_title"
    const val STATUS_ROW = "lb_list_card_status_row"
    const val STATUS_DOT = "lb_list_card_status_dot"
    const val STATUS_TEXT = "lb_list_card_status_text"
    const val SUMMARY = "lb_list_card_summary"
    const val META = "lb_list_card_meta"
    const val ACTION_ROW = "lb_list_card_action_row"
}

/**
 * 这一族的**文件私有**尺寸（`Dimens.kt` 的规矩：被 3 个以上文件复用的尺寸才上提）。
 *
 * ⚠ [STATUS_DOT_DP] 与 `LbSettingRow` 里那颗状态点是同一个数（6dp），但**两处各自画**：
 * 归并它得先动 `LbSettingRow`（本轮不属于任何一张列表卡的写入区）。缺的那一颗已经按
 * `LbListCardContractTest` 的说法登记在交接单里，等"状态点"上提成公共件再并。
 */
private object LbListCardDimens {
    const val STATUS_DOT_DP = 6f
    const val TITLE_MAX_LINES = 1
    const val SUMMARY_MAX_LINES = 2
    const val META_MAX_LINES = 1

    /** 动作行的颗数上限（基线 §3.6）：超出的槽位今天没有主人，见文件末那句 ⚠ */
    const val MAX_ACTION_SLOTS = 3
}

/**
 * 元信息那一行的分隔符。
 *
 * 它是**标点**，不是文案（不进 `res/`，也不是要翻译的那句话），所以由设计系统持有、全仓只写这一次。
 * 两边各留一个空格是抄母版的读法（`KnowledgeBaseActivity.kt`「阶段：X ｜ 已对话 N 轮」），
 * 不是这一颗自己新选的档。
 */
private const val META_SEPARATOR = " ｜ "

/**
 * 把若干段元信息合成一行（槽 4 的唯一画法）。
 *
 * 空白段直接丢：母版那一行从不写"暂无XX"，空字段排是 D1 §③-9 点名要治的那一条（A4）。
 * 页面要合并就调这一颗，不要自己 `joinToString("｜")`——分隔符长出第二个主人，
 * 下一次统一就只有一处生效。
 */
fun lbMetaLine(parts: List<String>): String = parts.filter { it.isNotBlank() }.joinToString(META_SEPARATOR)

/** [lbMetaLine] 的散参入口，与页面里"时间 ｜ 计数"那种两三段的写法对齐 */
fun lbMetaLine(vararg parts: String): String = lbMetaLine(parts.toList())

/**
 * 状态槽：一颗点 + 一句话。
 *
 * 两件事只有这一种形状：点用 [LbRowState] 那张表的颜色（就绪↔颜色在 core 里已经有一份，
 * 见 `LbRowState` 的 KDoc），字用 `bodySmall`12。
 * ⚠ 这里**不许**开 `color: Color`：旋钮一开，"这一格现在就绪没有"就变成每页自选的一对颜色。
 */
data class LbListCardStatus(val text: String, val tone: LbRowState)

/**
 * 动作行的一颗。
 *
 * **构造函数是私有的**——这是"删除恒 Destructive"的全部实现方式：语气不交出去，
 * 页面就只能从下面三颗具名档里挑一颗，删不掉那一档的红。
 * （`LbListCardContractTest` 用反射钉住"没有公开构造函数"这件事；开出 `tone:` 旋钮当场红。）
 *
 * 不用 `data class`：那会附带一颗公开的 `copy()`，等于把私有构造函数从后门发回去。
 */
class LbListCardAction private constructor(
    val label: String,
    val tone: LbTextActionTone,
    val onClick: () -> Unit
) {
    companion object {
        /** 行内次级动作（「编辑」「导出」这一族）——与母版那两颗同一档 */
        fun secondary(label: String, onClick: () -> Unit) =
            LbListCardAction(label, LbTextActionTone.RowSecondary, onClick)

        /** 这一格的引导动作（空态"去添加"那一族）：同一颗热区，只换语气 */
        fun primary(label: String, onClick: () -> Unit) =
            LbListCardAction(label, LbTextActionTone.Accent, onClick)

        /** 会把东西删掉/停用的那一颗：恒 [LbTextActionTone.Destructive]，页面改不了 */
        fun destructive(label: String, onClick: () -> Unit) =
            LbListCardAction(label, LbTextActionTone.Destructive, onClick)
    }
}

/**
 * F1 族列表卡：五个槽位 + 整卡一处操作。
 *
 * [title] 必填（类型上就不可空）；空白时不画那一行、也不留幽灵文本节点——
 * "无标题业务真源时不许编标题"是 D1 §③-9 明写的，页面自己截出来的那句不算编标题的话
 * 请传真实在的那一句（已踩案例传的是回复首句）。
 *
 * [onClick] 交 `null` 时这张卡不可点（纯容器），此时**不挂** `Role.Button`：
 * 读屏念出一颗点不动的按钮比念不出更糟。
 */
@Composable
fun LbListCard(
    title: String,
    // `modifier` 必须是**第一个可省参数**（lint `ModifierParameter`）：这一颗是设计系统的公开卡，
    // 调用方给的外框要排在内容槽之前；全部调用点都用具名实参，换序不动语义（本轮 L1b 的三处 + 测试两处）。
    modifier: Modifier = Modifier,
    status: LbListCardStatus? = null,
    summary: String? = null,
    meta: List<String> = emptyList(),
    actions: List<LbListCardAction> = emptyList(),
    onClick: (() -> Unit)? = null,
    detail: (@Composable () -> Unit)? = null
) {
    Card(
        shape = LoveBrainShape.lg,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = modifier
            .fillMaxWidth()
            // 基线 §3.4：静息结构由 1dp 描边管，阴影那一档不在列表卡上。
            // 描边圆角 == 卡片圆角（D1 §8 规则 R2），两档都取自 LoveBrainShape.lg，只写一次。
            .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
            .then(
                if (onClick == null) Modifier
                else Modifier.clickable(role = Role.Button, onClick = onClick)
            )
            .testTag(LbListCardTags.CARD)
    ) {
        // 卡内边距 = Spacing.lg（12，基线 §3.3 的"卡内 16→12"）。高由内容给，所以这里没有 heightIn。
        Column(modifier = Modifier.padding(Spacing.lg)) {
            // ── 槽 1 标题 ──────────────────────────────────────────────────────
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = AppTypography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = LbListCardDimens.TITLE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().testTag(LbListCardTags.TITLE)
                )
            }

            // ── 槽 2 状态：点与字同一行，缺一不画另一 ─────────────────────────
            if (status != null && status.text.isNotBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag(LbListCardTags.STATUS_ROW)
                ) {
                    Box(
                        modifier = Modifier
                            .size(LbListCardDimens.STATUS_DOT_DP.dp)
                            .clip(CircleShape)
                            .background(status.tone.color)
                            .testTag(LbListCardTags.STATUS_DOT)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = status.text,
                        style = AppTypography.bodySmall,
                        color = TextSecondary,
                        maxLines = LbListCardDimens.META_MAX_LINES,
                        modifier = Modifier.testTag(LbListCardTags.STATUS_TEXT)
                    )
                }
            }

            // ── 槽 3 摘要：最多两行 ───────────────────────────────────────────
            if (!summary.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = summary,
                    style = AppTypography.bodyMedium,
                    color = TextSecondary,
                    maxLines = LbListCardDimens.SUMMARY_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().testTag(LbListCardTags.SUMMARY)
                )
            }

            // ── 槽 4 元信息：合并成一行 ───────────────────────────────────────
            val metaLine = lbMetaLine(meta)
            if (metaLine.isNotBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = metaLine,
                    style = AppTypography.labelSmall,
                    color = TextHint,
                    maxLines = LbListCardDimens.META_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().testTag(LbListCardTags.META)
                )
            }

            // ── 槽 5 动作行：右对齐、最多三颗、同一颗公共件 ────────────────────
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.md))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth().testTag(LbListCardTags.ACTION_ROW)
                ) {
                    // 母版的读法：元信息占余宽、动作贴尾部（KnowledgeBaseActivity.kt）。
                    // 这一行没有第二颗"主按钮"——基线 §3.5：同一排只允许一颗。
                    Box(modifier = Modifier.weight(1f))
                    actions.take(LbListCardDimens.MAX_ACTION_SLOTS).forEach { action ->
                        LbTextAction(
                            label = action.label,
                            onClick = action.onClick,
                            tone = action.tone,
                            size = LbTextActionSize.RowCapsule
                        )
                    }
                }
            }

            // ── 展开层（第五槽之外的唯一补充位，理由见文件头那段）────────────
            if (detail != null) {
                Spacer(Modifier.height(Spacing.md))
                detail()
            }
        }
    }
}

