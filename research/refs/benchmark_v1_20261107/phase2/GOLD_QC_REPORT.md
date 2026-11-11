# Benchmark V1 Phase 2 · Gold QC 报告（GOLD_QC_REPORT）

> 生成：2026-11-08（W27-01）。事实来源：`benchmark/stories_v2/GOLD_SIDECAR/GOLD_INDEX.json`
> （schema GOLD_SIDECAR_v2）、`PHASE2_OLD_STORY_AUDIT.csv`、`GOLD_CONFLICT_RESOLUTION.csv`、
> `STORY_BATCH_REPORTS/REPAIR_MARKER_FIX_1.md`、三份 auditor 报告。

## 1. 全库口径（构建时点实测）

| 指标 | 值 |
|---|---|
| Episodes / Turns | 259 / 3,590 |
| 有 Gold 卡轮位 / 空 Gold | 3,407 / 79 |
| 原子断言 | 7,953 |
| quote strict / lenient | 7,953 / 7,953（**100%**） |
| 来源冲突（旧库登记） | 93（全部已裁决：GOLD_CONFLICT_RESOLUTION.csv） |
| must-not-affirm 原子 / 带 affirm_markers | 782 / 636（无 markers 者诚实 unrated） |
| Marker QC | n=1,370，**verbatim_violations=0** |
| QC 抽样 | n=40，strict/lenient 全过（seed=20261101） |
| unknowns 升级对象形态 / 保留字符串诚实 unrated | 189 / 2,649 |

## 2. wave4_dev（Phase 2 新建 38 篇）

- 488 轮 / 488 卡 / **1,350 原子**（quote strict 1,350 全命中、conflict 0）
- 标注流水：B1–B6 六波，全篇 status=annotated_pending_audit → 三审计（C1/C2/C3）→ 两修复
  （R1/R2）+ marker 修复（43→0）→ doc 重构（D1/D2/D3，audit.md 八节 38/38）
- 独立复验：C1 401 原子、C2 530 原子、C3 331 原子 quote 全量逐字复验 **0 失配**；分配表
  生成时对全部新旧原子再次独立重验 **2,610 条 0 失败**
- required_this_turn 口径（V1 定稿：来源必须早于本轮）：存活 51 处（B5/B6 记忆/更正链语义）；
  B1/B2 的"本轮必须处理"事实按设计由 acceptable_actions/forbidden/private_guidance 承载
- 已知诚实边界：conflict 状态层 1 不判分（forbidden+required 纠错原子替代）；E332_T09 反转位
  不机械带旧禁令（防误伤）；层 1 外金额/语义判点归 B3 亲读（各篇 audit.md §6 登记）

## 3. 旧题 45 篇（Phase 2 审核）

- 42 ACCEPT / 1 ACCEPT_WITH_NA（E021，N/A 位=T07）/ 2 REPAIR_OVERLAY（E065 算术卡面反向、
  LRE121_T23 契约残留——独立 overlay 保留旧哈希）/ 0 REJECT
- 93 处来源冲突全部裁决，无一处需改运行时 gold 值
- LRE121 参与 pilot 需加载 overlay（PILOT2_SUBSET note）

## 4. 修复与裁决历史（全留痕）

1. E325 CRITICAL（gold 把算术链方向装反）→ R2 三卡 21 处重锚，四轮方向回正
2. Marker QC 43 违例 → REPAIR_MARKER_FIX_1 全部逐字替换，0 置空
3. unknowns 探测词口径裁决：不要求题面逐字（GOLD_SCHEMA §5 设计），303 处不修
4. required 覆盖面登记：层 1 required-affirmation 对部分新篇贡献为零，事实判分由
   cancel 违例（不依赖 required 标志）+ 语义层承担
5. 评分侧移交 Phase 3（按 §9 版本化纪律，本阶段未改计分器）：C15 否定/疑问护栏、
   conflict 层 1 消费口径
