# E020 — 年糕复查之后 审计

## 0. 性质与暴露

- 合成虚构故事；不是用户真实素材，不含真实个人数据。
- 作者：QoderWork worker-AI；独立评审：无（同 worker 自检）。
- 8 turns；dev 候选，未用于本轮 P06 调参；见 `exposure.json`。

## 1. 生活动机

猫复查好转，她松一口气；接着愁十二月旅行假期，两个人在不确定里找可取消方案。

## 2. 连续性与回扣

年糕复查→感谢医生→厦门假未批→先别订→价格可能涨→可取消酒店→我十二月不确定→她不想催→煮鸡胸肉收尾。

## 3. ME 历史与边界

医生推荐和十二月项目不确定都在 initial_kb；没有编造猫的检查数据。

## 4. no_reply / 媒体局限

纯文本；无 no_reply；T08 是收尾。

## 5. 参考卡质量

- 参考原子 8 条，其中本轮必需 2 条；每条 source.quote 由构建器逐字核验。
- 每轮至少 2 个可接受动作，不设唯一回话短语。
- may_decline 轮：无。
- 意图修订：E020_T03=ACTIVE；E020_T07=ADJUSTED。

## 6. 已知风险

模型容易空口保证旅行；参考卡要求诚实说不确定。

## 7. 出口检查

- [x] 8 turns；case_id 连续；HER/ME 时间不晚于 clock。
- [x] episode 无 gold、无未来消息、无“考察/观测/评分”等测评说明。
- [x] reference 只给 evaluator，不进入 candidate 可见输入。
- [x] initial_kb 全部为标量字符串；公开事实映射到 me/her/warmth/style/stage/lessons。
- [x] 至少两种可接受行动；不要求复述旧事实。
