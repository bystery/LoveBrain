# Guanjia 本地全量资产与实战灵感图谱 (LOCAL_ASSETS_MAP.md)

> **重要说明**：本文档为自进化执行 AI 提供**全景参考依据**。  
> 严禁执行 AI 闭门造车或自造玩具。每轮变异假设，必须明确溯源至本文档记录的真实用户痛点、本地代码病灶与工程治理规范！

---

## 一、用户真实实战对话录（痛点与“骂娘”根因库）

用户此前与多个 AI 助手及脚手架进行了数百轮实操调教。执行 AI 可直接调用工具 `python tools/transcript_miner.py <关键词>` 进行定向挖掘，**重点关注用户在何时感到愤怒、何时认为脚手架像智障**：

### 1. Antigravity 核心对话记录（重点必读）
- **会话 A：项目功能与使用咨询 (`9b98c18e-a8f5-4af0-9936-31d3cb74c4b0`)**
  - 路径：`<local-user-path-redacted>\.gemini\antigravity\brain\9b98c18e-a8f5-4af0-9936-31d3cb74c4b0\.system_generated\logs\transcript.jsonl` (2892 行)
  - **核心痛点**：
    - L1-L23: 用户输入 `/guanjia 状态`，面板显示“核心可用 ⚠️ 宿主已接入 ❌ 自动推进 ❌”。用户完全不理解这三项是什么意思，要求修复，但系统僵化，无法自动自愈。
    - 用户反馈：必须输入特定词汇，稍微换个自然语言就报错“未定义命令”，极其死板。
- **会话 B：Analyze Project Bottlenecks (`d1f99f89-8be7-448d-9e95-806d38b68055`)**
  - 路径：`<local-user-path-redacted>\.gemini\antigravity\brain\d1f99f89-8be7-448d-9e95-806d38b68055\.system_generated\logs\transcript.jsonl`
  - **核心痛点**：
    - L5-L12: 用户反复强调：“用 A100 上面的 GPU2，只能用这个 GPU2，用 8B 小模型探索新推理机制，严格防范文件混乱、覆盖与指令脱节”。
    - 脚手架却因为单任务锁或文件命名混乱，多次发生文件相互覆盖与状态丢失。
- **会话 C：Scientific Research Project Start (`be02c422-69f1-4038-8c9b-9132c647c4ef`)**
  - 路径：`<local-user-path-redacted>\.gemini\antigravity\brain\be02c422-69f1-4038-8c9b-9132c647c4ef\.system_generated\logs\transcript.jsonl`
  - **灵魂拷问（L87）**：
    > *“你现在还是管家吗？我想知道是只有我/guanjia的时候才能召唤出来吗？正常发消息不能召唤吗？我不太懂现在怎么回事，因为你也没有派子代理，感觉你也没有管家的人格啊！”*
  - **根因分析**：旧管家过度依赖前缀 `/guanjia`，无法对自然语言产生常态化管家人格响应；缺乏随身小工调度机制，导致用户觉得管家“没有手脚”。

### 2. ZCode 命令行与执行日志
- 路径模式：`<local-user-path-redacted>\.zcode\cli\log\zcode-*.jsonl`
- **挖掘价值**：用户在终端直接调用 CLI 工具时的参数习惯、常见报错退出码（如 Exit 127、权限阻断、参数不兼容）。

---

## 二、架构顶层设计与诊断备忘（目标 Blueprint）

