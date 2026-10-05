package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 文字/图标动作的热区下限——**不另抄一个数**，就是全局那颗 [AppDimens.TOUCH_TARGET_MIN_DP]。
 *
 * 这颗数在本仓库只许写一次，理由见 Dimens.kt 上那段注释。
 */
const val LB_TEXT_ACTION_MIN_DP = AppDimens.TOUCH_TARGET_MIN_DP

/**
 * 语气。和 [LbButtonTone] 同理：**颜色来自词表，不是来自参数**——`color: Color` 一开成参数，
 * 下一颗就会长成第三种颜色。
 *
 * 这张表同时是图标档的那份墨色（[LbTextActionTone.ink] 两档共用）：「删除」是红的不论它写成字还是写成 `×`。
 */
enum class LbTextActionTone {
    /** 这页/这块的引导动作：空态"去添加"、错误态"重试" */
    Accent,

    /** 次要到可以忽略：「跳过」「以后再说」 */
    Muted,

    /**
     * 行/卡里那一颗次级动作：「编辑」「导出」。
     *
     * 加这一档**语气**而不是给行内动作开一个 `color:` 旋钮——旋钮一开就会长出第三种颜色。
     * 图标档里它是默认那一档：页头返回、卡片里的「复制」、面板行尾那颗 `✓` 用的都是这同一个墨色。
     */
    RowSecondary,

    /** 会把东西删掉/停用的那一行内动作：「删除」——字用错误色，热区与角色与上一档**完全一样** */
    Destructive
}

// 底色与字号写成扩展而不是枚举构造参数：构造参数里写 `Accent(Primary)` 时那个 `Primary`
// 会被解析成枚举项自己，不是同名的顶层颜色 val（`LbButtonTone` 上已经踩过一次）。
internal val LbTextActionTone.ink: Color
    get() = when (this) {
        LbTextActionTone.Accent -> Primary
        LbTextActionTone.Muted -> TextHint
        LbTextActionTone.RowSecondary -> TextSecondary
        LbTextActionTone.Destructive -> Error
    }

/**
 * 语气带着字号一起走。
 *
 * 不这么做的话，「跳过」为了复用这颗组件就得从 `labelMedium` 长成 `labelLarge`——
 * 那就是"复用"顺手改了一次外观。字号与颜色同属一种语气，就该同进同退。
 *
 * `RowSecondary` / `Destructive` 两档沿用被合并那颗原来的 `bodyMedium`（13sp）：
 * 归并换的是**所有者**，不是把行内动作顺手改成引导动作的字号。
 */
internal val LbTextActionTone.style: TextStyle
    get() = when (this) {
        LbTextActionTone.Accent -> AppTypography.labelLarge
        LbTextActionTone.Muted -> AppTypography.labelMedium
        LbTextActionTone.RowSecondary -> AppTypography.bodyMedium
        LbTextActionTone.Destructive -> AppTypography.bodyMedium
    }

/**
 * 图标档的**字形尺寸**——按"这颗图标住在哪"命名，不是按"这一页想要多大"。
 *
 * 为什么是档位而不是一个 `size: Dp` 参数：开一个 `Dp` 口子就等于承认"任何一页都可以再选一个数"，
 * 而选出来的第三个数没有一处能被发现。[LbTopBarLevel] 与 [LbMetricDensity] 用的是同一种写法：
 * 二选一/三选一的具名档，不是自由参数。
 */
enum class LbTextActionGlyph {
    /** 页头返回那一颗：22dp 字形（原来写在 `LbTopBarDimens.BACK_ICON_DP`） */
    Header,

    /** 行尾/卡外那一族：`✓` `×` chevron `⋯`，20dp 字形（供应商表单那 8 颗就是这个数） */
    Inline,

    /**
     * 列表行里那一族小动作：16dp 字形坐在 28dp 紧凑盒里（供应商行的星标/测试/编辑/删除）。
     *
     * 与 [Compact] 同热区不同字形：两颗不能合成一颗，否则行内图标会跟着卡片缩到 13dp，
     * 长模型名那一行又会被挤成两行。
     */
    RowIcon,

    /** 卡片内部挤着排的那一族小动作：复制/赞/踩，13dp 字形（原来写在 `SchemeCardDimens.ACTION_ICON_SIZE_DP`） */
    Compact
}

// 同上：尺寸写成扩展，枚举构造参数里写 `Header(22.dp)` 时那个数会被读成枚举项自己。
// 这三个数是**版式尺寸**，不是下限，所以它们不写成 `AppDimens.TOUCH_TARGET_MIN_DP` 的别名
// （那把"48 只许写一次"的尺会把它们误认成第二颗下限）。
internal val LbTextActionGlyph.glyphSize: Dp
    get() = when (this) {
        LbTextActionGlyph.Header -> 22.dp
        LbTextActionGlyph.Inline -> 20.dp
        LbTextActionGlyph.RowIcon -> 16.dp
        LbTextActionGlyph.Compact -> 13.dp
    }

