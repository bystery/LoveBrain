package com.lovebrain.app.ui.panel.settings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LbChip
import com.lovebrain.app.core.designsystem.LbChipInteraction
import com.lovebrain.app.core.designsystem.LbChipStyles
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbFieldInput
import com.lovebrain.app.core.designsystem.LbFormField
import com.lovebrain.app.core.designsystem.LbModalSheet
import com.lovebrain.app.core.designsystem.LbModalSheetActions
import com.lovebrain.app.core.designsystem.LbModalSheetTitle
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.model.IntentStatus
import com.lovebrain.app.ui.home.MiniSwitch

/**
 * 设置页里"持续意图"那一格的展开/收起动画时长——与面板页切换同档（[com.lovebrain.app.ui.panel.PanelPageMotion.SLIDE_MS]）。
 * §4.4「设置展开」那一条要的是 160–220ms 这一档，并且键盘弹出时不再对整页做高度动画：
 * 只有这一格自己让位，页面那一列一个字都不动。
 */
private const val INTENT_EXPAND_MS = 200

/**
 * 「已经确认过介绍」那一条记录的唯一所有者（§11.2）。
 *
 * 为什么必须落在盘上而不是 `remember` 里：这一格的可读语义是"已经确认过介绍后，再次启用
 * 无需重复介绍"，而 `remember` 跟着组合体一起没——收起面板、重启应用都会把它洗回"没见过"，
 * 于是每一轮都要用户点一次「知道了」。键名与库名沿用既有那一份（老用户已经确认过的不重弹）。
 */
internal object IntentIntroRecord {
    /** 与面板设置族同一份 SharedPreferences（宿主那一侧的透明度/引导记录不共用键，只共用文件） */
    const val PREFS_NAME = "lovebrain_settings"

    /** 介绍已经确认过的那一格 */
    const val INTRO_SEEN_KEY = "intent_intro_seen"

    fun seen(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(INTRO_SEEN_KEY, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(INTRO_SEEN_KEY, true)
            .apply()
    }
}

/**
 * 意图这一格在设置页上的六档状态（§11.3 那张表，逐行对应）。
 *
 * 判据不在这颗 enum 外面散着写：展开不展开、显示哪一句，都由 [settingsIntentStateOf]
 * 一次算出，所以"关闭却残留一大片禁用的空区域"（表里第一行）与"已到期当成关闭"
 * （表里第五行）这两种形状在代码里写不出来。
 */
internal enum class SettingsIntentState {
    /** 关闭：只保留开关行 */
    OFF,

    /** 首次待确认：介绍浮层在场，还没启用 */
    INTRO_PENDING,

    /** 开启且未输入：正常输入提示，不向请求注入空意图 */
    ON_WITHOUT_TEXT,

    /** 有效：展示正文与期限，允许修改 */
    ACTIVE,

    /** 已到期：不再注入请求，给轻量说明，可重新启用 */
    EXPIRED,

