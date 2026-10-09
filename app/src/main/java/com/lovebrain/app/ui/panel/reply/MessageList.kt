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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
     * 横滑的**位移门槛比例**（§8.2 起点档）：这一颗乘行宽，再被下面两道 dp 夹住，
     * 真门槛由 [swipeTravelThresholdPx] 算出来。
     *
     * 旧的 0.4 与"够线就删"那一条判据一起作废（§8.1 点名的就是它：横滑现在覆盖**两种**动作，
     * 一条 abs 判据把它们压成了一个）。0.2 也不是"随手换个百分比"——单靠比例在 300dp 宽的
     * 面板上还是 60dp 行程，所以下面那两根 dp 端点才是真正管住手感的那两根，比例只在大行上收口。
     */
    const val SWIPE_ACTION_THRESHOLD_FRACTION = 0.2f
    /**
     * 尾部那行军师备注（补充）**侧滑清除**的旧那一档，原样留着：它唯一的消费者是
     * `ReplyInput.kt` 的 `AdvisorNoteLine`（本轮不动那份文件）。
     *
     * 消息行**不再**读这一颗（见 [swipeTravelThresholdPx]）。留着它不是留两套消息入口——
     * §8.1 那张表里"补充"那一行本来就只有一个动作（两个方向都是删除），
     * 绝对值判据在那一行上不构成缺陷；它的门槛要不要跟着本轮的位移档降，归管备注行那一席。
     */
    const val SWIPE_DELETE_THRESHOLD_FRACTION = 0.4f
    /** 位移门槛的下限（dp）：行再窄也不许低过一次有意拖动的最短距离 */
    const val SWIPE_MIN_TRAVEL_DP = 24f
    /** 位移门槛的上限（dp）：行再宽也不许拖到那么远（旧 0.4 在长行上要拖 130dp+，就是这一档封顶） */
    const val SWIPE_MAX_TRAVEL_DP = 48f
    /**
     * 快滑档的两端（§8.2 起点，不是平台标准）：末段速度到 [FLICK_MIN_SPEED_DP_PER_SEC]
     * 且**同方向**位移不少于 [FLICK_MIN_TRAVEL_DP] 才算"有意的一甩"。
     * 同方向那一条是硬条件：反向回拉时末端速度符号与位移相反，旧峰值不许误触发。
     */
    const val FLICK_MIN_SPEED_DP_PER_SEC = 600f
    const val FLICK_MIN_TRAVEL_DP = 16f
    /**
     * 换角色那一次"气泡连续移到另一侧"的落位时长（ms）。
     *
     * 与 [EXIT_DURATION_MS] 同一档、同一来源（§4.4 那张表里"弹层约 200ms"那一行的既有基线），
     * 不是新增的一套节奏：这一族屏幕上只有"退场"和"落位"这两个时长，两者同值。
     */
    const val ROLE_SHIFT_DURATION_MS = 200
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
 * 自动滚动滚完一帧之后，被拖那一格**至少**还要留在视口里的像素数（dp 档，UI 侧乘 density 交进
 * [budgetedAutoScrollPx]）。16 是"条目还认得出在被回收的边界之内"那一档：LazyColumn 只看布局边界、
 * 不看 `graphicsLayer` 的 translationY，越过这条线被拖那一条就会被虚拟化掉（手指底下那条凭空不见）。
 * §8 要的是"被拖条目始终可见"，所以这一档挂在滚动预算上，而不是挂在事后纠错上。
 */
internal const val MESSAGE_DRAG_KEEP_VISIBLE_DP = 16

/**
 * 一次横滑**最多**落一个业务动作（§8.1 那张表的三行、两种动作）。
 *
 * `None` 不是一个"动作"，是"这一记不算数"：没够线、反向回拉、行宽还没量到。
 * 旧形状里没有这一颗——那时横过阈值一律 `Delete`，向左向右两种意图被同一条 `abs(offset)` 判据
 * 压成同一件事（§8.1 点名要拆掉的就是那一颗）。
 */
internal enum class SwipeAction { None, SwitchRole, Delete }

/**
 * 尾部那行军师备注（补充）侧滑清除的唯一判据：|拖量| ≥ 行宽 × 0.4 → 清。
 * 行宽还没量到（0）时恒不删——第一帧不许凭空满足阈值。
 *
 * ⚠ 这一颗现在**只**服务 `AdvisorNoteLine`（`ReplyInput.kt`，本轮不许动的文件）。
 * 消息行已经换成 [swipeCommitAction]：§8.1 要拆掉的是"abs(offset) 达阈值就删"这一条判据
 * **在消息侧覆盖两种操作**那个形状，而补充那一行按同一张表两个方向都只有"删除"一件事。
 * 拿它当消息行的判据 = 本轮那一格直接红回去（`MessageRowSwipeDeleteTest` 钉的就是这个）。
 */
internal fun shouldDeleteBySwipe(dragPx: Float, rowWidthPx: Float): Boolean =
    rowWidthPx > 0f && abs(dragPx) >= rowWidthPx * MessageDimens.SWIPE_DELETE_THRESHOLD_FRACTION

/**
 * 「方向 → 动作」的唯一映射（纯函数，**不看阈值、也不看速度**）：
 * 普通消息**向内换角色、向外删除**；`Role.IDEA`（补充那一族）两个方向都是删除。
 *
 * 输入是**起始角色**：手势开始时就定死动作身份，中途不因为角色被改动而换一个动作
 * （§8.2 原话"拖动过程中不提前改角色再把第二次阈值当作另一动作"）。
 * 与"够不够线"分成两颗，是为了让拖动过程中的可见反馈（揭示哪一句）和松手那一刻的落点
 * （到底落不落）读的是同一个方向判据，而不是各算各的。
 *
 * `dragPx == 0` 交回 None：没有方向就没有意图，不许拿"0"当成某个方向的读数。
 */
