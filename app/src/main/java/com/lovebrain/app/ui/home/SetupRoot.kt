package com.lovebrain.app.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lovebrain.app.ui.feedback.FeedbackCasesScreen
import com.lovebrain.app.core.designsystem.SurfaceBase
import com.lovebrain.app.viewmodel.SetupViewModel

/** 根导航内容最大宽度 */
private const val CONTENT_MAX_WIDTH_DP = 600

/**
 * 设置页根导航——根据 destination 切换 Home / Providers / Usage / About / FeedbackCases。
 *
 * 使用 [HomeDestination.Saver] + [rememberSaveable]，旋转/进程重建后恢复 destination。
 */
@Composable
fun SetupRoot(
    viewModel: SetupViewModel,
    onStartService: () -> Unit,
    onOpenPanel: (Int, Boolean) -> Unit,
    onTempHide: () -> Unit,
    onRestore: () -> Unit
) {
    var destination by rememberSaveable(stateSaver = HomeDestination.Saver) {
        mutableStateOf(HomeDestination.Home)
    }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBase)
            .systemBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = CONTENT_MAX_WIDTH_DP.dp)) {
            when (destination) {
                HomeDestination.Home -> HomeScreen(
                    viewModel = viewModel,
                    onStartService = onStartService,
                    onOpenPanel = onOpenPanel,
                    onTempHide = onTempHide,
                    onRestore = onRestore,
                    onNavigateFeedback = { destination = HomeDestination.FeedbackCases },
                    onNavigateAbout = { destination = HomeDestination.About },
                    onNavigateProviders = { destination = HomeDestination.Providers },
                    onNavigateUsage = { destination = HomeDestination.Usage },
                    onNavigateCaptureApps = { destination = HomeDestination.CaptureApps },
                    onBack = { (context as? Activity)?.finish() }
                )
                HomeDestination.FeedbackCases -> {
                    BackHandler { destination = HomeDestination.Home }
                    FeedbackCasesScreen(
                        viewModel = viewModel,
                        onBack = { destination = HomeDestination.Home }
                    )
                }
                HomeDestination.About -> {
                    BackHandler { destination = HomeDestination.Home }
                    AboutScreen(
                        onBack = { destination = HomeDestination.Home }
                    )
                }
                HomeDestination.Providers -> {
                    BackHandler { destination = HomeDestination.Home }
                    ProviderSection(viewModel = viewModel, onBack = { destination = HomeDestination.Home })
                }
                HomeDestination.CaptureApps -> {
                    BackHandler { destination = HomeDestination.Home }
                    CaptureAppsScreen(viewModel = viewModel, onBack = { destination = HomeDestination.Home })
                }
                HomeDestination.CaptureApps -> {
                    BackHandler { destination = HomeDestination.Home }
                    CaptureAppsScreen(viewModel = viewModel, onBack = { destination = HomeDestination.Home })
                }
                HomeDestination.Usage -> {
                    BackHandler { destination = HomeDestination.Home }
                    UsageDetailScreen(viewModel = viewModel, onBack = { destination = HomeDestination.Home })
                }
            }
        }
    }
}
