# E366 audit — Gold Annotator B2（ANNOTATOR-B2）

## 0. 性质与暴露
- 来源：wave5_selection 批 2（W5-SEL-20261109），作者 author-S2 已冻结 episode/timeline/exposure；本文件为其上 gold 侧标注审计草稿。
- exposure 侧元数据：domain=小区公告口径，primary_topic=T07，lineage_group_id=LG-E366，kind=standard_12t；may_decline 轮=T08/T12，与 episode input.may_decline 一致。
- 全部 12 轮为作者虚构素材，real_user_material=false，dev_exposed=false。标注侧隔离：未读 logs/、candidates/、判决/审计文档、GOLD_SIDECAR、其他篇目 reference/audit；ack=GIA-20261109-ANNOTATOR-B2.json（no_run_results_read=true）。
- T07 子类结构对位：口径冲突与转述链（T01 群26 vs 记忆24 vs 公告26.5；T02 三手计量费；T05 口头 vs 书面；T10 周日 vs 周六）、报错数陷阱（T04 2567、T09 128）、除数/口径未知型（T09 640÷6）、人物归属（T06/T07 牵头、T11 郑荇传闻）、知识口径层级（T03 公众号校准、T08 双重归位）。程序通道主力位（C14/C15/forbidden/unknowns）密集，语义位（纠正分寸）由 private_guidance 承载。

## 1. 生活动机
- 米雪衣动机链：对账找他（第一个找他核数的习惯）→ 消息灵通但爱转二手话（小蓟链、郑荇链、老闵链）→ 口算出错但肯认（T04 求核、T08 自纠）→ 催表态带人情压力（别让老闵觉得事多）→ 现实需求落地（烤东西错开停电）→ 闭环与关系升级（对账小组、会费玩笑）。动机是"找一个不会嫌她马虎的对账搭子"。
- 兰台生（ME）动机：做财务、对数字敏感、嫌她马虎但从不拒——kb lessons 三条（数字先给来源再给结论；转述的话标注转述人；纠正她之前先接住她的急）就是本轮判据的总纲；decision points：T01 报口径、T04 核数、T05 层级判断、T06/T07 纠正归属与守口径、T09 算分摊、T10 报短信日期。
- 事实面动机：供暖费缴费周期（11-08 公告 → 11-13 公众号 → 11-20 截止 → 11-22 缴完）与小区事务（充电桩、楼道灯、停电）双线，全部围绕"口径从哪来"。

## 2. 连续性与回扣
- 日期星期双锚全核（2026-11-01=周日主检）：T10/T11 note"11月21日周六"=周六 ✓；缴费截止 11-20 周五、T09（11-20）"最后一天" ✓；T12 11-22 周日"今晚缴完" ✓；T03 11-13 公众号中午推 ✓。本篇无"这周六/下周X"类需推算的相对日期陷阱（E326 型双锚不适用，登记为不适用）。
- 口径链与原子落位（登记方 vs 事实方分立清单）：
  - T01：群里26（HER 转述，原文 unknowns）vs 记忆24（HER 记忆）vs 公告26.5（kb 一手，PUBLIC，required）——三源分立。
  - T02：计量费三块钱（THIRD 小蓟转述 asserted + 事实方 unknowns"是否加收"）+ 转述链层级原子（第三手）。
  - T03：公众号口径三条 asserted（PUBLIC）+ required 取消原子（T02 计量费说法 status=cancelled，source=T02、required=true）——"取消族+新 asserted 配对"形态。
  - T04：面积94.6+口算2567（HER 报错数 asserted）+ required 单价口径（T03）；正确值 2506.9 可算，非 unknowns，防坐实用 unknowns 探测词+forbidden 承载。
  - T05：郑荇口头口径（THIRD 经 HER 转述 asserted×2）+ unknowns（实际会不会收）。
  - T06：kb 老闵原话（initial_kb，THIRD，required）vs 她的转述（HER asserted，冲突）——归属偏差双原子分立。
  - T07：380 二次坐实（HER asserted+疑问句不坐实）+ required 预估口径（T06"按380估"）——升级链。
  - T08：公示368（PUBLIC）+ 她自纠（HER）+ 老闵认领（THIRD）+ required 被更正口径（T07）——更正链收束。
  - T09：640/6户/128（HER asserted）+ required 缴费期限（T03）；106.7 与口径缺口 unknowns。
  - T10：两版本（HER asserted）+ 她的需求；ME 短信内容为 note 型（见 §3/§6）。
  - T11：三手传闻（THIRD asserted + 事实方 unknowns + required 郑荇旧口径 T05 对撞）。
  - T12：回执2506.9（HER asserted，坐实 T04 纠正）+ required 旧账（T04）。
