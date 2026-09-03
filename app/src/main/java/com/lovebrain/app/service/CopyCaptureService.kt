package com.lovebrain.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.lovebrain.app.AppConfig
import com.lovebrain.app.data.EventBus
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.util.L
import java.io.File

/**
 * 无障碍服务 v4：感知任意 App 里"长按消息"的动作，实现"复制多条 → 自动积累"。
 *
 * 改进：
 * - 通过 EventBus（SharedFlow）发送捕获的消息，不再直接调用 FloatingService 静态方法
 * - 使用 AppConfig 常量
 * - ：消息捕获总开关（captureEnabled）在事件入口前置判断；不再主动 startService 重启悬浮窗
 * - ：捕获链路本地诊断文件（capture_diag.log），只记类型/长度/毫秒，不记内容
 */
class CopyCaptureService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: CopyCaptureService? = null

        @Volatile
        var isRunning: Boolean = false

        /** 诊断文件名 */
        private const val DIAG_FILE = "capture_diag.log"
        /** 诊断文件大小上限 */
        private const val DIAG_MAX_BYTES = 200 * 1024L
    }

    private var pendingContent: String? = null
    private var pendingTime = 0L

    /** SecurePrefs 实例（用于读取 captureEnabled 开关） */
    private var securePrefs: SecurePrefs? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        securePrefs = SecurePrefs(this)
        L.init(this)
        L.w("CopyCaptureService connected v4 (EventBus, long-press capture)")
        appendDiag("SERVICE_CONNECTED")
    }

    override fun onDestroy() {
        // 终版：只清理静态状态，不干预无障碍 enabled 状态。
        // （用户实测"不锁定卡片"时系统正常回收，无需任何附加机制）
        instance = null
        isRunning = false
        super.onDestroy()
    }

    /** 追加诊断记录：格式 `uptimeMs|TAG|detail`，仅记类型/布尔/长度/毫秒 */
    private fun appendDiag(line: String) {
        runCatching {
            val file = File(filesDir, DIAG_FILE)
            if (file.exists() && file.length() > DIAG_MAX_BYTES) {
                // 超过 200KB：清空重写（只保留当前这行）
                file.writeText("")
            }
            val ts = SystemClock.uptimeMillis()
            file.appendText("$ts|$line\n")
        }
    }

    /**
     * A2 修复：递归遍历无障碍节点树，收集所有非空文本。
     * 新版微信消息文本常挂在子节点上，长按的容器节点本身不带字 → 直接取 event.text 取不到。
     * 深度限制 4 层防性能问题，取最长的一条（最可能是完整消息内容）。
     *
     * 注意：入参 node（通常 = event.source）的生命周期由系统管理，调用方不应 recycle 它。
     * 本方法只 recycle 自己创建的子节点（node.getChild(i)）。
     * 本方法仅在 TYPE_VIEW_LONG_CLICKED 事件中调用，与 collectAllTextFromTree（WINDOW 事件）
     * 不会在同一次事件中执行，不存在对同一子节点重复 recycle 的问题。
     */
    private fun collectTextFromChildren(node: AccessibilityNodeInfo?, depth: Int = 0, maxDepth: Int = 4): String? {
        if (node == null || depth > maxDepth) return null
        var best: String? = null
        node.text?.toString()?.trim()?.let { t ->
            if (t.isNotEmpty()) best = t
        }
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            val childText = collectTextFromChildren(child, depth + 1, maxDepth)
            if (childText != null) {
                val currentBest = best
                if (currentBest == null || childText.length > currentBest.length) {
                    best = childText
                }
            }
            child.recycle()
        }
        return best
    }

    /**
     * A3 修复：递归收集节点树中所有文本（用于弹窗菜单关键词匹配）。
     * 弹窗菜单项"复制"/"转发"等分散在各子节点，直取 event.text 经常取不到 → 菜单匹配失败。
     * 返回所有文本用 "|" 拼接，供 contains 关键词校验。
     *
     * 注意：入参 node（通常 = event.source）的生命周期由系统管理，调用方不应 recycle 它。
     * 本方法只 recycle 自己创建的子节点。仅在 TYPE_WINDOW_STATE_CHANGED 事件中调用，
     * 与 collectTextFromChildren（LONGCLICK 事件）不会在同一次事件中执行。
     */
    private fun collectAllTextFromTree(node: AccessibilityNodeInfo?, depth: Int = 0, maxDepth: Int = 4): String {
        if (node == null || depth > maxDepth) return ""
        val sb = StringBuilder()
        node.text?.toString()?.trim()?.let { if (it.isNotEmpty()) sb.append(it).append("|") }
        node.contentDescription?.toString()?.trim()?.let { if (it.isNotEmpty()) sb.append(it).append("|") }
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            sb.append(collectAllTextFromTree(child, depth + 1, maxDepth))
            child.recycle()
        }
        return sb.toString()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
      try {
        // 支持全部 App（不再限定微信/抖音），后续逻辑已有文本特征校验兑底
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // 忽略自身进程的事件

        val type = event.eventType

        //  问题 4②：消息捕获总开关前置检查 —— 每次事件读取最新值
        val capEnabled = securePrefs?.captureEnabled ?: true
        if (!capEnabled) {
            val typeTag = when (type) {
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "LONGCLICK"
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW"
                else -> "TYPE$type"
            }
            appendDiag("SWITCH_OFF|type=$typeTag")
            return
        }

        val texts = ArrayList<String>()
        runCatching {
            event.text?.forEach { it?.let { t -> texts.add(t.toString()) } }
            event.contentDescription?.let { texts.add("evDesc:" + it.toString()) }
            event.source?.contentDescription?.let { texts.add("srcDesc:" + it.toString()) }
        }
        var joined = texts.joinToString("|")
        // A3 修复：弹窗菜单项文本常在子节点里——直取不含"复制"时补查子节点树
        if (!joined.contains("复制")) {
            runCatching {
                val childTexts = collectAllTextFromTree(event.source)
                if (childTexts.contains("复制")) {
                    texts.add("tree:$childTexts")
                    joined = texts.joinToString("|")
                    appendDiag("WINDOW_MENU_FROM_TREE|matched")
                }
            }
        }
        val typeName = when (type) {
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "LONGCLICK"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW"
            else -> "TYPE$type"
        }
        //  隐私红线：事件文本不落日志，只记类型与长度
        L.w("event $typeName: len=${joined.length}")

        when (type) {
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                var content = event.contentDescription?.toString()?.trim()
                if (content.isNullOrEmpty()) {
                    content = event.text?.firstOrNull { !it.isNullOrEmpty() }?.toString()?.trim()
                }
                if (content.isNullOrEmpty()) {
                    content = runCatching {
                        event.source?.text?.toString()?.trim()
                    }.getOrNull()
                }
                // A2 修复：三处都取不到时，递归遍历子节点树取最长文本
                if (content.isNullOrEmpty()) {
                    content = runCatching {
                        collectTextFromChildren(event.source)
                    }.getOrNull()
                    if (!content.isNullOrEmpty()) {
                        appendDiag("LONGCLICK_TEXT_FROM_CHILDREN|len=${content.length}")
                    }
                }
                if (!content.isNullOrEmpty() && content.length >= 1) {
                    pendingContent = content
                    pendingTime = SystemClock.uptimeMillis()
                    L.w("long-press stored pending: len=${content.length}")
                    appendDiag("LONGCLICK_ARRIVE|len=${content.length}")
                } else {
                    // A1 修复：提取失败时必须清空 pending，否则旧值残留到下次捕获造成 off-by-one
                    pendingContent = null
                    pendingTime = 0L
                    appendDiag("LONGCLICK_ARRIVE|len=0|noTextFound|pendingCleared")
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val isMessageMenu = joined.contains("复制") && (
                    joined.contains("转发") || joined.contains("删除") ||
                    joined.contains("回复") || joined.contains("多选") ||
                    joined.contains("收藏") || joined.contains("举报") ||
                    joined.contains("引用")
                )
                val pending = pendingContent
                val sinceLongClick = if (pendingTime > 0L)
                    SystemClock.uptimeMillis() - pendingTime else -1L
                if (pending != null) {
                    val menuMatched = isMessageMenu &&
                        SystemClock.uptimeMillis() - pendingTime < AppConfig.LONG_PRESS_TIMEOUT_MS
                    appendDiag("WINDOW_ARRIVE|menuMatch=$menuMatched|sinceLong=${sinceLongClick}ms|pendingLen=${pending.length}")
                } else {
                    appendDiag("WINDOW_ARRIVE|noPending|sinceLong=${sinceLongClick}ms")
                }
                if (isMessageMenu && pending != null &&
                    SystemClock.uptimeMillis() - pendingTime < AppConfig.LONG_PRESS_TIMEOUT_MS
                ) {
                    L.w(">>> message menu confirmed, capture len=${pending.length}")
                    appendDiag("CAPTURE_OK|len=${pending.length}")
                    //  问题 4①：不再主动 startService 重启悬浮窗，
                    // 仅暂存 pendingMessage，由用户下次手动打开悬浮窗时消费（FloatingService.onCreate :143-147）
                    if (FloatingService.instance == null) {
                        L.w("FloatingService not running, storing pending (len=${pending.length})")
                        FloatingService.pendingMessage = pending
                        appendDiag("CAPTURE_PENDING|len=${pending.length}")
                    } else {
                        EventBus.emitCapturedMessage(pending)
                        appendDiag("CAPTURE_EVENTBUS|len=${pending.length}")
                    }
                    pendingContent = null
                } else if (isMessageMenu && pending != null) {
                    // A1 修复：菜单到了但超时未捕获——这次机会已结束，清掉脏 pending
                    // 否则残留到下次捕获造成 off-by-one（存成上一条）
                    pendingContent = null
                    pendingTime = 0L
                    appendDiag("WINDOW_ARRIVE|menuTimeout|pendingCleared")
                }
            }
        }
      } catch (e: Exception) {
        L.e("onAccessibilityEvent crash prevented", e)
      }
    }

    override fun onInterrupt() {
        L.w("CopyCaptureService interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        instance = null
        L.w("CopyCaptureService unbound")
        return super.onUnbind(intent)
    }
}