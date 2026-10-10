package com.lovebrain.app.ui.home

import androidx.compose.ui.graphics.Color
import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.core.designsystem.Success
import com.lovebrain.app.core.designsystem.Warning

/**
 * 首页那一格的**唯一**状态源。灯色、控件形状、下面那行黄字全部由它一次派生
 * （[render]），页面里不许再出现第二个 `when` 各判一遍。
 *
 * 四档的含义由产品合同写死：
 * - [Stopped]：没启动或已关闭 —— 红、▶、**不写常驻解释**；
 * - [Checking]：已点开始、正在检查 —— 黄、■、一句"正在检查…"；
 * - [RunningNeedsSetup]：已点开始但有真实缺项 —— 黄、■、那一行黄字逐个念；
 * - [RunningReady]：服务在跑 **且** 当前身份的连接真的检查成功 **且** 当前对象有知识库 —— 绿、■、不解释。
 */
enum class AdvisorState { Stopped, Checking, RunningReady, RunningNeedsSetup }

/** 灯三档。名字只说颜色语义，具体那个色值只在 [color] 这一处出现（沿用设计系统那三个语义色） */
enum class AdvisorLamp { Red, Yellow, Green }

internal val AdvisorLamp.color: Color
    get() = when (this) {
        AdvisorLamp.Red -> Error
        AdvisorLamp.Yellow -> Warning
        AdvisorLamp.Green -> Success
    }

/** 右侧那颗控件的两形：朝右三角=开始，方块=停止。形状是用户指定的，不许换成文字按钮 */
enum class AdvisorControl { Play, Stop }

/**
 * "哪一步没做"的具名步骤——黄字那一行念的就是它，一条一步，顺序固定。
 *
 * [label] 是**用户看得见的短句**，不是内部状态机参数：不写异常堆栈、不写 URL 候选、不写端口名。
 * ⚠ 这几条文案目前只能落在这里——本轮没有 `res/values` 的写入权（文件所有权边界），
 * 时把它们并进 `strings.xml` + `values-en`，这里换成 `@StringRes`。
 */
enum class AdvisorMissing(val label: String) {
    OverlayPermission("需要悬浮窗权限"),
    NoProvider("未配置模型供应商"),
    NoKnowledgeBase("请为当前对象建立知识库"),
    ConnectionFailed("连接失败，请检查Key或地址"),
    ServiceNotRunning("军师服务没起来"),

    /** 知识库这一条事实本机读不到（端口没接线）。不说成"没有"，也不说成"有"。 */
    KnowledgeUnread("还没读到当前对象的知识库"),

    /**
     * 这一组身份一次都没检查过，但也不是"试过失败"。
     * 生产里探针端口由 `HomeStatusViewModel` 接 `DeepSeekRepository.testConnectionWithProbe` 供着，
     * 只有按 ▶ 才走那一次；这一档留给"还没按过开始 / 供应商、模型、地址、Key 里有一位变了"的那些格子
     * （切知识库不改连接身份，见 `HomeStatusViewModel.identityOf`）——
     * 留着它是因为**没检查过绝不能点绿**，而把"没检查"报成"连接失败"是一句假话。
     *
     * ⚠ 文案 2026-10-05 由「连接还没检查成功」改成「连接还没检查过」（用户原话："…怎么会不成功呢？
     * …耗费 token 还谎报"）：原来那句落在"…成功"的否定式里，用户读成"检查失败了"，
     * 而这一刻的实情只是"还从没为这组身份按过 ▶"。**同一格、同一条字面量、只改话不改形状**：
     * `UiStringLiteralBudgetTest` 四把尺的锚点（`Text(` / `contentDescription =` / `stateDescription =` /
     * `Lb…(`）一条都数不到 enum 构造参（见该文件 EX-C2 那族 Context-free 结构性例外的登记原文），
     * 所以改字面量的**文本**不动任何一栏读数——它不是换桶，也没有新增字面量。
     */
    ConnectionUnchecked("连接还没检查过"),

    ;

    companion object {
        /** 多个缺项用一行短分隔（合同："多个缺项可用一行短分隔"） */
        fun describe(steps: List<AdvisorMissing>): String = steps.joinToString(separator = " · ") { it.label }
    }
}

