package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import com.lovebrain.app.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.model.GenerateResult
import com.lovebrain.app.model.LoveBrainResponse
import com.lovebrain.app.model.ReplyCompleteness
import com.lovebrain.app.model.RewriteCommand
import com.lovebrain.app.model.RewriteState
import com.lovebrain.app.model.Scheme
import com.lovebrain.app.model.SchemeFeedback
import com.lovebrain.app.model.SchemeIdentity
import com.lovebrain.app.model.MemoryRef
import com.lovebrain.app.model.CorrectionAction
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*

/** 结果区内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
internal object ResultDimens {
    /**
     * 结果区那几条**文字入口**（现在只剩「纠正记忆」）的可点击盒子下限。
     * 盒子按这颗数垫，里面的字仍按文字档那一号画——"点得中"和"长什么样"是两层，
     * 不能为了前者把后者撑成一颗大按钮。
     */
    const val UTILITY_HITBOX_DP = AppDimens.TOUCH_TARGET_MIN_DP
    const val FILTER_TAB_HEIGHT_DP = 28      // 筛选 Tab 高度
    const val SKELETON_TAG_WIDTH_DP = 60     // 骨架标签条宽度
}

@Composable
fun ResultArea(
    result: GenerateResult?,
    @Suppress("UNUSED_PARAMETER") isGenerating: Boolean,
    streamingCoreText: String,
    isGeneratingCore: Boolean,
    streamingSchemes: List<Scheme>,
    feedbacks: Map<String, SchemeFeedback>,
    onFeedback: (Scheme, SchemeFeedback) -> Unit,
    onCopyScheme: (Scheme) -> Unit,
    onRetry: () -> Unit,
    // 本轮参考记忆 + 纠正回调
    memoryRefs: List<MemoryRef> = emptyList(),
    onCorrection: (String, CorrectionAction, String, com.lovebrain.app.model.MuteDuration) -> Unit = { _, _, _, _ -> },
    onUndoCorrection: (String) -> Unit = {},
    providerReady: Boolean,
    onOpenSettings: () -> Unit,
    // 单条改写
    rewriteStates: Map<String, RewriteState> = emptyMap(),
    onRewrite: (SchemeIdentity, RewriteCommand) -> Unit = { _, _ -> },
    onClearRewriteState: (SchemeIdentity) -> Unit = {},
    onCancelRewrite: (SchemeIdentity) -> Unit = {},
    // 改写完成后界面不再画"返回原版/用这版"那条工具条：完成就是正文换了的一张默认卡。
    // 这一格接线仍挂在调用方（撤销能力在数据层，不在这里），本组件不再往下传。
    @Suppress("UNUSED_PARAMETER") onUndoRewrite: (SchemeIdentity) -> Unit = {},
    // 自定义改写回调
    onCustomRewrite: (SchemeIdentity, String) -> Unit = { _, _ -> },
    // 「仅看本轮」这一屏**不再画任何开关**：那颗状态住在 RoundStateStore，由长按生成那条
    // 路径在发起请求时读取。这两个形参只是让面板那边现有的调用点照常编译，
    // 调用点删掉这两个实参之后它们就该跟着删——这里不读它们，也就无处能把开关画回来。
    @Suppress("UNUSED_PARAMETER") onlyThisRound: Boolean = false,
    @Suppress("UNUSED_PARAMETER") onToggleOnlyThisRound: () -> Unit = {},
    // （ 交回单）｜显示侧保险：`MemoryRefsFeed` 的可见性真值表要求"这一份结果**当时**
    // 不是仅看本轮"才画记忆引用。这里要的是**生成时冻结**的那一份（VM `replyResultOnlyThisRound`），
    // 不是上面那颗实时开关——开关是这一轮之后才拨的，接实时值会把真用过的引用从屏上抹掉。
    // 面板调用点还没传它之前保持 false（等同请求侧单条撑着），接线落在 LoveBrainPanelScreen 的 ResultArea 实参处。
    frozenOnlyThisRound: Boolean = false,
    // 输入已变化（结果来自旧输入）这一判据仍在数据层，但它**在这一屏不再是就地的一条提示条**：
    // 通知位由面板那一侧按同一条判据排队（同一时间只显一条，前一条过期才轮到下一条），
    // 结果区里再画一条就是同一件事说两遍、还多出一条会常驻的横条。
    // 这两个形参只是让现有调用点照常编译，本组件不再读它们。
    @Suppress("UNUSED_PARAMETER") inputChanged: Boolean = false,
    @Suppress("UNUSED_PARAMETER") onRegenerateWithNewInput: () -> Unit = {},
    // 「记录实际发送」的可见入口已随结果区那颗总工具菜单一起退场，本组件不再调用它。
    // 同上：留着实参只为让面板现有的调用点照常编译。
    @Suppress("UNUSED_PARAMETER") onRecordSent: () -> Unit = {},
    // 结果区下方那条「纠正记忆」文字入口：这里只发"打开纠正中心"这一句意图，
    // 清单与撤销归 CorrectionCenter 自己画
    onShowCorrectionCenter: () -> Unit = {},
    // 「本轮参考」那份清单的展开态由调用方持有（每张卡下方那条文字入口去翻它）。
    // 默认收起：收起时 AnimatedVisibility 不占纵向高度。这一层留成入参而不是本地布尔，
    // 是因为翻它的入口在卡片那一侧、渲染清单的是这一块——状态得住在两者之上才换得动。
    memoryRefsExpanded: Boolean = false,
    // 卡下方那条文字入口点谁：展开的是**本轮共享**的参考清单（不是这张卡专属的证据关联），
    // 所以每颗卡传的是同一个 toggle；清单没内容时调用方传 null，入口就不渲染。
    onToggleMemoryRefs: () -> Unit = {},
    // 纠正浮层的状态与渲染归 MemoryCorrectionFlow（这一屏只发意图、不画浮层）。
    // 面板顶层传进来时遮罩盖满面板；没传就在本地建一颗并就地渲染——
    // 少一处接线不会变成"点了没反应"。
    correctionFlow: MemoryCorrectionFlow? = null,
    // 稳定轮次身份——只在整轮 generate 成功时变化
    generationRoundId: Int = 0,
    onInputIntent: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 卡下方那条文字入口的可见性判据：本轮一条参考信息都没有就不摆空工具块
    //（要求的"没有参考信息时不放一个空工具块"），有则每颗卡共用同一个 toggle——
    // 展开的是本轮共享清单，不是"这张卡专属引用"的归属。
    val roundRefsClick: (() -> Unit)? =
        if (memoryRefs.isNotEmpty()) onToggleMemoryRefs else null

    // 卡片行的**行级状态**：哪几张卡在调整展开态、每张卡在窗口里的落点、自定义草稿与
    // 自定义是否展开。四项一律按 identity.key 记，且住在 LazyRow item **之上**——
    // item 滑出视口会被销毁，状态留在卡里就是"滑出去再滑回来没了"或"串到同名标签的另一张卡"。
    // 换一整轮整包作废（跨轮 tag 会重名）。
    val rowState = rememberSchemeRowState(generationRoundId)
    // 点卡外收起：清空展开集合，但**不取消进行中的请求**、也不清草稿/结果
    val collapseAll: () -> Unit = { rowState.expandedKeys = emptySet() }
    // 改写完成标志清场：Done 只在卡片存在期间有意义——它是"这一版是新正文"的一次性回执，
    // 留着会让那张卡再点不开调整区（推导里 Done 排在展开之前）。正文由 ReplyPatch 贴好了才到这里。
    LaunchedEffect(rewriteStates, rowState) {
        rewriteStates.forEach { (key, state) ->
            val identity =
                if (state is RewriteState.Done && rowState.knownKeys[key] == true)
                    SchemeIdentity.fromKey(key)
                else null
            if (identity != null) {
                rowState.expandedKeys = rowState.expandedKeys - identity.key
                onClearRewriteState(identity)
            }
        }
    }

    // 谁持有 flow 谁渲染宿主：调用方给了就用人家的、不再就地画。
    // 提到 `when` 之外是因为**两种档都要能纠正**：卡下方那条入口在流式出卡的那一档也在，
    // 展开了清单就点得到「纠正」，不能等到成功档才有浮层的主人。
    val flow = correctionFlow ?: rememberMemoryCorrectionFlow()
    val hostLocally = correctionFlow == null

    when {
        // Phase 1 加载中：核心回复（schemes）还没出来
        isGeneratingCore -> {
            if (streamingSchemes.isNotEmpty()) {
                // ★ 边流式边出卡——只渲染风格（directions 不再 stream）
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .observeTapOutsideCards(rowState, collapseAll)
                ) {
                    if (streamingSchemes.isNotEmpty()) {
                        SchemeCardsRow(
                            schemes = streamingSchemes,
                            feedbacks = feedbacks,
                            onFeedback = onFeedback,
                            onCopyScheme = onCopyScheme,
                            rewriteStates = rewriteStates,
                            onRewrite = onRewrite,
                            onClearRewriteState = onClearRewriteState,
                            onCancelRewrite = onCancelRewrite,
                            onCustomRewrite = onCustomRewrite,
                            onReferenceClick = roundRefsClick,
                            rowState = rowState,
                            onInputIntent = onInputIntent
                        )
                    }
                    // 卡片行下方那条入口在流式那一档也是同一份**公共清单**，不是每张卡一份：
                    // 展开态、清单本体与逐条纠正都走成功档那一条通路（清单本体在 MemoryRefsFeed.kt）。
                    if (memoryRefs.isNotEmpty()) {
                        MemoryRefsSection(
                            memoryRefs = memoryRefs,
                            onlyThisRound = frozenOnlyThisRound,
                            showRefs = memoryRefsExpanded,
                            onCorrection = onCorrection,
                            onUndoCorrection = onUndoCorrection,
                            correctionFlow = flow,
                            onCollapse = onToggleMemoryRefs
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PrimaryLight, LoveBrainShape.md)
                            .padding(Spacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = Primary,
                                modifier = Modifier.size(Spacing.xl),
                                strokeWidth = Spacing.xs
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = "其余方案生成中…",
                                color = PrimaryDark,
                                style = AppTypography.bodySmall
                            )
                        }
                    }
                    LocalCorrectionFlowHostIfNeeded(
                        hostLocally = hostLocally,
                        flow = flow,
                        onCorrection = onCorrection
                    )
                }
            } else {
                CoreLoadingIndicator(
                    streamingCoreText = streamingCoreText,
                    modifier = modifier
                )
            }
        }

        // 全部完成：方案 + 进行中事项（分析展示区已移除）
        result is GenerateResult.Success -> {
            val response = result.response
            // 这里原来还有一格就地的「输入已变化」提示条：入口已经交给面板的通知队列
            //（判据同一条、位置同一处），成功档不再自己画第二条横条；
            // "按新输入重新生成"这一步的出口在通知那条上，不在这一格。

            // 两组已生成方案合成**一条**列表：风格在前、方向在后，按原有顺序接成
            // A B C D F E X S，存在几项就展示几项。这里没有"两组各一排、点着切"那一档了，
            // 但每一项仍然带着自己的来源与 identity（见 mergedSchemesInRoundOrder）。
            val roundSchemes = mergedSchemesInRoundOrder(response)

            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .observeTapOutsideCards(rowState, collapseAll)
            ) {
                SchemeCardsRow(
                    schemes = roundSchemes,
                    feedbacks = feedbacks,
                    onFeedback = onFeedback,
                    onCopyScheme = onCopyScheme,
                    rewriteStates = rewriteStates,
                    onRewrite = onRewrite,
                    onClearRewriteState = onClearRewriteState,
                    onCancelRewrite = onCancelRewrite,
                    onCustomRewrite = onCustomRewrite,
                    onReferenceClick = roundRefsClick,
                    rowState = rowState,
                    onInputIntent = onInputIntent
                )

                // §11.2: 八项里真缺的那几项要说出来——不静默隐藏，也不把合法 null 的
                // 「本轮不适合」报成缺项（那句文案归空卡自己，见 SchemeCollapsedBlock）。
                replyCompletenessNotice(response.replyCompleteness)?.let { notice ->
                    Spacer(Modifier.height(Spacing.sm))
                    ReplyIncompleteNotice(notice)
                }

                if (response.analysis.ongoing.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    OngoingSection(items = response.analysis.ongoing)
                }

                // 「本轮参考」那份清单在主 Column 中按正常文档流渲染，展开态是外面传进来的：
                // 默认收起时 AnimatedVisibility 不占纵向高度，展开后才正常增加信息高度，
                // 不覆盖方案卡。清单本体与逐条纠正词表住在 MemoryRefsFeed.kt。
                // 这一份是**整轮共用的一条清单**：八张卡下面那八条文字入口翻的都是它。
                if (memoryRefs.isNotEmpty()) {
                    MemoryRefsSection(
                        memoryRefs = memoryRefs,
                        onlyThisRound = frozenOnlyThisRound,
                        showRefs = memoryRefsExpanded,
                        onCorrection = onCorrection,
                        onUndoCorrection = onUndoCorrection,
                        correctionFlow = flow,
                        onCollapse = onToggleMemoryRefs
                    )
                }

                // 结果区下方那条「纠正记忆」：一颗文字入口，不常驻大型纠正工具区。
                // 中心里那份清单与撤销动作归 CorrectionCenter，这里只发"打开它"这一句意图。
                // 热区与字形仍是两层：外层盒子按 ResultDimens.UTILITY_HITBOX_DP 垫到可点下限
                // 并当那处点击，里面的字按文字档画——把字撑大不是"更好点"，那是把外观一起改了。
                val (correctionEntryInteraction, correctionEntryScale) =
                    rememberPressScale(0.96f, "correctionEntryScale")
                Spacer(Modifier.height(Spacing.xs))
                Box(
                    modifier = Modifier
                        .heightIn(min = ResultDimens.UTILITY_HITBOX_DP.dp)
                        .widthIn(min = ResultDimens.UTILITY_HITBOX_DP.dp)
                        .graphicsLayer {
                            scaleX = correctionEntryScale
                            scaleY = correctionEntryScale
                        }
                        .clip(LoveBrainShape.sm)
                        // clickable 排在 padding 之前：排后面等于自己把热区削掉一圈
                        .clickable(
                            interactionSource = correctionEntryInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = onShowCorrectionCenter
                        )
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "纠正记忆",
                        style = AppTypography.labelSmall,
                        color = PrimaryDark
                    )
                }

                LocalCorrectionFlowHostIfNeeded(
                    hostLocally = hostLocally,
                    flow = flow,
                    onCorrection = onCorrection
                )
            }
        }

        result is GenerateResult.Error -> {
            // 这一档原来是自画的 `Box + .background(ErrorBg) + 居中 Column`，那颗重试
            // 已经归进 `LbTextAction`（本机当时实量 72x26dp、role=无）。整块版式现在也
            // 交回设计系统：`LbEmptyState` 的 Strip 容器 + Error 语气——错因、标签文案、
            // 点击回调三者逐字照旧，改的只是"这一条谁画"。契约变化见 LbAsyncState.kt。
            LbEmptyState(
                message = result.message,
                tone = LbStateTone.Error,
                container = LbStateContainer.Strip,
                // 这里原来挂着一颗就地「重试」。本轮合同要回复档的主动作**只有一个插入槽**，
                // 而错误档属于"还没有结果"——槽上此刻画的正是那颗 40dp「生成回复」，
                // 再在错误条上放第二颗就变成同一屏两个入口做同一件事（实施书那句
                // "不可在结果底部再放第二份"）。错因照旧念出来，撤掉的只是那颗重复动作。
                modifier = modifier
            )
        }

        // 未配置供应商——空态不做死路，引导去设置页（可点通）
        !providerReady -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(LoveBrainShape.lg)
                    .background(SurfaceInset)
                    .padding(Spacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "还没有配置模型供应商",
                        color = TextSecondary,
                        style = AppTypography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(Spacing.md))
                    // 实量 **68x34dp、role=无**：同一族形状（自画 `Text + .background(Primary)`）。
                    // 这一档只有这一颗动作 ⇒ 它就是这一态的唯一主动作（判据：一态一颗主动作），
                    // 归 `LbPrimaryButton` 之后顺带第一次能表达禁用/进行中。
                    LbPrimaryButton(
                        state = LbButtonState.Idle,
                        label = stringResource(com.lovebrain.app.R.string.provider_open_settings),
                        onClick = { onOpenSettings() }
                    )
                }
            }
        }

        // 空状态：生成前直接留白
        else -> {
            Spacer(modifier = modifier.fillMaxWidth())
        }
    }
}

