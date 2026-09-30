package com.lovebrain.app.ui.kb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.common.CompactInput
import com.lovebrain.app.ui.common.ScreenPage
import com.lovebrain.app.ui.theme.*

/**
 * 建库向导——「新建知识库」那张五步问卷 + 第 6 步称呼收尾，连它自己的答题状态机接线
 * （[handleOptionClick]：单选/多选上限、Q1 改选清下游、红线触发时清 Q5 隐藏项）。
 *
 * 这一块为什么是**一块**（不是照着行数切的一段）：
 * - **做什么**：把 `domain/OnboardingBank` 的五题画成一屏能答完的问卷，自己管进度、
 *   分支、红线隐藏项与补充说明，收尾交出一份 `OnboardingSchema`。
 * - **谁触发**：`ui/KnowledgeBaseActivity.kt` 的 `KbManagementScreen`——空态那颗动作与
 *   底部那条「新建知识库」大按钮都只把 `showOnboarding` 置真，这一屏才挂起来。
 * - **状态归谁**：全部是这一屏的局部状态（currentStep / branch / answers / myName /
 *   herName / generating / showCustomInput / maxSelectionToast）。Activity 与
 *   `KnowledgeBaseViewModel` 从来没有持有过其中任何一格，跨出去的只有三个回调
 *   （onSkip / onComplete / onCancelGenerating）加一条返回。
 * - **今天有格子吗**：有——`ui/OnboardingScreenSemanticsTest`（逐颗热区下限、角色、
 *   五题答到底）、`ui/OnboardingPrimaryActionTest`（这一屏唯一那颗主动作）。
 *   它们挂的就是这颗 `internal` 入口，搬家后仍挂生产那一颗，只多一行 import。
 *
 * ⚠ 这一笔是**搬家不是重写**：函数体逐字照抄，连 `com.lovebrain.app.domain.…` 那些全限定名
 * 与内联中文文案都原样留着（文案还债归 `UiStringLiteralBudgetTest` 那四栏，不在本块范围）。
 * 拆法照抄邻居 `ui/panel/reply/ResultArea.kt`：一整块职责出一份文件、入口留 `internal`
 * 让仪器能挂生产那一颗、页面私有尺寸表交回本包的 [KbDimens]。
 */
