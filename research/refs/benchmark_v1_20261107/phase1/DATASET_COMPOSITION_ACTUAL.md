# DATASET_COMPOSITION_ACTUAL.md — 数据集实际构成盘点（Benchmark V1 Phase 1 / 工作包二）

- 盘点对象：当代故事库 `benchmark/stories_v2/` 的 **208 集库**（stage2/3/4 + wave1/2/3 + wave3_ext；不含 stage1 8 篇、stageD 3 篇、anchors AK01-12、final K/N、dev E001-E012 等库外材料，库外材料在 §6 单独登记）。
- 盘点日期：2026-11-07。
- 方法：全部数字由 `tools/benchmark_v1/inventory.py` 实测产出（纯标准库、零模型调用、只读；`python -I` 可复跑）。配套机读文件：同目录 `DATASET_INVENTORY.csv`（208 行+表头）与 `TOPIC_COVERAGE_MATRIX.csv`（T01-T10 十行+表头）。本文所有数字与两份 CSV 一致，分母与来源逐项标注。
- 边界纪律：`benchmark/b_line/` 只读取 `B_LINE_MANIFEST.json` 元数据（source_path/sha256/stage）与 `B_LINE_README.md` 纪律文本，**episode 正文零读取**。
- 文体声明：本文是盘点材料，不是宣传稿。primary_topic 为**启发式分类**（用户指导书 §2.3 的十大专题单选），依据与置信度逐篇写入 CSV 的 `annotation_basis`/`annotation_confidence` 列；**本盘点不构成独立语义审查**（全库 221 篇 exposure.json `independent_review=false`，出处：exposure.json 普查，见 `refs/dataset_review_20261107/A_COMPOSITION_AND_PROVENANCE.md` §2.1）。

---

## 1. 库规模总账（脚本实测）

| 波次 | 篇数 | 每篇轮数 | 轮数 | 出处 |
|---|---|---|---|---|
| stage2 | 16 | 12 | 192 | 目录实测 + `STAGE2_MANIFEST.json` |
| stage3 | 8 | 8 | 64 | 目录实测 + `STAGE3_MANIFEST.json` |
| stage4 | 8 | 24 | 192 | 目录实测 + `STAGE4_MANIFEST.json` |
| wave1 | 16 | 12 | 192 | 目录实测 + `wave1/STAGE1_MANIFEST.json` |
| wave2 | 48 | 12 | 576 | 目录实测 + `wave2/STAGE1_MANIFEST.json` |
| wave3 | 96（88 标准 + 8 CF） | 12 | 1152 | 目录实测 + `wave3/STAGE1_MANIFEST.json` + `STAGE1_MANIFEST_CF.json` |
| wave3_ext | 16 长剧（8×40 + 8×32） | 40/32 | 576 | 目录实测 + `MANIFEST_LRE*.json` |
| **合计** | **208** | — | **2944** | 脚本自检 rows=208、turns_sum=2944，与任务基线及 `refs/dataset_review_20261107/recompute_stats.py` §[1] 一致 |

- 长度分类（CSV `length_class`）：standard(12 轮) 176 / short(≤8) 8（stage3）/ deep(24) 8（stage4）/ long(32/40) 16（wave3_ext）。合计 208。
- gold 可用度（CSV `gold_available`，按带断言轮占比 >2/3 full、>0 partial、=0 none；数据源 `GOLD_SIDECAR/GOLD_<wave>.json` per_episode）：**full 183 / partial 25 / none 0**。库 208 篇带断言轮合计 2767（分母 2944 轮；sidecar 全库口径 2919/3102 含 stage1/stageD/wave1_ext，出处 `GOLD_INDEX.json` totals）。
- partial 25 篇名单：E023-E032、E034-E036、E038、E039、E041、E042、E044、E061、E063-E068（即 stage2 的 18 篇 + stage4 除 E062 外 7 篇；背景：全 sidecar 104 轮"有卡无断言"主要在 stage4（80 轮）与 stage1/2/3（24 轮），出处同 A 材料 §1.2）。
- 暴露状态（CSV `exposure_status`，数据源 `STORY_CUSTODY_LEDGER.json` lifecycle，registered=210=库 208+wave1_ext 2）：**EXPOSED 47 / SEALED 161**。
- B 线关联（CSV `b_line_related`，数据源 `B_LINE_MANIFEST.json` 元数据，episode 零读取）：**self 24 / lineage 8 / no 176 / both 0**。B 线 manifest 的 source_sha256 与库内 `episode.json` 实算 sha256 **24/24 全对上**（脚本自检 bline_source_sha_match=24）。
- 逐篇审查现状（CSV `review_status`，数据源：各波语义审查文档与账本审查事件，全部为 **AI 审查代理批次，非独立人工评审**）：W03-03 子集审查 12（stage2）/ 仅机械校验 20（stage2 其余 4 + stage3 8 + stage4 8）/ wave1 审查 16 / wave2 两批 48 / wave3 T1 32 / T2(S1-S4) 24 / T4(S5-S8) 32 / T3 长剧(R1a-R6) 16 / **CF 8 篇无批次语义审查记录**（仅生成自检+机械校验+CF1/CF2 实跑判决）。合计 208。
- gold 来源冲突（CSV `gold_conflict_count`/`source_conflict_flag`，数据源 GOLD sidecar 逐轮 `provenance.card_conflicts`）：全库 93 处，集中在 LRE117(13 轮)/LRE121(40)/LRE146(40) 三篇（跨 wave 汇编修订卡 W19_CROSS 与本 wave 卡的登记冲突），与 `GOLD_INDEX.json` totals.source_conflicts=93 一致。
- 已知勘误：**10 篇 11 条**（台账见 §4 与 CSV `known_errata` 列；出处 `refs/dataset_review_20261107/C_GOLD_AND_SCORING.md` §4.1 与 A 材料 §3.3）。

