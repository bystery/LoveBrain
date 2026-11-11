# MECHANISM.md — fact_ledger_v1

**存储**：真实消息经 v0 稳定 uid 入 events 之后、组装 parts 之前，对本轮每条 HER/ME 消息跑规则抽取（ledger_rules.py）：按 `[，。！？；\n]` 切 clause（≤60 UTF-16 码元），命中 R1 数字+单位 / R2 日期时点 / R3 承诺约定 / R4 变更作废 / R6 事件状态动词 / R5 数字兜底 才入账；条目含逐字原文、subject（HER/ME/SHARED/THIRD/UNKNOWN，判不准即 UNKNOWN）、kind、digits、capture_turn、ts、valid_time（解析不出留 ""）、src_uids、status、origin；抽取零模型调用，模型草稿永不入账。

**检索**：每轮从 status=asserted 且 capture_turn<当前轮 的条目按 `score = w_type + 0.8*rel + 0.6*exp(-Δturns/16)` 排序（rel=r7 bigram overlap 对本轮 HER/ME+note+intent 拼接文本，数字相交 +0.3）；valid_time 未到期的 promise/date/time 强制 pin（上限 6），其余按 score 填满 k=12 行，`_norm_cn` 同义去重；段预算 800 码元，超预算按 (pinned, score) 从低往高丢；段插在画像种子块之后、历史消息段之前，账本不占 v0 的 1400 CU 历史预算；条目 >120 时 kind=other 最旧优先淘汰，promise/date 类 asserted 永不淘汰。

**意图**：与 v0 完全一致——当前有效 intent 文本/状态经父本 build_intent_block 独立分区逐字进入请求，只取当轮、关闭即清空不留旧目标；intent 不参与账本写入，仅参与注入相关度打分（本轮输入拼接项），不设意图路由分支。

**上下文**：v0 六段装配顺序不变（种子 → intent → note → 历史消息 → 当前窗口 → 时钟），仅在其中插入账本段（第 4 位）；每段职责与预算同 v0（历史 1400 CU 整条裁剪、当前输入完整保留）；更正经移植自 r7 的 supersede/cancel pass：更正线索 + bigram overlap ≥0.42（同主体 ≥0.30）+ 数字冲突 → 旧条目 superseded，取消线索 + overlap → cancelled，条目保留可审计但不再注入（F3.2/F3.3 回流阻断），origin=seed 永不降级；每轮 notes 记 `ledger_injected/ledger_cu/superseded_this_turn/cancelled_this_turn` 可复核。
