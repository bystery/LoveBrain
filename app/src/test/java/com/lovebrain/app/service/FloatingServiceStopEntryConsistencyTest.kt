package com.lovebrain.app.service

import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.service.FloatingService.WindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * **停止入口一致**那一族（M05/M06）：主页方块、设置页那颗开关、通知栏「停止」三颗入口
 * 必须落到**同一颗**真实停止动作上，而「暂时隐藏」不是那颗动作。
 *
 * 为什么这一族能在 JVM 上判（本机无设备，一律不签「已验收」）：
 * 三颗入口的**写法**各不相同（`viewmodel` 那颗走端口、设置页那颗走回调、通知栏那颗走
 * `PendingIntent` + `onStartCommand`），但它们的**共同终点**只有一个：
 * `stopSelf()` → [FloatingService.onDestroy] 那一条清理路径。所以判据不必起服务，只钉三件事：
 * 1. 那条清理路径落 `STOPPED` 时留一笔可数的账（[FloatingService.stopActionCount]），
 *    并且**只有**它能留这笔账（隐藏留 0 笔）；
 * 2. 三颗入口的源码形状都只指向那颗动作：谁都不许在 UI 侧自己拆窗、自己停前台，
 *    也不许把「关闭」偷偷接到 `tempHide()` 那一支；
 * 3. 开关的读数来自真实 [WindowState]，不是新造的持久化布尔，也没有第五种状态。
 *
 * 量不到的那一半如实登记为**需真机**（最后一格写着），不把形状判读成"屏幕上已经成立"。
 *
 * ⚠ Application 走 [UiProbeApplication]：本族只拨 companion 读数 + 读源码形状，不碰依赖容器；
 * 清单默认那颗 App 会无条件 `startKoin`，而 Koin 的 GlobalContext 是 JVM 静态的（同沙箱第二次
 * 建 Application 会炸在装配阶段）。这一写法照抄 `CopyCaptureRejectLedgerTest` 的先例。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = UiProbeApplication::class)
class FloatingServiceStopEntryConsistencyTest {

    /** companion 那两颗静态量在同一 JVM 里跨用例存活：每格先摆回「服务不在」那一格 */
    @Before
    fun parkTheStateMachine() {
        FloatingService.setWindowState(WindowState.STOPPED)
        FloatingService.resetStopActionCount()
    }

    // ═══════════ 1. 读数本体：开关亮不亮只认窗口状态 ═══════════

    /**
     * 四档逐格判（原话第 2 条）。
     *
     * 回成什么样子会红：
     * · 把换算写成 `state == VISIBLE_BUBBLE || state == VISIBLE_PANEL`（= 把 TEMP_HIDDEN 读成「已关闭」）
     *   → TEMP_HIDDEN 那颗当场红：用户看见开关是灰的，屏上却还留着一颗随时会亮回来的球；
     * · 换算改成读一颗新造的持久化布尔 → 这一格换不到那颗布尔（编译期就只给 WindowState），
     *   而第 5 组那两格会红；
     * · 新加第五种状态（例如 `HIDDEN_BY_USER`）→ 那句 `values().size == 4` 当场红，
     *   而下面那格的状态表逐行判也会多出一行对不上（原话：本次不新增第五种窗口状态）。
     */
    @Test
    fun `the assistant reading comes from the real window state and hidden is not off`() {
        assertFalse("只有 STOPPED 才算关掉了", FloatingService.isAssistantOn(WindowState.STOPPED))
        assertTrue("球在屏上就是开着", FloatingService.isAssistantOn(WindowState.VISIBLE_BUBBLE))
        assertTrue("面板在屏上就是开着", FloatingService.isAssistantOn(WindowState.VISIBLE_PANEL))
        assertTrue(
            "暂时隐藏＝服务仍在跑、前台通知仍在、视图与 composition 仍在：它不许被读成「已关闭」",
            FloatingService.isAssistantOn(WindowState.TEMP_HIDDEN)
        )
        assertEquals(
            "本轮明写「不新增第五种窗口状态」，档位表一涨就必须连这本账一起改",
            4,
            WindowState.values().size
        )
    }

