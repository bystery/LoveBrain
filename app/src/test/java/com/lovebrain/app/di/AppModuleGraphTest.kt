package com.lovebrain.app.di

import android.content.Context
import android.content.SharedPreferences
import com.lovebrain.app.LoveBrainApp
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.FeedbackCaseRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.domain.ForegroundOperationCoordinator
import com.lovebrain.app.domain.KnowledgeTriggerCoordinator
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.RoundCommitJournal
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.viewmodel.KbEditViewModel
import com.lovebrain.app.viewmodel.KnowledgeBaseViewModel
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import com.lovebrain.app.viewmodel.SetupViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.error.InstanceCreationException
import org.koin.mp.KoinPlatformTools
import java.io.File

/**
 * Koin 生产图必须真解析一次。
 *
 * `AppModule` 里的注册是 `viewModel { X(get(), get(), androidContext()) }` 这种**位置参数**写法，
 * 构造函数加/删参数时编译期不会报错——要等到 App 首次 get() 才炸。
 * 本轮就改过 `SetupViewModel` 的构造签名（加 Context），所以把整张图在 JVM 上解析一遍：
 * 位置参数错位、缺依赖、循环依赖都会以 [InstanceCreationException] 暴露在这里，而不是在用户手机上。
 *
 * 不需要设备：Application/Context 用 mockk（LoveBrainApp 是 final class），
 * filesDir/cacheDir 指到 TemporaryFolder，SharedPreferences 给宽松桩。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModuleGraphTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 真实 CoroutineScope：备份/触发等 init 期 launch 会落到这里，空跑即可 */
    private val appScope = CoroutineScope(SupervisorJob())

    private fun fakeApp(): Context {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { prefs.edit() } returns editor
        every { editor.commit() } returns true
        every { prefs.getString(any(), any()) } returns null
        every { prefs.getStringSet(any(), any()) } returns null
        every { prefs.getBoolean(any(), any()) } returns false
        every { prefs.getInt(any(), any()) } returns 0
        every { prefs.getLong(any(), any()) } returns 0L
        every { prefs.contains(any()) } returns false

        val app = mockk<LoveBrainApp>(relaxed = true)
        every { app.filesDir } returns tmp.root
        every { app.cacheDir } returns tmp.root
        every { app.packageName } returns "com.lovebrain.app"
        every { app.applicationContext } returns app
        every { app.getSharedPreferences(any(), any()) } returns prefs
        every { app.applicationScope } returns appScope
        return app
    }

    @Before
    fun setUp() {
        // LoveBrainViewModel 会建 viewModelScope（Dispatchers.Main.immediate）；
        // JVM 上 Main 未接线会直接 IllegalStateException，这里装一个测试 Main 才能真解析图
        Dispatchers.setMain(StandardTestDispatcher())
        stopKoinIfRunning()
        startKoin {
            androidContext(fakeApp())
            modules(appModule)
        }
    }

    @After
    fun tearDown() {
        stopKoinIfRunning()
        Dispatchers.resetMain()
    }

    private fun stopKoinIfRunning() {
        runCatching { stopKoin() }
    }

    private inline fun <reified T : Any> resolve(): T =
        try {
            KoinPlatformTools.defaultContext().get().get()
        } catch (e: InstanceCreationException) {
            throw AssertionError(
                "Koin 无法构造 ${T::class.simpleName}——注册的位置参数与构造函数签名不一致：${e.cause}", e
            )
        }

    @Test
    fun `every singleton in the production graph can be constructed`() {
        resolve<SecurePrefs>()
        val repo = resolve<KnowledgeRepository>()
        assertSame("single 定义必须复用同一实例", repo, resolve<KnowledgeRepository>())
        resolve<DeepSeekRepository>()
        resolve<FeedbackCaseRepository>()
        resolve<PromptBuilder>()
        resolve<TopicRecorder>()
        resolve<RoundCommitJournal>()
        resolve<KnowledgeTriggerCoordinator>()
        resolve<GenerationEngine>()
        resolve<ForegroundOperationCoordinator>()
    }

    @Test
    fun `every view model in the production graph can be constructed`() {
        resolve<SetupViewModel>()
        resolve<KnowledgeBaseViewModel>()
        resolve<KbEditViewModel>()
        resolve<LoveBrainViewModel>()
    }

    /**
     * ViewModel 定义必须是工厂语义。
     *
     * 有人把 `viewModel {}` 改成 `single {}` 时，页面之间会共用一份可变 VM 状态；
     * 这条先红。
     */
    @Test
    fun `view model definitions are per-instance not shared singletons`() {
        assertNotSame(
            "KbEditViewModel 必须每次新实例",
            resolve<KbEditViewModel>(), resolve<KbEditViewModel>()
        )
        assertNotSame(
            "KnowledgeBaseViewModel 必须每次新实例",
            resolve<KnowledgeBaseViewModel>(), resolve<KnowledgeBaseViewModel>()
        )
        assertNotSame(
            "SetupViewModel 必须每次新实例",
            resolve<SetupViewModel>(), resolve<SetupViewModel>()
        )
    }
}
