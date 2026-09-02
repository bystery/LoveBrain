package com.lovebrain.app.di

import com.lovebrain.app.LoveBrainApp
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.domain.KnowledgeTriggerCoordinator
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.TopicRecorder
import com.lovebrain.app.viewmodel.LoveBrainViewModel
import com.lovebrain.app.viewmodel.SetupViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import java.io.File

/**
 * Koin 依赖注入模块。
 * 所有 Repository / Domain / ViewModel 在此注册为单例或工厂，
 * 消除之前 4 处重复 new KnowledgeRepository 的问题。
 */
val appModule = module {

    // 数据层（单例）
    single { SecurePrefs(androidContext()) }
    single { KnowledgeRepository(File(androidContext().filesDir, "knowledge"), get(), androidContext(), (androidApplication() as LoveBrainApp).applicationScope) }
    single { DeepSeekRepository(get()) }

    // 领域层（单例）
    single { PromptBuilder(androidContext(), get()) }
    single { TopicRecorder(get()) }
    single { KnowledgeTriggerCoordinator(get(), get(), get(), get()) }
    single { GenerationEngine(get(), get()) }

    // ViewModel（每次获取新实例）
    viewModel { LoveBrainViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SetupViewModel(get(), get()) }  // 第二个 get() 取 DeepSeekRepository 单例
}
