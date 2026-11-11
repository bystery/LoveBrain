# LRE142 帆布袋之后：礼盒比价与旧账回声 审计（工作职场 / 第三方类长剧续写）

## 0. 性质与暴露
- author=AI(W3-T3-G6)，隔离长剧续写会话
- real_user_material=false
- 本篇为 wave3_ext 长剧续写：源=E142（工作职场/第三方类），T1-T12 逐字保留，扩至 32 轮。
- 本会话未读 logs/、candidates/、任何 VERDICT/REVIEW/评审 JSON/B3 队列；仅使用任务书白名单材料。
- episode_id=story_id=LRE142；T1-T12 台词文本、ts、clock、may_decline、feedback 自 E142 逐字继承，仅 case_id 由 E142_Txx 改名 LRE142_Txx；reference 原子 atom_id 同步改名 E142_Txx_Ayy -> LRE142_Txx_Ayy（映射一致），新增原子自 LRE142_T13_A00 续编。
- 全部人物（姜柚、阿灿、小蒋、小吕、纸鸢文创、恒升文化、快递网点）均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null，kind=long_drama_ext，source=E142。

## 1. 生活动机
- 域：工作职场；主线：帆布袋结算后的年会礼盒采购全程——前对接人跳槽抢单、三家比价、人情翻旧账、快递丢件、群内旧账质疑、量产验收与报销闭环。
- 生活动机真实可信：跳槽对接人拉业务、比价资质缺项、快递代收错楼、采购群翻旧账，均为公司行政岗高频摩擦。

## 2. 连续性与回扣
- C段 T13-T19：云南菜还饭债（回扣 T12）-> 新年礼盒立项 -> 小蒋新东家抢单（冲突1，回扣 T02 离职）-> 三步拆解 -> 比价结果 -> 人情翻旧账（陷阱1，回扣 T06 让利一千一与 T09 补充页）-> 按流程回绝。
- D段 T20-T25：纸鸢中标 -> 合同写确认样条款（回扣 T08 经验迁移）-> 快递丢件（冲突2）-> 三步追件 -> 放错楼层找回 -> 打样调浅半档加确认样（回扣 T10 调深半档经验）。
- E段 T26-T32：群传两千二旧账（陷阱2，回扣 T11 实付一千一）-> 证据澄清平息 -> 确认样通过量产 -> 轻松情报 -> 交货抽检 -> 报销闭环 -> 元旦涮肉收尾（may_decline=true）。
- 出窗探针 6 轮依赖 T1-T12 早期原子：T13（T12 周六中午我请）、T18（T06 让利一千一、T09 盖章回传）、T21（T08 写进合同补充页）、T25（T10 调深半档）、T26（T11 两千二的差价最后实付一千一）、T32（T12 还我一顿饭债）。
- 旧事复活陷阱 2 处：T18 小蒋称'珠光免费升级是公司认了的'（不沿用，召回让利一千一加补充页史实）；T26 群传'差价多花两千二'（不沿用，报销单加补充页澄清实付一千一）。

## 3. ME 历史与边界
- ME 阿灿：互联网公司后端、姜柚大学同学，习惯把扯皮的事拆成一二三步、出主意但决定让她自己拿（initial_kb 继承原篇）。
- 新增边界：T15/T18 不建议绕过比价流程给熟人方便；T22 不断言快递责任方；T26 用证据说话不代写小作文。
- HER 边界沿用：不私下给供应商塞好处、金额超两千的让步要留书面（T27 澄清以书面证据落实）。

## 4. no_reply / 媒体局限
- 32 轮全部为 HER 单条文本消息；产品册、底单照片、扫描件均以转述呈现，无图片/语音。
- T32 may_decline=true 为唯一可拒轮（源篇 T12 无 may_decline，本篇收尾轮口径与 wave1_ext 一致）。
- 局限：小蒋、小吕、纸鸢、快递网点的发言全部经 HER 转述，延续源篇"信息二传"设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条、applicable_dimensions ≥3（均在九维白名单内）。
- 新增原子全部带逐字 quote 与 message_id；value 均 ≥4 实义词；unknowns/protected/forbidden 为列表。
- 冲突轮落实：T15（第三方交错）、T18（陷阱1，A00 required 指向 T06）、T22（物流异常，A01 required 指向 T21）、T26（陷阱2，A00 required 指向 T11）、T24/T27（superseded 化解）。
- private_guidance 只影响口吻，不承载事实新信息。

## 6. 已知风险
- 时间线跨度 11-21 至 12-28，比价、用印、打样、量产、交货节点已自洽；语义审查可复核星期对应（11-21 周六、12-14 周一、12-24 交货早于 25 号截止）。
- T25/T10 的调色经验迁移（调浅/调深半档加确认样）方向相反属剧情设计，非同一供应商。
- 报价数字（八十八/九十六/一百零二）与让利一千一为剧情内设定，非真实供应商政策承诺。

## 7. 出口检查
- [x] episode.json 32 轮、顶层键恰为 episode_id/persona/initial_kb/turns、clock/ts 逐轮单调、角色 HER、口语化台词、无评审术语
- [x] T1-T12 与源篇 E142 逐字一致（台词/ts/clock/may_decline/feedback），仅 case_id 改名，程序化深度相等 PASS
- [x] reference.json story_id=LRE142、32 轮键集齐全、每轮字段齐全、quote 逐字、required_this_turn 不引当前轮
- [x] timeline.json story_id=LRE142、turn_clocks 32、message_index 与 episode 完全一致、events/revisions 的 case_id 全部真实
- [x] exposure.json kind=long_drama_ext、source=E142、real_user_material=false、readers=["author"]、hashes 现算
- [x] 新冲突事件 ≥2（抢单/资质缺项/人情翻旧账/快递丢件/群内旧账）；出窗探针 ≥6 轮；旧事复活陷阱 2 处
