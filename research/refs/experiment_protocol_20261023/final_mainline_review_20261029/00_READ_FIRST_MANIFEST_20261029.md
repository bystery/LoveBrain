# 00 · 现场整理索引（先读我）— final_mainline_review_20261029

> 用途：给下一个窗口的**唯一正门**。本目录 103 文件 + `ENTRY_BEFORE/`，混着两代产物、三套版本线、
> 若干重复件与一堆证据数据。本索引逐文件说明"是什么 / 现在还有用吗 / 新还是旧 / 是不是污染 / 怎么处理"。
> 本文件是**新增导读件**，不改动任何原始答卷、判词、冻结身份、授权账本。生成 2026-10-29，纯整理、零 API。

## A. 30 秒定位：这个目录是什么

LoveBrain 聊天 Harness「固定基座重构 + 开发集对照实验」的**终审现场**。判决 = **REWORK**（执行完成、验收未完成）。
两条时间线叠在一个目录里：
- **第一代（00:25–00:39）**：对继承来的 260 份历史答卷做独立复核、给出 F0–F6 执行菜单。**是"地基/证据来源"，不是当前判决**。
- **第二代（09:35–18:23）**：真正跑 F0–F6 + B/C/D 收口 + 我的版本追加（REV2/REV3/判定/成本）。**这才是当前工作**。

**真正的现场跨两个当天目录**（别只看本目录）：
- `final_mainline_review_20261029/`（本目录）＝ 终审判决 + `STATE_AND_ERRATA.json` 总账 + B/C/D 收口。
- `../submission_review_20261029_1510/`（同级）＝ **实际提交包**（`SUBMISSION_REPORT.md`、两个 submission zip、
  `WORKER_CORE_CLOSEOUT_NEXT.md`〔B/C/D 的**执行依据**〕、`SEVERE_ERROR_SOURCE_REVIEW.json`〔C 重判的原始判词源〕、
  `E3_INDEPENDENT_REPLAY.json`〔E-F6-E 引用〕、打包/复算脚本）。
- `../onsite_check_20261029_0846/`＝ 当天早上的**进程/弹窗运维快照**（与质量结论无关，属噪声，见 §E）。

## B. 当前该读什么（正门读序，5 个文件就够接手）

1. **本索引**（`00_READ_FIRST_MANIFEST_20261029.md`）。
2. `F6_FINAL_VERDICT_20261029_REV2.md` — **判决与口径的当前版**（初版 `F6_FINAL_VERDICT_20261029.md` 已被它纠正两处事实错误+两处夸大，只作历史）。
3. `F_BCD_CLOSEOUT_ADDENDUM_20261029_REV3.md` — **B/C/D 收口的当前版**（REV1 对 C 说"未开工"、REV2 对 C 偏乐观，均被 REV3 更新）。
4. `F_C_CONTRAST2_GRADES_20261029.md` — **C 分层假设的最终结论**（第 2 独立抽样未复现 → NOT-CONFIRMED；这是最新、最重要的一次结果）。
5. `STATE_AND_ERRATA.json` — 唯一总账，但**对 C 与 cost 已过时**（见 §D 与文件内 `status_now` 指针），读时以 2/3/4 为准。

判决一句话：r4(e3/guard 系) 唯一质量证据＝F3 新材料 72 对 38 胜/10 负/24 平；B 跨窗检索 on 11/12 vs off 0/12（真增益）；
C 的 assertion_tiering 经两抽样**未被证实**（第 1 次是运气）；D 人工愿发**未回收，W=未知**；总体 **REWORK**。

## C. 版本线：谁新谁旧（同名 REV2 有两套，极易混！）

存在**两组彼此独立的 REV1→REV2(→REV3)**，命名都带 `_REV2`，不是同一东西：
- 判决线：`F6_FINAL_VERDICT_20261029.md` → **`F6_FINAL_VERDICT_20261029_REV2.md`**（当前）。
- 收口线：`F_BCD_CLOSEOUT_ADDENDUM_20261029.md`(=REV1) → `…_REV2.md` → **`…_REV3.md`**（当前，只更 C）。
处理：旧版**全部保留作历史**（内含"不回改原件"声明、且被新版逐条"撤销：<原话>—原因"引用）；新版只在其内指回旧版对应段，不改写旧版正文。判"当前"以 §B 读序为准，**不靠文件名"REV 号大就新"**（本仓一贯：禁按版本号称冠军）。

## D. `STATE_AND_ERRATA.json` 的过时点（已做"最小更新"的方式）

