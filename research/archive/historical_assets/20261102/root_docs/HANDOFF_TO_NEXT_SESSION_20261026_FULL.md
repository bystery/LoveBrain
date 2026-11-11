# HANDOFF_TO_NEXT_SESSION_20261026_FULL.md

> **本文件纠正先前 HANDOFF_TO_NEXT_SESSION_20261026_M0_CLOSE.md 的重大漏报**：先前交接文档只写主线轨 M0 成果，
> 完全漏掉了侧线轨在 02:30 后静默完成的三块大工作（E2 Kotlin 实验引擎实现、A3 小校准实跑、B3 互动环境层+马拉松总账）。
> 新窗口优先读本文件 + STATE_MAP 头部三段（按时间倒序：06:50/06:10/03:00）+ mainline_review_20261026/REVIEW.md + WORKER_MARATHON_EXECUTION.md。
> **新窗口开工第一动作：重新读一遍磁盘，不要相信任何交接文档的"当前已完成"清单**——侧线可能又静默产出了新东西。

## 一、当前真实阶段（截至 2026-10-26 07:00）

**M0 主线 + M0 侧线全部收口**。下一阶段是 M1：C4 候选筛选（128 调用）+ 入围方案复验（384）+ B3 互动实跑（96+96）。

主线与侧线分工（执行书 §1 分轨）：
- **主线轨**（本窗口）：四纠偏亲证、96 批收口、A2 裁判纠偏四件套、C1 价值账、C2/C3 设计稿、B1 开发池、B2 命题 4 短 1 长、E1 构建打通
- **侧线轨**（02:30 后静默接力，**先前漏报**）：E2 Kotlin 实验引擎实现（9 commits）、A3 小校准实跑（172/172 CLOSED）、B3 互动环境层 authored、马拉松额度总账 MARATHON_BUDGET_LEDGER.json、C2/C3 候选实现（goal_action_v1/、evidence_memory_v1/）

## 二、已落地（全部，绝对路径，按轨分）

### 主线轨（M0 主线）

#### A1 96 批新故事收口（全绿）
- `D:\lovebrain-arena\evaluation\NEWSTORY96_A1_CLOSURE.json` —— 96/96/0、order=True、bijection=True、usage api_usage 96/96、账本 CLOSED
- `D:\lovebrain-arena\evaluation\DELIVERY_AND_COST_COLUMNS_NEWSTORY96_FINAL.json` —— 终版六列
- `D:\lovebrain-arena\tools\close_newstory96_a1.py`、`tools\delivery_columns_v3_newstory96.py`
- 数字（不得改写）：parent n=48 T/P/C/K=48/47/48/47 tok÷C=7718.6；candidate n=48 T/P/C/K=48/46/48/46 **tok÷C=3277.9（-57.5%）**；Q/W=unknown；first_usable 全 96 null；14/14 ADJUSTED 位点 turn_dir_present=14

#### A2 裁判纠偏四件套（亲证+落地）
- `D:\lovebrain-arena\tools\judge_swap_v1_2.py` —— 9 checks OK
- `D:\lovebrain-arena\evaluation\SWAP_REINTERPRETATION_V1_2.json` —— R1 同坐标翻转 3/8（旧 57.1% 撤回）
- `D:\lovebrain-arena\evaluation\JUDGE_QUALITY_V2_1_SCOPE_CORRECTION.json` —— 目标 QUALIFIED→稳定性通过；v5 RETAIN→RETAIN_DEVELOPMENT
- `D:\lovebrain-arena\evaluation\CALIBRATION_PACKET_V1_1\REFERENCE_LABELS_V1_2.json` —— 17 条 human_reviewed→evidence_reviewed_by_ai
- `D:\lovebrain-arena\logs\auth_budget\AUTH-EVAL-JUDGE-20261025-01.jsonl.bak-20261026-m015` —— M015 结算前备份；M015 已 attempt_settled_unknown 后账本 CLOSED

