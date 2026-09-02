package com.lovebrain.app.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.lovebrain.app.ui.theme.SurfaceCard
import com.lovebrain.app.ui.theme.TextPrimary

/**
 * overlay 窗口专用 TextToolbar。
 *
 * 背景：悬浮窗 [android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] 没有合法的
 * Activity 窗口 token，Compose 默认的 TextToolbar（系统 ActionMode）挂不上 →
 * 长按 [androidx.compose.foundation.text.BasicTextField] 弹不出 复制/粘贴/全选 菜单。
 *
 * 这里自绘一个 Popup 菜单，复制/剪切/粘贴 直接走系统 [ClipboardManager]，
 * 让 overlay 内输入框的长按选词操作恢复正常。
 *
 * 仅用于悬浮面板（[com.lovebrain.app.service.FloatingService]）。
 */
class OverlayTextToolbar(
    private val context: Context
) : TextToolbar {

    /** 当前菜单状态（由 showMenu/hide 维护，供 Compose 侧观察） */
    private var statusState = mutableStateOf(TextToolbarStatus.Hidden)

    /** 待展示的菜单项（label → action），由 showMenu 注入 */
    private val itemsState = mutableStateOf<List<Pair<String, () -> Unit>>>(emptyList())

    /** 菜单锚定区域（窗口坐标） */
    private val rectState = mutableStateOf(Rect.Zero)

    override val status: TextToolbarStatus
        get() = statusState.value

    override fun showMenu(
        rect: Rect,
        onCopy: (() -> Unit)?,
        onPaste: (() -> Unit)?,
        onCut: (() -> Unit)?,
        onSelectAll: (() -> Unit)?
    ) {
        rectState.value = rect
        val items = buildList {
            onCopy?.let { add("复制" to it) }
            onCut?.let { add("剪切" to it) }
            onPaste?.let { add("粘贴" to it) }
            onSelectAll?.let { add("全选" to it) }
        }
        itemsState.value = items
        statusState.value = TextToolbarStatus.Shown
    }

    override fun hide() {
        statusState.value = TextToolbarStatus.Hidden
        itemsState.value = emptyList()
    }

    /**
     * 在 Compose 树里渲染菜单。由 [overlayTextToolbarHost] 调用，
     * 必须挂在悬浮窗的根组合里才能让 Popup 定位到正确坐标。
     */
    @Composable
    fun Content() {
        if (statusState.value != TextToolbarStatus.Shown) return
        val items = itemsState.value
        if (items.isEmpty()) return
        val rect = rectState.value

        // Popup 定位到选区附近：topLeft 像素坐标直接转为 IntOffset
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(rect.left.toInt(), rect.top.toInt() - 44)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceCard)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                items.forEachIndexed { _, (label, action) ->
                    Text(
                        text = label,
                        color = TextPrimary,
                        modifier = Modifier
                            .clickable {
                                action.invoke()
                                hide()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 给悬浮窗根组合注入：overlay TextToolbar 实例 + 渲染其菜单 Popup。
 *
 * 用法：
 * ```
 * CompositionLocalProvider(LocalTextToolbar provides overlayToolbar) {
 *     OverlayTextToolbarHost(toolbar = overlayToolbar) {
 *         // 面板内容
 *     }
 * }
 * ```
 *
 * 返回工具栏实例，便于调用方持有并传给 [OverlayTextToolbarHost]。
 */
@Composable
fun rememberOverlayTextToolbar(): OverlayTextToolbar {
    val context = LocalContext.current
    return remember { OverlayTextToolbar(context) }
}

/**
 * 渲染 overlay TextToolbar 菜单 Popup 的宿主组件。
 * 必须与 [OverlayTextToolbar] 在同一组合作用域内调用。
 */
@Composable
fun OverlayTextToolbarHost(
    toolbar: OverlayTextToolbar,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalTextToolbar provides toolbar
    ) {
        Box {
            content()
            toolbar.Content()
        }
    }
}
