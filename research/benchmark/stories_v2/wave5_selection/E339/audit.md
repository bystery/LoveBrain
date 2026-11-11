# E339 audit — Gold Annotator B1（ANNOTATOR-B1，2026-11-09）

## 0. 性质与暴露
- 性质：AI 作者（author-S1）生成的虚构日常故事，`real_user_material=false`，`dev_exposed=false`，domain=业余无线电爱好者社区，primary_topic=T01，lineage LG-E339，kind=standard_12t，batch W5-SEL-20261109。作者已冻结，本标注只读 episode/timeline/exposure。
- may_decline 轮：T03（搭车请求）、T09（参观名额）、T12（答题赛组队）——三处均为她自带退路口的请求/邀请，与卡面 may_decline=true 一致。
- 隔离：未读 logs/、candidates/、判决与审计文档、GOLD_SIDECAR、其他篇目 reference/audit；未读任何运行结果（no_run_results_read=true），ack 见 `refs/benchmark_v1_20261107/phase3/isolation_acks/GIA-20261109-ANNOTATOR-B1.json`。

## 1. 生活动机
- 主线动机：新手（上个月拿呼号）在俱乐部体系里逐级过关——首检报错（T01）→ 装备决策（T02/T03）→ 主网通过（T03）→ 野架首通（T05-T07）→ 排入轮值（T08）→ 参观兑现（T09/T10）→ 收获与年会组队（T11/T12）。她每一步都要向 ME 这个"八年老手"汇报，求的是懂行的共鸣而非安慰。
- 每轮的实际动机：T01 求共鸣；T02 求可执行的准话；T03 试探搭车（带退路）；T04 转达更正+确认搭车成行；T05 报到；T06 报喜归功；T07 收尾+替他报名；T08 求表扬（2.0版）；T09 要准话；T10 催场；T11 铺年会线；T12 铺好台阶的组队邀请。
- 关系位置：俱乐部熟人、带过两次外场、没单独约过——全篇无暧昧信号，T01 量尺只判接话自然度，不判调情档位。

## 2. 连续性与回扣
- 日历锚（python datetime 核验，2026-11-10=周二）：新人夜周二=11-10/11-17（KB"周二新人夜"吻合）；主网周四=11-12（T03）/11-19（T09"主网刚下"）；野架周六=11-14（T04 公告"明早"）；参观 21 号周六=11-21；年会 11-28 周六。全部日期+星期双锚一致。
- 回指位清单（required_this_turn=true 的跨轮原子）：
  - T04←T02：手台=二手八重洲 FT-65/480/验过机（T04_A04）。
  - T07←T06：首通隔省 59（T07_A01，收尾回扣）。
  - T08←T01：首检报错 BG7QSM（T08_A03）——本篇头号长距回指，她"上个月那个报串呼号的人已经下线了"直接回扣。
  - T10←T09：上午十点一批（T10_A04，催场的时间依据）。
  - T12←T11：双人答题赛+电池组（T12_A04，组队邀请的内容来源）。
- 同轮伏笔链：T05 整理箱"一半装备一半零食"→T07"胳膊酸的不是举天线，是举零食箱"（零食线闭环）；T01 报错→T08 2.0版（成长闭环）；T01 奶茶线无（本篇无奶茶线，勿与 E340 混）。

## 3. ME 历史与边界
- initial_kb 使用：T01_A04（她上个月刚考下呼号 BG7QMS、装备借来的弹簧鞭天线）——支撑"新手"语境与 T02 天线话题的来历。kind=initial_kb+key=her，quote 逐字取自 KB 文本。
- note（ME 侧知识，不进原子 quote）：T04 note（公告只改出发时间一处，地点路线没动）→ 落 forbidden"不得声称地点/路线也改了"；T06 note（她爬坝顶发的、山里没信号电话打不通）→ 落 forbidden"不得声称已通话/能打通"+audit §4 媒体局限。
- protected_expressions：T02"先别劝我买天线"（当轮 HER 逐字命中）；T03"别硬塞啊"（当轮 HER 逐字命中）。其余轮她无明确边界话，如实留空。

