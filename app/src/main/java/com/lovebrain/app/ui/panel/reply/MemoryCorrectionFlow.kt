package com.lovebrain.app.ui.panel.reply

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LB_SHEET_ACTION_MIN_DP
import com.lovebrain.app.core.designsystem.LbDialogAction
import com.lovebrain.app.core.designsystem.LbDialogActionTone
import com.lovebrain.app.core.designsystem.LbModalSheet
import com.lovebrain.app.core.designsystem.LbModalSheetActions
import com.lovebrain.app.core.designsystem.LbModalSheetTitle
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.PrimaryDark
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.model.MuteDuration

/**
 * 第6节第4条：「本轮参考记忆」那条纠正流程的**状态持有者**。
 *
 * 原话是"ResultArea 只负责结果内容，不再同时承载…浮层；这些拆成
 * 独立 state holder + modal host"。之前这两颗浮层的状态
 * （`showMuteSubmenu` / `showWrongDialog` / `wrongText`）是 `MemoryRefItem` 这一行
 * 自己 `remember` 的，浮层也就画在这一行的肚子里——`LbModalSheet` 的 `fillMaxSize()`
 * 于是只铺满**那一行**。改之前在本机量到（同一台仪器、360x400dp 挂载槽）：
 * 标题落在 y = **139–161dp**，而整槽垂直居中的话应当在 **≈190dp** 附近；
 * 也就是说"遮罩"盖不住面板其余部分，行以外那片还露在外面。
 *
 * 现在状态与渲染都从行里搬出来：行只发出"我要给这条选时长"/"这条要标错"的意图，
 * 谁持有 flow 谁渲染 [MemoryCorrectionFlowHost]。悬浮层因此可以画在面板顶层，
 * 而**不要求调用方必须提供宿主**——不传时 `ResultArea` 自己创建一个 flow 并就地渲染，
 * 少一处接线也不会变成"点了没反应"（那正是这种拆分最容易引入的新缺陷）。
 */
class MemoryCorrectionFlow {
    var muteTargetId: String? by mutableStateOf(null)
        private set
    var wrongTargetId: String? by mutableStateOf(null)
        private set
    var wrongDraft: String by mutableStateOf("")
        private set

    /**
     * **最后一次**请求到的 memoryId，不随 `dismiss()` 清空——只给渲染那一格当余温用。
     *
     * 为什么要有这一份：退场那 200ms 里 `muteTargetId` 已经是 null 了，可卡片还在树上播淡出，
     * 内容得继续画**最后那一条**。宿主若仍按 `muteTargetId?.let { LbModalSheet(...) }` 挂载，
     * 就是"请求一清、整棵树跟着没"，`LbModalSheet` 的 exit 动画永远播不到
     *（形状记在 `LbModalSheet` 的 `visible` 参数 KDoc）。现在 `visible` 由 `muteTargetId`
     * 说话、内容用这一份余温画完，两件事才分得开。
     *
     * ⚠ 它**不是**业务状态：判"现在有没有请求"仍然只看 [muteTargetId] / [wrongTargetId]，
     * 这一份只是画面余温，`dismiss()` 也不清它（清了就等于把退场那一段内容一起掏空）。
     */
    var lastMuteTargetId: String? by mutableStateOf(null)
        private set
    var lastWrongTargetId: String? by mutableStateOf(null)
        private set

    /**
     * 同一颗余温，给**草稿框**那一路：`dismiss()` 会把 [wrongDraft] 清成空串，
     * 而退场那 200ms 卡片还在淡出——框里那行字要是跟着清空，用户看到的就是
     * "自己打的话在一扇正在关的浮层里当场蒸发"。渲染那一格取这一份，[wrongDraft] 的
     * 业务语义（"dismiss 之后草稿该是空的"）一个字不动。
     *
     * 与 [lastWrongTargetId] 同一条规矩：只在**下一次请求**（[requestWrong]）时清，
     * `dismiss()` 不清。
     */
    var lastWrongDraft: String by mutableStateOf("")
        private set

    /** 一次只允许一颗：第二颗请求进来时，前一颗直接被顶掉（不是叠两层遮罩） */
    fun requestMute(memoryId: String) {
        wrongTargetId = null
        muteTargetId = memoryId
        lastMuteTargetId = memoryId
    }

    fun requestWrong(memoryId: String) {
        muteTargetId = null
        wrongTargetId = memoryId
        lastWrongTargetId = memoryId
        wrongDraft = ""
        // 新一次请求：余温要一起归零，否则上一轮的草稿会预填进这一轮那张卡
        lastWrongDraft = ""
    }

    fun editWrongDraft(value: String) {
        wrongDraft = value
        lastWrongDraft = value
    }

    fun dismiss() {
        muteTargetId = null
        wrongTargetId = null
        wrongDraft = ""
    }
}

@Composable
fun rememberMemoryCorrectionFlow(): MemoryCorrectionFlow = remember { MemoryCorrectionFlow() }

