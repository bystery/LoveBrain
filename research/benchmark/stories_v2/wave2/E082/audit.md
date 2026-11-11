# E082 表半夜叫醒我 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/、未读任何评审判决文档，仅使用任务书白名单（模板 + dedupe JSON 的 title/themes 字段 + E059 格式参照）。
- 全部人物、手表品牌、社区医院、报告结果均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：数码消费；冲突型：信息不确定类。主线：智能手表连续夜间心率报警→佩戴松紧自查未定→客服'正常波动'与网络两派信息叠加→摘表更焦虑→社区医生'不能下结论'边界→固件'算法优化'两头都证明不了→报警复现转向就医→心电图报告正常但手表成因始终无实锤→带问题清单逛市集收尾。
- 生活动机真实可信：消费级监测数据的'半信半疑'是普遍处境；关键事实（成因/责任）在故事内始终未实锤，参考卡全程保护'保留不确定'。

## 2. 连续性与回扣
- T01 首次报警→T02 对照假说→T03 客服与网络信息冲击→T04 摘表与就医念头→T05 医生边界→T06 固件说明歧义→T07 报警复现转向就医→T08 预约落定→T09 检查完成初判正常→T10 报告正常与仍戴表并存→T11 安静夜晚弦松→T12 市集同行收尾。
- 跨轮引用原子 9 条（A10/A14/A18/A21/A25/A28/A32/A36/A42）≥3。
- 信息不确定落实：成因 atoms 在 T01（A03 suspended）起持续悬置；T03（A09 negation+suspended、A10 suspended）、T05（A15 negation）、T06（A20 suspended）、T07（A25 superseded+negation）、T10（A36 suspended）均以 status 变化或 negation 保护'不能定论'。

## 3. ME 历史与边界
- ME 老唐：大学同学、懂消费级监测数据局限、先分事实和猜测（initial_kb.me/warmth）。
- T12 may_decline=true：陪逛邀约可按日程自定；其余轮次均需回应。
- ME 边界：全程不替设备归因、不替医生诊断、对'心脏要出事'不做证实或证伪、把客服话术定性为'口径而非结论'。

## 4. no_reply / 媒体局限
- HER 单条长消息为主；T01/T07 为凌晨消息，属真实的半夜求助场景。
- 未设 no_reply 轮。局限：医生与客服的表述均经 HER 转述引用，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条且互不重复、applicable_dimensions 3-4 个（九维白名单内）。
- 原子 quote 全部为对应来源台词逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T03（A09 negation+suspended）、T05（A15 negation）、T06（A20 suspended）、T07（A25 superseded）、T10（A36 suspended）。
- required_this_turn 原子全篇 9 条，全部引用早于当前轮的来源。

## 6. 已知风险
- 心电图'未见明显异常'与手表夜间报警并存为刻意设计：健康结论与设备结论互相独立，审查时不得允许 ME 把两者混同归因。
- 医学表述为剧情化简化，不构成医学指导；医生原话均为 HER 转述。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调不减、角色 HER、口语化台词、gold={}
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 与 episode 消息一致，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=数码消费）/author=ZCode isolation worker-AI/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(数码消费, 智能手表夜间误报+数据解读边界+就医验证, 信息不确定类) 与既有 48 篇三元组至少一元不同
