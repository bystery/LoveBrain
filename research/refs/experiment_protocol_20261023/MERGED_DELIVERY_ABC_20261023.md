# LoveBrain Arena · 训练执行会话 · 第二轮 A/B/C 合并收口交付 2026-10-23 (rev ABC)

**批次**：`MERGED_ABC_20261023`
**执行窗口**：本训练执行会话（不新开窗口、不重建考场）
**判决依据**：独立复核 `refs/experiment_protocol_20261023/r1r5_independent_review/REVIEW.md` §2/§3/§4/§5 + `CHECKS.json`
**真实模型调用**：0｜**旧答卷原始 artifact 覆盖**：0｜**授权**：`AUTH-EXAM1-20261023-01` 关闭不复活

## 0. 一句话

**上一轮 R1–R5 已签收的不动；本轮只修 A/B/C 三组 + 计划四句。94 项离线检查全绿，48 槽交错演练接回正式执行链（capture/mock/live 同入口）零派发前守卫、真实 attempt 账本 fail-fast、批注上下文三绑、emoji 码元预算统一。48 次实跑清单已备但不开跑，等新授权与再复核。**

## 1. 已签收（本轮未返工，据 reviewer 判决 1）

输出文本/槽位绑定、未知备选三态、LCS 权重归零、父本依赖闭包、中文事项数量控制、已有跨轮与恢复测试。这些保持不变，只在它们之上补 A/B/C。

## 2. A —— 演练接回正式执行链

**reviewer 复现的漏洞**：把父本 E001 T02 改成重复 T01，旧 rehearsal 仍报 `order_verified`；计划填全 0 候选冻结身份，仍报 `closure_ok=True`；rehearsal 直接 `mod.run_turn` + 自定义 `MockGateway`，绕开预算/网关/账本/评分/输入边界。

**修法（不新造框架，接回已有 `IsolatedRun`）**：`tools/exam_interleave_plan.py::execute_plan(plan, mode)`
- `mode ∈ {capture, mock, live}` **共用同一调度与守卫入口**，只换适配器：
  capture=`openai_compat`+进程内替身 urlopen+测试授权/预算账本（零网络，走满预算权威/网关/账本/评分/白名单）；mock=离线替身适配器；live 无授权直接拒；
- **派发前守卫**（任一不过 → 零派发 `PlanError`，根本不构造 IsolatedRun）：
  `validate_plan`（48 槽 seq 唯一 / 每 (臂,episode) 覆盖 T01..T08 无重复缺轮 / case_id↔题面 / 每 pair 两臂齐 / 先手顺序）+ `verify_frozen_identity`（当下重算两臂闭包 + 本批题面/评分卡/评分器/执行器/工具/配置全文件散列，与计划声明逐字节比对）；
- **真实 attempt 账本**：经 `ModelGateway` 落 `attempt_ledger.jsonl`，fail-fast 演练第 5 次派发注入传输错 → 账本 `5 started / 4 completed / 1 failed`，**停批且不补跑另一臂**；
- **可观察隔离**：6 个 (arm,episode) 各独立 run_id + 独立 out/control + `isolation_sentinel.json`；
- **最终请求新增段**：从每轮 `calls.json` 的真实出站 user prompt 测 `newseg_max_utf16`，不靠截短 notes。

**证据**：`tools/test_execute_plan.py` 8/8 PASS（capture 48/48/0、mock 48/48、fail-fast 5/4/1 不补配对、reject-duplicate、reject-bad-freeze、reject-missing-turn、6 独立 run_id 隔离、live 拒发）；产物 `logs/exam2_execute_*/{INTERLEAVE_PLAN,EXECUTE_CAPTURE,EXECUTE_MOCK,EXECUTE_FAILFAST,REJECT_DUPLICATE,REJECT_BAD_FREEZE}.json`。

## 3. B —— 批注上下文绑定 + 归属修正

**reviewer 复现的漏洞**：`_match_annotation` 只比 case_id/slot/reply/slot 文本散列，不校验文件头 run_id / harness_identity，也无题面内容散列 → 保持原输出、给批注设无关 run_id + 全 0 身份 + 错误 case hash 仍匹配；且 E001_T04、E003_T04 的 recommended 仍继承源自备选槽（warm / directions[2]）的 `edge`。

**修法**：
- 每条批注新增 **`case_input_sha256`（题面内容）+ `run_id` + `harness_identity_sha256`**；`_match_annotation(..., context=)` 与 `summarize(require_context=True)` 据该批 `EXAM_INDEX` 的真实 authorization_id / harness_identity + 冻结题面散列三绑校验；缺任一绑定 = 上下文未验证 → 拒、不凭活文件补造、不跨批静默复用（`context=None` 仅留给历史只读复算）；
- `build_semantic_annotations.py` 把 **E001_T04 / E003_T04 的 primary 记 unrated**（备选问题不再替首选定级），旧标签撤回记入 `provenance_corrections`；
- 结果：批注 23 条（22 primary 绑定 + 1 backup severe）；`primary_coverage_ratio` **0.958 → 0.875**（不再把备选派生的 edge 当有效首选审查覆盖）；`edge = 0`。

**证据**：`tools/test_annotation_binding.py` 15/15（R1 四反例 + B 五反例：full_context_match / case_content_drift_denied / wrong_run_or_harness_denied / missing_context_unverified / real_annotations_bound(bound=22, rated=21, drift_honored=0) / backup_derived_primary_unrated(edge=0, coverage=0.875) + R2 四态）。分层重算 `logs/exam1_20261023/LAYERED_SCORE_v05_B.json`。

## 4. C —— UTF-16 码元预算统一

