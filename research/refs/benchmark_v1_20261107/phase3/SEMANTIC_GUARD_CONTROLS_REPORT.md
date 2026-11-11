# SEMANTIC_GUARD_CONTROLS_REPORT · 语义守卫控制集报告（Phase 3 第 5 步执行件 A，2026-11-09）

> 指导书 P3-3.2（修复 Gold 语义误伤：C14/C15 否定/疑问/假设误判）执行件。
> 控制集：`SEMANTIC_GUARD_CONTROLS_V1.json`（105 例）｜评估器：`universal_scorer.py --eval-controls`
> ｜结果：`_guard_eval_controls_report.json`。构建器 `build_semantic_controls.py`（seed=20261109，可复算）。

## 1. 控制集构成（真值表由类别定义决定，非由守卫结果反推）

| 类别 | n | 期望判决 | 真值依据 | 结果 |
|---|---|---|---|---|
| statement_affirm（真坐实） | 15 | affirm | 取消族原子被重新坐实 → 应记违例 | 15/15 ✅ |
| negation（否定陈述） | 15 | guard | "没到X那一步" ≠ 坐实 X | 15/15 ✅ |
| question（疑问句） | 15 | guard | "X？定了吗？" ≠ 坐实 X（指导书原例类） | 15/15 ✅ |
| conditional（假设/条件） | 15 | guard | "要是X就麻烦了" ≠ 坐实 X | 15/15 ✅ |
| quote（转述引用） | 15 | guard | 「X」复述 ≠ 第一人称坐实 | 15/15 ✅ |
| past_mention（取消事项过去式提及） | 15 | guard | "上次取消的X我还记得" = 提及≠复活 | 15/15 ✅ |
| required_hit（required 原子正用） | 5 | affirm | 指导书 P3-4.4 意图达成证据 | 5/5 ✅ |
| required_miss（required 未用） | 5 | none | 漏检登记 | 5/5 ✅ |
| rhetorical（反问式复现，已知限制类） | 5 | guard | 保守取向：不判复活，交层 2 | 5/5 ✅（见 §3） |
| **合计** | **105** | | | **accuracy 1.000** |

- 原子来源：GOLD_SIDECAR 全库真实原子（636 个带 markers 的取消族 + 12 个带 markers 的
  required 族，随机 seed=20261109 抽样），回复文本按 6 类模板合成——**markers 是真实的，
  句子是模板的**；批 2 起新原子全部强制带 markers（见下 §4），届时可用真实回复扩容控制集。

## 2. 顺带发现并修复的根因（比控制集本身更重要）

**全库 marker 通道空转**：7,953 原子中 7,287（92%）无 affirm_markers；仅有的 666 个
（stageD）是嵌套组结构，而 `gold_diag._markers` 只取顶层字符串 → **一个都取不到**。
判分实际只剩 value 子串通道（value 是长句，回复几乎不可能逐字复现）→ 这解释了
Pilot `diag_required_hit_rate 0/46` 与大量 N/A。修复：
1. `universal_scorer.flatten_markers` 展平嵌套组（组间并集，any 语义与 gold_diag 一致），
   单元测试覆盖（嵌套/空/平铺）；
2. **批 2 标注简报强制每个原子带 affirm_markers**（嵌套组 2–4 组 × 1–3 备选，2–12 字）——
   marker 通道从 wave5 起恢复功能；旧波保持原样（可复现），其 N/A 继续诚实登记。

## 3. 已知限制（诚实登记）

1. **反问式复现是漏检方向**："不是说周六爬山吗，走啊现在"含否定词"不是"→ 被归为
   negated_mention 而不判复活——**保守偏置**（宁漏检不误杀，符合"未校准不判死"）；
   该类交层 2 judge + B3 亲读兜底，控制集如实记录其通过原因是保守分类。
2. 守卫是词面规则，不覆盖隐喻/指代（"那个事还办吗"无 marker 字面）→ 漏检方向；
3. required 命中保持"marker/value 出现即命中"宽口径 + guard_events 线索，"正确使用记忆"
   的严肃测量在层 2（Judge 资格复核后）与人工；
4. 控制集文本为模板合成，覆盖面有限；SELECTION/HOLDOUT 真实回复出来后按同 seed 扩容
   复评（本文件升级 v2），不把 1.000 外推为真实语料上的 1.000。

## 4. 对评分定位的落地含义

- 通用层 cancel_affirm 违例（语义守卫后）= 客观层可计数的**严重事实违例**信号，
  可作为四组对照指标（配对统计按 episode 聚类）；
- required 命中/漏检恢复为**可用指标**（新波有 markers 后），有效分母 = 有 required
  原子的轮位，N/A 不计通过也不计失败（P3-4.4 纪律）；
- lb_scorer（产品契约层）继续只服务 harness 组，不参与跨组质量排名。
