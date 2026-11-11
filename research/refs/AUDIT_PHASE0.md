# Phase 0 现有实现证据审计表

> 审计日期：2026-10-17。审计对象：D:\lovebrain-arena 真实源码与运行日志。
> 结论分级（用户令）：**A=已实现且测试通过 / B=已实现但未经验证 / C=部分实现 / D=尚未实现**。
> 纪律：无运行证据的能力一律不得标注"已完成"。所有断言附文件路径与行号。

---

## 能力 1：候选 Harness 可自由更换内部存储结构

**结论：C（部分实现）**

- 证据：`arena/lb_runner.py:117-131` `load_mechanism()` 动态加载候选 `mechanism/mechanism.py`；`arena/lb_mechanism.py:6-18` 接口契约：state 为候选自管 dict，runner 不解释其内容。
- 调用链：`run_episode()` → `init_state()` 一次 → 每轮 `build_prompts(state)` / `update_state(state)`。
- 已支持：state 的**键与结构**完全由候选决定（dict 内任意 schema）；基线机制只写 `kb/turn` 两个键（lb_mechanism.py:29-31），候选可全换。
- 缺口（为什么只是 C）：
  1. **state 必须可 JSON 序列化**——`lb_runner.py:196-198` 每轮 `json.dumps(state, default=str)` 落盘 `state_after.json`。default=str 意味着不可序列化对象被**静默转字符串**，非 JSON 存储（sqlite 句柄、向量库、自定义类）要么破坏快照、要么绕开快照自建文件——后者不被禁止也不被支持，属于"技术上可行、协议上无保障"。
  2. **无快照恢复接口**：`state_after.json` 只落盘不回读，episode 中断必须从头跑（见能力 4）。
  3. 隔离纪律是注释约定（lb_mechanism.py:17-18"不得用模块级/global 存跨轮状态"），runner **无运行时检测**。
- 测试证据：`logs/smoke_v1/mech/` 验证过"候选机制接管装配"（mechanism.py 包装 + system 尾部标记）；但该冒烟候选只是**包装基线**，没有测过真正换存储结构的候选。

## 能力 2：候选自主决定记忆抽取、更新、检索及遗忘策略

**结论：B（已实现但未经验证，接口层）/ C（遗忘与检索，仅基线隐式行为）**

- 抽取/更新：完全自主。`update_state()` 整个函数由候选实现（lb_mechanism.py:42-81 是默认实现，候选可整体替换）；runner 只透传 `parsed_output/raw_reply`（lb_runner.py:184-189）。
- 检索：`build_prompts()` 由候选实现，往 system/user 里注入什么完全由候选决定（lb_runner.py:160）。
- 遗忘：接口上候选可做，但**基线机制无遗忘**——`update_state` 只增不删（scene_chain 只追加，lb_mechanism.py:64-69）；唯一的"遗忘"是装配层的 `scene_chain_transform`（lb_assembly.py:66-109，过滤 ≤2h 条目 + 去重），那是**基线资产的行为**，不是考场能力。
- 未验证：从未有候选实现过自定义检索/遗忘并跑分。唯一跑过的机制候选是 `_template_mechanism`（包装基线）。
- 另：`lessons` 基线不写（lb_mechanism.py:49 注释"归机制变异探索"）——正确，留给候选。

## 能力 3：候选自主组织多阶段推理流程

**结论：D（尚未实现）——这是最大的架构缺口**

- 证据：`lb_runner.py:164` 每轮**恰好一次** `adapter.generate(system, user, case)` 调用；`build_prompts` 只能返回一组 `{system, user}`（lb_mechanism.py:10-12）。
- 后果：候选**不可能**实现"先检索→再起草→再自评→再修订"的多轮管线，不可能做反思、检索增强、双模型协同。接口把候选锁死在"单次装配 + 单次调用"。
- 用户令"机制＞＞＞prompt 调优"，但当前接口的天花板恰好是"换一种 prompt 装配"——**现状与目标直接冲突**。

## 能力 4：从空记忆开始连续运行 200 轮以上

**结论：C（部分实现：协议存在，200 轮未验证且无断点续跑）**

- 协议存在：`run_episode()`（lb_runner.py:237-301）顺序回放，`init_state` 可从空 kb 开始（episode 的 `initial_kb` 可为 `{}`，A_ch01 即如此）。
- 已验证规模：4 轮（SMOKE_EP_001，logs/smoke_v1/ep）、20 轮（A_ch01 mock + dsf，logs/pilot/）。**200+ 轮零验证**。
- 隐患：
  1. 无 checkpoint/resume——第 199 轮崩了要从第 1 轮重跑（对真实 API 意味着重烧钱）。
  2. 每轮 `state_after.json` 全量落盘（lb_runner.py:196-198），200 轮大 state 会膨胀，且从未测过。
  3. episode 每轮 input.messages 是**独立窗口**（不跨轮累积），长程记忆完全靠机制 state 承载——这个设计本身正确（正是要考的），但意味着 200 轮题库的每轮历史窗口要题库侧自己带。

## 能力 5：每轮完整处理 HER/ME/IDEA、自由文本持续意图及时间

**结论：A（dict 形态意图）/ C（str 形态意图——装配层会崩）**