**reviewer 复现的漏洞**：R3 的中文 20-extras 探针已关闭，但 `recall_body` 尾裁、`_truncate_chain_at_boundary`、`_fit_new_context` 仍用 Python 码点 `[:n]`/`[-n:]` 却按 `utf16_len` 计数。run_turn 反例：归档含 1200 个 🎂，仅 emoji 就占 2400 码元 > 声明 1800，完整 user 2822。

**修法**（候选侧，不动父本已登记的近似行为）：
- 新增 `utf16_truncate_head/tail`：按 UTF-16 码元截断、只在码点边界落脚、**绝不切代理对**；
- `recall_body` / `_truncate_chain_at_boundary` / `_fit_new_context` 全部改用码元安全截断；
- `_fit_new_context` **精确预留段头开销**（回忆段头 RECALL_HEADER + 分隔、进行中事项段头），extras 行 + recall body + 段头/提示/分隔合计 ≤ `MAX_NEW_CONTEXT_CHARS=1800`，取消旧魔法 `OVERHEAD_RESERVE_CHARS=260`；
- run_turn notes 报 `new_seg_est`（与 cap 同单位 1800，测试不再混用 2200/+400）。

**证据**：`tools/test_v2_recall.py` 15/15，含 c13（utf16 截断单位：1200 码元留 600 emoji 无半截代理）、c14（reviewer 1200-emoji run_turn：recall 段 1307 ≤ 1800，emoji 载荷 1200 ≤ 1800，无半截代理）、c15（BMP 归档 + 30 条 emoji chain 共同挤占：held=4/4、new_seg_est=1800、无半截代理）。

## 5. 计划四句同步（EXPERIMENT_PLAN rev ABC）

1. 删除"使候选始终能看到父本同题输出"；顺序只影响调度，任何一臂不得看另一臂输出/状态（§4）；
2. 同批两臂都 pass → 记"未观察到候选优势"，H1 **不得**改用输入侧断言补一个成功结论，输入送达与最终回复改善分层（§2）；
3. 新批必须逐槽重审、低覆盖不得宣布未退化或晋级、"尚未批注"不是生成途中必须停批的错误（§3.2 第 5 条 + §5）；
4. 48 硬上限无重试无补跑、~~两臂同位一并重跑~~ 作废、补采样另列独立预算、严重错误口径统一为"不增"、工程警戒线与用户交易分开（§3.2/§3.3/§4/§5/§8）。

## 6. 机器证据（一次可核）

`refs/experiment_protocol_20261023/abc_final_review/INDEPENDENT_CHECKS_ABC.json`
- `total_offline_checks = 94`：baseline_parity 26 + parity_extra 11 + carryover 9 + v2_recall 15 + annotation_binding 15 + identity_closure 6 + note_lcs_diagnostic 4 + **execute_plan 8**
- `real_model_calls = 0`｜`original_experiment_artifacts_overwritten = False`
- `current_identity`：reference_product_v1 `6e65b6bced2c2e3f…`（self）；v2_recall `36dc54d6220c8099…`（闭包含父本，C 改后从 456b06 变为 36dc54）
- `frozen_config_files`：题面×3 + 评分卡 + rubric + 批注 + arena（scorer/protocol/gateway/runner/assembly/api_candidate/trusted_controller/host_runtime/_candidate_host/authorization_budget）+ tools（api_trial/exam_layered_score/build_semantic_annotations/freeze_identity/exam_interleave_plan/rescore_offline）逐文件 sha256
- `reviewer_reproductions` A/B/C 各带 reviewer 探针 + 本次实测

## 7. 变更清单

新增 `tools/test_execute_plan.py`、`tools/aggregate_abc_evidence.py`、`logs/exam2_execute_*/*`、`logs/identity_freeze_ABC.json`、`benchmark/annotations/exam1_20261023_semantic.json`(v2 上下文三绑)、`LAYERED_SCORE_v05_B.json`、`abc_final_review/INDEPENDENT_CHECKS_ABC.json`。

修改 `tools/exam_interleave_plan.py`（execute_plan 接回 IsolatedRun + 守卫 + 真实账本）、`tools/build_semantic_annotations.py`（上下文三绑 + 归属修正）、`tools/exam_layered_score.py`（`_match_annotation` context + `summarize` require_context）、`tools/freeze_identity.py`（config_files 冻结）、`tools/test_v2_recall.py`（+c13/c14/c15，c8/c12 改真实新增段 ≤1800）、`tools/test_annotation_binding.py`（+B 六反例）、`candidates/v2_recall/mechanism/mechanism.py`（utf16_truncate_* + 精确段头预算）、`refs/experiment_protocol_20261023/EXPERIMENT_PLAN_20261023.md`（ABC 修订 + 四句）。

不改：旧答卷 `calls/manifest/scoring`、`LAYERED_SCORE.json` / `LAYERED_SCORE_v05.json`、`EXAM_REPORT.md`、rubric v0.5 / AGENTS v1.4；父本 `by_blocks` 已登记近似（e11）保持不擅改。

## 8. 48 次实跑是否可开

**清单已备但不开放**：无新授权；交回独立复核通过后再发 `AUTH-EXAM2-*`。本轮零真实调用、不复活旧单、不冒烟。通过后进入**有限前台、公开开发题的真实 A/B 数据收集**；仍不是完整原版生产性能、独立泛化或最终冠军证明。

## 9. 用户/复核需拍板

- 94 离线检查 + 9 条 reviewer 反例是否作为 A/B/C 关闭依据 → 发新授权；
- backup 槽人工补评扩跑（本批 `annotations_sufficient_for_gate=False`，160 backup 槽仅 1 评）：扩则 severe 率可作主指标，不扩则保持 lower bound/unknown；
- 若同批两臂 E001_T06 都 pass：H1 记"未观察到优势"（§2），是否接受改判 E001_T06 外的回忆型新位。
