package com.lovebrain.app.ui.home

import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.LbStatus
import com.lovebrain.app.core.designsystem.Neutral300
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.service.FloatingService.WindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §6.1 `LbStatusBadge` + §2.2「五处平行 when 并成一个判据」的账。
 *
 * 穷举 2 × 2 × 4 = 16 组输入（授权 / 服务在不在 / 四种窗口状态），钉三件事：
 * 1. 每组的 (badge, 说明, 按钮, 意图) 与**搬家前那五个 `when` 逐分支对得上**——
 *    两处差别是有意修的，写在下面各自的格子里；
 * 2. **badge 决定其余三项**：同一枚 badge 在任何输入下都配同一套文案与意图。
 *    旧写法拦不住这个——五个独立 `when` 完全可以让"运行中"在某组输入里配上一个别的颜色的分支；
 * 3. 颜色仍来自 `LbStatus` 那一张表（Running=Primary、其余旧三档=Neutral300、新档=Error）。
 *
 * **这条判据的强度要按矩阵的重复次数看，别看名字**：`badge 决定其余三项` 只有在
 * "同一枚 badge 被多组输入走到"时才有牙——实测把 `Hidden` 分支的按钮换成"打开军师"，
 * 这一格**照样绿**（`Hidden` 全矩阵只有一组输入走到，没有第二组来跟它比），
 * 红的是下面那条逐分支比标签的格子。所以两条都要留：
 * 性质格管的是"授权/未启动这两档跨 8 组 / 4 组输入会不会各说各话"（旧五个 when 最容易坏的地方），
 * 逐分支格管的是"每一档说的是不是那句"。别把上面那句"badge 决定其余三项"读成全覆盖。
 */
class AdvisorStatusTest {

    private val allWindows = WindowState.values().toList()

    private fun rows(): List<Pair<Triple<Boolean, Boolean, WindowState>, AdvisorStatus>> =
        buildList {
            for (granted in listOf(true, false)) {
                for (running in listOf(true, false)) {
                    for (w in allWindows) {
                        add(
                            Triple(granted, running, w) to
                                advisorStatus(
                                    overlayGranted = granted,
                                    serviceRunning = running,
                                    window = w
                                )
                        )
                    }
                }
            }
        }

    @Test
    fun `the matrix covers every combination and none of them throws`() {
        assertEquals("2 × 2 × 4 全跑", 16, rows().size)
        // 每组都必须落到一枚真实 badge —— 有 else 兜底不等于有答案
        rows().forEach { (input, s) ->
            assertTrue("$input 落到了 $s", LbStatus.values().contains(s.badge))
        }
    }

    @Test
    fun `badge alone determines the rest of the card`() {
        val byBadge = rows().groupBy({ it.second.badge }, { it.second })
            .mapValues { (_, v) -> v.map { Triple(it.descriptionRes, it.buttonRes, it.intent) }.toSet() }
        byBadge.forEach { (badge, variants) ->
            assertEquals(
                "$badge 在矩阵里配出了 ${variants.size} 套说明/按钮/意图：" +
                    "$variants —— 旧写法五个 when 各判一遍就是这个形状",
                1, variants.size
            )
        }
        assertEquals("五档 badge 都该被矩阵走到", LbStatus.values().size, byBadge.size)
    }

    @Test
    fun `no permission outranks everything because that is the first thing the user must do`() {
        allWindows.forEach { w ->
            listOf(true, false).forEach { running ->
                val s = advisorStatus(false, running, w)
                assertEquals(LbStatus.NoPermission, s.badge)
                assertEquals(R.string.home_desc_need_overlay, s.descriptionRes)
                assertEquals(R.string.home_btn_grant_overlay, s.buttonRes)
                assertEquals(AdvisorIntent.Start, s.intent)
            }
        }
    }

