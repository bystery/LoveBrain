# W14M-01 回归批判决（E061：window_expand_b2 vs r9_slim，2026-11-02）

批：BATCH-W14R-E061-20261102 ｜ AUTH-W14R-REG-20261102-01（48/48 完成，0 失败，
order_verified=true）｜ 评审 AUTH-W14R-REVIEW-20261102-02（24 main + 4 双序补判，
重试后 0 CALL_FAIL）｜ 设计判据：W14_SUPPRESSION_DESIGN_20261102.md §3.1

## 门判决（三项全过 → 回归 PASS）

| 门 | 结果 | 证据 |
|---|---|---|
| 九维最差维 ≥ −0.3 | **PASS**：最差 = init −0.042；7/9 维正向（boundary +0.292、time +0.227、goal +0.208…），emotion 0.0 | LP_W14R_NINEDIM_REVIEW.json |
| severe(b2) ≤ severe(parent)（亲读账） | **PASS**：1 ≤ 1。raw 2/1 → 亲读修正：T16 cand 成立（虚构"8月6号改约"需求）、T20 parent 成立（日期/事项混搅）、T24 cand 拒绝（四变体无违例，judge 无 note 且双序不一致） | LP_W14R_B3_READRESULT.json |
| prompt ≤ r9_slim | **PASS**：69,912 vs 145,998 字符（−52%） | batch manifest |

## 对拍存档（设计 §3.1 附加项）

b2 vs window_expand_b（W12-02 同故事跑）：**24/24 轮 system+user prompt 逐字节一致**；
压制段全程 supp_pairs=0（E061 无规则可测更正对，符合"≤24 轮短剧多数轮为空段、
成本近零"的设计预期）。prompt 总成本与 b 基线完全同值（69,912）。

## 诚实记录

- 程序层：b2 8 个失分轮 vs r9_slim 0 vs b 基线 2。失败族与 b 已知族相同：
  ① json_schema_broken（directions 数组含 null 元素，dsf temp0.7 采样噪声；b 基线同
  故事有 1 例）×5；② 来源引用格式打滑（"1"/"m19"/"m17"，m_src[i] 才是历史合法格式；
  b 基线 T21 有 m_src[20],m_src[19] 引用，FK-SCORE-01 修复后属合法）×3。
  输入逐字节相同 → 非机制引起；频次波动归采样方差，判决以语义+severe 亲读账为准。
  已登记为观察项：若判决批再现高比例 schema 破坏，升级为可靠性问题单独立账。

## 结论

**W14M-01 回归 PASS** → 按 §3.2 派发判决批（LRE121/LRE127/LRE145/LRE146，
288 attempts，b2 vs window_expand_b 冠军对冠军）。