/** 方案筛选模式 */
private enum class SchemeFilter { ALL, LIKED, DISLIKED }

/**
 * 一条横向列表要渲染的方案：四风格在前、四方向在后，按原有顺序接成 A B C D F E X S。
 *
 * reply 为空的卡片不删除——八项宇宙里少一张就少了 A–H 的一个编号（用户看到 7 而不是 8）。
 * 空卡的**成因**由 scheme 自己带（§11.2 三因分家）：合法 null 的方向 notSuitable=true 走
 * 「本轮不适合」，没生成的那几项走「未生成」，见 SchemeCollapsedBlock。
 * 顺序与判据的唯一真源在 `LoveBrainResponse.mergedEightItems`，完整性读数与这一排共用它。
 */
internal fun mergedSchemesInRoundOrder(response: LoveBrainResponse): List<Scheme> =
    response.mergedEightItems

/**
 * 八项完整性读数 → 用户看得见的那一句轻提示（§11.2「提示与空卡文案各归各」）。
 *
 * 纯函数，不挂 Compose，好让 JVM 那侧直接钉住读数：
 * - 缺项：点名**真正没生成**的那几项，数量按整池八项说（`2/8`），不拿局部数冒充全体；
 * - 重复：点名共用同一条正文的那几项（凑数）；
 * - 合法 null 的「本轮不适合」：**不进这一句**——它由空卡自己标，报进缺项就是把协议允许的
 *   输出说成模型漏了（那正是这一格要推掉的旧混判）。
 * 无缺项、无重复时返回 null，画面上不出现提示条。
 */
