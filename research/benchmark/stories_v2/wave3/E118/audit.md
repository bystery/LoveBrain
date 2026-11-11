# E118 司机不免单（出行拼车 · 费用类）审计单

批次：W3-T1-20261102 ｜ 会话：隔离生成会话 A（W3-T1-A） ｜ author=AI ｜ real_user_material=false
隔离声明：本会话未读 logs/、未读 candidates/、未读任何 VERDICT/REVIEW/AUDIT/评审 JSON、未读 B3 队列。

## 0. 性质与暴露
- 本篇为 AI 合成标准 12 轮故事，无任何真实用户材料（real_user_material=false）。
- 暴露 readers=["author"]，frozen_at=null，experiments_used 为空。
- 人物 之桃 / 沈航 及全部机构、地点、电话均为虚构合成，不含真实人名、电话、住址。

## 1. 生活动机
- 同事四人秋游拼车，账目口径之争是生活中最常见的小摩擦。
- 关系阶段：渐暧昧（朋友之上，常拿立规矩开玩笑）；HER 特质：爱立规矩、账目分明、招呼人一把好手。

## 2. 连续性与回扣
- 冲突型：费用类；主题集：出行拼车、四人自驾出游、司机免单之争、均摊口径回摆、停车费现金。
- 跨轮引用原子共 12 处，事件与修订链见 timeline.json。
- 口径三次变化（立、改、回摆）每轮都有逐字引语与否定标记，费用数字全程一致。

## 3. ME 历史与边界
- ME 垫付油费是惯例，但不接受被单方面免单；坚持自己立的规矩。
- HER 边界：欠账必还；不让朋友吃亏。

## 4. no_reply / 媒体局限
- T09（住一晚）、T10（保养建议）允许缓办；其余轮需明确表态。
- 台词零评审术语；导航、公众号、群通知等媒体均按 HER 转述呈现，不冒充第一手。

## 5. 参考卡质量
- 每轮 current_action 单值 + acceptable_actions 多选（≥2），applicable_dimensions ≥3。
- 冲突型所在轮原子带 status 变化或 negation；private_guidance 仅影响口吻。
- unknowns 明示未定项，不虚构补全。

## 6. 已知风险
- 费用口径回摆轮次较密，裁判需逐轮核对当前生效口径。

## 7. 出口检查
- 全部 source.quote 已由生成器做逐字子串断言（message 与 initial_kb 双通道）。
- 时钟与消息时间戳单调校验通过；quote 自核通过后入库。
- 机械校验以 tools/validate_story_v2.py --stage wave3 为准。

## 8. W03-06 wave3 T1 语义审查修复记录（2026-11-02）
- T11 unknowns 增补：登记 T08'三笔到账'与本轮阿哲份额中转到账的口径矛盾，实际到账路径未知（W03-06 wave3 T1 语义审查 2026-11-02）。
