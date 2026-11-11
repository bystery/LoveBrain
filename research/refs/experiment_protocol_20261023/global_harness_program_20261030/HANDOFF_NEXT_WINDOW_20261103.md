# HANDOFF — 下一窗口继续（2026-11-03 14:10）

> 读者：下一个执行窗口的主控。
> 工作根：`D:/lovebrain-arena`；只读参照：`D:/LoveBrain`。
> 固定基座：dsf @ `http://127.0.0.1:17770/v1`，temperature 0.7；judge 评审 temp 0.0。
> 本文件是当前唯一交接入口；`WORKER_NEW_WINDOW_START.md` 顶部已指向本文件。
> 先读本文件，再读 `WORKER_CURRENT_RUNBOOK.json`、`RESEARCH_STATE.json`、`TASK_QUEUE.json`。
> 不要通读旧计划；不要重建考场；不要改已封板题目/卡。

## 1. 一句话状态

W17c 已确认 ≤24 域冠军 `window_expand_b_norecap_guard`；W16 已确认 >24 域冠军
`window_expand_b_norecap_uc`。W18B（`uc_supp` vs `uc`）live 已 **320/320 完成、0 error**，
机械读数不支持 `uc_supp` 整体更好，语义评审尚未开始。评测基准复核已完成并落盘。
下一步按 §3 做三件事：W19-01 修尺子并离线重评、W18B 评审+B3 判 `uc_supp`、
`guard+uc` 单构建跨域批。当前**没有**单一通用冠军，也**没有**封存验收。

## 2. 当前证据与身份

### 2.1 冠军/挑战者

| 身份 | 域 | 状态 | 关键证据 | 证据路径 |
|---|---|---|---|---|
| `window_expand_b_norecap_guard` | ≤24 | 冠军 | W17c：severe B3 0 vs 3；prompt −52.2%；T07–12 keep 1.034；契约 3 vs 2 过门 | `W17C_MERGE_VERDICT_20261103.md` |
| `window_expand_b_norecap_uc` | >24 | 冠军 | W16：九维 9/9 正；severe 亲读 1 vs 6；prompt +0.78% | `W16_UNC_VERDICT_20261102.md` |
| `window_expand_b_norecap_uc_supp` | >24 | 挑战者，未判 | W18B：320/320、0 error；机械无整体优势；语义未评 | `logs/w18b_ucsupp_live_20261103/_reports/ARM_STATS_W18B.json` |
| `reference_product_v1` | 基线 | 参照 | 早期 W04/W09/早期 longprobe 对 B0；当前决赛未同批对 B0 | `benchmark/releases/arena_v1/split_manifest.json` |

### 2.2 W18B 机械读数（刚完成，只是机械账）

| 故事 | parent score | candidate score | schema 失败 par/cand | prompt delta |
|---|---:|---:|---:|---:|
| LRE117 | 8.50 | 9.50 | 5 / 2 | +2.09% |
| LRE121 | 8.50 | 7.75 | 5 / 9 | +1.45% |
| LRE146 | 8.92 | 8.83 | 4 / 4 | +0.00% |
| LRE138 | 9.50 | 9.25 | 2 / 3 | +0.58% |

- 320/320 manifest、0 `error.json`；p50 约 35–46s，p95 约 63–85s。
- `schema 失败` 指 `json_schema_valid`，不是 `source_ids`（不要归因到 m_src）。
- 结论：机械账不支持“uc_supp 更好”；LRE121 是明显退步。**必须等语义评审+B3 才能判去留。**

### 2.3 W17c / W16 关键星号

- W17c 九维 judge **未校准**；main 顺序 61 位全正，swap 6 位多数 −0.33；
  swap 一致 3/6。九维只能辅助，不能当稳定优势。
- W17c 原始程序分受 FK-SCORE-01 隔离误判污染：候选原始 5 零，其中
  E183_T03/E191_T02 是 `m_src` 假 veto；修正后候选 3 零 vs 父本 2 零，
  原始均值 9.3056，修正约 9.58 vs 父本 9.6758（正式口径待 W19-01）。
