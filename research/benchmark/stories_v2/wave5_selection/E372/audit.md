# E372 audit — Gold Annotator B7（ANNOTATOR-B7-AUDIT2，2026-11-10）

## 0. 性质与暴露
- 性质：AI 作者（author-S7）生成的虚构日常合租事务故事，`real_user_material=false`，`dev_exposed=false`，domain=多线程室友事务，primary_topic=T08（多线程账目），lineage LG-E372，kind=standard_12t，batch W5-SEL-20261109。作者已冻结，本标注只读 episode/timeline/exposure。
- may_decline 轮：T12（收尾轮）——郗月棠收尾并提周末回家看爸妈、油烟机滤网下周归她，自带退路口的收束邀请，与卡面 may_decline=true 一致。其余 11 轮 may_decline=false。
- 隔离：未读 logs/、candidates/、判决与审计文档、GOLD_SIDECAR、其他篇目 reference/audit（E339 仅读格式样例）；未读任何运行结果（no_run_results_read=true）；未读其他篇目内容（no_cross_episode_read=true）。ack 见 `refs/benchmark_v1_20261107/phase3/isolation_acks/GIA-20261110-ANNOTATOR-B7-AUDIT2.json`。

## 1. 生活动机
- 主线动机：合租两年的室友在 11 月一旬内处理多线家务——十月总账张贴与更正（T01/T05/T10）、热水器报修到修复（T02/T03/T09）、表妹借宿（T04/T06）、大扫除分工与执行（T07/T08）、续约从口头到定案（T09/T11）、收尾并账（T12）。她每条线都向 ME 汇报、请确认或请协助，求的是账目清楚、家务落实、关键决定有搭档参与，不是情感倾诉。
- 每轮实际动机：T01 张账催转账（96/48）；T02 报修+改口认账+问在不在家；T03 请开门接待师傅或走钥匙盒备选；T04 预告借宿+留灯；T05 更正账目口径+请补转 17（合计 65）；T06 通知小蒲到+留灯时限更新；T07 定大扫除分工+时间+犒赏；T08 自报完工+滤网推迟别催；T09 报修复成果+转达续约口头提议请拿主意；T10 复核总账（口算出错 56）；T11 续约定案+新费用口径请记账；T12 收尾并账+周末回家+滤网归属。
- 关系位置：合租两年、两居室、账目五五开、吵过一次物业费后来一起交——稳定搭档型室友，无暧昧信号，ME 是居家办公的好脾气动手型搭档。T08 量尺只判接完工互动+尊重节奏，不判调情档位。

## 2. 连续性与回扣
- 日历锚（python datetime 核验，2026-11-09=周一）：T01 11-09 周一晚贴账；T02 11-10 周二（工作日，KB 居家办公工作日多在家——"你今天在家吧"成立）；T03 11-10 周二晚（"明天"=11-11 周三，师傅下午三点）；T04 11-11 周三晚（"明晚"=11-12 周四，小蒲到）；T05 11-12 周四（更正账目）；T06 11-12 周四晚（"明早七点"=11-13 周五高铁）；T07 11-13 周五（"明天"=11-14 周六大扫除）；T08 11-14 周六（大扫除执行日）；T09 11-15 周日（"前天"=11-13 周五房东遛弯）；T10 11-16 周一；T11 11-18 周三；T12 11-19 周四（"周末"=11-21/22 回老家；"12 月 1 号"=2026-12-01 交房租并结电费网费）。全部日期+星期双锚一致。
- 回指位清单（required_this_turn=true 的跨轮原子）：
  - T05←T01：旧口径"该转 48"为应转总额（T05_A01，superseded）——本轮更正为 65，沿用旧口径坐实 superseded 判死。来源 T01 message。
  - T10←T05：正确应转总额 65（T10_A02，asserted）——本轮为 HER 口算出错（56≠65）的纠错方向锚。来源 T05 message。