    /** 已完成：结束生效，保留必要的编辑便利，不继续注入 */
    COMPLETED
}

/**
 * 六档状态的那一次换算（纯函数，JVM 可直接证伪）。
 *
 * 三条次序是判据本身，不是顺手写的：
 * · **介绍浮层在场就是「首次待确认」**，先于开关本身——确认之前那一格还没启用（§11.2）；
 * · **终态（已到期／已完成）先于开关**：自动到期那条链把 `enabled` 一起写成了 false
 *   （`feature/intent/IntentController.kt` 的 `refreshForKb`），照开关读就退化成「关闭」，
 *   用户看不到自己刚才那条意图、也没有"重新启用"的落点；
 * · 其余才看开关与正文。
 */
internal fun settingsIntentStateOf(
    enabled: Boolean,
    status: IntentStatus,
    hasText: Boolean,
    introPending: Boolean
): SettingsIntentState = when {
    introPending -> SettingsIntentState.INTRO_PENDING
    status == IntentStatus.EXPIRED -> SettingsIntentState.EXPIRED
    status == IntentStatus.COMPLETED -> SettingsIntentState.COMPLETED
    !enabled -> SettingsIntentState.OFF
    !hasText -> SettingsIntentState.ON_WITHOUT_TEXT
    else -> SettingsIntentState.ACTIVE
}

/**
 * 这一次写盘的对象对不对（§11.3「不能把 A 的意图展示或写给 B」）。
 *
 * [draftOwnerKb] 是这一稿正文**打字或获焦那一次**所属的库（两条都记，切库那个窗口里才不会漏），
 * [currentKb] 是现在屏幕上这块库。
 * 两者都认得出且不相等就不写：切库之后屏幕已经换成 B 那份数据，把 A 的正文交出去
 * 就是写给错的人。任一侧还没接上（宿主没交库名、或这一稿根本没被编辑过）都放行——
 * 拦下没接线那一侧会让"从没切过库"的正常保存变成静默失败，那是另一种假象。
 */
internal fun intentWriteOwnedBy(draftOwnerKb: String?, currentKb: String?): Boolean =
    draftOwnerKb == null || currentKb == null || draftOwnerKb == currentKb

/**
 * 介绍浮层的可见性与「已经看过」这两颗态的持有者（§11.2 全三条都在这一颗里）。
 *
 * 与面板那一族的 `remember*Holder` 同一形状（本仓对"屏幕函数中间摊一堆局部可见性布尔"
 * 有账，见 `UiLayerDependencyContractTest`）：浮层的开合只有这一个所有者，
 * 页面与那一格各拿一句回调，谁都不再自己记"弹过没有"。
 */
internal class SettingsIntentIntroHolder(initialSeen: Boolean) {
    var seen: Boolean by mutableStateOf(initialSeen)
        private set
    var pending: Boolean by mutableStateOf(false)
        private set

    /** 首次拨开：只把介绍挂起来。启用、落盘、展开正文都还没发生（§11.2「确认后才正式启用」） */
    fun request() {
        if (!seen) pending = true
    }

    /** 确认：把「已看过」定下来并收起浮层；启用那一句由宿主在回调里落盘 */
    fun acknowledge() {
        seen = true
        pending = false
    }

