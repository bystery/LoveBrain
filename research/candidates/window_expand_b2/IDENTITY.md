# IDENTITY.md — window_expand_b2

- 本代假设（H_SUP_main）：在扩窗装配上增加"被取代口径主动压制段"——运行时对全部历史做确定性更正检测（复用 ledger_rules supersede 纯函数，零模型调用），把检测到的"旧口径已作废→现行"对以 ≤240 CU 段注入在画像之后、历史之前，能显著降低"复活已作废口径/坐实未定"类 severe，同时 ≤24 轮域九维最差维 ≥−0.3、severe 不升、prompt ≤ 父本+8%。
- 机制 DNA：父本 window_expand_b 原样 + 一处 delta（M2 检索注入）：口径基线压制段（规则化更正检测：superseded 对 / cancelled 对 / 值名取消 / 回归现行 四类行；含未定线索的 clause 不入段）。
- 父本：window_expand_b（LD1 KEEP，>24 轮域冠军；mechanism.py 逐字复制，delta 位置见文件头注释）。
- 故障素材：LD1 cand LRE117_T14（复活已作废"五十五"+坐实未定"八点"）+ CF1 E152（外借-归还框架弱化）。
- 设计文档：W14_SUPPRESSION_DESIGN_20261102.md（判据跑前冻结，含 §2.2 实现修订记录）。
