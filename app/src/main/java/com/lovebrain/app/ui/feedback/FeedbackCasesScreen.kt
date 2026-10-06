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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lovebrain.app.R
import com.lovebrain.app.core.designsystem.AppDimens
import com.lovebrain.app.core.designsystem.AppTypography
import com.lovebrain.app.core.designsystem.Error
import com.lovebrain.app.core.designsystem.rememberPressScale
import com.lovebrain.app.core.designsystem.LbAsyncState
import com.lovebrain.app.core.designsystem.LbListCard
import com.lovebrain.app.core.designsystem.LoveBrainShape
import com.lovebrain.app.core.designsystem.Primary
import com.lovebrain.app.core.designsystem.ScreenAction
import com.lovebrain.app.core.designsystem.ScreenState
import com.lovebrain.app.core.designsystem.Spacing
import com.lovebrain.app.core.designsystem.SurfaceInset
import com.lovebrain.app.core.designsystem.TextHint
import com.lovebrain.app.core.designsystem.TextPrimary
import com.lovebrain.app.core.designsystem.TextSecondary
import com.lovebrain.app.core.designsystem.lbMetaLine
import com.lovebrain.app.model.FeedbackCase
import com.lovebrain.app.ui.common.ScreenPage
import com.lovebrain.app.viewmodel.SetupViewModel
import kotlinx.coroutines.launch

/*
 * 已踩案例页（页面名与首页入口那颗卡片读同一条资源 `feedback_cases_title`）。
 *
 * ## 这一页的外观从哪儿来
 *
 * **抄知识库一级页那张卡的布局语法，不抄它的内容。**母版那一套（`KnowledgeBaseActivity.kt`）
 * 是"白卡 + 一行 `titleMedium` SemiBold 单行标题 + 一行 `labelSmall` 元信息 + 行内 `RowCapsule` 动作"，
 * 今天这一页全部走同一颗公共件 `core/designsystem/LbListCard.kt` 拿：卡底、字阶、行数上限、
 * 动作写法与槽位间距都由那一颗持有，这一层只交内容（首句 / 余文 / 时间 / 展开层）。
 * 于是这张卡与知识库卡是**同一个壳、两种业务身份**：这里不会出现"阶段/编辑画像"，
 * 知识库那边也不会出现"导出 JSON"（`重做页通过判据` 的 M-10 就是逐区核对这一条）。
 *
 * 内容优先级按母版重排（D1 §③-9 的 A4 那一条）：
 * - **标题槽** = 回复的**首句**（[splitFirstSentence]），单行截断；旧写法是把整段正文当标题；
 * - **摘要槽** = 首句之后的余文，最多两行；折叠时能看到的是"这一条被踩的话开头 + 一点续文"；
 * - **元信息槽** = 时间，与母版同一档 `labelSmall` 10、同一颗 `lbMetaLine` 合并器（多条才用 `｜`）；
 * - **展开层** = 全文 + 用户真填过的上下文：六条诊断字段今天合成**一行**（分隔符仍是母版那一个），
 *   对话快照仍逐行——旧写法是七条 `labelSmall` 平排，那是调试面板不是卡片。
 *
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
 * 本轮重排版式**没有**把上面任何一样加回来：`待分析` 与模型那一族仍然不许上屏，
 * 所以状态槽在这一页交的是 `null`（见 [CaseCard] 那段），不是"忘了填"。
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
                // 空态也给出下一步那一颗真实动作（重做页通过判据 M-9：三页空态不许三种写法）。
                // 为什么是"重试"而不是"去做一条"：这一页没有跳到回复面板的入口（那条路归首页），
                // 而案例库真会被悬浮窗在别处写进新记录——重新读一次库是这一屏**做得到**的那件事，
                // 走的还是同一个 `loadFeedbackCases()`，不起第二份状态源。文案复用盘上已有的那一句。
                cases.isEmpty() -> ScreenState.Empty(
                    message = stringResource(R.string.feedback_empty_hint),
                    action = ScreenAction(stringResource(R.string.feedback_retry)) {
                        scope.launch { viewModel.loadFeedbackCases() }
                    }
                )
                else -> ScreenState.Content(cases)
            }
            if (screenState is ScreenState.Content) {
                LazyColumn(
                    // 列表拿页头剩下的高度：写 fillMaxSize 会让底边整格伸到屏外。
                    // 水平那一档归零——外框已经把整列往里推了一档，本页**不许**再自加水平 padding
                    // （第二个边距主人会被 `ScreenScaffoldFrameTest` 量成"这一页还在自己写外框"）。
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    // 基线 §3.3 的目标档：列表间距与卡内 12（`Spacing.lg`）、页尾 16（`Spacing.xl`）。
                    // ⚠ 这一页能自证的只有列表间距；水平边距那一档 24→16 归 `LbScreenScaffold`
                    // （M1 那层还没落，见 impl-M3b 台账的"待 M1 归一后回查"）。
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = Spacing.lg, bottom = Spacing.xl
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
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
 * 一张已踩案例卡 = 母版那颗 [LbListCard]，页面只交内容，不再自绘卡底。
 *
 * 三个槽在这一页是**空**的，每一件都有理由，不是漏填：
 * - `status = null`：状态槽要说的是"这一条现在到哪一步"，而案例的 `status` 只有
 *   `PENDING` 一种真实生产者（点踩落盘写进去的恒是它），界面上说得出"待分析"是本轮
 *   明令不许加回的那一句（文件头 ⚠）。没有真实状态就不许编一个状态；
 * - `actions = emptyList()`：这一页没有单条动作的真源（导出是整库一档、归页头尾部档），
 *   而设计系统的动作行不许画一颗假的；
 * - `meta` 只有时间一段：分隔符仍由 `lbMetaLine` 持有，将来有第二段时不用改版式。
 *
 * 整卡可点 = 展开/收起（与旧写法同一处操作，读屏听到的仍是 `Role.Button`，
 * 由公共件那一处声明，页面拿不到角色旋钮）。展开层走 [LbListCard] 的 `detail` 槽，
 * 画在同一张卡里——留在卡外会让那张白卡裂成两层底。
 * 而 [hasCaseDetail] 为假时**根本不交那一槽**：没有内容的案例点下去不许长出一段空白
 * （旧写法靠"展开层逐行判空"做到同一件事，现在把它提到槽位这一层，卡高一寸没动）。
 */
