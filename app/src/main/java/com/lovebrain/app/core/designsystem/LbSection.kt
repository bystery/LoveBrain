package com.lovebrain.app.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R

/**
 * `LbSection`：分区标题——**全站分区标题样式的唯一所有者**（「标题 + 内容，不允许每页另造标题样式」）。
 *
 * 「可选说明」那一槽**不开**：全仓没有一处需要它，开一个没人传的 `description` 就是哑参数复发。
 * 等真有一页要说明，再连同它的字号、颜色、maxLines 一起定一次。
 *
 * ## 两档，一种标题外观
 *
 * - **常驻档**（`fold == null`）：标题按文档流排，`content` 跟在它下面，标题节点自己带
 *   [LbTags.SECTION] 锚点。这一档**不可点**（`DesignSystemRolesTest` 盯着"不该可点的别进可交互集合"）。
 * - **可折叠档**（`fold != null`）：标题行**本身**就是一处操作，内容跟着折叠。
 *
 * ## 为什么「可折叠」是这一颗的一档，而不是第 12 个组件
 *
 * 折叠入口要的那三件事在各页是同一件事：点得中、说得出自己在哪一档、尾部那颗是同一个形状。
 * 分成几颗组件各写一遍，就会各写各的（下限有的写有的靠 padding 垫、`stateDescription` 干脆没设、
 * 尾部字形各画一份）。这一档收的就是那件事：[AppDimens.TOUCH_TARGET_MIN_DP] 的下限、
 * [Role.Button]、以及全站共用那两条状态公告资源（`R.string.state_expanded` / `state_collapsed`）。
 *
 * ## 这一档没有开任何外观旋钮
 *
 * 字号、字重、颜色、间距、圆角、进出场动画全部写死在这一处；调用方只能交出
 * 「标题、内容、折叠状态、尾部那颗」。尾部那颗必须是**槽**而不是字符串——与 `LbTopBar(trailing =)`
 * 同一口径：**页面自己的词留在页面**（设计系统一认具体页面，就得为下一页再改一次）。
 *
 * 区块的底色与圆角**也不在这里**（`SurfaceInset` 那层卡底留在调用页面上）：这一行说的是
 * 标题与内容的关系，不是"分区必须长在卡里"——首页三段就没有卡底，做成参数等于让下一页自选第三种底色。
 *
 * 按压缩放**没跟着设计系统其余几颗走**（它们用 [rememberPressScale] + `indication = null`）：
 * 这里用的是平台默认点击反馈，换掉它是另一次改外观。等其余折叠入口也进来时，那一根轴整个设计系统定一次。
 */
class LbSectionFold(
    /** 现在展开还是收起——状态归调用方持有（哪一档的内容要跨屏活过重组，得由那屏决定） */
    val expanded: Boolean,
    val onToggle: () -> Unit,
    /**
     * 尾部那颗说得出状态的词或箭头。
     *
     * 可见的那一份归页面，**听得见的那一份归组件**：`stateDescription` 由 [LbSection] 自己挂，
     * 所以不许出现「画了个箭头、读屏却说不出这行现在是什么状态」那种半档。
     */
    val label: @Composable () -> Unit
)

@Composable
fun LbSection(
    title: String,
    modifier: Modifier = Modifier,
    fold: LbSectionFold? = null,
    content: (@Composable () -> Unit)? = null
) {
    val foldRow = fold
    if (foldRow == null) {
        // 常驻档：标题就是标题，内容跟在下面，不加任何容器（改了就会动首页那三段的版式）
        SectionTitle(
            title,
            modifier
                .padding(start = Spacing.sm, bottom = Spacing.sm)
                .testTag(LbTags.SECTION)
        )
        content?.let { it() }
        return
    }
    require(content != null) {
        "LbSection 的可折叠档必须有内容可折（标题「$title」这行没有 content，折叠入口就是空转）"
    }
    // 公告文案必须在 semantics 之外解析：那个 lambda 不是 composable 上下文。
    // 走全站那两条资源，而不是让这一页把它自己的两个中文字念给读屏。
    val announcement = stringResource(
        if (foldRow.expanded) R.string.state_expanded else R.string.state_collapsed
    )
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 下限写在这里、不交给页面：折叠入口的热区只由这一颗主人保证。
                // heightIn 排在 clickable 之前、padding 排在之后，热区才是整行而不是被自己垫掉一圈
                .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                .semantics { stateDescription = announcement }
                .clickable(role = Role.Button, onClick = foldRow.onToggle)
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle(title, Modifier.weight(1f).testTag(LbTags.SECTION))
            foldRow.label()
        }
        AnimatedVisibility(
            visible = foldRow.expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg)) {
                content?.let { it() }
            }
        }
    }
}

/**
 * 标题那一档——**这一颗组件里只许写这一遍**。
 * 两个档都走这里，所以「同一页又把标题调小一号」这件事没有落点。
 */
@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = AppTypography.titleMedium,
        color = TextPrimary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}
