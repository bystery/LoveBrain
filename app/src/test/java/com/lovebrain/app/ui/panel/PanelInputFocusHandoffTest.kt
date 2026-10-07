package com.lovebrain.app.ui.panel

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * §12.2「输入焦点生命周期」这条合同表行点的形状尺。
 *
 * 用户那一行写的是：设置和自定义改写接了"进入编辑"信号，**仍缺完整的宿主失焦交接**，
 * 因此不能据此保证输入法、长按选区和退出输入都正常。本轮收的就是"失焦交接"那一半，
 * 三处各钉一条判据，每条都写清"回退成什么就红"：
 *
 * | 判据 | 回退成什么就红 |
 * | --- | --- |
 * | 面板里 `focusManager.clearFocus(` **恰好一处**，且在唯一出口 `handOffInput` 里 | 有人在某个 `LaunchedEffect` 里再手写一次 `clearFocus`（数到 2）——那份没有 `hide()`、也没点名宿主，就是旧 bug 的续集 |
 * | 那一颗出口同时做 `hide()` / `clearFocus(force = true)` / 点名非上报输入路 | 去掉 `hide()` ⇒ 键盘不收；去掉 `force` ⇒ 主动焦点清不掉；去掉点名 ⇒ 设置页/浮层/自定义改写那三条路的 EDITING 永不退出 |
 * | 四个交接点（注册口 + 切页 + 设置层 + 编辑器）与返回键都只调那一颗出口 | 任何一处改回自己拼 ⇒ `currentHandOffInput()` 数到 4 |
 * | 面板里有 `BackHandler` 且**同一层**提供了 `LocalOnBackPressedDispatcherOwner` | 只留 `BackHandler` 不供 owner ⇒ 悬浮窗里拿到 null，activity-compose 1.9.0 走的是 `throw IllegalStateException` 那一条（整扇面板起不来，不是静默失效） |
 * | 返回键由本屏根 view 的 `setOnKeyListener` 在 IME 之前那一站泵进 dispatcher | 删掉监听 ⇒ 回调进了表也没人泵；删掉 `hasEnabledCallbacks()` 守卫 ⇒ 没人听也吞键 |
 * | 自定义改写块有 `FocusRequester` + `onFocusChanged` + 展开那一次点名编辑意图 | 少任何一颗 ⇒ "点了自定义，光标在闪而键盘起不来"回来了 |
 *
 * ⚠ 每把尺都配**反向证人**（合成形状与合成坏形状各数一次）：本仓库的老规矩是
 * "数到 0 永远不许单独当结论"——正则扫空集恒绿那种事不能再犯。
 *
 * ⚠ 尺子一律先过 [SourceScan.maskComments]：注释里那些形状名（本文件的 KDoc 也一样）
 * 不许被当成接了线。
 */
class PanelInputFocusHandoffTest {

    private val appRoot: File
        get() = File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    private fun codeOf(path: String): String {
        val f = File(appRoot, path)
        assertTrue("找不到 $path——这一格会扫了个空集恒绿", f.isFile)
        return SourceScan.maskComments(f.readText(Charsets.UTF_8))
    }

    private val panelCode: String get() = codeOf("ui/panel/LoveBrainPanelScreen.kt")
    private val schemeCode: String get() = codeOf("ui/panel/reply/SchemeAdjustingBlock.kt")

    private fun count(code: String, regex: String) = Regex(regex).findAll(code).count()

    // ─── ① 唯一出口：收键盘 + 交焦点 + 点名宿主，同一处做完 ───────────────

