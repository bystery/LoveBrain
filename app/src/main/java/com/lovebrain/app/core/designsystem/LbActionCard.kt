package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 图标方块**住在哪一格**——按住处命名，不是按"这一页想要多大"。
 *
 * 为什么是具名档而不是一个 `size: Dp` 参数：那一开口就等于承认"任何一页都可以再选一个数"，
 * 而选出来的第三个数没有一处能被发现；[LbTextActionGlyph] 与 [LbMetricDensity] 用的是同一种写法。
 *
 * 两档之间**只动方块这一件事**：字形仍是 22dp、尾部箭头仍是 20dp（两档同数，
 * 所以它们不跟着这个 enum 走，也就没有"半行宽的卡把箭头也一起缩小"那种连带改外观）。
 * 并排的两张卡与整行宽的一张卡要的是同一种字号，差的只是那一格装得下几.dp。
 */
enum class LbActionCardIconBlock {
    /** 整行宽的一张入口卡：48dp 方块 */
    Standalone,

    /** 快捷功能那一族（半行宽、列间 12dp 并排）：方块跟着让到 40dp，字与箭头一寸不动 */
    GridCell
}

// 尺寸写成扩展而不是枚举构造参数：构造参数里写 `Standalone(48.dp)` 时那个数会被读成枚举项自己，
// 与 [LbTextActionGlyph.glyphSize] 同一课。48 这一颗是**版式**尺寸（恰好与触摸区下限同数），
// 它不跟随下限改——所以下面 `.size()` 那一处进的是"48 只许写一次"那把尺的白名单，
// 而不是换成 [AppDimens.TOUCH_TARGET_MIN_DP]：换成别名就等于宣布"方块是热区"，两件事会被绑死。
internal val LbActionCardIconBlock.blockSize: Dp
    get() = when (this) {
        LbActionCardIconBlock.Standalone -> 48.dp
        LbActionCardIconBlock.GridCell -> 40.dp
    }

/**
 * 快捷功能卡片——统一 LbActionCard。
 * 标题、说明、图标容器、箭头、按压、禁用状态完全同源。
 *
 * 图标方块那一档的尺寸不交给页面自己选：调用方只交出"这张卡住在哪一格"（[iconBlock]），
 * 数仍只有本文件这一处主人。字号、圆角、底色、描边、按压缩放与"整卡一处操作"一律没有旋钮。
 */
@Composable
fun LbActionCard(
    modifier: Modifier = Modifier,
    @androidx.annotation.DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    iconBlock: LbActionCardIconBlock = LbActionCardIconBlock.Standalone,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "actionCard_$title")
    Card(
        shape = LoveBrainShape.lg,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.lg)
            .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                // 整张卡就是一处操作，读屏得先说出"按钮"：
                // 手画的 Box/Card + clickable 不会像 Material Button 那样自带角色。
                role = Role.Button,
                onClick = onClick
            )
    ) {
        // 组件自己的锚点住在卡内层这一格，**不跟调用方的 `testTag` 挤同一条链**：
        // 同一个语义节点上撞进两条 testTag 时，外层的页面锚点（首页四格那四颗 ENTRY_*）会赢，
        // 组件的 ACTION_CARD 读数就地消失——首页结构守卫与视觉基线判"四入口确实是共用这一颗
        // LbActionCard"（）靠的就是每颗卡都带得出这个锚点；锚点挂在被顶掉的那条链上等于失明。
        // 内层是独立布局节点：不带走 clickable，整卡仍只有一处操作（可点读数不涨）。
        Column(modifier = Modifier.padding(Spacing.xl).testTag(LbTags.ACTION_CARD)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                // 图标方块：版式尺寸，取自 [LbActionCardIconBlock]——"这一格是整行宽的卡还是
                // 半行宽的一格"是唯一能改它的旋钮，页面拿不到一个自由 Dp。
                // 它与触摸区下限恰好同数，但**不跟随下限**改：所以 48 那颗写在档位表里、
                // 仍进"48 只写一次"那把尺的白名单，而不是写成下限的别名。
                .size(iconBlock.blockSize)
                .clip(LoveBrainShape.md)
                .background(PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = title,
                        tint = Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextHint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                title,
                style = AppTypography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                subtitle,
                style = AppTypography.labelSmall,
                color = TextHint,
                maxLines = 1
            )
        }
    }
}
