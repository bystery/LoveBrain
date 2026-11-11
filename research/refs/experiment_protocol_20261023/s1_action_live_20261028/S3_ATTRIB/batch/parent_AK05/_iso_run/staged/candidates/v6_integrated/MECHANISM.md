# v6_integrated — MECHANISM

## 四项决策

| 环节 | 策略 |
|---|---|
| 存储 | 真实事件（HER/ME 原文 + 稳定 uid + role + ts；回复不写回）+ 证据事实（fact_id/subject/relation/evidence/status/时间/版本）+ 计划事项（带 fact_id）+ 目标历史（仅审计） |
| 检索/注入 | 当前窗口永不挤出；相关事实 500CU、暂定 300CU、最近更正 220CU；整条截断；静态种子与 seed 块去重；过期/取消/完成/被替换不投影；无关旧事实不占预算 |
| 意图 | 继承 v6_goal_context：ACTIVE/ADJUSTED 生效、CLOSED/COMPLETED/EXPIRED/PAUSED 不生效、expiry<now 失效；关闭不复活；已确认约定仍保留 |
| 上下文组织 | 种子（按当前有效事实渲染）→ 目标 → 计划（带编号）→ note → 动作能力 → 相关事实/暂定/最近更正 → 真实历史 → 当前窗口 → 来源/媒体/时间/事实-行动边界 → 当前时间 |

## 证据记忆与状态机（本版关键差异）

1. **归档**：`initial_kb` 标量（me/her/warmth/style/stage）与 persona me/her 按 `kbin:/persona:`
   入库，`status=confirmed_seed`；`ongoing_plan` 逐条为 SHARED 事项，计划行显示 `fact_id`。
2. **写入**：模型每轮在同一 `analysis` 报告 `facts/tentatives/updates`；`src_ids` 必须映射到本轮
   uid；`quote` 必须是所引消息的逐字子串（空白归一）；主体枚举必须合法，否则 `unverified_src`。
   事实文本/主体相同则复用 `fact_id` 并追加证据；不重复建行。
3. **状态**：`confirmed_seed/confirmed/tentative/proposed/cancelled/completed/reopened/
   unverified_src/superseded`；`history` 保留旧版本（status/text/valid_to/原因/证据）。
   `updates` 只改指定 `fact_id`：correct 换文本并留旧版；cancel/complete 设 valid_to；
   reopen 恢复 active；引用不存在/无据的更新拒绝并记 notes。取消/完成不复活。
4. **投影**：只投影 active 且非静态种子的事实；相关性=当前消息+目标字符二元组重合率（floor 0.12，
   无信号按新近度）；过期（valid_to≤now）不投影；最近更正块列出取消/完成/更正，防旧值复活。
   当前窗口与有效指导优先；预算不足整条事实/消息截断，不切字符。

## 消融与边界

- `evidence_memory=false`：不投影/不写回记忆，计划行回退 v5 简单格式；`action_layer=false`：不注入
  动作层，目标块回退 v5。两者全 false = v5 基座行为。改 flags = 改 params = 新身份。
- 引文/成员/主体枚举是硬校验，语义主体支持仍可能被字面重合穿透——如实登记，交评审层。
- 不做自动摘要/反思/画像重估；不新增模型调用；不声称已证明两层整合都保留收益。
