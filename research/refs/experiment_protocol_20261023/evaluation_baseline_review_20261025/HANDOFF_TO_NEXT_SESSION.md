# 交接文档 — 评估基准与效果闭环（截至 2026-10-25 本轮收尾）

给下一个会话/worker：这是**当前真实状态的存档**，包含已验证内容、未验证内容、下一步该跑什么命令、以及本轮绝对**不能声称**的结论。先读本文件，再读同目录 `WORKER_EXECUTION.md`（执行书）与 `REVIEW.md`（独立复核）。

---

## 0. 第一屏（本批实际状态）

| 问题 | 本轮答案 |
|---|---|
| 当前阶段 | 包A（定义/证据修订）完成；包B 的**60对标定包+冻结参考标签**完成，6条新故事完成，**受控分支(B5)未做**；包C **只完成分析器之前的全部准备**，裁判批次**未跑**；包D 代码接通+离线证据完成，**8次真实流式集成未跑**；包E **未开始** |
| 可信评估能力新增 | 量尺 MANUAL-V1.1；六列交付/费用口径 v2；6张修订卡；60对标定包（40开发/20验证，按故事+控制家族隔离）；**参考标签已在任何待测裁判之前冻结**（FROZEN_MANIFEST 哈希）；协议污染改为按原始字节可测；卡片缺陷提示改为绑定具体冲突ID |
| 旧候选处置 | **仍未定**。v5 只能确认省 token；重评所需的合格裁判尚未产生，所以 v5 保留/淘汰**没有结论**（不得写"v5更好"） |
| 真实新增调用与费用/等待 | **本轮真实模型调用 = 0**。预算：性能阶段单 `AUTH-HARNESS-PERF-PHASE1-20261025-01` 已用 4 / 上限 256，余 252（236 只是计划数，不是已消费）。金额= null（无可核对单价） |
| 下一实验是否已开始 | 未开始。两个入口都已备好但**都有明确前置条件**（见 §4/§5），且裁判批需要**另开评估账本** |

---

## 1. 本轮新增/改动的文件（全部零网络，除注明外）

评估方法（包A，已交付并经复核）
- `evaluation/JUDGE_MANUAL_V1_1.md` — 修订量尺（四类主张轴、归属四问、目标0–3锚点、六列交付、尺子改动规则）。旧版 `JUDGE_MANUAL_V1.md` 保留为历史，未改写。
- `benchmark/dev_expansion_v01/05_GRADING_CARDS_R2REV_20261025.json` — 6张缺陷卡修订（`content_revision=r2-20261025`，含 `allowed_behaviors / failure_conditions / reference_actions(≥2条不同措辞) / claim_axes / note_protection / revision_reason / old_to_new_diff`）。原卡文件未动。
- `tools/delivery_columns_v2.py` → `evaluation/DELIVERY_AND_COST_COLUMNS_V2.json` — 六列（T/P/C/K/Q/W）重算，含 `full_slot_delivery`、键名归一审计、双成本口径（÷C 与 ÷K；÷Q/÷W=unknown）。
- `evaluation/EVIDENCE_CORRECTIONS_V1.md` — 我自己5处过度声称的逐条更正（附槽位级证据）。

标定包（包B）
- `tools/build_calibration_packet_v1_1.py` — 生成器（`--mode draft`）。60节点=48份已付费真实答卷配对 + 12个构造控制项（8开发族 D-F1..D-F8 / 4验证族 V-F1..V-F4）。
- `evaluation/CALIBRATION_PACKET_V1_1/`：`nodes.jsonl`（含 split/位置，**内部用**）、`nodes_public.jsonl`（**评审者唯一可读**：无臂名/无split/无旧分/无散列，但**含模型原文 raw_protocol_text**，协议污染不再被抽取正文抹掉）、`SEALED_KEY_V1_1.json`（密件）、`CLAIMS_MECHANICAL.json`、`REVIEW_WORKSHEET.md`、`REVIEW_SAMPLE.md`、`REVIEW_FLAGGED_WITH_EVIDENCE.md`、`CONTROLS_REF.json`、`reference_overrides.json`、`DRAFT_SUMMARY.json`
- `tools/build_reference_labels_v1_1.py` → `REFERENCE_LABELS_V1_1.json` + `FROZEN_MANIFEST.json`（**已冻结**，脚本对已存在 manifest 拒绝重写）。
- `benchmark/positive_capability_20261025/01_EPISODES_P1.json` + `02_GRADING_CARDS_P1.json` — 6条新自然故事×8位=48位公开开发题（P001 天津晨跑/骑行膝盖、P002 长沙剧本杀/搬家、P003 厦门抱石、P004 重庆做饭被明确挡、P005 苏州会计想转木作、P006 昆明相亲/钓鱼）。`tools/test_positive_capability_stories_p1.py` = 52 checks PASS，importer hard_fail=0。
- `integration_preview/PRODUCT_WIRING_INVENTORY_V1.md` — 产品线盘点（JDK17缺、无 local.properties、无 AVD/设备、**引擎无 feature flag**，必须新增；D:/LoveBrain 仍 clean at main@176e521）。