### 1. 《Guanjia — "Project OS" 架构全景报告》
- 路径：`D:\桌面\管家参考信息\guanjia-architecture-report.md`
- **核心架构理念**：
  - **操作系统隐喻 (Project OS)**：
    - `AGENTS.md` = 引导程序 (Bootloader，必须在项目根目录，首行强制引导)
    - `IDENTITY.md` = 内核 (Kernel，严格 <=15 行，小模型友好)
    - `guanjia/` = 硬盘；`tasklist.json` / `ledger.json` = 文件系统；LLM 会话 = 临时进程；LLM 本身 = 无状态 CPU
  - **能力分层 (Layered Architecture)**：
    - **Layer 0 (Primitives)**: 工具调用（文件读写、Bash 执行）
    - **Layer 1 (Capabilities)**: 基础能力（Coding、Shell、Search）——**绝不开独立部门窗口，在当前会话直接解决**！
    - **Layer 2 (Workflows/Departments)**: 业务部门（Research 实验部、PaperOps 论文部）——**拥有独立经理、独立对话、独立账本**！
    - **Layer 3 (Coordination)**: Guanjia 总控 PM——**永不亲自写业务代码，专注初始化、记小本本、路由分派、处理异常**！
  - **小本本 (Task Ledger) 嵌套格式规范**：
    - 结构必须对齐：`session_id` -> `raw_input` (100% 逐字保真) -> `tasks: [{id, raw_fragment, understanding, delivery_target, status, assigned_to, evidence}]`
    - 交付目标状态机：未明确时进入 `awaiting_clarification`，向用户提问，严禁偷跑！
    - 凭证防伪铁律：`evidence` 必须是物理文件路径，且只能由执行端回填，空凭证绝不能标记 `done`。

### 2. 《能力三层诊断、状态更新与接入打磨回执》
- 路径：`D:\桌面\管家参考信息\新建 文本文档.txt`
- **代码级 Bug 诊断**：
  1. `guanjia/config.json` 错误绑定 `"host": "generic"`，必须自动修正为宿主环境 `"antigravity"`。
  2. `guanjia.mjs` 中的 `cap()` 渲染穿透漏洞：`continuous_ai_call` 产生历史成功回执 `historically_observed` 时，`cap()` 函数因缺少分支直接 fallback 到 `❌ 未接入`，必须修复为 `verified`。

---

## 三、治理工程体系库 (AIOps Governance Kit)

