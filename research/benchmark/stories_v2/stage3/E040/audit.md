# E040 — 表姐看房的三方账 审计

## 0. 性质与暴露

- 合成虚构故事；不是用户真实素材，不含真实个人数据。
- 作者：main-zcode-20261030-0052（AI）；独立评审：无（dev 暴露，exposure.json 如实登记）。
- 8 turns；W03-02 新 DEV 批（stage3）。

## 1. 生活动机

帮亲戚看房，消息一层传一层；把谁说的、谁定的、谁还没定分清楚，比热心更重要。

## 2. 连续性与回扣

看房时间周六上午十点→周日下午两点；物业差的传闻保持为传闻。

## 3. ME 历史与边界

- ME 消息均为固定用户输入，两臂同等可见；不冒充模型已发送草稿。
- 备注位（note）共 0 处：无
- 用户边界按 initial_kb.lessons 与各轮 private_guidance 约束。

## 4. no_reply / 媒体局限

- may_decline 位：无。
- 本篇不涉及图片/媒体输入；时间戳均为 Asia/Shanghai。

## 5. 参考卡质量

- atoms 6 条 / 其中 required 1 条；每轮 acceptable_actions≥2，无单句标准答案。
- 卡面与 E040 原文逐字同源（builder 自动解析，无手写判据）。

## 6. 已知风险

- unknowns 共 6 项未在卡面给答案，评分时不得当 0 分用。
- T05 改期（表姐夫加入）；传闻到收尾仍未证实。

## 7. 出口检查

- [x] episode 无 gold/考点评语泄漏；[x] reference evaluator_only；
- [x] exposure dev_exposed=true / frozen_at=null；[x] runner 副本与 episode 一致。
