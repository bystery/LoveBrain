# E063 — 驾照考程四个月 审计

## 0. 性质与暴露

- 合成虚构故事；不是用户真实素材，不含真实个人数据。
- 作者：main-zcode-20261030-0052（AI）；独立评审：无（dev 暴露，exposure.json 如实登记）。
- 24 turns；W03-02 新 DEV 批（stage3）。

## 1. 生活动机

她在考驾照，我当陪练兼信息员；真实张力是考程一再变动她怕自己'太笨'，我要把流程规则说准又不越界替她做决定。

## 2. 连续性与回扣

科目二考期 11/28→12/5（考场系统升级）在 T08；练车时段 工作日晚→周末早 在 T10；教练更换 在 T14；补考间隔 10 天 规则贯穿 T16-T22。

## 3. ME 历史与边界

- ME 消息均为固定用户输入，两臂同等可见；不冒充模型已发送草稿。
- 备注位（note）共 0 处：无
- 用户边界按 initial_kb.lessons 与各轮 private_guidance 约束。

## 4. no_reply / 媒体局限

- may_decline 位：无。
- 本篇不涉及图片/媒体输入；时间戳均为 Asia/Shanghai。

## 5. 参考卡质量

- atoms 14 条 / 其中 required 27 条；每轮 acceptable_actions≥2，无单句标准答案。
- 卡面与 E063 原文逐字同源（builder 自动解析，无手写判据）。

## 6. 已知风险

- unknowns 共 19 项未在卡面给答案，评分时不得当 0 分用。
- T08 起科目二一律 12 月 5 日；T10 起练车一律周末早上；T14 起教练=王教练；旧日期/旧时段/李教练不得复活。

## 7. 出口检查

- [x] episode 无 gold/考点评语泄漏；[x] reference evaluator_only；
- [x] exposure dev_exposed=true / frozen_at=null；[x] runner 副本与 episode 一致。
