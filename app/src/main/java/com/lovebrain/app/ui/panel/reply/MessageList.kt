package com.lovebrain.app.ui.panel.reply

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lovebrain.app.R
import com.lovebrain.app.model.ChatMessage
import com.lovebrain.app.core.designsystem.*
import com.lovebrain.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 消息行内部尺寸常量。
 *
 * ⚠ 气泡那一档（横 12 / 竖 6、条目间距 4）**是本轮新合同给的起点值，不是 v1.3.1 量出来的旧值**——
 * 旧版行内边距是 12/8、行间距 4，本次用户要的是"一条消息占用的整体大小缩小"，
 * 所以竖内边距从 8 收到 6、整行不再垫到 48dp 可见高（见 [MessageRow] 那三轴的分账）。
 */
private object MessageDimens {
    /** 气泡内边距（横）——本轮新起点 */
    const val BUBBLE_HPAD_DP = 12
    /** 气泡内边距（竖）——本轮新起点 */
    const val BUBBLE_VPAD_DP = 6
    /** 条目之间的垂直间距——本轮新起点 */
    const val ROW_SPACING_DP = 4
    /**
     * 气泡最宽 = 这一行可用宽的八成。长文在这条线内自己换行，短句按内容宽收——
     * 于是左右两列看得出"谁在说话"，而不是两张贴边的整行白底卡。
     */
    const val BUBBLE_MAX_WIDTH_FRACTION = 0.8f
    /** 行里留给气泡的横向呼吸位（不是热区，也不是尺寸档） */
    const val ROW_HPAD_DP = 4
    /**
     * 滑动删除阈值：横向拖过**行宽的这一比**才落删除，不到一律回弹归零。
     * 0.4 是 Material 滑动删除的通行档——行宽约 300dp 的面板上约 120dp 行程，
     * 划在"故意拖出去"和"擦到列表想滚动"之间。不给甩动开后门：删除丢内容且不可
     * 撤销，小面板上速度误判代价太高，判据宁可只做这一条。
     */
    const val SWIPE_DELETE_THRESHOLD_FRACTION = 0.4f
    /** 空态那个蓝字入口的最小热区——它是一处操作，不是一行说明 */
    const val EMPTY_ACTION_MIN_HEIGHT_DP = AppDimens.TOUCH_TARGET_MIN_DP
    /** 长按拖拽时，离视口上下边缘多近才开始自动滚动 */
    const val DRAG_EDGE_ZONE_DP = 80
    /** 自动滚动的速度与"离边缘多近"成正比，这两颗是那两根端点（px/帧） */
    const val DRAG_EDGE_MIN_SPEED_PX = 6f
    const val DRAG_EDGE_MAX_SPEED_PX = 24f
    /** 边缘自动滚动那一颗任务唯一的节拍（ms）——只有它，不在手势回调里逐帧起新任务 */
    const val DRAG_SCROLL_FRAME_MS = 16L
    /** 被手指控制的那一行浮起来时的放大档 */
    const val DRAGGED_SCALE = 1.02f
    /** 退场时长（ms）：合同上限 200ms，删除落点始终只认 id */
    const val EXIT_DURATION_MS = 200L
}

/** 消息行的读屏/测试锚点：整行（手势落点） */
internal const val MESSAGE_ROW_TEST_TAG = "message_row"

/** 气泡本体的锚点：宽度、左右贴边、底色都从这一颗粒上量 */
internal const val MESSAGE_BUBBLE_TEST_TAG = "message_bubble"

/**
 * 滑动删除的唯一判据（纯函数，可逐值钉死）：|拖量| ≥ 行宽 × 阈值比 → 删。
 * 行宽还没量到（0）时恒不删——第一帧不许凭空满足阈值。
 */
internal fun shouldDeleteBySwipe(dragPx: Float, rowWidthPx: Float): Boolean =
    rowWidthPx > 0f && abs(dragPx) >= rowWidthPx * MessageDimens.SWIPE_DELETE_THRESHOLD_FRACTION

