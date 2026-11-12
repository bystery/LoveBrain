# LoveBrain 研究总览（RESEARCH_OVERVIEW）

> **本文定位：** 研究总览——研究问题、动机、整体方法与主要贡献的一页入口。与 [README.md](README.md)（资产导览与研究环境）、[architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md)（真实生产链路与研究架构）、[PAPER.md](PAPER.md)（论文稿，成绩表述须与判决文件互查）、[benchmark/STATISTICS.md](benchmark/STATISTICS.md)（公开统计口径页）互链；细节以各专文为准。
> **状态：** 2026-11-12 编制。面向外部读者：本文全部引用只需本仓（`research/` 相对路径）即可核验。
> **纪律：** 零真实密钥、零真实聊天内容、零个人数据；完整性口径 = 文件数 + 字节量（见 [provenance/](provenance/) 迁移清单）；本文不含任何文件哈希值。全部数字带版本标签；限定语一律整句呈现、不压缩不弱化。

---

## 1. 项目定位与研究问题

### 1.1 定位

LoveBrain 的项目定位是**偏大模型算法研究，产品 AI 应用开发为次要展示**——研究问题、数据、实验和机制是主要工作（项目章程定位决策原文口径）。Android 产品（[app/](../app/)）是机制的落地载体与真实验证环境；研究资产公开面整体落位于本目录 `research/`。

### 1.2 研究问题与动机

LoveBrain 是一个长对话聊天辅助产品，其核心研究问题不是"如何写更好的 Prompt"，而是**上下文与记忆的确定性管理**。动机来自三类真实失败模式：

1. **无关历史干扰。** 聊天辅助产品不是记忆越多越好：全量注入会让已结束话题重现、旧目标在用户改变想法后继续生效、旧事项挤占预算。核心原则：**存储里存在，不代表当前轮应该使用。**
2. **关键证据滑出窗口。** 长对话中丢失的不是大概主题而是具体事实：早期金额、日期、约定、决定主体、方案是否取消、较早的重要原话。滑动窗口把早期消息移出上下文，摘要链可能丢事件动作、主体、原话和细节——问题的实质是**关键原始证据已不在当前可见上下文里**。
3. **事实升级与输出漂移。** 未确认的信息必须继续保持未确认，不能被模型补全成已发生事实；同时模型输出结构会漂移（键名、槽位、方向字段），需要确定性的修复边界。

### 1.3 整体方法与主要贡献

**方法：** 把机制候选放入独立研究考场（[arena/](arena/)），在固定模型服务下进行连续对话对照与消融，以判决制收口（KEEP / MERGE / KEEP_NO_PROMOTE / ELIMINATE / 未判定如实登记），只有获得判决支持的机制才向 Android 产品移植，移植后以受控档位接入（当前 SHADOW，默认 OFF）。评测读数一律可追溯到落盘判决文件。

**主要贡献：**

1. **三条机制主线**（上下文装配与相关性门控 / 证据优先的历史记忆 / 事实与输出约束），各带判决支持的实验证据与产品移植状态（§3、§5）；
2. **可核验的题库与 Gold 双轨评测体系**：统计口径机器重算、多源一致、恒等式闭合（§4）；
3. **判决书级实验证据链**：53 份判决文件全量登记入索引，负结果与错误版本不删（§5、[refs/KNOWN_FAILURES.md](refs/KNOWN_FAILURES.md)）；
4. **原样保留的诚实边界**：复现范围、未验证项与不公开材料逐项登记（§7）。

> **证据：** [PAPER.md](PAPER.md)（研究论文稿）；[README.md](README.md)（资产总览）；[architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) §1–§3（三仓关系与研究架构）。

---

## 2. 三仓库体系

LoveBrain 项目由三个仓库构成，角色不互相混淆。**产品仓 `bystery/LoveBrain`**（唯一公开仓库）：Android 聊天辅助产品（Kotlin/Compose），`closeout-20261106` 分支承载机制移植与收口，`research/` 目录承载研究资产公开面；**研究仓 `bystery/arena`**（私有）：研究考场——`arena/` 考场运行时框架 + `isolation/` 隔离包、`tools/` 评测工具、`candidates/` 参赛候选与分域冠军、`runs/` 实验账本、判决书与实验协议，其公开面已按迁移清单整体平移至本仓 `research/` 十二区；**治理仓 `bystery/TeamWorkspace`**（私有，全程只读）：产品规格、工程规则与任务台账，对产品仓的合并与发布施加治理约束（机制上线须真机验收与明确授权）。三者之间的信息流是单向收敛的：产品真实故障族进入研究考场形成实验与判决，只有获得 KEEP/MERGE 判决的机制才向产品移植，移植后以受控档位接入产品，治理仓负责守住合并门槛。

