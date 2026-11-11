# STORY_GENERATION_PROTOCOL · 生成流程与角色分离协议（Phase 2，v1 2026-11-08）

依据：指导书 §6（不能让同一个 Worker 一边看失败案例一边造题）。

## 1. 三角色

| 角色 | 职责 | 禁读 | 登记位置 |
|---|---|---|---|
| A Story Author | 故事正文（episode/timeline/exposure 骨架） | logs/、candidates/、判决与评审文档、GOLD_SIDECAR、其他 episode 正文（E117 结构样例除外） | GENERATION_ISOLATION_LEDGER.json (author 项) |
| B Gold Annotator | reference.json 卡、episode.json 的 gold 原子、audit.md | 任何候选在本篇的运行表现/失败案例；未冻结的评分结果 | 同账本 (annotator 项) |
| C Independent Auditor | 独立审核故事+Gold（事实/时间/来源/混淆/自洽/专题/判别力/Gold 偏向） | 同 A+B 的禁读并集之外的皆可读（冻结后材料） | 同账本 (auditor 项) |

同一篇故事的 A/B/C 不得是同一个会话；本协议下三者=三个独立代理会话，账本留痕。

## 2. 批次计划（§6.2）

| 批 | 内容 | 规模 |
|---|---|---|
| 1（本批） | 试制样本：T01–T08 各 4 + T09 新 2(24/32 轮) + T10 孪生 2 组(4 篇) = 38 篇新题 | 波目录 wave4_dev/，ID E301–E338 |
| 2 | 审批修订规范后补齐 DEV 剩余 + SELECTION | 40–70 篇 |
| 3 | 独立生成 HOLDOUT（120 篇，独立会话，SEALED） | wave5_holdout/ |
| 4 | 全库扫描与冻结 | 工具链 |

每批 5–10 篇/代理，完成一批审核一批；T10 必须按孪生组生成与审核。

## 3. 状态机与账本

- exposure.status: authored_pending_review → annotated_pending_audit → audited_accepted(或 audited_rejected) → (入池)
- custody：入池时 seed 注册（story_custody.py），状态按 SPLIT_AND_CUSTODY_PLAN.md（修订版）；
  HOLDOUT 冻结后 = SEALED + UNTOUCHED 语义（未被任何候选消费，experiments_used=[]）。
- 谱系资格按组算（指导书 §8.1 终裁）：lineage_check.py 执行；CF 孪生同组同分区。

## 4. Pilot（§10）

- 样本 = 本批 38 篇新题 + 2 篇已暴露 ACCEPT 级 T09 旧题；
- live 对照组 = DIRECT vs CURRENT（当前域冠军），USTC deepseek-flash-2，temperature 0.7；
- cap = 1500 attempts（指导书），并发 4 流（Phase 2 指导书未限定并发，如实登记）；
- 通过标准按指导书 §10.3；不进行冠军判决；Holdout 不参与任何 Pilot。
