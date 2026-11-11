# E014 — 旧同学的工作消息 审计

## 0. 性质与暴露

- 合成虚构故事；不是用户真实素材，不含真实个人数据。
- 作者：QoderWork worker-AI；独立评审：无（同 worker 自检）。
- 8 turns；dev 候选，未用于本轮 P06 调参；见 `exposure.json`。

## 1. 生活动机

六七年没见的旧同学，因为工作需求重新联系；既有旧情谊，也有真实预算边界。

## 2. 连续性与回扣

工地照片→旧话古建→HR自嘲→小展厅需求→预算只够概念→画册旧物→明天发需求。

## 3. ME 历史与边界

me 的旧话和画册都在 initial_kb；没有编造这些年做过的项目。

## 4. no_reply / 媒体局限

纯文本；没有 no_reply；T08 是明确收尾，不应强行续聊。

## 5. 参考卡质量

- 参考原子 8 条，其中本轮必需 0 条；每条 source.quote 由构建器逐字核验。
- 每轮至少 2 个可接受动作，不设唯一回话短语。
- may_decline 轮：无。
- 意图修订：E014_T08=ADJUSTED。

## 6. 已知风险

工作合作可能让模型过度承诺；参考卡把预算/周期列为必须问清的未知。

## 7. 出口检查

- [x] 8 turns；case_id 连续；HER/ME 时间不晚于 clock。
- [x] episode 无 gold、无未来消息、无“考察/观测/评分”等测评说明。
- [x] reference 只给 evaluator，不进入 candidate 可见输入。
- [x] initial_kb 全部为标量字符串；公开事实映射到 me/her/warmth/style/stage/lessons。
- [x] 至少两种可接受行动；不要求复述旧事实。
