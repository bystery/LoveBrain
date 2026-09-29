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
 * §6.1 表第 3 行 `LbSection`：分区标题——**全站分区标题样式的唯一所有者**（表里那句
 * 「标题 + 可选说明 + 内容，不允许每页另造标题样式」就是这一颗）。
 *
 * 表里承诺三样。这一格把**内容**那一槽补上（补它不是为了好看：折叠档必须有内容才折得动），
 * 「可选说明」那一槽**仍然不开**——全仓没有一处需要它，开一个没人传的 `description`
 * 就是 `LbSettingRow` 那个「statusText 的值从来没被画出来过」的哑参数复发。
 * 等真有一页要说明，再连同它的字号、颜色、maxLines 一起定一次。
 *
 * ## 两档，一种标题外观
 *
 * - **常驻档**（`fold == null`）：首页那三段（快捷功能 / 服务设置 / 使用概览）。
 *   标题按文档流排，`content` 跟在它下面；标题节点自己带 [LbTags.SECTION] 锚点。
 *   这一档**不可点**（`DesignSystemRolesTest` 那一族判据盯着"不该可点的别进可交互集合"）。
 * - **可折叠档**（`fold != null`）：标题行**本身**就是一处操作，内容跟着折叠。
 *
 * ## 为什么「可折叠」是这一颗的一档，而不是第 12 个组件
 *
 * 加这一档的理由不是「某一页想要个折叠」，而是**折叠入口的契约在全仓被手抄成了三份、
 * 三份给的还不是同一件事**（本机把结果区那颗挂进语义树实量：`360x41dp`、`role=无`、
 * `stateDescription` 根本没设过；另两份的写法见 `SuggestPanel` 与 `CounselingPanel`）：
 *
 * | 折叠入口 | 热区下限写了吗 | 读屏说得出「现在收起/展开」吗 | 尾部那颗 |
 * |---|---|---|---|
 * | 锦囊建议卡 | 写了，页面私有 `SuggestDimens.FOLD_MIN_HEIGHT_DP` | 说得得出，走资源 | 页面自己的 `TriangleArrow` |
 * | 谈心「继续追问」 | 没写，靠 `padding` 垫出来的 | 说得得出，走资源 | 页面自己的 `TriangleArrow` |
 * | 结果区「进行中事项」（归并前） | **没写，实量 41dp，低于下限** | **没有 stateDescription** | 两个**内联中文**字 |
 *
 * 三行要的是同一件事：折叠入口得点得中、得说得出自己在哪一档。这一档收的就是那件事：
 * [AppDimens.TOUCH_TARGET_MIN_DP] 的下限、[Role.Button]、以及全站共用那两条状态公告资源
 * （`R.string.state_expanded` / `state_collapsed`，锦囊与谈心已经在用同一对，不是这页新造的词）。
 *
 * ## 这一档没有开任何外观旋钮
 *
 * 字号、字重、颜色、间距、圆角、进出场动画全部写死在这一处；调用方只能交出
 * 「标题、内容、折叠状态、尾部那颗」。尾部那颗必须是**槽**而不是字符串——与 `LbTopBar(trailing =)`
 * 同一口径：**页面自己的词留在页面**（设计系统一认具体页面，就得为下一页再改一次），
 * 而那两条词也因此继续记在 `Text(` 那一栏，不从文案预算里换个抽屉躲账（换桶不算还债）。
 *
 * 区块的底色与圆角**也不在这里**（`SurfaceInset` 那层卡底留在调用页面上）：表里这一行说的是
 * 标题与内容的关系，不是"分区必须长在卡里"——首页三段就没有卡底，把它做成参数等于
 * 让下一页自选第三种底色。
 *
 * 按压缩放这一档**没跟着设计系统其余几颗走**（它们用 [rememberPressScale] + `indication = null`）：
 * 归并前那一页用的是平台默认的点击反馈，顺手换成按压缩放是另一次改外观，不该混进这次收口。
 * 等锦囊与谈心那两颗也进来时，那一根轴整个设计系统定一次。
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
                // §6.5 :531 的下限写在这里、不交给页面：归并前这一行实量 41dp。
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
