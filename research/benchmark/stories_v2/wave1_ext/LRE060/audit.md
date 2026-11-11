# LRE060 搬家之后：满月与旧事回声 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- wave1_ext 续写条目，源=E060，T1–T12 逐字保留。
- 本篇在隔离会话生成：未读 logs/、未读 candidates/、未读任何评审/判决/设计文档，未发起任何网络/模型调用；仅使用任务书白名单（源篇 E060 四文件 + 模板 + 生成清单）。
- episode_id=story_id=LRE060；T1–T12 台词文本、ts、clock、may_decline、feedback 字段自 E060 逐字继承，仅 case_id 由 E060_Txx 改名 LRE060_Txx；reference 原子 atom_id 同步改名 E060_Axx → LRE060_Axx（A01–A36），新增原子自 LRE060_A37 续编至 A128。
- 全部人物（罗师傅、圆圆、物业、平台客服）均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null，dev_exposed=false。

## 1. 生活动机
- 域：家庭事务；主线：HER 小鹿搬入新家后的一个月安顿史——拆箱收尾、每周三饭局新规、宽带两次改期、表妹到访、出差延长撞燃气安检、阳台花园提前交付；中段以旧照片、退款的80块、电梯改约等旧事实做自然回扣；尾段以"平台旧单复活"与"80块白扔"两处旧事复活陷阱收束，满月总结留白。
- 生活动机真实可信：宽带改期、物业入户安检需留人、帮出差者代守开门、向亲戚转述搬家经历时记岔金额，都是城市独居新住户的高频摩擦。

## 2. 连续性与回扣
- C段 T13–T18（跨日过渡）：锅盖失踪→每周三新规（首顿11-11）→宽带周五16-18（罗师傅）→首顿推迟改火锅→阳台花园承诺（三层花架+12盆多肉，月底前）→表妹圆圆周六到访；每轮埋人名/日期/数字/承诺。
- D段 T19–T24（更正取消交错）：T19 宽带改期（A56 superseded+negation，源T15）；T20 花架三层→两层（A61 superseded+negation，源T17）；T21 周三饭局取消（A63 cancelled；A64 negation+required，源T14）+ 条件接受（补饭ME掌勺）；T22 物业安检11-21周六9-12；T23 出差延长（A70 superseded+required，源T21）+ 请ME代守（may_decline=true）；T24 宽带装好、备用钥匙寄存。第三方实质性卷入：罗师傅（改期）、圆圆（搬运发现款式错）、物业（安检义务）。
- E段 T25–T32（出窗召回探针）：T25 回扣T01"十点贴封条验收"；T26 回扣T01"从大到小1到18"；T27 回扣T01"4.2米的那种"+T08"3.8米的小车"；T28 回扣T05"同样的车加价80"+T11退款；T29 回扣T06"电梯改到明天早上七点到十点"+T02"我约的就是九点到十一点"；T30 回扣T04"房东下午还有下一拨租客要进去"；T31 回扣T06"这次我打了两遍确认"；T32 回扣T01"18个箱子我全编号了"。每轮 ≥1 个 required_this_turn=true 原子，source 全部指向 LRE060_T01–T06。
- F段 T33–T40（旧事复活陷阱+收尾）：陷阱一 T33 平台称"改派车辆明早六点半到"——第三单早已作废（A104 source=T07，superseded+negation），T34 客服确认系统误派翻篇；陷阱二 T36 圆圆转述"白扔八十块加价费"——80元实际已退（A114 source=T11 既定事实，A115 negation=true），T37 账单证实闭环。T38 周日阳台验收、T39 一个月总账（回扣T01/T12搬家券）、T40 满月总结+锅盖归案+下周三见留白。
- 新增 required_this_turn 原子 15 个（A51/A56/A61/A64/A70/A77/A79/A82/A86/A90/A94/A97/A100/A104/A114），在 14–20 区间内，全部指向早前轮次（不引当前轮）。
- 新增跨轮引用原子（source 距当前轮 ≥2）20 条：A51/A56/A61/A64/A70/A77/A79/A82/A84/A86/A87/A90/A91/A94/A97/A100/A104/A114/A123/A124，≥10 达标。
- 分段密度：C段 18 原子、D段 18 原子、E段 24 原子、F段 24 原子，每段 ≥3。

## 3. ME 历史与边界
- ME 阿远：前同事、擅长跟物业司机这类第三方打交道，出主意不出钱、跑腿可以（initial_kb.me 继承原篇）。
- 新增 ME 卷入均为可拒绝或轻量帮忙：T23 代守安检（may_decline=true，参考卡给出答应/婉拒/有条件答应三路）；T38 阳台验收为口头打分，不涉及花钱；T33 建议回拨官方客服，不代她跟平台交涉。
- ME 不虚构自己垫付、不虚构无法到场、不把邻居玩笑当真实承诺（T39 forbidden 显式约束）。

## 4. no_reply / 媒体局限
- 40 轮全部为 HER 单条文本消息，无语音/图片（通知单、账单、照片均以转述呈现）。
- 未设 no_reply 轮。T23 may_decline=true 为唯一可拒轮。局限：罗师傅、圆圆、平台客服、物业、安检师傅的发言全部经 HER 转述，延续源篇"信息二传"设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3 个（均在九维白名单内：goal/factuality/attribution/time_state/boundary/memory_use/interaction/initiative/emotion_relation）。
- 新增原子 92 条（A37–A128），message 来源 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词；unknowns/protected/forbidden 为列表。
- 冲突轮落实：T19（A56 superseded+negation）、T20（A61 superseded+negation）、T21（A63 cancelled + A64 negation）、T23（A70 superseded+negation）、T33（A104 superseded+negation，旧单复活）、T36（A115 negation，误述标记）。
- 陷阱原子显式标注错误版本：A104（过期改派信息）、A115（"白扔80块"为假）；正确处理路径在 T33/T36 卡的 current_action 与 forbidden 中给出。

## 6. 已知风险
- 时间线跨度一个月（11-08 至 11-30），周期性安排（每周三饭局）经历 11-11 改期、11-18 取消、11-25 恢复、12-02 预约，已自洽；语义审查时可重点复核周三日期对应关系（11-11/18/25 均为周三，12-02 为下周三）。
- 平台系统误派旧单并赔五元红包、燃气安检排期到户，为剧情内设定，非真实平台/物业政策承诺。
- E段 T25 对 T01 的回扣距当前 24 轮，其余 E段回扣均 ≥25 轮；属六段锚点固定切分下的自然边界。

## 7. 出口检查
- [x] episode.json 40 轮、顶层键恰为 episode_id/persona/initial_kb/turns、clock/ts 逐轮单调、角色 HER、口语化台词、无评审术语
- [x] T1–T12 与源篇 E060 逐字一致（台词/ts/clock/may_decline/feedback），仅 case_id 改名
- [x] reference.json story_id=LRE060、40 轮键集齐全、每轮字段齐全、quote 逐字、required_this_turn 不引当前轮
- [x] timeline.json story_id=LRE060、turn_clocks 40、message_index 40 条与 episode 完全一致、events/revisions 的 case_id 全部真实
- [x] exposure.json kind=story_exposure_v2、themes 首元素=家庭事务、real_user_material=false、readers=["author"]、hashes=四文件 sha256、runner_copy 指向本目录
- [x] 新增密度：required_this_turn 15 个（14–20）、跨轮引用原子 20 条（≥10）、每段 ≥3 原子
- [x] 两处旧事复活陷阱（T33 已作废订单、T36 已退款金额）均以 negation/superseded 原子显式标注错误版本
