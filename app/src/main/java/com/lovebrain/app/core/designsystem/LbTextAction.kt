package com.lovebrain.app.core.designsystem

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
 * 语气。和 [LbButtonTone] 同理：**颜色来自词表，不是来自参数**（§6.1 :490 末句
 * "不许只在一个页面看起来不一样"——如果把 `color: Color` 开成参数，下一颗就会长成第三种颜色）。
 *
 * 这一张表原先只管文字；图标档进来之后它**同时是图标的那份墨色**（[LbTextActionTone.ink]
 * 两档共用），因为「删除」是红的不论它写成字还是写成 `×`。
 */
enum class LbTextActionTone {
    /** 这页/这块的引导动作：空态"去添加"、错误态"重试" */
    Accent,

    /** 次要到可以忽略：「跳过」「以后再说」 */
    Muted,

    /**
     * 行/卡里那一颗次级动作：「编辑」「导出」。
     *
     * 这一档是为 §6.1 归并 `RowActionButton` 加的，理由要写清：那颗小按钮自己画
     * `Box + Text + clickable`，热区只垫了高度（短标签本机实量 **32x48dp**），
     * 而且 `clickable` 没声明角色 —— 读屏只念那两个字，不念"按钮"。
     * 这两样正是本组件已经替全站修过一遍的。加一档**语气**而不是给它开一个
     * `color:` 旋钮，是因为旋钮一开就会长出第三种颜色（:490 末句）。
     *
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
 * 为什么是档位而不是一个 `size: Dp` 参数：这一族今天散落的字形尺寸只有三种数，
 * 而那个数在每一处都是**抄来的**（页头自己写 22、卡片自己写 13、行尾各自写 20）。
 * 开一个 `Dp` 口子就等于承认"任何一页都可以再选一个数"，而选出来的第三个数
 * 没有一处能被发现——"不许只在一页长得不一样"那条拦的就是这件事，[LbTopBarLevel] 与
 * [LbMetricDensity] 用的是同一种写法：二选一/三选一的具名档，不是自由参数。
 */
enum class LbTextActionGlyph {
    /** 页头返回那一颗：22dp 字形（原来写在 `LbTopBarDimens.BACK_ICON_DP`） */
    Header,

