# PHASE3_PREREGISTRATION · 第三阶段预注册（Phase 2 交付件）

> 目的：Phase 3 开跑前冻结判据，避免"先跑后定尺"。本文件在 Phase 2 数据冻结后由
> Worker 与用户确认一次，之后不得修改；任何修改=新版本+勘误说明。

## 1. 研究问题（继承指导书，不改）

- RQ1：如何构造能评估聊天辅助系统综合能力的数据集（240 篇/十专题/原子断言/三分区）？
- RQ2：LoveBrain Harness 相对 Direct/CoT/历史 Harness，在最终回复质量上提升了哪些能力？

## 2. 实验设计（冻结）

- 四组：DIRECT / COT / LEGACY_COMPAT_R9（经输入适配的历史版本，身份与哈希见
  ARM_REGISTRY） / CURRENT（双域冠军按轮数分流）；`reference_product_v1` 为可选第五组
  （保真边界见 BASELINE_PARITY，是否入正式论文在 Phase 3 开跑前决定）。
- 数据集：Benchmark V1 冻结版（DEV 80 / SELECTION 40 / HOLDOUT 120；HOLDOUT 只在
  最终验收跑一次）。
- 模型服务：USTC `deepseek-flash-2`，temperature 0.7，stream false；四组同服务端配置；
  并发如实登记；cap 按批次授权。

## 3. 主判据（预注册，跑前冻结）

| 判据 | 定义 | 层级 |
|---|---|---|
| G1 客观断言 | gold_diag（取消族坐实违例数、required 命中率），按故事聚类汇总 | 客观 |
| G2 契约可靠性 | parse_ok/contract_ok/空回复率/有效回复率 | 客观 |
| G3 语义评审 | judge 九维（资格表限定；boundary/memory 禁止单独判死）+ 引文硬校验 + severe 位 B3 亲读 | 语义 |
| G4 行为锚分级 | T02 调情 0/1/2 档、T03 深聊四子类判据、T04 时机分寸——由带资格的评审按 rubric 判 | 语义 |
| G5 顺序交换 | 关键对位 swap 复跑，翻转率超门 → 不得宣布单方向结论 | 方法 |
| G6 用户偏好 | 用户盲评（愿发选择）；未收集 = NOT_COLLECTED，不冒充 | 人工 |
| G7 资源 | usage tokens、p50/p95 时延、调用数（含内部附加） | 客观 |

- 主比较单位：每组首选回复（normalize_v1 固定规则，不挑备选）。
- 统计口径：以 Episode 为聚类单位处理多轮相关性；报告按专题分层；置信区间方法在
  数据冻结后、跑批前登记（Bootstrap by episode，B=2000，预注册于此）。
- 结论分级：跨分区一致（强）/ 仅 DEV（弱）/ 仅 judge 无亲读（线索级）；程序分 ≠ 回复质量。

## 4. 停止与防止条件

- HOLDOUT 一次性消费（only_once）；任何按题回修=当轮变异作废。
- 连续 3 批无实际优势 → 换方向，不重试到赢（继承 AGENTS §8）。
- 预算：每批授权 cap 登记制；不自动扩额。

## 5. Phase 3 执行队列（入口）

1. 数据集冻结确认（Phase 2 验收通过后）；
2. 四组 mock/capture 全绿 → SELECTION 40 篇四组对照（cap 按登记）→ 分析；
3. DEV 回归确认无机制回退；
4. HOLDOUT 一次性四组对照（only_once）；
5. 专题×组差异分析 + 机制归因（消融按需、预注册）；
6. 论文图表 + 复现材料 + GitHub 归档。