/**
 * 气泡底色的**三种档**：两列各自的角色色，加一档"这条正处于被操作的状态"。
 *
 * 刻意只有三档、且**没有第四档给 IDEA**：旧数据里的 `Role.IDEA` 不是对话的一方，它折叠进
 * 列表尾部那一行灰字（见 [foldAdvisorNote]）。给它留一颗第三种颜色的气泡，等于把备注
 * 冒充成对话——那正是 等 两侧都不许发生的形状。
 */
internal enum class BubbleInk { Incoming, Outgoing, InState }

/**
 * 谁涂哪一档（纯函数）：状态那一档**盖过**角色（编辑中/被拖着的是"状态"，不是"第三种人"），
 * 其余按"是不是我说的"分左右两色。
 *
 * 这里只交档位、不交色值：色值仍写在 `MessageRow` 的 `.background(...)` 那一处，
 * 因为仓库那把品牌底账（`UiLayerDependencyContractTest`）就是锚在字面 token 上的——
 * 把 token 搬进本函数会让那两把尺当场看不见这一处（数量掉到 0 也算红）。
 */
internal fun bubbleInkOf(isMine: Boolean, isEditing: Boolean, isDragged: Boolean): BubbleInk = when {
    isEditing || isDragged -> BubbleInk.InState
    isMine -> BubbleInk.Outgoing
    else -> BubbleInk.Incoming
}

/**
 * 军师备注正文的唯一折叠口径（纯函数）：宿主交来的那份（`ComposerStore.ideaHint()`，
 * 已含提交过的补充与输入框里没提交的草稿）排在前面，列表里**残留的旧 `Role.IDEA` 行**
 * 补在后面；按行去重（同一句不出现两遍），空白一律丢。
 *
 * 什么都剩不下时交回 **null**——null 的含义是"这一行根本不画"（：没有备注不许留一块空标题）。
 */
internal fun foldAdvisorNote(ideaContents: List<String>, noteText: String?): String? {
    val lines = LinkedHashSet<String>()
    fun addAll(block: String?) {
        block?.lines()?.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) lines.add(trimmed)
        }
    }
    addAll(noteText)
    ideaContents.forEach { addAll(it) }
    return if (lines.isEmpty()) null else lines.joinToString("\n")
}

/** 拖拽中一格在列表里的槽位（展示位 + 布局偏移 + 高度），全 px */
internal data class DragSlot(val index: Int, val offsetPx: Int, val sizePx: Int)

/**
 * 与相邻行交换一步的结果：换到哪一个展示位，以及**交换后要从累计位移里扣掉的量**。
 *
 * 那笔扣减就是"跟手"与"跳回原位"的分界：交换让被拖行在布局里挪了一格，
 * 累计位移不跟着扣，画在手指下的那颗就会瞬移。
 */
internal data class DragSwap(val newIndex: Int, val offsetCompensationPx: Float)

/**
 * 长按拖拽的一步判据（纯函数；UI 只把 layoutInfo 的槽位喂进来）：
 * 被拖行带着累计位移后的**视觉中心**越过硬相邻行的中心 ⇒ 与那一行交换，并给出布局补偿量。
 * 没越过任何一条中心线时给 null（这一帧只画位移，不动顺序）。
 *
 * 补偿量的推法（向下交换）：交换后被拖行占的是"相邻格之后"那一槽，
 * 新布局偏移 = below.offset + below.size - dragged.size；
 * 于是 `新布局偏移 + (累计位移 + 补偿)` 与交换前的 `原布局偏移 + 累计位移` 逐字相等。
 */
internal fun dragSwapStep(
    dragged: DragSlot,
    offsetPx: Float,
    above: DragSlot?,
    below: DragSlot?
): DragSwap? {
    val visualCenter = dragged.offsetPx + offsetPx + dragged.sizePx / 2f
    if (below != null && visualCenter > below.offsetPx + below.sizePx / 2f) {
        val newLayoutOffset = below.offsetPx + below.sizePx - dragged.sizePx
        return DragSwap(below.index, (dragged.offsetPx - newLayoutOffset).toFloat())
    }
    if (above != null && visualCenter < above.offsetPx + above.sizePx / 2f) {
        return DragSwap(above.index, (dragged.offsetPx - above.offsetPx).toFloat())
    }
    return null
}

