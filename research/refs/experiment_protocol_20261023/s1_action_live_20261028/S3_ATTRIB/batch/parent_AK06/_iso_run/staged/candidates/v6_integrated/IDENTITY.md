# v6_integrated — 目标分支 + 证据记忆 + 状态更新 + 上下文选择（MAX-288 V2 P05-B）

主假设：**目标/行动层与证据记忆层可以在同一次调用里共存**——前者负责"这轮做什么动作"，
后者负责"哪些事实/约定/更正必须被保住并正确使用"；两层正交，但共享一次调用，需实测注意力挤占。

## 机制 DNA

- 父本：`v6_goal_context`（闭包引用）；动作层/目标作用域/钩子审计全部继承，不复制后漂移。
- 四份 system 资产：自带副本；core 增加"记忆与事实"段；format.md 的 analysis 为单一合并契约
  （signal/goal_category/action_taken/anchor/hook + facts/tentatives/updates）；redline/naturalness
  与 v5 逐字节相同。
- 真实事件：HER/ME 原文 + 稳定 uid + role + ts；当前窗 alias 只映射，不把跨轮裸编号当来源身份；
  模型回复永不写回。
- 证据记忆：`fact_id/subject/relation/evidence_uids/evidence_quotes/certainty/status/event_time/
  valid_from/valid_to/supersedes`；初始画像/共同知识按 `kbin:`/`persona:` 来源归档；
  `ongoing_plan` 逐条入库并在计划行显示 `fact_id`。
- 状态机与历史：`confirmed_seed/confirmed/tentative/proposed/cancelled/completed/reopened/
  unverified_src/superseded` 与 `history` 分离；更正只改指定 `fact_id`；取消/完成不复活；
  旧版本留档可审计。
- 硬校验：`src_ids` 必须属于本轮；`quote` 必须是所引消息的逐字子串（空白归一）；
  主体/引文不成立 → `unverified_src`，只留审计、不投影；不做名字特判，主体语义错换边界如实登记。
- 上下文选择：当前窗口永不挤出；相关事实 500CU、暂定 300CU、最近更正 220CU；整条截断；
  静态种子与 seed 块去重；过期/取消/完成/被替换的事实不投影。
- 快照/恢复：事件、事实、历史版本、目标历史、动作日志、计划编号全部往返，用 runner 的
  `--stop-after/--resume` 跨进程验证。

## 诚实边界

- 引文存在 ≠ 语义主体正确；本机制只做成员/引文/主体枚举硬校验，不声称解决全部语义错换。
- 不做自动摘要/反思/画像重估；不新增模型调用；`analysis` 是写回素材，不是效果分。
- 未证明整合后两层都保留收益；注意力挤占正是 P06 的第一问题，不预写结论。

## 关系与消融

父本/祖父本冻结不动；本候选新目录、新身份。`mechanism_flags.evidence_memory=false` 关记忆层；
`action_layer=false` 关动作层；两者全 false = v5 基座行为（回归锚）。改 flags 会改变 params.json，
从而改变身份——消融必须是新身份。
