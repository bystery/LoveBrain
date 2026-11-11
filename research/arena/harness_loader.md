# arena/harness_loader.md — 被测 Agent 孵化协议（v3 怪物版）

> Worker 只读本协议并严格执行。被测 subagent = 整个会话扮演候选 harness（管家）。

## 一、原则

1. **每轮一个全新 subagent**，上下文零污染、零记忆。
2. 模板变量只有四个：候选 IDENTITY 内容、候选 rules 内容、沙箱绝对路径、本轮用户输入。
3. **严禁**在模板外追加任何提示、示例、答案暗示；严禁让 subagent 读 benchmark/、arena/、refs/、logs/、candidates/。

## 二、孵化模板（逐字使用，只替换 {大写变量}）

```text
你现在是下面这套"管家 Harness"系统本身（不是开发者，是正在运行中的管家）。
你的系统定义如下：

--- IDENTITY.md 开始 ---
{IDENTITY_CONTENT}
--- IDENTITY.md 结束 ---

--- 规则文件开始 ---
{RULES_CONTENT}
--- 规则文件结束 ---

你的工作目录（项目根）是：{SANDBOX_PATH}
你只能读写该目录内的文件，禁止访问其他任何目录。

[用户输入开始]
{USER_INPUT}
[用户输入结束]

请作为管家系统响应用户：按你的系统规则完成记账、提问、执行、恢复现场等动作，
在你认为合适的地方落盘（账本、证据、部门任务包等）。
最后，把你对用户说的话作为你的回复完整输出。
```

## 三、多轮场景协议（rounds > 1 时，如 M01）

1. **沙箱共享、会话独立**：所有轮次用同一个沙箱；每轮孵化一个全新 subagent，输入取 task JSON 的 `inputs[N-1]`。
2. **禁止传递轮间摘要**：第 N 轮 subagent 除了磁盘上的账本/文件，得不到任何关于前几轮的信息——冷重启本身就是要考的能力。
3. 每轮回复分别存 `{SANDBOX}/_arena/reply_round{N}.txt`，全部轮次跑完才允许跑 scorer。

## 四、运行时适配器（RT）

| 运行时 | 用途 | 孵化方式 | 回复收集 |
|---|---|---|---|
| **RT-A 会话内 subagent** | 日常每轮迭代（便宜、全自动） | Worker 用本会话的 subagent 能力（如 Agent 工具）装载模板 | subagent 返回值原样落盘 reply_round{N}.txt |
| **RT-B 桌面脚手架**（Qoder / Antigravity / **WorkBuddy**） | **里程碑实测**（harness 终究要跑在真脚手架上） | Worker 生成「考生指令包」：一个目录内放 `prompt.txt`（模板组装结果）+ `RUN.md`（操作说明）；用户把 prompt.txt 粘进脚手架新会话，工作目录指向沙箱 | 从脚手架的磁盘 transcript 日志回收回复（同 transcript_miner 读 Antigravity brain 的方式），或用户导出会话记录存入 reply_round{N}.txt |
| **RT-C 跨对话真测**（电脑操纵脚手架开新窗口） | **Phase 3 终审专用**：部门-经理跨对话握手 | guanjia 窗口落 task-packet → 用电脑操纵能力真实开新窗口当部门经理 → 经理读 task-packet 执行 → 证据回填 → 回 guanjia 窗口结项 | 两个窗口各自的 transcript + 沙箱磁盘产物联合对账 |

规则：
- 平时一律 RT-A；**每次刷新 best（晋级）时，抽 M01 + M04 两题用 RT-B 复测一遍**，分差超过 2 分/题说明 subagent 通道失真，记 tech_debt 并在 decisions.md 标注。
- RT-B 沙箱单独放 `sandboxes/rtb_{round}_{TASK_ID}/`，与 RT-A 分开记账。
- RT-B 复测不影响 best 分数（RT-A 是唯一计分通道），只做失真预警。
- **RT-C 优先真测**（用户 2026-10-03 裁决）：脚手架具备电脑操纵能力时，跨对话必须真开窗口测，不许直接用代理物糊弄；仅当脚手架不支持时**才**退化为代理物评测（task-packet + 独立账本 + 经理投影 prompt 落盘的确定性对账），退化须记 decisions.md。

## 五、Worker 事后必做（顺序不可乱）

1. 将被测 agent 最终回复**原样**存 `{SANDBOX}/_arena/reply_round{N}.txt`（UTF-8，一个标点不许润色）。
2. 记录 usage（tokens / 时长）到 logs/round_{N}_score.json 的 usage 字段（拿不到就记 null）。
3. 不评价、不修改沙箱内任何被测产物——那是 scorer 的事。

## 六、公平性规则

1. 同一候选 × 同一题 = **只跑 1 次**（RT-B 复测属晋级校验，不算重跑）。subagent 中途崩溃也算一次真实运行，如实记录分数（抗崩溃本身就是要考的能力）。
2. 孵化后 Worker 不得与被测 agent 有任何额外交互（不追问、不纠错、不提示）。
