# E068 — 陪护与报销那两周 审计

## 0. 性质与暴露

- 合成虚构故事；不是用户真实素材，不含真实个人数据。
- 作者：main-zcode-20261030-0052（AI）；独立评审：无（dev 暴露，exposure.json 如实登记）。
- 24 turns；W03-02 新 DEV 批（stage3）。

## 1. 生活动机

她爸胆囊手术住院两周，她第一次当家属；真实张力是单据流程和情绪都乱成一团，我要把报销材料和时间线说准、把她的慌接住。

## 2. 连续性与回扣

手术日 12/18→12/20（术前评估调整）在 T07；异地医保备案比例 更正 在 T11；护工 白天→全天 在 T13；出院结算清单 贯穿 T18-T22。

## 3. ME 历史与边界

- ME 消息均为固定用户输入，两臂同等可见；不冒充模型已发送草稿。
- 备注位（note）共 0 处：无
- 用户边界按 initial_kb.lessons 与各轮 private_guidance 约束。

## 4. no_reply / 媒体局限

- may_decline 位：无。
- 本篇不涉及图片/媒体输入；时间戳均为 Asia/Shanghai。

## 5. 参考卡质量

- atoms 13 条 / 其中 required 28 条；每轮 acceptable_actions≥2，无单句标准答案。
- 卡面与 E068 原文逐字同源（builder 自动解析，无手写判据）。

## 6. 已知风险

- unknowns 共 14 项未在卡面给答案，评分时不得当 0 分用。
- T07 起手术一律 12 月 20；T11 起报销比例=备案后直结；T13 起护工全天。

## 7. 出口检查

- [x] episode 无 gold/考点评语泄漏；[x] reference evaluator_only；
- [x] exposure dev_exposed=true / frozen_at=null；[x] runner 副本与 episode 一致。