---

## 2. 十大专题覆盖矩阵（分母=库 208 篇 / 2944 轮）

即 `TOPIC_COVERAGE_MATRIX.csv` 原文（脚本实测；"B线相关"=b_line_related∈{self,lineage,both} 计数）：

| 专题 | 篇数 | 轮数 | 带断言轮 | SEALED | EXPOSED | B线相关 | 已知勘误篇 | 高置信分类 |
|---|---|---|---|---|---|---|---|---|
| T01 日常聊天/自然接话/幽默 | 144 | 1712 | 1676 | 126 | 18 | 19 | 3 | 0 |
| T02 调情/暧昧/反撩/拉扯 | 6 | 64 | 40 | 3 | 3 | 1 | 1 | 1 |
| T03 深聊/价值观/思想交流 | 1 | 12 | 12 | 1 | 0 | 1 | 0 | 0 |
| T04 主动推进/邀约/关系发展 | 6 | 72 | 56 | 2 | 4 | 0 | 0 | 3 |
| T05 情绪理解/关心/安慰 | 6 | 64 | 55 | 4 | 2 | 1 | 0 | 1 |
| T06 用户意图/口吻/改写/多候选 | 4 | 48 | 48 | 4 | 0 | 0 | 0 | 1 |
| T07 事实/人物归属/不确定性 | 2 | 48 | 34 | 0 | 2 | 0 | 1 | 2 |
| T08 记忆/知识库/相关信息提取 | 9 | 108 | 96 | 6 | 3 | 2 | 0 | 1 |
| T09 长程聊天/状态变化/纠错取消 | 22 | 720 | 654 | 7 | 15 | 4 | 4 | 22 |
| T10 反事实/边界/抗干扰/状态污染 | 8 | 96 | 96 | 8 | 0 | 4 | 1 | 8 |
| **合计** | **208** | **2944** | **2767** | **161** | **47** | **32** | **10 篇 11 条** | **39** |

十行列合计=208（脚本自检 topic_episode_sum=208），与库总账闭合。

## 3. 每专题七个数（定义、数值与结论）

七数定义（全部可由两份 CSV 复算）：
1. **候选旧题数** = primary_topic 计数（分母 208）。
2. **可详细复核数** = EXPOSED 且 gold_available≠none（已暴露、可自由重放/亲读，无封存顾虑）。
3. **已知错误数** = known_errata 非空篇数（含登记级与阻断级，明细见 §4）。
4. **有明确可测专项数** = 该专题篇内存在**确定性判分钩子**的篇数（GOLD sidecar 逐轮任一：must-not-affirm 原子带 affirm_markers / 取消事项 item_markers / unknowns 升级带锚点 / intent 契约 keywords_any / may_decline=true 轮；脚本实测口径）。
5. **缺可靠评分依据数** = 确定性钩子为 0 的篇数（gold=none 为 0 篇，不加计）。判维资格背景：W20-03 judge 校准（`long_probe_20261030/W20_03_JUDGE_CAL_REPORT_20261103.md`）full=goal/time/interact/emotion（fact/init 低证据 full），**attribution not-testable、boundary/memory 判停用**——T07/T08/T10 的语义评分因此更依赖确定性钩子或亲读。
6. **仅辅助覆盖数** = 该专题出现在 secondary_topics 而非主分类的篇数（全库口径）。
7. **明显不足结论** = 由前六数与 §5 缺口表导出的判断。

