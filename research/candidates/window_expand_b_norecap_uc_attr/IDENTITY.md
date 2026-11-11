# IDENTITY.md — window_expand_b_norecap_uc_attr

- 本代假设（W22-01）：归因错位（把 HER 的制作品/承诺/决定说成自己所作）是跨批复现的冠军链共性族
  （W17c E208_T01、W19CROSS E191_T08、本批 E191_T08 双臂+LRE121_T39 倒置）。把"归属以 HER 原话为准"
  提升为显式锚定注入，可在不损互动的条件下消除/压降该族。
- 机制 DNA：父本 window_expand_b_norecap_uc 原样 + 单变量增量——uc 注入块后的**归属锚定块**
  （`mechanism/attribution_rules.py` 纯函数检测：HER 第一人称创作/承诺/决定表述 + 实体锚点，
  ≤3 条、≤120 CU、逐字引用；无触发则整段不出现，prompt 与父本逐字节相同）。
- 父本：window_expand_b_norecap_uc（2026-11-03 起 >24 轮域冠军，W16 KEEP）。
- 不变量：零模型调用；不改输出契约；snapshot/restore 沿用父本；不碰 b_line/封板题。
- 设计（判据冻结）：`refs/experiment_protocol_20261023/long_probe_20261030/W22_01_ATTR_DESIGN_20261104.md`。
- 对照：G1 severe B3 亲读（每域 cand≤par 且归因错位族触发面下降，主假设直接检验）｜G2 九维辅助｜
  G3 契约+空回复（重试后口径）｜G4 prompt≤+5% 触发轮均值+无触发轮逐字节相同+0 额外调用｜
  G5 swap 同冻结口径｜触发面 (10%,90%]。
- candidate dependency: candidates/reference_product_v1
- candidate dependency: candidates/v3_system_slim
