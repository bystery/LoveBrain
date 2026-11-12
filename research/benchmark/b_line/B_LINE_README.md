# B 线故事集（benchmark/b_line/）

## 0. 公开状态标注（2026-11-13）

本目录 24 篇 episode.json（题目）连同 `benchmark/stories_v2/GOLD_SIDECAR/` 内对应波次 Gold 断言，已随公开研究分支整体公开。**自公开时起，本批次不再是未曝光测试集**——它是公开 Benchmark / 历史 B 线（2026-11-02 建档 v1、2026-11-03 起为 v2 的历史评测资产），可作公开对照与复算素材；主文档与引用处不得再把它表述为"当前独立盲测"。若需严格意义的独立泛化终验，须另用从未公开的数据并在公开前完成；**本仓未做该终验，如实登记为"独立泛化终验未完成"**（封存 holdout 集执行至 889/6,240 当晚中止、判决不存在，见 `research/BENCHMARK_EVALUATION.md` §6）。

## 1. 构成（b_line_v2，共 24 篇）

本目录是 LoveBrain 研究资产中的 24 篇评测故事集，与 `benchmark/stories_v2/` 各 wave 故事同源同构，作为故事库的固定子集随迁移整体保留。逐篇来源路径与版本登记见 `B_LINE_MANIFEST.json`（现行版本 b_line_v2，supersedes b_line_v1，逐篇含 source/copy 成对完整性登记）；版本沿革详见 `B_LINE_CHANGELOG.md`。

- **16 篇 12 轮标准故事**：E045/E046/E048/E049/E052/E053/E055/E056/E058/E059（wave1，10 篇）+ E069/E070/E071/E073/E074/E075（wave2，6 篇）；
- **4 篇长剧**（各 32 轮）：LRE119/LRE123/LRE139/LRE142（wave3_ext）；
- **4 篇反事实故事**（12 轮）：E150/E152/E153/E155（wave3）；
- 域覆盖（按 manifest）：8 域 ×2 标准篇 + 长剧 3 域 + 反事实篇。

## 2. 文件布局与来源回链

- 每篇一个目录：`episodes/<story_id>/episode.json`，为源文件的逐字节复制；
- 源文件回链：`benchmark/stories_v2/<stage>/<story_id>/episode.json`，stage 与篇目对应关系即上节所列；逐篇的来源与复制件对应关系以 `B_LINE_MANIFEST.json` 逐篇条目为准；
- 评分参考（gold/reference sidecar）按迁移布局位于 `benchmark/stories_v2/GOLD_SIDECAR/`，不在本目录；B 线各篇断言内嵌于对应波次 GOLD 文件（wave1×10 / wave2×6 / wave3×4 / wave3_ext×4）。

## 3. Manifest 版本沿革

- `b_line_v1`：2026-11-02 创建的初始版本（24 篇），已被 v2 取代（supersedes），manifest 文件未随本目录保留；v1 构成的文字记录见 v1 版 README 历史版本的构成节；
- `b_line_v2`：2026-11-03 起为现行版本；构成与逐篇登记以 `B_LINE_MANIFEST.json` 为准；
- v1→v2 变更的原因、范围与影响详见 `B_LINE_CHANGELOG.md`。
