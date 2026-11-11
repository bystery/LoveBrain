# EXPERIMENT_PROTOCOL_FROZEN_V1 · 正式实验协议（Phase 3 第 6 步）

> 冻结状态：**DRAFT-FOR-FREEZE**（本稿按指导书 §P3-4 全文预写；"冻结生效"章在第 3 步
> 数据冻结完成时盖时间戳与数据哈希，随后不得修改——修改=新版本+勘误，且不得在看到
> HOLDOUT 结果后改任何规则）。

## P3-4.1 研究问题

- **RQ1**：Benchmark V1 如何构造、覆盖什么任务、采用什么 Gold、如何控制数据质量？
- **RQ2**：CURRENT 相较 DIRECT、COT、LEGACY_COMPAT_R9，在什么任务上有可验证的质量提升？
- **RQ3**：这些增益需要多少额外 Token、调用次数和响应时延？

## P3-4.2 研究假设（预注册，非预先宣布的结果）

- **H1**：CURRENT 相对 DIRECT 在复杂多轮、状态和关系语境任务上可能具有优势；
- **H2**：CURRENT 相对 COT 可能具有结构性增益；
- **H3**：CURRENT 相对 LEGACY_COMPAT_R9 可能改善部分能力；
- **H4**：CURRENT 的额外计算成本可能显著增加。

## P3-4.3 主指标：实际用户盲评愿发偏好

- 同轮位两条回复成对比较；三组主比较：CURRENT vs DIRECT / COT / LEGACY_COMPAT_R9；
- 允许选择：A更好 / B更好 / 两条都不错 / 两条都不愿发送 / 无法判断；
  "都不错"与"都不愿发送"不得混成同一质量结论；
- **没有足够实际人工评分时本指标保持未完成（NOT_COLLECTED），不得用 AI Judge 冒充**；
- 单一评审者 = 如实声明"反映该用户偏好"，不外推。

## P3-4.4 辅助指标（通用层四组同权；产品契约层单列）

- 客观：事实错误率（cancel_affirm 违例率，语义守卫 v1 后）、人物归属/状态变化正确率
  （judge 辅助读数 + B3 亲读）、取消事项复活率（=cancel_affirm）、required 命中率
  （有效分母=有 required 原子的轮位；N/A 不计通过也不计失败）、合法不回复正确率、
  用户意图达成率（judge/人工）、T02 0/1/2 行为锚、T03 深聊行为锚、T04 推进分寸；
- 工程：API 成功率、调用次数、Tokens、P50/P95 时延、成本（真实 usage）；
- 产品契约层（LEGACY/CURRENT 专用，单列不参与跨组排名）：lb_scorer veto/score10。

## P3-4.5 统计规则（实现=tools/benchmark_v1/stats_phase3.py v1）

- 以 **Episode 为主要聚类单位**；反事实孪生以 **lineage_group 为更高层相关单位**
  （bootstrap 按组重采样，同组同进退）；
- **Bootstrap 重采样 B=2000，固定种子 20261109**；95% CI（percentile 法）；
- 主要三组比较采用预先固定的 **Holm-Bonferroni** 多重比较校正；
  bootstrap p = 2·min(P(diff>0), P(diff<0))（双侧）；
- 专题结果分别报告；样本量不足（有效配对位 <10）只报描述性结果和置信区间，
  不强行宣布显著优势；
- 多轮相关样本不当作完全独立样本；请求位嵌套于故事，不夸大把握。

## P3-4.6 固定条件

- Provider: USTC OpenAI-compatible；Base URL: https://api.llm.ustc.edu.cn/v1；
  Model: deepseek-flash-2；Temperature 0.7；Stream false；四组同服务端配置；
- 四组身份：DIRECT / COT / LEGACY_COMPAT_R9（经输入适配的历史版本，**不得称为未改动原版**）
  / CURRENT（≤24=window_expand_b_norecap_guard，>24=window_expand_b_norecap_uc）；
  身份哈希在 ARM_REGISTRY_FROZEN.json（第 3 步数据冻结时定稿）；
- 主比较只用每组**首选回复**（normalize_v1 固定规则，不事后挑备选）；
- 输入公平：四组获得相同原始任务信息（聊天历史/意图/画像）；harness 允许更复杂的
  组织与检索（研究对象本身），其额外计算成本如实记录；
- 预算：全局共享 reserve 账本（GLOBAL_ATTEMPT_LEDGER.jsonl）；SELECTION cap=**2250**、
  HOLDOUT cap=**7000**；到限停批不扩额；质量失败不重跑；基础设施重试按授权登记；
- once-only：HOLDOUT 每篇消费一次；跑后登记不可逆 EXPOSED；
  任何按题回修/改评分器/换 prompt = 当轮作废。

## P3-4.7 评分口径（详见 SCORER_PARITY_REPORT / SEMANTIC_GUARD_CONTROLS_REPORT / JUDGE_CALIBRATION_V2）

- 通用层（四组同权）：normalize_v1 抽取 + 语义守卫 v1 的 gold 断言检查 + 严重错误；
- 产品契约层（适用组单列）：lb_scorer（LEGACY/CURRENT）/ normalize light 契约（DIRECT/COT）；
- judge 九维：仅 interact=full-qualified 可进辅助门；其余 assistive（禁判死）；
  boundary/memory 禁止单独判死；B3 亲读独立登记；
- 主指标用户盲评 NOT_COLLECTED 前，一切语义结论=描述性+CI，不宣布显著优势。

## 冻结生效章（2026-11-10）

本协议自此刻起冻结。

- **FROZEN_AT**: 2026-11-10T23:59:00Z
- **DATA_SHA256**: `ed63b6b91d8b3bda2a28afde35900ae1cb114bfe6c304fd0449fbef1a7ef79c3`
  - 来源：`DATASET_V1_FROZEN_MANIFEST.json` 的 `layer_3_dataset_hash`
  - 覆盖：240 篇（DEV 80 + SELECTION 40 + HOLDOUT 120）× 4 文件三层哈希 + 分配表 + 评分器 + ARM 注册表
- **ARMS_SHA256**: 见 manifest `component_hashes.arm_registry_frozen`
- **SCORER_SHA256**: 见 manifest `component_hashes.universal_scorer`
- **ALLOCATION_SHA256**: 见 manifest `component_hashes.allocation_csv`
- **FROZEN_BY**: Worker（Phase 3 第 3 步冻结，第 6 步协议盖章）

此后不得修改题目、评分规则、ARM 定义。跑后修改 = 当轮作废。
HOLDOUT 在第 8 步一次性运行前保持零消费（exposure.experiments_used = []）。