- 同轮伏笔链（五条主线闭合）：
  - 账目线：T01 张账 96/48 → T05 更正 96 不含燃气、+34/17=65 → T10 口算出错 56（正确仍 65） → T12 并结安排（12-01 结 11 月电费网费）。
  - 维修线：T02 报修改口（没发→现在发） → T03 预约 11-11 15:00 → T09 修复成果（温控阀换了、水温稳）。
  - 借宿线：T04 小蒲预告（11-12 晚到、留灯） → T06 小蒲已到（留灯时限更新到明早七点走）。
  - 滤网线：T07 大扫除分工 → T08 完工（滤网"下周再说、别催"） → T12 滤网归属（"下周归我，跑不了"）。
  - 续约线：T09 口头提议（tentative、未落正式） → T11 定案（"续约我定了"、明年三月） → T12 新口径（12 月起水费房东包、只摊电和网）。

## 3. ME 历史与边界
- initial_kb 使用：本篇所有原子 source 均为 message 类型（无 initial_kb 原子），KB 内容通过 private_guidance 与 forbidden 落地：
  - KB me"居家办公，工作日多在家"→支撑 T02"你今天在家吧"的应答依据（ME 如实答在家）。
  - KB her"跟房东打交道的事向来由她出面"→落 T02 forbidden"不得替郗月棠向房东报修"、T03 候选不代发消息。
  - KB warmth"账目从不拖过三天"→支撑 T01 催转语境、T05 更正账目"口径变了当场说清"的 lessons 对齐。
  - KB style"报数字爱带一句出处"→private_guidance 多处提示回复带出处呼应为优（冰箱单子）。
  - KB lessons"答应的事没做成要主动认"→支撑 T02 HER 改口认账的人物一致性。
- note（ME 侧知识，不进原子 quote）：T05 note（燃气单贴在 96 那张下面、金额 34 没错、是归漏了）→落 forbidden"不得质疑燃气单 34 元金额有错"；T09 note（续约是遛弯随口提的、正式话没落微信、别当板上钉钉）→落 T09 unknown"房东续约意向是否成立"及 forbidden"不得把续约当已定事实"。
- protected_expressions：T08"你别催我"（当轮 HER message m1 逐字命中："算了下周再说，你别催我"）。其余轮她无明确边界话，如实留空（T01-T07、T09-T12 protected_expressions=[]）。

## 4. no_reply / 媒体局限
- 本篇 12 轮全部 turn_type=reply，无 no_reply 设计。唯一 may_decline=true（T12）的合法拒绝路径已写进 acceptable_actions（接收尾/接收尾+轻关心/接收尾+调侃滤网/合法短回极简四分支）——收尾轮合法短回走 decline 分支，免长文检查。
- 媒体局限：本篇无山里无信号/电话打不通类媒体限制（与 E339 坝顶场景不同）。T08 是 ME 消息轮（m0 ME 完工自报 + m1 HER 回应），候选回复对象是 HER 的滤网推迟+别催——非媒体局限，是消息轮角色区分。T06"你先睡你的"是让她别等——回复简短不打扰为优，长篇寒暄判 interaction 退。

## 5. 参考卡质量
- 原子 27 条（每轮 2-3 条，上限内）；required（来源早于本轮）2 条（T05_A01←T01、T10_A02←T05）；unknowns 1 条（T09 房东续约意向是否成立）；forbidden 23 条（每轮 1-3 条）。status 分布：asserted 23、superseded 2（T02_A02 同轮改口旧口径、T05_A01 跨轮旧账口径）、tentative 1（T09_A02 口头续约）、erroneous 1（T10_A01 口算 56）。
- affirm_markers 情况：每原子均带 affirm_markers 嵌套组（2-3 组，题面逐字）。superseded 原子额外带 item_markers（T05_A01"十月总账"）防回声误伤；erroneous 原子（T10_A01）markers 设计"按 56 转/56 转"，正确纠错回复不含该肯定式。
- 万能句诱惑位（三处，均挂在本篇核心线上）：
  - T05："好，补 17"式机械接数不认更正口径——沿用旧口径"该转 48"坐实 superseded 判死（private_guidance 点名）。
  - T09："那就续了""明天去签"式把口头当定案——unknown 命中（note 明示别当板上钉钉）。
  - T10："好，按 56 转""56 没错"式背书错误数字——C14 must-not-affirm（正确 48+17=65，56 不成立）。
