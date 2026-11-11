<!-- historical worker document, sanitized; not current -->
# 当前交接（2026-11-11，RESEARCH-P1 第一大阶段——优先读）

**第一件事**：读第一大阶段执行宪章
`<arena-root>/refs/research_harness_20261110/phase1/RESEARCH_PRINCIPLES.md`

当前状态一句话：用户 2026-11-11 下发《外部研究驱动、Benchmark 与 Harness 并行、快速机制探索》总指导书（RESEARCH-P1，效力最高），并明确纠正：**此前的 H01–H15 仅为解释"机制差异"的非约束性例示，绝不许当研发清单或倒推来源；Worker 必须自主联网检索论文、自主提出机制上真正不同的 Harness**。G0 启动门已达成，三线并行：A 线（60 位 DEV 快速评估集）、B 线（第一轮外部文献研究 ≥12 篇/精读 ≥5 篇/≥8 机制候选）、C 线（统一输入/输出/计量接口）。G1 达标后立即开始 ≥3 个论文驱动机制的最小原型（本阶段不新增 HOLDOUT 实验）。三线交付位置：`refs/research_harness_20261110/phase1/{benchmark,literature,harness}/`；门槛核对表 `phase1/PHASE1_ACCEPTANCE.md`；状态板 `phase1/WORKSTREAM_STATUS.md`。权威状态在 TASK_QUEUE.json 的 W28 条目与 RESEARCH_STATE.json 的 research_p1 节。

**仍有效的旧规矩**：产品改动只在分支 `closeout-20261106`（等用户真机验收后合并 main）；`benchmark/b_line/` 物理封存禁止读取；HOLDOUT E424–E520 封存不动、E401–E423 已曝光不得当新题（`refs/benchmark_v1_20261107/phase3/HOLDOUT_EXPOSURE_MARK_20261111.json`）；API Key 只从环境变量 `LB_API_KEY` 读、仓库零落盘；旧 dsf@<model-port> 成绩不与 USTC deepseek-flash-2 混比。

---

# 历史交接（2026-11-07，版本收口模式）

**当时的第一件事**：读收口指导书
`<arena-root>/refs/version_closeout_20261106/CLOSEOUT_GUIDE_20261106.md`

当前状态一句话：用户 2026-11-06 解除产品接入暂停（原话"全面8路并发 开始版本收口！！！全面执行"），转入整体版本收口，**不再新开研究候选**。W23 收口包已全部闭账：第一批 8 轨（W23-01）＋第二批 8 轨（W23-02）＋外部审计修复批 R1-R8（W23-03）＋审计第三轮定向收尾批 F1-F4（W23-04，commit `e2ebde1`，CI #120 success 本地 gh 复核）全 DONE；产品线 HEAD=`e2ebde1`（分支 closeout-20261106），待用户真机验收后合并 main。2026-11-07 追加：W24-01 项目整理归档（根目录 184→29 条目，清单 archive/20261107_root_cleanup/MANIFEST.md）与 W24-02 论文式展示文档（根目录 PAPER.md + README.md）完成；用户明确**当前只是阶段性收尾，评估标准和数据集改好之后还要继续迭代**。外部审计四轮复核的 Harness 全景还原与数字核查报告：`refs/version_closeout_20261106/X_HARNESS_RESTORE_AND_AUDIT_VERIFICATION_20261107.md`。权威状态在 TASK_QUEUE.json 的 W23/W24 条目与 `refs/version_closeout_20261106/`。

**收口规矩（必须遵守）**：
1. 产品改动只在分支 `closeout-20261106`，不动 master/主干；不 commit（改动留在工作区，由主会话统一收口）。
2. `benchmark/b_line/` 保持物理封存，禁止读取；留给 S3 全量前最终盲评一次性消费。
3. 机制线冻结：不新增研究候选，不为清队列做实验；只合并 KEEP 判决机制（guard/uc/b_norecap 家族），机制合并走 W15-06 三阶段（S1 shadow→S2 单风格→S3 全量）。
4. gradle 隔离 worktree 纪律：构建/编译验证在隔离 worktree 中进行，不污染主仓工作区。
5. KNOWN_FAILURES F1–F6 为硬门；P2 项（Issue#3/#6）不入本版不阻塞发布。

