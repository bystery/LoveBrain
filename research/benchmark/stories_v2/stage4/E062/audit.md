# E062 — 一支镜头的转手全流程 审计

## 0. 性质与暴露

- 合成虚构故事；不是用户真实素材，不含真实个人数据。
- 作者：main-zcode-20261030-0052（AI）；独立评审：无（dev 暴露，exposure.json 如实登记）。
- 24 turns；W03-02 新 DEV 批（stage3）。

## 1. 生活动机

同好群里认识半年的插画师收我的闲置镜头；真实张力是钱和参数都要当场说清，谁说错谁更正，不留尾巴。

## 2. 连续性与回扣

挂价4300含罩 T01；光圈 f4 更正在 T07；面交 周六→周日 更正在 T10；灰点议价后总价 4400 定格 T14；快门数 8200 贯穿 T05/T19/T24。

## 3. ME 历史与边界

- ME 消息均为固定用户输入，两臂同等可见；不冒充模型已发送草稿。
- 备注位（note）共 0 处：无
- 用户边界按 initial_kb.lessons 与各轮 private_guidance 约束。

## 4. no_reply / 媒体局限

- may_decline 位：无。
- 本篇不涉及图片/媒体输入；时间戳均为 Asia/Shanghai。

## 5. 参考卡质量

- atoms 20 条 / 其中 required 23 条；每轮 acceptable_actions≥2，无单句标准答案。
- 卡面与 E062 原文逐字同源（builder 自动解析，无手写判据）。

## 6. 已知风险

- unknowns 共 14 项未在卡面给答案，评分时不得当 0 分用。
- T07 起此镜头是 f4 不是 2.8；T10 起面交一律周日；T14 起总价 4400（4300+环300-灰点200）。

## 7. 出口检查

- [x] episode 无 gold/考点评语泄漏；[x] reference evaluator_only；
- [x] exposure dev_exposed=true / frozen_at=null；[x] runner 副本与 episode 一致。
