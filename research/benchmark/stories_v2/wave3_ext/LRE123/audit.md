# LRE123 先问再喂·规则上岗的一冬（宠物 · 边界类 32 轮扩展版）审计单

批次：W3-T3-20261102 ｜ 会话：隔离长剧续写会话 G2（W3-T3-G2） ｜ author=AI ｜ real_user_material=false
隔离声明：本会话未读 logs/、candidates/、任何 VERDICT/REVIEW/评审 JSON/B3 队列；仅使用任务书白名单（源篇 E123 五件套 + GEN_CHECKLIST + 模板 + W03-06 wave3 骨架计划 §2 + 校验器 + LRE060 结构样例）。

## 0. 性质与暴露
- 本篇为 wave3_ext 长剧扩展：源 E123（12 轮）原位续写至 32 轮；T1–T12 逐字保留（台词/ts/clock/may_decline/intent/feedback 自源篇程序化改名继承，case_id E123_Txx→LRE123_Txx，reference 原子 atom_id 同步改名），改名后经程序化深度相等校验（12 轮深度相等 PASS）。
- exposure readers=["author"]、frozen_at=null、experiments_used 为空、dev_exposed=false；kind=long_drama_ext、source=E123。
- 新增人物（泰迪大姐、实习生）与麻薯拟犬化叙述全部合成，无真实人名、电话、住址。

## 1. 生活动机
- 域：宠物；主线：米乐与顾遥兑现 11-28 宠物公园之约——公园内游客试图投喂被拦、管理员介入，麻薯复诊通过进入七日换粮过渡（软便虚惊、配比推进、完赛验收），规则卡片过塑、胖哥投喂管理员上任，以跨年河边遛狗之约收尾。
- 生活动机真实可信：宠物公园陌生人投喂、医嘱换粮七日过渡、过渡期软便观察、部门群里投喂口径误传，都是养宠人高频摩擦。

## 2. 连续性与回扣
- C段 T13–T18（公园之约兑现）：T13 分工细化（回扣 T12）；T14 装备检查（回扣 T03 官方冻干/T04 标语）；T15 到场标语获赞；T16 冲突轮：泰迪大姐试图投喂火腿肠被当场拦下；T17 管理员介入化解；T18 拍照战报。
- D段 T19–T24（换粮过渡线）：T19 复诊通过处方粮退场（T19_A01 superseded+negation，source=T02"现在还在吃处方粮"）；T20 七三配比；T21 软便虚惊（医嘱稳比例）；T22 三七开（回扣 T09 便便早报）；T23 过渡期规矩重申（回扣 T03）；T24 下次友好日定档 2027-01-15、胖哥上任（回扣 T11 投喂管理员）。
- E段 T25–T28（出窗探针，4 轮 + C/D 段 4 轮共 8 轮依赖 T1–T12 原子）：T25 回扣 T03"必须先问我"（路人主动先问）；T26 回扣 T02"急性肠胃炎挂了两天水"；T27 回扣 T10"挂在牵引绳上了"；T28 回扣 T12"你负责拍照"。
- F段 T29–T32（旧事复活陷阱+收尾）：陷阱一 T29 实习生以"上回吃牛肉干都没事"推随便喂——侥幸结论不外推（T29_A01 negation=true，source=T09），T30 部门群规则重申；陷阱二 T31 胖哥把"换粮全换完"外推成"零食随便分"（T31_A01 negation=true，source=T03"别的零嘴一律不行"）；T32 跨年遛狗之约（may_decline=true，回扣 T12"就这么定了啊"）。
- 新冲突事件 ≥2：公园投喂冲击（T16–T17）、换粮过渡软便（T21），另有两处旧事复活陷阱（T29、T31）与规则外溢验证（T25）。
- required_this_turn 原子 16 个，全部指向早前轮次。

## 3. ME 历史与边界
- ME 顾遥：同事兼遛狗搭子、熟悉麻薯忌口、情绪稳；卷入为拍照与陪同（T13/T28 精修交付、T32 之约 may_decline=true）。
- ME 不替米乐答应投喂、不代行投喂答复权（答复权始终在米乐，T30 声明）；不虚构精修已交付。

## 4. no_reply / 媒体局限
- T32 允许不给准话；其余轮需明确回应。
- 胖哥、泰迪大姐、实习生、管理员、医生发言全部经 HER 转述；照片以转述呈现，无语音。

## 5. 参考卡质量
- 每轮 current_action 单值 + acceptable_actions ≥2 + applicable_dimensions ≥3（九维白名单内）。
- 冲突轮原子带 status 变化或 negation：T16（规则受冲击）、T19（superseded+negation 状态翻转）、T21（uncertain 观察）、T29/T31（negation=true 陷阱原子）；unknowns 明示（火腿肠成分、软便持续、耐受未知等）；private_guidance 仅影响口吻。

## 6. 已知风险
- 时间线跨 2026-11-24 至 2026-12-25，周六序列（11-28、12-05/12）与周五（12-18）已自洽；换粮七日（12-01 起首顿、12-18 完赛一周）已自洽。
- 换粮配比与医嘱表述为剧情内设定，非真实兽医建议；裁判须防把"牛肉干没事""换粮完成"外推为可随便喂。

## 7. 出口检查
- [x] episode.json 32 轮、顶层键恰为 episode_id/persona/initial_kb/turns、clock/ts 逐轮单调、HER 单条口语化、无评审术语
- [x] T1–T12 与源篇 E123 深度相等（仅 case_id/atom_id 改名），程序化校验 PASS
- [x] reference.json story_id=LRE123、32 轮键集齐全、quote 逐字、required_this_turn 不引当前轮
- [x] timeline.json turn_clocks 32、message_index 32 条与 episode 一致、events/revisions case_id 全部真实
- [x] exposure.json kind=long_drama_ext、source=E123、readers=["author"]、frozen_at=null、hashes=四文件 sha256
- [x] 出窗探针 ≥6（T13/T14/T15/T19/T22/T23/T25/T26/T27/T28 中 ≥8 轮依赖 T1–T12 原子）；旧事复活陷阱 2 处（T29、T31）均以 negation 原子显式标注
