package com.lovebrain.app.ui.home

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「从系统授权页回来必须重读事实」这一格的门禁（2026-10-06，用户真机读数）。
 *
 * 用户原话（当天两次追加里的第二次）：
 * 「我按照长文把无障碍打开了之后，我看开关还是没开，我想拨开又开始跳转到设置了，
 *   但是我已经开启了啊，卡了好几次，我重新退出页面重进发现好了」。
 * 指导书 第3条 的验收句「未授权 → 点击去设置 → **返回刷新**」判的就是这一格。
 *
 * ## 为什么这一格用源码形状尺，而不是挂组件跑一遍
 *
 * `ON_RESUME` 是 **Activity 生命周期**事件，而这一页是挂在 `SetupRoot` 里的一段 composable：
 * Robolectric 这边 `createComposeRule()` 挂出来的那一屏没有真的宿主生命周期可推
 * （本仓库记过同类坑：量不到的那一轴交给**纯函数/形状格**去钉，别在语义树里假装能量）。
 * 所以这里钉的是"订阅在不在、退订在不在、触发的是不是同一颗重读"，
 * 而"重读之后四颗事实各自念什么"归 `CaptureAppsScreenTruthTest` 与 `CaptureTruthStateTest`。
 *
 * ## 每一句"回退成什么会红"
 *
 * | 判据 | 回退成什么就红 |
 * | --- | --- |
 * | 这一页订阅 `Lifecycle.Event.ON_RESUME` | 只留 `LaunchedEffect(rescanTick)`（改前的形状）⇒ 数到 0 |
 * | resume 那一格推的是 `rescanTick++` | 换成"只 `scanning = true`"或空回调 ⇒ 数到 0 |
 * | 有 `removeObserver`（退订） | 只 add 不 remove ⇒ Activity 销毁后观察者留着（漏 + 死页还会回调重读） |
 * | 订阅包在 `DisposableEffect` 里 | 写进 `LaunchedEffect` ⇒ 取消协程不等于移除观察者，语义不对 |
 *
 * ⚠ 测不到的部分如实交出去：**真机上"跳去系统给完权限回来，状态当场跟不跟"**只有设备能定
 * （本机 `adb devices` 为空，已登记"未验证-需真机"）。这一格证的是订阅形状，不是手感。
 */
class CapturePageResumeRefreshTest {

    private val appRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    private val pageCode: String by lazy {
        val f = File(appRoot, "ui/home/CaptureAppsScreen.kt")
        assertTrue("找不到 CaptureAppsScreen.kt——这一格会扫了个空集恒绿", f.isFile)
        SourceScan.maskComments(f.readText(Charsets.UTF_8))
    }

    private fun count(code: String, regex: String) = Regex(regex).findAll(code).count()

