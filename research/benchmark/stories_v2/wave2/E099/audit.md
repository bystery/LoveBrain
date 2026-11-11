# E099 三千块的差价 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 skeleton dedupe 的 title/themes 字段 + wave1/E059 格式参照）。
- 全部人物、店家（A 店/B 店）、价格数字均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。
- 注：STORY_CUSTODY_LEDGER.json 未在仓库中找到（find 无结果），隔离声明以本节替代登记。

## 1. 生活动机
- 域：家居装修；主线：HER 文文封窗换断桥铝——A 店 8800"全包"被立柱纱窗另计项打破（费用冲突第一波），比价 B 店发现便宜在玻璃降级（5 毫米单层对中空），持参数回头压价至 9700 并将"无增项"写入报价单；安装日防爆栓 480 增项撞上书面条款被当场顶回，冲突收敛为"合同内归店家、自选升级自担"；完工后尾款 500 留扣挂钩开春不漏，大风夜窗户无声收尾。
- 生活动机真实可信：报价漏项、安装日增项、口头承诺不落纸、尾款博弈是装修费用纠纷的完整链条，HER 的成长线（新手到会拆单）有真实弧度。

## 2. 连续性与回扣
- T01 首报价 → T02 核漏项 → T03 全包被打破 → T04 比价启动 → T05 参数差异 → T06 压价成交+书面条款 → T07 口头承诺隐患 → T08 增项对峙 → T09 责任划分 → T10 完工回扣五金 → T11 尾款留扣 → T12 大风夜收尾。
- 跨轮引用原子 10 条（A05/A06/A10/A12/A14/A17/A20/A22/A25/A27/A29 中跨轮者 10 条）≥3。
- 冲突型=费用类落实：E099_A06（voided+negation，全包口径被打破）、A13/A14（confirmed，压价成交及其筹码）、A16（negation，打胶未落纸）、A19/A20（书面条款对峙）、A22（retracted+negation，增项由店方消化）、A26（withheld，尾款留扣）。

## 3. ME 历史与边界
- ME 老康：装过两套房，看单子先看漏项，帮人只点问题不替人决定（initial_kb.me）。
- T12 may_decline=true：周五约酒保留拒绝余地；T01-T11 均为征询/共享，无强制回复义务设计。
- ME 全程不替她拍板定店家、不虚构市价与工艺细节；T08 提醒她找老板不跟师傅吵，防止冲突错位；功劳归她的谈判与自查（T06/T10/T11）。

## 4. no_reply / 媒体局限
- 全篇 HER 单条消息，全部为文本；报价单、安装现场均以转述呈现，无图片。
- 未设 no_reply 轮。局限：店家与师傅的原话均经 HER 转述；B 店最终未成交，其后续为刻意留白。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条互异、applicable_dimensions 3-4 个（八维内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T03（A06 voided+negation）、T07（A16 negation 口头隐患）、T08（A20 required 书面对峙）、T09（A22 retracted+negation）、T11（A26 withheld）。
- 跨轮引用原子 ≥3；required_this_turn 原子 8 条（T02/T03/T06/T08/T09/T10/T11/T12），全部引自早前轮次，在 6-9 红线内。

## 6. 已知风险
- 型材"108 系"、玻璃"5+12A+5"、价格数字均为合成，不构成装修行情指导。
- T03 HER 台词"这也好意思叫全包？"含质问语气，参考卡已限定 ME 落点在算账与比价而非拱火。
- T09 的责任划分是 HER 与店家的口头结果，参考卡要求 ME 提醒补微信文字确认，避免口头反复。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=域）/author/author_type/real_user_material=false/readers=["author"]/dev_exposed=false/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(家居装修, 封窗断桥铝报价+玻璃参数比价+增项对峙, 费用类) 与既有篇三元组不同
