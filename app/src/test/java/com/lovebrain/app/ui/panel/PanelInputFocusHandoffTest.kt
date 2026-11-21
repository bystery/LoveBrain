package com.lovebrain.app.ui.panel

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「输入焦点生命周期」这条合同表行点的形状尺。
 *
 * 用户那一行写的是：设置和自定义改写接了"进入编辑"信号，**仍缺完整的宿主失焦交接**，
 * 因此不能据此保证输入法、长按选区和退出输入都正常。失焦交接如今由各输入面**自己报**
 * （字段边沿 + 离树补报，经面板按面绑定的共享通道），面板这一侧收的是"唯一出口 + 各面接线"，
 * 每处各钉一条判据，每条都写清"回退成什么就红"：
 *
 * | 判据 | 回退成什么就红 |
 * | --- | --- |
 * | 面板里 `focusManager.clearFocus(` **恰好一处**，且在唯一出口 `handOffInput` 里 | 有人在某个 `LaunchedEffect` 里再手写一次 `clearFocus`（数到 2）——那份没有 `hide()`、也没点名宿主，就是旧 bug 的续集 |
 * | 那一颗出口同时做 `hide()` / `clearFocus(force = true)` / 保留名单点名点 | 去掉 `hide()` ⇒ 键盘不收；去掉 `force` ⇒ 主动焦点清不掉；拆掉点名点 ⇒ 日后再出现非上报路就没有登记处 |
 * | 七个交接点（注册口 + 切页 + 设置层 + 编辑器 + 批量导入层 + 标错浮层 + 返回键）都只调那一颗出口 | 任何一处改回自己拼 ⇒ `currentHandOffInput()` 数不到 7 |
 * | 非上报名单**为空**，且每颗悬浮窗输入 id 都绑了焦点上报器/容器观测 | 名单里塞回 id ⇒ 有人又造了一条不报失焦的路；某颗 id 没绑上报 ⇒ 那条路的 EDITING 永不退出 |
 * | 面板里有 `BackHandler` 且**同一层**提供了 `LocalOnBackPressedDispatcherOwner` | 只留 `BackHandler` 不供 owner ⇒ 悬浮窗里拿到 null，activity-compose 1.9.0 走的是 `throw IllegalStateException` 那一条（整扇面板起不来，不是静默失效） |
 * | 返回键由本屏根 view 的 `setOnKeyListener` 在 IME 之前那一站泵进 dispatcher | 删掉监听 ⇒ 回调进了表也没人泵；删掉 `hasEnabledCallbacks()` 守卫 ⇒ 没人听也吞键 |
 * | 自定义改写块有 `FocusRequester` + 容器 `onFocusChanged` + 展开那一次点名编辑意图 + 字段级焦点上报 | 少任何一颗 ⇒ "点了自定义，光标在闪而键盘起不来"或"焦点走了宿主不知道"回来了 |
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
        assertTrue("出口里必须保留名单点名点（如今名单为空，各面自报；拆了它就没有登记处）：\n${body.take(300)}", reportAt >= 0)
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
        // 注册口 / 切页 / 设置层开合 / 编辑器关闭 / 批量导入层开合 / 标错浮层关闭 / 返回键 = 七处
        val calls = count(panelCode, "currentHandOffInput\\(\\)")
        assertEquals(
            "七个交接点全部走那一颗出口（实到 $calls 处）：少一处就是那条路又自己拼了一遍",
            7, calls
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
    fun theBatchImportSheetOpeningAndClosingHandsFocusBackThroughThatExit() {
        // 批量导入浮层的全部关闭路径（遮罩/取消键/提交成功）都汇到 `surface.closeBatchImport()`，
        // 粘贴框的失焦由容器级观测报——开/关层这一翻必须经唯一出口清焦：退场动画期间树还在，
        // 清焦才触发得了容器那次真实失焦。缺这一句，浮层关了宿主还认为 batch_import 持焦：
        // 窗口卡 EDITING、键盘不收。
        val effect = Regex("""LaunchedEffect\(surface\.batchImportOpen\)\s*\{([^}]*)\}""")
            .find(panelCode)
        assertTrue(
            "面板里必须有一颗 LaunchedEffect(surface.batchImportOpen) 接住浮层开合——" +
                "关层不交焦点就是审计 06 续那条 P0 原样复发",
            effect != null
        )
        assertTrue(
            "那颗 LaunchedEffect 的体必须走唯一出口，不许自己拼第二份 clearFocus：\n" +
                effect!!.groupValues[1],
            effect.groupValues[1].contains("currentHandOffInput()")
        )
        // 反向证人：key 换成别的状态就数不到——这把尺抓的确实是 batchImportOpen 那一颗
        val wrongKeyShape = "LaunchedEffect(surface.settingsOpen) { currentHandOffInput() }"
        assertEquals(
            "证人：对错 key 的形状这把尺必须数到 0",
            0, Regex("""LaunchedEffect\(surface\.batchImportOpen\)""").findAll(wrongKeyShape).count()
        )
    }

    @Test
    fun theNonReportingListIsEmptyAndEveryOverlayInputReportsFocus() {
        // 名单必须存在且为空：失焦由各面自报（边沿 + 离树补报），不再靠名单猜
        val declared = Regex("""NON_REPORTING_INPUT_IDS\s*=\s*([^\n]+)""").find(panelCode)
        assertTrue("面板里找不到那份名单——出口的点名点就没东西可登记", declared != null)
        assertTrue(
            "名单应为空（各面自报）；塞回 id 就是有人又造了一条不报失焦的路：${declared!!.groupValues[1]}",
            declared.groupValues[1].contains("emptyList")
        )
        assertEquals(
            "名单里不该再留任何 id 字面量",
            0, Regex("\"[^\"]+\"").findAll(declared.groupValues[1]).count()
        )

        // 四颗 LbFieldInput 面：面板按面绑焦点上报器（id 与那颗意图信号同名），
        // 面内取共享上报器交给字段——两头都接上，失焦才真的有人报
        val surfaces = mapOf(
            "settings" to "ui/panel/settings/SettingsIntentEntry.kt",
            "intent_editor" to "ui/panel/IntentEditorSheet.kt",
            "scheme_adjust" to "ui/panel/reply/SchemeAdjustingBlock.kt",
            "memory_wrong" to "ui/panel/reply/MemoryCorrectionFlow.kt"
        )
        for ((id, file) in surfaces) {
            assertEquals(
                "`$id` 必须真的是面板交出去的一颗 onInputIntent id",
                1, count(panelCode, """onInputIntent\("$id"\)""")
            )
            assertEquals(
                "`$id` 必须在面板调用点绑了焦点上报器，否则面内报的是空通道",
                1, count(panelCode, """rememberOverlayFocusReporter\("$id""")
            )
            val surface = codeOf(file)
            assertTrue(
                "$file 必须取共享上报器（字段边沿 + 离树补报那一颗）",
                surface.contains("rememberOverlayInputFocusReporter()")
            )
            assertTrue(
                "$file 必须把上报器交给 LbFieldInput 的 onFocusChange",
                surface.contains("onFocusChange =")
            )
        }
        // 粘贴框在冻结组件里没有字段级入口：容器级观测直接点名宿主，意图信号仍在
        assertEquals(1, count(panelCode, """onInputFocusChange\("batch_import"""))
        assertEquals(1, count(panelCode, """onInputIntent\("batch_import"\)"""))
        // reply / counseling 两颗参照面保持自报（新通道不许把它们吞掉）
        for (id in listOf("reply", "counseling_main", "counseling_followup")) {
            assertEquals(
                "`$id` 仍走它自己那颗 onFocusChange 直报",
                1, count(panelCode, """onInputFocusChange\("$id", focused\)""")
            )
        }
        // 反向证人：凭空的名字数到 0；合成"名单塞回一颗 id"的坏形状必须被这把尺抓得住
        assertEquals(0, count(panelCode, """rememberOverlayFocusReporter\("notARealInputId"""))
        val badShape = "private val NON_REPORTING_INPUT_IDS = listOf(\"settings\")"
        val badDecl = Regex("""NON_REPORTING_INPUT_IDS\s*=\s*([^\n]+)""").find(badShape)!!
        assertFalse("证人：空名单判据对塞了 id 的坏形状必须判假", badDecl.groupValues[1].contains("emptyList"))
        assertEquals(1, Regex("\"[^\"]+\"").findAll(badDecl.groupValues[1]).count())
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
        // 字段级焦点上报也要接上：容器观测只管这一行的账，交回宿主靠字段边沿 + 离树补报
        assertTrue(
            "自定义改写块必须取共享焦点上报器（边沿 + 离树补报那一颗）",
            schemeCode.contains("rememberOverlayInputFocusReporter()")
        )
        assertEquals(
            "上报器必须交进 LbFieldInput 的 onFocusChange，实到 " +
                count(schemeCode, "onFocusChange\\s*=") + " 处",
            1, count(schemeCode, "onFocusChange\\s*=")
        )
        // 反向证人：这几把尺对合成形状各数得到 1，对"什么都没有"数到 0
        val emptyShape = "@Composable fun X() { Column { Text(\"hi\") } }"
        for (needle in listOf("FocusRequester\\(\\)", "\\.requestFocus\\(\\)", "\\.onFocusChanged\\s*\\{", "onFocusChange\\s*=")) {
            assertEquals("证人：$needle 在空形状里必须数到 0", 0, count(emptyShape, needle))
        }
        assertEquals(1, count("Row(Modifier.onFocusChanged { })", "\\.onFocusChanged\\s*\\{"))
        assertEquals(1, count("val r = remember { FocusRequester() }", "FocusRequester\\(\\)"))
        assertEquals(1, count("runCatching { customFocusRequester.requestFocus() }", "\\.requestFocus\\(\\)"))
        assertEquals(1, count("LbFieldInput(onFocusChange = r)", "onFocusChange\\s*="))
    }
}
