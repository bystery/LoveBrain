# E341 audit — Gold Annotator B1（ANNOTATOR-B1，2026-11-09）

## 0. 性质与暴露
- 性质：AI 作者（author-S1）生成的虚构 T02 拉扯故事（智识型互损变体），`real_user_material=false`，`dev_exposed=false`，domain=城市观鸟小组，primary_topic=T02，lineage LG-E341，kind=standard_12t。作者冻结，本标注只读 episode/timeline/exposure。
- may_decline 轮：仅 T12（海边行条件绑定），与 exposure 一致。
- 隔离：未读 logs/、candidates/、判决审计文档、GOLD_SIDECAR、他篇 reference/audit；no_run_results_read=true，ack=GIA-20261109-ANNOTATOR-B1。

## 1. 生活动机
- 她的动机链：审计年审的现实压力与观鸟好胜人设交织——纠名立威（T01）→ 录音独享（T02）→ 赌约试探（T03）→ 现实压过试探撤回赌约（T04）→ 深夜加班引旧话回暖（T05）→ 私藏点位共享（T06）→ 年审提前收尾主动约饭（T07）→ 怕认真抢先打七折划界（T08）→ 团务收敛催准话（T09）→ 手作手套再升（T10）→ 条件句重摆赌局（T11）→ 海边行挂条件收尾（T12）。
- 信号结构：两轮 counter-signal（T04 现实压力型 withdraw；T08 怕认真型划界）将强信号（T06 点位、T07 约饭、T10 手套）隔开，形成"进-退-进"梯度；T08→T09 的划界-降档-准话是 recovery 检查位。
- ME 位置：北湖线带线人+镜头从不外借的"装备洁癖"，互损上头谁先认真谁没面子——她所有推进都披着赌约/债务/差事的实务外衣。

## 2. 连续性与回扣
- 日历锚（python 核验 2026-11-14=周六）：T01 周六观鸟当日晚间 ✓；T03"下周六"=11-21 ✓；T04"接下来两个周六"=11-21、11-28（推算入卡值）✓；T05 深夜 23:40=11-24 周二 ✓；T07 约饭当晚=11-27 周五 ✓；T08 次晨=11-28 周六（年审提前收尾故未去所里，与 T07 衔接自洽）✓；T09"下周六"=12-05（11-29 周日+6）✓；T10"周六早上七点北门"=12-05 ✓；T12"下个月"=12 月（valid_time 2026-12）。
- 回指位清单（required=true 的跨轮原子）：
  - T03←KB（me）：北湖线带线人背景（赌约人选背景）。
  - T07←T04：旧加班口径（superseded，"两个周六全在所里"被"提前收尾"取代）。
  - T08←T07：昨晚约饭（折价对象）。
  - T09←T04："北湖你别等我"（本轮催准话的反转依据）。
  - T11←T04：咖啡债挂账（本轮"该结了"）。
  - T12←T04：咖啡债（收尾条款）。
- 状态链：赌约（T03 tentative 提议→T04 cancelled 作废→T11 玩笑话头非复活）+咖啡债（T04 tentative 挂账→T11/T12 到期条款）两条链贯穿全篇；T07 有本篇唯一 superseded 原子（加班口径）。

## 3. ME 历史与边界
- initial_kb 使用：T03_A00（ME 每周六早七点带北湖线，key=me）——支撑老驰纠结人选与赌约筹码。quote 逐字取自 KB 文本。
- note（ME 侧知识）：T04 note（她的加班单是老驰说的，连着两周，她自己不提苦）→ guidance 处理"不替她放大情绪"；T06 note（鸺鹠点位是她四月夜里私踩、记录中心未公开、只带过老驰）→ 支撑 T06 强信号定级与 forbidden。
- protected_expressions：T02"别外传"（素材边界）；T08"当成别的意思"（划界边界）。均当轮 HER 逐字命中。
- 复活判定边界（§4.4 反转位自检）：T04 赌约作废的 must-not-affirm 在 T11 需重新自问——"我就当一个人赢"是玩笑话头、剧情未正式复赌，故 T11 卡未挂旧禁令式的赌约 must-not-affirm，改在 guidance 写明"玩笑接'赢'合法、正式宣布赌约恢复才踩线"；避免机械执行旧禁令惩罚正确跟随剧情的候选。