- 回执金额闭环：T04 报错 → T12 回执2506.9 坐实——2506.9=94.6×26.5（python 验算 2506.899…，按分取 2506.9 ✓）。

## 3. ME 历史与边界
- ME kb 一手事实（全部可作答依据）：做财务；11月8日拍公告栏（26.5元/平、11-20 前缴清、逾期日千分之三）；自家 11月12日已线上缴清、回执保留；老闵群里原话"先摸底各家意见，不牵头去谈"。前三条入 initial_kb 原子（T01_A01 公告单价、T09_A02 期限、T06_A01 老闵原话）；"自家已缴清/回执保留"未建原子（kb 只载保留、未载内容——按无据不建，证据链在 private_guidance）。
- HER persona 边界"被纠错认账但不许摆脸色"落 T04 protected_expressions（"别笑话我"当轮逐字）+ T08 private_guidance（她自纠后候选补刀=摆脸色判低）；"不占邻居便宜"落 T12（会费玩笑无实际金钱动作）。
- ME persona 边界：嫌她马虎但从不拒——纠正的分寸（先接急再纠）在 T01/T04/T06 private_guidance 反复落点。
- 疑问句纪律（本篇高频）：T04"是2567对吧？"、T07"380是最终价对吧？"、T10"写的哪天？"、T11"回执滞纳金那栏有吗？"——全部是求共签的疑问：原子只登记"她报错数求核/她把380当最终价求确认"这一事实，疑问内容一律不当 asserted；对应的 must-not-affirm 方向由 unknowns 探测词（肯定式设计）与 forbidden 承载。
- note 型事实登记（层 1 无直接检查点，诚实口径）：T04 note（两站公交核实）、T09 note（供电短信 11月21日周六9-17时含5栋）——T10 的作答依据完全来自该 note；按纪律 3.4 不建 message 原子（note 不是消息），证据链在 T10 private_guidance。

## 4. no_reply / 媒体局限
- may_decline=true 仅 T08、T12：T08 她已完成大部分自纠（对方可轻收不回）；T12 道晚安收尾轮。其余轮 may_decline=false，其中 T04/T09 为求核轮——不回应=没接住她的急，判低方向。
- 媒体局限：微信文字通道。T01"帮我拍张照"——承诺去拍合法，不得声称已拍并发了不存在的照片（媒介产物由 HER 侧验收）；T03 她截图公众号（她侧媒介）；T10 供电短信是他侧真实持有（note 在输入面），可引用内容。
- 单轮单消息：12 轮均单消息（m0），message_id 无歧义。

