# IDENTITY.md — window_expand_b_norecap_uc_supp
- 本代假设（W18）：uc（W16 KEEP）只压"未定不得坐实"，S 型"已作废旧说法被复活"仍缺约束；
  在 uc 上叠加 b2 形状的 S 压制段，用 W18 渲染护栏（R-W18-1..4）压住 LRE117_T14/LRE121_T28
  一类 severe，同时不把"变回一个人/反手连练两周/六人满编"等非口径句当依据。
- 机制 DNA：父本 `window_expand_b_norecap_uc` 逐字复制 + 唯一变异 = 组合两段：
  S 段（`mechanism/suppression_rules.py`：ledger_rules 的 E1–E4 副本 + R-W18-1 口径锚点门、
  R-W18-2 同锚点取最新、R-W18-3 真实 turn、R-W18-4 ≤240CU 保新丢旧）；
  U 段（`uncertain_rules.py` 逐字不改，≤120CU）；跨段去重保留 S、丢 U，总预算 ≤360CU。
- 父本：window_expand_b_norecap_uc（>24 轮域冠军，W16 KEEP）。
- 不变量：无 S 条目时 prompt 与 uc 父本逐字节相同；零模型调用；回复仍不写入 events；
  snapshot/restore 与 uc 逐字节同构（不新增字段）。
- 非 b2 原样：T 标注/去重/门控均为 W18 修订，W14 的 b2 读数不可直接迁移。
- 设计：W18_UNC_SUPPRESS_DESIGN_20261102.md §2（判据跑前冻结）。
- 对照：G1 severe 亲读 cand<par｜G2 interact/emotion/initiative ≥−0.2｜G3 prompt ≤+8%｜
  G4 触发面 (10%,90%]；LRE146 为 S=0 惰性对照。