/**
 * 字形档位**顺带决定的热区**——不开 `size:` 自由参数，理由与上面选档位的理由同一条。
 *
 * [LbTextActionGlyph.Compact] / [LbTextActionGlyph.RowIcon] 的归属是"住在 158dp 宽的卡片里、
 * 一排三颗"，那个位置**装不下** 3×48=144dp 的操作行（正文就没了），所以这两档的热区是
 * [AppDimens.CARD_ACTION_HIT_DP] 见方；[LbTextActionGlyph.Header] / [LbTextActionGlyph.Inline]
 * 仍在页面行尾/页头，走全站那颗 [LB_TEXT_ACTION_MIN_DP] 下限，一寸不改。
 */
internal val LbTextActionGlyph.hitSize: Dp
    get() = when (this) {
        LbTextActionGlyph.Compact -> AppDimens.CARD_ACTION_HIT_DP.dp
        LbTextActionGlyph.RowIcon -> AppDimens.CARD_ACTION_HIT_DP.dp
        LbTextActionGlyph.Header -> LB_TEXT_ACTION_MIN_DP.dp
        LbTextActionGlyph.Inline -> LB_TEXT_ACTION_MIN_DP.dp
    }

/**
 * 文字档的**形状档**——按"这颗住在哪、要不要带底"命名，不是开一个 `containerColor:` 旋钮。
 *
 * [RowCapsule] 是行内小动作的**可见**形状：[AppDimens.ROW_ACTION_COMPACT_MIN_HEIGHT_DP] 最小高 +
 * `TextSecondary` 8% 浅灰底 + full 圆角 + 水平 12 / 垂直 4dp 内边距，知识库卡「编辑/导出」与
 * 供应商行用的就是它。这一层底作为**具名档**放在公共件里，页面不许各自再画一份。
 * ⚠ 那颗 32 是**版式高度**、不是热区下限：胶囊档的热区两轴仍垫到全站那颗 [LB_TEXT_ACTION_MIN_DP]（48），
 * 由外层透明盒补足（见 [LbTextActionHotZone] 的两层写法）。
 */
enum class LbTextActionSize {
    /** 48dp 见方、无底：空态引导、错误重试、"跳过"这一族 */
    Standard,

    /** 可见 32dp 最小高 + 浅灰胶囊；热区两轴仍 ≥48：知识库卡「编辑/导出」、供应商行内动作 */
    RowCapsule
}

internal val LbTextActionSize.hitSize: Dp
    get() = when (this) {
        LbTextActionSize.Standard -> LB_TEXT_ACTION_MIN_DP.dp
        // 胶囊档的**可见**胶囊只有 32dp 高，但**热区**下限仍是全站那颗 [LB_TEXT_ACTION_MIN_DP]：
        // 两轴由外层透明盒垫到 48，可见胶囊自己留在 [capsuleVisualMinHeight] 那一档。
        // 过去这里直接把版式档 32 当成热区下限交出去，短标签量出 32x32dp——正是"只垫高度不算达标"
        // 那一族的宽度欠账（`RowActionSemanticsTest` 第6节第1条 判的两条红）。
        LbTextActionSize.RowCapsule -> LB_TEXT_ACTION_MIN_DP.dp
    }

/** 胶囊那一层 8% 的浅灰底：只有一个数、只在这里出现一次 */
internal val LbTextActionSize.capsuleColor: Color?
    get() = when (this) {
        LbTextActionSize.Standard -> null
        LbTextActionSize.RowCapsule -> TextSecondary.copy(alpha = 0.08f)
    }

/**
 * 胶囊那一档的**可见**最小高度（版式轴，不是热区下限）。
 *
 * 与 [hitSize] 分家：热区走外层透明盒的全站下限 [LB_TEXT_ACTION_MIN_DP]（48 两轴），
 * 可见胶囊只占 [AppDimens.ROW_ACTION_COMPACT_MIN_HEIGHT_DP]（32）那一档——
 * "点得到的面积 > 看见的框"就是 Dimens 给这颗矮档明写的用途（"需要更大的可点面积时由外层盒子补"）。
 */
internal val LbTextActionSize.capsuleVisualMinHeight: Dp?
    get() = when (this) {
        LbTextActionSize.Standard -> null
        LbTextActionSize.RowCapsule -> AppDimens.ROW_ACTION_COMPACT_MIN_HEIGHT_DP.dp
    }

