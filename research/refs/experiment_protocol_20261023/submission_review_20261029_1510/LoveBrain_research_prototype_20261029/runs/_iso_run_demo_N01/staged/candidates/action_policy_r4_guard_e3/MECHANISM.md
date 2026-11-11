# MECHANISM — action_policy_r4_guard_e3

父本 `action_policy_r2`，全量运行时继承（与 `action_policy_r4_guard_final` 同一条继承链）。
本身份是 F5 修复身份：与冻结的 `action_policy_r4_guard_final` 相比，只有 vendored `output_repair.py` 多出 E3 一条规则；
其余机制（含提示词、动作合同、主体证据守卫）逐字节相同。下面记录本候选相对 r2 新增的四项。

## 存储（storage）
继承 r2/r1 的证据事实库：每条事实带 fact_id/subject/relation/evidence_uids/evidence_quotes/status/history。
本候选新增：当 analysis.facts 的关系性断言（认识/介绍/交往/同事/朋友/关系等）只引一条消息、且引文没有双方共同支持时，
不把模型摘要的主体当作已证事实——
- 单说话人且 quote 非空：把文本改成“我说：<quote>”/“她说：<quote>”，subject 改为该说话人，保留逐字证据；
- 多说话人或 quote 为空：subject 置 UNKNOWN，按父本规则落 `unverified_src`，不参与投影。
更正类 updates 若改的是关系性 new_text 且 quote 无双方支持，整条 update 丢弃并留损，不静默改旧事实。

## 检索/注入（retrieval & injection）
不改父本的 facts/tentatives/updates 预算与相关度投影（500/300/220 CU，1400 CU 历史窗）。本候选只保证被修复后的 analysis
进入父本写回，因此检索层看到的是规范 JSON，而不是被截断/坏键破坏的 analysis。

## 意图处理（intent）
不改 r2/r1 的目标作用域、动作能力表与发送合同。r3 的 FIX-9 不采纳：F0 已证明三格“过度沉默”不成立。
`hygiene_wording_fix` 只改注入文字里的括号说明，不改变动作语义。

## 上下文组织（context）
父本负责分区、当前窗口不裁剪、历史预算与证据投影。本候选新增一个网关返回适配层：
1. 在父机制 `_extract_json(reply)` 之前，复用 `output_repair.repair` 修复围栏/弯引号/提前闭合/键名别名/directions 超槽；
   **F5 增补**：`output_repair` 新增 E3——模型把最后的 `analysis` 多包了一层 `{ }`（N01_T05 形态，实测还伴随
   directions 数组用 `}` 收尾）时，在 I1/E2 都失败后，最多归一 1 处错配闭合符并删除 1 对多余花括号；
   只有重拼后能解析且顶层同时含 response/directions/analysis 才生效，文字与键值一律不动。
2. 额外方向、别名冲突全文移入 notes，返回 JSON 保持四槽与协议键；
3. 原文仍由 ModelGateway 先写 calls.json；reply_raw.txt 是 harness 规范返回，不能冒充模型原文；
4. 若 raw 不可解析，不编造修复，原样交给父本（记忆写回失败但留损）。

## 消融
- `hygiene_wording_fix=false`：注入文字逐字回到 r2。
- `contract_guard_fix=false`：不做 JSON 规范，父本直接吃 raw。
- `subject_evidence_fix=false`：不做关系主体替换/丢弃。
三项全关时除薄包装外行为回到 r2；旧 r2/r3 文件未被改名或覆盖。
