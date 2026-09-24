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
    // 端口视图必须解析到**同一个**仓库实例：KnowledgeRepository 自己 implements
    // KnowledgePort，所以这里只是给同一个对象开两个类型的门，不是再造一个包装。
    // 如果哪天这里变成 `single { FileKnowledgePort(get()) }`，两个 get() 会拿到两个包装——
    // 读写的还是同一个库，但"只有一个事务 owner"就不再能从图上读出来了。
    single<com.lovebrain.app.domain.port.KnowledgePort> { get<KnowledgeRepository>() }
    single<com.lovebrain.app.domain.port.KnowledgeReadPort> { get<KnowledgeRepository>() }
    single { DeepSeekRepository(get()) }
    // 端口绑定必须显式写 get<具体类>()：写成 get() 会解析到自己，Koin 直接 StackOverflowError
    single<com.lovebrain.app.domain.port.AiGateway> { get<DeepSeekRepository>() }
    // 时间是输入，不是日志：domain 里落进正文与 prompt 的时间一律走这个端口。
    // 现在靠构造参数的默认值（SystemClock）注入，绑在这里是为了
    // ① 让测试能在图外换一个 FixedClock，② 让"生产用的是真钟"这件事在图上看得见。
    single<com.lovebrain.app.domain.port.Clock> { com.lovebrain.app.domain.port.SystemClock }
    single { FeedbackCaseRepository(androidContext()) }

    // 领域层（单例）
    // Clock 显式 get()，不靠构造参数默认值：默认值是给测试用的后门，
    // 图上必须能看见"生产的时间从哪来"，否则上面那条 single<Clock> 就是装饰。
    single { com.lovebrain.app.domain.OngoingContextSelector(get(), get()) }
    single { PromptBuilder(androidContext(), get(), get(), get()) }
    single { RoundCommitJournal(get()) }
    // 事务日志必须由容器给出，不能在这里再 new 一个：
    // TopicRecorder 持有手工构造的 journal、容器又注册另一个 journal，
    // 就等于同一份 WAL 有两把 txMutex——"唯一事务 owner"只剩名字。
    single { TopicRecorder(get(), get(), get()) }
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