    /** 状态表逐行判：恰好那四档、按那四个名字、不多不少（新增一档要动这一格才过） */
    @Test
    fun `the window state table is unchanged`() {
        val src = serviceSource()
        val at = src.indexOf("enum class WindowState {")
        assertTrue("尺读不到窗口状态那张表", at >= 0)
        val body = src.substring(at + "enum class WindowState {".length, src.indexOf("}", at))
        val declared = body.lineSequence()
            .map { it.trim().removeSuffix(",").trim() }
            .filter { it.matches(Regex("""[A-Z][A-Z0-9_]*""")) }
            .toList()
        assertEquals(
            "状态表就是那四档，多一档少一档都说明这一族的账变了",
            listOf("STOPPED", "VISIBLE_BUBBLE", "VISIBLE_PANEL", "TEMP_HIDDEN"),
            declared
        )
    }

    // ═══════════ 2. 真实停止动作的账：一次真停一笔，隐藏一笔都不记 ═══════════

    /**
     * [FloatingService.stopActionCount] 的读数本体。
     *
     * 回成什么样子会红：
     * · 把加笔挂在「入口被点」上（各入口自己 +1）→ 绕过清理路径的入口照样把账加了，
     *   下面「重复落 STOPPED 幂等」那一句会数出第二颗；
     * · 把加笔挂在每次写 STOPPED 上（不看前值）→ 那一句同样数到 2，红；
     * · 谁让 tempHide 也落 STOPPED（＝隐藏当成关闭）→ TEMP_HIDDEN 那一步把账加到 1，红；
     * · 谁把清理路径里的 `setWindowState(STOPPED)` 删了（关了却说不清状态）→ 全程数到 0，红。
     */
    @Test
    fun `a real stop ticks the ledger once and a hide ticks it never`() {
        FloatingService.setWindowState(WindowState.VISIBLE_BUBBLE)
        assertEquals("亮球不是停止动作", 0, FloatingService.stopActionCount)
        FloatingService.setWindowState(WindowState.VISIBLE_PANEL)
        assertEquals("开面板不是停止动作", 0, FloatingService.stopActionCount)
        FloatingService.setWindowState(WindowState.TEMP_HIDDEN)
        assertEquals("暂时隐藏不是停止动作（原话：隐藏不等于关闭）", 0, FloatingService.stopActionCount)
        FloatingService.setWindowState(WindowState.VISIBLE_BUBBLE)
        assertEquals("从隐藏亮回来也不是停止动作", 0, FloatingService.stopActionCount)

        FloatingService.setWindowState(WindowState.STOPPED)
        assertEquals("真停一次＝恰一笔账", 1, FloatingService.stopActionCount)
        FloatingService.setWindowState(WindowState.STOPPED)
        assertEquals("重复落 STOPPED 必须幂等：不许数出第二颗停止动作", 1, FloatingService.stopActionCount)

        FloatingService.setWindowState(WindowState.VISIBLE_BUBBLE)
        assertEquals("亮回来不涨账", 1, FloatingService.stopActionCount)
        FloatingService.setWindowState(WindowState.STOPPED)
        assertEquals("第二轮真停再加一笔（三颗入口共用这一把尺）", 2, FloatingService.stopActionCount)
    }

    /** volatile 与 StateFlow 那一双写口仍是同一颗读数：开关订阅的是它，不是新造的布尔 */
    @Test
    fun `the window state flow keeps the same reading as the volatile`() {
        FloatingService.setWindowState(WindowState.TEMP_HIDDEN)
        assertEquals(WindowState.TEMP_HIDDEN, FloatingService.windowState)
        assertEquals(WindowState.TEMP_HIDDEN, FloatingService.windowStateFlow.value)
        assertEquals("隐藏不涨停止账", 0, FloatingService.stopActionCount)
        FloatingService.setWindowState(WindowState.STOPPED)
        assertEquals(WindowState.STOPPED, FloatingService.windowStateFlow.value)
        assertEquals(1, FloatingService.stopActionCount)
    }

