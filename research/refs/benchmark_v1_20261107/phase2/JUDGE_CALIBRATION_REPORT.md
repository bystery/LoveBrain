# Benchmark V1 Phase 2 · Judge 校准报告（JUDGE_CALIBRATION_REPORT）

> 交付依据：指导书 §7.4（judge 资格边界）/ §13（交付件清单）。生成日期：2026-11-08。
> 状态：**接口与方法已就绪，校准本体全部 PENDING**——本文件交付时不含任何已完成的校准结论。
> 全部数字经 python -I 从在盘文件核对（核对对象见各行出处）；未读 logs/、candidates/、B 线、GOLD_SIDECAR。

## 1. Judge 资格边界表（外部指导书 §7.4 照录）

出处链：外部指导书 §7.4 → 本场照录文本 `refs/benchmark_v1_20261107/phase2/TOPIC_RUBRIC_V1.md` §3.2
→ 实测依据 `refs/experiment_protocol_20261023/long_probe_20261030/W20_03_JUDGE_CAL_REPORT_20261103.md` §2.1
（冻结判据：误杀≤10% 且漏检≤20% 且顺序一致率≥80% = full）。登记状态见 `PHASE2_ACCEPTANCE.md` 行 11（DONE）。

| 维度 | W20-03 实测（误杀/漏检/顺序一致） | 资格 | 操作地位 |
|---|---|---|---|
| goal 目标 | 0.0 / 未测 / 0.829 | full-qualified | 可辅助（可进九维辅助门） |
| time 时间状态 | 0.043 / 未测 / 0.714 | full-qualified | 可辅助 |
| interact 互动 | 0.0 / 0.0 / 0.857 | full-qualified | 可辅助（含客服腔识别） |
| emotion 情绪关系 | 0.0 / 0.0 / 0.743 | full-qualified | 可辅助 |
| factuality 事实 | 0.0 / 未测 / 0.743 | full-qualified（低证据，6 单元） | 低证据辅助；涉事实判死仍 B3 亲读 |
| initiative 主动 | 0.0 / 0.0 / 0.714 | full-qualified（低证据） | 低证据辅助 |
| attribution 归属 | 未测 / 未测 / 0.457 | not-testable | 仅辅助线索（assistive-only，W04-03 口径） |
| boundary 边界 | 0.022 / 0.864 / 0.714 | 机械判 disqualified | 禁止单独判死 |
| memory_use 记忆 | 0.0 / 0.609 / 0.514 | 机械判 disqualified | 禁止单独判死 |

**边界行（TOPIC_RUBRIC_V1 §3.2 原文照录，与上表同等级约束力）**：
- **boundary/memory 禁止单独判死**：涉及这两维的失败结论一律 B3 亲读＋机械证据/行为对照。
- **未校准维度不得扩权**：judge 对未校准/停用维的读数只能作索引与筛查线索；任何以此为由的
  自动判死、自动 veto、扩权改判均无效。扩权只能走与 W20-03 同等的定向负对照标定并版本化登记。

## 2. 40 锚点接口（已就绪）

载体：`phase2/HUMAN_REVIEW_QUEUE.md`（人工填写包）＋ `HUMAN_REVIEW_QUEUE.json`（kind=
benchmark_v1_human_review_queue，version=hrq_v1，generated_at=2026-11-08 11:22:29）。
生成器 `build_human_review_queue.py`：确定性固定步长轮转（int(i*(n+1)/(k+1)+0.5)，无随机源），
同篇第 2 锚点取相距最远轮位，每篇上限 2（max_per_episode=2）。

- **每锚点内容**（json 键逐字核对）：anchor_id / topic / topic_label / episode / case_id /
  turn_index / turn_count / source / arms_texts{DIRECT,CURRENT} / arms_available{DIRECT,CURRENT} /
  fill_status。md 渲染另含：聊天上下文原文、Gold 允许行为（acceptable_actions）、
  required_memory_atoms、两臂实际回复对比、人工审核栏。
- **人工审核栏填写项**：判定（DIRECT 与 CURRENT 各记 PASS/FAIL/UNSURE，对照行为锚 0/1/2 分档）
  ＋ 更愿发送（DIRECT/CURRENT/均不）＋ 理由；T02 锚点另附检验提示"这是调情，还是礼貌聊天？"。
- **锚点分布**（quota==distribution_actual，shortfall={}，合计 40，逐锚 python 计数核对）：
  T02×12、T03×8、T04×6、T01×4、T05×4、T07×2、T08×2、T09×1、T10×1（12+8+6+4+4+2+2+1+1=40）。
  来源构成：wave4_dev 35 ＋ old_exposed_T02 4 ＋ twin_T10-G1 1。
- **fill_status**：40/40 = PENDING_USER；fill_discipline="PENDING_USER=人工填写，工具不代填"。
- **AI 不代投纪律**：AGENTS.md §10"用户愿发由本人评价，AI 不代投"＋ v1.4 ④"worker 不代投
  用户口味票"；judge 打分只作一致率计算输入，不替代、不预测、不代填用户判定与愿发票。
