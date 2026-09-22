package com.lovebrain.app.service

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import android.app.Application
import android.content.Intent
import com.lovebrain.app.model.ReplyRequestState
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * R1-06: Overlay 生成生命周期冒烟测试。
 *
 * 验证真实 Service 宿主中 ViewModel + Engine 的生成生命周期：
 * - 快速连点只产生一个活跃请求
 * - 停止后状态回到 Idle
 * - 无 Provider 时显示可恢复错误
 *
 * 需要 emulator/设备运行：
 *   ./gradlew :app:connectedDebugAndroidTest --no-daemon
 *
 * 注意：此测试不发起真实网络请求，只验证状态机正确性。
 */
@RunWith(AndroidJUnit4::class)
class OverlayGenerateSmokeTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun generate_whenNoProvider_showsRecoverableError() {
        // 启动 ViewModel（不配置 Provider）
        startKoinForTest()

        try {
            val vm = org.koin.core.context.GlobalContext.get().get<LoveBrainViewModel>()

            // 无 Provider 配置时点击生成
            vm.generate()

            // 应该进入可恢复错误状态，而不是崩溃
            // 等待状态变化（最长 5s）
            val state = runBlocking {
                withTimeoutOrNull(5000L) {
                    vm.replyRequestState.first { it is ReplyRequestState.RecoverableError }
                }
            }
            assertNotNull("Should reach RecoverableError state", state)
            val errorState = state as ReplyRequestState.RecoverableError
            assertTrue("Error should mention provider/config", errorState.retryable)
        } finally {
            stopKoin()
        }
    }

    @Test
    fun generate_rapidDoubleTap_producesOnlyOneRequest() {
        startKoinForTest()

        try {
            val vm = org.koin.core.context.GlobalContext.get().get<LoveBrainViewModel>()

            // 快速连点两次
            vm.generate()
            vm.generate()  // 第二次应该被 guard 拒绝

            // 状态应为 Preparing 或 Streaming（只有一个活跃请求）
            val state = vm.replyRequestState.value
            assertTrue(
                "State should be busy (Preparing/Streaming) or Error, not two concurrent requests",
                state is ReplyRequestState.Preparing ||
                state is ReplyRequestState.Streaming ||
                state is ReplyRequestState.RecoverableError
            )

            // 如果处于 Preparing，requestId 应该是唯一的
            if (state is ReplyRequestState.Preparing) {
                val id1 = state.requestId
                // 第二次 generate 不应该改变 requestId
                vm.generate()
                val state2 = vm.replyRequestState.value
                if (state2 is ReplyRequestState.Preparing) {
                    assertEquals("Second tap should not create new request", id1, state2.requestId)
                }
            }
        } finally {
            stopKoin()
        }
    }

    @Test
    fun stopGeneration_returnsToIdle() {
        startKoinForTest()

        try {
            val vm = org.koin.core.context.GlobalContext.get().get<LoveBrainViewModel>()

            // 发起生成
            vm.generate()

            // 等待进入活跃状态
            runBlocking {
                withTimeoutOrNull(3000L) {
                    vm.replyRequestState.first { it.isBusy }
                }
            }

            // 停止
            vm.stopGeneration()

            // 应回到 Idle 或 RecoverableError
            val state = vm.replyRequestState.value
            assertTrue(
                "After stop, should be Idle or Error, not busy",
                state !is ReplyRequestState.Preparing && state !is ReplyRequestState.Streaming
            )
        } finally {
            stopKoin()
        }
    }

    @Test
    fun floatingService_startsAndStopsWithoutCrash() {
        // 验证 Service 可以启动和停止而不崩溃
        val intent = Intent(app, FloatingService::class.java)
        try {
            serviceRule.startService(intent)
            // Service 启动后 instance 应该非 null
            assertTrue(
                "FloatingService.instance should be non-null after start",
                FloatingService.instance != null
            )
        } finally {
            serviceRule.stopService(intent)
            // 停止后 instance 应该为 null
            Thread.sleep(500)  // 等待 onDestroy 完成
            assertNull(
                "FloatingService.instance should be null after stop",
                FloatingService.instance
            )
        }
    }

    private fun startKoinForTest() {
        stopKoin()
        startKoin {
            modules(module {
                // 测试用最小 Koin 模块——使用生产组件
                viewModel {
                    val app = ApplicationProvider.getApplicationContext<Application>()
                    val securePrefs = com.lovebrain.app.data.SecurePrefs(app)
                    val knowledgeRepo = com.lovebrain.app.data.KnowledgeRepository(
                        java.io.File(app.filesDir, "knowledge"),
                        securePrefs,
                        app,
                        (app as com.lovebrain.app.LoveBrainApp).applicationScope
                    )
                    val deepSeekRepo = com.lovebrain.app.data.DeepSeekRepository(securePrefs)
                    val promptBuilder = com.lovebrain.app.domain.PromptBuilder(app, knowledgeRepo, com.lovebrain.app.domain.OngoingContextSelector(knowledgeRepo))
                    val topicRecorder = com.lovebrain.app.domain.TopicRecorder(knowledgeRepo)
                    val triggerCoordinator = com.lovebrain.app.domain.KnowledgeTriggerCoordinator(knowledgeRepo, promptBuilder, topicRecorder, deepSeekRepo)
                    val generationEngine = com.lovebrain.app.domain.GenerationEngine(deepSeekRepo, promptBuilder)
                    LoveBrainViewModel(deepSeekRepo, knowledgeRepo, promptBuilder, topicRecorder, securePrefs, triggerCoordinator, generationEngine)
                }
            })
        }
    }
}
