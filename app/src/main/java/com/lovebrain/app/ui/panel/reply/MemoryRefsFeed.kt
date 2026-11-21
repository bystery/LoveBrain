package com.lovebrain.app.ui.panel.reply

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.LB_SHEET_ACTION_MIN_DP
import com.lovebrain.app.core.designsystem.LbTextAction
import com.lovebrain.app.core.designsystem.LbTextActionTone
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.PrimaryDark
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.model.MemoryRef

/** 一屏内先念几条，多出来的靠「更多 N 条」那条文字入口展开 */
private const val MAX_INITIAL_REFS = 5

/**
 * 「这一块到底画不画」的**唯一判据**，抽成一颗不碰 Compose 的纯函数。
 *
 * 三条一起成立才画：手上真的有条目（`memoryRefs` 非空）、这一份结果**当时不是**「仅看本轮」
 * （`!onlyThisRound`）、以及问它的人要的是**展开后的那一块**（`showRefs`）。
 *
 * 这一颗会被问两次，两次问的都是同一颗函数，不许有第二套 when：
 * - **容器在不在**（`showRefs = true` 去问）：有真引用且非仅看本轮 ⇒ 紧凑容器画（首行计数 +
 *   行尾动作）；没有引用或那一轮是「仅看本轮」⇒ 整区一个节点都不画——诚实由"什么都不说"保证。
 * - **清单展没展开**（把真实 `showRefs` 传进去）：容器之内那一份 ref 清单跟这一格收放。
 *
 * 为什么要单独有一颗：禁令（第10节第3条/第10节第4条）是**判据**，不是画法——
 * "开着「仅看本轮」时一条引用都不许渲染"要在不启动组合栈的地方也能被读数钉住
 * （离线探针与 `MemoryRefsFeedTest` 的真值表都取这一颗）。藏在 `AnimatedVisibility`
 * 的参数里就只剩"跑一次 Robolectric 才知道"这一种验法，而那一台仪器本轮正是红的来源。
 *
 * 三种坏实现各红在自己的那一行上：漏掉那颗布尔（开着开关仍画假引用）、
 * 把整块删空（关着开关也画不出真引用，于是「修正记忆」这一族整条消失）、
 * 给空清单铺一个带说明句的空壳。判据取集合而不是取条数，
 * 所以"顺手补一句本轮没有引用"那种解释也红（可见文字从 0 涨到 1）。
 */
internal fun memoryRefsFeedVisible(
    memoryRefs: List<MemoryRef>,
    showRefs: Boolean,
    onlyThisRound: Boolean
): Boolean = showRefs && memoryRefs.isNotEmpty() && !onlyThisRound

/**
 * 「本轮参考」的**紧凑容器**：首行小字念「本轮参考 N 条」＋ 行尾一颗常驻「纠正中心」动作，
 * 点首行展开才看见 ref 清单（每条仍带四个纠正动作与撤销）。
 *
 * 为什么长这样（2026-11-21 信息减法）：旧形状是四层壳——问句标题「这轮参考了哪些信息？」、
 * 「调整军师本轮参考的记忆」说明句、独立的「修正记忆」文字入口、再套着几条数据。
 * 用户原话："像写说明书不像软件，反人类"。三层壳并成一个容器：
 * 首行计数小字自己就是入口与说明（N 条是真的，念错条数就是编），
 * 纠正中心的去向收进行尾那颗常驻动作，不再各占一行。
 *
 * 两种空态一个字都不画（判据就是 [memoryRefsFeedVisible]，`showRefs = true` 那一次问）：
 * 本轮没有真引用 ⇒ 整区不渲染，不留"本轮没有引用历史记忆"那句常驻说明；
 * 这一份结果当时是「仅看本轮」⇒ 同样不画——那一轮的请求正文里没有旧记忆，
 * 画得出的任何引用都是假引用（第10节第3条）。诚实由"什么都不说"保证，
 * 不由"说一句诚实的话常驻"保证。钉住它的是 `MemoryCorrectionEntryTest`
 * 里那几格零文字零可点的读数，与 `MemoryRefsFeedTest` 的真值表。
 *
 * 展开态不搬进来：`showRefs` 由调用方持有（翻它的是首行那一颗 toggle），换一整轮由调用方归零。
 * 展开与收起就是首行自己：没有第二个「收起」动作，也没有标题行之外的说明句。
 *
 * 清单本体与逐条纠正词表住在这一份里：kind 短标签（画像/场景/事项/经验）、初始折叠条数与
 * 那行「更多 N 条」、行尾入口点开的那四项（这条不对 / 暂时别提 / 已结束 / 不是她的），
 * 撤销只在刚纠正过这一条时出现。**只有一份清单**：整轮共享，不许按卡分身
 * （钉子：`no per-card copy of the list exists`）。
 *
 * 浮层的开合与草稿不归这一份：它们住在 [MemoryCorrectionFlow]（它自己渲染
 * [MemoryCorrectionFlowHost]），这里只发意图；落盘动作由调用方传进来的回调承担。
 *
 * 行与行之间的「更多 N 条」与行尾「纠正中心」都走 [LbTextAction]（热区两轴与角色由组件保证，
 * 不自画 `Text(...).clickable {}`）。首行那颗 toggle 的热区按
 * [ResultDimens.UTILITY_HITBOX_DP] 垫到全站下限——"点得中"和"长什么样（小字）"分两层。
 */
