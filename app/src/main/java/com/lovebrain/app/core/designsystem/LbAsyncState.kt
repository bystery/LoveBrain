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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/** 自动化锚点：状态换了tag不换，文字换了tag也不换 */
object LbAsyncTags {
    const val LOADING = "lb_async_loading"
    const val MESSAGE = "lb_async_message"
    const val ACTION = "lb_async_action"
}

/**
 * 语气：空态是中性灰，错误态要把话说到"这里坏了"，通知那一族（提醒 / 已成事实）各说各的那一档。
 *
 * 颜色走**词表**而不走参数：`container: Color` / `textColor: Color` 一开成旋钮，
 * "这条通知是什么语气"就变成每页自选一对颜色，下一对颜色就没有地方拦。
 * 语气只有这四个名字，字色与浅底各自只在下面这两张表里出现一次。
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
 * `Neutral` 用的是 `SurfaceInset`（中性说明条），不是透明——`when` 必须对四档都有答案，
 * 否则下一位调用方会自己补一个颜色，那就又是旋钮。
 * 对比度由 `ContrastRegressionTest` 盯着（`TextHint` vs `SurfaceInset`、`Warning` vs `WarningBg`
 * 两对都在它表里 ≥4.5:1）。
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
 * 为什么是两档：一处是"这一整块出事了的**底板错误态**"（说明可以多行、可以居中），
 * 一处是"别的内容还在、只在其上挂一条话的**通知条**"（贴着 20dp 的一横条排）。
 * 把 70dp 那式硬塞给通知条，等于把面板顶部撑高一倍；把通知条那式硬塞给错误态，
 * 长错因就会被裁成"网络断了，这轮没生…"——**少掉的那半句是用户自救唯一的线索**。
 * 所以给两个**有名字的档**，不留一个 `containerColor:`/`shape:` 参数让每页自选：
 * 那种参数等于把"每页另造样式"合法化（同 `LbTopBarLevel`、同 `LbMetricDensity`）。
 */
enum class LbStateContainer {
    /** 整块：居中一叠、透明底——[LbAsyncState] 的 Empty/Error 两档用的就是这个 */
    Block,

    /** 就地一条：语气浅底的一横条，说明在前、动作在后；面板里那六处用的就是这个 */
    Strip,

    /**
     * 就地一条**轻通知**：与 [Strip] 同一族浅底容器，但整条收进画像 / 阶段建议卡那一套紧凑语言——
     * 文本 `labelMedium` 11/16、圆角仍 [LoveBrainShape.md] 10、内边距横 12 / 竖 4、
     * 尾部那颗关闭走**图标 14 坐在 24dp 见方盒**（[LbTextActionGlyph.Notice]），不是 48×48 的无底文字盒。
     *
     * 为什么是**第三档**而不是把 [Strip] 直接改小（基线 v1 §6.3、原话第 16 条）：
     * `Strip` 还管着面板里另外几处 empty/error 横条，把它的字号 / 关闭一起压小 = 全站那些条无差别变小，
     * 正是指导书 §7.5 明令"别把旧 Strip 测试一把放松"要挡的形状。所以另立一档、`Block`/`Strip` 一字不动。
     *
     * 复用视觉**不等于**复用确认行为：这一档没有确认键、不靠用户点了才消失，
     * 排队 / 通道 / 显示起算的过期时间全部由 `feature/notice/NoticeBoard` 那一格自己带着。
     */
    Notice
}