- W16 只有 3 篇；九维同样是未校准 judge 的辅助证据；severe 1 vs 6 是 B3 亲读。
- 当前冠军都未与 `reference_product_v1` 同批同题对拍；不能宣布“比原版全面更好”。

## 3. 立即要做的三件事（按依赖顺序）

### 任务 A — W19-01：修隔离路径 m_src + 离线重评（0 模型调用）

**代码点**：`arena/isolation/trusted_controller.py::IsolatedRun.step_one_turn`
（约 L740 的 `id_map = lb_runner.canonical_id_map(...)` 之后）。

父本 `arena/lb_runner._exec_turn_v2` 已做（L334–338）：

```python
for _i in range(prior_real_n):
    id_map.setdefault("m_src[%d]" % _i, "HIST")
```

隔离路径缺这段。修法：在 `step_one_turn` 拿到 `ep`、`i` 后，按评估侧原题计数：

```python
prior_real_n = sum(
    1 for t in ep["turns"][:i]
    for m in (t.get("input") or {}).get("messages") or []
    if isinstance(m, dict) and m.get("role") in ("HER", "ME"))
for _i in range(prior_real_n):
    id_map.setdefault("m_src[%d]" % _i, "HIST")
```

要求：
1. 单测：构造一个合法 `m_src[0]` 引用，修复前 veto、修复后 pass；
   构造伪造 `m_src[999]`，修复后仍 veto（不能把合法集无限扩大）。
2. 不要改 `arena/lb_runner.py` 的既有逻辑；不要让候选回报参与 `prior_real_n`。
3. 离线重评 W17b/W17c/W18B 已付费答卷（零模型调用），逐位对照
   `veto/score10/source_ids`；原 `scoring.json` 不改，另写
   `scoring_v05_fixed.json` + `RESCORE_SUMMARY_<batch>.json`。
4. **注意**：现有 `tools/rescore_offline.py` 只支持 `E0*` + `benchmark/episodes`
   旧布局，不能直接跑 LRE/E157；需扩展或新建 batch-aware rescore：
   - 从 batch 目录的 `candidate_<EP>` / `parent_<EP>` 读取 `turn_*/`；
   - 从 `benchmark/stories_v2/{wave3,wave3_ext}/<EP>/episode.json`（或 runner_episode）
     取 `case_id` 对应的 turn；gold 近期为空是事实，不要伪造；
   - id_map 必须包含 `m0..mN` 和 `m_src[i]`（i < 历史真实消息数）。
5. 验收：W17b/W17c/W18B 各批逐位 diff 落盘；W17c 的 E183_T03/E191_T02
   veto 清除；W18B LRE121 的 9 个 schema 失败**不应**被 m_src 修复改变
   （那是 schema，不是 source_ids），报告中分开列。
6. 完成后更新 `refs/KNOWN_FAILURES.md` 的 FK-SCORE-01 状态、W17c 判决附记、
   `TASK_QUEUE.json` W19-01 为 DONE。

### 任务 B — W18B 评审 + B3（模型调用，live 已结束，可独占评审容量）

**前置**：W18B live 已 320/320、0 error；不要与任何 live 并行。
**授权**：新建评审账本 `AUTH-W18B-REVIEW-20261103-01`（通过
`tools/run_lp_review_generic.py` 的 AuthorizationBudget 自动建账；不要手填账本）。
**命令**（在 `D:/lovebrain-arena`）：

```bash
PYTHONIOENCODING=utf-8 python tools/run_lp_review_generic.py \
  --batch-dir logs/w18b_ucsupp_live_20261103 \
  --stories LRE117,LRE121,LRE146,LRE138 \
  --auth AUTH-W18B-REVIEW-20261103-01 \
  --tag W18B \
  --cand-name window_expand_b_norecap_uc_supp \
  --cards benchmark/stories_v2/wave3_ext/GRADING_CARDS_W18.json \
  --cap 213
```

