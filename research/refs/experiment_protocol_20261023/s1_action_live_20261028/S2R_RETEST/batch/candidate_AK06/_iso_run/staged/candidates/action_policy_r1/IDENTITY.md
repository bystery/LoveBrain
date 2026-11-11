# action_policy_r1 — 发送合同对齐 + 目标让位（MAX-288 V3 W03 第一个增量）

主假设：**P07 v2 pilot 中 v6 的两大评审短板（no_reply_fitness 0-1、goal_action 1-2）来自两个
可定位的机制级根因，修根因（不换基座、不承诺收敛机制）即可在反馈敏感场景补齐短板**。

## 机制 DNA

- 父本：`v6_integrated` 全量继承（证据记忆/状态机/上下文选择/promise 收敛零改动）；
  祖父本 `v6_goal_context` 仍为闭包引用，磁盘冻结不动。
- 本分支唯一差异（全在 mechanism.py 加载期 flag 门控补丁，零额外模型调用）：
  - **FIX-1 发送合同对齐（action_contract_fix）**：机制侧"收尾/停"规则与公共消费者
    `classify_reply` 动作语义对齐——不回动作正文必须为空；may_decline=false 的收尾轮
    不提供不回动作，收住意图由"承接"承载自然短消息；动作块注入显式发送合同。
  - **FIX-2 目标让位（goal_yield_fix）**：她带低落/疲惫/自嘲/沉重色彩时目标让位，
    裁量权交模型（不建关键词表，遵守 W03 第2条），goal_category=暂缓不算失败。
  - **FIX-3 钩子承接（hook_catch_fix）**：她的具体细节（身体状态/物件/动作/人事）是
    最高优先钩子，至少接住一个再谈别的（评审 hook_response 落空 n=5）。
  - **FIX-4 交付失败修复意识（failure_repair_fix）**：配套环境 v2.1——runner 把上一轮
    delivery_failed 以类型化 `own_events` 进下轮 ctx，机制渲染为系统事件注释并给修复
    指引（评审 misunderstanding_repair 无修复 n=4；此前模型根本不知道自己上轮哑了）。
  - **FIX-5 自我状态有据（self_grounding_fix）**：自己的时间/位置/经历陈述须有前文依据，
    没有就含糊或不提（评审 factual_safety "时间好安排/只是路过"无据 n=1）。
- promise/约定收敛机制不动（v6 唯一双序稳定优势维）。

## 证据来源与诚实边界

- 证据 = P07 v2 pilot 成对评审：I2__fbhesitant 双序一致 REF 20/21 > V6 10/14
  （`evaluation/P07_V2_PAIR_REVIEW_RESULTS_V1.md` §6-8）；实测根因 trace：
  `TRACE_I2__fbhesitant__v6_integrated__r1.json` trace[2] classify_reason=
  `action_conflict:no_reply_with_body`。
- **两修复均未经真实轨迹验证**——只声称消除了合同错位与缺让位指令两个已定位根因，
  不声称已修复评审短板；下一轮 pilot 实测后回填结论。
- I4 场景 v6 接近持平/微弱领先：本分支不追求在积极反馈上再加分，只求 I2 类
  反馈敏感场景不再丢分；若实测出现负迁移，按消融回滚对应 flag。

## 关系与消融

父本/祖父本冻结不动；本候选新目录、新身份。`mechanism_flags.action_contract_fix=false`
单独关 FIX-1；`goal_yield_fix=false` 单独关 FIX-2；两者全关 + 其余沿父本 = 逐字节回到
v6_integrated 行为（回归锚）。改 flags 会改变 params.json，从而改变身份——消融必须是新身份。
