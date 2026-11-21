package com.lovebrain.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
import org.koin.android.ext.android.inject
import java.io.File

/**
 * 无障碍服务 v4：感知任意 App 里"长按消息"的动作，实现"复制多条 → 自动积累"。
 *
 * 改进：
 * - 通过 EventBus（SharedFlow）发送捕获的消息，不再直接调用 FloatingService 静态方法
 * - 事件配对寿命与诊断上限等常量集中在 companion（见 [MENU_PENDING_MAX_AGE_MS]）
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

        /** 整树取文的预算：长按取文与菜单确认共用同一把尺（深度 / 节点数 / 毫秒） */
        private const val TEXT_SCAN_MAX_DEPTH = 4
        private const val TEXT_SCAN_MAX_NODES = 256
        private const val TEXT_SCAN_BUDGET_MS = 100L

        /**
         * 长按 → 复制菜单的配对寿命。
         *
         * 为什么不再是 30s：旧配对只比包名，同包正文里出现"复制"二字就会被当成菜单吞掉正文；
         * 现在配对还要求**菜单来自另一个窗口**（见 `menuPairingOf`），菜单本来就是长按后立刻弹出的，
         * 几秒内不出现就不是这次长按的配对，剩余寿命只会放大误配窗口。
         */
        internal const val MENU_PENDING_MAX_AGE_MS = 5_000L

        /** CAP-04：关闭捕获时主动废弃 pending，无需等下一条 AccessibilityEvent */
        fun discardPendingCapture() {
            instance?.clearPending("capture_toggle_off")
        }
    }

    private var pendingContent: String? = null
    private var pendingTime = 0L
    /** H1 包名锁定：pending 来自哪个 App，窗口事件须同包名才消费（防跨 App 幽灵捕获） */
    private var pendingPkg: String? = null
    /** H1 窗口锁定：长按发生在哪个窗口；复制菜单必须是长按之后新开的另一个窗口（防同包正文吞掉） */
    private var pendingWindowId: Int = WINDOW_ID_UNSPECIFIED

    /** 诊断日志异步写入——有界 channel + IO worker，不阻塞主回调线程 */
    private var diagScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val diagChannel = Channel<String>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var diagWorkerStarted = false

    /** CAP-02：统一清理 pending 捕获事务，避免多路径手写各字段清理遗漏 */
    internal fun clearPending(reason: String) {
        val hadPending = pendingContent != null

        pendingContent = null
        pendingTime = 0L
        pendingPkg = null
        pendingWindowId = WINDOW_ID_UNSPECIFIED

        if (hadPending) {
            appendDiag("PENDING_CLEAR|reason=$reason")
        }
    }

    /**
     * 捕获入库的唯一出口：长按菜单确认与多选点选两条路径共用——悬浮窗在跑才投递，
     * 不在就只记账丢弃（不暂存、不补录，代价与取舍登台账 impl-CAP1-capture）。
     */
    private fun deliverCapturedMessage(content: String, diagTag: String) {
        if (FloatingService.instance == null) {
            L.w("FloatingService not running, capture dropped (len=${content.length})")
            appendDiag("$diagTag|serviceDown|dropped")
            clearPending("capture_finished")
        } else {
            val accepted = EventBus.emitCapturedMessage(content)
            appendDiag("$diagTag|accepted=$accepted|len=${content.length}")
            clearPending("capture_finished")
        }
    }

    // 与 FloatingService 同源，不再自造第二实例。
    /** SecurePrefs 实例（Koin 容器单例，`by inject()` 惰性解析；读取开关/范围/披露三员） */
    private val securePrefs: SecurePrefs by inject()

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
        appendDiag("PACKAGE_SCOPE|count=${allowed.size}")
    }

    /**
     * 点选捕获的送达面：XML 静态声明保持隐私审计签字的"长按 + 窗口状态"两枚不动，
     * 点击事件在服务连上时经 serviceInfo 运行时补声明（与 packageNames 同一条回写通道，
     * 同样必须整颗回写才生效）。
     */
    private fun declareTapSelectDelivery() {
        val info = serviceInfo ?: run {
            appendDiag("TAP_DELIVERY|noInfo")
            return
        }
        if ((info.eventTypes and AccessibilityEvent.TYPE_VIEW_CLICKED) != 0) return
        info.eventTypes = info.eventTypes or AccessibilityEvent.TYPE_VIEW_CLICKED
        serviceInfo = info
        appendDiag("TAP_DELIVERY|declared")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 上一次 onUnbind/onDestroy 已取消 IO scope：同一实例重连时先复位 scope 与 worker 标志，
        // 否则 diagWorkerStarted 还挂着 true，appendDiag 再也不会起新 worker（诊断静默丢光），
        // 而 clearPending/syncDeclaredPackageScope 这些路径本身也会写诊断。
        diagScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        diagWorkerStarted = false
        clearPending("service_connected_reset")
        instance = this
        isRunning = true
        L.init(this)
        L.w("CopyCaptureService connected v4 (EventBus, long-press capture)")
        // 服务一连上就把当前范围声明出去（此后 allowlist 变更由 onAccessibilityEvent 里的复检接管，
        // 无需重启服务）。
        syncDeclaredPackageScope(securePrefs.captureAllowedPackages)
        // 多选点选的点击事件同理：XML 不动，连上时把送达面补到 CLICK
        declareTapSelectDelivery()
        // ⚠ 光靠事件里的复检不够：新勾的那一个 App 在**送达面还没跟上之前根本不会有事件进来**
        //（框架按 packageNames 先滤）。所以订阅偏好变更，让范围页一保存就重声明。
        // 幂等由 `syncDeclaredPackageScope` 自己兜（值没变就早退），这里不必再比一次。
        // 重连（同一实例再次 onServiceConnected）时旧订阅必须先退订再换：SharedPreferences 持着
        // 上一颗监听器＝持着一份旧回调，换掉而不退订会越积越多。
        runCatching { scopeSubscription?.invoke() }
        scopeSubscription = securePrefs.onAnyPreferenceChanged {
            syncDeclaredPackageScope(securePrefs.captureAllowedPackages)
        }
        appendDiag("SERVICE_CONNECTED")
    }

    /** 停侧复位唯一入口（onUnbind/onDestroy 共用）：清 pending → 退订偏好订阅并释放静态句柄 → 取消 diag IO scope。 */
    private fun resetLifecycleState(reason: String) {
        clearPending(reason)
        // 先退订：`SharedPreferences` 若还持着这颗监听器，就等于持着整个服务实例（漏 + 服务死后还会回调）
        runCatching { scopeSubscription?.invoke() }
        scopeSubscription = null
        instance = null
        isRunning = false
        diagScope.cancel()
    }

    override fun onDestroy() {
        resetLifecycleState("service_destroy")
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
     * 取节点子树里最长的一条非空文本（长按事件与多选点选共用）。
     *
     * 新版微信的消息文本常挂在子节点上，容器节点本身不带字，所以必须下探；
     * 判定本体在 [longestTextIn]，深度 / 节点数 / 毫秒三道闸与菜单确认共用 [walkNodeTreeDepthFirst]。
     *
     * 入参 node（通常 = event.source 或多选行容器）的生命周期由系统管理，不回收；
     * 只回收遍历取到的子节点。
     */
    private fun collectTextFromChildren(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        return longestTextIn(
            root = node,
            maxDepth = TEXT_SCAN_MAX_DEPTH,
            maxNodes = TEXT_SCAN_MAX_NODES,
            budgetMs = TEXT_SCAN_BUDGET_MS,
            childCount = { it.childCount },
            childAt = { parent, index -> parent.getChild(index) },
            textOf = { it.text?.toString()?.trim() },
            release = { it.recycle() }
        )
    }

    /**
     * 取节点子树全部文本 + contentDescription，用 "|" 拼接（弹窗菜单关键词匹配用）。
     * 菜单项"复制"/"转发"分散在各子节点，直取 event.text 经常取不到；遍历与预算同 [collectTextFromChildren]。
     *
     * 入参 node 归系统，不回收；只回收遍历取到的子节点。
     */
    private fun collectAllTextFromTree(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val builder = StringBuilder()
        walkNodeTreeDepthFirst(
            root = node,
            maxDepth = TEXT_SCAN_MAX_DEPTH,
            maxNodes = TEXT_SCAN_MAX_NODES,
            budgetMs = TEXT_SCAN_BUDGET_MS,
            childCount = { it.childCount },
            childAt = { parent, index -> parent.getChild(index) },
            visit = { current ->
                current.text?.toString()?.trim()?.let { if (it.isNotEmpty()) builder.append(it).append('|') }
                current.contentDescription?.toString()?.trim()?.let {
                    if (it.isNotEmpty()) builder.append(it).append('|')
                }
            },
            release = { it.recycle() }
        )
        return builder.toString()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
      try {
        // 默认 fail-closed 的 allowlist——只有用户明确选过的聊天 App 才进入捕获，
        // 且即使命中 allowlist，密码/验证码节点与系统窗口仍做二次拒绝。
        val pkg = event.packageName?.toString() ?: return
        // CAP2（档位 D）：每条事件先复检范围——allowlist 变了就重设 serviceInfo.packageNames，
        // 范围页保存后不用重启服务就生效。读一次复用，别再读第二遍。
        val allowed = securePrefs.captureAllowedPackages
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
            // 只记录裁决原因，绝不记录正文或节点文本；真机分诊只认 capture_diag.log 这一本账。
            clearPending("capture_denied:${decision.reason}")
            appendDiag("DENY|pkg=${pkg.takeLast(24)}|reason=${decision.reason}")
            return
        }

        val type = event.eventType

        //  问题 4②：消息捕获总开关前置检查 —— 每次事件读取最新值
        // 2026-10-06 CAP3/CAP4：开关读不到时按**关**处理。以前这里写的是 `securePrefs?.captureEnabled ?: true`
        // 与号串上那份"默认开"，是用户报"第一次进来开关就是开的"的根因；同一颗键在 `SecurePrefs`
        // 里的新默认是 fail-closed（没写过就是关）。2026-10-08 Q39① 起偏好实例从容器注入（非空），
        // 那道本地空值兜底随之失去存在理由——"没写过就是关"的默认由 SecurePrefs 的 getter 持有，
        // 判据见 `CopyCaptureRejectLedgerTest` 与 `SettingsStorePortContractTest` 那两格。
        val capEnabled = securePrefs.captureEnabled
        if (!capEnabled) {
            clearPending("capture_disabled")
            val typeTag = when (type) {
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "LONGCLICK"
                AccessibilityEvent.TYPE_VIEW_CLICKED -> "CLICK"
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW"
                else -> "TYPE$type"
            }
            appendDiag("SWITCH_OFF|type=$typeTag")
            return
        }

        // 无 consent 时不读取节点文字——旧版本用户已开启无障碍但未确认新披露时也拦截
        val consentVersion = securePrefs.accessibilityDisclosureVersion
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
        // 弹窗菜单项文本常挂在子节点里，直取事件字段并不可靠：事件够格（窗口切换 + 确有 pending）时，
        // 就用共享遍历一次取齐整树文本，不再"先拼一遍、发现缺关键词再补扫一遍"。
        // 普通事件（含长按本事件）不遍历，别把主线程整树扫描挂在每条事件上。
        if (shouldScanMenuTree(type, pendingContent != null)) {
            runCatching {
                val treeTexts = collectAllTextFromTree(event.source)
                texts.add("tree:$treeTexts")
                if (treeTexts.contains("复制")) appendDiag("WINDOW_MENU_FROM_TREE|matched")
            }
        }
        val joined = texts.joinToString("|")
        val typeName = when (type) {
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "LONGCLICK"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "CLICK"
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
                // 事件字段只带容器不带字时，内容就在子节点里：走共享遍历取最长的一条。
                if (content.isNullOrEmpty()) {
                    content = runCatching {
                        collectTextFromChildren(event.source)
                    }.getOrNull()
                    if (!content.isNullOrEmpty()) {
                        appendDiag("LONGCLICK_TEXT_FROM_CHILDREN|len=${content.length}")
                    }
                }
                if (!content.isNullOrEmpty()) {
                    pendingContent = content
                    pendingTime = SystemClock.uptimeMillis()
                    pendingPkg = pkg
                    // 记下长按所在窗口：复制菜单必须是这之后新开的另一个窗口（见 menuPairingOf）
                    pendingWindowId = event.windowId
                    L.w("long-press stored pending: len=${content.length}")
                    appendDiag("LONGCLICK_ARRIVE|len=${content.length}")
                } else {
                    // 取不到字就必须清 pending：旧值残留到下一次捕获会造成 off-by-one。
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
                val eventWindowId = event.windowId
                if (pending != null) {
                    appendDiag("WINDOW_ARRIVE|menuMatch=$isMessageMenu|pkgMatch=${pendingPkg == pkg}|sameWindow=${pendingWindowId == eventWindowId}|sinceLong=${sinceLongClick}ms|pendingLen=${pending.length}")
                } else {
                    appendDiag("WINDOW_ARRIVE|noPending|sinceLong=${sinceLongClick}ms")
                }
                if (isMessageMenu && pending != null) {
                    // H1/H2：配对同时看包名、寿命与窗口归属——同包正文含"复制"、旧 pending 都不消费
                    when (menuPairingOf(
                        pendingPkg,
                        pendingWindowId,
                        pkg,
                        eventWindowId,
                        sinceLongClick,
                        eventClassName = event.className?.toString()
                    )) {
                        MenuPairing.MATCH -> {
                            L.w(">>> message menu confirmed, capture len=${pending.length}")
                            appendDiag("CAPTURE_OK|len=${pending.length}")
                            runCatching {
                                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipText = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                appendDiag("CLIP_DUMP|len=${clipText.length}>>>")
                                appendDiag(clipText)
                                appendDiag("<<<CLIP_END")
                            }
                            deliverCapturedMessage(pending, diagTag = "CAPTURE_OK")
                        }

                        MenuPairing.CROSS_PKG -> {
                            // H1：跨 App 的复制菜单不是这次长按的确认——不消费，保留 pending 等原 App 的菜单
                            appendDiag("WINDOW_ARRIVE|CROSS_PKG_SKIP")
                        }

                        MenuPairing.SAME_WINDOW -> {
                            // 同窗事件（含读不到窗口号）证不了"这是长按后新开的菜单"：
                            // 不消费也不清 pending——同包正文里出现"复制"不再吞掉这次长按
                            appendDiag("WINDOW_ARRIVE|SAME_WINDOW_SKIP|eventWindow=$eventWindowId")
                        }

                        MenuPairing.EXPIRED -> {
                            // H2 过期：这次长按的机会已结束，清掉脏 pending，防止残留到下次捕获造成 off-by-one
                            clearPending("expired")
                            appendDiag("WINDOW_ARRIVE|PENDING_EXPIRED|age=${sinceLongClick}ms|pendingCleared")
                        }
                    }
                }
            }
        }
      } catch (e: Exception) {
        L.e("onAccessibilityEvent crash prevented", e)
      }
    }

    /**
     * 多选模式"点选即捕获"：与长按捕获共用同一入库通道，只多一道结构闸门——
     * 点击节点沿祖先链存在挂 checkable=true 子节点的容器（多选行的勾选圆圈，
     * 这是多选行独有的结构特征）。普通聊天点击消息没有勾选祖先、底栏按钮不在
     * 勾选行容器内，都判不进；图片行结构上命中但没有可取文本，跳过不投递。
     */
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
     *
     * 节点账：入参 root（= event.source）归系统，不回收；遍历中 `getChild` 取到的每个节点
     * 由 [scanNodesBounded] 交回 [AccessibilityNodeInfo.recycle]。此前这条路径从不回收，
     * 每条事件最多漏 120 个节点——高频事件下就是连接池与 Parcel 的慢性泄漏。
     */
    private fun hasCredentialNode(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return scanNodesBounded(
            root = root,
            maxNodes = CREDENTIAL_SCAN_MAX_NODES,
            childCount = { it.childCount },
            childAt = { node, index -> node.getChild(index) },
            visit = { node ->
                // AccessibilityNodeInfo 在 API 30 之前不暴露 inputType，
                // 所以这里能用的凭据信号就是 isPassword 布尔位本身；
                // 第三参数留给能拿到 inputType 的调用方，这里不假装知道更多。
                CapturePolicy.looksLikeCredentialNode(
                    className = node.className?.toString(),
                    isPassword = node.isPassword,
                    textHintsPasswordInputType = false
                )
            },
            release = { it.recycle() }
        )
    }

    override fun onUnbind(intent: Intent?): Boolean {
        resetLifecycleState("service_unbind")
        L.w("CopyCaptureService unbound")
        return super.onUnbind(intent)
    }
}