说明：
- `cap=213` 自算：160 main + 16 swap 补判 + 35 重试预留 + 2；不要低于自算值。
- 评审 temp 0.0；main 顺序判 160 位；swap 子集 T08/T16/T20/T24 共 16 位；
  真双序补判 16 位。总调用约 176+重试。
- 产出：`refs/experiment_protocol_20261023/long_probe_20261030/LP_W18B_NINEDIM_REVIEW.json`
  和 `LP_W18B_B3_QUEUE.json`、`LP_W18B_PARTIAL.jsonl`。
- 断点续跑：sidecar 已写；不要在已有判定上重烧。
- **B3 亲读必须主会话本人做**（不能交给 subagent 代替）：
  按 `LP_W18B_B3_QUEUE.json` 的 severe 标记，用 `tools/show_turn.py` 逐位看
  原文/卡/双臂；记录 `LP_W18B_B3_READRESULT.json`（标记 confirmed/downgraded）。
  注意：`run_lp_review_generic.py` 内置的 B3 queue 对 LRE 故事的 story 解析会为空
  （它只认 `E\d{3}`），且默认卡路径是 stage4；队列只作索引，**严重位必须回原评审
  JSON + W18 卡 + 原文核对**。如队列缺位，直接用 review JSON 的 severe 标记建清单。
- 评审 callfail >10% 时，severe 账降权并在报告标注；重试只到预登记 20% 头上。
- 判据（W18 设计冻结）：G1 severe 两类都覆盖且 cand<par；G2 九维最差 ≥−0.2
  且 interact/emotion/initiative 各 ≥−0.2；G3 prompt ≤+8% 且 0 额外调用；
  G4 触发面 (10%,90%]。全过才 KEEP；否则 `uc` 保持 >24 冠军。
- 不要因为 W18B 机械退步就直接判死；也不要因为 LRE117 候选更好就忽略 LRE121。
  先 W19-01 修尺子，再读程序分；语义以 B3 亲读 severe 为主，九维辅助。

### 任务 C — guard+uc 单构建 + 跨域批（新 live，最后做）

目标：把双域冠军收成一个**开发集总冠军候选**，不是封存冠军。

#### C1. 新候选（离线实现）

建议名：`window_expand_b_norecap_uc_guard`
（父本 = `window_expand_b_norecap_uc`；唯一增量 = guard 的确定性输出契约守卫；
**不要**同时加 suppression，保持单变量）。

实现步骤：
1. 复制 `candidates/window_expand_b_norecap_uc` 整树到新目录。
2. 从 `window_expand_b_norecap_guard/mechanism/` 复制：
   - `contract_guard.py`
   - `_vendor/output_repair.py`
   - `_vendor/normalize_reply_contract.py`
3. 在 `mechanism.py` 中按 guard 的方式加载 `_GUARD`；在 `gw.generate` 返回
   `reply` 后调用 `_GUARD.repair_and_audit(reply)`，写
   `contract_guard_audit.jsonl`；notes 增加 `guard_changed/guard_rules`。
   **prompt 构造不改**，只作用于返回文本。
4. 在 `mechanism.py` 顶部加显式依赖标记（staging 只扫 mechanism.py）：
   `# candidate dependency: candidates/reference_product_v1`
   `# candidate dependency: candidates/v3_system_slim`
   否则 staging 可能缺 base。
5. 更新 `params.json`（parent=uc；`mechanism_flags.contract_guard_fix=true`；
   `uncertain_marker=true`）、`IDENTITY.md`、`MECHANISM.md`。
6. 离线验证：
   - `python tools/test_identity_closure.py`（或现有 identity 测试）；
   - 无 guard 改动时，prompt 与 uc 逐字节相同；
   - guard 单测正反例；修复不改变合法 JSON 的正文；
   - 用 mock 跑 1 短 1 长，确认 staged/candidates 含
     `reference_product_v1`、`v3_system_slim`、新候选三棵树。