internal fun swipeActionForDirection(startRole: ChatMessage.Role, dragPx: Float): SwipeAction = when {
    dragPx == 0f -> SwipeAction.None
    // 补充不是对话的一方，它没有"另一边"，所以两个方向都只有删除
    startRole == ChatMessage.Role.IDEA -> SwipeAction.Delete
    else -> {
        val inward = (startRole == ChatMessage.Role.ME && dragPx < 0f) ||
            (startRole == ChatMessage.Role.HER && dragPx > 0f)
        if (inward) SwipeAction.SwitchRole else SwipeAction.Delete
    }
}

/**
 * 位移门槛（px）：`max(24dp, min(48dp, 行宽×20%))`（§8.2 调试起点）。
 *
 * ⚠ 那两根端点是 **dp**，指针给的是 **px**：端点必须先乘 density 换算，否则 density=2 的机器上
 * "48" 其实是 24dp，窄面板与宽面板的判定会各差一倍（§8.2 明写"必须经密度转换"）。
 * 行宽或 density 没量到 ⇒ 交回无限大：第一帧不许凭空满足任何动作（与旧判据 `rowWidthPx > 0`
 * 那道门同一目的，只是这里连快滑档一起挡掉）。
 */
internal fun swipeTravelThresholdPx(rowWidthPx: Float, density: Float): Float {
    if (rowWidthPx <= 0f || density <= 0f) return Float.POSITIVE_INFINITY
    return (rowWidthPx * MessageDimens.SWIPE_ACTION_THRESHOLD_FRACTION).coerceIn(
        MessageDimens.SWIPE_MIN_TRAVEL_DP * density,
        MessageDimens.SWIPE_MAX_TRAVEL_DP * density
    )
}

/**
 * 松手那一帧的唯一判据（纯函数，JVM 可逐值钉）：这一记手势落**哪一个**动作，最多一个。
 *
 * 两条路各挡一档：
 * ① **慢拖过线**：位移达到 [swipeTravelThresholdPx] ⇒ 落方向那一侧的动作；
 * ② **有意快滑**：位移没到线，但同方向位移 ≥16dp 且末段速度 ≥600dp/s（两者都换算过 density）
 *   ⇒ 同样落那一个动作。速度符号与位移不一致（反向回拉后停手）一律不算，旧峰值不许说话。
 * 两条都不满足 ⇒ None ⇒ 回弹，什么都不落。
 *
 * [velocityXPxPerSec] 是水平末段速度（px/s，带符号）。拖动过程中给这一颗传 0：那时快滑档
 * 还没有读数，可见反馈只按位移那一档亮（见 [MessageRow] 里那句 `isSwipeArmed`）。
 */
internal fun swipeCommitAction(
    startRole: ChatMessage.Role,
    dragPx: Float,
    rowWidthPx: Float,
    velocityXPxPerSec: Float,
    density: Float
): SwipeAction {
    val action = swipeActionForDirection(startRole, dragPx)
    if (action == SwipeAction.None) return SwipeAction.None
    if (rowWidthPx <= 0f || density <= 0f) return SwipeAction.None
    if (abs(dragPx) >= swipeTravelThresholdPx(rowWidthPx, density)) return action
    // Kotlin 的 Float 没有 Java 那颗 `signum()`；同向判据写成"都非零且正负号一致"，
    // 速度为零（拖动中途读数还没来）一律算不同向，旧峰值不许说话。
    val sameDirection = dragPx != 0f && velocityXPxPerSec != 0f &&
        (dragPx > 0f) == (velocityXPxPerSec > 0f)
    return if (sameDirection &&
        abs(dragPx) >= MessageDimens.FLICK_MIN_TRAVEL_DP * density &&
        abs(velocityXPxPerSec) >= MessageDimens.FLICK_MIN_SPEED_DP_PER_SEC * density
    ) action else SwipeAction.None
}

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

/**
 * 拖拽中一格在列表里的槽位（展示位 + 布局偏移 + 高度 + 认人的 key），全 px。
 *
 * `key` 是本轮§8 加的那一维：**帧判据要自己认出"哪一格是被拖那一条"**，不能靠 UI 先查好再喂进来——
 * UI 侧那一查（`visibleItemsInfo.firstOrNull { it.key == id }`）正是"读的是上一帧布局"的那半截成因，
 * 把查询搬进纯函数（见 [dragFramePlan]）之后，滚动、交换、夹持三者读的才是**同一份**最新读数。
 * 默认 null：`dragSwapStep` 那一族的夹具与判据都只量槽位几何，不认人。
 */
internal data class DragSlot(
    val index: Int,
    val offsetPx: Int,
    val sizePx: Int,
    val key: String? = null
)

/**
 * 屏上那一行的身份：`index` 与 [DragSlot.index] 同轴（LazyColumn 的条目位），
 * `originalIndex` 是**持有者列表**的下标——发重排只交这个数（与 [onReorder] 的口径一致）。
 */
internal data class DragRow(val index: Int, val originalIndex: Int, val id: String)

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
 *
 * 指导书§8：固定 80dp 边缘区在短视口（约 160dp 或更矮）会覆盖/重叠，中央稍动也触发滚动。
 * 修复：边缘区取 `min(edgeZonePx, viewportHeight / 3)`，保证中间至少有 1/3 视口是安全区。
 * 在 160dp 视口上边缘区压到 ~53dp，中间 ~53dp 不滚；正常视口仍用 80dp。
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
    // 短视口限流：边缘区不超过视口高度的 1/3，确保中央有安全区
    val viewportHeight = viewportEndPx - viewportStartPx
    val effectiveZone = minOf(edgeZonePx, viewportHeight / 3f)
    val toBottom = viewportEndPx - fingerY
    val toTop = fingerY - viewportStartPx
    return when {
        toBottom <= 0f -> MessageDimens.DRAG_EDGE_MAX_SPEED_PX
        toBottom < effectiveZone -> depthSpeed(toBottom, effectiveZone)
        toTop <= 0f -> -MessageDimens.DRAG_EDGE_MAX_SPEED_PX
        toTop < effectiveZone -> -depthSpeed(toTop, effectiveZone)
        else -> 0f
    }
}

