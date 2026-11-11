# MECHANISM — RH-PIPE-V1

- 存储：三账本（said_ledger 问句/承诺/更正、invalidated 含传播链、state_card schema）每轮从全量历史确定性重算（12 轮规模毫秒级）
- 检索：M-A 段索引（Jaccard 骤降+6 轮封顶）→ M-D 查询重叠+近因加权选 top3 段注入原文（近 3 轮走尾部锚定不重复）
- 意图/上下文：意图进中部；头部=人物背景+状态卡；尾部=最近 3 轮+话语账本+失效账本+硬约束
- 输出契约：轻量 {"action","recommended"}
