package com.lovebrain.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统一时间格式工具（DRY：消除各处重复的 SimpleDateFormat("yyyy-MM-dd HH:mm")）。
 */
object TimeFmt {
    private const val PATTERN = "yyyy-MM-dd HH:mm"

    /** 当前时间 → "yyyy-MM-dd HH:mm" */
    fun now(): String = SimpleDateFormat(PATTERN, Locale.getDefault()).format(Date())

    /** 当前日期 → "yyyy-MM-dd" */
    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** "yyyy-MM-dd HH:mm" → epoch millis；解析失败返回 0 */
    fun parse(time: String): Long = runCatching {
        SimpleDateFormat(PATTERN, Locale.getDefault()).parse(time)?.time ?: 0L
    }.getOrDefault(0L)
}