/** 事件没带窗口号（`AccessibilityWindowInfo.UNDEFINED_WINDOW_ID`）——判不了"菜单在另一个窗口"。 */
private const val WINDOW_ID_UNSPECIFIED = -1

/** 长按 ↔ 复制菜单的配对结论：只有 [MATCH] 允许消费并投递暂存内容。 */
internal enum class MenuPairing {

    /** 同包、没过期、且来自长按之后新开的另一个窗口——这次才是同一次手势的复制菜单 */
    MATCH,

    /** 不是长按那个 App 的菜单：不消费，保留 pending 等原 App 的菜单 */
    CROSS_PKG,

    /** 同窗事件（或窗口号读不到）：证不了"这是长按后新开的菜单"，不消费也不清 pending */
    SAME_WINDOW,

    /** 超过 [CopyCaptureService.MENU_PENDING_MAX_AGE_MS] 的旧暂存：清掉，防止残留到下次捕获 */
    EXPIRED
}

/**
 * 长按暂存与"复制"菜单窗口的配对判据（纯函数，便于摆正反例）。
 *
 * 只按包名 + 寿命配对的老口径会把**同包正文里出现的"复制"二字**当成菜单确认吞掉长按内容，
 * 所以再加一条窗口归属：菜单必须是长按所在窗口之外的新窗口；窗口号读不到/相同但事件类名
 * 明确是弹窗（[looksLikePopupWindow]）时按"可证的弹出窗口"放行，两者都不成立就按
 * [MenuPairing.SAME_WINDOW] 不消费）。寿命同时从 30s 收到 [CopyCaptureService.MENU_PENDING_MAX_AGE_MS]。
 */
