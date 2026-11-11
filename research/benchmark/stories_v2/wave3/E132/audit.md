# E132 audit(W3-T1-B 隔离生成会话)

## 0. 性质与暴露
- author: AI(隔离生成会话 W3-T1-B)
- author_type: AI
- real_user_material: false(全部人物/机构/价格为合成)
- readers: ["author"];frozen_at: null;experiments_used: []
- kind: standard_12t;domain: 医疗健康;conflict_type: 更正类
- 批次: W3-T1-20261102;隔离声明已记入 STORY_CUSTODY_LEDGER(generation_isolation_ack)。

## 1. 生活动机
待入职新人做体检,两处口径误会(十点报到vs九点半采血截止、本部vs城东分院)接连更正,医学同学全程参谋。

## 2. 连续性与回扣
T1体检安排→T2空腹疑问→T3截止时间更正改约最早场(更正核心轮)→T4加项犹豫→T5不加项决定→T6机构记错更正→T7踩点动线→T8空腹准备→T9抽血完成→T10报告解读→T11隐私边界执行→T12糖水答谢;更正原子在T3/T6两轮,状态变化清晰。

## 3. ME 历史与边界
ME岑聿医学院研究生,讲证据;边界为不做确定诊断,解释留余地说以机构要求为准。

## 4. no_reply / 媒体局限
T5(被说服后确认)、T12(收尾请客)may_decline=true;无媒体消息。

## 5. 参考卡质量
T3轮十点口径superseded+negation、九点半口径corrected;T6机构原子corrected;跨轮必选覆盖全程;unknowns逐轮明示。

## 6. 已知风险
窦性心动过缓解释为一般科普口径,不构成医疗建议;机构与人事为合成。
- W03-06 T1-B 语义审查登记(2026-11-02):**阻塞级时间线缺陷**。HER 于 T01(11-05 周四)与 T04(11-07 周六)两次称体检"下周三"(真实日历=11-11 周三,参考卡 A02/A05 valid_time 与之一致),但事件链 T07 踩点(11-11 晚)、T08 禁食(11-12 晚)、T09 抽血完成(11-13)整体晚两日——作者侧疑似按 11-13=周三 的错误日历构建。reference-only 缓解已做:T08 unknowns 登记日期矛盾、禁圆场。**判决:冻结/开测前必须由作者会话修复 ts 链**(最小修复:T07 ts→11-09 或 11-10 晚、T08 ts→11-10 晚、T09 ts→11-11 午;下游 T10 报告三个工作日出恰与 11-11 体检衔接,11-16 出报告无需改),修复前本篇不得进入实验。

## 7. 出口检查
- validate_story_v2 --stage wave3 --turns 12 --count 8(随分片 STAGE1_MANIFEST_B.json 全量机械校验)
- 冲突型落实:该型所在轮原子含 status 变化或 negation(见 revisions)。
- 台词零评审术语;quote 逐字自核;不虚构真实人名/电话/住址。
