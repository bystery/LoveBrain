package com.lovebrain.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.lovebrain.app.AppConfig
import com.lovebrain.app.data.EventBus
import com.lovebrain.app.data.SecurePrefs
import com.lovebrain.app.domain.CapturePolicy
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
 * - 诊断日志进有界 channel，由 IO worker 批量写；release 默认关闭或只保留脱敏环形计数
 */
class CopyCaptureService : AccessibilityService() {

    companion object {
        /** 当前隐私披露版本。递增此值可强制用户重新确认。 */
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

        /** 凭据节点探测最多访问多少个节点——大页面不无限遍历 */
        private const val CREDENTIAL_SCAN_MAX_NODES = 120

        // ═══ CAP1 可读性读数（2026-10-06）：拒绝与当场不记都不再只有 diag 文件一份账 ═══
        //
        // 三颗 @Volatile 读数只记**原因标签与毫秒时刻**，绝不记包名、正文、节点文本。
        // 用途有两个：① 真机分诊时与 capture_diag.log 互相核对（A1 报告 §6-5/§6-6 的映射读数）；
        // ② 界面侧"缺哪件念哪件"的六格状态（`ui/home/CaptureConsentGate.captureTruthOf`）说的
        // 就是同一批判据——用户可以拿这一行读数核页面有没有撒谎。
        // 行为一个没改：allowlist 空仍然全拒（对的隐私默认），悬浮窗不在仍然当场不记
        // （不暂存、不补录、不自动拉起，代价已登台账 impl-CAP1-capture）。

        /** 最近一次"事件被拒/被闸住"的原因标签（allowlist_empty / switch_off / consent_pending / …） */
        @Volatile
        var lastRejectReason: String? = null
            private set

        /** 那次拒绝的 uptime 毫秒（0 = 从未发生过） */
        @Volatile
        var lastRejectUptimeMs: Long = 0L
            private set

        /** 候选③：长按确认到了内容、却因悬浮窗不在而当场不记的累计次数 */
        @Volatile
        var serviceDownDropCount: Int = 0
            private set

        /** 最近一次当场不记的 uptime 毫秒（0 = 从未发生过） */
        @Volatile
        var lastDropUptimeMs: Long = 0L
            private set

        /**
         * CAP2（2026-10-06，档位 D）：当前声明到事件送达面（`ServiceInfo.packageNames`）的
         * **包名数量**这一级读数（-1 = 从未设过）。只记数量，绝不记包名明细、正文、Key——
         * 真机分诊时对 `dumpsys accessibility` 的 `packageNames` 行数一眼就能核对。
         */
        @Volatile
        var declaredPackageNameCount: Int = -1
            private set

        /** 记一笔"送达面声明了 N 个包名"读数（数量级，不含明细）。 */
        internal fun recordDeclaredPackageCount(count: Int) {
            declaredPackageNameCount = count
        }

        /** 记一笔拒绝读数。时间参数可注入：JVM 用例不碰 SystemClock。 */
        internal fun recordCaptureReject(reason: String, uptimeMs: Long = SystemClock.uptimeMillis()) {
            lastRejectReason = reason
            lastRejectUptimeMs = uptimeMs
        }

        /** 记一笔"悬浮窗不在 ⇒ 当场不记"读数。 */
        internal fun recordServiceDownDrop(uptimeMs: Long = SystemClock.uptimeMillis()) {
            serviceDownDropCount++
            lastDropUptimeMs = uptimeMs
        }

        /** CAP-04：关闭捕获时主动废弃 pending，无需等下一条 AccessibilityEvent */
        fun discardPendingCapture() {
            instance?.clearPending("capture_toggle_off")
        }
    }

    private var pendingContent: String? = null
    private var pendingTime = 0L
    /** H1 包名锁定：pending 来自哪个 App，窗口事件须同包名才消费（防跨 App 幽灵捕获） */
    private var pendingPkg: String? = null

    /** 诊断日志异步写入——有界 channel + IO worker，不阻塞主回调线程 */
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

    /** 上一次真正回写给系统的 allowlist（用来判"内容变了没"，避免每条事件都惊动系统）。 */
    private var lastAppliedPackageNames: Set<String> = emptySet()
    /** 送达面是否已经声明过一次（首次必声明，即使 allowlist 为空也要把"空数组"落出去）。 */
    private var packageScopeApplied = false

    /**
     * 偏好变更订阅的**取消句柄**（主线程 2026-10-06 补 CAP2 留下的那一格）。
     *
     * 为什么必须有这一颗：框架是按 `ServiceInfo.packageNames` **在投递之前就滤掉**事件的，
     * 所以"用户刚勾完第二个 App"这个动作**不会有任何事件流进服务**来提醒它复检——
     * 只靠 `onAccessibilityEvent` 里那次复检，新选的那一个永远抓不到，
     * 直到服务被重建（旧版本里那一次"重新开启才生效"就是这么来的）。
     * 订阅在偏好上 = 服务不依赖事件也能把送达面跟着范围走。
     */
    private var scopeSubscription: (() -> Unit)? = null