@Composable
private fun CaseCard(case: FeedbackCase, expanded: Boolean, onToggle: () -> Unit) {
    val (headline, rest) = splitFirstSentence(case.candidateReply)
    LbListCard(
        title = headline,
        summary = rest,
        meta = listOf(case.timestamp),
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        detail = if (expanded && hasCaseDetail(case, rest)) {
            @Composable { CaseDetail(case, rest) }
        } else {
            null
        }
    )
}

/**
 * 这一条案例**有没有**能展开给人看的东西：全文（首句之外的那一截）或任何一项真实上下文。
 *
 * 单独成一顆纯函数，是为了让"空白案例点下去不长东西"这件事能被直接判：
 * 反例是把这一格写成恒真（`detail` 永远交出去）——那一格会在公共件里留下一段
 * `Spacer` 与一个空容器，卡变高了而屏上没有多一个字，正是 `a case with no extra data…`
 * 那一格要挡的坏法。
 */
internal fun hasCaseDetail(case: FeedbackCase, rest: String): Boolean =
    rest.isNotBlank() || case.categories.isNotEmpty() || case.reasons.isNotEmpty() ||
        case.userNote.isNotBlank() || case.betterVersion.isNotBlank() ||
        case.ideaHint.isNotBlank() || case.intentText.isNotBlank() ||
        case.dialogueSnapshot.isNotEmpty()

/**
 * 展开那一层：逐项判"有没有真内容"，空白的一律不画，因此画不出空字段排。
 *
 * 与旧写法的差别只有一处判据级的：六条诊断字段（分类 / 原因 / 补充 / 期望版本 / 本轮想法 / 意图）
 * 以前是六个各占一行的 `Text`，现在合成**一行**、用母版那一个 `｜` 分隔（`lbMetaLine` 是唯一主人）。
 * 对话快照仍然逐行：那是"谁说了什么"的次序，压成一行就读不出谁先谁后了。
 * 全文那一行只在折叠层没排完的时候才画——首句能代表整句（一句而已）时不重复一遍，
 * 这也挡住了"没有上下文的案例展开后长出一排空字段"那一种坏形状
 * （`FeedbackCasesSemanticsTest` 的 `a case with no extra data renders no empty field rows`）。
 */