#### C2. 合并卡与批配置

- 新建 `benchmark/stories_v2/wave3/GRADING_CARDS_W19_CROSS.json`：
  合并 `GRADING_CARDS_W17C.json`（72）+ `GRADING_CARDS_W18.json`（160）。
  要求：232 张、case_id 唯一、无缺卡；保留两套卡字段的并集；
  加 `stage: "W19_cross"` 元数据。
- 新建 `refs/experiment_protocol_20261023/long_probe_20261030/W19_CROSS.BATCH_CONFIG.json`：
  - `batch_id`: `BATCH-W19-CROSS-20261103`
  - `arm_map`: parent=`window_expand_b_norecap_uc`,
    candidate=`window_expand_b_norecap_uc_guard`
  - `episode_order`: E157,E164,E170,E183,E191,E212（12 轮）+
    LRE117,LRE121,LRE146,LRE138（40 轮），arm_order 交替
    parent_first/candidate_first
  - `cards_path`: 上面的 232 卡
  - `recall_capable`: false（沿用 W18B；如设计另有理由再改并登记）
- 规模：6×12×2 + 4×40×2 = **464 attempts**。
- 用冻结的 `tools/exam_interleave_plan.py`（**不得编辑**）：
  `--emit-plan --batch-config ... --out ...`，再 `--execute-mock` 生成 canonical
  `W19_CROSS.INTERLEAVE_PLAN.json`；校验 464 attempts、10 篇、卡 232、无缺位。
- 建授权 `AUTH-W19-CROSS-20261103-01`：
  `real_endpoint` dsf/temp0.7；`max_attempts=464`；`fail_fast=true`；
  `no_retry=true`；`no_makeup=true`；`automatic_judge=false`；
  `frozen_bindings` 绑定 plan_sha256、batch_id、cards、attempts、parent/candidate
  identity_sha256（用 `tools/freeze_identity.py` 计算）。
- **跑前**把跨域判据写进 `W19-03_CROSS_DOMAIN_DESIGN_20261103.md`，不得跑后改：
  - 每域 G1 severe B3：candidate ≤ parent，且不新增故障族；
  - G2 九维 main-only 最差 ≥−0.3，interact/emotion/init ≥−0.2；
  - G3 契约：candidate veto ≤ parent+1；空回复 0；
  - G4 成本：prompt ≤+5%、0 额外调用；p50/p95 只报告；
  - G5 顺序：swap 只作稳定性诊断；若 swap 与非 swap 方向翻转，不得宣布单冠军；
  - 两域全过才算开发集总冠军；任一域破门 → 保留双域冠军。

#### C3. live

- `tools/run_max288_batch.py --plan ... --authorization ... --out logs/w19_cross_live_20261103 --mode precheck`
  过 G1–G7 后 `--mode live --workers 6`。
- **独占**：live 期间不跑 review、不跑其它 live、不让 6–8 个 agent 同时打模型。
- 完成标准：464/464 manifest、0 CALL_FAIL/TIMEOUT；否则按 fail_fast/no_retry
  记账，不为赢重跑。

#### C4. 跨域评审

- live 完成后单独跑：
  `--batch-dir logs/w19_cross_live_20261103 --stories E157,E164,E170,E183,E191,E212,LRE117,LRE121,LRE146,LRE138 --auth AUTH-W19-CROSS-REVIEW-20261103-01 --tag W19CROSS --cards GRADING_CARDS_W19_CROSS.json --cap 328`
- cap 自算：232 main + 40 swap 补判 + 54 重试预留 + 2 = 328。
- B3 亲读由主会话做；输出 `LP_W19CROSS_B3_READRESULT.json`。
- 判决：两域全过 → 单冠军 `uc_guard`（开发集冠军，仍非封存）；
  任一域失败 → 双域冠军保留，记录交易。

## 4. 并行分工（用户要求 6–8 个 agent）