/** 离边缘越近越快：把"还剩多远"换成速度，并夹在那两根端点之间 */
private fun depthSpeed(distancePx: Float, edgeZonePx: Float): Float =
    (MessageDimens.DRAG_EDGE_MAX_SPEED_PX * (1f - distancePx / edgeZonePx))
        .coerceAtLeast(MessageDimens.DRAG_EDGE_MIN_SPEED_PX)

// ═══════════ §8 拖拽帧判据：滚动 / 交换 / 夹持读同一份最新布局 ═══════════

/**
 * 一帧拖拽的**全部**输入：最新那一份布局 + 当前那一份记账。
 *
 * 手指帧与自动滚动帧喂的是同一个类型、同一颗算式（[dragFramePlan]），于是§8 点名的两件事
 * 在算式这一侧就没有出口：
 * · "自动滚动只 scrollBy 并加偏移，不检查交换与被拖条目可见性" —— 滚动帧现在**必须**带着
 *   `rows`（刚读到的 `visibleItemsInfo`）来问，问不出可见槽位就一格都不许滚；
 * · "即时偏移补偿与旧 layoutInfo 的夹持混用" —— 位移永远相对**它自己那一份槽位**表达
 *   （见 [DragFramePlan.landingSlot]），旧槽位在这一颗里根本读不到。
 *
 * [fingerY] 为 null = 这一帧没有手指事件（自动滚动帧）：速度不再重算，只维持/交回 0。
 * [requestedScrollPx] = 这一帧想滚的量：手指帧传 0，滚动帧传那颗任务的速度。
 */
internal data class DragFrameInput(
    val rows: List<DragSlot>,
    val displayed: List<DragRow>,
    val draggedId: String,
    val offsetPx: Float,
    val pendingSwapFromIndex: Int,
    val landingSlot: DragSlot?,
    val viewportStartPx: Int,
    val viewportEndPx: Int,
    val fingerY: Float?,
    val edgeZonePx: Float,
    val requestedScrollPx: Float,
    val keepVisiblePx: Int
)

/**
 * 一帧的判定结果：交回 UI 的全是"该写哪几个数、该发哪一发"，UI 侧一条判据都不留。
 *
 * [scrollPx] 是**整数** px（与 `LazyListItemInfo.offset` 同一档），于是"补偿 = 滚动量"逐字成立、
 * 画位一像素都不漂；滚不动的那一截（视口预算用尽）在这一颗里就已经被截掉，不会补进位移。
 * [offsetPx] 相对 [landingSlot]（这一帧发了交换就是新落点，否则就是滚完那一格）表达；
 * [draggedVisible] = false 时 UI 只停、只把条目请回视口，**不**拿任何槽位继续夹或继续算补偿。
 */
internal data class DragFramePlan(
    val scrollPx: Int,
    val offsetPx: Float,
    val reorder: Pair<Int, Int>?,
    val pendingSwapFromIndex: Int,
    val landingSlot: DragSlot?,
    val speedPx: Float,
    val draggedVisible: Boolean
)

/**
 * 滚完这一帧之后，被拖那一格**还剩在视口里**吗（纯函数，§8 的"条目不许凭空丢"就是这一条）。
 *
 * LazyColumn 回收的是**布局边界**出视口的那些条目，而它看不见 `graphicsLayer` 的 translationY：
 * 只补位移、不验槽位的那颗旧任务让被拖那一格的边界一路漂出视口，条目被虚拟化掉 ⇒
 * 用户看到"手指底下那条不见了"（而且累计位移还挂在一个已经不存在的槽位上）。
 * 所以每一帧的滚动量都夹在"这一格至少还留 [keepVisiblePx] 个像素在视口里"那一档预算内。
 */
internal fun budgetedAutoScrollPx(
    requestedPx: Float,
    dragged: DragSlot,
    viewportStartPx: Int,
    viewportEndPx: Int,
    keepVisiblePx: Int
): Int {
    val requested = requestedPx.toInt()
    if (requested == 0) return 0
    if (viewportEndPx <= viewportStartPx) return 0
    return if (requested > 0) {
        // 向下滚 = 槽位往视口上沿走：下沿不许退过 start + keepVisiblePx
        requested.coerceAtMost(
            (dragged.offsetPx + dragged.sizePx - keepVisiblePx - viewportStartPx).coerceAtLeast(0)
        )
    } else {
        // 向上滚 = 槽位往视口下沿走：上沿不许退过 end - keepVisiblePx
        requested.coerceAtLeast(
            (dragged.offsetPx + keepVisiblePx - viewportEndPx).coerceAtMost(0)
        )
    }
}

