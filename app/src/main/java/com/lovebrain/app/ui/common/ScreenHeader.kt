package com.lovebrain.app.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lovebrain.app.core.designsystem.LbScreenScaffold
import com.lovebrain.app.core.designsystem.LbTopBar
import com.lovebrain.app.core.designsystem.LbTopBarLevel
import com.lovebrain.app.core.designsystem.Spacing

/**
 * 页头——**现在只是 `LbTopBar` 的一个薄壳**。
 *
 * §6.1 :479 要的是"标题、副标题、返回等单一尾部动作"归一颗组件。这一族以前是
 * 全 App 页头的第二种写法（自己量行高、自己挂返回钮、自己画分割线），
 * 而它那颗返回钮的名字是**硬编码中文** `"返回"`：
 * 英文环境下资源已经翻成 "Back"，它还是念中文——
 * `PageHeaderConsistencyTest` 就是照着这条写的红（实到 `"返回"`，期望 `"Back"`）。
 *
 * 留着这个函数只为少改四个调用点；规格、名字来源、热区全在
 * `core/designsystem/LbTopBar.kt`。以前记在这里的那条历史（返回钮热区从 32dp
 * 抬到 48dp，是 `KbListScreenStatesTest` 第一次量 `ScreenHeader` 量到的）
 * 跟着实现一起搬过去了，别在这儿再长出一份。
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
 * **外框本身已经不属于这里了**：§6.1 要求"页面背景、安全区、顶部栏、统一水平边距"
 * 归一个所有者，那一个是 `core/designsystem/LbScreenScaffold`。
 * 这一层只是它上面套的一个薄壳——留着的理由是三个调用点都只想要
 * "标题 + 返回 + 一个尾部动作"这套组合，不是想再养一套外框。
 *
 * 今天那 24dp 的顶部留白与 24dp 底部留白用 `Spacer` 复现，而不是往外框上挂
 * `padding(vertical = …)`：挂在外框上会排到 `background` 之内还是之外取决于链序，
 * 排错了就是顶部一条没上色的带子——本机看不出来，真机才看得出来。
 */
@Composable
fun ScreenPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    LbScreenScaffold(modifier = modifier) {
        Spacer(Modifier.height(Spacing.xxxl))
        ScreenHeader(title = title, onBack = onBack, trailing = trailing)
        Spacer(Modifier.height(Spacing.xl))
        content()
        Spacer(Modifier.height(Spacing.xxxl))
    }
}
