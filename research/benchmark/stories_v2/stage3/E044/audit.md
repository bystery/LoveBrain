# E044 — 对门的新邻居 审计

## 0. 性质与暴露

- 合成虚构故事；不是用户真实素材，不含真实个人数据。
- 作者：main-zcode-20261030-0052（AI）；独立评审：无（dev 暴露，exposure.json 如实登记）。
- 8 turns；W03-02 新 DEV 批（stage3）。

## 1. 生活动机

推荐被否不代表交情失败；接得住'不好吃'三个字，才有下一句家常。

## 2. 连续性与回扣

推荐 T04 →落空 T05 →调整 T06；猫寄养请求 T07 出现，边界在便签。

## 3. ME 历史与边界

- ME 消息均为固定用户输入，两臂同等可见；不冒充模型已发送草稿。
- 备注位（note）共 0 处：无
- 用户边界按 initial_kb.lessons 与各轮 private_guidance 约束。

## 4. no_reply / 媒体局限

- may_decline 位：无。
- 本篇不涉及图片/媒体输入；时间戳均为 Asia/Shanghai。

## 5. 参考卡质量

- atoms 7 条 / 其中 required 0 条；每轮 acceptable_actions≥2，无单句标准答案。
- 卡面与 E044 原文逐字同源（builder 自动解析，无手写判据）。

## 6. 已知风险

- unknowns 共 6 项未在卡面给答案，评分时不得当 0 分用。
- （无改期链；推荐修复是主线）

## 7. 出口检查

- [x] episode 无 gold/考点评语泄漏；[x] reference evaluator_only；
- [x] exposure dev_exposed=true / frozen_at=null；[x] runner 副本与 episode 一致。