/**
 * 那一行末尾那颗**去处**走哪一条路（指导书 §5.2 末段 + §2.2 第 5 条）。
 *
 * 名单收在这里而不是散在页面的 `if` 里，为的是"哪一条缺项配哪一种下一步"只有一处判据；
 * 页面只回答"这一条具体怎么跳"（导航 / 平台授权链 / 本地重读 / 再发那一次请求），
 * 它拿不到"该不该画这颗"的判断——那一半在本文件。
 *
 * ⚠ 这一族里**没有**"给 [AdvisorMissing.ServiceNotRunning] 与 [AdvisorMissing.ConnectionUnchecked]
 * 配一颗去处"这两档：那两条说的是事实，首页此刻没有对应的目的地，硬造一颗按钮就是假出口
 * （服务那一格的出口是状态卡右边那颗 ■/▶，连接那一格的出口是用户自己按 ▶）。
 */
enum class HomeMissingAction {
    /** 悬浮权限缺 → 走宿主那条平台链（它才认得系统授权页与"回来后继续启动"那半截） */
    GrantOverlay,

    /** 模型配置缺 → 四入口里的「模型供应商」那一格（§5.2：只有模型配置缺失才去供应商） */
    OpenProviders,

    /** 当前对象没有知识库 → 知识库管理页（与首页那颗入口卡同一个目的地） */
    OpenKnowledgeBase,

    /** 资料没读到 → 重读一次本地事实，**一个请求都不发**（§5.2：不伪装无库，也不谎报在检查） */
    RetryKnowledgeRead,

    /** 本次连接请求失败 → 在**那颗请求的位置**再发那一次（§5.2 末行：重试挂在请求处） */
    RetryConnection
}

/**
 * 一条缺项 → 它在首页有没有下一步。`null` = 这一条此刻没有目的地可给（见 [HomeMissingAction] 那条 ⚠）。
 *
 * 这是**状态 → 意图**的一次派生，与 [render] 同一族：页面里不许再出现第二个 `when` 各判一遍。
 */
internal val AdvisorMissing.homeAction: HomeMissingAction?
    get() = when (this) {
        AdvisorMissing.OverlayPermission -> HomeMissingAction.GrantOverlay
        AdvisorMissing.NoProvider -> HomeMissingAction.OpenProviders
        AdvisorMissing.NoKnowledgeBase -> HomeMissingAction.OpenKnowledgeBase
        AdvisorMissing.KnowledgeUnread -> HomeMissingAction.RetryKnowledgeRead
        AdvisorMissing.ConnectionFailed -> HomeMissingAction.RetryConnection
        AdvisorMissing.ServiceNotRunning, AdvisorMissing.ConnectionUnchecked -> null
    }

/** 状态 + 真实缺项 = 首页那一格的全部输入（无 lambda、无颜色，能被用例逐格比） */
data class AdvisorStatus(
    val state: AdvisorState,
    val missing: List<AdvisorMissing> = emptyList()
)

/**
 * 那一行末尾**至多一颗**去处，它跟着"最有用的那一项"走（§5.2 末段："先显示最有用的一项和对应动作"）。
 *
 * "最有用的一项" = 清单里的**第一条**，而清单顺序的唯一主人是
 * `HomeStatusViewModel.advisorMissingSteps`（权限 → 配置 → 知识库 → 连接 → 服务）——
 * 页面与本文件都不再排第二遍序，也不"跳过没有目的地的那一条去挑后面那条"
 * （那等于让一颗后面的大按钮盖住前面那件更该办的事，正是 §5.2 末段禁的形状）。
 */
internal val AdvisorStatus.primaryAction: HomeMissingAction?
    get() = missing.firstOrNull()?.homeAction

/**
 * [AdvisorStatus] 派生出来的渲染产物：灯、控件、两个读屏名字、那一行黄字。
 *
 * `hint == null` 就是"那一行不画"——红与绿两档都没有常驻解释，黄档才有话可说。
 */
data class AdvisorRender(
    val lamp: AdvisorLamp,
    val control: AdvisorControl,
    val lampDescription: String,
    val controlDescription: String,
    val hint: String?
)

/**
 * 一份状态 → 一份渲染。**这里只有这一个 `when`**：搬家之前是 `statusText` / `statusColor` /
 * `description` / `buttonText` / `buttonAction` 五处平行判据，它们可以各说各话。
 */