internal fun menuPairingOf(
    pendingPkg: String?,
    pendingWindowId: Int,
    eventPkg: String?,
    eventWindowId: Int,
    sinceLongClickMs: Long,
    eventClassName: String? = null,
    maxAgeMs: Long = CopyCaptureService.MENU_PENDING_MAX_AGE_MS
): MenuPairing {
    if (pendingPkg == null || pendingPkg != eventPkg) return MenuPairing.CROSS_PKG
    if (sinceLongClickMs < 0L || sinceLongClickMs > maxAgeMs) return MenuPairing.EXPIRED
    val windowKnown =
        pendingWindowId != WINDOW_ID_UNSPECIFIED && eventWindowId != WINDOW_ID_UNSPECIFIED
    if (windowKnown && eventWindowId != pendingWindowId) return MenuPairing.MATCH
    return if (looksLikePopupWindow(eventClassName)) MenuPairing.MATCH else MenuPairing.SAME_WINDOW
}

/**
 * 弹窗容器类名（`PopupWindow$PopupDecorView` 之类）是"这是一个弹出菜单窗口"的可证标识。
 * 只在窗口号证不了跨窗时兜底用，凭类名放行；正文/标题不参与判断。
 */
internal fun looksLikePopupWindow(eventClassName: String?): Boolean =
    eventClassName != null && eventClassName.contains("popup", ignoreCase = true)