internal fun replyCompletenessNotice(completeness: ReplyCompleteness): String? = when (completeness) {
    ReplyCompleteness.Complete, ReplyCompleteness.Empty -> null
    is ReplyCompleteness.Incomplete -> {
        val causes = buildList<String> {
            if (completeness.missingLabels.isNotEmpty()) {
                add(
                    "未生成 ${completeness.missingLabels.size}/${completeness.totalItems} 项：" +
                        completeness.missingLabels.joinToString("、")
                )
            }
            if (completeness.duplicatedLabels.isNotEmpty()) {
                add(
                    "${completeness.duplicatedLabels.size} 项正文重复：" +
                        completeness.duplicatedLabels.joinToString("、")
                )
            }
        }
        if (causes.isEmpty()) null else causes.joinToString("；") + "，可重新生成"
    }
}

/**
 * 卡片标签上那个**连续编号** A–H：一组四项、两组接起来还是八项，一个都不少
 * （第四个方向 DIRECTION:S 也在表里，别把它当成"ABC 之后只有 EFG"漏掉）。
 *
 * 这一层只管显示：内部 `scheme.tag` 仍是 STYLE 的 A/B/C/D 与 DIRECTION 的 F/E/X/S，
 * JSON 协议、prompt、记录键、身份全都不动，也**不许** `scheme.copy(tag = …)`。
 * 表里没有的（旧数据、异常 tag）退回原 tag 显示，不猜一个字母出来。
 */