| 专题 | 候选旧题数 | 可详细复核数 | 已知错误数 | 有明确可测专项数 | 缺可靠评分依据数 | 仅辅助覆盖数 | 明显不足结论 |
|---|---|---|---|---|---|---|---|
| T01 | 144（关键词命中仅 8 + 兜底 136） | 18 | 3（E021/E022/E164） | 137 | 7 | 4 | **名义超标、专项空洞**：136/144 是无主题词信号的兜底桶，真正的"幽默/接话"设计篇只有 8 篇（E021/E025/E026/E036/E038/E043/E053/E105）；高置信分类 0 |
| T02 | 6 | 3 | 1（E029 阻断级） | 5 | 1 | 3 | 缺 18 篇（对 24 均配）；可复用仅 3；主线调情/拉扯无长程载体（长剧均在 T09） |
| T03 | 1（E059） | 0 | 0 | 1 | 0 | 10 | **最大结构性缺口**：唯一 1 篇还是 B 线 self（不可复用不可动）；实际可用=0，缺 24 篇 |
| T04 | 6 | 4 | 0 | 6 | 0 | 9 | 缺 18 篇；可复用 4；邀约/跃迁无 24 轮长程载体 |
| T05 | 6 | 2 | 0 | 6 | 0 | 8 | 缺 18 篇；可复用仅 2（E037/E041）；安慰/情绪支持无 deep/long 载体 |
| T06 | 4（库内）+库外 AK12 锚 | 0 | 0 | 4 | 0 | 1 | 库内 4 篇全部 SEALED 不可复用；库内意图/口吻专项只有 1 篇高置信（E124，intent 3/12）；合法不回密集 3 篇靠 may_decline 密度规则判定（中等置信） |
| T07 | 2（E061/E062） | 1（E061；E062 被勘误阻断） | 1（E062 阻断级） | **0** | **2** | 45 | **双重不足**：篇数缺 22；且两篇均无确定性钩子（无 affirm/item markers、无 unknown 升级、无 intent/md——脚本逐轮实测），"无据确定化"判分只能靠 judge/B3 亲读，而 attribution 维 not-testable |
| T08 | 9 | 3 | 0 | 9 | 0 | 20 | 缺 15 篇；judge memory 维**判停用**（W20-03 漏检 0.609），语义评分只能靠 gold 钩子（9/9 有钩子）与 B3；长程记忆提取主要落在 T09 长剧（辅助覆盖） |
| T09 | 22（16 长剧 + stage4 E063-E068） | 15 | 4（LRE117 阻断级；E065/LRE125/LRE128 登记级，其中后两者为账目陷阱设计/重复登记） | 19 | 3 | 87 | 数量接近 24 均配（缺 2），但 3 篇无确定性钩子（更正链语义靠 judge）；可复用 14；出窗/逐字保持专项判决记录在 LD1/W19CROSS（A/C 材料 §7 样本 16/17） |
| T10 | 8（全部 CF 孪生） | 0（全 SEALED，正确地未烧） | 1（E149 登记级） | 8 | 0 | 35 | 缺 16 篇；CF1/CF2 follow-rate 专项判决存在但样本极小（8 篇、判决书自认置信宽）；"边界"子能力的 judge 维停用——标准篇中 35 篇边界信号只能作辅助覆盖 |

## 4. 已知勘误台账（10 篇 11 条，写入脚本 ERRATA 常量）

出处：`refs/dataset_review_20261107/C_GOLD_AND_SCORING.md` §4.1 素材勘误表 + `refs/dataset_review_20261107/A_COMPOSITION_AND_PROVENANCE.md` §3.3。

| 篇 | 位置 | 内容 | 处置 | 级别 |
|---|---|---|---|---|
| E021 | T07 | 时钟(周三11-11)与卡面"今晚有球"设计矛盾 | W16-05 v1 修时钟；FS1 E3 severe 记素材缺陷位剔除 | 登记 |
| E021 | T11/T12 | "下季度忙完"与"五月起"季候衔接略松 | 登记保留不阻塞 | 登记 |
| E022 | T12 | 收尾轮"练习册"轻量背景补注 | 登记保留不阻塞 | 登记 |
| E029 | T07 | 12 月时钟下问"九月假期"时间视野跳变 | **后备池待修，尚未修** | **阻断** |
| E062 | F06 | 快门数挂镜头=品类错误 | T24 探针作废；stories v2 下一版修复（当前库仍带） | **阻断** |
| E065 | T21 | "到账15200"与 3600×3+3800=14600 自相矛盾 | 已裁决算术挑战不计胜负 | 登记 |
| E149 | T05 | CF 保留源篇"明天/后天"措辞与 +1 天不一致 | 登记，不影响 CF1 判决 | 登记 |
| E164 | T05 | 账目陷阱（HER 报"省60/三十七块七"除数不符） | 有意设计改造（LRE117 先例），非缺陷 | 登记（设计） |
| LRE117 | T04/T05 | 部分原子 asserted 应为 stale（GT 瑕疵） | 维护轨待修（影响 gold 判分口径） | **阻断** |
| LRE125 | A07 | 原子重复登记（LD1 批发现） | 维护轨 | 登记 |
| LRE128 | T35 | 账目陷阱（"省60"实为超 60） | 有意设计改造（同 E164），非缺陷 | 登记（设计） |

