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
 * P0-1 修复：VoiceRewrite 只负责 STT（语音转文字）。
 *
 * 核心原则：STT 可以提前得到 final transcript，但绝对不能提前提交 rewrite。
 * final transcript 暂存；只有 SchemeCard 收到真实 RELEASED 后才能 commit。
 * CANCELLED / move-out / system cancel 永远丢弃 transcript，绝不调用 API。
 *
 * STT 状态机：
 *   IDLE → RECORDING → PROCESSING → IDLE
 *
 * final transcript 非空时暂存在 pendingTranscript 中，
 * 等待外部 commitTranscript() 调用（由 SchemeCard 在 RELEASED 时触发）。
 *
 * 手势生命周期（P0-2）：
 *   PRESSING → RECORDING（达到长按阈值）
 *   RECORDING → RELEASED（松手 → stopListening → 等待 final → commitTranscript）
 *   RECORDING → CANCELLED → IDLE（手指移出 / 手势 cancel，不发 API）
 *   PROCESSING → IDLE（拿到 final transcript 或 onError）
 *   partial transcript 绝不发 API
 *   final transcript 为空绝不发 API
 *
 * Permission UX（P0-3）：
 *   首次无权限 → 系统权限 sheet
 *   允许 → 回调 onPermissionGranted 提示用户再次长按
 *   拒绝 → 回调 onPermissionDenied 提示用户
 */
enum class VoiceRewriteState {
    IDLE, RECORDING, PROCESSING
}

/**
 * P0-5: 手势生命周期状态——单一 owner，SchemeCard 和 VoiceRewriteController 共用。
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

/** 语音改写控制器返回值 */
data class VoiceRewriteController(
    val state: VoiceRewriteState,
    val startListening: () -> StartListeningResult,
    val stopListening: () -> Unit,
    val cancel: () -> Unit,
    /** 提交暂存的 final transcript——只在物理 RELEASED 时调用 */
    val commitTranscript: () -> Unit,
    val hasPermission: Boolean,
    val permissionResult: VoicePermissionResult?
)

/**
 * 长按语音修改 Helper——封装 SpeechRecognizer 生命周期 + runtime permission。
 *
 * 关键修复：
 * - onResults 不再直接调用 onVoiceRewrite——final transcript 暂存在 pendingTranscript
 * - 只有 commitTranscript() 才真正提交（由 SchemeCard 在 RELEASED 时调用）
 * - stopListening 只在 recognizer 存在时才进入 PROCESSING
 * - startListening 返回 typed result（Started/PermissionRequested/Failed）
 * - SpeechRecognizer.destroy() 统一 cleanup（结束、错误、取消、dispose）
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

    // P0-1: final transcript 暂存——onResults 写入，commitTranscript 读取
    val pendingTranscript = remember { mutableStateOf<String?>(null) }
    // P0-1: 手势取消标志——cancel 后即使 onResults 到达也不提交
    val wasCancelled = remember { mutableStateOf(false) }

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

    // P0-1: 统一 cleanup——destroy recognizer 释放资源
    fun destroyRecognizer() {
        speechRecognizer.value?.let { sr ->
            runCatching { sr.cancel() }
            runCatching { sr.destroy() }
        }
        speechRecognizer.value = null
    }

    fun startListening(): StartListeningResult {
        if (!hasPermission) {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return StartListeningResult.PERMISSION_REQUESTED
        }

        accumulatedText.clear()
        pendingTranscript.value = null
        wasCancelled.value = false

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
                    // 但不提交——提交只在 commitTranscript（物理 RELEASED）
                    if (!wasCancelled.value && voiceState == VoiceRewriteState.RECORDING) {
                        voiceState = VoiceRewriteState.PROCESSING
                    }
                }

                override fun onError(error: Int) {
                    L.w("VoiceRewrite: STT error=$error")
                    voiceState = VoiceRewriteState.IDLE
                    destroyRecognizer()
                    pendingTranscript.value = null
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

                    voiceState = VoiceRewriteState.IDLE
                    destroyRecognizer()

                    // P0-1: final transcript 暂存——不直接提交
                    // 只有未被 cancel 时才暂存（cancel 后到达的 results 丢弃）
                    if (!wasCancelled.value && finalText.isNotBlank()) {
                        pendingTranscript.value = finalText.trim()
                        L.w("VoiceRewrite: final transcript cached, waiting for commit")
                    } else {
                        pendingTranscript.value = null
                        if (wasCancelled.value) {
                            L.w("VoiceRewrite: final transcript discarded (gesture cancelled)")
                        } else {
                            L.w("VoiceRewrite: final transcript empty, not caching")
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

    fun stopListening() {
        // P0-1: 只有 recognizer 真实存在且状态允许时才进入 PROCESSING
        // 否则直接回 IDLE——防止 null recognizer + PROCESSING 永久卡死
        val sr = speechRecognizer.value
        if (sr != null && (voiceState == VoiceRewriteState.RECORDING || voiceState == VoiceRewriteState.PROCESSING)) {
            voiceState = VoiceRewriteState.PROCESSING
            runCatching { sr.stopListening() }
        } else {
            // recognizer 不存在（无权限/创建失败/onError 后松手）→ 安全回 IDLE
            voiceState = VoiceRewriteState.IDLE
        }
    }

    fun commitTranscript() {
        // P0-1: 物理松手才调用——提交暂存的 final transcript
        val transcript = pendingTranscript.value
        pendingTranscript.value = null
        if (transcript != null && !wasCancelled.value) {
            L.w("VoiceRewrite: committing cached transcript")
            onVoiceRewrite(tagRef, transcript)
        } else {
            L.w("VoiceRewrite: no transcript to commit (transcript=$transcript, cancelled=${wasCancelled.value})")
        }
        // 提交后确保状态归位
        voiceState = VoiceRewriteState.IDLE
    }

    fun cancel() {
        wasCancelled.value = true
        pendingTranscript.value = null
        destroyRecognizer()
        voiceState = VoiceRewriteState.IDLE
        accumulatedText.clear()
    }

    // P0-1: Composable dispose 时统一 destroy——防资源泄漏
    DisposableEffect(schemeTag) {
        onDispose {
            destroyRecognizer()
        }
    }

    return VoiceRewriteController(
        state = voiceState,
        startListening = { startListening() },
        stopListening = { stopListening() },
        cancel = { cancel() },
        commitTranscript = { commitTranscript() },
        hasPermission = hasPermission,
        permissionResult = permissionResult
    )
}