/**
 * 整树扫描只服务"这次长按的菜单确认"：只有窗口切换事件 + 真有 pending 时才值得做。
 * 普通事件（含长按本事件）不做 256 节点 / 100ms 的主线程遍历。
 */
internal fun shouldScanMenuTree(eventType: Int, hasPending: Boolean): Boolean =
    hasPending && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

/**
 * 取子树里最长一条非空文本的判定本体（泛型壳）：真机侧适配 AccessibilityNodeInfo，
 * JVM 用例用假节点钉行为——长按取文与多选点选取文共用这一份逻辑，
 * 预算与回收语义同 [walkNodeTreeDepthFirst]（入参 [root] 不回收，取到的子节点后序恰好还一次）。
 *
 * @param now 时钟注入点：JVM 用例不碰 SystemClock，真机默认 uptime。
 */
internal fun <T : Any> longestTextIn(
    root: T,
    maxDepth: Int,
    maxNodes: Int,
    budgetMs: Long,
    childCount: (T) -> Int,
    childAt: (T, Int) -> T?,
    textOf: (T) -> String?,
    release: (T) -> Unit,
    now: () -> Long = { SystemClock.uptimeMillis() }
): String? {
    var best: String? = null
    walkNodeTreeDepthFirst(
        root = root,
        maxDepth = maxDepth,
        maxNodes = maxNodes,
        budgetMs = budgetMs,
        childCount = childCount,
        childAt = childAt,
        visit = { current ->
            val text = textOf(current)
            val bestSoFar = best
            if (!text.isNullOrEmpty() && (bestSoFar == null || text.length > bestSoFar.length)) {
                best = text
            }
        },
        release = release,
        now = now
    )
    return best
}