> **证据：** [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) §1（三仓关系图与逐节点实测核对表 §5.3）；迁移映射见 [provenance/SOURCE_MAP.md](provenance/SOURCE_MAP.md) 与 [provenance/MIGRATION_MANIFEST.csv](provenance/MIGRATION_MANIFEST.csv)。

---

## 3. 核心机制：三条主线

### 3.1 概括（公开叙事定稿，逐字）

> LoveBrain 的 Harness 围绕上下文装配、证据记忆和生成约束三条主线设计。系统首先根据当前意图、场景、话题和持续事项筛选相关信息，再通过完整历史事件和来源身份保留关键证据，并使用未确认事项约束与确定性输出守卫减少事实升级和结构错误。我们将这些机制放入独立 Arena，在固定模型服务下进行连续对话对照和消融，再将有价值的机制移植回 Android 产品。

这段话能直接回答"LoveBrain 不就是套 Prompt 吗"：

**不是。Prompt 只是输入的一部分；LoveBrain 还决定记忆如何形成、信息何时注入、旧事实何时失效、当前话题如何切换，以及模型输出如何被确定性处理。**

### 3.2 主线一：上下文装配与相关性门控

上下文机制不是"检索更多记忆"，而是**先判断哪些状态仍然有效、哪些信息与当前轮相关，再按优先级组织上下文并控制预算**。产品侧机制族：持续事项选择（四态生命周期 + 相关性信号 + 冷却）、话题切换门（`topic_status=new` 不等于话题已切换）、纠正事实过滤（被纠正的旧事实不再进入上下文）、场景注入、意图装配、Prompt 预算（代码位置见 [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md)）。新研究线候选 `rh_converge` 把这套确定性装配重组为完整 Harness：三类相关性信号 + 3 轮冷却 + DORMANT 5 轮、7 类场景固定优先级、未确认块三信号、9000 字符三步裁剪、知识段装配、said/invalidated 账本、Top-3 段检索、示例锚定、每轮一次模型调用。

实验读数（【长程对照环判决链】口径，[refs/research_harness_20261110/overnight/](refs/research_harness_20261110/overnight/)）：五维质量均值（8 关键位）**4.67 vs DIRECT 4.69（−0.3%）**——注记：DIRECT 4.69 为 60 位审计总均口径（12 位固定抽样上 DIRECT 为 4.65），两口径并列、不可混用；**Token/轮 1,090 vs 1,420（约 −23%）**；**端到端 P50 42 秒 vs 约 20 秒**——**等待时间更长，须如实并列：更多上下文处理不自动等于更快**；Q2 逻辑连贯首次突破 4.0（两处 Q2=5，DIRECT 无同口径 8 关键位 Q2 读数）。判决 KEEP；**定性为效率型候选（质量在预定可接受差异内相当而资源明显降低）——不得写成"全面击败 DIRECT 的质量冠军"**。

### 3.3 主线二：证据优先的历史记忆

机制三件套：① 有序真实消息事件流（稳定 UID、保留角色/原文/时间；**模型生成的回复只是草稿不进入真实事件流，只有用户确认的真实消息进入后续历史**——"模型说过一句话，不代表用户真的发送过这句话"）；② **3600 CU 完整历史窗口**（预算按 UTF-16 code unit 计量、从最新向前保留、以完整消息为单位、不切半句）；③ 删除无贡献 recap。

关键对照读数（判决原文件口径，[refs/experiment_protocol_20261023/long_probe_20261030/](refs/experiment_protocol_20261023/long_probe_20261030/)）：