它是**总账**、不是判词/答卷/冻结件，允许最小可逆更新。过时项：
- `phases.C_major_reclass_hypothesis.status="analysis_done_dispatch_not_registered"` 与 `next_actions#1`、
  `F_BCD…REV1 §C` 末——都说 C"未开工/未派发"。**已被后续真实执行超越**：17:20 建身份 r5、17:30 跑 C1 批 24/24 关单；
  18:05 又跑 C2 复测批（fail_fast 于第 19 格）。
- `cost_index.totals` = **530 started / 521 completed**（含 B、不含 C）。含 C1 = 554/545；含 C1+C2 = **573/564/6**。
处理（最小、可逆、不重写历史）：
1. 在 STATE **顶部新增一个 `status_now` 指针块**（仅追加、指向 §B 当前链 + 列出上面过时字段），历史字段原样保留（原件已备份）。
2. 已给易被误当"最新"的旧文档**各加一行顶部横幅**（纯追加、正文不动、原件已备份）：`F6_FINAL_VERDICT_20261029.md`→指 REV2；`F_BCD…_ADDENDUM.md`(REV1) 与 `…_REV2.md`→C 段指 REV3；`WORKER_FINAL_PACKAGE_20261029.md`→指当前判决；`F_D_HUMAN_PAIRS_WORKSHEET.md`→标"已派发未回收、W=未知"。
3. 另有两个独立登记件已存在、不冲突：`STATE_ERRATA_PATCH_C_20261029.json`（更 C）、`C_CONTRAST*_COST*.json`（更 cost）。

## E. 每个文件是什么（按功能族；标 ✅当前 / 📜历史证据 / 🔁重复 / ⚠️噪声）

### E1 判决/收口文档（人类读）
- `F6_FINAL_VERDICT_20261029_REV2.md` ✅ 当前判决口径 | `F6_FINAL_VERDICT_20261029.md` 📜 被 REV2 取代的初版
- `F_BCD_CLOSEOUT_ADDENDUM_20261029_REV3.md` ✅ 当前收口 | `_REV2.md` 📜 仅 C 被 REV3 更新 | `.md`(REV1) 📜 C 段过时
- `F_C_CONTRAST_GRADES_20261029.md`(C1 判读) 📜 结论被 C2/REV3 部分撤回 | `F_C_CONTRAST2_GRADES_20261029.md` ✅ C 最终结论
- `F_B_KNOWLEDGE_REPORT.md` ✅ B 报告(未变) | `F_C_MAJOR_RECLASS_20261029.md` 📜 C 重判(其候选有利方向已被 C2 复测削弱)
- `F_C_HYPOTHESIS_FREEZE_20261029.md` 📜 假设冻结件 | `REFACTOR_NOTES_20261029.md` 📜 工程审计 | `F1/F3/F4/F5_REPORT.md` 📜 各阶段报告
- `HOST_MEMORY_STOP_20261029.md` 📜 主机内存事故账本(E-F3-B) | `INDEPENDENT_REVIEW.md`(本目录内) 📜 一代继承复核

### E2 总账/判词/授权（不可回改，只可指针对）
- `STATE_AND_ERRATA.json` ✅总账(带 status_now) | `STATE_ERRATA_PATCH_C_20261029.json` ✅C 更正登记
- `JUDGMENT_ERRATA_20261029.json` 📜 判词撤回(D01/D02_T08、FIX-9) | `BINDING_GUARD_INDEPENDENT.json`/`OUTPUT_REPAIR_PARITY_INDEPENDENT.json` 📜 继承护栏
- 授权件（全部 `authorization_registration`，均已关单/记账）：`F1/F3/F3_RESTART/F3_JUDGE/F4/F4_KNOWFIX/F4_JUDGE/F5_RECHECK/F5_JUDGE/F5_JUDGE_SWAP/KB_PROBE/C_CONTRAST/C_CONTRAST2_*AUTHORIZATION*`；✅当前只有 `C_CONTRAST2_AUTHORIZATION`(最新，已关)；其余📜各自批次证据。
- 成本：`F6_COST_INDEX.json` 📜 F0–F5(506/497) | `C_CONTRAST_COST_20261029.json` ✅C1(170,766) | `C_CONTRAST2_COST_20261029.json` ✅C2(132,830，含 fail_fast 处置)

### E3 身份冻结（三身份各一份，均现行有效、非重复）
- `FINAL_CANDIDATE_FREEZE_20261029.json`＝guard_final `9b8f4e…` | `F5_CANDIDATE_FREEZE_20261029.json`＝guard_e3 `17fd2160…` | `F_R5_CANDIDATE_FREEZE_20261029.json`＝r5_tier `ceb8008f…`(delta_from_e3=只加分层段)。`F2_FREEZE_RECORD_20261029.json`📜选 r2 为基、退役 r3 的记录。
- 三身份本窗口均 `check_identity_drift --flat`（final 用非 --flat）复算 identical=true。

