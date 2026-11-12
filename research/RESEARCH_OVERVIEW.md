# LoveBrain 研究总览（RESEARCH_OVERVIEW）

> **本文定位：** 研究总览——研究问题、动机、整体方法与主要贡献的一页入口。与 [README.md](README.md)（研究入口与导航）、[architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md)（真实生产链路与研究架构）、[PAPER.md](PAPER.md)（论文稿）、[benchmark/STATISTICS.md](benchmark/STATISTICS.md)（公开统计口径页）互链；细节以各专文为准。
> **纪律：** 零真实密钥、零真实聊天内容、零个人数据（数据隐私边界见 [DATASET_DOCUMENTATION.md](DATASET_DOCUMENTATION.md) §8）；全部数字可按标注的判决文件或口径页核验。

---

## 1. 项目定位与研究问题

### 1.1 定位

LoveBrain 的项目定位是**偏大模型算法研究，产品 AI 应用开发为次要展示**——研究问题、数据、实验和机制是主要工作。Android 产品（[app/](../app/)）是机制的落地载体与真实验证环境；研究资产公开面整体落位于本目录 `research/`。

### 1.2 研究问题与动机

LoveBrain 是一个长对话聊天辅助产品，其核心研究问题不是"如何写更好的 Prompt"，而是**上下文与记忆的确定性管理**。动机来自三类真实失败模式：

1. **无关历史干扰。** 聊天辅助产品不是记忆越多越好：全量注入会让已结束话题重现、旧目标在用户改变想法后继续生效、旧事项挤占预算。核心原则：**存储里存在，不代表当前轮应该使用。**
2. **关键证据滑出窗口。** 长对话中丢失的不是大概主题而是具体事实：早期金额、日期、约定、决定主体、方案是否取消、较早的重要原话。滑动窗口把早期消息移出上下文，摘要链可能丢事件动作、主体、原话和细节——问题的实质是**关键原始证据已不在当前可见上下文里**。
3. **事实升级与输出漂移。** 未确认的信息必须继续保持未确认，不能被模型补全成已发生事实；同时模型输出结构会漂移（键名、槽位、方向字段），需要确定性的修复边界。

### 1.3 整体方法与主要贡献

**方法：** 把机制候选放入独立研究考场（[arena/](arena/)），在固定模型服务下进行连续对话对照与消融，以判决制收口（KEEP / MERGE / KEEP_NO_PROMOTE / ELIMINATE / 未判定如实登记），只有获得判决支持的机制才向 Android 产品移植，移植后以受控档位接入（档位状态见 §3.5 与 [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) 卷首声明）。评测读数一律可追溯到落盘判决文件。

**主要贡献：**

1. **三条机制主线**（上下文装配与相关性门控 / 证据优先的历史记忆 / 事实与输出约束），各带判决支持的实验证据与产品移植状态（§3、§5）；
2. **可核验的题库与 Gold 双轨评测体系**：统计口径机器重算、多源一致、恒等式闭合（§4）；
3. **判决书级实验证据链**：53 份判决文件全量登记入索引，负结果与错误版本不删（§5、[refs/KNOWN_FAILURES.md](refs/KNOWN_FAILURES.md)）；
4. **原样保留的诚实边界**：复现范围、未验证项与不公开材料逐项登记（§7）。

> **证据：** [PAPER.md](PAPER.md)（研究论文稿）；[README.md](README.md)（研究入口）；[architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) §1–§3（三仓关系与研究架构）。

---

## 2. 三仓库体系

LoveBrain 项目由三个仓库构成，角色不互相混淆。**产品仓 `bystery/LoveBrain`**（唯一公开仓库）：Android 聊天辅助产品（Kotlin/Compose），`closeout-20261106` 分支承载机制移植与收口，`research/` 目录承载研究资产公开面；**研究仓 `bystery/arena`**（私有）：研究考场——`arena/` 考场运行时框架 + `isolation/` 隔离包、`tools/` 评测工具、`candidates/` 参赛候选与分域冠军、`runs/` 实验账本、判决书与实验协议，其公开面已按迁移清单整体平移至本仓 `research/` 十二区；**治理仓 `bystery/TeamWorkspace`**（私有，全程只读）：产品规格、工程规则与任务台账，对产品仓的合并与发布施加治理约束（机制上线须真机验收与明确授权）。三者之间的信息流是单向收敛的：产品真实故障族进入研究考场形成实验与判决，只有获得 KEEP/MERGE 判决的机制才向产品移植，移植后以受控档位接入产品，治理仓负责守住合并门槛。

