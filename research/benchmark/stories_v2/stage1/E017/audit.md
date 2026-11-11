# E017 — 周六早上再决定 审计

## 0. 性质与暴露

- 合成虚构故事；不是用户真实素材，不含真实个人数据。
- 作者：QoderWork worker-AI；独立评审：无（同 worker 自检）。
- 8 turns；dev 候选，未用于本轮 P06 调参；见 `exposure.json`。

## 1. 生活动机

她连续加班，周末可能有一天休；我早就提过一个小展，她主动问起。

## 2. 连续性与回扣

加班累→问展期/免费/预约→可能有一天休→我周六下午在→临时约→只能一小时→门票→周六早上决定→收尾。

## 3. ME 历史与边界

me 的周六下午空档来自 initial_kb；没有编造展票信息。

## 4. no_reply / 媒体局限

纯文本；无 no_reply；T08 是收尾，不应再确认时间。

## 5. 参考卡质量

- 参考原子 8 条，其中本轮必需 1 条；每条 source.quote 由构建器逐字核验。
- 每轮至少 2 个可接受动作，不设唯一回话短语。
- may_decline 轮：无。
- 意图修订：E017_T04=ACTIVE；E017_T07=ADJUSTED。

## 6. 已知风险

模型容易把“临时约”说成“说定了”；参考卡明确她未答应。

## 7. 出口检查

- [x] 8 turns；case_id 连续；HER/ME 时间不晚于 clock。
- [x] episode 无 gold、无未来消息、无“考察/观测/评分”等测评说明。
- [x] reference 只给 evaluator，不进入 candidate 可见输入。
- [x] initial_kb 全部为标量字符串；公开事实映射到 me/her/warmth/style/stage/lessons。
- [x] 至少两种可接受行动；不要求复述旧事实。