    // ═══════════ 3. 源码形状：账只有一个写者，写者就是那条清理路径 ═══════════

    private fun masked(path: String): String {
        val f = File(path)
        assertTrue("尺接错了文件，找不到 $f —— 宁可红也不许空转", f.isFile)
        return SourceScan.maskComments(f.readText(Charsets.UTF_8))
    }

    private fun serviceSource() = masked("src/main/java/com/lovebrain/app/service/FloatingService.kt")

    /** [FloatingService.onDestroy] 那一段（到装配层那颗 `private fun showBubble()` 收口） */
    private fun destroyRegion(src: String): String {
        val at = src.indexOf("override fun onDestroy()")
        assertTrue("尺读不到 onDestroy（它是三条入口唯一的共同终点，形状被改了要连这本账一起改）", at >= 0)
        val end = src.indexOf("private fun showBubble()", at)
        assertTrue("尺读不到 onDestroy 之后的收口行", end > at)
        return src.substring(at, end)
    }

    /**
     * 清理路径**没被绕过**（原话第 3 条）。这一格不重写 onDestroy（现场确认它可靠），
     * 它只做两件负向判：
     * · 摘窗与拆 composition 的那几颗**各恰好一处**，并按原顺序排在 onDestroy 里
     *   （顺序即判据：把 `scope.cancel()` 提到 `bubble.remove()` 之前，动画回调会在视图已摘之后
     *   还持有 Service；把 `panel.destroy()` 挪到 `stopForeground` 之后，前台通知会挂着一扇已拆的窗）；
     * · `STOPPED` 与 `stopForeground(STOP_FOREGROUND_REMOVE)` 全文件也各只有一处、且都在这一段里
     *   ⇒ 落 STOPPED = 走清理，走清理 = 落 STOPPED，两边不许分家。
     *
     * 回成什么样子会红：在别处（比如某颗「快速隐藏」）再调一次 `bubble.remove()`／`stopForeground(`；
     * 把 `setWindowState(WindowState.STOPPED)` 挪出 onDestroy；把 `panel.destroy()` 删掉——
     * 那正是"透明但可点的残余触摸层"留下来的形状（语义树里没有窗口，所以只能按形状判）。
     */
    @Test
    fun `the destroy teardown stays the single ordered cleanup path`() {
        val src = serviceSource()
        val destroy = destroyRegion(src)

        val order = listOf(
            "bubble.cancelAnimation()",
            "panel.cancelExitAnimation()",
            "bubble.remove()",
            "panel.destroy()",
            "scope.cancel()",
            "stopForeground(STOP_FOREGROUND_REMOVE)"
        )
        var cursor = 0
        order.forEach { step ->
            val at = destroy.indexOf(step)
            assertTrue("onDestroy 里看不见「$step」这条清理动作（被删了就是漏了一类残余）", at >= 0)
            assertTrue("「$step」排到了上一条之前：移除顺序就是判据，不许重排", at >= cursor)
            cursor = at
        }

        listOf(
            "bubble.remove()" to 1,
            "panel.destroy()" to 1,
            "stopForeground(" to 1,
            "setWindowState(WindowState.STOPPED)" to 1,
            "stopActionCount++" to 1
        ).forEach { (needle, want) ->
            val got = Regex(Regex.escape(needle)).findAll(src).count()
            assertEquals(
                "全文件「$needle」必须恰 $want 处（多一处＝有人另开了一条不走这条清理路径的路）",
                want,
                got
            )
        }
        assertTrue("STOPPED 只能由清理路径落下", destroy.contains("setWindowState(WindowState.STOPPED)"))
        // 加笔那一处确实落在状态机里、而不是落在某一颗入口上（三颗入口共用一把尺的根）
        val setter = src.substring(src.indexOf("fun setWindowState(state: WindowState)"))
            .substringBefore("\n        }")
        assertTrue("加笔只认「落进 STOPPED」那一次迁移：$setter", setter.contains("stopActionCount++"))
        assertTrue("加笔必须带 STOPPED 条件（无条件加＝每次写状态都算停过一次）", setter.contains("state == WindowState.STOPPED"))
    }