/**
 * 统一的空态/错误态/通知态版式：一段主说明 + 一个可选动作。
 *
 * 三条硬规定：
 * - **动作是一处操作，不是一行文字**：热区垫到见方、带 `Role.Button`，由 [LbTextAction]
 *   那唯一一处实现保证（两轴都垫到下限，不是只垫高度）。被引导去点的地方点不到，等于没有入口。
 * - 说明文字带 `contentDescription` 语义锚点，读屏/截图两边都能定位。
 * - 不允许"每页自己画一个居中 Text"。
 *
 * ## [LbStateContainer.Strip] / [LbStateContainer.Notice] 那两档定下的三件事
 *
 * **"一条带语气浅底的状态条"只有这一个主人**，页面不许再自画 `Box/Row + .background(…Bg)`：
 * 1. **字阶由这一处定，页面传不进来**：[Strip] 是 [AppTypography.bodyMedium]，
 *    [Notice]（轻通知档，原话第 16 条）与画像 / 阶段建议卡的正文**同用** [AppTypography.labelMedium]——
 *    这是"直接复用紧凑语言"落到的那一个现成字阶，不新增字号档（基线 v1 §3.2）。
 *    改字阶要回扫 `EmptyStateOwnershipSemanticsTest` 与 `NoticeStripSemanticsTest` 那两栏排布判据。
 * 2. **通知条不裁行**（没有 `maxLines = 1 + Ellipsis`）：裁掉半句等于丢话，
 *    而这一族的说明正是用户下一步该做什么的唯一依据。整句排开，条子按内容长高。
 * 3. **底色成对走词表，页面拿不到颜色**：留住某一条页面上的描边就得开 `borderColor` 旋钮，
 *    而归并换的是**所有者**，不是给组件加参数。
 *
 * 不变的那两样：说明文字逐字不变、动作仍是同一颗回调。
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

        // 轻通知档（原话第 16 条）：同一条浅底容器，但整条收进画像 / 阶段建议卡那一套紧凑语言。
        // 与 Strip 的三处差异逐条对上基线 v1 §6.3：① 字阶换成建议卡正文那颗 labelMedium；
        // ② 内边距横 12 / 竖 4（Strip 是 8 / 4）；③ 尾部那颗关闭从 48×48 的无底文字盒
        //   换成图标 14 坐在 24dp 见方盒（[StateAction] 的 compact 那一支）——正是这条把
        //   24dp 一横条顶成 56dp 的元凶。圆角仍是 LoveBrainShape.md（10），不改。
        //   `Block`/`Strip` 上面两支一字没动，全站那些 empty/error 条不会因此变小（§7.5）。
        LbStateContainer.Notice -> Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(LoveBrainShape.md)
                .background(tone.stripBackground, LoveBrainShape.md)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            StateMessage(
                message = message,
                tone = tone,
                style = AppTypography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            if (action != null) StateAction(action, compact = true)
        }
    }
}

/** 说明那一行。三档容器共用这一颗，所以"说法 + 锚点 + 字色"只在这里写一遍。 */
@Composable
private fun StateMessage(
    message: String,
    tone: LbStateTone,
    modifier: Modifier = Modifier,
    style: TextStyle = AppTypography.bodyMedium
) {
    Text(
        text = message,
        style = style,
        color = tone.ink,
        textAlign = TextAlign.Center,
        modifier = modifier
            .semantics { contentDescription = message }
            .testTag(LbAsyncTags.MESSAGE)
    )
}

/**
 * 动作那一颗——本文件里"文字/图标动作"唯一的出口。
 *
 * 文字动作转进设计系统里那唯一一处（[LbTextAction]），本文件不再自画 `Box + Text`。
 * 三件事由那一处保证、不在这里重复：热区垫到见方两轴、`Role.Button` 只声明在外层盒上、
 * 短标签也要够宽（只垫高度时"重试"会在宽度轴不达标）。
 * testTag 仍挂在**外层可点击盒**上：挪进里面的 Text 就成了"锚点找得到、
 * 按钮找不到"。
 *
 * [compact] 为真时（轻通知档 [LbStateContainer.Notice] 的关闭那颗，原话第 16 条）交回的是
 * [LbTextAction] 的**图标档**：一枚 `Icons.Filled.Close` 坐在 [LbTextActionGlyph.Notice] 的 24dp
 * 见方盒里，读屏名字仍来自 [ScreenAction.label]（那句 `a11y_close_notice` 资源）。失败语气用图标、
 * 不写 `✗` 字符——字面符号在资源/读屏两侧都是坑（基线 v1 §3.9）。
 *
 * ⚠ 本文件里 `LbTextAction(` 出现**两次**（文字档一次、[compact] 图标档一次，都在 `StateAction` 一个函数里）、
 * `.clickable(` 只许出现**零次**——这两个数是 `UiLayerDependencyContractTest > the design-system text
 * action has exactly one owner` 钉着的。"动作只有一处画法"靠的是 `clickable == 0` 那一条；
 * 两次 `LbTextAction(` 是同一颗设计系统组件的两支入口（文字 / 图标），不是两个所有者。
 */
@Composable
private fun StateAction(action: ScreenAction, compact: Boolean = false) {
    if (compact) {
        LbTextAction(
            icon = Icons.Filled.Close,
            description = action.label,
            onClick = action.run,
            glyph = LbTextActionGlyph.Notice,
            modifier = Modifier.testTag(LbAsyncTags.ACTION)
        )
    } else {
        LbTextAction(
            label = action.label,
            onClick = action.run,
            modifier = Modifier.testTag(LbAsyncTags.ACTION)
        )
    }
}

/**
 * 一个目的地的四态渲染器——那四类状态的唯一出口。
 *
 * 它**不判**该显示哪一格：状态由持有者算好传进来。
 * 理由见 [ScreenState] 的注释——判据一旦在两处各写一遍，两边就会慢慢长得不一样。
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
        is ScreenState.Content -> Box(modifier) { content(state.value) }
    }
}
