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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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

/**
 * 真实对话行的唯一判据（宿主与 [MessageList] 内部分支**必须**同用这一颗）：
 * 列表里存不存在 HER/ME 消息（`Role.IDEA` 是内容种类、不是对话，见 [foldAdvisorNote]）。
 *
 * 为什么单列出来：次级控制（意图 / 仅看本轮）按"有没有真实消息"在两个地方二选一挂载——
 * 有消息时挂输入区行 2（[ReplyInput.hasRealMessages]），无消息时收进消息卡空态分支。
 * 空/非空的边界帧若两处各算各的口径，就会两帧里都画或都不画。判据收成这一个纯函数、
 * 输入同一份 `messages` ⇒ 两处必然同一口径（source-01 §2.3 点名的可疑时序在此结清）。
 */
internal fun hasRealDialogueRows(messages: List<ChatMessage>): Boolean =
    messages.any { it.role != ChatMessage.Role.IDEA }

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
 * 「上一次交换在布局里落地了没有」（纯函数，长按拖拽发重排的唯一闸门）：
 * [pendingSwapFromIndex] < 0 = 没有待落地的交换；否则**被拖那一条还站在发那次交换时的那一格**
 * 就意味着列表还没追上上一次改动（组合/布局是帧边界才跑的，一帧里可以来好几记移动事件）。
 *
 * 为什么这一档必须存在（原话第 19 条"最终顺序 = prompt 顺序"的那一半）：落库那一步是
 * "从 from 摘下、插到 to"，同一帧里对**同一对下标**发两次会把第一次那一步整个**抵消**
 * （[A,B,C] → [B,A,C] → [A,B,C]），屏幕上手指已经把那条拖下去了、列表却回到了原点——
 * 于是喂给模型的顺序和用户拖出来的顺序不是一件事。布局一旦挪过（不管是挪到目标格还是
 * 被别处的增删挪去别处），这里一律判"落地了"，所以闸门不会把拖拽锁死。
 */
internal fun dragSwapIsSettled(draggedLayoutIndex: Int, pendingSwapFromIndex: Int): Boolean =
    pendingSwapFromIndex < 0 || draggedLayoutIndex != pendingSwapFromIndex

/**
 * 拖拽位移的**视口夹持**（纯函数）：手指控制那一条"画出来的中心"必须留在视口里。
 * 手指滑出列表那一段（备注行、输入区；长按起势后 pointerInput 仍在收事件，位置在列表外也照收）时，
 * 那一行停在边缘、至少半个身位在眼睛底下，而不是跟着手指飞出屏幕。
 *
 * 为什么钉的是**中心**而不是整条：交换判据 [dragSwapStep] 量的是"越过相邻行的中心线"。
 * 夹成"整条不许出视口"，顶边就永远上不到首行中心线以上 ⇒ **第一条那一格再也拖不进去**，
 * 用户看到的是"拖不到最上面"——比半条出界严重得多。夹中心则两端各还剩半个身位可以越线，
 * 首格与末格都进得去（`MessageListDragOrderTest` 拿时间线钉这一档）。
 * 行比视口高（大字体 + 展开的长消息）时同理：中心在视口内 ⇒ 视口被它盖掉一半以上，仍能上下拖。
 *
 * 视口或行高没量到时（0 / 倒挂）原样交回：第一帧不许凭空造一个位置
 * （与 [edgeAutoScrollSpeedPx] 的"视口没量到就不许有速度"同一规矩）。
 */