@Composable
internal fun OnboardingScreen(
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onComplete: (com.lovebrain.app.domain.OnboardingSchema) -> Unit,
    onCancelGenerating: () -> Unit
) {
    // v4.2 向导状态：currentStep 1-5 答题，6 称呼收尾
    var currentStep by remember { mutableStateOf(1) }
    var branch by remember { mutableStateOf("") }
    // v4.2: 统一答案对象（Set<Int> + customText），废除 -1 哨兵
    val answers = remember {
        mutableStateMapOf<Int, com.lovebrain.app.domain.OnboardingAnswer>()
    }
    var myName by remember { mutableStateOf("") }
    var herName by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    // 补充说明展开状态
    var showCustomInput by remember { mutableStateOf(false) }
    // 多选上限提示
    var maxSelectionToast by remember { mutableStateOf(false) }

    val totalSteps = 5
    val singleColumn = com.lovebrain.app.ui.onboarding.shouldUseSingleColumn()

    ScreenPage(
        title = "新建知识库",
        onBack = {
            if (generating) {
                onCancelGenerating()
            } else if (currentStep > 1) {
                currentStep--
                showCustomInput = false
            } else {
                onDismiss()
            }
        },
        trailing = {
            TextButton(
                onClick = onSkip,
                enabled = !generating,
                // 实量 **72x40dp**：M3 那颗"至少 48dp"是 `minimumInteractiveContainer`
                // 装饰（挂在另一个节点上），带 `role=Button` 的这一颗自己只有 40 高（:531）。
                // 同形缺陷已在表单「显示/隐藏」与捕获范围页各撞过一次——别再相信框架管好了。
                modifier = Modifier.heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            ) {
                Text(
                    "建空档案",
                    color = if (generating) TextHint else TextSecondary,
                    style = AppTypography.labelLarge
                )
            }
        }
    ) {
        // ── 进度条 ──
        val progressStep = if (currentStep <= totalSteps) currentStep else totalSteps
        Text(
            "第 $progressStep 步 · 共 $totalSteps 步",
            style = AppTypography.labelSmall,
            color = TextHint
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(KbDimens.PROGRESS_BAR_HEIGHT_DP.dp)
                .clip(LoveBrainShape.full)
                .background(SurfaceInset)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressStep.toFloat() / totalSteps)
                    .height(KbDimens.PROGRESS_BAR_HEIGHT_DP.dp)
                    .clip(LoveBrainShape.full)
                    .background(Primary)
            )
        }
        Spacer(modifier = Modifier.height(Spacing.lg))

        // ── 内容区 ──
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            if (currentStep <= totalSteps) {
                val question = if (currentStep == 1) {
                    com.lovebrain.app.domain.OnboardingBank.q1
                } else {
                    com.lovebrain.app.domain.OnboardingBank.question(currentStep, branch)
                }

                // 红线检测
                val redline = com.lovebrain.app.domain.OnboardingStateMachine
                    .isRedlineTriggered(answers, branch)
                val hiddenIndices = if (redline && currentStep == 5) {
                    com.lovebrain.app.domain.OnboardingStateMachine.hiddenOptionIndices(true)
                } else emptySet()

                // 红线提示
                if (redline && currentStep == 5) {
                    Text(
                        "军师检测到你目前的情况更适合止损和自我调整，暂时不提供挽回建议。",
                        color = Error,
                        style = AppTypography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                }

                // ── 题目标题（最强层级）──
                Text(
                    question.title,
                    style = AppTypography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                // ── 规则说明（次弱层级）──
                val currentAnswer = answers[currentStep]
                    ?: com.lovebrain.app.domain.OnboardingAnswer()
                if (question.selectionMode == com.lovebrain.app.domain.SelectionMode.MULTIPLE) {
                    val max = question.maxSelections ?: 2
                    val selectedCount = currentAnswer.selectedIndices.size
                    val hintText = if (selectedCount == 0) {
                        "可多选，最多 $max 项"
                    } else {
                        "已选 $selectedCount/$max"
                    }
                    Text(
                        hintText,
                        style = AppTypography.labelMedium,
                        color = if (selectedCount >= max) PrimaryDark else TextHint
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))

                // ── 选项区 ──
                val visibleOptions = question.options.mapIndexed { idx, opt -> idx to opt }
                    .filter { (idx, _) -> idx !in hiddenIndices }

                if (singleColumn) {
                    // 大字体 / 窄屏：单列
                    visibleOptions.forEach { (idx, opt) ->
                        com.lovebrain.app.ui.onboarding.OnboardingOptionCard(
                            text = opt.text,
                            selected = idx in currentAnswer.selectedIndices,
                            selectionMode = question.selectionMode,
                            enabled = !generating,
                            onClick = {
                                maxSelectionToast = false
                                handleOptionClick(
                                    question = question,
                                    index = idx,
                                    currentStep = currentStep,
                                    answers = answers,
                                    branch = branch,
                                    onBranchChange = { branch = it },
                                    onMaxReached = { maxSelectionToast = true },
                                    generating = generating
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            useSingleColumn = true
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))
                    }
                } else {
                    // 标准：双列 chunked(2)
                    visibleOptions.chunked(2).forEach { pair ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            pair.forEach { (idx, opt) ->
                                com.lovebrain.app.ui.onboarding.OnboardingOptionCard(
                                    text = opt.text,
                                    selected = idx in currentAnswer.selectedIndices,
                                    selectionMode = question.selectionMode,
                                    enabled = !generating,
                                    onClick = {
                                        maxSelectionToast = false
                                        handleOptionClick(
                                            question = question,
                                            index = idx,
                                            currentStep = currentStep,
                                            answers = answers,
                                            branch = branch,
                                            onBranchChange = { branch = it },
                                            onMaxReached = { maxSelectionToast = true },
                                            generating = generating
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // ── 补充说明入口（仅 Q2-Q5）──
                if (currentStep in 2..totalSteps) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    // 弱一级入口：点击展开/收起
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            // 实量 **87x22dp、role=无**。这一颗是从第 2 步才出现的，
                            // 所以首屏那一档扫不到它——只测首屏会以为这一屏很干净。
                            // `heightIn/widthIn` 排在 `clickable` **之前**、`padding` 之后：
                            // 反过来写就是自己把热区削一圈（表单那颗「＋ 添加模型」同一修法）。
                            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                            .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = { showCustomInput = !showCustomInput }
                            )
                            .padding(vertical = Spacing.xs)
                    ) {
                        Text(
                            if (showCustomInput) "− 收起补充" else "＋ 补充其他情况",
                            style = AppTypography.bodySmall,
                            color = Primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // 展开后的输入框
                    if (showCustomInput) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        val existingCustom = answers[currentStep]?.customText ?: ""
                        var customText by remember(currentStep, existingCustom) {
                            mutableStateOf(existingCustom)
                        }
                        CompactInput(
                            value = customText,
                            onValueChange = {
                                customText = it.take(100)
                                val cur = answers[currentStep]
                                    ?: com.lovebrain.app.domain.OnboardingAnswer()
                                answers[currentStep] = cur.copy(customText = customText)
                            },
                            placeholder = "简单说说你的情况，100 字以内"
                        )
                    }
                }

                // 多选上限提示
                if (maxSelectionToast) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        "最多选择 ${question.maxSelections ?: 2} 项",
                        style = AppTypography.labelSmall,
                        color = Error
                    )
                }
            } else {
                // Step6：称呼输入（选填）
                Text("选填（不填也能建，之后能改）", style = AppTypography.bodySmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(Spacing.xs))
                CompactInput(
                    value = myName,
                    onValueChange = { myName = it },
                    placeholder = "你的称呼"
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                CompactInput(
                    value = herName,
                    onValueChange = { herName = it },
                    placeholder = "她的称呼"
                )
            }
        }

        // ── 底部按钮 ──
        if (generating) {
            Button(
                onClick = { onCancelGenerating() },
                enabled = true,
                colors = ButtonDefaults.buttonColors(containerColor = TextHint),
                shape = LoveBrainShape.md,
                modifier = Modifier.fillMaxWidth().height(KbDimens.PRIMARY_ACTION_HEIGHT_DP.dp)
            ) {
                CircularProgressIndicator(
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .height(KbDimens.ONBOARDING_SPINNER_SIZE_DP.dp)
                        .width(KbDimens.ONBOARDING_SPINNER_SIZE_DP.dp),
                    strokeWidth = Spacing.xs
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Text("点击取消（军师还在生成画像…）", style = AppTypography.titleMedium)
            }
        } else if (currentStep > totalSteps) {
            // Step6：完成按钮
            // 同一颗槽位的另一档（`currentStep > totalSteps`），实量 **312x48dp role=Button** ⇒ 达标。
            // 归 `LbPrimaryButton` 之后它才**能表达禁用与进行中**：原来 `enabled = true` 写死，
            // 而同一位置在 generating 那一档会换成另一颗手写 Button——三档三种实现。
            LbPrimaryButton(
                state = LbButtonState.Idle,
                label = stringResource(R.string.kb_finish_profile),
                onClick = {
                    generating = true
                    val schema = com.lovebrain.app.domain.OnboardingSchemaBuilder.build(
                        answers.toMap(), myName.trim(), herName.trim()
                    )
                    onComplete(schema)
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // 答题阶段：下一步按钮（不自动跳页）
            val question = if (currentStep == 1) {
                com.lovebrain.app.domain.OnboardingBank.q1
            } else {
                com.lovebrain.app.domain.OnboardingBank.question(currentStep, branch)
            }
            val currentAnswer = answers[currentStep]
                ?: com.lovebrain.app.domain.OnboardingAnswer()
            val canProceed = currentAnswer.isAnswered(question)

            // §6.1 :479——这一屏的唯一主动作归 `LbPrimaryButton`。
            // 搬之前先量：实量 **312x48dp、role=Button、没答时报 disabled**，三项都达标 ⇒
            // 又是**归所有者，不是修缺陷**（首页、知识库「新建」、表单「保存」同一结论第四次）。
            // 搬的收益还是"两张表管一件事"收成一旋钮：原来 `enabled = canProceed` 与
            // `containerColor = if (canProceed) Primary else SurfaceInset`
            // 与 `color = if (canProceed) White else TextHint` **三处**各判一遍同一个条件。
            LbPrimaryButton(
                state = if (canProceed) LbButtonState.Idle else LbButtonState.Disabled,
                label = stringResource(R.string.kb_next_step),
                onClick = {
                    // 不用再判一次 `canProceed`：`Disabled` 那一档的 `clickable(enabled = false)`
                    // 已经吃不进点击了。原来这里是"条件写在三处"，收成一颗旋钮之后
                    // 再留一份判断就是我自己反对的那种写法。
                    if (currentStep < totalSteps) {
                        currentStep++
                        showCustomInput = false
                        maxSelectionToast = false
                    } else {
                        currentStep = totalSteps + 1
                        showCustomInput = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 处理选项点击：toggle 选中状态、Q1 改选清理后续、红线变化清理 Q5 隐藏项。
 */
private fun handleOptionClick(
    question: com.lovebrain.app.domain.OnboardingQuestion,
    index: Int,
    currentStep: Int,
    answers: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, com.lovebrain.app.domain.OnboardingAnswer>,
    branch: String,
    onBranchChange: (String) -> Unit,
    onMaxReached: () -> Unit,
    generating: Boolean
) {
    if (generating) return

    val currentAnswer = answers[currentStep]
        ?: com.lovebrain.app.domain.OnboardingAnswer()

        // 记录红线变化前状态
    val wasRedline = com.lovebrain.app.domain.OnboardingStateMachine
        .isRedlineTriggered(answers, branch)

    // Q1 改选 → 分支变化 → 清空后续
    if (currentStep == 1) {
        val newBranch = com.lovebrain.app.domain.OnboardingStateMachine
            .branchFromQ1(index)
        if (newBranch != branch) {
            onBranchChange(newBranch)
            com.lovebrain.app.domain.OnboardingStateMachine.clearDownstreamAnswers(answers)
        }
        // Q1 是 SINGLE，直接设为唯一选择
        answers[currentStep] = com.lovebrain.app.domain.OnboardingAnswer(
            selectedIndices = setOf(index),
            customText = currentAnswer.customText
        )
    } else {
        // Q2-Q5: 走 toggle 逻辑
        val newAnswer = com.lovebrain.app.domain.OnboardingStateMachine
            .toggleOption(question, currentAnswer, index)
        // 只有 MULTIPLE 且集合完全没变且点的是新项 → 才是因上限被拒
        if (
            question.selectionMode == com.lovebrain.app.domain.SelectionMode.MULTIPLE &&
            newAnswer.selectedIndices == currentAnswer.selectedIndices &&
            index !in currentAnswer.selectedIndices
        ) {
            onMaxReached()
        } else {
            // 清除上限提示
        }
        // 保留 customText
        answers[currentStep] = newAnswer.copy(customText = currentAnswer.customText)
    }

    // 红线变化检测
    val nowRedline = com.lovebrain.app.domain.OnboardingStateMachine
        .isRedlineTriggered(answers, branch)
    if (!wasRedline && nowRedline) {
        // 新触发红线 → 清理 Q5 中的隐藏项
        val hiddenIndices = com.lovebrain.app.domain.OnboardingStateMachine
            .hiddenOptionIndices(true)
        com.lovebrain.app.domain.OnboardingStateMachine.cleanHiddenFromQ5(
            answers, hiddenIndices
        )
    }
}