### E4 证据数据（大、只读、别当"文档"通读；按需查）
- 历史行动账：`ACTION_OUTCOMES_130.jsonl`(130 行) + `_SUMMARY` ✅F0 | `F3_ACTION_OUTCOMES_72.jsonl`(72)+`_SUMMARY` ✅F3
- 题料/绑定：`F1_MATERIALS.jsonl`(158) `F3_MATERIALS.jsonl`(72) `F4_MATERIALS.jsonl`(12) `F5_MATERIALS.jsonl`(12) 与 `*_PRIVATE_BINDINGS.json`
- 评分/分析：`F1/F3/F4/F5_ANALYSIS.json` `F1_CONTROLS_ANALYSIS.json`(⚠️=F1_ANALYSIS 的控制题子集，非新结论) `F6_TABLES.json` `F3_KNOWLEDGE_CHAIN.jsonl`(18)+`_SUMMARY`
- B/C 专用：`F_B_PRECHECK.json`(零调用前置 8 探针) `F_B_PROBE_REGISTRATION.json` `F_B_KNOWLEDGE_RESULT.json`；`F_C_CONTRAST_REGISTRATION.json`(C1) 与 `F_C_CONTRAST2_REGISTRATION.json`(C2) — **两者字节相同**（选样确定性，故意的，别当误复制）
- 复测/回放：`F5_MATERIALS_SWAP.jsonl` `F5_PRIVATE_BINDINGS_SWAP.json`(A/B 换序复测，非重复) `F5_REPLAY.json` `F5_REPAIR_REPLAY.json`

### E5 一代继承产物（00:25–00:39，STATE 的"inherited_and_verified"来源）
- `INDEPENDENT_RESULTS.json` ✅继承结论(7 批 VERIFIED_ALL) | 7×`{S1,S2,S2R,S3,D1,R3A,R3B}_INDEPENDENT_AGGREGATE.{json,md}` 📜各批成绩
- `WORKER_FINAL_PACKAGE_20261029.md`/`FINAL_EXECUTION_PLAN.json`/`NEW_WINDOW_OPENING_PROMPT.md`/`CORE_ACTION_FROZEN_EVIDENCE.json`/`AUDITED_SOURCE_HASHES.json` 📜开工地基与菜单(已被实际执行超越)
- `ENTRY_BEFORE/`📜进门前快照(AGENTS.md 手册 29KB + WORKER_CURRENT_RUNBOOK.json + WORKER_NEW_WINDOW_START.md)

### E6 工具/代码（非文档）
- `independent_delta.py`(零调用增量复核算法) `PRE_F5_OUTPUT_REPAIR.py`(可交付性修复测算，只读) — 留作可复现工具。

### E7 重复/打包/噪声（可安全"标记忽略"，不删）
- 🔁`LOVEBRAIN_FINAL_DELIVERY_20261029.zip` + `.sha256` + `BUNDLE_MANIFEST.json`：打包的是 9 个一代文件，**与本目录松散同名件内容重复**；BUNDLE_MANIFEST 就是它的清单。读松散原件即可，zip 当交付快照留档。
- ⚠️`onsite_check_20261029_0846/`(同级)：进程/弹窗/可见窗口快照，与质量判决无关。

## F. 全仓噪声与"判权威"的硬规则（本目录之外，别去动它们、但要会绕开）

- 顶层 **130 个 `*_acl_locked_*` 整目录副本**（20261021 历史快照，非本轮产物），恰好 **5 个前缀族 × 各 26 份**：
  `arena_acl_locked_*`、`benchmark_anchors_acl_locked_*`、`refs_acl_locked_*`、`benchmark_episodes_acl_locked_*`、`benchmark_cases_acl_locked_*`。
  **一律不读、不判、不删**，除非确要做 ACL 取证。
- `arena/isol_runs`、`arena/dist` 现为空；`_archive_v1/`(10) `backups/`(6) 为归档。
- `refs/` 根与 `refs/experiment_protocol_20261023/` 各有几十个**跨代同名** `*_INDEPENDENT_*` / `AUTHORIZATION` / `CHECKS`。
- **判权威唯一办法：按"批次目录 + 内容哈希"，绝不按文件名。** 本轮权威＝`final_mainline_review_20261029/` + `submission_review_20261029_1510/`；跨批同名 `AUTH-F5-JUDGE-…` 等在多个目录都有，以本目录内、且被 `C_CONTRAST*`/`STATE` 哈希绑定的那份为准。

## G. 污染处置总原则（回应"怎么处理/怎么最小更新"）

