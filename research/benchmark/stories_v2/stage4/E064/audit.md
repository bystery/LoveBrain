# E064 — 一只猫的医疗时间线 审计

## 0. 性质与暴露

- 合成虚构故事；不是用户真实素材，不含真实个人数据。
- 作者：main-zcode-20261030-0052（AI）；独立评审：无（dev 暴露，exposure.json 如实登记）。
- 24 turns；W03-02 新 DEV 批（stage3）。

## 1. 生活动机

她的猫到龄该绝育，我帮着对时间线；真实张力是她第一次养猫样样怕出错，我要把疫苗/绝育/护理的时间口径说准又不过度插手。

## 2. 连续性与回扣

绝育日期 12/3→12/10（医生排班）在 T07；第二针提前一周 在 T04；术后拆线 自己拆→医院拆 在 T16；狂犬年检 在 T20。

## 3. ME 历史与边界

- ME 消息均为固定用户输入，两臂同等可见；不冒充模型已发送草稿。
- 备注位（note）共 0 处：无
- 用户边界按 initial_kb.lessons 与各轮 private_guidance 约束。

## 4. no_reply / 媒体局限

- may_decline 位：无。
- 本篇不涉及图片/媒体输入；时间戳均为 Asia/Shanghai。

## 5. 参考卡质量

- atoms 15 条 / 其中 required 26 条；每轮 acceptable_actions≥2，无单句标准答案。
- 卡面与 E064 原文逐字同源（builder 自动解析，无手写判据）。

## 6. 已知风险

- unknowns 共 17 项未在卡面给答案，评分时不得当 0 分用。
- T07 起手术一律 12 月 10 日；T04 起第二针=12 月 1 日；T16 起拆线=回医院；旧值不得复活。

## 7. 出口检查

- [x] episode 无 gold/考点评语泄漏；[x] reference evaluator_only；
- [x] exposure dev_exposed=true / frozen_at=null；[x] runner 副本与 episode 一致。
