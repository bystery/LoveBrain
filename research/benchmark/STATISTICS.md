# benchmark/ 公开统计口径定稿（STATISTICS）

> 状态：公开口径定稿（2026-11-12，第 4 份指导书批次 C S9 落位）。本文是 `research/benchmark/` 公开统计数字的唯一口径页；数字与 `research/README.md` §2.4 权威口径链、§2.5 Gold 三代口径交叉核对一致（见 §四）。
> 版本标注纪律（第 4 份指导书 §14）：每个数字带版本标签；不同版本、不同分母、不同统计单位的数字不相加、不互改；历史口径并列登记、不覆盖。
> 完整性口径：**文件数 + 字节数**；哈希登记口径停用，本文件零哈希值。
> 解封状态：317 / 299 / 4,082 / 9,646（及目录口径 319、账本审计时点 248）已按 §14 取得多源一致复算（§五）；该判定不延伸为 D1–D7 全部子字段已复算。

## 一、八个公开口径数字（每数字一行：值 / 口径 / 统计时间 / 来源 / 边界）

| 值 | 口径（版本标签） | 统计时间 | 来源 | 边界 |
| ---: | --- | --- | --- | --- |
| 208 | 第一代主库库内 Episode（PAPER 库内口径；伴随口径 2,944 库内 Turn） | 2026-11-07 只读复算 | `refs/dataset_review_20261107/A_COMPOSITION_AND_PROVENANCE.md` + `recompute_stats.py`（波次表 16+8+8+16+48+96+16） | 仅库内；Gold Sidecar 另含库外早期材料；不与 221 / 299 相加；历史口径并列登记 |
| 221 | Gold Sidecar v1 文档口径 Episode（伴随 3,102 Turn / 6,603 atoms） | 2026-11-03（v1 index 生成日） | `benchmark/stories_v2/GOLD_SCHEMA.md`（自述 schema v1.0） | 文档口径、无对应文件实体；221 = 208 库内 + 11（stage1/stageD）+ 2（wave1_ext）；不与 259 / 299 相加 |
| 259 | GitHub 已提交 Gold v2 远端快照 Episode（伴随 3,590 Turn / 7,953 atoms） | 2026-11-08T06:00:43（generated） | `benchmark/stories_v2/GOLD_SIDECAR_v1_remote_snapshot/GOLD_INDEX.json`（13 文件快照） | 历史快照轨，仅作版本历史与审计对照，不作为评分加载输入；不与权威轨 299/4,082/9,646 相加或互改 |
| 299 | Gold 覆盖 Episode 数（权威轨：GOLD_SIDECAR_v2 本地重建，14 文件） | 2026-11-10T04:25:15（generated）；2026-11-11 审计 + 2026-11-11 机器重算 + 2026-11-12 第三重复算三重一致 | `benchmark/stories_v2/GOLD_SIDECAR/`（GOLD_INDEX.totals）+ `DATASET_FINAL_AUDIT_20261111` + episode-recount | 覆盖口径：覆盖的是既有 story，不是新增独立故事；全集 317 − 299 = 18 未覆盖（anchors 12 + final 6，设计如此）；无磁盘外幽灵 ID、无跨文件重复 |
| 4,082 | Gold Turn 数（权威轨同 299） | 同 299（三重一致） | 同 299（GOLD_INDEX.totals；12 个分文件求和 = INDEX totals） | 含库外探针轮；有断言轮 3,899（95.5%）+ 空 Gold 轮 79 + 有卡无断言轮 104；不把消息条数或调用次数当 Turn |
| 9,646 | Gold Atomic Assertions 数（权威轨同 299） | 同 299（三重一致） | 同 299（GOLD_INDEX.totals；quote strict 9,646/9,646、lenient 9,646/9,646） | atoms ≠ 题目数 ≠ 轮数 ≠ marker 数（affirm_markers 非空 686、item_markers 非空 68 单列）；unknowns 3,116 = 2,649 字符串 + 467 对象 |
| 317 | 去重后唯一 Story 数（stories_v2 文件系统口径；目录数口径 319） | 2026-11-11 审计 + 2026-11-11 机器重算 + 2026-11-12 第三重复算（三重一致） | `DATASET_FINAL_AUDIT_20261111` + episode-recount + 迁移工作区统一口径件（dataset-final-reconciliation） | 319 目录 − 2（D01/D02 版本目录重复组）= 317；其中 315 有 episode.json 支撑（2 个 bundle 目录除外）；远端文件数 2,022 不是 Episode 数 |
| 248 | custody 账本注册 Story 数（**审计时点口径**） | 账本最后写入 2026-11-10 04:25（审计时点）；当前口径 288（2026-11-12 wave5 补登 40 条后，纯追加） | STORY_CUSTODY_LEDGER（补登前状态另有备份副本佐证，存迁移工作区 asset-audit/ledger-backup-before-wave5-20261112.json） | 审计时点 248 = 299 − 8（stage1）− 3（stageD）− 40（wave5）；补登后 288 stories / 379 events；两口径并列登记、不相加不互改；补登后未注册差集 29 = stage1 8 + stageD 3 + anchors 12 + final 6（设计如此） |