**入口链**：WORKER_CURRENT_RUNBOOK.json（指针）→ 本文件 → 收口指导书 → TASK_QUEUE.json W23-01/W23-02。

以下为历史通用启动语（与上述收口模式冲突时，以收口指导书和本文件开头为准）。

# 可直接复制给执行worker的开启prompt

你是LoveBrain完整Harness长期迭代的执行主控。用户最新要求是全局、多轨、大工作量、持续循环，不是只修一个错误，不是只整理提交包，也不是重建考场。

工作根目录<arena-root>；原项目<LoveBrain-repo>只读作公平对照，暂不产品接入。固定基础模型dsf、<model-port>/v1、temperature=0.7；优化的是提示资产＋意图/备注＋记忆/知识＋上下文＋调用/消费，非权重训练。质量一级：事实/主体/时间/状态、知识正确使用、目标完成、自然主动互动、钩子/转场、调情/推进、深聊与反馈调整。费用、等待、可靠性、复杂度是二级共同考量，不能用省token冒充总体能力提升，不要求短回复。

立即读取并按序执行：

1. <arena-root>/AGENTS.md、SESSION_COORDINATION_V1.md、WORKER_CURRENT_RUNBOOK.json；登记真实心跳与范围认领，先核进程、未决attempt、代码差额和已完成证据。
2. <arena-root>/refs/experiment_protocol_20261023/global_harness_program_20261030/USER_REQUIREMENTS_TRACEABILITY.md。
3. 同目录MASTER_CONTINUOUS_HARNESS_PROGRAM.md、TASK_QUEUE.json、RESEARCH_STATE.json。
4. 按总书的有限入口继承原版范围、F3/B/C与已有工具；不要通读40份旧计划或再次全盘扫描。

必须继承：E3可运行研究原型保留；r5分层提示两轮未复现，不晋级。F3原版/r4四篇72对38/10/24是开发集证据，F1混合旧身份不归r4；B知识24次、C24+19次已完成不重跑；E3不为“补身份”全量重抽。现有runner/预算/绑定/归一器/compiler继续用。早晨260状态及“仅修确定化”旧单已被本全局任务书覆盖。

执行方式：首轮差额恢复≤2有效工程小时，然后同时推进数据评分、架构提示与机制、真实对照、独立复核。宿主支持子代理/外部实施会话时按文件所有权真实并行；不能用同一人自评冒充独立人类。至少保留公平原版、Bmin、≤3个实质候选；共同任务上比较和淘汰，失败不换版本名冒充升级。意图、知识、上下文、正向互动均为独立主线，不能被某个小修复堵住。

用户已有持续实验授权，按既有管理帽有效余额自行登记有限目的scope，不反复每48次问许可，不复活关闭单，不无限重试。hy4-preview硬禁；基座固定dsf，辅助允许hy3、dsf、qwen3.8-flash、gemini-3.8-flash，按代表性任务实际速度/资格选；每alias跨角色合计≤3。慢/空返路线退出关键路径，换评审须登记cohort而非静默混尺。慢裁判不冻结无依赖研发；没有真人W评分就未知，不代填，也不停止客观研究。

第一完整循环按总书目标在24–48有效工程小时内交至少两个实质候选的真实共同质量/错误/代价比较，给KEEP/REWORK/ELIMINATE并继续下一轮。144/288是规划工作量检查点，不得凑时长、盲花预算或声称会话结束仍在自主思考。

每次返回前更新机读状态、原始证据、当前候选、预算/未决、下一批和可恢复命令。结束一个工作包不是结束项目；按W16补足后续48–72有效工程小时任务，直到范围内验收或精确外部阻断。能继续的工作直接做，不停在“准备好/等你继续/测试全绿”。真实后台任务必须有进程、日志、检查点与完成回执；工具可用才配置已授权持续唤醒，不能虚构。后台Windows进程隐藏，不再弹黑框。

现在开始执行总书，不以再次输出一份计划作为本轮完成。
