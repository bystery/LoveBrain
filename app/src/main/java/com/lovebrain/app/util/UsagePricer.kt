package com.lovebrain.app.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * 用量计价器（ 计算核心，计价规则为主人拍板、写死入码）。
 *
 * 计费口径（账本  ）：
 * - 双条件计费：工单地址含 deepseek.com 且 API 返回的 usage 带缓存命中/未命中字段；
 * - 档位按工单模型名含 flash / pro 匹配（flash 判定优先，）；
 * - 高峰时段 = 北京时间周一至周五 9:00-12:00、14:00-18:00（左闭右开），其余为低谷；
 * - 价格表单位 = 元/百万 tokens；其他模型（UNKNOWN 档）不计费返回 0。
 *
 * 纯函数、无 Android 依赖（先例 = HttpsTrustGuard），由 UsagePricerTest 回归钉死。
 * 展示口径：双条件不满足或非闪/PRO 档 → 本次花费位显示占位符（步骤 6 消费侧处理）。
 */
internal object UsagePricer {

    /** 计价档位：按模型名匹配；UNKNOWN = 其他模型，不计费 */
    enum class PriceTier { FLASH, PRO, UNKNOWN }

    private const val PER_MILLION = 1_000_000.0

    /** 价格表（元/百万 tokens）：Triple(缓存命中, 缓存未命中, 输出)——账本数值逐字入码 */
    private val FLASH_OFF_PEAK = Triple(0.05, 1.5, 4.5)
    private val FLASH_PEAK = Triple(0.10, 3.0, 9.0)
    private val PRO_OFF_PEAK = Triple(0.15, 4.5, 13.5)
    private val PRO_PEAK = Triple(0.30, 9.0, 27.0)

    private val BEIJING: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 档位判定（：flash 优先，防双关键词模型名互扰） */
    fun priceTier(model: String): PriceTier {
        val s = model.lowercase()
        return when {
            s.contains("flash") -> PriceTier.FLASH
            s.contains("pro") -> PriceTier.PRO
            else -> PriceTier.UNKNOWN
        }
    }

    /** 高峰判定：北京时间周一至周五 [9:00,12:00)∪[14:00,18:00)，左闭右开 */
    fun isPeakHourBeijing(instant: Instant): Boolean {
        val zdt = instant.atZone(BEIJING)
        if (zdt.dayOfWeek >= DayOfWeek.SATURDAY) return false
        val minutes = zdt.hour * 60 + zdt.minute
        return minutes in 9 * 60 until 12 * 60 || minutes in 14 * 60 until 18 * 60
    }

    /** 本次金额（元）；UNKNOWN 档返回 0（不计费） */
    fun costYuan(
        cacheHitTokens: Long,
        cacheMissTokens: Long,
        outputTokens: Long,
        tier: PriceTier,
        peak: Boolean
    ): Double {
        val (hit, miss, out) = when (tier) {
            PriceTier.FLASH -> if (peak) FLASH_PEAK else FLASH_OFF_PEAK
            PriceTier.PRO -> if (peak) PRO_PEAK else PRO_OFF_PEAK
            PriceTier.UNKNOWN -> return 0.0
        }
        return (cacheHitTokens * hit + cacheMissTokens * miss + outputTokens * out) / PER_MILLION
    }

    /** 计费双条件（主人拍板）：地址含 deepseek.com 且 usage 带缓存字段 */
    fun shouldBill(baseUrl: String, usageHasCacheFields: Boolean): Boolean =
        baseUrl.contains("deepseek.com") && usageHasCacheFields
}
