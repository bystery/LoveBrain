# QUALITY_PANEL_SCORECARD_V2 — 质量成绩单 V2（判词/亲读/派生三层分开）

日期：2026-10-27。对象：`logs/quality_panel_v1_live_20261027_003723`（AUTH-QUALITY-PANEL-20261027-01，CLOSED 173/192）。
性质：判词分数**不改**；V2 修分母、拆首选/备选、把 worker 亲读与派生指标分栏。不宣布胜者，跨批横比只作探索。

> **身份范围（必读）**：本成绩单判的是 C4 历史答卷，四臂历史身份为 ref `6e65b6bc…`、v5 `ef855510…`、C2 `de600ff9…`、C3 `009d2425…`；当前候选身份已变（ref `501bc426…`、v5 `7c6b51ce…`、C2 `fc296dd8…`、C3 `fddcdb51…`），因此本成绩单**不能**直接当作当前代码的成绩，P06 必须按新身份重跑。

## 0. 一句话结论

- 首选可评 **117/128**；首选无 major **115/117**；备选 **769** 槽、备选 major **6** 条。
- 严格可用（有可发文本 + 已判小节无 major）**113/117 已评**（96.6%）；另有 11 条未评=unknown，不是失败。
- 同批严格配对：reference_product_v1 vs goal_action_v1: 0/21/4 (a/tie/b), incomparable=7；v5_purpose_aligned vs evidence_memory_v1: 1/27/1 (a/tie/b), incomparable=3。
- 结论只支持“本轮四臂在四个开发故事上的质量分布与缺陷族”，不支持跨批排名，也不支持用户愿发 W。

## 1. 分母与未评（如实）

| 项 | 数量 | 说明 |
|---|---:|---|
| 全批 attempt | 173 | 173/173 派发、0 传输失败、0 停批 |
| 首选计划 | 128 | 4 故事 × 8 位点 × 4 臂 |
| 首选可评 | **117** | 判词可解析且 schema 通过 |
| 首选未评 | **11** | Q024、Q031、Q036、Q118、Q104、Q058、Q056、Q098、Q103、Q113、Q057 |
| 开发控制 | 32/32 | 16 应判错 + 16 应保护 |
| 固定复评 | 10/13 可评 | 3 条未评（Q083/Q071/Q107） |
| 已评总数 | 159 | 117 首选 + 32 控制 + 10 复评 = 159 |
| 备选槽 | **769** | 仅已评首选展开；按臂 原版 reference_product_v1=179、v5_purpose_aligned=197、C2 goal_action_v1=190、C3 evidence_memory_v1=203 |

未评明细：

- `Q024` E006_T03 / v5_purpose_aligned
- `Q031` E007_T01 / goal_action_v1
- `Q036` E007_T01 / reference_product_v1
- `Q118` E008_T04 / goal_action_v1
- `Q104` E008_T05 / goal_action_v1
- `Q058` E012_T01 / reference_product_v1
- `Q056` E012_T03 / reference_product_v1
- `Q098` E012_T04 / reference_product_v1
- `Q103` E012_T05 / evidence_memory_v1
- `Q113` E012_T07 / reference_product_v1
- `Q057` E012_T08 / v5_purpose_aligned

臂身份对照（历史答卷 vs 当前代码）：

| 臂 | C4 历史身份 | 当前身份 | 变化 |
|---|---|---|---|
| 原版 reference_product_v1 | `6e65b6bced2c…` | `501bc426977b…` | 历史 C4 冻结→当前：IDENTITY.md 修订 + reference_chain.py v1.1 选择器/v1.2 ADJUSTED 生效。 |
| v5_purpose_aligned | `ef85551091a3…` | `7c6b51cec978…` | v5 自身文件未变，但闭包内 reference 从历史 6e65 变为 501bc4，因此身份变为 7c6b。 |
| C2 goal_action_v1 | `de600ff9c052…` | `fc296dd822fb…` | 历史 C4→当前：format.md analysis 契约与机制消费一致化（v1.1，P04-05）；机制代码同。 |
| C3 evidence_memory_v1 | `009d2425e13c…` | `fddcdb51aa80…` | 历史 C4→当前：v1.1 成员/引文硬校验 + v1.2 静态种子事实（P04-02/03）。 |

> 本成绩单判的是 C4 历史答卷（历史身份）；当前候选身份已变，不得把本成绩单直接当作当前代码的成绩。P06 必须按新身份重跑。

