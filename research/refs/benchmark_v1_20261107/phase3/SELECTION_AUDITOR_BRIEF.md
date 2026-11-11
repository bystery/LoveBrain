# SELECTION_AUDITOR_BRIEF · 批 2 独立审计简报（wave5_selection，2026-11-09）

> 适用：Phase 3 第 2 步 SELECTION 40 篇的 **Independent Auditor（角色 C）**。
> 你是独立验收：作者/标注者会话不得复用；你不修复，只判与登记。

## 0. 角色与输入边界

- 你读**冻结后**的材料：篇目五件（episode/reference/timeline/exposure/audit.md）、
  三份 V1 规范、TOPIC_RUBRIC_V1、本简报。你可以运行只读校验工具。
- 你不得读取：`logs/`、`candidates/`、B 线任何内容、其他审计人的本批报告、
  作者/标注者的会话产出（isolation_acks 除外）。同篇审计会话必须独立于其作者与标注者。
- 开工 ack：`refs/benchmark_v1_20261107/phase3/isolation_acks/<ACK_ID>.json`（role=independent_auditor）。

## 1. 审计清单（每篇逐项，证据落报告）

1. **故事质量**：连续性（不是独立问答）、关系/分寸变化、角色声音一致、无出题痕迹/元语；
2. **机械链**：JSON 三件可解析、轮数/case_id/ts 时序/may_decline 一致；
   `python -I tools/validate_story_v2.py`（按批配置）+ 名字唯一性
   （`python -I tools/benchmark_v1/check_name_uniqueness.py --wave wave5_selection`）；
3. **时间与算术**：clock 单调、日期-星期一致、金额/数量无算术错误（逐个验算）；
4. **Gold 质量**：quote 逐字回验（strict 0 失败）、原子状态族用法、required_this_turn
   与回指位对应、unknowns/forbidden 的"提及≠坐实"边界、疑问句不当坐实、
   T02 篇 t02_signal_gate 九项齐全且与对话面一致、**Gold 不规定唯一措辞**；
5. **专题匹配与判别力**：primary_topic 与实际内容一致（T02 信号梯度/反诱惑位、
   T03 交锋位、T04 时机、T06 指令可核验、T07 归属链、T08 干扰设计、T09 状态机、
   T10 单变量扰动）；弱 T02（信号藏私有 kb）直接 FAIL；
6. **近重复与谱系**：与同批其他篇骨架去重（复用 `dedupe_wave.py`）；孪生组同构性
   （唯一扰动位）与同分区；人名唯一性（池外名字 = FAIL）；
7. **评分缺陷预查**：会误导评分器的卡面（预设立场、过度模板化开头、万能句诱惑位）；
8. **实验可用性**：episode 与 runner 副本一致性（若有）。

## 2. 判定与产出

- 每篇 verdict：**PASS / FAIL（列缺陷与修复建议）/ PWN（发现可让候选白嫖的机制）**；
- 批报告：`refs/benchmark_v1_20261107/phase3/STORY_BATCH_REPORTS/AUDIT_S1_<段>.md`，
  含：逐篇判定表、缺陷清单（编号 B-S1-xx）、近重登记、T02 门逐篇核验表、
  算术/时间验算记录、隔离声明；
- FAIL 项交回修复（修复人=独立第 4 会话，ack role=repairer），修复后你复验并在报告
  追加"复验"节。**你不直接改题**。

## 3. 纪律

- 不给"放水 PASS"：宁可 FAIL 交修，不带病入池（Phase 2 先例：C1/C2/C3 均有 FAIL 定点修复）；
- 不为凑数降低判别力标准；不确定项写 UNCERTAIN 并说明，不猜；
- 全程零模型调用（确定性工具 + 通读）。