    @Test
    fun theInputHandoffHasExactlyOneExitAndItCoversKeyboardFocusAndHostTogether() {
        val handoff = Regex("""val\s+handOffInput\s*:\s*\(\)\s*->\s*Unit\s*=\s*\{""")
            .findAll(panelCode).toList()
        assertEquals("面板里那颗唯一出口应恰好一处声明，实到 ${handoff.size} 处", 1, handoff.size)

        val body = panelCode.substring(handoff[0].range.first,
            minOf(handoff[0].range.first + 600, panelCode.length))
        val hideAt = body.indexOf("keyboard?.hide()")
        val clearAt = body.indexOf("focusManager.clearFocus(force = true)")
        val reportAt = body.indexOf("NON_REPORTING_INPUT_IDS.forEach")
        assertTrue("出口里必须收输入法：\n${body.take(300)}", hideAt >= 0)
        assertTrue("出口里必须交回 Compose 焦点（force = true 才算交出主动焦点）：\n${body.take(300)}",
            clearAt >= 0)
        assertTrue("出口里必须点名那三条只有进入编辑信号的路：\n${body.take(300)}", reportAt >= 0)
        assertTrue("顺序：收键盘在交焦点之前", hideAt < clearAt)
        assertTrue("顺序：交焦点在点名宿主之前", clearAt < reportAt)

        // 别处再拼一份 = 旧 bug 的续集（那一份没有 hide、也没点名）
        assertEquals(
            "`focusManager.clearFocus(` 在面板里只许出现在那一颗出口里，实到 " +
                count(panelCode, """focusManager\.clearFocus\(""") + " 处",
            1, count(panelCode, """focusManager\.clearFocus\(""")
        )
        // 反向证人：这两把尺对合成形状都数得到
        val witness = "val handOffInput: () -> Unit = { keyboard?.hide(); " +
            "focusManager.clearFocus(force = true); NON_REPORTING_INPUT_IDS.forEach { f(it) } }"
        assertEquals(1, Regex("""val\s+handOffInput\s*:\s*\(\)\s*->\s*Unit\s*=\s*\{""")
            .findAll(witness).count())
        assertEquals(1, count(witness, """focusManager\.clearFocus\("""))
        assertEquals(
            "证人：不带 force 的写法必须被这把尺放过（它才是旧形状）",
            0, count("focusManager.clearFocus()", """focusManager\.clearFocus\(force = true\)""")
        )
        assertEquals(1, count("focusManager.clearFocus(force = true)",
            """focusManager\.clearFocus\(force = true\)"""))
    }

    @Test
    fun everyHandoffPointGoesThroughThatSingleExit() {
        // 注册口 / 切页 / 设置层开合 / 编辑器关闭 / 返回键 = 五处
        val calls = count(panelCode, "currentHandOffInput\\(\\)")
        assertEquals(
            "五个交接点全部走那一颗出口（实到 $calls 处）：少一处就是那条路又自己拼了一遍",
            5, calls
        )
        assertTrue(
            "宿主反向那条链（onClearComposeFocus）也必须是同一颗出口",
            panelCode.contains("onClearComposeFocus { currentHandOffInput() }")
        )
        // 反向证人：合成形状里三处调用就数到 3
        assertEquals(3, count("a { currentHandOffInput() } b { currentHandOffInput() } " +
            "c { currentHandOffInput() }", "currentHandOffInput\\(\\)"))
    }

    @Test
    fun theNonReportingInputListNamesOnlyIdsThePanelActuallyUses() {
        // 名单不是凭空写的：那三颗 id 必须真的在面板里作为"进入编辑"信号出现过
        val declared = Regex("""NON_REPORTING_INPUT_IDS\s*=\s*listOf\(([^)]*)\)""")
            .find(panelCode)
        assertTrue("面板里找不到那份名单——交接点就没东西可点名", declared != null)
        val ids = Regex("\"([^\"]+)\"").findAll(declared!!.groupValues[1])
            .map { it.groupValues[1] }.toList()
        assertEquals("名单应有三条只有进入编辑、没有失焦的输入路，实到 $ids", 3, ids.size)
        for (id in ids) {
            assertEquals(
                "`$id` 必须真的是面板交出去的一颗 onInputIntent id，否则点名的是空名字",
                1, count(panelCode, """onInputIntent\("$id"\)""")
            )
        }
        // 会自己报失焦的那两格不许混进名单（混进来就是多发一次空操作之外的第二本账）
        for (reporting in listOf("reply", "counseling_main", "counseling_followup")) {
            assertFalse("名单里不该有 $reporting（它自己 onFocusChanged 就报了）",
                ids.contains("\"$reporting\"") || ids.contains(reporting))
        }
        // 反向证人：凭空的名字数到 0，说明这一句真抓得住接错的线
        assertEquals(0, count(panelCode, """onInputIntent\("notARealInputId"\)"""))
        assertEquals(1, count("onInputIntent(\"settings\")", """onInputIntent\("settings"\)"""))
    }

    // ─── ② 返回键：退出输入那一条路 ────────────────────────────────────

