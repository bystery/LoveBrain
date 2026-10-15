package com.lovebrain.app.ui.panel.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.core.designsystem.LbRowLabel
import com.lovebrain.app.core.designsystem.LbChip
import com.lovebrain.app.core.designsystem.LbChipInteraction
import com.lovebrain.app.core.designsystem.LbChipStyles
import com.lovebrain.app.core.designsystem.LbFieldInput
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.ui.home.MiniSwitch
import com.lovebrain.app.ui.panel.INTENT_MAX_LENGTH
import kotlinx.coroutines.delay

/**
 * 行根锚点（整页量具的锚点合同）：量具按这一颗找到"持续意图"那一行。
 * 只挂在**开关那一行**的根 [Row] 上（与 [Modifier.settingsEntryRow] 同一颗）；
 * 输入那一行是它的兄弟段，不另挂同名 tag——一格里两颗同 tag 会让"这行恰有一颗"那一句没有牙。
 */
internal const val SETTINGS_INTENT_ROW_TAG = "settings-row-intent"

/** 「保存」那颗的锚点：颜色会变档（实心），名字会变（读屏），tag 不变 */
internal const val SETTINGS_INTENT_SAVE_TAG = "settings-save-button"

/**
 * 「✓ 已保存」那一档在屏上停留多久（毫秒）：原型 `.save.success` 那一拍自定的 1300ms，
 * 到点自己收回实心档。它**不是**"等写盘结果"的超时——那一拍由宿主交下来的 [saving]
 * 与 [saveReceipts] 两个真读数决定，这一档只管亮多久。
 */
private const val INTENT_SAVE_FLASH_MS = 1_300L

/**
 * 这一次写盘的对象对不对（"不能把 A 的意图展示或写给 B"）。
 *
 * [draftOwnerKb] 是这一稿正文**第一次被编辑那一次**所属的库，
 * [currentKb] 是现在屏幕上这块库。
 * 两者都认得出且不相等就不写：切库之后屏幕已经换成 B 那份数据，把 A 的正文交出去
 * 就是写给错的人。任一侧还没接上（宿主没交库名、或这一稿根本没被编辑过）都放行——
 * 拦下没接线那一侧会让"从没切过库"的正常保存变成静默失败，那是另一种假象。
 */
internal fun intentWriteOwnedBy(draftOwnerKb: String?, currentKb: String?): Boolean =
    draftOwnerKb == null || currentKb == null || draftOwnerKb == currentKb

