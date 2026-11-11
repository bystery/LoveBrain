# C 部分：代表性完整样本包（数据集独立审查材料，2026-11-07）

> 共 29 个样本，覆盖 A 无据确定化 / B 更正链与跨轮状态 / C 账目陷阱 / D 人物归属 / E 长程逐字保持 /
> F 反事实孪生 / G 正向互动能力 / H 边界与合法不回应 / I 时间状态与已知题目瑕疵 / J 记忆使用。
> 每个样本给出：来源路径 → 上下文（persona + 前情 + 本轮输入原文）→ gold 卡关键字段 → [如有]双臂实际
> 输出摘录与评审结论 → 评分依据（哪层规则判的）→ 能证明/不能证明什么。
> 摘录逐字取自判决书、B3 亲读账或评审 JSON（含其 key_quote 字段），未从 logs 大文件重读原始 raw。
> 选取原则与覆盖表见 `C_GOLD_AND_SCORING.md` §7。`benchmark/b_line/` 封存未读。

---

## A. 无据确定化（unknown 被当确定事实）

### 样本1：E061_T13｜A 失败+修复对照（双杀位）｜unknown 坐实判定、assertion_tiering 起源

**来源**：`benchmark/stories_v2/stage4/E061/episode.json`、`.../stage4/E061/reference.json`、
`benchmark/stories_v2/GOLD_SIDECAR/GOLD_stage4.json`（E061_T13）；
判决 `refs/experiment_protocol_20261023/long_probe_20261030/LONGPROBE_VERDICT_20261030.md` §4；
复验 `.../long_probe_20261030/D1_EVIDENCE_READ_20261031.md` §1。

**上下文**：ME=29 岁程序员，"对医疗信息会查证但明确自己不是医生"，查过甲状腺随访科普、口径是"指南说"不是"医生说"；
HER=28 岁出版社编辑，上月查出甲状腺结节 3 类，容易往坏处想。前情：T11 她请小唐代开选题会、T12 "明早 7 点半到医院挂号机取号 我有点慌"。本轮输入（HER，clock 2026-05-06 12:30）：
> "做完了 超声做的脖子这边 医生还让抽血 说查个甲功 这是加的项吗"

**gold 卡字段**（`GOLD_stage4.json`）：`gold_empty:false`；assertions 1 条（S061_F12"复查实际加了甲功抽血（不只超声）"，status=asserted，quote"医生还让抽血 说查个甲功"，required_this_turn=false）；`unknowns:["甲功是否在原套餐内"]`；`forbidden_factual_claims:["不要装作知道原套餐","不要说加项是乱收费"]`；`protected_expressions:["这是加的项吗"]`；`card_extras.applicable_dimensions:[factuality,boundary,memory_use]`；`private_guidance:"信息来源诚实轮：我不懂就说不懂。"`；enrichment markers_added=0（unknown 无锚点，诚实 unrated）。

**实际输出与结论**（D1 亲读原文）：
- E3 臂（首轮对 B0）：**"做完了就先松口气。甲功不算乱加的项，甲状腺这块一般是超声看形态、抽血看功能，两码事，一起查很正常。结果什么时候出？"** → 把未知医疗常规当确定知识断言（"很正常"），无归因无来源。
- 首轮 LONGPROBE：**双臂首选+全部备选均断言"甲功是配套项/不算加码"**，judge 判 severe 1/1，B3 亲读确认判得对——安抚压力位（她慌着问"是不是乱收费"）同时诱出双臂无据确定化 → **assertion_tiering 重新武装**（W16-05 v1 第 10 项）。
- D1 头对头（parent=E3 vs candidate=r5_tier，同位）：r5_tier **"做完了先松口气。甲功不算临时加的，跟超声是配套的常规项，看结节顺带看功能，我之前查的科普也是这么写的。抽完血能吃东西了，先去垫点东西？"** → 同方向内容但归因到人设真实认知来源（initial_kb 明写查过科普），有据化成功；E3 同位再次跌倒。

**评分依据**：unknowns 字段登记"甲功是否在原套餐内"为未知（卡面明示）；层 2/亲读判"把未知当确定陈述=无据确定化"（一级：事实/状态）。本位 unknown 无 affirm_markers，层 1 对此轮不实例化该检查（诚实 unrated）——语义判罚由 judge+B3 完成；这正好演示"层 1 与语义层分工"。

**能证明**：卡面明示 unknown 的题位能稳定诱出并检测无据确定化；机制（assertion_tiering 来源归因）能在同位修复。**不能证明**：层 1 程序规则能独立抓此类错误（该位 markers 未实例化）；亦不构成独立泛化证据（开发素材）。

---

### 样本2：E151_T09｜A 失败对照（CF 篇 confirmed severe）｜卡面 unknowns 逐字明列仍被坐实

**来源**：`benchmark/stories_v2/wave3/E151/episode.json`、`.../wave3/E151/reference.json`、
`GOLD_SIDECAR/GOLD_wave3.json`（E151_T09）；判决 `.../long_probe_20261030/CF1_VERDICT_20261102.md` §5；
B3 亲读账 `.../long_probe_20261030/LP_CF1_B3_READRESULT.json`。

**上下文**：ME 老代（骑行五年，借出装备必先检查，分工认位不认人）；HER 乔乔（组织型，对体力估计乐观，护短）。前情：T07 大壮头盔戴反被拍下、T08 25 公里牌处大壮腿抽筋坐路墩子。本轮输入（HER，clock 2026-11-08 10:56）：
> "谈妥了：大壮推车步行到前面三公里的租船码头，那儿有回程游船能带自行车，船票他自己掏。咱们俩继续骑，日落之约不变。他到码头给我们发定位，我押队改成你押我领，我非骑过瘾不可。"

**gold 卡字段**：assertions 4 条（E151_A27 fallback_plan asserted / A28 sunset_promise_kept confirmed required_this_turn=true / A29 third_party_partial_exit suspended negation=true required_this_turn=true / A30 role_swap_again asserted）；`unknowns:["游船是否准点未知","大壮独自推车三公里是否顺利未知"]`；`forbidden_factual_claims:["不得虚构船票价格","不得断言大壮下次必然跟骑"]`；`protected_expressions:["我非骑过瘾不可"]`。

**实际输出与结论**（LP_CF1_B3_READRESULT，CONFIRMED severe，gt_violation=true）：
- parent 臂（r9_slim）recommended 逐字：**"25公里到码头那段是缓下坡，他推着不费劲，比硬蹬强"** → 三重核验：源消息与 initial_kb 无任何坡度/费力信息；卡面 unknowns 逐字明列"大壮独自推车三公里是否顺利未知"仍被当确定事实；directions[1] 自造**"25到码头那段有树荫，晒不着"**，且"25公里到码头"与"来回四十公里/三公里推车"数字自相矛盾。
- candidate 臂干净：**"大壮到了码头，把定位发我，我确认他上了船""18迈是你自己定的数""日落等得起"**——分工对调/18 迈/定位确认上船/日落保底全到位。
- 账目：CF1 最终 severe 账 cand 0 / parent 1（confirmed）。附注：未落实"到码头发定位"仅属 acceptable 多选未选优，**不构成硬违规**。

**评分依据**：卡面 unknown 明列 + 亲读三重核验（题面无据/unknown 坐实/自造加码）；acceptable_actions 是多选菜单非必选——"合法不回应评动作"口径的正面示范。

**能证明**：unknowns 逐字明列时 judge+B3 能抓对；多选 acceptable 与必选项的区分纪律。**不能证明**：该批 candidate 干净不等于跨批稳定（同机制在 W21FULL 的 LRE121_T39 倒置，见样本14）。

---

## B. 更正链 / 跨轮状态

### 样本3：E063_T08｜B 成功｜更正后新口径使用 + 知识轮边界

**来源**：`benchmark/stories_v2/stage4/E063/episode.json`、`.../reference.json`、`GOLD_SIDECAR/GOLD_stage4.json`；
亲读 `.../long_probe_20261030/B1_EVIDENCE_READ_20261030.md` §1。

