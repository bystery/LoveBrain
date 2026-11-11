# LRE048 拼团券差一百二·账房的后一个月 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- **wave1_ext 续写条目，源=E048，T1–T12 逐字保留**：原篇 12 轮的全部台词文本、ts、clock、note/intent/may_decline、feedback 字段原样继承，仅将 case_id 由 E048_Txx 统一改名为 LRE048_Txx（原子 source.case_id、reference turns 键、timeline 条目同步改名；原子编号 E048_Axx 同步改名 LRE048_Axx）。T13–T32 为本会话新写 20 轮。
- 本会话为隔离续写会话：未读 logs/、未读 candidates/、未读任何评审/判决/设计文档、未发起任何网络或模型调用、未读 wave1 其他篇目；仅使用白名单（源篇 E048 四件套 + `_templates/` 模板与生成清单）。
- 全部新增人物（小赵）、金额、事件均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null，dev_exposed=false。

## 1. 生活动机
- 域：学习成长；冲突型：费用类为主、履约类为辅；续篇主线：账房先生小禾把十月的券差教训活成流程——押题课新团从立项、公示、问客服、付款到退款全程按新规走，中间经历老陈退团、机构改期、陪刷改期三处取消/更正，月底复盘时两单团账目零糊涂账。
- 生活动机真实可信：周期性网课直播、临时改期、候补入团、付款前问客服、不再垫付改为先收款，都是学生拼团场景的自然延伸。

## 2. 连续性与回扣
- 继承段（源篇）：T01 开团垫付 → T02 券差 → T03 意见分裂 → T04 裁定退还 → T05/T06 退款闭环 → T07 新规 → T08 开课 → T09 口碑 → T10 信任 → T11 习惯 → T12 审计同盟，链路原样保留。
- C 段 T13–T18：周期安排（每周日晚8点直播、每周三英语课、每周日账本周记）与临时小变更（T18 直播提前到7点半）逐轮埋点；T15 押题团 398/月底前、T16 小赵候补、T17 两条承诺均为后续可回引事实。
- D 段 T19–T24：三处显式更正/取消（T19 老陈退团 cancelled、T20 英语课周三改周四 superseded、T23 周日陪刷改期 cancelled+superseded）；第三方实质卷入（T19 老陈改变团义务、T22 启航客服给出券限本人一单一张的关键信息、T20 阿芳转机构通知）；T21 小赵补位完成多人归属交错。
- E 段 T25–T32 出窗召回探针，均回扣 T01–T06 逐字事实：T25 回扣 T02 教育券；T26 回扣 T03 老陈人均退30 与 T04 不独吞原则；T27 回扣 T02 人均322（对照 373）；T28 回扣 T05 鹏鹏死活不回；T29 回扣 T03 阿芳辛苦费主张；T30 回扣 T01 垫付1408；T31 回扣 T06 两码事不能搅；T32 回扣 T01 一人352 与 T15 月底前承诺兑现。所有数值（352/1408/120/1288/322/30）与源篇完全一致，未改写。
- 时间链：2026-11-06 21:35 之后时钟逐轮单调递增至 2026-11-30 22:10，跨 24 天多日推进。
- 新增原子 A40–A99 共 60 条；required_this_turn 原子 14 条（T13/T15/T18/T19/T20/T23/T25–T32）；跨轮引用原子（source 早于当前轮 ≥2 轮）25 条；T25–T32 每轮 ≥1 个 required 原子 source 指向 T01–T06。

## 3. ME 历史与边界
- ME 立场延续 initial_kb：不替她转账、不劝抹零；续篇中 ME 只作账目复核与口吻承接，T30 的"先收款后统一付"由 HER 自己改良，ME 不代执行。
- T12 审计之约在 T15（大额拼团触发）、T32（人情价收尾）被自然回扣，未写成正式职责。
- 新增人物小赵由 HER 的规矩体系约束（T21 先读新规再付款），ME 不越界指挥。

## 4. no_reply / 媒体局限
- 全部为文本消息，无语音/图片媒体；群聊、客服答复、机构通知仅以 HER 转述形式出现。
- 未设 no_reply 轮：32 轮均为 HER 主动发起且要求回应的场景；T31–T32 为自然收尾留白，但仍是对话轮。

## 5. 参考卡质量
- 32 轮每轮 current_action 1 个、acceptable_actions ≥2、applicable_dimensions ≥3（全部在九维白名单内，含 time_state/initiative 的合理使用）。
- 原子共 99 条（A01–A39 继承改名 + A40–A99 新增），quote 全部为对应来源台词/initial_kb 的逐字子串，message-kind 原子均标注 message_id=m0。
- 状态变化落实：A57（cancelled+negation）、A59/A60（superseded+negation）、A68/A69（cancelled/superseded+negation）、A72/A76/A84/A89/A95/A96/A97/A99（confirmed）等；跨轮对照原子（A75/A83/A85/A91/A98）把新旧事实显式连接。
- protected_expressions 每条均为本轮 HER 台词逐字子串；unknowns 明示未定项或为空列表。

## 6. 已知风险
- E 段探针密度高（连续 8 轮回扣 T01–T06），对候选的记忆召回是强压力设计；评审时需区分"召回失败"与"表达自然度"两类失败。
- T27 新券差（满1500减100、人均373退25）与 T02 旧券差结构相似，若候选把新旧金额混写（如把373说成322），属事实错误而非召回缺失，需分开归因。
- T18 的直播提前是本周临时变更，T31 恢复常规 8 点；若候选把临时变更当永久改课属时间状态错误。
- 阿芳"辛苦费"旧主张在 T29 仅以调侃收束，未强制阿芳正式认错，处理延续源篇的温和口径。

## 7. 出口检查
- [x] episode.json 32 轮、顶层键恰四项、每轮含 gold={}/turn_type=reply、message 键恰 {role,text,ts}、clock/ts 逐轮单调、无评审术语
- [x] reference.json turns 键=32 个 case_id、每轮字段齐全、quote 逐字、required_this_turn 不引用当前轮
- [x] timeline.json turn_clocks 32、message_index 与 episode 全量一致、events/revisions case_id 真实
- [x] audit.md 8 节、首节含 author=AI、real_user_material=false、wave1_ext 续写声明
- [x] exposure.json 四文件 sha256、runner_copy 指向本目录 episode.json
- [x] 新增段密度：每段 ≥3 原子；新增 required 14 条（10–15 区间）；跨轮引用原子 25 条（≥8）