/**
 * 两档共用的那一颗热区——**"这个形状"在这里画一次，别处不许再画**。
 *
 * 顺序是判据的一部分：`modifier`（调用方的 `testTag` 一类，不含尺寸）→
 * 两轴下限 → 按压缩放 → `clickable` →（胶囊档）内层可见胶囊 → 状态/名字。`clickable` 一旦排到
 * `padding` 后面，内边距落在热区外面，等于白垫——本仓库把它钉成源码级合同。
 *
 * 无底档（[LbTextActionSize.Standard] 与全部图标档）热区盒自己就是可见盒，画成**单层**；
 * 胶囊档（[LbTextActionSize.RowCapsule]）画成**两层**：外层透明盒把热区两轴垫到全站下限 [minHit]，
 * 内层才是带底、带圆角、带留白的可见胶囊（只占 [capsuleVisualMinHeight] 那一档版式高度）。
 * 这样"点得到的面积（48 两轴）"与"看见的框（32 高胶囊）"各归各轴——正是 Dimens 给那颗矮档明写的用途。
 *
 * `contentDescription` 与 `selected` 都挂在**带 clickable 的外层这一个节点**上：挂在子节点上，
 * 语义树读到的就是另一个所有者（角色那条要的是"这一颗自己"）。
 */
@Composable
private fun LbTextActionHotZone(
    modifier: Modifier,
    minHit: Dp,
    capsuleColor: Color? = null,
    capsulePadding: Boolean = false,
    capsuleVisualMinHeight: Dp? = null,
    description: String?,
    selected: Boolean?,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "lbTextActionScale")
    // 热区盒：两轴下限 + 按压缩放 + clickable + 名字/状态——两档共用，顺序即判据（clickable 在留白之前）。
    val hitZone = modifier
        .heightIn(min = minHit)
        .widthIn(min = minHit)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            enabled = enabled,
            onClick = onClick
        )
        // 只在"真的有这一项"时才挂语义：写一条 `contentDescription = null` 或
        // `selected = null` 进树，等于给读屏加一层它听不懂的噪声。
        .then(
            if (description == null) Modifier
            else Modifier.semantics { contentDescription = description }
        )
        .then(
            if (selected == null) Modifier
            else Modifier.semantics { this.selected = selected }
        )

    if (capsuleColor == null) {
        // 无底档：热区盒 == 可见盒，单层，一寸不改。
        Box(modifier = hitZone, contentAlignment = Alignment.Center) {
            content()
        }
    } else {
        // 胶囊档：外层是可点热区（≥48 两轴），内层是可见胶囊（只占 32 那一档版式高）。
        // 底色与留白都落在内层、位于外层 clickable 之内——padding 排在 clickable 之后就不削热区（坑表第 13 条）。
        Box(modifier = hitZone, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .clip(LoveBrainShape.full)
                    .background(capsuleColor)
                    .heightIn(min = capsuleVisualMinHeight ?: 0.dp)
                    .widthIn(min = capsuleVisualMinHeight ?: 0.dp)
                    // 胶囊档的留白落在热区内侧，所以底色盖得住这块留白，而点击面积一寸没少。
                    .then(
                        if (!capsulePadding) Modifier
                        else Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                    ),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}

/**
 * 一颗**只有文字、或者只有一枚图标**的动作——两档共用同一颗组件、同一个热区。
 *
 * 文字与图标合在一颗组件里，不另立 `LbIconAction`：两档各自持有"热区两轴 + `Role.Button` +
 * 按压缩放 + 名字挂在哪"这四件事一定会分叉。代价如实记着——两档共用同一张语气表
 * （[LbTextActionTone.ink]），"页面想要另一种墨色"这条路不存在。
 *
 * 这一颗保证、调用方拿不到旋钮的四件事：
 * 1. 热区**两轴都要垫**，垫到哪一档由形状的主人决定：文字档与 [LbTextActionGlyph.Header] /
 *    [LbTextActionGlyph.Inline] ≥ [LB_TEXT_ACTION_MIN_DP] 见方；[LbTextActionGlyph.Compact]
 *    （卡内一排三颗）垫到 [AppDimens.CARD_ACTION_HIT_DP] 见方——低于全局下限是**明写的例外**，
 *    因为 158dp 的卡放不进 3×48dp 的操作行，而卡片宽度是版式合同钉死的。
 *    只垫高度不够（短标签会在宽度轴不达标），两轴都不垫更不够；图标档同样躲不掉：
 *    字形只有 13~22dp，热区全靠这两条 `In` 垫出来；
 * 2. [Role.Button] 与名字都声明在**带 `clickable` 的那一个节点自己**上：图标档把 `description`
 *    写在这一颗上，内层 `Icon` 一律 `contentDescription = null`——两处都写会被语义合并成
 *    「复制+复制」，读屏把同一个按钮念两遍；
 * 3. 按压缩放走全站那一处 [rememberPressScale]；
 * 4. 没有 `containerColor`、没有 `shape`、没有 `size: Dp`、没有 `tint: Color`。
 *    可选的只有语气（[LbTextActionTone]）、图标档的字形尺寸（[LbTextActionGlyph]），以及
 *    **状态**——`selected` 与 `enabled` 不是外观旋钮，它们决定读屏听不听得出
 *    "这颗现在在哪一格""这颗现在能不能按"。
 *
 * `clickable` 必须排在任何 `padding` **之前**：排在后面内边距就落在热区外面，等于自己把热区削掉一圈。
 *
 * ⚠ 图标档**不接**文字档那条 `Modifier.padding(horizontal = Spacing.lg)`：那条是给短标签补横向
 * 留白的，字形已由 [LbTextActionGlyph] 定死，再垫一层会把热区撑到下限之外——卡片里三颗并排就挤出
 * 卡宽（`SchemeCardPresentationStateTest` 钉的是"卡内放得下三颗下限"这条几何性质）。
 */
@Composable
fun LbTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: LbTextActionTone = LbTextActionTone.Accent,
    size: LbTextActionSize = LbTextActionSize.Standard
) {
    val capsule = size.capsuleColor
    LbTextActionHotZone(
        modifier = modifier,
        // 文字档的**热区**两轴都走全站下限那颗 48（[LbTextActionSize.hitSize]）；
        // 胶囊档只在**可见**层收回 [capsuleVisualMinHeight]（32dp 版式档），点得到的面积一寸没少。
        minHit = size.hitSize,
        capsuleColor = capsule,
        capsulePadding = capsule != null,
        capsuleVisualMinHeight = size.capsuleVisualMinHeight,
        description = null,        // 文字档的名字就是那段字：再写一份 contentDescription 会被念两遍
        selected = null,
        enabled = true,
        onClick = onClick
    ) {
        Text(
            text = label,
            style = tone.style,
            color = tone.ink,
            // 胶囊档的留白已经在热区内侧那次 padding 里买过了，这里再垫一次就是双份内边距
            modifier = if (capsule != null) Modifier else Modifier.padding(horizontal = Spacing.lg)
        )
    }
}

