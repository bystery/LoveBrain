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
 * 三条一起成立才画：展开态开着（`showRefs`）、手上真的有条目（`memoryRefs` 非空）、
 * 这一份结果**当时不是**「仅看本轮」（`!onlyThisRound`）。
 *
 * 为什么要单独有一颗：这一族的禁令（ 第10节第3条/第10节第4条）是**判据**，不是画法——
 * "开着「仅看本轮」时一条引用都不许渲染"要在不启动组合栈的地方也能被读数钉住
 * （离线探针与 `MemoryRefsFeedTest` 的真值表都取这一颗）。藏在 `AnimatedVisibility`
 * 的参数里就只剩"跑一次 Robolectric 才知道"这一种验法，而那一台仪器本轮正是红的来源。
 *
 * 三种坏实现各红在自己的那一行上：漏掉那颗布尔（开着开关仍画假引用）、
 * 把整块删空（关着开关也画不出真引用，于是「修正记忆」这一族整条消失）、
 * 给空清单铺一张带说明句与「收起」的空壳。判据取集合而不是取条数，
 * 所以"顺手补一句本轮没有引用"那种解释也红（可见文字从 0 涨到 1）。
 */
internal fun memoryRefsFeedVisible(
    memoryRefs: List<MemoryRef>,
    showRefs: Boolean,
    onlyThisRound: Boolean
): Boolean = showRefs && memoryRefs.isNotEmpty() && !onlyThisRound