@Composable
internal fun MemoryRefsSection(
    memoryRefs: List<MemoryRef>,
    showRefs: Boolean,
    onCorrection: (String, CorrectionAction, String, com.lovebrain.app.model.MuteDuration) -> Unit,
    onUndoCorrection: (String) -> Unit,
    correctionFlow: MemoryCorrectionFlow,
    /** 首行那一颗 toggle：展开态住在调用方（ResultArea → 面板），翻它的只有这一颗 */
    onToggleRefs: () -> Unit = {},
    /**
     * 这一份结果**当时**是不是「仅看本轮」——传冻结的那一份（`replyGenerationContext.onlyThisRound`），
     * 不是界面上那颗实时开关。为真时整区一个字都不画：那一轮的请求正文里没有一段来自知识库，
     * 于是任何看起来像"本轮参考过的记忆"的东西都是假引用（第10节第3条）。
     */
    onlyThisRound: Boolean = false,
    correctedIds: Set<String> = emptySet(),
    onRefCorrected: (String) -> Unit = {},
    /** 行尾那颗常驻动作：打开记忆纠正中心（旧的独立文字入口已并进这一颗容器） */
    onShowCorrectionCenter: () -> Unit = {}
) {
    AnimatedVisibility(
        // 容器在不在：同一颗判据，把展开态当 true 问一次（无引用/仅看本轮 ⇒ 整区不渲染）
        visible = memoryRefsFeedVisible(memoryRefs, showRefs = true, onlyThisRound),
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm)
                .clip(LoveBrainShape.md)
                .background(SurfaceInset, LoveBrainShape.md)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
        ) {
            // 首行：计数小字自己就是展开/收起的那一处操作；行尾那颗是纠正中心的常驻动作。
            // clickable 排在 padding 之前（排后面等于自己把热区削掉一圈）。
            val (interaction, scale) = rememberPressScale(0.96f, "memoryRefsHeaderScale")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ResultDimens.UTILITY_HITBOX_DP.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onToggleRefs
                    )
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.memory_refs_summary, memoryRefs.size),
                    style = AppTypography.labelSmall,
                    color = TextHint,
                    modifier = Modifier.weight(1f)
                )
                LbTextAction(
                    label = stringResource(R.string.memory_center_entry),
                    tone = LbTextActionTone.Muted,
                    onClick = onShowCorrectionCenter
                )
            }
            AnimatedVisibility(
                // 清单展没展开：同一颗判据的第二次问（真实 showRefs）
                visible = memoryRefsFeedVisible(memoryRefs, showRefs, onlyThisRound),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    var showAllRefs by remember { mutableStateOf(false) }
                    val displayRefs =
                        if (showAllRefs) memoryRefs else memoryRefs.take(MAX_INITIAL_REFS)
                    displayRefs.forEachIndexed { index, ref ->
                        if (index > 0) Spacer(Modifier.height(Spacing.xs))
                        MemoryRefItem(
                            ref = ref,
                            onCorrection = onCorrection,
                            onCorrected = { onRefCorrected(ref.id) },
                            undoAvailable = ref.id in correctedIds,
                            onUndoCorrection = onUndoCorrection,
                            correctionFlow = correctionFlow
                        )
                    }
                    if (memoryRefs.size > MAX_INITIAL_REFS && !showAllRefs) {
                        LbTextAction(
                            label = stringResource(
                                R.string.memory_refs_more,
                                memoryRefs.size - MAX_INITIAL_REFS
                            ),
                            tone = LbTextActionTone.Muted,
                            onClick = { showAllRefs = true }
                        )
                    }
                }
            }
        }
    }
}

/**
 * kind 的短标签——只有这一处映射，展开区里念的就是这两个字。
 *
 * 走资源而不是 Kotlin 常量：这一族以前在英文设备上念的是中文（常量只有一份中文），
 * 标签又正是"这条被引用的东西是哪一类"那句话的一部分，念错等于没念。
 */