private val schemeDisplayLettersByIdentityKey: Map<String, String> = mapOf(
    "STYLE:A" to "A", "STYLE:B" to "B", "STYLE:C" to "C", "STYLE:D" to "D",
    "DIRECTION:F" to "E", "DIRECTION:E" to "F", "DIRECTION:X" to "G", "DIRECTION:S" to "H"
)

internal fun schemeDisplayTag(scheme: Scheme): String =
    schemeDisplayLettersByIdentityKey[scheme.identity.key] ?: scheme.tag

/**
 * 卡片行的行级状态——四样东西都按 identity.key 记，都住在 LazyRow item 之上。
 *
 * - [expandedKeys]：**一张集合**，多张卡可以同时停在调整展开态；收起一张不许顺手关掉别的几张。
 * - [cardBoundsInWindow]：每张卡自己在窗口里的落点，只给"点卡外收起"那一步判断落点用。
 * - [customDrafts] / [customOpenKeys]：自定义那格的草稿与开合态。留在卡片里会随 item
 *   滑出视口被销毁（或者串到同名标签的另一张卡上），所以提到这一层。
 *
 * 换一整轮整包作废：跨轮的 tag 会重名（还是那 A/B/C/D），不清就会把的展开与草稿
 * 串到新一轮的卡上。这一份由 [rememberSchemeRowState] 按轮次身份发，同轮之内跨组合复用。
 */
