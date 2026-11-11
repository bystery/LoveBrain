# E110 秤是慢变量 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 dedupe JSON 的 story_id/title/themes 字段 + E059 五文件作格式参照）。
- 全部人物、门诊、处方、数据均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：医疗健康；主线：HER 小鹿在社区医院体重管理门诊立下三个月处方计划并向学长 ME 立爬山之约；出差驻场触发全盘暂停冲动，经劝阻退为两条底线；底线又遭烧烤局冲击，靠通勤凑步守住；复诊重启并加量后遭遇平台期，终止冲动被腰围证据化解，目标由掉秤改为围度加睡眠；三个月结业转维持期，以爬山字据兑现收尾。
- 生活动机真实可信：目标启动易、中断易、重启难，是目标管理的典型全链。

## 2. 连续性与回扣
- T01 立约启动 → T02 处方落地 → T03 暂停冲动 → T04 预支辩解 → T05 两条底线变通 → T06 底线告急 → T07 底线守住 → T08 复诊重启 → T09 平台期终止冲动 → T10 证据换目标 → T11 稳定运行 → T12 结业履约收尾。
- 跨轮引用原子 9 条（message 跨轮 8 条：A08/A13/A17/A19/A23/A29/A32/A34；initial_kb 跨轮 1 条：A26）≥3。
- 冲突型=目标启停类落实：E110_A07（suspended+negation，全盘暂停）、A13（retracted，暂停收回）、A15/A19（撤底线念头起落）、A21/A22（confirmed，重启加量）、A25/A29（终止冲动起落）、A28（confirmed，目标改口径）、A33/A34（confirmed，结业履约）——启动、暂停、变通、重启、终止诱惑、调整全链齐备。

## 3. ME 历史与边界
- ME 阿岩：大学学长，懂营养皮毛，给依据不替人拿主意，口头禅'秤是慢变量，腿是快变量'（initial_kb.me）。
- T04 与 T12 may_decline=true：T04 她预支'别劝'，ME 可不接劝阻话头；T12 履约应约属可轻答场合。
- ME 全程不做医学诊断、不替医生改处方、不替她决定停与不停；围度建议限于'量腰围、看裤子'的常识口径。

## 4. no_reply / 媒体局限
- HER 每轮单条消息，全部为文本；处方内容与医生评价均经 HER 转述并引用，属刻意的信息二传设计。
- 未设 no_reply 轮。局限：打卡记录、腰围数值无媒体呈现，仅口头汇报。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T03（A07 suspended+negation）、T04（A11 加固）、T06（A15 negation）、T09（A25 negation）、T10（A29 retracted + A28 confirmed）、T12（A33/A34 confirmed）。
- 跨轮引用原子 8 条，≥3 条红线达成；required_this_turn 原子 8 条，全部来自早前轮次或 initial_kb。

## 6. 已知风险
- 体重、腰围、处方均为剧情化数据，不构成医学指导；医生评价均出自 HER 转述。
- 渐暧昧关系标注需语义审查确认：全程互动限于损与被损、爬山之约，无越界表达。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(医疗健康, 体重管理门诊+处方打卡+底线降档+平台期换目标, 目标启停类) 与既有 64 篇三元组至少一元不同
