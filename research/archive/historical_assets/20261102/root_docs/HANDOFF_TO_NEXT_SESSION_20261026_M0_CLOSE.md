# HANDOFF_TO_NEXT_SESSION_20261026_M0_CLOSE.md

> 上一窗口（M0 阶段）收尾交接。新窗口先读本文件 + STATE_MAP_20261025.md 头部 + mainline_review_20261026/REVIEW.md + WORKER_MARATHON_EXECUTION.md，再开工。

## 一、当前阶段

**M0 已收口，M1 未启动**。用户主线确认（七项完整主线）+ 独立复核四纠偏已亲证并落地；96 批新故事已 96/96/0 完成并收口。下一阶段是 M1：A3 小校准 + C4 候选筛选（128 调用）+ E2 Kotlin 实验引擎实施。

**真实模型调用增量（本会话）**：96 次（BATCH-NEWSTORY-96-20261026，账本 CLOSED 96/96）；其余 0。
**累计账本状态**：
- AUTH-EVAL-JUDGE-20261025-01：CLOSED（200 completed + 3 failed 永不重发 + 1 unknown_consumed=M015:primary:68）
- AUTH-STREAM-INT-20261025-01：CLOSED（8/8）
- AUTH-NEWSTORY-96-20261026-01：CLOSED（96/96）
- AUTH-HARNESS-PERF-PHASE1-20261025-01：未关闭（4/256，仅性能阶段历史，不再续用）
- **下一批新增上限 1280 attempts**（执行书 §0 表，分阶段使用，不花完为目标）

## 二、本会话已落地（M0 交付物，绝对路径）

### A1 96 批收口（全绿）
- `D:\lovebrain-arena\evaluation\NEWSTORY96_A1_CLOSURE.json` —— 收口摘要（96/96/0、order=True、bijection=True、usage 全量 api_usage、账本 CLOSED）
- `D:\lovebrain-arena\evaluation\DELIVERY_AND_COST_COLUMNS_NEWSTORY96_FINAL.json` —— 终版六列（流式身份独立列，不与历史非流式合并）
- `D:\lovebrain-arena\tools\close_newstory96_a1.py` —— 一键收口脚本（可重复运行）
- `D:\lovebrain-arena\tools\delivery_columns_v3_newstory96.py` —— 流式身份六列口径工具

**终版数字（不得改写口径）**：
- parent n=48 T/P/C/K=48/47/48/47 full=25 tok÷C=7718.6
- candidate n=48 T/P/C/K=48/46/48/46 full=26 **tok÷C=3277.9（-57.5%，新故事独立复现）**
- Q/W=unknown；first_usable 全 96 位如实 null（普通链路未接消费回调，不以离线解析回填）
- 14/14 ADJUSTED 意图位点 turn_dir_present=14（不得用作意图调整机制有效证据）

### A2 裁判纠偏四件套（亲证+落地）
- `D:\lovebrain-arena\tools\judge_swap_v1_2.py` —— 换序语义修正模块（9 checks OK）：`swap_payload` 真绑定交换 + `swap_labels` 非破坏 + `normalize_v1_2`
- `D:\lovebrain-arena\evaluation\SWAP_REINTERPRETATION_V1_2.json` —— 旧 repeat_swapped 重解释：R1 同坐标翻转 3/8（旧错误转置下 5/8），57.1% 结论撤回
- `D:\lovebrain-arena\evaluation\JUDGE_QUALITY_V2_1_SCOPE_CORRECTION.json` —— 目标维度 QUALIFIED→STABILITY_PASSED_CORRECTNESS_NOT_QUALIFIED；v5 RETAIN→RETAIN_DEVELOPMENT
- `D:\lovebrain-arena\evaluation\CALIBRATION_PACKET_V1_1\REFERENCE_LABELS_V1_2.json` —— 17 条 human_reviewed→evidence_reviewed_by_ai（旧 V1_1 原样保留）
- `D:\lovebrain-arena\logs\auth_budget\AUTH-EVAL-JUDGE-20261025-01.jsonl.bak-20261026-m015` —— M015 结算前备份（仓库非 git，改前必备）
- M015 已按证据结算 `attempt_settled_unknown` 后账本 CLOSED

### M0 B/C/E 轨设计稿与探测
- `D:\lovebrain-arena\candidates\marathon_c_20261026\C1_ORIGINAL_MECHANISM_VALUE_ACCOUNT_V1.md` —— 原版 16 组件价值账（解决什么/读什么/代价/收益副作用/保留删除决定）
- `D:\lovebrain-arena\candidates\marathon_c_20261026\C2_C3_CANDIDATE_DESIGN_V1.md` —— C2 目标驱动动作 + C3 证据记忆设计稿（M1 实施）
- `D:\lovebrain-arena\benchmark\dev_pool_registry_20261026.json` —— B1 开发池登记（E001-E012 台词级通读 + 三条件分层 + 反事实对）
- `D:\lovebrain-arena\benchmark\episodes\S101.json` `S102.json` `S103.json` `S104.json` —— B2 四条新短故事（8 位/条，schema 已验证 OK，覆盖摄影/宠物/菜谱/夜跑四新领域）
- `D:\lovebrain-arena\benchmark\episodes\L101.json` —— B2 第一条长故事（24 位，时间单调，含旧事实纠正 T06、目标停摆重启 T10-T12、相似事件 T05+T16、跨月空档）

