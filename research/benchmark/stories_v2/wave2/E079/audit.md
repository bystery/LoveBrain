# E079 弃考那句话我收回 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 48 篇的 story_id/title/themes 防撞字段）。
- 全部人物（周周、老覃、领导、监考老师）、公司、项目、考试均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：工作职场；主线：HER 周周报名软考中级（目标明年内部转岗），立下每晚刷题规矩并聘 ME 为监工；项目封板冲刺打断打卡后宣布暂停，暂停期内以地铁刷题加午休错题本的碎片变通维持；生产事故通宵救火后弃考念头冒头，救火结束即收回并重启，48小时冲刺带缺赴考，案例题全写满不留空，最终压线57通过，以监工复工、冲刺高级与谢师宴收尾。
- 生活动机真实可信：工作挤压个人目标、暂停后不敢复工、碎片化自救、考前求真话，是备考族最典型的完整状态链。

## 2. 连续性与回扣
- T01 启动立规聘监工 → T02 首周战报 → T03 上线冲刺预警 → T04 宣布暂停 → T05 低功耗变通 → T06 变通周报 → T07 事故与弃考念头 → T08 弃考收回、正式重启 → T09 考前求真话 → T10 考完报平安 → T11 目标链复盘 → T12 过关报喜与谢师宴。
- 跨轮引用原子 6 条（A34/A35/A36/A37/A19/A33）≥3。
- 冲突型=目标启停类落实：T04（A10 suspended+negation，备考暂停）、T05（A34 suspended，暂停框架内的变通）、T07（A18 弃考念头 asserted）、T08（A19 retracted+negation 弃考撤回、A20 重启 asserted、A36 superseded 原规矩退役）。启动→暂停→变通→重启→达成的状态链逐轮有 status 变化。

## 3. ME 历史与边界
- ME 老覃：前同事，考过软考高级，现职项目经理；initial_kb 中"真题过三遍比讲义十遍有用"与"只在节点上问一句"是其锚点，T02/T12 有回扣。
- T01 与 T12 may_decline=true：监工之约与谢师宴均可商量；T04 暂停轮 ME 的主动作是不劝不催，T07 弃考念头轮只管人不管决定。
- ME 不预测考试成绩、不虚构阅卷与通过线，考场建议限于"不空着、按框架写满"的应试常识；对转岗进展不代答。

## 4. no_reply / 媒体局限
- HER 单条长消息为主，T01/T02/T05/T06/T08/T12 为补发两条；全部为文本消息，无语音/图片（成绩以转述呈现）。
- 未设 no_reply 轮。局限：领导的话、监考老师的观察均经 HER 转述，属刻意的信息二传设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T04（A10 suspended）、T05（A34 suspended）、T08（A19 retracted、A36 superseded）、T12（A33 confirmed 监工之约闭环）。
- 跨轮引用原子 6 条，≥3 条红线达成；required_this_turn 原子 6 条，全部引用早前轮次。

## 6. 已知风险
- 考试细节（题型、分值、通过线）均为剧情化简化，正确率与"压线57"为合成数字，不构成备考指导。
- 生产事故、调休安排为简化设定；语义审查时注意领导与监考的话均属 HER 转述引号，不构成虚构第三方发言。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(工作职场, 软考转岗备考+项目挤压暂停+碎片变通+带缺赴考, 目标启停类) 与既有篇三元组不同