@StringRes
private fun memoryKindLabelRes(ref: MemoryRef): Int = when (ref.kind) {
    com.lovebrain.app.model.MemoryKind.PROFILE -> R.string.memory_kind_profile
    com.lovebrain.app.model.MemoryKind.SCENE -> R.string.memory_kind_scene
    com.lovebrain.app.model.MemoryKind.ONGOING -> R.string.memory_kind_ongoing
    com.lovebrain.app.model.MemoryKind.LESSON -> R.string.memory_kind_lesson
}

/**
 * 单条**正在被引用的**记忆：`[短标签] + 具体内容 + 一颗「修正记忆」文字入口`。
 *
 * 字号按普通阅读档给（正文 12sp、标签 11sp），不是当"次要到可以忽略"的脚注画——
 * 用户问的第一句话就是"它到底引用了什么"，那行正文得读得动。
 *
 * 入口点开才是那四种说法（这条不对 / 暂时别提 / 已结束 / 不是她的），撤销只在
 * 这一行刚交出去过纠正时作为最后一行出现（没纠正过的 id 撤销必然失败，不给假出口）；
 * 行上常驻的只有一颗字，四种动作不铺开，菜单里也不再每项跟一行技术解释
 * （"标记为错误内容"那种说法住在测试与文档里，不住在用户的指头上）。
 */
@Composable
internal fun MemoryRefItem(
    ref: MemoryRef,
    onCorrection: (String, CorrectionAction, String, com.lovebrain.app.model.MuteDuration) -> Unit,
    onUndoCorrection: (String) -> Unit,
    /** 浮层的开关与草稿都归持有者，这一行只发意图 */
    correctionFlow: MemoryCorrectionFlow,
    /** 这一行交出去过纠正（本屏内）——只有这时「撤销」才有东西可撤 */
    onCorrected: () -> Unit = {},
    undoAvailable: Boolean = false
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "[${stringResource(memoryKindLabelRes(ref))}]",
                style = AppTypography.labelMedium,
                color = TextHint,
                modifier = Modifier.padding(end = Spacing.xs)
            )
            Text(
                text = ref.text,
                style = AppTypography.bodySmall,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            LbTextAction(
                label = stringResource(R.string.memory_fix_entry),
                tone = LbTextActionTone.Accent,
                onClick = { menuOpen = !menuOpen }
            )
        }

        // 修正菜单——DropdownMenu 浮层，不改变结果区 layout height
        androidx.compose.material3.DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier
                .clip(LoveBrainShape.md)
                .background(SurfaceCard)
        ) {
            // 四种说法就是四种已有动作，顺序按用户读得顺的那一条走；撤销（若有）永远在最后
            CorrectionDropdownItem(stringResource(R.string.memory_action_wrong)) {
                menuOpen = false
                correctionFlow.requestWrong(ref.id)
                onCorrected()
            }
            CorrectionDropdownItem(stringResource(R.string.memory_action_mute)) {
                menuOpen = false
                correctionFlow.requestMute(ref.id)
                onCorrected()
            }
            CorrectionDropdownItem(stringResource(R.string.memory_action_finished)) {
                onCorrection(
                    ref.id,
                    CorrectionAction.FINISHED,
                    "",
                    com.lovebrain.app.model.MuteDuration.UNTIL_RESTORE
                )
                onCorrected()
                menuOpen = false
            }
            CorrectionDropdownItem(stringResource(R.string.memory_action_wrong_person)) {
                onCorrection(
                    ref.id,
                    CorrectionAction.WRONG_PERSON,
                    "",
                    com.lovebrain.app.model.MuteDuration.UNTIL_RESTORE
                )
                onCorrected()
                menuOpen = false
            }
            // 「撤销」只在这条引用本屏内真的被纠正过时出现：否则点下去是一次注定的假失败
            // （撤销接口对"本来就没有纠正"的 id 返回 false，界面却把那次当成撤销失败）。
            // 更早纠正过的条目已经被注入侧过滤、不会出现在这份清单里；要撤销它们走纠正中心。
            if (undoAvailable) {
                CorrectionDropdownItem(stringResource(R.string.memory_action_undo)) {
                    onUndoCorrection(ref.id)
                    menuOpen = false
                }
            }
        }

    }
}

/**
 * 菜单里的一项：一行字、一颗按钮，**没有第二行说明**。
 *
 * 以前这一项是 `label + desc` 两段（「不对」后面跟「标记为错误内容」），读起来像工具说明书，
 * 而且那两段都是内联中文。现在热区走设计系统那颗浮层动作的下限（与「暂停时长」三档同一常量），
 * 名字就是这一句短词。
 */
@Composable
private fun CorrectionDropdownItem(
    label: String,
    onClick: () -> Unit
) {
    val (interaction, scale) = rememberPressScale(0.96f, "correctionDropdownScale")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LB_SHEET_ACTION_MIN_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
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