/**
 * 边缘自动滚动的速度（px/帧，带符号）：手指落在视口上下边缘 [edgeZonePx] 以内才给速度，
 * 越靠边越快；上边缘为负、下边缘为正、中间是 **0**。
 * 0 就是"立刻停"——那颗唯一的自动滚动任务读到 0 就退出，不需要额外的取消信号。
 */
internal fun edgeAutoScrollSpeedPx(
    fingerY: Float,
    viewportStartPx: Int,
    viewportEndPx: Int,
    edgeZonePx: Float
): Float {
    if (edgeZonePx <= 0f) return 0f
    // 视口还没量到（列表尚未布局完成，start == end）时一律不滚：
    // 那一帧凭空给一个速度，用户看到的就是"我还没拖它自己先跑了一下"
    if (viewportEndPx <= viewportStartPx) return 0f
    val toBottom = viewportEndPx - fingerY
    val toTop = fingerY - viewportStartPx
    return when {
        toBottom <= 0f -> MessageDimens.DRAG_EDGE_MAX_SPEED_PX
        toBottom < edgeZonePx -> depthSpeed(toBottom, edgeZonePx)
        toTop <= 0f -> -MessageDimens.DRAG_EDGE_MAX_SPEED_PX
        toTop < edgeZonePx -> -depthSpeed(toTop, edgeZonePx)
        else -> 0f
    }
}

/** 离边缘越近越快：把"还剩多远"换成速度，并夹在那两根端点之间 */
private fun depthSpeed(distancePx: Float, edgeZonePx: Float): Float =
    (MessageDimens.DRAG_EDGE_MAX_SPEED_PX * (1f - distancePx / edgeZonePx))
        .coerceAtLeast(MessageDimens.DRAG_EDGE_MIN_SPEED_PX)

