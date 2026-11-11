# LRE141 恒信之后：年会档期与旧口径回声 审计（工作职场 / 更正类长剧续写）

## 0. 性质与暴露
- author=AI(W3-T3-G6)，隔离长剧续写会话
- real_user_material=false
- 本篇为 wave3_ext 长剧续写：源=E141（工作职场/更正类），T1-T12 逐字保留，扩至 40 轮。
- 本会话未读 logs/、candidates/、任何 VERDICT/REVIEW/评审 JSON/B3 队列；仅使用任务书白名单材料。
- episode_id=story_id=LRE141；T1-T12 台词文本、ts、clock、may_decline、feedback 自 E141 逐字继承，仅 case_id 由 E141_Txx 改名 LRE141_Txx；reference 原子 atom_id 同步改名 E141_Txx_Ayy -> LRE141_Txx_Ayy（映射一致），新增原子自 LRE141_T13_A00 续编。
- 全部人物（小满、阿灿、拉哥、小郑、印厂、行政、客户档案室）均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null，kind=long_drama_ext，source=E141。

## 1. 生活动机
- 域：工作职场；主线：包装项目收官后的年会物料全程——brief 落地、年会提前一周、方向稿两版选一、插单撞期、背景板加宽与预算权衡、印厂烫金事故、验收开场、复盘与季度续约。
- 生活动机真实可信：年会改期、同日双项目撞车、喷绘超预算二选一、印厂装版歪斜重印、归档版本口径，均为乙方设计岗高频摩擦。

## 2. 连续性与回扣
- C段 T13-T19：brief 落地（回扣 T11/T12 年会预定与下周一节点）-> 年会提前（T15 冲突1）-> 方向稿深蓝选中 -> 启润插单撞期（T18 冲突2）-> 拉哥拍板恒信优先。
- D段 T20-T27：周日香锅（回扣 T10）-> 客户索 v2 归档（T21 冲突3+陷阱2）-> 撤回收尾 -> 背景板加宽 -> 预算二选一（T24 冲突4）-> 哑光布落定 -> 复盘会探针 -> 物料进厂。
- E段 T28-T36：周末闲聊 -> 烫金事故（T29 冲突5）-> 重印留样 -> 验收报喜 -> 年会开场 -> 圆满落幕 -> 复盘交付节奏探针（回扣 T12 九点归档）-> 季度框架（回扣 T03 乌龙翻篇）。
- F段 T37-T40：庆功火锅定档（回扣 T10 加急餐）-> 年后排期探针（回扣 T19 恒信优先）-> 年末总结 -> 平安夜收尾（may_decline=true）。
- 出窗探针 7 轮依赖 T1-T12 早期原子：T13（T11 年会物料还找你）、T16（T01/T03 周五口径与下周三更正）、T20（T10 麻辣香锅）、T21（T04 v2 先不用发了）、T34（T12 稿子九点就归档啦）、T35（T03 是我听岔了）、T36（T10 加急餐我请）。
- 旧事复活陷阱 3 处：T16 周五交付旧口径被复述（不沿用，召回更正史实）；T21 已冻结 v2 被索要归档（不外发，终版纪律）；T34 交付节奏被记岔为下午拖交（以台历九点归档更正）。

## 3. ME 历史与边界
- ME 阿灿：设计组包装视觉，台历记节点、锁定文字层、回话先复述要点（initial_kb 继承原篇）。
- 新增边界：T18 插单不越过 PM 私下应下双单，优先级交拉哥；T21 不把冻结旧版当有效版本外发；T24 不替行政拍板花钱。
- 新增卷入均为可拒绝或轻量帮忙，无虚构垫付、无虚构到场。

## 4. no_reply / 媒体局限
- 40 轮全部为 HER 单条文本消息；打样照片、报价单均以转述呈现，无图片/语音。
- T40 may_decline=true 为唯一可拒轮（源篇 T12 同类收尾口径）。
- 局限：拉哥、小郑、印厂、行政、客户档案室的发言全部经 HER 转述，延续源篇"信息二传"设计。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 2-3 条、applicable_dimensions ≥3（均在九维白名单内）。
- 新增原子全部带逐字 quote 与 message_id；value 均 ≥4 实义词；unknowns/protected/forbidden 为列表。
- 冲突轮落实：T15（A00 superseded+negation）、T18（边界）、T21（A00 negation 陷阱）、T24（费用）、T29（生产事故）、T26/T34（出窗召回 required 原子）。
- private_guidance 只影响口吻，不承载事实新信息。

## 6. 已知风险
- 时间线跨度 11-11 至 12-24，年会两次改期与送货节点已自洽；语义审查可复核星期对应（11-16/12-14 周一、11-21/12-05/12-12/12-19 周六、12-11 周五）。
- T26 盒盖开合方向按设计显式留白"具体左右无存档"，评审时不得把未知当错误。
- 喷绘超预算四成多、哑光布灯光补反光为剧情内设定，非真实供应商政策承诺。

## 7. 出口检查
- [x] episode.json 40 轮、顶层键恰为 episode_id/persona/initial_kb/turns、clock/ts 逐轮单调、角色 HER、口语化台词、无评审术语
- [x] T1-T12 与源篇 E141 逐字一致（台词/ts/clock/may_decline/feedback），仅 case_id 改名，程序化深度相等 PASS
- [x] reference.json story_id=LRE141、40 轮键集齐全、每轮字段齐全、quote 逐字、required_this_turn 不引当前轮
- [x] timeline.json story_id=LRE141、turn_clocks 40、message_index 与 episode 完全一致、events/revisions 的 case_id 全部真实
- [x] exposure.json kind=long_drama_ext、source=E141、real_user_material=false、readers=["author"]、hashes 现算
- [x] 新冲突事件 5 个（改期/插单/索 v2/预算/烫金）≥2；出窗探针 ≥6 轮；旧事复活陷阱 3 处
