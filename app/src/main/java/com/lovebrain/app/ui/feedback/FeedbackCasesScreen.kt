package com.lovebrain.app.ui.feedback

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.core.designsystem.LbAsyncState
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.ScreenAction
import com.lovebrain.app.core.designsystem.ScreenState
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.ui.common.ScreenPage
import com.lovebrain.app.viewmodel.SetupViewModel
import kotlinx.coroutines.launch

/*
 * 已踩案例页（页面名与首页入口那颗卡片读同一条资源 `feedback_cases_title`）。
 *
 * ## 这一页的外观从哪儿来
 *
 * 不自己设计：页头、内容边距、白卡、两行摘要、列表间距全部走知识库那一族已经在用的
 * 那一套（`ScreenPage` + `Card(shape = lg, SurfaceCard)` + `Spacing` 那一档），
 * 与知识库卡、供应商行是同一副壳；这一层只填"案例"这件事的内容。
 * 仓库读写仍归 ViewModel，这一页不 new Repository，也不复制别的 Activity 的逻辑过来。
 *
 * ## 这次删掉的三样东西（都是用户点名的废话，不是数据）
 *
 * - 分类 chips 与 `filterCategory` 那一族分支：列表就是全部已踩案例，没有"全部/理解错误"这一排；
 * - Markdown/JSON 切换与大段导出文本预览弹窗：导出只有 JSON 一档，点一下直接进系统保存流程；
 * - 卡片默认那一行诊断字段（空的「【】」、模型名、《待分析》、`上下文模式：full`、
 *   `版本：1.40-re1（release）`、buildType、tokens、费用）。
 *
 * ⚠ 最后一条是**不要展示**，不是删历史数据：`FeedbackCase` 那些字段一个都没动，
 * 旧 `cases.json` 照样读得起来，导出的 JSON 也照样带着原字段（走仓库那一份序列化）。
 * 这一族合同由 `FeedbackCasesSemanticsTest`（展示位）与 `FeedbackExportJsonTest`（数据）两把尺分头钉。
 */

/** 导出只有这一档 MIME；文件名固定 `.json`（，界面上没有可切的格式） */
internal const val FEEDBACK_EXPORT_MIME = "application/json"
internal const val FEEDBACK_EXPORT_FILE_NAME = "feedback_cases.json"

/** 交给 `SetupViewModel.exportFeedback` 的格式实参：只有 JSON 这一支 */
internal const val FEEDBACK_EXPORT_FORMAT = "json"

/**
 * 导出这一条链上"现在该干什么"只判一次。
 *
 * 为什么要单独一颗纯函数：`Success` 这一格以前解成"打开一张预览弹窗，让用户再点一次保存"，
 * 现在解成"直接进系统保存流程"。这一步骤映射写在 `LaunchedEffect` 里没法单独判，
 * 抽出来之后，"弹窗那一档回来了"会让映射本身变样（`WriteToSystem` 不再是 `Success` 的那一支），
 * 用例当场红——而不是靠读源码里有没有某个符号。
 */
internal sealed interface ExportStep {
    /** 正文已经备好，直接交给系统保存面板（没有预览这一格） */
    data class WriteToSystem(val text: String) : ExportStep

    /** 导出没做成：只给一行就地短提示，不起弹窗 */
    data class ShowInlineError(val message: String) : ExportStep

    /** 还没开始或正在算：什么都不做 */
    data object Wait : ExportStep
}

internal fun exportStepOf(state: SetupViewModel.ExportState): ExportStep = when (state) {
    is SetupViewModel.ExportState.Success -> ExportStep.WriteToSystem(state.text)
    is SetupViewModel.ExportState.Error -> ExportStep.ShowInlineError(state.message)
    SetupViewModel.ExportState.Loading, SetupViewModel.ExportState.Idle -> ExportStep.Wait
}

