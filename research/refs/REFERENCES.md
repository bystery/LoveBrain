# Guanjia Harness 参考资料与知识库索引 (REFERENCES.md)

> 本文件是 Guanjia 架构演进与自进化循环的「外脑参考库」。
> 执行 AI 在每轮迭代前，必须按需检索以下本地资产与网络前沿，严禁闭门造车。

---

## 一、本地核心资产仓库（仅作参考，严禁生搬硬套）

1. **AI 治理架构模板库**：`D:\桌面\工作信息\aiops-governance-kit`
   - 核心重点：
     - `总控.md`：会话生命周期、多层汇报协议、协调机制
     - `授权.md`：放权阶梯、凭证流转、最小特权原则
     - `角色.md`：Planner / Worker / Checker / Researcher 四大角色投影
     - `.aiops/docs/PROGRESS.md`：极度精简的进度看板范式
   - 借鉴点：如何用「纯 Markdown + 机械 hook」实现强确定性治理，不依赖复杂的外部守护进程。

2. **Guanjia 当前代码底座**：`D:\guanjia`
   - 核心模块：
     - `templates/guanjia/bin/guanjia-entry.mjs`：现有意图路由与"未定义"报错的病灶来源
     - `templates/guanjia/bin/guanjia.mjs`：单任务锁 state.json、上下文与资源 Governor
     - `templates/guanjia/modules/paperops/`：目前唯一的完整部门模块实现范式
     - `templates/guanjia/START.md`：极简入口（64行）
   - 批判点：必须彻底破除硬编码正则路由、单任务死锁、假性 Verified 映射漏洞。

