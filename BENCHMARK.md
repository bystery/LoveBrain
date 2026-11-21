# 基准统计口径

本文是仓库根的公开统计页，数字与口径承接权威口径页 [`research/benchmark/STATISTICS.md`](research/benchmark/STATISTICS.md)
（2026-11-12 定稿），并与 README、`research/DATASET.md` §1.1 权威口径链交叉核对一致。

## 八个公开口径数字

| 值 | 口径（版本标签） | 统计时间 | 来源 | 边界 |
| ---: | --- | --- | --- | --- |
| 208 | 第一代主库库内 Episode（伴随 2,944 库内 Turn） | 2026-11-07 只读复算 | `refs/dataset_review_20261107/A_COMPOSITION_AND_PROVENANCE.md` + `recompute_stats.py` | 仅库内；Gold Sidecar 另含库外早期材料 |
| 221 | Gold Sidecar v1 文档口径 Episode（伴随 3,102 Turn / 6,603 atoms） | 2026-11-03（v1 index 生成日） | `benchmark/stories_v2/GOLD_SCHEMA.md`（schema v1.0） | 文档口径、无对应文件实体；221 = 208 库内 + 11（stage1/stageD）+ 2（wave1_ext） |
| 259 | GitHub 已提交 Gold v2 远端快照 Episode（伴随 3,590 Turn / 7,953 atoms） | 2026-11-08T06:00:43（generated） | `GOLD_SIDECAR_v1_remote_snapshot/GOLD_INDEX.json`（13 文件快照） | 历史快照轨，仅作版本历史与审计对照 |
| 299 | Gold 覆盖 Episode 数（权威轨：GOLD_SIDECAR_v2 本地重建，14 文件） | 2026-11-10T04:25:15（generated）；2026-11-11 审计 + 机器重算 + 2026-11-12 第三重复算三重一致 | `benchmark/stories_v2/GOLD_SIDECAR/`（GOLD_INDEX.totals）+ `DATASET_FINAL_AUDIT_20261111` | 覆盖的是既有 story，不是新增独立故事；全集 317 − 299 = 18 未覆盖（anchors 12 + final 6，设计如此）；无跨文件重复 |
| 4,082 | Gold Turn 数（权威轨同 299） | 同 299（三重一致） | 同 299（GOLD_INDEX.totals；12 个分文件求和 = INDEX totals） | 含库外探针轮；不把消息条数或调用次数当 Turn |
| 9,646 | Gold Atomic Assertions 数（权威轨同 299） | 同 299（三重一致） | 同 299（GOLD_INDEX.totals；quote strict 9,646/9,646） | atoms ≠ 题目数 ≠ 轮数 ≠ marker 数（affirm_markers 非空 686、item_markers 非空 68 单列） |
| 317 | 去重后唯一 Story 数（stories_v2 文件系统口径；目录数口径 319） | 2026-11-11 审计 + 机器重算 + 2026-11-12 第三重复算（三重一致） | `DATASET_FINAL_AUDIT_20261111` + episode-recount + dataset-final-reconciliation | 319 目录 − 2（D01/D02 版本目录重复组）= 317；其中 315 有 episode.json 支撑（2 个 bundle 目录除外） |
| 248 | custody 账本注册 Story 数（审计时点口径） | 账本最后写入 2026-11-10 04:25；当前口径 288（2026-11-12 wave5 补登 40 条后，纯追加） | STORY_CUSTODY_LEDGER | 审计时点 248 = 299 − 8（stage1）− 3（stageD）− 40（wave5）；补登后 288 stories / 379 events |

## 恒等式（对账闭合，逐项复算通过）

```text
317 = 299 + 12（anchors） + 6（final）
248 = 299 − 8（stage1） − 3（stageD） − 40（wave5）        （审计时点）
288 = 248 + 40（wave5 补登，纯追加）                        （当前口径）
319 − 2（D01/D02） = 317
317 − 288 = 29（stage1 8 + stageD 3 + anchors 12 + final 6）
12 个 GOLD 分文件 episodes/turns/assertions 求和 = GOLD_INDEX.totals（逐项全等）
```

## 结果量构成

- 4,082 轮 Gold Turn 中：有断言轮 3,899（95.5%），空 Gold 轮 79，有卡无断言轮 104。
- 9,646 条 Atomic Assertions 全部通过 quote 严格回验（strict 9,646/9,646、lenient 9,646/9,646）。
- unknowns 3,116 = 2,649 字符串 + 467 对象。
- 去重结果：319 目录去重为 317 个唯一 Story，无磁盘外幽灵 ID、无跨文件重复；唯一池 = 299。
- 解封判定（2026-11-12）：317 / 299 / 4,082 / 9,646（及目录口径 319、账本审计时点 248）经审计三件套、
  机器重算、第三重复算、账本侧解析与备份副本直读等多重独立来源复算一致，恒等式闭合。

## 使用纪律

1. 不把文件数当 Episode 数；
2. 不把 Gold 覆盖 Episode 当独立新故事；
3. 不把一篇故事在多个方向的出现次数当独立 Story（各方向去重数单列、不相加，唯一池 = 299）；
4. 不把开发与选题用途的既有批题当新增独立泛化测试集；
5. 不把旧版和新版本副本当成两篇新故事（D01/D02 去重）；
6. 不把断言 marker 当 Atomic Assertion（marker 数单列）。

每个数字带版本标签；不同版本、不同分母、不同统计单位的数字不相加、不互改；历史口径并列登记、不覆盖。
完整性口径为文件数 + 字节数。
