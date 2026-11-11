# LoveBrain Arena · 训练执行会话 · R1-R5 合并交付 2026-10-23 (rev R1R5)

**批次**：`MERGED_R1R5_20261023`
**执行窗口**：本训练执行会话（不新开窗口，不重建考场）
**判决依据**：独立评审 `refs/experiment_protocol_20261023/merged_independent_review/REVIEW.md` §合并修法（R1-R5 + §7）
**真实模型调用**：0
**旧答卷 artifact 覆盖**：0（`logs/exam1_20261023/**` 全部保留；新版分层结果另存 `LAYERED_SCORE_v05_R1R2.json`）
**授权**：`AUTH-EXAM1-20261023-01` 关闭不复活；本批无新授权发出，全部离线。

## 0. 一句话交付

**5 条阻塞项（R1-R5）+ §7 边界与文案全部离线修完并附反例回归；77 checks 全绿；48-slot 交错演练 0 网络跑通；实验计划、批注绑定、身份闭包、评测分层四线同批收紧；尚无新的两臂成绩——48 次实跑清单已就绪，等待新授权与再复核。**

## 1. 交付五问（AGENTS 交付纪律）

| 项 | 回答 |
|---|---|
| 改了什么 | 见 §2 分类清单（父本 / 候选 / 评分器 / 分层工具 / 身份工具 / 计划文档 / 演练工具）|
| 为什么 | 独立复核 R1-R5 + §7 判决条目 |
| 如何验证 | 77 离线 checks + 48-slot 零网络 rehearsal + 6 组 reviewer 反例重现 |
| 减少哪种错 | 语义评审冒充（R1）、指标虚假权威（R2）、prompt 越界（R3）、备注合法引用被误扣（R4）、身份漏父本（R5.1）、配对基线错位（R5.2）、批次交错顺序含糊（R5.3）、BMP/emoji 半截代理（§7）、后台不触发理由失实（§7）|
| 什么代价 | 分层指标由"二值权威"退到"三态 + lower_bound + coverage gate"；主指标改走同批配对；本批**不再有 0.87 那种"上一批数字"** 可直接引用；父本 identity 变了 → 所有旧答卷 hash 保留但不与当前 identity 关联 |

## 2. 变更清单（按 R 分派）

### R1 · 语义批注 hash-bound

- 新增 `benchmark/annotations/exam1_20261023_semantic.json`：24 位 primary + 1 位 backup severe（E003_T04 directions[2]）；每条 = `case_id + slot + raw_reply_sha256 + slot_text_sha256 + label + note + evidence + reviewer + review_status`。
- 新增 `tools/build_semantic_annotations.py`：一次性从旧 `SEMANTIC_LEGACY` + `BACKUP_SEVERE_LEGACY` 抽；不改动旧 dict 只作历史源。
- 重写 `tools/exam_layered_score.py`：`_match_annotation` 硬绑四元组；无匹配 → unrated；`summarize()` 不再静默继承 `SEMANTIC_LEGACY`。
- 反例：`tools/test_annotation_binding.py` 9/9 PASS（含 reviewer 那条"换一段明显错误文本仍报 pass"）。

### R2 · 三态 + 覆盖率

- `tools/exam_layered_score.py::_tri_any_pass` / `_tri_backup_severe` 三态返回 `"true" | "false" | "unknown"`；unrated 存在一律 unknown。
- `_summarize_group` 输出 15+ 字段：`primary_coverage_ratio` / `at_least_one_pass_true_false_unknown` / `severe_error_backup_lower_bound_rate` / `backup_slot_coverage_ratio` / `annotations_sufficient_for_gate`。
- `_assert_invariants` 检查 `primary_pass ≤ at_least_one_pass_true`。
- 反例：`tools/test_annotation_binding.py::r2_tri_*` + `r2_real_exam_coverage_and_invariants` + `r2_lower_bound_severe_rate` 全 PASS。
- 真实答卷重跑：`LAYERED_SCORE_v05_R1R2.json` 里 `annotations_sufficient_for_gate=False`，`severe_lower_bound=0.042` 显式标 lower bound（拒绝当权威）。

