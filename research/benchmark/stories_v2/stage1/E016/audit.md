# E016 — 玩笑之后的认真 审计

## 0. 性质与暴露

- 合成虚构故事；不是用户真实素材，不含真实个人数据。
- 作者：QoderWork worker-AI；独立评审：无（同 worker 自检）。
- 8 turns；dev 候选，未用于本轮 P06 调参；见 `exposure.json`。

## 1. 生活动机

三次约会后，一句玩笑触到她的边界；她愿意继续，但要求直接。

## 2. 连续性与回扣

“不是喜欢的类型”→她想了半天→承认嘴欠→她说不喜欢试探→缓和→问她喜欢什么→再次划边界→收尾。

## 3. ME 历史与边界

me 的嘴贫/用玩笑试探在 initial_kb；没有编造共同经历。

## 4. no_reply / 媒体局限

纯文本；无 no_reply；T08 是明确收尾。

## 5. 参考卡质量

- 参考原子 8 条，其中本轮必需 1 条；每条 source.quote 由构建器逐字核验。
- 每轮至少 2 个可接受动作，不设唯一回话短语。
- may_decline 轮：无。
- 意图修订：E016_T05=ACTIVE；E016_T07=ADJUSTED。

## 6. 已知风险

模型容易用玩笑化解或过度道歉；参考卡要求先承认、再直接。

## 7. 出口检查

- [x] 8 turns；case_id 连续；HER/ME 时间不晚于 clock。
- [x] episode 无 gold、无未来消息、无“考察/观测/评分”等测评说明。
- [x] reference 只给 evaluator，不进入 candidate 可见输入。
- [x] initial_kb 全部为标量字符串；公开事实映射到 me/her/warmth/style/stage/lessons。
- [x] 至少两种可接受行动；不要求复述旧事实。
