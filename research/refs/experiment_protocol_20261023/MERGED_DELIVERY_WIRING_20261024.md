# §3 剩余接线合并交付（训练执行者）

日期：2026-10-24。执行依据：`HARNESS_OPTIMIZATION_EXECUTION_GUIDE_20261024.md` §3。
证据来源：上一轮独立复核 `abc_independent_review_20261024/CHECKS.json`。

## 一句话

第 3 节三组接线缺陷已全部改完并用**真实执行链**离线证明（48 slot 逐轮交错、
真实 attempt↔slot 一一对应、执行序核验、live 授权入口、题面散列缺失不再继承、
两臂产物→分层/配对报告接通）；**本轮零真实 API 调用**（无匹配本批的有效新授权）。
本包属"候选准备/工程修复"，**不是**效果优化完成——真实 A/B 需你签发下面那份 48 次授权。

## 1. 逐项：reviewer 缺陷 → 修法 → 证据

### 1.1 没有真正逐轮交错（reviewer：实际仍父连跑 8 轮再候选；乱序前驱也能过）

- 把 `arena/isolation/trusted_controller.py::run_episode` 拆成
  `begin_run / step_one_turn / finish_run` 三个可复用原语，`run_episode` 外部行为不变
  （编排壳 begin→逐轮 step→finish，`api_trial` 调用方零改动）。
- `tools/exam_interleave_plan.py::execute_plan` 重写为**按冻结 48 slot 的 seq 逐项推进**：
  先建 6 个常驻宿主（每 `(arm,episode)` 一个，各持独立连续状态），再对每个 slot 调
  `step_one_turn(state, expect_turn=row.turn)`——**派发前**核对精确前驱，缺失/落后/超前即拒。
- 执行序**从真实派发事件核验**（`execution_order_verified` / `dispatch_bijection` /
  `per_key_dispatch`），不再由计划重造一份"实际顺序"。
- 证据：交错真实序为 `parent E001_T01, candidate E001_T01, parent E001_T02, …`；
  `per_key_dispatch` 六键各 8；`REJECT_OUT_OF_ORDER` 把 parent E001 的 T02/T03 互换
  （计划仍自洽、validate_plan 过）→ 停在越界 slot、`attempt_started=2`（派发前拦下，不补跑）。

### 1.2 授权后的真实执行入口没实现（reviewer：live 无条件拒绝、无授权参数）

- `execute_plan` 现收 `authorization=`，mode 扩为 `mock / capture / live / live_stub`，
  **四者共用同一逐 slot 调度/状态/边界/记账**，只差网络与授权来源：
  capture=进程内替身 urlopen + 演练授权；live=真实 urlopen + 你签发的授权；
  live_stub=真实授权的配置/预算接线 + 替身网络（验收用，零真实调用）。
- online 适配器（openai_compat）派发**之前**先过 `validate_authorization_doc` 逐字对账
  端点/model/temperature/额度 + `AuthorizationBudget.ensure_batch`；无授权或无效授权
  → 在构造任何 `IsolatedRun` 之前 `PlanError` 拒绝（零派发）。测试授权只在不能出网的
  替身路径使用，绝不接真实服务。
- 证据：`LIVE_NO_AUTH` / `LIVE_INVALID`（authorized=false）均零派发拒绝；
  `LIVE_STUB` 合法授权走通 48/48、执行序核验、联合预算达标、`real_model_calls=0`。

### 1.3 题面散列缺失仍沿用旧批注 + 身份口径混用（reviewer：缺题面绑定不能沿用语义标签）

- `exam_layered_score._match_annotation`：新批口径下评估侧 `case_input_sha256=None`
  → 直接判未验证（`return None`→unrated），不再 `is not None` 短路跳过比较而静默继承旧标签。
  历史只读复算（`context=None`）保持宽松不变。
- `summarize` 显式分层 `authorization_id`（覆盖整批、跨 run 复用，非每 run 唯一）、
  `batch_id`（本生成批次）、`per_episode_run_id`（真正产答卷的 IsolatedRun run_id），
  并在逐轮记录单列 `generating_run_id` + `case_input_sha256_16`。
- 新增反例 `b_missing_case_input_context_denied`：run/身份/答卷/槽位四项全对、仅题面散列缺失
  → 拒；带上匹配散列 → 命中（证明拒绝只针对缺散列）。

### 1.4 新增段诊断命名 + 真实渲染联合预算 + 报告接线

- 旧诊断只测【回忆】段却像"二者总量"：改名为 `recall_seg_*`（明确只量【回忆】段），
  **另加真实渲染核验** `combined_newctx_*`：候选相对父本在同 case 真实出站 prompt 的
  UTF-16 码元差 = extras+recall 实际新增段，沿用已修好的联合预算单位 `MAX_NEW_CONTEXT_CHARS`
  （1800），不另造预算口径。