private fun LazyListItemInfo.asDragSlot() = DragSlot(index, offset, size)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageList(
    messages: List<ChatMessage>,
    editingIndex: Int,
    onReorder: (Int, Int) -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    // 空态蓝字 = 主动发入口（点击召唤/再点关闭）
    onEmptyAction: (() -> Unit)? = null,
    proactiveActive: Boolean = false,
    //  那行灰字的正文真源（宿主从 `ComposerStore.ideaHint()` 现读，含未提交的草稿）。
    // null = 宿主还没接线 → 只把列表里残留的旧想法行折进备注；两边都空时这一行**根本不画**。
    noteText: String? = null,
    // 点那行灰字 = 去编辑完整备注（宿主投 `ComposerStore.Intent.BeginNoteEdit`）。
    // 默认值是"未接线"那一档：这颗入口没接线时 noteText 也不会有内容，画不出点了没反应的东西。
    onEditNote: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    // 长消息折叠按 msg.id 存储，避免重排/删除后展开状态串到别的消息
    val expandedMessages = remember { mutableStateMapOf<String, Boolean>() }
    // 删除退场（滑到阈值 → 200ms 滑出+淡出 → onDelete）。按 id 的 Map 支持快速连续删除
    //（旧方案用单个 index，连续操作会取消前一个计时器）。用退场而非收缩，避免列表跳动。
    val deletingIds = remember { mutableStateMapOf<String, Boolean>() }
    // 滑动删除的退场方向（+1 右 / -1 左）
    val swipeExitDirs = remember { mutableStateMapOf<String, Int>() }

    // ── 长按拖拽：认人靠稳定 id，画位置靠累计位移 ────────────────────────────
    // 谁在被手指控制（不是"第几格"：交换之后格号在动，id 不动）
    var draggedId by remember { mutableStateOf<String?>(null) }
    // 这一行从它自己的布局槽位起累计走了多少 px —— 直接写进 translationY
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    // 每一次长按换一个 session 号：自动滚动那颗任务只认这一个键
    var dragSession by remember { mutableIntStateOf(0) }
    // 当帧的边缘滚动速度（px/帧）。0 = 不在边缘 = 那颗任务立刻退出
    var edgeSpeedPx by remember { mutableFloatStateOf(0f) }

    val endDrag: () -> Unit = {
        draggedId = null
        edgeSpeedPx = 0f
        dragOffsetY = 0f
    }

    // 同一份列表派生两件事：聊天行只有 HER/ME 两列，且**带着原始下标**——onEdit/onReorder 的
    // 口径始终是持有者那份列表的下标，过滤掉一条想法不会让编辑落到别的消息上；
    // IDEA 旧数据一律不进聊天顺序，它折进列表尾部那一行灰字（画成第三种颜色的聊天气泡
    // 是把备注冒充成对话）。
    val numbered: List<Pair<Int, ChatMessage>> = messages.mapIndexed { index, msg -> index to msg }
    val dialogueDisplayed: List<Pair<Int, ChatMessage>> =
        numbered.filter { it.second.role != ChatMessage.Role.IDEA }
    val advisorNoteText = foldAdvisorNote(
        ideaContents = numbered.filter { it.second.role == ChatMessage.Role.IDEA }
            .map { it.second.content },
        noteText = noteText
    )

    // 长按拖拽的命中判定跑在不随重组重启的 pointerInput 里，索引映射必须每次读最新一份
    val currentDialogue by rememberUpdatedState(dialogueDisplayed)

    // 自动滚动到最新消息：只在用户已接近底部时触发，避免翻看历史时被强制拉回底部
    LaunchedEffect(messages.size) {
        if (dialogueDisplayed.isNotEmpty()) {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            val isNearBottom = lastVisible >= dialogueDisplayed.lastIndex - 1
            if (isNearBottom) listState.animateScrollToItem(dialogueDisplayed.lastIndex)
        }
    }

    // 边缘自动滚动的**唯一**一颗任务：速度变了就重启（上一颗随之取消），速度归零就退出。
    // 滚出去多少才补偿多少——滚不动（已经到顶/到底）时一分都不补，被拖行不会自己飘走。
    LaunchedEffect(dragSession, edgeSpeedPx) {
        val speed = edgeSpeedPx
        if (speed == 0f) return@LaunchedEffect
        while (true) {
            val consumed = speed - listState.scrollBy(speed)
            dragOffsetY += consumed
            delay(MessageDimens.DRAG_SCROLL_FRAME_MS)
        }
    }

    // 删除的两条路（横滑到阈值 / 读屏自定义动作）都只认 id，而且**走同一颗 arm**。
    // 方向一律显式交进来，`swipeExitDirs` 与 `deletingIds` 永远是同一次写入的两笔：
    // 旧写法读屏那一支只置 `deletingIds`、方向靠退场里的 `?: 1` 兜底，于是"同一条消息，
    // 横滑删是往左滑出、读屏删是往右滑出"——两条路各存半份状态，判据也只覆盖得了一条。
    val armDelete: (String, Int) -> Unit = { id, dir ->
        swipeExitDirs[id] = dir
        deletingIds[id] = true
    }
    val finishDelete: (String) -> Unit = { id ->
        deletingIds.remove(id)
        swipeExitDirs.remove(id)
        onDelete(id)
    }
    val toggleExpanded: (String) -> Unit = { id ->
        expandedMessages[id] = !(expandedMessages[id] ?: false)
    }

    // 备注那一行的**尺寸档**由 `AdvisorNoteLine` 自己管（48dp 热区 + 单行省略），
    // 旧 IdeaSection 那套"按列表槽算限高"随它一起没了。横滑不当点击这一道闸门（[swipeIsNotATap]）
    // 挂在**调用点**交进去：组件本体归 ReplyInput 那一侧，这一轮的删除/手势合同都在这个文件里判。
    if (dialogueDisplayed.isEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth().padding(vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ✦ 空态图标：素材已预处理（贴边白底转透明+裁边），直接呈现不再裁圆；
            //   tint=Unspecified 必须：M3 Icon 默认按内容色染色，位图彩图会被整体染黑（全黑根因）
            Icon(
                painter = painterResource(R.drawable.ic_empty_reply),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(AppDimens.EMPTY_ICON_CONTAINER_DP.dp)
            )
            Spacer(Modifier.height(Spacing.md))
            // 空态蓝字 = 主动发入口——点击召唤主动发，再点关闭。外层 Box 承担 ≥48dp 热区与
            // 按钮角色，文字在里面居中；两种文案进资源（内联写法正则尺看不见）。
            Box(
                modifier = Modifier
                    .heightIn(min = MessageDimens.EMPTY_ACTION_MIN_HEIGHT_DP.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = { onEmptyAction?.invoke() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        if (proactiveActive) R.string.proactive_empty_turn_off
                        else R.string.proactive_empty_send_one
                    ),
                    color = Primary,
                    style = AppTypography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                )
            }
            // 只有备注、还没聊天的那一轮：那一行灰字照常钉在最下面
            //（ 原话要的就是这一格——备注不能因为没聊天就整个不见）
            if (advisorNoteText != null) {
                Spacer(Modifier.height(Spacing.md))
                AdvisorNoteLine(noteText = advisorNoteText, onClick = onEditNote, modifier = Modifier.swipeIsNotATap())
            }
        }
        return
    }

    // 聊天行与那一行备注同坐在这块旧版圆角底上；备注不参与聊天行的滚动
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceInset, LoveBrainShape.lg)
            .padding(Spacing.md)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val hitItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                offset.y >= item.offset && offset.y < item.offset + item.size
                            }
                            if (hitItem != null) {
                                // 认人只认 id：随后每一次交换都只改格号，手指底下的那颗不变
                                draggedId = hitItem.key as? String
                                dragOffsetY = 0f
                                edgeSpeedPx = 0f
                                dragSession++
                                // 长按这一下手震一次；此后只在真的换了一格时再震（不每像素震）
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val id = draggedId ?: return@detectDragGesturesAfterLongPress
                            // ① 先按手指走：这一帧的位移原样累加，不"等交换了才动"
                            dragOffsetY += dragAmount.y

                            val layout = listState.layoutInfo
                            val items = layout.visibleItemsInfo
                            val draggedItem = items.firstOrNull { it.key == id }
                            if (draggedItem != null) {
                                val slot = draggedItem.asDragSlot()
                                val swap = dragSwapStep(
                                    dragged = slot,
                                    offsetPx = dragOffsetY,
                                    above = items.firstOrNull { it.index == slot.index - 1 }?.asDragSlot(),
                                    below = items.firstOrNull { it.index == slot.index + 1 }?.asDragSlot()
                                )
                                val shown = currentDialogue
                                val fromPair = shown.getOrNull(slot.index)
                                val toPair = swap?.let { shown.getOrNull(it.newIndex) }
                                // 只有"这一格此刻确实还是被拖那一条"才发重排：列表还没追上上一次交换时
                                // 映射是旧的，那种帧宁可不动作，也不按旧下标发一次假的重排
                                if (swap != null && fromPair != null && toPair != null &&
                                    fromPair.second.id == id
                                ) {
                                    onReorder(fromPair.first, toPair.first)
                                    // ② 扣掉交换产生的布局偏移：画在手指下的位置一步都不跳
                                    dragOffsetY += swap.offsetCompensationPx
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                // ③ 边缘速度只写这一个数，滚动交给那一颗唯一任务
                                edgeSpeedPx = edgeAutoScrollSpeedPx(
                                    fingerY = change.position.y,
                                    viewportStartPx = layout.viewportStartOffset,
                                    viewportEndPx = layout.viewportEndOffset,
                                    edgeZonePx = MessageDimens.DRAG_EDGE_ZONE_DP * density
                                )
                            }
                        },
                        onDragEnd = { endDrag() },
                        onDragCancel = { endDrag() }
                    )
                },
            verticalArrangement = Arrangement.spacedBy(MessageDimens.ROW_SPACING_DP.dp)
        ) {
            itemsIndexed(
                items = dialogueDisplayed,
                key = { _, pair -> pair.second.id }
            ) { _, pair ->
                val msg = pair.second
                val isDragged = draggedId != null && draggedId == msg.id
                MessageRow(
                    msg = msg,
                    originalIndex = pair.first,
                    editingIndex = editingIndex,
                    isDragged = isDragged,
                    draggedOffsetY = { dragOffsetY },
                    // 重排接管时这一行不再累积滑动量：两条手势各走各的轴，不互抢
                    reorderActive = draggedId != null,
                    isDeleting = deletingIds[msg.id] == true,
                    swipeExitDir = swipeExitDirs[msg.id],
                    isExpanded = expandedMessages[msg.id] == true,
                    onToggleExpanded = { toggleExpanded(msg.id) },
                    onEdit = { onEdit(pair.first) },
                    // 读屏那一条删除没有"手指方向"，就按气泡自己那一侧退场：HER 往左、ME 往右。
                    // 这一档与横滑那一条共用 armDelete，所以两条路交出去的只有 id + 方向两件事。
                    onRequestDelete = {
                        armDelete(msg.id, if (msg.role == ChatMessage.Role.ME) 1 else -1)
                    },
                    onSwipeArmed = { dir -> armDelete(msg.id, dir) },
                    onExitFinished = { finishDelete(msg.id) },
                    // 被手指控制的那一行**不许**再吃 placement 动画：它会对着手指正在占的那一格反向插值，
                    // 于是"拖到一半弹回去"。其余行照常 animateItemPlacement，看得出位置让开了。
                    modifier = if (isDragged) Modifier else Modifier.animateItemPlacement()
                )
            }
        }
        //  那一句原话："放在最后一个聊天气泡下面，一行灰色的提示性小字"——
        // 挂在 LazyColumn **之外**，所以它既不在聊天顺序里、也拖不进聊天顺序
        if (advisorNoteText != null) {
            Spacer(Modifier.height(Spacing.sm))
            AdvisorNoteLine(noteText = advisorNoteText, onClick = onEditNote, modifier = Modifier.swipeIsNotATap())
        }
    }
}

