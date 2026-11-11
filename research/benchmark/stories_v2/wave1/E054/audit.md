# E054 取号，改期，再取号 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + stage2/stage3 manifest 的 story_id/title/themes 字段）。
- 全部人物、工单、网点、规则均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：数码消费；主线：笔记本屏幕花屏 → 预约官方售后检测 → 临时出差取消改期 → 会议撞车二次改期 → 检测为排线老化保修免费换 → 取机闭环。
- 生活动机真实可信：工作设备维修与个人行程打架、怕留爽约记录，是高频职场生活场景。

## 2. 连续性与回扣
- T02 提醒委托（提前一小时出门）在 T05（周三再提醒）与 T12（第二道保险升级）两次回扣。
- T04 两小时规则在 T07 二次冲突中被 HER 主动援引复用。
- 取消改期状态链：T03 周四预约取消（negation）→ T04 改周三10:30 → T07 撞车 → T08 改周五18:30（status 变化）；T09 订货三到五天在 T10 兑现（confirmed）。

## 3. ME 历史与边界
- ME 承担她的预约与差旅日历外挂（initial_kb.me），复述规则、确认时间点，不代替她联系客服。
- 冲突型=取消改期类：两次取消改期均有原子 status 变化（cancelled/rebooked/confirmed）与 negation（去不成、不算爽约）。
- ME 不虚构爽约条款、不担保改期结果。

## 4. no_reply / 媒体局限
- HER 在 T02 发两条连发消息；其余轮单条。全部为文本，无语音/图片。
- 无 no_reply 轮：每轮 HER 均有明显信息诉求；ME 无外部动作主张。

## 5. 参考卡质量
- 每轮 acceptable_actions ≥2 且不重复；applicable_dimensions ≥3 且全部在九维白名单。
- 原子 quote 均为来源台词逐字子串，message-kind 均带 message_id。
- 跨轮引用原子 8 条（A05/A08/A10/A12/A14/A16/A19/A21/A23 中 8 条跨轮）≥3。
- unknowns 明示：工单号、配件到货日、客服受理结果等未定项。

## 6. 已知风险
- "提前两小时取消不计爽约、爽约两次拉黑一个月"为剧情内售后网点规则设定，不对应真实品牌条款。
- HER 情绪波动幅度大（慌→后怕→笃定→慌→如释重负），评审需注意这是取消改期冲突型的自然节律而非反馈不一致。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 字段齐、real_user_material=false、readers=["author"]
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(数码消费, 预约维修改期+爽约规则, 取消改期类) 与既有篇三元组不同
