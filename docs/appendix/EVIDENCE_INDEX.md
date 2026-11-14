# LoveBrain｜实验成绩单与证据索引

> 本页服务于根 README：保留各项原始分母、评分口径、对照对象、历史不同批次的结果，不让首页读者替研究人员翻目录。以下全部继承已有 Arena / LoveBrain 实验，**没有新跑实验、没有重生成、没有新造题**。

## A. 用户最关心的主战果

| 要证明的价值 | 实验原值 | 对照是什么 | 原始证据 |
| --- | --- | --- | --- |
| 长聊天不会因窗口缩短丢掉关键证据 | 第 25–32 轮保持率 **1.000 vs 0.493 / 0.509**；Prompt −41.9% | 同批滑窗父本 `r9_slim`，两篇独立故事各一个父本读数 | [FLJUDGE](../../research/refs/experiment_protocol_20261023/long_probe_20261030/FLJUDGE_VERDICT_20261102.md) |
| 长程保持跨四域仍成立 | 出行/职场/餐饮/健康四域第 25–32 轮 **1.000 / 1.000 / 1.000 / 1.000**；父本 **.323 / .512 / .624 / .568**；终态 425/430 | 同批原滑窗父本 | [LD1](../../research/refs/experiment_protocol_20261023/long_probe_20261030/LD1_VERDICT_20261102.md) |
| 避免把未定的事说成已经发生 | 严重事实错误 **1 vs 6**（亲读终值）；memory +.301 / fact +.287 / goal +.278 / emotion +.226 / boundary +.148，九维全部为正 | 同批不带 UC 约束块的父本 | [W16](../../research/refs/experiment_protocol_20261023/long_probe_20261030/W16_UNC_VERDICT_20261102.md) |
| 多风格回复确有整组选择价值（新生成） | **LoveBrain 11 胜 / B1 1 胜**，12 对、无平局 | 原生子代理同批生成与盲评，完整候选组比较 | [M5-N 最终账（权威）](../../research/evaluation/user_value_v1/m5-final-tally.json) · [逐对摘要（次级）](../../research/evaluation/user_value_v1/summary.json) |
| 历史固定模型、按整组比较再次获胜 | **CURRENT 11 胜 / DIRECT 0 胜 / 1 平** | deepseek-flash-2 历史输出，原生子代理按组评审 | [M5-H 最终账](../../research/evaluation/user_value_v1/m5h-final-tally.json) |
| 主建议直接可用 | **12/12 vs 11/12**（N 和 H 两批分别测得同数） | 各自同批的普通基线 | [M5-N](../../research/evaluation/user_value_v1/m5-final-tally.json) · [M5-H](../../research/evaluation/user_value_v1/m5h-final-tally.json) |
| 有效话题方向，不是同义词换皮 | **44/47 = 93.6%**（新生成）与 **41/41 = 100%**（历史固定模型）；有效候选重复率 **0/329**（新）与 **0/287**（旧） | 内部有效性与重复性检验，基线没有完全同构的方向槽 | [M5-N](../../research/evaluation/user_value_v1/m5-final-tally.json) · [M5-H](../../research/evaluation/user_value_v1/m5h-final-tally.json) |
| 输出结构更稳，能被应用解析 | **契约违规 2/72 vs 7/72**；同批严重错误 **0 vs 2**；零额外模型调用 | 同批不启用 ContractGuard 的父本 | [W17B](../../research/refs/experiment_protocol_20261023/long_probe_20261030/W17B_GUARD_VERDICT_20261103.md) |
| 有价值的简化而非堆复杂度 | 删除 recap 后长程保持两臂仍 **1.0/1.0**，Prompt −0.3% | recap 有无消融 | [W15N01](../../research/refs/experiment_protocol_20261023/long_probe_20261030/W15N01_ABLATION_VERDICT_20261102.md) |

**读表说明：** 前三行及契约守卫是具体机制相对历史父本的同批实验；M5 是完整候选组与普通基线的用户价值对比。不能把旧滑窗父本直接写成 CoT，也不能把两批 M5 胜数合并冒充单个 24 对盲评。对用户而言，它们共同解释了产品价值：不丢早期事实、不乱坐实事实、给出有意义的不同回复选择。

### 来自 Arena 新研究线的另一条多候选证据

[2026-11-11 晨报](../../research/refs/research_harness_20261110/overnight/MORNING_REPORT.md) 与 [完整 LOOP 日志](../../research/refs/research_harness_20261110/overnight/LIVE_LOOP_LOG.md) 还给出了**多候选池天花板**：同批 24 个位置，从四路候选中选取各位最优，达到 **19 胜 / 5 平 / 0 负**（对比单路 b04），五维最优均分 **4.95**；后续轻量选择器与原固定选择对比为 **5 更好 / 7 持平 / 0 更差**（12 位）。这是另一个实验，其价值在于证明“多生成几条 + 让用户/选择器挑选”确实能提高可用上限，不冒充已经自动选择后的全部实际收益，也不混入 M5 的 12 对分母。

## B. M5 六项已经闭合，不再评估