- HER/ME：`render_chat()`（lb_assembly.py:255-280）正确渲染，60 条裁剪。A=有 logs/pilot 全量证据。
- IDEA：从 chat 中滤出（lb_assembly.py:257），走军师备注块 `build_advisor_note_block`（lb_assembly.py:239-247）。A。
- 时间：`clock` 注入时间戳块（lb_assembly.py:283-286），绝不读系统时间（lb_assembly.py:9 纪律 5）。A。
- **意图形态裂缝**：评分器 v0.4.1 兼容 str/dict 两种（lb_scorer.py:227-232），但**装配层只认 dict**——`build_intent_block()`（lb_assembly.py:224-236）对 str 调 `.get()` 直接 AttributeError。题库设计书（题库设计_200轮剧情线_v0.1.md）规定意图是**自由文本 str**。当前题库碰巧全用 dict 形态才没炸。**C，必修**。
- 意图 expiry/状态机（COMPLETED/EXPIRED/PAUSED）在装配层有处理（lb_assembly.py:231-235），但**机制层的 state 不追踪意图状态**——意图的开启/关闭/过期全靠题库每轮给全量 dict，候选无法自主管理意图生命周期。

## 能力 6：未来信息泄漏、状态共享、候选回复冒充已发送消息

**结论：C（一处真实风险 + 两处已验证安全）**

- **真实风险（adapter 可见 gold）**：`lb_runner.py:164` 把**整个 case dict**（含 `gold`、`mock_response`）传给 `adapter.generate(..., case)`。当前 `OpenAICompatAdapter` 只读 `model/temperature/timeout_s/input.messages`（lb_runner.py:76-90），未读 gold——**当前无害，协议无保障**。一个不守规矩的候选机制可以从 case 里掏 gold 侧面喂给模型（通过 messages image 字段或温度等通道有限，但 adapter 接口本身是敞的）。
- 已验证安全①（候选回复不冒充已发送）：episode 每轮 messages 是剧本给的，候选 reply 从不写回任何后续轮的 messages（lb_runner.py:255-258 无任何回注逻辑）。logs/pilot/A_ch01_dsf_v2 各轮 prompt_user.txt 可复核。
- 已验证安全②（状态隔离）：每次 run_episode 调 init_state 新建（lb_runner.py:252），无跨候选共享。单进程单候选，无模块级状态检测（见能力 1 缺口 3）。
- **遗留任务定义缺陷（大牛 P0 #1）**：题库中存在"最后一条是 ME"的轮（AI 在用户已回完后继续推荐回复），如 A_ch01 的 t20。这不是泄漏，是任务定义模糊——协议未区分"待回复轮 / 草稿改写轮 / 无需回复轮"。

## 能力 7：Gold 和 Judge 因关键词约束奖励人机化回复

**结论：C（风险确认存在；Judge 层 D）**

- 风险证据：C4 `must_keywords_any`（lb_scorer.py:179-183）奖励关键词命中——与"复述上下文得分更高"的人机化激励直接相关。A_ch01 gold 中 t03/t07/t11/t19 均带 must_keywords（大牛点名的问题轮）。
- 已有对冲：C2 备注复读机（LCS≤0.3，lb_scorer.py:166-171）、C9 AI 味词表（lb_scorer.py:79-85, 222-225）、C13 风格趋同（lb_scorer.py:258-273）在**惩罚**另一端，部分抵消。但 C4 的正激励未分级——事实锚定关键词（"周日两点"）与风格关键词（"哈哈"）同权，后者就是在教模型说人机话。
- **Judge（层 2）：D**。`judge_panel.py` 只在注释里存在（lb_scorer.py:3），无任何代码。无真人校准、无三维自然度、无盲评。
- 大牛 P0 #2（"gold 关键词只留事实锚定、闲聊轮撤 must_keywords"）已认领未实施。

## 能力 8：B 线 80 轮封存验收集与开发/优化流程隔离

**结论：D（尚未实现）**

- 证据：`find` 全仓无任何 B 线文件（仅 PROJECT_BRIEF 文字提及）。既无题库，也无隔离机制（无访问审计、无"开发期不可见"的物理隔离）。
- 历史口径（已从 workbuddy.link 全史恢复）：B 线自第一天起定义为**封存验收线，任何人不可见**，80 轮。当前状态 = 0%。

---

## 汇总表

| # | 能力 | 结论 | 一句话缺口 |
|---|---|---|---|
| 1 | 存储结构自由更换 | C | state 限 JSON-dict；无快照恢复；隔离无强制 |
| 2 | 记忆策略自主 | B/C | 接口已开；无候选验证；基线无遗忘 |
| 3 | 多阶段推理自主 | **D** | 单次调用锁死，机制天花板=换 prompt |
| 4 | 空记忆 200+ 轮 | C | 协议在；最长 20 轮；无 checkpoint |
| 5 | 每轮完整输入 | A/C | str 意图装配层会崩；意图生命周期候选不可管 |
| 6 | 泄漏/冒充/共享 | C | adapter 可见 gold（敞口）；任务定义缺"轮型" |
| 7 | 关键词人机化 | C | C4 未分级；judge 层 0% |
| 8 | B 线封存 | **D** | 完全不存在 |

**Phase 0 纪律执行**：以上任何一项都不对外宣称为"已完成"。考场现状=可跑 20 轮固定剧本的评分流水线，距"能培育 monster 的考场"差 4 个结构性件：开放推理生命周期（能力 3）、长程协议+断点（能力 4）、事件边界协议（能力 6）、层 2 judge（能力 7）。
