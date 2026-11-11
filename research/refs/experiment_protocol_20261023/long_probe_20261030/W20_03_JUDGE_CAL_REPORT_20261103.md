# W20-03 Judge 锚点校准报告（2026-11-03）

> 设计（跑前冻结）：`W20_03_JUDGE_CAL_DESIGN_20261103.md`；GT 标签：`W20_03_GT_LABELS.json`；
> 原始输出：`W20_03_JUDGE_CAL_RAW.jsonl`（102 判定）；指标：`W20_03_JUDGE_CAL_METRICS.json`。
> 授权与账本：judge 批 `AUTH-W20-JUDGE-CAL-20261103-01`（cap 160，**实用 114** = 计划 102 + 有界重试 12，0 失败）；
> 负对照生成批 `AUTH-W20-NEGGEN-20261103-01`（cap 40，**实用 36**，与 calls.json 实际模型调用逐一对账）。
> 形态选择（如实声明）：锚点集是单臂素材，九维评审是配对形态 → 构造校准对（锚点好臂 × 负对照坏臂）
> 与真实评审同构（复用 `run_fs1_semantic_review.py` 的 SYS，dsf temp 0.0 非流式，盲序）。未做单臂直评。

## 1. 素材与执行

- 好臂 12 条：worker 依 `GRADING_CARDS_AK01_AK12.json` acceptable_actions 逐字落地
  （`W20_03_ANCHOR_REPLIES.json`，逐条 provenance）；按构造 severe=false。
- 坏臂 36 条：`neg_a_amnesia`/`neg_b_dumper`/`neg_c_service` × AK01–AK12 经 lb_runner
  openai_compat 真跑（36 次 dsf 调用，usage 记 api_usage）。**补登事实**：三个负对照缺
  `assets/`（phase05 时代空资产可跑，现 runner fail-fast 拒绝），已补 `round_0_baseline/assets`
  逐字副本并在候选内落 `ASSETS_PROVENANCE.md`；缺陷在机制层，资产层三对照一致，不改判别设计。
- 历史复读 12 位：`LP_W19CROSS_NINEDIM_REVIEW.json` 已判位抽样（6 swap 位 + 6 main 位，
  E157/E164/E183/LRE117/LRE121/LRE138/LRE146），原始回复取自 w19/w19b live 批目录。
- judge 调用：36 对×双序 72 + 历史 12×双序 24 + 同文 sanity 6 = 102；并发 6；1 次有界重试。
  未解析 2 条（`AK06×neg_b` B 序、`LRE121_T08` B 序无 winner），如实计入失败不补跑。

## 2. 核心结果

### 2.1 逐维资格表（冻结判据：误杀≤10% 且 漏检≤20% 且 顺序一致率≥80% = full）

| 维 | 误杀率（好臂≤1分） | 漏检率（坏臂≥2分） | 逐维双序完全一致 | 结论 | 操作地位 |
|---|---|---|---|---|---|
| goal 目标 | 0/58=0.0 | 未测（无定向负对照） | 29/35=0.829 | **full-qualified** | 可进九维辅助门 |
| fact 事实 | 0/6=0.0 | 未测 | 26/35=0.743 | full-qualified（**低证据**，6 单元） | 辅助；证据薄，涉事实判死仍 B3 |
| attrib 归属 | 未测 | 未测 | 16/35=0.457 | **not-testable**（卡面无此维、负对照不定向） | 维持 assistive-only（W04-03 口径） |
| time 时间状态 | 2/46=0.043 | 未测 | 25/35=0.714 | **full-qualified** | 可进九维辅助门 |
| boundary 边界 | 1/46=0.022 | 19/22=0.864 | 25/35=0.714 | 机械判 disqualified | **不得单独判死**；见 §2.3 归因 |
| memory 记忆 | 0/12=0.0 | 28/46=0.609 | 18/35=0.514 | 机械判 disqualified | **不得单独判死**；见 §2.3 归因 |
| interact 互动 | 0/64=0.0 | 0/24=0.0 | 30/35=0.857 | **full-qualified** | 可进九维辅助门（含客服腔识别） |
| init 主动 | 0/6=0.0 | 0/24=0.0 | 25/35=0.714 | full-qualified（低证据） | 可进九维辅助门 |
| emotion 情绪关系 | 0/42=0.0 | 0/24=0.0 | 26/35=0.743 | **full-qualified** | 可进九维辅助门 |

- 全局 winner 双序一致率（构造对）31/35=0.886；同文 sanity 6/6=TIE（不虚构差异）。
- fact/attrib 与 W04-03 旧结论的关系：fact 在本素材上 0 误杀但**漏检不可测**，证据不足以
  翻案，维持 B3 亲读；attrib 本素材完全测不到，维持辅助。

### 2.2 severe 旗 P/R（主口径 = A 序；对照 GT 冻结标签）

- **precision 0.062（1 TP / 15 FP）**，recall 0.5（2 个 GT 阳性抓住 1 个）。B 序类似（0.071/0.5）。
- GT 阳性仅 2：`neg_b×AK11`（"手术时间我改成十点的"=编造已发生安排，**被判出**）、
  `neg_a×AK05`（主动要求逐段点评，卡面禁区，**未判出**——judge 按五类严重定义不改判，合理）。
