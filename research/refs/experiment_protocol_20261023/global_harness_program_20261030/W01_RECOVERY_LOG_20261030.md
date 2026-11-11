# W01 恢复日志（2026-10-30）

- 会话：`main-zcode-20261030-0052`（role=main，心跳 00:52:59+08:00 登记，认领 TASK_QUEUE/RESEARCH_STATE W01 状态段）
- 授权：`AUTH-W01-RECOVERY-20261030-01`（零模型调用 scope，已 scope+closed 落盘）
- 依据：`WORKER_NEW_WINDOW_START.md` → `MASTER_CONTINUOUS_HARNESS_PROGRAM.md` §1/§5-W01；整包上限 2 有效工程小时，实际 ≈0.6
- 六个验收出口全部落盘；本文件同时是本轮 handoff（§8 断窗恢复）。

## 1. RESUME_STATE（W01-01：在跑/已完/未知可定位）

**在跑：无。**
- 协调层：最后心跳 `global-harness-program-20261030` 00:35:55 已 status=done；claims.jsonl 无 active 认领。
- 文件层：key 目录（candidates/arena/tools/benchmark/program目录/logs）近 45 分钟新写入仅本恢复轮自身产物； planning 包文件时间为 00:21–00:35。
- 进程层：无 python 批处理在跑（仅宿主 python.exe 16928 / pythonw.exe 49396 常驻小进程与 ZCode 自身 node）；**dsf 网关未监听**（curl 127.0.0.1:17770/v1/models → exit 7 / http 000）。
- attempt 层：79 本账本全部 attempt 均有终态（见 §6 BUDGET_STATE）；INDEPENDENT_CHECKS 登记的 4 个 orphan（F1×3 judge、F3-NEWMAT×1）在已关账本内，未知→不重发。
- 旧噪声：顶层 130 个 `*_acl_locked_*` 快照目录、`_archive_v1/`、`backups/` 按 final_mainline_review manifest §F 规则不读不判不删。

**已完（直接签收，不重跑）：** E3 冻结（hash 17fd2160…，replay 71/72 等价+1 机械修复）；guard_final（9b8f4e…）；r5_tier NOT-CONFIRMED 不晋级；F3 四篇 72 对 38/10/24（九维 delta 全正）；B 24 次（on 11/12 vs off 0/12，合并槽读法边界保留）；C 24+19 次（分层未复现）；P06 192/192；Stage2 16 条 192 位；F0–F6 判决 REWORK；260/144 绑定核验通过；direction_check 三账本（67 生成）关单。
**未知可定位：** dsf 网关服务启动方式（见 §5 RUN_CONFIG 缺件——派发前置）；12 对人工愿发未回收（W=unknown，只有用户能填）。
**旧 RUNNING/已关单：** "仅修确定化"单问题入口已被本全局任务书覆盖；direction_check 00:14 三账本关单属实。

## 2. INHERITED_EVIDENCE（W01-02：hash 直签，历史不改名）

| 项 | 身份/证据 | 核验来源 |
|---|---|---|
| E3 | `candidates/action_policy_r4_guard_e3/`，sha256 17fd2160…（68 files，matches_frozen=true） | submission_review_20261029_1510/INDEPENDENT_CHECKS.json identities |
| r5 | `candidates/action_policy_r5_tier/`（delta=只加分层段），r5_disposition=NOT_CONFIRMED_DO_NOT_PROMOTE | direction_check_20261030_0008/DELTA_CHECKS.json |
| F3 | r4 vs 原版同批：4 篇（N01/N02/K01/K02）72 对 38胜/10负/24平；九维配对 delta 全正（goal +0.528、initiative +0.671、memory_use +0.453…）；绑定 144/144 通过；作者看过候选答卷=DEV 暴露 | INDEPENDENT_CHECKS.f3 + final_mainline_review manifest §I（✅行） |
| B | 24 次真实调用（6 探针×on/off×2 重复），worker 合并槽读法 on 11/12 vs off 0/12（**不是**首选记忆准确率/全库率）；24 份网关实际请求与预注册逐字一致；账本 24/24 关单 | DELTA_CHECKS.json knowledge_prompt_binding + knowledge_worker_grade |
| C | 24+19 次（1 fail_fast），assertion_tiering 两抽样未复现 NOT-CONFIRMED；C1 170,766 / C2 132,830 tokens | DELTA_CHECKS + F_C_CONTRAST2_GRADES |
| F1 | 127 对多身份混合复审，**不归 r4** | INDEPENDENT_CHECKS.historical_127_is_r4_vs_original=false |
| W | 12 对愿发样本未回收，W=unknown；AI 不代投 | manifest §H |

