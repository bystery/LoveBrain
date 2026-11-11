# E065 — 第一次当房东 审计

## 0. 性质与暴露

- 合成虚构故事；不是用户真实素材，不含真实个人数据。
- 作者：main-zcode-20261030-0052（AI）；独立评审：无（dev 暴露，exposure.json 如实登记）。
- 24 turns；W03-02 新 DEV 批（stage3）。

## 1. 生活动机

她继承了外婆的老房子第一次出租，我有过出租经验帮她把关；真实张力是她急着成交想让价，我要把合同里的钱和责任说死又不抢她的决定权。

## 2. 连续性与回扣

挂牌价 3800→3600（两周无果调价）在 T09；合同起租 2/1→2/15（她的搬家时间改）在 T12；租客情况 两人合租误判→同事合租确认 在 T15；押金数字 3800 贯穿。

## 3. ME 历史与边界

- ME 消息均为固定用户输入，两臂同等可见；不冒充模型已发送草稿。
- 备注位（note）共 0 处：无
- 用户边界按 initial_kb.lessons 与各轮 private_guidance 约束。

## 4. no_reply / 媒体局限

- may_decline 位：无。
- 本篇不涉及图片/媒体输入；时间戳均为 Asia/Shanghai。

## 5. 参考卡质量

- atoms 16 条 / 其中 required 28 条；每轮 acceptable_actions≥2，无单句标准答案。
- 卡面与 E065 原文逐字同源（builder 自动解析，无手写判据）。

## 6. 已知风险

- unknowns 共 15 项未在卡面给答案，评分时不得当 0 分用。
- T09 起挂价一律 3600；T12 起起租日一律 2 月 15；T15 起租客=同事合租两人。

## 7. 出口检查

- [x] episode 无 gold/考点评语泄漏；[x] reference evaluator_only；
- [x] exposure dev_exposed=true / frozen_at=null；[x] runner 副本与 episode 一致。
