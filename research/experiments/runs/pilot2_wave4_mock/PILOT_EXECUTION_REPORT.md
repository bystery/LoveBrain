# Pilot 执行报告（2026-11-08 11:01:31）

> 指标版本 `pilot_metrics_v0`；本报告只验证指标能否正确计算，**不做冠军判决**。
> 程序分/契约读数 ≠ 回复质量；开放式质量见盲评包（NOT_COLLECTED 项如实标注）。
> 客观诊断层 `benchmark_v1_gold_diag_v0`：跨四组统一的 gold 断言确定性检查（诊断用，非判决）。

**mock/capture 模式注记**：harness 组（LEGACY/CURRENT）的 `illegal_source_id` veto 来自 MockAdapter 台本引用了不存在的消息源（m0/m2），属离线伪影；DIRECT/COT 轻量契约 `{action, recommended}` 不在 harness 评分器协议键内（其 veto 为预期伪影，判据以归一化 parse/contract 为准）。真实行为读数仅来自 --live。

| 组 | 轮位 | 可用回复 | 合法不回 | parse_ok | contract_ok | 主要错误 | p50/p95 时延(ms) | prompt tok | completion tok | 程序分均值 |
|---|---|---|---|---|---|---|---|---|---|---|
| DIRECT | 488 | 488 | 0 | 1.000 | 1.000 | 无 | 4/7 | 0 | 0 | 0.0 |
| COT | 0 | - | - | - | - | - | - | - | - | - |
| LEGACY | 0 | - | - | - | - | - | - | - | - | - |
| CURRENT | 488 | 488 | 0 | 1.000 | 0.000 | veto:illegal_source_id:m2×488 | 5/8 | 0 | 0 | 0.0 |

## 客观诊断（gold_diag_v0，跨四组统一）

| 组 | 可判轮 | N/A 轮 | 取消族坐实违例 | required 命中/总数 | 命中率 |
|---|---|---|---|---|---|
| DIRECT | 19 | 469 | 0 | 0/26 | 0.0 |
| CURRENT | 19 | 469 | 0 | 0/26 | 0.0 |

## 失败/异常轮位明细（如实保留，不伪造完整结果）

| 组 | case | 错误 |
|---|---|---|
| CURRENT | E301_T01 | veto:illegal_source_id:m2 |
| CURRENT | E301_T02 | veto:illegal_source_id:m2 |
| CURRENT | E301_T03 | veto:illegal_source_id:m2 |
| CURRENT | E301_T04 | veto:illegal_source_id:m2 |
| CURRENT | E301_T05 | veto:illegal_source_id:m2 |
| CURRENT | E301_T06 | veto:illegal_source_id:m2 |
| CURRENT | E301_T07 | veto:illegal_source_id:m2 |
| CURRENT | E301_T08 | veto:illegal_source_id:m2 |
| CURRENT | E301_T09 | veto:illegal_source_id:m2 |
| CURRENT | E301_T10 | veto:illegal_source_id:m2 |
| CURRENT | E301_T11 | veto:illegal_source_id:m2 |
| CURRENT | E301_T12 | veto:illegal_source_id:m2 |
| CURRENT | E302_T01 | veto:illegal_source_id:m2 |
| CURRENT | E302_T02 | veto:illegal_source_id:m2 |
| CURRENT | E302_T03 | veto:illegal_source_id:m2 |
| CURRENT | E302_T04 | veto:illegal_source_id:m2 |
| CURRENT | E302_T05 | veto:illegal_source_id:m2 |
| CURRENT | E302_T06 | veto:illegal_source_id:m2 |
| CURRENT | E302_T07 | veto:illegal_source_id:m2 |
| CURRENT | E302_T08 | veto:illegal_source_id:m2 |
| CURRENT | E302_T09 | veto:illegal_source_id:m2 |
| CURRENT | E302_T10 | veto:illegal_source_id:m2 |
| CURRENT | E302_T11 | veto:illegal_source_id:m2 |
| CURRENT | E302_T12 | veto:illegal_source_id:m2 |
| CURRENT | E303_T01 | veto:illegal_source_id:m2 |
| CURRENT | E303_T02 | veto:illegal_source_id:m2 |
| CURRENT | E303_T03 | veto:illegal_source_id:m2 |
| CURRENT | E303_T04 | veto:illegal_source_id:m2 |
| CURRENT | E303_T05 | veto:illegal_source_id:m2 |
| CURRENT | E303_T06 | veto:illegal_source_id:m2 |
| CURRENT | E303_T07 | veto:illegal_source_id:m2 |
| CURRENT | E303_T08 | veto:illegal_source_id:m2 |
| CURRENT | E303_T09 | veto:illegal_source_id:m2 |
| CURRENT | E303_T10 | veto:illegal_source_id:m2 |
| CURRENT | E303_T11 | veto:illegal_source_id:m2 |
| CURRENT | E303_T12 | veto:illegal_source_id:m2 |
| CURRENT | E304_T01 | veto:illegal_source_id:m2 |
| CURRENT | E304_T02 | veto:illegal_source_id:m2 |
| CURRENT | E304_T03 | veto:illegal_source_id:m2 |
| CURRENT | E304_T04 | veto:illegal_source_id:m2 |