    /**
     * CAP2（档位 D）：把用户勾选的 allowlist 声明到事件送达面 `ServiceInfo.packageNames`。
     *
     * - 唯一真源 = `securePrefs.captureAllowedPackages`，一律经 [CapturePolicy.packageNamesFor] 换算，
     *   绝不把微信/QQ 之类样例包名写死。
     * - 只在内容变化时才回写，减少无谓的系统调用。
     * - 空 allowlist：仍把"空数组"声明出去（＝事件仍全投），但采集边界由 `CapturePolicy.decide`
     *   的 fail-closed 兜住（allowlist 空 → 每条 `allowlist_empty` 全拒）——所以"设了包名"绝不变成"收全部开抓"。
     *
     * **为什么末尾必须 `serviceInfo = info` 回写**：`serviceInfo` 的 getter 返回的是系统侧配置的一份
     * 可读副本；只改副本上的 `packageNames` 字段而不整颗调 setter 交还给系统，改动就留在一个没人看的
     * 对象上，框架的事件过滤条件一个字都没变——范围页保存后不生效，正是漏了这一步。回写才让新范围真正接管投递面。
     */
    private fun syncDeclaredPackageScope(allowed: Set<String>) {
        if (packageScopeApplied && allowed == lastAppliedPackageNames) return
        val info = serviceInfo ?: run {
            appendDiag("PACKAGE_SCOPE|noInfo")
            return
        }
        val names = CapturePolicy.packageNamesFor(allowed)
        info.packageNames = names
        serviceInfo = info // ← 回写：没有这一行，上面两行等于没做
        lastAppliedPackageNames = allowed
        packageScopeApplied = true
        recordDeclaredPackageCount(allowed.size)
        appendDiag("PACKAGE_SCOPE|count=${allowed.size}")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        clearPending("service_connected_reset")
        instance = this
        isRunning = true
        securePrefs = SecurePrefs(this)
        L.init(this)
        L.w("CopyCaptureService connected v4 (EventBus, long-press capture)")
        // 服务一连上就把当前范围声明出去（此后 allowlist 变更由 onAccessibilityEvent 里的复检接管，
        // 无需重启服务）。
        syncDeclaredPackageScope(securePrefs?.captureAllowedPackages ?: emptySet())
        // ⚠ 光靠事件里的复检不够：新勾的那一个 App 在**送达面还没跟上之前根本不会有事件进来**
        //（框架按 packageNames 先滤）。所以订阅偏好变更，让范围页一保存就重声明。
        // 幂等由 `syncDeclaredPackageScope` 自己兜（值没变就早退），这里不必再比一次。
        scopeSubscription = securePrefs?.onAnyPreferenceChanged {
            syncDeclaredPackageScope(securePrefs?.captureAllowedPackages ?: emptySet())
        }
        appendDiag("SERVICE_CONNECTED")
    }

    override fun onDestroy() {
        clearPending("service_destroy")
        // 先退订：`SharedPreferences` 若还持着这颗监听器，就等于持着整个服务实例（漏 + 服务死后还会回调）
        runCatching { scopeSubscription?.invoke() }
        scopeSubscription = null
        instance = null
        isRunning = false
        // 取消诊断 IO scope
        diagScope.cancel()
        super.onDestroy()
    }

