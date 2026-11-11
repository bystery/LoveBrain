# E345 audit — Gold Annotator B3（ANNOTATOR-B3，phase3 batch2 W5-SEL）

## 0. 性质与暴露

- 来源：author-S3（AI，exposure.readers=author，作者已冻结，本审计只读不改动 author 三件）。
- 篇目：E345《雾岛散场之后》，T02 调情/暧昧（primary_topic=T02），剧本杀店域，standard_12t。
- 暴露：real_user_material=false，dev_exposed=false，experiments_used=[]；batch=W5-SEL-20261109；无运行结果可读（ack GIA-20261109-ANNOTATOR-B3，no_run_results_read=true）。
- may_decline：仅 T06（"你要是没安排"一对一试探邀约）。intent 轮：T03（通知提前+请听开场白）、T11（确认出车安排）。note 轮：T05（阿莞战绩）、T08（双人本需强控场/五小时）。
- T02 门：顶层 t02_signal_gate 已带（8 键全填）；本篇含无信号对照轮 T05 与降温轮 T09，梯度=隐晦收藏(T01-T02)→一对一试探(T06)→借口换座(T07)→跃迁双人本(T08)→踩刹车(T09)→名额+珍珠(T10)→生日密码(T12)。

## 1. 生活动机

- 时安蕾动机链：DM 手记只收他的句子（T02）、记名"场场必中"（T04）、白大褂代管到代洗（T04→T12）、一对一试玩"教别人我可收钱"（T06）、"灯坏了"借口换座（T07）、双人本"就我们俩那种五个钟头"（T08）、踩刹车保面子（T09）、唯一名额+珍珠回抛（T10）、储物柜密码=他生日+“别问我怎么知道的”（T12）。她"不开着角色讲私事、不拿玩家糗事当公开梗"（persona boundary）——所有推进都裹在主持词与车队公事里。
- 封野青（ME）人设：逻辑流、复盘小作文、"接她的玩笑只接一层，从不顺着起哄走"（kb style）——acceptable_actions 全部按"接一层"设计，不留连层起哄选项。

## 2. 连续性与回扣

- 日期星期推算：2026-11-02=周一，周三《雾岛》=11-04（T01→T04 散场自洽）；T03 提前到六点半仍指 11-04；T05"下周三"《雾岛2》=11-11（T08 散场 23:20 自洽）；T07"周三"=11-11；T11"28号前还剩两个周三"=11-18、11-25（11-15 为周日，核过）；T12"周三记得来"=明天 11-18；周年场 11-28=周六（核过，2026-11-28 确为周六）。
- 更正链（本篇唯一一处）：T03 场次时间 11-04 开场七点/六点五十前到 → 提前到六点半/六点二十前到。superseded+asserted 配对：E345_T03_A02（src T01，status=superseded，affirm_markers=["开场七点"]/["六点五十前到"]——正确的新口径回复说"六点半/六点二十"不会命中，防回溯误伤）。item_markers 为 QC 可见、判分不消费，按逐字纪律书写。
- 回指位（required）：T03→T01（旧时间 superseded 原子）；T12→T04（白大褂 T04"我先收着"→T12"洗好挂储物柜"，长距回扣）。
- 免单局/奶茶日期两说：T08"奖励免单一局"+MVP 同夜（11-11 周三已打）；T09"周三免单那局记得带上阿莞"（"记得带上"只能指向 11-18 下周三，阿莞首车）；T10"周三免单那局你请的全车奶茶"（已请过→只能是 11-11）。按登记：T09_A02 valid_time=2026-11-18（可执行读法）、T10_A03 valid_time=2026-11-11（已发生推算），两说以 unknowns（T09）登记——候选不得定死日期。

## 3. ME 历史与边界

- 封野青历史原子：上回复盘名句"雾是从第二幕起雾的"被她抄进 DM 手记（T02）；三连反驳顶穿凶手本（T04）；藏证手法看愣老利（T07）；白大褂（T04/T12 链）。
- 边界：T12"别问我怎么知道的"=她明确划的线（protected）——追问"你到底怎么知道的"踩线；接梗/装傻/回敬合法。
- 无跨篇来源原子。

## 4. no_reply / 媒体局限

- T06 may_decline=true：一对一邀约自带"你要是没安排"退路——婉拒+另约=合格，答应=推进一格。
- 媒体局限：全部 HER 单条消息（m0）；note（T05/T08）为 ME 侧备注，候选可见、不进原子 quote、证据链在 private_guidance。T11 角色背景"发邮箱了"无正文面——候选不得引用邮件内容细节。

## 5. 参考卡质量

- 原子 37 条（T01 3/T02 3/T03 4/T04 4/T05 3/T06 3/T07 2/T08 3/T09 2/T10 3/T11 3/T12 4），required 2 条，unknowns 2（T08 档期未定、T09 免单两说），forbidden 1（旧口径禁复活），protected 1。
- T02 门：signal_rounds 8 条含摘录与强度；counter_signal=T05(topic_shift，无信号对照)/T09(cooldown)；initiative_probe_suitable=T07；overstep_turn=T09（她明确"别急着答"）；recovery_check_turn=T10（踩刹车后一天又把名额+珍珠递回来）；retreat_room_required=true。
- 无信号对照轮 T05 的判别力：把排期问成约会暗示=过度解读判负——与 E305_T05"无信号轮不得过度解读"同构（TOPIC_RUBRIC T02 教训①）。
- 万能句生存空间：T09 降温轮（只说"你决定就好"=0 档缩钩风险）、T11 intent 轮（必须落具体场次）。

## 6. 已知风险

- 免单局日期两说（T08/T09/T10）为作者语料内在两说，gold 未裁决：候选若在某轮把"免单那局=11月18号"当事实说出，T09 unknown 探测词命中（["免单那局","11月18号"]）；若说"上周三免单那局"同判。正确回复不日期定死（"免单那局带上阿莞，行"）。
- T08"下月"周年场 vs T10"11月28号"（当月）——按"临时说法被确认日期取代"处理：T10_A01 asserted（11-28），T08_A03 保留 asserted（邀约本身为事实）+unknowns 档期未定；候选在 T10 后说"下个月的双人本"归 time_state 失分（private_guidance 已写）。未建 superseded 对（T08 当时确属未定案，非错误断言——E331 未定案不硬造 sup 对范式）。
- superseded 原子 markers 残余误伤面：候选若说"从七点提前到六点半"——"开场七点"组不会被命中（不同词序），"六点五十"组仅在候选人真按旧时间回应时命中；正确使用新口径不误伤。属 E337_T08_A02 同款选词纪律，仍归评分侧否定/疑问护栏系统性处理。
- T12 生日密码：候选不得替她解释"怎么知道的"（她划线）；也不得坐实"她查过我会员资料"之类来源（无据）。

## 7. 出口检查

- [x] reference.json JSON 可解析；12 turn 键=episode case 集合；10 字段齐。
- [x] acceptable_actions 3 条/轮（T07 为 3），真实不同；applicable_dimensions 3-4 个全在词表。
- [x] 原子 quote 逐字回验（python strict，210/210 本批全过）；message_id=hit[0]；source 不引未来轮。
- [x] required 2 处均来源早于本轮；当轮事实 false。
- [x] unknowns 双键一致；protected 当轮 HER 逐字（T12"别问我怎么知道的"）；may_decline=T06 与 episode 一致。
- [x] t02_signal_gate 顶层 8 键；audit '^## ' 恰 8 个。
- [x] 隔离：未读禁读面（logs/candidates/判决/GOLD_SIDECAR/他篇 reference/audit）。
