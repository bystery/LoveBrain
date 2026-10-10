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
    // 页面注入这颗端口视图、DeepSeekRepository 注入具体 SecurePrefs——两者解析到**同一个**实例，
    // 加密与降级判据仍只有 SecurePrefs 一处，端口没有开出第二条落盘口。
    single<com.lovebrain.app.domain.port.SettingsStorePort> { get<SecurePrefs>() }
    single { KnowledgeRepository(File(androidContext().filesDir, "knowledge"), get(), androidContext(), (androidApplication() as LoveBrainApp).applicationScope) }
    // 端口视图必须解析到**同一个**仓库实例：KnowledgeRepository 自己 implements
    // KnowledgePort，所以这里只是给同一个对象开两个类型的门，不是再造一个包装。
    // 如果哪天这里变成 `single { FileKnowledgePort(get()) }`，两个 get() 会拿到两个包装——
    // 读写的还是同一个库，但"只有一个事务 owner"就不再能从图上读出来了。
    single<com.lovebrain.app.domain.port.KnowledgePort> { get<KnowledgeRepository>() }
    single<com.lovebrain.app.domain.port.KnowledgeReadPort> { get<KnowledgeRepository>() }
    // 写口视图同样指回**同一个**仓库实例：OngoingPlanStore 这类只要写能力的 domain 协作者
    // 因此能拿到"只有写成员"的最窄视角，而落盘边界仍是仓库那一处，不是第二条链。
    single<com.lovebrain.app.domain.port.KnowledgeWritePort> { get<KnowledgeRepository>() }
    // 页面侧那三颗端口（viewmodel 注入的类型）里，文档与运行时这两颗仍是给**同一个**仓库对象
    // 再开一扇门。判据不是"少写几行"：这两颗里任何一颗换成 `single { SomeWrapper(get()) }`，
    // 图上就会出现两个持有文件系统的对象，"只有一个事务 owner"就不再能从图上读出来了。
    single<com.lovebrain.app.domain.port.KnowledgeDocumentPort> { get<KnowledgeRepository>() }
    // 目录那颗不一样：实现者是仓库持有的协作者 `catalogWrites`（KnowledgeCatalogWriteStore），
    // 不是仓库本身。它是"库的存在性"的写侧主人——建、删、切当前库、改显示名第一次可以在
    // **不抱着整个仓库**的情况下被调用（第5节第3条 那条完成定义）。
    // 这不算第二个持有文件系统的对象：它不持锁、不摸 File、不落盘，四件承重的事都经
    // CatalogWriteStorage 问回仓库（唯一那把 fileMutex、唯一那条写链、唯一那道路径守门）。
    // 换成 `get<KnowledgeRepository>()` 就等于把这格又塞回唯一入口，那才是回退。
    single<com.lovebrain.app.domain.port.KnowledgeBaseCatalogPort> { get<KnowledgeRepository>().catalogWrites }
    single<com.lovebrain.app.domain.port.KnowledgeRuntimePort> { get<KnowledgeRepository>() }
    // 归档能力交给一个只搬流的小适配器：它把目录归属（knowledge/ 根、cache 暂存）收在实现侧，
    // 转手仍用那唯一的 KbArchiveTransfer——不是第二条写链，只是把页面从具体类名解耦。
    single<com.lovebrain.app.domain.port.KbArchivePort> {
        com.lovebrain.app.data.FileKbArchiveTransfer(
            knowledgeRoot = File(androidContext().filesDir, "knowledge"),
            stagingBase = androidContext().cacheDir
        )
    }
    single { DeepSeekRepository(get()) }
    // 端口绑定必须显式写 get<具体类>()：写成 get() 会解析到自己，Koin 直接 StackOverflowError
    single<com.lovebrain.app.domain.port.AiGateway> { get<DeepSeekRepository>() }
    // Prompt 资产读取交给 assets 适配器：domain/PromptBuilder 从此不抱 Context（还 PackageDependencyTest
    // 基线里 domain 那条债）。缺资产的 fail-open 兜底（空串 + 日志）随读取挪进 AssetPromptSource，
    // 与旧 readAsset 逐字同语义——端口没有开出第二条资产通道。
    single<com.lovebrain.app.domain.port.PromptSourcePort> {
        com.lovebrain.app.data.AssetPromptSource { path -> androidContext().assets.open(path) }
    }
    // 「本机是否已有知识库」判给 data 实现：knowledge/ 根的路径归属收在 FileKnowledgePresence，
    // SetupViewModel 不再自己拼 File（viewmodel 禁 java.io.File 那条债）。
    single<com.lovebrain.app.domain.port.KnowledgePresencePort> {
        com.lovebrain.app.data.FileKnowledgePresence(File(androidContext().filesDir, "knowledge"))
    }
    // 面板打开请求的端口视图：EventBus 本体 implements PanelRequestPort，两边解析到同一个单例，
    // 事件没有第二条通道（与上面 SettingsStorePort 那格同一张处方；ui 从此不认 data.EventBus 的类型）。
    single<com.lovebrain.app.domain.port.PanelRequestPort> { com.lovebrain.app.data.EventBus }
    // 时间是输入，不是日志：domain 里落进正文与 prompt 的时间一律走这个端口。
    // 现在靠构造参数的默认值（SystemClock）注入，绑在这里是为了
    // ① 让测试能在图外换一个 FixedClock，② 让"生产用的是真钟"这件事在图上看得见。
    single<com.lovebrain.app.domain.port.Clock> { com.lovebrain.app.domain.port.SystemClock }
    single { FeedbackCaseRepository(androidContext()) }

    // 领域层（单例）
    // Clock 显式 get()，不靠构造参数默认值：默认值是给测试用的后门，
    // 图上必须能看见"生产的时间从哪来"，否则上面那条 single<Clock> 就是装饰。
    single { com.lovebrain.app.domain.OngoingContextSelector(get(), get()) }
    // PromptBuilder 只认 PromptSourcePort（第一格），不再传 androidContext()
    single { PromptBuilder(get(), get(), get(), get()) }
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
    viewModel {
        LoveBrainViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(),
            // 面板**背景层**浓度那一格：读写口接在 `SecurePrefs` 上（盘上那唯一一格与
            // `PanelBackdropOpacity` 那把刻度尺都在它身上），VM 只拿到一对函数、不认识具体存储类。
            // 这两行不接时 VM 那侧是"读默认 100%、写丢弃"，面板照常能画，但设置滑杆就不跨重启了。
            readBackdropOpacityPercent = { get<SecurePrefs>().panelBackdropOpacityPercent },
            writeBackdropOpacityPercent = { get<SecurePrefs>().panelBackdropOpacityPercent = it },
            // 「仅看本轮」首次轻提示的"提示过没有"旗标（§2.2 第 3 条）：与上面那对同一形状、
            // 接在同一个 SecurePrefs 上——盘上那一格只有一份，落盘后每次回来不再重弹。
            readRoundScopeHintShown = { get<SecurePrefs>().roundScopeHintShown },
            writeRoundScopeHintShown = { get<SecurePrefs>().roundScopeHintShown = it }
        )
    }
    // 第四格是 KnowledgePresencePort（老用户判定的"本机有没有知识库"那一员），不再是 Context
    viewModel { SetupViewModel(get(), get(), get(), get(), get()) }
    viewModel { com.lovebrain.app.viewmodel.KnowledgeBaseViewModel(androidContext(), get(), get(), get()) }
    viewModel { com.lovebrain.app.viewmodel.KbEditViewModel(get(), get()) }
    // 首页那盏灯（）。装配收在 viewmodel 那一侧的那一颗函数里，这一格只点名一颗跨层类型；
    // 三颗 get() 按位置解析到 SettingsStorePort / KnowledgeReadPort / DeepSeekRepository——
    // 也就是**盘上那一份偏好、仓库那一份读口、探测那一条链**，没有第二颗 SecurePrefs、
    // 也没有第二个"当前供应商"的缓存主人（为什么不走 SetupViewModel：见那颗函数上的注释）。
    // 探针唯一的触发条件是用户在首页按 ▶，进页面/切子页回来都只重读事实，所以这里没有任何
    // "注册即发请求"的形状：Koin 造这颗 VM 时不碰网络。
    viewModel { com.lovebrain.app.viewmodel.newHomeStatusViewModel(get(), get(), get()) }
}
