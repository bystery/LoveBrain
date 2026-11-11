# W17c 合并复评判决（2026-11-03）

> **Rev2（2026-11-03 13:05，独立红队复核后）**：判决维持 MERGE；修正 G1 口径（main-only）、
> 机械零分位与评分器误判。详见文末“Rev2 修正”。

## 判决：**MERGE（≤24 轮域）**

`window_expand_b_norecap_guard` 在 6 篇**未暴露** 12 轮篇上通过全部冻结门，
**≤24 轮域冠军改为 `window_expand_b_norecap_guard`**；`action_policy_r9_slim` 转历史（证据保留）。
>24 轮域冠军仍为 `window_expand_b_norecap_uc`（W16 KEEP）。两域现同属 b_norecap 家族但仍是两个构建；
“真正单构建”需 guard+uc 组合另测，本判决不宣称。

批：`BATCH-W17C-MERGE-20261103`（144/144，failed=0，order_verified=true，state_isolated_real=true）。
判据：`W17C_PREREGISTRATION_20261103.md`（跑前冻结，对齐 TASK_QUEUE W17-03 acceptance）。

## 门逐项

| 门 | 结果 | 判 |
|---|---|---|
| G1 九维（main-only n=61/72） | 最差维 **emotion +0.033**；interact +0.180 / emotion +0.033 / init +0.230 均 ≥ −0.2（Rev2 按 main 顺序重算；含 swap 的合并值 −0.015 见文末） | **PASS** |
| G2 severe（B3 亲读） | candidate **0** vs parent **3 确认**（judge 旗 4，E157_T06 降级） | **PASS** |
| G3 契约违规 | **2/72 vs 2/72**（cand ≤ parent+1） | **PASS** |
| G4 空回复 | **0 vs 0** | **PASS** |
| G5 成本（不作门） | prompt 440,613 → 210,578（**−52.2%**）；calls 72/72，0 额外 | 如实报告 |
| 诊断：T07–T12 keep | **1.034**（≥0.95×） | PASS（不改判） |

九维全表（主口径 main-only 配对差 cand−parent，n=61）：
goal +0.377｜fact +0.213｜attrib +0.164｜time +0.262｜boundary +0.148｜memory +0.393｜interact +0.180｜init +0.230｜emotion +0.033。
9/9 维为正。合并（含 swap）值 n=67：goal +0.313 … emotion −0.015；swap-only 6 位多数维 −0.33（emotion −0.50），
说明候选优势对顺序敏感——见 Rev2 与 `LP_W17C_G1_MAINONLY.json`。

成本/等待：p50 **79.7s vs 86.5s**（cand 更快）；p95 120.1s vs 117.7s；max 130.0s vs 179.7s。
机械分均值原始 9.306 vs 9.676；**修正隔离路径 m_src 误判后 9.583 vs 9.676（−0.093）**。
原始零分位 candidate 5 个（E157_T03/E170_T11 schema 写坏、E164_T04 `m2`、E183_T03/E191_T02 `m_src`），
parent 2 个 schema 零分位（E157_T09/T11）+ 2 个 8.33（E157_T12/E212_T02）。
其中两个 `m_src` 是 **FK-SCORE-01 在隔离路径复现**的评分器误判，非候选错误；`m2` 是候选真实的
source-id 格式错误。详见 Rev2。

## B3 亲读（零抽样，4 个 judge 旗位全部读）

| 位置 | 旗 | 亲读结论 |
|---|---|---|
| E157_T06 | parent | **降级不计**：'多半是怕排不下人' 带 hedge，属推测非坐实；未虚构恢复日期 |
| E164_T04 | parent | **成立**：把季卡按旧二十次卡折算成 44.95/次，季卡按次数计未确认 |
| E170_T02 | parent | **成立**：责任未定即断言'不是你的责任'，同时按客户原因条款收两百，自相矛盾 |
| E170_T05 | parent | **成立**：把仅写'六百门店出'的责任单升级为'门店全责' |

candidate 全部 0 旗；candidate 在 E170_T02/T05 均要求先定责/先看书面单；E164_T04 是算账澄清
（未替 HER 认定责任，Rev2 修正原表述）。

## 质量注记与诚实边界

1. **swap 一致率 3/6**，3 个分歧位（E170_T08、E183_T08、E191_T08）均为 main=candidate / swap=parent 或 TIE；
   candidate 的优势在这些位置对顺序敏感。G1 按预注册用 main 顺序，swap 只作质量标注。
2. **评审侧 judge 调用失败 4/72（5.6%）**（3 CALL_FAIL + 1 UNPARSEABLE），是评审质量标注；
   **live 臂级 CALL_FAIL 0/144**。10% 阈值属臂级，不套用到评审侧。九维 main-only n=61/72
   （含 swap 的总 n=67），缺位不作通过项。
3. **评审中断事故**：12:27 为保护 live 容量，E157/E164/E170 评审被中止；sidecar 断点续跑恢复，
   在途调用计入授权账，本文件只用最终完整评审。6 篇评审调用合计 89 次（含重试/swap/在途）。
4. 六篇均未出现在 W17/W17b/W18，但同属已见模板族；结论是“未暴露篇泛化”，不是新域泛化。
5. 评审为 AI 九维 + 主会话亲读，不等于用户愿发偏好。
6. 契约守卫的剩余 2 例 = JSON 写坏（缺右括号）且守卫正确地不改写；与 W17b 的已知残余同类，
   若要继续降需有界重试或更强的解析重拼，不在本批范围。