    /** 行尾/卡外那一族：`✓` `×` chevron `⋯`，20dp 字形（供应商表单那 8 颗就是这个数） */
    Inline,

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
        LbTextActionGlyph.Compact -> 13.dp
    }

/**
 * 两档共用的那一颗热区——**"这个形状"在这里画一次，别处不许再画**。
 *
 * 顺序是判据的一部分：`modifier`（调用方的 `testTag` 一类，不含尺寸）→
 * 两轴下限 → 按压缩放 → `clickable` → 状态/名字。`clickable` 一旦排到 `padding` 后面，
 * 内边距落在热区外面，等于白垫——这一族在本仓库被反复量到，写法已经钉在下面那格
 * 源码级合同里（`clickable` 必须排在内边距之前）。
 *
 * `contentDescription` 与 `selected` 都挂在**带 clickable 的这一个节点**上：
 * 挂在被合并掉的子节点上，语义树里读到的就是另一个所有者（角色那条要的是"这一颗自己"）。
 */
@Composable
private fun LbTextActionHotZone(
    modifier: Modifier,
    description: String?,
    selected: Boolean?,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "lbTextActionScale")
    Box(
        modifier = modifier
            .heightIn(min = LB_TEXT_ACTION_MIN_DP.dp)
            .widthIn(min = LB_TEXT_ACTION_MIN_DP.dp)
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
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 一颗**只有文字、或者只有一枚图标**的动作——两档共用同一颗组件、同一个热区。
 *
 * ## 契约改过一次，改在哪、为什么
 *
 * 这一格的契约原先写的是"只有文字、没有底色"。现在它是
 * **文字或图标，二选一档，同一颗组件**。
 * 改的原因不是"又想到一个用法"，是人已经明确定过调子：图标型动作**并进这颗现有组件**，
 * 不立第 12 颗设计系统组件。当时的另一条路是另起一颗 `LbIconAction`——那张 11 行的组件表就会
 * 涨到 12 行，而两颗组件各自持有"48 见方 + `Role.Button` + 按压缩放 + 名字挂在哪"这四件事，
 * **一定会分叉**：这一族的缺陷记录本来就散在不同的组件里——只垫高度量出 40x48dp
 * （`LbEmptyState` 第一版）、只垫高度量出 32x48dp（`RowActionButton`）、两轴都不够的
 * 40x32dp（消息行那颗删除）、`role=无`（首页两颗入口 + 那颗删除 + 逐条记忆那颗 `⋯`）、
 * 名字写在内层图标上被合并成念两遍（面板那颗收起钮改之前实测到「Collapse panel+Collapse
 * panel」）。多一颗主人，就是多一份"这四条再各自修一遍"的机会。并进来的代价也如实记着：两档共用同一张语气表（[LbTextActionTone.ink]），
 * 于是"页面想要另一种墨色"这条路彻底没有了。
 *
 * ## 名字为什么原先只在文字那一档存在
 *
 * §6.1 那张表里"文字动作"这个词是设计系统自己承认的（`LbEmptyState` 那行：
 * "图标、主说明、可选文字动作；动作热区 ≥48dp"），但在这一格之前它**只在
 * `LbEmptyState` 内部存在过**——页面想要一颗别的文字动作就没有地方放，于是
 * 每页自己画一个 `Text(...).clickable {}`。本格起因就是首次引导那颗「跳过」：
 * 语义树实量 **38x25dp**，而它是这屏唯一能让人退出流程的出口。
 * 图标那一族坏得更早也更广：页头私藏了一颗返回钮、方案卡私藏了一颗画三遍的表态钮、
 * 供应商表单私藏了一颗画八遍的 `IconAction`，加上首页两颗入口、消息行那颗删除、
 * 逐条记忆那颗 `⋯`——**同一个形状五个文件各写一遍**，其中四个调用点的 `clickable`
 * 连角色都没声明。
 *
 * ## 现在由这一颗保证、调用方拿不到旋钮的四件事
 *
 * 1. 热区 ≥ [LB_TEXT_ACTION_MIN_DP] **见方**——两轴都要垫。只垫高度不够，
 *    短标签会量出 40x48dp（`LbEmptyState` 的第一版就是这么被自家测试测红的），
 *    两轴都不垫更不够（消息行那颗删除量到 **40x32dp**），图标档同样躲不掉：
 *    字形只有 13~22dp，热区全靠这两条 `In` 垫出来；
 * 2. [Role.Button] 声明在**带 `clickable` 的那一个节点自己**上。名字同理：图标档把
 *    `description` 写在这一颗上，内层 `Icon` 一律 `contentDescription = null`——
 *    两处都写会被语义合并成「复制+复制」，读屏把同一个按钮念两遍（面板收起钮改之前
 *    实测到的就是这一串）；
 * 3. 按压缩放走全站那一处 [rememberPressScale]；
 * 4. 没有 `containerColor`、没有 `shape`、没有 `size: Dp`、没有 `tint: Color`。
 *    可选的只有：语气（[LbTextActionTone]，两档共用一张墨色表）、图标档的字形尺寸
 *    （[LbTextActionGlyph]，三档）、以及**状态**——`selected` 与 `enabled` 不是外观旋钮，
 *    它们决定读屏听不听得出"这颗现在在哪一格""这颗现在能不能按"。
 *
 * `clickable` 必须排在任何 `padding` **之前**：排在后面等于自己把热区又削掉一圈。
 *
 * ⚠ 图标档**不接**文字档那条 `Modifier.padding(horizontal = Spacing.lg)`：
 * 那条是给短标签补横向留白的，字形已经由 [LbTextActionGlyph] 定死，再加一层内边距
 * 会把热区撑到下限之外（卡片里三颗并排就挤出卡宽，`SchemeCardPresentationStateTest`
 * 钉的是"卡内放得下三颗下限"这条几何性质，不是放得下三颗下限+留白）。
 *
 * **现在这一颗是"文字动作"和"图标动作"两档唯一的主人**（§6.1 归并）：
 * `ui/common/RowAction.kt` 那颗 `RowActionButton` 与 `LbTopBar` 里私藏的返回钮
 * 都已改成转进这里。代价如实记在这儿：`RowActionButton` 原先那层浅灰胶囊底
 * （`TextSecondary` 8% 不透明）随归并消失了——要把底色带进来就得开一个 `containerColor`
 * 旋钮，而那一开就等于同意"下一颗可以再长一种底色"；字号与颜色留在语气词表里
 * （[LbTextActionTone.RowSecondary] / [LbTextActionTone.Destructive]），同进同退那条规矩一个字没破。
 */
@Composable
fun LbTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: LbTextActionTone = LbTextActionTone.Accent
) {
    LbTextActionHotZone(
        modifier = modifier,
        description = null,        // 文字档的名字就是那段字：再写一份 contentDescription 会被念两遍
        selected = null,
        enabled = true,
        onClick = onClick
    ) {
        Text(
            text = label,
            style = tone.style,
            color = tone.ink,
            modifier = Modifier.padding(horizontal = Spacing.lg)
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

