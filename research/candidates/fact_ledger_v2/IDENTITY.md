# IDENTITY.md — fact_ledger_v2

- 本代假设：fact_ledger_v1 回归门 FAIL 的 cand 独有 severe（E061 T09）主因是 **H-主观句错账**——HER 的推测/抱怨/情绪/意愿句被 capture 入账并以账本权威口吻回注，诱导模型把未知项断言成事实并替她拍板；在 capture 侧加 R0 主观句排除层、注入侧对无硬锚 HER 主观类行降权并加 ‹她说› 来源标记，可消除该错账族，同时保持 v1 三项已确认收益（窗口保持 98.8%、prompt −47.4%、init +0.333）不退化。
- 机制 DNA：父本 fact_ledger_v1（v0_minimal_events 骨架 + M1 规则化逐字账本 + M2 top-k 注入 + r7 supersede）+ R0 主观句过滤（无硬锚且命中推测/情绪/意愿语言学信号不入账，R1–R6 之前）+ 来源透明化（HER 无客观锚行 ‹她说› 前缀）+ 注入降权（w_type 0.4/0.6→0.15）。
- 父本：fact_ledger_v1（其骨架逐字继承自 v0_minimal_events；v2 只改 ledger_rules 的 R0 层与 mechanism 的打分/行格式，不重建考场）。
- 故障族：H-主观句错账（FLREG_VERDICT_20261101 失败归因 §2，T09 cand 独有 severe）；已知基座族 LR1（T13 双杀）不在本代修复范围。
