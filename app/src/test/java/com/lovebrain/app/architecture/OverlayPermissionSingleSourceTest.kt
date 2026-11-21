package com.lovebrain.app.architecture

import com.lovebrain.app.core.testing.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 悬浮窗权限判据只有一个主人（审计项 4）。
 *
 * 旧形状：挂窗（`OverlayWindowHost`）、设置页申请（`SetupActivity`）、首页灯（`HomeScreen`）
 * 各写一次 `Settings.canDrawOverlays`。三处语义看似相同，实际各自解释"什么算有权限"，
 * 一处忘了兜底就出现灯说没权限、挂窗却照挂的分裂。
 *
 * 这里钉两件静态事实（注释先剥，KDoc 里的旧写法不算）：
 * 1. 系统调用只剩 [com.lovebrain.app.service.OverlayWindowHost] 那一颗 `overlayPermissionGranted`；
 * 2. 两个 UI 入口都改调这颗共用判据——首页仍是两处（组合初值 + ON_RESUME 现读），
 *    不许把刚加的 resume 刷新退回成组合期闭包。
 */
class OverlayPermissionSingleSourceTest {

    private fun mainRoot(): File =
        File("src/main/java/com/lovebrain/app").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/lovebrain/app")

    private fun masked(path: String): String {
        val file = File(mainRoot(), path)
        assertTrue("尺接错了文件，找不到 $file", file.isFile)
        return SourceScan.maskComments(file.readText(Charsets.UTF_8))
    }

    @Test
    fun `the system overlay check has exactly one owner`() {
        val hits = mainRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .mapNotNull { file ->
                val calls = Regex("""Settings\.canDrawOverlays\(""")
                    .findAll(SourceScan.maskComments(file.readText(Charsets.UTF_8))).count()
                if (calls > 0) file.name to calls else null
            }
            .toMap()

        assertEquals(
            "系统悬浮窗权限调用只许住在共用判据里，别处再写一次就是第二本账",
            mapOf("OverlayWindowHost.kt" to 1),
            hits
        )
    }

    @Test
    fun `both ui entry points call the shared judgment`() {
        val setup = masked("ui/SetupActivity.kt")
        assertFalse("SetupActivity 不许再直读系统权限", setup.contains("Settings.canDrawOverlays"))
        assertEquals(
            "申请前与 onResume 结算各一次，都必须走共用判据",
            2,
            Regex("""overlayPermissionGranted\(this\)""").findAll(setup).count()
        )

        val home = masked("ui/home/HomeScreen.kt")
        assertFalse("HomeScreen 不许再直读系统权限", home.contains("Settings.canDrawOverlays"))
        assertEquals(
            "首页仍是组合初值 + ON_RESUME 现读两处，resume 刷新不许回退",
            2,
            Regex("""overlayPermissionGranted\(context\)""").findAll(home).count()
        )
    }
}
