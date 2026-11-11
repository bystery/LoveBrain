# Pilot 执行报告（2026-11-08 18:08:41）

> 指标版本 `pilot_metrics_v0`；本报告只验证指标能否正确计算，**不做冠军判决**。
> 程序分/契约读数 ≠ 回复质量；开放式质量见盲评包（NOT_COLLECTED 项如实标注）。
> 客观诊断层 `benchmark_v1_gold_diag_v0`：跨四组统一的 gold 断言确定性检查（诊断用，非判决）。

**mock/capture 模式注记**：harness 组（LEGACY/CURRENT）的 `illegal_source_id` veto 来自 MockAdapter 台本引用了不存在的消息源（m0/m2），属离线伪影；DIRECT/COT 轻量契约 `{action, recommended}` 不在 harness 评分器协议键内（其 veto 为预期伪影，判据以归一化 parse/contract 为准）。真实行为读数仅来自 --live。

| 组 | 轮位 | 可用回复 | 合法不回 | parse_ok | contract_ok | 主要错误 | p50/p95 时延(ms) | prompt tok | completion tok | 程序分均值 |
|---|---|---|---|---|---|---|---|---|---|---|
| DIRECT | 228 | 225 | 3 | 1.000 | 1.000 | 无 | 14780/38267 | 251317 | 93053 | 0.132 |
| COT | 0 | - | - | - | - | - | - | - | - | - |
| LEGACY | 0 | - | - | - | - | - | - | - | - | - |
| CURRENT | 228 | 228 | 0 | 1.000 | 0.996 | veto:illegal_source_id:1,1×1 | 76881/141005 | 578146 | 367164 | 9.949 |

## 客观诊断（gold_diag_v0，跨四组统一）

| 组 | 可判轮 | N/A 轮 | 取消族坐实违例 | required 命中/总数 | 命中率 |
|---|---|---|---|---|---|
| DIRECT | 1 | 227 | 0 | 0/1 | 0.0 |
| CURRENT | 1 | 227 | 0 | 0/1 | 0.0 |

## 失败/异常轮位明细（如实保留，不伪造完整结果）

| 组 | case | 错误 |
|---|---|---|
| CURRENT | E307_T02 | veto:illegal_source_id:1,1 |

## 运行记录

- CURRENT/E304 [live] ok 
- CURRENT/E305 [live] ok 
- CURRENT/E306 [live] ok 
- CURRENT/E307 [live] ok 
- CURRENT/E308 [live] ok 
- CURRENT/E309 [live] ok 
- CURRENT/E310 [live] ok 
- CURRENT/E311 [live] ok 
- CURRENT/E312 [live] ok 
- CURRENT/E313 [live] ok 
- CURRENT/E314 [live] ok 
- CURRENT/E315 [live] ok 
- CURRENT/E316 [live] ok 
- CURRENT/E317 [live] ok 
- CURRENT/E318 [live] ok 
- CURRENT/E319 [live] ok 
- CURRENT/E301 [live] ok 
- CURRENT/E302 [live] ok 
- CURRENT/E303 [live] ok 

**LIVE BLOCKED**: live precheck 未通过 ['provider 检查未通过: BLOCKED（/models 返回 HTTP 503（Key 可能无权限或无效））']