/**
 * 拖拽一帧的唯一判据（纯函数，JVM 可逐值钉；UI 只把刚读到的那份 layoutInfo 喂进来）。
 *
 * 三条分支按§8 的三处缺陷各挡一档：
 * ① **量不到被拖那一格**（被自动滚出 `visibleItemsInfo`、或列表被别处改花）⇒ 这一帧
 *    不滚、不补、不夹、不交换，速度交回 0（那颗任务自己退出），UI 据 `draggedVisible = false`
 *    把条目请回视口。旧写法在这里会拿着**上一帧**的槽位继续夹、继续加补偿，条目真就没了。
 * ② **上一次交换还没落地**（[dragSwapIsSettled] 判住）⇒ 这一帧读到的槽位是交换**前**的，
 *    滚动补偿与它混用就是"交换尚未落到实际布局时跳动"：这一帧一律不滚，位移只按
 *    [DragFrameInput.landingSlot]（发出交换那帧预测出的落点）夹；落点也没有就原样交回，
 *    **绝不**退回去用旧槽位夹。
 * ③ 正常帧：滚动量先过 ① 的预算，然后整份布局按同一个量平移（滚动只搬内容、不改格序），
 *    交换判据、补偿与视口夹持**全部**读这一份平移后的槽位 ⇒ 三者同一坐标空间、同一帧落地。
 *    交换发出时把位移改挂到新落点上（[dragSwapStep] 交回的那笔补偿），夹持随即改按落点算，
 *    于是"发完交换还拿旧槽位夹"这一档在算式里没有出口。
 */
internal fun dragFramePlan(input: DragFrameInput): DragFramePlan {
    val dragged = input.rows.firstOrNull { it.key == input.draggedId }
    val viewportMeasured = input.viewportEndPx > input.viewportStartPx
    if (dragged == null || !viewportMeasured) {
        return DragFramePlan(
            scrollPx = 0,
            offsetPx = input.offsetPx,
            reorder = null,
            pendingSwapFromIndex = input.pendingSwapFromIndex,
            landingSlot = input.landingSlot,
            speedPx = 0f,
            draggedVisible = dragged != null
        )
    }
    // ② 布局还没追上上一次交换：这一帧只维持手势，一格都不滚
    if (!dragSwapIsSettled(dragged.index, input.pendingSwapFromIndex)) {
        val landing = input.landingSlot
        return DragFramePlan(
            scrollPx = 0,
            offsetPx = if (landing == null) input.offsetPx else clampDragOffsetInsideViewport(
                offsetPx = input.offsetPx,
                itemLayoutOffsetPx = landing.offsetPx,
                itemSizePx = landing.sizePx,
                viewportStartPx = input.viewportStartPx,
                viewportEndPx = input.viewportEndPx
            ),
            reorder = null,
            pendingSwapFromIndex = input.pendingSwapFromIndex,
            landingSlot = landing,
            // 手指帧照旧按手指位置重算；滚动帧维持原速 = 等布局追上那一格再继续滚（不停下来）
            speedPx = input.fingerY?.let {
                edgeAutoScrollSpeedPx(it, input.viewportStartPx, input.viewportEndPx, input.edgeZonePx)
            } ?: input.requestedScrollPx,
            draggedVisible = true
        )
    }
    // ③ 正常帧：这一帧真滚掉的量（整数 px，滚出视口的预算之外一分都不滚）
    val scrollPx = budgetedAutoScrollPx(
        requestedPx = input.requestedScrollPx,
        dragged = dragged,
        viewportStartPx = input.viewportStartPx,
        viewportEndPx = input.viewportEndPx,
        keepVisiblePx = input.keepVisiblePx
    )
    // 整份布局一起平移：格序不变、相邻关系不变，于是交换判据与夹持读的都是**滚动后**的槽位
    val shifted = dragged.copy(offsetPx = dragged.offsetPx - scrollPx)
    val above = input.rows.firstOrNull { it.index == shifted.index - 1 }
        ?.let { it.copy(offsetPx = it.offsetPx - scrollPx) }
    val below = input.rows.firstOrNull { it.index == shifted.index + 1 }
        ?.let { it.copy(offsetPx = it.offsetPx - scrollPx) }
    var offsetPx = input.offsetPx + scrollPx
    var reorder: Pair<Int, Int>? = null
    var nextPending = -1
    var landing: DragSlot? = null
    val swap = dragSwapStep(shifted, offsetPx, above, below)
    val from = input.displayed.getOrNull(shifted.index)
    val to = swap?.let { input.displayed.getOrNull(it.newIndex) }
    // 只有"这一格此刻确实还是被拖那一条、而且两侧都在屏上那几行里"才发重排：
    // 相邻格是尾部备注那一行（`displayed` 读不到）时宁可不动，也不按旧下标发一次假交换
    if (swap != null && from != null && to != null && from.id == input.draggedId) {
        reorder = from.originalIndex to to.originalIndex
        offsetPx += swap.offsetCompensationPx
        nextPending = shifted.index
        landing = DragSlot(
            index = swap.newIndex,
            offsetPx = shifted.offsetPx - swap.offsetCompensationPx.toInt(),
            sizePx = shifted.sizePx,
            key = input.draggedId
        )
    }
    // 夹持按**位移所在那一格**算：发了交换就是落点，没发就是滚完的当前格（不许混旧槽位）
    val clampAgainst = landing ?: shifted
    val speed = input.fingerY?.let {
        edgeAutoScrollSpeedPx(it, input.viewportStartPx, input.viewportEndPx, input.edgeZonePx)
    } ?: if (scrollPx != 0) input.requestedScrollPx else 0f
    return DragFramePlan(
        scrollPx = scrollPx,
        offsetPx = clampDragOffsetInsideViewport(
            offsetPx = offsetPx,
            itemLayoutOffsetPx = clampAgainst.offsetPx,
            itemSizePx = clampAgainst.sizePx,
            viewportStartPx = input.viewportStartPx,
            viewportEndPx = input.viewportEndPx
        ),
        reorder = reorder,
        pendingSwapFromIndex = nextPending,
        landingSlot = landing,
        speedPx = speed,
        draggedVisible = true
    )
}

