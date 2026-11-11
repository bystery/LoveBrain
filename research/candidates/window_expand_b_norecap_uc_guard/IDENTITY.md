# IDENTITY.md — window_expand_b_norecap_uc_guard

- 本代假设（W19-C1 单变量合并）：双域冠军合并——uc（>24 域冠军）的未定约束段 + guard（≤24 域冠军）
  的输出契约守卫应可叠加：守卫只在模型返回后对 reply 做确定性修复+审计，不触碰任何 prompt 构造，
  故与 uc 的未定段正交；预期契约违规率回到 guard 水平、未定 severe 收益与 uc 持平。
- 机制 DNA：父本 window_expand_b_norecap_uc 逐字复制 + mechanism/contract_guard.py
  （_vendor/output_repair.py 与 _vendor/normalize_reply_contract.py 与 guard 候选逐字节相同；
  风格键模糊映射 / directions 截补 / action↔response / 空回复审计）。
- 唯一变量：输出契约守卫（gw.generate 返回后运行 repair_and_audit，审计落 sandbox
  contract_guard_audit.jsonl，notes 增 guard_changed/guard_rules）。
- 父本：window_expand_b_norecap_uc（>24 轮域冠军）；对照：window_expand_b_norecap_guard（≤24 轮域冠军）。
- 不变量：prompt 构造、3600 CU 历史预算、未定段规则、intent/note 装配、事件存储与 uc 逐字一致；
  不加 suppression、不恢复 recap；零模型调用。