> **证据：** [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) §1（三仓关系图与逐节点实测核对表 §5.3）；迁移映射见 [provenance/SOURCE_MAP.md](provenance/SOURCE_MAP.md) 与 [provenance/MIGRATION_MANIFEST.csv](provenance/MIGRATION_MANIFEST.csv)。

---

## 3. 核心机制：三条主线

### 3.1 概括

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

### 3.5 实现状态

每个机制区分三种状态：**研究原型**（[arena/](arena/) 与 [candidates/](candidates/) 候选）、**Android 移植件**（[app/](../app/) champion 文件实存并注册）、**生产主链路状态**。当前状态：移植件经显式 SHADOW 档在影子链路运行，SHADOW≠主链路启用；分域冠军是分域的，不是全域的；LIVE_AUTO 分域接线（≤24 轮 guard / ≥25 轮 uc）已在 `closeout-20261106` 产品分支落码并设为默认档，真机行为级验收待执行；本研究分支的 `app/` 代码不含该接线（默认 OFF）。不把计划写成已启用。

> **证据：** [architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) §3/§4（研究架构与接线状态）；[refs/experiment_protocol_20261023/long_probe_20261030/](refs/experiment_protocol_20261023/long_probe_20261030/)（W16/W17B/W17C/W19/FLJUDGE/LD1/W15N01 判决原文件）；[refs/VERDICT-INDEX.md](refs/VERDICT-INDEX.md)（53 份判决→机制主线映射）；[refs/research_harness_20261110/overnight/](refs/research_harness_20261110/overnight/)（新研究线判决链）；[tools/benchmark_v1/prototype_candidates/rh_converge/](tools/benchmark_v1/prototype_candidates/rh_converge/)。

---

## 4. 数据集与评测体系

题库为合成对话故事库（`benchmark/stories_v2/`，315/315 标记 `real_user_material=false`）。核心口径链：

| 数字 | 口径 |
| ---: | --- |
| **319 → 317** | stories_v2 全部 story 目录 → 按 ID 去重后唯一 Story（2 个版本目录重复组；其中 315 有 episode.json 支撑） |
| **299 / 4,082 / 9,646** | Gold 覆盖 Episode / Turn / Atomic Assertion（权威轨 GOLD_SIDECAR_v2 本地重建，2026-11-10；覆盖的是既有 story，不是新增独立故事） |
| **248 → 288** | custody 账本注册 Story（审计时点 248；2026-11-12 wave5 补登 40 条后 288，纯追加；两口径并列登记、不相加不互改） |

全部口径、恒等式与使用纪律的唯一权威页为 [benchmark/STATISTICS.md](benchmark/STATISTICS.md)；历史口径（208 / 221 / 259 / 弃用的 296）并列登记于该页 §一，不在首页展开。Gold 两轨三代口径（文档口径 221 / 远端快照 259 / 本地 v2 重建 299）的构成、落位与权威轨选择依据见 [DATASET_DOCUMENTATION.md](DATASET_DOCUMENTATION.md) §6。

### 4.1 评测协议（要点）

- **评分协议**：[benchmark/rubric.md](benchmark/rubric.md)（现行版；历史草案 rubric_v0_6_draft.md 留档；批次↔rubric 版本对照表待登记，登记前不声称全部历史 run 的版本已知）。计分器 [arena/lb_scorer.py](arena/lb_scorer.py) v0.7 为唯一计分器（C0–C15 检查点 + veto 族），前置代际 [arena/scorer.py](arena/scorer.py)（v3）并存保留、不互相冒充；历史 scoring 文件重算必须与产出它的评分器版本对齐。
- **Judge 器械**：校准器（≥80% 人评一致率上岗）、位置偏差控制、温度×位置 2×2 归因实验（[arena/lb_judge_calib.py](arena/lb_judge_calib.py) 等）；机器判定 fuzzy 命中未经人工复核不得进对外结论。
- **评审判决版本链**：[evaluation/](evaluation/)（FINAL / SUPERSEDED / INTERIM / BUGGY / PREVIEW / MOCK / DEBUG 全保留，错误版不删）。

> **证据：** [benchmark/STATISTICS.md](benchmark/STATISTICS.md)（八数字口径页与解封判定）；[benchmark/stories_v2/GOLD_SCHEMA_V2.md](benchmark/stories_v2/GOLD_SCHEMA_V2.md)；体系细节见 [BENCHMARK_EVALUATION.md](BENCHMARK_EVALUATION.md)。