## 5. 与 240 规划的缺口表

**诚实声明**：240 集规划出自用户外部指导书，本仓库内没有任何文件记载 240 的专题配比（全仓检索无"240 集/240篇"规划文件）。下表按"十大专题均配 24 篇"的读法计算缺口（24×10=240），**若指导书另有配比，以指导书为准**；可从仓库直接核实的是总量缺口 240−208=32。

| 专题 | 现库候选数（实测） | 规划数（均配 24 假设） | 缺口 | 缺口性质（结合 §3 七数） |
|---|---|---|---|---|
| T01 | 144（专项仅 8） | 24 | −120（名义盈余）/ +16（按专项 8 计） | 兜底桶虚胖；若按严格专项口径实际缺 16 |
| T02 | 6 | 24 | +18 | 数量+可复用双缺 |
| T03 | 1 | 24 | +23 | 最严重；唯一篇目为 B 线 self，可用 0 |
| T04 | 6 | 24 | +18 | 数量+可复用双缺 |
| T05 | 6 | 24 | +18 | 数量+可复用双缺 |
| T06 | 4（+AK12 库外） | 24 | +8（计 AK）/ +20（不计） | 库内 4 篇全部不可复用 |
| T07 | 2 | 24 | +22 | 数量+评分依据双缺（确定性钩子 0/2） |
| T08 | 9 | 24 | +15 | 数量缺 + judge memory 维停用 |
| T09 | 22 | 24 | +2 | 数量接近；质量缺口在 3 篇无确定性钩子 |
| T10 | 8 | 24 | +16 | CF 专项质量高但样本小；边界子维停用 |
| **合计** | **208** | **240** | **+32（总量）** | 总量缺口 32 集中在 T02-T08；T01 虚胖掩盖结构性短缺 |

## 6. 库外材料登记（不计入 208 分母，仅供对照）

- **anchors AK01-12**（`anchors_20261028/`，12 篇×1 轮，may_decline 3 轮、intent 全 ACTIVE）：T06 的库外辅助探针。manifest 自报 `independence="同作者公开开发集，非独立封存"`、卡面"同故事 reference.json 逐字派生"（`GRADING_CARDS_AK01_AK12.PROVENANCE.json`）；不在 gold sidecar、不在保管账、不属 208。judge 校准批 W20-03 以其为素材（好臂 12×坏臂 36）。
- stage1（8×8=64 轮）、stageD（3 篇 22 轮）、wave1_ext（LRE048/LRE060，2 篇 72 轮）：在 gold sidecar（合计凑成 221 篇/3102 轮）与/或保管账内，但不在库 208；wave1_ext 的谱系风险见 LINEAGE_LEAKAGE_REPORT.md。
- dev_pilot E001-E003（24 位）+ dev_expansion E004-E012（9 篇）、final_20261029 K/N（72 轮）、怪物题 core/full_set：更早时代材料，与 208 库不同 ID 空间，本盘点不混入。

## 7. 本盘点不能证明之事

1. **primary_topic 是启发式分类**：高置信 39（阶段规则 32 = CF 8 + 长剧 16 + stage4 8；关键词命中达高阈值 6 = T02×1/T04×3/T05×1/T08×1；intent 密集规则 1 = E124）、中置信 17、**低置信 152**（其中 136 篇为兜底 T01）。它反映 manifest 词面信号，不等于独立语义审查结论；逐篇依据在 CSV `annotation_basis`，可复核可推翻。
2. 篇级 themes/domain 是自由词（300+ 种），非受控枚举；"某题针对某故障族"无题目侧字段支撑（A 材料 §2.3 同口径）。
3. gold 断言为 AI 生成（非人工真值）；104 轮有卡无断言与 79 轮 empty_gold（全 sidecar 口径）的程序覆盖有限（C 材料 §2.4/§7）。
4. T01 的 136 篇兜底归类对"日常聊天"专题的正覆盖是**低承诺**的：这些篇多数实为"事务型接话"，把它们计为 T01 只说明"没有更强的专题信号"，不说明它们能测幽默/自然度专项。
5. 240 规划的专题配比无法从仓库核实（§5 声明）。

---

*生成：tools/benchmark_v1/inventory.py（2026-11-07 实测运行）；配套 CSV：DATASET_INVENTORY.csv / TOPIC_COVERAGE_MATRIX.csv；勘误与审查出处见 §4 与文中标注。*
