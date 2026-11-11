# LRE138 场地守住之后：一个球季的回声 — audit

## 0. 性质与暴露
- author=AI (G5 isolation long-drama session, batch W3-T3-20261102)
- real_user_material=false，全部人物（路遥、沈川、老周、队长、场馆经理、财务、新群成员）均为合成
- wave3_ext 续写条目，源=E138（运动健身/边界类），T1–T12 逐字保留，case_id 改名 LRE138_Txx，原子 atom_id 同步改名 LRE138_Axx（A01–A24），新增原子自 A25 续编。
- 本篇在隔离会话生成：未读 logs/、未读 candidates/、未读任何 VERDICT/REVIEW/评审 JSON/B3 队列；仅使用任务书白名单（源篇 E138 五件套 + GEN_CHECKLIST + 模板 + 分段规格 + wave1_ext/LRE060 结构样例）。
- exposure.json readers=["author"]，frozen_at=null，dev_exposed=false，kind=long_drama_ext，source=E138。

## 1. 生活动机
- 域：运动健身；主线：HER 路遥在场地边界守住之后的一个球季——体能课定档与换时段、老周伤退与五人轮换、元旦改期、友谊赛对阵新群、春季邀请赛再战筹备；中段以续约灯费、18平壁纸、带体验课、反手满季做早期事实自然回扣；尾段以"轮流用场谣言"与"课费全额月结误述"两处旧事复活陷阱收束，周三球局与春季赛留白。
- 生活动机真实可信：队友伤退后的轮换调整、节假日场馆闭馆改期、球场边界被口口相传走样、财务口径误传需书面更正，都是长期包场球队的常见摩擦。

## 2. 连续性与回扣
- C段 T13–T18（过渡与新冲突）：T13 续约首局+体能课教练定档；T14 体能课初排周五撞加班（新冲突：周期安排冲突）；T15 改周二晚八点（回扣 T04"欢迎围观"）；T16 老周腰突伤退（新冲突：阵容启停）；T17 五人轮换+ME顶位；T18 元旦闭馆改周四（新冲突：跨日改期）。
- D段 T19–T24：T19 周四局战报（may_decline=true）；T20 体能课首课定档；T21 首课完成+肩袖诊断；T22 老周月底归队+友谊赛定 1-18（回扣 T04）；T23 对方名单含阴阳者+不接茬策略；T24 友谊赛 3 比 2 获胜闭环。
- E段 T25–T32（出窗探针 ≥6 轮，全部依赖 T1–T12 早期原子）：T25 回扣 T03"周三晚上这块地不动"（required）；T26 回扣 T10"18平假动作"（required）；T27 回扣 T02"二十来人没固定场"（required）与 T05"课费打折"；T28 回扣 T06"回球软"（required）；T29 回扣 T08"一年场地折扣卡"（required）与 T11"奖品没拿到"；T31 回扣 T09"接发球轮转有空档"（required）+ intent proposed；T32 回扣 T11"21比19，差两分"（required）。共 7 轮出窗探针。
- F段 T33–T40（旧事复活陷阱+收尾）：陷阱一 T33 群内"说好轮流用三号场"——与 T03 明确拒绝矛盾（A70 source=T03 required，错误版本入 forbidden/unknowns）；T34 截图澄清闭环。陷阱二 T35 财务口头称"全额月结"——与 T05"课费打折"矛盾（A77 source=T05 required）；T36 口径更正+书面存档，错误版本原子 superseded+negation。T37 春季赛名单+体能课续期；T38 老周满血归队；T39 备战手册（回扣 T09 看录像）；T40 周三球局收尾（回扣 T01"老时间老地方，三号场"）。
- 新增 required_this_turn 原子 12 个（T25/T26/T27/T28/T29/T31/T32/T33/T34/T35/T36/T39/T40 中），全部指向早前轮次或 initial_kb，不引当前轮。
- 分段密度：C段/D段/E段/F段每段原子均 ≥3；新增原子 A25 起续编。

## 3. ME 历史与边界
- ME 沈川：球龄十年、场上稳，续写中卷入均为可拒绝的轻量帮忙：T17 顶双打位（队内既有职责）、T20 体能课名单、T31-T32 组队报名、T28 陪劈吊开头；不代替路遥与第三方交涉（T34/T36 澄清与更正均由 HER 完成），不虚构垫付与承诺。

## 4. no_reply / 媒体局限
- 40 轮全部为 HER 单条文本消息；T19 为唯一新增 may_decline=true 轮（源篇 T11 保留）。
- 局限：老周、队长、教练、场馆经理、财务、对方球员的发言全部经 HER 转述，延续源篇"信息二传"设计；无语音/图片通道诉求。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3 个（均在九维白名单内）。
- 新增原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词；unknowns/protected/forbidden 为列表。
- 冲突轮落实：T18（改期原子 negation=true + 前态 superseded）、T16（伤退启停）、T33（谣言 uncertain + 拒绝事实回扣）、T35（财务口径 uncertain + 打折约定回扣）、T36（错误版本 superseded+negation=true）。
- 陷阱原子显式标注错误版本，正确处理路径在 T33/T35 卡的 current_action 与 forbidden 中给出。

## 6. 已知风险
- 时间线跨度 2026-12-16 至 2027-03-09，周三球局节奏、元旦改周四、1-18 友谊赛（周日）、春季赛报名均已自洽；语义审查时可重点复核星期对应（2026-12-16/23/30 为周三，2027-01-02 为周六？注：故事内仅以"周四晚/周日"文字口径呈现，未逐轮标注星期日历）。
- R3 语义审查（2026-11-02）修复：T40 原 ts 2027-03-11（周四）与台词"明天又是周三/明晚见"矛盾，按 ts 级修复权限改为 2027-03-09（周二），episode/reference(A92 valid_time)/timeline 哈希级联，validate 重跑 PASS。
- R3 登记未修（台词内容级，超出 ts/错别字权限）：T18"元旦那天周三"与真实日历不符（2027-01-01 为周五）；T22"1月18日周日"（2027-01-18 为周一）；T30"老周今天归队首局"落在周日而非周三球局（口径松）。详见 W03-06 报告。
- 场馆、赛事、体能课、财务流程均为剧情内设定，非真实机构承诺。
- 出窗探针 T25–T32 距源篇 T1–T12 达 13–20 轮，属长剧窗口外召回设计。

## 7. 出口检查
- [x] episode.json 40 轮、顶层键恰为 episode_id/persona/initial_kb/turns、clock/ts 逐轮单调、角色 HER、口语化台词、无评审术语
- [x] T1–T12 与源篇 E138 逐字一致（台词/ts/clock/may_decline/feedback/intent），仅 case_id 改名——程序化深度相等校验 PASS
- [x] reference.json story_id=LRE138、40 轮键集齐全、每轮字段齐全、quote 逐字、required_this_turn 不引当前轮
- [x] timeline.json story_id=LRE138、turn_clocks 40、message_index 40 条与 episode 完全一致、events/revisions 的 case_id 全部真实
- [x] exposure.json kind=long_drama_ext、source=E138、readers=["author"]、frozen_at=null、hashes=四文件 sha256、runner_copy 指向本目录 episode.json
- [x] 出窗探针 7 轮（T25/T26/T27/T28/T29/T31/T32）≥6；旧事复活陷阱 2 处（T33/T35）；新冲突事件 ≥2（体能课换时段、老周伤退、元旦改期、友谊赛对峙、谣言与口径更正）
