package com.lovebrain.app.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lovebrain.app.core.designsystem.LbScreenScaffold
import com.lovebrain.app.core.designsystem.LbTopBar
import com.lovebrain.app.core.designsystem.LbTopBarLevel
import com.lovebrain.app.core.designsystem.Spacing

/**
 * 页头——**只是 `LbTopBar` 的一个薄壳**。
 *
 * "标题、副标题、返回等单一尾部动作"归一颗组件：这一族不许自己量行高、自己挂返回钮、
 * 自己画分割线。返回钮的名字**必须来自 `R.string.*`**：硬编码 `"返回"` 在英文环境下
 * 资源已经翻成 "Back" 它还是念中文（`PageHeaderConsistencyTest` 判的就是这条）。
 *
 * 留着这个函数只为少改四个调用点；规格、名字来源、热区全在
 * `core/designsystem/LbTopBar.kt`，别在这儿再长出一份。
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    LbTopBar(
        title = title,
        level = LbTopBarLevel.Page,
        onBack = onBack,
        showsDivider = true,
        trailing = trailing
    )
}
/**
 * 带页头的那一类目的外框。
 *
 * **外框不属于这里**："页面背景、安全区、顶部栏、统一水平边距"归一个所有者，
 * 那一个是 `core/designsystem/LbScreenScaffold`。
 * 这一层只是它上面套的一个薄壳——留着的理由是三个调用点都只想要
 * "标题 + 返回 + 一个尾部动作"这套组合，不是想再养一套外框。
 *
 * 今天那 24dp 的顶部留白与 24dp 底部留白用 `Spacer` 复现，而不是往外框上挂
 * `padding(vertical = …)`：挂在外框上会排到 `background` 之内还是之外取决于链序，
 * 排错了就是顶部一条没上色的带子——本机看不出来，真机才看得出来。
 *
 * 键盘那一档也收在这一处：外框吃 `imePadding()`，于是每一条走这一层的页面
 * （建库向导的下一步与称呼输入、知识库编辑的正文输入、捕获范围的搜索框）
 * 键盘弹起来之后，贴在柱子里侧的那颗动作仍然留在键盘上方按得到，而不是被顶到屏外。
 * 这一档只写一次：页面自己不该再各垫一层键盘内边距。
 */
@Composable
fun ScreenPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    LbScreenScaffold(modifier = modifier.imePadding()) {
        Spacer(Modifier.height(Spacing.xxxl))
        ScreenHeader(title = title, onBack = onBack, trailing = trailing)
        Spacer(Modifier.height(Spacing.xl))
        content()
        Spacer(Modifier.height(Spacing.xxxl))
    }
}
