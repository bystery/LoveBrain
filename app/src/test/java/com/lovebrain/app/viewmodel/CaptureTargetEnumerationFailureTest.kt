package com.lovebrain.app.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.core.testing.UiProbeApplication
import com.lovebrain.app.data.SecurePrefs
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * §6.3 捕获范围页 Error 那一格的**来源**：枚举候选 App 失败必须与"真的没有候选"分得开。
 *
 * 旧写法 `runCatching { pm.queryIntentActivities(...) }.getOrDefault(emptyList())`
 * 把三种情况压成一种：抛异常、平台返回 null、确实一台可启动的都没有。
 * `queryIntentActivities` 是同步 IPC，装机量大时 `TransactionTooLargeException`
 * 与远端进程死亡都从这里过——那些以前都会让用户看到"这台设备没有可授权的 App"。
 *
 * 这一组钉的就是拆开之后的两种答案：抛 → null（读不出来）；给空表 → 空表（**不是** null）。
 * "平台回 null"那条在本机构造不出来，原因写在下面那格上面，不当已验收益记。
 * 另外钉住搬运过程中没改的既有语义：排除本应用自身、按显示名排序、包名去重、
 * 二次拒绝的类别仍然出现在列表里（只是不能勾）。
 * 一个 JVM 边界要说清：本机 `ResolveInfo.loadLabel` 取不到标签（android.jar 桩回 null，
 * 被被测代码的 runCatching 兜成包名），所以断言里 displayName 就是包名——这恰好把
 * "取不到标签不许显示空字符串"这条兜底也钉住了。真机上标签读得出来，排序键随之变成标签；
 * **这条差异本轮没有任何用例覆盖**（androidTest 里也没有捕获页的用例），记在账本 §21.6 的欠账里。
 *
 * 为什么挂 Robolectric：这组用例要走真实的 `Intent(ACTION_MAIN).addCategory(...)`，
 * 而裸 JVM 下 android.jar 的桩把 `addCategory` 回成 null（`isReturnDefaultValues=true` 的副作用），
 * 四格全崩在构造那行、根本到不了被测代码。PackageManager 仍然用 mockk，
 * Robolectric 只负责让 Intent 是个真的 Intent。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = UiProbeApplication::class)
class CaptureTargetEnumerationFailureTest {

    private val selfPackage = "com.lovebrain.app"

    private fun vm() = SetupViewModel(
        mockk<SecurePrefs>(relaxed = true),
        mockk<DeepSeekRepository>(relaxed = true),
        null,
        null
    )

    private fun contextWith(pm: PackageManager, packageName: String = selfPackage): Context =
        mockk<Context>(relaxed = true).also {
            every { it.packageManager } returns pm
            every { it.packageName } returns packageName
        }

    private fun resolve(pkg: String): ResolveInfo = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply { packageName = pkg }
    }

    @Test
    fun `a throwing enumeration says read-failed instead of no-apps-installed`() {
        val pm = mockk<PackageManager>()
        every { pm.queryIntentActivities(any<Intent>(), any<Int>()) } throws
            RuntimeException("Transaction too large")

        assertNull("抛异常时仍然返回空表 = 页面会撒谎说没有可授权的 App", vm()
            .selectableCaptureTargets(contextWith(pm)))
    }

    /**
     * 平台真的回 null 这条**在本机构造不出来**，所以这里没有对应用例。
     *
     * 当前 compileSdk 把 `queryIntentActivities(Intent, int)` 标成 `@NonNull`，
     * mockk 的 `returns null` 在编译期就被拒（"Null can not be a value of a non-null type"）。
     * 生产代码里那条 `?: return null` 因此是**防注解撒谎的兜底**（个别 ROM 确实会回 null，
     * 而它一旦漏出去就是组合期 NPE 崩整页），不当作已验收益记账——
     * 它守的"读不出来"这一格在 UI 侧由 `captureScreenState(null)` 那组用例钉住。
     */
    @Test
    fun `an empty result is an empty list not a failure`() {
        val pm = mockk<PackageManager>()
        every { pm.queryIntentActivities(any<Intent>(), any<Int>()) } returns mutableListOf()

        val got = vm().selectableCaptureTargets(contextWith(pm))
        assertNotNull("平台明确回了空表，就不该报成读不出来", got)
        assertTrue(got!!.isEmpty())
    }

    @Test
    fun `the mapping keeps excluding this app and deduplicating packages`() {
        val pm = mockk<PackageManager>()
        every { pm.queryIntentActivities(any<Intent>(), any<Int>()) } returns mutableListOf(
            resolve(selfPackage), resolve("com.b"), resolve("com.b"), resolve("com.a")
        )

        val got = vm().selectableCaptureTargets(contextWith(pm))!!

        assertEquals("本应用自己不该出现在授权列表里：$got", 2, got.size)
        assertEquals(listOf("com.a", "com.b"), got.map { it.packageName })
        assertTrue(
            "标签取不到时退回包名，不许留空字符串",
            got.all { it.displayName.isNotBlank() }
        )
    }

    @Test
    fun `a second-reject category stays listed but flagged`() {
        val pm = mockk<PackageManager>()
        val blocked = "com.bank.app"
        every { pm.queryIntentActivities(any<Intent>(), any<Int>()) } returns mutableListOf(
            resolve(blocked), resolve("com.chat.app")
        )

        val got = vm().selectableCaptureTargets(contextWith(pm))!!

        assertEquals("被二次拒绝的类别也要显示出来，让用户看得见为什么不能勾", 2, got.size)
        val flagged = got.first { it.packageName == blocked }
        val plain = got.first { it.packageName == "com.chat.app" }
        assertTrue("$blocked 应被标成不可授权", flagged.secondRejected)
        assertTrue("普通聊天类不该被误标", !plain.secondRejected)
    }
}