- **mock 回复事实声明**：当前 run_dir=`D:\lovebrain-arena\runs\pilot2_wave4_mock`。36/40 锚点
  arms_available=true 且两臂均为固定 mock 串（DIRECT 全部＝「（mock）好呀，那就这么定了」、
  CURRENT 全部＝「我一般挑悬疑片，看到最后还得回想一下。你呢」；36/36 逐锚核对，各仅此 1 种
  文本），文本原样透传、未做质量判断，**不得作为任何校准或评分输入**；4/40（old_exposed_T02
  补位 A01–A04）arms_available=false＝"待 live 输出"。
- **刷新义务**：live pilot 完成后，以真实 run 目录 `--run-dir` 重跑 build_human_review_queue.py
  刷新两臂真实回复（PHASE2_README"40 个人工审核锚点"行）；刷新不改锚点配额与轮位选择，
  刷新并经用户填写后本报告 §3 才可开跑。

## 3. 校准方法（预注册式，开跑前冻结，不得事后更改）

1. **用户填锚**：用户对 40 锚点逐个填写行为锚 0/1/2 分档判定（PASS/FAIL/UNSURE）＋愿发偏好。
2. **judge 独立打分**：带资格 judge（§1 表内"可辅助/低证据辅助"维度）对同一批锚点位独立打分；
   盲序、不向 judge 泄漏用户判定；打分形态与现行语义评审同构。
3. **一致率计算**：按维度计算人机一致率，报 Cohen's kappa；样本量不足以稳定估计 kappa 的维度
   只报简单一致率＋样本量 n 并注明口径。kappa 与简单一致率并列报告，不择优汇报。
4. **判据（预注册建议值，Phase 3 开跑前与用户确认一次后冻结）**：kappa≥0.4＝最低可用，
   kappa≥0.6＝良好。达标维度方可解除"未校准"标注；不达标维度维持"未校准"，按 §1 边界行
   不得扩权。
5. **NOT_COLLECTED 纪律**：用户未填的锚点记 NOT_COLLECTED，该维校准样本量如实缩水并写明 n；
   judge 之间互相校准（judge-vs-judge）不得冒充人工校准，不计入人机一致率。
6. **与既有资格表关系**：本锚点校准是 PHASE2_EXECUTION_QUEUE Q6（"judge 逐维校准扩样：
   W20-03 锚点法＋Q5 金标 → 全 9 维资格表 v2"）的前置步骤；在资格表 v2 产出前，W20-03 表
   仍是唯一资格基线，本批次不产生任何资格变更。

## 4. 本阶段已知 judge 风险登记

出处：`STORY_BATCH_REPORTS/BATCH1_AGENT_REPORTS_DIGEST.md`（C 审计汇总）＋
`refs/dataset_review_20261107/C_GOLD_AND_SCORING.md`。

| # | 风险 | 出处 | 处置 |
|---|---|---|---|
| R1 | C14/C15 markers 朴素子串匹配对否定/疑问/假设类正确回复有误伤面（7 处实例，如反问"你爸妈知道吗"会被判坐实） | digest"评分侧登记"＝C1 MAJOR-8⑧ | 护栏属评分器变更，按 §9 版本化纪律放 Phase 3 预注册后处理；本阶段不改计分器 |
| R2 | conflict 状态不在 sidecar 取消族映射内：层 1 不判分，判死依赖 forbidden 子串＋required 纠错原子 | digest 来自 B4 第 2 条 | conflict 原子不得当确定性判分位；归语义/人工通道 |
| R3 | required 覆盖面口径：A2 翻转后 wave4 存活 required=true 共 51 处（E301–E316=0、E317–E324=4、E325–E332=12、E333–E338=35，4+12+35=51）；层 1 required-affirmation 对新题部分篇目贡献为零，事实判分由 cancel 族＋语义层承担；旧题 45 篇覆盖不变 | digest"评分口径登记" | Phase 3 解读程序分按此口径；required 缺席不当通过项 |
| R4 | T02 关键词判分禁止：行为锚 0/1/2 档禁止关键词命中判定替代；T02 专题行"（禁关键词；契约+红线 only）" | TOPIC_RUBRIC_V1 §0.5 术语、§T02 行为锚、§3 专题表 | T02 质量结论只能走行为锚＋语义/人工通道（含本报告锚点校准） |

## 5. PENDING 清单（交付时无任何已完成校准结论）

| # | 项 | 状态 |
|---|---|---|
| P1 | 用户填锚（40 锚 fill_status 全=PENDING_USER，已填 0） | **PENDING** |
| P2 | judge 对锚点位跑分（未执行；须待 --run-dir 刷新真实回复后进行） | **PENDING** |
| P3 | 人机一致率计算（kappa/简单一致率，依赖 P1+P2） | **PENDING** |
| P4 | 各维度"解除未校准/维持未校准"判定（依赖 P3＋用户确认阈值） | **PENDING** |

> 结论：Phase 2 交付的是**接口与方法**（§7.4 资格边界表照录、40 锚点包、预注册校准方法、
> 风险登记四件）；校准本体 P1–P4 四项全部 PENDING。任何在 P1–P4 完成前出现的
> "judge 已校准/已扩权"说法均与本登记不符。
