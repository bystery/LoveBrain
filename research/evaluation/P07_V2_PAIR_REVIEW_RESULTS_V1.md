# P07 v2 成对轨迹评审结果 V1（首对：I2__fbhesitant）

日期 2026-10-28 06:15；评审模型 qwen3.8-flash（purpose=trajectory_review，账本 AUTH-P07-V2-20261028-01，本对 4 笔：2 成功 + 1 超时 + 1 超时后正序重试成功）；
评审器 `tools/run_p07_traj_review.py`（盲化+左右随机+swap 正反序）；判词原文=`logs/p07_pilot_v2_20261028_055728c/TRAJ_REVIEW__I2_hesitant_pair01*.json`。

## 1. 数据

- 唯一合格对：I2__fbhesitant——ref__r2（complete, 3send/3sim）vs v6__r1（complete, 2send+1交付失败/3sim）。
- 两个排序各评一次（seed 42/43）。

## 2. 原始读数（21 分制）

| 排序 | left | right |
|---|---|---|
| normal(seed43) | **ref 20**（goal3 hook3 adapt3 repair3 promise2 factual3 noreply3） | v6 15（goal2 hook2 adapt2 repair2 promise3 factual3 noreply1） |
| swap(seed42) | **v6 19**（goal2 hook3 adapt3 repair3 promise2 factual3 noreply3） | ref 11（goal1 hook2 adapt1 repair1 promise3 factual3 noreply0） |

## 3. 核心发现：位置偏置压倒臂间差异

**两种排序下都是 left 臂胜出**（分差 +5 与 +8）。同一臂换位后总分摆动 5–9 分
（ref：20↔11；v6：15↔19）。评审器存在强 left 位置偏好，**本对评审不能作为两臂比较证据**。
维度级平均（探索性，n=2）：v6 在 feedback_adaptation/repair/no_reply 微幅领先，
ref 在 goal_action 微幅领先，promise_confirmation/factual 持平——不足以支持任何判决。

## 4. 评审器偏置修正清单（评审 ≤12 的剩余额度冻结至修正后）

1. prompt 增加显式独立性指令（"左右顺序随机，位置不代表质量，请仅按内容打分"）；
2. 每对至少 4 个排序（2 seed × 正反）+ 按维度取中位数，弃用单次总分；
3. 分维度要求逐字引用（当前 issues 只在低分时强制）；
4. 增加"两臂都好/都差"的允许表述，强制区分绝对质量与相对比较；
5. 修正后先用已知质量差异的对照对（如人工构造强弱对）验证偏置消除，再评真实对。

## 5. 诚实边界

n=1 对 ×2 排序，qwen 单模型，模拟器自报 state_delta 未独立验证；本结果不构成任何臂的
质量判决。r1 的 6 条 incomplete 保留原样；I4__positive__v6 连续 3 次断在 sim（unknown 是否
与该对话内容长度相关）；评审剩余额度（≤12 中已用 4）冻结至偏置修正完成。

---

## 6. 偏置修正与双序验证（2026-10-28 06:45，watchdog30 接管轮）

**修正内容**（备份 `.bak-20261028-0630-wd-biasfix`）：SYS_TMPL 增加独立性纪律块——
①左右顺序随机、位置不代表质量；②允许两臂同分、禁止硬造差异；③两臂独立打分；
④任何 ≤1 的维度及两臂各自最好/最差维度必须逐字 quote。修正清单第 1/3/4 条落实，
第 5 条（强弱对照 gate）通过后再解冻，第 2 条部分落实（双序+维度级对比）。

**Gate 验证（人工强弱对，`logs/p07_review_bias_check_20261028/`）**：STRONG 两序皆大胜
（正常位 20 vs 5；交换位 20 vs 8）→ 位置不再决定胜负，评审器解冻。
同臂跨位摆动从 5–9 分降至 ref 0 分 / v6 4 分。
验证记录 = `max288_v3_20261028/P07_REVIEWER_BIAS_FIX_VERIFIED.json`。