    /**
     * 暂时隐藏那一支仍然是「隐藏」，不是「关闭」（原话第 2 条的后半）。
     *
     * 回成什么样子会红：把 `tempHide()` 实现成 `stopSelf()`／拆窗／停前台——任何一件出现
     * 这一格当场红，第 2 组那格的读数也会跟着对不上。
     */
    @Test
    fun `temp hidden stays a hide and never reaches the stop action`() {
        val src = serviceSource()
        val at = src.indexOf("private fun tempHide()")
        assertTrue("尺读不到 tempHide（它必须继续与真关闭分得开）", at >= 0)
        val region = src.substring(at, src.indexOf("private fun restoreFromTempHidden()", at))
        listOf("stopSelf(", "stopForeground(", "bubble.remove(", "panel.destroy(", "WindowState.STOPPED)")
            .forEach { needle ->
                assertFalse("tempHide 里出现了「$needle」＝隐藏被写成了关闭：$region", region.contains(needle))
            }
        assertTrue(
            "tempHide 仍要把状态落成 TEMP_HIDDEN（区分的那一颗）",
            region.contains("setWindowState(WindowState.TEMP_HIDDEN)")
        )
    }

    // ═══════════ 4. 三颗入口的形状：都只指向那颗动作，没有第四颗 ═══════════

    /**
     * 通知栏那一颗：`ACTION_STOP` → `onStartCommand` 里 `stopSelf()` → `START_NOT_STICKY`。
     *
     * 回成什么样子会红：停止那颗动作被删（用户只剩"杀进程"一条路）；`stopSelf()` 被换成自己拆窗
     * （绕过清理路径＝第 3 组那格一起红）；或者改成再 `startService` 一次（无保活的承诺就破了）。
     */
    @Test
    fun `the notification stop action goes through the same real stop`() {
        val src = serviceSource()
        val at = src.indexOf("if (intent?.action == ACTION_STOP) {")
        assertTrue("尺读不到 ACTION_STOP 那一支", at >= 0)
        val branch = src.substring(at, src.indexOf("}", at))
        assertTrue("通知栏「停止」必须叫真停止动作：$branch", branch.contains("stopSelf()"))
        assertTrue("停止之后不重建（无保活）：$branch", branch.contains("START_NOT_STICKY"))
        assertTrue(
            "通知栏那颗动作仍要指着 stopIntent",
            Regex("""addAction\([^)]*stopIntent""").containsMatchIn(src)
        )
        assertTrue(
            "stopIntent 仍是 setAction(ACTION_STOP)（同一条链，不另起第二颗入口）",
            Regex("""setAction\(ACTION_STOP\)""").containsMatchIn(src)
        )
        assertEquals(
            "服务侧不许自己再拉一次服务（无保活、无自重启）",
            0,
            Regex("""startService\(|startForegroundService""").findAll(src).count()
        )
    }

    /**
     * 主页那一颗：`stopClicked()` → `service.stop()` → `FloatingService.instance?.stopSelf()`。
     *
     * 这一格读的是别席的文件（**只读不改**），钉的是「设置页那颗开关接的必须是同一颗」：
     * 主页的停止动作不夹带别的清理（不自己 stopForeground、不自己摘窗），所以设置页接过来的是
     * 同一颗，而不是"看起来一样但少了一步"的第二颗。
     *
     * 回成什么样子会红：那颗端口开始自己拆窗口/停前台（＝清理路径旁边长出第二步），
     * 或 `stopClicked()` 里出现第二次 `service.stop()`（一次点击停两遍）。
     */
    @Test
    fun `the home stop button owns one stop call and nothing else`() {
        val src = masked("src/main/java/com/lovebrain/app/viewmodel/HomeStatusViewModel.kt")
        val portAt = src.indexOf("object FloatingServiceHomePort : HomeServicePort")
        assertTrue("尺读不到主页用的那颗服务端口", portAt >= 0)
        val stopBody = src.substring(src.indexOf("override fun stop()", portAt))
            .substringBefore("override fun runningChanges")
        assertTrue("主页端口的停止动作就是 stopSelf()：$stopBody", stopBody.contains("FloatingService.instance?.stopSelf()"))
        listOf("stopForeground", "removeView", "dismissPanel", "tempHide", "ACTION_TEMP_HIDE").forEach { needle ->
            assertFalse("主页端口自己拆了「$needle」＝清理路径外多了一步：$stopBody", stopBody.contains(needle))
        }
        val stopClicked = src.substring(src.indexOf("fun stopClicked()")).substringBefore("\n    }")
        assertEquals(
            "点方块只投一次停止动作",
            1,
            Regex("""service\.stop\(\)""").findAll(stopClicked).count()
        )
    }