/**
 * 设置页里那一格**持续意图**：用户写给军师的一句长期目标，开着的时候每轮生成都参考它。
 *
 * 屏上严格只有两态，没有第三种形状：
 * · **关**：只剩「持续意图 [开关]」那一行。输入与保存**整段不在树上**（不是禁用的空表单），
 *   盘上那条文本原样留着、不注入。
 * · **开**：「持续意图 [开关]」那一行下面**紧跟**另一段（同一张组外框之内、两段之间**不画分隔线**：
 *   组内那条线归页面用在**相邻两格**之间，而开关行与输入行是同一格的两段），
 *   里面是同一行的 [LbFieldInput]（吃掉剩余宽度）与一颗「保存」。
 *   两行各自取 [Modifier.settingsEntryRow]（同一族行的版式下限），输入与保存垂直居中。
 *   那颗按钮走 [LbChipStyles.actionSolid]（实心强调色 + 白字，与原型 `.save` 同脸）：
 *   白底描边那张是**可选项**的脸，挂在动作按钮上会和同一行那颗输入框读成同一类东西，
 *   于是"保存"在屏上找不到。可见高与那颗输入框同数（[com.lovebrain.app.core.designsystem.AppDimens.INPUT_ROW_HEIGHT_DP]），
 *   热区由芯片自己那圈分层外盒垫到全站下限，所以不需要、也**不许**在按钮外面再套一层空盒；
 *   两者之间只留一份 [Spacing.sm]。
 *   正文再长也不许把「保存」挤走：输入框走单行、靠光标水平移动编辑，宽度由 `weight(1f)` 那一侧
 *   自己吃掉，芯片按内容排宽。三档悬浮窗宽度（260 / 300 / 350dp）都看得见完整的"保存"两个字，
 *   判据与实测位置写在 `SettingsIntentRowTest`（同一格里挂一份自然宽参照，比的是一颗芯片排开
 *   那两个字到底要多宽）。
 *
 * 这一行**没有**输入下方的那一句灰字说明：原型 `.intent-edit` 那一段里只有输入与保存两颗，
 * 而"开着时每轮生成都会参考这句"这件事已经由那颗输入框的 placeholder 说出口（空草稿时它就念得出），
 * 多写一行就是原型上没有的那张脸（10-11 那一版"语义对了但脸不对"的一条成因）。
 *
 * 只有两处提交：**拨开关**与**按保存**。
 * 输入只改这一格自己的草稿，失焦不写盘（以前那一路 `.onFocusChanged` 会在失焦时再交一次，
 * 与那颗「保存」并存就是同一稿写两遍，还把保存按钮变成摆设）；正文也不逐键落盘。
 * 草稿的唯一主人是 [localText]：宿主那份读数换掉（写盘成功回来、或切到另一块库）时跟着同步，
 * 写失败时宿主那份没动，草稿因此原样留着可以重试。
 *
 * 「保存」正在飞的时候**按不动**，但不是从屏上消失：[LbChip] 的 `enabled = false` 仍把那颗
 * 节点留在原位、仍念得出"保存"，只多报一个 Disabled（读屏知道它当下不可用）。
 * 实心档不给"禁用"另画一张脸（不降透明度、不换底色），所以那一拍靠的是 Disabled 那一槽与
 * 按下去没反应这两件事，不是靠颜色——颜色在这里从来不是这一格的规范位（与 [LbChip] 文件头第 1 条同一口径）。
 * 按不动与亮绿这两件事读的都是**写侧那两个真读数**（[saving] / [saveReceipts]），
 * 这一格自己不留"哪一笔还在飞"的第二本账：那种账要有人负责翻回来，翻不回来就留下一颗永远灰着的按钮。
 *
 * 这一格不持有配置：读数由宿主交（[intentEnabled] / [intentText]），唯一的写口是 [onIntentChange]——
 * 开关与保存都只走这一颗，每次都把当前正文一起带过去，免得"打完字没落盘就先拨开关、正文被旧值盖掉"
 * 那条竞态。那一句「已保存」与那一句「保存失败，请重试」仍由宿主那条写盘链在真正落定之后经
 * **统一通知通道**发出，这一格不替宿主说话；它只把"刚刚又成功了一笔"这一件事画在自己那颗按钮上
 * （[saveReceipts] 那张票根**相对本入口上屏时的基线**跳一号 ⇒ 亮 [LbChipStyles.actionSuccess]
 * 那一档，文案复用同一条资源 `intent_saved_notice`，到 [INTENT_SAVE_FLASH_MS] 自己收回。
 * 基线那句是 F05B：回执属于这一次点击，不属于历史——别处保存过再进这一页，首帧不许白亮。
 * 失败那一路票根不跳，所以屏上永远不会先说成功——这一格禁止的形状。
 *
 * 期限与状态在这一格已经没有位置（开关就是启用标志，开着且正文非空才生效），所以写口只把
 * **正文与开没开**这两件事交出去：这一格不选档、不改档，也就不会替用户把到期时刻续一遍。
 *
 * 组壳与横向内缩归页面（整组一张卡 + 组外框那 [Spacing.lg]），所以这里**不**再调
 * `settingsEntryCard()`、也不自带横向 padding：这一格只排行，每行自己取 [Modifier.settingsEntryRow] 那一档版式下限。
 *
 * @param ownerKbName 屏幕上这一块库的名字；与 [intentText] 同源，用来认这一稿正文的主人
 * @param saving 那一笔写盘还在飞吗——**写侧那唯一的真读数**（`IntentController.isSaving`），
 *   由宿主交下来；这一格不自己推演"我刚才按了一下所以应该在飞"
 * @param saveReceipts 「已保存」成功回执的纪元票根（`IntentController.savedReceipts`）：
 *   每真正落盘成功一次跳一号。这一格只认**相对自己上屏基线的增量**（F05B），
 *   历史值多大都不点亮首帧
 * @param onInputIntent 触碰输入区时通知宿主进入编辑态（悬浮窗那条键盘通路）
 */