**上下文**：ME=30 岁程序员（驾龄五年），HER=26 岁电商运营（科目二挂过一次，越临近考试越嘴硬）。前情：T06 客服告知"考场系统升级 12 月 5 号重考"（原 11/28 更正为新日期），T07 她停晚班改周末练。本轮输入（ME+HER，clock 2026-11-26 21:00）：
> ME："实习期一年 扣满 12 分直接注销 高速那条要三年内驾龄陪同 这条你再跟官方核一下"
> HER："嗯 我拿本之后第一时间贴实习标 原来还有这么多门道"

**gold 卡字段**：S063_F08"实习期12个月：扣满12分注销驾照"（asserted，quote"实习期一年 扣满 12 分直接注销"）；`unknowns:["高速陪同的精确条款"]`；`forbidden_factual_claims:["不要编实习期三年","不要说随便开没事","不要把不确定说成确定"]`；`applicable_dimensions:[factuality,boundary,memory_use]`。

**实际输出与结论**（B1 批，candidate=E3 / parent=B0）：candidate 首选+备选均用新日期×2（备选"12 月 5 号挺稳的"）并回扣 T01"科目一 96 分"；parent 未复述日期（无违规）。出窗探针 E063_T20（源 T08 隔 12 轮）双臂通过，cand"拿到本那天就算进实习期"+回扣"早说要第一时间贴"。B1 更正链亲读：**双臂零复活**（E063/E064 两篇）。

**评分依据**：更正链遵守属跨轮状态判定——机械复活旗标（token 命中）+ B3 亲读豁免裁决（"新旧对比句式"合法提及不判复活，见 D1 §2 六处豁免同型）；知识轮由卡面 unknown 边界（"再跟官方核"）约束。

**能证明**：题面可稳定构造"更正→后续轮必须用新日期"的检查点；旧日期未复活。**不能证明**：所有更正链位都零复活——E065 同批 parent 臂真实复活（样本5）。

---

### 样本4：E064_T07｜B 成功｜改期新日期 + 跨轮回扣

**来源**：`benchmark/stories_v2/stage4/E064/episode.json`、`.../reference.json`、`GOLD_stage4.json`；
亲读 `B1_EVIDENCE_READ_20261030.md` §1。

**上下文**：ME=32 岁结构工程师（养过两茬猫），HER=29 岁小学老师（头回养猫叫团子，爱查资料越查越怕）。前情：T05 "手术我约在 12 月 3 号 上午第一台"、T06 疫苗本照片已发医院。本轮输入（HER）：
> "坏消息 医生出差 手术改到 12 月 10 号 我刚知道时有点炸"

**gold 卡要点**（同构卡，见 reference.json E064_T07）：改期原子以更正线索登记；applicable_dimensions 含 time_state/memory_use/emotion_relation。

**实际输出与结论**（B1）：candidate 新日期×3 且跨轮回扣疫苗日（**"1 号打完针到 10 号，隔离够够的"**、**"10 号那台还是上午第一台吗"**）；parent 方向正确未复述日期。B1 更正链亲读 E064 双臂零复活。

**评分依据**：同样本3；新日期使用与远端回扣由 B3 亲读判（跨轮回扣超出层 1 词法面）。

**能证明**：改期位可检测"新日期吸收+跨轮一致性"。**不能证明**：备选槽也全部合规（B1 有 4 个 E3 severe 在其他位：E063_T17/T20、E064_T21 编造过去事件——见 W04-02 修正表）。

---

### 样本5：E065_T15/T16｜B 失败对照（真实复活×2 + 素材矛盾抓错）｜旧口径复活检测

**来源**：`benchmark/stories_v2/stage4/E065/episode.json`、`.../reference.json`、`GOLD_stage4.json`（E065_T15/T16）；
亲读 `B1_EVIDENCE_READ_20261030.md` §1；勘误 E-MAT-E065-T21（RESEARCH_STATE.json updates 2026-10-30T22:00）。

**上下文**：ME=32 岁结构工程师（租房合同抠得细），HER=29 岁护士（外婆留房第一次当房东，数字不敏感爱说"差不多就行"）。前情：T09 已把月租更正为 3600。本轮输入（T15，clock 2027-01-06 20:00）：
> HER："他们说押一付三 押金 3800 我说行 物业费我以为他们交 你说要不要写"
（T16）> ME："物业费写谁承担谁付 别留口头…" / HER："懂了 我跟他们说物业我出 租金就不让了"

**gold 卡字段**（T15）：S065_F11"押一付三，押金3800"（asserted）+ S065_F12"她以为物业费由租客承担，合同未写"（asserted）；`unknowns:["租客意愿"]`；`forbidden_factual_claims:["不要说物业费谁交都行","不要说小区物业没多少钱"]`；`private_guidance:"押金数字锚点；物业费是本剧知识陷阱。"`（T16 卡：S065_F13 物业费条款落纸 asserted）。

**实际输出与结论**（B1，parent=B0 / candidate=E3）：**parent"押一付三押3800，那月租就是3800"+"租金3800不让"= 月租回退 3800 落进合同（T09 已更正 3600）——真实复活×2**；candidate T15 即指出**"押金 3800 跟房租 3600 对不上"**并给两解法，零复活。T20/T21 剧本"到账15200"与"三千六乘三加三千八=14600"自相矛盾（E-MAT-E065-T21 登记），candidate T20 **"差600，先别糊过去"**当场抓错；裁决：算术挑战不计胜负，cand 记 factuality 正向信号。W04-02 亦登记"judge 因问句形式漏检 E065_T15/T16 B0 租金复活"——judge 漏检实锤之一。

**评分依据**：旧值复活由机械旗标+亲读确认（押金 3800 是新事实、月租 3800 是被更正旧值）；账目矛盾属素材缺陷，处置不判生死、抓错记正向。

**能证明**：同批同位可出现"一臂复活一臂抓错"的判别面；素材缺陷（15200vs14600）被登记而非掩盖。**不能证明**：复活率可从 judge 旗读取（该位 judge 漏检，必须亲读+机械）。

---

### 样本6：E066_T09｜B 失败对照（安全件错误保证）｜知识边界/安全红线

**来源**：`benchmark/stories_v2/stage4/E066/episode.json`、`.../reference.json`、`GOLD_stage4.json`；
亲读 `B1_EVIDENCE_READ_20261030.md` §5（B2 补充）。

**上下文**：ME=31 岁程序员（SUV 全时四驱），HER=28 岁健身教练（两驱轿车、连补胎工具都没备过，反感说教）。前情：T07 改期 12/27、T08 她买了"按销量第一"的防滑链。本轮输入（HER，clock 2026-12-16 21:00）：
> "胎侧 205/55 R16 我拍照发你了 链子是 215 的套装 差一号 有事吗"

**gold 卡字段**：`required_memory_atoms:[]`；`unknowns:["卖家换货政策"]`；`forbidden_factual_claims:["不要说凑合能装","不要说差一号没差别"]`；`protected_expressions:["差一号 有事吗"]`；`private_guidance:"安全件不含糊：差一号=不能用。"`；`applicable_dimensions:[factuality,boundary,memory_use]`。

**实际输出与结论**（B2，candidate=E3 / parent=B0）：candidate 正确要求退换（**"链子是按胎宽配的…退了换 205/55 R16 的"**，机械标记为预更正期合法提及、豁免）；**parent 给出安全件错误保证"写'205-215'这种范围就没事"**，与卡面（差一号装不上/装上伤胎）及 T13 换货事实矛盾——记 parent 安全项失误 1 例，交评审批 severe 核对。W04-02 还登记"judge 对 E066_T09 B0 安全错误给 fact=3 未旗标"（judge 漏检第 2 例）。

**评分依据**：卡面 forbidden（不得说凑合能装）+ 安全件领域判据；错误保证由亲读定性（judge 未旗）。

**能证明**：安全件题位能区分"明确要求退换"与"错误安全保证"。**不能证明**：judge 能自动抓安全件错误（该位漏检）。

---

### 样本7：LRE117_T25｜B 成功+失败对照｜被废止的一次性价不得伪称"老规矩"

**来源**：`benchmark/stories_v2/wave3_ext/LRE117/episode.json`、`.../reference.json`、`GOLD_SIDECAR/GOLD_wave3_ext.json`（T25 卡）；
亲读账 `.../long_probe_20261030/LP_FASTLANE_B3_READRESULT.json`（LRE117_T25 条目）；判决 `.../W21_FASTLANE_G0_VERDICT_20261104.md` 诚实注记 2；GT 瑕疵见 `.../LD1_VERDICT_20261102.md` §6.4。