### E1 产品构建打通
- `D:\LoveBrain\local.properties` —— `sdk.dir=D:/Android/Sdk`（正斜杠！单反斜杠会被 properties 解析吃掉变成 `D:AndroidSdk` 触发 SdkLocator.validateSdkPath 异常）
- 构建命令：`cd /d/LoveBrain && cmd //c "set JAVA_HOME=D:\Android\AndroidStudio\jbr&& gradlew.bat :app:assembleDebug --console=plain -q"`（**JBR21**，target 17 编译无障碍）
- 产物：`D:\LoveBrain\app\build\outputs\apk\debug\app-debug.apk` 13.8MB（02:17 构建成功）
- **JDK17 阻塞解除**：Android Studio 自带 JBR 21.0.4 兼容 target 17；无需用户安装；不要改全局 Java

### STATE_MAP 与校验器
- `D:\lovebrain-arena\STATE_MAP_20261025.md` —— 头部已加 M0 段落；权威链 #4/#5 已指向 mainline_review_20261026/
- `D:\lovebrain-arena\tools\state_map_check.py` —— 134 OK / 0 MISMATCH / 2 WARN（合法增长：logs 子目录、AUTH-STREAM-INT 提示）；stale hash 已同步

## 三、四项独立复核纠偏（已亲证，新窗口勿重探）

1. **14 位 ADJUSTED 意图未启用**：`candidates/reference_product_v1/reference_chain.py:32` `is_intent_effective` 闸门只认 ACTIVE（忠实原版 Models.kt L446-447），14/48 位点 status=ADJUSTED → 目标块未注入。**两臂共同输入缺口，非候选错误，不废 96 批**。下一版改"调整=ACTIVE+变更事件流；关闭用产品支持终态"。
2. **换序实验两处实错**：`tools/analyze_irr_v2.py:99` `user_payload(swapped=True)` 标签↔内容绑定没换（只换序列化顺序）；`:125` `normalize_rec` `g[sw[k]]=g.pop(k)` 原地覆盖。**已离线复现**：双键丢乙、单键不换。旧 57.1% 翻转结论撤回为探索性。
3. **目标维度"QUALIFIED"过度声称**：仅由两两评分一致算出，无正确性金标（vs_reference_numeric.available=false）；允许动作覆盖是字符串包含，未验证实际输出。降级为"稳定性通过、正确性未获资格"。
4. **17 条 human_reviewed 无人类凭据**：`tools/build_reference_labels_v1_1.py:167` 无条件标注，无人类身份/回收表/逐条确认。改 evidence_reviewed_by_ai（V1_2）；旧 V1_1 保留。

## 四、M1 立即可推进的工作（按执行书 §7 M1 时段）

| 优先 | 任务 | 上限 | 前置 |
|---|---|---|---|
| P1 | **A3 小校准包**：冻结 32 对新参考动作（覆盖 6 能力族×3 生活领域，含 20 无据/冲突主张 + 20 受保护表达），参考先于 judge；裁判 32对×3=96 + 16同序+16真换序=128，共 ≤128 | A2 已落地，可直接用 `judge_swap_v1_2` 修过的 swap 构造 |
| P1 | **C4 候选筛选**：原版/v5/C2/C3 四臂 ×4 开发故事(E006/E008/E012/E007)×8 位=128 调用 | C2/C3 实施（M1 主工程）|
| P1 | **C2 实施** `candidates/goal_action_v1/`：IDENTITY+MECHANISM+assets+mechanism.py+params.json（按 v5 模板）| 设计稿已就绪 |
| P1 | **C3 实施** `candidates/evidence_memory_v1/`：同上 | 设计稿已就绪 |
| P2 | **E2 Kotlin 实验引擎**：在 `D:\LoveBrain` 加默认关闭的 feature flag + 实验引擎开关 + 状态恢复/回退；按 v5/入围稳定接口接通 | E1 已打通构建 |
| P2 | **B2 续写**：再 3 条短故事（覆盖调情/反撩补强）+ 3 条长故事（24 位/条）=96+72 位 | S101-S104+L101 已 4 条 |
| P3 | **B3 互动环境**：4 场景×2 臂×2 重复×6 轮=96+96（harness+对方模拟），不靠长度奖惩 | C2/C3 入围后 |

**M1 出口**：第一轮真实保留/淘汰 + 候选代码可用 + 接口对拍。失败即允许"没改进就删掉"，不保全代码强判赢。

## 五、纪律与边界（违反即返工）