**map 语义修正**（`.bak-20261028-0645-wd-mapfix`）：swap 文件的 `map_left_right` 旧版
反写实际显示位（记规范序），已改为记实际显示位；旧 swap 文件按旧语义解码。

## 7. 修后真实对判定：I2__fbhesitant（评审额度 7/12）

| 排序 | ref__r2 | v6__r1 |
|---|---|---|
| normal(seed11) | **20**（左） | 10（右） |
| swap(seed12) | **21**（右） | 14（左） |

**双序方向一致：reference_product 胜 v6_integrated**（总分中位 ≈20.5 vs ≈12）。

维度级（两序对照）：
- ref 全维领先：goal_action（3 vs 1,2）、hook_response（3 vs 1,2）、feedback_adaptation（3 vs 1,2）、misunderstanding_repair（3 vs 1,3）、**no_reply_fitness（3 vs 0,1，最大短板——她不回时 v6 有追问/硬推倾向）**；
- v6 唯一真实增量：promise_confirmation（3,3 vs 2,3——承诺收敛机制有效，与轨迹中"行，11点，周日上午展馆门口见"+sim 约定+1×3 一致）；
- factual_safety 两臂平（3=3）。

**结论（喂给 W03 行动迭代）**：I2__hesitant 条件下，v6 的意图/记忆机制当前实现反而
降低对话质量——目标动作驱动过强、no_reply 分支差；应保留 promise 收敛机制，修 goal
驱动语气与 no_reply 分支，而非回退机制本身。

**诚实边界**：n=1 对×2 排序；ref 用 r2、v6 用 r1（重复间变异未控）；qwen 单评审模型；
第三/四排序未做（剩余额度 5 笔留给 I4 对）；模拟器 state_delta 自报未独立验证。

---

## 8. r3 补齐与 I4 两对评审（2026-10-28 07:15，同轮续）

**r3 波**（授权文书 repeats_plan_r3，账本 69→77）：探针 2257ms OK 后两段并行实跑，
`I4__fbpositive__v6_integrated__r3` 与 `I4__fbhesitant__reference_product_v1__r3` 均
**complete**（各 1 harness + 3 sim，skip r1/r2 不重派）。三次内跑通，证实 I4 两对侧
失败是 qwen 间歇性环境故障而非内容相关。

**评审结果**（本目录 `logs/p07_pilot_v2_20261028_065658c/`，评审额度 12/12 用满）：

| 对 | 排序 | 结果 | 方向 |
|---|---|---|---|
| I4__fbpositive | normal(seed21) | 20 vs 20 平 | 两臂都好（独立性指令允许同分） |
| I4__fbpositive | swap(seed22) | V6 21 vs REF 18 | V6 微弱（v6 全维 3，ref hook 1/adapt 2） |
| I4__fbhesitant | normal(seed33) | **V6 21 vs REF 16** | 单序，不可作判决 |
| I4__fbhesitant | swap×2 | 双双超时 | 按消耗计，额度尽 |

I4hes normal 判词 JSON 在 issues 引号处损坏，parsed 离线正则补取（API 响应只当数据，
未重发未重试），已回写 trace 文件 `parsed_recovered_offline` 字段。

**汇总读数**（探索性，格内 n=1）：
- I2__hesitant：REF 明确胜（双序一致）——v6 的 no_reply/conflict 动作是实锤短板
  （trace[2] classify_reason=action_conflict:no_reply_with_body → delivery_failed）；
- I4__positive：接近持平、V6 微弱——积极反馈下 v6 的目标推进不吃亏，promise 收敛有效；
- I4__hesitant：V6 单序领先 5 分，方向与 I2 相反，双序未确认，unknown。

**W02 出口判定**：七项全过（三项带边界），= `max288_v3_20261028/W02_EXIT_VERDICT.json`。
按 MASTER §129：W09 扩展与新 scope 预登记进队列，W03 迭代方向已定。