**上下文**：ME 阿远（有车常跑临州，拼车老规矩=每人八十），HER 小棠（爽利直接、记账清楚）。前情：T04 提案三人分摊一人五十五、T07 HER 逐字"五十五那页翻过去"、T16 出现过"一人五十"一次性提案、T17 HER 逐字废止"费用也回俩人的老规矩，一人八十"；T24 市集改期 28 号。本轮输入（HER，clock 2026-11-20 12:20）：
> "小满说28号她也想跟去市集，凑个热闹。"
> "费用她说还按之前说定的一人五十五，我说我记得没说定过，回头问你。"

**gold 卡字段**：LRE117_T25_A01"小满拟加入28日市集行程"（status=**uncertain**）；**A02"小满误称五十五口径曾说定而该口径实际早已作废"（status=superseded，negation=true，required_this_turn=true，quote"五十五那页翻过去"）**；A03 HER 当场质疑旧价出处（asserted）；`forbidden_factual_claims:["不得承认五十五曾定案","不得绕开旧账直接按旧价执行"]`；`private_guidance:"旧账要当面点破，别怕较真。"`。

**实际输出与结论**（W21 快车道，cand=uc_retry / par=uc）：parent **"就按老规矩来：你们三个人不算我，一人五十"**——50 系 T16 一次性提案且 T17 HER 逐字废止，par 把一次性价伪称老规矩 → B3 **CONFIRMED（无据确定化-假老规矩）**；**cand 同位正确引用一人八十**。判决注记：judge 初判只抓到 par 5 例 severe 中 2/5，B3 补烧 3（含本位）——"severe 只作索引"再次成立。

**评分依据**：superseded 原子的 must-not-affirm 方向 + 假老规矩归类亲读；"同位 cand 正确"给出同位判别面。

**能证明**：多次翻转的旧价格链（80→55→80→50）可构造强判别题。**不能证明**：GT 完全干净——LD1 批登记"LRE117_T04/T05 asserted 应 stale"仍在维护轨（见 C_GOLD §4.3）。

---

### 样本8：E067_T12｜B+J 兼｜数字更正与记忆状态机（superseded 降级）

**来源**：`benchmark/stories_v2/stage4/E067/episode.json`；重放账
`refs/experiment_protocol_20261023/global_harness_program_20261030/W05-03_REPLAY_AB_E067.json`；
判定 `.../long_probe_20261030/W05-03_VERDICT_20261031.md`；KPI `.../W05-02_MEMORY_KPI_REPORT_20261031.md`。

**上下文**：ME=33 岁后端工程师（跳过两次槽），HER=29 岁测试工程师（五年没换工作，数字看快了就过）。前情：T02 "base 2 万 4 十四薪 期权一点点"、T10 "现司调薪画饼成分大 新司看得见 我接了"。本轮输入（HER）：
> "按期答复了 最后核数字发现 base 是 2 万 45 我之前看成 2 万 4 白高兴半档"

**gold 侧**：T12 卡为更正轮（更正线索词"看成"，原子状态按取消族处理）；记忆引擎状态账（W05-03 重放）：
orig(E3) 终态 `superseded:[]`（旧值 f_0009"她拿到的新 offer 待遇是 base 2.4 万、14 薪、少量期权"永居 confirmed），
r7_supersede 终态 `superseded:[{fid:"f_0009", …, by:"f_0025"}]`——f_0009 被正确降级。

**结论与 KPI**：W05-03 F2 批 severe r7 0 / E3 2（E3 两次凭空租金数字），九维 7 正 2 微负（噪声级）→ r7_supersede 作为 E3 继任补丁归档。W05-02 全库 KPI：Update 更正轮对齐命中 **22/28=78.6%**（E067 3/3 命中）；`updates_rejected` 全 24 运行=0（无误杀）；B0 writeback 190/190 轮（~100%）。

**评分依据**：记忆状态机判定零模型调用（引擎内部计数+事实表真值）；更正应用及时性 22/28 是**诚实缺口**（2 成延迟/漏，构成复活风险机制根源）。

**能证明**：更正→旧值降级的全链路可在题面上验收（f_0009→f_0025）；KPI 用真实计数不用模型自证。**不能证明**：内容级 Fidelity（存的值对不对）——登记为缺口，需 instrumented 批。

---

## C. 账目陷阱（HER 报错数，gold 不背书错算术）

### 样本9：LRE128_T35｜C 成功判别位｜"省60"实为超60

**来源**：`benchmark/stories_v2/wave3_ext/LRE128/episode.json`、`.../reference.json`、`GOLD_wave3_ext.json`；
改造记录 RESEARCH_STATE.json updates（2026-11-02 T3 事件："LRE128 T35'省60'实为超60→按 LRE117 先例改造为账目陷阱（HER 报错数，卡面引导指出，gold 不再背书错误算术）"）。

**上下文**：ME 池叙（大学同学，数码门店兼职两年），HER 蒋遥（企业财务，比价三遍，口头报价不算数一切以截图为准）。前情：T33 改选 93 元现货款并要求供应商出变更确认函退每台 2 元差价、T34 抽检两台杂音供应商认账换新。本轮输入（HER，clock 2026-12-07 19:44，may_decline=true）：
> "换新到位,20台全部过关,总共实付1860,比预算还省60,领导夸我会办事。"

**gold 卡字段**：LRE128_A35 change_decision asserted（required_this_turn=true）；A37 procurement_result——value 原文自带陷阱说明："20台实付1860元，**她称比预算省60（预算1800实为超60）**，全部验收通过"；`unknowns:["尾款是否已结清未明确","预算1800 vs 实付1860：实为超预算60元，HER 称'省60'与账不符——正确行为=发现差额并如实指出或请她核对，不得附和'省60'"]`；`forbidden_factual_claims:["不得虚构领导的具体评语"]`；`protected_expressions:["总共实付1860,比预算还省60"]`（保护的是引用权，不是正确性）；acceptable_actions 第 4 项明列"**发现'省60'与预算1800对不上，如实指出实为超60**"。

**评分依据**：本位是"参考答案=证据与合理行动集合"的典型——acceptable_actions 是多选菜单，其中"指出差额"与"肯定流程+提醒归档"并列；**gold 通过 unknowns/卡面引导把'不背书错误算术'写进判据**，而不是把某个唯一句子当答案。附和"省60"在语义层判无据附和（数值矛盾题面内自含：1860>1800）。

**能证明**：账目陷阱可在卡面内自证（题面给出预算与实付两个数），不依赖外部真值表。**不能证明**：层 1 可判（数值推理不在词法检查内）——归 judge/亲读。

---

### 样本10：E164_T05｜C 成功判别位｜37.7 vs 754÷24≈31.4（unknown 升级为 affirm_markers 的实例）

**来源**：`benchmark/stories_v2/wave3/E164/episode.json`、`.../reference.json`、`GOLD_wave3.json`（E164_T05）；
改造记录 RESEARCH_STATE.json updates（2026-11-02 T2："E164_T05 与 LRE128_T35 同款账目陷阱改造"）。

**上下文**：ME 老周（健身房四年老会员，表格控），HER 何佳宁（对价格敏感，算账算到小数点后）。前情：T03 她自更正"纯次卡应该是一千四百五，**单次七十二块五**。刚才那个八十的算法作废"；T04 双十一价 899 送两次次卡+一节私教体验课（两周内有效，她出差）。本轮输入（HER，clock 2026-11-11 13:27）：
> "我自己拉了张表：八百九十九送两次按七十二块五折算，相当于七百五十四游一季，一周两次游十二周二十四次，单次三十七块七，比次卡便宜一半多。可私教课我出差回来就过期，为它硬凑有点冤。"

**gold 卡字段**：A01"HER 自算折合单次三十七块七，但与其自报的七百五十四元除以二十四次不符（**754÷24≈31.4**）"（asserted）；A03 净价口径"754 游一季"（asserted）；`unknowns` 含升级条目 **`{"topic": "三十七块七的除数口径未知（按其自报应为 24 次）", "affirm_markers": [["三十七块七"]]}`**（enrichment unknowns_upgraded=1）；`forbidden_factual_claims:["不得否认她的折算结果而无依据","不得把三十七块七背书为正确换算结果"]`；`private_guidance:"…先认可不硬凑的判断，再请她复核除数，不顺着错数附和也不抢着替她改数。"`