历史不改名为当前：E3/r5/guard_final 三身份名字与哈希原样保留；本轮未改任何 candidates/ 冻结件。

## 3. MEASUREMENT_DELTA（W01-03：只改矛盾，不重建量尺）

**继承的量尺合同（继续用，不重建）：** `benchmark/rubric.md` v0.5 + `QUALITY_MANUAL_V1_1`（major⇒维度压制）+ `EVALUATION_MASTER_DRAFT_20261025.md`；九维 0–3 主表（NR/NA/UNCERTAIN 与 0 分开）、T/P/C/S/W 五层、知识六段、首选/备选分审、故事为独立统计单位。

**已登记、待 W04 处置的实证冲突/缺口（errata 原件留存，裁决另版）：**
1. `E-MAT-B`（K02 素材日期冲突）、`RULER-C-01`（卡面把未敲定减两千当已生效）——已登记未生效，改尺=离线重评+版本化。
2. B3 亲读登记卡修订项：E006 跨轮回扣分野（九维口径未捕捉）、首选槽触顶偏置（待查）、C3 过早邀约护栏（整合候选护栏输入）、m 维隐式确认盲区；他代词性别 minor 漏检已由 V1_1 覆盖。
3. INDEPENDENT_CHECKS cautions：F3 控制对 8 独立非 16；F3 严重错误 8（候选）vs 5（原版），"无退化"未确立；prompt 更多事实≠长期召回。
4. 未测清单（进 W04/W05/W08/W13）：转场/反撩/深聊/收尾独立验收分母；Capture/Fidelity/Update 全链（B 只是 6 探针窄证据）；未见故事/反事实泛化；W 愿发。

**结论：量尺矛盾已全部登记在案，本轮不新增也不修改量尺。**

## 4. REUSE_MAP（W01-04：拟新增工具先查此表）

| 能力 | 现成入口 |
|---|---|
| 考场运行时 | `arena/lb_runner.py`（装配→适配器→落盘→评分→manifest）；机制层经候选 mechanism/ 接管 |
| 装配器（对拍基准字节一致） | `arena/lb_assembly.py` |
| 唯一计分器 | `arena/lb_scorer.py`（rubric 层 1） |
| 网关/强制记账 | `arena/lb_gateway.py`（calls.json 落盘） |
| 流式计时 | `arena/lb_stream_timing.py`（W14-01 复用） |
| API 边界 | `arena/api_candidate.py`（验收点 tools/test_api_boundary.py） |
| 数据/出题 | `benchmark/stories_v2/`（stage1/stage2/stageD/anchors_20261028/final_20261029；31 篇 350 位=文件计数）+ 现有 schema/compiler/card 结构，不另写格式 |
| 绑定 | `PANEL_IDENTITY_V1.json`（128 节点身份图） |
| 评分分析 | `tools/analyze_quality_panel_v1.py`（V1_1）、`tools/build_quality_scorecard_v2.py` |
| 批量入口 | `tools/run_max288_batch.py`、`tools/run_p07_pilot.py`（v2 含归一器 B6）、`tools/probe_partner_sim.py` |
| 身份核验 | `tools/check_identity_drift.py` |
| 状态自检 | `tools/state_map_check.py` + `STATE_MAP_20261025.md`（历史权威链） |
| 授权/预算 | `logs/auth_budget/*.jsonl` 79 账本 + `tools/build_*_authorization.py`（内含 forbidden_models 硬禁） |
| 复现包 | `submission_review_20261029_1510/LoveBrain_research_submission_20261029.zip`（冻结 E3；报告截止 15:24，提交须附最新 B/C 与一页状态） |
| 判决/收口 | `final_mainline_review_20261029/`（F6 REV2 + B/C REV3 + STATE_AND_ERRATA）+ `direction_check_20261030_0008/DELTA_CHECKS.json` |

