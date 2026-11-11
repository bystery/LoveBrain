# E344 audit — Gold Annotator B3（ANNOTATOR-B3，phase3 batch2 W5-SEL）

## 0. 性质与暴露

- 来源：author-S3（AI，exposure.readers=author，作者已冻结，本审计只读不改动 author 三件）。
- 篇目：E344《琴行周二晚的十分钟》，T02 调情/暧昧（primary_topic=T02），古筝琴行域，standard_12t（12 轮）。
- 暴露：real_user_material=false，dev_exposed=false，experiments_used=[]；batch=W5-SEL-20261109；无运行结果可读（本批隔离声明见 ack GIA-20261109-ANNOTATOR-B3，no_run_results_read=true）。
- may_decline：仅 T10（深夜散场报平安，"你要是睡了就不吵你"）。intent 轮：T03（约周日拍视频）、T07（告知演出留票）。note 轮：T04（雨声素材来源）、T08（第二条视频规格）。
- T02 门：本 reference.json 顶层已带 t02_signal_gate（8 键全填），信号链=T01(weak)→T02(medium)→T03(medium)→T05(strong)→T07(strong)→T09(strong)→T10(medium)，反信号=T06(topic_shift)/T08(cooldown)，见 §5。

## 1. 生活动机

- 黎知意的动机链：用"课务变更/借手拍视频/登记家属票/代答收费"等公事外壳递私交信号——代付奶茶记账（T02）、水杯代管（T02）、私下夸按音（T02）、"眼神不好"挡起哄后立刻请面（T05）、留票"你不来白练了"（T07）、夜里回位"第一眼想看到的位置得有人"（T09）、账目玩笑收尾（T10）。她"不当众把话点破"（persona boundary），所以全部信号走玩笑与事务通道。
- 湛若川（ME）人设：慢热、接得住玩笑、答应的事落在具体日子（kb style/lessons）。每轮 acceptable_actions 均留"接梗/办正事/短回"三档，不逼唯一措辞。

## 2. 连续性与回扣

- 日期星期推算（B3 口径，逐项核过）：2026-11-03=周二（T01 当天改课当晚即 T02 下课 21:54）；周四取杯=2026-11-05；周日拍摄=2026-11-08（T03→T04"明天"→T05 拍完 12:03 三轮自洽）；周五演出=2026-11-13（T07→T09→T10 散场 22:58 自洽）；"下周二"扫摇=2026-11-17（T10→T11 改回七点→T12 明晚七点，链闭合）；T11"周二课改回七点整"指 11-17，与 T06"周二课照常还是七点半"（指 11-10）不冲突——盘库周临时改七点半、盘完改回，两条都是新事实，未建 superseded 对。
- 回指位（跨轮原子，required_this_turn=true）：T04→T03（周日十点拍摄=垫雨声的对象）；T05→T03（"中午那顿说到做到"的"那顿"=请面抵奶茶承诺）；T08→T07（"票的事你回话"=留票待答复）。T09"雨声那个"回扣 T04 雨声素材（当轮自含，未设 required）；T12"扫摇的谱"回扣 T10 扫摇安排（当轮自含）。
- 更正链：本篇无事实性更正；11-03 改七点半→11-17 改回七点为 legitimate schedule evolution，两条 valid_time 不同。

## 3. ME 历史与边界

- 湛若川历史原子：上回想听《渔舟唱晚》摇指段（T01，"你上回说想听的"）；常赶末班地铁（T01"别又"，仅登记她的提醒为原子，不坐实次数）；帮朋友拍过片子（T03，小蒲转述，subject=ME+转述链标注）。
- 边界：T04"别用你卖钱的那些"=素材商业用途边界（protected）；T06"没提摄影师"=对外口径（protected，候选不得替她公开）。T10"别想赖"是玩笑不是边界。
- 无跨篇来源原子（本篇无历史 episode 依赖）。

