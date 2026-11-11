# PHASE2_CLOSEOUT_CORRECTIONS · Phase 2 收口修正登记（Phase 3 第 1 步交付，2026-11-09）

> 依据：Phase 3 指导书 §P3-0（先修复 Phase 2 遗留问题）。本文件登记全部已执行的修正、
> 证据与执行状态；P0-2/P0-4 属评分与运行器修复，在第 4 步执行（本文件只登记缺陷确认与计划）。

## A. 已执行修正（本步完成）

### A1. P0-1 分区污染修正 ✅
- **缺陷**：E335/E336（LG-T10-G2 孪生）在 phase2 v2 分区表列 SELECTION，但两篇已于 2026-11-08
  参加 DIRECT/CURRENT 真实 pilot → 全部 38 篇 wave4 均有开发实验曝光史。
- **修正**：E335/E336 → DEV（`phase3/DATASET_V1_ALLOCATION_V3.csv`）；禁止任何已曝光篇改判
  未见 SELECTION（新 SELECTION 40 篇全部新建，E339–E378）；v2 表原地保留作历史版本。
- **证据**：`CUSTODY_EXPOSURE_RECONCILIATION.md` §1/§4；guard 0 违例。

### A2. P0-3 暴露/保管账本对账 ✅
- **缺陷**：38 篇 wave4 不在 STORY_CUSTODY_LEDGER（210 条中 0 条）；exposure.json
  `dev_exposed=false / experiments_used=[]` 与真实运行（1,163 attempts）不符；status 停在
  `annotated_pending_audit`。
- **修正**（`phase3/reconcile_step1.py`，幂等）：
  - 38 篇 exposure.json：`dev_exposed=true` + `experiments_used` 逐篇回填真实窗口/attempts
    （源=gateway_events `attempt_started` 实数）+ status→`audited_accepted`（审计出处随行登记）；
  - 账本 seed 38 条（lifecycle=EXPOSED，append-only，修改前自动备份）+ 40 条 events；
  - E063/LRE121 补 `pilot2_oldT09_live_20261108` 消费事件。
- **对账结果**：1,033（wave4）+ 130（oldT09）= **1,163 attempts**，与 PHASE2_PILOT_REPORT
  权威口径 1,163/1,500 精确一致。

### A3. P3-0.2 正式主集重新分配 ✅
- E125（LIN-E125 与 LRE125 同前缀谱系重复）/ E042（旧 T02 弱专项，重分类 T09 后不作 T09
  主代表）/ E065（历史算术 Gold 修订）→ `status=DIAGNOSTIC_POOL`（supplementary diagnostic，
  不计入 240 主集；原文件与旧审核记录零改动）。
- 主集 = 旧 42 + wave4 38 = **DEV 80**；SELECTION 40 全部新建（E339–E378）；HOLDOUT 120 全部
  新建（E401–E520）；240 = 80+40+120 闭合（python 实算逐格通过）。

### A4. P3-0.3/P3-0.4 配额表更新 ✅
- v2 的 157 PENDING 作废（原地保留）；新 PENDING=160，逐专题（指导书固定）：
  SELECTION：T01 1 / T02 7 / T03 4 / T04 3 / T05 6 / T06 6 / T07 5 / T08 3 / T09 1(E374, 24轮) / T10 4(G3 goal×2 + G4 time×2)；
  HOLDOUT：T01 5 / T02 25 / T03 20 / T04 12 / T05 12 / T06 12 / T07 10 / T08 8 / T09 8(E505–E509 24轮 + E510–E512 32轮) / T10 8(G5 time / G6 subject / G7 negation-status / G8 goal)。
- 8 组反事实孪生：G1/G2=DEV（已建）、G3/G4=SELECTION（待建，同组同分区）、G5–G8=HOLDOUT（待建）。
- 强制检查：`custody_split_guard --expected-pending 160` = **0 违例 / 0 警告**（twin_groups=8）；
  `lineage_check`（V2 split csv）= 0 违例 / 0 警告。

## B. 缺陷确认与执行计划（后续步骤）

| 缺陷 | 确认证据 | 执行步骤 |
|---|---|---|
| P0-2 评分口径不公平：DIRECT/COT 的 `recommended` 顶层键不在 lb_scorer PROTOCOL_TOP_KEYS 白名单 → veto 544/552 全量触发，score10 0.14 vs 9.96 是结构假象 | arena/lb_scorer.py:173；PHASE2_PILOT_REPORT §2 警告 | 第 4 步（通用评分层/产品契约层分离，版本化+旧答卷离线重评） |
| P0-4 LEGACY_COMPAT_R9 未接线：four_arm_runner LEGACY 直指原版 action_policy_r9_slim；`--episodes-dir` wave4 前缀绕过政策门 | four_arm_runner.py:60/:177/:441；LEGACY_COMPAT_REPORT"尚未接线" | 第 4 步（LEGACY_COMPAT_R9 身份接线 + phase3_runner + 全局预算门 + 政策门按 split manifest） |
| Judge 资格标签失真：time(0.714)/emotion(0.743)/initiative(0.714)/factuality(0.743) 顺序一致率 <0.80 却标 full-qualified | JUDGE_CALIBRATION_REPORT §1 表 | 第 5 步（资格复核重标：仅 goal 0.829 / interact 0.857 保 full；其余降辅助/未校准） |
| 40 锚 A01–A04 缺旧 T02 真实输出（旧 T02 三篇弱且不在 pilot2） | HUMAN_REVIEW_QUEUE.json | 第 5 步（按指导书 P3-3.4 用 E305–E308 非重复轮位补齐，保留 40 锚总数、T02 12 锚） |
| C14/C15 否定/疑问/假设误伤（"你爸妈知道吗"不能坐实知情） | PHASE2_ACCEPTANCE 验收发现 1（unknowns 侧 303 处非逐字）；MAJOR-8 移交 | 第 5 步（结构化语义判据 + ≥100 正负控制样本；不可靠判分降级为辅助诊断） |
| 跨作者人名重名 12 组 | C1/C2 审计清单；OLD_STORY_FINAL_DISPOSITION §6-4 | 第 2 步简报内置唯一人名池 + validate 加跨篇重名检查（HOLDOUT 批次前） |

## C. 明确不做的事

- 不改 episode.json / reference.json / timeline.json / audit.md（考题本体零接触）；
- 不撤销、不改写任何旧审核判决（E065/LRE121 overlay 原样保留，判分需加载 overlay）；
- 不把 DIAGNOSTIC_POOL 3 篇计入 240；
- 不把"账本已补登记"说成"运行前已登记"——补登时间 2026-11-09，实验发生 2026-11-08，note 字段如实。
