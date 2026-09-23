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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File

/**
 * 无障碍服务 v4：感知任意 App 里"长按消息"的动作，实现"复制多条 → 自动积累"。
 *
 * 改进：
 * - 通过 EventBus（SharedFlow）发送捕获的消息，不再直接调用 FloatingService 静态方法
 * - 使用 AppConfig 常量
 * - ：消息捕获总开关（captureEnabled）在事件入口前置判断；不再主动 startService 重启悬浮窗
 * - ：捕获链路本地诊断文件（capture_diag.log），只记类型/长度/毫秒，不记内容
 * - P3-04: 诊断日志进有界 channel，由 IO worker 批量写；release 默认关闭或只保留脱敏环形计数
 */
class CopyCaptureService : AccessibilityService() {

    companion object {
        /** RA-02：当前隐私披露版本。递增此值可强制用户重新确认。 */
        const val CURRENT_DISCLOSURE_VERSION = 1

        @Volatile
        var instance: CopyCaptureService? = null
            private set

        @Volatile
        var isRunning: Boolean = false

        /** 诊断文件名 */
        private const val DIAG_FILE = "capture_diag.log"
        /** 诊断文件大小上限 */
        private const val DIAG_MAX_BYTES = 200 * 1024L

        /** CAP-04：关闭捕获时主动废弃 pending，无需等下一条 AccessibilityEvent */
        fun discardPendingCapture() {
            instance?.clearPending("capture_toggle_off")
        }
    }

    private var pendingContent: String? = null
    private var pendingTime = 0L
    /** H1 包名锁定：pending 来自哪个 App，窗口事件须同包名才消费（防跨 App 幽灵捕获） */
    private var pendingPkg: String? = null

    /** P3-04: 诊断日志异步写入——有界 channel + IO worker，不阻塞主回调线程 */
    private val diagScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val diagChannel = Channel<String>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var diagWorkerStarted = false

    /** CAP-02：统一清理 pending 捕获事务，避免多路径手写三字段清理遗漏 */
    internal fun clearPending(reason: String) {
        val hadPending = pendingContent != null

        pendingContent = null
        pendingTime = 0L
        pendingPkg = null

        if (hadPending) {
            appendDiag("PENDING_CLEAR|reason=$reason")
        }
    }