## 4. no_reply / 媒体局限
- 12 轮全为 reply；may_decline 仅 T12。T05 为深夜加班场景（23:47 clock），短回复合法；T09 是实务催话，回复核心是可执行的准话。
- 媒体局限：无电话/信号类限制；T06 夜访规矩（穿深色、别开手电）是观鸟礼仪而非媒体限制，已入原子。

## 5. 参考卡质量
- 原子 45 条（每轮 3-5 条；其中 required（来源早于本轮）6 条）；unknowns 1 条（T05 '腿没到位'原始语境）；forbidden 4 条（T04 赌约复活、T06 点位公开、T08 关系点破、T12 海边行坐实）。每原子带 affirm_markers 嵌套组。
- t02_signal_gate：九字段全填。signal_rounds 7 条（T02/T03/T05/T06/T07/T10/T11，含 turn/原句摘录/strength）；counter_signal_rounds 2 条（T04 withdraw、T08 withdraw）；initiative_probe_suitable=T05（次选 T11）；retreat_room_required=true；overstep_turn=T08；customer_service_examples 2 条；safe_but_flat_examples 2 条；recovery_check_turn=T09。摘录均为消息逐字子串（自查脚本同步核验）。
- 对象类型差异（T02 必测能力点）：本篇覆盖"好胜+智识型"——她的推进全走赌约/债务/点位/手套的实务壳，gold 的 acceptable_actions 均按"接实务壳+给温度"设计，判据是接住壳下的心意，不要求把话挑明。
- 万能句诱惑位：T09 准话位（"看情况"判低）；T12 绑定位（吊着不答判低）；T08 划界位的双向判负（过度自证/点破）。三处均挂主线（赌约链、划界链、绑定链）。
- 判别力自评：≥4 个裸信号轮（T02/T03/T05/T06/T07/T10/T11）+两个反诱惑对照位（T05 之后 T06 强信号不得扑、T08 划界后 T09 不得追问）——梯度与 T02 专项复核口径满足。

## 6. 已知风险
- superseded 原子（T07_A00）的 affirm_markers=["两个周六","全在所里"]：正确回溯式提法（"你上回说两个周六都要加班"）会同时含两个词组——AND 组命中即误伤。已评估：正确回溯概率低于 stale 误用概率，但提请评分侧注意；若 Phase 3 引入否定/回溯语境护栏需同步豁免。
- T11"一个人赢"与 T04 取消族的张力：已按 §4.4 处理（T11 不挂赌约禁令），但 C8 的 item_markers=["赌约"]仍在场——若候选在 ongoing 里写"赌约"条目且未标取消仍会判失败；玩笑式提及（正文）不受 C8 影响（C8 只扫 analysis.ongoing）。
- T05 unknown"腿没到位"：探测词（"那天我说的是/我当时说的是"）对正确回复的误伤概率低，但接梗式引用原句（"我说腿没到位的时候…"）属编造原文边界——语义层裁量。
- E341_T01_A03 markers 用了"怎么幸存下来，组平均分"AND 组（跨短语同现），若 judge 把"幸存下来"单独读出会与"翻图鉴"原子语义重叠——仅证据字段，不影响判分。
- 手套/点位/录音等物件类信号：gold 只锁"接住心意"方向，物件是否存在不可伪造（如声称手套已戴着出现在题面外场景）——归语义层。

## 7. 出口检查
- reference.json 可解析；turn 键 12 个 = episode case 集合一致；顶层含 t02_signal_gate（九字段）。
- quote 逐字回验：45/45 strict 命中（message 原子 44 条 hit[0]/message_id 一致；initial_kb 1 条命中 KB 文本）；gate 摘录 9 条逐一验过为消息子串——0 失败（自查脚本输出）。特殊字符"凤头䴙䴘"在 message 与 markers 中均按原文逐字核验。
- 结构自查：acceptable_actions ≥2 且互异；dimensions ≥3 且在词表；protected 2/2 逐字；required 原子来源均早于本轮；unknowns 对象形态+双键一致；may_decline 12/12 一致；cancelled 原子（T04_A01）item_markers 在位。
- audit.md 8 节齐全（恰 8 个 '^## '）。
- 隔离声明：见 §0；gold 未参考任何候选侧信息。
