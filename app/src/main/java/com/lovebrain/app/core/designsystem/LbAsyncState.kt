package com.lovebrain.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign

/** 自动化锚点：状态换了tag不换，文字换了tag也不换 */
object LbAsyncTags {
    const val LOADING = "lb_async_loading"
    const val MESSAGE = "lb_async_message"
    const val ACTION = "lb_async_action"
}

/**
 * 语气：空态是中性灰，错误态要把话说到"这里坏了"，通知那一族（提醒 / 已成事实）
 * 各说各的那一档。
 *
 * 为什么颜色走**词表**而不是走参数：§6.1 :490 末句拦的是"只在一个页面看起来不一样"。
 * 面板顶部那三张通知条原先自己带着 `container: Color` / `textColor: Color` 两个旋钮
 * （`SuccessBg`/`Success` 是默认值、`WarningBg`/`Warning` 是第二处调用现填的），
 * 于是"这条通知是什么语气"变成了每页自选一对颜色——下一对颜色就没有地方拦。
 * 现在语气只有这四个名字，字色与浅底各自只在下面这两张表里出现一次。
 */
enum class LbStateTone { Neutral, Error, Warning, Success }

/** 说明文字的字色。与 [stripBackground] 同进同退：换一档语气就同时换字色与浅底。 */
internal val LbStateTone.ink: Color
    get() = when (this) {
        LbStateTone.Neutral -> TextHint
        LbStateTone.Error -> Error
        LbStateTone.Warning -> Warning
        LbStateTone.Success -> Success
    }

/**
 * [LbStateContainer.Strip] 那一档的浅底。
 *
 * `Neutral` 用的是 `SurfaceInset`（中性说明条），不是透明——这一档在本仓库今天还没有调用方，
 * 但 `when` 必须对四档都有答案，否则下一位调用方会自己补一个颜色，那就又是旋钮。
 * 对比度由 `ContrastRegressionTest` 那把尺盯着（`TextHint` vs `SurfaceInset`、
 * `Warning` vs `WarningBg` 两对都已经在它表里 ≥4.5:1）。
 */
internal val LbStateTone.stripBackground: Color
    get() = when (this) {
        LbStateTone.Neutral -> SurfaceInset
        LbStateTone.Error -> ErrorBg
        LbStateTone.Warning -> WarningBg
        LbStateTone.Success -> SuccessBg
    }

/**
 * 同一份"说明 + 可选动作"的**两种容器**（组件仍只有一颗，形状只在这里画一次）。
 *
 * 为什么要第二档，而不是让面板继续自己画一条：本轮收口的六处里，
 * 四处是"这一整块出事了的**底板错误态**"（结果区、锦囊、谈心、主动发结果区），
 * 两处是"别的内容还在、只在其上挂一条话的**通知条**"（面板顶部那三张、
 * 输入已变化那一条）。前者的说明可以多行、可以居中；后者今天写作
 * `maxLines = 1 + Ellipsis`，把 20dp 高的条压在滚动列表中间。
 * 把 70dp 那式硬塞给通知条，等于把面板顶部撑高一倍；把通知条那式硬塞给错误态，
 * 长错因就会被裁成"网络断了，这轮没生…"——**少掉的那半句是用户自救唯一的线索**。
 * 所以给两个**有名字的档**，不留一个 `containerColor:`/`shape:` 参数让每页自选：
 * 那种参数等于把"每页另造样式"合法化（同 `LbTopBarLevel`、同 `LbMetricDensity`）。
 */
enum class LbStateContainer {
    /** 整块：居中一叠、透明底——[LbAsyncState] 的 Empty/Error 两档用的就是这个 */
    Block,

    /** 就地一条：语气浅底的一横条，说明在前、动作在后；面板里那六处用的就是这个 */
    Strip
}

