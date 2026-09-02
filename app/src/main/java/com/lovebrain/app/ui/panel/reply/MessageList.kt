package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lovebrain.app.R
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.ui.panel.rememberPressScale
import com.lovebrain.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 消息列表内部尺寸常量（ 令牌化：数值不变，仅外放命名） */
private object MessageDimens {
    const val ROLE_CHIP_HPAD_DP = 6    // 角色 chip 水平内边距
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageList(
    messages: List<ChatMessage>,
    editingIndex: Int,
    onReorder: (Int, Int) -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    // 主人重构：空态蓝字 = 主动发入口（点击召唤/再点关闭）
    onEmptyAction: (() -> Unit)? = null,
    proactiveActive: Boolean = false
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    // 长消息折叠状态：记录哪些 index 的消息已展开
    // 调研依据：NN/G 10 Heuristics #6 Recognition rather than recall——长消息默认折叠关键信息，
    // 用户按需展开查看全文，减少视觉噪音
    val expandedMessages = remember { mutableStateMapOf<Int, Boolean>() }

    // 删除动画：× 点击 → 先播放退场（200ms 向右滑出+淡出），动画结束后再真正移除
    // 用按消息ID的 Map 支持快速连续删除多个 item（旧方案用单个 index，连续点击会取消前一个的计时器）
    val deletingIds = remember { mutableStateMapOf<String, Boolean>() }

// 自动滚动到最新消息（消息数量变化时触发）
// 只在用户已接近底部时才自动滚动，避免翻看历史消息时被强制拉回底部
LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
        val layout = listState.layoutInfo
        val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
        val isNearBottom = lastVisible >= messages.lastIndex - 1
        if (isNearBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
}

    if (messages.isEmpty()) {
        // 主人纠正（2026-08-30）：空态包高+垂直居中，去掉 20dp 大边距（原固定高顶部对齐致想法行上方大片空白）
        Column(
            modifier = modifier.fillMaxWidth().padding(vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ✦ 空态图标：素材已预处理（贴边白底转透明+裁边，白圈根因根治），直接呈现不再裁圆；
            //   tint=Unspecified 必须：M3 Icon 默认按内容色染色，位图彩图会被整体染黑（全黑根因）
            Icon(
                painter = painterResource(R.drawable.ic_empty_reply),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(AppDimens.EMPTY_ICON_CONTAINER_DP.dp)
            )
            Spacer(Modifier.height(Spacing.md))
            // 主人重构：空态蓝字 = 主动发入口——点击召唤主动发，再点关闭
            Text(
                text = if (proactiveActive) "主动发模式已开启，点击关闭" else "还没有聊天记录，点这里主动发一条",
                color = Primary,
                style = AppTypography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable { onEmptyAction?.invoke() }
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
            // ：空态副文案删除（捕获机制无需在空态重复提示），只保留主文案一行
            // 需求#33：《手动输入》按钮完全去掉（输入区已有输入框，无需空状态冗余入口）
            // ：原空态卡片内的控制条插槽整体删除（三件套常驻生成行，）
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceInset, LoveBrainShape.lg)
            .padding(Spacing.md)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val hitItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                            offset.y >= item.offset - 8 && offset.y < item.offset + item.size + 8
                        }
                        if (hitItem != null) {
                            draggedIndex = hitItem.index
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val from = draggedIndex ?: return@detectDragGesturesAfterLongPress

                        val layoutInfo = listState.layoutInfo
                        val visibleItems = layoutInfo.visibleItemsInfo
                        val totalCount = layoutInfo.totalItemsCount
                        val fingerY = change.position.y

                        val targetIndex = if (visibleItems.isNotEmpty()) {
                            val candidate = visibleItems.minByOrNull { item ->
                                val center = item.offset + item.size / 2f
                                abs(fingerY - center)
                            }
                            when {
                                candidate != null -> {
                                    val center = candidate.offset + candidate.size / 2f
                                    if (candidate.index < from && fingerY < center) candidate.index
                                    else if (candidate.index > from && fingerY > center) candidate.index
                                    else from
                                }
                                fingerY < visibleItems.first().offset -> 0
                                else -> totalCount - 1
                            }
                        } else from

                        val clampedTarget = targetIndex.coerceIn(0, (totalCount - 1).coerceAtLeast(0))
                        if (clampedTarget != from) {
                            onReorder(from, clampedTarget)
                            draggedIndex = clampedTarget
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }

                        val edgeZone = 80 * density
                        val viewportEnd = layoutInfo.viewportEndOffset
                        val viewportStart = layoutInfo.viewportStartOffset
                        val scrollSpeed = when {
                            fingerY > viewportEnd - edgeZone -> {
                                val ratio = (fingerY - (viewportEnd - edgeZone)) / edgeZone
                                (ratio * 30f + 5f).coerceAtMost(40f)
                            }
                            fingerY < viewportStart + edgeZone -> {
                                val ratio = ((viewportStart + edgeZone) - fingerY) / edgeZone
                                -((ratio * 30f + 5f).coerceAtMost(40f))
                            }
                            else -> 0f
                        }
                        if (scrollSpeed != 0f) {
                            scope.launch { listState.scrollBy(scrollSpeed) }
                        }
                    },
                    onDragEnd = { draggedIndex = null },
                    onDragCancel = { draggedIndex = null }
                )
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        itemsIndexed(
            items = messages,
            key = { _, msg -> msg.id }
        ) { index, msg ->
            // 删除退场动画：向右滑出 + 淡出（避免 shrinkVertically 高度收缩导致的列表跳动）
            val isDeleting = deletingIds[msg.id] == true
            LaunchedEffect(isDeleting) {
                if (isDeleting) {
                    delay(200)
                    deletingIds.remove(msg.id)
                    onDelete(msg.id)
                }
            }
            AnimatedVisibility(
                visible = !isDeleting,
                exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) + fadeOut(tween(200))
            ) {
            val isDragged = draggedIndex == index
            val isEditing = index == editingIndex

            val scale by animateFloatAsState(
                targetValue = if (isDragged) 1.02f else 1f,
                label = "dragScale"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragged) 1f else 0f)
                    .scale(scale)
                    .animateItemPlacement()
                    .then(
                        // ：拖拽态阴影 6dp 超限 → 收敛至上限 4dp（ELEVATION_MAX_DP）
                        if (isDragged) Modifier.shadow(AppDimens.ELEVATION_MAX_DP.dp, LoveBrainShape.md)
                        else Modifier
                    )
                    .background(
                        when {
                            isDragged -> PrimaryLight
                            isEditing -> PrimaryLight
                            else -> SurfaceCard
                        },
                        LoveBrainShape.md
                    )
                    // 需求#4：单击消息 → 选中并在上方输入框编辑（长按拖拽不受影响）
                    .clickable { onEdit(index) }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            ) {
                // 角色标签：彩色 chip（颜色编码让用户一眼识别角色；：想法 = 琥珀色第三种）
                Box(
                    modifier = Modifier
                        .clip(LoveBrainShape.sm)
                        .background(
                            when (msg.role) {
                                ChatMessage.Role.HER -> PrimaryLight
                                ChatMessage.Role.IDEA -> WarningBg
                                else -> Neutral600
                            },
                            LoveBrainShape.sm
                        )
                        .border(
                            AppDimens.BORDER_WIDTH_DP.dp,
                            when (msg.role) {
                                ChatMessage.Role.HER -> PrimarySubtle
                                ChatMessage.Role.IDEA -> WarningBg
                                else -> Border
                            },
                            LoveBrainShape.sm
                        )
                        .padding(horizontal = MessageDimens.ROLE_CHIP_HPAD_DP.dp, vertical = Spacing.xs)
                ) {
                    Text(
                        text = msg.role.label,
                        color = when (msg.role) {
                            ChatMessage.Role.HER -> PrimaryDark
                            ChatMessage.Role.IDEA -> Warning
                            else -> TextSecondary
                        },
                        style = AppTypography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(Spacing.sm))

                // 长消息折叠：超过阈值时默认折叠，点击展开/收起
                // 调研依据：NN/G Progressive Disclosure——长文本先展示摘要，按需展开详情
                val COLLAPSE_THRESHOLD = 80
                val isExpanded = expandedMessages[index] == true
                val shouldFold = msg.content.length > COLLAPSE_THRESHOLD
                val displayText = if (shouldFold && !isExpanded) {
                    msg.content.take(COLLAPSE_THRESHOLD)
                } else {
                    msg.content
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayText,
                        color = TextPrimary,
                        style = AppTypography.bodyMedium,
                        maxLines = if (shouldFold && !isExpanded) 2 else Int.MAX_VALUE,
                        overflow = if (shouldFold && !isExpanded) TextOverflow.Ellipsis else TextOverflow.Visible
                    )
                    if (shouldFold) {
                        Text(
                            text = if (isExpanded) "收起" else "展开",
                            color = Primary,
                            style = AppTypography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                // ：间距并入 clickable 覆盖区（先声明为外层），热区 16+8=24dp
                                .clickable {
                                    expandedMessages[index] = !(expandedMessages[index] ?: false)
                                }
                                .padding(vertical = Spacing.sm)
                        )
                    }
                }

                if (!isDragged) {
                    // 需求#3：× = 直接删除（换 ic_close 图标统一语言；走删除动画，需求1.2）
                    // ：删除盒补按压反馈（复用标准件 0.96 scale + 120ms）
                    // （A1 复制按钮已按主人对齐第 4 条删除：只保留 ❌，全 App 禁 Toast）
                    val (deleteInteraction, deleteScale) = rememberPressScale(0.96f, "deleteScale")
                    Box(
                        modifier = Modifier
                            .clip(LoveBrainShape.sm)
                            .graphicsLayer { scaleX = deleteScale; scaleY = deleteScale }
                            .clickable(interactionSource = deleteInteraction, indication = null, onClick = { deletingIds[msg.id] = true })
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "删除消息",
                            tint = if (isEditing) Error else TextHint,
                            modifier = Modifier.size(Spacing.xl)
                        )
                    }
                }
            }
            }
        }
    }
}