### R3 · v2 新注入联合预算

- `candidates/v2_recall/mechanism/mechanism.py`：
  - 新常量：`MAX_RECALL_CHARS=1200`、`MAX_NEW_CONTEXT_CHARS=1800`、`MAX_ONGOING_EXTRA_LINES=4`、`MAX_EXTRA_CHAIN_CHARS=120`、`OVERHEAD_RESERVE_CHARS=260`；
  - 新函数 `_truncate_chain_at_boundary` / `_score_extra_relevance` / `_fit_new_context`；
  - `run_turn` 在 `_knowledge_block` 前调 `_fit_new_context`；
  - `notes` 追加 `newctx[kept=%d dropped=%d body=%d extras=%d cap=%d]`。
- 反例：`tools/test_v2_recall.py::c12_reviewer_20_extras_probe` PASS；reviewer 原探针 extras_selector=20 → extras_in_prompt=4 seg_utf16=893 < cap 2200。

### R4 · note_lcs 降为诊断（权重 0）

- `arena/lb_scorer.py`：`NOTE_LCS_STRICT_MAX=0.9`；C2 检查改名 `note_lcs_diag`，`pts=0`；`worst < 0.9` 才 pass。
- 反例：`tools/test_note_lcs_diagnostic.py` 4/4 PASS（reviewer "我明天14:55到南门" 合法引用 score10=10.0；整句照搬 pass=False 但 score10 仍 10.0；语义泄漏交人工批注不由 LCS 裁决）。

### R5.1 · identity closure

- `tools/api_trial.py::harness_identity`：沿 `params.json.parent` 递归合并父本目录所有 .py/.md/.json 文件；防环。
- `candidates/v2_recall/params.json`：`role="candidate"`、`parent="reference_product_v1"`（修前是 `role="reference_baseline"` 且无 parent）。
- 新增 `tools/freeze_identity.py`：生成 `logs/identity_freeze_<ts>.json` 落完整 closure manifest；两臂 identity 与 closure_order 一并登记。
- 反例：`tools/test_identity_closure.py` 6/6 PASS（含"追加父本 mechanism.py 一行 → v2 identity 从 456b0622… → d1b95cb3…"）。
- **新 identity**（R5.1 closure 语义下）：
  - `reference_product_v1` = `6e65b6bced2c2e3f…` closure=[self] files=9
  - `v2_recall` = `456b0622303de19d…` closure=[v2_recall, reference_product_v1] files=17

### R5.2 · 实验计划 rev

- `refs/experiment_protocol_20261023/EXPERIMENT_PLAN_20261023.md` 重写：
  - §0 顶部显式列出 (a)-(g) 七条相对旧版的修订；
  - §1 身份表加"闭包"列，两臂 identity 由 `freeze_identity.py` 生成，非旧 `b91d9ef4/07c6f4f3`；
  - §2 H1 主指标强调"同批父本配对"、"edge ≠ pass"；
  - §3 拆三段：3.1 主指标 / 3.2 退化保护 / 3.3 工程警戒线（+20% token / +15% p95 **不预登记为拒绝线**）/ 3.4 保护性回归；
  - §4 交错顺序**预固定**（E001 P/C、E002 C/P、E003 P/C），删除"（或反向）"含糊；
  - §5 停止条件 fail-fast 传输错（**任何一次**即停，不"连续 5 次"）；无自动重试；
  - §7 "本批主动限定为前台回复、排除后台"取代旧"短故事未达 3/5 话题阈值"；
  - §3.2 (5) 覆盖率闸门明写 `annotations_sufficient_for_gate=False` 时数字必须标 lower bound / unknown。

### R5.3 · 交错演练