#### B/C 设计稿与命题
- `D:\lovebrain-arena\candidates\marathon_c_20261026\C1_ORIGINAL_MECHANISM_VALUE_ACCOUNT_V1.md` —— 原版 16 组件价值账
- `D:\lovebrain-arena\candidates\marathon_c_20261026\C2_C3_CANDIDATE_DESIGN_V1.md` —— 设计稿
- `D:\lovebrain-arena\benchmark\dev_pool_registry_20261026.json` —— B1 开发池登记
- `D:\lovebrain-arena\benchmark\episodes\S101.json`~`S104.json` —— 4 短故事（8 位/条，摄影/宠物/菜谱/夜跑）
- `D:\lovebrain-arena\benchmark\episodes\L101.json` —— 1 长故事（24 位，长沙装修工作室+玻璃工作室）

#### E1 构建打通
- `D:\LoveBrain\local.properties` —— `sdk.dir=D:/Android/Sdk`（**正斜杠**！单反斜杠被 properties 解析吃掉变非法路径触发 SdkLocator.validateSdkPath 异常）
- 构建命令：`cd /d/LoveBrain && cmd //c "set JAVA_HOME=D:\Android\AndroidStudio\jbr&& gradlew.bat :app:assembleDebug --console=plain -q"`（JBR21，target 17）
- 产物 `D:\LoveBrain\app\build\outputs\apk\debug\app-debug.apk` 13.8MB；JDK17 阻塞解除

### 侧线轨（先前漏报，必读）

