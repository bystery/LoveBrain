# E126 audit(W3-T1-B 隔离生成会话)

## 0. 性质与暴露
- author: AI(隔离生成会话 W3-T1-B)
- author_type: AI
- real_user_material: false(全部人物/机构/价格为合成)
- readers: ["author"];frozen_at: null;experiments_used: []
- kind: standard_12t;domain: 餐饮聚会;conflict_type: 取消改期类
- 批次: W3-T1-20261102;隔离声明已记入 STORY_CUSTODY_LEDGER(generation_isolation_ack)。

## 1. 生活动机
同事午饭搭子约新开湘菜馆拔草,两次因临时会与出差取消改期,以云点菜玩法补偿并立奶茶赌约。

## 2. 连续性与回扣
T1邀约→T2取号→T3取消改明晚→T4预约号→T5出差再取消→T6云点菜→T7存菜单→T8延至周四→T9落地立赌约→T10临行确认→T11吃成并抛云南菜钩子→T12周末回扣;取消原子在T3/T5、改期原子在T4/T8形成链条。

## 3. ME 历史与边界
ME顾北为三个月午饭搭子,嘴稳会踩点;边界为不拿取消开玩笑,不追问总监安排细节。

## 4. no_reply / 媒体局限
T6(云点菜提议可婉拒)、T12(周末闲聊)may_decline=true;无媒体消息。

## 5. 参考卡质量
T3/T5取消轮原子negation=true且status=cancelled;改期链条跨轮必选;unknowns逐轮明示。

## 6. 已知风险
出差城市与高铁时刻为合成;不指涉任何真实店铺。
- W03-06 T1-B 语义审查登记(2026-11-02):参考卡 A06 valid_time 由"2026-11-05 19:00"修正为"2026-11-06 19:00"(reference-only 修复;依据 T04 于 11-05 称"明晚"、T05 于 11-06 晨仍退"晚上的预约")。episode 侧缺陷只登记不改:①改期链存在一日漂移(T03"改明晚"=11-05 口径 vs T04/T05 执行口径 11-06),已在 T04 unknowns 登记;②T06"我到杭州了"(11-06 午)与 T05"明早七点二十出发"(11-07 晨)矛盾,已在 T06 unknowns 登记;③T10(周四 22:15)称"你下班直接过去,我提前十分钟到门口等你",发送时刻晚于当日聚餐时段,按临行确认理解,登记不动。建议冻结前由作者会话微修 ②(T05 ts 改 11-05 晨最省)。

## 7. 出口检查
- validate_story_v2 --stage wave3 --turns 12 --count 8(随分片 STAGE1_MANIFEST_B.json 全量机械校验)
- 冲突型落实:该型所在轮原子含 status 变化或 negation(见 revisions)。
- 台词零评审术语;quote 逐字自核;不虚构真实人名/电话/住址。
