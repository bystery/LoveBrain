package com.lovebrain.app.service

import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.domain.CapturePolicy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * CAP2（2026-10-06，档位 D）· "allowlist → 事件送达面 packageNames" 的账。
 *
 * 修的是**事件送达面**：以前 XML 没有 `packageNames`＝全 App 都投，而"代码层 allowlist 过滤"
 * 那句审计承诺从没真在生产码里落到送达面。这一族把两半钉死：
 *
 * 1. 纯函数格（`CapturePolicy.packageNamesFor`）：给一份包名集合 ⇒ 数组恰是那一份（排序确定、
 *    无夹带样例包名）；空集合 ⇒ **空数组而不是 null**。这把"允许清单→packageNames"的算术
 *    与"不许写死微信/QQ"一起钉住。
 * 2. 源码形状格（`SourceScan.maskComments` 剥注释后数）：服务确实**读了那份唯一真源、经纯函数换算、
 *    并且回写了 `serviceInfo`**——Android 的规矩是改完副本必须整颗 `serviceInfo = info` 交回系统，
 *    漏了这一步＝范围页保存后不生效。回退成什么就红：
 *    - 去掉 `serviceInfo = info` 回写 → 回写格红；
 *    - 把数组来源换成写死的包名（不调 `packageNamesFor`）→ 换算格红；
 *    - 在 diag 里 dump 包名明细（`joinToString`）→ 读数只许数数量格红；
 *    - onServiceConnected / onAccessibilityEvent 不再复检范围 → 接线格红。
 * 3. companion 读数：只留"声明了 N 个包名"这一级数量（-1＝从未设过）。
 *
 * 语义树/`stringResource` 这轮用不到，故不涉及。
 */
@RunWith(RobolectricTestRunner::class)
// 本族只算纯函数 + 拨 companion 读数 + 读源码文件，一行都不依赖 Koin 容器；走不 startKoin 的空壳 App。
// 默认清单那颗 App 在 onCreate 无条件 startKoin，而 GlobalContext 是 JVM 静态的——同沙箱第二个
// Application 必抛 KoinAppAlreadyStartedException，且抛在 Robolectric 装配阶段（用例里 stopKoin 拦不住）。
// 换的只是 Application：形状判的数法（maskComments 后数调用点、函数体配对）一颗没动。
@Config(application = UiProbeApplication::class)
class CopyCaptureServicePackageNamesTest {

    // ═══════════ 1. 纯函数格：允许清单 → 数组 ═══════════

    @Test
    fun `packageNamesFor returns exactly the given set, sorted`() {
        val allowed = setOf("com.tencent.mm", "com.alibaba.android.rimet")
        val names = CapturePolicy.packageNamesFor(allowed)
        // 排序确定，与 Set 迭代序无关
        assertArrayEquals(arrayOf("com.alibaba.android.rimet", "com.tencent.mm"), names)
        // 数组内容 = 那一份，无夹带、无丢失
        assertEquals(allowed, names.toSet())
        assertEquals(allowed.size, names.size)
    }

    @Test
    fun `packageNamesFor is order independent`() {
        val a = CapturePolicy.packageNamesFor(setOf("com.z", "com.a", "com.m"))
        val b = CapturePolicy.packageNamesFor(setOf("com.a", "com.m", "com.z"))
        assertArrayEquals(a, b)
    }

    @Test
    fun `empty allowlist yields an empty array, never null`() {
        val names = CapturePolicy.packageNamesFor(emptySet())
        // 返回类型是 Array<String>（非 null），这里钉的是语义：交出去的是那颗空数组对象，不是 null。
        assertEquals("空清单必须给空数组而不是 null", 0, names.size)
        // 反向钉死"设了包名就变收全部/偷偷加样例"：空清单结果里不许凭空冒出任何包名
        assertFalse(names.toList().contains("com.tencent.mm"))
        assertTrue(names.isEmpty())
    }

