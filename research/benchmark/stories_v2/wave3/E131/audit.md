# E131 audit(W3-T1-B 隔离生成会话)

## 0. 性质与暴露
- author: AI(隔离生成会话 W3-T1-B)
- author_type: AI
- real_user_material: false(全部人物/机构/价格为合成)
- readers: ["author"];frozen_at: null;experiments_used: []
- kind: standard_12t;domain: 兴趣社交;conflict_type: 信息不确定类
- 批次: W3-T1-20261102;隔离声明已记入 STORY_CUSTODY_LEDGER(generation_isolation_ack)。

## 1. 生活动机
书友会转露营搭子,带零基础新手拼营,明火与预约两处规则信息打架,双渠道核实后按实测闭环。

## 2. 连续性与回扣
T1求带→T2明火三说打架→T3按电煮锅兜底→T4租买分工→T5预约口径含糊(不确定核心轮)→T6双通道核实→T7口径统一(免预约/明火登记)→T8周六班车→T9装备齐→T10天气路线→T11首刷复盘抛钩子→T12清账收尾;不确定原子T2/T5在T7被corrected。

## 3. ME 历史与边界
ME白砚露营五六次有帐篷炉子;边界为不坐黑车,不替她买贵装备。

## 4. no_reply / 媒体局限
T9(装备汇报)、T12(清账闲谈)may_decline=true;无媒体消息。

## 5. 参考卡质量
T2/T5不确定原子在T7必选并corrected;跨轮必选覆盖全程;unknowns逐轮明示口径来源。

## 6. 已知风险
营地规则为合成场景,不指涉真实景区;班车时刻为虚构。
- W03-06 T1-B 语义审查登记(2026-11-02):**阻塞级时间线缺陷**。作者侧按错误日历构建(把 2026-11-17 当周六),episode 原文与真实日历矛盾:T08(11-14 周六晚)定"周六进山"、T10(11-16)报"周六天气",按真实日历推得 11-21,但 T11(11-18)"刚从山里回来"、T12(11-20)"押金也退了"均先于 11-21 发生;11-17 实为周二。reference-only 缓解已做:A09/A11 valid_time 去除错误"(周六)"标签,T08 unknowns 登记禁断言进山日期。**判决:冻结/开测前必须由作者会话修复 ts 链**(如 T08→11-12、T09→11-13、T10→11-13、T11→11-16、T12→11-18 并同步 atom valid_time),修复前本篇不得进入实验。

## 7. 出口检查
- validate_story_v2 --stage wave3 --turns 12 --count 8(随分片 STAGE1_MANIFEST_B.json 全量机械校验)
- 冲突型落实:该型所在轮原子含 status 变化或 negation(见 revisions)。
- 台词零评审术语;quote 逐字自核;不虚构真实人名/电话/住址。