- **FLJUDGE**【判决 2026-11-02】：T25-32 保持率 **1.0 vs 滑窗父本 0.493/0.509**；Prompt 482,846 → 280,628（−41.9%）。**限定语（整句）：0.493（LRE060）与 0.509（LRE048）是同一父本臂（r9_slim）在两篇故事上的读数，不存在独立"对照"臂。**
- **LD1**【判决 2026-11-02】：四篇新领域（出行拼车 / 工作职场 / 餐饮聚会 / 医疗健康）T25-32 保持率**全部 1.0（0 miss）**；父本臂四篇读数 .323/.512/.624/.568；Prompt 991,960 → 555,786（−44.0%）；终态真实保持 425/430 = 0.988。**限定语（整句）：长程保持效果跨四域复现。**
- **W15N01**【消融判决 2026-11-02】：删除 recap 后两臂保持率 1.000/1.000；九维 9/9 全正；Prompt −0.3%。**判决自我限定（整句）：简化主张是『无损可删』，不是『recap 有害』——不得扩张成"所有摘要都没有价值"。** recap 删除依据（判决原口径，整句）：**「LD1+本批共 6 篇长剧实测 recap 仅在 T34+ 出线且 ≤199 CU（T25-32 判决段全程为空）」。**

### 3.4 主线三：事实与输出约束

两条互补约束，分别作用在输入侧与输出侧：

1. **未确认事项约束（uc，输入侧）。** 纯函数检测三类信号（转述/推测、未确认问句、引号转述），命中才插入短约束块：最多 3 条、整段 ≤120 CU、原始逐字引用不改写不概括、无未确认事项时整段完全不出现（与父本 Prompt 逐字节相同，可用 [tools/check_prompt_equivalence.py](tools/check_prompt_equivalence.py) 验证）、不调用额外模型。**实现口径限定语（整句）："只保留仍未确认且与当前轮有关的事项"在 Arena 机制中的实现为预算截断保最新（≤3 条 / ≤120 CU / 超限只留最新），无显式相关性评分。**
2. **确定性输出契约守卫（guard，输出侧）。** 模型返回文本后做确定性处理：去围栏/弯引号规范化/已知键别名映射/未知风格键按字符相似度映射（仅目标槽为空时）/缺槽补 null 不虚构/空回复只记账/无法安全修复保留原始输出。**工程原则（整句）：确定性代码只修复结构，不替模型编造回复内容。** 未知风格键模糊映射默认关闭（FUZZY_MAP_ENABLED=false，未与 Python 夹具对拍前不得启用）。

### 3.5 三实现状态与状态红线

每个机制区分三种状态：**研究原型**（[arena/](arena/) 与 [candidates/](candidates/) 候选）、**Android 移植件**（[app/](../app/) champion 六文件实存并注册）、**生产主链路状态**。状态红线：SHADOW 是影子链，**SHADOW≠主链路启用**；分域冠军是分域的，不是全域的；LIVE_AUTO 分域接线（≤24 轮 guard / ≥25 轮 uc）已落码、**生产启用待收口验收，默认档保持 OFF——不把计划写成已启用**。

> **证据：** [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) §3/§4（研究架构与接线状态）；[refs/experiment_protocol_20261023/long_probe_20261030/](refs/experiment_protocol_20261023/long_probe_20261030/)（W16/W17B/W17C/W19/FLJUDGE/LD1/W15N01 判决原文件）；[refs/VERDICT-INDEX.md](refs/VERDICT-INDEX.md)（53 份判决→机制主线映射）；[refs/research_harness_20261110/overnight/](refs/research_harness_20261110/overnight/)（新研究线判决链）；[tools/benchmark_v1/prototype_candidates/rh_converge/](tools/benchmark_v1/prototype_candidates/rh_converge/)。

---

## 4. 数据集与评测体系

### 4.1 统计口径链（机器重算，多源一致）

题库为合成对话故事库（`benchmark/stories_v2/`，315/315 标记 `real_user_material=false`）。公开统计数字的唯一口径页为 [benchmark/STATISTICS.md](benchmark/STATISTICS.md)；数字与 [README.md](README.md) §2.4 权威口径链、§2.5 Gold 三代口径交叉核对一致。核心口径链（每数带版本标签）：

| 数字 | 口径（版本标签） |
| ---: | --- |
| **319** | stories_v2 全部 story 目录（目录数口径；2026-11-11 机器重算 + 2026-11-12 第三重复算一致） |
| **317** | 按 ID 去重后唯一 Story（文件系统口径；319 − 2 个版本目录重复组；其中 315 有 episode.json 支撑） |
| **299** | Gold 覆盖 Episode（权威轨：GOLD_SIDECAR_v2 本地重建，2026-11-10；覆盖的是既有 story，不是新增独立故事） |
| **4,082** | Gold Turn 数（权威轨同 299；三重一致） |
| **9,646** | Gold Atomic Assertions 数（权威轨同 299；quote strict 9,646/9,646） |
| **248** | custody 账本注册 Story 数（审计时点口径；**当前口径 288** = 2026-11-12 补登 40 条后，纯追加；两口径并列登记、不相加不互改） |