internal class SchemeRowState {
    var expandedKeys by mutableStateOf(emptySet<String>())
    val cardBoundsInWindow = mutableStateMapOf<String, Rect>()
    val knownKeys = mutableStateMapOf<String, Boolean>()
    val customDrafts = mutableStateMapOf<String, String>()
    val customOpenKeys = mutableStateMapOf<String, Boolean>()

    /** 进/出整窗设置页这类"内容被换掉"的往返：展开区收起，草稿与结果留着 */
    fun collapseButKeepDrafts() {
        expandedKeys = emptySet()
        cardBoundsInWindow.clear()
        customOpenKeys.clear()
        knownKeys.clear()
    }
}

/**
 * 卡片行状态的暂存位。
 *
 * 为什么要有这一格：结果区整块会在两种情况下离开组合——换生成模式、以及齿轮那扇整窗设置页
 * （它换的是整窗内容）。离开组合就等于 `remember` 丢掉，展开区确实按要求收起了，但草稿也跟着没了。
 * 这里只把**同一轮**的那份捞回来：轮次身份一变，暂存位就不作数，不会把的草稿接到新一轮的卡上。
 * 更干净的落点是让持有者在面板层（或数据层）拿着这份状态，那样这一格就可以删掉——接线需求写在交付说明里。
 */
private object SchemeRowStateStash {
    var roundId: Int? = null
    var state: SchemeRowState? = null
}

@Composable
private fun rememberSchemeRowState(generationRoundId: Int): SchemeRowState {
    val stashed = SchemeRowStateStash.state
    val restored =
        if (stashed != null && SchemeRowStateStash.roundId == generationRoundId) stashed else null
    // 捞回来的那一份先收起展开区（卡片已经不在了，落点也是过期的），草稿与结果不动。
    // 这一步放在 remember 里而不是组合期直接写：组合中途改状态容易自己触发自己。
    val rowState = remember(generationRoundId) {
        (restored ?: SchemeRowState()).also { if (restored != null) it.collapseButKeepDrafts() }
    }
    LaunchedEffect(rowState, generationRoundId) {
        SchemeRowStateStash.roundId = generationRoundId
        SchemeRowStateStash.state = rowState
    }
    return rowState
}

/**
 * "点卡片外侧就收起展开区"：挂在结果区那一整块上的**只观察、不消费**的手势。
 *
 * 为什么不是遮罩：铺一层可点击的透明层会顺手把卡内输入、提交、纵向滚动、横向划卡全挡住，
 * 那些恰恰是不该被收起来的东西。这里的写法是——
 * 1. 用 `awaitFirstDown(requireUnconsumed = false)` 起手势，全程不 consume 任何事件，
 *    子节点（卡片、胶囊、输入框、LazyRow）该收到的照收；
 * 2. 按下与抬起之间的位移超过 touch slop 就当"这不是点击"（划卡、拖列表都不算）；
 * 3. 抬起那点经本节点的 `localToWindow` 换算到窗口系，逐个和卡片上报的落点比：
 *    落在任一张卡里就不收，全不落才清空展开集合。
 *
 * 清空只做这一件事：不取消在跑的改写请求、不清草稿、不清改写结果——
 * 那些各有自己的主人（前台协调器 / [SchemeRowState] / RewriteStore）。
 */
@Composable
private fun Modifier.observeTapOutsideCards(
    rowState: SchemeRowState,
    onOutsideTap: () -> Unit
): Modifier {
    val bounds = rememberUpdatedState(rowState.cardBoundsInWindow)
    val onOutside = rememberUpdatedState(onOutsideTap)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return this
        .onGloballyPositioned { coordinates = it }
        .pointerInput(rowState) {
            // 位移阈值取 8dp 换算成像素（PointerInputScope 给的 density），
            // 与 detectTapGestures 用来区分"点击 / 拖动"的那一档同数量级：
            // 划横向卡片列表不该被读成"点在外面"。
            val slop = 8f * density
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var latest = down.position
                var dragged = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    latest = change.position
                    if (!dragged && (latest - down.position).getDistance() > slop) dragged = true
                    if (!change.pressed) break
                }
                if (dragged) return@awaitEachGesture
                val node = coordinates ?: return@awaitEachGesture
                val pointInWindow = node.localToWindow(latest)
                val tappedOnACard = bounds.value.values.any { it.contains(pointInWindow) }
                if (!tappedOnACard) onOutside.value()
            }
        }
}

