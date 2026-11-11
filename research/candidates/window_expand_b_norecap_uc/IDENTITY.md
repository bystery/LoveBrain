# IDENTITY.md — window_expand_b_norecap_uc

- 本代假设（W16-09/W16-10）：无据确定化不是"事实没存住"，而是系统缺"已声明但未核实"这一档。把未定事项
  提升为显式约束注入（≤120 CU、逐字引用、禁止替对方/第三方补全），可在不牺牲互动的条件下降低该类 severe。
- 机制 DNA：父本 window_expand_b_norecap 原样 + 单变量增量——历史段后的「未定/待确认事项」块
  （`mechanism/uncertain_rules.py` 纯函数检测：hedge 或 未确认问句 + 实体锚点/第三方主体 + 未被后续确认关闭）。
- 父本：window_expand_b_norecap（2026-11-02 起 >24 轮域冠军）。
- 不变量：无未定项时不输出该段，prompt 与父本逐字节相同；零模型调用；回复仍不写入 events。
- 设计：W16_UNCERTAIN_DESIGN_20261102.md §3（含实现前修订 R1：本轮问句纳入扫描）。
- 对照：G1 severe 亲读 cand<par（主门）｜G2 interact/emotion/initiative 各 ≥−0.2｜G3 prompt ≤+5%｜
  G4 触发面 (10%,90%]。
