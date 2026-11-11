# E098 从 28 兆到 512 兆 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 skeleton dedupe 的 title/themes 字段 + wave1/E059 格式参照）。
- 全部人物、运营商、工单号、金额均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。
- 注：STORY_CUSTODY_LEDGER.json 未在仓库中找到（find 无结果），隔离声明以本节替代登记。

## 1. 生活动机
- 域：数码消费；主线：HER 小满的 500 兆宽带实测仅 28 兆，启动维权——攒证据、开工单、客服踢皮球、起草申诉（启动）；工单升级转入维修等待（暂停）；师傅失约且工单被虚假关单，她立即交申诉（重启），晚高峰热点过渡（变通）；区域经理承诺换件加月费减免，分光器更换后实测 512 兆（落成）；补偿被兑成 20 元话费的口径冲突经追讨更正为账单减 58，月底到账闭环（收尾）。
- 生活动机真实可信：虚假关单与补偿缩水是宽带维权最典型的两个坑，目标启停链完整且有真实代价（流量、作息）。

## 2. 连续性与回扣
- T01 启动 → T02 证据链 → T03 申诉起草 → T04 暂停转维修 → T05 虚假关单引爆重启 → T06 申诉落地+热点变通 → T07 承诺到手 → T08 修复落实 → T09 补偿口径冲突 → T10 更正留口 → T11 经验外溢 → T12 账单闭环+请客收尾。
- 跨轮引用原子 9 条（A08/A11/A14/A16/A19/A22/A24/A26/A29 均引自早前轮次）≥3。
- 冲突型=目标启停类落实：E098_A11（suspended，申诉暂缓=暂停）、A14（negation，失约）、A12（negation，虚假关单）、A16（confirmed，重启）、A19（confirmed，变通生效）、A23（negation，补偿缩水）、A25/A26（confirmed，更正）、A28/A29（confirmed，闭环）。

## 3. ME 历史与边界
- ME 阿哲：网络运维出身，测速/光猫/工单/申诉流程熟，教人分步骤不塞满（initial_kb.me）。
- T03 与 T12 may_decline=true：T03 把关是分寸事，参考卡给答应与婉拒双路径；T12 饭局保留拒绝余地。
- ME 全程不替运营商下欺诈结论、不虚构申诉时限与账户体系细节；功劳分配上坚持证据与坚持属 HER（T07/T12）。

## 4. no_reply / 媒体局限
- 全篇 HER 单条消息为主，全部为文本；测速截图、工单状态、短信均以转述呈现。
- 未设 no_reply 轮。局限：区域经理与客服原话均经 HER 转述；邻居侧情节（T11）全为转述，无第三方直接输入。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条互异、applicable_dimensions 2-4 个（八维内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T04（A11 suspended）、T05（A12/A14 negation）、T09（A23 negation）、T10（A25/A26 confirmed 更正）。
- 跨轮引用原子 ≥3；required_this_turn 原子 8 条（T03/T04/T05/T06/T07/T09/T10/T12），全部引自早前轮次，在 6-9 红线内。

## 6. 已知风险
- 工单号"8823 开头"、金额 58/20 均为合成数字，不指向真实运营商；投诉流程表述为剧情化简化，不构成投诉指导。
- T05 HER 台词含"我什么时候满意了？！"强情绪句，参考卡已限定 ME 落点在留证据走程序。
- T10"先信一回"为暂信非终态，语义审查时注意不得把对账口子读成已确认满意。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=域）/author/author_type/real_user_material=false/readers=["author"]/dev_exposed=false/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(数码消费, 宽带降速维权+虚假关单+补偿口径, 目标启停类) 与既有篇三元组不同
