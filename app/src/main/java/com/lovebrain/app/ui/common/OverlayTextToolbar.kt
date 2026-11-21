package com.lovebrain.app.ui.common

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lovebrain.app.core.designsystem.SurfaceCard
import com.lovebrain.app.core.designsystem.TextPrimary

/**
 * overlay 窗口专用 TextToolbar。
 *
 * 悬浮窗 `TYPE_APPLICATION_OVERLAY` 没有 Activity 窗口 token，Compose 默认的系统 ActionMode
 * 挂不上，长按输入框弹不出菜单。这里自绘一个 Popup，四个动作全部复用框架传进来的回调
 * （剪贴板读写由框架那侧的 ClipboardManager 承担），不另起一套输入或选区。
 */
class OverlayTextToolbar(context: Context) : TextToolbar {

    /**
     * 发布给 Compose 的唯一状态：不可变快照。回调与 label 同序构建、随快照一起发布，
     * 点击永远取「渲染出这份 label 的那份快照」里的回调——组合与点击之间框架换入新动作集
     * （如剪切出现）也不会下标错位。
     *
     * 相等判定刻意只比 rect+labels：框架每次 showMenu 都递新 lambda，把回调身份算进去
     * 会让快照恒不相等，每次调用都打脏观察者。
     */
    private class Menu(
        val rect: Rect,
        val labels: List<String>,
        val actions: List<() -> Unit>
    ) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Menu && rect == other.rect && labels == other.labels)

        override fun hashCode(): Int = 31 * rect.hashCode() + labels.hashCode()
    }

    private val menu = mutableStateOf<Menu?>(null, structuralEqualityPolicy())

    /** showMenu 调用计数（快照相等也递增）：宿主据此豁免「手势期间框架重新 showMenu」的收起。 */
    internal var showGeneration: Int = 0
        private set

    // 读 status 必须绕开读观察：框架的 hideSelectionToolbar() 就在 CoreTextField 的重组作用域里
    // 读它，普通读会把那个作用域登记成观察者，随后 showMenu 的写又把它打脏 —— 自激回路。
    override val status: TextToolbarStatus
        get() = Snapshot.withoutReadObservation {
            if (menu.value != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden
        }

    /** 供窗口层在 ACTION_OUTSIDE 判定时读：菜单/手柄显示期间不得释放输入。 */
    val isShowing: Boolean
        get() = status == TextToolbarStatus.Shown

    override fun showMenu(
        rect: Rect,
        onCopy: (() -> Unit)?,
        onPaste: (() -> Unit)?,
        onCut: (() -> Unit)?,
        onSelectAll: (() -> Unit)?
    ) {
        val labels = ArrayList<String>(4)
        val callbacks = ArrayList<() -> Unit>(4)
        onCopy?.let { labels.add("复制"); callbacks.add(it) }
        onCut?.let { labels.add("剪切"); callbacks.add(it) }
        onPaste?.let { labels.add("粘贴"); callbacks.add(it) }
        onSelectAll?.let { labels.add("全选"); callbacks.add(it) }
        showGeneration++
        // 结构相等策略下，rect+labels 没变的赋值不产生新状态记录、不通知任何观察者，
        // 所以这里不必先读再比 —— 那次读反而会把调用方登记成观察者。
        menu.value = Menu(rect, labels, callbacks)
    }

    override fun hide() {
        menu.value = null
    }

    /** 渲染菜单 Popup，必须挂在悬浮窗根组合里才能定位到正确坐标。 */
    @Composable
    fun Content() {
        val snapshot = menu.value ?: return

        // 抬高一档按 dp 经当前 Density 换算，不写死像素：硬编码 44px 在低密度屏抬过头、
        // 在高密度屏几乎贴着选区。
        val liftPx = with(LocalDensity.current) { 44.dp.toPx().toInt() }
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(
                snapshot.rect.left.toInt(),
                (snapshot.rect.top - liftPx).toInt()
            ),
            onDismissRequest = { hide() },
            properties = PopupProperties(
                // 抢焦点会丢掉底下输入框的选区，复制/剪切/粘贴的回调也就没了对象。
                focusable = false,
                // 点外自动收依赖子窗口收到 ACTION_OUTSIDE，而打开菜单的那次长按还没结束，
                // 部分机型会把它判成 OUTSIDE，菜单当场闪退。面板内的点外收起由宿主走
                // 清焦 → 框架失焦收起那条路（见 OverlayTextToolbarHost），不靠这个开关。
                dismissOnClickOutside = false,
                dismissOnBackPress = true
            )
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceCard)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                snapshot.labels.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        color = TextPrimary,
                        modifier = Modifier
                            .clickable {
                                snapshot.actions.getOrNull(index)?.invoke()
                                hide()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/** 悬浮窗根组合持有工具栏实例，便于传给 [OverlayTextToolbarHost] 与窗口层探针。 */
@Composable
fun rememberOverlayTextToolbar(): OverlayTextToolbar {
    val context = LocalContext.current
    return remember { OverlayTextToolbar(context) }
}

/**
 * 给悬浮窗根组合注入工具栏并渲染其菜单：
 * ```
 * OverlayTextToolbarHost(toolbar = rememberOverlayTextToolbar()) { 面板内容 }
 * ```
 */
@Composable
fun OverlayTextToolbarHost(
    toolbar: OverlayTextToolbar,
    content: @Composable () -> Unit
) {
    val focusManager = LocalFocusManager.current
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        Box(
            modifier = Modifier.pointerInput(toolbar, focusManager) {
                // 点面板里非聚焦区域时没人清焦，框架没有收起时机，菜单会一直挂着。
                // 这里被动观察「完整一次轻点」：按下时菜单已开、且手势期间框架没有重新
                // showMenu，松手才 clearFocus——收起仍由框架在失焦时做，不另起选区来源。
                // 不碰 DOWN、也不在框架刚重新 showMenu 的手势上动手：打开菜单的那次长按与
                // 重选区长按都会被豁免，不会回到「两边对着干」的闪烁。
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val showingAtDown = toolbar.isShowing
                    val generationAtDown = toolbar.showGeneration
                    var released = false
                    while (!released) {
                        released = awaitPointerEvent().changes.none { it.pressed }
                    }
                    if (showingAtDown && toolbar.isShowing &&
                        toolbar.showGeneration == generationAtDown
                    ) {
                        // 长按弹菜单时输入框正是「主动焦点」：不带 force 的 clearFocus 只清
                        // 非主动焦点，对这颗等于空操作、菜单继续挂住。force 才交得出去，
                        // 与面板交回输入（LoveBrainPanelScreen 的唯一出口）同一口径。
                        focusManager.clearFocus(force = true)
                    }
                }
            }
        ) {
            content()
            toolbar.Content()
        }
    }
}