1. **不永久删任何文件**（本仓非 git；删除走回收站，且仅限我本轮自造的 scratch）。历史版本、重复件、一代继承产物**一律就地保留**——删了会断"撤销:<原话>"的可追溯链与哈希绑定。
2. **"旧版本"不重写，只加指针**：STATE 加 `status_now`（§D）；REV1/REV2 的过时判断由 REV2/REV3 的"撤销/修正"段就地引用纠正，不改旧正文。
3. **重复件不合并**，只在索引里标"🔁=X 的副本/字节相同/故意的复测确定性"，避免下一个窗口误以为被篡改或重复烧钱。
4. **命名冲突不重命名原件**（重命名会破坏 STATE/REV 里的文件名互引与 sha）；靠 §C 的双线说明 + 本索引消歧。
5. 全仓 acl_locked/isol_runs/dist 属既有噪声，本轮**只在 §F 给绕行规则，不清扫**（超出现场、且风险高）。

## H. 待办交接（下一个窗口只剩这些，且多为"需要人"）

- **D 人工愿发**：`F_D_HUMAN_PAIRS_WORKSHEET.md` 12 对**全未标注**（勾选皆空）→ W=未知，只有用户本人能标，AI 不代投。绑定见 `F_D_HUMAN_PAIRS_BINDING.json`（seed `kbD-20261029`，勿据其反推给用户体验）。
- **C 无据确定化的真修复**：分层 prompt 已证不靠；要转向**输出前确定性事实/状态校验**（新身份+新授权，属新工作，非本现场范围）。
- 已登记未生效、不得擅自生效：`E-MAT-B`(K02 素材日期冲突)、`RULER-C-01`(卡面把未敲定减两千当已生效)。改尺子=离线重评+版本化，不为改尺子重跑生成。
- 禁止：重跑 F0–F6、为补身份全量重抽、就地改冻结候选、复活已关账本、接入/美化排在实测前。

## I. 数字溯源（2026-10-29 收尾：本索引实质数字逐条核过，防旧 md 断言冒充"已核实"）

| 结论/数字 | 值 | 本轮核对方式 | 状态 |
|---|---|---|---|
| F3 胜负平 | 候选 **38** / 原版 **10** / 平 **24** | 重算 `F3_ANALYSIS.json.rows_detail` 的 `winner_arm`（n=72） | ✅本轮复现 |
| F3 schema | 72/72 | `F3_ANALYSIS.schema_ok` | ✅ |
| 九维配对增益 | 9 维 delta **全为正** | `F6_TABLES.nine_dim_new_material_72.dims.*.delta` | ✅ |
| 新 72 交付可提取 | 候选 replied71+undeliverable1；原版 72 | `F6_TABLES.delivery.new_72_pairs`（＝无法提取 原版0/候选1） | ✅ |
| B Use | on **11/12** vs off **0/12** | `F_B_KNOWLEDGE_RESULT.hand_grading` | ✅ |
| B Update/Attr/污染 | 3/4、4/4、1 格 | 同上 | ✅ |
| B token | on 83,869 / off 79,686 | `F_B_KNOWLEDGE_RESULT.resource.by_arm` | ✅ |
| C 分层结论 | **NOT-CONFIRMED** | 本窗口逐格读 C2 `reply_raw`（K01_T19/T20 tier 复现无据断言） | ✅第一手 |
| 计数 | 573 started / 564 completed / failed 6 / refused 9 | 各 `logs/auth_budget/*.jsonl` 事件复算 | ✅ |
| token 全口径 | 2,699,282（各桶相加，作废批不进） | 1078663+1153468+163555+170766+132830 | ✅ |
| 三身份 | e3/r5/guard_final 均 identical=true | `tools/check_identity_drift.py` | ✅ |
| 合同 veto 原版2/候选1（K02_T04、N02_T08 / N01_T05） | 2/1 | **未从 144 份 scoring.json 重算**，取自 E-F6-B 判词 | ⚠️仅文档 |
| major 8 条/5 位点、原版 5/5 | 8/5/5 | 取自 `F_C_MAJOR_RECLASS`（重判文档） | ⚠️仅文档 |
| F3 同批 token −10.10% / 中位 +17.35% / p95 +28.22% | — | 取自 STATE `secondary_metrics`，未从 calls 重算 | ⚠️仅文档 |

> 溯源原则：✅ 行可直接引用为已核实；⚠️ 行须回原始件重算后才算结论。本轮**未发现 ✅ 行与原始件冲突**（唯一一次"对不上"是我最初误取 `verdict` 盲评字母而非 `winner_arm`，已修正）。这正是"清洁"的一部分。
