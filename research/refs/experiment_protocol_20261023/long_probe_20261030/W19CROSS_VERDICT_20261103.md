# W19-03 跨域批判决 — uc_guard（2026-11-03，主会话判决）

> 批次：`BATCH-W19-CROSS-20261103`（w19b 重跑批 464/464，0 error；首批 w19_cross 17:02 仅 parent 臂后停，按事故不续跑）。
> 判据：`W19-03_CROSS_DOMAIN_DESIGN_20261103.md` G1–G5（跑前冻结，原文照录）。
> 评审：`LP_W19CROSS_NINEDIM_REVIEW.json`（AUTH-W19CROSS-REVIEW-20261103-01，callfail 3.9% 不降权）。
> B3 亲读账：`LP_W19CROSS_B3_READRESULT.json`（本会话逐位亲读，引文逐字回验）。
> 时机注记：judge 锚点校准批（W20-03，AUTH-W20-JUDGE-CAL-20261103-01）在本判决期间由前一窗口执行中；
> 本判决按冻结判据既定口径把九维 judge 仅作辅助门（G2），不依赖校准结论，口径不受其影响。

## 判决：**不宣布开发集总冠军，双域冠军保留**

- ≤24 轮域冠军维持 `window_expand_b_norecap_guard`（W17c MERGE）
- \>24 轮域冠军维持 `window_expand_b_norecap_uc`（W16 KEEP）
- `window_expand_b_norecap_uc_guard` 记为**跨域候选未晋升**（两域全过才算总冠军，long40 破门）

## 逐门结论

| 门 | short12（6 篇 72 位） | long40（4 篇 160 位） | 结论 |
|---|---|---|---|
| G1 severe B3 亲读 | cand **2** ≤ par **2** ✓（族=无据确定化/归因错位，均既有族） | cand **11** ≤ par **13** ✓（cand 增量全部为底模输出契约崩坏族，par 同族更多；族既有，无新增故障族） | PASS/PASS |
| G2 九维辅助门 | 全 9 维正，最差 init +0.016（保护维 interact+0.161/emotion+0.032/init+0.016） | 全 9 维正，最差 time +0.127（保护维全过） | PASS/PASS |
| G3 契约 | veto 9 ≤ 12+1 ✓；空回复 0 ✓ | veto 34 ≤ 44+1 ✓；**空回复 5 ≠ 0 ✗**（LRE138 T32/T36/T37/T38/T39 全 0 字节） | PASS/**FAIL** |
| G4 成本 | prompt 828,614 vs 833,253 = **−0.56%** ✓；0 额外调用 ✓（232/232，guard 为确定性后处理）；p50 +7.9% / p95 +2.2%（只报告） | 同左 | PASS |
| G5 swap 稳定性 | — | — | **触发**：故事级方向翻转 4/10（E164/E183/E191：main=cand、swap=parent；LRE117：main 25c/3p、swap 1c/3p）；位级真翻转 4（E191_T08、LRE117_T08、LRE117_T24、LRE121_T20）→ **不得宣布单冠军** |

**破门点：long40 G3（空回复 5≠0，冻结零容忍）+ G5 翻转诊断。按预注册处置：保留双域冠军，不改判据重跑、不换指标到赢。**

## 关键事实（B3 亲读，全部引文逐字回验）

1. **judge 不可靠性再证**：误烧 4 位（E164_T10/LRE121_T33/LRE146_T26/LRE146_T27——三位是"未查前文"型，原文逐字有据：`那单我不下了`/`11月14日`+`封网演练`/`全出`+`钙片`/`撇嘴…等着瞧`）、漏检 7 位（含 cand 5 个 0 字节空回复 LRE138_T32/T36-T39 全部未烧）、无注 severe 8 位（原始回复 PARSE_OK，不可复现）、双重标准模式 3 处。**severe 必须 B3 亲读的纪律再次成立；W20-03 校准必要性再证。**
2. **程序分分层读**：cand 8.11 vs par 7.56 只说明契约与采样噪声（veto 43 vs 56），质量结论以分层分+亲读为准，不进判决主证据。
3. **guard 行为边界（观察项，不改判据）**：guard 在可解析文本上有效复现 W17b 修复效果（12轮域 veto 9 vs 12；整体 43 vs 56）；对**完全不可解析/空原文按设计不触碰**（`unparseable_raw_left_untouched`/`reply_not_committed=1`）→ 空回复与全乱码是 guard 机制盲区，长域晚段（LRE138 T32-T39）集中出现，双臂同族（par 空 6 vs cand 空 5）。**登记为后续机制方向**：输出契约崩坏的前置缓解（如空/残片重试位检出）属新假设，须走新批预注册，不在本批判据内补偿。
4. **uc 触发面**：本批 uc_cu 仅零星在位（如 LRE146_T20 uc_cu=119/uc_items=2），与 W18B 惰性对照一致，未发现 uc 段引发契约崩坏的证据。

## 边界声明（承设计，原样保留）

- 本批为开发集冠军候选判定，非封存验收；未与 reference_product_v1 同批对拍；
- 题材全部来自已暴露开发素材（W17C/W18B 复用），泛化受限；
- swap 样本量小（短域每篇 1 位、长域每篇 4 位），G5 按冻结原文执行，样本量异议不构成改判依据；
- 本判决零模型调用（B3 亲读+机械重算），未占用 6 流，未与 W20-03 校准批并发派发。

## 状态记账

- 判决文档：本文档；亲读账：`LP_W19CROSS_B3_READRESULT.json`。
- RESEARCH_STATE.json / TASK_QUEUE.json（W19-03 → DONE）/ WORKER_CURRENT_RUNBOOK.json 已同步更新。
- 下一动作：W20-03 校准报告落盘后按逐维资格接线 judge 维；从里程碑倒排下一批（单变量候选→判据预注册→precheck G1-G7→live 独占→批收口 append exposure→gold 分层评审→B3→判决）。
