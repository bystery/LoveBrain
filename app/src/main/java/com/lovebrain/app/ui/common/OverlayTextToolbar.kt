package com.lovebrain.app.ui.common

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

    /** 发布给 Compose 的唯一状态：不可变快照，只含锚定区域与有序 label。 */
    private data class Menu(val rect: Rect, val labels: List<String>)

    private val menu = mutableStateOf<Menu?>(null, structuralEqualityPolicy())

    // 回调不进快照：框架每次 showMenu 都新建 lambda，把它们纳入相等判定会让快照恒不相等，
    // 于是每次调用都打脏观察者。与 Menu.labels 同序构建、同批赋值，下标一一对应。
    private var actions: List<() -> Unit> = emptyList()

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
        actions = callbacks
        // 结构相等策略下，rect+labels 没变的赋值不产生新状态记录、不通知任何观察者，
        // 所以这里不必先读再比 —— 那次读反而会把调用方登记成观察者。
        menu.value = Menu(rect, labels)
    }

    override fun hide() {
        actions = emptyList()
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
                // 部分机型会把它判成 OUTSIDE，菜单当场闪退。收起只交给框架与返回键。
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
                                actions.getOrNull(index)?.invoke()
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
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        // 不在这里观察 DOWN 收菜单：选区还活着时框架会紧接着重新 showMenu，两边对着干
        // 就是"菜单/手柄闪一下、长按粘贴菜单来不及点"。收起只由框架（选区塌陷/失焦）负责。
        Box {
            content()
            toolbar.Content()
        }
    }
}