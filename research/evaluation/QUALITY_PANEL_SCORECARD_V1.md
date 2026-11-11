# QUALITY_PANEL_SCORECARD_V1 — 第一版逐维质量成绩单（AI 辅助口径）

日期：2026-10-27 02:50。批次：`logs/quality_panel_v1_live_20261027_003723`（AUTH-QUALITY-PANEL-20261027-01，
173/173 派发、0 传输失败、0 停批；账本 used=173/192，**余 19 不是补跑额度**）。
冻结依据：`QUALITY_PLAN.json`（输入散列）+ `QUALITY_MANUAL_V1.md`（九维 0-3 锚）+ 32 开发控制 +
13 固定复评。分析器：`tools/analyze_quality_panel_v1.py`（ ratings 源=RATINGS_REPAIRED.jsonl，见"修复通道"）。

## 0. 判读资格（先看这个，再看分数）

| 项 | 实测 | 判定 |
|---|---|---|
| 开发控制应判错检出 | **15/16（93.8%）**；分组：仅"观点玩笑vs确定亲历" 2/4，其余 7 组全 4/4 | 达到派发前声明阈值（事实检出≥90%）的证据方向；**缺陷族明确：把随意口吻包装的编造亲历当玩笑放过** |
| 开发控制保护样本误杀 | **1/16（6.3%）** | 低于误杀≤15% 阈值 |
| 重测一致（13 计划） | 10 条有 repeat、9 条可比（Q107 首选+复评均不可评）；逐维精确一致均值 **6.56/9**，差≤1 均值 7.22/9 | 中等偏稳；Q062（4/8）最弱，Q127 9/9；**未达手册"≥7 维完全一致"的逐维全信门槛 → 九维分数整体只能作辅助读数** |
| 判词引文逐字核对 | **19 处不符** → 待核队列（人审该判词，不算考生错） | 已登记，`quote_violations` 字段 |
| 控制身份 | worker-AI 亲笔开发控制，非独立封存集、非人类标定 | 九维=**AI 辅助读数**，不得单独决定晋级；愿发 W=unknown |

## 1. 分母与未评（如实）

- 173 attempt：**159 评**（92%）、10 条不可解析（Q024/Q031/Q036/Q057/Q058/Q098/Q104/Q113/Q118/Q071-rep），
  4 条内容违例（Q056 备选 5/6 槽、Q103 backups 非列表、Q083-rep backups 非列表、Q107-rep 备选 5/7 槽）——**全部未评、不补跑**。
- 修复通道：`tools/repair_quality_panel_ratings.py` → `RATINGS_REPAIRED.jsonl`。现场 159 条 schema_fail
  主因=模型把 node_id 回显成 case_id（校验器过严，已改正口径：回显=警告；scores 键值/备选槽覆盖仍硬校验）；
  4 条 JSON 后带尾随文本用 raw_decode 提取首对象（尾随原文保留在 raw_tail 字段）。
- 原始 `RATINGS_*.jsonl` 未改动；账本未动。

## 2. 逐维均分（0-3，首选+非空备选**合并段**口径；n=该臂首选评得上位数）

| 臂 | n | goal | fact | attribution | time_state | boundary | memory_use | interaction | initiative | emotion_relation | claims(总/major) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 原版 reference_product_v1 | 27 | 2.39 | 2.75 | 2.88 | 2.81 | 2.84 | **2.51** | 2.48 | **2.15** | 2.30 | 370 / 4 |
| v5_purpose_aligned | 30 | 2.39 | 2.87 | 2.92 | 2.83 | 2.90 | 2.59 | 2.50 | 2.39 | 2.35 | 469 / 1 |
| goal_action_v1 (C2) | 29 | **2.46** | 2.81 | 2.96 | 2.85 | 2.90 | 2.50 | **2.65** | 2.41 | **2.46** | 439 / 1 |
| evidence_memory_v1 (C3) | 31 | 2.39 | 2.86 | 2.93 | 2.90 | 2.89 | **2.69** | 2.58 | 2.32 | 2.43 | 468 / 4 |

（四臂横比带批次/时段混杂；同批配对见 §3。major=判词标注的关键级主张。）

## 3. 同批严格配对（首选槽，逐案例九维均差，|Δ|≤0.5 记平）

- **原版 vs C2**（sweep_a）：原版 0 胜 / 平 21 / **C2 4 胜**，7 不可比
- **v5 vs C3**（sweep_b）：v5 1 胜 / 平 27 / C3 1 胜，3 不可比

## 4. 当前最大缺陷（本轮可执行结论）

1. **裁判缺陷族（先修再下一批）**：对"随意口吻的编造亲历"检出率 2/4（控制组 CTL06/CTL08 类）——
   正是 C2 在 C4 轮被人工审出的"无据既成断言"族；下一批判前须在手册加"亲历断言需逐条对历史"的硬规则并补控制样。
2. **候选层**：C3 在 memory_use 领先（2.69 vs 原版 2.51）但 major 主张 4 条；C2 interaction/initiative 领先
   且 major 少，但 conflict 主张 3 条（与其已知"无据断言"族一致）；原版 initiative 全场最低（2.15）。
3. **口径缺口**：本轮均分为首选+备选合并段；首选/备选分离表、故事级聚合、19 条引文待核、
   worker 亲读复核（128 首选 + 全部 major + 32 分层样本）= 下一工作块内容。

## 5. unknown（不填空）

- 用户愿发 W：unknown（模型禁填；32 样本表仍未回收）。
- 金额：unknown（无可核对单价）；token 记账在账本（judge 侧逐 attempt usage）。
- 泛化：四个故事全部曝光，本成绩只对开发集成立。
- 九维资格：辅助读数（见 §0）；不得单独决定保留/淘汰。

## 6. 证据索引

- 批输出：`D:\lovebrain-arena\logs\quality_panel_v1_live_20261027_003723\`（RATINGS_*、DISPATCH_PLAN、
  JUDGE_BATCH_SUMMARY、RATINGS_REPAIRED.jsonl、QUALITY_PANEL_ANALYSIS_V1.json、GATEWAY_EVENTS_w*）
- 冻结件：`D:\lovebrain-arena\refs\experiment_protocol_20261023\knowledge_quality_independent_review_20261026\`
  下 QUALITY_PLAN.json / QUALITY_MANUAL_V1.md / QUALITY_CONTROLS_DEV_V1.json / QUALITY_REFERENCE_CARDS_V1.json /
  FACT_CHECKPOINTS_DRAFT_V1.json / PANEL_IDENTITY_V1.json / AUTHORIZATION_QUALITY_PANEL.json
- 账本：`D:\lovebrain-arena\logs\auth_budget\AUTH-QUALITY-PANEL-20261027-01.jsonl`（173/192，未关闭——
  余 19 按授权文书"不是补跑额度"，是否关闭待本阶段收口时按实际登记）
