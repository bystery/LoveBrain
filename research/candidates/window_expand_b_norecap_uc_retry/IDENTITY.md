# IDENTITY.md — window_expand_b_norecap_uc_retry

- 本代假设（W21-02）：长域输出契约崩坏（空回复/json_schema_broken）是底模随机族，一次确定性重试
  可把有效回复率抬到 par + 预注册差值以上，且不损语义质量、不引入选择偏倚。
- 机制 DNA：父本 window_expand_b_norecap_uc **原样**（mechanism/ 与父本逐字节相同，prompt 构造零改动）
  + 单变量增量 = **runner 批级崩坏轮重试策略**（params.collapse_retry=true）：回复 raw 为空或不可解析
  时，同 prompt 逐字节重调 1 次，两次留档取首个可解析者，逐次登记授权账本；双臂同策。
- 父本：window_expand_b_norecap_uc（2026-11-03 起 >24 轮域冠军，W16 KEEP）。
- 不变量：不碰 prompt/装配/评分器；不删失败位；重试额度 ≤20% base attempts 预注册；
  重试后仍崩位逐位登记为底模族并入 G1 亲读范围。
- 设计（判据冻结）：`refs/experiment_protocol_20261023/long_probe_20261030/W21_02_NEXT_BATCH_DESIGN_20261103.md`
  （裁决①② 主会话代裁全文在案，2026-11-04）。
- 对照：G1 severe B3 亲读（有效回复位）cand ≤ par 且不新增故障族｜G2 九维辅助门（按 W20-03 资格表）｜
  G3' 有效回复率 cand ≥ par+差值｜G4 prompt 逐字节相同、额外调用=重试数 ≤cap｜G5 swap 同 W19-03 口径。
- candidate dependency: candidates/reference_product_v1
- candidate dependency: candidates/v3_system_slim
