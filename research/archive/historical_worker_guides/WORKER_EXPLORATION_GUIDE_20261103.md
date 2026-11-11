<!-- historical worker document, sanitized; not current -->
# WORKER EXPLORATION GUIDE — 全面探索指导书（2026-11-03）

> 读者：新开窗口的执行主控。本文教你**如何从零建立本项目的完整态势感**。
> 配套：`WORKER_LOOP_PROMPT_20261103.md`（可直接粘贴的继续任务指令）。
> 工作根：`<arena-root>`；只读参照：`<LoveBrain-repo>`（原版产品，比较基准）。

## 0. 这个项目是什么（30 秒版）

LoveBrain 斗兽场：以 `<LoveBrain-repo>` 原版为公平参照，在同一固定模型服务（dsf @ `<fixed-model-endpoint>`，temp 0.7）下，对**完整 harness**（提示+记忆+意图+上下文+调用策略）做删除/重写/合并的迭代进化。API 是固定考场服务；**被改进的是 harness 机制，不是模型**。最高纪律：`agents.md`（v1.6）+ `benchmark/rubric.md` v0.5。

## 1. 入口链（按此顺序读，不要跳）

```
agents.md                                    ← 宪法：总任务/纪律/晋级/权限边界
WORKER_CURRENT_RUNBOOK.json                  ← 指针文件（只做指针，不承载状态）
WORKER_NEW_WINDOW_START.md                   ← 开窗指引（指向本文与 LOOP_PROMPT）
refs/experiment_protocol_20261023/global_harness_program_20261030/
  ├─ RESEARCH_STATE.json                     ← 权威状态：冠军/批次/判决/失败族
  ├─ TASK_QUEUE.json                         ← 权威任务队列（含 W20 基准修复）
  └─ W19_EXECUTION_INCIDENT / 各判决文档      ← 单批证据
refs/KNOWN_FAILURES.md                       ← 已知故障册（FK 编号，判决硬门）
refs/experiment_protocol_20261023/long_probe_20261030/  ← 长探针全部判决与评审 JSON
```

**规则：状态以 RESEARCH_STATE.json + TASK_QUEUE.json 为准；任何文档正文与它们冲突时以它们为准；任何"计划/估算"不等于"已运行/已完成"。**

## 2. 冠军谱系（谁活着、谁死了、为什么）

当前**双域冠军**（判决挂起状态见 §5）：
- ≤24 轮域：`window_expand_b_norecap_guard`（W17c MERGE）
- >24 轮域：`window_expand_b_norecap_uc`（W16 KEEP）
- `uc_supp`：W18B 判 KEEP-no-regression，不替换冠军（目标族未实例化）
- `uc_guard`（=uc+guard 合并）：跨域批 464/464 已跑完，**判决挂起**（用户令：基准修复优先）

谱系速览（详见各 IDENTITY.md）：
```
round_0_baseline（原版快照）
├─ reference_product_v1（移植基准）── v2_recall / v3_system_slim / v4/v5/v6（v 系，单变量实验）
├─ v0_minimal_events（最小对照）── fact_ledger_v1/v2（结构化账本，LD1/FLJUDGE 输给扩窗，淘汰）
├─ evidence_memory_v1 / goal_action_v1（C2/C3 设计稿实现）
└─ r1→r2→r4_guard_e3(E3)→r7_supersede→r9_slim（行为合同+证据记忆引擎线，r10_compose=开关接口）
   window_expand_b（3600 扩窗）→ b2（+压制段，淘汰）→ norecap（删 recap）→ guard / uc / uc_supp / uc_guard
```
44 个候选的机制逐个说明见本窗口 2026-11-03 会话记录，或直接读 `candidates/<name>/IDENTITY.md`（每个 ≤15 行，权威）。

## 3. 数据集（benchmark/stories_v2/，208+ 条目）

- **184 篇 12 轮标准剧**（stage1/2/3 + wave1/2/3）+ **16 篇长剧**（wave3_ext，8×40+8×32 轮）+ **8 对反事实孪生**（E149-156）+ 早期集（stage4/stageD/final，已消耗）。
- 每篇：HER/ME 消息 + note + intent + 证据卡（应保留原子/禁断/unknowns）。
- **gold sidecar v2**（`benchmark/stories_v2/GOLD_SIDECAR/`，2026-11-03 建）：2919/3102 turn 有事实断言，quote 100% 逐字命中；schema 见 `GOLD_SCHEMA.md`。评分器 `arena/lb_scorer.py` v0.9.3 消费它（C14 取消族/C15 unknown 检查），demo 证明 fact_gold 层可实例化。
- **B 线盲测集**（`benchmark/b_line/`，v2）：24 篇全净（16 标准+4 长剧+4 CF），物理封存+hash，**ONLY_ONCE**——仅最终验收可开箱一次，开发迭代禁读。
- 保管账 `STORY_CUSTODY_LEDGER.json`：210 篇 SEALED/EXPOSED + 事件流。**2026-11-03 教训：批收口必须 append exposure 事件**（曾漏记导致 B 线 v1 污染，已补 54 条并重建）。