@Composable
fun FeedbackCasesScreen(
    viewModel: SetupViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cases by viewModel.feedbackCases.collectAsStateWithLifecycle()
    val isLoading by viewModel.feedbackLoading.collectAsStateWithLifecycle()
    val loadError by viewModel.feedbackError.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    // 只有"展开的是哪一条"这一颗界面状态需要活过旋转；筛选状态随着 chips 一起没了
    var expandedCaseId by rememberSaveable { mutableStateOf<String?>(null) }
    // CreateDocument 回调只回传 Uri，正文要自己配着（系统面板关闭之后状态就换了）
    var pendingJson by remember { mutableStateOf<String?>(null) }
    // 就地短提示的那一句话（保存失败 / 导出失败都写在这里，不再起 Dialog）
    var inlineError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadFeedbackCases() }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(FEEDBACK_EXPORT_MIME)
    ) { uri ->
        val text = pendingJson
        if (uri == null) {
            // 用户在系统面板里点了取消：不是失败，也不算成功，状态收回去让他能再点一次
            viewModel.resetExportState()
            pendingJson = null
        } else if (text == null) {
            inlineError = context.getString(R.string.feedback_save_failed_no_stream)
            viewModel.resetExportState()
        } else {
            try {
                var wrote = false
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(text.toByteArray())
                    wrote = true
                }
                // openOutputStream() 返回 null 时不假装成功
                if (!wrote) inlineError = context.getString(R.string.feedback_save_failed_no_stream)
            } catch (e: Exception) {
                // 回调不在 composition 里，取资源走 context 那一份
                inlineError = context.getString(
                    R.string.feedback_save_failed,
                    e.message ?: context.getString(R.string.feedback_error_unknown)
                )
            }
            viewModel.resetExportState()
            pendingJson = null
        }
    }

    LaunchedEffect(exportState) {
        when (val step = exportStepOf(exportState)) {
            is ExportStep.WriteToSystem -> {
                // 先配好正文再开系统面板：回调只回传 Uri，写哪一份正文得由这一侧记住
                pendingJson = step.text
                saveLauncher.launch(FEEDBACK_EXPORT_FILE_NAME)
            }
            is ExportStep.ShowInlineError -> {
                inlineError = step.message
                viewModel.resetExportState()
            }
            ExportStep.Wait -> { /* 还没到写文件这一步 */ }
        }
    }

    // 这一层 Box 只当浮层的叠放父节点：不画底色、不加边距。
    // 整屏底色、水平边距与页头都交回 `ScreenPage` 那一族——这一页与知识库两页、捕获范围页
    // 用同一副页头，不再在这一页外面另拼一层顶栏。
    Box(modifier = Modifier.fillMaxSize()) {
        ScreenPage(
            title = stringResource(R.string.feedback_cases_title),
            onBack = onBack,
            trailing = {
                ExportAction(
                    enabled = cases.isNotEmpty(),
                    label = stringResource(R.string.feedback_export_json)
                ) {
                    inlineError = null
                    viewModel.exportFeedback(cases, FEEDBACK_EXPORT_FORMAT)
                }
            }
        ) {
            // 「共多少条」是内容的一行小字，不是页头的第二行；数出来多少条就传多少。
            Text(
                text = pluralStringResource(R.plurals.feedback_case_count, cases.size, cases.size),
                style = AppTypography.labelMedium,
                color = TextHint
            )

            // 就地短提示：一行小字，不弹窗、不另起一张卡。错误由用户点掉下一动作或重试自然清。
            inlineError?.let { message ->
                Text(
                    text = message,
                    style = AppTypography.labelSmall,
                    color = Error,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }

            // 导出进行中：一行小字 + 全站那颗 spinner，不再是一张不可关闭的整屏浮层
            if (exportState is SetupViewModel.ExportState.Loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Spacing.xs)
                ) {
                    CircularProgressIndicator(
                        color = Primary,
                        modifier = Modifier.size(AppDimens.LOADING_SPINNER_SIZE_DP.dp),
                        strokeWidth = Spacing.xs
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        stringResource(R.string.feedback_exporting),
                        style = AppTypography.labelSmall,
                        color = TextHint
                    )
                }
            }

            // 四态只判一次、版式只有一套。判定顺序与知识库页同一副：Loading > Error > Empty > Content
            val screenState: ScreenState<List<FeedbackCase>> = when {
                isLoading -> ScreenState.Loading
                loadError != null -> ScreenState.Error(
                    message = loadError.orEmpty(),
                    retry = ScreenAction(stringResource(R.string.feedback_retry)) {
                        // 读取那颗是 suspend：动作位本身不是，所以在这里起一次
                        scope.launch { viewModel.loadFeedbackCases() }
                    }
                )
                cases.isEmpty() -> ScreenState.Empty(stringResource(R.string.feedback_empty_hint))
                else -> ScreenState.Content(cases)
            }
            if (screenState is ScreenState.Content) {
                LazyColumn(
                    // 列表拿页头剩下的高度：写 fillMaxSize 会让底边整格伸到屏外。
                    // 水平那一档归零——外框已经把整列往里推了一档。
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = Spacing.md, bottom = Spacing.xl
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    items(screenState.value, key = { it.caseId }) { c ->
                        CaseCard(
                            case = c,
                            expanded = expandedCaseId == c.caseId,
                            onToggle = {
                                expandedCaseId = if (expandedCaseId == c.caseId) null else c.caseId
                            }
                        )
                    }
                }
            } else {
                // 另外三格与列表同一档高度：转圈/空/错都居中在"剩下的那一段"里
                LbAsyncState(
                    screenState,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) { }
            }
        }
    }
}

/**
 * 一张已踩案例卡 = 白卡 + 两行摘要（候选正文 + 小号时间），点整卡展开。
 *
 * 默认那一行只有用户真正要看的两样：正文（长文折三行并省略）与时间。
 * 模型名、《待分析》、上下文模式、App 版本与构建类型、tokens、费用都不在这里出现——
 * 字段还在数据里（见文件头那条 ⚠），只是这一屏不再把它们摆在脸面上。
 * 展开那一层只放**真实存在**的上下文：旧数据里用户填过的理由/补充、本轮想法、意图、
 * 点踩当刻冻结的对话。哪一项不存在就不画那一行，绝不摆"暂无XX"那种空字段排。
 */
