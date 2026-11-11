# Pilot 执行报告（2026-11-08 18:08:44）

> 指标版本 `pilot_metrics_v0`；本报告只验证指标能否正确计算，**不做冠军判决**。
> 程序分/契约读数 ≠ 回复质量；开放式质量见盲评包（NOT_COLLECTED 项如实标注）。
> 客观诊断层 `benchmark_v1_gold_diag_v0`：跨四组统一的 gold 断言确定性检查（诊断用，非判决）。

**mock/capture 模式注记**：harness 组（LEGACY/CURRENT）的 `illegal_source_id` veto 来自 MockAdapter 台本引用了不存在的消息源（m0/m2），属离线伪影；DIRECT/COT 轻量契约 `{action, recommended}` 不在 harness 评分器协议键内（其 veto 为预期伪影，判据以归一化 parse/contract 为准）。真实行为读数仅来自 --live。

| 组 | 轮位 | 可用回复 | 合法不回 | parse_ok | contract_ok | 主要错误 | p50/p95 时延(ms) | prompt tok | completion tok | 程序分均值 |
|---|---|---|---|---|---|---|---|---|---|---|
| DIRECT | 64 | 64 | 0 | 1.000 | 1.000 | 无 | 10170/33479 | 80690 | 16652 | 0.0 |
| COT | 0 | - | - | - | - | - | - | - | - | - |
| LEGACY | 0 | - | - | - | - | - | - | - | - | - |
| CURRENT | 64 | 64 | 0 | 1.000 | 1.000 | 无 | 50074/105588 | 184524 | 84770 | 10.0 |

## 客观诊断（gold_diag_v0，跨四组统一）

| 组 | 可判轮 | N/A 轮 | 取消族坐实违例 | required 命中/总数 | 命中率 |
|---|---|---|---|---|---|
| DIRECT | 19 | 45 | 0 | 0/20 | 0.0 |
| CURRENT | 19 | 45 | 0 | 0/20 | 0.0 |


## 运行记录

- DIRECT/E063 [live] ok 
- DIRECT/LRE121 [live] ok 
- CURRENT/LRE121 [live] ok 
- CURRENT/E063 [live] ok 