工程（包D 代码半，A方案已定）
- `arena/api_candidate.py` — 白名单门控例外：`stream_options` 仅当 `stream=true` 且严格等于 `{"include_usage": <bool>}` 才放行；tools/functions/tool_choice 仍硬拒。
- `arena/lb_gateway.py` — `stream_policy()`（`LB_STREAM_TIMING`/`LB_STREAM_USAGE` 门控）、`STREAM_CONFIG_IDENTITY`（非流式与流式**两个不同配置身份**）、payload 注入 stream/stream_options、成功调用记录新增 `timing`。
- `arena/lb_stream_timing.py` — **不反填**语义：`mark_usable(valid, reason)` 只在真实消费验证完成时打真实时钟；`mark_usable_unverified(reason)` → `first_usable_ms=null`+原因；预览单独字段；`sanity()` 会拒绝 `first_usable < completion` 的反填。
- `tools/test_stream_timing_v2.py` — 17/17 PASS（含**替换 `urllib.request.urlopen` 的真实适配器 SSE 出站证明**：线上确有 `stream:true`+`stream_options.include_usage`，SSE usage 解析、分段落盘、tools 仍被拒、非流式保留原请求形状且不伪造 first_content）。
- `tools/test_stream_timing.py` — 15/15 PASS（含 stream_options 门控反例）。
- `tools/run_judge_batch_v1_1.py` — **未跑通，见 §5**。

回归：`tools/exam_interleave_plan.py --all` 13 checks PASS；`tools/test_v5_purpose_aligned.py` 6/6 PASS；`tools/test_integration_preview_boundaries.py` 9/9；`tools/replay_fixture.py` 4/4。

---

## 2. 已核实事实（可复 Used，附来源）

