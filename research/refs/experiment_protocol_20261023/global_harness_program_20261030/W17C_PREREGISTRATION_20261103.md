# W17c 合并复评 · 跑前冻结（2026-11-03 12:10）

> 本文件在 live 批产出任何评审/判决读数前写定。来源门逐字继承，不新增事后口径。
> 批：BATCH-W17C-MERGE-20261103（144 attempts，6 篇未暴露 12 轮故事）。
> 臂：parent=`action_policy_r9_slim`，candidate=`window_expand_b_norecap_guard`。
> 计划 sha `b42cdf2c…`，授权 `AUTHORIZATION_W17C.json`（cap 144，no_retry/no_makeup/fail-fast）。

## 1. 假设

H_MERGE_GUARD：在 ≤24 轮未暴露域，带契约守卫的 b_norecap 相对 r9_slim 满足全部门，
可替代 r9_slim 成为 ≤24 轮域冠军，并证明 W17b 的守卫收益可泛化到新故事（不只是已暴露回归）。

## 2. 门（全部继承，不改）

| 门 | 判据 | 来源 |
|---|---|---|
| G1 九维 | 配对差（cand−parent）最差维 ≥ −0.3；**且** interact / emotion_relation / initiative 各 ≥ −0.2 | TASK_QUEUE W17-03 acceptance（=W17_SINGLE_CHAMPION_DESIGN G1 + W17B_GUARD_DESIGN G3） |
| G2 severe | B3 主会话亲读后 candidate ≤ parent；无据确定化族不得新增 | W17-03 acceptance（=W17_SINGLE_CHAMPION_DESIGN G3） |
| G3 契约 | 契约违规位数 cand ≤ parent+1 | W17-03 acceptance（=W17B_GUARD_DESIGN G1） |
| G4 空回复 | candidate 空 response = 0 | W17-03 acceptance（=W17B_GUARD_DESIGN G2） |
| G5 成本 | prompt/token/等待如实报告；**不作门**；守卫不改 prompt（预期 0 增幅）、0 额外调用作为事实核查 | W17-03 acceptance + W17_SINGLE_CHAMPION_DESIGN G4 |
| 诊断（不改判） | T07–T12 机械分 keep ≥ 0.95×parent | W17_SINGLE_CHAMPION_DESIGN G2 遗留；本批复核并报告，若 <0.95 写入“后半段退化待复核”，不单独触发维持双冠军 |

## 3. 判决表（冻结）

| 情形 | 判决 |
|---|---|
| G1–G5 全过 | **MERGE**：≤24 轮冠军 = `window_expand_b_norecap_guard`，r9_slim 转历史（证据保留）；双冠军中 ≤24 侧统一为 b_norecap 家族 |
| G1 ∈ [−0.5, −0.3) 且其余过且成本显著更低 | **不合并**：登记“分区可用”，不代用户接受质量换资源 |
| G1 < −0.5 | **维持双冠军**：r9_slim 在 ≤24 域有机制增量价值 |
| G2 破 | **维持双冠军 + 登记新故障族** |
| G3 或 G4 破 | **不合并**：守卫在未暴露篇失效或产生空回复，回到 W17b 重设计（不调参重跑本批） |
| 任一臂 CALL_FAIL >10% | 该篇降权；有效篇 <4 → 证据不足，不判决 |

## 4. 分析步骤（冻结）

1. 机械：9/10 分位次、T07–T12 keep、契约违规与空回复计数（逐篇 + 合计）。
2. 九维：`run_lp_review_generic` 每篇 main 顺序 + swap 补充；合并用 `merge_lp_reviews.py`。
3. B3 亲读：全部 judge severe 旗位（两臂、零抽样），主会话逐位读，落 `LP_W17C_B3_READRESULT.json`。
4. 成本：calls.json 实测 prompt CU 与调用数；等待只报告（并行批不作 claims）。
5. 判决 + 注册表同步（TASK_QUEUE / RESEARCH_STATE / RUNBOOK）。

## 5. 诚实边界

- 六篇均未出现在 W17/W17b/W18，但同属已见故事域的模板族；结论是“未暴露篇泛化”，不是新域泛化。
- 评审仍为 AI 九维 + 主会话亲读，不能代替用户愿发偏好。
- 并行批的端到端等待受双批竞争影响，只作运行观察，不当作候选性能证据。