    @Test
    fun `blank package entries are dropped from the declaration`() {
        val names = CapturePolicy.packageNamesFor(setOf("com.tencent.mm", "", "  "))
        assertArrayEquals(arrayOf("com.tencent.mm"), names)
    }

    // ═══════════ 2. companion 读数：只到数量级 ═══════════

    @Test
    fun `declared package count reading stores only the count`() {
        CopyCaptureService.recordDeclaredPackageCount(3)
        assertEquals(3, CopyCaptureService.declaredPackageNameCount)
        CopyCaptureService.recordDeclaredPackageCount(0)
        assertEquals(0, CopyCaptureService.declaredPackageNameCount)
    }

    // ═══════════ 3. 源码形状：真源 → 换算 → 回写，且范围接线不脱 ═══════════

    private fun serviceSourceMasked(): String {
        val f = File("src/main/java/com/lovebrain/app/service/CopyCaptureService.kt")
        assertTrue("尺接错了文件，找不到 $f —— 宁可红也不许空转", f.isFile)
        return SourceScan.maskComments(f.readText(Charsets.UTF_8))
    }

    /** 取一个函数头 `{` 之后、配对 `}` 之前的函数体（maskComments 后注释已是空格，不影响配对）。 */
    private fun functionBody(masked: String, header: String): String {
        val at = masked.indexOf(header)
        assertTrue("尺读不到函数头「$header」", at >= 0)
        var i = at + header.length
        val start = i
        var depth = 1
        while (i < masked.length && depth > 0) {
            when (masked[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        assertTrue("函数「$header」括号没闭合，尺读歪了", depth == 0)
        return masked.substring(start, i - 1)
    }

    @Test
    fun `sync derives names from the sole source and writes serviceInfo back`() {
        val body = functionBody(
            serviceSourceMasked(),
            "private fun syncDeclaredPackageScope(allowed: Set<String>) {"
        )
        // 数组来自那份唯一真源经纯函数换算，而不是写死
        assertTrue("送达面数组必须来自 CapturePolicy.packageNamesFor: $body",
            body.contains("CapturePolicy.packageNamesFor("))
        assertTrue("必须把换算结果设进 info.packageNames: $body",
            Regex("""info\.packageNames\s*=""").containsMatchIn(body))
        // Android 回写规矩——这一行漏了，上面两行等于没做
        assertTrue("serviceInfo 改完必须整颗回写（serviceInfo = info）才生效: $body",
            Regex("""serviceInfo\s*=\s*info""").containsMatchIn(body))
    }

    @Test
    fun `scope reading logs only the count, never the package details`() {
        val body = functionBody(
            serviceSourceMasked(),
            "private fun syncDeclaredPackageScope(allowed: Set<String>) {"
        )
        assertTrue("diag 读数停在数量级 count=: $body",
            Regex("""appendDiag\("PACKAGE_SCOPE\|count=""").containsMatchIn(body))
        // 反向钉：不许把包名明细拼进日志（正文/明细是隐私红线）
        assertFalse("读数不许 dump 包名明细（joinToString）: $body", body.contains("joinToString"))
    }

    @Test
    fun `service connects and every event re-check the scope from the current allowlist`() {
        val src = serviceSourceMasked()
        // 连上就声明一次，且读的是 captureAllowedPackages 那份真源
        val connected = functionBody(src, "override fun onServiceConnected() {")
        assertTrue("onServiceConnected 未声明送达面: $connected",
            connected.contains("syncDeclaredPackageScope("))
        assertTrue("onServiceConnected 的范围来自 captureAllowedPackages: $connected",
            connected.contains("captureAllowedPackages"))
        // 每条事件复检，allowlist 变更不用重启服务就生效
        val eventBody = functionBody(src, "override fun onAccessibilityEvent(event: AccessibilityEvent) {")
        assertTrue("onAccessibilityEvent 未复检范围（保存后不生效）: ",
            eventBody.contains("syncDeclaredPackageScope("))
    }
}
