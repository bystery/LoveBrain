# E120 交房短信没来（家庭事务 · 信息不确定类）审计单

批次：W3-T1-20261102 ｜ 会话：隔离生成会话 A（W3-T1-A） ｜ author=AI ｜ real_user_material=false
隔离声明：本会话未读 logs/、未读 candidates/、未读任何 VERDICT/REVIEW/AUDIT/评审 JSON、未读 B3 队列。

## 0. 性质与暴露
- 本篇为 AI 合成标准 12 轮故事，无任何真实用户材料（real_user_material=false）。
- 暴露 readers=["author"]，frozen_at=null，experiments_used 为空。
- 人物 许南 / 陆则 及全部机构、地点、电话均为虚构合成，不含真实人名、电话、住址。

## 1. 生活动机
- 父母养老迁居中的交房日期悬置，是典型的信息不确定生活场景。
- 关系阶段：长期熟人（大学同学）；HER 特质：靠谱、操心家里、遇事爱商量。

## 2. 连续性与回扣
- 冲突型：信息不确定类；主题集：家庭事务、父母同城养老、交房日期不确定、借用暂缓、短信确认。
- 跨轮引用原子共 10 处，事件与修订链见 timeline.json。
- 不确定口径三次演进（双口径-模糊通知-短信确认）均有逐字引语，否定与暂缓原子齐备。

## 3. ME 历史与边界
- ME 出借折叠床与加湿器，遵嘱暂缓，不催促不抢跑。
- HER 边界：不给人添麻烦；借东西必有还期。

## 4. no_reply / 媒体局限
- T12（馄饨之约）允许缓办；不确定信息轮必须避免断言。
- 台词零评审术语；导航、公众号、群通知等媒体均按 HER 转述呈现，不冒充第一手。

## 5. 参考卡质量
- 每轮 current_action 单值 + acceptable_actions 多选（≥2），applicable_dimensions ≥3。
- 冲突型所在轮原子带 status 变化或 negation；private_guidance 仅影响口吻。
- unknowns 明示未定项，不虚构补全。

## 6. 已知风险
- 裁判需区分'转述业主说法'与'既成事实'，防误记 asserted。

## 7. 出口检查
- 全部 source.quote 已由生成器做逐字子串断言（message 与 initial_kb 双通道）。
- 时钟与消息时间戳单调校验通过；quote 自核通过后入库。
- 机械校验以 tools/validate_story_v2.py --stage wave3 为准。
