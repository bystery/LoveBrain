# E117 多出来的同乘人（出行拼车 · 更正类）审计单

批次：W3-T1-20261102 ｜ 会话：隔离生成会话 A（W3-T1-A） ｜ author=AI ｜ real_user_material=false
隔离声明：本会话未读 logs/、未读 candidates/、未读任何 VERDICT/REVIEW/AUDIT/评审 JSON、未读 B3 队列。

## 0. 性质与暴露
- 本篇为 AI 合成标准 12 轮故事，无任何真实用户材料（real_user_material=false）。
- 暴露 readers=["author"]，frozen_at=null，experiments_used 为空。
- 人物 小棠 / 阿远 及全部机构、地点、电话均为虚构合成，不含真实人名、电话、住址。

## 1. 生活动机
- 城乡两头的家庭托带与一场表姐婚礼，构成顺风车行程的真实生活压力。
- 关系阶段：普通朋友偏熟（同小区邻里）；HER 特质：爽利直接、记账清楚、家里托带事务多。

## 2. 连续性与回扣
- 冲突型：更正类；主题集：出行拼车、返乡顺风车、同乘人数更正、油费重算、后备箱取舍。
- 跨轮引用原子共 12 处，事件与修订链见 timeline.json。
- 更正链四处（人数、费用、时间、米酒数量）均有逐字原话支撑，跨轮原子引用充分。

## 3. ME 历史与边界
- ME 常跑临州、油费老规矩八十，是可依赖的熟人司机；可婉拒额外托带。
- HER 边界：不占人便宜；受人帮助必定回礼。

## 4. no_reply / 媒体局限
- T03（米酒可拒）、T12（市集邀约可缓）允许不接；驾驶轮（T09）必须短回复。
- 台词零评审术语；导航、公众号、群通知等媒体均按 HER 转述呈现，不冒充第一手。

## 5. 参考卡质量
- 每轮 current_action 单值 + acceptable_actions 多选（≥2），applicable_dimensions ≥3。
- 冲突型所在轮原子带 status 变化或 negation；private_guidance 仅影响口吻。
- unknowns 明示未定项，不虚构补全。

## 6. 已知风险
- 多处更正若裁判只看最后一轮可能漏判中间口径，需按轮独立记。

## 7. 出口检查
- 全部 source.quote 已由生成器做逐字子串断言（message 与 initial_kb 双通道）。
- 时钟与消息时间戳单调校验通过；quote 自核通过后入库。
- 机械校验以 tools/validate_story_v2.py --stage wave3 为准。