## 4. 评测基准现状（2026-11-03 修复后）

| 层 | 状态 | 用法 |
|---|---|---|
| 程序分（lb_scorer 层1） | ✅ 契约检查 + **gold 事实层（C14/C15）已接线** | 契约分与 fact_gold 分**分层读**，禁止合并成一个数 |
| 九维 judge | **已校准**（W20-03，2026-11-03）：full-qualified=goal/time/interact/emotion（init/fact 合格但低证据）；attrib=assistive-only；**memory/boundary=disqualified，判死必须 B3**；severe P=0.062/R=0.5 → 只作 B3 复核触发器，禁自动 veto；引文硬幻觉 11.9% → 评审管线必须接引文逐字硬校验（W20-06）；门禁读数用逐维分，**不用 winner 单值** | 按逐维资格用（`W20_03_JUDGE_CAL_REPORT_20261103.md`） |
| severe | **必须主会话 B3 亲读**（judge 曾有引文幻觉 2 例+双重标准） | 引文逐字回验 |
| 用户愿发（W 表） | 只有用户能填 | AI 不代投，不阻塞 |
| usage 口径 | usage=上游回报单位（≈0.73×字符），见 `USAGE_UNIT_NOTE.md` | 成本门只用同单位配对差 |

## 5. 挂起事项（用户 2026-11-03 令）

- 用户判定旧评测基准不合格，下令：**停止一切机制设计与候选实现，先修基准**。
- 已修：gold/评分器/B 线/judge 校准（进行中）。
- **挂起中**：`uc_guard` 跨域批判决（数据已采：464/464，程序 cand 8.11 vs par 7.56、prompt −0.56%；评审 JSON `LP_W19CROSS_NINEDIM_REVIEW.json` 已落盘，未判）、`uc_supp` 去一法消融（W19-07 ON_HOLD_USER）。
- 基准修复收尾后按 `WORKER_LOOP_PROMPT_20261103.md` 进入迭代循环。

## 6. 常见误读清单（每个都是真踩过的坑）

1. **程序分 ≠ 质量分**：8.11 vs 7.56 只说明契约与采样噪声；质量看分层分 + B3 亲读。
2. **账本可能漏记**：exposure 以"批次 DONE.json + 账本"双源核对；新批收口必 append。
3. **usage 不是字符**：成本门用配对差；绝对值标 usage-unit。
4. **后台完成通知不可信**：长批收口盯产物文件与账本，不信通知。
5. **冻结身份**：plan/frozen_bindings 里的 identity_sha256 与磁盘不一致时先查是谁改了候选，不得重 emit 掩盖。
6. **目标族未实例化 → 判决只能写"无退化"**，不能宣称改善（W18B 先例）。
7. **隔离路径评分**：`trusted_controller` 与 `lb_runner` 同口径（FK-SCORE-01 教训，修复在 `prior_hist_id_map`）。
8. **网关会死**：派发前探针 <model-port>；批中失败按 fail_fast 停批 → 事故登记 → 新 scope 整批重跑（no_makeup）。
9. **gold 空位要诚实**：`gold_empty` 与 unrated 不进分母，不伪造。
10. **负对照三篇**（neg_amnesia/dumper/service）是评分器的"差"端校准素材，不是候选。

## 7. 工具速查

| 用途 | 工具 |
|---|---|
| 批生成/live | `tools/run_max288_batch.py`（--mode precheck/live --workers 6） |
| 配对九维评审 | `tools/run_lp_review_generic.py`（自动建评审账本） |
| 离线重评 | `tools/rescore_batch.py`（batch-aware） |
| gold 生成 | `tools/build_gold_sidecar.py` |
| B3 亲读页 | `tools/show_turn.py` |
| 身份冻结 | `tools/freeze_identity.py` + plan frozen_bindings |
| 保管账 | `tools/story_custody.py`（seed/expose/status/verify） |
| 授权账本 | `arena/isolation/authorization_budget.py`（绝不手改） |