## 运行记录

- DIRECT/E301 [mock] ok 
- DIRECT/E302 [mock] ok 
- DIRECT/E303 [mock] ok 
- DIRECT/E304 [mock] ok 
- DIRECT/E305 [mock] ok 
- DIRECT/E306 [mock] ok 
- DIRECT/E307 [mock] ok 
- DIRECT/E308 [mock] ok 
- DIRECT/E309 [mock] ok 
- DIRECT/E310 [mock] ok 
- DIRECT/E311 [mock] ok 
- DIRECT/E312 [mock] ok 
- DIRECT/E313 [mock] ok 
- DIRECT/E314 [mock] ok 
- DIRECT/E315 [mock] ok 
- DIRECT/E316 [mock] ok 
- DIRECT/E317 [mock] ok 
- DIRECT/E318 [mock] ok 
- DIRECT/E319 [mock] ok 
- DIRECT/E320 [mock] ok 
- DIRECT/E321 [mock] ok 
- DIRECT/E322 [mock] ok 
- DIRECT/E323 [mock] ok 
- DIRECT/E324 [mock] ok 
- DIRECT/E325 [mock] ok 
- DIRECT/E326 [mock] ok 
- DIRECT/E327 [mock] ok 
- DIRECT/E328 [mock] ok 
- DIRECT/E329 [mock] ok 
- DIRECT/E330 [mock] ok 
- DIRECT/E331 [mock] ok 
- DIRECT/E332 [mock] ok 
- DIRECT/E333 [mock] ok 
- DIRECT/E334 [mock] ok 
- DIRECT/E335 [mock] ok 
- DIRECT/E336 [mock] ok 
- DIRECT/E337 [mock] ok 
- DIRECT/E338 [mock] ok 
- CURRENT/E304 [mock] ok 
- CURRENT/E305 [mock] ok 
- CURRENT/E306 [mock] ok 
- CURRENT/E307 [mock] ok 
- CURRENT/E308 [mock] ok 
- CURRENT/E309 [mock] ok 
- CURRENT/E310 [mock] ok 
- CURRENT/E311 [mock] ok 
- CURRENT/E312 [mock] ok 
- CURRENT/E313 [mock] ok 
- CURRENT/E314 [mock] ok 
- CURRENT/E315 [mock] ok 
- CURRENT/E316 [mock] ok 
- CURRENT/E317 [mock] ok 
- CURRENT/E318 [mock] ok 
- CURRENT/E319 [mock] ok 
- CURRENT/E320 [mock] ok 
- CURRENT/E321 [mock] ok 
- CURRENT/E322 [mock] ok 
- CURRENT/E323 [mock] ok 
- CURRENT/E324 [mock] ok 
- CURRENT/E325 [mock] ok 
- CURRENT/E326 [mock] ok 
- CURRENT/E327 [mock] ok 
- CURRENT/E328 [mock] ok 
- CURRENT/E329 [mock] ok 
- CURRENT/E330 [mock] ok 
- CURRENT/E331 [mock] ok 
- CURRENT/E332 [mock] ok 
- CURRENT/E333 [mock] ok 
- CURRENT/E334 [mock] ok 
- CURRENT/E335 [mock] ok 
- CURRENT/E336 [mock] ok 
- CURRENT/E337 [mock] ok 
- CURRENT/E338 [mock] ok 
- CURRENT/E301 [mock] ok 
- CURRENT/E302 [mock] ok 
- CURRENT/E303 [mock] ok 