    /** 取消／点遮罩／离开这一页：保持关闭，一个字都不写 */
    fun cancel() {
        pending = false
    }
}

/**
 * 介绍浮层持有者的唯一创建口。
 *
 * 盘上那份记录只在**第一次组合**那一次读一次（`remember(context)` 那一格），不每次重组都
 * 去碰一次 SharedPreferences：这一页是悬浮窗里那一扇，组合次数不止一次，
 * 把盘读挂在组合路径上等于给每次重画加一次同步 IO。
 */
@Composable
internal fun rememberSettingsIntentIntroHolder(): SettingsIntentIntroHolder {
    val context = LocalContext.current
    return remember(context) { SettingsIntentIntroHolder(IntentIntroRecord.seen(context)) }
}

/**
 * 设置页里那一格**持续意图**：一行「意图」加一颗滑动开关，确认后展开有效期／正文／操作。
 *
 * §11.3 的展开顺序照抄那一行：**有效期选择（1 小时、1 天、1 星期）→ 意图输入 →
 * 必要的保存／完成操作**。三个期限与「已经完成」**不是四颗等价档位**：完成是"结束当前
 * 这条意图"的动作，所以它住在下面那一行操作里，不挤进有效期那一排（旧形状四颗并排单选，
 * 读起来像第四个时长）。
 *
 * 期限从哪个事件起算（同一格另一条原话）：
 * · **确认启用／重新启用**与**换有效期档**才重算——两者都把 `recomputeExpiry` 说成真，
 *   由 [com.lovebrain.app.domain.IntentPolicy.saveDecision] 定状态与到期时刻；
 * · **保存正文／失焦落盘／拨到关**都不重算（`recomputeExpiry = false`），每按一键就把
 *   "一天"续到此刻+1 天的那种写法会让意图永远不过期；
 * · 那颗**完成**交的是 `IntentExpiry.COMPLETED`——它是"换成终态"那一次换档，
 *   所以 `recomputeExpiry = true`，而时间档的到期时刻在终态下本来就不带（判据在
 *   [com.lovebrain.app.domain.IntentPolicy.effectiveExpiryDate]）；
 * · 正文只在失焦那一次与点「保存」那一次交出去，不逐键落盘。
 *
 * **介绍浮层不在这一格里画**（§11.2 点名的挂载问题）：这一格坐在设置页那根滚动柱里，
 * 而滚动柱交给子节点的最大高度是无限的——那一层 `fillMaxSize()` 的遮罩在那种约束下
 * 只剩自己那一块，既不覆盖面板、又占掉一个表单槽位、还能被滚走。浮层因此由页面根部
 * 那一格 [SettingsIntentIntroSheet] 画（见 [LoveBrainSettingsContent] 那棵 Box），
 * 这一格只说"要介绍"（[onRequestIntro]）。
 *
 * 本格不持有配置：读数由宿主交（[intentEnabled]/[intentText]/[intentExpiry]/[intentStatus]/
 * [intentExpiryDate]），一条写口 [onIntentChange] 由宿主接到 `IntentController.save(...)`——
 * 开关／有效期／正文／完成四种编辑都走这同一颗，每次都把当前正文一起带过去，
 * 避免"打完字没失焦就拨开关、正文被旧值盖掉"的那条竞态。
 *
 * @param ownerKbName 屏幕上这一块库的名字；与 [intentText] 同源，用来认这一稿正文的主人
 * @param onRequestIntro 首次拨开那一句：只挂介绍，不启用
 * @param onInputIntent 触碰输入区时通知宿主进入编辑态（悬浮窗那条键盘通路）
 */
@Composable
internal fun SettingsIntentEntry(
    intentEnabled: Boolean,
    intentText: String,
    intentExpiry: IntentExpiry,
    onIntentChange: (String, Boolean, IntentExpiry, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    intentStatus: IntentStatus = IntentStatus.ACTIVE,
    intentExpiryDate: String = "",
    ownerKbName: String? = null,
    introSeen: Boolean = true,
    introPending: Boolean = false,
    onRequestIntro: () -> Unit = {},
    onInputIntent: (() -> Unit)? = null
) {
    // 正文本地态：从宿主那份读数同步（换库、换那一格都跟着重键），编辑期间不每按一键落盘。
    var localText by remember(intentText) { mutableStateOf(intentText) }
    var fieldHadFocus by remember { mutableStateOf(false) }
    // 这一稿正文的主人：获焦或第一次打字那一次记一下，写盘前与屏幕上那块库对一次账（见 [intentWriteOwnedBy]）。
    var draftOwnerKb by remember { mutableStateOf<String?>(null) }
    val fieldFocusRequester = remember { FocusRequester() }

    val state = settingsIntentStateOf(
        enabled = intentEnabled,
        status = intentStatus,
        hasText = intentText.isNotBlank(),
        introPending = introPending
    )
    // 展开条件：开着，或者已经落到终态但用户还需要看见自己那条意图（已到期／已完成都要有落点）。
    val showsBody = intentEnabled ||
        state == SettingsIntentState.EXPIRED ||
        state == SettingsIntentState.COMPLETED

    // 唯一那条写出去的路：四个编辑动作（开关／换档／保存／完成）都从这一颗走，
    // 先对一次库的主人再交出去。写成 lambda 而不是本函数里的局部 `fun`：
    // 这一族的状态是 `by remember` 那种委托局部量，赋值要落在闭包里（与本页别处同一写法）。
    val commit: (String, Boolean, IntentExpiry, Boolean) -> Unit = { text, enabled, expiry, recomputeExpiry ->
        if (!intentWriteOwnedBy(draftOwnerKb, ownerKbName)) {
            // 屏幕已经换成另一块库：这一稿不写给错的人。主人归零、草稿交回屏幕上那一份，
            // 于是下一次写的是新库那份数据——既没有"静默失灵"，也不留"看着是 A、写进 B"的中间态。
            draftOwnerKb = null
            localText = intentText
        } else {
            onIntentChange(text, enabled, expiry, recomputeExpiry)
        }
    }

    Column(
        modifier = modifier.settingsEntryCard().onFocusChanged { focusState ->
            val focused = focusState.hasFocus
            if (focused && !fieldHadFocus) draftOwnerKb = ownerKbName
            // 失焦那一次把正文交出去——切到本卡内别处（开关／chips）不算离开（hasFocus 仍 true）。
            if (fieldHadFocus && !focused && localText != intentText) {
                commit(localText.trim(), intentEnabled, intentExpiry, false)
            }
            fieldHadFocus = focused
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.intent_entry_title),
                style = AppTypography.labelMedium,
                color = TextSecondary
            )
            MiniSwitch(
                checked = intentEnabled,
                label = stringResource(R.string.intent_label),
                onCheckedChange = { on ->
                    if (on && !introSeen) {
                        // §11.2：首次拨开只把介绍挂起来，确认前不启用、不落盘、不展开
                        onRequestIntro()
                    } else {
                        // 已经确认过介绍：再次启用不再重复介绍，直接按既有契约落盘（重算与否由宿主那颗判据说）
                        commit(localText.trim(), on, intentExpiry, false)
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = showsBody,
            enter = fadeIn(tween(INTENT_EXPAND_MS, easing = FastOutSlowInEasing)) +
                expandVertically(tween(INTENT_EXPAND_MS, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(INTENT_EXPAND_MS, easing = FastOutSlowInEasing)) +
                shrinkVertically(tween(INTENT_EXPAND_MS, easing = FastOutSlowInEasing))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(Spacing.md))
                // 有效期的标签与期限读数同一行：左是档名，右是这条意图真正的到期时刻
                // （§11.3「有效：展示正文与期限」）。终态那一格读的是状态，不是某个第四档时长。
                val deadlineReadout = if (state == SettingsIntentState.COMPLETED) {
                    stringResource(R.string.intent_expiry_completed)
                } else {
                    intentExpiryDate
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.intent_expiry_label),
                        style = AppTypography.labelMedium,
                        color = TextSecondary
                    )
                    if (deadlineReadout.isNotBlank()) {
                        Text(
                            text = deadlineReadout,
                            style = AppTypography.labelSmall,
                            color = if (state == SettingsIntentState.EXPIRED) TextHint else TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                // 三颗期限，互斥单选；「已经完成」不在这一排里（它是下面那颗动作）。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    IntentExpiryOption(stringResource(R.string.intent_expiry_one_hour), intentExpiry == IntentExpiry.ONE_HOUR) {
                        commit(localText.trim(), intentEnabled, IntentExpiry.ONE_HOUR, true)
                    }
                    IntentExpiryOption(stringResource(R.string.intent_expiry_one_day), intentExpiry == IntentExpiry.ONE_DAY) {
                        commit(localText.trim(), intentEnabled, IntentExpiry.ONE_DAY, true)
                    }
                    IntentExpiryOption(stringResource(R.string.intent_expiry_one_week), intentExpiry == IntentExpiry.ONE_WEEK) {
                        commit(localText.trim(), intentEnabled, IntentExpiry.ONE_WEEK, true)
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                LbFormField(
                    label = stringResource(R.string.intent_content_label),
                    error = null
                ) {
                    LbFieldInput(
                        value = localText,
                        onValueChange = {
                            // 打字本身就是"这一稿属于屏幕上这块库"的信号，与获焦那一次同一个判据；
                            // 两条都记，切库那一个窗口里才不会漏（漏了就等于把 A 的正文写给 B）。
                            if (draftOwnerKb == null) draftOwnerKb = ownerKbName
                            localText = it
                        },
                        placeholder = stringResource(R.string.intent_content_placeholder),
                        focusRequester = fieldFocusRequester,
                        onInputIntent = onInputIntent
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                // 必要的保存／完成操作（§11.3 展开顺序的最后一段）。两颗的分工不一样：
                // 「保存」只交正文、不碰计时（`false`）；「完成」是把这条换成终态那一次换档（`true`），
                // 终态下面没有期限可言（`effectiveExpiryDate` 对 COMPLETED 一律给空串）。
                // 「保存」这一支的成功回执（「已记录」）**不在这里发**：这一格看不见落盘结果，
                // 回执由宿主那条 `IntentController.save` 在写盘真返回之后经统一通知通道发出
                // （request2 §四「意图保存反馈」；先说成功、后落盘是这一格禁止的形状）。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End)
                ) {
                    LbChip(
                        label = stringResource(R.string.intent_save),
                        selected = false,
                        onClick = { commit(localText.trim(), intentEnabled, intentExpiry, false) },
                        interaction = LbChipInteraction.Action
                    )
                    if (state != SettingsIntentState.COMPLETED) {
                        LbChip(
                            label = stringResource(R.string.intent_expiry_completed),
                            selected = false,
                            onClick = { commit(localText.trim(), intentEnabled, IntentExpiry.COMPLETED, true) },
                            interaction = LbChipInteraction.Action
                        )
                    }
                }
            }
        }
    }
}

/**
 * 意图介绍浮层：**由页面根部那一格画**（[LoveBrainSettingsContent] 那棵 Box 的最后一层），
 * 不由 [SettingsIntentEntry] 画——理由写在那一格的 KDoc 里（滚动柱里的无限高度约束会让
 * 遮罩只剩自己那一块，还占掉一个表单槽位）。
 *
 * 壳仍是设计系统那一颗 [LbModalSheet]（§11.2「不能再写一份自定义弹窗」），
 * 可见性走它的 `visible` 参数、树常驻，入退场那 200ms 由壳自己持有（§4.4「先修挂载层级，
 * 不重复包动画」：这一层外面**没有**第二份 AnimatedVisibility）。
 *
 * ⚠ 这一颗是**委托壳**，不是又一处自画形状（异形账本里它是壳、不是异形）：体里只有
 * [LbModalSheet] + [LbModalSheetTitle] + [LbModalSheetActions] 三句设计系统调用，
 * **没有第二棵容器**。这里以前裹着一层 `Column(Modifier.fillMaxWidth())`——那是白加的一层：
 * [LbModalSheet] 卡片自己就是一棵竖列（`LbModalSheet.kt` 里 `Column(align=Center, fillMaxWidth(0.92f) …)`），
 * 内容再套一列既不改像素（宽度约束由同一根列给出），也不提供任何本页特有的形状，
 * 只是让这一格从"只把内容交给公共件"退化回"壳里自己排版面"。同一分工的先例：
 * 纠正中心与 [com.lovebrain.app.ui.panel.reply.MemoryCorrectionFlowHost] 都是把
 * 标题／正文／动作直接排在壳里；需要裹一层的只有编辑器那种大块内容
 * （`IntentEditorDialog` 壳 + `IntentEditorBody`，见 [com.lovebrain.app.ui.panel.IntentEditorDialog]）。
 *
 * 内容三件：标题 + 一段说明正文 + 一颗确认动作，加上壳给的那条既有出口（点遮罩即关，
 * 关就是"保持关闭"）。正文的唯一主人是 `R.string.intent_intro_body`：这一页不写第二份文案，
 * 也不把功能宣讲拉长。
 */
@Composable
internal fun SettingsIntentIntroSheet(
    visible: Boolean,
    onAcknowledge: () -> Unit,
    onDismiss: () -> Unit
) {
    LbModalSheet(onDismissRequest = onDismiss, visible = visible) {
        LbModalSheetTitle(stringResource(R.string.intent_label))
        Spacer(Modifier.height(Spacing.sm))
        Text(
            stringResource(R.string.intent_intro_body),
            style = AppTypography.bodyMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(Spacing.md))
        LbModalSheetActions(
            listOf(
                LbDialogAction(stringResource(R.string.intent_intro_acknowledge), onAcknowledge)
            )
        )
    }
}

/**
 * 有效期 chip——与意图编辑浮层里 `IntentExpiryChip` 同一族（[LbChip] 的 Single 档），
 * 选中那一颗实心、未选那一颗字色更浅。两处共用同一份形状，不各画一份。
 *
 * ⚠ 选中那一颗在语义树里的**名字带一个对勾前缀**（`LbChipStyles.filled` 的
 * `markSelectedWithCheck` 默认开着，勾由 [LbChip] 自己加在 label 前面）：屏幕上三颗期限
 * 都在位，而"当前是哪一档"那一颗读出来是「✓ 一天」。页面上任何一格要数这三颗，
 * 就按芯片那颗节点（`LbTags.CHIP`）+ 名字子串去认，别按整串字面量等值去找——
 * 那种写法偏偏在选中那一档上读成 0（量具瞎，不是这里没画；前例同
 * `ui/home/ProviderFormSemanticsTest.kt` 的超时四档那一格）。
 */
@Composable
private fun IntentExpiryOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    LbChip(
        label = label,
        selected = isSelected,
        onClick = onClick,
        interaction = LbChipInteraction.Single,
        style = LbChipStyles.filled.copy(
            textColor = TextHint,
            paddingHorizontal = Spacing.sm,
            paddingVertical = Spacing.xs
        )
    )
}
