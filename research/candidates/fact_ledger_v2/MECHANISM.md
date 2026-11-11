# MECHANISM.md — fact_ledger_v2

**v2 delta（相对父本 fact_ledger_v1）**：FLREG 回归门 FAIL 归因 **H-主观句错账**（HER 的推测/抱怨/情绪/意愿句被 capture 入账，以账本权威口吻回注，诱导模型把未知项断言成事实并替她拍板，见 refs/experiment_protocol_20261023/long_probe_20261030/FLREG_VERDICT_20261101.md）。三处针对性修改，其余全部继承 v1：账本 schema、注入位置（种子块后、历史消息段前）、预算 800 CU、k=12、pin 规则、supersede/cancel pass、容量帽 120、每轮恰好 1 次 generate。

**存储**：与 v1 相同——真实消息经 v0 稳定 uid 入 events 后，对本轮每条 HER/ME 消息跑规则抽取（ledger_rules.py）：按 `[，。！？；\n]` 切 clause（≤60 UTF-16 码元），**v2 在 R1–R6 之前新增 R0 排除层**：clause 无硬锚（数字/日期/时点/金额均无）且命中以下任一语言学信号则不入账——a 推测/不确定语气（可能/大概/也许/好像/是不是/能不能/要不要/说不定/万一/应该吧/吧？/觉得/感觉/我猜）；b 情绪宣泄主导（气死/烦死/火大/崩溃/麻了/无语/裂开/好家伙/离谱）；c 纯意愿/假设（要是/真想/恨不得/如果…就）。判断依据是语言学信号不是内容审查：含硬锚的句子即使带情绪词也保留（如"气死了 约的不是九点"有"九点"时点锚，仍按 R2 入账）。问句排除（无 R3 信号）保持 v1 口径。其余（条目 schema、逐字原文、subject 判不准即 UNKNOWN、valid_time 解析不出留 ""、模型草稿永不入账）同 v1。

**检索**：与 v1 相同的 `score = w_type + 0.8*rel + 0.6*exp(-Δturns/16)`（rel=r7 bigram overlap，数字相交 +0.3）、pin 上限 6、`_norm_cn` 同义去重、超预算按 (pinned, score) 从低往高丢。**v2 两处注入侧修改**：①降权——kind=other/event 且 subject=HER 且无 digits 且无 valid_time 的行 w_type 从 0.4/0.6 降到 0.15（有硬锚的行不变）；②来源透明化——subject=HER 且 origin=message 且 kind∈{other,event} 的行注入时加 `‹她说› ` 前缀（如 `- [F012|事件|T05|05-05] ‹她说› 主讲那场会挪不动（HER）`），promise/date/time/number/amount 有客观锚不加。给模型的信号：这行是她的原话不是已证事实，不得当作已证事实复述或加码。

**意图**：与 v1/v0 完全一致——当前有效 intent 文本/状态经父本 build_intent_block 独立分区逐字进入请求，只取当轮、关闭即清空；intent 不参与账本写入，仅参与注入相关度打分，不设意图路由分支。

**上下文**：v0 六段装配顺序不变，账本段仍插第 4 位；各段职责与预算同 v0（历史 1400 CU 整条裁剪、当前输入完整保留）；supersede/cancel pass（移植自 r7 纯函数）、seed 永不降级、条目 >120 时 kind=other 最旧优先淘汰——全部同 v1；每轮 notes 记 `ledger_injected/ledger_cu/superseded_this_turn/cancelled_this_turn` 可复核。