## 2. 判词分数（首选，n=117；不混备选）

| 臂 | n | goal | fact | attribution | time_state | boundary | memory_use | interaction | initiative | emotion_relation | claims(总/major) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 原版 reference_product_v1 | 27 | 2.78 | 2.76 | 2.90 | 2.93 | 2.90 | 2.74 | 2.70 | 2.32 | 2.33 | 65 / 3 |
| v5_purpose_aligned | 30 | 3.00 | 2.79 | 2.93 | 2.85 | 2.96 | 2.88 | 2.93 | 2.64 | 2.60 | 94 / 0 |
| C2 goal_action_v1 | 29 | 2.97 | 2.90 | 3.00 | 3.00 | 3.00 | 2.88 | 3.00 | 2.63 | 2.76 | 93 / 0 |
| C3 evidence_memory_v1 | 31 | 2.94 | 2.87 | 2.93 | 3.00 | 2.96 | 2.89 | 2.97 | 2.68 | 2.68 | 100 / 1 |

NA 说明：每维的 `dims_na_n` 见 JSON；time_state 的 NA 最多，不能把 NA 当 0 分。首选 major 节点：Q008、Q043。

## 3. 判词分数（备选，769 槽；弱选项单列）

| 臂 | 槽数 | goal | fact | attribution | time_state | boundary | memory_use | interaction | initiative | emotion_relation | 含 major 槽 | major 条数 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 原版 reference_product_v1 | 179 | 2.33 | 2.75 | 2.87 | 2.79 | 2.83 | 2.48 | 2.45 | 2.13 | 2.29 | 1 | 1 |
| v5_purpose_aligned | 197 | 2.30 | 2.88 | 2.92 | 2.83 | 2.89 | 2.55 | 2.44 | 2.35 | 2.31 | 1 | 1 |
| C2 goal_action_v1 | 190 | 2.38 | 2.80 | 2.95 | 2.83 | 2.89 | 2.45 | 2.60 | 2.37 | 2.42 | 1 | 1 |
| C3 evidence_memory_v1 | 203 | 2.30 | 2.85 | 2.93 | 2.88 | 2.88 | 2.66 | 2.52 | 2.26 | 2.39 | 2 | 3 |

备选 major 明细（严重备选错误）：

| 首选 | 故事 | 臂 | 槽 | major 条数 |
|---|---|---|---|---:|
| Q015 | E007 | C2 goal_action_v1 | directions[1] | 1 |
| Q008 | E008 | C3 evidence_memory_v1 | response.playful | 1 |
| Q008 | E008 | C3 evidence_memory_v1 | response.warm | 2 |
| Q043 | E008 | 原版 reference_product_v1 | response.warm | 1 |
| Q054 | E012 | v5_purpose_aligned | directions[3] | 1 |

合计 **6 条 major / 5 个备选槽 / 4 条首选**（Q008、Q015、Q043、Q054）；其中 v5 Q054“先不说这个了”经 major 复核为存疑（备选弱选项边界），其余 5 条为确认缺陷。

## 4. 故事级（首选；均值为九维非空单元的未加权平均）

| 故事 | 臂 | 可评 | 均分 | 首选 major 条/节点 | 备选槽 | 备选 major |
|---|---|---:|---:|---|---:|---:|
| E006 | 原版 reference_product_v1 | 8/8 | 2.84 | 0/0 | 53 | 0 |
| E006 | v5_purpose_aligned | 7/8 | 2.85 | 0/0 | 46 | 0 |
| E006 | C2 goal_action_v1 | 8/8 | 2.89 | 0/0 | 51 | 0 |
| E006 | C3 evidence_memory_v1 | 8/8 | 2.92 | 0/0 | 53 | 0 |
| E007 | 原版 reference_product_v1 | 7/8 | 2.65 | 0/0 | 47 | 0 |
| E007 | v5_purpose_aligned | 8/8 | 2.90 | 0/0 | 51 | 0 |
| E007 | C2 goal_action_v1 | 7/8 | 2.90 | 0/0 | 45 | 1 |
| E007 | C3 evidence_memory_v1 | 8/8 | 2.79 | 0/0 | 53 | 0 |
| E008 | 原版 reference_product_v1 | 8/8 | 2.52 | 3/1 | 54 | 1 |
| E008 | v5_purpose_aligned | 8/8 | 2.78 | 0/0 | 55 | 0 |
| E008 | C2 goal_action_v1 | 6/8 | 2.94 | 0/0 | 40 | 0 |
| E008 | C3 evidence_memory_v1 | 8/8 | 2.85 | 1/1 | 53 | 3 |
| E012 | 原版 reference_product_v1 | 4/8 | 2.83 | 0/0 | 25 | 0 |
| E012 | v5_purpose_aligned | 7/8 | 2.83 | 0/0 | 45 | 1 |
| E012 | C2 goal_action_v1 | 8/8 | 2.88 | 0/0 | 54 | 0 |
| E012 | C3 evidence_memory_v1 | 7/8 | 2.93 | 0/0 | 44 | 0 |

