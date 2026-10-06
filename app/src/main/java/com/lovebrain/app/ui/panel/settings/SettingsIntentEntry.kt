package com.lovebrain.app.ui.panel.settings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Border
import com.lovebrain.app.core.designsystem.LbChip
import com.lovebrain.app.core.designsystem.LbChipInteraction
import com.lovebrain.app.core.designsystem.LbChipStyles
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbFieldInput
import com.lovebrain.app.core.designsystem.LbFormField
import com.lovebrain.app.core.designsystem.LbModalSheet
import com.lovebrain.app.core.designsystem.LbModalSheetActions
import com.lovebrain.app.core.designsystem.LbModalSheetTitle
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.model.IntentExpiry
import com.lovebrain.app.ui.home.MiniSwitch

/**
 * 设置页里"持续意图"那一格的展开/收起动画时长——与面板页切换同档（[com.lovebrain.app.ui.panel.PanelPageMotion.SLIDE_MS]）。
 */
private const val INTENT_EXPAND_MS = 200

/**
 * 设置页里那一格**持续意图**入口：开关 + 展开的有效期/正文录入。
 *
 * 这是从面板输入行搬进设置页的入口（原话第 10 条要收掉屏上那两排次级控件）。
 * 默认关；开着的时候展开下面那一组有效期 chips + 正文输入框。关掉就收。
 *
 * · **开关**复用设计系统里同一颗 [MiniSwitch]（与意图编辑浮层那颗同款），不再自己画
 *   一颗没有 role / 欠 48dp 热区的 44×24 胶囊。
 * · **首次打开**弹一扇 [LbModalSheet] 介绍这个功能（复用浮层那一份开合动画与格式），
 *   确认后写进 SharedPreferences，之后不再弹；已开着的意图切回设置页直接展开，不弹。
 * · **有效期**四档互斥单选 chips：一小时 / 一天 / 一个星期 / 已完成，归 [LbChip] 的
 *   Single 那一档（与意图编辑浮层里同一族）。
 * · **正文**用 [LbFormField] + [LbFieldInput]，不再自己画 Box+BasicTextField。正文
 *   在输入框失焦那一次落盘——每按一键落一次盘会把到期时刻反复重置（见 [IntentExpiry]
 *   时间档的"保存时刻算"），所以这里只在失焦/换档/拨开关时才交出去。
 *
 * 本格不持有配置：读数由宿主交（[intentEnabled]/[intentText]/[intentExpiry]），
 * 一条写口 [onIntentChange] 由宿主接到 `IntentController.save(...)`——开关 / 有效期 / 正文
 * 三种编辑都走这同一颗，每次都把**当前正文**一起带过去，避免"打完字没失焦就拨开关、
 * 正文被旧值盖掉"的那条竞态。
 *
 * @param recomputeExpiry `true` = 换了有效期档，宿主传空串让
 *   [com.lovebrain.app.domain.IntentPolicy.effectiveExpiryDate] 按新档重算到期时刻；
 *   `false` = 没换档（开关 / 正文），宿主传**已有 expiryDate**，不把计时重置。
 */