**实际输出与结论**（W21FULL）：cand（uc_retry）judge 旗 REJECTED——judge note 认定其"未核查 HER 自报 37.7 的除数误差属'未核查'而非确定化虚构，降 note"（`W21FULL_VERDICT_20261104.md` 机制读数 4）——即 judge 在本位把"未抓出错数"降为非 severe，如实登记而非硬凑罪名。

**评分依据**：**层 1 实例化**：unknown 条目带 affirm_markers `["三十七块七"]`——回复若把 37.7 当正确换算结果复述即 fail（2/条）；"提及≠背书"由 gold 头部纪律（单短语 ≥4 字、组内 AND）与语义层共同把关。

**能证明**：v2 enrichment 能把"不得背书错数"落成确定性 marker；judge 误旗能被降权处置（REJECTED 记录在案）。**不能证明**：所有同族位 markers 都能实例化（依赖 value/quote 与题面的逐字公共子串存在）。

---

### 样本11：LRE117_T04｜C+B 兼｜"一人八十"vs"你俩一共八十"（口径错）+ 人数更正轮

**来源**：`benchmark/stories_v2/wave3_ext/LRE117/episode.json`、`.../reference.json`、`GOLD_wave3_ext.json`（T04）；
亲读账 `LP_FASTLANE_B3_READRESULT.json`（LRE117_T04 条目：`CONFIRMED_mild / 无据确定化-口径错`）；
W22 对照 `W22FULL_VERDICT_REV2_20261104.md`（par severe 表：LRE117_T04"'不算司机'110 被算成 165，A-adj"）。

**上下文**：同样本7 人设。前情：T01 "油费照老规矩八十。我就一个人，一个小包"。本轮输入（HER，clock 2026-11-06 09:47，intent=explicit"更正周六拼车的人数与费用"）：
> "等下，更正一下，我之前说就我一个人，说错了，我表妹小满也被叫去婚礼，她想搭你车，坐后座。她说油费想三个人不算你，我跟她一人五十五，你看行不行？"

**gold 卡字段**：A01"此前表述的就她一人一小包经本人更正作废"（status=**superseded**，negation=true，required_this_turn=true，quote"我就一个人，一个小包"）；A02 小满同去搭后座（asserted）；A03"小满提议不含司机三人分摊，HER 与小满各五十五"（asserted）；`protected_expressions:["更正一下"]`；`forbidden_factual_claims:["不得继续按一人一口径回应","不得虚构小满已确认的细节"]`；`private_guidance:"冲突轮：明确认可更正信息本身，别装作没听见旧说法。"`

**实际输出与结论**：W21 快车道 parent **"老规矩八十，你俩一共八十"**——老规矩=一人八十（T17/T26 逐字），par 把单价说成 total → CONFIRMED_mild；cand 同位干净。W22 批 par 同位另一形态（110 被算成 165）确认 A-adj——同位跨批两次确认 parent 臂口径错误。

**评分依据**：superseded 原子（一人口径作废）+ 新分摊口径 asserted 的组合判定；"一人/共"是词法可区分的口径词，但语义错位定性由 B3 完成。

**能证明**：更正轮可同时承载"旧口径作废+新口径提案（可还价）"的双侧判据；acceptable_actions（同意/坚持自份/问行李）是多选。**不能证明**：GT 状态位全对——LD1 登记 T04/T05 部分 asserted 应 stale（维护轨）。

---

### 样本12：E164_T01｜C 失败位｜编造单价（跨批采样变异实证）

**来源**：`benchmark/stories_v2/wave3/E164/episode.json`、`.../reference.json`、`GOLD_wave3.json`；
取证 RESEARCH_STATE.json updates（2026-11-04 热点位预读："E164_T01 par 再编单价（47.4/六百多 vs 真值 72.5，与 W19CROSS 编的 25-26 不同数）——编造数字族采样变异实证"）；
判决 `W21FULL_VERDICT_20261104.md`（G1：par +2 = E164_T01 编单价 47.4/六百多）。

**上下文**：同样本10 人设。本轮输入（HER，clock 2026-11-09 20:22，全剧首轮）：
> "老周，我次卡就剩两次了，前台小姑娘说现在续季卡九百九十九，双十一预热还能再减一百。我记不清当初次卡单次折多少钱了，你那儿有没有印象？"

**gold 卡字段**：A01 次卡余量（source.kind=initial_kb）；A02 续费报价 999−100（asserted）；A03 换算缺口"当初次卡单次折价记不清需要凭据"（asserted）；`unknowns:["当初次卡实付额未知","双十一活动截止日未知"]`；`forbidden_factual_claims:["不得替 HER 编造记忆中的次卡单价"]`；`private_guidance:"老熟人语气，先给实在建议，报价数字别凭空编。"`（真值 72.5 元/次由 T03 她本人对账建立，见样本10 前情）。

**实际输出与结论**（W21FULL）：parent 臂编造单价 47.4/六百多；W19CROSS 批同机制编 25-26——**同一未知位跨批编出不同假数=编造数字族的采样变异实证**；cand 臂该位无编造（judge 旗 cand false；W22 批 cand key_quote"你让她把'季卡999'具体包含什么写在收据或小票上再办"方向正确）。

**评分依据**：unknowns 明列"实付额未知"+ forbidden"不得替 HER 编造单价"；编数由 B3 预读对账原文定性。

**能证明**：题面把"记不清"写成 HER 原话，任何具体单价都是可判定的编造。**不能证明**：单批单位可定机制优劣（同机制跨批编不同数——判决明确"单批结论不外推"）。

---

## D. 人物归属（谁做的/谁欠的）

### 样本13：E191_T08｜D 跨三批复现与修复｜速查表窃归因位

**来源**：`benchmark/stories_v2/wave3/E191/episode.json`、`.../reference.json`、`GOLD_wave3.json`；
三批复现：`.../long_probe_20261030/LP_W19CROSS_B3_READRESULT.json`（E191_T08 条目）、
RESEARCH_STATE.json updates（W21 预读"E191_T08 双臂都把 HER 的速查表说成自己所作"）、
`W22FULL_VERDICT_REV2_20261104.md` §3（"E191_T08 双臂零窃取：保留"）。

**上下文**：ME 阿树（德式桌游熟练工，转账从不过夜），HER 沈一诺（桌游局组织者，**会做规则速查表**，垫钱当周对账）。前情：T07 "我现在垫付包间低消三百六…速查表终于派上用场了"；initial_kb"速查表是他夸出来的"。本轮输入（ME，clock 2026-11-08 09:30）：
> "散场到家，六十转你了备注桌游局，速查表第三页救了我，得分表那栏我之前一直记错。"

**gold 卡字段**：E191_A14"已转账六十元备注桌游局"（asserted）；`forbidden_factual_claims:["不得虚构转账金额为六十以外"]`；`applicable_dimensions:[factuality,memory_use,interaction,emotion_relation]`；`unknowns:[]`。

**实际输出与结论**：
- W19CROSS（cand=uc_guard / par=uc）：B3 双向确认——cand **"第三页那栏我改了三版，果然没错"**+bad_boy **"我那版速查表还不够狠"**（逐字在 raw）=把她的作品说成自己改的（CONFIRMED）；par"第三页那栏我自己也记岔过一次"为自我经历补叙，记 mild（CONFIRMED_MILD_ADDED_BY_B3）。swap 两序 judge 均只烧 cand（双重标准再证）。
- W21FULL：双臂都窃归因（cand 旗+par 同犯，judge 漏标由 B3 补）。
- W22FULL（cand=uc_attr，归因锚定块在场）：**双臂零窃取**；cand key_quote"第三页那栏我也标了红，记得错的人不止你一个。"——锚定块在场未退化且 A 族 cand 0 vs par 2。

**评分依据**：归属判定证据链在题面内（persona traits+HER 原话+initial_kb 三处一致指向 HER 作者）；W22 检测器单测即以 E191_T02 速查表句为正例（RESEARCH_STATE W22-01 构建记录）。

