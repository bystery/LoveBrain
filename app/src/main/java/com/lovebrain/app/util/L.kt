package com.lovebrain.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 隐私红线：release 不落文件（已关闭，曾全量沉淀事件文本）。
 * 调用方另须遵守（聊天内容/Key/画像禁止入日志，只记长度/类型）。
 */
object L {
    private const val TAG = "LoveBrain"
    private var logFile: File? = null
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** 在 Service/Application 创建时调用一次，指定日志文件位置 */
    fun init(context: Context) {
        runCatching {
            logFile = File(context.filesDir, "lb_log.txt")
        }
    }

    fun w(msg: String) {
        Log.w(TAG, msg)
        append(msg)
    }

    fun e(msg: String, t: Throwable? = null) {
        Log.e(TAG, msg, t)
        append("ERROR: $msg" + (t?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }

    @Synchronized
    private fun append(msg: String) {
        if (!com.lovebrain.app.BuildConfig.DEBUG) return
        runCatching {
            val f = logFile ?: return
            // 控制文件大小：超过 512KB 就清空重写
            if (f.exists() && f.length() > 512 * 1024) f.writeText("")
            f.appendText("${timeFmt.format(Date())} $msg\n")
        }
    }
}
