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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalContext
import com.lovebrain.app.util.L

/**
 * P0-10: STT 长按语音修改状态。
 * - IDLE：未开始
 * - RECORDING：正在录音/识别中
 * - PROCESSING：等待 final transcript
 * - REWRITING：已发送改写请求，等待结果
 */
enum class VoiceRewriteState {
    IDLE, RECORDING, PROCESSING, REWRITING
}

/** 语音改写控制器返回值：state + startListening + stopListening + cancel */
data class VoiceRewriteController(
    val state: VoiceRewriteState,
    val startListening: () -> Unit,
    val stopListening: () -> Unit,
    val cancel: () -> Unit,
    val hasPermission: Boolean
)

/**
 * P0-10: 长按语音修改 Helper——封装 SpeechRecognizer 生命周期 + runtime permission。
 *
 * 长按开始 → 检查权限 → 启动 SpeechRecognizer
 * 松手 → 停止录音，等待 final transcript
 * transcript 非空 → 调用 onVoiceRewrite(tag, transcript)
 * transcript 为空 → 取消，不发 API
 * partial transcript 不直接发请求
 *
 * Runtime permission: 使用系统权限 sheet (ActivityResultContracts.RequestPermission)，
 * 禁止自造权限弹窗。
 */
@Composable
fun rememberVoiceRewriteController(
    schemeTag: String,
    onVoiceRewrite: (String, String) -> Unit
): VoiceRewriteController {
    val context = LocalContext.current
    var voiceState by remember { mutableStateOf(VoiceRewriteState.IDLE) }
    val speechRecognizer = remember { mutableStateOf<SpeechRecognizer?>(null) }
    val accumulatedText = remember { StringBuilder() }
    val tagRef = remember { schemeTag }

    // P0-10: Runtime permission——使用系统权限 sheet
    var hasPermission by remember {
        mutableStateOf(
            context.checkPermission(
                android.Manifest.permission.RECORD_AUDIO,
                android.os.Process.myPid(),
                android.os.Process.myUid()
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 权限请求 launcher——系统权限 sheet，不自造弹窗
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            L.w("VoiceRewrite: RECORD_AUDIO permission denied by user")
        }
    }

    // 权限状态可能在外部变化（用户从设置回来），在组合时重新检查
    LaunchedEffect(Unit) {
        hasPermission = context.checkPermission(
            android.Manifest.permission.RECORD_AUDIO,
            android.os.Process.myPid(),
            android.os.Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startListening() {
        // P0-10: 检查录音权限——无权限时请求系统权限 sheet
        if (!hasPermission) {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }

        accumulatedText.clear()
        voiceState = VoiceRewriteState.RECORDING

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
                    speechRecognizer.value = null

                    if (finalText.isNotBlank()) {
                        voiceState = VoiceRewriteState.REWRITING
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
        voiceState = VoiceRewriteState.PROCESSING
        speechRecognizer.value?.stopListening()
    }

    fun cancel() {
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
        hasPermission = hasPermission
    )
}
