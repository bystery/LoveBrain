# MECHANISM — RH-CONVERGE

## 存储
无持久化存储。所有状态进程内（handle），换故事清空。冷却状态简化为 handle 内 dict。
corrections 暂为空（v1 留接口）。

## 检索
段检索（pipe_v1 复用）：bigram Jaccard 边界分段 → 当前轮 bigram 查询 → top-3 段原文注入中部。
不与尾部最近消息重复（recent_ids 过滤）。

## 意图
OngoingContextSelector（移植）：三信号 relevance gating（真实消息关键词 / 备注 / 意图）+ 冷却 3 轮 + DORMANT 5 轮。
数据无 ongoing_plan 时 no-op。意图文本参与关键词匹配但不刷新证据时间（防止持续意图永保 ACTIVE）。

## 上下文
14 段装配（头部稳定→中部动态→尾部贴近生成点）→ BudgetTrimmer 9000 字符裁剪。
- 头部：人物背景 / 关系画像(KnowledgeAssembler: me/her/warmth/style/lessons) / 场景(SceneInjection: 7类固定优先级)
- 中部：相关往事(段检索 top-3) / 未定事项(UncertainDetector: hedge/question/quote 三信号)
- 尾部：备注/意图/时间 / 最近消息(K=3) / 话语账本(said_ledger: answered+open+invalidated) / 失效事项(invalidated+硬约束) / 示例(b20 锚定) / 输出要求

## 与原版差异
1. corrections 暂为空（v1 留接口，不做纠正落盘）
2. 冷却简化为进程内（v1 不做两段式提交）
3. OngoingContextSelector 在数据无 ongoing_plan 时为 no-op
4. SceneChainInjection 未移植（数据无 scene_chain 字段）
