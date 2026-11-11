# v0_minimal_events — IDENTITY

最小 harness 对照候选。目的：检验 reference_product_v1 的多层记忆/摘要复杂度对实际
聊天收益是否值得，而不是继续在原版上叠机制。

- **不改弱原版**：原版仍是 48 次回忆实验的父本，本候选是独立第三条臂。
- **不预塞正确摘要**：只喂"当前输入 + 当前目标 + 预算内真实消息历史"。
- **记忆边界明确**：真实事件=episode 反馈里实际发送的 HER/ME；生成回复=草稿，永不写回真实历史。
- **能力缺失如实公开**（见 MECHANISM.md「未支持」）：无 topic/ongoing/scene 多层摘要、无自动画像重估/反思、无辅助生成、跨话题归档召回。
- **共享依赖进身份**：`rc.utf16_len` 与 `build_system_prompt` 复用父本 `reference_product_v1`（params.parent 声明，进入 harness_identity 闭包），不复制一份悄悄漂移。

身份与长度记录：`refs/experiment_protocol_20261023/V0_MINIMAL_FREEZE_20261024.json`。
