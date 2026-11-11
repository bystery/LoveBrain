# 已付费答卷覆盖矩阵 V1（臂×位点，离线生成）

- 扫描批数：6；逐位记录 368 条；出现过的臂：evidence_memory_v1, goal_action_v1, reference_product_v1, v3_system_slim, v4_intent_context_r2, v5_purpose_aligned
- 用途：回答『同一道题上到底有几个版本可比』。跨批不同题**不许拼成一条升级曲线**。
- 生成脚本：`tools/panel_coverage_matrix.py`；本脚本真实模型调用 0 次。

| 批次 | 授权单 | 臂(该臂位点数) | 出站prompt归档 | 状态快照归档 | 配置身份 |
|---|---|---|---|---|---|
| BATCH-C4-A-ORIG-VS-GOAL-20261026 | AUTH-C4-SCREEN-20261026-01 | goal_action_v1×32/reference_product_v1×32 | 64/64 | 32/64 | stream+usage |
| BATCH-C4-B-V5-VS-MEM-20261026 | AUTH-C4-SCREEN-20261026-01 | evidence_memory_v1×32/v5_purpose_aligned×32 | 64/64 | 0/64 | stream+usage |
| BATCH-NEWSTORY-96-20261026 | AUTH-NEWSTORY-96-20261026-01 | reference_product_v1×48/v5_purpose_aligned×48 | 96/96 | 48/96 | stream+usage |
| BATCH-R2-CTX-20261024 | AUTH-R2-CTX-20261024-01 | reference_product_v1×24/v4_intent_context_r2×24 | 48/48 | 24/48 | non_stream_or_unknown |
| BATCH-V3-DELETION-20261024 | AUTH-V3-DELETION-20261024-01 | reference_product_v1×24/v3_system_slim×24 | 48/48 | 48/48 | non_stream_or_unknown |
| BATCH-V5-CTX-20261025 | AUTH-V5-CTX-20261025-01 | reference_product_v1×24/v5_purpose_aligned×24 | 48/48 | 24/48 | non_stream_or_unknown |

## 每故事的臂覆盖（同题可比性）

| 故事 | 族 | 位点数 | 有答卷的臂 | 臂数=4的位点 | 位点臂数 min–max |
|---|---|---:|---|---:|---|
| E004 | dev_expansion | 8 | reference_product_v1, v3_system_slim | 0 | 2–2 |
| E005 | dev_expansion | 8 | reference_product_v1, v3_system_slim | 0 | 2–2 |
| E006 | dev_expansion | 8 | evidence_memory_v1, goal_action_v1, reference_product_v1, v3_system_slim, v5_purpose_aligned | 8 | 5–5 |
| E007 | dev_expansion | 8 | evidence_memory_v1, goal_action_v1, reference_product_v1, v4_intent_context_r2, v5_purpose_aligned | 8 | 5–5 |
| E008 | dev_expansion | 8 | evidence_memory_v1, goal_action_v1, reference_product_v1, v5_purpose_aligned | 8 | 4–4 |
| E009 | dev_expansion | 8 | reference_product_v1, v5_purpose_aligned | 0 | 2–2 |
| E010 | dev_expansion | 8 | reference_product_v1, v4_intent_context_r2 | 0 | 2–2 |
| E012 | dev_expansion | 8 | evidence_memory_v1, goal_action_v1, reference_product_v1, v4_intent_context_r2, v5_purpose_aligned | 8 | 5–5 |
| P001 | positive_capability | 8 | reference_product_v1, v5_purpose_aligned | 0 | 2–2 |
| P002 | positive_capability | 8 | reference_product_v1, v5_purpose_aligned | 0 | 2–2 |
| P003 | positive_capability | 8 | reference_product_v1, v5_purpose_aligned | 0 | 2–2 |
| P004 | positive_capability | 8 | reference_product_v1, v5_purpose_aligned | 0 | 2–2 |
| P005 | positive_capability | 8 | reference_product_v1, v5_purpose_aligned | 0 | 2–2 |
| P006 | positive_capability | 8 | reference_product_v1, v5_purpose_aligned | 0 | 2–2 |

## 共同 panel 的混淆结构（必须随成绩单一并出示）

- panel 位点数：32；每个位点由几个批次拼成：{"3": 24, "4": 8}
- 跨批重复出现过的臂（同臂在不同批次各一份答卷）：reference_product_v1，v5_purpose_aligned

| 同批配对对照 | 两臂 | 位点 | 顺序核验 | 派发双射 | Σ派发(键数)==付费调用 | 配置身份 |
|---|---|---:|---|---|---|---|
| BATCH-C4-A-ORIG-VS-GOAL-20261026 | goal_action_v1 vs reference_product_v1 | 32 | True | True | 64（8 键）==True | stream+usage |
| BATCH-C4-B-V5-VS-MEM-20261026 | evidence_memory_v1 vs v5_purpose_aligned | 32 | True | True | 64（8 键）==True | stream+usage |
| BATCH-NEWSTORY-96-20261026 | reference_product_v1 vs v5_purpose_aligned | 48 | True | True | 96（12 键）==True | stream+usage |
| BATCH-R2-CTX-20261024 | reference_product_v1 vs v4_intent_context_r2 | 24 | True | True | 48（6 键）==True | non_stream_or_unknown |
| BATCH-V3-DELETION-20261024 | reference_product_v1 vs v3_system_slim | 24 | True | True | 48（6 键）==True | non_stream_or_unknown |
| BATCH-V5-CTX-20261025 | reference_product_v1 vs v5_purpose_aligned | 24 | True | True | 48（6 键）==True | non_stream_or_unknown |

## 结论（裁定问题的直接回答）

- 同题多臂表：拼得出来：E006/E007/E008/E012 共 32 个位点、每位点 4–5 臂有已付费答卷；这四个故事上的全部答卷中，出站 prompt 归档 208/208（⇒『实际注入』段都可测），结束时状态快照只有 80/208（⇒『保留/存储保真』段只对留下快照的臂可测，其余记 NR）；『最终用对』与主观意愿段仍需人判。
- 但该表的跨臂比较带批次混淆：32 个位点里有 32 个由 ≥3 批拼成；有跨批重复答卷的臂只有 `reference_product_v1`、`v5_purpose_aligned`，可用它估批间漂移，其余臂的跨批差异无法与时间块分离。因此一级结论优先用**同批配对**列，跨臂合并只作并列描述不作因果排序。

## 可直接当作共同 panel 的故事（全部位点都有 ≥4 臂答卷，且属 E 族）

`E006`（8 位点），`E007`（8 位点），`E008`（8 位点），`E012`（8 位点）

- 四臂齐全位点：32 / 112 个已付费位点（跨全部批次去重）

> 诚实边界一：同一故事在多个批次重复出现的臂，属于**不同时间块/不同配置身份**的答卷，并列报告但不合并求均；跨批比较同一臂要先看配置身份是否一致。
> 诚实边界二：臂在某故事存在 ≠ 该臂在该故事**每道题**都有答卷，见 min–max 列。
> 诚实边界三：P 族（positive_capability）与 E 族（dev_expansion）是不同故事集，不许跨族凑成同一道题的比较。
> 诚实边界四：本表只证明「同一道题上有几份答卷」，不证明任何质量高低。
