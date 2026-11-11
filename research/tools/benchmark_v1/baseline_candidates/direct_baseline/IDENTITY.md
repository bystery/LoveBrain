# DIRECT baseline — 身份卡

- 组别：A · Direct（Benchmark V1 Phase 1 新建轻量适配器）
- 假设：同一个模型，只给任务描述+可见信息+输出契约，不携带任何 LoveBrain harness 机制。
- 机制：单次调用；自累积可见真实聊天历史（HER/ME 剧本消息）；无记忆策略/无阶段机制/无风格槽。
- 输出契约：`{"action": "reply"|"no_reply", "recommended": str|null}`。
- 与 COT 的唯一差异：system 末尾多一行固定思考提示（其余逐字节同构）。
- 禁止：虚构历史；携带 LoveBrain 资产文本；读取 gold/未来轮。
