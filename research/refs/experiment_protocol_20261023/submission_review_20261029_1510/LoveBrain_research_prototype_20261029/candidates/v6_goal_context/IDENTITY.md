# v6_goal_context — 目标/行动与自然互动候选（MAX-288 V2 P05-A）

主假设：**把"目标"降为本轮作用域内的方向，而不是压在聊天上的旧任务**；用显式反馈条件化本轮可用动作，
让回复既能接住、也能主动开切口，同时在明确拒绝/忙碌/收尾时真正收力。

## 机制 DNA

- 父本：`v5_purpose_aligned`（闭包引用，不复制后漂移）；共享 `reference_product_v1` 的 rc/语义块。
- 四份 system 资产：自带副本（core 增加"互动"段；format.md 的 analysis 扩为
  signal/goal_category/action_taken/anchor/hook 五键；redline/naturalness 与 v5 逐字节相同）。
- 目标作用域：ACTIVE/ADJUSTED 生效；CLOSED/COMPLETED/EXPIRED/PAUSED 不生效；`expiry<now` 失效；
  `goal_history` 记录每次变化，快照/恢复不丢。
- 条件化能力集：机制检测本轮 HER 最近消息的显式收尾/拒绝/忙碌/暂缓/积极/含糊信号，每轮只展示
  2—5 个动作能力；显式拒绝/忙碌时邀约不出现；含糊给多动作（承接/反抛/轻试探/转场），不自动降温；
  不按单个"哈哈/切"硬路由。
- 钩子审计：analysis 记录本轮选择的动作、依据的本轮消息 id、钩子类型与正文原文；消费端检查
  anchor 属于本轮、hook.text 逐字出现在最终可发文本；未兑现只记 notes，不二次调用、不罚分。
- 私密/公开/草稿/猜测分区沿用 v5 的四类 note 和事实-行动边界；关闭目标不删除已确认真实约定。

## 诚实边界

未证明比 v5/C2 更会聊；真实质量由 P06 同批对照与原文复核判定。显式短语检测不覆盖反讽/绕弯/
上下文否定，检测结果只作提示，语义判断仍归模型。`analysis` 是审计记录，不是效果分。

## 关系

父本冻结不动；本候选新目录、新身份。`mechanism_flags.action_layer=false` = 回退 v5 目标块行为，
但改 flags 会改变 params.json 从而改变身份——消融必须是新身份，不能在同一身份下偷换行为。
