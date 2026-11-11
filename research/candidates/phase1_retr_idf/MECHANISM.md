# MECHANISM.md — phase1_retr_idf（检索改进候选）

父版本：`candidates/phase0_beta_single`。**唯一主变量 = 检索策略**；
其余（存储、写回、编排、assets、params、memory-off 语义）与父版本逐字节一致。

## 1. 检索策略（v2.2，2026-10-20 独立复核后收口）

输入 = 本轮 HER 窗口文本 + 当前 facts 库；输出 = 注入 system 的 scene_chain。

1. **混合表示**：CJK 内容字（剥时间指示词整词 + 通用停用字，无话题词表）
   ∪ 英数 token（字母数字连续序列，小写）——设备型号/航班号/编号等实体标识。
2. **条目级归因激活门**（v2.2，替代 v2.1 窗口级 any() 门）：每条事实的字级
   证据可采性只由【该事实自身】与窗口的关联决定，任一其他条目不得替它放行
   （v2.1 实测：一条"退票规则"背景记录即让"猫眼能退票吗"重新误取猫过敏）。
   三条独立通道满足其一即放行：a) 分段保留的原始 CJK bigram 共享（实体连续性，
   "成都"不被停用字切断）；b) 内容字共享 ≥2（单字证据歧义不可信）；c) 英数
   token 共享（独立证据）。
3. **bigram 分段修复**（v2.2）：bigram 在任何非 CJK 字符处切断——v2.1 先删
   分隔符再拼接，跨标点伪造"退，票→退票""猫。眼→猫眼"（独立复核 §3.3 实测）。
4. **混合 IDF 余弦**：ln(1+N/df)，库上现算；score ≥ 0.15 入选，topk=6，
   平分按 clock 稳定排序。

阈值 0.15 沿用 v1 校准（t5 期望最低 0.2031 / t6 噪声最高 0.1069）；
v2.2 在 `tools/test_retr_idf.py` 全案例（A 冻结时点回放 + B 锁定 8 例 +
F reviewer 反例 3 例 + **H 故障族配对回归 5 例** + G 开发节点 12 例）上验证。

**登记权衡（配对回归显式断言，不藏进总 PASS）**：
- 相关短问单字证据被拒："我能抱一下猫吗"×猫毛过敏、"那家火锅很辣"×不吃辣——
  与"猫眼"单字歧义在字面上不可区分，语义层缺失；v1 能取回属单字歧义放行
  （"猫眼"误取）的同一枚硬币反面。gate 字段归因 `denied:single_char_only`，
  与否定极性理解分开归因（复核 §3.2）。
- 零字面重叠纯同义改述不可召回（v1 已登记）；HER/ME 归属检索层不可分。
  详见 `refs/L3B_RETR_DEV_NODES_20261020.md`。

## 2. 写回政策（明确登记，第九波判决 §4 范围澄清）

**合法 analysis 独立写回**（延续父版本行为，非全输出原子提交）：
- 写回入口只调用 `lb_scorer.analysis_type_errors`——analysis 内层
  （scene_facts/ongoing/topic_*）过类型门后逐项提交；坏结构整体跳过、留痕
  （`trace.writeback.skipped = nested_type_violation:...`）、生命周期时钟照常前进。
- analysis 缺失/null/list/string：无可消费内容，`skipped = analysis_not_dict:<type>`，
  不崩溃（第九波判决 §4 回归已修 + `tools/test_trace_robustness.py` T1/T2 回归）。
- **输出其余部分（directions/response null 等）不参与写回门**——展示层契约
  （string|null）由评分器管辖；两处职责分离，不互相代表。
- 若未来决定全输出原子提交，须实现+测试后再宣称（本候选未做）。

## 3. 来源追踪（评估专用；不注入请求、不含 gold）

每轮追加 `sandbox/retrieval_trace.jsonl`：
- `facts_stored`：已存储（clock+text）
- `retrieval.facts_scored`：被检索（score/shared_chars/shared_tokens/gate/
  selected/filtered_reason/gate_policy/strategy）
- `injected`：进入请求（scene_chain fact ids、ongoing 全量注入显式标注、topic）
- `writeback`：facts_appended / skipped（含 analysis_not_dict）

第四态（被回复采用）由 `tools/retr_report.py` v2 离线复算：**词面重合提示**
（原文 bigram，不停用字过滤——"过敏"保真），不推断因果采用；facts/ongoing
双通道同概念在场时标"通道不可辨识"。语义使用属人工/AI 判读，工具不自动判定。

## 4. memory-off 语义

与父版本一致：六渠道全关（facts/ongoing/topic/检索/last_clock/initial_kb）；
共同画像 persona 保留。回归 `tools/test_retr_idf.py` E 组。