    /**
     * 取 `DisposableEffect(lifecycleOwner) { … }` 那一具的**体内**（花括号配平，不是 `[^}]*`：
     * 具里有 `LifecycleEventObserver { … }` 与 `onDispose { … }` 两对花括号，非配平会把观察者那半截当终点）。
     *
     * resume 观察分支只住在这一具里；页内 `onRescan = { rescanTick++ }` 住在另一具（`ScreenPage`/
     * `CaptureScopePicker` 那一列）——把"resume 推那颗重读计数"钉在这一具的跨度里，才能只数 resume 那一颗。
     */
    private fun disposableObserverBody(code: String): String {
        val start = code.indexOf("DisposableEffect(lifecycleOwner)")
        assertTrue("这一具没了：`DisposableEffect(lifecycleOwner)` 不在盘上——resume 观察无处可挂", start >= 0)
        val open = code.indexOf('{', start)
        assertTrue("`DisposableEffect(lifecycleOwner)` 后面没有函数体", open >= 0)
        var depth = 0
        for (i in open until code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return code.substring(open + 1, i)
                }
            }
        }
        throw AssertionError("DisposableEffect 的花括号没配平，这一具量不出 scoped 跨度")
    }

    @Test
    fun `the capture page re-reads its facts when the activity resumes`() {
        assertTrue(
            "这一页必须订阅 resume：不订阅，从系统授权页回来时 `accessibilityGranted` 还停在出发前的值，" +
                "拨开关就又跳去设置（用户 2026-10-06 的读数）",
            count(pageCode, """Lifecycle\.Event\.ON_RESUME""") >= 1
        )
        // resume 上推那颗重读计数——**只准在观察者那一具里数**（上一版按整页量，把页内 `onRescan` 那颗
        // 合法的第二颗一起数进来，才把 `expected:<1> but was:<2>` 撑红；收窄 ≠ 放宽，见下面两枚反向证人）。
        val resumeBody = disposableObserverBody(pageCode)
        assertTrue(
            "resume 观察分支必须就挂在 DisposableEffect 那一具里：具内没有 `Lifecycle.Event.ON_RESUME` ⇒ 回来没人推重读",
            count(resumeBody, """Lifecycle\.Event\.ON_RESUME""") >= 1
        )
        assertEquals(
            "resume 上推的必须就是那颗重读计数（`rescanTick++`），且这一具里不许另开第二条读取通道；" +
                "页外的 onRescan 那颗不算进来（把 resume 那处换成直接读事实，这里就数到 0）",
            1, count(resumeBody, """rescanTick\+\+""")
        )
        assertEquals(
            "订阅要包在 `DisposableEffect` 里（LaunchedEffect 的取消不等于移除观察者）",
            1, count(pageCode, """DisposableEffect\(lifecycleOwner\)""")
        )
        assertEquals(
            "必须有退订那一步：只 add 不 remove = 观察者留在死掉的 Activity 上，还会回调重读",
            1, count(pageCode, """lifecycle\.removeObserver\(observer\)""")
        )
        // 反向证人（尺本人要有牙）：改前那种"只有初次组合读一遍"的形状，必须被第一句数到 0
        val oldShape = "LaunchedEffect(rescanTick) { val batch = withContext(Dispatchers.IO) { facts() } }\n" +
            "LaunchedEffect(Unit) { onRescan() }"
        assertEquals(
            "注件没就位：改前的旧形状里应当数不到 `ON_RESUME`（数得到就说明第一句是恒真）",
            0, count(oldShape, """Lifecycle\.Event\.ON_RESUME""")
        )
        assertEquals(
            "注件没就位：合成一句带 resume + 退订的形状应当各数到 1",
            1, count(
                "if (event == Lifecycle.Event.ON_RESUME) rescanTick++\n" +
                    "lifecycle.removeObserver(observer)",
                """Lifecycle\.Event\.ON_RESUME"""
            )
        )
        assertEquals(
            "注件没就位：合成一句只 add 不退订的形状，退订那句应当数到 0",
            0, count("lifecycle.addObserver(observer)", """lifecycle\.removeObserver""")
        )
        // 反向证人：证明上面"只数观察者那一具"是真的收窄，不是把尺松成"rescanTick 出现过即可"。
        assertEquals(
            "注件没就位：整页有两颗 `rescanTick++`（resume + 页外 onRescan）时，scoped 只许数到观察者具里那一颗",
            1, count(
                disposableObserverBody(
                    "DisposableEffect(lifecycleOwner) {\n" +
                        "    if (event == Lifecycle.Event.ON_RESUME) rescanTick++\n" +
                        "}\n" +
                        "CaptureScopePicker(onRescan = { rescanTick++ })"
                ),
                """rescanTick\+\+"""
            )
        )
        assertEquals(
            "注件没就位：resume 那处换成第二条读取通道（不经 `rescanTick`）时，scoped 必须数到 0——这才叫有牙",
            0, count(
                disposableObserverBody(
                    "DisposableEffect(lifecycleOwner) {\n" +
                        "    if (event == Lifecycle.Event.ON_RESUME) viewModel.readFactsDirectly(context)\n" +
                        "}"
                ),
                """rescanTick\+\+"""
            )
        )
    }
}