/**
 * 统一的空态/错误态/通知态版式：一段主说明 + 一个可选动作。
 *
 * 三条硬规定（都来自 §6.1 那张组件表，不是审美偏好）：
 * - **动作是一处操作，不是一行文字**：热区垫到见方，且带 `Role.Button`。这一条现在
 *   由 [LbTextAction] 那唯一一处实现负责——本文件过去自己画了一颗 `Box + Text`，
 *   与页面级那颗「跳过」是同一个形状、各写各的数，这一格把它并过去了。
 *   前例见 `MessageList` 那个 224x23dp 的蓝字入口——被引导去点的地方点不到，等于没有入口。
 * - 说明文字带 `contentDescription` 语义锚点，读屏/截图两边都能定位。
 * - 不允许再出现"每页自己画一个居中 Text"：那正是 §6.3 要消灭的东西。
 *
 * ## 这次加的那一档改了什么契约（要写清，别让它慢慢漂）
 *
 * [container] = [LbStateContainer.Strip] 之后，**"一条带语气浅底的状态条"从此有了主人**：
 * 面板里那六处自画的 `Box/Row + .background(ErrorBg/WarningBg/SuccessBg)` 都转进这里。
 * 随归并一起变掉的三件事，如实记在这儿（都是"复用"的代价，不是顺手改外观）：
 * 1. **字号统一抬到 [AppTypography.bodyMedium]**（原先四处错误条写 `bodySmall`、
 *    两处通知条写 `labelSmall`）。`EmptyStateOwnershipSemanticsTest` 第④栏已经为同一件事
 *    立过证人（换所有者之后必须回扫 12 格排布），本轮新加的证人见
 *    `NoticeStripSemanticsTest`。
 * 2. **通知条那颗 `maxLines = 1 + Ellipsis` 不再由组件提供**：裁掉半句等于丢话，
 *    而这一族的说明正是用户下一步该做什么的唯一依据。现在整句排开，条子按内容长高。
 * 3. **底色成对走词表，页面拿不到颜色**：`InputChangedBanner` 那条 1dp `Warning` 描边随归并
 *    消失（要留住它就得开一个 `borderColor` 旋钮，同 [LbTextAction] 归并 `RowActionButton`
 *    时那层浅灰胶囊底的处置一样——归并换的是**所有者**，不是给组件加参数）。
 *
 * 不变的那三样：说明文字逐字不变、动作仍是同一颗回调、
 * 动作的热区与角色由 [LbTextAction] 那一处保证（两轴都垫到下限，不是只垫高度）。
 */
@Composable
fun LbEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    action: ScreenAction? = null,
    tone: LbStateTone = LbStateTone.Neutral,
    container: LbStateContainer = LbStateContainer.Block
) {
    when (container) {
        LbStateContainer.Block -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StateMessage(message = message, tone = tone)
            if (action != null) {
                Spacer(Modifier.height(Spacing.md))
                StateAction(action)
            }
        }

        // 就地一条：浅底 + 描边都不在这里画，颜色只从语气词表来（[LbStateTone.stripBackground]）。
        // 说明占满余宽、动作贴尾部；动作那颗的热区/角色与整块那一档**完全同一处实现**。
        LbStateContainer.Strip -> Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(LoveBrainShape.md)
                .background(tone.stripBackground, LoveBrainShape.md)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            StateMessage(message = message, tone = tone, modifier = Modifier.weight(1f))
            if (action != null) StateAction(action)
        }
    }
}

/** 说明那一行。两档容器共用这一颗，所以"说法 + 锚点 + 字色"只在这里写一遍。 */
@Composable
private fun StateMessage(
    message: String,
    tone: LbStateTone,
    modifier: Modifier = Modifier
) {
    Text(
        text = message,
        style = AppTypography.bodyMedium,
        color = tone.ink,
        textAlign = TextAlign.Center,
        modifier = modifier
            .semantics { contentDescription = message }
            .testTag(LbAsyncTags.MESSAGE)
    )
}

/**
 * 动作那一颗——本文件里"文字动作"唯一的出口。
 *
 * 这颗以前是本文件自己画的 `Box + Text`，现在转进设计系统里那唯一一处
 * "文字动作"（[LbTextAction]）。三件事没变、只是不再重复：热区垫到见方、
 * `Role.Button` 只声明在外层盒上、短标签也要够宽（第一版只垫高度时"重试"
 * 量出 40x48dp，被自家测试测红过一次——那段记录搬去了 LbTextAction.kt）。
 * testTag 仍挂在**外层可点击盒**上：挪进里面的 Text 就成了"锚点找得到、
 * 按钮找不到"。
 *
 * ⚠ 本文件里 `LbTextAction(` 只许出现**一次**、`.clickable(` 只许出现**零次**——
 * 这是 `UiLayerDependencyContractTest > the design-system text action has exactly one owner`
 * 钉着的两个数。加一档容器时如果把动作再抄一遍到那一档里，红的就是那一格。
 */
@Composable
private fun StateAction(action: ScreenAction) {
    LbTextAction(
        label = action.label,
        onClick = action.run,
        modifier = Modifier.testTag(LbAsyncTags.ACTION)
    )
}

/**
 * 一个目的地的四态渲染器——§6.3 那四类状态的唯一出口。
 *
 * 它**不判**该显示哪一格：状态由持有者算好传进来。
 * 理由见 [ScreenState] 的注释——判据一旦在两处各写一遍，两边就会慢慢长得不一样
 * （本轮在 `KnowledgeRepository.listAll` 上刚撞到过一次同样的形状）。
 */
@Composable
fun <T> LbAsyncState(
    state: ScreenState<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    when (state) {
        ScreenState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = Primary,
                modifier = Modifier
                    .size(Spacing.xl)
                    .testTag(LbAsyncTags.LOADING)
            )
        }
        is ScreenState.Empty -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LbEmptyState(message = state.message, action = state.action)
        }
        is ScreenState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LbEmptyState(message = state.message, action = state.retry, tone = LbStateTone.Error)
        }
        is ScreenState.Content -> content(state.value)
    }
}
