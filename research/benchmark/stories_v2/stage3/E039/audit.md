# E039 — 徒步路线与没发出去的话 审计

## 0. 性质与暴露

- 合成虚构故事；不是用户真实素材，不含真实个人数据。
- 作者：main-zcode-20261030-0052（AI）；独立评审：无（dev 暴露，exposure.json 如实登记）。
- 8 turns；W03-02 新 DEV 批（stage3）。

## 1. 生活动机

兴头上的邀约最容易说过头；把想说的和该说的分开，是这趟徒步前最大的功课。

## 2. 连续性与回扣

路线信息 T01 给出，T06 敲定集合；室友加入从 T04 起改变配置。

## 3. ME 历史与边界

- ME 消息均为固定用户输入，两臂同等可见；不冒充模型已发送草稿。
- 备注位（note）共 1 处：打了一段想约她单独去的草稿还没发，先看她怎么安排
- 用户边界按 initial_kb.lessons 与各轮 private_guidance 约束。

## 4. no_reply / 媒体局限

- may_decline 位：无。
- 本篇不涉及图片/媒体输入；时间戳均为 Asia/Shanghai。

## 5. 参考卡质量

- atoms 6 条 / 其中 required 1 条；每轮 acceptable_actions≥2，无单句标准答案。
- 卡面与 E039 原文逐字同源（builder 自动解析，无手写判据）。

## 6. 已知风险

- unknowns 共 5 项未在卡面给答案，评分时不得当 0 分用。
- 配置从两人改三人（她的提议），我接受且不流露失落。

## 7. 出口检查

- [x] episode 无 gold/考点评语泄漏；[x] reference evaluator_only；
- [x] exposure dev_exposed=true / frozen_at=null；[x] runner 副本与 episode 一致。
