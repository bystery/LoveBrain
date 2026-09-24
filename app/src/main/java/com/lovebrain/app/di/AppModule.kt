package com.lovebrain.app.di

import com.lovebrain.app.LoveBrainApp
import com.lovebrain.app.data.DeepSeekRepository
import com.lovebrain.app.data.FeedbackCaseRepository
import com.lovebrain.app.data.KnowledgeRepository
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.GenerationEngine
import com.lovebrain.app.domain.KnowledgeTriggerCoordinator
import com.lovebrain.app.domain.PromptBuilder
import com.lovebrain.app.domain.RoundCommitJournal
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
    single { FeedbackCaseRepository(androidContext()) }

    // 领域层（单例）
    single { com.lovebrain.app.domain.OngoingContextSelector(get()) }
    single { PromptBuilder(androidContext(), get(), get()) }
    single { RoundCommitJournal(get()) }
    // 事务日志必须由容器给出，不能在这里再 new 一个：
    // TopicRecorder 持有手工构造的 journal、容器又注册另一个 journal，
    // 就等于同一份 WAL 有两把 txMutex——"唯一事务 owner"只剩名字。
    single { TopicRecorder(get(), get()) }
    single { KnowledgeTriggerCoordinator(get(), get(), get(), get()) }
    single { GenerationEngine(get(), get()) }
    // ForegroundOperationCoordinator 作为单例——使用 application scope
    single { com.lovebrain.app.domain.ForegroundOperationCoordinator((androidApplication() as LoveBrainApp).applicationScope) }

    // ViewModel（每次获取新实例）
    viewModel { LoveBrainViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SetupViewModel(get(), get(), get(), androidContext()) }  // securePrefs, DeepSeekRepository, FeedbackCaseRepository, Context
    viewModel { com.lovebrain.app.viewmodel.KnowledgeBaseViewModel(androidContext(), get(), get()) }
    viewModel { com.lovebrain.app.viewmodel.KbEditViewModel(get(), get()) }
}
