package com.lovebrain.app

import android.app.Application
import com.lovebrain.app.di.appModule
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class LoveBrainApp : Application() {

    /**
     * 应用级协程异常兜底（ / ）：
     * 后台作业（备份、知识库触发等）抛未捕获异常时记日志不崩。
     * SupervisorJob 已防兄弟取消，此 Handler 兜底未被 runCatching 包住的漏网异常。
     */
    private val appCrashHandler = CoroutineExceptionHandler { _, t ->
        com.lovebrain.app.util.L.e("applicationScope uncaught coroutine exception", t)
    }

    /**
     * 应用级协程作用域：替代 GlobalScope，生命周期与 Application 绑定。
     * 使用 SupervisorJob 确保子协程异常不会取消兄弟协程。
     * 调研依据：Kotlin 官方文档明确建议避免 GlobalScope（无法管理生命周期、易泄漏）。
     */
    val applicationScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + appCrashHandler
    )

    override fun onCreate() {
        super.onCreate()

        // 初始化 Koin 依赖注入
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LoveBrainApp)
            modules(appModule)
        }

        // 资产自检：注册处声明的全部资产必须真实存在，缺失立即报警——
        // 防  类"资产搬家/改名后代码静默断链"重演。release 不执行。
        if (BuildConfig.DEBUG) {
            com.lovebrain.app.domain.AssetRegistry.ALL.forEach { path ->
                runCatching { assets.open(path).use { /* 只验存在 */ } }
                    .onFailure { com.lovebrain.app.util.L.e("AssetRegistry 自检失败：$path 不存在（代码即将读到空串）", it) }
            }
        }

        // 暗色模式已删，全站固定亮色；主题色固定 DeepSeek 浅蓝（Color.kt 默认 230°）
    }
}
