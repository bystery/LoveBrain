# arena_v1 覆盖矩阵（G05，C1/C2）

12 能力族 × 41 观察点；missing=0；字段审计 episodes=24 issues=0

## F1 HER/ME/IDEA来源与军师备注，草稿不能变成真实历史
- [应成功] `SMOKE_NOTE_TRAP_001`（SMOKE_NOTE_TRAP_001:SMOKE_NOTE_TRAP_001）军师备注（note）不得进入 HER 可见正文或被当作 HER 原话
- [对照] `SMOKE_NOTE_TRAP_002`（SMOKE_NOTE_TRAP_002:SMOKE_NOTE_TRAP_002）泄露反例：评分器必须抓住备注泄露/越权（预期低分，不得误放）
- [应成功] `NCA_t04`（NCA_CORR_INTENT_v1:NCA_t04）窗口含剧本 ME 消息（含'调岗'字样）→旧事实可被召回且来源标注正确，不误判为 HER 原话｜节点复用: F6

## F2 自由文本意图及优先级（推进/调侃暧昧/安抚/克制/收尾）
- [应成功] `PHASE05_INTENT_flirt:INT_t1`（PHASE05_INTENT_flirt:INT_t1）暧昧意图下分寸升级但不越界
- [应成功] `PHASE05_INTENT_restraint:INT_t1`（PHASE05_INTENT_restraint:INT_t1）克制意图：不推进不发邀约
- [对照] `PHASE05_INTENT:INT_t1`（PHASE05_INTENT:INT_t1）无意图注入：不得臆造推进/暧昧动作（与注入组对照）

## F3 普通朋友/新认识投缘/渐暧昧等不同分寸
- [应成功] `A_ch01_t01`（A_ch01_sample_v01:A_ch01_t01）普通朋友分寸：20 轮连续故事的自然承接
- [应成功] `PHASE05_INTENT_friend:INT_t1`（PHASE05_INTENT_friend:INT_t1）新认识投缘的试探分寸
- [应成功] `PHASE05_INTENT_flirt:INT_t1`（PHASE05_INTENT_flirt:INT_t1）渐暧昧分寸｜节点复用: F2
- [对照] `PHASE05_INTENT:INT_t1`（PHASE05_INTENT:INT_t1）分寸越界防护：无意图注入下不得臆造推进/暧昧动作（越权升级即失败信号）｜节点复用: F2

## F4 相关记忆影响回复，允许不显式复述记忆
- [应成功] `L3B_MINI_FULL:L3B_t05`（L3B_MINI_FULL:L3B_t05）过敏约束+房间边界回忆（关键词均不在当轮窗口）：允许自然化用，不要求逐字复述
- [对照] `L3B_MINI_CTRL:L3B_t05`（L3B_MINI_CTRL:L3B_t05）关记忆分支：关键词未命中属预期，不得因关记忆未复述而误杀｜节点复用: 本条第一项

## F5 无关旧事不侵入、长期跨度与跨话题干扰
- [应成功] `NCA_t03`（NCA_CORR_INTENT_v1:NCA_t03）坏消息情绪轮：期望不召回良渚/纪录片（无关话题不侵入）
- [应成功] `NCA_t01`（NCA_CORR_INTENT_v1:NCA_t01）空库新话题：无旧事实误召回
- [对照] `NCA_t05`（NCA_CORR_INTENT_v1:NCA_t05）相关事实不在当前窗口：检索观测位，'该召回而未召回'是失败信号而非误杀对象｜节点复用: F9

## F6 更正、取消、完成、错人；旧值失效与新值保留
- [应成功] `NCA_t04`（NCA_CORR_INTENT_v1:NCA_t04）调岗→换项目组：旧值失效、新值保留、口径采用正确
- [应成功] `NCB_t02`（NCB_ENTITY_DENSE_v1:NCB_t02）G7812 改签 D3145：旧车次失效新车次有效
- [应成功] `DEV_NEW_STATION_t01`（DEV_NEW_STATION_v1:DEV_NEW_STATION_t01）独立更正节点（dev 集）
- [对照] `NCB_t02`（NCB_ENTITY_DENSE_v1:NCB_t02）旧值失效防护：更正后旧车次（G7812）不得被采用为有效口径（回复须用 D3145 新口径）｜节点复用: F9

## F7 提议/确认/发生，进行中事项更新与过期
- [应成功] `NCB_t02`（NCB_ENTITY_DENSE_v1:NCB_t02）改签=提议状态更新（proposal→新 proposal，旧 proposal 过期）｜节点复用: F6
- [应成功] `NCB_t03`（NCB_ENTITY_DENSE_v1:NCB_t03）到站=提议→发生（状态证据化：event 落库）
- [应成功] `NCA_t02`（NCA_CORR_INTENT_v1:NCA_t02）新提议（看纪录片）建立，库存无前史不得误召回已发生口径
- [对照] `NCB_t02`（NCB_ENTITY_DENSE_v1:NCB_t02）提议过期防护：旧 proposal 被新 proposal 替换后不得当作仍有效安排｜节点复用: F6,F9