    /**
     * 服务不在 = `Off`，**哪怕 windowState 还停在 TEMP_HIDDEN**。
     *
     * 这是有意改掉的一处旧行为：`onDestroy` 里 `instance = null` 先跑、`setWindowState(STOPPED)` 后跑，
     * 所以"实例已空、状态还是已隐藏"这一组是能读到的；旧 `buttonText` / `buttonAction` 的分支顺序
     * 把 TEMP_HIDDEN 排在 `isServiceRunning` 之前 ⇒ 那一刻卡片上是一颗「恢复军师」按钮，
     * 点了发出 ACTION_RESTORE，而新起的实例 windowState 是 STOPPED，
     * `restoreFromTempHidden()` 第一行就 `return` —— 死按钮。
     * 现在这一组说"未启动 / 启动军师悬浮窗"，走的是一条真的会把球拉起来的路径。
     */
    @Test
    fun `a dead service is Off even if the window state still says hidden`() {
        val s = advisorStatus(overlayGranted = true, serviceRunning = false, window = WindowState.TEMP_HIDDEN)
        assertEquals(LbStatus.Off, s.badge)
        assertEquals(R.string.home_desc_not_started, s.descriptionRes)
        assertEquals(R.string.home_btn_start, s.buttonRes)
        assertEquals(AdvisorIntent.Start, s.intent)
    }

    @Test
    fun `not started and hidden and running keep the labels they had before`() {
        val off = advisorStatus(true, false, WindowState.STOPPED)
        assertEquals(LbStatus.Off, off.badge)
        assertEquals(R.string.home_btn_start, off.buttonRes)
        assertEquals(AdvisorIntent.Start, off.intent)

        val hidden = advisorStatus(true, true, WindowState.TEMP_HIDDEN)
        assertEquals(LbStatus.Hidden, hidden.badge)
        assertEquals(R.string.status_hidden_desc, hidden.descriptionRes)
        assertEquals(R.string.home_btn_restore, hidden.buttonRes)
        assertEquals(AdvisorIntent.Restore, hidden.intent)

        listOf(WindowState.VISIBLE_BUBBLE, WindowState.VISIBLE_PANEL).forEach { w ->
            val run = advisorStatus(true, true, w)
            assertEquals("$w 应当算运行中", LbStatus.Running, run.badge)
            assertEquals(R.string.home_desc_running, run.descriptionRes)
            assertEquals(AdvisorIntent.OpenPanel, run.intent)
        }
    }

    /**
     * 修的那一格：服务活着、窗口却从未出现（`wm.addView` 抛了，`stopSelf()` 还是异步的）。
     * 旧代码走 `else`，说"运行中 · 军师正在运行，长按消息即可捕获"。
     */
    @Test
    fun `a live service whose window never appeared no longer claims to be running`() {
        val s = advisorStatus(overlayGranted = true, serviceRunning = true, window = WindowState.STOPPED)
        assertEquals(LbStatus.WindowMissing, s.badge)
        assertEquals(R.string.home_desc_window_missing, s.descriptionRes)
        // 按钮沿用旧的那颗（打开军师）：EventBus.requestPanel 那条路不依赖悬浮球，
        // 服务活着就能 showPanel()，所以这颗不是死的。
        assertEquals(R.string.home_btn_open, s.buttonRes)
        assertEquals(AdvisorIntent.OpenPanel, s.intent)
    }

    @Test
    fun `colors still come from the one status table`() {
        assertEquals(Primary, LbStatus.Running.color)
        assertEquals(Neutral300, LbStatus.Hidden.color)
        assertEquals(Neutral300, LbStatus.Off.color)
        assertEquals(Neutral300, LbStatus.NoPermission.color)
        assertEquals(Error, LbStatus.WindowMissing.color)
    }

    @Test
    fun `the four old states keep the colors they had`() {
        // 搬家前：!overlayGranted || !isServiceRunning -> Neutral300；TEMP_HIDDEN -> Neutral300；else -> Primary
        listOf(true, false).forEach { running ->
            assertEquals(Neutral300, advisorStatus(false, running, WindowState.STOPPED).badge.color)
        }
        assertEquals(Neutral300, advisorStatus(true, false, WindowState.STOPPED).badge.color)
        assertEquals(Neutral300, advisorStatus(true, true, WindowState.TEMP_HIDDEN).badge.color)
        assertEquals(Primary, advisorStatus(true, true, WindowState.VISIBLE_BUBBLE).badge.color)
    }
}