- 15 个 FP 全部落在**坏臂**（neg_c 客服模板 10 个、neg_b/neg_a 各 2–3 个）：judge 把
  "模板化/未承接/未收尾"**升格记为 severe**（note 原文可见），超出 rubric 五类严重定义。
- **好臂 severe 误杀 = 0**（双序共 70 个好臂侧旗全 false）。
- 结论：severe 旗**不能作为判死依据**（P=0.06），但作为"坏回复预警器"零误伤好臂——
  用法只能是 **severe=true → 触发 B3 亲读复核**，永不自动 veto。

### 2.3 漏检归因（诚实登记，不归咎 judge 的部分要说明）

- `boundary` 漏检 0.864、`memory` 漏检 0.609 的主因是**负对照器械效度不足**：
  neg_b 的"全量倾倒"缺陷注入在提示侧（scene_chain），单位置素材上**不落到回复表层**；
  neg_a 的"无记忆"在单位置锚点上无劣势（记忆原子全部在本轮窗口内，judge 判的是回复
  对可见上下文的使用）。judge 对**回复表层**缺陷（neg_c 客服化）检测率 24/24=100%。
- 推论：memory/boundary 两维在本素材上**"未证明合格"**（机械 disqualified），
  实操维持辅助 + B3；若要真测这两维，需多轮累积素材让负对照缺陷显形（进 backlog）。

### 2.4 引文逐字硬校验（已知缺陷 FLJUDGE/W18B 的系统化实测）

- 101 条有效 key_quote：**逐字命中 88（87.1%）**；宽松（空白归一）同为 88；
  **硬幻觉 12 条（11.9%）**；空引文 1 条；片段平均覆盖 0.958——幻觉形态是
  **"真片段拼装改写"**（每段可在语料中找到、整句不存在），与 FLJUDGE/W18B 同族。
- 结论：**引文逐字硬校验必须接线为评审管线必选项**（>0 幻觉率即触发）：
  ①judge 输出落盘后逐条跑逐字校验，失败引文标记 `quote_unverified`；
  ②severe=true 且引文不过逐字校验 → severe 记 `unverified`，强制 B3；
  ③逐字校验是确定性代码，零调用成本，接线点在评审驱动落盘后、指标汇总前。

### 2.5 历史真实批复读（12 位，含偏置声明：6/12 为 swap 子集位，偏向不稳定位）

- 与存量判决 winner 复现 6/12=50%；复读双序一致 7/12=0.583；
- **逐维分数 ±1 一致率 0.972**（65/67 维次）、完全一致 0.565；
- 判读：judge 的**逐维分数读数较稳**（±1 内几乎恒定），**winner 单值判决噪声大**（尤其
  接近局时 TIE/翻转频繁）。与 §2.1 一致：九维分可用作辅助读数，winner 不可单独定案。

## 3. 九维逐维资格总结（进判决 / B3 亲读清单）

- **可进判决（九维辅助门读数）**：goal、time、interact、emotion（+init/fact 低证据辅助）。
- **必须 B3 亲读**：severe 判定（全部）；boundary、memory 的判死级结论；fact 的判死级结论；
  attrib（无测试面，维持 assistive-only）；winner 级别的翻案/定案。
- **judge 单方面判死仍然全面禁止**——与本批 severe P=0.062、winner 复现 50% 的实测一致，
  rubric §3 的收紧在数据上成立。

## 4. 给评审管线的接线建议清单（按优先级）

1. **引文逐字硬校验接线**（必做，确定性，零成本）：落盘后逐条校验 key_quote；失败记
   `quote_unverified`；severe 引文不过校验 → severe=unverified 强制 B3。当前管线缺此步，
   幻觉率 11.9% 会直接污染 key_quote 证据链。
2. **severe 旗降级为"复核触发器"**：severe=true 只触发 B3 亲读，不进任何自动门禁
   （本批 P=0.062，且 FP 形态=把互动失败升格为严重错误）。
3. **winner 单值不进门**：门禁读数用逐维分（±1 稳定），winner/TIE 只作展示与抽样复核定位。
4. **维度标签对齐**：卡面 applicable_dimensions（time_state/interaction/…）与 judge 维名
   （time/interact/…）需固定映射表进评审驱动，避免逐批手译。
5. **memory/boundary 判别素材补建**（backlog）：多轮累积 + 缺陷落回复表层的负对照
   （如把倾倒文本写进回复、无记忆导致硬约束违反），否则这两维永远测不出资格。
6. **severe GT 扩容**：单位置锚点天然缺 severe 阳性（2/48），后续用 F3/B 线已知 severe
   位扩充校准阳性池（不碰 b_line 封存）。

## 5. 边界声明

- 锚点/负对照均为公开开发素材（audit.md 原声明），结论不宣称独立泛化；
- 好臂为 worker 撰写参考回复（非模型生成），检验的是"judge 对已知好/坏的判别力"，
  不是候选质量；负对照禁入晋级（params.json 原声明）；
- 未修改 lb_scorer / 既有评审工具；新驱动 `tools/run_w20_03_neggen.py`、
  `tools/run_w20_03_judge_cal.py`、`tools/analyze_w20_03_judge_cal.py` 只 import 复用；
- 未读取 `benchmark/b_line/`；全部调用入账（114+36），无凑数、无补跑。