## 5. RUN_CONFIG（W01-05：禁 hy4、未知配置零派发）

- 基座固定：`http://127.0.0.1:17770/v1`，model `dsf`，temperature 0.7；同批比较端点/model/采样固定。
- 辅助允许 alias：`hy3`、`dsf`、`qwen3.8-flash`、`gemini-3.8-flash`（按实际任务速度/资格选；慢/空返退出关键路径）；**每 alias 跨角色合计 ≤3**。
- `hy4-preview` 硬禁——已验证：arena/ 与 candidates/ 零命中；tools/ 10 个命中全部是 `forbidden_models` 禁用条款或 .bak（如 build_kb_probe_authorization.py L80）。
- **缺件（派发前置）**：dsf 网关当前未监听 17770（本轮实测 connection refused）。恢复派发前须先按既有方式启动网关服务并复探通过；在此之前所有生成/评审批保持 BLOCKED，离线轨继续。
- 输出契约：沿用各候选 params.json 既有 output_contract，运行中不改合同。

## 6. BUDGET_STATE（W01-06：不把总帽减错范围，不复活关闭单）

- 79 本 canonical 账本全量只读聚合：**started 合计 2726**（与总书规划轮盘点一致，未采信"精确剩 13658"）。
- 关单 36 本；**开放 43 本**——全部 started 均有终态（在飞=0），开放=缺关单事件而非缺终态。名义未用预留 ≈587：G11-STANDIN×7（各 cap12 用 7）、MATRIX-keyed×16（cap2 用 1）、HARNESS-PERF-PHASE1（cap256 用 4）、P07-PILOT-01（48/8）、P07-PILOT-02（96/24）、P07-V2（96/86）、**P07-W09（cap304 用 142，含 batch_rejected）**、P08×7 与 TEST-CAPTURE×4（cap 恰好用满）。
- 处置：本轮**不代关任何旧账本**（关单事件属批次所有者流程）；开放账本=可接管预占池，新 scope 登记时按"先结清开放预留、不得双重登记"处理。
- 管理帽 16,384 不变；TASK_QUEUE wave_attempt_menu_caps（A1536/B2048/C3072/D3072/E1536）是菜单上限不是支出目标。
- 本轮第一有限 scope：`AUTH-W01-RECOVERY-20261030-01`（cap 0，零调用，已 closed）。

## 7. Handoff：下一可执行动作（§8 调度序）

- **E 轨（立即可做，零调用）**：W03-01 DATASET_CENSUS（stories_v2 故事/位点/独立性/实跑分列）；W04-01 RUBRIC_ANCHORS。
- **H 轨（离线）**：W09-01 ARCHITECTURE_MAP；W06-01 意图合同差额场景。
- **X 轨**：W02-01 BASELINE_SCOPE（复用 BASELINE_PARITY）；W10-01 预登记草案（依赖未齐，先做文件框架）。
- **R 轨**：对 F3 严重错误 8 条做独立抽查资格核读。
- **派发前置（外部缺件）**：启动 dsf 网关并复探 17770 通过后，才能登记 A 波次生成 scope。
- 恢复入口：RESEARCH_STATE.json（已更新本轮字段）+ 本文件 + heartbeat.jsonl。
