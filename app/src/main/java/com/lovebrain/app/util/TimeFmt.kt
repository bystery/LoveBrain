package com.lovebrain.app.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 统一时间格式工具（DRY：消除各处重复的 SimpleDateFormat("yyyy-MM-dd HH:mm")）。
 *
 * 格式器钉 Locale.ROOT + ISO 公历：提示词里的「当前时间」是模型判断时段的唯一依据，
 * 它不能随设备语言变。SimpleDateFormat + 默认 Locale 在 th_TH 会给出佛历年份
 * （2569 而不是 2026）、在 ar_SA 会给出本地数字——同一份输入在两台设备上拼出两份 prompt。
 * java.time 的 DateTimeFormatter 不按 Locale 换历法/数字系统（minSdk 26 可用）。
 */
object TimeFmt {
    private const val PATTERN = "uuuu-MM-dd HH:mm"
    private const val DAY_PATTERN = "uuuu-MM-dd"
    private const val ISO_PATTERN = "uuuu-MM-dd'T'HH:mm:ssXXX"
    private const val STAMP_PATTERN = "uuuuMMdd_HHmm"

    private val FORMATTER = DateTimeFormatter.ofPattern(PATTERN, Locale.ROOT)
    private val DAY_FORMATTER = DateTimeFormatter.ofPattern(DAY_PATTERN, Locale.ROOT)
    private val ISO_FORMATTER = DateTimeFormatter.ofPattern(ISO_PATTERN, Locale.ROOT)
    private val STAMP_FORMATTER = DateTimeFormatter.ofPattern(STAMP_PATTERN, Locale.ROOT)

    /** 当前时间 → "yyyy-MM-dd HH:mm" */
    fun now(): String = format(System.currentTimeMillis())

    /** 指定毫秒 → "yyyy-MM-dd HH:mm"；与 [now] 共用同一颗格式器与同一个时区口径 */
    fun format(epochMs: Long): String =
        FORMATTER.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    /** 指定毫秒 → "yyyy-MM-dd"；与 [format] 共用同一颗日期格式器与同一个时区口径 */
    fun date(epochMs: Long): String =
        DAY_FORMATTER.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    /** 当前日期 → "yyyy-MM-dd" */
    fun today(): String = date(System.currentTimeMillis())

    fun isoNow(): String = ISO_FORMATTER.format(Instant.now().atZone(ZoneId.systemDefault()))
    fun stampNow(): String = STAMP_FORMATTER.format(Instant.now().atZone(ZoneId.systemDefault()))

    /** "yyyy-MM-dd HH:mm" → epoch millis；解析失败返回 0 */
    fun parse(time: String): Long = runCatching {
        LocalDateTime.parse(time, FORMATTER).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrDefault(0L)
}
