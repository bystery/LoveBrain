package com.lovebrain.app.ui.panel.reply

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lovebrain.app.util.L

/**
 * VoiceRewrite 只负责 STT（语音转文字）。
 *
 * 核心原则：STT 可以提前得到 final transcript，但绝对不能提前提交 rewrite。
 * 提交条件统一为（两事件 rendezvous）：
 *   physicalReleased && finalTranscript 非空 && !cancelled && !submitted
 * 两个事件谁先来都行：
 *   路径 A: onResults 先来 → 缓存 transcript，等 release 时提交
 *   路径 B: release 先来 → 标记 released，stop recognizer，等 onResults 到达时提交
 * cancel/move-out/system cancel 在任意时间发生后，都永久禁止本次 submit。
 *
 * STT 状态机：
 *   IDLE → RECORDING → PROCESSING → IDLE
 *
 * Permission UX：
 *   首次无权限 → 系统权限 sheet
 *   允许 → 回调 onPermissionGranted 提示用户再次长按
 *   拒绝 → 回调 onPermissionDenied 提示用户
 */
enum class VoiceRewriteState {
    IDLE, RECORDING, PROCESSING
}

/**
 * 手势生命周期状态——单一 owner，SchemeCard 和 VoiceRewriteController 共用。
 *
 * 用于跟踪长按手势的物理阶段，确保：
 * - 手指移出卡片 → CANCELLED（不发送 transcript，不请求 API）
 * - 正常松手 → RELEASED（等待 final transcript）
 * - 手势被系统取消 → CANCELLED
 *
 * 这个状态由 SchemeCard 的 pointerInput 驱动，VoiceRewriteController 只读取它。
 * 禁止两套状态机各自漂移。
 */
enum class GesturePhase {
    /** 未开始手势 */
    IDLE,
    /** 手指按下，尚未达到长按阈值 */
    PRESSING,
    /** 达到长按阈值，正在录音 */
    RECORDING,
    /** 正常松手，等待 final transcript */
    RELEASED,
    /** 手指移出或手势被取消，不发送 transcript */
    CANCELLED
}

/** 语音权限结果 */
enum class VoicePermissionResult {
    /** 初始已有权限 */
    ALREADY_GRANTED,
    /** 本次授权成功 */
    GRANTED,
    /** 用户拒绝 */
    DENIED,
    /** 永久拒绝（不再弹系统框） */
    PERMANENTLY_DENIED
}

/** startListening 返回值——只有 Started 才允许外层进入 RECORDING */
enum class StartListeningResult {
    /** 成功启动 SpeechRecognizer */
    STARTED,
    /** 无权限，已弹出权限请求（外层不应进入 RECORDING） */
    PERMISSION_REQUESTED,
    /** 创建/启动失败（外层不应进入 RECORDING） */
    FAILED
}

/**
 * 语音改写控制器——两事件 rendezvous 模型。
 *
 * 调用方只需要：
 * - startListening() 开始录音
 * - release() 松手时调用（内部完成 stopListening + rendezvous）
 * - cancel() 取消（移出/系统取消）
 *
 * 不再暴露 stopListening + commitTranscript 两个需要调用方按序拼接的接口。
 */
data class VoiceRewriteController(
    val state: VoiceRewriteState,
    val startListening: () -> StartListeningResult,
    /** 松手时调用——controller 内部完成 stop recognizer + rendezvous 提交 */
    val release: () -> Unit,
    /** 取消——移出/系统取消后调用，永久禁止本次提交 */
    val cancel: () -> Unit,
    val hasPermission: Boolean,
    val permissionResult: VoicePermissionResult?
)

/**
 * 长按语音修改 Helper——封装 SpeechRecognizer 生命周期 + runtime permission。
 *
 * 真正修复：两事件 rendezvous 模型
 * - onResults 不再直接调用 onVoiceRewrite——final transcript 缓存到 finalTranscript
 * - release() 标记 physicalReleased 并调用 tryCommit()
 * - tryCommit() 统一提交条件：physicalReleased && finalTranscript非空 && !cancelled && !submitted
 * - 两个事件谁先来都行——result→release 和 release→result 都只提交一次
 * - cancel 后即使 onResults 到达也不提交
 * - submitted 标志保证永远只提交一次
 */
