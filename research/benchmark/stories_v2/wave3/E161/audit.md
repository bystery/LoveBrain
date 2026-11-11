# E161 审计（T2-A2 隔离生成会话）

## 0. 性质与暴露

- author=AI，real_user_material=false，全部人物、号码、地址均为合成，无真实用户材料。
- 本篇为跨故事连续篇 A 篇（pair=E161+E162），role=A。exposure.json readers=["author"]，frozen_at=null，experiments_used=[]。
- 隔离声明：本会话未读 logs/、未读 candidates/、未读任何评审/判决 JSON；仅读清单、模板、manifest 的 story_id/title/themes 字段与校验器源码。
- 三元组去重：(家居装修, [厨房水电改造/补点报价更正/复尺改期/工具借用归还], 报价更正与改期兑现)，与全库既有篇（含 E129/E130/E152 家居装修篇）主题集至少一元不同。

## 1. 生活动机

厨房水电开工时发现图纸疑点，业主自查出缺插座、比价、砍需求、改复尺、借工具，是一条普通业主装修的真实行动链：先保刚需（岛台），再控预算（砍冰箱后），再排日程（复尺改期），最后闭环（工具当天归还、对账收尾）。

## 2. 连续性与回扣

- 跨轮回扣（≥3）：T02/T03/T04 回扣 T01 的缺口清单；T05 回扣 T04 暗装二百六；T07 更正 T06 的周六取用；T08 回扣 T04 二百六并撤回需求；T09/T10 回扣 T07 门岗交接与 T08 撤回；T11 兑现 T10 的当天归还；T12 对账 T09/T11。
- 更正落实：T04（报价口径）、T07（取用改期）、T08（乌龙撤回）、T10（面板降级）均有 status 变化或 negation，对应 timeline.revisions。
- 对 B 篇（E162）的接口：借出（T06）、改期与门岗交接（T07/T10）、当天归还（T11）、二百六定案（T09/T12）、定金抵勾缝（T08）、完工请客与表妹钩子（T12）。A 篇不引用 E162，不泄露其内容。

## 3. ME 历史与边界

ME 沈聿去年自装过房、留有电动工具、借工具不收费（initial_kb.me）。ME 是参谋而非包工头：不替工头定价、不替她拍板、不代跑现场；意见干脆、落锤留给她。两人渐暧昧，调侃有分寸，不虚构亲密事实。

## 4. no_reply / 媒体局限

- T12 设 may_decline=true：收尾致谢加钩子，允许合法不回或极短回复。
- 本篇无媒体附件依赖：图纸、报价、单据均以口头转述出现，未假设照片/语音可被 ME 直接查看。
- 其余轮次均为需要回应的求助/确认/比价，不设 no_reply。

## 5. 参考卡质量

- 每轮 current_action + ≥2 acceptable_actions 多选，dimensions ≥3，均取自 DIMS 集合。
- 原子含 subject/relation/value（≥4 实义词）/valid_time/status/negation/source；quote 均为台词逐字子串，message_id=m0 与首条命中一致。
- 跨轮引用原子：T02/T03/T04/T05/T07/T08/T09/T10/T11/T12 均有更早轮来源且 required_this_turn=true，满足跨轮引用 ≥3。
- unknowns 登记未定项（工头答复、排产、请客日期等）；forbidden_factual_claims 阻止替第三方定价、断言未验证项。
- 说明：跨篇引用原子的机械实现为 source 指向 B 篇内 HER 逐字复述消息并附 cross_origin（指向 E161 轮 id），因校验器仅在本故事 case_index 内检索 message 来源；A 篇本身无跨篇原子（cross_story_refs=[]）。

## 6. 已知风险

- 工头报价、定金抵勾缝为 HER 转述，若被评审判为"第三方事实"需按转述口径处理，参考卡已在 forbidden_factual_claims 中约束。
- T11 十二点半出发/一点半送达与 T10 下午两点前的约定需按"提前兑现"理解，已在 A02 原子中写明。
- 装修术语（明装盒/暗装/勾缝/复尺）对 judge 可能有理解门槛，属题材固有成本。

## 7. 出口检查

- [x] 五件套齐全（episode/reference/timeline/audit/exposure），runner_episode 副本与 episode.json 一致。
- [x] 12 轮、gold={}、ts/clock 单调、消息角色 HER、case_id 全库唯一。
- [x] 三哈希（episode/reference/timeline）现算写入 exposure 与 STAGE1_MANIFEST_T2A2.json。
- [x] 机械校验 validate_story_v2.py（wave3, 12 turns, count=2, T2A2 分片）通过后入库。
