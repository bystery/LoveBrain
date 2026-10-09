package com.lovebrain.app.ui.panel.counseling

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.LbChip
import com.lovebrain.app.core.designsystem.LbChipInteraction
import com.lovebrain.app.core.designsystem.LbChipLabelAlignment
import com.lovebrain.app.core.designsystem.LbChipStyle
import com.lovebrain.app.core.designsystem.LbChipStyles
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceBase

/**
 * 模板 chip 尾部渐隐遮罩尺寸（仅本文件使用）。
 *
 * 高度 28 与那颗胶囊的**可见**高度同档（[AppDimens.CHIP_PANEL_HEIGHT_DP]）。
 */
internal object CounselingTemplateDimens {
    const val FADE_MASK_WIDTH_DP = 24
    const val FADE_MASK_HEIGHT_DP = 28
}

/**
 * 谈心快速模板 chip 行：未在谈心中、无结果时，显示常见困惑模板（全部 6 个，一行横向滚动）。
 * 点击模板直接填入输入框。
 */
@Composable
internal fun CounselingTemplateChips(
    onTemplateSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val templates = listOf(
        "她突然冷淡了怎么办",
        "我们吵架了该谁先低头",
        "她说了这句话什么意思",
        "怎么判断她喜不喜欢我",
        "暧昧期怎么推进关系",
        "她嫌我不够浪漫"
    )
    // 尾部渐隐遮罩，提示后面还有可滑动的 chip
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            templates.forEach { template ->
                TemplateChip(text = template) { onTemplateSelect(template) }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(CounselingTemplateDimens.FADE_MASK_WIDTH_DP.dp)
                .height(CounselingTemplateDimens.FADE_MASK_HEIGHT_DP.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, SurfaceBase)
                    )
                )
        )
    }
}

/**
 * 谈心这一族芯片的**紧凑档只有一个主人**（书 §9「谈心模板整组用同一紧凑档」）。
 *
 * 「整组」管的是谈心这一页里所有 [LbChip] 调用点：模板行那六颗（[PanelTemplateChipStyle]）、
 * 结果区那两颗动作（`CounselingPanel.kt` 的「继续追问」「清空重聊」）。改之前这一档被**抄了三遍**
 * （模板那颗在本文件 `neutral.copy(…)`，结果区两颗各自在页面里 `soft.copy(…)` / `neutral.copy(…)`），
 * 三处写的还是**不同形状**：模板走 `layeredTouch` 分层、结果区那两颗走单层，
 * 行数上限一处 1 一处缺省 `Int.MAX_VALUE`。抄三遍的下场不是"难看"，是**这一档随时会各自跑偏**：
 * [LbChipStyle.touchFloor] 的缺省值是 `true`，谁漏抄一句"不垫下限"，那颗就回到
 * `LbChip.kt` 单层那条"取 pillHeight 与下限里更大的那颗"链上 ⇒ 可见胶囊被撑到 48、
 * **它所在那一整行跟着占高 48**（旧台账第 12 条「谈心案例那一排太高」判的就是整行占位，
 * 不是胶囊自己多高）。所以现在只有一处能写这几个旋钮。
 *
 * 管的是**高度与热区这两轴**（可见胶囊多高、要不要垫到全局下限、标签贴哪儿、允许几行）；
 * **颜色与字阶不归它管**——那两轴是"这颗是什么身份"（追问是浅底主动作、清空是灰底次要），
 * 由各调用点从 `LbChipStyles` 的既有族里挑一颗 base 再 `.copy` 交进来。
 * 横向内边距同理留在调用点（模板那颗照 `neutral` 的 `Spacing.md`，结果区那两颗照 `Spacing.lg`，
 * 都是逐项抄改前那条链，本轮不改观感）。
 *
 * ⚠ `core/designsystem` 里那颗具名档（`LbChipStyles.panelChip`）本轮仍**没有**——
 * `LbChip.kt` 的 KDoc 已经在两处指向它，实体却还不存在，回复输入行那一族也还带着自己那颗同名 28
 * （`ReplyDimens.ROLE_CHIP_HEIGHT_DP`，住在 `reply/ReplyInput.kt`，别的席位地盘、本席不碰）。
 * 所以这一档先落在**谈心自己这一族**里；上提与合并是跨页决策，已登记交主线程，
 * 不在这里替公共件发明 API。
 *
 * 三轴分离后的三个数（§3.1 / §12.1 R12）：
 * · **可见**：胶囊 [AppDimens.CHIP_PANEL_HEIGHT_DP] 高、标签居中（`Center`）、竖内边距 0；
 * · **热区**：`layeredTouch=true` 分两层，外层透明盒负责语义与 clickable；
 *   `touchFloor=false` 不再垫到 48——横滚行里 chip 宽度充足，这一档高的点击区是可接受的紧凑视觉
 *   （TEAM_RULES §3：确实无法同时满足紧凑视觉和全局热区下限时，保留用户指定的紧凑视觉）；
 * · **相邻布局**：行高由这颗胶囊自己决定，不再是 48 外层盒——这正是 R12 要修的"整行仍占高"。
 */
internal fun counselingCompactChipTier(base: LbChipStyle): LbChipStyle = base.copy(
    labelAlignment = LbChipLabelAlignment.Center,
    paddingVertical = 0.dp,
    pillHeight = AppDimens.CHIP_PANEL_HEIGHT_DP.dp,
    layeredTouch = true,
    touchFloor = false,
    labelMaxLines = 1
)

/**
 * 模板行那六颗的档：base 取 `neutral`（`SurfaceInset` 底 + `Border` 描边 + `labelSmall` +
 * Medium + 左右 [Spacing.md] + 按压 0.92，逐项照改前那条链），高度与热区那一档
 * 由 [counselingCompactChipTier] 给——谈心整族只有那一个主人。
 */
private val PanelTemplateChipStyle = counselingCompactChipTier(LbChipStyles.neutral)

/**
 * 谈心模板 chip（需求11：折叠后仍保持统一 chip 样式）——形状归设计系统那颗 [LbChip]。
 *
 * 点下去是把模板填进输入框，不是"在哪一格" ⇒ [LbChipInteraction.Action]：
 * `Role.Button`（与改之前那条链上写的同一个角色），语义树里不发 `selected`。
 */
@Composable
private fun TemplateChip(text: String, onClick: () -> Unit) {
    LbChip(
        label = text,
        onClick = onClick,
        interaction = LbChipInteraction.Action,
        style = PanelTemplateChipStyle
    )
}
