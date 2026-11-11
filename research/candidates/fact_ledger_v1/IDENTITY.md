# IDENTITY.md — fact_ledger_v1

- 本代假设：FK-LR2-01（长剧域逐字事实保持塌陷）主因是"persona 种子 + 最近消息滑窗"的概括粒度不足以承载逐字召回；把保持职责从滑窗容量移到结构化账本（capture 逐字入账 + top-k 相关度注入）可将 T25+ 段真实保持率 ~50% 提到 ≥85% 且 ≤24 轮域不退化。
- 机制 DNA：父本 v0_minimal_events（简单事件记忆 + 精简 system）+ M1 账本存储（ledger_rules 规则化逐字条目）+ M2 检索注入（w_type×相关度×时间衰减 top-k，pin/去重/800 CU 预算）+ r7 supersede 语义移植。
- 父本：v0_minimal_events（基座逐字复用其 init/snapshot/restore 骨架与 _uid_for/_budget_history/rc；run_turn 装配显式复制并插入账本段，不 monkeypatch）。
- 故障族：FK-LR2-01（refs/KNOWN_FAILURES.md 尾部）；设计文档 W12_FACT_LEDGER_DESIGN_20261101.md。