## 二、恒等式（对账闭合，逐项复算通过）

```text
317 = 299 + 12（anchors） + 6（final）
248 = 299 − 8（stage1） − 3（stageD） − 40（wave5）        （审计时点）
288 = 248 + 40（wave5 补登，纯追加）                        （当前口径）
319 − 2（D01/D02） = 317
317 − 288 = 29（stage1 8 + stageD 3 + anchors 12 + final 6）
12 个 GOLD 分文件 episodes/turns/assertions 求和 = GOLD_INDEX.totals（逐项全等）
```

## 三、使用纪律（第 4 份指导书 §13.2 六条"不能把"，本文全部遵守）

1. 不把文件数当 Episode 数；
2. 不把 Gold 覆盖 Episode 当独立新故事；
3. 不把一篇故事在多个方向的出现次数当独立 Story（各方向去重数单列、不相加，唯一池 = 299）；
4. 不把开发与选题用途的既有批题当新增独立泛化测试集（分区登记见统一口径件，本文不展开）；
5. 不把旧版和新版本副本当成两篇新故事（D01/D02 去重）；
6. 不把断言 marker 当 Atomic Assertion（marker 数单列）。

## 四、与 `research/README.md` 口径链的交叉核对（2026-11-12）

| 核对点 | README 位置 | 结论 |
| --- | --- | --- |
| 319/317/299/248/315 权威口径链 | README §2.4 | 一致（299 行同时载 4,082 turns / 9,646 atoms；315 单列为 episode.json 支撑口径） |
| Gold 三代口径（221 / 259 / 299） | README §2.5 | 一致（本地 v2 重建 14 文件为权威，远端快照 13 文件与 v1 文档口径并列登记） |
| 历史口径并列（208/2,944、221/3,102/6,603、"296"弃用） | README §2.4 | 一致（296 无法可靠复现，弃用） |
| README 待更新项 | §2.4 补登状态写"执行中" | README 落后于事实：wave5 补登已于 2026-11-12 执行完成（248 → 288）；README 收口批次统一更新 |

## 五、§14 解封判定与待解释项

- **判定（第 4 份指导书批次 A 产出，2026-11-12）**：317 / 299 / 4,082 / 9,646（及 319、248 审计时点口径）可写为"已对账复算"。依据：三件套实测存在并读取；六数取得审计三件套、机器重算、第三重复算、账本侧解析与备份副本直读等多重相互独立来源且逐项相等；恒等式闭合；GOLD 侧 12 分文件求和与 INDEX totals 全等。
- **边界**："已对账复算"指上述总量与九步对账结果可复算且多源一致，**不延伸为 D1–D7 全部子字段已复算**——D1/D3/C8 的审计内部出入与 unknowns 百分比分母仍为待解释项（X1–X3、X5），如实保留。
- 本文不登记任何哈希值；逐文件完整性以文件数 + 字节数口径承载（`research/provenance/` 迁移清单）。
