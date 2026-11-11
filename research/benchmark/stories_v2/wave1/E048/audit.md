# E048 拼团券差一百二 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + stage2/stage3 manifest 的 story_id/title/themes 字段 + 同批 E053 样例形制）。
- 全部人物、平台、课程、金额、事件均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：学习成长；冲突型：费用类；主线：二战考研的小禾组织拼团代付网课，付款后发现自己账号的120元教育券差价归属成谜，在'辛苦费'与'均分退还'之间裁定为人均退30，全部退款闭环后沉淀'先公示后付款'的团购新规。
- 生活动机真实可信：拼团代付、优惠券叠加差价、人情与明账的拉扯都是学生群体常见费用场景。

## 2. 连续性与回扣
- T01 开团垫付 → T02 发现券差（费用冲突进场）→ T03 两派意见分裂 → T04 裁定退还（利己归属 superseded）→ T05 两笔退款、一笔悬置 → T06 退款全闭环并拒绝以奶茶抵账 → T07 新规沉淀 → T08 开课回扣券（风波了结）→ T09 口碑引来管账新角色回扣 T07 → T10 信任修复回扣 T06 → T11 记账习惯升级 → T12 英语团闭环回扣 T11。
- 跨轮引用：T01 约定价在 T02 被对照；T02 券差在 T04 被裁定（superseded + negation）、T08 被了结（confirmed）；T04 方案在 T05/T06 被执行（confirmed）；T03 意见在 T07 落定（confirmed）；T06 信任在 T10 修复完成；T11 习惯在 T12 被复用。
- 跨轮引用原子共 10 条（≥3）。

## 3. ME 历史与边界
- ME 记过拼团账（initial_kb.me），并有"不替她转账，账目有出入时帮她算清而不是劝她算了"的长期立场（negation 型原子，T05 落实）。
- 冲突型=费用类：ME 只算账不裁断（T02/T03），归属裁定与退款执行均由 HER 亲自完成；对'这么点钱算了'式的和稀泥保持不附和。
- ME 不代她催款转账，不虚构平台券规则。

## 4. no_reply / 媒体局限
- 全部为文本消息，无语音/图片媒体；群聊内容仅以 HER 转述形式出现。
- 未设 no_reply 轮：12 轮均为 HER 主动发起且要求回应的场景。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions ≥2、applicable_dimensions ≥3（全部在九维白名单内）。
- 原子共 39 条（required 11 条），quote 全部为对应来源台词/initial_kb 的逐字子串，message-kind 原子均标注 message_id。
- 冲突轮落实：T02 原子 negation（实付与约定价的差额事实链）；T04 原子 status=superseded + negation（利己归属被否定）；T05 原子 negation（退款未全部完成）；T06 原子 status=confirmed（更正闭环）；T08 原子 status=confirmed（风波了结）。
- 跨轮引用原子共 10 条 ≥3。

## 6. 已知风险
- 券差裁定依赖'事先没约定'这一事实，若评审认为团长留差价属行业惯例可能产生分歧；已用 T07 新规自洽。
- 阿芳'辛苦费'说法未被正面驳斥而是被新规超越，处理偏温和。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns 非空或明确为空列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(学习成长, 拼团代付+费用更正, 费用类) 与既有篇三元组不同
