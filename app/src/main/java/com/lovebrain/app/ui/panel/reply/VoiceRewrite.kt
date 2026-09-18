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
 * STT 状态机：
 *   IDLE → RECORDING → PROCESSING → IDLE
 *
 * final transcript 非空：
 *   先回 IDLE，然后调用 onVoiceRewrite(tag, transcript)。
 *   API 改写状态由统一 RewriteState 管理，不再维护 REWRITING。
 *
 * 手势生命周期（P0-2）：
 *   PRESSING → RECORDING（达到长按阈值）
 *   RECORDING → PROCESSING（松手 / onEndOfSpeech）
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

/** 语音改写控制器返回值
 *
 * P0-5: gesturePhase 由外部（SchemeCard pointerInput）驱动，
 * 不在 controller 内部维护第二套状态机。
 */
data class VoiceRewriteController(
    val state: VoiceRewriteState,
    val startListening: () -> Unit,
    val stopListening: () -> Unit,
    val cancel: () -> Unit,
    val hasPermission: Boolean,
    val permissionResult: VoicePermissionResult?
)

/**
 * 长按语音修改 Helper——封装 SpeechRecognizer 生命周期 + runtime permission。
 *
 * 长按开始 → 检查权限 → 启动 SpeechRecognizer
 * 松手 → 停止录音，等待 final transcript
 * transcript 非空 → 调用 onVoiceRewrite(tag, transcript)
 * transcript 为空 → 取消，不发 API
 * partial transcript 不直接发请求
 *
 * Runtime permission: 使用系统权限 sheet (ActivityResultContracts.RequestPermission)。
 * P0-3: 权限结果通过回调通知 UI 层展示用户可理解的提示。
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
    // P0-5: gesturePhase 由 SchemeCard pointerInput 外部驱动，不在此处维护
    val speechRecognizer = remember { mutableStateOf<SpeechRecognizer?>(null) }
    val accumulatedText = remember { StringBuilder() }
    val tagRef = remember { schemeTag }
    var permissionResult by remember { mutableStateOf<VoicePermissionResult?>(null) }

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
            // 检查是否永久拒绝（shouldShowRationale 返回 false 说明用户勾选了"不再询问"）
            val shouldShow = if (context is android.app.Activity) {
                context.shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO)
            } else {
                // 非Activity上下文，保守判断为非永久拒绝
                true
            }
            permissionResult = if (shouldShow) VoicePermissionResult.DENIED else VoicePermissionResult.PERMANENTLY_DENIED
            onPermissionDenied(!shouldShow)
        }
    }

    // 权限状态可能在外部变化（用户从设置回来），P2: 基于 lifecycle resume 刷新
    // 不再依赖仅执行一次的 LaunchedEffect(Unit)，开始录音前也会重新检查
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

    fun startListening() {
        if (!hasPermission) {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }

        accumulatedText.clear()
        voiceState = VoiceRewriteState.RECORDING
        // P0-5: gesturePhase 由 SchemeCard pointerInput 外部管理，不在此处设置
        try {
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
                    voiceState = VoiceRewriteState.PROCESSING
                }

                override fun onError(error: Int) {
                    L.w("VoiceRewrite: STT error=$error")
                    voiceState = VoiceRewriteState.IDLE
                    speechRecognizer.value = null
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

                    // P0-5: wasCancelled 由外部 gesturePhase 查询——
                    // controller 不再持有 gesturePhase，手势取消时 SchemeCard 不会调用 stopListening()
                    // 而是调用 cancel()，cancel() 已清空 accumulatedText 并设 IDLE
                    // 到达 onResults 说明走的是正常 stopListening 路径
                    voiceState = VoiceRewriteState.IDLE
                    speechRecognizer.value = null

                    if (finalText.isNotBlank()) {
                        onVoiceRewrite(tagRef, finalText.trim())
                    } else {
                        L.w("VoiceRewrite: final transcript empty, not sending API")
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            sr.startListening(intent)
        } catch (e: Exception) {
            L.w("VoiceRewrite: SpeechRecognizer failed: ${e.message}")
            voiceState = VoiceRewriteState.IDLE
        }
    }

    fun stopListening() {
        // P0-5: gesturePhase 由 SchemeCard pointerInput 外部管理
        voiceState = VoiceRewriteState.PROCESSING
        speechRecognizer.value?.stopListening()
    }

    fun cancel() {
        // P0-5: gesturePhase 由 SchemeCard pointerInput 外部管理
        speechRecognizer.value?.cancel()
        speechRecognizer.value = null
        voiceState = VoiceRewriteState.IDLE
        accumulatedText.clear()
    }

    // 清理
    DisposableEffect(schemeTag) {
        onDispose {
            speechRecognizer.value?.cancel()
            speechRecognizer.value = null
        }
    }

    return VoiceRewriteController(
        state = voiceState,
        startListening = { startListening() },
        stopListening = { stopListening() },
        cancel = { cancel() },
        hasPermission = hasPermission,
        permissionResult = permissionResult
    )
}