internal fun AdvisorStatus.render(): AdvisorRender = when (state) {
    AdvisorState.Stopped -> AdvisorRender(
        lamp = AdvisorLamp.Red,
        control = AdvisorControl.Play,
        lampDescription = HOME_LAMP_STOPPED,
        controlDescription = HOME_A11Y_PLAY,
        hint = null
    )
    AdvisorState.Checking -> AdvisorRender(
        lamp = AdvisorLamp.Yellow,
        control = AdvisorControl.Stop,
        lampDescription = HOME_LAMP_CHECKING,
        controlDescription = HOME_A11Y_STOP,
        hint = HOME_CHECKING_HINT
    )
    AdvisorState.RunningReady -> AdvisorRender(
        lamp = AdvisorLamp.Green,
        control = AdvisorControl.Stop,
        lampDescription = HOME_LAMP_READY,
        controlDescription = HOME_A11Y_STOP,
        hint = null
    )
    AdvisorState.RunningNeedsSetup -> AdvisorRender(
        lamp = AdvisorLamp.Yellow,
        control = AdvisorControl.Stop,
        lampDescription = HOME_LAMP_NEEDS_SETUP,
        controlDescription = HOME_A11Y_STOP,
        // 判据在 `HomeStatusViewModel.advisorStatusOf` 那一侧保证"这一档必带至少一条缺项"
        // （矩阵用例逐格比那条）。这里拿空清单就当没有话可说，不在组合期抛——黄字宁可缺席也不许崩这一屏。
        hint = if (missing.isEmpty()) null else AdvisorMissing.describe(missing)
    )
}

// ═══════ 用户可见短句（见 [AdvisorMissing] 那条 ⚠：等 res/values 的写入权，本轮只此一份）═══════

internal const val HOME_A11Y_PLAY = "开始军师服务"
internal const val HOME_A11Y_STOP = "停止军师服务"
internal const val HOME_CHECKING_HINT = "正在检查…"

internal const val HOME_LAMP_STOPPED = "军师未启动"
internal const val HOME_LAMP_CHECKING = "正在检查军师配置"
internal const val HOME_LAMP_READY = "军师已就绪"
internal const val HOME_LAMP_NEEDS_SETUP = "军师还差设置"

/** 四入口的名字：合同点名的四个，一个不多一个不少 */
internal const val HOME_ENTRY_KNOWLEDGE = "知识库"
internal const val HOME_ENTRY_FEEDBACK = "已踩案例"
internal const val HOME_ENTRY_CAPTURE = "消息捕获"
internal const val HOME_ENTRY_PROVIDER = "模型供应商"

/**
 * 四入口的**简短辅助描述**（M09 §5 第 4 条：恢复入口的辨识度 = 保留当前四个有用入口 +
 * 适当增加简短辅助描述）。
 *
 * 三条纪律：
 * - **只给还活着的那四格**写话。1.3.1 那四格（知识库 / 今日锦囊 / 谈心模式 / 感情五维）里有三格
 *   已被替换掉，旧副标题（"每日做法建议""分析关系困局""亲密·信任·承诺"）跟着功能一起离场，
 *   一个字都不许照搬——搬回来就是给不存在的功能画入口。
 *   唯一沿用的一句是「她的专属记忆」：那一格功能与目的地都没变（知识库），旧话说的还是它。
 * - **每句 5–7 字**，与旧版 `FeatureCard` 那一档同长度：入口卡只有半行宽（360dp 屏上约 150dp），
 *   `LbActionCard` 的副标题槽是 `labelSmall` + `maxLines = 1`，超了就省略号截掉。
 * - **说的是"点进去能办什么事"**，不是营销话：四句各自指得进那一页的真功能
 *   （知识库管理 / 点踩案例列表 / 捕获哪些应用 / 模型供应商与连接测试）。
 *
 * ⚠ 形状与上面那四颗名字完全一致：走 `internal const`，**不在 `LbActionCard(subtitle = …)`
 * 调用里内联中文**——后者会进 `UiStringLiteralBudgetTest` 的 COMPONENT 那把尺（锚点 `Lb…(`），
 * 而 const 声明不在任何锚点射程里（与 `HOME_ENTRY_*`、`HOME_LAMP_*` 同一族既有事实）。
 */
internal const val HOME_ENTRY_KNOWLEDGE_SUB = "她的专属记忆"
internal const val HOME_ENTRY_FEEDBACK_SUB = "复盘说错的对话"
internal const val HOME_ENTRY_CAPTURE_SUB = "选要读取的应用"
internal const val HOME_ENTRY_PROVIDER_SUB = "接哪个大模型"