/**
 * 一条真实对话的整行：HER 靠左白底、ME 靠右微信绿，底色只在**气泡**身上，整行只是手势与锚点。
 * 可见的「她/我」标签与尾部 ❌ 都撤了——角色改由位置 + 读屏标签（[contentDescription]）表达，
 * 删除只剩两条路：横滑过阈值、读屏自定义动作。屏幕默认不放叉号（第5节第2条）。
 *
 * 三轴分账（第3节第1条）：
 *  · 可见尺寸 = 气泡内容宽（上限一行的八成）+ 横 12 / 竖 6 内边距；
 *  · 触摸热区 = 整行（全宽、含气泡上下那点余量），单击编辑挂在这一层；
 *  · 相邻布局 = 条目间距 4dp。
 * 旧的"整行垫到 48dp 可见高"这一档**本次主动放弃**：它买的是版式高度而不是可点性，
 * 而用户合同要的正是把条目缩下来（取舍记在交接报告，读屏替代出口是那颗自定义删除动作）。
 *
 * 手势分界：按下后先横移越过触控 slop → 这一行水平 draggable 起势（长按计时器被移动取消，
 * "快滑 = 删除候选"）；按住不动到长按阈值 → 重排接管并消费后续事件，draggable 的 enabled
 * 同时被 reorderActive 关掉；首个方向是纵向 → 水平拖不起势，事件留给列表滚动；
 * 没越过 slop 的起落 → 仍是原来的单击编辑。垂直浏览因此不会被误判成删除。
 * 横滑在这一行**自己**的孩子节点上消费，父层（回复/谈心切页）拿不到已经越轴的移动量。
 */