    /**
     * 设置页那一颗：开关拨关与「关闭悬浮助手」都**只**投宿主那一条关闭回调，本页不碰平台。
     *
     * 回成什么样子会红：
     * · 把「关闭」接到 `tempHide`／`ACTION_TEMP_HIDE`（隐藏当关闭，原话第 2 条禁的那件事）；
     * · 页面自己去 `stopForeground`／`removeView`／摘窗（"残余透明层"与"通知还在但窗没了"两种形状）；
     * · 页面自己起服务或跳授权页（`startActivity` 一出现就是第四颗入口，
     *   `SettingsPageStructureTest` 那把闸同时会判红）；
     * · 两颗动作被合并成一颗 `(Boolean) -> Unit` 让页面自己判断该干什么（判据长回页面里）。
     */
    @Test
    fun `the settings switch hands the host one close and does no platform work itself`() {
        val src = masked("src/main/java/com/lovebrain/app/ui/panel/settings/SettingsAssistantEntry.kt")
        assertEquals("关闭回调的在场形状：声明 + 开关拨关 + 那颗关闭动作", 3, Regex("""onRequestClose""").findAll(src).count())
        assertEquals("开启回调的在场形状：声明 + 开关拨开", 2, Regex("""onRequestEnable""").findAll(src).count())
        assertTrue(
            "拨关那一支必须投关闭，不是投开启",
            Regex("""if \(on\) onRequestEnable\(\) else onRequestClose\(\)""").containsMatchIn(src)
        )
        listOf(
            "tempHide", "TEMP_HIDE", "stopSelf(", "stopForeground(", "removeView", "WindowManager",
            "startActivity", "ACTION_", "WindowState", "FloatingService", "addView"
        ).forEach { needle ->
            assertFalse("设置页那一格自己碰了「$needle」＝清理路径旁边长出第二颗入口", src.contains(needle))
        }
        // 两颗控件都是既有的件（原话：不要在狭窄顶部再塞一排新图标）
        assertTrue("开关走既有那颗 MiniSwitch", src.contains("MiniSwitch("))
        assertTrue("行壳走既有那颗 LbSettingRow", src.contains("LbSettingRow("))
        assertTrue("关闭动作走既有那颗文字档 LbTextAction", src.contains("LbTextAction("))
        assertTrue("容器走那一族唯一的主人 settingsEntryCard", src.contains("settingsEntryCard()"))
        assertFalse("这一页不许多造一颗「收起」", src.contains("panel_collapse"))
    }

    // ═══════════ 5. 没有新布尔、没有新键 ═══════════

