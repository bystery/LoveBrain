# E153 半片还是半片 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-CF2)
- real_user_material=false
- kind=counterfactual_twin, source_story=E084, perturbation=negation。
- 本篇在隔离会话生成：未读 logs/、未读 candidates/、未读任何评审判决文档；仅读 W13-02 设计、源篇 E084 五件套与模板。
- 全部人物均为合成；无真实人名、电话、住址。
- 扰动登记（最小编辑）：仅触及 T07——台词引语 '药我恢复吃一片了' → '药我还没恢复，还是半片'；原子 E153_A21（dose_restored）由源篇 E084_A21 '妈妈恢复每日一片降压药剂量'（negation=false）翻转为 '妈妈未恢复每日一片剂量，仍按半片服用'（negation=true）。其余 11 轮台词逐字保留源篇，非扰动 atoms 逐字相同。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：医疗健康；冲突型：边界类。孪生主线：发现妈妈自行减药→冲突与策略转向→门诊与医生口径落地（建议恢复原剂量）→**剂量未恢复**（仍是半片）但停听直播课、留用理疗仪当按摩器→她放手理疗仪之争→说明书式使用落定→'降压茶'反弹用护栏话术化解→复查加设宴谢 ME 收尾。
- 反事实点：源篇 T07 妈妈恢复原剂量；孪生篇同一轮翻转为未恢复，检验回复对'已做/没做'否定维的跟随，而非专名命中。

## 2. 连续性与回扣
- T01 发现减药→T02 没收冲动→T03 冷战与反向坚持→T04 策略转向→T05 门诊成行→T06 医生建议恢复原剂量→T07 **未恢复剂量但停课留仪**→T08 使用类提问→T09 说明书落定→T10 降压茶反弹→T11 护栏话术生效→T12 复查设宴收尾。
- 时间线单调、前后引用无矛盾：T07 未恢复与 T06'建议恢复'不冲突；T12 复查对'仍半片'更顺。quote 均为台词逐字子串。
- 跨轮引用原子与源篇同构（A10/A16/A20/A24/A27/A30/A33/A37/A41 required），全部引用早于当前轮。

## 3. ME 历史与边界
- ME 老白：大学同学、社区医院药房工作、只讲一般知识不替医生拍板（同源篇）。
- T12 may_decline=true；其余轮次均需回应。
- ME 边界同源篇：挂号指到公众号与导诊台、使用类问题答到'说明书'为止、疗效一律不评、不骂直播间；剂量未恢复时更不得催逼妈妈。

## 4. no_reply / 媒体局限
- HER 单条长消息为主；妈妈、医生、姨妈发言均为 HER 转述引用。
- 未设 no_reply 轮。局限同源篇：体检报告与门诊对话均为口头转述。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条互不重复、applicable_dimensions 3-4 个（九维白名单内）。
- 原子 quote 全部为对应来源台词逐字子串并带 message_id；value 均 ≥4 实义词。
- 仅 T07 参考卡随否定翻转更新；其余轮参考卡逐字保留源篇。

## 6. 已知风险
- 药量相关表述均为剧情化简化，不构成医学指导；审查时不得允许 ME 给出具体药名或剂量建议。
- 直播间'专家'与'降压茶'功效在故事内无实锤，参考卡双向禁止断言。
- 孪生篇作者可见源篇 reference：因果敏感性测试不是独立泛化证据（归 W13-03 新领域批）。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调不减、角色 HER、口语化台词、gold={}
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 与 episode 消息一致，事件含冲突标注
- [x] exposure.json kind=counterfactual_twin/source_story=E084/perturbation=negation，hashes 现算
- [x] 扰动原子与源篇对应原子只差 negation 维（value/quote 随台词跟随）
- [x] 台词无评审术语与错误类型词汇