/**
 * 那两颗浮层的**唯一渲染处**。
 *
 * 放在这一层而不是行里，是为了让"遮罩盖满它所在的容器"这句话成立：
 * 传进来越靠近面板顶层，覆盖就越完整。
 *
 * 两扇都**常驻在树里**，开合只经 `visible` 说话（旧写法是 `muteTargetId?.let { LbModalSheet(...) }`
 * / `wrongTargetId?.let { ... }`：请求一清就整棵树被摘掉，[LbModalSheet] 写的退场动画永远播不到，
 * 形状记在它 `visible` 参数的 KDoc）。卡片里那条 memoryId 取 holder 的
 * [MemoryCorrectionFlow.lastMuteTargetId] / [MemoryCorrectionFlow.lastWrongTargetId] 那一份余温，
 * 草稿框取 [MemoryCorrectionFlow.lastWrongDraft] 那一份——退场那 200ms 才画得完整、
 * 也仍然交得对那一条、那一句话。
 */
@Composable
fun MemoryCorrectionFlowHost(
    flow: MemoryCorrectionFlow,
    onMute: (String, MuteDuration) -> Unit,
    onWrong: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 可见性只看"现在有没有请求"；卡片内容用最后一次那一条（两者都 null 时这一棵从没被画过，
    // 空串取不到——它落不进组合，只是让类型说得通）。
    val muteMemoryId: String = flow.muteTargetId ?: flow.lastMuteTargetId ?: ""
    LbModalSheet(
        onDismissRequest = { flow.dismiss() },
        modifier = modifier,
        visible = flow.muteTargetId != null
    ) {
        LbModalSheetTitle(stringResource(R.string.memory_mute_sheet_title))
        Spacer(Modifier.height(Spacing.md))
        Column {
            MuteDuration.entries.forEach { duration ->
                CorrectionSubmenuItem(durationLabel(duration)) {
                    onMute(muteMemoryId, duration)
                    flow.dismiss()
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        // 这一档**没有主动作**：只有一个"取消"。空标签的动不入树（第29节 修过的那条）
        LbModalSheetActions(
            listOf(LbDialogAction(stringResource(R.string.a11y_action_cancel), { flow.dismiss() }, tone = LbDialogActionTone.Muted))
        )
    }

    val wrongMemoryId: String = flow.wrongTargetId ?: flow.lastWrongTargetId ?: ""
    // 草稿同理：dismiss 那一步把 wrongDraft 清了，淡出的那张卡不能因此画出一只空框——
    // 开着时读业务那一份，退场那一段读余温那一份。
    val shownWrongDraft: String =
        if (flow.wrongTargetId != null) flow.wrongDraft else flow.lastWrongDraft
    LbModalSheet(
        onDismissRequest = { flow.dismiss() },
        modifier = modifier,
        visible = flow.wrongTargetId != null
    ) {
        LbModalSheetTitle(stringResource(R.string.memory_mark_wrong_sheet_title))
        Spacer(Modifier.height(Spacing.sm))
        // 第6节第5条 第②栏：屏幕上那行说明与输入框的读屏名字共用同一条资源
        val wrongHint = stringResource(R.string.memory_wrong_input_hint)
        Text(
            text = wrongHint,
            style = AppTypography.labelSmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = shownWrongDraft,
            onValueChange = { flow.editWrongDraft(it) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = wrongHint },
            placeholder = {
                Text(stringResource(R.string.memory_wrong_input_placeholder), style = AppTypography.labelSmall, color = TextHint)
            },
            textStyle = AppTypography.labelSmall.copy(color = TextPrimary),
            singleLine = false,
            maxLines = 3,
            shape = LoveBrainShape.sm
        )
        Spacer(Modifier.height(Spacing.md))
        LbModalSheetActions(
            listOf(
                LbDialogAction(stringResource(R.string.a11y_action_cancel), { flow.dismiss() }, tone = LbDialogActionTone.Muted),
                LbDialogAction(
                    label = stringResource(R.string.a11y_action_confirm),
                    onClick = {
                        onWrong(wrongMemoryId, shownWrongDraft.trim())
                        flow.dismiss()
                    }
                )
            )
        )
    }
}

/** 时长选项的中文说法。原来散在三个手写的 `CorrectionSubmenuItem("仅本轮")` 里，
 *  少一档没人会发现；现在跟着 enum 走，加一档就多一行。
 *  `internal` 是给守卫用的：测试要拿**同一份**标签去树里找，而不是自己抄一遍中文。 */
internal fun durationLabel(duration: MuteDuration): String = when (duration) {
    MuteDuration.THIS_ROUND -> "仅本轮"
    MuteDuration.TODAY -> "今天剩余"
    MuteDuration.UNTIL_RESTORE -> "直到手动恢复"
}

/**
 * "暂时别提"时长选择子菜单项。
 *
 * 它唯一的调用者就是本文件那颗暂停时长浮层，所以它跟着宿主住，而不是留在结果区。
 *
 * 第6节第5条 :531：这一档上一格没量过——那格只量了「不对」那颗浮层。回扫同一菜单的
 * 另一条分支时量到 **307x23dp**（探针 ：去掉下面那颗 `heightIn` 后守卫报出的实测值），
 * 离 48dp 差着一整档手指。现在这一档和浮层动作共用同一个下限常量，
 * 下次改设计系统的动作热区，这一档会跟着走，而不是留一个自己抄的数。
 */
@Composable
internal fun CorrectionSubmenuItem(
    label: String,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "muteSubmenu_$label")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LB_SHEET_ACTION_MIN_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = AppTypography.labelSmall,
            color = PrimaryDark,
            fontWeight = FontWeight.Medium
        )
    }
}