读法：故事级分母不同（E012 ref 仅 4/8，E008 C2 6/8），不得直接跨臂横比故事均分；故事级只用于定位缺陷热点。E008_T03 是热点。

## 5. 同批严格配对（首选；全 32 槽为分母）

| 配对 | 可比 | a 胜 | 平 | b 胜 | 不可比 |
|---|---:|---:|---:|---:|---:|
| reference_product_v1 vs goal_action_v1 | 25 | 0 | 21 | 4 | 7 |
| v5_purpose_aligned vs evidence_memory_v1 | 29 | 1 | 27 | 1 | 3 |

- reference_product_v1 vs goal_action_v1 分故事：E006 w/t/l/x=0/8/0/0；E007 w/t/l/x=0/6/1/1；E008 w/t/l/x=0/3/3/2；E012 w/t/l/x=0/4/0/4
- v5_purpose_aligned vs evidence_memory_v1 分故事：E006 w/t/l/x=0/7/0/1；E007 w/t/l/x=1/7/0/0；E008 w/t/l/x=0/8/0/0；E012 w/t/l/x=0/5/1/2
- 跨批组合（ref vs C3、ref vs v5、C2 vs C3、v5 vs C2）没有同批严格配对；四臂横比来自两批，禁止据此晋级/淘汰。

## 6. worker 亲读 overlay（不是判词分数，也不改分）

### 6.1 覆盖与修正

- 4 个故事、128 条首选原文全部亲读；117 条有判词逐一对读；**判词分数零修正**。B3 分片手写 121 条，V2 重算为 117 条（见 §10）。
- 亲读性质：同一 AI 作者侧的第二双眼睛，非独立人类金标。

### 6.2 漏检登记（不改历史判词）

| 位点 | 臂 | 级别 | 亲读发现 |
|---|---|---|---|
| E012_T03 | C2 goal_action_v1 | major | 首选“那家我也一直想再去一次”是无据亲历断言（原话只支持“你上次提过”，不支持“我也去过”）；判词仅 factuality=2，未标 major。 |
| E008_T02 | v5_purpose_aligned | minor | 用“他”指小林，题面未给小林性别；无据人称具体化，判词未标 minor。 |

### 6.3 major 复核

- 控制 major 20 条：全部落在预期应判错控制上。
- 真实臂 major 10 条：确认候选缺陷 9 条；存疑 1 条（E012_T05 v5_purpose_aligned `先不说这个了`）。
- 热点：E008_T03（ref+C3 共 8 条 major）。

### 6.4 引文核对

- 原始 flag 19 条：分析器重建假阳性 16 条、空格/标点级编辑 3 条、捏造 0 条。
- V1_1 分析器仍有 5 条 residual（Q015、Q034、Q044、Q081、Q111），列入审计队列，不当作考生错误。

### 6.5 定性发现（不进九维分）