private fun LazyListItemInfo.asDragSlot() = DragSlot(index, offset, size, key as? String)

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
    onClearNote: (() -> Unit)? = null,
    // §8.1 方向映射里"向内"那一条的落点（宿主投 `ComposerStore.Intent.SwitchMessageRole`，
    // 交的是**这一条**的稳定 id）。null = 宿主没接换角色出口 ⇒ 向内那一侧**什么都不落**、
    // 也不揭示那一句（不许退回"横滑一律删"那一档：留着两套入口正是本轮要拆的东西）。
    onSwitchRole: ((String) -> Unit)? = null
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
    // 这一行从**它自己那一份槽位**起累计走了多少 px —— 直接写进 translationY。
    // 这一颗永远只由 [dragFramePlan] 交回的读数写（槽位与位移成对，不许各拿一份旧账）
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    // 每一次长按换一个 session 号：自动滚动那颗任务只认这一个键
    var dragSession by remember { mutableIntStateOf(0) }
    // 当帧的边缘滚动速度（px/帧）。0 = 不在边缘 = 那颗任务立刻退出
    var edgeSpeedPx by remember { mutableFloatStateOf(0f) }
    // 上一次交换发出时，被拖那一条站的那一格；< 0 = 没有待落地的交换（见 [dragSwapIsSettled]）
    var pendingSwapFromIndex by remember { mutableIntStateOf(-1) }
    // 上面那笔累计位移**挂在哪一格**上：发出交换那一帧预测出的落点槽位，布局追上之前一直用它。
    // 它与 [pendingSwapFromIndex] 是同一件事的两半（从哪格发的 / 该落到哪格），成对写、成对清。
    var pendingSwapLanding by remember { mutableStateOf<DragSlot?>(null) }

    val density = LocalDensity.current.density

    val endDrag: () -> Unit = {
        draggedId = null
        edgeSpeedPx = 0f
        dragOffsetY = 0f
        pendingSwapFromIndex = -1
        pendingSwapLanding = null
    }

    // 同一份列表派生两件事：聊天行只有 HER/ME 两列，且**带着原始下标**——onReorder 的
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

    // 被拖那一条量不到最新槽位时（被别的改动搬走、或已经滚出 visibleItemsInfo）把它请回视口：
    // 消息不丢、手势不断。这一颗只做"搬回眼里"这一件事——夹持与补偿都不在这儿发生
    // （[dragFramePlan] 的①那一支已经把这帧的滚动、补偿、交换全部停掉了）。
    val dragScope = rememberCoroutineScope()
    // 同一颗也负责 §8.3 的第四判据"列表发生有效变化后清理拖动态"：被拖那一条**已从数据里
    // 消失**（拖拽途中被删除/被本轮消耗）时不是"搬回眼里"能解决的——target<0 就地结束这次
    // 拖拽，五笔状态一起清。旧写法只请回不消失的：条目真没了就什么都不做，`draggedId` 悬住，
    // 全行的 `reorderActive` 把横滑一直压到抬指才松（L1 复核挑中）。滚出视口≠消失：
    // 那种 target≥0，走的还是"搬回眼里"那一支。
    val reshowDraggedRow: (String) -> Unit = { id ->
        val target = currentDialogue.indexOfFirst { it.second.id == id }
        if (target >= 0) dragScope.launch { listState.scrollToItem(target) }
        else endDrag()
    }

    /**
     * 拖拽一帧的唯一出口（§8）：**手指帧与自动滚动帧都只走这一颗**，判据一条都不留两处。
     * 每次都现读 `listState.layoutInfo` 那一份（最新）布局喂进 [dragFramePlan]，于是滚动、
     * 交换、夹持三者读的是同一帧的槽位；发出去的重排、闸门键与落点都只在这一颗里写。
     *
     * [fingerY] 传 null = 这一帧没有手指事件（自动滚动帧）：速度由那颗任务维持，不在这里重算。
     * 交回这一帧真该滚的量（整数 px），只有那颗任务用它去 `scrollBy`。
     */
    val runDragFrame: (Float?, Float) -> Int = { fingerY, requestedScrollPx ->
        val id = draggedId
        val layout = listState.layoutInfo
        val plan = dragFramePlan(
            DragFrameInput(
                rows = layout.visibleItemsInfo.map { it.asDragSlot() },
                displayed = currentDialogue.mapIndexed { position, pair ->
                    DragRow(index = position, originalIndex = pair.first, id = pair.second.id)
                },
                draggedId = id.orEmpty(),
                offsetPx = dragOffsetY,
                pendingSwapFromIndex = pendingSwapFromIndex,
                landingSlot = pendingSwapLanding,
                viewportStartPx = layout.viewportStartOffset,
                viewportEndPx = layout.viewportEndOffset,
                fingerY = fingerY,
                edgeZonePx = MessageDimens.DRAG_EDGE_ZONE_DP * density,
                requestedScrollPx = requestedScrollPx,
                keepVisiblePx = (MESSAGE_DRAG_KEEP_VISIBLE_DP * density).toInt()
            )
        )
        // 位移只吃算式交回的那一个数（滚动补偿、交换补偿与视口夹持都在里头按同一帧槽位算好了）
        dragOffsetY = plan.offsetPx
        pendingSwapFromIndex = plan.pendingSwapFromIndex
        pendingSwapLanding = plan.landingSlot
        plan.reorder?.let { (from, to) ->
            onReorder(from, to)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        edgeSpeedPx = plan.speedPx
        // ①"条目凭空丢"这一档：量不到最新槽位 ⇒ 不拿旧布局继续算，改为把被拖那一条搬回视口
        if (!plan.draggedVisible && id != null) reshowDraggedRow(id)
        plan.scrollPx
    }

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
    // 每一帧都先过 [runDragFrame]：滚多少、被拖那一格此刻还看不看得见、这一帧该不该发交换、
    // 位移该挂在哪一格上夹回视口，全是那一颗**读最新那一份 layoutInfo** 之后交回的读数。
    // 旧写法在这一颗里只 `scrollBy` + 加偏移：条目被滚出 `visibleItemsInfo` 还在继续补、继续拿
    // 上一帧的槽位算账（=§8 点名的第一处缺陷），交换更要等下一次手指事件才纠错（第二处）。
    // 滚不动的那一截（列表已到顶/到底）按**真滚掉的量**退回来：补偿永远等于实际滚动量，画位不漂。
    // 循环条件带"还在拖 + 还在边缘"：手指抬了、那颗归零、或被拖那一条量不到，都不许再多滚一帧。
    LaunchedEffect(dragSession, edgeSpeedPx) {
        if (edgeSpeedPx == 0f) return@LaunchedEffect
        while (draggedId != null && edgeSpeedPx != 0f) {
            val planned = runDragFrame(null, edgeSpeedPx)
            val scrolled = if (planned != 0) listState.scrollBy(planned.toFloat()) else 0f
            if (scrolled != planned.toFloat()) dragOffsetY += scrolled - planned
            delay(MessageDimens.DRAG_SCROLL_FRAME_MS)
        }
    }

    // 删除的两条路（横滑**向外**到阈值 / 读屏自定义动作）都只认 id，而且**走同一颗 arm**。
    // 方向一律显式交进来，`swipeExitDirs` 与 `deletingIds` 永远是同一次写入的两笔：
    // 旧写法读屏那一支只置 `deletingIds`、方向靠退场里的 `?: 1` 兜底，于是"同一条消息，
    // 横滑删是往左滑出、读屏删是往右滑出"——两条路各存半份状态，判据也只覆盖得了一条。
    // 横滑的**向内**那一侧不走这里（它落 [onSwitchRole]，同一条气泡换边、不删、不建）。
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

    // 聊天行与那一行备注同坐在这块旧版圆角底上
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceInset, LoveBrainShape.lg)
            .padding(Spacing.md)
    ) {
        // 指导书§12.1 R15：备注随最后消息共同滚动，不钉视口底部。
        // 备注作为 LazyColumn 的 footer item，随消息一起滚动；
        // 拖拽重排只在 itemsIndexed 的聊天行上起势，footer 不参与重排。
        val dialogueIds = remember(dialogueDisplayed) {
            dialogueDisplayed.map { it.second.id }.toHashSet()
        }
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
                            // 只认聊天行的 id（footer 的 key 不在 dialogueIds 里，不起势）
                            val hitId = hitItem?.key as? String
                            if (hitId != null && hitId in dialogueIds) {
                                // 认人只认 id：随后每一次交换都只改格号，手指底下的那颗不变
                                draggedId = hitId
                                dragOffsetY = 0f
                                edgeSpeedPx = 0f
                                pendingSwapFromIndex = -1
                                pendingSwapLanding = null
                                dragSession++
                                // 长按这一下手震一次；此后只在真的换了一格时再震（不每像素震）
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (draggedId == null) return@detectDragGesturesAfterLongPress
                            // ① 先按手指走：这一帧的位移原样累加，不"等交换了才动"
                            dragOffsetY += dragAmount.y
                            // ② 剩下的全是那一颗帧算式的事：现读最新一份布局，在同一坐标空间里
                            //    判交换（含补偿）、把位移夹回视口、算边缘速度。手指帧不滚（传 0），
                            //    但读的是与滚动帧**同一颗** [dragFramePlan] ⇒ 没有"只在手指事件里
                            //    拿上一帧布局夹一次"这种半截账了。
                            runDragFrame(change.position.y, 0f)
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
                    // 横滑落点：向外 = 删除（走 arm→退场→onDelete，交 id + 退场方向），
                    // 向内 = 换角色（只交这一条的 id 给宿主，正文/顺序/元数据由持有列表的一方原样保留）。
                    // 两条路由行内那一颗 [swipeCommitAction] 二选一，一次手势只会走到其中一支。
                    onDeleteArmed = { dir -> armDelete(msg.id, dir) },
                    onSwitchRole = onSwitchRole?.let { owner -> { -> owner(msg.id) } },
                    onExitFinished = { finishDelete(msg.id) },
                    // 被手指控制的那一行**不许**再吃 placement 动画：它会对着手指正在占的那一格反向插值，
                    // 于是"拖到一半弹回去"。其余行照常 animateItemPlacement，看得出位置让开了。
                    // ⚠ 这一颗挂在**条目根节点**上（MessageRow 里它落到 AnimatedVisibility 那一层）：
                    // zIndex 只管同一颗粒度内的兄弟，挂在行内的子节点上就等于没挂——被拖那一条会被
                    // 后面那些不透明气泡整个盖住，"手指底下那条看不见"量的就是这一档。
                    modifier = if (isDragged) Modifier.zIndex(1f) else Modifier.animateItemPlacement()
                )
            }
            // 指导书§12.1 R15：备注作为 LazyColumn footer 随消息共同滚动。
            // 不在 itemsIndexed 里 → 拖拽重排碰不到它；
            // 侧滑清除走 onClearNote（→ ClearNote），被删对象与回调分离：绝不落 onDelete 那条消息链。
            if (advisorNoteText != null) {
                item(key = "advisor_note_footer") {
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
    }
}