- 新增 `tools/exam_interleave_plan.py`：
  - `--emit-plan` 写 `logs/exam2_rehearsal_<ts>/INTERLEAVE_PLAN.json`（48 attempts，seq/arm/episode/turn/pair_key/within_pair_pos 全落）；
  - `--rehearse` mock gateway 零网络跑完整 48 位次；断言 `all_done` / `order_verified` / `state_isolated` / `extras_ok` / `closure_ok` / `real_model_calls=0`；
  - `--rehearse-fail-fast` 在第 5 次调用抛传输错，验证立即停。
- 演练结果（`logs/exam2_rehearsal_20261023_174608/`）：
  - `REHEARSAL.json`：executed=48/48, order_ok=True, extras_ok=True, closure_ok=True, state_isolated=True, real_model_calls=0；
  - `REHEARSAL_FAILFAST.json`：executed=4, stopped_at_seq=5, transport_handled=True。

### §7 · 边界与文案

- `refs/arena_v1/BASELINE_PARITY.md` §8 "话题轮换后的关系维度 / 经验画像后台重估触发（KnowledgeTriggerCoordinator）" 条目改写：**本批主动限定为前台回复、排除后台**；旧"短故事未达 3/5 话题阈值"理由失实（reviewer 实测 E001 8 轮里 7 轮 topic_status=new）已删除并显式登记撤回。
- `tools/test_baseline_parity_extra.py` 新增 `e11_by_blocks_emoji_utf16_boundary`：知识段裁尾落在 emoji 附近时验证 (1) `utf16_len(emoji)=2`；(2) 输出无半截代理；(3) 总 UTF-16 长 ≤ `TOTAL_BUDGET + 40`。同时登记 Kotlin `substring` 按 code unit / Python `str[:]` 按 code point 的**已知偏差**（本项目文本里 emoji 罕见，暂不擅改）。

## 3. 机器证据（一次可核）

`refs/experiment_protocol_20261023/merged_r1r5_review/INDEPENDENT_CHECKS_R1R5.json`

- `total_offline_checks = 77`（parity 26 + parity_extra 11 + carryover 9 + v2_recall 12 + annotation_binding 9 + identity_closure 6 + note_lcs_diagnostic 4）
- `real_model_calls = 0`
- `original_experiment_artifacts_overwritten = False`
- `reviewer_blocking_items_status`：9 项全部 `FIXED`
- `reviewer_reproductions` R1/R2/R3/R4/R5.1/R5.3 各带 reviewer 探针 + 本次实测数字
- `layered_score_old_vs_new`：`at_least_one_pass_rate / severe_error_backup_rate` 二值 → 三态分布；`lower_bound_rate` 显式命名；`annotations_sufficient_for_gate` 新增
- `source_stability`：首场 24 位 calls/manifest/scoring 三份 + 旧分层 JSON 两份全部 sha256 落盘，`modified_by_this_batch=false`

## 4. 48 次实跑清单是否可开？

**结论：清单已备，但不能开跑**。

**已备**：
- 交错顺序（§4 预固定 + rehearsal 48/48 PASS）
- 身份闭包（两臂 identity + closure manifest 已 freeze）
- 停止条件（fail-fast 演练第 5 次传输错即停 PASS）
- 分层评测（三态 + 覆盖率 + gate 标志）
- 主指标定义（同批配对、edge 不算 pass）

**不能开跑的原因**（AGENTS 交付纪律 §紧急停止+禁止越界 + EXPERIMENT_PLAN §9）：
- 无新授权：`AUTH-EXAM1-20261023-01` 24/24 已用尽；本批**不复活、不冒烟、不试探**；
- 独立复核闸门未过：本合并交付需先交回独立复核窗口，通过 48 次清单的**再一次**复核后才发出新授权；
- 语义批注 `annotations_sufficient_for_gate=False`（本批 160 backup 槽仅 1 位有批注）：本批数字可支持 H1 判决（走主槽 pass/fail 配对），**不足以**支持"严重错误备选率"权威数字。新批次若要把 backup 也纳入门禁，必须扩跑批注人工评审——这是**用户裁定的成本**（EXPERIMENT_PLAN §8 第 4 条）。

