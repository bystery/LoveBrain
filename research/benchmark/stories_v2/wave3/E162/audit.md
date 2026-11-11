# E162 审计（T2-A2 隔离生成会话）

## 0. 性质与暴露

- author=AI，real_user_material=false，全部人物、物品、单据均为合成，无真实用户材料。
- 本篇为跨故事连续篇 B 篇（pair=E161+E162），role=B。exposure.json readers=["author"]，frozen_at=null，experiments_used=[]。
- 隔离声明：本会话未读 logs/、未读 candidates/、未读任何评审/判决 JSON；仅读清单、模板、manifest 的 story_id/title/themes 字段与校验器源码。
- 三元组去重：(家庭事务, [工具归还免赔/旧烤箱转让/送货改期/完工作客之约], 免赔边界与转让信息更正)，与全库既有篇（含 E119/E120/E146/E155 家庭事务篇）主题集至少一元不同。

## 1. 生活动机

借用工具归还后礼节性提出赔偿，装修单据出现与既定撤回决定冲突的笔误需更正，长辈腾车库翻出旧物要处置，白送被边界拆开后按瑕疵件定价转让，送货遇加班改期——全是熟人往来里最常见的账目与边界小事。

## 2. 连续性与回扣

- 跨篇引用机制：B 篇 HER 在对话中逐字回述 A 篇已发生事件，reference 原子 source 指向本篇回述消息（quote 机械可验），cross_origin 字段登记 A 篇轮 id，共 9 处（T01→E161_T11、T02→E161_T06、T03→E161_T08/T09、T04→E161_T08、T09→E161_T12/T06、T10→E161_T12、T12→E161_T01），满足 ≥3。
- 事件因果：A 篇 T06/T11 借还闭环 → B 篇 T01/T02 裂纹赔偿；A 篇 T08 撤回与抵扣 → B 篇 T03/T04 单据更正；A 篇 T12 完工请客与表妹钩子 → B 篇 T09/T10 兑现。
- 篇内跨轮回扣（≥3）：T02 回扣 T01、T05 回扣 T02、T06 回扣 T05、T08 回扣 T07、T09 回扣 T08、T11 回扣 T08、T12 回扣 T09/T11。
- B 篇不泄露 A 篇未来剧情：A 篇全部事件在 B 篇开始前已完结；B 篇亦不预写自身 T12 之后的走向。

## 3. ME 历史与边界

ME 沈聿工具外借不收费（initial_kb.me），但对"白送当赔偿"的捆绑立边界：赠品归赠品、赔偿归赔偿。参谋角色不替商家定价、不替表妹拍板、价格按瑕疵件一口价定死不拉扯。渐暧昧关系延续调侃口吻，不虚构亲密事实。

## 4. no_reply / 媒体局限

- T11、T12 设 may_decline=true：轻量补充与致谢收尾，允许合法不回或短回复。
- 媒体局限：T10/T11 提到"拍了段视频发你微信"，故事不假设 ME 能真实播放视频，参考卡只要求给判断框架与回复期限（周末前），不要求对视频内容下断言（见 forbidden_factual_claims）。
- T01-T09 均为需要回应的求助/确认/定价，不设 no_reply。

## 5. 参考卡质量

- 每轮 current_action + ≥2 acceptable_actions，dimensions ≥3 均在 DIMS 集合内。
- 原子字段齐全，value ≥4 实义词，quote 为本篇台词逐字子串，message_id=m0 与首条命中一致；required_this_turn 原子均来自更早轮或 initial_kb（校验器禁止 current-turn required）。
- 跨篇引用原子的实现说明：校验器仅在本故事 case_index 内检索 message 来源，故 source.case_id 取 B 篇内 HER 逐字复述轮，cross_origin 承载 A 篇轮 id；reference.json 顶层 cross_story_refs 汇总 9 处跨篇引用。
- 更正落实：T04（单据笔误更正）、T06（白送撤销+瑕疵坦白，negation=true）、T08（周六取消改期，negation=true）、T09（四项定案）均有 status 变化，对应 timeline.revisions。
- unknowns 登记裂纹成因、烤箱另一根管寿命、视频内容等未定项。

## 6. 已知风险

- 裂纹是否 HER 责任未定（T01 status=uncertain），若评审判为已定责任需按参考卡 forbidden_factual_claims 阻断。
- 商家赵经理的更正为 HER 转述，属二手信息，参考卡已约束不得当作已核实的结算结论。
- "玻璃罐""酸豆角"等家常物件密度较高， judge 需按互动细节而非计分点处理。

## 7. 出口检查

- [x] 五件套齐全（episode/reference/timeline/audit/exposure），runner_episode 副本与 episode.json 一致。
- [x] 12 轮、gold={}、ts/clock 单调、消息角色 HER、case_id 全库唯一（与 E161_Txx 无碰撞）。
- [x] 三哈希（episode/reference/timeline）现算写入 exposure 与 STAGE1_MANIFEST_T2A2.json。
- [x] 机械校验 validate_story_v2.py（wave3, 12 turns, count=2, T2A2 分片）通过后入库。
