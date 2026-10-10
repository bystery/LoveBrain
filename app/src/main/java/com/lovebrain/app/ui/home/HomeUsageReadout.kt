package com.lovebrain.app.ui.home

import com.lovebrain.app.core.designsystem.LbMetric
import com.lovebrain.app.viewmodel.HomeUsageReadout
import com.lovebrain.app.viewmodel.costReadout
import java.util.Locale
import kotlin.math.absoluteValue

/*
 * 首页那一块累计使用**小卡**的口径层。
 *
 * **为什么这一块现在可以存在**：2026-10-10 新指导书 §五 取舍③（活台账 M25）是本会话的**新决定**，
 * 用户原话「首页可以重新加入累计使用面板。这是你这次的新决定，优先于旧文档里删除使用概览的规定。
 * 只是需要控制规模，维持一块简洁的小卡，不恢复大而复杂的仪表盘。」⇒ 它**替代**旧规
 * 「不恢复使用概览 / 不增加无业务价值的统计仪表盘 / 不恢复内部统计」那几条禁令；
 * 被替代的只是"不许有"，**"一块简洁小卡"是这次新决定自带的上限**，所以下面第 4 条把规模写成判据。
 *
 * **数字来路只有一条**：四格的值全部出自 [HomeUsageReadout]（`HomeStatusViewModel.usage`
 * 那一路只读快照，字段原样读自 `SettingsStorePort` 的 `totalGenerateCount` / `totalCostYuan` /
 * `totalCopyCount` / `totalAdoptCount`，落盘在 `data/SecurePrefs.kt:472-490`）。
 * 刷新点唯一：`HomeStatusViewModel.returnedFromSubpage`（进页面/从子页回来时一次本地读盘，
 * 不发任何请求），入口是 `HomeScreen` 的 `LaunchedEffect(Unit)` 与 ON_RESUME。
 * 与悬浮面板顶部那条 `UsageStatBar` 读的是**同一批数**——这里不新建第二本统计账。
 *
 * 这里收的是**四件事**，一件都不是画形状（形状交设计系统 `LbMetricGrid`，本文件不认识 Composable）：
 * 1. **数字只有一个来源**：四格全部从上面那一条只读通道取——界面里不写死任何一颗数。
 * 2. **花费口径不撒谎**：走面板/详情页同一颗 [costReadout] 判据（一笔可计价记录都没入过账时念「—」，
 *    绝不念成 0/免费）。
 * 3. **文字一律在资源里**：本文件不内联任何中文——标签由调用方用 `stringResource` 解好传进来
 *    （`R.string.home_usage_*` 那五颗键：四颗标签 + 一颗带单位的计数串），
 *    单位"次"与占位串（「—」「不足 …」）也都来自资源，所以 `UiStringLiteralBudgetTest` 四栏读数
 *    不因这一格变化。
 * 4. **规模是判据**：这一格**只有四格**（累计生成 / 复制 / 采纳 / 花费），
 *    多一格就不是"简洁小卡"；刻意不收"内部指标/版本区"那一类（上一轮已删、本轮新指导书第④条明令
 *    不许恢复），也不收端口那第五颗 `totalRewriteCount`（没有格子画它，带进快照就是死读数）。
 */

/**
 * 首页这一格自带的货币符号（全角 ￥，与面板顶部那条的半角 ¥ 分家：那条 10sp、这一条 18sp，
 * 两处口径本来就不同，见 `UsageStats.kt` 的 amountText 说明）。
 * ⚠ 它不是"文字"（`HAN_LITERAL` 数不到全角货币符），也不是统计账里的一颗数——只是格式化旋钮。
 * `internal` 而非 `private`：`cost_below_cent` 那条资源也要用同一个符号（"不足 ￥0.01"），
 * 让这一颗符号在首页这一格只有一位主人，不在调用点抄第二遍。
 */
internal const val HOME_COST_CURRENCY = "￥"

/** 首页的花费读数取**两位小数**（与旧版使用概览同一口径，不锁 Locale；面板那条才是三位+锁 US） */
internal fun homeYuanText(yuan: Double): String =
    HOME_COST_CURRENCY + String.format(Locale.getDefault(), "%.2f", yuan.absoluteValue)

/**
 * 把 VM 交回的只读快照换成 `LbMetricGrid` 要的那四格。
 *
 * @param readout 唯一的数据来源（盘上那四颗被这一屏画出的 `total*` 的只读视图，见 [HomeUsageReadout]）
 * @param countUnit 已带单位的整串（如「62 次」——"次"由调用方从 `R.string.home_usage_count` 解出）
 * @param generatedLabel/copiedLabel/adoptedLabel/costLabel 四格标签，全部来自资源
 * @param costUnknown/costBelowCent [costReadout] 的两条非数值占位串，同样来自资源
 */
fun homeUsageMetrics(
    readout: HomeUsageReadout,
    generatedLabel: String,
    copiedLabel: String,
    adoptedLabel: String,
    costLabel: String,
    countUnit: (Int) -> String,
    costUnknown: String,
    costBelowCent: String
): List<LbMetric> = listOf(
    LbMetric(generatedLabel, countUnit(readout.totalGenerateCount)),
    LbMetric(copiedLabel, countUnit(readout.totalCopyCount)),
    LbMetric(adoptedLabel, countUnit(readout.totalAdoptCount)),
    LbMetric(costLabel, costReadout(readout.totalCostYuan, costUnknown, costBelowCent, ::homeYuanText))
)