/**
 * 多选行识别：从点击节点沿祖先链向上最多 [maxUpLevels] 层，找"直接子节点里有 checkable=true
 * 节点的容器"——微信多选模式里每一行都挂着一个勾选圆圈，这是多选行独有的结构特征；
 * 普通聊天点击消息没有勾选祖先，底栏按钮也不在勾选行容器内，都判不进。
 *
 * 节点账：入参 [clicked] 归系统/调用方，绝不回收；childAt 取来做勾选检查的子节点当场交
 * [release]，爬层换下的中间父节点也交 [release]；命中的行容器原样交回调用方
 * （它此刻归调用方所有，用完由调用方归还）。
 */


/**
 * 有界广度优先访问，并**由遍历自己记账回收**。
 *
 * 只有经 [childAt] 新取到的节点才会交 [release]（入参 [root] 归调用方/系统所有，绝不回收）；
 * 回收统一发生在遍历结束之后，所以不会把还要取子节点/继续遍历的父节点提前还掉。
 * 泛型壳是为了让 JVM 用例能拿假节点钉"取了多少、还了多少、父节点何时能还"——
 * `AccessibilityNodeInfo` 在测试里造不出可 `getChild` 的真树。
 *
 * @return 是否命中（[visit] 返回 true 即提前结束）
 */