历史口径并列登记、不覆盖：**208**（论文稿库内口径，伴随 2,944 库内 Turn）；**221**（Gold Sidecar v1 文档口径，伴随 3,102/6,603）；**259**（GitHub 已提交远端快照，伴随 3,590/7,953）；更早的"296"口径无法可靠复现，弃用如实登记。

### 4.2 恒等式（对账闭合，逐项复算通过）

```text
317 = 299 + 12（anchors） + 6（final）
248 = 299 − 8（stage1） − 3（stageD） − 40（wave5）        （审计时点）
288 = 248 + 40（wave5 补登，纯追加）                        （当前口径）
319 − 2（D01/D02） = 317
317 − 288 = 29（stage1 8 + stageD 3 + anchors 12 + final 6）
12 个 GOLD 分文件 episodes/turns/assertions 求和 = GOLD_INDEX.totals（逐项全等）
```

使用纪律（六条"不能把"）：不把文件数当 Episode 数；不把 Gold 覆盖 Episode 当独立新故事；不把一篇故事在多个方向的出现次数当独立 Story；不把开发与选题用途的既有批题当新增独立泛化测试集；不把旧版和新版本副本当成两篇新故事；不把断言 marker 当 Atomic Assertion。

### 4.3 Gold 三代口径（并列登记，不混写、不静默覆盖）

| 代际 | 生成时间 | totals（episodes/turns/atoms） | 落位与角色 |
| --- | --- | --- | --- |
| 文档口径（schema v1.0 自述） | 2026-11-03 | 221 / 3,102 / 6,603 | [benchmark/stories_v2/GOLD_SCHEMA.md](benchmark/stories_v2/GOLD_SCHEMA.md) 原样保留（历史文档） |
| 远端已提交快照 | 2026-11-08 | 259 / 3,590 / 7,953 | [benchmark/stories_v2/GOLD_SIDECAR_v1_remote_snapshot/](benchmark/stories_v2/GOLD_SIDECAR_v1_remote_snapshot/)（仅版本历史与审计对照，不作评分加载输入） |
| **本地 v2 重建（权威）** | 2026-11-10 | **299 / 4,082 / 9,646** | [benchmark/stories_v2/GOLD_SIDECAR/](benchmark/stories_v2/GOLD_SIDECAR/)（14 文件；正式评分加载输入）；schema 说明见 [GOLD_SCHEMA_V2.md](benchmark/stories_v2/GOLD_SCHEMA_V2.md) |

### 4.4 评测协议

- **评分协议：** [benchmark/rubric.md](benchmark/rubric.md)（现行版）+ [rubric_v0_6_draft.md](benchmark/rubric_v0_6_draft.md)（历史草案留档）；批次与 rubric 版本的逐批对照表待登记，登记前不声称"全部历史 run 的 rubric 版本已知"。
- **计分器：** [arena/lb_scorer.py](arena/lb_scorer.py) v0.7 为唯一计分器（C0–C15 检查点 + veto 族）；前置代际 [arena/scorer.py](arena/scorer.py)（v3）并存保留、不互相冒充。历史 scoring 文件重算必须与产出该文件的评分器版本对齐。
- **Judge 器械：** 校准器（20 道人评锚点 + trap 负样本，≥80% 人评一致率上岗）、位置偏差控制（5 候选随机重排）、温度×位置 2×2 不稳定归因实验（[arena/lb_judge_calib.py](arena/lb_judge_calib.py) 等）。**机器判定 fuzzy 命中未经人工复核不得进对外结论。**
- **评审判决版本链：** [evaluation/](evaluation/)（FINAL / SUPERSEDED / INTERIM / BUGGY / PREVIEW / MOCK / DEBUG 全保留，错误版不删）。

> **证据：** [benchmark/STATISTICS.md](benchmark/STATISTICS.md)（八数字口径页与 §14 解封判定）；[benchmark/stories_v2/GOLD_SCHEMA_V2.md](benchmark/stories_v2/GOLD_SCHEMA_V2.md)；[README.md](README.md) §2.4–§2.7（口径链、Gold 双轨、rubric/scorer 版本）。