7. 本批 live 与 W18 事故并行发生（W18 因 9 路并发超时中止）；W17c 自身 0 失败，
   但 p95 已达 120s，后续 live 按“独占 6 路、评审后置”执行。

## 注册表变更

- `main_incumbent`：≤24 轮域 → `window_expand_b_norecap_guard`；>24 轮域不变（uc）。
- `action_policy_r9_slim` → 历史（证据链：W17/W17b/W17c 全保留）。
- 下一步：W18-04（W18B 全量重跑，uc_supp vs uc）→ 若 KEEP，再做 guard+uc 组合的单构建验证。

## 证据

- 计划/授权：`W17C.INTERLEAVE_PLAN.json`（sha `b42cdf2c…`）/ `AUTHORIZATION_W17C.json`（cap 144）
- 机械：`logs/w17c_merge_live_20261103/_reports/ARM_STATS_W17C.json`、`LAYERED_SCORE_*.json`、`PAIRED_DIFF.json`
- 九维：`LP_W17C_NINEDIM_REVIEW.json`（含 6 篇分文件）
- B3：`LP_W17C_B3_READRESULT.json`
- 事故：`W18_EXECUTION_INCIDENT_20261103.md`

## Rev2 修正（2026-11-03 13:05，独立红队复核后）

判决维持 MERGE，但原稿有 4 处证据错误，已逐条核验并修正：

1. **G1 口径**：`LP_W17C_NINEDIM_REVIEW.dims` 实际把 6 个 T08 的 **swap 重判**计入（order=swap），
   原稿“main 顺序”表述错误。按预注册重算 main-only（n=61）见上方表；
   最差维 **+0.033**（不是 −0.015），G1 更强通过。swap-only 6 位多数维 −0.33（emotion −0.50），
   swap 一致 3/6 → 候选优势对顺序敏感，作为稳健性注记；不改变 main 口径判决。
   证据：`LP_W17C_G1_MAINONLY.json`。
2. **机械零分位**：候选原始 5 个零分位（2 个 schema + 1 个 `m2` + 2 个 `m_src`），parent 2 个 schema
   + 2 个 8.33；原稿“3 个”错误。其中 E183_T03/E191_T02 的 `m_src` 是
   **FK-SCORE-01 在隔离路径复现**：`trusted_controller.step_one_turn` 建 id_map 时未继承
   `lb_runner` 的 `m_src[i]` 合法集（`i < prior_real_n`），属评分器误判，非候选错误。
   修正后候选 3 零（2 schema + 1 m2）vs parent 2 零 + 2×8.33；均值 **9.583 vs 9.676（−0.093）**，
   不是原稿的 9.306（−0.37）。
3. **契约口径**：schema-only 2/72 vs 2/72（G3 预注册口径，W17b 先例同）；
   修正 `m_src` 误判后的全 veto 为 candidate 3 vs parent 2，仍满足 cand ≤ parent+1（3≤3）。
   `E164_T04` 的 `m2` 是候选真实的 source-id 格式错误（非 `m_src`），登记待修。
   若不做隔离路径修复而直接按全 veto 读，会得到 5 vs 2 的错误结论——修复后再评。
4. **CALL_FAIL 与单篇 keep**：原稿把评审侧 judge 调用失败 4/72 与臂级 CALL_FAIL 混用；
   已改为“评审侧 4/72（5.6%），live 臂级 0/144”。单篇 keep E170 **0.833**（其 T11 为真实
   schema 写坏）原稿只报批级 1.034，已补披露。
5. **W18B 并行性**：E191/E212 评审文件 mtime 早于 W18B 首个 attempt（12:51:08）约 100 秒，
   W18B 起跑时评审已结束；“live 与评审零并行”成立（红队 F 项 WARN 不成立）。
6. **B3 表述**：candidate 在 E164_T04 是算账澄清，不是“先定责”；先定责表述仅适用于 E170_T02/T05。

**附条件**：在 W19-01（隔离路径 `m_src` 修复 + W17b/W17c/W18B 离线重评）完成前，
本批程序分不得当作最终质量证据；E164_T04 的 `m2` 类 source-id 格式错误需在下一版守卫处理。

## Rev3 附记（2026-11-03 W19-01 完成后）

W19-01 已完成：`trusted_controller.step_one_turn` 补 `m_src[i]` 合法集（与 runner 同口径），
单测 4/4；`tools/rescore_batch.py` 离线重评 608 位（原 scoring.json 未动）：
- W17c：候选 9.306→**9.583**（E183_T03/E191_T02 误 veto 清除、各恢复 10.0），零分 5→3；
  parent 9.676 不变。上文 Rev2 的修正均值即为本口径，**Rev2 数字有效，无需再改**。
- W17b：清除 2（E199_T07/E208_T05），cand 8.958→9.236。
- W18B：320/320 逐位不变（LRE121 9 个 json_schema 失败为 schema 族，与 m_src 正交）。
红队复核 13 个 illegal_source_id 位逐一独立重算全对（REDACT_TEAM_W19_01_CHECK_20261103.md）。
本批"附条件"解除：程序分可按修正口径使用；`E164_T04` 的 m2 类格式错误仍待 W19-02 守卫清洗。