**容量铁律**：6–8 个 agent 只并行**离线**文件/分析工作；模型调用始终按
dsf ≈6 流，live 独占，review 不与 live 并行。不要 6 个 agent 同时发模型请求。

建议文件所有权（互不重叠）：

| Agent | 任务 | 写文件 | 模型调用 |
|---|---|---|---:|
| A | W19-01 修复 + 单测 | `arena/isolation/trusted_controller.py`、`tools/test_fk_score*.py` | 0 |
| B | batch-aware 离线重评工具 + W17b/W17c/W18B 重评 | `tools/rescore_offline.py`（扩展）或 `tools/rescore_batch.py`、`logs/*RESCORE*` | 0 |
| C | W18B 九维评审 | `refs/.../LP_W18B_NINEDIM_REVIEW.json` 等 | 176+（6 流） |
| D | guard+uc 新候选实现 + 单测 | `candidates/window_expand_b_norecap_uc_guard/**` | 0 |
| E | 232 卡合并 + batch config/plan | `benchmark/.../GRADING_CARDS_W19_CROSS.json`、`refs/.../W19_CROSS.*` | 0 |
| F | B3 展示包/摘要（只读） | `logs/_w18b_read_digest.txt` 等 | 0 |
| G | 红队复核：diff/veto 逐位、identity hash、plan 计数、卡唯一性 | 只读 + 报告 | 0 |
| H | 快车道设计草案（§7） | `refs/.../FAST_LANE_DESIGN_20261103.md` | 0 |

执行顺序：
1. 并行开 A/B/D/E/F/G/H；C 同时跑 W18B 评审（6 流）。
2. A/B 完成后读 W19-01 结果；C 完成后主会话 B3。
3. B3 判决后，D/E 的跨域候选/计划应已就绪；跑 precheck。
4. live 464 调用（6 流、独占）；live 后跨域评审；主会话 B3；出裁决。
5. 更新 `RESEARCH_STATE.json`、`WORKER_CURRENT_RUNBOOK.json`、`TASK_QUEUE.json`。

## 5. 不可违反的约束

- 固定 dsf/17770/temp0.7；`hy4-preview` 硬禁；不擅自换模型/买服务。
- 授权账本只由 `AuthorizationBudget` 写，不手改；scope/cap 不可变；
  目的、身份、固定条件、预算先登记后派发。
- `no_retry / no_makeup`：不为赢重跑；失败不入分母删除；W18 90/320 事故保留。
- live 独占 ≈6 流；评审后置；W18 的 9 流 120s 超时事故是戒。
- `tools/exam_interleave_plan.py` 等冻结执行器不得编辑。
- 题目封板后不改题；故事 SEALED 直到批消耗；B 线只开一次（当前不存在）。
- 未校准 judge 不单独判死；severe 必须主会话 B3 亲读；worker 不代投用户票。
- 任何候选/协议变更必须版本化；协议变更 ≠ 候选收益；旧答卷离线重评优先。
- 删除需用户确认；当前只允许移 archive；不要清用户未提交内容。

## 6. 评测基准复核结论（已落盘，不要重复造）

完整报告：`refs/experiment_protocol_20261023/global_harness_program_20261030/EVALUATION_BASELINE_AUDIT_20261103.md`

结论摘要：
- 近期故事 gold 全空（`stories_v2` 0/3202），程序分只证契约；
  W17c/W18B 的 9.x 不是事实质量分。
- 九维 judge 未按 rubric §3 完成 20 锚校准；`logs/judge_calibration.json` 不存在；
  历史 IRR 在 fact/attrib/信息不足上不合格；九维只辅助。
- `split_manifest.json` 明写 selection n=0、b_line `NOT_CREATED`；
  `final_20261029` 已被 `f3_newmat_live_20261029` 消耗；当前无封存最终集。
- exposure `experiments_used` 多处为空但实际已用；LRE 是 E117 等扩展；
  卡 `independent_review: false`；当前决赛未同批对 B0。