## F8 主体/地点/异地物理可行性/跨日与相对时间
- [应成功] `NCB_t03`（NCB_ENTITY_DENSE_v1:NCB_t03）合肥到站异地交接：地点与物理可行性口径｜节点复用: F7
- [应成功] `NCB_t04`（NCB_ENTITY_DENSE_v1:NCB_t04）回提主体=她（'上次拿过'主体是她）：主体不得错认
- [应成功] `DEV_MEETING_FACTS_t01`（DEV_MEETING_FACTS_v1:DEV_MEETING_FACTS_t01）跨日/时间事实（dev 集）
- [对照] `NCB_t04`（NCB_ENTITY_DENSE_v1:NCB_t04）主体错认防护：'上次拿过'主体是她——主体错认为 ME 或他人即失败信号｜节点复用: F7

## F9 具体时间/编号/数量有据；正确细节不能被删光
- [应成功] `NCB_t01`（NCB_ENTITY_DENSE_v1:NCB_t01）车次号 G7812 原样保留=客观契约（英数 token 独立证据通道）
- [应成功] `NCA_t05`（NCA_CORR_INTENT_v1:NCA_t05）组会每周二晚=initial_kb 客观契约，须答对
- [对照] `NCB_t02`（NCB_ENTITY_DENSE_v1:NCB_t02）旧车次召回不记失败（append-only 无降权）；判读只看回复是否采用 D3145 新口径——不得因提及旧车次而误杀｜节点复用: F6,F7

## F10 图片进请求、图片不可见时诚实承接
- [应成功] `L1_t07`（PILOT_L1:L1_t07）真实图片文件进请求（benchmark/media/pilot/L1_t07_phone.png）
- [应成功] `L2_t04`（PILOT_L2:L2_t04）多图进请求（商场组图）
- [对照] `L2_t01`（PILOT_L2:L2_t01）video_url_text 为虚构链接：图片/视频不可见时须诚实承接，不得假装看过

## F11 允许不回/必须正常回复/低信息量/冷场与重开
- [应成功] `NCA_t06`（NCA_CORR_INTENT_v1:NCA_t06）收尾 may_decline=true：no_reply 或自然收尾均合法，不发新邀约
- [应成功] `NCB_t05`（NCB_ENTITY_DENSE_v1:NCB_t05）收尾 may_decline=true：不追问不安排明天细节
- [应成功] `L1_t12`（PILOT_L1:L1_t12）PILOT L1 收尾 may_decline 节点
- [对照] `PHASE05_INTENT_none:INT_t1`（PHASE05_INTENT_none:INT_t1）低信息量轮：不得以'信息少'为由误判必须不回；默认正常回复

## F12 JSON/结构/动作/失败与下一轮行为的可审计性
- [应成功] `SMOKE_NOTE_TRAP_002`（SMOKE_NOTE_TRAP_002:SMOKE_NOTE_TRAP_002）对照题 mock_response 预期低分：评分器对泄露/越权动作可审计扣分｜节点复用: F1
- [应成功] `R1-R16契约矩阵`（MISSING）16 条契约规则实际执行取证（veto/case.input.may_decline/soft_keywords_any/id_map/正文关键词）
- [应成功] `15套既有测试`（MISSING）结构校验/失败持久化/评分权威/输入边界/无回复协议等（test_output_structure/test_failure_persistence/test_scoring_authority/test_input_boundary/test_no_reply_protocol 等）
- [对照] `SMOKE_NOTE_TRAP_002`（SMOKE_NOTE_TRAP_002:SMOKE_NOTE_TRAP_002）评分不误放防护：泄露/越权 mock_response 必须被评分器扣分（预期低分，不得误放）｜节点复用: F1

## C4 正反保护登记
复用真实节点的 control 已标 node_reuse_of，不算独立样本；未登记族如实声明。
- F1：control 1 点（独立 1，复用 无）
- F2：control 1 点（独立 1，复用 无）
- F3：control 1 点（独立 0，复用 PHASE05_INTENT:INT_t1）
- F4：control 1 点（独立 0，复用 L3B_MINI_CTRL:L3B_t05）
- F5：control 1 点（独立 0，复用 NCA_t05）
- F6：control 1 点（独立 0，复用 NCB_t02）
- F7：control 1 点（独立 0，复用 NCB_t02）
- F8：control 1 点（独立 0，复用 NCB_t04）
- F9：control 1 点（独立 0，复用 NCB_t02）
- F10：control 1 点（独立 1，复用 无）
- F11：control 1 点（独立 1，复用 无）
- F12：control 1 点（独立 0，复用 SMOKE_NOTE_TRAP_002）

## 字段审计问题
- （无）