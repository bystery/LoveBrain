package com.lovebrain.app.ui.panel

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 原话第 8 条的门禁：「缺模型点『去设置』进的是调透明度那一页，而不是进 App 主页面！！！」
 *
 * 用户点的是**失败条上那颗「去设置」**，落到的是悬浮窗内的设置页（齿轮那一扇）。
 * 指导书 §153 把旧约定「去设置和齿轮同一个入口」列为被推翻项，判据落成两句话：
 * **两扇门必须分开**，而且**宿主必须真的把 App 那扇接上**。
 *
 * ## 为什么这一格用源码形状尺，而不是语义树
 *
 * 语义树能证"点那颗确实投了某个回调"（`PanelErrorStatesSemanticsTest` 那一族已经在那一层量过），
 * 但它**读不出"那颗投的是哪一扇门的实现"**——`surface.openSettings()` 换成本机 Compose 状态翻转，
 * 与 `FloatingService.openAppMainPage()` 换成 `startActivity`，在树上是同一颗"有点击动作的节点"。
 * 而这条原话的病灶恰好就是**接错了线**，所以判据必须贴着接线本身。
 *
 * ## 每一句"回退成什么会红"
 *
 * | 判据 | 回退成什么就红 |
 * | --- | --- |
 * | `surface.openSettings()` 在面板正文里**恰好一处** | 有人把失败条那颗又并回齿轮那一扇门（数到 2）；或把齿轮删了（数到 0） |
 * | 失败条那颗传的是 `onOpenSettings = onOpenAppPage` | 改回 `{ surface.openSettings() }` ⇒ 这一句数到 0 |
 * | 宿主那颗参数**不许有默认值** | 写成 `onOpenAppPage: () -> Unit = {}` ⇒ 没接线的宿主会悄悄过，这颗假入口又回来了 |
 * | `FloatingService` 里 `onOpenAppPage = { openAppMainPage() }` 一处 | 主线程以后把它接成别的（例如又指向悬浮窗）时这一句会数到 0，逼人来改这条判据而不是静默漂移 |
 *
 * ⚠ 尺本人有**反向证人**：每句"数到 N"都配一件合成坏形状，证明那句真数得到；
 * "数到 0"永远不许单独当结论（本仓库的老坑：空集恒绿）。
 */
class PanelSettingsDoorTest {

    private val appRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    private fun codeOf(path: String): String {
        val f = File(appRoot, path)
        assertTrue("找不到 $path——这一格会扫了个空集恒绿", f.isFile)
        // 注释里写 `surface.openSettings()` 不算一处接了线：剥掉注释再数（那些话正是判据的说明）
        return SourceScan.maskComments(f.readText(Charsets.UTF_8))
    }

    private val panelCode: String get() = codeOf("ui/panel/LoveBrainPanelScreen.kt")
    private val serviceCode: String get() = codeOf("service/FloatingService.kt")

    private fun count(code: String, regex: String) = Regex(regex).findAll(code).count()

    /** 两扇门必须分开：悬浮窗设置只归齿轮，App 主页面走宿主那颗新参数 */
    @Test
    fun theGearAndTheMissingProviderButtonAreDifferentDoors() {
        val gearDoor = count(panelCode, """surface\.openSettings\(\)""")
        assertEquals(
            "悬浮窗内设置页这一扇门只许齿轮一颗在开（正文里 `surface.openSettings()` 应恰好 1 处，实到 $gearDoor 处）：" +
                "失败条那颗若又并回来，用户点『去设置』就还是进调透明度那一页",
            1, gearDoor
        )
        assertEquals(
            "失败条那颗『去设置』必须投宿主的 App 门（`onOpenSettings = onOpenAppPage`）",
            1, count(panelCode, """onOpenSettings = onOpenAppPage""")
        )
        // 反向证人：把两扇门并成一把的坏形状，必须被上面那句数到
        assertEquals(
            "注件没就位：合成两句 `surface.openSettings()` 应当数到 2",
            2, count(
                "onOpenSettings = { surface.openSettings() }\n" +
                    "onOpenSettings = { surface.openSettings() }",
                """surface\.openSettings\(\)"""
            )
        )
        assertEquals(
            "注件没就位：合成一句 `onOpenSettings = onOpenAppPage` 应当数到 1",
            1, count("onOpenSettings = onOpenAppPage", """onOpenSettings = onOpenAppPage""")
        )
    }

    /** 宿主那颗参数不许有默认值——默认值 = 没接线也能编过的假入口 */
    @Test
    fun theAppDoorHasNoSilentDefault() {
        assertEquals(
            "`onOpenAppPage` 必须是无默认值的必填参数（带 `= ` 就等于允许宿主忘了接）",
            0, count(panelCode, """onOpenAppPage: \(\) -> Unit =""")
        )
        assertEquals(
            "参数本体必须在签名里（回退成删掉这颗参数：数到 0）",
            1, count(panelCode, """onOpenAppPage: \(\) -> Unit,""")
        )
        // 反向证人：那颗"允许静默"的坏形状必须被第一句数到
        assertEquals(
            "注件没就位：合成一句带默认值的签名应当被第一句数到 1",
            1, count("onOpenAppPage: () -> Unit = {},", """onOpenAppPage: \(\) -> Unit =""")
        )
    }

    /** 接线必须在生产宿主上，而且落的是"起 App 主页面"那一颗 */
    @Test
    fun theHostWiresTheAppDoorToSetupActivity() {
        assertEquals(
            "FloatingService 给面板的 `onOpenAppPage` 应恰好接一次（实到 ${count(serviceCode, """onOpenAppPage = \{ openAppMainPage\(\) \}""")} 处）",
            1, count(serviceCode, """onOpenAppPage = \{ openAppMainPage\(\) \}""")
        )
        assertEquals(
            "`openAppMainPage()` 的定义只许一颗（多出来就是第二本进主面的账）",
            1, count(serviceCode, """private fun openAppMainPage\(\)""")
        )
        // 进主面必须 NEW_TASK（从服务里起 Activity，没有前台窗口），且目标仍是 SetupActivity
        assertTrue(
            "openAppMainPage 必须带 NEW_TASK（后台起 Activity 不带它只会静默失败）",
            Regex("""openAppMainPage\(\)[\s\S]{0,400}FLAG_ACTIVITY_NEW_TASK""").containsMatchIn(serviceCode)
        )
        assertTrue(
            "openAppMainPage 起的必须是 SetupActivity（App 主页面），不是别的",
            Regex("""openAppMainPage\(\)[\s\S]{0,400}SetupActivity::class\.java""").containsMatchIn(serviceCode)
        )
        // 反向证人：宿主整排没接（`onOpenAppPage` 零处）时第一句会数到 0——
        // 那一句的牙由"合成一句接了的形状必须数到 1"证明，尺没瞎。
        assertEquals(
            "注件没就位：合成一句宿主接线形状应当数到 1",
            1, count("onOpenAppPage = { openAppMainPage() },", """onOpenAppPage = \{ openAppMainPage\(\) \}""")
        )
    }
}