- FK-SCORE-01 隔离 m_src 误判 OPEN；W19-01 未完成前程序零分不能当候选退步。
- 金额因本地网关无单价记“未知”，不是 0；可维护性未量化。

## 7. 关于“demo 快速迭代”（用户 2026-11-03 提问）

**为什么不能只拿 demo 快速迭代：**
- 主要失败族是长链状态/时间/记忆/取消事项/污染（severe），3–6 轮短 demo
  根本触发不到；在短 demo 上 KEEP 会在 40 轮批上塌。
- 评审本身有成本/顺序偏置；短 demo 的 judge 读数噪声更大，容易把
  “位置偏置”误当机制收益。
- 但当前流程确实慢：每个候选都跑 144–320 live + 72–160 review，且 live/review
  因 6 流限制串行。慢在“全量评估”，不在“设计”。

**建议的快车道（需预登记，不能偷偷用于晋级）：**
- 固定 2 篇 mini-dev：1 篇 12 轮（如 E157）+ 1 篇 40 轮（如 LRE117）；
  2 臂 = 104 调用，6 流约 20–25 分钟；评审 52 位 + swap ≈ 60 调用，约 20 分钟。
- 快车道只做**淘汰**：预登记 G0（契约/空回复/严重错误/成本）任一破门即淘汰；
  通过者才进全量 232 位。
- 离线先跑：prompt 等价、contract 单测、mock replay（0 调用）；6–8 个 agent
  可并行做这些。
- 全量仍只在决赛候选上跑；不要把快车道结果写成冠军证据。
- 快车道素材必须是固定、未暴露、预登记；不能每轮换故事“跑到赢”。
- 建议新增 `W19-06 快车道预登记`，先写设计再使用；未登记前不启用。

## 8. 当前状态文件与证据入口

- `WORKER_CURRENT_RUNBOOK.json`（as_of 2026-11-03 14:00）
- `refs/experiment_protocol_20261023/global_harness_program_20261030/RESEARCH_STATE.json`
- `refs/experiment_protocol_20261023/global_harness_program_20261030/TASK_QUEUE.json`
  （W18-04 已更新 320/320；新增 W19-03/04/05）
- W17c 判决：`refs/experiment_protocol_20261023/long_probe_20261030/W17C_MERGE_VERDICT_20261103.md`
- W16 判决：`refs/experiment_protocol_20261023/long_probe_20261030/W16_UNC_VERDICT_20261102.md`
- W18B 机械账：`logs/w18b_ucsupp_live_20261103/_reports/ARM_STATS_W18B.json`
- 评测审计：`refs/experiment_protocol_20261023/global_harness_program_20261030/EVALUATION_BASELINE_AUDIT_20261103.md`
- 已知故障：`refs/KNOWN_FAILURES.md`（FK-SCORE-01 OPEN）
- rubric：`benchmark/rubric.md` v0.5
- 单冠军设计旧档：`refs/experiment_protocol_20261023/global_harness_program_20261030/W17_SINGLE_CHAMPION_DESIGN_20261102.md`
- W18 设计：`refs/experiment_protocol_20261023/global_harness_program_20261030/W18_UNC_SUPPRESS_DESIGN_20261102.md`

## 9. 交接验收清单（下一窗口开窗先核）

- [ ] `WORKER_CURRENT_RUNBOOK.json.as_of` = 2026-11-03 14:00；`current_candidate` 已写 W18B 完成。
- [ ] W18B 目录 320/320 manifest、0 error；不要重跑。
- [ ] `TASK_QUEUE.json` 含 W19-01/02/03/04/05；W18-04 的 result_note 已更新。
- [ ] 没有正在运行的 live/review 进程；`logs/w18b...` 已收口。
- [ ] 读 `EVALUATION_BASELINE_AUDIT_20261103.md`，不要重新宣称程序分=质量。
- [ ] 先做 W19-01（0 调用），再做 W18B 评审+B3；跨域 live 最后做且独占。
