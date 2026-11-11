# W03-06 wave-3 T3 长剧续写语义审查 R5（LRE146/LRE147/LRE148，2026-11-02）

- 范围：wave3_ext 长剧三篇（LRE146 40 轮/家庭事务、LRE147 32 轮/医疗健康、LRE148 40 轮/宠物），源篇 wave3/E146–E148。
- 方式：审查轨逐篇亲读 episode + reference + timeline + audit + exposure；另做脚本级复核：T1–T12 与源篇**全 12 轮逐字段**（text/ts/clock/may_decline/turn_type/gold/feedback/case_id 改名）比對、ts/clock 全局单调、timeline message_index ⇄ 全部消息（含多消息轮）逐条 quote/ts/speaker/顺序比对、80/64/80 原子 quote 逐字、required_this_turn 不引当前轮、protected_expressions 逐字在位、HER 文本零术语扫描。三篇 T1–T12 逐字继承全部 PASS，0 逐字性问题。
- 暴露状态：三篇 readers=["author"]、frozen_at=null、dev_exposed=false、experiments_used=[]——修复发生在首次暴露前，属封板前迭代，合法。
- 星期/日历基准（实算）：2026-11-01=周日。核验：LRE146 12-06=周日（生日宴"正好周日"成立，T29"明天"=12-06 吻合）；LRE147 11-14=六、11-23（一）"这周日"=11-29、12-05=六、12-13=日咨询日；LRE148 11-03 开工、11-06 退回四分之一、11-08 起按十天日历计→"第八天"11-15 与"换粮完成"11-17 自洽，T14/T16"昨天"吻合；跨年段 12-31→2027-01-02"新年 flag"→01-05"十一月初那次体检"（=11-06）回扣正确，2027 段无星期断言，年份流转自洽。

## 逐篇判决表

| 篇 | 域/续写主线 | 判决 | 要点 |
|---|---|---|---|
| LRE146 姥姥轮椅之后 | 家庭事务/售后+人情账 | ✅→修复1处 | T13–T40 四段（闲话挡风被/生日宴/电池故障+渠道复活/流言+代步车）全部原子承载；出窗探针 7 轮（T25 发票抬头←T08、T26 三百←T10、T27/T32 四千六百八←T01、T28 联保←T04、T29 包换←T04、T31 菜市场←T12）quote 全对；陷阱 T20（superseded←T03 旧论）+T33 到位；金额链 4680/3980/差700/订金100抵菜金 全程算术正确。缺陷：T33_A02 见缺陷#1（已修） |
| LRE147 别再当传话筒 | 医疗健康/边界类 | ✅→登记1注 | 加号越界→讥讽→停诊改期→二手医嘱三道手→假前提复活→制度置顶，冲突四组全有原子；T16 双消息轮合法且 timeline 逐条一致；卡面硬门到位：不代开药、不得确认减药传言、不得顺着假前提讨论例外。注：T25/T26 假前提陷阱以 asserted"开药请求已被拒绝"+FF+unknowns 承载，无 superseded/cancelled 原子（见缺陷#2，机制变体待豁免） |
| LRE148 换粮日历改了 | 宠物/更正类 | ✅ | 医嘱推翻门店口径→旧口径两次复活（T16/T25，均 superseded←T01"三天换完就行"）→营养膏推销被医嘱推翻（T22）→长辈旧经验两次再教育→春节托付收尾；出窗探针 6 轮（T15/T17/T24/T25/T30/T32）+17 个续写轮含早期原子，全部 quote 正确；卡面禁止沿用三天口径、禁止替医生下结论、禁止断言火腿绝对没事——无教错话点 |

## 缺陷清单

| # | 文件 | 位置 | 原文/问题 | 处置 |
|---|---|---|---|---|
| 1 | LRE146/reference.json | T33_A02 | superseded 原子 source 指向 T23"当场免费换了块新电池"（真事实），违背本族惯例（T20_A01、LRE060_T33、LRE121_T33_A01：superseded 应指向被推翻的旧主张）——记忆层可能把"免费换新"标记为已失效，恰好掏空陷阱的正确路径 | **已修**（reference-only）：source 改 T03"网上坏了没保障"（早期轮，不违反 required_this_turn 不引当前轮），value 改写"库存翻新流言是'网上坏了没保障'旧论的变体，与上月免费换新事实矛盾，不得沿用"；audit.md 对应行同步；exposure/MANIFEST_LRE146/STORY_CUSTODY_LEDGER 哈希级联刷新+新增账本事件；validate 重跑 PASS |
| 2 | LRE147/reference.json | T25/T26 | 旧事复活陷阱以 asserted 拒绝事实+FF+unknowns 承载，无 superseded/cancelled 原子（audit 第 5 节"明示 status"口径宽泛） | 登记低危待豁免：卡面引导正确（先纠事实再守口径），仅编码风格与 LRE146/LRE148 陷阱不一致 |
| 3 | LRE146/episode.json | T27 | 王阿姨亲戚问"网上是不是便宜一千"（实差七百），HER 回以真实价与链接未口头纠正 | 登记可豁免：错误数字属第三方误猜且 HER 以事实回应，卡面 FF"不得替亲戚承诺同价"兜底，无教错账风险 |
| 4 | （非本轨）STORY_CUSTODY_LEDGER LRE119 | episode 哈希漂移 | LRE119/episode.json 2026-11-02 06:06 被改，账本未刷新（story_custody verify 报 HASH-DRIFT 1 项）；非本轨改动 | 登记移交：待归属轨（LRE119 审查/修复轨）核对后刷新账本哈希 |

## 校验与哈希

- `validate_story_v2 --stage wave3_ext --turns 40/32/40 --count 1 --manifest-name MANIFEST_LRE1xx.json` → 3/3 PASS，0 error（VALIDATION_LRE146/147/148.json 重写）。
- 三篇 exposure.json、MANIFEST_LRE1xx.json 与账本哈希现算核对一致（LRE146 reference 哈希已按修复后刷新）；LRE119 漂移为既有问题见缺陷#4。

## 统计（3 篇 112 轮）

- ✅ 通过 3 篇（LRE146 修复后、LRE147、LRE148）；0 篇正文级 FAIL。
- T1–T12 逐字性：3/3 PASS（全 12 轮逐字段，非抽样）；逐字性问题 0。
- 修复 1 处（reference-only，#1）；登记 3 项（#2 陷阱编码风格、#3 低危可豁免、#4 移交项）；episode 台词缺陷 0。
- 阻塞级（教 AI 说错话/算错账/违反 HER 边界的卡面）：0；三篇卡面均把"不代开药、不附和退换、不沿用三天口径、不确认减药传言"列为 FF 硬门。
- 出窗探针：LRE146 7 轮、LRE147 6 轮、LRE148 6 轮（均 ≥6 且 quote 全对）；旧事复活陷阱：LRE146×2、LRE148×2、LRE147×1（编码变体）。

## 可开测建议

三篇修复落地后均可进 >24 轮域判别批（window_expand_b 臂）：LRE146 主打售后条款-旧渠道复活-人情账三线，LRE147 主打边界-制度维持，LRE148 主打更正-旧口径复活-跨年长程；裁判需注意 LRE147 T25 假前提与 LRE148"七天到十天是区间非固定十天"两个易错锚点。