- **F1** E006 跨轮回扣分野：C2/C3 在 T05/T07 主动回扣“昨天被当众批评”，ref/v5 全故事零回扣。 证据：B3 E006 §2；卡把回扣列为 optional_history、无 required atoms，memory_use 不计分。 动作：九维未覆盖的正向行为；下一版卡增设 optional 回扣加分观察，不加扣分。
- **F2** C3 在 E007_T01 第一轮情绪位塞入隐性邀约（“出来吃口热的，就当逃跑第一站”）。 证据：B3 E007 §1；judge goal=1 压分正确。 动作：动作层加“首情绪位不塞邀约”护栏；列 M2 回归。
- **F3** E007_T03 首选层四臂均无编造亲历；编造在 C2 备选槽（directions[1]）。 证据：B3 E007 §2；major 复核确认。 动作：首选/备选必须分开读，备选 major 不得被首选层均分稀释。
- **F4** 回扣行为跨臂不稳定：E007_T07 v5 回扣最佳；E006 则 C2/C3 回扣。 证据：B3 E006/E007 §2。 动作：不能把“某臂会回扣”当稳定属性；需多故事收尾/转场位采样。
- **F5** E008_T03 factuality 梯度 0/1/2/2 与无据确定否定条数 3/2/1/1 精确对应；v5 是唯一引用有据事实的臂。 证据：B3 E008 §1/§2；4 条 major 落在 f0/f1 两臂。 动作：本热点上判词事实维可信度正例；作为裁判锚例。
- **F6** E008_T06 memory_use 对“行动即确认”不敏感：ref/C2 用行动配合得 m=None，v5/C3 显式复述得 m=3。 证据：B3 E008 §2。 动作：m 均分差不得直接读成“记忆能力差”；口径注记。
- **F7** E012_T03 C2 首选“那家我也一直想再去一次”漏标 major（无据亲历）。 证据：B3 E012 §1；与控制组 CTL06/08、E007_T03 备选同族。 动作：V1_1 §0-7+§2a 必要性的第三处证据；下批带 CTL33-36 复测。
- **F8** 判词轻微保守：打趣/计划自述被 factuality=2 压半档（C2/C3 E012_T01、ref E012_T02），无 0/1 级错判。 证据：B3 E012 §2。 动作：会系统性压低 interaction 高臂的 factuality 均分；V2 注记，不改分。

正向锚例候选：v5 E012_T04 “话我收回一半”（退边界/不赌气）；v5 E012_T05 “是我推得太急了…去不去、什么时候去你说了算，不去我也不会多想”（认领+还主动权）；C2 E012_T08 “这句我先收着，等你困劲儿过去再说”（接信号不逼定）。

## 7. 派生指标：至少一个可用率

| 臂 | 计划 | 可评 | 有可发文本 | 首选无 major | 备选无 major | 严格可用/可评 | 严格可用/计划 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 原版 reference_product_v1 | 32 | 27 | 32 | 26 | 26 | 26（96.3%） | 26（81.2%） |
| v5_purpose_aligned | 32 | 30 | 32 | 30 | 29 | 29（96.7%） | 29（90.6%） |
| C2 goal_action_v1 | 32 | 29 | 32 | 29 | 28 | 28（96.6%） | 28（87.5%） |
| C3 evidence_memory_v1 | 32 | 31 | 32 | 30 | 30 | 30（96.8%） | 30（93.8%） |

定义：**有可发文本**=response 四候选或 directions 至少一个非空字符串；**严格可用**=有可发文本 + 判词可解析 + primary/backups 全部已判小节无 major。未评 11 条不进入分母，按 unknown 处理；因此“严格可用/计划”是下界，不是失败率。

- 全批有可发文本（至少一个可发候选）：**128/128（100.0%）**；这只证明有字，不证明质量。
- 首选 major 条数 4、备选 major 条数 6；首选 major 节点 Q008、Q043。

## 8. 知识分母（与质量分分开）

来源：`evaluation/KNOWLEDGE_STAGE_SCORECARD_V1.json`（离线复算，真实调用 0）。

- 全卡集聊天消息原子 **45** 条；被排除的 note/intent 记录 **38** 条；本 panel 的迟于 T08 原子作用域 **40** 条、配对 **158**。
- 45 与 38 不是同一总体的加减，不能相减推 40；40 是 panel 作用域。

| 臂 | S1 T08 在场 | S1 存储 | S2 条目 | S2 有据% | S3 在场 | S3 结构化 | S3 仅原始% | S4 |
|---|---|---|---:|---:|---|---:|---:|---|
| 原版 reference_product_v1 | 11/40 | 44/45 | 53 | 62.5% | 67/158 | 18/158 | 29.7% | NR-需人判 |
| v5_purpose_aligned | 39/40 | NR(无可观测存储) | — | — | 157/158 | 0/158 | 96.8% | NR-需人判 |
| C2 goal_action_v1 | 39/40 | NR(无可观测存储) | — | — | 157/158 | 0/158 | 96.8% | NR-需人判 |
| C3 evidence_memory_v1 | 39/40 | NR(无可观测存储) | — | — | 157/158 | 2/158 | 95.6% | NR-需人判 |

- 45 条是本卡集聊天消息原子；38 条是被排除的 note/intent 记录；两者不是同一总体的加减，不可用 45-38 推 40。
- S1 三列数据源不同，不可相加；S1_store 只有 reference_product_v1 可观测。
- S3 只证明“进了出站 prompt”，不证明回复用对；S4 为 NR-需人判。
- S2 只有有落盘存储的臂可测；本批仅 reference_product_v1，其他 NR=覆盖缺口，不是零分。