---

## 5. 实验结果概述

### 5.1 分域冠军判决（分域不合并）

长对话证据记忆与约束机制的最终判决是**分域冠军**，不合并、不宣布单一全域冠军：

- **长域冠军（>24 轮）= `window_expand_b_norecap_uc`**【W16 判决 2026-11-02，KEEP】：severe **亲读 1 vs 6**（judge 原始 1 vs 10，以亲读账为准）；九维 9/9 全正；Prompt +0.78%（≤5%）；额外模型调用 0；触发 35/120 = 29.2%——**机制只在有需要时增加上下文，不每轮堆提示词**。
- **短域冠军（≤24 轮）= `window_expand_b_norecap_guard`**【W17B 判决 2026-11-03，KEEP】：契约违规 **2/72 vs 7/72**；severe 亲读 cand 0 vs par 2（judge 原始 0 vs 7）；Prompt +0%；额外调用 0；**九维 8/9 为正（initiative −0.058 为唯一负维，如实并列）——不得写成"9/9"**。短对话域复评【W17C 判决 2026-11-03，MERGE】：severe 亲读 candidate 0 vs parent 3 确认（judge 旗 4，其中一处降级）；**Prompt −52.2% 为该批父本对照读数（父本与候选消费结构不同），不将其归因于 guard 本身**。
- **合并候选不晋升**【W19 跨域判决 2026-11-03】：**不宣布开发集总冠军，双域冠军保留**；uc_guard 记为跨域候选未晋升（长域空回复 5 ≠ 0 未过 G3，短域通过）——**两个机制分别有价值不等于组合后更好，不为合成一个名字追加实验**。分域口径红线：两域不同时启用 UC 与 Guard，**不得以任何形式变相启用未晋升的合并候选**。

### 5.2 三主线对照读数（同口径来源）

- **证据记忆**：FLJUDGE T25-32 保持率 1.0 vs 滑窗父本（r9_slim）0.493/0.509（Prompt −41.9%）；LD1 四域全 1.0（Prompt −44.0%，终态 425/430 = 0.988）；W15N01 删 recap 无损（−0.3%，severe 亲读 1 ≤ 3）——限定语整句见 §3.3。
- **上下文装配**：rh_converge 质量 4.67 vs 4.69（−0.3%）、Token/轮 −23%、端到端 P50 42s vs 20s 如实并列——**效率型候选定性，不写全面胜出**。
- **事实与输出约束**：W16 severe 亲读 6→1（Prompt +0.78%，0 额外调用）；W17B 违规 7/72→2/72（+0%）；W17C 短域复评 0 vs 3（0 额外调用）。

### 5.3 负结果与未判定结果（原样保留）

负结果与未判定结果不删除、不改写，全部落盘可查：

| 结果 | 判决与限定语（整句） |
| --- | --- |
| 账本式事实压制候选（b2） | 【判决 2026-11-02】H_SUP_null 触发，**淘汰**。机制归因限定语：**判决的机制归因是"检测覆盖缺口，非压制失效"——该篇压制段为空、b2 与父本输入逐字节相同、坐实两臂同暴露；不得写成"b2 机制主动坐实"。** 教训：记忆结构化不等于事实可靠；未确认状态需要单独保留。 |
| 重试变体（uc_retry） | 【W21 判决 2026-11-04】重试修复率 12/12 = 100%，但 swap 翻转 3/10 → **不得宣布单冠军**；作为评测可靠性组件保留，不包装成新的质量冠军。 |
| 归因变体（uc_attr） | 【W22 REV2 判决 2026-11-04，取代同日 REV1】json_schema_broken par 11 → cand 22、veto G3 FAIL（par 19 / cand 29）、swap 翻转 7/22；判决 **KEEP_NO_PROMOTE**——**不能为了降低归因错误，接受更严重的输出契约回归。** |
| 反事实跟随对照（CF1） | 【判决 2026-11-02】跟随率 0.681 vs 0.698，双臂均落 0.60–0.75 不确定带；**不判 PASS、不淘汰任何臂、不补跑**（未判定态）。 |
| 确定性级联（rh_b06） | 全部回复"盲答"（实现 Bug），路线撞墙淘汰；新研究线早期候选若干已被看门狗降级为待复验（[refs/KNOWN_FAILURES.md](refs/KNOWN_FAILURES.md) 与 [refs/research_harness_20261110/](refs/research_harness_20261110/) 如实登记）。 |