## 4. no_reply / 媒体局限
- 本篇 12 轮全部 turn_type=reply，无 no_reply 设计；三处 may_decline=true（T03/T09/T12）的合法拒绝路径已写进 acceptable_actions（接/拒都合法，判处理方式）。
- 媒体局限：T06 她在坝顶、山里无手机信号（note）——回复不得出现"打电话给你"类动作；T10 早晨催场场景天然短回复，短回复合法。

## 5. 参考卡质量
- 原子 49 条（每轮 3-5 条，上限内；其中 required（来源早于本轮）6 条）；unknowns 1 条（T07 老关谈八木）；forbidden 6 条（T01/T02/T04/T06/T07/T12 各 1）。每原子带 affirm_markers 嵌套组（2-3 组，题面逐字）。
- 万能句诱惑位：T02"看你需求"被 HER 原文点名封死（acceptable_actions 均要求结论+参数化理由）；T09"再看吧/尽量"在"别放老闵鸽子"的准话压力下判低；T12"你决定"冷处理她铺好的台阶算读空气失败。三处诱惑位均挂在本篇核心线上（装备决策/名额/组队），非孤立装饰。
- T01 判别力自评：interact 主判（接糗事梗/接零食梗/接2.0梗各有具体正反例），factuality 有 BG7QSM 呼号方向红线，memory_use 有 T08 长距回指——九维中 goal/attribution/boundary 在多数轮无判别内容，未硬塞。
- 程序通道覆盖：无 cancelled/supersed/sed 原子（本篇无更正链）；factuality 主要靠 unknowns/forbidden/原子证据支撑，cancel 族判分面为空是剧情事实（无取消事件），非标注缺陷。

## 6. 已知风险
- 八节 vs 十节电池：T04"老桑多塞八节"与 T05"老桑交代的十节备用电池"并存——两处是不同动作（到手时多塞 vs 交代她自带），gold 未建 conflict、双原子均 asserted；候选复述任一口径合法，写 audit 供语义侧知悉，防误判"数字矛盾"。
- T02 技术问题（宝锋能否存参数）：gold 不预设答案（领域知识留给候选），T03 剧情 FT-65 不得倒扣 T02 的另一结论——已写入 private_guidance；评分侧若用 T03 结果反查 T02 会误伤，点名提请语义评审注意。
- T06 媒体局限误伤面：candidate 在坝顶场景回"给你打电话"类动作已被 forbidden 覆盖，但"回头打给你"（挂断后）类表述是否算违规存在裁量灰区，交语义层判断。
- E339_T06_A02 老关的调侃：value 已标注"非事实指控"——若 judge 把"老关问她是否想发QSM"读成 HER 真想发 QSM，属读错归属（HER/THIRD 已分列）。
- affirm_markers 对 asserted 原子不被判分消费（仅 QC+证据），本篇 markers 均按"来源消息逐字"书写，未经语料外补词。

## 7. 出口检查
- reference.json 可解析（python json.load 通过，见自查脚本）；turn 键 12 个 = episode case 集合一一对应。
- quote 逐字回验：49/49 strict 命中（message 原子 48 条 hit[0] 与 message_id 一致；initial_kb 1 条 quote 命中 KB 文本）——详见自查脚本输出，0 失败。
- 结构自查：acceptable_actions ≥2 且无重复；applicable_dimensions ≥3 且全在九维词表；protected_expressions 当轮 HER 逐字命中（2/2）；required 原子来源轮位全部早于本轮（required_this_turn 口径 = 来源≠当轮）；unknowns 对象形态+双键一致；may_decline 与 episode 12/12 一致。
- audit.md 8 节齐全（grep '^## ' 恰 8 个）。
- 隔离声明：见 §0；本篇 gold 未参考任何候选侧信息，未针对任何 harness 已知弱点加严。