## 9. unknown 与可比性

- 用户愿发 W：unknown（模型禁填；32 样本表仍未回收）。
- 金额：unknown（无可核对单价；judge 侧 token 记账在账本）。
- 泛化：四个故事全部 dev_exposed，本成绩只对开发集成立。
- 11 条首选未评：unknown，不是失败，也不是零分。
- 九维资格：AI 辅助读数；重测逐维精确一致 6.56/9，未达“全信”门槛。

- 四臂横比来自 C4 两批（sweep_a ref/C2，sweep_b v5/C3），批次/时段混杂；仅同批配对可作严格比较，跨批横比只作探索，不得据此晋级/淘汰。
- 不可排名组合：C2 与 C3、ref 与 v5、ref 与 C3 等跨批组合均无同批严格配对。

## 10. 修正与口径（撤销/修正逐条）

### B3 分片的首选可评数

- 原文：B3 E012 分片写四片合计 121 条有判词；E008 分片写 31 条；四片分项相加为 118（E006 31 + E007 30 + E008 31 + E012 26）。
- 修正：按 RATINGS_REPAIRED 重算：117 条首选可评；四片为 31/30/30/26（E008 缺 Q118 E008_T04、Q104 E008_T05，均为 C2 未评）。
- 原因：手工分片计数未按可解析字段重算，且分片间算术不一致；V2 以机器分母为准。
- 来源：evaluation/B3_PRIMARY_READ_E008_V1.md；evaluation/B3_PRIMARY_READ_E012_V1.md；logs/quality_panel_v1_live_20261027_003723/RATINGS_REPAIRED.jsonl

### 重测差≤1 均值

- 原文：V1 写 7.22/9。
- 修正：按 9 条可比复算 7.11/9（64/9）；逐维精确一致 6.56/9（59/9）不变。
- 原因：V1 的分母/求和口径笔误；V2 由 repeat rows 重算。
- 来源：logs/quality_panel_v1_live_20261027_003723/QUALITY_PANEL_ANALYSIS_V1_1.json

### 判词引文 19 处不符

- 原文：V1 把 19 条原始 flag 读成“判词引文不可靠”的证据。
- 修正：19 条中 16 条为分析器输入重建假阳性、3 条为空格/标点级轻微编辑、0 条捏造；V1_1 分析器仍有 5 条 residual 审计队列（Q015/Q034/Q044/Q081/Q111）。
- 原因：原始 flag 是待核队列，不是判词错误；major 复核已逐条人工对读。
- 来源：evaluation/QUALITY_PANEL_SCORECARD_V1.md；evaluation/QUALITY_PANEL_MAJOR_REVIEW_V1.md；logs/quality_panel_v1_live_20261027_003723/QUALITY_PANEL_ANALYSIS_V1_1.json

### 首选/备选均分口径

- 原文：V1 §2 的逐维均分是“首选+非空备选合并段”口径，n 为首选数。
- 修正：V2 把 primary（117）与 backups（769 槽）完全分开；合并段仅作为 legacy_merged_view 附录保留，不用于判优。
- 原因：备选是弱选项，混入均值会稀释/放大风险，且不同臂备选槽数不同。
- 来源：evaluation/QUALITY_PANEL_SCORECARD_V1.md；logs/quality_panel_v1_live_20261027_003723/QUALITY_PANEL_ANALYSIS_V1_1.json

## 11. 证据索引

- `D:/lovebrain-arena/logs/quality_panel_v1_live_20261027_003723/RATINGS_REPAIRED.jsonl`
- `D:/lovebrain-arena/logs/quality_panel_v1_live_20261027_003723/QUALITY_PANEL_ANALYSIS_V1_1.json`
- `D:/lovebrain-arena/logs/quality_panel_v1_live_20261027_003723/JUDGE_BATCH_SUMMARY.json`
- `refs/experiment_protocol_20261023/knowledge_quality_independent_review_20261026/PRIVATE_BINDINGS.json`
- `evaluation/KNOWLEDGE_STAGE_SCORECARD_V1.json`

---

判词分数未改；V2 只改分母、拆首选/备选、并把 worker 亲读与派生指标分栏。任何晋级/淘汰结论须等 P06 三批同批严格配对与用户 W 标定，不能由本成绩单单独决定。