#### A3 小校准实跑 + 分析 V1_2（CLOSED 172/172）
- `D:\lovebrain-arena\logs\auth_budget\AUTH-A3-JUDGE-20261026-01.jsonl` —— CLOSED 172/172（168 completed + 4 基础设施失败零网络零 token 永久排除），509,901 tokens
- `D:\lovebrain-arena\evaluation\A3_CALIBRATION_ANALYSIS_V1_2.json` —— 最终分析（V1/V1_1 两版 swap 归一缺陷留档为教训）
- `D:\lovebrain-arena\evaluation\JUDGE_MANUAL_V2_0_A3.md` —— V2.0-A3 手册（四纠偏落判据：行动分与事实风险分拆开/拆分粒度/受保护边界/位置中立硬约束）
- `D:\lovebrain-arena\evaluation\CALIBRATION_PACKET_A3_20261026\` —— 32 对包 authored 冻结（6族×3域+16swap+22+22电池）
- 分析关键结果：
  - 行动分 124/124 落参考区间；乙 0/124（预期缺陷侧）
  - 内嵌无据主张检出 56/56；电池 22/22 + 20/20
  - 换序 16/16 零翻转；重测 15/15；三会话一致 100%
  - **误旗 12.1%**（带保留句/玩笑自嘲/自己安排需她侧证据/1 例归属错）= 残余缺陷族
  - 资格口径：**清晰材料可作辅助排序+缺陷检出；不得外推到边缘材料**（204 批真实答卷误杀 22-25% 旧账仍在）
  - 裁判阶段额度 172/256，余 84 不足 128 对全验 → 下轮须缩小终验范围或并入下一授权周期

#### E2 Kotlin 实验引擎实现（产品隔离 worktree）
- **`D:\LoveBrain-arena-e\`** —— 产品隔离 worktree（分支 `arena/exp-engine-20261026`，9 commits，main 0 脏未推送）
- commit 序列（自下而上）：
  1. `7e2dd5c` 实验引擎 lane 路线图（侦察+缝图）
  2. `8cc5156` engineFlavor 设置 + 默认关产品引擎 + 双侧 clamp + 契约钉
  3. `ecf0524` 移植 v5 冻结 system 资产逐字节 + 注册实验集 + 字节冻结测试钉 SHA-256+size
  4. `ffed024` flavor-aware system 装配 + 每请求冻结 promptFlavor 贯穿 GenerationInput/Engine/VM；原引擎字节钉不变
  5. `51936ab` 设计决策修订 1 - flavor 按请求冻结带哈希
  6. `51dd7b2` 反事实目标消费钉 - 调整保持 ACTIVE 只注入新体、关闭静默、目标文本任一 flavor 都不进 system
  7. `071bd9f` 钉 v5 输出契约对产品消费 - analysis:{} 可解析、null/absent direction 区分、空 analysis 轮保持 scene/plan 字节不动
  8. `c95c932` lane 关闭门绿 - 全量 2792/0/0/1（基线 2777 + 15 新）+ assembleDebug RC=0 13.8MB；fixture read-surface 随 flavor-arg 哈希重载（8 严格 mock + 故障注入点移位）
- 关键文件：`app/src/main/java/com/lovebrain/app/domain/AssetRegistry.kt`、`data/SecurePrefs.kt`、`domain/port/SettingsStorePort.kt`、`viewmodel/LoveBrainViewModel.kt`
- 状态：**可构建 debug 产物 + 全量测试绿 + 意图反事实 4/4 + v5 输出契约 3/3**；未推送，未做真机/UI

#### C2/C3 候选实现（设计稿落地）
- `D:\lovebrain-arena\candidates\goal_action_v1\` —— C2 实现（IDENTITY+MECHANISM+assets+mechanism+params）
  - 主假设：目标×反馈信号条件化启用动作能力；模型在同一次调用内自评 feedback_signal/goal_category/action_taken 进 analysis
  - 基座：v5_purpose_aligned 闭包（四 system 资产逐字节继承）；差异全在 user 侧目标-动作层
  - 信号=不明 ⇒ 保守承接（"切"不自动=降温 P003 教训写死）
- `D:\lovebrain-arena\candidates\evidence_memory_v1\` —— C3 实现
  - 主假设：五分离存储（events/facts/tentatives/goals/notes）+ 预算化投影
  - 投影预算：当前窗口 > events 近窗(1400CU) > facts 相关(500CU) > tentatives(300CU) > goal(600CU)
  - facts 相关性=字符二元组重合率（零领域词）；窗外事实召回带 src/ts 供回扣
  - V1 不做自动旧事实纠正翻转（留待长故事测试暴露）

#### B3 互动环境层（authored，runner 待臂）
- `D:\lovebrain-arena\benchmark\interaction_env_20261026\B3_SCENARIOS_V1.md` + `scenarios.json`
- 4 场景：I1 初识转场 / I2 互相试探 / I3 深聊意愿变化 / I4 推进线下有日程犹豫
- 纪律：模拟器盲于臂身份、不靠长度/问号奖惩、双方可拒绝/结束、轮次上限 6、不奖励"约得越多越好"
- 跑法：2 臂 × 4 场景 × 2 重复 × 每轮 1 harness + 1 模拟 = 阶段帽 96+96（harness+模拟器）
- 状态：scenarios.json + 模拟器构造器就绪，**runner 待臂**

#### 马拉松额度总账
- `D:\lovebrain-arena\refs\experiment_protocol_20261023\mainline_review_20261026\MARATHON_BUDGET_LEDGER.json` —— 1280 上限 8 阶段分账
  - 已用：裁判校准+新故事评审 172/256
  - 其余 7 阶段 0/各帽（C4 筛选 128、复验 384、冻结新题 256、互动 96+96、产品 16、命题 48）
  - 由侧线维护、主线恢复后共笔

## 三、独立复核四纠偏（已亲证，新窗口勿重探）

1. **14 ADJUSTED 意图未启用**：`candidates/reference_product_v1/reference_chain.py:32` 闸门只认 ACTIVE；14/48 位点 status=ADJUSTED → 目标块未注入（两臂共同缺口，非候选错误，不废 96 批）
2. **换序实验两 bug**：`tools/analyze_irr_v2.py:99` payload 绑定没换 + `:125` normalize 原地覆盖（已离线复现双键丢乙/单键不换）；A3 V1_2 已用 `judge_swap_v1_2` 修法，16/16 零翻转
3. **目标 QUALIFIED 过度声称**：仅评分一致，无正确性金标；降级稳定性通过；v5 RETAIN→RETAIN_DEVELOPMENT
4. **17 human_reviewed 无凭据**：改 evidence_reviewed_by_ai（V1_2，旧 V1_1 保留）

## 四、M1 立即可推进的工作（按执行书 §7 M1 时段）

| 优先 | 任务 | 上限 | 状态 |
|---|---|---|---|
| **P1** | **C4 候选筛选**：原版/v5/C2/C3 四臂 ×4 开发故事×8 位=128 | 128 | C2/C3 候选已实现（侧线），可派发 |
| **P1** | **B3 互动实跑**：2 臂 × 4 场景 × 2 重复 × 6 轮 = 96 harness + 96 模拟 | 96+96 | scenarios.json 已 authored，runner 待臂 |
| P2 | **A3 续验**：余 84 不足 128 对全验；缩范围或并入下一授权周期 | (84/256) | 已记录 |
| P2 | **B2 续写**：再 3 短（调情/反撩补强）+ 3 长（24 位/条） | (96+72) | S101-S104+L101 已 4 条 |
| P3 | **E2 真机/UI**：实验引擎开关→输入→生成→复制→确认发送→重启→下一轮→关闭目标→切回原版 | 16 | 代码已落，待设备 |
| P3 | **C4 入围后复验**：原版/v5/入围组合 ×8 故事×8 位×2 重复=384 | 384 | 等 C4 出入围方案 |

**M1 出口**：第一轮真实保留/淘汰 + 候选代码可用 + 接口对拍。失败即允许"没改进就删掉"，不保全代码强判赢。

## 五、纪律与边界（违反即返工）

### 不可越线
- 禁止没弄清现状前调用任何模型（**已 CLOSED 账本不可复活，新批必须新账本**）
- 禁止改题面、改已付费答卷（96/A3 答卷只读）
- API 响应只当数据（json.loads，绝不 eval，绝不要求修复重发）
- 账本不可复活：关闭的 authorization_id 在构造阶段抛 BudgetRefused
- 改文件前先备份（仓库非 git）；绝不物理删除用户文件
- Grep 必须限定路径（撞 _acl_locked 副本刷屏）
- Edit 前必须先 Read；批量小改脚本锚点必须先断言存在，失败报错不静默

### 运行环境
- Python：`D:\anaconda\python.exe`，先 `set PYTHONIOENCODING=utf-8`
- 产品构建：`cmd //c "set JAVA_HOME=D:\Android\AndroidStudio\jbr&& gradlew.bat ..."`（JBR21）
- 网关：`http://127.0.0.1:17770/v1`，model=dsf，temp=0.7（不可达先跑 `D:\桌面\各大网站\脚手架网关\启动网关.bat`；知识库 `D:\workflow-library\知识库\外部脚手架互控\模型网关与DSH接入.md` §6）