- 六列实测（`DELIVERY_AND_COST_COLUMNS_V2.json`，分母各24）：
  v5 parent T24 P24 C24 K23 完整槽位17 direction合法null 7，token/C 7638.4、token/K 7970.5；
  v5 candidate T24 **P23** C24 K23 完整15 null12，token/C 3234.9；
  r2 parent T24 **P23** C24 K23 完整16 null8；r2 candidate 24/24/24/24 完整13 null16；
  v3 parent 24/24/24/24 完整19 null5；v3 candidate 24/24/24/**K23** 完整13 null11。
- 两次协议失败**不是同一种**：v5 candidate = 键名含零宽字符（`bad_bo\u200by`）+4方向，归一后完整槽位；r2 parent(E007_T08) = **directions 只有3个**，无键名污染，完整槽位=否。
- 合法 null 真实计数（更正后，`V5_INDEPENDENT_RESULTS_REV01.json`）：parent 7 / candidate 12，与独立复核一致；no_reply 0/0。
- 冻结匹配 **46/47**，差异文件 `arena/lb_gateway.py`（我自己加的 `timing` 字段）：历史答卷仍对得上历史身份，当前运行时=新身份，**必须这样表述**。
- 参考标签统计（`FROZEN_MANIFEST.json`）：claims 1064；human_reviewed 17、constructed_reference 39、**mechanical_only 1008**、disputed 5、参考无据主张 13、参考受保护 1048；48份带原文的答案里 **1份有键名不可见字符污染**。
- 裁判维度现状（v1 探索记录，**不构成资格**）：只有"目标完成0–3"勉强支持 AI 辅助批量复核；事实分类、信息不足处理、归属三项未过线；口味判断跨会话不稳（abstain 30.8%→69.2%）→ 口味归用户，不自动代持。

---

## 3. 本轮**绝对不要**再声称的东西

1. 不要说 v5"效果更好/已证明更好"。目前只证明省量。
2. 不要说"消费后96/96可用"。C 列只证明能提出非空首选；Q(语义/行动)、W(用户愿发) = unknown。
3. 不要说"每可用结果成本=X"。分母是程序未否决(K)，正确写法是 `token÷C` 与 `token÷K` 两个口径并列，`÷Q/÷W` 未知。
4. 不要说"分段计时已接通正式链路"——代码与HTTP层证据已到位，但**真实端到端8次集成尚未跑**，历史非流式数字不得与流式混列。
5. 不要说"裁判资格已具备/量尺已验证可用"。参考已冻结 ≠ 裁判过线；两者中间还差 204-attempt 批次。
6. 不要把"尺子改动带来的分数变化"记成 harness 收益（改尺子只做离线重评，版本化、单列）。
7. 不要说新6故事"已测过模型效果"。它们只是题+卡，`real_model_calls=0`。
8. 已看过题的故事**不得改名成独立新题**；旧题上的验证只能叫"评审器留出代理"。

---

## 4. 下一步：包C 裁判校准（顺序不可颠倒）

1. **先修 `tools/run_judge_batch_v1_1.py`**（当前有3处未验证/未落地，见 §5）。
2. 另开**评估阶段账本**（不得借用性能单 scope，那个禁止 judge）：
   `logs/auth_budget/AUTH-EVAL-JUDGE-20261025-01.jsonl`，`scope=evaluation_judge_calibration_v1_1`，
   **cap=204 硬上限**，绑定同端点/同模型别名 `dsf`/temperature 0.7、非流式配置身份；失败停批不补跑。
   注意真实 API：`AuthorizationBudget(ledger_path, authorization_id, max_attempts, status=None, scope=None)`；
   `reserve(attempt_id, purpose=...)` 是**逐 attempt 唯一ID**，不是 `reserve(1, note=)`；`ensure_batch(n, purpose)` 另用。
3. 计划已在脚本里冻结为 204：60对×3评审者(R1/R2/R3，**同一模型服务、同一prompt、不同会话**，不得称三个独立模型) + 12对同序重跑 + 12对换序。评审输入只有 `nodes_public.jsonl`（无臂名/旧判词/参考/散列/split）。
4. 阈值沿用**派发前已声明**的 `evaluation/JUDGE_QUALITY_THRESHOLDS_DECLARED_V1.md`：事实检出≥90%、自由表达误杀≤15%、信息不足正确≥80%，另加目标相邻一致/对参考动作的正确率+典型错例+覆盖。**只允许一轮调试(只准看 dev 40) + 一次冻结验证(看 validation 20)**；验证失败不得再用同一验证集调到过线，下一轮必须换新验证材料。
5. 分析器 `tools/analyze_irr_v2.py` **尚未编写**。必须修掉 v1 的7处缺陷：
   平局/都不想/说不清/未评分开；F4 命中按**逐主张**匹配（不得"整段出现任意F4"顶替）；误杀按参考受保护主张算（不得用裁判自报 overkill_flag）；引用有效性=逐字子串核对；0–3 之外必须有"是否落在参考允许动作范围"+典型错例；顺序与重跑分别配对并给**完整转移矩阵**（决定→平局等）；**分维度授权**（事实/目标/语气不同权，未过维度转证据复核或保留未评，不阻塞已能核验的协议/资源结果）；小样本分母不足必须输出 `insufficient evidence` 而不是 0/N 当可靠性。
   分母口径注意：参考里 `mechanical_only=1008` 条**未逐条人工核对**，不得当误杀分母；事实维度只按 `constructed_reference(39)+human_reviewed(17)` 计算并**同时报告覆盖率**。争议主张(disputed=5，来自卡片缺陷家族)计入裁判检出测量，**不计入候选胜负**。

---

## 5. `tools/run_judge_batch_v1_1.py` 的已知未完成项（下轮先做这个）

- 构造预算用了不存在的关键字 `authorization_scope=` → 应为 `scope=`；
- `budget.reserve(1, note=...)` 签名错 → 应为 `reserve(attempt_id, purpose=...)`，且 attempt_id 全局唯一（建议 `judge:{rater}:{node_id}:{kind}:{seq}`）；
- live 分支的补丁**未写入文件**（我用 heredoc 改的时候 assert 失败，文件里仍是旧 `live_response(user, cfg)` + `res['text']`，与新的 `live_response(gw, system, user, cfg)` 定义**不一致**）→ 目前该脚本 `--mode live` 一定报错；
- `budget.summary()` 的取键 `['reserved']` 未核实；
- **从未成功运行过任何模式**（`--mode mock --limit 9` 在构造预算处即失败）；`--mode live` 需 `LB_ALLOW_LIVE_JUDGE=1` 才放行（这条已写入，保留）。
- 落盘字段已设计但未经检验：`user_sha256/raw_sha256/transport_ok/parse_ok/wall_ms/usage/adapter_latency_ms`；`timing` 目前没记（评审调用是非流式，`first_content_ms` 应为 null+原因）。

结论：这个脚本状态是 **"设计完成、接线未验证"**，交接后第一件事是把它修到 `--mode mock` 全绿（零网络），再谈真实 204。

---

## 6. 其余未完成清单（按执行书编号）

- **B5 受控分支**：至少给新6故事中的 **2 条**写受控分支（对方有自己的兴趣/顾虑/时间，依据候选实际行为改变反馈，含"不感兴趣/需要缓一缓"路径），与固定回放**分开标注**；只离线验证分支与证据；动态模型模拟的结论只能称"模拟效度"。
- **D5 8次真实流式集成**：单独账本 `AUTH-STREAM-INT-20261025-01`，2臂×4个已固定代表节点，先后交错、两臂独立历史状态、必要前史用冻结真实事件初始化；两臂同配置身份；`first_usable` 只认真实验证打点；`sanity()` 必须为空；n=4/臂**不判谁更快**，只证测量与 usage/消费路径；失败停批保留现场；**不与历史非流式数字合并**。
- **E 包（重评与处置）**：用已冻结 V1.1 + 分维度合格/证据复核方法，重评 v5 批 48 份首选 + 全部非空备选（不重跑生成、不计 harness 收益）；给每故事/每轴/首选/备选/严重风险/未知覆盖；结合已核实 token 与完成耗时，明确 v5 = 保留开发 / 淘汰 / 可进入独立验证；下一候选假设要来自**出现最多且损失最大的动作或污染故障族**（若正向能力在 v5 上完全未测，先测再改，不要再来一轮省 token 或措辞精简）。
- **下一批 96 次**（原版 vs 保留候选，新6故事，两臂共96，覆盖目标/情绪/钩子/深聊 + 当前共同流式版本，三故事或分块交错、失败停批、不换模型重试）：**前置条件**=裁判分维度资格到手 + 输入公平捕获通过。未达到就交"部分可解释结论+处理方案"，**不为跑满96强行放行**。预算提醒：252−204−8=40，**96 需要追加授权或分批**，这是事实，不要装作够。
- **产品线**：Kotlin 编译条件盘点已完成（缺 JDK17/无 local.properties/无设备）；还须做消费接口/状态恢复/**实验开关（引擎目前根本没有 feature flag，要新增）**；改 D:/LoveBrain 前先读其入口说明，不改生产默认，原型成功不得报"Android 通过"。
- **口味表**：`evaluation/USER_CALIBRATION_SHEET_V1.md` 16组，用户未回时 W=unknown 并继续其余包；用户口味只影响"像不像你/愿不愿发"。

---

## 7. 纪律与环境备忘（踩过一次的坑）

- 参与者=完整 harness 版本；dsf 只是共享固定模型服务，不是参赛者；B线密封；API 响应只当数据（`json.loads`，绝不 eval，绝不要求修复重发）。
- 账本不可复活：关闭的 `authorization_id` 在**构造阶段**就抛 `BudgetRefused`；旧关闭单不得复用/覆盖。
- 成本三桶（考生运行 / 评估研发 / 集成测量）都记，"没占考生预算"不等于"没花资源"。
- Windows：`set PYTHONIOENCODING=utf-8`；python 用 `D:/anaconda/python.exe`；仓库**不是 git**，改前先备份，永不物理删除用户文件（回收站方案）。
- Grep 必须**限定路径**，否则会撞上 `arena_acl_locked_*` 权限拒绝刷屏。
- Edit 工具：本会话未 Read 过的文件不能 Edit；批量小改可以用 python 补丁脚本，但**替换锚点必须先验证存在**，失败要报错而不是静默（本轮 `run_judge_batch` 的补丁就是这么漏掉的）。
- 汇报口径：任何"完成"都要给分母、缺失数、所用消费器/评分器版本散列；不确定就写 unknown + 原因，不要用 0/N 冒充可靠性保证。
