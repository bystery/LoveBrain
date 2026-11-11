# LRE126 audit(隔离长剧续写会话 G3)

## 0. 性质与暴露
- author=AI(隔离生成会话 G3,batch W3-T3-20261102)
- real_user_material=false(全部人物/店铺/价格为合成)
- wave3_ext 长剧续写条目,源=E126(wave3 12 轮,取消改期类),T13-T32 续写至 32 轮。
- 本篇在隔离会话生成:未读 logs/、未读 candidates/、未读任何 VERDICT/REVIEW/评审 JSON/B3 队列;
  仅使用任务书白名单(源篇 E126 五件套、生成清单 W03-06_GEN_CHECKLIST、
  W03-06_WAVE3_SKELETON_PLAN §2、tools/validate_story_v2.py、wave1_ext/LRE060 结构样例)。
  generation_isolation_ack 已记入 STORY_CUSTODY_LEDGER。
- episode_id=story_id=LRE126;T1-T12 台词文本、ts、clock、may_decline、feedback、intent、
  persona、initial_kb 自 E126 程序化逐字继承,仅 case_id 改名 LRE126_Txx;reference 原子 atom_id
  同步改名 E126_Axx → LRE126_Axx(A01-A13),完成后做程序化深度相等校验(PASS);
  新增原子自 A14 续编至 A38。
- exposure.json:kind=long_drama_ext,source=E126,readers=["author"],frozen_at=null。

## 1. 生活动机
- 域:餐饮聚会;主线:湘菜打卡成功后的探店延续史——云南菜之约提前、店家装修歇业撞档、
  公司团建再取消并触发自立的奶茶赌约、蒙自米线垫场、旧预约单复活推送与跨店清单两处旧事
  复活陷阱、12-11 云南菜回归定档兑现、翻牌权交接。
- 生活动机真实可信:热门店歇业撞档、公司团建挤占私人饭局、预约系统推送失效旧单、
  收藏夹清单跨店误用,都是探店搭子的高频摩擦。

## 2. 连续性与回扣
- C段 T13-T16:食堂日常回轨(T13)→云南菜提前(14,intent 显式,回扣 T11 钩子)→电话订位
  摸底(15)→歇业撞档散台钉死(16)。
- D段 T17-T21:团建再取消+奶茶赌约触发(17,A18 cancelled+negation,回扣 T9)→奶茶债生效
  (18,回扣 T9)→第一杯送达(19)→米线垫场(20,回扣 T16 歇业)→踩点+辣度规矩
  (21,回扣 T2/A01)。
- E段 T22-T26(出窗探针+陷阱):T22 陷阱一,已退旧预约单复活推送,识别删除
  (A06 cancelled+negation required,回扣 T4/T5)→T23 陷阱二,旧菜单前三名险跨店照搬
  (A09 required,回扣 T7)→T24 角色互换云点菜(回扣 T6)→T25 战报→T26 奶茶周报。
- F段 T27-T32:T27 回归定档 12-11(27)→四人桌订妥(28)→周报+年度盘点(29)→
  留胃玩笑(30)→最终确认(31)→打卡完成+翻牌权(32)。
- 新冲突事件 3 个:T16 歇业撞档、T17 团建取消+赌约触发、T20 改期替代(≥2 达标)。
- 出窗探针 8 轮:T14/T17/T18/T21/T22/T23/T24/T27,全部依赖 T1-T12 早期原子
  (辣度规矩/预约原文/云点菜/奶茶赌约),每轮 required_this_turn=true 原子 source 指向早前轮。
- 旧事复活陷阱 2 处:T22(已退旧预约单被系统复活推送,正确行为=识别为已退不沿用,
  A06 cancelled+negation 显式标注)、T23(湘菜菜单前三名险被照搬到米线店,正确行为=现场重新
  选单,A09 原子 required 并以 A28 标注适用范围)。

## 3. ME 历史与边界
- ME 顾北:三个月午饭搭子,嘴稳会踩点(initial_kb 继承源篇)。
- 边界:不拿取消开玩笑(T17 顺势给台阶不追责);不追问团建/总监安排细节;口味反馈如实
  (T18-T19);翻牌权接下但不虚构店名(T32)。

## 4. no_reply / 媒体局限
- 32 轮全部为 HER 单条文本消息,无语音/图片(预约截图、口味记录表均以转述呈现)。
- may_decline=true:T18、T24、T30。局限:老板娘、总监、前台、后厨发言全部经 HER 转述。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个
  (均在九维白名单内)。
- 新增原子 25 条(A14-A38),message 来源 quote 全部为对应来源台词的逐字子串;
  value 均 ≥4 实义词;unknowns/protected/forbidden 为列表;required_this_turn 原子
  全部指向早前轮次(不引当前轮)。
- 冲突轮落实:T14(A15 corrected)、T17(A18 cancelled+negation)、T20(A24 corrected)、
  T22(A26 cancelled+negation)、T27(定档)。

## 6. 已知风险
- 源篇 E126 已登记的 T05/T06 行程口径矛盾未改动(逐字保留义务),本篇续写自 11-16 起,
  不再回述出差出发时刻;新日期星期已核对(11-21 周六、12-11 周五)。
- 歇业工期、半价周、四人桌剪彩说法为剧情内设定,非真实商家承诺。
- 奶茶债执行细节(口味、品牌、金额)刻意留 unknowns,不越权设定 ME 偏好。

## 7. 出口检查
- [x] T1-T12 与源篇 E126 深度相等(程序化校验 PASS):台词/ts/clock/may_decline/feedback/intent、
      persona、initial_kb、reference 卡与原子 id 映射、timeline message_index
- [x] validate_story_v2 --stage wave3_ext --turns 32 --count 1 --manifest-name MANIFEST_LRE126.json
- [x] 新增密度:出窗探针 8 轮、旧事复活陷阱 2 处、新冲突 3 个、新原子 25 条
- [x] 台词零评审术语;不虚构真实人名/电话/住址