@Composable
private fun MessageRow(
    msg: ChatMessage,
    originalIndex: Int,
    editingIndex: Int,
    isDragged: Boolean,
    draggedOffsetY: () -> Float,
    reorderActive: Boolean,
    isDeleting: Boolean,
    swipeExitDir: Int?,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    onSwipeArmed: (Int) -> Unit,
    onExitFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    // 行内横向位移：拖到哪画到哪；松手未达阈值由 onDragStopped 弹回 0
    val swipeX = remember(msg.id) { Animatable(0f) }
    var rowWidthPx by remember(msg.id) { mutableFloatStateOf(0f) }
    val lift by animateFloatAsState(
        targetValue = if (isDragged) MessageDimens.DRAGGED_SCALE else 1f,
        label = "dragScale"
    )
    // draggable 的 state 只 remember 一次，里面读的那几样要走"最新一份"，
    // 否则重排接管后这一行还在偷偷累积滑动量
    val blocked by rememberUpdatedState(reorderActive || isDeleting || isDragged || rowWidthPx <= 0f)
    val latestWidth by rememberUpdatedState(rowWidthPx)
    val latestSwipeArmed by rememberUpdatedState(onSwipeArmed)

    // 读屏身份：可见标签撤了，角色不能跟着消失
    val roleAnnouncement = when (msg.role) {
        ChatMessage.Role.HER -> stringResource(R.string.a11y_message_her)
        ChatMessage.Role.ME -> stringResource(R.string.a11y_message_me)
        ChatMessage.Role.IDEA -> msg.role.label
    }
    val deleteActionLabel = stringResource(R.string.a11y_delete_message)

    // 退场收尾才真删：200ms 滑出+淡出之后交 id（延迟期间落点仍是这颗 id，不用旧下标）
    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            delay(MessageDimens.EXIT_DURATION_MS)
            onExitFinished()
        }
    }

    AnimatedVisibility(
        visible = !isDeleting,
        exit = slideOutHorizontally(
            targetOffsetX = { it * (swipeExitDir ?: 1) },
            animationSpec = tween(MessageDimens.EXIT_DURATION_MS.toInt())
        ) + fadeOut(tween(MessageDimens.EXIT_DURATION_MS.toInt()))
    ) {
        val isMine = msg.role == ChatMessage.Role.ME
        val isEditing = originalIndex == editingIndex
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                // 被手指控制的那一行浮在其他消息之上
                .zIndex(if (isDragged) 1f else 0f)
                .graphicsLayer {
                    translationX = swipeX.value
                    // 被手指控制那一条按**累计位移**画（不是 index 换算出来的位置）。
                    // 这两处读取都留在 graphicsLayer 里：逐帧只重画图层，不重组整棵列表
                    //（重组每一次事件都跑一边 items，正是"看着不跟手"的另一半成因）。
                    translationY = if (isDragged) draggedOffsetY() else 0f
                    scaleX = lift
                    scaleY = lift
                }
                .then(modifier)
                .then(
                    // 拖拽态阴影收敛至上限 4dp
                    if (isDragged) Modifier.shadow(AppDimens.ELEVATION_MAX_DP.dp, LoveBrainShape.md)
                    else Modifier
                )
                .testTag(MESSAGE_ROW_TEST_TAG)
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (!blocked) {
                            scope.launch {
                                swipeX.snapTo(
                                    (swipeX.value + delta).coerceIn(-latestWidth, latestWidth)
                                )
                            }
                        }
                    },
                    orientation = Orientation.Horizontal,
                    enabled = !blocked,
                    onDragStopped = {
                        if (!blocked && shouldDeleteBySwipe(swipeX.value, latestWidth)) {
                            // 达到阈值：记下退场方向，走退场→onDelete 那一条路
                            latestSwipeArmed(if (swipeX.value < 0f) -1 else 1)
                        } else if (swipeX.value != 0f) {
                            scope.launch { swipeX.animateTo(0f) }  // 未达阈值：回弹归零，什么都不删
                        }
                    }
                )
                // 单击消息 → 选中并在上方输入框编辑（长按拖拽与滑动都不受影响）
                .clickable { onEdit() }
                // 撤掉可见标签之后，角色与删除都挂在这**一层**语义节点上：它同时是点击的落点，
                // 读屏聚焦的就是这一颗（挂在气泡里就要赌"自定义动作会不会被合并上去"这件事）
                .semantics(mergeDescendants = true) {
                    contentDescription = roleAnnouncement
                    customActions = listOf(
                        CustomAccessibilityAction(deleteActionLabel) {
                            onRequestDelete()
                            true
                        }
                    )
                }
                .onSizeChanged { rowWidthPx = it.width.toFloat() }
                .padding(horizontal = MessageDimens.ROW_HPAD_DP.dp),
            contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth * MessageDimens.BUBBLE_MAX_WIDTH_FRACTION)
                    .testTag(MESSAGE_BUBBLE_TEST_TAG)
                    .background(
                        when (bubbleInkOf(isMine = isMine, isEditing = isEditing, isDragged = isDragged)) {
                            // 正在编辑 / 正被拖着的那条走品牌浅蓝：那是**状态**，不是第三种角色色
                            BubbleInk.InState -> PrimaryLight
                            BubbleInk.Outgoing -> ChatOutgoingBg
                            // 没有第四档：旧 `Role.IDEA` 根本进不到这一行（它折进尾部那行灰字）
                            BubbleInk.Incoming -> SurfaceCard
                        },
                        LoveBrainShape.md
                    )
                    .padding(
                        horizontal = MessageDimens.BUBBLE_HPAD_DP.dp,
                        vertical = MessageDimens.BUBBLE_VPAD_DP.dp
                    )
            ) {
                MessageBody(
                    msg = msg,
                    modifier = Modifier,
                    isExpanded = isExpanded,
                    onToggleExpanded = onToggleExpanded
                )
            }
        }
    }
}