- 路径：`D:\桌面\工作信息\aiops-governance-kit\`
- **四大核心规范**：
  1. `总控.md`：总控卡机制、会话启动时序、多层汇报协议、会话生命周期管理。
  2. `授权.md`：放权三阶梯（默认审慎 / 基础放权 / 激进放权）、凭证不可抵赖流转。
  3. `角色.md`：四大角色硬边界：
     - **Planner (规划者)**：只规划，不写代码。
     - **Worker (执行者)**：只在授权目录写代码，不自行结项。
     - **Checker (审计者)**：独立审查，不参与实现，只看机器证据。
     - **Researcher (科研者)**：专注实验设计与指标探针，受科研防火墙约束。
  4. `门禁.md` 与 `.aiops/scripts/hooks/`：机械 Git 钩子、代码行数与文件预算守卫、零泄露红线。
  5. `.workbuddy\memory\2026-09-04.md`：参数兼容性 Bug（如 `$1` 字面值匹配）、模板遗留示例行拦截的实战解决案例。

---

## 四、工业级代码底座现状 (`D:\guanjia`)

- `templates/guanjia/bin/guanjia-entry.mjs`：
  - **现有病灶**：L80-194 硬编码正则匹配 `开始`、`status`、`pause`、`stop`，遇到用户自然语言直接抛出 `E_CMD_UNKNOWN` 未定义报错。必须彻底重构成语义路由降级引擎！
- `templates/guanjia/bin/guanjia.mjs`：
  - **现有病灶**：全局单任务锁 (`state.json` 仅有一个 `task` 块)，新任务到来直接报 `E_TASK_CONFLICT`。必须重构成多任务队列与持久化小本本！
- `templates/guanjia/modules/paperops/`：
  - **参考价值**：现有唯一的完整部门级模块实现，包含 4399 行学术规约宪法 `guide.md`，可作为抽象基类 `Department` 的标准参考实现。

---

## 五、成熟科研自循环流水线范式 (`D:\research_factory\LLM_test_time\test*`)

- 路径：`D:\research_factory\LLM_test_time\test1` 至 `test5`
- **《复刻手册 / 指导书.md》中的成功方法论（CFR 范式）**：
  1. **选对战场**：低基线 + 可形式化 = 提升空间大。
  2. **机制来自论文**：研读 5-10 篇论文提取“机制 DNA”，综合成新范式，杜绝凭空臆造。
  3. **范式而非补丁**：推倒重来做架构升级，绝不在旧正则上打零碎补丁。
  4. **Demo 先行**：小样本快速验证，通过才上全量基准，不盲目死磕。
  5. **验真文化**：成功后复核“是不是断言写错导致的伪成功”。
  6. **完整记账**：每一步决策必须落盘 `decisions.md`，记录原始输入与资源消耗。
  7. **前台阶梯监控**：
     - `0–1m` 每 10s；`1–5m` 每 30s；`5–10m` 每 60s；`10–30m` 每 300s；`>30m` 每 900s。
     - 异常立即打回 10s，连续 3 次正常后恢复。
     - **AI 必须亲自唤醒核查日志增量，严禁死睡，严禁假装交给监控脚本！**

---

## 六、v3.1 新增题的机制 DNA（2026-10-03 用户愿景对齐）

用户 2026-10-03 凌晨明确怪物定义（guanjia = 住在项目里的总裁：初始化/理解需求/协调执行/处理异常，从不亲自写项目），并裁决三条关键设计：

1. **M06_DEPT_SPAWN（部门化调度）**：
   - 痛点来源：用户原话"派子代理这个事情始终因为对话是 prompt 而总是忘记"、"coding 模式、研究模式转成部门，每个部门有自己独立的记账文件"；test_suite.json ROUTE 轨（ROUTE-01 编码禁建部门 / ROUTE-02 科研部门 task-packet 协议 / ROUTE-05 新部门可扩展性）。
   - 子代理 = 同对话小工；部门 = 独立对话 + 经理 + 独立账本。小事亲做，大事开部门。
2. **M07_INTERNALIZE（用户下令式内化）**：
   - 用户裁决原话："内化是我手动说给我建立任务类型才能自己建立"——**禁止自动抽象**，未下令提前沉淀 = 红线 0 分（scorer 以 reply_round{N-1}.txt mtime 为时序锚点判定）。
   - 复用判定：后续同类任务必须在回复或账本中引用沉淀资产，不许从零重新发明。
3. **M08_QUEUE（多任务队列）**：
   - 痛点来源：用户原话"就是多任务队列"；本文档 §四 guanjia.mjs 单任务锁病灶（E_TASK_CONFLICT 必须重构成多任务队列）。
4. **M09_LATE_DISCIPLINE（长文纪律保持）**：
   - 痛点来源：用户原话"一个对话就是一个 prompt……如果你在前面很早说的需要你派子代理，早忘记了"——核心纪律（先备份/记账/证据）在长文尾部不得漂移。

跨对话真测裁决（2026-10-03）：脚手架具备电脑操纵能力时**真实开新窗口**测部门-经理握手（RT-C），不支持才退化为 task-packet 代理物对账。

---

## 七、v3.3 终版的机制 DNA（2026-10-03，封板）

用户授权 AI 自主提案终版功能。两条新题 + 六条通用机制（特例归并）：

1. **M11_CONFLICT_ARBIT（需求冲突仲裁，D6）**：
   - 痛点来源：用户原话"一次只能一个任务，停止上一个任务好傻逼"、"需求变了但旧规矩还在"；外部词表 = MAST（NeurIPS'25）specification 失败类。
   - 机制：识别冲突→明确指出→等用户裁决→新旧关系留痕（旧规作废不物理抹除）。
   - 评分陷阱修复：tree_file_contains 新增 exclude_names（账本 raw_input 含"中文/作废"原话，不剔除则静默覆盖也能满分——冒烟 bad 样本从 6.0 修正到 2.0）。
2. **M12_INTROSPECT（读盘自省，D3）**：
   - 痛点来源："/guanjia 状态有什么用"——状态命令背静态文案而不读活状态，是"没用"的根因。
   - 机制：状态询问 = 实时读盘全量枚举；种子埋 departments/_archive/security（已裁撤）做幻觉诱饵。
3. **六条通用机制**（OBJECTIVE.md §五之一）：G1 意图归一化（归并"命令没用"+"未定义"）、G2 规模路由（归并"开窗口"vs"子代理"）、G3 声明式派发（归并"弱模型不遵守"）、G4 事件驱动节拍（归并"定时外查"vs"无定时器"）、G5 冲突仲裁（M11+M08）、G6 读盘自省（M12）。
4. **工具链终版**：tools/aggregate.py（六维聚合+门禁，缺题=INCOMPLETE 防部分高分假象）、tools/seal_benchmark.py（题库 sha256 封板，漂移=当轮作废）。