@Composable
internal fun SettingsIntentEntry(
    intentEnabled: Boolean,
    intentText: String,
    onIntentChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    ownerKbName: String? = null,
    saving: Boolean = false,
    saveReceipts: Int = 0,
    onInputIntent: (() -> Unit)? = null
) {
    // 草稿：宿主那份读数一变（写盘回来、切库）就重键同步一次；编辑期间不逐键落盘。
    var localText by remember(intentText) { mutableStateOf(intentText) }
    // 这一稿正文的主人：第一次打字那一次记一下，写盘前与屏幕上那块库对一次账（见 intentWriteOwnedBy）。
    var draftOwnerKb by remember { mutableStateOf<String?>(null) }
    // 「✓ 已保存」那一拍：由宿主交下来的成功票根点亮，到点自己收回实心档。
    var justSaved by remember { mutableStateOf(false) }

    val draft = localText.trim()
    // 长度那一把尺与浮层入口共用同一颗数（F05A，2026-10-15 书 §6）。超限的形状也同口径：
    // **字不截断**（继续可编辑）、只多挂一行极短的话（不常驻、不第三行说明卡）、
    // 保存与开关都交不动（灰着还在，同浮层那一颗）。
    val overLimit = draft.length > INTENT_MAX_LENGTH

    // 票根的基线在这颗入口上屏那一刻记下（F05B，书 §6）：「已保存」只许**相对基线跳号**才亮——
    // 它属于这一次点击，不属于"这台机器历史上成功保存过"。旧写法只判 `!= 0`，
    // 在别处（浮层）保存过再进设置页，首帧就白亮 1.3 秒。基线走普通 remember：
    // 键盘弹出这类重组不重置它；整段离开树再回来＝重新进页，基线重记正是该有的行为。
    var receiptsBaseline by remember { mutableIntStateOf(saveReceipts) }

    // 成功回执：票根相对基线每跳一号 = 这一次正文真的落盘成功了一次 ⇒ 亮那一档。
    LaunchedEffect(saveReceipts) {
        if (saveReceipts <= receiptsBaseline) return@LaunchedEffect
        receiptsBaseline = saveReceipts
        justSaved = true
        delay(INTENT_SAVE_FLASH_MS)
        justSaved = false
    }

    // 唯一那条写出去的路：开关与保存都从这一颗走，先对一次库的主人再交出去。
    // 写成 lambda 而不是本函数里的局部 `fun`：这一族的状态是 `by remember` 那种委托局部量，
    // 赋值要落在闭包里（与本页别处同一写法）。
    val commit: (String, Boolean) -> Unit = { text, enabled ->
        if (overLimit) {
            // 超限这一稿哪儿都不去：开关与保存同一条路同一条闸（屏上那句超限话就挂在下面，
            // 这不是静默失灵——看得见为什么按不动）。
        } else if (!intentWriteOwnedBy(draftOwnerKb, ownerKbName)) {
            // 屏幕已经换成另一块库：这一稿不写给错的人。主人归零、草稿交回屏幕上那一份，
            // 于是下一次写的是新库那份数据——既没有"静默失灵"，也不留"看着是 A、写进 B"的中间态。
            draftOwnerKb = null
            localText = intentText
        } else if (!saving) {
            // 只有"正文 + 开没开"这两件事交出去：期限、状态那一族读数已经没有消费者，
            // 落盘形状由写侧统一（新值一律 ACTIVE、到期时刻留空）。
            onIntentChange(text, enabled)
        }
        // `saving` 为真的那一拍什么都不交：按钮此刻也是按不动的（见下面那颗 `enabled = !saving`）。
        // 这一格**不再自己留一本"哪一笔还在飞"的账**——那一本要有人负责翻回来，
        // 而真值本来就在写侧那一颗 `isSaving` 上，读它就行。
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            // 行根锚点与版式下限挂在同一颗节点上（锚点合同：量具只认行根那一颗）
            modifier = Modifier.testTag(SETTINGS_INTENT_ROW_TAG).settingsEntryRow()
        ) {
            // 整页唯一一种行标签（labelLarge + TextPrimary，原型 `.lbl`）：这一格不再借灰字那一档
            LbRowLabel(text = stringResource(R.string.intent_label))
            MiniSwitch(
                checked = intentEnabled,
                label = stringResource(R.string.intent_label),
                onCheckedChange = { on -> commit(draft, on) }
            )
        }

        // 关着的时候这一整段**不组合**：输入与保存不在树上，屏上也就没有"已到期／已完成／
        // 有效期／一小时·一天·一个星期"那一族读数的落脚处。
        // 它是开关行的**兄弟段**（同一张组外框之内、两段之间不画分隔线），不是页面那一层的另一行，
        // 也不是塞进开关行里的第三颗控件——原型 `<div id="intent-edit">` 就长在 `.item` 之后。
        if (intentEnabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.settingsEntryRow()
            ) {
                LbFieldInput(
                    value = localText,
                    onValueChange = {
                        // 打字本身就是"这一稿属于屏幕上这块库"的信号；没对过账就写等于写错人。
                        if (draftOwnerKb == null) draftOwnerKb = ownerKbName
                        localText = it
                    },
                    placeholder = stringResource(R.string.intent_row_placeholder),
                    onInputIntent = onInputIntent,
                    // 字阶吃原型那一档（`.intent-edit input{font-size:11px}`，比行标签 `.lbl` 的 12px 还小一档）：
                    // 公共件默认那档是 13sp 正文，挂在这一页就会"输入的字比行标签还大"，主次倒过来。
                    // 走的是那颗件新加的字阶参数，默认值不变 ⇒ 别的页面一个字没动。
                    textStyle = AppTypography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                // 实心档 + 白字（原型 `.save`）；按钮外面不套空盒：热区由这颗芯片自己的分层外盒垫出来。
                // 成功那一拍换 [LbChipStyles.actionSuccess]（状态色底 + 深绿字 + 文案前一枚对勾），
                // 文案也换成同一句「已保存」——用的就是宿主那句回执的同一条资源，不在这格另写第二份话。
                // 锚点挂在外面这颗**紧贴的盒**上，不挂在芯片自己身上：`LbChip` 内部那条链的末端
                // 会写 `LbTags.CHIP`（全族芯片共用的计数把手，`SettingsPageSemanticsTest` 按它数
                // "整页芯片 = 两排分段 + 这一颗保存"），而调用方传进来的 `testTag` 排在链首、
                // 会把那颗盖掉——两颗 tag 在同一颗节点上只能活一颗。
                // 外面这一圈不给宽度、不给内缩、不给对齐：它量出来就是芯片那一颗的尺寸。
                Box(modifier = Modifier.testTag(SETTINGS_INTENT_SAVE_TAG)) {
                    LbChip(
                        label = stringResource(
                            if (justSaved) R.string.intent_saved_notice else R.string.intent_save
                        ),
                        selected = justSaved,
                        onClick = { commit(draft, true) },
                        interaction = LbChipInteraction.Action,
                        style = if (justSaved) LbChipStyles.actionSuccess else LbChipStyles.actionSolid,
                        // 超限与在飞同闸（F05A）：灰着还在，不是消失——与浮层那颗同口径
                        enabled = !saving && !overLimit
                    )
                }
            }
            // 超限时多挂这一行极短的话（只在这一刻存在：不常驻、不是第三行说明卡）。
            // 文案与浮层共用同一条资源，两处念的是同一句。
            if (overLimit) {
                Text(
                    text = stringResource(R.string.intent_too_long_error, INTENT_MAX_LENGTH),
                    style = AppTypography.labelMedium,
                    color = Error
                )
            }
        }
    }
}