@Composable
private fun CaseDetail(case: FeedbackCase, rest: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        if (rest.isNotBlank()) {
            Text(
                text = case.candidateReply,
                style = AppTypography.bodyMedium,
                color = TextPrimary
            )
        }
        val contextLine = lbMetaLine(
            listOfNotNull(
                if (case.categories.isNotEmpty()) {
                    stringResource(R.string.feedback_categories, categoryNamesOf(case.categories))
                } else {
                    null
                },
                if (case.reasons.isNotEmpty()) {
                    stringResource(R.string.feedback_reasons, case.reasons.joinToString(", "))
                } else {
                    null
                },
                if (case.userNote.isNotBlank()) {
                    stringResource(R.string.feedback_user_note, case.userNote)
                } else {
                    null
                },
                if (case.betterVersion.isNotBlank()) {
                    stringResource(R.string.feedback_better_version, case.betterVersion)
                } else {
                    null
                },
                if (case.ideaHint.isNotBlank()) {
                    stringResource(R.string.feedback_idea_hint, case.ideaHint)
                } else {
                    null
                },
                if (case.intentText.isNotBlank()) {
                    stringResource(R.string.feedback_intent, case.intentText)
                } else {
                    null
                }
            )
        )
        if (contextLine.isNotBlank()) {
            // 一行元信息：字档与母版那一行同一档（`labelSmall` 10），但这一行**不截断**——
            // 展开层的用途就是"看全"，裁掉半句理由等于用户自己也不知道当初为什么踩。
            Text(
                text = contextLine,
                style = AppTypography.labelSmall,
                color = TextSecondary
            )
        }
        if (case.dialogueSnapshot.isNotEmpty()) {
            Text(
                text = stringResource(R.string.feedback_dialogue_title),
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
 * 句末标点：中英两套 + 换行。换行算句末，但**不把换行符本身**留在标题里
 * （标题槽是单行，留着一个 `\n` 只是让读屏念出一段空白）。
 */
internal val SENTENCE_TERMINATORS = charArrayOf('。', '！', '？', '!', '?', '\n')

/**
 * 「回复首句当标题」那一步的唯一实现。
 *
 * 交回 `首句 to 余文`：
 * - 只有一句（或压根没有句末标点）⇒ `首句 = 整段`、`余文 = ""`，摘要槽不画，
 *   于是屏上那一条完整的回复仍然**整条在语义树里**（`FeedbackCasesSemanticsTest` 的夹具正格）；
 * - 多句 ⇒ 标题只有第一句，其余进摘要（两行），全文走展开层那一行。
 *
 * 两头都 `trim()`：卡片标题带着一串空白的话，母版那一行的对齐就白买了。
 * 纯函数、不判 composable，是为了能被直接判（[splitFirstSentence] 的反例见那颗测试）。
 */
internal fun splitFirstSentence(text: String): Pair<String, String> {
    val source = text.trim()
    if (source.isEmpty()) return "" to ""
    val index = source.indexOfFirst { it in SENTENCE_TERMINATORS }
    if (index < 0) return source to ""
    val cut = if (source[index] == '\n') index else index + 1
    return source.substring(0, cut).trim() to source.substring(cut).trim()
}

/**
 * 页头尾部那颗导出：形状照知识库卡尾部那一族（实心品牌底，零结果时灰），
 * 只保留"点一下导出 JSON"这一个动作。
 *
 * ⚠ **缺口，不是选择**：这一颗该并进设计系统的动作档，但今天没有那颗能装它的档——
 * `LbTextAction(label = …)` 那一支把 `enabled` 写死成 `true`（`LbTextAction.kt:302-315`），
 * 而这一格要的正是在零结果时"仍在树上、并且报得出 disabled"（`FeedbackCasesSemanticsTest`
 * 的 `the export action stays visible but reports itself disabled when the list is empty`）。
 * 图标档那两支有 `enabled` 旋钮，可它是字形档、装不下一句"导出 JSON"。
 * ⇒ 等的就是具名那一档：**文字动作的 `enabled` 槽**（`LbTextAction(label, onClick, enabled)`，
 * 禁用仍留在树上报 disabled）；补齐后这一颗整块删掉、改 `RowActionButton`/`LbTextAction` 调用，
 * 异形与品牌底两本账同时销行。需求已写进 `handoffs/2026-10-05-M3b-公共件缺口.md`。
 * 本轮**不**新建第四种动作写法：宁可留一处已登记的自绘，也不长第二套形状。
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
