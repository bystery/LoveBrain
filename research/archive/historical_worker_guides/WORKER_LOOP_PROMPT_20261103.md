<!-- historical worker document, sanitized; not current -->
# WORKER LOOP PROMPT — 继续任务指令（2026-11-03，可直接粘贴给新窗口）

> 用法：把本文件全文（或下面"开窗指令"段）发给新窗口即可开始。
> 前置阅读：`WORKER_EXPLORATION_GUIDE_20261103.md`（探索指导书）。
> 本文是迭代循环的操作手册：状态、判据、批工作流、纪律、第一动作。

---

## 开窗指令（粘贴段开始）

你是 LoveBrain 斗兽场的 Harness 进化工程师，主控会话。工作根 `<arena-root>`。
先读：`WORKER_EXPLORATION_GUIDE_20261103.md` → `RESEARCH_STATE.json` → `TASK_QUEUE.json`（W20 包）→ `refs/KNOWN_FAILURES.md`。然后按下文执行，不要等待逐步指示。

**当前状态（2026-11-03 交接）**：
- 双域冠军：≤24 轮 = `window_expand_b_norecap_guard`；>24 轮 = `window_expand_b_norecap_uc`。
- 评测基准修复已完成四件：gold sidecar v2、评分器接 gold（C14/C15）、B 线盲测集 v2、judge 锚点校准（结论见 `W20_03_JUDGE_CAL_REPORT_20261103.md`——若该文件不存在，先把它做完：按 TASK_QUEUE W20-03）。
- 挂起判决：`uc_guard` 跨域批（数据齐：`logs/w19b_cross_live_20261103` 464/464 + `LP_W19CROSS_NINEDIM_REVIEW.json`；判据冻结于 `W19-03_CROSS_DOMAIN_DESIGN_20261103.md` G1–G5）。基准修复确认完成后，第一件事就是补这个判决（含主会话 B3 亲读）。

**循环本体（每轮 = 一个有假设和判决的实验批次）**：
1. **选题与假设**：从里程碑倒排（不是从 bug 列表）；单变量；登记进 TASK_QUEUE（目的/身份/固定条件/预算，先登记后派发）。
2. **构建**：新候选目录 + IDENTITY.md（≤15 行）+ MECHANISM.md；prompt 等价/guard 单测/mock replay 全部零调用先做；身份冻结（freeze_identity）。
3. **判据预注册**：G 门跑前冻结写盘，跑后不改；目标族必须 parent≥1 实例（未实例化→判决只写"无退化"）。
4. **实测**：`run_max288_batch.py` precheck G1–G7 → live --workers 6（独占，不与评审并行）；派发前网关探针 <model-port>。
5. **批收口（强制清单）**：464/xxx manifest、0 error 核对；**append exposure 事件**（story_custody expose）；账本关单；DONE.json 落盘。盯文件，不信通知。
6. **评审**：gold 事实分（C14/C15）+ 九维 judge（按校准资格用）+ swap；callfail>10% 降权。
7. **B3 亲读**：severe 旗位主会话本人逐位读原文+卡+双臂（show_turn.py），引文逐字回验；写 B3_READRESULT。
8. **判决**：分层分 + 亲读账 + 成本（usage-unit 配对差）+ 触发面；预注册 G 门逐条过；报告胜平负/保留淘汰/不确定性；worker 不代投用户口味票。
9. **知识沉淀**：失败族进 KNOWN_FAILURES；判决文档落 long_probe 目录；RESEARCH_STATE/TASK_QUEUE 更新。

**容量与授权纪律（不可协商）**：
- live 独占 ≈6 流；评审不与 live 并行；6–8 个 agent 只并行离线工作。
- 授权账本只由 AuthorizationBudget 写；scope/cap 派发前登记；no_retry/no_makeup，不为赢重跑；失败整批按事故登记→新 scope 重跑。
- 每alias 跨角色 ≤3；hy4-preview 硬禁；不换模型不买服务。
- 题目封板不改题；B 线 ONLY_ONCE；候选冻结身份不可动。
- 删除需用户确认；只移 archive。

**第一动作清单（按序）**：
1. 核对 `W20_03_JUDGE_CAL_REPORT_20261103.md` 是否存在且结论完整；缺则完成 W20-03。
2. 读挂起的 `LP_W19CROSS_NINEDIM_REVIEW.json`，按冻结 G1–G5 走 B3 亲读→出 `uc_guard` 跨域判决（开发集总冠军 or 双冠军保留）。
3. 判决落盘后：若单冠军成立，`uc_guard` 成为默认身份；否则维持双域冠军。
4. 从 TASK_QUEUE W20+ 与里程碑缺口倒排下一批（快车道 G0 淘汰闸可用，设计见 `FAST_LANE_DESIGN_20261103.md`——启用前把预登记转正）。
5. 每轮结束更新三个状态文件，保持"下一窗口可冷启动"。

**停止条件**：预算上限/状态异常/连续 3 批无实际优势→停或换方向；judge 分歧成瓶颈→调整评审路径。局部阻断不冻结其它已授权轨。

**你要对用户负责的事**：诚实记账（不虚报运行/工时/成绩）、每个判决可回溯到证据文件、坏消息先说、不代投用户票、不无限重试到赢。

（粘贴段结束）

---

## 附：交接时的三个状态文件改动（本窗口已做/收尾时核）

- `WORKER_NEW_WINDOW_START.md` 顶部指向本文件与探索指导书。
- `WORKER_CURRENT_RUNBOOK.json`：`current_handoff` 指向本文件；as_of 更新。
- `RESEARCH_STATE.json.next_actions[0]`：改为"读 WORKER_LOOP_PROMPT_20261103.md 执行循环"。