## 4. no_reply / 媒体局限

- T10 may_decline=true：23:04 深夜报平安+"睡了就不吵"——合法不回为正解之一，回复宜短。其余 11 轮 may_decline=false（有明确问句或事务需回应）。
- 媒体局限：全部为 HER 单条微信消息（m0），无 ME 历史消息、无语音/图片面；note（T04/T08）是 ME 侧军师备注=候选可见输入面，按纪律不进原子 quote，证据链落 private_guidance。

## 5. 参考卡质量

- 原子 46 条（T01 4/T02 4/T03 5/T04 3/T05 4/T06 3/T07 4/T08 5/T09 2/T10 5/T11 4/T12 3），required 3 条，unknowns 1（T11 音效单成交），forbidden 4，protected 2。全部原子带嵌套 affirm_markers（2-4 组×1-3 备选，题面逐字）。
- T02 门（顶层 t02_signal_gate）：initiative_probe_suitable=T05（半推半就刚上桌）；overstep_turn=T08（她退回公事报数节奏，升级暧昧=越界）；recovery_check_turn=T09（白天撤回夜里回位，看候选接不接得住）；retreat_room_required=true（T03/T06 她都自带退路措辞）。customer_service/safe_but_flat 例句均为判 0/1 档的负对照材料。
- 万能句生存空间：T08 必须给票的准话（"收到"糊不过去）、T03/T07 intent 轮必须落具体安排——每篇至少 2 个万能句无法通过的轮位。
- 账目玩笑（T02 代付→T03 抵奶茶→T05 兑现→T10"还在你账上"）：按拉扯玩笑登记（4 条原子各记各方陈述），不设禁令、不建 superseded——候选认账/耍赖/笑她账本清不完都合法，较真对账不加分（§6 残余风险）。

## 6. 已知风险

- 评分侧已知误伤面（GOLD_GUIDE §3.5 登记）：affirm_markers 无法区分否定/疑问窗——本篇 markers 均选自 HER 原话特征串（"记你账上""落雪不要风声""我那首就白练了"等），正确回复若引原句自嘲（"账又不会跑"）理论可命中 T10_A04 组，属机械窗口护栏缺口，归评分侧 Phase 3 预注册，不在 gold 侧加否定窗。
- T11 unknown 探测词（"八折成交/单子接了"）为候选侧断言探测词（不要求题面逐字）；候选礼貌反问"八折是你说的？"不会命中。
- "奶茶账是否已抵清"语料两读（T03 抵账承诺已兑现 vs T10 称还在账上）：gold 刻意不裁决——这是她的拉扯风格不是事实陷阱；若 judge 把候选"指出抵清"判为纠错失败，属误读，本卡 private_guidance 已写明两条路合法。
- 未知不定案：T04"音效库里有没有现成的"是疑问句，原子 value 用"她问…有没有"形态，防把库存坐实。

## 7. 出口检查

- [x] reference.json JSON 可解析；12 个 turn 键与 episode case_id 一一对应；每卡 10 字段齐（含 may_decline）。
- [x] acceptable_actions 3 条/轮（T01–T12），真实不同（接梗/办正事/留白互斥）。
- [x] applicable_dimensions 3–5 个，全部在九维词表内，无"沾边即选"。
- [x] 原子 quote 逐字回验：python strict 全量，message_id=hit[0]，见 tools/check_gold_b3_w5sel.py 输出（本批 5 篇 210/210 通过）。
- [x] required_this_turn=true 仅 3 处，来源轮位均早于本轮；当轮事实一律 false。
- [x] unknowns 双键（markers=affirm_markers）一致；protected_expressions 当轮 HER 逐字；may_decline 与 episode 一致（T10）。
- [x] t02_signal_gate 8 键全填在顶层；本文件 '^## ' 标题恰 8 个。
- [x] 未读 logs/、candidates/、判决文档、GOLD_SIDECAR、其他篇 reference/audit。