@Composable
private fun CaseCard(case: FeedbackCase, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        shape = LoveBrainShape.lg,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            // 阴影与知识库那张同一档（2/4 令牌里的上限 4）
            .shadow(AppDimens.ELEVATION_MAX_DP.dp, LoveBrainShape.lg)
            .clip(LoveBrainShape.lg)
            .clickable(role = Role.Button) { onToggle() }
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            Text(
                text = case.candidateReply,
                style = AppTypography.bodyMedium,
                color = TextPrimary,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (case.timestamp.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = case.timestamp,
                    style = AppTypography.labelSmall,
                    color = TextHint,
                    maxLines = 1
                )
            }
            if (expanded) {
                Spacer(Modifier.height(Spacing.md))
                ExpandedBody(case)
            }
        }
    }
}

/** 展开那一层：逐项判"有没有真内容"，空白的一律不画，因此画不出空字段排 */
@Composable
private fun ExpandedBody(case: FeedbackCase) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        if (case.categories.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.feedback_categories,
                    categoryNamesOf(case.categories)
                ),
                style = AppTypography.labelSmall,
                color = TextSecondary
            )
        }
        if (case.reasons.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.feedback_reasons,
                    case.reasons.joinToString(", ")
                ),
                style = AppTypography.labelSmall,
                color = TextSecondary
            )
        }
        if (case.userNote.isNotBlank()) {
            Text(
                stringResource(R.string.feedback_user_note, case.userNote),
                style = AppTypography.labelSmall,
                color = TextSecondary
            )
        }
        if (case.betterVersion.isNotBlank()) {
            Text(
                stringResource(R.string.feedback_better_version, case.betterVersion),
                style = AppTypography.labelSmall,
                color = Primary
            )
        }
        if (case.ideaHint.isNotBlank()) {
            Text(
                stringResource(R.string.feedback_idea_hint, case.ideaHint),
                style = AppTypography.labelSmall,
                color = TextSecondary
            )
        }
        if (case.intentText.isNotBlank()) {
            Text(
                stringResource(R.string.feedback_intent, case.intentText),
                style = AppTypography.labelSmall,
                color = TextSecondary
            )
        }
        if (case.dialogueSnapshot.isNotEmpty()) {
            Text(
                stringResource(R.string.feedback_dialogue_title),
                style = AppTypography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            // 说话人这两个词只有这一份：卡片里那一行与读屏念的是同一句
            val partnerLabel = stringResource(R.string.feedback_speaker_partner)
            val selfLabel = stringResource(R.string.feedback_speaker_me)
            case.dialogueSnapshot.forEach { msg ->
                Text(
                    stringResource(
                        R.string.feedback_dialogue_line,
                        if (msg.speaker == "PARTNER") partnerLabel else selfLabel,
                        msg.text
                    ),
                    style = AppTypography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * 页头尾部那颗导出：形状照知识库卡尾部那一族（实心品牌底，零结果时灰），
 * 只保留"点一下导出 JSON"这一个动作。
 *
 * ⚠ 它**没有**换成 `RowActionButton`/`LbTextAction`：那颗公共件今天还没有
 * "禁用仍留在树上、并报得出 disabled" 这一档，而这一格要的正是在零结果时
 * 读屏听得见的 disabled（第6节第5条 :479）。等上补出 `enabled` 槽再并。
 */
@Composable
private fun ExportAction(enabled: Boolean, label: String, onClick: () -> Unit) {
    val (interaction, scale) = rememberPressScale(0.96f, "exportBtn")
    Box(
        modifier = Modifier
            .heightIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .widthIn(min = AppDimens.TOUCH_TARGET_MIN_DP.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LoveBrainShape.md)
            .background(if (enabled) Primary else SurfaceInset, LoveBrainShape.md)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = AppTypography.labelMedium,
            color = if (enabled) Color.White else TextSecondary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 类别名——只有旧数据里填过类别的案例会在展开那一层用到它。
 * 走资源不写内联中文：英文环境下这一行念英文。
 */
@Composable
private fun categoryDisplayName(cat: com.lovebrain.app.model.FeedbackCategory): String =
    when (cat) {
        com.lovebrain.app.model.FeedbackCategory.UNDERSTANDING_ERROR ->
            stringResource(R.string.feedback_category_understanding_error)
        com.lovebrain.app.model.FeedbackCategory.EXPRESSION_DISLIKE ->
            stringResource(R.string.feedback_category_expression_dislike)
        com.lovebrain.app.model.FeedbackCategory.OTHER ->
            stringResource(R.string.feedback_category_other)
    }

/**
 * 「分类：A、B」那一行的名字表。
 *
 * 为什么不是 `cats.joinToString { categoryDisplayName(it) }`：`joinToString` 的转换 lambda
 * 是普通 inline lambda，**里面调 @Composable 编译不过**。这一层用普通 for 循环把名字取出来，
 * 判据仍只有一份（[categoryDisplayName]）。
 */
@Composable
private fun categoryNamesOf(cats: List<com.lovebrain.app.model.FeedbackCategory>): String {
    val names = ArrayList<String>(cats.size)
    for (cat in cats) names.add(categoryDisplayName(cat))
    return names.joinToString(", ")
}

private const val COLLAPSED_MAX_LINES = 3
