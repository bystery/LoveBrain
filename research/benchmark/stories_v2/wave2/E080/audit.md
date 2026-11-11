# E080 糯米吞珠疑云 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 48 篇的 story_id/title/themes 防撞字段）。
- 全部人物（桃桃、老白）、鹦鹉糯米、异宠医院均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：宠物；主线：HER 桃桃的玄凤鹦鹉糯米掀翻首饰盘后疑似误食白珠，她先凭记忆报"少一颗、下午两点前完好"，夜间自查后双更正——珠子可能两颗、打翻时间在早上，观察窗拉长；呕吐出现后赴异宠医院拍片，实证消化道一颗高密度异物，医嘱观察三天垫白纸数便便，喂香油土办法被医生否掉；次日白珠随粪便排出、另一颗在拖鞋里找到，"两颗都吞了"被推翻，医生确认未划伤并延长观察两天；环境收纳重建、复盘入账，以带鸟验收与学剪指甲收尾。
- 生活动机真实可信：主人凭印象报数报时间、被实证打脸后自我更正，是突发宠物事件里最典型的信息修正链。

## 2. 连续性与回扣
- T01 误食嫌疑 → T02 观察首报（含错误时间陈述）→ T03 双更正爆发 → T04 呕吐与就医决策 → T05 拍片实证与医嘱 → T06 观察首晚 → T07 中段焦虑与复拍冲动 → T08 实物排出与数量收口 → T09 医生确认与延长观察 → T10 环境重建 → T11 复盘入账 → T12 验收之约收尾。
- 跨轮引用原子 6 条（A06/A15/A22/A32/A34/A36）≥3。
- 冲突型=更正类落实：T03（A06 superseded+negation，"只少一颗"被推翻；A08 superseded+negation，时间陈述被推翻）、T05（A14 negation，香油被医生否）、T08（A22 superseded+negation，"可能两颗"被推翻；A23 negation，"第二颗没吞"）。更正均有实证来源（自查、拍片、实物）。

## 3. ME 历史与边界
- ME 老白：发小、养过八年玄凤，糯米是老白帮着挑的（initial_kb warmth），T12 验收之约即此回扣。
- T12 may_decline=true：带鸟来访可商量；全程 ME 以"一条消息只给一个动作"的观察者身份在场，不诊断、不打包票。
- ME 不虚构异宠医学细节：拍片、异物可排、划伤风险均出自 HER 转述的医生口径，ME 只做医嘱复述与执行提醒；对香油偏方以"别喂"的边界立场处理，医学结论归医生。

## 4. no_reply / 媒体局限
- HER 单条长消息为主，T03/T05 为补发两条；全部为文本消息，无语音/图片（拍片、排出实物均以转述与描述呈现）。
- 未设 no_reply 轮。局限：医生的话全部经 HER 二传，属刻意的信息二传设计；实物照片不可见，仅凭文字描述。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T03（A06/A08 superseded+negation）、T05（A14 negation）、T08（A22/A23 superseded/negation）。
- 跨轮引用原子 6 条，≥3 条红线达成；required_this_turn 原子 6 条，全部引用早前轮次。

## 6. 已知风险
- 异宠医学细节（高密度影、可自行排出、观察三天）为剧情化简化，不构成兽医指导；医药费一千二为合成数字。
- 医生的判断均属 HER 转述引号，语义审查时注意引用层级不构成虚构第三方发言；香油偏方以否定边界处理，不再展开医学论证。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(宠物, 鹦鹉误食观察+数量时间双更正+医嘱闭环+环境重建, 更正类) 与既有篇三元组不同