| 冻结指标 | N：LoveBrain L2 | N：普通 B1 | H：CURRENT | H：DIRECT |
| --- | ---: | ---: | ---: | ---: |
| 首选可用率 | **12/12** | 11/12 | **12/12** | 11/12 |
| 至少一条可用率 | **12/12** | 11/12 | **12/12** | 单通道，与首选等价 |
| 有效话题方向 | **44/47** | 无同构方向槽 | **41/41** | 无同构方向槽 |
| 重复候选对比例（越低越好） | 0/329 | 0/36 | 0/287 | 单通道不适用 |
| 存在严重错误备选的题比例（越低越好） | 3/12 | 2/12 | 0/12 | 单通道不适用 |
| 整组盲评偏好 | **11 胜** | 1 胜 | **11 胜** | 0 胜、1 平 |

六项标准在实验前的 [评分规则 5](../../research/evaluation/user_value_v1/scoring-templates.md) 冻结：**可用**不是空泛的喜欢，是事实没有用错、未定事项没有坐实、没有越过重要边界；**有效方向**必须与当下话题相关、彼此真正不同；**重复**按最长公共子串占比超过 0.6 判定；**整组偏好**匿名交换顺序，评委选择哪一组更方便找到想发送的回复。子指标 6（N）直接复用已完成的 E5-N 盲评，没有二次跑题或重生成。

**此处公开全部六项，是为了准确记录实验，不是要求继续改造：** N 线备选严重错误题比例 3/12 对 2/12；M4 两臂同为 12/12 优秀；它们在这里保留，但不喧宾夺主地占据首页黄金位置。

## C. 与 CoT / Direct 的独立对照

本轮固定模型旧输出的 12 题小样本，按首选回复评分结果如下。它不是前面长程窗口专用测试：

| 12 题小样本 | DIRECT | CoT | LoveBrain CURRENT | LEGACY |
| --- | ---: | ---: | ---: | ---: |
| 关键事实均正确的题数 | 5 | 9 | 7 | 10 |
| 最新状态及旧事抑制均正确 | 3 | 3 | 2 | 1 |
| 无严重事实边界错误 | 9 | 7 | **10** | 11 |

这批题说明 **LoveBrain 对 Direct 的关键事实使用与事实边界有提升，事实边界对 CoT 有提升**；而在短题记忆和纠错上没有全面领先。真正的 25–32 轮长程保持优势，应以它自己已经跑完、题型对口的 FLJUDGE/LD1 为准，不用把短题当长题再考一遍。源文件：[scores.csv](../../research/evaluation/user_value_v1/scores.csv) · [summary.json](../../research/evaluation/user_value_v1/summary.json)。

## D. 为什么“一组多条”和“首选一条”是不同产品

同一批历史答案，只看首选回复时，历史回复偏好判定 **6 胜 / 6 负**；改为按真实交付的完整回复组评估时，**11 胜 / 0 负 / 1 平**。这是评估单位不同带来的信息差，不能说成同一指标从 6:6 训练提升到了 11:0。

从产品体验看，一条能发的回复不等于「给了我几种可选择的态度」。LoveBrain 允许认真、幽默、试探、温暖和换话题等路线同时存在。测试中的完整候选数不要求基线与 LoveBrain 相同，这恰好是我们设计的能力本身。**N 批 B1 在部分案例确实给出了多个纯文本选项**，不能一概描述为单条；H 批 DIRECT 为单条输出。评审标准不是“谁字多谁获胜”，而是谁的建议更有效、更安全、更有真正可选的方向。

## E. 原始故事、可复核输出、规模

- [E340 合唱团真实合成测试故事](../../research/benchmark/stories_v2/wave5_selection/E340/episode.json)；[LoveBrain CURRENT 原始回复](../../research/experiments/runs/selection_v1/CURRENT/E340/turn_10_E340_T10/reply_raw.txt)；[Direct 原始回复](../../research/experiments/runs/selection_v1/DIRECT/E340/turn_10_E340_T10/reply_raw.txt)；[E340 的 M5-H 逐候选评语](../../research/evaluation/user_value_v1/m5-eval/hpair2-01.json)；[新生成对照的 M5-N 逐候选评语](../../research/evaluation/user_value_v1/m5-eval/npair2-01.json)。
- [数据集权威统计](../../research/benchmark/STATISTICS.md)：317 个去重故事，其中 299 个 Gold 覆盖，4,082 题目轮、9,646 条细粒度断言。60 题为当前用户价值冻结集，12 对为本次 M5 盲评的实际对比规模；这三个数量不是同一个分母。
- [53 份已有判决的全目录](../../research/refs/VERDICT-INDEX.md)；[候选机制与结果映射](../../research/CORE_MECHANISMS.md)；[原始评审报告](../../research/evaluation/user_value_v1/report.md)；[可复现范围](../../research/REPRODUCIBILITY.md)。
- [Android 演示 GIF](../../images/demo.gif) · [架构示意图](../../images/lovebrain-pipeline.svg) · [正式 APK Releases](https://github.com/bystery/LoveBrain/releases/latest)。

## F. 研究成果与 Android 发版的关系

上述数字分别来自历史 Arena 机制测试与当前用户价值测评，并不等同于最新版 APK 在用户手机上的完整端到端实验。`research-migration-20261112` 是研究公开分支；`closeout-20261106` 完成了 LIVE_AUTO 的产品接线，但尚未与 `main` 合并。这里清楚记录身份差异，不用为此把已成立的机制成绩从首页撤掉。

**收口规则：** 沿用现有分子分母、原始判决和输出，不引入新题、不重生成、不重复盲评。今后产品迭代另开版本，本页数字维持当前证据时间截面。