    /** SecurePrefs 实例（用于读取 captureEnabled 开关） */
    private var securePrefs: SecurePrefs? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        clearPending("service_connected_reset")
        instance = this
        isRunning = true
        securePrefs = SecurePrefs(this)
        L.init(this)
        L.w("CopyCaptureService connected v4 (EventBus, long-press capture)")
        appendDiag("SERVICE_CONNECTED")
    }

    override fun onDestroy() {
        clearPending("service_destroy")
        instance = null
        isRunning = false
        // P3-04: 取消诊断 IO scope
        diagScope.cancel()
        super.onDestroy()
    }

    /** P3-04: 追加诊断记录——异步写入，不阻塞主回调线程。
     * 格式 `uptimeMs|TAG|detail`，仅记类型/布尔/长度/毫秒，不记内容 */
    private fun appendDiag(line: String) {
        val ts = SystemClock.uptimeMillis()
        val entry = "$ts|$line\n"
        // 启动 IO worker（只启动一次）
        if (!diagWorkerStarted) {
            diagWorkerStarted = true
            diagScope.launch {
                val file = File(filesDir, DIAG_FILE)
                while (true) {
                    val data = diagChannel.receive()
                    try {
                        if (file.exists() && file.length() > DIAG_MAX_BYTES) {
                            file.writeText("")
                        }
                        file.appendText(data)
                    } catch (e: Exception) {
                        L.e("appendDiag async write failed", e)
                    }
                }
            }
        }
        // 非阻塞投递——channel 满时丢弃最旧条目
        diagChannel.trySend(entry)
    }

    /**
     * A2 修复：递归遍历无障碍节点树，收集所有非空文本。
     * 新版微信消息文本常挂在子节点上，长按的容器节点本身不带字 → 直接取 event.text 取不到。
     * 深度限制 4 层防性能问题，取最长的一条（最可能是完整消息内容）。
     *
     * P3-04: 增加节点数预算（maxNodes）和截止时间（deadline），超限 fail closed。
     *
     * 注意：入参 node（通常 = event.source）的生命周期由系统管理，调用方不应 recycle 它。
     * 本方法只 recycle 自己创建的子节点（node.getChild(i)）。
     * 本方法仅在 TYPE_VIEW_LONG_CLICKED 事件中调用，与 collectAllTextFromTree（WINDOW 事件）
     * 不会在同一次事件中执行，不存在对同一子节点重复 recycle 的问题。
     */
    private fun collectTextFromChildren(node: AccessibilityNodeInfo?, depth: Int = 0, maxDepth: Int = 4): String? {
        return collectTextFromChildrenBounded(node, depth, maxDepth, IntArray(1).apply { this[0] = 256 }, SystemClock.uptimeMillis() + 100L)
    }

    private fun collectTextFromChildrenBounded(node: AccessibilityNodeInfo?, depth: Int, maxDepth: Int, nodeCount: IntArray, deadline: Long): String? {
        if (node == null || depth > maxDepth) return null
        if (nodeCount[0] <= 0 || SystemClock.uptimeMillis() > deadline) return null
        nodeCount[0]--
        var best: String? = null
        node.text?.toString()?.trim()?.let { t ->
            if (t.isNotEmpty()) best = t
        }
        for (i in 0 until node.childCount) {
            if (nodeCount[0] <= 0 || SystemClock.uptimeMillis() > deadline) break
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            val childText = collectTextFromChildrenBounded(child, depth + 1, maxDepth, nodeCount, deadline)
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
        return collectAllTextFromTreeBounded(node, depth, maxDepth, IntArray(1).apply { this[0] = 256 }, SystemClock.uptimeMillis() + 100L)
    }

    private fun collectAllTextFromTreeBounded(node: AccessibilityNodeInfo?, depth: Int, maxDepth: Int, nodeCount: IntArray, deadline: Long): String {
        if (node == null || depth > maxDepth) return ""
        if (nodeCount[0] <= 0 || SystemClock.uptimeMillis() > deadline) return ""
        nodeCount[0]--
        val sb = StringBuilder()
        node.text?.toString()?.trim()?.let { if (it.isNotEmpty()) sb.append(it).append("|") }
        node.contentDescription?.toString()?.trim()?.let { if (it.isNotEmpty()) sb.append(it).append("|") }
        for (i in 0 until node.childCount) {
            if (nodeCount[0] <= 0 || SystemClock.uptimeMillis() > deadline) break
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            sb.append(collectAllTextFromTreeBounded(child, depth + 1, maxDepth, nodeCount, deadline))
            child.recycle()
        }
        return sb.toString()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
      try {
        // P3-05: 默认最小化——排除银行、密码管理器、支付、系统设置等敏感 App
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // 忽略自身进程的事件
        if (isSensitiveApp(pkg)) {
            clearPending("sensitive_app_excluded")
            return
        }

        val type = event.eventType

        //  问题 4②：消息捕获总开关前置检查 —— 每次事件读取最新值
        val capEnabled = securePrefs?.captureEnabled ?: true
        if (!capEnabled) {
            clearPending("capture_disabled")
            val typeTag = when (type) {
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "LONGCLICK"
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW"
                else -> "TYPE$type"
            }
            appendDiag("SWITCH_OFF|type=$typeTag")
            return
        }

        // RA-02：无 consent 时不读取节点文字——旧版本用户已开启无障碍但未确认新披露时也拦截
        val consentVersion = securePrefs?.accessibilityDisclosureVersion ?: 0
        if (consentVersion < CURRENT_DISCLOSURE_VERSION) {
            clearPending("disclosure_not_confirmed")
            appendDiag("CONSENT_PENDING|version=$consentVersion")
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
                    pendingPkg = pkg
                    L.w("long-press stored pending: len=${content.length}")
                    appendDiag("LONGCLICK_ARRIVE|len=${content.length}")
                } else {
                    // A1 修复：提取失败时必须清空 pending，否则旧值残留到下次捕获造成 off-by-one
                    clearPending("longclick_no_text")
                    appendDiag("LONGCLICK_ARRIVE|len=0|noTextFound|pendingCleared")
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 消息菜单 = 弹窗文字含「复制」即可确认；转发/删除/多选等七词门槛已删（分享/拷贝类菜单也能捕）
                val isMessageMenu = joined.contains("复制")
                val pending = pendingContent
                val now = SystemClock.uptimeMillis()
                val sinceLongClick = if (pendingTime > 0L) now - pendingTime else -1L
                if (pending != null) {
                    appendDiag("WINDOW_ARRIVE|menuMatch=$isMessageMenu|pkgMatch=${pendingPkg == pkg}|sinceLong=${sinceLongClick}ms|pendingLen=${pending.length}")
                } else {
                    appendDiag("WINDOW_ARRIVE|noPending|sinceLong=${sinceLongClick}ms")
                }
                if (isMessageMenu && pending != null) {
                    val pkgOk = pendingPkg == pkg
                    // H2：超过 30s 的旧 pending 视为过期（惰性校验，无定时器开销）；慢菜单(>3s)不再作废
                    val notExpired = sinceLongClick <= AppConfig.PENDING_MAX_AGE_MS
                    if (pkgOk && notExpired) {
                        L.w(">>> message menu confirmed, capture len=${pending.length}")
                        appendDiag("CAPTURE_OK|len=${pending.length}")
                        if (FloatingService.instance == null) {
                            // 悬浮服务没在跑：不再暂存补录（旧话自己冒出来的源头），直接丢弃并在 diag 里交代
                            L.w("FloatingService not running, capture dropped (len=${pending.length})")
                            appendDiag("CAPTURE_OK|serviceDown|dropped")
                            clearPending("capture_finished")
                        } else {
                            val accepted = EventBus.emitCapturedMessage(pending)
                            appendDiag("CAPTURE_EVENTBUS|accepted=$accepted|len=${pending.length}")
                            clearPending("capture_finished")
                        }
                    } else if (!pkgOk) {
                        // H1：跨 App 的复制菜单不是这次长按的确认——不消费，保留 pending 等原 App 的菜单
                        appendDiag("WINDOW_ARRIVE|CROSS_PKG_SKIP|pendingPkg=$pendingPkg|pkg=$pkg")
                    } else {
                        // H2 过期：这次长按的机会已结束，清掉脏 pending，防止残留到下次捕获造成 off-by-one
                        clearPending("expired")
                        appendDiag("WINDOW_ARRIVE|PENDING_EXPIRED|age=${sinceLongClick}ms|pendingCleared")
                    }
                }
            }
        }
      } catch (e: Exception) {
        L.e("onAccessibilityEvent crash prevented", e)
      }
    }

    override fun onInterrupt() {
        clearPending("service_interrupted")
        L.w("CopyCaptureService interrupted")
    }

    /**
     * P3-05: 敏感 App 排除列表——默认排除银行、密码管理器、支付、系统设置等。
     * 未来可通过 SecurePrefs 让用户自定义 allowlist/blocklist。
     */
    private val sensitiveAppPrefixes = setOf(
        "com.android.settings",
        "com.android.phone",
        "com.android.systemui",
        "com.android.contacts",
        "com.android.dialer",
        "com.google.android.gm",
        "com.android.chrome",
        "com.android.vending"
    )

    private val sensitiveAppKeywords = listOf(
        "bank", "banking", "pay", "wallet", "finance", "stock", "trade",
        "password", "1password", "lastpass", "bitwarden", "keeper",
        "alipay", "wechatpay", "unionpay", "cmb", "icbc", "boc", "ccb", "abc",
        "mabank", "spdb", "citic", "cebbank", "pab", "cmbc", "bocom",
        "google.android.apps.photos",
        "com.android.insecurebatch"
    )

    private fun isSensitiveApp(pkg: String): Boolean {
        // 精确匹配前缀列表
        for (prefix in sensitiveAppPrefixes) {
            if (pkg == prefix || pkg.startsWith("$prefix.")) return true
        }
        // 关键词匹配（小写）
        val pkgLower = pkg.lowercase()
        for (keyword in sensitiveAppKeywords) {
            if (pkgLower.contains(keyword)) return true
        }
        return false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        clearPending("service_unbind")
        isRunning = false
        instance = null
        // P3-04: 取消诊断 IO scope
        diagScope.cancel()
        L.w("CopyCaptureService unbound")
        return super.onUnbind(intent)
    }
}