失败证据入口：[refs/KNOWN_FAILURES.md](refs/KNOWN_FAILURES.md) 与 [arena/KNOWN_FAILURES.md](arena/KNOWN_FAILURES.md)（失效域参照，失败族不删除）；53 份判决全量索引见 [refs/VERDICT-INDEX.md](refs/VERDICT-INDEX.md)；批次级实验账本见 [experiments/runs-registry.csv](experiments/runs-registry.csv) 与 [experiments/runs/](experiments/runs/)。

> **证据：** [refs/experiment_protocol_20261023/long_probe_20261030/](refs/experiment_protocol_20261023/long_probe_20261030/)（W13–W22/FLJUDGE/LD1/CF1 各判决原文件，W22 用 REV2）；[refs/KNOWN_FAILURES.md](refs/KNOWN_FAILURES.md)；[refs/VERDICT-INDEX.md](refs/VERDICT-INDEX.md)；[experiments/runs-registry.csv](experiments/runs-registry.csv)。

---

## 6. 公开资产导览（十二区）

逐区计数与迁移状态以 [README.md](README.md) §1 为准；每区一句话：

| 区域 | 一句话说明 |
| --- | --- |
| [benchmark/](benchmark/) | 题库与评测基准：合成故事库 stories_v2、Gold 侧车双轨三代口径、公开开发集 episode、rubric 两版与统计口径页。 |
| [dataset/](dataset/) | 母数据集公开面：结构摘要与合成样本（原件为真实产品数据，永不出站，见 §7.1 与 README §3 三口径）。 |
| [candidates/](candidates/) | 全部候选目录与版本谱系（分域冠军、历史版本、负向对照、消融、模板、参考件全保留，含不晋升者）。 |
| [evaluation/](evaluation/) | 评审判决版本链（FINAL/SUPERSEDED/INTERIM/BUGGY 等全保留，错误版不删）。 |
| [experiments/](experiments/) | 实验账本：runs 批次目录、逐批次登记表（runs-registry.csv）、隔离测试与原始日志索引（原始日志原件走 Release/LFS）。 |
| [arena/](arena/) | 研究考场运行时框架：纯标准库 Python，含网关、计分器、Gold 加载、Judge 器械族与隔离包。 |
| [refs/](refs/) | 判决书、实验协议、独立评审与失败分析（保留历史层级；VERDICT-INDEX.md 为 53 份判决的入口索引）。 |
| [archive/](archive/) | 历史归档：移植原型、交付快照与历史工作指引副本（均附"historical, not current"标注与索引）。 |
| [provenance/](provenance/) | 迁移证据：来源映射、逐文件迁移清单、隐私与许可审查。 |
| [tools/](tools/) | 评测/审计/重评分工具与测试（含在研区；唯一第三方依赖 Pillow）。 |
| [architecture/](architecture/) | 架构文档：真实生产链路、研究架构与接线状态（三态标注，不把计划画成已启用）。 |
| [PAPER.md](PAPER.md) | 研究论文稿（迁移译注前置、正文逐字不动；成绩表述须与判决文件互查）。 |

> **证据：** [README.md](README.md) §1（十二区导览与逐区计数）、§2（研究环境十四项）；[provenance/PROVENANCE-INDEX.md](provenance/PROVENANCE-INDEX.md)。

---

## 7. 诚实边界

### 7.1 不公开的内部材料（私有治理仓登记件，七项）

内部治理仓 `bystery/TeamWorkspace`（私有）承载产品规格、工程规则与治理记录，**不公开、不迁移**。其登记件为：START_HERE.md；ARCHITECTURE.md；PRODUCT_SPEC.md；ACTIVE.md；1.5.0 收口批次记录；1.4.2 发布记录；公开/私有资产边界政策（PUBLISH_POLICY）。公开侧只作角色与约束级描述，不引用台账细节。

另按母数据集三口径（[README.md](README.md) §3）：真实 KB 快照原件（5 个目录 / 84 文件 / 735,202 B）**本地保留、永不出站**；公开面仅结构摘要（不含任何正文）与合成样本（结构相似、内容全假、零真实姓名残留、零真实对话片段、零可还原特征，三层合成标记 + 生成后强制隐私扫描）。

### 7.2 离线 smoke test（十项设计，逐字保留）

