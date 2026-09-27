package com.lovebrain.app.data

import java.util.TimeZone

/**
 * 字节基线类测试共用的**时钟基准**：把 JVM 默认时区钉成同一个，用完还原。
 *
 * 为什么要单独一个所有者（而不是各测试文件自己写一遍）：同一件事在仓库里有两套实现，
 * 迟早一套会漂——这次漂的是 CI 与本机。
 *
 * 事故实底（CI run 36308220834 = `8226b73`）：两份字节基线在**本机 6 格全绿、CI 6 格全红**。
 * 根因不在生产码，在仪器：`isoNow()` 用的是 JVM 默认时区，
 * 本机是 `+08:00`（`2026-09-27T17:09:23+08:00`，25 字符），CI 跑在 UTC
 * （`2026-09-27T09:09:23Z`，20 字符）。于是两个方向同时错开：
 * ① 时刻归一正则当时只认带 `[+-]偏移` 的形状，`Z` 那种没抹掉；
 * ② 更阴的是基线里还钉着**字节数**，而字节数直接含时刻串的长度——
 *    就算把 ① 修好，字节数仍会差 5。
 * ⇒ 光把 `Z` 加进正则只是把红从"哈希不同"挪到"长度不同"，所以要把时钟本身钉住。
 *
 * 钉 `Asia/Shanghai` 而不是钉 UTC，是因为现有基线表（归一哈希 + 字节数）是在这台机器上
 * 实测录下来的；换基准就得整表重录，而"重录后的表还钉不钉得住旧行为"又要另做一发对照。
 * 选哪个都不影响这条判据的意义：**同一份码在两台机器上必须量出同一串字节**。
 */
internal object FixedClockZone {

    /** 与基线录制时一致的标准时区 */
    const val ZONE_ID = "Asia/Shanghai"

    /** 装上钉死的时区，返回原来的（供 [restore]） */
    fun install(): TimeZone {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE_ID))
        return previous
    }

    fun restore(previous: TimeZone?) {
        previous?.let { TimeZone.setDefault(it) }
    }
}