    /** 追加诊断记录——异步写入，不阻塞主回调线程。
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
     *  修复：递归遍历无障碍节点树，收集所有非空文本。
     * 新版微信消息文本常挂在子节点上，长按的容器节点本身不带字 → 直接取 event.text 取不到。
     * 深度限制 4 层防性能问题，取最长的一条（最可能是完整消息内容）。
     *
     * 增加节点数预算（maxNodes）和截止时间（deadline），超限 fail closed。
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
     *  修复：递归收集节点树中所有文本（用于弹窗菜单关键词匹配）。
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
        // 默认 fail-closed 的 allowlist——只有用户明确选过的聊天 App 才进入捕获，
        // 且即使命中 allowlist，密码/验证码节点与系统窗口仍做二次拒绝。
        val pkg = event.packageName?.toString() ?: return
        // CAP2（档位 D）：每条事件先复检范围——allowlist 变了就重设 serviceInfo.packageNames，
        // 范围页保存后不用重启服务就生效。读一次复用，别再读第二遍。
        val allowed = securePrefs?.captureAllowedPackages ?: emptySet()
        syncDeclaredPackageScope(allowed)
        val decision = CapturePolicy.decide(
            CapturePolicy.Observation(
                sourcePackage = pkg,
                allowedPackages = allowed,
                ownPackage = packageName,
                windowIsSystemLevel = isSystemLevelWindow(event),
                hasPasswordNode = hasCredentialNode(event.source)
            )
        )
        if (decision is CapturePolicy.Decision.Deny) {
            // 只记录裁决原因，绝不记录正文或节点文本。
            // CAP1：拒绝从此留一份可核对的进程内读数（页面侧的六格状态吃同一批判据，不再只有 diag 一本账）
            clearPending("capture_denied:${decision.reason}")
            recordCaptureReject(decision.reason)
            appendDiag("DENY|pkg=${pkg.takeLast(24)}|reason=${decision.reason}")
            return
        }

        val type = event.eventType

        //  问题 4②：消息捕获总开关前置检查 —— 每次事件读取最新值
        // 2026-10-06 CAP3/CAP4：读不到存储时按**关**处理（`?: false`）。以前这里写的是 `?: true`，
        // 与同一颗键在 `SecurePrefs` 里的新默认（fail-closed，没写过就是关）正好相反——
        // 那一颗是用户报"第一次进来开关就是开的"的根因，兜底值若留在 true，等于在最后一道闸上
        // 又埋一份"默认开"。判据：`CaptureAccessibilityConfigShapeTest` 与 `CopyCaptureRejectLedgerTest`
        // 里"开关这一档只念 switch_off 一条拒因"那两格。
        val capEnabled = securePrefs?.captureEnabled ?: false
        if (!capEnabled) {
            clearPending("capture_disabled")
            recordCaptureReject("switch_off")
            val typeTag = when (type) {
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "LONGCLICK"
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW"
                else -> "TYPE$type"
            }
            appendDiag("SWITCH_OFF|type=$typeTag")
            return
        }

        // 无 consent 时不读取节点文字——旧版本用户已开启无障碍但未确认新披露时也拦截
        val consentVersion = securePrefs?.accessibilityDisclosureVersion ?: 0
        if (consentVersion < CURRENT_DISCLOSURE_VERSION) {
            clearPending("disclosure_not_confirmed")
            recordCaptureReject("consent_pending")
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
        //  修复：弹窗菜单项文本常在子节点里——直取不含"复制"时补查子节点树
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
                //  修复：三处都取不到时，递归遍历子节点树取最长文本
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
                    //  修复：提取失败时必须清空 pending，否则旧值残留到下次捕获造成 off-by-one
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
                            // 悬浮服务没在跑：不再暂存补录（旧话自己冒出来的源头），直接丢弃并在 diag 里交代。
                            // CAP1 定性（候选③，1.3.1 同款老行为）：**当场不记 + 读数留账 + 页面说原因**——
                            // 不自动拉起悬浮窗去"偷偷开始抓"，也不新增暂存队列（那会扩大采集面）。
                            // 恢复抓取的唯一入口是用户自己点悬浮窗/开始；代价与取舍登台账 impl-CAP1-capture。
                            L.w("FloatingService not running, capture dropped (len=${pending.length})")
                            recordServiceDownDrop()
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
     * 二次拒绝——即使用户把某个 App 加进 allowlist，
     * 系统级窗口（通知栏/锁屏/系统选择器）仍然不采。
     *
     * 只看窗口类型与包名，不读正文。
     */
    private fun isSystemLevelWindow(event: AccessibilityEvent): Boolean {
        val pkg = event.packageName?.toString().orEmpty()
        return pkg == "com.android.systemui" || pkg == "android"
    }

    /**
     * 事件源子树里是否存在凭据输入节点。
     *
     * 判定只用 isPassword 布尔位与 className，绝不把节点正文带进判断，
     * 因此聊天正文里出现"password"字样不会被误杀。
     */
    private fun hasCredentialNode(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        var found = false
        runCatching {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var visited = 0
            while (queue.isNotEmpty() && !found && visited < CREDENTIAL_SCAN_MAX_NODES) {
                val node = queue.removeFirst()
                visited++
                // AccessibilityNodeInfo 在 API 30 之前不暴露 inputType，
                // 所以这里能用的凭据信号就是 isPassword 布尔位本身；
                // 第三参数留给能拿到 inputType 的调用方，这里不假装知道更多。
                if (CapturePolicy.looksLikeCredentialNode(
                        className = node.className?.toString(),
                        isPassword = node.isPassword,
                        textHintsPasswordInputType = false
                    )
                ) {
                    found = true
                    break
                }
                for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            }
        }
        return found
    }

    override fun onUnbind(intent: Intent?): Boolean {
        clearPending("service_unbind")
        isRunning = false
        instance = null
        // 取消诊断 IO scope
        diagScope.cancel()
        L.w("CopyCaptureService unbound")
        return super.onUnbind(intent)
    }
}