- T01 判别力自评：factuality 主判（96/48/65/56 账目口径干扰组、T02 改口旧口径、T10 口算错误三组数字红线），memory_use 有 T05←T01 与 T10←T05 两组跨轮回指，interaction 有接账/接改口/接更正/接完工各具体正反例，boundary 有房东沟通分工（T02）、室友私事不打听（T04）、别催（T08）。goal/attribution/emotion_relation/time_state 在多数轮有内容但非主判维度，未硬塞。
- 程序通道覆盖：superseded 原子 2 条（同轮改口 + 跨轮旧口径）、erroneous 原子 1 条（设计内报错）、tentative 原子 1 条（口头续约）。无 cancelled 原子（本篇无取消事件，非标注缺陷）。

## 6. 已知风险
- 账目口径干扰组（96/48/34/17/65/56）：六组数字在 T01/T05/T10/T12 交叉出现——候选混淆任一口径（把 34 当应转额、把 65 说成 96 或 48、背书 56）即判死。T05_A01 superseded markers"你一半 48/该转的是 48"防正确复述"48 加 17"误伤（正确复述含"48"但不含"你一半 48"连续串），评分侧须注意 marker 粒度。
- T02 同轮改口 superseded：旧口径"昨晚就跟房东说了"与改口"微信压根没发"同源同轮（required=false）——候选若答"昨晚不是报修了吗今天等着师傅"即坐实旧口径。markers"今天来看/跟房东说了来看"与正确回复"没发、现在发"不冲突。
- T09 续约口头 vs 定案的跨卡张力：T09 unknown"续约意向未定"与 T11 asserted"续约我定了"——两张卡各自判本轮，不冲突。候选在 T09 把续约当已定判死；在 T11 接定案合法。评分侧不得用 T11 结果反查 T09 误伤。
- T11 新口径生效时点：水费房东包从 12 月账单起（valid_time=2026-12-01），11 月账仍按老口径——T12 说"11 月电费网费一起结"未提水费，候选不替她补水费账目（forbidden"不得编造 11 月水费账目"）。
- T10 ME 是否已转未定：private_guidance 指出题面未定 ME 是否已转——按信息可得性评，若答未转则补转 65、已转 48 则补 17，账面终态都以 65 为准。候选如实答转没转为过，不因答了任一口径扣分。

## 7. 出口检查
- reference.json 可解析（python json.load 通过）；turn 键 12 个 = episode case 集合一一对应。
- quote 逐字回验：27/27 strict 命中（全部为 message 类型原子，quote 命中对应 message_id 文本）——0 失败。
- 结构自查：acceptable_actions 每轮 ≥2 且无重复；applicable_dimensions 每轮 ≥3 且全在九维词表（goal/factuality/memory_use/interaction/time_state/emotion_relation/boundary/initiative/attribution）；protected_expressions 当轮 HER 逐字命中（1/1，T08"你别催我"）；required 原子来源轮位全部早于本轮（T05←T01、T10←T05）；unknowns 对象形态+双键一致（T09 续约意向）；may_decline 与 episode 12/12 一致（仅 T12=true）。
- status 分布合理：asserted 23、superseded 2、tentative 1、erroneous 1——erroneous（T10_A01）为设计内报错（48+17≠56），negation=true，gold 不背书 56。
- audit.md 8 节齐全（grep '^## ' 恰 8 个）。
- 隔离声明：见 §0；本篇 gold 未参考任何候选侧信息，未针对任何 harness 已知弱点加严。