### 知识库地基（用户长期指令要求实时读+回写）
- `D:\workflow-library\知识库\外部脚手架互控`、`D:\workflow-library\知识库\网页聊天自动化`
- 涉外部脚手架/网页聊天的任务，开工前先读这两份地基，新发现要回写

## 六、第一屏交付格式（用户要求）

新窗口第一次汇报只报五件事：
1. 当前阶段（M0 主线+侧线全部收口 / M1 进行中哪一项）
2. 可信评估能力新增了什么（A3 已收口 V1_2：清晰材料辅助排序+缺陷检出，边缘材料不外推）
3. 旧候选 v5 处置结论（RETAIN_DEVELOPMENT；目标维度稳定性通过正确性未获资格）
4. 真实新增调用次数及费用与等待口径（A3 172/172 CLOSED 509,901 tokens；分桶：考生 0 / 评估 172 / 集成 0 本阶段）
5. 下一实验是否已开始（C4 筛选 128 + B3 互动 96+96 待启动；候选已实现可派发）

之后正文索引证据文件（一律绝对路径）。低频边角问题合并进 backlog。

## 七、必读（绝对路径，按顺序）

1. `D:\lovebrain-arena\AGENTS.md` —— 宪法 v1.5
2. `D:\lovebrain-arena\OBJECTIVE.md` —— 目标书 v1.4
3. **`D:\lovebrain-arena\STATE_MAP_20261025.md` —— 头部三段（06:50/06:10/03:00 倒序）**
4. `D:\lovebrain-arena\refs\experiment_protocol_20261023\mainline_review_20261026\REVIEW.md`
5. `D:\lovebrain-arena\refs\experiment_protocol_20261023\mainline_review_20261026\WORKER_MARATHON_EXECUTION.md`
6. `D:\lovebrain-arena\refs\experiment_protocol_20261023\mainline_review_20261026\INDEPENDENT_RESULTS.json`
7. `D:\lovebrain-arena\refs\experiment_protocol_20261023\mainline_review_20261026\MARATHON_BUDGET_LEDGER.json` —— 总账
8. `D:\lovebrain-arena\evaluation\JUDGE_MANUAL_V2_0_A3.md` —— A3 评审手册 V2.0
9. `D:\lovebrain-arena\evaluation\A3_CALIBRATION_ANALYSIS_V1_2.json` —— A3 分析结果
10. `D:\lovebrain-arena\evaluation\JUDGE_QUALITY_V2_1_SCOPE_CORRECTION.json` —— 资格口径降级
11. `D:\lovebrain-arena\evaluation\NEWSTORY96_A1_CLOSURE.json` + `DELIVERY_AND_COST_COLUMNS_NEWSTORY96_FINAL.json`
12. `D:\lovebrain-arena\candidates\marathon_c_20261026\` —— C1 价值账 + C2/C3 设计
13. `D:\lovebrain-arena\candidates\goal_action_v1\IDENTITY.md` + `evidence_memory_v1\IDENTITY.md` —— C2/C3 实现
14. `D:\lovebrain-arena\benchmark\interaction_env_20261026\B3_SCENARIOS_V1.md` —— B3 场景
15. `D:\LoveBrain-arena-e\` —— E2 worktree（`git log --oneline -10` 看 9 commits）
16. `D:\LoveBrain\AGENTS.md` + `D:\TeamWorkspace\team\projects\lovebrain\START_HERE.md` + `tasks\ACTIVE.md` —— 产品入口

旧 `evaluation_baseline_review_20261025/` 三件保留为历史，不再作执行依据（已被 mainline_review_20261026 取代）。
旧 `HANDOFF_TO_NEXT_SESSION_20261026_M0_CLOSE.md` 已被本文件取代（漏报侧线成果）。

## 八、用户原话主线（七项，新窗口勿偏离）

1. 公平参照：原版机制忠实保留为基线，同时检查哪些复杂度真正有用
2. 代表性任务：连续聊天、长期记忆、意图变化、调情推进、深聊、转场和收尾
3. 可信评估：事实、目标行动、自然互动、个人口味分别评，允许多种正确表达
4. 实质重构：改提示资产、意图机制、记忆与上下文组织、调用及消费策略
5. 真实实验闭环：改候选→同条件比较→检查效果、成本、等待→保留或淘汰
6. 防止过拟合：新故事、长故事和互动挑战验证，不能只在熟题上赢
7. 产品落地：接回 `D:\LoveBrain`，能切换、恢复、回退，真正试用

**终点不是"全绿"，而是至少两次实际改进-比较-处置闭环 + 一条原产品可运行的新旧切换链路。** 不能先写结论再挑指标；不能为跑满批次放行。
