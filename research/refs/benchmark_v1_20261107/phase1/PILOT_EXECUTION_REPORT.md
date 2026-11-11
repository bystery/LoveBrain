# Pilot 执行报告（2026-11-07 22:21:13）

> 指标版本 `pilot_metrics_v0`；本报告只验证指标能否正确计算，**不做冠军判决**。
> 程序分/契约读数 ≠ 回复质量；开放式质量见盲评包（NOT_COLLECTED 项如实标注）。
> 客观诊断层 `benchmark_v1_gold_diag_v0`：跨四组统一的 gold 断言确定性检查（诊断用，非判决）。

**mock/capture 模式注记**：harness 组（LEGACY/CURRENT）的 `illegal_source_id` veto 来自 MockAdapter 台本引用了不存在的消息源（m0/m2），属离线伪影；DIRECT/COT 轻量契约 `{action, recommended}` 不在 harness 评分器协议键内（其 veto 为预期伪影，判据以归一化 parse/contract 为准）。真实行为读数仅来自 --live。

| 组 | 轮位 | 可用回复 | 合法不回 | parse_ok | contract_ok | 主要错误 | p50/p95 时延(ms) | prompt tok | completion tok | 程序分均值 |
|---|---|---|---|---|---|---|---|---|---|---|
| DIRECT | 72 | 72 | 0 | 1.000 | 0.000 | veto:unknown_payload_field×72 | 4/7 | 0 | 0 | 0.0 |
| COT | 72 | 72 | 0 | 1.000 | 0.000 | veto:unknown_payload_field×72 | 5/7 | 0 | 0 | 0.0 |
| LEGACY | 20 | 20 | 0 | 1.000 | 0.000 | veto:illegal_source_id:m2×20 | 5/10 | 0 | 0 | 0.0 |
| CURRENT | 72 | 72 | 0 | 1.000 | 0.000 | veto:illegal_source_id:m2×72 | 6/9 | 0 | 0 | 0.0 |

## 客观诊断（gold_diag_v0，跨四组统一）

| 组 | 可判轮 | N/A 轮 | 取消族坐实违例 | required 命中/总数 | 命中率 |
|---|---|---|---|---|---|
| DIRECT | 31 | 41 | 0 | 0/31 | 0.0 |
| COT | 31 | 41 | 0 | 0/31 | 0.0 |
| LEGACY | 1 | 19 | 0 | 0/1 | 0.0 |
| CURRENT | 31 | 41 | 0 | 0/31 | 0.0 |

## 失败/异常轮位明细（如实保留，不伪造完整结果）

| 组 | case | 错误 |
|---|---|---|
| DIRECT | E032_T01 | veto:unknown_payload_field |
| DIRECT | E032_T02 | veto:unknown_payload_field |
| DIRECT | E032_T03 | veto:unknown_payload_field |
| DIRECT | E032_T04 | veto:unknown_payload_field |
| DIRECT | E032_T05 | veto:unknown_payload_field |
| DIRECT | E032_T06 | veto:unknown_payload_field |
| DIRECT | E032_T07 | veto:unknown_payload_field |
| DIRECT | E032_T08 | veto:unknown_payload_field |
| DIRECT | E032_T09 | veto:unknown_payload_field |
| DIRECT | E032_T10 | veto:unknown_payload_field |
| DIRECT | E032_T11 | veto:unknown_payload_field |
| DIRECT | E032_T12 | veto:unknown_payload_field |
| DIRECT | E037_T01 | veto:unknown_payload_field |
| DIRECT | E037_T02 | veto:unknown_payload_field |
| DIRECT | E037_T03 | veto:unknown_payload_field |
| DIRECT | E037_T04 | veto:unknown_payload_field |
| DIRECT | E037_T05 | veto:unknown_payload_field |
| DIRECT | E037_T06 | veto:unknown_payload_field |
| DIRECT | E037_T07 | veto:unknown_payload_field |
| DIRECT | E037_T08 | veto:unknown_payload_field |
| DIRECT | E191_T01 | veto:unknown_payload_field |
| DIRECT | E191_T02 | veto:unknown_payload_field |
| DIRECT | E191_T03 | veto:unknown_payload_field |
| DIRECT | E191_T04 | veto:unknown_payload_field |
| DIRECT | E191_T05 | veto:unknown_payload_field |
| DIRECT | E191_T06 | veto:unknown_payload_field |
| DIRECT | E191_T07 | veto:unknown_payload_field |
| DIRECT | E191_T08 | veto:unknown_payload_field |
| DIRECT | E191_T09 | veto:unknown_payload_field |
| DIRECT | E191_T10 | veto:unknown_payload_field |
| DIRECT | E191_T11 | veto:unknown_payload_field |
| DIRECT | E191_T12 | veto:unknown_payload_field |
| DIRECT | LRE117_T01 | veto:unknown_payload_field |
| DIRECT | LRE117_T02 | veto:unknown_payload_field |
| DIRECT | LRE117_T03 | veto:unknown_payload_field |
| DIRECT | LRE117_T04 | veto:unknown_payload_field |
| DIRECT | LRE117_T05 | veto:unknown_payload_field |
| DIRECT | LRE117_T06 | veto:unknown_payload_field |
| DIRECT | LRE117_T07 | veto:unknown_payload_field |
| DIRECT | LRE117_T08 | veto:unknown_payload_field |

## 运行记录

- DIRECT/E032 [capture] ok 
- DIRECT/E037 [capture] ok 
- DIRECT/E191 [capture] ok 
- DIRECT/LRE117 [capture] ok 
- COT/E037 [capture] ok 
- COT/E191 [capture] ok 
- COT/LRE117 [capture] ok 
- COT/E032 [capture] ok 
- LEGACY/E191 [capture] error turns_done=None
- LEGACY/LRE117 [capture] error turns_done=None
- LEGACY/E032 [capture] ok 
- LEGACY/E037 [capture] ok 
- CURRENT/LRE117 [capture] ok 
- CURRENT/E032 [capture] ok 
- CURRENT/E037 [capture] ok 
- CURRENT/E191 [capture] ok 
