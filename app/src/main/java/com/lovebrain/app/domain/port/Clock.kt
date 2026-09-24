package com.lovebrain.app.domain.port

import com.lovebrain.app.util.TimeFmt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 时间端口（复核 §4 DIP 目标里列的 `Clock`：domain 只依赖
 * AiGateway / KnowledgeReadPort / KnowledgeWritePort / Clock）。
 *
 * 为什么值得单独立一个端口：domain 里有三类"读时间"，前两类的结果**会被写进文件
 * 或送进 prompt**，所以它们不是日志而是输入：
 *
 * 1. [wallClock] —— 轮次块头 `- [yyyy-MM-dd HH:mm]`、归档块标题。写进 recent.md /
 *    raw_topic.md，等于知识库内容的一部分。
 * 2. prompt 里的「## 当前时间」段。同一个输入在 09:59 和 10:01 会得到不同 prompt，
 *    于是"BENCHMARK 那串 hash 描述的是这棵树"这种话永远差一个变量。
 * 3. `System.currentTimeMillis()` 算耗时（GenerationEngine 那 14 处 PERF 计时）。
 *    这类**不进任何状态**，不需要端口，硬套 Clock 只会制造假抽象（YAGNI）。
 *
 * 有了 1 和 2，测试才能钉住时间：注入 FixedClock 之后，"这条轮次记录的块头到底是
 * 什么"变成可断言的事实，而不是"跑一次看它像不像今天"。
 */
interface Clock {
    /** 落进知识库正文与 prompt 的分钟级时间戳，格式与 [TimeFmt.now] 一致 */
    fun wallClock(): String

    /** 日期粒度（今日锦囊的"当日快照"这类判断用） */
    fun today(): String

    /** 毫秒数：给"话题多久没换"这类差值计算用 */
    fun epochMs(): Long
}

/** 生产实现：就是直接读系统时间，唯一职责是别处不许再自己 new SimpleDateFormat */
object SystemClock : Clock {
    override fun wallClock(): String = TimeFmt.now()
    override fun today(): String = TimeFmt.today()
    override fun epochMs(): Long = System.currentTimeMillis()
}

/**
 * 测试用的固定时钟。放在 main 里是因为 androidTest 与 unitTest 都要用，
 * 而 §5.1 的目标结构里它最终属于 `core/testing`（等包边界稳定后再模块化）。
 *
 * [advanceMinutes] 存在的原因：光有"固定"测不到依赖时间的逻辑——
 * 冷却、话题老化、当日锦囊换天，都需要时间真的往前走一步才能触发被测分支
 * （这正是复核 §9 第 3 条说的"测试必须真的走进生产路径"）。
 */
class FixedClock(startMs: Long = DEFAULT_START_MS) : Clock {
    private var ms: Long = startMs

    fun advanceMinutes(minutes: Int) { ms += minutes * 60_000L }

    fun advanceHours(hours: Int) = advanceMinutes(hours * 60)

    override fun epochMs(): Long = ms

    override fun wallClock(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

    override fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ms))

    companion object {
        /** 2026-09-24 09:00 —— 与本轮被审提交同一天，便于和真实产物对得上 */
        const val DEFAULT_START_MS = 1_758_685_200_000L
    }
}
