package com.lovebrain.app.model

/**
 * 结果区当前展示的是哪一类结果——普通回复还是主动开场。
 *
 * 原先它是 `LoveBrainViewModel` 的嵌套 enum。搬到 `model` 有两个原因，缺一不可：
 * `feature/` 包不许 import `viewmodel`（包依赖棘轮管着这条），而"模式"要归
 * [com.lovebrain.app.feature.proactive.ProactiveStore] 所有；UI 与 androidTest
 * 又都直接按类型名引用它，嵌套在 VM 里就永远搬不动。
 */
enum class ResultMode { REPLY, PROACTIVE }

/**
 * Composer 的模式 —— UI 会话状态的单一事实源。
 *
 * - [REPLY]：普通回复模式（默认）
 * - [PROACTIVE]：主动发模式（蓝字切换进入，**不发网络请求**）
 *
 * 它替代了更早的写法：`inputMode` / `resultMode` / `isProactive` 三个变量各自推断"现在是什么模式"，
 * 于是同一个瞬间能读出三个互相矛盾的答案。搬出 VM 的原因同 [ResultMode]。
 */
enum class ComposerMode { REPLY, PROACTIVE }