### 不可越线
- 禁止没弄清现状前调用任何模型（**96 批已 CLOSED，账本不可复活，新批必须新账本**）
- 禁止改题面、改已付费答卷（96 答卷只读）
- API 响应只当数据（json.loads，绝不 eval，绝不要求修复重发）
- 账本不可复活：关闭的 authorization_id 在构造阶段就抛 BudgetRefused
- 改文件前先备份（仓库非 git，本会话已多处 .bak-20261026-* 备份）
- 绝不物理删除用户文件
- Grep 必须限定路径（撞 _acl_locked 副本会刷屏）
- 用 Edit 工具前必须先 Read
- 批量小改用脚本时，替换锚点必须先断言存在，失败要报错不要静默

### 运行环境
- Python：`D:\anaconda\python.exe`，先 `set PYTHONIOENCODING=utf-8`
- 产品构建：`cmd //c "set JAVA_HOME=D:\Android\AndroidStudio\jbr&& gradlew.bat ..."`（JBR21）
- 网关：`http://127.0.0.1:17770/v1`，model=dsf，temp=0.7（如不可达先跑 `D:\桌面\各大网站\脚手架网关\启动网关.bat`，详见知识库 `D:\workflow-library\知识库\外部脚手架互控\模型网关与DSH接入.md` §6）

### 知识库地基（用户长期指令要求实时读+回写）
- `D:\workflow-library\知识库\外部脚手架互控` —— 外部脚手架互控地基
- `D:\workflow-library\知识库\网页聊天自动化` —— 网页聊天自动化地基
- 任何涉及外部脚手架/网页聊天的任务，开工前先读这两份地基，新发现要回写

## 六、第一屏交付格式（用户要求）

新窗口第一次汇报只报五件事：
1. 当前阶段（M0 已收口 / M1 进行中哪一项）
2. 可信评估能力新增了什么（A3 校准？C4 筛选？）
3. 旧候选 v5 处置结论（RETAIN_DEVELOPMENT；目标维度稳定性通过正确性未获资格）
4. 真实新增调用次数及费用与等待口径（分桶：考生/评估/集成）
5. 下一实验是否已开始

之后正文索引证据文件（一律绝对路径）。低频边角问题合并进 backlog。

## 七、必读（绝对路径，按顺序，新窗口开工前）

1. `D:\lovebrain-arena\AGENTS.md` —— 宪法 v1.5（§0 唯一总目标）
2. `D:\lovebrain-arena\OBJECTIVE.md` —— 目标书 v1.4
3. **`D:\lovebrain-arena\STATE_MAP_20261025.md` —— 头部 M0 段落 + 权威链 #4/#5 已更新**
4. `D:\lovebrain-arena\refs\experiment_protocol_20261023\mainline_review_20261026\REVIEW.md` —— 主线复核（四纠偏亲证）
5. `D:\lovebrain-arena\refs\experiment_protocol_20261023\mainline_review_20261026\WORKER_MARATHON_EXECUTION.md` —— 当前执行书（24-40h，1280 上限分阶段）
6. `D:\lovebrain-arena\refs\experiment_protocol_20261023\mainline_review_20261026\INDEPENDENT_RESULTS.json` —— 机器证据快照
7. `D:\lovebrain-arena\evaluation\JUDGE_QUALITY_V2_1_SCOPE_CORRECTION.json` —— 资格口径降级（**取代旧 V2_AND_V5_DISPOSITION 的"QUALIFIED"措辞**）
8. `D:\lovebrain-arena\evaluation\NEWSTORY96_A1_CLOSURE.json` —— 96 批收口摘要
9. `D:\lovebrain-arena\evaluation\DELIVERY_AND_COST_COLUMNS_NEWSTORY96_FINAL.json` —— 终版六列
10. `D:\lovebrain-arena\candidates\marathon_c_20261026\` —— C1 价值账 + C2/C3 设计
11. `D:\lovebrain-arena\benchmark\dev_pool_registry_20261026.json` —— B1 开发池
12. `D:\LoveBrain\AGENTS.md` + `D:\TeamWorkspace\team\projects\lovebrain\START_HERE.md` + `tasks\ACTIVE.md` —— 产品入口

旧 `evaluation_baseline_review_20261025/` 三件（REVIEW/WORKER_EXECUTION/HANDOFF）保留为历史，不再作执行依据（已被 mainline_review_20261026 取代）。

## 八、用户原话主线（七项，新窗口勿偏离）

1. 公平参照：原版机制忠实保留为基线，同时检查哪些复杂度真正有用
2. 代表性任务：连续聊天、长期记忆、意图变化、调情推进、深聊、转场和收尾
3. 可信评估：事实、目标行动、自然互动、个人口味分别评，允许多种正确表达
4. 实质重构：改提示资产、意图机制、记忆与上下文组织、调用及消费策略
5. 真实实验闭环：改候选→同条件比较→检查效果、成本、等待→保留或淘汰
6. 防止过拟合：新故事、长故事和互动挑战验证，不能只在熟题上赢
7. 产品落地：接回 `D:\LoveBrain`，能切换、恢复、回退，真正试用

**终点不是"全绿"，而是至少两次实际改进-比较-处置闭环 + 一条原产品可运行的新旧切换链路。** 不能先写结论再挑指标；不能为跑满批次放行。