    @Test
    fun theBackKeyExitsInputAndThePanelSuppliesTheOwnerItRequires() {
        assertEquals(
            "面板里那颗返回键应恰好一处，实到 " + count(panelCode, "BackHandler\\s*\\{") + " 处",
            1, count(panelCode, "BackHandler\\s*\\{")
        )
        // activity-compose 1.9.0：拿不到 owner 的 BackHandler 走的是 throw IllegalStateException
        // （本机对着 BackHandlerKt 字节码核过）⇒ 光写那颗会让整扇面板起不来，必须同时供 owner
        assertEquals(
            "必须在本屏供一份 OnBackPressedDispatcherOwner，实到 " +
                count(panelCode, "LocalOnBackPressedDispatcherOwner\\s+provides\\s+\\w+") + " 处",
            1, count(panelCode, "LocalOnBackPressedDispatcherOwner\\s+provides\\s+\\w+")
        )
        // 而且要与那颗返回键同一层 provider：出了这层，BackHandler 读到的还是 null
        val providerAt = panelCode.indexOf("LocalOnBackPressedDispatcherOwner provides")
        val backAt = panelCode.indexOf("BackHandler {")
        assertTrue("provider 必须在 BackHandler 之前（同一层往里包）：provider=$providerAt back=$backAt",
            providerAt in 0 until backAt)

        assertTrue("返回键要有人泵：本屏根 view 的 setOnKeyListener",
            panelCode.contains("setOnKeyListener"))
        assertEquals(1, count(panelCode, "KeyEvent\\.KEYCODE_BACK"))
        assertTrue("没人在听就不许吞按键：hasEnabledCallbacks 守卫",
            panelCode.contains("hasEnabledCallbacks()"))
        assertTrue("监听要在 onDispose 里摘掉，不留已死的回调",
            panelCode.contains("setOnKeyListener(null)"))
        // 返回键那一句仍走同一颗出口（不是再拼一份）
        val backBody = panelCode.substring(backAt, minOf(backAt + 260, panelCode.length))
        assertTrue("BackHandler 体里必须点那颗唯一出口：\n$backBody",
            backBody.contains("currentHandOffInput()"))
        // 反向证人
        assertEquals(1, count("BackHandler { currentHandOffInput() }", "BackHandler\\s*\\{"))
        assertEquals(0, count("no back key here at all", "BackHandler\\s*\\{"))
    }

    // ─── ③ 自定义改写那一条路的"进入编辑"信号 ──────────────────────────

    @Test
    fun theCustomRewriteFieldOwnsItsFocusEntrySignal() {
        assertEquals(1, count(schemeCode, "FocusRequester\\(\\)"))
        assertEquals(
            "那颗 requester 必须挂进输入框（`focusRequester = …`），否则它只是个没人要的对象",
            1, count(schemeCode, "focusRequester\\s*=\\s*customFocusRequester")
        )
        assertEquals(
            "展开那一次必须真的去要焦点（`.requestFocus()`），实到 " +
                count(schemeCode, "\\.requestFocus\\(\\)") + " 处",
            1, count(schemeCode, "\\.requestFocus\\(\\)")
        )
        assertTrue("展开那一次必须点名编辑意图（窗口还不可聚焦时，焦点请求起不了输入法）",
            schemeCode.contains("onInputIntent?.invoke()"))
        assertTrue("要过一帧再要焦点：刚翻 open 时那颗框还没进树",
            schemeCode.contains("withFrameNanos"))
        assertTrue("滑回视口那种本来就开着的重新进树，不许当成刚展开去抢焦点",
            schemeCode.contains("justOpened"))
        assertEquals(
            "焦点观测点恰好一处（容器级 onFocusChanged），实到 " +
                count(schemeCode, "\\.onFocusChanged\\s*\\{") + " 处",
            1, count(schemeCode, "\\.onFocusChanged\\s*\\{")
        )
        // 反向证人：这几把尺对合成形状各数得到 1，对"什么都没有"数到 0
        val emptyShape = "@Composable fun X() { Column { Text(\"hi\") } }"
        for (needle in listOf("FocusRequester\\(\\)", "\\.requestFocus\\(\\)", "\\.onFocusChanged\\s*\\{")) {
            assertEquals("证人：$needle 在空形状里必须数到 0", 0, count(emptyShape, needle))
        }
        assertEquals(1, count("Row(Modifier.onFocusChanged { })", "\\.onFocusChanged\\s*\\{"))
        assertEquals(1, count("val r = remember { FocusRequester() }", "FocusRequester\\(\\)"))
        assertEquals(1, count("runCatching { customFocusRequester.requestFocus() }", "\\.requestFocus\\(\\)"))
    }
}
