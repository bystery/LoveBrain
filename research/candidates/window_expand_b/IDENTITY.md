# IDENTITY.md — window_expand_b

- 本代假设：FK-LR2-01 对照臂——若历史窗口 3600 CU + 确定性摘录 recap 就能把长剧域逐字保持率拉到与 fact_ledger_v1 同水平，则账本的排序/相关度机制无独立贡献；否则账本贡献成立。
- 机制 DNA：父本 v0_minimal_events 原样 + 两处 delta：M4 容量（MAX_EVENT_HISTORY_CU 1400→3600）+ 确定性摘取式 recap（首个 R1–R4 信号 clause 逐字一行，≤400 CU，零模型调用）。
- 父本：v0_minimal_events（mechanism.py 全文复制，身份闭包共享依赖不变；delta 位置见文件头注释）。
- 故障族：FK-LR2-01 对照（非修复候选）；设计文档 W12_FACT_LEDGER_DESIGN_20261101.md §3。