internal fun clampDragOffsetInsideViewport(
    offsetPx: Float,
    itemLayoutOffsetPx: Int,
    itemSizePx: Int,
    viewportStartPx: Int,
    viewportEndPx: Int
): Float {
    if (itemSizePx <= 0 || viewportEndPx <= viewportStartPx) return offsetPx
    val half = itemSizePx / 2f
    // 下限 = 中心顶到视口上沿、上限 = 中心贴到视口下沿（end > start 在这一颗里是前置条件，
    // 所以这对端点天然有序，`coerceIn` 不会被喂进 min>max）
    val min = viewportStartPx - half - itemLayoutOffsetPx
    val max = viewportEndPx - half - itemLayoutOffsetPx
    return offsetPx.coerceIn(min, max)
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

/**
 * 一次手势的观察结果：横向越过触控 slop 就置位。
 * 备注行用它把"擦过去"与"真点它"分开（见 [noteSwipeIsNotATap]）——它只是一格读数，不是第二本状态账。
 */
private class NoteSwipeFlag {
    var crossedSlop = false
}

/**
 * 备注行那道"擦过不是点击"的闸门：**只观察、一个事件都不消费**。
 *
 * 为什么必须有：`Modifier.clickable` 的判据是"抬指时还在本节点范围内"，**不看走了多远**。
 * 备注那一行铺满整行宽（344dp），一记横滑的起与止都落在它自己身上 ⇒ 抬指被当成点击，
 * 编辑入口就这么被擦着了。`AdvisorNoteLine` 自己那颗 `draggable` 只在传了 `onSwipeClear` 时存在，
 * 宿主没接清除出口时（`onClearNote == null`）这一路没有人挡。
 * 钉它的格子：`MessageRowSwipeDeleteTest`.`the note line is an edit entry, not a swipe delete target`。
 *
 * 为什么不消费事件：被撤掉的旧 `swipeIsNotATap` 在 Initial pass 无条件吃事件，代价是把备注行
 * 变成父层切页的死区（原话第 15 条一起撤的正是这一档）。这里只读坐标：横滑清除仍归备注自己的
 * `draggable`，纵向滚动与切页照常收得到事件。
 */
private fun Modifier.noteSwipeIsNotATap(flag: NoteSwipeFlag): Modifier = pointerInput(flag) {
    // slop 在 PointerInputScope 这一层读（`viewConfiguration` 挂在这颗上，手势那一层没有这一颗）
    val slopPx = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        flag.crossedSlop = false
        val startX = down.position.x
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (abs(change.position.x - startX) > slopPx) flag.crossedSlop = true
            if (!change.pressed) break
        }
    }
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
    // 空态蓝字 = 主动发入口（点击召唤/再点关闭）
    onEmptyAction: (() -> Unit)? = null,
    proactiveActive: Boolean = false,
    //  那行灰字的正文真源（宿主从 `ComposerStore.ideaHint()` 现读，含未提交的草稿）。
    // null = 宿主还没接线 → 只把列表里残留的旧想法行折进备注；两边都空时这一行**根本不画**。
    noteText: String? = null,
    // 点那行黄字 = 去编辑完整备注（宿主投 `ComposerStore.Intent.BeginNoteEdit`）。
    // 默认值是"未接线"那一档：这颗入口没接线时 noteText 也不会有内容，画不出点了没反应的东西。
    onEditNote: () -> Unit = {},
    // 侧滑清除备注（宿主投 `ComposerStore.Intent.ClearNote`）。null = 没接线 ⇒ 备注不留侧滑出口
    //（沿用"不给没接线的屏留半截手势"的旧规矩）；传进来后备注像消息一样可侧滑删，
    // 被删对象与回调分离：这里落的是"清备注"，绝不走 onDelete 那条消息删除链。
    onClearNote: (() -> Unit)? = null
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
    // 上一次交换发出时，被拖那一条站的那一格；< 0 = 没有待落地的交换（见 [dragSwapIsSettled]）
    var pendingSwapFromIndex by remember { mutableIntStateOf(-1) }

    val endDrag: () -> Unit = {
        draggedId = null
        edgeSpeedPx = 0f
        dragOffsetY = 0f
        pendingSwapFromIndex = -1
    }

    // 画在手指下的那一夹回视口（长按每一记事件都过这一颗；边缘滚动那一颗靠"补偿 = 滚动量"
    // 把画位钉在原地，不在这儿夹，理由见下面那颗任务上的记录）。
    // 指下那颗量不到时（列表被别处改过 / 还没布局）交回速度归零——不许拿着上一帧的速度自己滚下去，
    // 那正是"我没动、它自己跑"的那一种。
    val clampDraggedRowIntoViewport: () -> Unit = {
        val id = draggedId
        val layout = listState.layoutInfo
        val item = id?.let { key -> layout.visibleItemsInfo.firstOrNull { it.key == key } }
        if (item == null) {
            edgeSpeedPx = 0f
        } else {
            dragOffsetY = clampDragOffsetInsideViewport(
                offsetPx = dragOffsetY,
                itemLayoutOffsetPx = item.offset,
                itemSizePx = item.size,
                viewportStartPx = layout.viewportStartOffset,
                viewportEndPx = layout.viewportEndOffset
            )
        }
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
    // 这一颗**只补位移、不夹视口**：`layoutInfo` 要到下一次布局才反映刚滚掉的那一截，
    // 在这儿夹一次会拿旧槽位算新边界，画出来的位置每帧漂一格；夹的活由 [clampDragOffsetInsideViewport]
    // 在每一记手指事件上做（那里读到的就是上一帧的布局），而"补偿 = 滚动量"这一条本来就把
    // 画出来的位置钉在原地，视口内进得来的、滚动中也出不去。
    // 循环条件带"还在拖 + 还在边缘"：手指抬了、或者那颗已经归零，就不许再多滚一帧。
    LaunchedEffect(dragSession, edgeSpeedPx) {
        val speed = edgeSpeedPx
        if (speed == 0f) return@LaunchedEffect
        while (draggedId != null && edgeSpeedPx != 0f) {
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

    // 备注那一行的**尺寸档与侧滑清除**由 `AdvisorNoteLine` 自己管（一行黄色小字 + 单行省略 +
    // 传入 onSwipeClear 时可像消息一样侧滑删）。旧的 `swipeIsNotATap`（Initial pass 无条件吃事件、
    // 把备注变成切页死区又没有删除出口）随原话第 15 条一起撤：备注行的横滑现在**归备注**（走清除），
    // 纵向滚动交给 [draggable] 的方向门自然放行，不再被误记为"已消费"。
    // 撤掉那道闸门后剩下一格真空：`clickable` 只看抬指在不在自己范围内，而备注行有整行宽
    // ⇒ 宿主没接清除出口时（没有 draggable 接横滑），擦过去就把编辑入口点着。
    // 补回来的这一颗只观察不消费（见 [noteSwipeIsNotATap]），两个分支共用同一份读数与同一个出口。
    val noteSwipeFlag = remember { NoteSwipeFlag() }
    val editNoteFromTap: () -> Unit = { if (!noteSwipeFlag.crossedSlop) onEditNote() }
    if (dialogueDisplayed.isEmpty()) {
        // 空态也坐进同一块圆角底：原话要的是"无消息时这一组（意图 / 仅看本轮）收进消息卡内"，
        // 所以空态分支补上与非空分支同一档容器（SurfaceInset + LoveBrainShape.lg）。
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(SurfaceInset, LoveBrainShape.lg)
                .padding(Spacing.md),
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
            // 只有备注、还没聊天的那一轮：那一行黄字照常钉在最下面，仍可侧滑清除
            //（原话第 15 条——备注不能因为没聊天就整个不见，也不能失去删除出口）
            if (advisorNoteText != null) {
                Spacer(Modifier.height(Spacing.md))
                AdvisorNoteLine(
                    noteText = advisorNoteText,
                    onClick = editNoteFromTap,
                    onSwipeClear = onClearNote,
                    modifier = Modifier.fillMaxWidth().noteSwipeIsNotATap(noteSwipeFlag)
                )
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
                                pendingSwapFromIndex = -1
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
                                // 布局追上上一次交换了才接着发：还站在发那次交换的那一格 = 列表还没重组，
                                // 这一帧既不发第二次重排、也**不扣补偿**（扣了就是凭空瞬移）
                                if (dragSwapIsSettled(slot.index, pendingSwapFromIndex)) {
                                    pendingSwapFromIndex = -1
                                }
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
                                if (swap != null && pendingSwapFromIndex < 0 &&
                                    fromPair != null && toPair != null &&
                                    fromPair.second.id == id
                                ) {
                                    onReorder(fromPair.first, toPair.first)
                                    pendingSwapFromIndex = slot.index
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
                            // ④ 最后把画出来的那一夹回视口：手指划到列表外面（备注行、输入区）时
                            // 长按仍收得到事件，不夹这条就是"消息飞出屏幕"
                            clampDraggedRowIntoViewport()
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
                    // ⚠ 这一颗挂在**条目根节点**上（MessageRow 里它落到 AnimatedVisibility 那一层）：
                    // zIndex 只管同一颗粒度内的兄弟，挂在行内的子节点上就等于没挂——被拖那一条会被
                    // 后面那些不透明气泡整个盖住，"手指底下那条看不见"量的就是这一档。
                    modifier = if (isDragged) Modifier.zIndex(1f) else Modifier.animateItemPlacement()
                )
            }
        }
        // 原话第 15 条："放在最后一条真实消息下面，一行黄色小字"——
        // 挂在 LazyColumn **之外**，所以它既不在聊天顺序里、也拖不进聊天顺序（重排数组碰不到它）。
        // 侧滑清除走 onClearNote（→ ClearNote），被删对象与回调分离：绝不落 onDelete 那条消息链。
        if (advisorNoteText != null) {
            Spacer(Modifier.height(Spacing.sm))
            AdvisorNoteLine(
                noteText = advisorNoteText,
                onClick = editNoteFromTap,
                onSwipeClear = onClearNote,
                modifier = Modifier.fillMaxWidth().noteSwipeIsNotATap(noteSwipeFlag)
            )
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
 *
 * [modifier] 是**条目级**那一份（被手指控制那一条的浮起 / 其余行的位移动画），它落在
 * `AnimatedVisibility` 那一颗粒上——也就是列表条目的根节点。这一档不许搬回行内：
 * `zIndex` 只在同一颗粒度的兄弟之间排序，挂在行内的子节点上等于没挂，被拖那一条会被后面
 * 那些不透明气泡整个盖住（"手指底下那条看不见"）。
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
        // 条目级那三件事（浮起 / 位移动画 / 手势位移）都挂在这一颗粒上：这一颗才是列表条目的
        // 根节点。挂在它下面的 BoxWithConstraints 只在这颗的内部排序，既管不着"压住哪一条"，
        // 位移也归退场动画那一层管——被裁在哪一档 JVM 量不出来（记在台账，真机验）。
        // 位移读取仍留在 graphicsLayer 里：逐帧只重画图层，不重组整棵列表
        //（重组每一次事件都跑一遍 items，正是"看着不跟手"的另一半成因）。
        modifier = modifier.graphicsLayer {
            translationX = swipeX.value
            // 被手指控制那一条按**累计位移**画（不是 index 换算出来的位置）；那颗累计位移
            // 在长按那一支已经过了一遍视口夹持，所以画出来的中心不会离开视口
            translationY = if (isDragged) draggedOffsetY() else 0f
            scaleX = lift
            scaleY = lift
        },
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