/**
 * 一条真实对话的整行：HER 靠左白底、ME 靠右微信绿，底色只在**气泡**身上，整行只是手势与锚点。
 * 可见的「她/我」标签与尾部 ❌ 都撤了——角色改由位置 + 读屏标签（[contentDescription]）表达，
 * 删除只剩两条路：横滑**向外**、读屏自定义动作。屏幕默认不放叉号（第5节第2条）。
 *
 * §8.1 之后横滑有两种动作，按**起始角色**分：向内 = 换成对方（同一条气泡连续移到另一侧，
 * id/正文/顺序/时间戳都不动，也不弹确认框），向外 = 删除（走退场）。揭示当前动作的短标签
 * 画在气泡让开的那一侧，到线之后加重（[isSwipeArmed]）。
 *
 * 三轴分账（第3节第1条）：
 *  · 可见尺寸 = 气泡内容宽（上限一行的八成）+ 横 12 / 竖 6 内边距；
 *  · 触摸热区 = 整行（全宽、含气泡上下那点余量），单击编辑挂在这一层；
 *  · 相邻布局 = 条目间距 4dp。
 * 旧的"整行垫到 48dp 可见高"这一档**本次主动放弃**：它买的是版式高度而不是可点性，
 * 而用户合同要的正是把条目缩下来（取舍记在交接报告，读屏替代出口是那颗自定义删除动作）。
 *
 * 手势分界（§8.4 那三行的消息侧）：按下后先横移越过触控 slop → 这一行水平 draggable 起势
 * （长按计时器被移动取消，"快滑 = 本条的动作候选"）；按住不动到长按阈值 → 重排接管并消费后续
 * 事件，draggable 的 enabled 同时被 reorderActive 关掉；首个方向是纵向 → 水平拖不起势，事件留给
 * 列表滚动；没越过 slop 的起落 → 仍是原来的单击编辑。垂直浏览因此不会被误判成删除或换角色。
 * 横滑在这一行**自己**的孩子节点上消费，父层（回复/谈心切页）拿不到已经越轴的移动量。
 *
 * [modifier] 是**条目级**那一份（被手指控制那一条的浮起 / 其余行的位移动画），它落在
 * `AnimatedVisibility` 那一颗粒上——也就是列表条目的根节点。这一档不许搬回行内：
 * `zIndex` 只在同一颗粒度的兄弟之间排序，挂在行内的子节点上等于没挂，被拖那一条会被后面
 * 那些不透明气泡整个盖住（"手指底下那条看不见"）。
 *
 * [onSwitchRole] 为 null = 宿主没接换角色出口：向内那一侧既不揭示也不落（不许退回"横滑一律删"）。
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
    onDeleteArmed: (Int) -> Unit,
    onSwitchRole: (() -> Unit)?,
    onExitFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    // 行内横向位移：拖到哪画到哪（拖动期间 snapTo 追手，§4.4"不用缓动追手"那一行）；
    // 松手未达阈值弹回 0，落了换角色也弹回 0（只有删除那一条把行交给退场动画）
    val swipeX = remember(msg.id) { Animatable(0f) }
    var rowWidthPx by remember(msg.id) { mutableFloatStateOf(0f) }
    // 气泡本体的宽：换角色那一次要搬的距离 = 行内容宽 - 气泡宽（不量气泡就不知道搬多少）
    var bubbleWidthPx by remember(msg.id) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current.density
    val lift by animateFloatAsState(
        targetValue = if (isDragged) MessageDimens.DRAGGED_SCALE else 1f,
        label = "dragScale"
    )
    // 「换角色 = 同一条气泡连续移到另一侧」那一档的唯一动画（与 lift 同一颗 animateFloatAsState，
    // 不是新起一套动画体系）。角色没变时 `side` 与角色档相等 ⇒ 差值 0 ⇒ **稳态一个像素都不搬**，
    // 版式与这一档加进来之前逐像素相同；只有角色刚落那一档 tween 里差值非零，
    // 于是气泡从它原来那一侧画起、滑到另一侧，中间不消失、也不新建条目（id 全程没变）。
    val sideTarget = if (msg.role == ChatMessage.Role.ME) 1f else 0f
    val side by animateFloatAsState(
        targetValue = sideTarget,
        animationSpec = tween(MessageDimens.ROLE_SHIFT_DURATION_MS),
        label = "bubbleSide"
    )
    // draggable 的 state 只 remember 一次，里面读的那几样要走"最新一份"，
    // 否则重排接管后这一行还在偷偷累积滑动量
    val blocked by rememberUpdatedState(reorderActive || isDeleting || isDragged || rowWidthPx <= 0f)
    val latestWidth by rememberUpdatedState(rowWidthPx)
    val latestDeleteArmed by rememberUpdatedState(onDeleteArmed)
    val latestSwitchRole by rememberUpdatedState(onSwitchRole)
    val latestRole by rememberUpdatedState(msg.role)
    val latestDensity by rememberUpdatedState(density)
    // 本次手势的**起始角色**：draggable 起势那一刻抄一份，抬指就作废。
    // 它同时是"这一记手势还活着"那面旗（揭示标签读这一颗，落动作之后不再亮）。
    // 之后就算列表把这一条的角色改了，这一记也仍按开始时的身份决定"向内/向外"
    // （§8.2 点名要挡的那一档），而且一次手势只落一个动作——中途不换动作、不换阈值重算第二遍。
    var gestureStartRole by remember(msg.id) { mutableStateOf<ChatMessage.Role?>(null) }

    // ── §8.1 的可见反馈：滑动时揭示当前动作，到线之后状态更明确 ──────────────
    // 位移读数留在 derivedStateOf 里：只有**方向符号**与**到没到线**这两件事变化才重组，
    // 逐像素重组是这一文件一直避开的那一档（见 AnimatedVisibility 上那条 graphicsLayer 注释）。
    // ⚠ 三颗都 key 在 swipeX 上：那颗 Animatable 是 `remember(msg.id)` 的，
    //   不 key 就会在条目换人时还闭着**上一颗** Animatable 读数（读着一个不存在的手势）。
    val dragIsLeftward by remember(swipeX) { derivedStateOf { swipeX.value < 0f } }
    val isSwipeRevealed by remember(swipeX) { derivedStateOf { swipeX.value != 0f } }
    val isSwipeArmed by remember(swipeX) {
        derivedStateOf {
            // 速度传 0：快滑那一档要到松手才有读数，拖动过程中"到线"只认位移这一条
            swipeCommitAction(latestRole, swipeX.value, latestWidth, 0f, latestDensity) !=
                SwipeAction.None
        }
    }
    val revealedAction = swipeActionForDirection(msg.role, if (dragIsLeftward) -1f else 1f)
    // 揭示哪一句 = 方向那一侧的动作，且那一侧必须有主人：
    // 换角色没接线时不揭示（不能承诺一句宿主接不住的动作，与"没接线不留半截手势"同一规矩），
    // 向内那一侧这时既不亮也不落，一律回弹。
    val revealLabelRes: Int? = when {
        // 手势不在进行中（抬指之后位移还在往回走）⇒ 不亮：那一句已经不算数了
        gestureStartRole == null -> null
        !isSwipeRevealed -> null
        revealedAction == SwipeAction.Delete -> R.string.a11y_action_delete
        revealedAction == SwipeAction.SwitchRole && onSwitchRole != null ->
            if (msg.role == ChatMessage.Role.ME) R.string.swipe_action_to_her
            else R.string.swipe_action_to_me
        else -> null
    }

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
                    // 起势那一刻把**起始角色**抄死：整记手势的方向→动作都按它算（§8.2）
                    onDragStarted = { gestureStartRole = latestRole },
                    // velocity = 水平末段速度（px/s，带符号）：只有快滑那一档用它，而且必须与位移同向
                    onDragStopped = { velocityPxPerSec ->
                        val startRole = gestureStartRole ?: latestRole
                        // 抬指即作废这一记的身份抄本：它同时是"手势还活着"的那面旗
                        // （揭示标签读它，落动作之后不许再亮着一句已经不算数的动作）
                        gestureStartRole = null
                        if (!blocked) {
                            val dragPx = swipeX.value
                            // 唯一判据：这一记落哪个动作，最多一个（向内换角色 / 向外删除 / 不算数）
                            val action = swipeCommitAction(
                                startRole = startRole,
                                dragPx = dragPx,
                                rowWidthPx = latestWidth,
                                velocityXPxPerSec = velocityPxPerSec,
                                density = latestDensity
                            )
                            when (action) {
                                // 向外：记下退场方向，走退场→onDelete 那一条路
                                SwipeAction.Delete -> latestDeleteArmed(if (dragPx < 0f) -1 else 1)
                                // 向内：同一条气泡换到另一侧（id/正文/顺序/元数据由持有者原样保留，
                                // 不弹确认框，也不碰输入框当前角色与捕获默认角色）
                                SwipeAction.SwitchRole -> latestSwitchRole?.invoke()
                                // 没够线 / 反向回拉：什么都不落
                                SwipeAction.None -> Unit
                            }
                            // 除删除那一条（行交给退场带走）之外都把手势位移弹回 0：
                            // 换角色那一路用与落位同一档 tween，两条位移合起来才看得见"连续移过去"
                            if (action != SwipeAction.Delete && dragPx != 0f) {
                                scope.launch {
                                    if (action == SwipeAction.SwitchRole) {
                                        swipeX.animateTo(
                                            0f,
                                            tween(MessageDimens.ROLE_SHIFT_DURATION_MS)
                                        )
                                    } else {
                                        swipeX.animateTo(0f)
                                    }
                                }
                            }
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
            // 行内容宽（px，已经扣掉左右各 4dp 的行留白）：**在组合期间**取一次存成局部数，
            // 不在 graphicsLayer 那种延后执行的 lambda 里读 `constraints`——
            // BoxWithConstraints 的 scope 属性挂在会被复用的对象上，延后读到的可能不是这一格
            val innerWidthPx = constraints.maxWidth.toFloat()
            // §8.1 那句"滑动时揭示当前动作的短标签"：画在气泡让开的那一侧
            // （与拖动方向相反的那一头，那一头才是被让出来的地方），到线之后颜色与字重一起加重。
            // 它刻意排在气泡**之前**=画在气泡底下，也不参与读屏（clearAndSetSemantics：
            // 这一行念的还是那一句角色公告 + 那一条删除动作，没多出来的话）。
            revealLabelRes?.let { res ->
                Text(
                    text = stringResource(res),
                    color = when {
                        !isSwipeArmed -> TextHint
                        revealedAction == SwipeAction.Delete -> Error
                        else -> Primary
                    },
                    style = AppTypography.labelSmall,
                    fontWeight = if (isSwipeArmed) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .align(if (dragIsLeftward) Alignment.CenterEnd else Alignment.CenterStart)
                        .clearAndSetSemantics { }
                        .padding(horizontal = Spacing.md)
                )
            }
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth * MessageDimens.BUBBLE_MAX_WIDTH_FRACTION)
                    // 换角色那一次的"连续移到另一侧"：稳态差值 = 0 ⇒ 一个像素都不搬，
                    // 只有角色刚落那一档里从它原来那一侧画起、滑过去（要搬的距离 = 行内容宽 - 气泡宽）
                    .graphicsLayer {
                        val crossPx = innerWidthPx - bubbleWidthPx
                        translationX =
                            if (crossPx > 0f && crossPx.isFinite()) (side - sideTarget) * crossPx
                            else 0f
                    }
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
                    // 量在 padding **外面**这一档：要的是气泡那只盒子的宽（含横 12 内边距），
                    // 不是正文的宽——搬多少按画出来的那只盒子算
                    .onSizeChanged { bubbleWidthPx = it.width.toFloat() }
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