@Composable
internal fun SettingsIntentEntry(
    intentEnabled: Boolean,
    intentText: String,
    intentExpiry: IntentExpiry,
    onIntentChange: (String, Boolean, IntentExpiry, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val prefs = remember {
        ctx.getSharedPreferences("lovebrain_settings", Context.MODE_PRIVATE)
    }
    var introSeen by remember {
        mutableStateOf(prefs.getBoolean(INTRO_PREF_KEY, false))
    }
    var showIntro by remember { mutableStateOf(false) }

    // 正文本地态：从宿主那份读数同步，编辑期间不每按一键落盘——失焦那一次才交。
    var localText by remember(intentText) { mutableStateOf(intentText) }
    var fieldHadFocus by remember { mutableStateOf(false) }
    val fieldFocusRequester = remember { FocusRequester() }

    // 卡容器与 [SettingsOpacityEntry] 同一档（基线 v1 §6.1：SurfaceCard 行 / Border 1dp / Lg 圆角 / 内 12）。
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.lg)
            .background(SurfaceCard, LoveBrainShape.lg)
            .border(AppDimens.BORDER_WIDTH_DP.dp, Border, LoveBrainShape.lg)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .onFocusChanged { state ->
                // 失焦那一次把正文交出去——这一格只在子树焦点真的离开时落盘，
                // 切到本卡内别处（开关/chips）不算离开（hasFocus 仍 true）。
                if (fieldHadFocus && !state.hasFocus && localText != intentText) {
                    onIntentChange(localText.trim(), intentEnabled, intentExpiry, false)
                }
                fieldHadFocus = state.hasFocus
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
                    if (on && !introSeen) showIntro = true
                    // 拨开关时把当前正文一起带过去——避免"打完字没失焦就拨开关、正文被旧值盖掉"
                    onIntentChange(localText.trim(), on, intentExpiry, false)
                }
            )
        }

        // 展开：开关开着就画有效期 + 正文。首次打开时介绍浮层也同时起，介绍只是叠加层，
        // 不挡这一格已经在的录入区（已开着的意图切回设置页不会再弹介绍）。
        AnimatedVisibility(
            visible = intentEnabled,
            enter = fadeIn(tween(INTENT_EXPAND_MS, easing = FastOutSlowInEasing)) +
                expandVertically(tween(INTENT_EXPAND_MS, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(INTENT_EXPAND_MS, easing = FastOutSlowInEasing)) +
                shrinkVertically(tween(INTENT_EXPAND_MS, easing = FastOutSlowInEasing))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.intent_expiry_label),
                    style = AppTypography.labelMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    IntentExpiryOption(stringResource(R.string.intent_expiry_one_hour), intentExpiry == IntentExpiry.ONE_HOUR) {
                        onIntentChange(localText.trim(), intentEnabled, IntentExpiry.ONE_HOUR, true)
                    }
                    IntentExpiryOption(stringResource(R.string.intent_expiry_one_day), intentExpiry == IntentExpiry.ONE_DAY) {
                        onIntentChange(localText.trim(), intentEnabled, IntentExpiry.ONE_DAY, true)
                    }
                    IntentExpiryOption(stringResource(R.string.intent_expiry_one_week), intentExpiry == IntentExpiry.ONE_WEEK) {
                        onIntentChange(localText.trim(), intentEnabled, IntentExpiry.ONE_WEEK, true)
                    }
                    IntentExpiryOption(stringResource(R.string.intent_expiry_completed), intentExpiry == IntentExpiry.COMPLETED) {
                        onIntentChange(localText.trim(), intentEnabled, IntentExpiry.COMPLETED, true)
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                LbFormField(
                    label = stringResource(R.string.intent_content_label),
                    error = null
                ) {
                    LbFieldInput(
                        value = localText,
                        onValueChange = { localText = it },
                        placeholder = stringResource(R.string.intent_content_placeholder),
                        focusRequester = fieldFocusRequester
                    )
                }
            }
        }
    }

    // 首次打开的介绍浮层——复用 [LbModalSheet] 的开合动画与版式（标题 + 正文 + 动作行）。
    if (showIntro) {
        LbModalSheet(
            onDismissRequest = {
                // 取消介绍 = 把开关拨回去，与"还没确认就不算开"一致
                showIntro = false
                onIntentChange(localText.trim(), false, intentExpiry, false)
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                        LbDialogAction(stringResource(R.string.intent_intro_acknowledge), {
                            introSeen = true
                            prefs.edit().putBoolean(INTRO_PREF_KEY, true).apply()
                            showIntro = false
                        })
                    )
                )
            }
        }
    }
}

/**
 * 有效期 chip——与意图编辑浮层里 [IntentExpiryChip] 同一族（[LbChip] 的 Single 档），
 * 选中那一颗实心、未选那一颗字色更浅。两处共用同一份形状，不各画一份。
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

private const val INTRO_PREF_KEY = "intent_intro_seen"