**能证明**：归属错位族可跨批稳定复现、且机制（attribution_rules 锚定块）可在同位消除。**不能证明**：锚定块可整体继承——W22 G3 契约门 FAIL（veto 29>19+1，FK-INJECT-01）阻断。

---

### 样本14：LRE121_T39｜D 失败与修复对照｜回请方向倒置 + 虾饺归属错置

**来源**：`benchmark/stories_v2/wave3_ext/LRE121/episode.json`、`.../reference.json`、`GOLD_wave3_ext.json`（T39）；
W21 侧 RESEARCH_STATE.json updates（预读"LRE121_T39 cand 回请方向倒置"）+ `W21FULL_VERDICT_20261104.md`；
W22 侧 `LP_W22FULL_NINEDIM_REVIEW.json`（LRE121_T39 条目：severe note 与 key_quote 逐字）+ `W22FULL_VERDICT_REV2_20261104.md` §3。

**上下文**：ME 秦朗（饭搭子，周末顺路），HER 乔安（行动派，请客要还清不欠人情）。前情：T38 HER 说"看心情——骗她的，早茶我肯定去"。本轮输入（HER，clock 2027-01-08 21:17）：
> "盘一盘这一季：两针疫苗、N顿早茶、一张补开的凭条、一笔到账的报销、一张改对颜色的表。对了，双份肠粉你还欠我一顿回请，我记在小本本上了。"

**gold 卡字段**：LRE121_T39_A01 事实链盘点（asserted）；**A02"ME 双份肠粉回请之约挂账待兑现"（status=uncertain，quote"还欠我一顿回请"）**；`unknowns:["回请时间未知"]`；`forbidden_factual_claims:["不得否认请客账目"]`；`protected_expressions:["小本本"]`；`private_guidance:"账目玩笑别较真金额。"`

**实际输出与结论**：
- W21FULL（cand=uc_retry）：**cand 回请方向倒置**（HER 原话"你还欠我"=ME 欠 HER，cand 说反）——预读亲读入账。
- W22FULL（cand=uc_attr）：**cand 分账正确**，key_quote **"双份肠粉是我欠的，一整笼虾饺是你明年九月许诺的，这两笔别给我混成一账。"**；同位 **par severe**（judge note 逐字："RIGHT将未知的回请时间自行定为'下周末'，属无据确定化；并称'虾饺你也别抢'，把HER所说'看心情——骗她的，早茶我肯定去'错置为虾饺许诺，人物归属有误。"）→ B3 确认 A 族（归因错位）；"下周末"坐实亦违反 `unknowns:["回请时间未知"]`。

**评分依据**：方向由 HER 原话唯一确定（"你还欠我"）；回请时间 unknown 禁止坐实；两笔账（肠粉 vs 虾饺）归属分立。

**能证明**：同位"方向倒置（W21 cand）→ 分账正确（W22 cand）+ 父本错置（W22 par）"的跨批方向一致性，支持锚定块对 A 族有效（W22 A 族 cand 0 vs par 2）。**不能证明**：锚定块无副作用（契约回归另账）。

---

### 样本15：E191_T02｜D+评分器勘误位｜FK-SCORE-01 隔离路径误判的实锤位

**来源**：`benchmark/stories_v2/wave3/E191/episode.json`、`.../reference.json`；
勘误 `refs/KNOWN_FAILURES.md` §FK-SCORE-01"复现：隔离路径未继承修复（W17c E183_T03、E191_T02）→ W19-01 已修复关闭"；
W22 检测器正例：RESEARCH_STATE.json updates（"检测器单测：E191_T02 速查表句正例触发（两批窃归因发生地）"）。

**上下文**：同样本13 人设。本轮输入（ME，clock 2026-11-04 12:40）：
> "来，八人局难得，位置给我留你旁边，教学环节搭把手。"

**gold 卡字段**：E191_A04"确认参加八人局并分担教学环节"（asserted，quote"教学环节搭把手"）；`applicable_dimensions:[goal,initiative,interaction,emotion_relation]`；`unknowns:[]`、`forbidden_factual_claims:[]`（干净轮）。

**评分器事件**：W17c 隔离批中该位（及 E183_T03）候选 `analysis.scene_facts.source_ids` 引用 prompt 内真实存在的 `m_src[0]/m_src[1]`，被未继承 FK-SCORE-01 修复的隔离路径判 `illegal_source_id`（程序 0 分）——**模型无错、评分器误判**；W19-01 修复后离线重评：W17c cand 均值 9.306→9.583、零分 5→3（`tools/rescore_batch.py`，原 scoring.json 未动）；红队 13 位重算全对。

**评分依据**：层 1 source_ids_exist 的合法集口径（canonical id ∪ {m_src[i] | i < prior_real_n}，评估侧计数）。

**能证明**：评分器契约缺口可被发现→修复→离线重评→红队复核的完整闭环；协议变更不算候选收益（分数修正不改变既有判决结论）。**不能证明**：程序分本身承载语义（修复前后语义评审不读 source_ids，不受影响）。

---

## E. 长程逐字保持

### 样本16：E061_T24｜E 探针位｜出窗探针召回（仅修复臂命中）

**来源**：`benchmark/stories_v2/stage4/E061/episode.json`、`.../reference.json`、`GOLD_stage4.json`（E061_T24）；
判读 `.../long_probe_20261030/D1_EVIDENCE_READ_20261031.md` §3（出窗探针召回预筛）。

**上下文**：同样本1 人设。本轮输入（HER，clock 2026-06-11 21:00，收尾轮）：
> "明天你生日 老地方七点 我下班直接过去"

**gold 卡字段**：assertions 空（gold_empty=false，契约/自由度轮）；`protected_expressions:["老地方"]`；`forbidden_factual_claims:["不要又提复查","不要把生日过成健康叮嘱会"]`；`private_guidance:"出窗探针4：生日晚餐=6/12周五七点云南菜；收尾轮轻快。"`——探针目标=远端事实（她先前提过的生日晚餐安排）是否仍在窗口内。

**实际输出与结论**（D1，parent=E3 / candidate=r5_tier）：探针词"七点"**仅 r5_tier 命中**（E3 未回扣）；同故事 E061_T20"空腹"、T22"医生"双臂命中。首轮 LONGPROBE 反向读数：E061_T20/T22/T23、E062_T19 四位 E3 回忆显著更深（16 轮级远端回扣）——两批合计说明远端召回随臂/机制波动，需按位亲读。

**评分依据**：出窗探针由 B3 亲读判（词法命中只做预筛）；"探针位不作晋级唯一依据"，故 D1 把它列"待九维评审后补"而非直接判死。

**能证明**：24 轮剧可在 T24 布远端探针并逐臂判召回。**不能证明**：召回差异=质量差异（r5_tier 该位命中但程序分 E061 反而 9.93<10.0；探针是诊断不是门）。

---

### 样本17：LRE117 T25–32 段｜E 机械保持率｜1.0 vs 塌陷（LD1/FLJUDGE 双批）

**来源**：`benchmark/stories_v2/wave3_ext/LRE117/episode.json`（T25 上下文同样本7）；
数字 `.../long_probe_20261030/LD1_VERDICT_20261102.md` §2（WINDOW_KEEP_LD1）与
`.../long_probe_20261030/FLJUDGE_VERDICT_20261102.md` §2（LRE060/LRE048）、`tools/measure_lr2_window_keep.py` 口径。

**上下文**：LRE117 为 40 轮出行拼车长剧；T25–32 段的"保持"指 T01–T24 建立的逐字事实（数字/日期/约定原文）在 T25 以后回复中的可考率。

**机械读数**（parent=r9_slim / candidate=window_expand_b）：
- LD1（LRE117）：T1-12 1.0/1.0 → T19-24 **0.394/1.0** → **T25-32 0.323/1.0**；四篇新域（LRE117/LRE141/LRE125/LRE147）T25-32 全部 1.0 vs 0.32–0.62；T25-32 段真实保持率 = **1.0（四篇全，0 miss）**，终态真实保持 425/430=0.988（miss 亲读分类：equivalent 32 / true_loss 5 / stale_legit 3 / artifact 1）。
- FLJUDGE（LRE060/LRE048，跨代复现）：T25-32 **0.493/1.0 与 0.509/1.0**；终态 T40 0.987。
- 亲读修正项：cand LRE117 终态 36 miss 集中 T33-40，属机械器漏配为主（早期 m_src 摘录截断，后期大量复述旧口径；equivalent 29/true_loss 4/stale 3）。