---

## 5. 实验结果概述

### 5.1 分域冠军判决（分域不合并）

长对话证据记忆与约束机制的最终判决是**分域冠军**，不合并、不宣布单一全域冠军：

- **长域冠军（>24 轮）= `window_expand_b_norecap_uc`**【W16 判决 2026-11-02，KEEP】：severe **亲读 1 vs 6**（judge 原始 1 vs 10，以亲读账为准）；九维 9/9 全正；Prompt +0.78%（≤5%）；额外模型调用 0；触发 35/120 = 29.2%——**机制只在有需要时增加上下文，不每轮堆提示词**。
- **短域冠军（≤24 轮）= `window_expand_b_norecap_guard`**【W17B 判决 2026-11-03，KEEP】：契约违规 **2/72 vs 7/72**；severe 亲读 cand 0 vs par 2（judge 原始 0 vs 7）；Prompt +0%；额外调用 0；**九维 8/9 为正（initiative −0.058 为唯一负维，如实并列）——不得写成"9/9"**。短对话域复评【W17C 判决 2026-11-03，MERGE】：severe 亲读 candidate 0 vs parent 3 确认（judge 旗 4，其中一处降级）；**Prompt −52.2% 为该批父本对照读数（父本与候选消费结构不同），不将其归因于 guard 本身**。
- **合并候选不晋升**【W19 跨域判决 2026-11-03】：**不宣布开发集总冠军，双域冠军保留**；uc_guard 记为跨域候选未晋升（长域空回复 5 ≠ 0 未过 G3，短域通过）——**两个机制分别有价值不等于组合后更好，不为合成一个名字追加实验**。分域口径红线：两域不同时启用 UC 与 Guard，**不得以任何形式变相启用未晋升的合并候选**。

### 5.2 负结果与未判定结果（原样保留）

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

## 6. 公开资产导览

研究目录十二区（benchmark / dataset / candidates / evaluation / experiments / arena / refs / archive / provenance / tools / architecture / PAPER.md）的逐区说明见 [README.md](README.md) 目录导览，件级索引与阅读路径见 [RESEARCH_INDEX.md](RESEARCH_INDEX.md)；迁移证据（来源映射、逐件清单、隐私与许可审查）见 [provenance/PROVENANCE-INDEX.md](provenance/PROVENANCE-INDEX.md)。

---

## 7. 关键边界

### 7.1 数据隐私与不公开材料

母数据集 KB 快照原件（真实产品运行时导出，5 个目录 / 84 文件 / 735,202 B）**本地保留、永不出站**；公开面仅结构摘要（不含任何正文）与合成样本（结构相似、内容全假，三层合成标记 + 生成后隐私扫描，见 [DATASET_DOCUMENTATION.md](DATASET_DOCUMENTATION.md) §8）。内部治理仓 `bystery/TeamWorkspace`（私有）承载产品规格、工程规则与治理记录，不公开、不迁移；公开侧只作角色与约束级描述（三仓库关系见 §2）。

### 7.2 复现范围

环境搭建、运行入口与离线 smoke test 实测记录见 [REPRODUCIBILITY.md](REPRODUCIBILITY.md) §1–§7（首跑 9 PASS / 0 FAIL / 1 停用；验证的是模块级导入与文件级解析，端到端命令重跑待收口）。复现范围七项声明见同文 §8.2（权威来源）；一键完全重跑不在当前复现范围内（§10）。历史模型实验的原始答卷和判决已保留（§5、[refs/KNOWN_FAILURES.md](refs/KNOWN_FAILURES.md)）。

### 7.3 表述口径

- 分域冠军是分域的，不合并、不宣布单一全域冠军（§5.1）。
- SHADOW≠主链路启用；LIVE_AUTO 接线状态按分支区分（§3.5）。
- 候选目录是研究资产，不写成当前 Android 生产机制。
- 未经人工复核的机器判定不进对外结论；未验证数字不写。

> **证据：** [provenance/PRIVACY_REVIEW.md](provenance/PRIVACY_REVIEW.md)；[provenance/LICENSE_AND_RIGHTS.md](provenance/LICENSE_AND_RIGHTS.md)；[REPRODUCIBILITY.md](REPRODUCIBILITY.md) §8–§10；[architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md) 卷首分支状态声明。
