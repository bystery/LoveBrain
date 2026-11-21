package com.lovebrain.app.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.lovebrain.app.core.testing.SourceScan
import com.lovebrain.app.core.testing.UiProbeApplication
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * **悬浮服务对 LoveBrainViewModel 的所有权**：VM 必须挂在本服务的 ViewModelStore 上，
 * 关服务时随 store.clear() 一起被清掉，而不是像裸 Koin `inject()` 那样无人 clear。
 *
 * 为什么这一族要单独钉：线上症状是"累计花费倒退/重复"——旧 VM 的 EAGER stateIn 流与
 * costEvents 收集器都挂在 viewModelScope 上，没有 owner 就没有 clear，收集器常驻并继续回写。
 * 所以判据不是"代码里出现了 ViewModelProvider"，而是三件可量的事：
 * 1. store.clear() 真的走到 VM.onCleared，且 viewModelScope 里的写入从那一刻起停止；
 * 2. 服务自己的 teardown 清的是同一只 store，且重复调用不崩（停止入口 + onDestroy 可能都走）；
 * 3. 服务里没有再出现第二条裸取 VM 的路（`by inject()` / `get<LoveBrainViewModel>`）。
 *
 * 服务的 onCreate 起不来（前台通知 + WindowManager + Koin 装配），所以这里只构造 Service
 * 实例（Robolectric 只 attach、不 create），teardown 本体因此收成 internal 的
 * [FloatingService.disposeRuntime] 以便直接量到；onDestroy 是否按序调用它由源码形状那格钉住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = UiProbeApplication::class)
class FloatingServiceViewModelOwnershipTest {

    /**
     * 最小模型：`write()` 就是线上那条"往状态/花费上回写"的收集器；
     * 它在 viewModelScope 里执行，clear 之后必须再也跑不起来。
     */
    private class ProbeViewModel : ViewModel() {
        var clearedCount = 0
        var writes = 0
        var lastJob: Job? = null

        fun write() {
            lastJob = viewModelScope.launch {
                writes++
                // 写一次后挂住：线上那条收集器是长命的（EAGER stateIn 常驻收集），
                // 瞬时完成的作业在 scope.cancel 后照样报 isCancelled=false，量不出取消
                awaitCancellation()
            }
        }

        override fun onCleared() {
            clearedCount++
        }
    }

    // ═══════════ 1. 机制本体：ViewModelProvider + store.clear ═══════════

    /**
     * 与生产同一条链：`ViewModelProvider(store, factory)[VM]` 建出的实例，随 store.clear()
     * 触发 onCleared 并取消 viewModelScope——这正是 `by inject()` 那条路缺的一步。
     */
    @Test
    fun `provider 建出的 VM 随 store clear 被清且 viewModelScope 不再写`() {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProbeViewModel() as T
        }
        val vm = ViewModelProvider(store, factory)[ProbeViewModel::class.java]
        vm.write()
        assertEquals("clear 前写一次，证明这条回写链本来是活的", 1, vm.writes)

        store.clear()

        assertEquals("store.clear 必须走到 VM.onCleared（裸 inject 那条路走不到）", 1, vm.clearedCount)
        assertTrue("viewModelScope 必须随 clear 取消", vm.lastJob!!.isCancelled)
        vm.write()
        assertEquals("clear 之后 EAGER 流/收集器不许再回写任何状态", 1, vm.writes)
    }

    // ═══════════ 2. 服务自己的 teardown：同一只 store、幂等 ═══════════

    /**
     * 服务实例上直接量：teardown 清的是 [FloatingService.viewModelStore] 这只 store，
     * 第二遍调用直接返回（既不重复 clear，也不碰已清 VM）。
     */
    @Test
    fun `服务 teardown 清空自己的 store 且重复调用不崩`() {
        val service = Robolectric.buildService(FloatingService::class.java).get()
        val probe = ProbeViewModel()
        service.viewModelStore.put("probe", probe)

        service.disposeRuntime()

        assertEquals("服务销毁必须清到同一只 store 里的 VM", 1, probe.clearedCount)
        val writesBefore = probe.writes
        probe.write()
        assertEquals("clear 之后不许再回写", writesBefore, probe.writes)

        service.disposeRuntime()
        assertEquals("重复 dispose 不许再清第二遍", 1, probe.clearedCount)
    }

    // ═══════════ 3. 源码形状：没有第二条裸取 VM 的路 ═══════════

    private fun masked(path: String): String {
        val f = File(path)
        assertTrue("尺接错了文件，找不到 $f —— 宁可红也不许空转", f.isFile)
        return SourceScan.maskComments(f.readText(Charsets.UTF_8))
    }

    private fun serviceSource() = masked("src/main/java/com/lovebrain/app/service/FloatingService.kt")

    @Test
    fun `VM 只从本服务 store 取且 teardown 顺序与幂等闸都在`() {
        val src = serviceSource()
        assertFalse("不许再有裸 Koin inject：那样 VM 没有 owner，关服务清不掉", src.contains("LoveBrainViewModel by inject()"))
        assertTrue(
            "VM 必须经本服务的 store 创建",
            src.contains("ViewModelProvider(store, viewModelFactory)[LoveBrainViewModel::class.java]")
        )
        assertTrue(
            "工厂仍走 Koin 那一格（构造参数不变）",
            src.contains("getKoin().get<LoveBrainViewModel>(LoveBrainViewModel::class)")
        )

        val at = src.indexOf("internal fun disposeRuntime()")
        assertTrue("尺读不到 teardown 本体", at >= 0)
        val body = src.substring(at, src.indexOf("viewModelInstance = null", at))
        val order = listOf(
            "compareAndSet(false, true)",
            "scope.cancel()",
            "viewModelInstance?.dispose()",
            "store.clear()"
        )
        var cursor = 0
        order.forEach { step ->
            val idx = body.indexOf(step)
            assertTrue("teardown 里看不见「$step」（漏一步就是漏一类泄漏）", idx >= 0)
            assertTrue("「$step」排到了上一步之前：先停监听与收集，再 VM 显式收尾，最后才 clear", idx >= cursor)
            cursor = idx
        }
        assertTrue(
            "clear 之后必须把引用清空：晚到回调不许碰已清 VM，也不许悄悄重建第二个",
            src.substring(at).contains("viewModelInstance = null")
        )
    }

    /**
     * 服务包内不许有第二个裸取 VM 的地方（第二个入口 = 第二个无主 VM）。
     * 只读扫描，不认任何别的类型名的注入。
     */
    @Test
    fun `service 包里没有第二处裸取 LoveBrainViewModel`() {
        val dir = File("src/main/java/com/lovebrain/app/service")
        assertTrue("尺接错了目录，找不到 $dir", dir.isDirectory)
        val offenders = dir.listFiles { f -> f.extension == "kt" }!!
            .filter { file ->
                // 工厂那一格（viewModelFactory 里的 Koin get）是唯一合法取用处，正向形状已由
                // 「VM 只从本服务 store 取…」那格钉住；剥掉它再扫，剩下的任何裸取形状都算第二个入口
                val code = SourceScan.maskComments(file.readText(Charsets.UTF_8))
                    .replace("getKoin().get<LoveBrainViewModel>(LoveBrainViewModel::class)", "")
                Regex("""(get|inject)<LoveBrainViewModel>|LoveBrainViewModel by inject\(\)""").containsMatchIn(code)
            }
            .map { it.name }
        assertEquals("服务包里只许 FloatingService 那一格持有 VM：$offenders", emptyList<String>(), offenders)
    }
}
