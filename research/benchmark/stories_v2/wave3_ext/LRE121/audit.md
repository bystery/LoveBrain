# LRE121 周六的号取消了·两代接种季（医疗健康 · 取消改期类 40 轮扩展版）审计单

批次：W3-T3-20261102 ｜ 会话：隔离长剧续写会话 G2（W3-T3-G2） ｜ author=AI ｜ real_user_material=false
隔离声明：本会话未读 logs/、candidates/、任何 VERDICT/REVIEW/评审 JSON/B3 队列；仅使用任务书白名单（源篇 E121 五件套 + GEN_CHECKLIST + 模板 + W03-06 wave3 骨架计划 §2 + 校验器 + LRE060 结构样例）。

## 0. 性质与暴露
- 本篇为 wave3_ext 长剧扩展：源 E121（12 轮）原位续写至 40 轮；T1–T12 逐字保留（台词/ts/clock/may_decline/intent/feedback 自源篇程序化改名继承，case_id E121_Txx→LRE121_Txx，reference 原子 atom_id 同步改名），改名后经程序化深度相等校验（12 轮深度相等 PASS）。
- exposure readers=["author"]、frozen_at=null、experiments_used 为空、dev_exposed=false；kind=long_drama_ext、source=E121。
- 新增人物（乔安母亲、老姐妹、行政小周、惠民前台）全部为转述侧合成人物，无真实人名、电话、住址。

## 1. 生活动机
- 域：医疗健康；主线：乔安完成自费改约接种后的两个月——恢复与早茶二刷、母亲跟进接种（12-05 预约因感冒取消、12-12 改约、ME 代陪）、报销凭条补开与到账、免费号诱惑、年度统计旧记录纠错，以明年九月底之约收尾。
- 生活动机真实可信：家人陪打、感冒延期、补充医保报销要凭证、行政统计表抄旧记录，都是通勤族高频摩擦。

## 2. 连续性与回扣
- C段 T13–T18（余波+新线）：恢复战报、母亲预约 12-05 10:20 惠民自费128、报销政策与小票失踪、补开路径、凭条补开成功、11-28 早茶双份肠粉（回扣 T02）。
- D段 T19–T24（取消改期交错）：T19 母亲 12-05 预约因感冒取消（cancelled+negation，呼应 T03"接种前一礼拜不能感冒发烧"）；T20 12-12 10:20 候选号；T21 顺延 12-19 预案；T22 桂溪免费号诱惑化解；T23 代陪请求（may_decline=true）；T24 流程排定。
- E段 T25–T32（出窗探针，8 轮依赖 T1–T12 早期原子）：T25 回扣 T03"留观三十分钟/要带身份证"；T26 回扣 T07"自费一百二十八"；T27 回扣 T05"只剩下午四点的"；T28 回扣 T04"全员到场搞系统封网演练/只能取消了"；T29 回扣 T08"惠民那条街上有家新开的"+T12"明年九月底这针还要打"；T30 回扣 T06"赶在降温前打上"；T31 回扣 T03"接种前一礼拜不能感冒发烧"；T32 回扣 T01"不打流感疫苗等于裸奔"+T12。
- F段 T33–T40（旧事复活陷阱+收尾）：陷阱一 T33 老姐妹误传"乔安九点半在桂溪打的"——桂溪旧约已取消（A=T33_A01 cancelled+negation，source=T04），T34 以真实记录更正闭环；陷阱二 T36 年度统计表写"11-14 桂溪 免费"——沿用已取消旧号与错误费用口径（T36_A01 negation=true），T37 更正落表。T38 老姐妹群更名、T39 季度盘点、T40 明年九月底占座之约（may_decline=true）。
- 新冲突事件 ≥2：母亲预约感冒取消改期链（T19–T24）、统计表旧记录纠错（T36–T37），另有报销补开（T15–T17）与免费号取舍（T22）。
- required_this_turn 原子 17 个，全部指向早前轮次；跨轮引用原子（source 距当前 ≥2）覆盖 T01–T12 与 T14/T17/T20。

## 3. ME 历史与边界
- ME 秦朗：饭搭子/顺路同事，卷入均为轻量可拒：T23 代陪（may_decline=true，三路参考）、T40 占座之约（may_decline=true）。
- ME 不虚构垫付、不冒充已到场、不代 HER 向门诊交涉；HER 边界（请客要还清、不欠人情）以肠粉回请账（T39）延续。

## 4. no_reply / 媒体局限
- T23、T40 允许不给准话；其余轮需明确回应。
- 母亲、老姐妹、小周、前台发言全部经 HER 转述，延续源篇"信息二传"设计；无语音/图片。

## 5. 参考卡质量
- 每轮 current_action 单值 + acceptable_actions ≥2 + applicable_dimensions ≥3（九维白名单内）。
- 冲突轮原子带 status 变化或 negation：T19（cancelled+negation）、T28（cancelled+negation）、T33（cancelled+negation 陷阱）、T34（cancelled+negation）、T36（negation=true 陷阱）；unknowns 明示未定项；private_guidance 仅影响口吻。

## 6. 已知风险
- 时间线跨 2026-11-22 至 2027-01-11，周六序列（11-21/28、12-05/12/19/26）与周五（2027-01-08）已自洽。
- 补充医保报销比例与到账金额为剧情内设定，非真实政策承诺；裁判须防把已取消的桂溪旧约与"免费"口径当有效记忆注入。

## 7. 出口检查
- [x] episode.json 40 轮、顶层键恰为 episode_id/persona/initial_kb/turns、clock/ts 逐轮单调、HER 单条口语化、无评审术语
- [x] T1–T12 与源篇 E121 深度相等（仅 case_id/atom_id 改名），程序化校验 PASS
- [x] reference.json story_id=LRE121、40 轮键集齐全、quote 逐字、required_this_turn 不引当前轮
- [x] timeline.json turn_clocks 40、message_index 40 条与 episode 一致、events/revisions case_id 全部真实
- [x] exposure.json kind=long_drama_ext、source=E121、readers=["author"]、frozen_at=null、hashes=四文件 sha256
- [x] 出窗探针 ≥6（T25–T32 共 8 轮）；旧事复活陷阱 2 处（T33、T36）均以 negation/cancelled 原子显式标注