> 全程**不调用真实模型 API、不读取任何题面正文**（红线）。**总状态：十项均处"设计 + 部分前置实测"；迁移落盘后逐项实测并改写为实测结果。当前不宣称 smoke test 可运行。**

| # | 设计项 | 当前状态 |
| --- | --- | --- |
| 1 | Python 模块可导入 | 源仓库本机实测通过（3.12.4，9 个 arena 核心模块 + 隔离控制器 + tools 侧 9 个 + 通用评分器）；**迁移副本待实测** |
| 2 | Gold Sidecar 可加载 | 待实测（对象 `gold_sidecar.py`） |
| 3 | rubric 和 scorer 可加载 | 待实测 |
| 4 | 一个公开开发集 episode 可被解析 | 待实测 |
| 5 | 一个历史 run manifest 可解析 | 待实测 |
| 6 | 一个已保存 scoring 文件可被读取 | 待实测 |
| 7 | 一份判决材料能找到对应原始结果 | 待实测 |
| 8 | 数据统计脚本能运行 | 待实测（候选脚本面见 [README.md](README.md) §2.12） |
| 9 | 完整性校验脚本能运行 | **停用**：基于哈希清单的公开校验流程在本版防御性分析停用，完整性以文件数 + 字节量口径登记（[README.md](README.md) 头部） |
| 10 | 全过程不调用真实模型 API、不读取题面正文 | 全程红线，与第 1–8 项同时验收 |

### 7.3 复现范围声明（七项，逐字保留）

**七项范围声明（原文级诚实）**：

1. **可从公开数据离线重算的结果**：已保存 scoring 的确定性重算、mock/echo 批次重跑——须经 smoke test 与实测确认后才可写"可重算"；当前登记为"设计可行、待实测"。
2. **依赖历史模型服务响应的结果**：全部 live 批次的原始答卷——只能重放与重评分，**不能重新生成**。
3. **依赖特定模型 endpoint 的结果**：网关默认模型 `gpt-oss-120b`；历史批次钉死端点为本地替身上游 `http://127.0.0.1:17770/v1`；授权单↔端点一致性校验是 tools 批执行器硬门槛。
4. **可能已不可用的历史 API 服务**：本地替身上游（127.0.0.1:17770）非公开服务；Judge live 批次所用外部服务无任何可用性承诺。
5. **只保留原始答卷、无法保证重新调用得到相同结果的实验**：全部 live 批次（采样参数与上游行为不可复现）。
6. **有 Judge 器械效度限制的评审结果**：judge 校准以 ≥80% 人评一致率为上岗门槛（20 道人评锚点 + trap 负样本）；位置偏差（5 候选随机重排）与温度×位置 2×2 不稳定归因实验并存；机器判定 fuzzy 命中未经人工复核**不得进对外结论**。
7. **不是当前 Android 生产代码的候选**：[candidates/](candidates/) 全部 50 个候选目录均为研究资产；[archive/integration_preview_20261025/](archive/integration_preview_20261025/) 是历史移植原型，**不冒充当前生产接线**；`best/score.json` 不作独立冠军判决；[PAPER.md](PAPER.md) 成绩表述须与 runs/判决书互查。

历史模型实验的原始答卷和判决已保留（§5、[refs/KNOWN_FAILURES.md](refs/KNOWN_FAILURES.md)）；一键完全重跑不在当前复现范围内（见 [REPRODUCIBILITY.md](REPRODUCIBILITY.md) §10）。

### 7.4 表达红线

- 分域冠军是分域的：**不得把分域冠军写成全面胜出**；不宣布单一全域冠军。
- SHADOW≠主链路启用；LIVE_AUTO 生产启用待收口验收（默认 OFF），不把计划写成已启用。
- 候选目录是研究资产，不写成当前 Android 生产机制。
- 未经人工复核的机器判定不进对外结论；未验证数字不写。
- [README.md](README.md) §4 待实测清单收口前，对应能力一律按"待实测"表述，不预填。

> **证据：** [README.md](README.md) §2.10（smoke 十项）、§2.13（完整性核验口径）、§2.14（复现范围声明）、§3（dataset 三口径）、§4（待实测清单）；[provenance/PRIVACY_REVIEW.md](provenance/PRIVACY_REVIEW.md)；[provenance/LICENSE_AND_RIGHTS.md](provenance/LICENSE_AND_RIGHTS.md)；[architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) §6（文档纪律）。
