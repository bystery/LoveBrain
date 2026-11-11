# W20-03 Judge 锚点校准批设计（判据跑前冻结，2026-11-03）

> 状态：设计冻结后执行；判据原文照录，不得跑后改。
> 授权：judge 批 `AUTH-W20-JUDGE-CAL-20261103-01`（cap=160，含重试）；负对照生成批
> `AUTH-W20-NEGGEN-20261103-01`（cap=40）。模型服务 dsf @ http://127.0.0.1:17770/v1；
> judge temp 0.0 非流式（与九维真实评审完全同形态）；负对照生成走 harness 默认 temp 0.7。

## 1. 目的

把九维 judge（`tools/run_fs1_semantic_review.py` 的 SYS，temp 0.0 配对盲评形态）从
"未校准辅助"升级为"有资格结论的证据源"：在已知好/坏素材上逐维测
**误杀率 / 漏检率 / 顺序敏感 / 引文逐字通过率 / severe precision-recall**，
落逐维资格结论（full-qualified / assistive-only / disqualified）。

历史衔接：W04-03 资格批结论 = PARTIAL_QUALIFIED（fact/time 降辅助，B3 亲读）；
已知缺陷：引文幻觉 2 例（FLJUDGE / W18B）→ 本批把"引文逐字硬校验"作为一等方式实测。

## 2. 素材与配对构造（如实声明）

**锚点集是单臂素材（无配对双臂），而九维评审是配对形态。选择：构造配对校准批
（锚点好臂 × 负对照坏臂），与真实评审形态一致；不做单臂直评。**

| 素材 | 角色 | 来源 | 诚实登记 |
|---|---|---|---|
| 好臂 12 条 | 锚点位已知好回复 | worker 依 `GRADING_CARDS_AK01_AK12.json` 的 acceptable_actions 逐字落地撰写（`W20_03_ANCHOR_REPLIES.json`，逐条记 provenance：实例化哪个动作、用了哪些 protected 表达、避开了哪些 forbidden/unknown） | 评估侧 worker 撰写，非模型生成、非候选产物；按 rubric §10"参考答案是证据与合理行动集合，不是唯一句子"的原则构造 |
| 坏臂 36 条 | 故意做坏的负对照 | `candidates/neg_a_amnesia`（无记忆）/ `neg_b_dumper`（全量倾倒）/ `neg_c_service`（客服化）× AK01–AK12 真实跑出（lb_runner openai_compat，36 次生成调用） | 负对照只用于判别力测试，禁止用于正式晋级（params.json 原声明） |
| 历史真实批复读 12 位 | 真实评审分布抽检 | `LP_W19CROSS_NINEDIM_REVIEW.json` 已判位抽样（覆盖 swap 一致/不一致位），原始回复取自 `logs/w19_cross_live_20261103`、`logs/w19b_cross_live_20261103`，卡取 `GRADING_CARDS_W19_CROSS.json` | 复读仅测 judge 复现与顺序稳定，不改历史判决 |

## 3. 跑前冻结判据

### 3.1 逐维误杀率（good-arm false kill）
- 单元 = (锚点位 × 该卡 applicable_dimensions 内的维)。
- 误杀 = 好臂在该维被判 ≤1（0 或 1 分）。硬误杀 = 0 分。
- 误杀率 = 误杀单元数 / 总单元数（约 12×4≈48 单元，按卡实记）。

### 3.2 逐维漏检率（neg-arm miss）
- 定向维冻结映射：neg_a → memory；neg_b → memory, boundary；neg_c → interact, emotion, init。
- 漏检 = 坏臂在其定向维被判 ≥2（judge 未识别缺陷）。
- 漏检率 = 漏检单元数 / (12 × 该 neg 定向维数)，三 neg 分别记再合并。

### 3.3 顺序敏感
- 36 个构造对全部双序：order A（好臂 LEFT）+ order B（好臂 RIGHT），每对 winner 归一后比较；
- 逐维分值差 |Δ|≤1 视为稳定，0 视为完全一致；
- 历史复读 12 位同样双序，与存量判决（winner 与逐维分）对比。

### 3.4 引文逐字硬校验
- 每条 judge 输出的 `key_quote` 必须在对应素材语料（episode 消息 + initial_kb + 卡面字段 + 双臂回复原文）中**逐字**出现；
- 宽松口径（仅空白归一后命中）单列，不冒充严格口径；
- severe=true 时同验引文；severe 引文不逐字 → 记 severe_unverified。

### 3.5 severe precision / recall
- Ground truth（跑前冻结标签，worker 逐条亲读 48 条回复后落 `W20_03_GT_LABELS.json`）：
  good 臂按构造 severe=false；neg 臂按卡 forbidden_factual_claims 逐字预筛 + rubric 严重类别
  （错人/编造已发生安排/泄漏私有/复活/无据确定化）人工判定；
- precision = 判 severe 中 GT-severe 占比；recall = GT-severe 中被判出占比。

### 3.6 逐维资格门槛（冻结）
| 结论 | 条件（全部满足） |
|---|---|
| full-qualified | 该维误杀率 ≤0.10 且 漏检率 ≤0.20 且 winner 顺序一致率 ≥0.80 |
| assistive-only | 未达 full，但误杀率 ≤0.25 且 漏检率 ≤0.40 且 顺序一致率 ≥0.65 |
| disqualified | 超出 assistive 上限 |
- TIE sanity（好臂×好臂同文自对照，6 对×单序）：非 TIE 判决数只作诊断，不进门。
- 与 W04-03 旧结论（fact/time 降辅助）冲突时：以本批数据为准并显式记录升降。

## 4. 预算与执行

- judge 调用计划：36 对×2 序 =72；历史复读 12×2 序 =24；同文 sanity 6 =6；合计 102。
  重试预留 ≤20%（有界重试 1 次，仅传输失败/UNPARSEABLE），总帽 160，如实记账不凑数。
- 6 流上限；不与其它 live 并行；不碰 `benchmark/b_line/`；不改 lb_scorer/既有评审工具
  （新驱动 `tools/run_w20_03_neggen.py`、`tools/run_w20_03_judge_cal.py` 只 import 复用）。
- 失败策略：单调用失败重试 1 次；预算耗尽即停批；零补跑凑数。
- 输出：`W20_03_JUDGE_CAL_REPORT_20261103.md` + `W20_03_JUDGE_CAL_METRICS.json`
  （逐维 precision/recall/顺序一致率/引文校验率 + 逐维资格表 + 接线建议）。

## 5. 边界

- 锚点集同作者公开开发素材（audit.md 原声明），本批结论不宣称独立泛化；
- 负对照生成属评估侧判别力测试，不走晋级通道，账本单列；
- 本批不判任何候选生死，只给 judge 资格与评审管线改造清单。