/** 长消息折叠：超过阈值默认折叠，点击展开/收起（渐进展开——先给摘要，按需给全文） */
@Composable
private fun MessageBody(
    msg: ChatMessage,
    modifier: Modifier,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val COLLAPSE_THRESHOLD = 80
    val shouldFold = msg.content.length > COLLAPSE_THRESHOLD
    val displayText = if (shouldFold && !isExpanded) msg.content.take(COLLAPSE_THRESHOLD) else msg.content

    Column(modifier = modifier) {
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
                    // 间距并入 clickable 覆盖区（先声明为外层）；这一档随条目一起收到 2dp
                    .clickable { onToggleExpanded() }
                    .padding(vertical = Spacing.xs)
            )
        }
    }
}

/**
 * 横滑不是点击——给"只有一个点击出口的整行"（尾部那行军师备注）挂一道触控 slop 闸门。
 *
 * 为什么必须有这一道：`Modifier.clickable` 的判据是"抬起时人还在本节点范围内"，
 * **它不看走了多远**。于是"从右往左擦过整行"这一记（第5节第3条 手势优先级里这一档属于
 * 非气泡区域 → 横滑切回复/谈心）会被当成"点中了备注"而把编辑器打开：用户只是想切页，
 * 结果备注被拉进输入框。合同要的是"点它 = 去编辑完整备注"，擦过去不该是点击。
 *
 * 实现走 `PointerEventPass.Initial`：同一条链上先声明的修饰符在 Initial 通道**先于**子节点
 *（这里的子节点就是 `AdvisorNoteLine` 内部那颗 `clickable`）拿到同一批 `PointerInputChange`，
 * 消费掉它们，子节点的点击检测按 Compose 的常规收到"被别人接走"就取消——这正是父层滚动
 * 容器抢走按钮点击的同一套机制，不是新发明。
 *
 * 两条克制：① **只有横向主导**且累计行程越过 slop 才开始消费——纵向浏览照旧交给列表滚动，
 * 原地起落的真点击一个事件都不消费；② 只在事件仍被按住时消费，抬起即收手，不留悬挂状态。
 *（消费写在这一行的节点上，不在面板最外层无条件吃事件——那是 第5节第3条 明令禁止的形状。）
 */
private fun Modifier.swipeIsNotATap(): Modifier = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    // ⚠ 整段必须包在 awaitPointerEventScope 里：`awaitPointerEvent` 是那个 scope 的成员，
    //   直接写在 PointerInputScope 上编不过（编译时才抓到这一条）。
    awaitPointerEventScope {
        while (true) {
            var pending: PointerInputChange? = null
            while (pending == null) {
                pending = awaitPointerEvent(PointerEventPass.Initial)
                    .changes.firstOrNull { it.pressed }
            }
            val pressed = pending!!
            val pointerId = pressed.id
            val originX = pressed.position.x
            val originY = pressed.position.y
            var stealing = false
            var tracking = true
            while (tracking) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pointer = event.changes.firstOrNull { it.id == pointerId }
                if (pointer == null) {
                    tracking = false
                } else {
                    val dx = pointer.position.x - originX
                    val dy = pointer.position.y - originY
                    if (!stealing && abs(dx) > slop && abs(dx) > abs(dy)) stealing = true
                    if (stealing) event.changes.forEach { moved -> moved.consume() }
                    if (!pointer.pressed) tracking = false
                }
            }
        }
    }
}