## 5. 用户/复核方需拍板的事（不自决）

1. 本批 77 离线 checks 是否作为 R1-R5 关闭依据 → 是则发出 `AUTH-EXAM2-*` 新授权；
2. 48 attempts 上限、交错顺序、无重试、fail-fast 传输错即停——四条是否与 EXPERIMENT_PLAN §4/§5 完全一致；如有一条要改，写清哪条改法；
3. backup 槽人工评审扩跑：本批不扩，backup_slot_coverage_ratio=0.006；扩与不扩决定"severe_error_backup_rate"能否作为主指标；
4. 若同批父本 E001_T06 因 R3/R4/R5 修好升到 pass（不再 fail）：H1 判决线走 §2 第二条"候选在其余 23 位不劣化 + prompt 事实层断言"，还是走"新增回忆型题集"另开小批——两种走向都需要用户裁定。

## 6. 交付清单（复核窗口应能看到）

**新增**：
- `tools/build_semantic_annotations.py`
- `tools/test_annotation_binding.py`
- `tools/test_note_lcs_diagnostic.py`
- `tools/freeze_identity.py`
- `tools/test_identity_closure.py`
- `tools/exam_interleave_plan.py`
- `tools/aggregate_r1r5_evidence.py`
- `benchmark/annotations/exam1_20261023_semantic.json`
- `logs/identity_freeze_R51.json`
- `logs/exam2_rehearsal_20261023_174608/INTERLEAVE_PLAN.json`
- `logs/exam2_rehearsal_20261023_174608/REHEARSAL.json`
- `logs/exam2_rehearsal_20261023_174608/REHEARSAL_FAILFAST.json`
- `logs/exam1_20261023/LAYERED_SCORE_v05_R1R2.json`（新版分层；未改旧 `_v05.json`）
- `refs/experiment_protocol_20261023/merged_r1r5_review/INDEPENDENT_CHECKS_R1R5.json`

**修改**：
- `candidates/v2_recall/params.json` (role → candidate + parent)
- `candidates/v2_recall/mechanism/mechanism.py`（R3 `_fit_new_context` + 常量组）
- `arena/lb_scorer.py`（R4 note_lcs_diag 权重 0 + `NOTE_LCS_STRICT_MAX`）
- `tools/api_trial.py`（R5.1 harness_identity 递归 parent）
- `tools/exam_layered_score.py`（R1 hash-bound + R2 三态）
- `tools/test_v2_recall.py`（+c12）
- `tools/test_baseline_parity_extra.py`（+e11）
- `refs/experiment_protocol_20261023/EXPERIMENT_PLAN_20261023.md`（R5.2 rev）
- `refs/arena_v1/BASELINE_PARITY.md` §8（§7 后台理由改写）

**未改**：
- `logs/exam1_20261023/{E001,E002,E003}/**/calls.json|manifest.json|scoring.json`
- `logs/exam1_20261023/LAYERED_SCORE.json` / `LAYERED_SCORE_v05.json`
- `logs/exam1_20261023/EXAM_REPORT.md`（历史账本）
- `benchmark/rubric.md` / `AGENTS.md` / `OBJECTIVE.md`（v0.5 / v1.4 已锁）
- 未发出任何真实模型调用；未新建授权；未改系统权限

## 7. 下一步（复核通过才启动）

- 独立复核窗口拿到本文档 → 逐条核 §3 `INDEPENDENT_CHECKS_R1R5.json` → 若关闭 → 发出 `AUTH-EXAM2-*` 新授权 → 训练执行窗口按 §4 预固定顺序跑 48 位次；
- 若复核发现新的阻塞项 → 本窗口继续修，**不冒跑到复核完成前**。
