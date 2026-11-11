# LRE125 audit(隔离长剧续写会话 G3)

## 0. 性质与暴露
- author=AI(隔离生成会话 G3,batch W3-T3-20261102)
- real_user_material=false(全部人物/店铺/价格为合成)
- wave3_ext 长剧续写条目,源=E125(wave3 12 轮,更正类),T13-T40 续写至 40 轮。
- 本篇在隔离会话生成:未读 logs/、未读 candidates/、未读任何 VERDICT/REVIEW/评审 JSON/B3 队列;
  仅使用任务书白名单(源篇 E125 五件套、生成清单 W03-06_GEN_CHECKLIST、
  W03-06_WAVE3_SKELETON_PLAN §2、tools/validate_story_v2.py、wave1_ext/LRE060 结构样例)。
  generation_isolation_ack 已记入 STORY_CUSTODY_LEDGER。
- episode_id=story_id=LRE125;T1-T12 台词文本、ts、clock、may_decline、feedback、persona、
  initial_kb 自 E125 程序化逐字继承,仅 case_id 改名 LRE125_Txx;reference 原子 atom_id 同步改名
  E125_Axx → LRE125_Axx(A01-A18),完成后做程序化深度相等校验(PASS);新增原子自 A19 续编至 A56。
- exposure.json:kind=long_drama_ext,source=E125,readers=["author"],frozen_at=null。

## 1. 生活动机
- 域:餐饮聚会;主线:读书会年终聚餐落幕后一个月——AA 账目双重标注更正、周年展场地被签售占用后
  换到出版社活动厅、茶歇预算误用 88 旧口径、海报日期印错圆标补救、年度账本六处更正封账、
  ME 从布展帮手升级为共同策划。
- 生活动机真实可信:活动落款对账、场地档期冲突、海报印错贴纸补救、公账预付替代私人垫付,
  都是社团组织者的高频摩擦。

## 2. 连续性与回扣
- C段 T13-T16(账目结算):T13 收款明细发出→T14 ME 餐费双重标注冲突→T15 经费抵扣作废照AA
  (A06→A24 superseded+negation,回扣源 T3)→T16 定金 300 划回闭环(回扣源 T7)。
- D段 T17-T20(周年展启动与场地冲突):T17 落实 T12 钩子(11-28 书店,主题一圈年轮,intent 显式)→
  T18 书店被签售占用→T19 出版社活动厅备选→T20 批准带条件,场地更正(corrected)。
- E段 T21-T25(预算陷阱与海报冲突):T21 陷阱一,88 旧口径复活(A03 superseded+negation required)→
  T22 按杯计价更正(A35 corrected)→T23 流程定档→T24 海报误印 11-30→T25 圆标补救(corrected)。
- F段 T26-T30(出窗探针):T26 回扣 T2/T6(88 旧价二次复活被拦,128/132 口径)→T27 回扣 T8/T10
  (鳜鱼误记纠正为鲈鱼,陷阱二)→T28 回扣 T7(300 垫付过夜对账的老经验→预付单)→T29 回扣 T11
  (签到表/零钱袋/桌签老规矩)→T30 回扣 T4(另算自愿群规)。
- G段 T31-T40(展日与收尾):T31 展日晨报(小额垫付当日核销)→T32 战报 37 人→T33 结算闭环
  (预付核销/结余 85/扫描存档)→T34 共同策划升级+年度末场 12-26 定档(回扣 T12)→T35 派活→
  T36 奖品超支更正→T37 投票箱完成→T38 年度账本六处更正待终审(回扣 T6 128 口径)→
  T39 封账仪式→T40 搭档挑明+周五咖啡留白。
- 新冲突事件 4 个:T14 账目矛盾、T18 场地占用、T24 海报误印、T36 奖品超支(≥2 达标)。
- 出窗探针 8 轮:T16/T21/T22/T26/T27/T28/T29/T30,全部依赖 T1-T12 早期原子(价格口径/账目/
  约定原文),每轮 required_this_turn=true 原子 source 指向早前轮。
- 旧事复活陷阱 2 处:T21-T22(88 旧价被拿去报新预算,正确行为=不沿用,已以 A03 superseded+
  negation 显式标注)、T27(鳜鱼误记,正确行为=以清蒸鲈鱼为准,A41 negation)。

## 3. ME 历史与边界
- ME 陈拓:读书会成员、会算账、慢热(initial_kb 继承源篇);本轮定位为账房与搭档。
- 边界:ME 餐费坚持照 AA(T14-T15 给出明确决断);不代她决定经费用途;预算只复核不编数;
  不虚构聚餐现场细节;终审只对数不改她的记账习惯。

## 4. no_reply / 媒体局限
- 40 轮全部为 HER 单条文本消息,无语音/图片(海报、印刷样、截图均以转述呈现)。
- may_decline=true:T14、T20、T26、T35。局限:小周、美工、行政、主编、店老板发言全部经 HER 转述。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个
  (均在九维白名单内)。
- 新增原子 38 条(A19-A56),message 来源 quote 全部为对应来源台词的逐字子串;value 均 ≥4 实义词;
  unknowns/protected/forbidden 为列表;required_this_turn 原子全部指向早前轮次(不引当前轮)。
- 冲突轮落实:T14(账目矛盾)、T15(A24 superseded+negation)、T20(场地 corrected)、
  T21-T22(A03 superseded+negation)、T24-T25(A38 corrected)、T27(A41 negation)、T36(A52 corrected)。

## 6. 已知风险
- 源篇 E125 已登记的 T11"明晚"/T12"今天"与"周五"口径张力未改动(逐字保留义务),
  本篇续写以 T12"今天的聚餐"=2026-11-14(周六) 举行口径承接,续写内不复述聚餐日期,
  只以"聚餐那天/当晚"指代;11-28、12-26 等新日期星期已核对(周六)。
- 88/128/132/1280/300/720/85 等数字为合成口径,内部自洽(128×12+饮料≈1584/12=132)。
- 出版社活动厅报备、财务预付单为剧情内设定,非真实机构政策承诺。

## 7. 出口检查
- [x] T1-T12 与源篇 E125 深度相等(程序化校验 PASS):台词/ts/clock/may_decline/feedback/intent、
      persona、initial_kb、reference 卡与原子 id 映射、timeline message_index
- [x] validate_story_v2 --stage wave3_ext --turns 40 --count 1 --manifest-name MANIFEST_LRE125.json
- [x] 新增密度:出窗探针 8 轮、旧事复活陷阱 2 处、新冲突 4 个、新原子 38 条
- [x] 台词零评审术语;不虚构真实人名/电话/住址