/**
 * 「本轮参考」那几条记忆的**公共清单本体与逐条修正入口**。
 *
 * 词表全部走资源（`values/strings.xml` 与 `values-en/strings.xml` 两份同键）：入口
 * `memory_fix_entry`、四种说法 `memory_action_wrong` / `_mute` / `_finished` /
 * `_wrong_person`、撤销 `memory_action_undo`、每条前面那个短标签 `memory_kind_*`、
 * 折叠时那句 `memory_refs_more`。这一族以前的写法是 Kotlin 常量 + 菜单里每项再补一行
 * 技术解释（「标记为错误内容」「暂停作为续聊素材」那种），于是英文环境念中文、
 * 日常页面读起来像工具说明书。现在日常页面上只剩入口那几个字，
 * 说明整块只有一句 `memory_refs_note`；守卫取锚点也从资源取（`app.getString(…)`），
 * 不许再抄第二份中文。
 *
 * 入口与四种动作调的都是已有的 `MemoryCorrection` 机制（标错 / 静音 / 已结束 / 串错对象，
 * 都可撤销），**不是**让用户给对面发一条新消息——这句判断不在界面上解释。
 *
 * 它为什么是一个独立的所有者：变化理由是记忆条目的**展示与纠正词表**——kind 短标签
 * （画像/场景/事项/经验）、初始折叠条数与那行「更多 N 条」、行尾入口点开的那五项
 * （这条不对 / 暂时别提 / 已结束 / 不是她的 / 撤销）。这些跟方案卡的内容、跟卡上是哪一张
 * 都不是同一件会一起变的事。
 *
 * **这一份只有一份清单**：八张卡下面那八条文字入口翻的是同一个展开区，因为手上有的证据
 * 只到"这一轮参考了这些"，没有到"这一句是哪一张卡用的"。所以这里不许长出按卡分身的清单，
 * 也不许给某一条参考编一个它专属的归属。
 *
 * 浮层的开合与草稿不归这一份：它们住在 [MemoryCorrectionFlow]（它自己渲染
 * [MemoryCorrectionFlowHost]），这里只发意图；落盘动作由调用方传进来的回调承担。
 * 本文件只回答"这一条记忆怎么念出来、从哪里能改它"。
 *
 * 展开态也不搬进来：`showRefs` 由调用方持有（翻它的是卡片行下方那条文字入口，画清单的是
 * 这一份，状态得住在两者之上才换得动）。换一整轮由调用方归零。
 *
 * 渲染形状：本块在主 Column 中按正常文档流渲染，收起时 AnimatedVisibility 不占纵向高度，
 * 展开后才正常增加信息高度，不覆盖方案卡或切换器。
 *
 * ⚠ **没有真引用就一块都不画**：`visible` 那一条同时要求 `memoryRefs` 非空、`onlyThisRound`
 * 为假，且这一条判据不看 `showRefs` 开没开。三种长法都算事故——空清单还画一张带说明句与
 * 「收起」的大壳，替这一轮编一条"看起来被引用过"的条目，或者**开着「仅看本轮」还画得出引用**。
 * 开着「仅看本轮」的那一轮，请求侧交回来的就是**空清单**（旧记忆整块没进 prompt），
 * 所以那一轮这里必须什么都不画；还画得出引用，画的就是假引用。
 * 第二半条判据住在 [onlyThisRound] 这一颗形参上：它是**这一份结果当时冻结的范围**
 * （`replyGenerationContext.onlyThisRound` 那一份，不是界面上那颗实时开关——开关是这一轮
 * 之后才拨的，翻不回结果已经用过的上下文）。规则本身不在这里重新推导：请求侧的唯一真源是
 * `domain/prompt/MemoryRefPolicy.refsForRoundScope`，这一族只是它的显示侧保险——
 * 清单与范围对不上时，宁可一块都不画，也不画一条用户点得着、却根本没参与这一轮请求的记忆。
 * 钉住它的是 `MemoryRefsFeedTest` 里那三格：
 * "空清单 ⇒ 整棵树零个文字节点、零颗可点节点"、"开着仅本轮 + 一份非空清单 ⇒ 同样零个"，
 * 反向证人"关掉开关 + 一条真引用 ⇒ 正文入口标签收起全都在"，
 * 外加一格不依赖组合栈的 `the visibility gate is one truth table over its three inputs`
 * —— 它把 [memoryRefsFeedVisible] 的整个输入空间钉成"只有『展开 + 有真引用 + 非仅本轮』画得出"，
 * 同一台仪器（Robolectric）这一轮出过 8 格锚点漂移的红，禁令必须留一条不靠它也读得数的判据。
 * 反例是同一格里"塞一条占位条目/铺一个空壳/漏掉那颗布尔"必须红。
 *
 * ⚠ 但"这一块不画"**不等于"这一族在屏上消失了"**：结果区下方那条常驻纠正出口
 * （`ResultArea.kt` 的 [MemoryCorrectionEntry]）不归这一块管，也**不许**跟着这里的布尔一起收掉——
 * 那一轮的请求正文里确实没有旧记忆，可用户仍然要知道这一刻军师用的是什么上下文、仍然要有改它的出口。
 * 判据分得很清：**清单只写真引用**（这里），**出口常驻**（ResultArea 那一颗），
 * 把出口挂在"有真引用"之后就是 M20 那条"入口不易发现"的本体。
 *
 * 这一颗**不归进** `LbSection`，两条都是事实不是偏好：
 * 1. 它没有标题，而且不许有。「这轮参考了哪些信息？」那句话的归属是卡片下方那条入口
 *    （`SchemeCard`），不是这块清单自己；这里再写一遍就是同一句话说两遍，
 *    而展开区要的只是"逐条念出来 + 每条能改"，不是一段使用说明。
 *    唯一那一句说明（`memory_refs_note`）挂在清单头上，是因为用户原话问的就是
 *    "这块到底是干嘛的"——一句之内说清，不再往下叠第二句。
 * 2. 折叠的入口不在这一颗上。`showRefs` 住在调用方，而 `LbSection` 的可折叠档规定的是
 *    "**标题行自己就是那一处操作**"（热区下限、`Role.Button`、状态公告都由那一行给）；
 *    这里既没有那一行，也就没有那一处可点。
 *
 * 行与行之间的「更多 N 条」「收起」两条都已经转进 [LbTextAction]（原先是页面自画的
 * `Text(...).clickable {}`，热区只由 `padding` 垫出来、角色为空——同一族缺陷的第三种写法）。
 * 「收起」用资源里已有的那句，中英文都已经有了。
 */