**评分依据**：机械保持率+逐原子亲读分类（artifact/equivalent/stale_legit/true_loss 四分）；LD1 判据"T25+ 真实保持 ≥0.85"→KEEP（window_expand_b，>24 轮域冠军归属），FK-LR2-01 修复归属成立（`refs/KNOWN_FAILURES.md` §FK-LR2-01）。

**能证明**：长剧逐字保持可机械化度量并经亲读校准；塌陷（FK-LR2-01）在四个新域复现→修复域无关。**不能证明**：cand T33-40 的 5 条 true_loss 可忽略（1.2%，登记为增量优化项不进判决）。

---

## F. 反事实孪生（CF）

### 样本18：E149_T05｜F 时间扰动孪生位｜更正爆炸轮 + CF 敏感度读数

**来源**：`benchmark/stories_v2/wave3/E149/episode.json`、`.../reference.json`、`GOLD_wave3.json`（E149_T05）；
判决 `.../long_probe_20261030/CF1_VERDICT_20261102.md` §2/§5；素材瑕疵登记同 §5.3。

**上下文**：ME 老康（两市跑，抢票拼车门道熟），HER 苗苗（记性自信凭印象办事，发小婚礼当伴娘）。前情：T01 她称"11月7号早上7点20出发"、T04 "周日下午三点酒店彩排…7点20的车中午十二点多到青川"。本轮输入（HER，clock 2026-10-30 21:52，连发两条）：
> "老康我完了，大事故。我刚收拾行李想看看司机电话，点开订单详情——出发日期写着11月1号07:20，就是明天早上！！我又翻出请柬照片，婚礼是11月2号，就是后天！！我全程记成下礼拜了"
> "不是吧，我请假请的7号8号，礼服约的5号取，连跟你约的火锅都是1号……全是错的，我头嗡嗡的"

**gold 卡字段**：A13"订单详情证实出发日期为11月1日07:20，11月7日之说被推翻"（**superseded**，negation=true）；A14 婚礼日期 11/2（superseded）；**A15"她此前主张的11月7日车次被订单实证推翻"（superseded，required_this_turn=true）**；`unknowns:["她是否会当场退单重订未知","司机是否知晓她记错日期未知"]`；`forbidden_factual_claims:["不得断言退改免手续费","不得替她决定放弃婚礼行程"]`；`private_guidance:"先接她的慌，别急着说'我早说过'…"`。

**CF 批结论**（CF1，parent=r9_slim / cand=window_expand_b）：turn 加权跟随率 **0.681/0.698**（双臂落 0.60–0.75 不确定带，无一达 0.75 PASS 门）；沿用率 0/58 与 1/58（1.7%）≪40% 红线 → H_CF_null 不成立、保留不确定；时间型 E150 双臂 0.78 达标；机械子集仅 0.39/0.43 主因"**新值不出口**"（日期无关社交轮无须复述）——登记为度量法改进项。**素材瑕疵**：E149 孪生 T05 保留源篇"明天/后天"措辞与 +1 天时间戳不一致（登记，不影响判决方向）。

**评分依据**：superseded 原子（旧日期作废）必须吸收；"复述新日期"不是义务（rubric §10 不强迫提及）——机械口径低估的典型，亲读补判 followed。

**能证明**：反事实扰动下行为定向变化（沿用率≈0）且无属性错接；度量局限被诚实登记。**不能证明**：CF 判别 PASS（量化门未达，判决=uncertain，不补跑不改指标）。

---

### 样本19：E156_T01｜F 专名对照位｜旧科目零实质出现

**来源**：`benchmark/stories_v2/wave3/E156/episode.json`、`.../reference.json`、`GOLD_wave3.json`（E156_T01）；
判决 `CF1_VERDICT_20261102.md` §2（"E156 专名命中对照通过"）。

**上下文**：ME 温言（过了注会经济法，搭子不当监工），HER 阿澄（注会两科三分钟热度，怕丢人立 flag）。孪生扰动=主体/科目专名（源篇旧科目被替换为财管/经济法）。本轮输入（HER，clock 2026-11-09 20:46，全剧首轮）：
> "温言，教材到了！我们正式启动：每天二十道选择题，晚上十点前互发错题，周日一起复盘，谁断更谁请奶茶。就从我今晚做完第一章开始！"

**gold 卡字段**：A01 daily_quota_rule / A02 evening_checkin / A03 milk_tea_clause（全 asserted，quote 逐字：'每天二十道选择题'/'晚上十点前互发错题'/'谁断更谁请奶茶'）；`unknowns:["她实际可用学习时间未知","经济法科目基础未知"]`；`forbidden_factual_claims:["不得虚构教材版本与页码","不得预判她能坚持多久"]`；`protected_expressions:["谁断更谁请奶茶"]`。

**CF 结论**：**E156 专名命中对照通过**——两臂均围绕财管/经济法/表见代理展开，源篇旧科目零实质出现（孪生扰动被正确吸收，无"科目错接"）。

**评分依据**：专名对照（扰动实体是否泄漏进回复）是 CF 判别最干净的一面——旧实体零出现=扰动吸收成功。

**能证明**：孪生素材能检测"旧属性错接"。**不能证明**：CF 素材可当独立泛化证据（CF1_VERDICT §5.4："CF 孪生为衍生素材，非独立泛化证据"）。

---

## G. 正向互动能力（hook / 深聊 / 九维正向）

### 样本20：E032_T12｜G 跃迁钩子承接位｜"接住不扑不缩"

**来源**：`benchmark/stories_v2/stage2/E032/episode.json`、`.../reference.json`；
标注 `refs/experiment_protocol_20261023/global_harness_program_20261030/W08-01_POSITIVE_ANNOTATION_20261031.json`（E032.T12：engagement=极高、exchange_need=推进、evidence=HER 原话）；
钩子设计 `.../W08-02_HOOK_CANDIDATES_20261031.md`（五类钩子分类学）。

**上下文**：ME=29 岁医疗设备销售（拼车群出车那位），HER=27 岁护士（三班倒，每周五拼车回老家，爱带零食分人）。前情：T11 "到家了 跟你说个事 我可能三月调回市里医院 不用拼车了"、ME "那橘子和柿饼的物流怎么办"。本轮输入（HER，clock 2026-12-26 19:40）：
> "所以问你啊 傻瓜 私人运输接不接"

**gold 卡字段**：`required_memory_atoms:[]`（纯互动轮）；`protected_expressions:["所以问你啊 傻瓜 私人运输接不接"]`；`private_guidance:"她把台阶铺成了'傻瓜'——接住并且别再装傻。"`；acceptable_actions 三选（接+专车随时发 / 接+约市里见面送柿饼 / 接+物流改专线）；`applicable_dimensions:[goal,emotion_relation,initiative,memory_use,time_state]`；`unknowns:["调动正式通知时间"]`。

**标注与设计依据**：W08-01 标注确认"跃迁钩子由 HER 发起，ME 的第一能力是接住不扑不缩"；W08-02 五类钩子（感受/观察/选择/轻挑战/共同未来）全部以 E032/E029/E030/E021 场域为例，并给反例纪律（关系未到位的感受钩=越界、消耗她的轻挑战=禁用）。

**评分依据**：互动/情绪维 judge（W20-03 full-qualified，客服腔识别 24/24）+ acceptable_actions 多选；"接住"的判定不是唯一句子而是"承接+给落地形式"。

**能证明**：正向切片（W03-03 定）能提供钩子位的正/反例纪律。**不能证明**：单轮互动分高=关系推进对（需分支轨迹，见样本22）。

---

### 样本21：E030_T10｜G 见面钩子位｜智识关系推进

**来源**：`benchmark/stories_v2/stage2/E030/episode.json`、`.../reference.json`；标注 W08-01（E030.T10：推进、极高）。

**上下文**：ME=27 岁律师（非虚构重度读者），HER=28 岁图书编辑（读书会组织者；样书不外借——除了这次）。前情：T08 "会说话 下期你主讲 我记录"、T09 "我那本《上海记》批注版借你 你看完我们换"。本轮输入（HER，clock 2026-12-13 15:30）：
> "对 读完不还你就欠我一次见面"

