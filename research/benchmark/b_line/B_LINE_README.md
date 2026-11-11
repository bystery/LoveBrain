# B 线故事集（benchmark/b_line/）

本目录是 LoveBrain 研究资产中的 24 篇评测故事集，与 `benchmark/stories_v2/` 各 wave 故事同源同构，作为故事库的固定子集随迁移整体保留。逐篇来源路径与版本登记见 `B_LINE_MANIFEST.json`（现行版本 b_line_v2）。

## 1. 构成（b_line_v2，共 24 篇）

- **16 篇 12 轮标准故事**：E045/E046/E048/E049/E052/E053/E055/E056/E058/E059（wave1，10 篇）+ E069/E070/E071/E073/E074/E075（wave2，6 篇）；
- **4 篇长剧**（各 32 轮）：LRE119/LRE123/LRE139/LRE142（wave3_ext）；
- **4 篇反事实故事**（12 轮）：E150/E152/E153/E155（wave3）；
- 域覆盖（按 manifest）：8 域 ×2 标准篇 + 长剧 3 域 + 反事实篇。

## 2. 文件布局与来源回链

- 每篇一个目录：`episodes/<story_id>/episode.json`，为源文件的逐字节复制；
- 源文件回链：`benchmark/stories_v2/<stage>/<story_id>/episode.json`，stage 与篇目对应关系即上节所列；
- 评分参考（gold/reference sidecar）按迁移布局位于 `benchmark/stories_v2/GOLD_SIDECAR/`，不在本目录。

## 3. Manifest 版本沿革

- `b_line_v1`：2026-11-02 创建的初始版本（24 篇），已被 v2 取代，未随本迁移树保留；
- `b_line_v2`：2026-11-03 起为现行版本；v1 中六篇因早期版本原因未在 v2 沿用，现行构成以 v2 清单为准；
- 逐篇来源路径与登记明细以 `B_LINE_MANIFEST.json` 为准。
