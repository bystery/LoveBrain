# E053 左耳的杂音 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + stage2/stage3 manifest 的 story_id/title/themes 字段）。
- 全部人物、订单、金额、事件均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：数码消费；主线：一副新耳机出现左耳电流声，HER 在 7 天无理由期限（10-30）前与客服口径博弈，最终通过"质量问题不受七天无理由限制"的平台规则走质量退货通道成功退货退款。
- 生活动机真实可信：电子产品瑕疵 + 平台退换货规则 + 期限压力，是常见消费场景。

## 2. 连续性与回扣
- T01 开箱喜悦 → T02 缺陷 → T03 期限博弈 → T04-T06 借账号边界冲突 → T07-T09 自己找到质量通道并过审 → T10 道歉回扣 T04-T06 → T11 退款与重购回扣 T07 → T12 收尾回扣 T04（借账号没门）与 T10（关系修复完成）。
- 跨轮引用：T02 缺陷在 T04 被引用；T03 客服说法在 T07 被推翻（status=superseded）；T04 借账号请求在 T06 被取消（status=cancelled, negation）；T07 规则在 T11 被复用。

## 3. ME 历史与边界
- ME 记录过订单期限（initial_kb.me），并有"不外借实名账号和支付账户"的长期立场（initial_kb.me，negation 型原子）。
- 冲突型=边界类：T04-T06 围绕借账号请求展开，ME 拒绝且不因施压（T05）改口；HER 主动撤回（T06，negation + status 变化），冲突以 HER 找到替代路径（T07）与道歉（T10）收束。
- ME 不充当客服枪手、不代替 HER 提交材料，仅做材料核对与流程梳理。

## 4. no_reply / 媒体局限
- HER 两轮发连续两条消息（T03、T12），存在 HER 补发消息的形态；全部为文本消息，无语音/图片媒体。
- 未设 no_reply 轮：12 轮均为 HER 主动发起且要求回应的场景；ME 无需伪造"已致电/已操作"类外部动作。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions ≥2、applicable_dimensions ≥3（全部在九维白名单内）。
- 原子 quote 全部为对应来源台词/initial_kb 的逐字子串，message-kind 原子均标注 message_id。
- 冲突轮落实：T05 原子 E053_A10（negation=true，边界立场）、T06 原子 E053_A11/A12（status=cancelled, negation=true）、T07 原子 E053_A14（status=superseded）。
- 跨轮引用原子共 11 条（A03/A07/A09/A10/A12/A14/A16/A18/A20/A22/A24）≥3。

## 6. 已知风险
- "质量问题不受七天无理由限制"为剧情内平台规则设定，不对应任何真实平台条款原文。
- HER T05 的施压强度中等，评审时若期待更强对抗可能偏弱；已用 T12 的边界共识收尾做补偿。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns 非空或明确为空列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(数码消费, 退换货期限博弈+账号边界, 边界类) 与既有篇三元组不同
