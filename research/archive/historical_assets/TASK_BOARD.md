> [2026-11-02 废止标注] 本板 2026-10-27 后停更，其指向的 RUN_STATE.json 已废弃；
> 现行任务与状态唯一真源 = refs/experiment_protocol_20261023/global_harness_program_20261030/{TASK_QUEUE,RESEARCH_STATE}.json

# TASK_BOARD — max288_v2 接管周期

更新：2026-10-27 16:00 +0800。机器状态以 `RUN_STATE.json` 为准；本板只列任务与门禁。

## 1. 当前在做

| 工作流 | 状态 | 证据/入口 | 下一步 |
|---|---|---|---|
| P06 harness live（考生 dsf） | **DONE** | 三批 `logs/p06_{A,B,C}_live_20261027.DONE.json` 64/64、failed=0；账本 `AUTH-P06-MAX288V2-20261027-01` 192/192/0；链 14:59 正常退出 | 已完成，不重派 |
| P06 交付可靠性（裁判无关） | **DONE** | `evaluation/P06_DELIVERY_RELIABILITY_V1.md`；协议 veto/风格键写错族横跨父本与候选 | 作为下一轮第一优先修复输入 |
| 模型切换 | **DONE（登记）** | `evaluation/P06_JUDGE_INTERRUPTION_AND_MODEL_SWITCH.md`；dsf 上游 15:14 起 0 正文，用户改指 qwen3.8-flash | 后续候选轮次同基座重设基线 |
| P06 judge（qwen3.8-flash） | **BLOCKED→诊断中** | grant-1 dsf 作废保留；grant-2 `AUTH-P06-JUDGE-QWEN-20261027-02` 大 prompt 非流式 ~411s HTTP 502；正探“控制 vs 大节点”可行性 | 可行→降输出/流式跑完；不可行→登记阻断，转离线主线 |
| P02 Stage1 首批故事 | **DONE（阶段）** | `benchmark/stories_v2/stage1/STAGE1_CLOSURE.md`；8 条 E013–E020×8=64 位；验证 8/8/0 错；capture 128/128、P64/C64 | 扩到 64 自然故事/12 位 + 8 分支 + 8 长链（离线可做） |

同时活跃工作流上限 3。P07 闭环互动（用户新增要求）待有可用模型再实跑，先做离线设计。

## 2. 阶段板

| 阶段 | 状态 | 已交/缺什么 |
|---|---|---|
| P01 继承与基线 | **DONE** | `VERSION_LINEAGE_V2.json`、`BASELINE_SCOPE_V2.md`、`RUN_STATE.json`、`TASK_BOARD.md`；三类基线分清；C2/C3 parent 缺口登记 |
| P02 新故事 | **STAGE1 DONE / EXPAND NEXT** | 8 条×8=64 位已交并跑通；64 自然/12 位、8 分支、8 长链未开始（离线可做，是当前最不依赖模型的实交付） |
| P03 质量成绩 V2 | **DONE** | `evaluation/QUALITY_PANEL_SCORECARD_V2.md/.json`；117 首选/11 未评/769 备选槽；首选/备选分离；worker overlay；派生可用率；知识分母；身份范围 |
| P04 缺陷清除 | **PARTIAL** | 四项修复在代码/`.bak`；`FAULT_FAMILY_IMPLEMENTATION.json` 未生成；C3 主体串写等剩余根因需补正/负对照 |
| P05 两分支 | **DONE（实现/自测/capture）** | `candidates/v6_goal_context`、`candidates/v6_integrated`；`test_v6_candidates.py`、`test_v6_restore_e2e.py`；P06 capture 64/64×3 |
| P06 首轮真实对照 | 生成 **DONE**；语义判决 **BLOCKED** | A/B/C 三批 192 份 + 交付可靠性已交；九维语义判决卡在裁判模型（dsf 死、qwen 长 prompt 502） |
| P07 正向互动 + 闭环 | **PLANNED（离线先设计）** | 8 分支互动故事、4 反馈条件受控闭环；需可用模型实跑 |
| P08 泛化/反事实 | PLANNED | 依赖裁判与验证集 |
| P09 长期记忆链 | PLANNED | 依赖可用模型 |
| P10 消融/重构 | PLANNED | 依赖 P06 verdict |
| P11 成本/延迟 | 部分（交付可靠性已含 token/延迟） | 集中优化待质量线跑通 |
| P12 收口 | PLANNED | 最终报告 |

## 3. 不能重复/不能碰

- 不重跑 `AUTH-QUALITY-PANEL-20261027-01`（CLOSED 173/192；余 19 不是补跑额度）。
- 不复用/补跑 dsf 裁判 `AUTH-P06-JUDGE-20261027-01`（35 用、18 未评、2 传输失败，作废留证）。
- 不重跑 C4；不把 `6e65/ef8555/de600/009d` 旧答卷贴到 `501bc4/7c6b/fc296/fddcdb`。
- P06 live 已结束，但 **v6 候选身份在语义判决落定前仍冻结**（改了会作废已生成的 192 份对照）；
  下一轮修复请另建 v7/新身份，不在原地动 v6。
- 不把 capture/mock 零网络结果冒充 live 语义结果；不把 harness 授权用于 judge。
- 不因心跳老就重派；先读账本尾、PID、DONE、turn 目录。

## 4. P06 门禁（生成已过，裁判待恢复时同样适用）

裁判每批必须：计划 sha 现场重算；冻结输入散列全合；`ensure_batch` 整批预检；live 网关健康且非 0 正文；
输出目录不覆盖旧答卷；空正文=交付失败 fail-fast；失败消耗不退款、不补跑、不换 ID 伪装新研究。

## 5. 下一个具体动作

1. 看 qwen 可行性探针：控制样本与大节点分别能否产出合规 JSON。
   - 若大节点非流式必 502：改**裁判流式**（新 `api_cfg:…_stream_true` 身份、单独登记）或**主/备拆分**再跑；
   - 若整体不可用：登记精确阻断，保留 grant-2 未耗额度，转 §P02 离线扩量与 §P07 离线闭环设计。
2. 裁判恢复后：`run_p06_quality_judge.py --mode live --resume-outdir` 续评未评项，再 `analyze_p06_judge.py`。
3. P02 扩量：按 Stage1 同一 builder/validator schema 写下一批（64 自然/12 位、8 分支、8 长链），先冻结验证子集；
   `initial_kb` 只放标量。
4. P07：设计受控闭环（发送策略固定、对方模拟器冻结身份、成对同初始），待模型可用先小样每轨迹 4—6 决策。