internal fun <T : Any> scanNodesBounded(
    root: T,
    maxNodes: Int,
    childCount: (T) -> Int,
    childAt: (T, Int) -> T?,
    visit: (T) -> Boolean,
    release: (T) -> Unit
): Boolean {
    val queue = ArrayDeque<T>()
    val owned = ArrayList<T>()
    queue.add(root)
    var visited = 0
    var matched = false
    try {
        while (queue.isNotEmpty() && !matched && visited < maxNodes) {
            val node = queue.removeFirst()
            visited++
            if (runCatching { visit(node) }.getOrDefault(false)) {
                matched = true
                break
            }
            val count = runCatching { childCount(node) }.getOrDefault(0)
            for (i in 0 until count) {
                val child = runCatching { childAt(node, i) }.getOrNull() ?: continue
                owned.add(child)
                queue.add(child)
            }
        }
    } finally {
        owned.forEach { runCatching { release(it) } }
    }
    return matched
}

/**
 * 深度优先、三道闸（深度 / 节点数 / 毫秒）的节点树访问原语。
 *
 * 长按取"最长文本"与菜单取"全部文本"只在 [visit] 的回调里不同，遍历与预算必须共用这一份：
 * 以前两棵递归各写一遍同样的 4 层 / 256 节点 / 100ms，任何一处预算漂移都会让两条路径的行径不一致。
 *
 * 节点账：入参 [root] 归系统/调用方，绝不回收；[childAt] 取到的子节点在**其子树访问完之后**
 * 立刻交 [release] 回收（后序），所以父节点取子节点时仍然有效。
 * 泛型壳与 [scanNodesBounded] 同因：JVM 用例要用假节点钉取还数量与顺序。
 *
 * @param now 时钟注入点：JVM 用例不碰 SystemClock，真机默认 uptime。
 */
internal fun <T : Any> walkNodeTreeDepthFirst(
    root: T,
    maxDepth: Int,
    maxNodes: Int,
    budgetMs: Long,
    childCount: (T) -> Int,
    childAt: (T, Int) -> T?,
    visit: (T) -> Unit,
    release: (T) -> Unit,
    now: () -> Long = { SystemClock.uptimeMillis() }
) {
    val budget = NodeWalkBudget(maxNodes, budgetMs, now)
    fun descend(node: T, depth: Int) {
        if (depth > maxDepth || budget.exhausted) return
        budget.take()
        visit(node)
        val count = runCatching { childCount(node) }.getOrDefault(0)
        for (i in 0 until count) {
            if (budget.exhausted) break
            val child = runCatching { childAt(node, i) }.getOrNull() ?: continue
            try {
                descend(child, depth + 1)
            } finally {
                release(child)
            }
        }
    }
    descend(root, 0)
}

/** 节点访问预算：节点数在访问前扣，毫秒闸在每次下探前看一次（与旧两棵递归同口径） */
private class NodeWalkBudget(
    private var remaining: Int,
    budgetMs: Long,
    private val now: () -> Long
) {
    private val deadline = now() + budgetMs
    val exhausted: Boolean get() = remaining <= 0 || now() > deadline
    fun take() {
        remaining--
    }
}
