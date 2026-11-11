# E125 audit(W3-T1-B 隔离生成会话)

## 0. 性质与暴露
- author: AI(隔离生成会话 W3-T1-B)
- author_type: AI
- real_user_material: false(全部人物/机构/价格为合成)
- readers: ["author"];frozen_at: null;experiments_used: []
- kind: standard_12t;domain: 餐饮聚会;conflict_type: 更正类
- 批次: W3-T1-20261102;隔离声明已记入 STORY_CUSTODY_LEDGER(generation_isolation_ack)。

## 1. 生活动机
读书会组织者张罗年终聚餐,报价口径记错后在截止前主动核实更正,邀请老同学当账房参谋。

## 2. 连续性与回扣
T1定计划→T2报价→T3人数→T5价格存疑→T6更正(88午市/128晚市)→T7锁包间→T8定菜单→T9人数回弹12人→T10菜单小更正→T11分工→T12复盘并抛周年展钩子;更正原子跨T2/T3/T6/T9/T10多轮回扣。

## 3. ME 历史与边界
ME陈拓为读书会成员三个月,帮排过两次签到表,会算账;边界为不白吃(主动提AA),不越俎代庖。

## 4. no_reply / 媒体局限
T4、T10为可短回同步轮,may_decline=true;无媒体消息。

## 5. 参考卡质量
每轮2-4个原子,T6更正轮含status=superseded/corrected与negation原子;跨轮必选原子覆盖全部12轮;unknowns逐轮明示。

## 6. 已知风险
价格与低消数字为合成,若实拍需替换;无真实人名机构。
- W03-06 T1-B 语义审查登记(2026-11-02):聚餐按 HER 三轮"周五"口径定为 2026-11-13(周五),参考卡 A17 valid_time 已由"2026-11-14(周五)"修正为"2026-11-13(周五)"(reference-only 修复)。episode 侧缺陷只登记不改:①T11(11-13 周五晨)称聚餐为"明晚",与周五口径错位一日(建议作者侧修 ts 至 11-12 或"明晚"→"今晚");②T12(11-14 23:05)称"今天的聚餐",若聚餐在 11-13 则 ts 应为 11-13;③T07"1408 加两瓶饮料凑低消"——1408 已超低消 1280,"凑低消"措辞松;④T10"鳜鱼进价贵"中鳜鱼未在 T08 菜单(豆豉蒸鱼)出现过;⑤T09"人均还能再摊薄"与 128 元/人自助口径松配。上述均不阻塞打分,但建议冻结前由作者会话微修 ①②。

## 7. 出口检查
- validate_story_v2 --stage wave3 --turns 12 --count 8(随分片 STAGE1_MANIFEST_B.json 全量机械校验)
- 冲突型落实:该型所在轮原子含 status 变化或 negation(见 revisions)。
- 台词零评审术语;quote 逐字自核;不虚构真实人名/电话/住址。
