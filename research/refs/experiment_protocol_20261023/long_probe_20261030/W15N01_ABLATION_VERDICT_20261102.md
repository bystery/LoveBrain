# W15N-01 消融判决：window_expand_b_norecap（2026-11-02，判据=W15_NORECAP_ABLATION_DESIGN 冻结）

批：BATCH-W15N-NORECAP-20261102（AUTH-W15N-20261102-01，160/160 零失败，--workers 并行派发）
评审：AUTH-W15N_{A,B}-REV×2 并行（80 位，swap 3/8）｜ keep：`WINDOW_KEEP_W15N.json`

## 门逐项（冻结判据）

| 门 | 结果 | 判 |
|---|---|---|
| T25-32 keep ≥0.95×父本 | 双臂 1.000/1.000（LRE117+LRE146） | **PASS** |
| 九维最差维 ≥ −0.3 | **9/9 维全正**（memory +0.253、fact +0.241、goal/boundary +0.203、最差 init/emotion +0.038） | **PASS** |
| severe 总账不升（亲读） | 1 ≤ 3（亲读全部 12 位：cand 成立位=LRE117_T22 坐实"转账已收"+捏造 HER 本人转账；par 成立 3 位=LRE117_T04 擅自改价、LRE117_T34 替小满确认到账、LRE146_T38 虚增一万；另 2 降级、6 不成立；judge 原始 1 vs 12，见 `LP_W15N_B3_READRESULT.json`） | **PASS** |
| 成本 | prompt 303,664 vs 304,550（−0.3%，recap 本就近死） | PASS |

## 判决：**KEEP → 冠军简化**

>24 轮域冠军由 window_expand_b 更替为 **window_expand_b_norecap**（删 delta ② recap 段，
保留 3600 扩窗；少一段装配逻辑、少一份 ledger_rules 依赖）。

依据三点：
1. 离线证据：LD1+本批共 6 篇长剧实测 recap 仅在 T34+ 出线且 ≤199 CU（T25-32 判决段
   全程为空——即消融在该段的输入与父本逐字节相同）；
2. 本批头对头：九维 9/9 全正（小幅但方向一致——recap 段对模型是轻微噪声源而非资产）；
3. severe 亲读账无退化。

诚实注记：
- 九维差值幅度小（+0.04~+0.25），80 位不宣称统计显著；方向与离线证据一致即可支撑简化
  （简化主张是"无损可删"，不是"recap 有害"）；
- cand 唯一成立 severe（T22 坐实转账）与父本 T04/T34/T38 的坐实/虚增同族——**无据确定化
  是冠军链共性风险**（与 b2 淘汰、W13-04 b 位三案同族），W16 机制假设（对已声明判断做
  uncertain 标记注入）独立于本消融继续成立；亲读还显示两臂方向不同：par 替 HER 确认第三方/
  金额状态，cand 把未定项补成 HER 自己的行为——更支持"缺少未定标记"而非"事实存储不足"；
- swap 一致率 3/8 偏低（judge 近平位不稳定），已按 main 顺序口径报告。

## 格局更新（2026-11-02）

- >24 轮域冠军：**window_expand_b_norecap**（原 window_expand_b 保留为历史版本）
- ≤24 轮域冠军：action_policy_r9_slim（待"≤24 轮已见域确认批"评估是否合并为 b_norecap 单冠军）
