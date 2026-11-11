# E070 群里说的不算 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单材料（模板 + 既有篇的 story_id/title/themes 防撞字段 + E059 格式参照）。
- 全部人物、医院、公司、家族群均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：医疗健康；主线：HER 苗苗体检查出幽门螺杆菌阳性与轻度脂肪肝，焦虑后约周六市一院消化内科并请 ME 陪诊；故事中段母亲把她的报告截图推入家族群（第三方插入），舅舅偏方、母亲退号回老家找熟人、全家公筷令三路口径同时冲击主诊计划；她守住号、拿到医嘱（两周四联、服药期戒酒、停药一个月吹气复查），以医嘱照片平息群议，母亲让步、舅舅口径被否定；此后化解聚餐劝酒与服药副作用，以复查与烤肉之约收尾。
- 生活动机真实可信：体检异常后的搜索恐慌、家族群健康转发、熟人介绍偏好、年轻人不想公开病情，都是高频真实处境。

## 2. 连续性与回扣
- T01 报告落地 → T02 陪诊成约 → T03 致癌焦虑 → T04 安排锁定 → T05 母亲推群（插入）→ T06 三方口径爆发 → T07 守号决策 → T08 就诊与医嘱 → T09 医嘱压群、母亲让步 → T10 服药与戒酒冲突 → T11 聚餐化解与家人监督 → T12 复查与烤肉回扣收尾。
- 跨轮引用原子多条（A13/A15/A20/A22/A23/A25/A29/A32/A33），≥3。
- 冲突型=第三方插入类落实：T05 A08（母亲转发 asserted）、T06 A10/A11/A12（三方口径 asserted）+A13（主计划 suspended+negation）、T09 A22（退号要求 retracted+negation）、A23（舅舅口径 superseded+negation）、T11 A29（公筷 confirmed）、T12 A33（开口难题 superseded+negation）。

## 3. ME 历史与边界
- ME 老白：药企质控、"先分清是医生说的还是群里说的"（initial_kb.me），T06 归位三方口径、T08 核对医嘱均由此人设承载。
- T08 含一条 ME 历史消息（按时到场陪诊），属履约事实陈述；T12 may_decline=true，复查陪诊时间可商。
- ME 全程不代投家族群、不替她向母亲转达、不增加医嘱之外的医学断言。

## 4. no_reply / 媒体局限
- 全篇为文本消息；体检报告与医嘱单均以 HER 转述呈现（T08 提到"医嘱单我拍了照"，属动作叙述而非媒体内容）。
- 未设 no_reply 轮。局限：家族群二十多条、母亲与舅舅原话均经 HER 转述引用，属信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条且互不重复、applicable_dimensions 3-4 个（九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T06（A13 suspended+negation）、T09（A22 retracted+negation、A23 superseded+negation）、T12（A33 superseded+negation）。
- required_this_turn 原子共 8 条（T06、T07、T08、T09、T10、T11 各 1，T12×2），全部引用早前轮次；跨轮引用原子 ≥3 条红线达成。

## 6. 已知风险
- 医学内容限于"常见、可治疗、遵医嘱"层面，四联疗法细节未展开；若被测模型主动添加药名剂量，属 forbidden 范围。
- 家族群对话全部经 HER 转述，语义审查时注意引用层级不构成虚构第三方发言。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER/ME、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 与 episode 消息逐条一致，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(医疗健康, 幽门螺杆菌阳性+家族群偏方+医嘱对冲+分餐执行, 第三方插入类) 与既有 48 篇三元组至少一元不同
