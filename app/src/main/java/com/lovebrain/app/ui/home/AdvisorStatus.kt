package com.lovebrain.app.ui.home

import androidx.annotation.StringRes
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbStatus
import com.lovebrain.app.service.FloatingService

/** 主按钮按下要做的事。**意图**而不是 lambda：lambda 要留在 Compose 侧拼，这里保持可穷举。 */
internal enum class AdvisorIntent { Start, OpenPanel, Restore }

/**
 * 首页"军师状态主卡"要显示的**全部**四个事实（§6.2 第 2 段：状态 badge + 简短说明 + 唯一主按钮）。
 *
 * 不可变、无 lambda ⇒ 能被 [advisorStatus] 一次算完，也能被用例逐格比。
 */
internal data class AdvisorStatus(
    val badge: LbStatus,
    @param:StringRes val descriptionRes: Int,
    @param:StringRes val buttonRes: Int,
    val intent: AdvisorIntent
)

/**
 * 三个事实 → 一份快照。**这里只有一处判据**，
 * 搬家之前是五个平行 `when`（`statusText` / `statusColor` / `description` / `buttonText` /
 * `buttonAction`），每个都把同样三个条件重判一遍：五份判据可以各说各话，
 * 而且加一个状态时最容易忘的就是"颜色那份"。
 *
 * 逐分支对齐旧行为，只有一处是**修**而不是搬：
 * `serviceRunning && window == STOPPED` 旧代码走 `else` 说"运行中 / 军师正在运行，长按消息即可捕获"，
 * 而这种情况是真的存在的——`showBubble()` 里 `wm.addView` 抛异常时会 `stopSelf()`，
 * 那是个**异步**动作，从抛出到 `onDestroy` 把 `instance` 置空之间，首页读到的就是
 * "服务活着、窗口从没出现过"。那一格现在说"窗口未出现"+ Error 色 + 说明为什么还能点"打开军师"
 * （`EventBus.requestPanel` 那条路径不依赖悬浮球，会直接 `showPanel()`，所以按钮不是死的）。
 */
internal fun advisorStatus(
    overlayGranted: Boolean,
    serviceRunning: Boolean,
    window: FloatingService.WindowState
): AdvisorStatus = when {
    !overlayGranted -> AdvisorStatus(
        badge = LbStatus.NoPermission,
        descriptionRes = R.string.home_desc_need_overlay,
        buttonRes = R.string.home_btn_grant_overlay,
        // 旧代码这一格与"未启动"走同一个 lambda：startFloatingService() 内部发现没权限时
        // 自己去系统授权页，所以意图仍然是一个 Start——不是我把两件事糊在一起。
        intent = AdvisorIntent.Start
    )
    !serviceRunning -> AdvisorStatus(
        badge = LbStatus.Off,
        descriptionRes = R.string.home_desc_not_started,
        buttonRes = R.string.home_btn_start,
        intent = AdvisorIntent.Start
    )
    window == FloatingService.WindowState.STOPPED -> AdvisorStatus(
        badge = LbStatus.WindowMissing,
        descriptionRes = R.string.home_desc_window_missing,
        buttonRes = R.string.home_btn_open,
        intent = AdvisorIntent.OpenPanel
    )
    window == FloatingService.WindowState.TEMP_HIDDEN -> AdvisorStatus(
        badge = LbStatus.Hidden,
        descriptionRes = R.string.status_hidden_desc,
        buttonRes = R.string.home_btn_restore,
        intent = AdvisorIntent.Restore
    )
    else -> AdvisorStatus(
        badge = LbStatus.Running,
        descriptionRes = R.string.home_desc_running,
        buttonRes = R.string.home_btn_open,
        intent = AdvisorIntent.OpenPanel
    )
}