/**
 * 图标档：一枚字形 + 一个名字。
 *
 * `description` 必须是**读屏听得出这颗在干什么**的那句（"返回""删除""测试连接"），
 * 而且要来自 `R.string.*`：这一族原先有五处把中文写死在 Kotlin 里，英文环境下
 * `values-en` 已经翻好了，读屏照样念中文。屏幕上看不到这句话，所以它不进 `Text(`，
 * 只走 `contentDescription`——写死中文的那几处连文案预算那把尺都看不见（盲区写在
 * `UiStringLiteralBudgetTest` 自己的注释里）。
 *
 * `selected` 只有"这一族里互斥/可反复表态"的那几颗需要（卡片的赞/踩）；
 * 普通动作交 `null`，语义树里就不会多出一条读屏会念的选中态。
 */
@Composable
fun LbTextAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: LbTextActionTone = LbTextActionTone.RowSecondary,
    glyph: LbTextActionGlyph = LbTextActionGlyph.Inline,
    selected: Boolean? = null,
    enabled: Boolean = true
) {
    LbTextActionHotZone(
        modifier = modifier,
        minHit = glyph.hitSize,
        description = description,
        selected = selected,
        enabled = enabled,
        onClick = onClick
    ) {
        Icon(
            imageVector = icon,
            // 名字只在上那一颗热区 Box 上声明一次；这里再声明一遍就是念两遍。
            contentDescription = null,
            tint = tone.ink,
            modifier = Modifier.size(glyph.glyphSize)
        )
    }
}

/**
 * 图标档的资源入口（`R.drawable.*` 那一族：`ic_copy`、`ic_close`、`ic_chevron_down`…）。
 *
 * 与 [ImageVector] 那一支**共用同一个热区**，只是取字形的方式不同；两支行数一样、
 * 判据一样，不存在"哪一支的热区/角色由谁保证"这种分家。
 */
@Composable
fun LbTextAction(
    iconRes: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: LbTextActionTone = LbTextActionTone.RowSecondary,
    glyph: LbTextActionGlyph = LbTextActionGlyph.Inline,
    selected: Boolean? = null,
    enabled: Boolean = true
) {
    LbTextActionHotZone(
        modifier = modifier,
        minHit = glyph.hitSize,
        description = description,
        selected = selected,
        enabled = enabled,
        onClick = onClick
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tone.ink,
            modifier = Modifier.size(glyph.glyphSize)
        )
    }
}