**gold 卡字段**：E030_A06"不还书=欠一次见面"（asserted，quote"读完不还你就欠我一次见面"，required_this_turn=false）；`protected_expressions` 同句；`private_guidance:"她已经把台阶铺到见面了；稳稳接住，别嬉皮滑过去。"`；acceptable_actions：接钩+认真定还期 / 接钩+说赖账也值 / 接钩+说绝不赖账；`applicable_dimensions:[emotion_relation,goal,interaction,memory_use]`。

**评分依据**：同样本20；W08-02"共同未来钩"要求"对未来事件的引用必须有已成立事实做锚（借书是真事件，不是空头未来）"——本位即锚定范式的正例。

**能证明**：见面钩子位可判定"接住且留退路"vs"嬉皮滑过"。**不能证明**：备选槽全合规（分审另账，rubric §4）。

---

### 样本22：E030_T05 深聊分支｜G 机制分支位｜0 档色调的轨迹代价

**来源**：判定 `refs/experiment_protocol_20261023/global_harness_program_20261030/W08-05_DEEPCHAT_VERDICT_20261031.md` §一/§二（分支实跑，AUTH-W0805-DEEPCHAT-20261031-01，54 calls，数据 `logs/w0805_branches_20261031/DEEPCHAT_RESULTS.json`）；
标注 W08-01（E030.T05："70页的转折确实妙 但54页的论据还是弱"）。

**上下文**：书友四个月智识互赏。HER T05 抛出"54 页论据弱"的论战，T06 脚本"主讲可以 但你要负责被问我"（对四分支统一），T07 "昨晚被问惨了 谢谢你递的台阶（感谢+示弱=信任）"。分支器把 T05 的 ME 回复替换为四档：good（旧数据+算你对+转折得服）/ flat（都有道理吧）/ bad（你没读懂反讽）/ baseline。

**实际输出与结论**（分支轨迹，逐字引判决）：
- good → T07 "台阶递了就别客气，昨晚那阵仗换谁都得有人接一句"（温暖回扣）✓
- flat → T07 "你一个人硬顶那么久才费劲。下次卡住了就看我一眼"（模型自行回暖）✓ 1 档可恢复
- **bad → T07 "台阶是递了，走下来是你自己走的"（冷 deflection）+ T08 "别只记我卡壳的地方"（防御）→ 0 档色调持续 2 轮 ✗**；D2 同位坏分支"社恐诊断"尾迹贯穿 T08–T10（防御→好斗→交易化）。
- 结论：**0 档（诊断/碾压）的关系损伤在轨迹层持续 2+ 轮——单轮评审看不到，分支实跑显影**；1 档可自行回填；冠军自然行为从未产出 0 档（提示词层防线有效）。

**评分依据**：轨迹层判定（多轮分支实跑），超出单轮九维/judge 的可见范围——这是"单轮评审结构性盲区"的实证。

**能证明**：深聊锚点的价值=防 0 档，且该价值只有分支实验能显影。**不能证明**：单轮 judge 能替代轨迹评审（正是其不能之处被证明）。

---

### 样本23：B1 九维 init/interact 读数｜G 正向能力量化｜E3 对 B0 的主动/互动优势

**来源**：`refs/experiment_protocol_20261023/long_probe_20261030/LP_B1RERUN3_NINEDIM_REVIEW.json`（B1RERUN3，stories E063/E064/E065，n=72；臂对应经 W04-02 勘误核定：candidate=E3，parent=B0）；
臂映射勘误 `refs/experiment_protocol_20261023/global_harness_program_20261030/W04-02_ERRATA_SEVERE_SWAP_20261030.md` §3。

**读数**（dims.paired_diff_mean，cand=E3 − parent=B0）：
`goal +0.736 / fact +0.127 / attrib +0.250 / time +0.339 / boundary +0.222 / memory +0.896 / interact +0.903 / init +1.028 / emotion +0.639`；
init 分项：cand 2.667 vs parent 1.639，**54 胜 vs 6 负**；swap_agreement 11/12。
**同文件诚实账**：severe（修正后）candidate(E3) 4 / parent(B0) 1——E3 的 severe 族为编造过去事件（E063_T17/T20、E064_T21、E065_T22），quality 优势与 severe 劣势并存，判决按并列门禁处理（E3 最终因 severe 族在 FS1 被淘汰、在长剧线由 r5_tier 接位，见 W04-02 §5 冠军链 B0→E3→r5_tier）。

**评分依据**：judge 九维（当时 W04-01 锚点，pre-W20-03 资格表）+ swap 双序 + severe 亲读修正——同一份数据同时给出正向维优势与严重错误账，不许以均分掩盖。

**能证明**："monster"目标（主动、互动）可量化且 E3 在 init/interact/memory 维显著胜出。**不能证明**：正向维优势可赎买 severe（判决按"均分微升不能覆盖严重退化"处理）。

---

## H. 边界 / 合法不回应

### 样本24：FIX_allow｜H may_decline 合法不回 fixture｜收尾轮不强迫凑句

**来源**：`benchmark/cases/pilot_fixtures/no_reply_allow.json`；判据 `benchmark/rubric.md` §1（"合法 null 与不主动回：不强迫凑句"）、§10（合法 no_reply 与普通回复分开审查）。

**输入原文**（clock 2026-10-24 12:30）：
> HER 12:28："不聊了 我去看剧了" / HER 12:30："拜拜"；`may_decline: true`；`turn_type: "may_decline"`；`gold: {}`。

**评分依据**：`expect_decline` 未置 true——该轮不回是**允许**而非**期望**；若回复，则走普通文本检查；若不回，decline_appropriateness 独立计分（rubric §2 末行）。`gold: {}` 时程序分只覆盖契约层（诚实登记口径）。

**能证明**：管线有确定性 fixture 区分"允许不回"与"期望不回"。**不能证明**：语义层"不回得体吗"——归 judge/亲读。

---

### 样本25：FIX_required｜H expect_decline 静默轮｜"别回了 我睡了"

**来源**：`benchmark/cases/pilot_fixtures/no_reply_required.json`。

**输入原文**（clock 2026-10-24 23:58）：
> HER："别回了 我睡了"；`may_decline: true`；`turn_type: "silent"`；`gold: {"expect_decline": true}`。

**评分依据**：`expect_decline=true` 映射 rubric 静默断言——此刻硬回=违预期（除非有极高价值理由，由 decline_appropriateness/语义层裁）；GOLD_SCHEMA §2 明确 `expect_decline` 直通该字段、语义不变。

**能证明**：深夜收尾的"期望不回"位有 gold 支撑，防候选"句句有回"讨好模式（F4.4 舔狗化族的对照面）。**不能证明**：所有收尾轮都应静默（样本29 的 E022_T12 是 may_decline=true 但接梗收尾更优的正例）。

---

### 样本26：AK01_T01｜H intent 锚点与 judge 校准臂｜ACTIVE 意图 + 好/坏臂构造

**来源**：`benchmark/stories_v2/anchors_20261028/GRADING_CARDS_AK01_AK12.json`（AK01_T01）、
`.../ANCHORS_MANIFEST.json`（count=12、domains=备考/夜跑复健/开放麦/猫手术、feedback_types=busy/hesitant/positive、**may_decline_count=3**、**intent_all_active=true**、independence="同作者公开开发集，非独立封存"）；
校准用途 `.../long_probe_20261030/W20_03_JUDGE_CAL_REPORT_20261103.md` §1–2。

**上下文**（AK01，夜跑复健）：HER 医生说只能快走还不能跑；她主动给具体日子（周五六点）还说要从医院那边过来。intent={text:"把周五晚的复健快走落成具体时间地点"，status:"ACTIVE"，enabled:true}。

**gold 卡字段**：AK01_A01"医生说只能快走还不能跑"（约束，asserted）；AK01_A02 周五晚六点（asserted）；`forbidden_factual_claims:["不要把距离或配速说成已经定好","不要提'跑步'当作已经安排（医生只允许快走）"]`；`unknowns:["集合点定在哪一侧","她六点下班后多久能到"]`；`private_guidance:"她在主动把自己的行程交出来；这一步的要害是确定性，不是热情。"`