/** 点卡片展开/收起调整区：算出新的展开集合，顺带回答"这次要不要先把那张卡上一次的
 * 改写结果清掉"。
 *
 * 展开态是**一张集合**而不是"当前那一张"：判据是多张卡片可以同时停在调整展开态，
 * 收起其中一张不许顺手关掉别的几张（那正是这一轮要拆掉的旧约束）。
 * 清结果的口径没跟着变——带着 Done/Error 结果的卡重新进入展开态时先清掉，
 * 免得新一轮调整叠在上一次的旧结果上；收起那一步不清。
 * 抽成纯函数只为让 JVM 侧能直接判这两条，展开区本身还是卡片自己画。
 */
internal fun toggleRewriteExpansion(
    expanded: Set<String>,
    identityKey: String,
    rewriteState: RewriteState?
): Pair<Set<String>, Boolean> =
    if (identityKey in expanded) {
        (expanded - identityKey) to false
    } else {
        val clearPreviousResult =
            rewriteState is RewriteState.Done || rewriteState is RewriteState.Error
        (expanded + identityKey) to clearPreviousResult
    }

/** 方案卡片行——一条横向可滑列表，Phase 1 完成就立刻渲染 */
@Composable
private fun SchemeCardsRow(
    schemes: List<Scheme>,
    feedbacks: Map<String, SchemeFeedback>,
    onFeedback: (Scheme, SchemeFeedback) -> Unit,
    onCopyScheme: (Scheme) -> Unit,
    rewriteStates: Map<String, RewriteState> = emptyMap(),
    onRewrite: (SchemeIdentity, RewriteCommand) -> Unit = { _, _ -> },
    onClearRewriteState: (SchemeIdentity) -> Unit = {},
    onCancelRewrite: (SchemeIdentity) -> Unit = {},
    onCustomRewrite: (SchemeIdentity, String) -> Unit = { _, _ -> },
    // 每张卡下方那条"这轮参考了哪些信息？"：null=不渲染。展开的是本轮共享清单，
    // 所以每颗卡传的是同一个 toggle（不许编造"这张回复专门引用了某条"的归属）。
    onReferenceClick: (() -> Unit)? = null,
    // 展开集合 / 落点 / 草稿：住在调用方（LazyRow item 之上），换一整轮整包作废
    rowState: SchemeRowState,
    onInputIntent: (() -> Unit)? = null
) {
        // 方案筛选：全部常驻 + 已赞/已踩按状态出现（用户 2026-10-03 原话："《全部》按钮全程显示，
    // 《已赞》按钮点赞了就显示，目前还没有《已踩》按钮需要加上，然后也是点了踩才显示"）
    var filter by remember(rowState) { mutableStateOf(SchemeFilter.ALL) }
    // 统计口径覆盖**整条合并列表**（八项都在内），不是某一组
    val likedCount = schemes.count { feedbacks[it.identity.key] == SchemeFeedback.LIKED }
    val dislikedCount = schemes.count { feedbacks[it.identity.key] == SchemeFeedback.DISLIKED }

    // 本行现存的项：完成标志清场那一步只认这里在架的身份
    LaunchedEffect(schemes, rowState) {
        val present = schemes.map { it.identity.key }.toSet()
        rowState.knownKeys.keys.filter { it !in present }.forEach { rowState.knownKeys.remove(it) }
        schemes.forEach { rowState.knownKeys[it.identity.key] = true }
    }

    // 哪一档的数量归零，就不许再停在那一档上——防止空页死角。
    // 这一格与"筛选 tab 按数量出现"共用同一条判据，两处不会各说一套。
    LaunchedEffect(likedCount, dislikedCount) {
        if (likedCount == 0 && filter == SchemeFilter.LIKED) filter = SchemeFilter.ALL
        if (dislikedCount == 0 && filter == SchemeFilter.DISLIKED) filter = SchemeFilter.ALL
    }

    val displaySchemes = when (filter) {
        SchemeFilter.ALL -> schemes
        SchemeFilter.LIKED -> schemes.filter { feedbacks[it.identity.key] == SchemeFeedback.LIKED }
        SchemeFilter.DISLIKED -> schemes.filter { feedbacks[it.identity.key] == SchemeFeedback.DISLIKED }
    }

    val scrollState = rememberLazyListState()
    // 去掉当前卡片指示器（原 activeIndex 追踪已移除）
    // 切换筛选时滚动回起点
    LaunchedEffect(filter) {
        scrollState.scrollToItem(0)
    }
    Column {
        // 筛选 Tab 行：**有方案在架就常驻**（旧合同是"点过赞才出现整排"，本轮按原话翻掉）。
        // 已赞/已踩两档各自只在数量 > 0 时出现——踩了马上能筛，不需要先弹窗填原因。
        if (schemes.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SchemeFilterTab(
                    label = "全部 ${schemes.size}",
                    isSelected = filter == SchemeFilter.ALL,
                    onClick = { filter = SchemeFilter.ALL }
                )
                if (likedCount > 0) {
                    SchemeFilterTab(
                        label = "已赞 $likedCount",
                        isSelected = filter == SchemeFilter.LIKED,
                        onClick = { filter = SchemeFilter.LIKED }
                    )
                }
                if (dislikedCount > 0) {
                    SchemeFilterTab(
                        label = stringResource(R.string.scheme_filter_disliked, dislikedCount),
                        isSelected = filter == SchemeFilter.DISLIKED,
                        onClick = { filter = SchemeFilter.DISLIKED }
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }

        if (displaySchemes.isEmpty()) {
            // 已赞筛选下无结果：空状态引导
            // 调研：NN/G 10 Heuristics #1 Visibility of System Status——空状态应说明原因和下一步
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(LoveBrainShape.md)
                    .background(SurfaceInset)
                    .padding(Spacing.xl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "还没有赞过的方案\n点「赞」收藏喜欢的方案",
                    color = TextHint,
                    style = AppTypography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            //修复：入场动画只在首次出现时播放一次（playedKeys 集合）。
            // LazyRow item 离开视口会销毁 remember，若动画状态留在 item 内，
            // 从右向左滑（item 重新组合）会重播动画 → 卡片"闪一下"。提升到外层集合解决。
            val playedKeys = remember(rowState) { mutableStateMapOf<String, Boolean>() }
            LazyRow(
                state = scrollState,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxWidth()
                    .testTag("scheme_cards_row")
            ) {
                items(displaySchemes, key = { it.identity.key }) { scheme ->
                    val identityKey = scheme.identity.key
                    // 入场动效：淡入+上移，逐张交错 60ms（仅首次组合播放）
                    val index = displaySchemes.indexOfFirst { it.identity.key == identityKey }
                    var visible by remember { mutableStateOf(playedKeys[identityKey] ?: false) }
                    LaunchedEffect(Unit) {
                        if (!(playedKeys[identityKey] ?: false)) {
                            playedKeys[identityKey] = true
                            visible = true
                        }
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(250, delayMillis = index * 60)) +
                            slideInVertically(
                                initialOffsetY = { it / 6 },
                                animationSpec = tween(300, delayMillis = index * 60)
                            )
                    ) {
                        SchemeCard(
                            scheme = scheme,
                            feedback = feedbacks[identityKey] ?: SchemeFeedback.NONE,
                            onFeedback = onFeedback,
                            onCopy = onCopyScheme,
                            displayTag = schemeDisplayTag(scheme),
                            rewriteState = rewriteStates[identityKey],
                            onRewrite = onRewrite,
                            onClearRewriteState = onClearRewriteState,
                            onCancelRewrite = onCancelRewrite,
                            isExpanded = rowState.expandedKeys.contains(identityKey),
                            onToggleRewriteExpand = { identity ->
                                val (nextExpanded, clearPreviousResult) = toggleRewriteExpansion(
                                    expanded = rowState.expandedKeys,
                                    identityKey = identity.key,
                                    rewriteState = rewriteStates[identity.key]
                                )
                                if (clearPreviousResult) onClearRewriteState(identity)
                                // 只切当前这一张：别的卡的展开态原样留着
                                rowState.expandedKeys = nextExpanded
                            },
                            onCustomRewrite = onCustomRewrite,
                            customDraft = rowState.customDrafts[identityKey] ?: "",
                            onCustomDraftChange = { text ->
                                rowState.customDrafts[identityKey] = text
                            },
                            isCustomInputOpen = rowState.customOpenKeys[identityKey] == true,
                            onCustomInputOpenChange = { open ->
                                rowState.customOpenKeys[identityKey] = open
                            },
                            onCardBoundsChanged = { rect ->
                                if (rect == null) rowState.cardBoundsInWindow.remove(identityKey)
                                else rowState.cardBoundsInWindow[identityKey] = rect
                            },
                            onReferenceClick = onReferenceClick,
                            onInputIntent = onInputIntent
                        )
                    }
                }
            }
            // 去掉卡片下方的四个点（当前卡片指示器）
        }
    }
}

/**
 * 方案筛选 Tab：选中态高亮 + 点击切换（与 ActionChip 样式统一）
 *
 * 热区与视觉**分两层**：外面那颗 ≥48dp 见方的盒负责"点得中"和语义（role/selected），
 * 里面那颗 28dp 胶囊负责"长什么样"。
 * 以前只有里面那颗，它既画外观又当点击点，本机语义树实量 **46x28dp**
 * （判据是 clickable 边界 ≥48×48）——这一屏第一次被挂进仪器就量到了。
 * 胶囊高度保持不动，所以外观没变，变的是"要点多准才算点到"。
 */
@Composable
private fun SchemeFilterTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "filterTabScale")
    Box(
        modifier = Modifier
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            // clickable 排在 padding 之前：排后面等于自己把热区削掉一圈
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics { selected = isSelected }
            .padding(horizontal = Spacing.sm, vertical = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(ResultDimens.FILTER_TAB_HEIGHT_DP.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(LoveBrainShape.md)
                .background(if (isSelected) PrimaryLight else SurfaceInset, LoveBrainShape.md)
                .border(
                    AppDimens.BORDER_WIDTH_DP.dp,
                    if (isSelected) PrimarySubtle else Border,
                    LoveBrainShape.md
                )
                .padding(horizontal = Spacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = AppTypography.labelMedium,
                color = if (isSelected) PrimaryDark else TextHint,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun CoreLoadingIndicator(
    streamingCoreText: String,
    modifier: Modifier = Modifier
) {
    val phrases = remember {
        listOf(
            "军师正在生成核心回复…",
            "思考四种风格方案…",
            "为你精选最佳话术…"
        )
    }
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1500)
            index = (index + 1) % phrases.size
        }
    }

    // 骨架屏闪烁动画（调研：NN/G Progress Indicators——骨架屏减少感知等待时间）
    val skeletonTransition = rememberInfiniteTransition(label = "skeleton")
    val skeletonAlpha by skeletonTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // 骨架卡片占位：4 张灰色卡片，让用户预知即将出现的内容布局
        // 骨架卡尺寸引用 SchemeCardDimens（166→150 对齐实体卡，消除加载完成瞬间跳变）
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(4) { _ ->
                Box(
                    modifier = Modifier
                        .width(SchemeCardDimens.CARD_WIDTH_DP.dp)
                        .height(SchemeCardDimens.CARD_HEIGHT_DP.dp)
                        .clip(LoveBrainShape.lg)
                ) {
                    // ④ 裁决修复（方案 a）：呼吸 alpha 只作用于独立背景层（无子节点，层 alpha 与色 alpha
                    // 合成恒等），避免外层 graphicsLayer 包子内容造成 s×(s+0.1) 乘算漂移——对齐 GenerateButton 叠层先例
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = skeletonAlpha }
                            .background(Neutral600)
                    )
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        // 骨架标签条
                        Box(
                            modifier = Modifier
                                .width(ResultDimens.SKELETON_TAG_WIDTH_DP.dp)
                                .height(Spacing.xl)
                                .clip(LoveBrainShape.sm)
                                .graphicsLayer { alpha = skeletonAlpha + 0.1f }
                                .background(Neutral500)
                        )
                        Spacer(Modifier.height(Spacing.md))
                        // 骨架文本行
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Spacing.lg)
                                    .clip(LoveBrainShape.sm)
                                    .graphicsLayer { alpha = skeletonAlpha + 0.1f }
                                    .background(Neutral500)
                            )
                            if (it < 2) Spacer(Modifier.height(Spacing.xs))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        // 加载文案 + 流式文本
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(LoveBrainShape.md)
                .background(PrimaryLight)
                .padding(Spacing.lg)
        ) {
            CircularProgressIndicator(
                color = Primary,
                modifier = Modifier.size(Spacing.xl),
                strokeWidth = Spacing.xs
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(text = phrases[index], color = PrimaryDark, style = AppTypography.bodySmall)
        }
        // 显示流式核心文本（逐字显示）
        if (streamingCoreText.isNotBlank()) {
            Spacer(Modifier.height(Spacing.md))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, LoveBrainShape.md)
                    .padding(Spacing.lg)
            ) {
                TypewriterText(
                    text = streamingCoreText,
                    color = TextPrimary,
                    style = AppTypography.bodySmall,
                    maxLines = 6
                )
            }
        }
    }
}

