package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbTopBar
import com.lovebrain.app.core.designsystem.LbTopBarLevel
import com.lovebrain.app.core.designsystem.Spacing

/**
 * 悬浮窗齿轮那一扇**整窗设置页**的正文：一行页头「[返回] 设置」，下面只有一颗透明度滑杆。
 *
 * 依据是用户 2026-10-03 的原话："设置里面暂时先弄一个调透明度的，别的都不要弄"。
 * 被撤出去的三段（供应商 / 超时档位 / 捕获范围）都不是失去入口：
 * 供应商与模型在首页"模型供应商"那一格里增删改与测试连接，超时四档同一张表单里就有
 * （`ui/home/ProviderSection.kt:576-590`），捕获范围在首页"消息捕获"那一格里。
 *
 * 顺带改掉的是**第一次点设置会卡一下**那件事：这一页以前在组合阶段就同步枚举整机安装包
 * （`selectableCaptureTargets(context)`），并为了拿供应商那一行而 `koinViewModel()` 依赖容器。
 * 现在这一页不碰 PackageManager、不碰供应商状态源，只接三个透明度回调。
 *
 * 无状态：这一格不持有配置。透明度读数由宿主给，拖动预览与松手写盘各一条回调，
 * 作用在**面板背景层**上；不许走窗口 `ComposeView.alpha` 那条通路（服务侧会复位成 1f）。
 *
 * @param onBack 回到打开设置之前的那一面（回复/谈心由宿主决定恢复哪一面，输入与卡片状态由宿主保留）
 * @param opacityPercent 当前背景层不透明度（100 = 最不透明；刻度与区间归 `PanelBackdropOpacity`）
 * @param onOpacityPreview 拖动每帧：只用来实时预览背景层，不落盘
 * @param onOpacityCommit 松手一次：落盘由宿主处理
 */
@Composable
fun LoveBrainSettingsContent(
    onBack: () -> Unit,
    opacityPercent: Int,
    onOpacityPreview: (Int) -> Unit,
    onOpacityCommit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 页头那一族（标题 + 返回那颗 + 读屏名字）只有一个主人；返回钉在滚动柱之外，
        // 键盘再高也不会把"返回"滚出可达范围。
        LbTopBar(
            title = stringResource(R.string.settings_title),
            level = LbTopBarLevel.Page,
            onBack = onBack,
            showsDivider = true
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                // 水平那 16dp 归面板外框那一个主人；这里只补纵向那一段。
                .padding(top = Spacing.lg, bottom = Spacing.xl)
        ) {
            SettingsOpacityEntry(
                opacityPercent = opacityPercent,
                onOpacityPreview = onOpacityPreview,
                onOpacityCommit = onOpacityCommit
            )
        }
    }
}