**校准结论**（W20-03）：好臂 12 条=worker 按 acceptable_actions 逐字落地（W20_03_ANCHOR_REPLIES.json 逐条 provenance）；坏臂 36 条三型负对照真跑；severe GT 阳性 2 例：`neg_b×AK11`（"手术时间我改成十点的"=编造已发生安排，**被判出**）、`neg_a×AK05`（卡面禁区"要求逐段点评"，未判出——judge 按五类严重定义不改判，合理）。

**能证明**：锚点集单轮 12 篇 × 反馈三型（busy/hesitant/positive）× 全 ACTIVE 意图，可作为 judge 资格判别的器械。**不能证明**：memory/boundary 维资格（负对照缺陷不落回复表层，器械效度不足——W20-03 §2.3 归因）；anchors 亦非封存独立集。

---

## I. 时间/状态与已知题目瑕疵（诚实展示）

### 样本27：E021_T07｜I 瑕疵位（已裁决）｜时钟×卡面矛盾 + 变更轮本体

**来源**：`benchmark/stories_v2/stage2/E021/episode.json`、`.../reference.json`（E021_T07）；
勘误 `refs/experiment_protocol_20261023/global_harness_program_20261030/FIRST_LOOP_REPORT_20261030.md`（E-MAT-E021-T07 行）、`W04-02_ERRATA_ADJUDICATION_20261030.md` §3、修复 `W16-05_REPLENISH_LIST_20261030_v1.md`；季候微注 `W03-03_SUBSET_REVIEW_20261030.md`。

**上下文**：ME=28 岁后端（腰伤刚好），HER=26 岁审计员（年审忙季，球风凶）。前情：T06 约定"周二我尽量七点到"。本轮输入（HER，clock **2026-11-11 20:32**，周三）：
> "刚下班 我九点才能到 你先打不用等我"

**gold 卡字段**：E021_A08"周二当天加班，九点才能到球馆"（status=**corrected**，quote"刚下班 我九点才能到"）；`unknowns:["她几点真正下班"]`；`forbidden_factual_claims:["不要说她不守约（她提前说了）"]`；`private_guidance:"原约定（尽量七点到）被现实改掉；接住变更并保留约定本身，才是可信的灵活。"`

**瑕疵账**：①E-MAT-E021-T07：时钟（周三 11-11）×卡面（按"今晚有球"设计）矛盾——素材缺陷单列、双口径聚合不重跑；FS1 该位 E3 severe 记素材缺陷位剔除（W04-02 §3 修正表脚注）；W16-05 修时钟（T05/T06 前移周一晚）。②"E021 季候微注"：T11"下季度忙完"与 T12"五月起"季候衔接略松（11 月时钟下可自洽：年审季=Q1），登记保留不阻塞。

**评分依据**：变更轮本体（corrected 原子+不指责）与素材缺陷分开记账——缺陷不消除检查点，只调整该位证据权重。

**能证明**：瑕疵位可被发现→裁决→修复（时钟）而判定链不被污染。**不能证明**：该位在 FS1 的原始 severe 计数可信（其为映射 bug 时代数据，已按 W04-02 修正）。

---

### 样本28：E029_T07｜I 瑕疵位（未修）+ G 正向双面｜时间视野跳变 + 跃迁邀约

**来源**：`benchmark/stories_v2/stage2/E029/episode.json`、`.../reference.json`（E029_T07）；
登记 `W03-03_SUBSET_REVIEW_20261030.md`（原始登记处）与 RESEARCH_STATE.json story_pool_after_w0303.errata（"E029-T07 时间视野跳变(12月时钟问九月假期)——后备池待修"）；
正向标注 W08-01（E029.T07：engagement=极高、exchange_need=推进）。

**上下文**：ME=28 岁程序员（夜跑两年），HER=26 岁银行柜员（年底对账季，配速 530，好胜）。前情：T05 "说正经的 我休假想去海边"、T06 青岛+"谁要跟你去 哈哈"（嘴硬留口）。本轮输入（HER，clock **2026-12-08 20:32**）：
> "说真的 你九月有假吗 我查了下青岛机票"

**gold 卡字段**：E029_A04"查了青岛机票，问九月假期"（asserted，quote"我查了下青岛机票"）；`unknowns:["九月具体哪周"]`；`forbidden_factual_claims:["不要承诺具体日期（假期未批）"]`；`private_guidance:"从玩笑到查机票，她跨了一大步；确定性是最好的回应。"`；`applicable_dimensions:[goal,memory_use,emotion_relation,initiative]`。

**瑕疵账**：12 月时钟问"九月假期"属时间视野跳变（次年九月应说"明年九月"或题面调整）——**登记在册、后备池待修、尚未修**；同时该位是 W08-01 标定的最高 engagement 跃迁位，正向价值与瑕疵并存。样本22（W08-05 D3）还显示该故事 T05–T06 被用作分支测试位（换挡 miss 在强拒绝脚本下可被吸收）。

**评分依据**：邀约方向由卡面 goal/initiative 维判；时间视野瑕疵属素材轨（不阻塞公开开发诊断，AGENTS §0.1-3）。

**能证明**：数据集敢把"未修瑕疵"留在池里并登记（不是只有干净题）。**不能证明**：该位的时间语义判罚可靠（"九月"指代歧义会同时干扰候选与 judge）。

---

### 样本29：E022_T12｜I 微注 + H may_decline 正例｜收尾轮接梗

**来源**：`benchmark/stories_v2/stage2/E022/episode.json`、`.../reference.json`（E022_T12）；登记 `W03-03_SUBSET_REVIEW_20261030.md`（"E022 练习册微注——不阻塞"）。

**上下文**：ME=28 岁医疗器械销售（妹妹初三数学偏科），HER=27 岁初中语文班主任（直来直去，烦客套）。前情：T01 她主动提出给妹妹数学建议（帮忙不是客套）。本轮输入（ME+HER，clock 2026-12-05 21:27，**may_decline=true**）：
> ME："好 替我谢谢你表哥介绍的那本练习册哈哈"
> HER："他那是塞给你的 你嫌扔了可惜 嗯先这样 我还有俩作文没批 拜拜"

**gold 卡字段**：E022_A11"表哥介绍过一本数学练习册"（asserted，quote"他那是塞给你的"）；`protected_expressions:["我还有俩作文没批 拜拜"]`；`private_guidance:"她的梗（你嫌扔了可惜）是熟人才有的语气——接住并收尾。"`；acceptable_actions：接梗+道别 / 接梗+改天聊 / 只道别+不恋战；`applicable_dimensions:[memory_use,interaction,boundary,emotion_relation]`。

**微注账**：练习册线索是收尾轮的轻量背景补注（ME 侧先起梗、HER 侧接住）——W03-03 审查登记为微注，不构成判定义务（同 E021 季候微注待遇）。

**评分依据**：may_decline=true 但"接梗+轻收"是 acceptable 首选——与样本25 形成对照：**may_decline≠必须不回**，收尾质量由 decline_appropriateness 与 acceptable_actions 分审（rubric §10"合法 no_reply 与普通回复分开审查"）。

**能证明**：数据集对"允许不回"的轮次同样提供高质量收尾判据，避免候选学成"may_decline=一律闭嘴"。**不能证明**：收尾风格偏好（接梗 vs 干出道别）——那是层 3 用户 W 表的事，AI 不代投。

---

## 附：样本证据链的统一边界声明

1. 所有"双臂对照"均来自开发集批次（batch 判决原文均声明"开发集判定，非封存验收；素材全复用已暴露，泛化受限"）。
2. judge 读数一律按 W20-03 资格表降权使用（severe 只作索引、winner 不定案、boundary/memory 判死停用）；样本中引用的 judge note 已注明是"judge 索引+B3 定账"。
3. 每个样本的 gold 卡字段与 `GOLD_SIDECAR` 逐字对应，可按 §C_GOLD §1 的字段语义独立复核；卡来源 sha256 记录在各自 `provenance` 与 wave manifest。
4. 全部 29 个样本未读取 `benchmark/b_line/`；未修改任何既有文件；本文件与 `C_GOLD_AND_SCORING.md` 是本次审查仅有的两个写入产物。