/**
 * 「谁持有 flow 谁渲染宿主」那一句话的落点：调用方交了 flow 就**不**在这里画浮层
 * （面板顶层那一颗已经把遮罩盖满整窗），没交才就地补一颗，免得点了没反应。
 *
 * 两处渲染（流式那一档与成功那一档）共用这一条落盘通路，所以「不对」的自定义输入与
 * 「暂时别提」的时长选择都仍走原来那颗 [MemoryCorrectionFlow]，不在这里另开一套。
 */
@Composable
private fun LocalCorrectionFlowHostIfNeeded(
    hostLocally: Boolean,
    flow: MemoryCorrectionFlow,
    onCorrection: (String, CorrectionAction, String, com.lovebrain.app.model.MuteDuration) -> Unit
) {
    if (!hostLocally) return
    MemoryCorrectionFlowHost(
        flow = flow,
        onMute = { id, duration ->
            onCorrection(id, CorrectionAction.MUTED, "", duration)
        },
        onWrong = { id, text ->
            onCorrection(
                id, CorrectionAction.WRONG, text,
                com.lovebrain.app.model.MuteDuration.UNTIL_RESTORE
            )
        }
    )
}

/**
 * §11.2：八项回复不完整时的一行轻量提示。
 *
 * 指导书要求"用户应能看清是八项中的哪个没生成，不能静默隐藏"。
 * 这里只做展示——已有的候选卡片仍保留，不删不清；用户可点「重新生成」。
 * 与主动发那条 [ProactiveStore.closeRun] 同一族轻量提示语言：
 * 一行小字、Warning 色、不挡操作。
 */
@Composable
private fun ReplyIncompleteNotice(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LoveBrainShape.sm)
            .background(WarningBg)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Text(
            text = text,
            color = Warning,
            style = AppTypography.labelMedium
        )
    }
}
