# JUDGE_CALIBRATION_V2 · Judge 资格复核与校准路径（Phase 3 第 5 步，2026-11-09）

> 指导书 P3-3.3（必须重新判断 Judge 资格）+ P3-3.4（用户 40 锚校准）执行件。
> 复核工具：`recompute_judge_quals.py`（数字照录 W20-03 实测，判据=冻结原文，
> 结果 `JUDGE_QUALS_V2.json` 可复算）；锚点包：`HUMAN_REVIEW_QUEUE_PHASE3.md/.json`。

## 1. 资格复核结论（诚实重标）

冻结判据：**误杀≤10% 且 漏检≤20% 且 顺序一致率≥80% = full**；任一分量未测 = 无法验证 = 不给 full。

| 维度 | W20-03 实测（误杀/漏检/顺序） | phase2 旧标签 | **V2 复核标签** | 失败原因 |
|---|---|---|---|---|
| interact 互动 | 0.0 / 0.0 / 0.857 | full-qualified | **full-qualified**（唯一保住） | — |
| goal 目标 | 0.0 / 未测 / 0.829 | full-qualified | assistive（禁判死） | 漏检未测 |
| time 时间状态 | 0.043 / 未测 / 0.714 | full-qualified | assistive（禁判死） | 漏检未测 + 顺序<0.80 |
| emotion 情绪关系 | 0.0 / 0.0 / 0.743 | full-qualified | assistive（禁判死） | 顺序<0.80 |
| factuality 事实 | 0.0 / 未测 / 0.743 | full-qualified | assistive（禁判死） | 漏检未测 + 顺序<0.80 |
| initiative 主动 | 0.0 / 0.0 / 0.714 | full-qualified | assistive（禁判死） | 顺序<0.80 |
| attribution 归属 | 未测 / 未测 / 0.457 | not-testable | disqualified / assistive-only | 三项全不过 |
| boundary 边界 | 0.022 / 0.864 / 0.714 | mechanical-disqualified | disqualified（不变） | 漏检 0.864 + 顺序 |
| memory 记忆 | 0.0 / 0.609 / 0.514 | mechanical-disqualified | disqualified（不变） | 漏检 0.609 + 顺序 |

**确认指导书发现**：time/emotion/initiative/factuality 的历史顺序一致率 <0.80 却被标
full-qualified——本复核全部降级；boundary/memory 维持禁止单独判死（漏检率惨烈）。

## 2. 对正式实验的操作后果（先登记后执行）

1. **SELECTION/HOLDOUT 语义层结论只能分三级**：
   - `客观层`（通用评分层：契约/cancel_affirm/required/严重错误）——可作四组对照主证据；
   - `judge 辅助筛查`（九维，唯一 full=interact；其余 assistive 读数只作线索与索引，
     **不得以 judge 单独判死任何臂**，不得据此宣布质量优劣结论）；
   - `B3 亲读`（失败位人工级复核记录，标注"AI 亲读，非用户"）。
2. 主指标（用户盲评愿发）未收集前，**任何质量结论只能是描述性 + 置信区间**，不宣布显著优势。
3. 未校准维度的扩权路径只有一条：用户填完 40 锚 → 计算同锚 judge-用户一致率
   （按维度）→ 达到冻结判据的维度登记晋级（版本化 + 记录锚点证据），否则维持 assistive。
4. judge 分数与用户分数分开报告（P3-3.4：judge 可同批评锚，但不得代替用户填写）。

## 3. 40 锚校准包（已就绪，待用户填写）

`HUMAN_REVIEW_QUEUE_PHASE3.md/.json`（+ 匿名映射 `blind_key_anchor_phase3.json`，不入展示包）：

- **40/40 全部真实模型回复**（pilot2 live 三分片合并；Mock 内容 0——已程序核查）；
- T02 补位：A01–A04 旧 T02 弱锚（缺真实输出）→ **E305–E308 各第 3 锚**
  （轮位=与该篇已用两轮距离最远的确定性候选位，公式无随机源；40 锚总数不变，T02 仍 12）；
- 盲化：两臂显示为 臂A/臂B（按锚序奇偶交换），DIRECT/CURRENT 映射单独登记；
- 填写项：判定 PASS/FAIL/UNSURE（T02 对照 0/1/2 行为锚）＋ 更愿发送
  （臂A/臂B/都不错/都不愿发送）＋ 理由；未填 = NOT_COLLECTED；AI 不代投；
- 40 锚覆盖九维中的 7 维判据位（T01/T02/T03/T04/T05/T07/T08/T09/T10 分布见包内表），
  不足以校验全部九维——不足维度诚实保持未校准。

## 4. 校准闭环（等待项）

```
[用户填 40 锚] → [judge 同批评分] → [按维一致率/漏检/误杀计算]
   → 达标维度登记晋级（版本化+证据） → 否则维持 assistive
   → 冻结后用于 HOLDOUT 结果的语义层（含扩权边界）
```

当前状态：judge 同批评分可在用户填写前后任一时点跑（同锚材料已固定）；用户填写是
唯一的外部依赖，未完成前主指标记 NOT_COLLECTED。