3. **用户经验与总结**：`D:\桌面\管家参考信息\`
   - 核心重点：用户总结的管家痛点、三层能力缺失、配置脱节诊断。

4. **成熟自循环探索工程范式**：`D:\research_factory\LLM_test_time\test*`
   - `test1` 至 `test5` 的 `AGENTS.md`、`指令.md`、`decisions.md`、`logs/progress_*.md`
   - 借鉴点：
     - 阶段门控（Demo 验证通过才能铺全量）
     - 每次决策强制落盘 `logs/decisions.md`
     - 严格区分文件修改权限（执行 AI 不可改宪法）
     - 运行资源监控与用量统计

---

## 二、真实用户实战与调教对话录（灵感库）

以下为用户与各 Agent 脚手架的实战上下文及对话 ID（位于各宿主应用存储或本地记录中）：

- **Antigravity 平台核心对话**：
  - `9b98c18e-a8f5-4af0-9936-31d3cb74c4b0` — 《项目功能与使用咨询》
  - `d1f99f89-8be7-448d-9e95-806d38b68055` — 《Analyze Project Bottlenecks》（分析项目瓶颈）
  - `be02c422-69f1-4038-8c9b-9132c647c4ef` — 《Scientific Research Project Start》（科研项目启动）
- **Workbuddy 平台实战记录**：用户对多会话切换、小工调度、任务丢失的真实反馈。
- **ZCode / 编程脚手架实战记录**：代码生成过程中的脱节、覆盖、过度执行问题。

> **调优教训提炼**：
> - 用户发自然语言时极度厌恶被反问或提示"未定义命令"。
> - 用户需要看到清晰的「原始需求 -> 理解后需求 -> 交付标准」对照。
> - 不要让 AI 假装完成了，必须有机械证据。

---

## 三、前沿 Harness 与评测框架搜索关键词（外网调研库）

执行 AI 必须按调度要求（每 10-20 分钟），通过学术搜索引擎与开源社区主动检索以下领域的最前沿方案：

1. **Agent 评测与 Harness 前沿论文（2024–2026）**：
   - `AGENTIF: Evaluating Instruction Following in Agentic Scenarios` (NeurIPS/ICLR)
   - `TheAgentCompany: Benchmarking LLM Agents on Consequential Real-World Tasks` (NeurIPS 2025)
   - `AgentBench: Evaluating LLMs as Agents` (THUDM)
   - `Tau-Bench: A Benchmark for Tool-Agent-User Interaction in Real-World Domains`
   - `SWE-bench / SWE-bench Verified`: 代码与工程任务真实交付标准
   - `OSWorld / WorkArena`: 模拟真实操作系统与工作流调度的评测体系

2. **Agent 架构与状态机设计**：
   - Multi-agent orchestration without message loss
   - File-based state management for stateless LLM agents
   - Hierarchical task networks (HTN) in LLM task decomposition
   - Fail-soft and graceful degradation in autonomous agents

---

## 四、权威来源白名单与已验证外脑图谱（v3.2，2026-10-03 外查沉淀）

> 外脑门禁（AGENTS.md §3.2）的取信顺序：① 顶会/顶刊（ICLR / NeurIPS / ICML / ACL）② 官方开源仓库（高星、机构维护）③ 机构技术博客（DeepMind / Meta / Sierra / THUDM 等）④ 个人深度博客（仅作线索，结论须回溯①②③）。arXiv 未中会论文标注 [未 peer-review]。

### 1. Harness 自进化方法论（斗兽场的学术同族，每轮优先跟踪）

| 工作 | 出处 | 一句话 | 我们学什么 |
|---|---|---|---|
| Darwin Gödel Machine (DGM) | 2025, Sakana AI [未 peer-review] | 进化可自我修改的 scaffold 代码，SWE-bench Verified 20%→50% | 被进化对象 = harness 本身；candidates/+best/ = archive |
| Self-Harness | 2026 [未 peer-review] | "提出-评估-接纳"：已见+未见问题都不退步才保留 | = 我们 core+full 帕累托晋级，设计被独立验证 |
| ADAS (Meta Agent Search) | ICLR 2025, Hu/Lu/Clune (UBC) | agent 设计 = 图灵完备代码空间搜索 | 结构可搜索化的理论基石 |
| AFlow | ICLR 2025, MetaGPT 团队 | 工作流 = 代码图上的 MCTS；GPT-4o 4.55% 成本超 GPT-4o | 弱模型+好结构 > 强模型（对口"弱模型主控"痛点） |
| GEPA | 2025, DSPy 团队 | 读完整 trace 自然语言归因再演化 prompt | 失败归因方法论（我们的 decisions.md 同款） |
| A Survey of Self-Evolving Agents | arXiv 2506.16411 [综述] | What/When/How/Where to evolve 全景 | 每轮外查的首选锚点 |
| OPRO / PromptBreeder / TextGrad / AgentSquare / EvoFlow / MemEvolve / MaAS | ICLR'24 起 | prompt/结构/记忆进化族谱 | 变异算子灵感库 |
| MetaClaw / AgentX | 2026 | 真实用户反馈驱动自进化（用户驱动 vs 题库驱动） | Phase 3 之后：用户真实使用 = 终审信号 |

### 2. 能力维度 benchmark（维度对标与 Phase 2/3 抽检来源）

| Benchmark | 出处 | 对应我们的题 |
|---|---|---|
| τ-bench（Tool-Agent-User） | ICLR 2025, Sierra（arXiv 2406.12045，github sierra-research/tau-bench；后继 τ²/τ³-bench 2025-2026） | M01/M04；pass^k → Phase 2 一致性抽检 |
| LongMemEval | ICLR 2025, Wu et al. | T05/M09（abstention 拒答 → T02/M04） |
| LoCoMo | ACL 2024, Maharana et al. | M09 长文记忆外部参照 |
| LifelongAgentBench | 2025 | M07（经验迁移/skill reuse） |
| HaluMem | 2025 | 记忆幻觉三阶段（写入/更新/调用）→ 账本写入正确性参照 |
| Agent Memory L1–L6 分层 | hyacehila blog（线索级，须回溯原始论文） | 六维的外部参照系 |
| AGENTIF / TheAgentCompany / AgentBench / SWE-bench Verified / OSWorld / WorkArena | NeurIPS/ICLR 2024-2025 | §三已有索引，Phase 3 抽检候选 |

### 3. 外查纪律

1. 每轮 decisions.md ≥3 条外查记录（来源+链接+学到什么+吸收/否决及理由），不足 = 当轮作废（AGENTS.md §3.2）。
2. 吸收进题库/机制的每一条，必须在 LOCAL_ASSETS_MAP §六 或本表标注出处——变异必须溯源（AGENTS.md §2.1）。
3. 博客/二手解读只做线索，结论落盘前必须回溯到论文或官方 repo。

### 4. 失败分类学（v3.3 外查补充）

| 工作 | 出处 | 一句话 | 我们学什么 |
|---|---|---|---|
| MAST（Why Do Multi-Agent LLM Systems Fail?） | NeurIPS 2025 D&B Track, UC Berkeley（arXiv 2503.13657，github multi-agent-systems-failure-taxonomy/MAST） | 14 种失败模式 3 大类：specification / inter-agent misalignment / task verification；κ=0.88 标注一致性 | 失败归因的外部词表：specification→M11/M09（规矩漂移），misalignment→M06/M10（派发错位），verification→M03/T03（验收缺失）；Worker 写失败归因时按此三类归档 |