@Composable
fun rememberVoiceRewriteController(
    schemeTag: String,
    onVoiceRewrite: (String, String) -> Unit,
    onPermissionGranted: () -> Unit = {},
    onPermissionDenied: (Boolean) -> Unit = {}
): VoiceRewriteController {
    val context = LocalContext.current
    var voiceState by remember { mutableStateOf(VoiceRewriteState.IDLE) }
    val speechRecognizer = remember { mutableStateOf<SpeechRecognizer?>(null) }
    val accumulatedText = remember { StringBuilder() }
    val tagRef = remember { schemeTag }
    var permissionResult by remember { mutableStateOf<VoicePermissionResult?>(null) }

    // 两事件 rendezvous 状态
    val physicalReleased = remember { mutableStateOf(false) }
    val finalTranscript = remember { mutableStateOf<String?>(null) }
    val cancelled = remember { mutableStateOf(false) }
    val submitted = remember { mutableStateOf(false) }

    var hasPermission by remember {
        mutableStateOf(
            context.checkPermission(
                android.Manifest.permission.RECORD_AUDIO,
                android.os.Process.myPid(),
                android.os.Process.myUid()
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 权限请求 launcher——系统权限 sheet
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            hasPermission = true
            permissionResult = VoicePermissionResult.GRANTED
            onPermissionGranted()
        } else {
            val shouldShow = if (context is android.app.Activity) {
                context.shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO)
            } else {
                true
            }
            permissionResult = if (shouldShow) VoicePermissionResult.DENIED else VoicePermissionResult.PERMANENTLY_DENIED
            onPermissionDenied(!shouldShow)
        }
    }

    // 权限状态可能在外部变化（用户从设置回来），基于 lifecycle resume 刷新
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = context.checkPermission(
                    android.Manifest.permission.RECORD_AUDIO,
                    android.os.Process.myPid(),
                    android.os.Process.myUid()
                ) == PackageManager.PERMISSION_GRANTED
                if (nowGranted != hasPermission) {
                    hasPermission = nowGranted
                    if (nowGranted) {
                        permissionResult = VoicePermissionResult.ALREADY_GRANTED
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 统一 cleanup——destroy recognizer 释放资源
    fun destroyRecognizer() {
        speechRecognizer.value?.let { sr ->
            runCatching { sr.cancel() }
            runCatching { sr.destroy() }
        }
        speechRecognizer.value = null
    }

    // 统一提交逻辑——两事件 rendezvous
    // 使用 shouldCommitTranscript() 作为单一决策源——与 VoiceGestureCommitTest 测试的纯函数一致
    // 条件：physicalReleased && finalTranscript非空 && !cancelled && !submitted
    // 两个事件谁先来都行，只要两个都满足就提交
    fun tryCommit() {
        if (shouldCommitTranscript(
            physicalReleased = physicalReleased.value,
            finalTranscript = finalTranscript.value,
            cancelled = cancelled.value,
            submitted = submitted.value
        )) {
            submitted.value = true
            L.w("VoiceRewrite: committing transcript (rendezvous satisfied)")
            onVoiceRewrite(tagRef, finalTranscript.value!!.trim())
            // 提交后状态归位
            voiceState = VoiceRewriteState.IDLE
        }
    }

    fun startListening(): StartListeningResult {
        if (!hasPermission) {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return StartListeningResult.PERMISSION_REQUESTED
        }

        // 重置 rendezvous 状态
        accumulatedText.clear()
        finalTranscript.value = null
        physicalReleased.value = false
        cancelled.value = false
        submitted.value = false

        return try {
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer.value = sr

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    // STT 自行判断结束——进入 PROCESSING 等待 final results
                    if (!cancelled.value && voiceState == VoiceRewriteState.RECORDING) {
                        voiceState = VoiceRewriteState.PROCESSING
                    }
                }

                override fun onError(error: Int) {
                    L.w("VoiceRewrite: STT error=$error")
                    voiceState = VoiceRewriteState.IDLE
                    destroyRecognizer()
                    finalTranscript.value = null
                    // onError 后不再有 final transcript——但如果已经 released，
                    // 不需要做任何事（transcript 为 null，tryCommit 不会提交）
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // partial transcript 不发 API
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        accumulatedText.clear()
                        accumulatedText.append(partial)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val finalText = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?: accumulatedText.toString()

                    destroyRecognizer()

                    // 缓存 final transcript——不直接提交
                    // 只有未被 cancel 时才缓存（cancel 后到达的 results 丢弃）
                    if (!cancelled.value && finalText.isNotBlank()) {
                        finalTranscript.value = finalText.trim()
                        L.w("VoiceRewrite: final transcript cached, attempting rendezvous commit")
                        // 状态归位——但只在尚未 released 时
                        // 如果已经 released，tryCommit 会处理状态归位
                        if (physicalReleased.value) {
                            tryCommit()
                        } else {
                            // 等 release 到达时提交——保持 PROCESSING 让 UI 显示识别完成
                            voiceState = VoiceRewriteState.PROCESSING
                        }
                    } else {
                        finalTranscript.value = null
                        if (cancelled.value) {
                            L.w("VoiceRewrite: final transcript discarded (gesture cancelled)")
                        } else {
                            L.w("VoiceRewrite: final transcript empty, not caching")
                        }
                        // 即使 transcript 为空，如果已经 released，也需要归位状态
                        if (physicalReleased.value) {
                            voiceState = VoiceRewriteState.IDLE
                        } else {
                            voiceState = VoiceRewriteState.PROCESSING
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            sr.startListening(intent)
            voiceState = VoiceRewriteState.RECORDING
            StartListeningResult.STARTED
        } catch (e: Exception) {
            L.w("VoiceRewrite: SpeechRecognizer failed: ${e.message}")
            voiceState = VoiceRewriteState.IDLE
            destroyRecognizer()
            StartListeningResult.FAILED
        }
    }

    /**
     * release()——松手时调用，controller 内部完成 stop + rendezvous。
     * 不再暴露 stopListening + commitTranscript 两个需要调用方按序拼接的接口。
     */
    fun release() {
        // 标记物理松手
        physicalReleased.value = true

        // 停止 recognizer——如果 recognizer 仍存在，stopListening 会触发 onResults
        val sr = speechRecognizer.value
        if (sr != null && (voiceState == VoiceRewriteState.RECORDING || voiceState == VoiceRewriteState.PROCESSING)) {
            voiceState = VoiceRewriteState.PROCESSING
            runCatching { sr.stopListening() }
            // onResults 会在稍后回调，那时 finalTranscript 会被设置并触发 tryCommit
        } else {
            // recognizer 不存在（无权限/创建失败/onError 后松手）→ 安全回 IDLE
            voiceState = VoiceRewriteState.IDLE
        }

        // 尝试提交——如果 onResults 已经先到达并缓存了 transcript，此时提交
        // 如果 onResults 尚未到达，tryCommit 不会提交（finalTranscript 为 null），
        // 等 onResults 到达时再触发 tryCommit
        tryCommit()

        // 如果 recognizer 不存在或已 destroy，且没有 transcript，直接归位
        if (sr == null && finalTranscript.value == null) {
            voiceState = VoiceRewriteState.IDLE
        }
    }

    fun cancel() {
        cancelled.value = true
        finalTranscript.value = null
        destroyRecognizer()
        voiceState = VoiceRewriteState.IDLE
        accumulatedText.clear()
    }

    // Composable dispose 时统一 destroy——防资源泄漏
    DisposableEffect(schemeTag) {
        onDispose {
            destroyRecognizer()
        }
    }

    return VoiceRewriteController(
        state = voiceState,
        startListening = { startListening() },
        release = { release() },
        cancel = { cancel() },
        hasPermission = hasPermission,
        permissionResult = permissionResult
    )
}