    /**
     * 「服务是否在运行」这一件事只有一颗真源：`WindowState`。
     *
     * 回成什么样子会红：给开关补一颗 `assistantEnabled` 落盘（重启后「看着开着、其实没服务」
     * 那一类 bug 的根源），或页面自己 `remember` 一份开关状态（拨一下就先把界面翻绿）。
     */
    @Test
    fun `no new persisted flag and no local state sit behind that switch`() {
        val entry = masked("src/main/java/com/lovebrain/app/ui/panel/settings/SettingsAssistantEntry.kt")
        listOf(
            "putBoolean", "SharedPreferences", "getSharedPreferences", "editor", "prefs",
            "remember", "mutableStateOf", "mutableIntStateOf", "IntentIntroRecord"
        ).forEach { needle ->
            assertFalse("设置页那一格存了自己的东西「$needle」（读数必须来自真实窗口状态）", entry.contains(needle))
        }
        assertFalse(
            "assistantOn 不许有默认值：有默认值＝宿主漏接线时屏幕自己猜一个状态",
            Regex("""assistantOn: Boolean\s*=""").containsMatchIn(entry)
        )

        val src = serviceSource()
        assertEquals(
            "服务里 securePrefs 的引用仍只有面板尺寸那两读两写（多一颗＝给助手状态新开了键）",
            4,
            Regex("""securePrefs\.""").findAll(src).count()
        )
        assertEquals(
            "服务里不许出现偏好写口本体（面板尺寸那条走的是 SecurePrefs 的属性写，不经 editor）",
            0,
            Regex("""getSharedPreferences|editor\.put|edit\(\)""").findAll(src).count()
        )
    }

    // ═══════════ 6. 前台通知保持原样（不许为了「运行时零通知」动手）═══════════

    /**
     * 通知那一格只判「没被拆」：低重要性 + 静音 + 常驻 + 带「停止」动作 + 真的进了前台服务。
     *
     * 回成什么样子会红：删掉 `buildOverlayNotification()` 或不再 `startForeground`（前台服务退化成
     * 不稳定的后台服务）、把重要性抬到 DEFAULT/HIGH（会响铃）、去掉 `setSilent(true)`、
     * 改掉 FGS 类型，或者加保活/隐藏通知那一类系统版本兼容手法。
     */
    @Test
    fun `the overlay notification stays low importance silent and stoppable`() {
        val src = serviceSource()
        assertTrue("前台通知那颗被删了", src.contains("private fun buildOverlayNotification("))
        assertTrue(
            "onCreate 仍要无条件进前台（ServiceCompat.startForeground + buildOverlayNotification）",
            Regex("""ServiceCompat\.startForeground\([\s\S]{0,240}buildOverlayNotification\(\)""").containsMatchIn(src)
        )
        assertTrue("通道仍是低重要性（生命周期通知，不响铃）", src.contains("NotificationManager.IMPORTANCE_LOW"))
        assertTrue("仍不亮 badge", src.contains("setShowBadge(false)"))
        assertTrue("仍不震动", src.contains("enableVibration(false)"))
        assertTrue("仍不设声音", src.contains("setSound(null, null)"))
        assertTrue("通知仍静音", src.contains(".setSilent(true)"))
        assertTrue("通知仍常驻（划不掉就没有「通知没了但服务还在跑」）", src.contains(".setOngoing(true)"))
        assertTrue(
            "前台服务类型仍是 SPECIAL_USE（不许改成别的档或退成后台服务）",
            src.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE")
        )
        assertEquals("无保活：不重建那一句仍是 START_NOT_STICKY（四颗 return）", 4, Regex("""START_NOT_STICKY""").findAll(src).count())
    }

    /**
     * 留在真机上的那一半（本机无设备，本族一律不签「已验收」）：
     * · 关闭后面板、悬浮球、前台通知是不是**同时**消失，屏幕上有没有剩一层透明但可点的区域；
     * · 关掉再重开会不会出现两颗球（`onCreate` 与 `instance` 的配对）；
     * · 系统回收进程之后静态 `windowStateFlow` 是否仍与实际存活的那一实例一致（静态读数的已知边界）。
     *
     * 这一格只做一件自证：确认上面那些判据确实只判了**形状**——JVM 侧没有任何真实窗口可查。
     * 所以它不许被读成"残余层已经验过"，也不许被搬成屏幕上的一句断言（没有屏幕）。
     */
    @Test
    fun `this family judges shape only and the screen half stays for the device`() {
        val src = serviceSource()
        assertTrue("形状判据看得见 onDestroy（屏幕上的同时消失要真机补）", src.contains("override fun onDestroy()"))
        assertEquals(
            "服务里没有第二处真实挂窗的调用可查＝本族判不了屏幕，只能判形状（0 才是诚实的读数）",
            0,
            Regex("""WindowManager\.SERVICE""").findAll(src).count()
        )
    }
}