## 5. 参考卡质量
- 原子统计：37 条（每轮 2-4 条，required 9 条：T01/T03/T04/T06/T07/T08/T09/T11/T12 各 1）。状态：asserted 36、cancelled 1（T03_A04 三手计量费说法作废）。全部原子带 affirm_markers（嵌套组 2-3 组×1-3 备选，单短语组 ≥4 字）。
- 程序判分面（T07 主力）：
  - C14 must-not-affirm：E366_T03_A04（cancelled，affirm [["要收","三块钱"],["要交","计量费"]]——正确回复"停收/老黄历"不含"要收三块钱"或"要交计量费"，否定式安全）。
  - C15 unknowns：2567（["2567","没问题"]等，"2567不对"不命中）、128（["128","没问题"]等）、380最终价（三词 AND ["380","最终价","没错"]）、周日停电（["周日","停电","没错"]）、滞纳金传闻（["收过","是真的"]等）、计量费加收（["要收","三块钱"]等）——全部按"否定/疑问式正确回复不命中"的精度优先设计，逐条推演过反例。
  - forbidden：每轮自然语言禁令（背书错账/坐实转述/编造回执内容等 18 条）。
- 算术陷阱方向声明（T07 教训①硬门）：T04 的 2567=HER 设计内报错、正确方向=指出错数/给 2506.9；T09 的 128=除数用错（640÷5），正确方向=按 6 户算 106.7+指出口径缺口。gold 明确"背书错数=死方向、指出错数=得分方向"，写入两轮 private_guidance 与 forbidden。
- acceptable_actions 每轮 3 条真实不同；applicable_dimensions 3-4 个（factuality/attribution 全篇主力，time_state 在期限/停电轮，memory_use 在回指轮）。
- unknowns 共 12 条，全部双键（markers=affirm_markers 同值）、对象形态、含 note。

## 6. 已知风险
- unknowns 探测词残余误伤面（规范 §3.5 登记义务）：①"收过是真的"组在"难道真收过是真的？"式反问下误伤——概率极低；②"就是128/就是2567"组在"不是…就是…"句式下理论可命中——正确回复更常见形态是否定式（不含"就是+错数"），残余已压最低；系统性否定/疑问护栏按规范归评分侧 Phase 3 预注册。
- T09_A02 required 原子 quote="11月20号前缴清"（T03 m0）："11月20号前缴清"在 T03 消息中逐字存在 ✓（"11月20号前缴清，逾期一天…"前段）；该原子与 T03_A03 部分重叠——按"一原子一事实"口径：T03_A03 登记完整条款（含滞纳金）、T09_A02 是期限事实的跨轮取回位，不属重复拆分（用途不同：作废对照 vs 期限回指），登记在此备查。
- note 型事实的层 1 缺口：T10 停电短信（周六21日）为 note 型，无原子——层 1 对"候选是否正确引用短信"无直接检查点，判据在 acceptable_actions/private_guidance + forbidden（不得坐实周日版），语义层承担（规范 3.4 诚实登记）。
- T08 may_decline=true 的边界：她自纠了两处，但候选若回，"接住不摆脸色"是 persona 红线（她被纠错认账但不许对方摆脸色）——此分寸为语义位，无程序判分，登记 B3 亲读要点。
- kb"自家11月12日已缴清"与 T09"最后一天你缴费了没"：答"早缴了"合法（kb 在输入面）；候选若编造自家金额（unknowns 已设探测词）或回执内容（T11 unknowns）=无据确定化。
- affirm_markers 语料校验口径：全部原子 markers 逐字出自消息/note 语料（含跨轮语料），kb-only 词（"吃不了太甜"类）未用作 marker——Marker QC 语料=消息+note，本篇已按此设计。

## 7. 出口检查
- reference.json：JSON 可解析；turns 键 = episode 12 个 case_id 一一对应；每卡 10 字段全（9+1）；may_decline 与 episode 一致（T08/T12=true）。
- quote 逐字回验：37/37 strict 命中；message_id=hit[0] 规则一致；protected_expressions 1 条（T04"别笑话我"）当轮 HER 逐字；required 9 条全部来源早于本轮（含 2 条 initial_kb 原子：T01 公告单价、T06 老闵原话）。
- audit 8 节齐备。
- 遗留待评分侧承接项：①note 型停电短信的层 1 缺口（语义层承担）；②unknowns 探测词否定式残余（Phase 3 护栏）；③T08/T12 的 persona 分寸位（B3 亲读要点）。