@Composable
internal fun MemoryRefsSection(
    memoryRefs: List<MemoryRef>,
    showRefs: Boolean,
    onCorrection: (String, CorrectionAction, String, com.lovebrain.app.model.MuteDuration) -> Unit,
    onUndoCorrection: (String) -> Unit,
    correctionFlow: MemoryCorrectionFlow,
    /** 展开区自己留一条回得去的路：翻开来那颗入口在**横向**卡片行里，划出视野就点不着了 */
    onCollapse: () -> Unit = {},
    /**
     * 这一份结果**当时**是不是「仅看本轮」——传冻结的那一份（`replyGenerationContext.onlyThisRound`），
     * 不是界面上那颗实时开关。为真时这一块一个字都不画：那一轮的请求正文里没有一段来自知识库，
     * 于是任何看起来像"本轮参考过的记忆"的东西都是假引用（第10节第3条）。
     */
    onlyThisRound: Boolean = false
) {
    AnimatedVisibility(
        // 空清单不画壳、开着「仅看本轮」不画假引用：判据就是上面那颗 `memoryRefsFeedVisible`
        visible = memoryRefsFeedVisible(memoryRefs, showRefs, onlyThisRound),
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
            // 整块只此一句，说的是"这里改的是军师本轮参考的记忆"，不是给对方发消息
            Text(
                text = stringResource(R.string.memory_refs_note),
                style = AppTypography.labelSmall,
                color = TextHint
            )
            Spacer(Modifier.height(Spacing.xs))
            var showAllRefs by remember { mutableStateOf(false) }
            val displayRefs =
                if (showAllRefs) memoryRefs else memoryRefs.take(MAX_INITIAL_REFS)
            displayRefs.forEachIndexed { index, ref ->
                if (index > 0) Spacer(Modifier.height(Spacing.xs))
                MemoryRefItem(
                    ref = ref,
                    onCorrection = onCorrection,
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
            LbTextAction(
                label = stringResource(R.string.action_collapse),
                tone = LbTextActionTone.Muted,
                onClick = onCollapse
            )
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
 * 入口点开才是那四种说法（这条不对 / 暂时别提 / 已结束 / 不是她的）加最后一行撤销；
 * 行上常驻的只有一颗字，四种动作不铺开，菜单里也不再每项跟一行技术解释
 * （"标记为错误内容"那种说法住在测试与文档里，不住在用户的指头上）。
 */
@Composable
internal fun MemoryRefItem(
    ref: MemoryRef,
    onCorrection: (String, CorrectionAction, String, com.lovebrain.app.model.MuteDuration) -> Unit,
    onUndoCorrection: (String) -> Unit,
    /** 浮层的开关与草稿都归持有者，这一行只发意图 */
    correctionFlow: MemoryCorrectionFlow
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
            // 四种说法就是四种已有动作，顺序按用户读得顺的那一条走；撤销永远在最后
            CorrectionDropdownItem(stringResource(R.string.memory_action_wrong)) {
                menuOpen = false
                correctionFlow.requestWrong(ref.id)
            }
            CorrectionDropdownItem(stringResource(R.string.memory_action_mute)) {
                menuOpen = false
                correctionFlow.requestMute(ref.id)
            }
            CorrectionDropdownItem(stringResource(R.string.memory_action_finished)) {
                onCorrection(
                    ref.id,
                    CorrectionAction.FINISHED,
                    "",
                    com.lovebrain.app.model.MuteDuration.UNTIL_RESTORE
                )
                menuOpen = false
            }
            CorrectionDropdownItem(stringResource(R.string.memory_action_wrong_person)) {
                onCorrection(
                    ref.id,
                    CorrectionAction.WRONG_PERSON,
                    "",
                    com.lovebrain.app.model.MuteDuration.UNTIL_RESTORE
                )
                menuOpen = false
            }
            CorrectionDropdownItem(stringResource(R.string.memory_action_undo)) {
                onUndoCorrection(ref.id)
                menuOpen = false
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