- `reports_from_batch`：两臂真实输出目录 + `EXAM_INDEX` + `exam_layered_score` 一起接通，
  从同一批次直接生成 `LAYERED_SCORE_parent/candidate.json` 与 `PAIRED_DIFF.json`（逐位程序分/
  veto/token/时延 + 新增段）。无批注时语义槽一律如实 `unrated`，绝不冒充 pass。
- 证据：`REPORT_WIRING` 两臂各 24 轮、`all_semantic_unrated_without_annotations=True`、
  程序分逐位可读、`combined_newctx_ok=True`。

## 2. 测试文档表（全部零真实调用）

| 级别 | 套件 | 覆盖 | 结果 |
|---|---|---|---|
| P0 | `exam_interleave_plan.py --all` | capture48/mock/fail-fast5-4-1/重复/坏冻结/**乱序前驱**/**live无授权**/**live无效**/**live替身走通**/报告接线 | 10/10 PASS |
| P0 | `test_execute_plan.py` | 真交错+order+bijection+联合预算、fail-fast不补配对、6 独立 run_id+哨兵、乱序拦截、live 三门、报告 | 11/11 PASS |
| P1 | `test_annotation_binding.py` | R1 散列绑定 / B run·身份·题面绑定（含**新增缺题面散列拒绝**）/ R2 三态 | 16/16 PASS |
| P1 | `test_v2_recall.py` | 机制 P1-P4/R3 联合预算/UTF-16 码元截断不切代理对 | 15/15 PASS |
| P2 | `run_episode` 编排壳 | stop_after=3 → 3 轮 complete=False 行为不变 | 手动 PASS |

修复的既有回归：`trusted_controller.deny_files`/undeny 与 `exam_layered_score.summarize` 的
`os.path.relpath` 在**跨盘**（C: 临时 vs D: 仓库）抛 ValueError，已加驱动安全回落（Windows 坑）。

## 3. 身份重算（执行前须一致；本树未动候选，身份不变）

| 对象 | identity_sha256（前 16） | 闭包文件数 |
|---|---|---|
| reference_product_v1（父） | `6e65b6bced2c2e3f` | 9 |
| v2_recall（候选） | `36dc54d6220c8099` | 17 |

与指导书 §2 记录一致。`6e65…`/`36dc…` 为工作树当前值，非照抄上表。

## 4. 待你签发：精确 48 次真实 A/B 授权清单

机器清单：`refs/experiment_protocol_20261023/CALL_MANIFEST_48_20261024.json`
（含 48 个 `(seq, arm, episode, case_id)`、逐字绑定的端点/model/temperature、
两臂闭包身份 + 本批全部冻结配置文件散列、批/授权级持久账本口径、失败即停条件）。

请据此签发一份授权单（worker 不自造、不复用旧授权）：

```json
{
  "authorization_id": "AUTH-AB-RECALL-20261024-01",   // 由你命名
  "authorized": true, "status": "ACTIVE",
  "scope": "24 位公开开发题 × 两臂 = 48 生成 attempt（同题/同卡/同采样，逐轮交错）",
  "real_endpoint": {
    "api_base": "http://127.0.0.1:17770/v1",
    "model": "dsf", "temperature": 0.7, "max_attempts": 48
  },
  "budget_ledger": "logs/auth_budget/AUTH-AB-RECALL-20261024-01.jsonl"
}
```

约束：`max_attempts=48` 只含生成，**不含**探活额外答卷、评审/AI judge 调用（另计）；
`AUTH-EXAM1-20261023-01` 已关闭（账本 24/24 + authorization_closed），绝不复活/补跑。
拿到有效授权单后，我按 §4 直接跑 `execute_plan(mode="live", authorization=<上单>)`
并连续完成第 4.3 节全量内容初审，不再另加"是否继续读输出"的确认。

## 5. 等授权期间的离线工作（不冒烟、不动已冻结两臂）

按 §5 先做**资产删减候选**：`v3_system_slim` 保持父本记忆读/写不变，只把长 system
资产改成最短可工作的任务/行为说明（当前谁与谁交流、真实记录 vs 私下备注、当前目标、
不补造既成事实、期望回复形态），下一批与父本对照回答：删文本后意图/事实/互动有无变差、
被压平的主动性/调侃/推进有无改善、真实省了多少输入 token、哪些失败可归因于删除缺失任务信息。

## 6. 本批局限

三故事 24 位是开发探索，非独立充分样本；不能据此宣称整个新架构/用户口味/完整后台成本/
泛化成功。离线演练 recall/combined 段为 0 是事实（替身答卷、无播种记忆可召回），
真实渲染核验路径已证明会切真实 prompt——真实记忆内容下才会非零，不代表候选真实 token 成绩。
