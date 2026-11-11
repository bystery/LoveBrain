# DATASET_REUSE_AUDIT.md — 旧题复用预算审计（Benchmark V1 Phase 1 / 工作包二）

- 审计问题：**"160 条旧题复用预算"是否可信？**
- 审计日期：2026-11-07。
- 方法：`tools/benchmark_v1/inventory.py` 实测（纯标准库、零模型调用、只读），逐篇资格判定落在 `DATASET_INVENTORY.csv` 的 `eligible_for_dev/eligible_for_selection/eligible_for_reuse/reuse_block_reason` 列；本文数字与该 CSV 完全一致。
- 边界纪律：`benchmark/b_line/` 只读 manifest 元数据，episode 正文零读取。

---

## 1. 直接回答

**不可信。实测可复用旧题 = 45 条，不是 160 条；若复用预算按 160 条计，缺口 = 115 条。**

**最接近"160"的账面数是 161 —— 恰好是必须保持封存的 SEALED 池**（保管账 lifecycle=SEALED 的库内篇数）。把封存池当成复用池是方向性错误：这些篇的存在意义就是留给盲测/独立验证，一旦消费即失去"未见"资格。

## 2. 资格口径（指导书规则，逐条可复核）

| 规则 | 实现（CSV 列） |
|---|---|
| eligible_for_dev / eligible_for_selection | exposure_status=EXPOSED（已在保管账登记消费，可自由使用）且 b_line_related=no（本体不在 B 线、谱系组内无 B 线成员）且无阻断级勘误 |
| eligible_for_holdout | **一律 false**——holdout 必须是新出题，旧题一律不合格（避免"拿开发过的题冒充 holdout"的自欺） |
| eligible_for_reuse | dev 可用 且 gold_available≠none |
| reuse_block_reason | 未暴露(SEALED) / B线相关(self/lineage) / 阻断勘误 / gold=none，多因叠加以"+"连接 |

## 3. 从 208 到 45 的逐步剔除（全部脚本实测）

| 步骤 | 剔除数 | 剩余 | 明细 |
|---|---|---|---|
| 库总量 | — | 208 | DATASET_INVENTORY.csv 208 行 |
| − SEALED 未暴露 | 161 | 47 | 保管账 lifecycle=SEALED；这批是盲测/独立验证储备池，**不得复用** |
| − B 线相关（均在 SEALED 内，不另减） | （32，与上行重叠） | 47 | self 24（E045/046/048/049/052/053/055/056/058/059、E069/070/071/073/074/075、LRE119/123/139/142、E150/152/153/155）+ lineage 8（E105/E072/E084/E090 为 CF 孪生伙伴、E119/E123/E139/E142 为长剧母本） |
| − 阻断级勘误（EXPOSED 中） | 2 | 45 | E062（F06 品类错误，stories v2 下一版才修）、LRE117（T04/T05 GT 瑕疵待修）；E029 亦为阻断勘误但本身 SEALED，已在第一步被剔 |
| − gold=none | 0 | **45** | 全库 208 篇无 gold=none（最差为 partial） |
| **= eligible_for_reuse** | | **45** | 其中 gold full 29 / partial 16 |

资格判定自检：EXPOSED 47 = 可复用 45 + 勘误阻断 2（E062、LRE117，二者的 b_line_related 均为 no）；B 线相关 32 条全部位于 SEALED 池（与"封存时从未暴露池重选"的 B 线纪律一致）。

## 4. 45 条可复用旧题清单（按专题）

| 专题 | 条数 | ID |
|---|---|---|
| T01 | 18 | E021, E026, E033, E117, E122, E125, E131, E157, E164, E170, E183, E189, E190, E195, E199, E207, E208, E212 |
| T02 | 3 | E032, E039, E042 |
| T03 | 0 | —（唯一 T03 篇 E059 为 B 线 self） |
| T04 | 4 | E024, E027, E031, E140 |
| T05 | 2 | E037, E041 |
| T06 | 0 | —（库内 4 篇全 SEALED） |
| T07 | 1 | E061 |
| T08 | 3 | E034, E166, E191 |
| T09 | 14 | E063, E064, E065, E066, E067, E068, LRE121, LRE125, LRE127, LRE138, LRE141, LRE145, LRE146, LRE147 |
| T10 | 0 | —（CF 8 篇全 SEALED，正确地未烧） |
| **合计** | **45** | 16 篇为 gold partial（E024/E026/E027/E031/E032/E034/E039/E041/E042/E061/E063-E068），使用时须按 GOLD_SCHEMA"无 gold 不冒通过"口径折价 |

## 5. 三种可能口径下的实测数（供对账）

| 口径 | 实测数 | 说明 |
|---|---|---|
| "旧题"=全部 208 | 208 | 无资格过滤的纸面数，不能当复用预算 |
| "旧题"=SEALED 池 | 161 | **方向颠倒**：这是必须封存的池，最接近所谓"160 条" |
| EXPOSED（已消费可自由用） | 47 | 未剔勘误 |
| **eligible_for_reuse（本审计口径）** | **45** | 剔除阻断勘误后 |
| 其中 gold full | 29 | partial 16 篇带断言轮占比 ≤2/3，评分覆盖打折 |

## 6. 即便是这 45 条，也有两条使用限制

1. **对"新候选开发/回归"可用，对"已参战 harness 的独立泛化"不可用。** EXPOSED 意味着这些篇已被 fs1/p08/SLIM/longprobe/W13-03/W14-W19B 等批次消费过（保管账 consumed_by 可逐篇追溯）；凡参与过这些批次的 harness 已见过题面。全库 221 篇 `independent_review=false`，当前唯一 blind 面是 B 线 24 篇（`B_LINE_MANIFEST.json` only_once 条款）——旧题不构成独立泛化证据。
2. **partial gold 的 16 篇**（stage2 大部 + stage4 除 E062 外）带断言轮占比不足 2/3（stage4 尤甚：E063-E068 仅 12-13/24 轮带断言），层 1 程序分覆盖有限，语义判分更多依赖 judge/B3——复用时应按专题评分依据强弱折价（见 DATASET_COMPOSITION_ACTUAL.md §3 第 5 数）。

## 7. 结论

- "160 条旧题复用预算"**不能成立**：可复用旧题实测 **45 条**（29 full + 16 partial），缺口 **115 条**（对 160 而言）；对"十大专题均配 24"的规划口径，可复用旧题缺口更大（详见 DATASET_COMPOSITION_ACTUAL.md §5——T03/T06/T10 的可复用数为 0）。
- 任何把 SEALED 池（161 条，数字上与"160"最接近）计入复用预算的说法，都会吃掉盲测/独立验证储备，属于口径错误而非保守估计。
- 补足方向（供决策，不在本工作包执行）：按缺口表新增出题（T03 深聊/价值观、T07 事实/归属、T02/T04/T05 各 +18 量级）新题入账即 SEALED，随后按同一 eligibility 规则逐批消费转 EXPOSED，才能把复用预算做实。

---

*数据来源：DATASET_INVENTORY.csv（208 行，tools/benchmark_v1/inventory.py 实测）；保管账 STORY_CUSTODY_LEDGER.json（registered=210，EXPOSED 49=库内 47+库外 LRE048/LRE060）；GOLD sidecar per_episode 统计；B_LINE_MANIFEST.json 元数据。*
