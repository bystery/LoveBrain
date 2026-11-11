# IDENTITY — action_policy_r9_slim

- 谱系：action_policy_r7_supersede（现役冠军）的 SLIM 变体，W09-02 产物。
- 机制：完全继承 r7（supersede_contradiction 引擎侧作废 + mem_dump + E3 修复链），新增
  slim_callback_user 标志（剥除 r2 在 user 侧注入的【回扣有据】重复陈述）。
- 资产：自带 system_prompt/。core.md 删两处（AI 暴露禁令——红线已有；期望的可用回复节——
  互动 item1 + 自然度 item1 已覆盖）；redline.md 删情绪簇 item2（core 底线 item2 并集覆盖）。
- 不变量：信息边界（core「你在依据什么说话」四类 + 一句话总结）与输出合同（format.md 全部 +
  r2 _HYGIENE 单 JSON 硬约束）零改动。
- 防回退：slim_callback_user=false + 原 assets = 行为回 r7。
- 状态：重放范围证明 PASS（W09-02_REPLAY_SLIM_20261031.json）；**行为中立性未验证**——
  需 live 批（建议：r9_slim vs r7 双臂 SEALED 故事 FS1 协议）才可晋升。
