# E061 — 甲状腺随访三个月 审计

## 0. 性质与暴露

- 合成虚构故事；不是用户真实素材，不含真实个人数据。
- 作者：main-zcode-20261030-0052（AI）；独立评审：无（dev 暴露，exposure.json 如实登记）。
- 24 turns；W03-02 新 DEV 批（stage3）。

## 1. 生活动机

女友查出甲状腺结节 3 类，随访链横跨五周；真实张力是她怕坏消息又要强，我要在'查证过但不专业'的位置上陪她把事办对。

## 2. 连续性与回扣

复查日期 5/20→5/6 更正在 T08；随访间隔 半年→3个月 更正在 T15；抽血加项 T13 披露；8/6 新复查日贯穿 T15-T23。

## 3. ME 历史与边界

- ME 消息均为固定用户输入，两臂同等可见；不冒充模型已发送草稿。
- 备注位（note）共 0 处：无
- 用户边界按 initial_kb.lessons 与各轮 private_guidance 约束。

## 4. no_reply / 媒体局限

- may_decline 位：无。
- 本篇不涉及图片/媒体输入；时间戳均为 Asia/Shanghai。

## 5. 参考卡质量

- atoms 18 条 / 其中 required 14 条；每轮 acceptable_actions≥2，无单句标准答案。
- 卡面与 E061 原文逐字同源（builder 自动解析，无手写判据）。

## 6. 已知风险

- unknowns 共 17 项未在卡面给答案，评分时不得当 0 分用。
- T08 旧日期 5 月 20 不得复活；T15 起随访间隔一律 3 个月；T13 起本项目=超声+甲功抽血；T17 起不忌碘。

## 7. 出口检查

- [x] episode 无 gold/考点评语泄漏；[x] reference evaluator_only；
- [x] exposure dev_exposed=true / frozen_at=null；[x] runner 副本与 episode 一致。
