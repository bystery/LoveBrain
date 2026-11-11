# 主线合并交付：A 实跑收口 · B 精简候选 · C 最小对照 · D 扩题 · E 评审（2026-10-24）

执行依据：`refs/experiment_protocol_20261023/wiring_slim_independent_review_20261024/NEXT_MAINLINE_BATCH.md`。
本轮真实模型调用数：**0**（无匹配本批的有效新授权；A 用零网络替身演练真实分支）。

## 0. 整合交付标准逐条

### (1) A 是否已可真实执行
是。上一批 reviewer 复现的三处会损坏交付的 live 缺陷全部合并修好，并用**零网络注入 transport
走真实 live 代码分支**验证（socket 阻断）：

- **A1 付费证据默认保留 + 拒绝覆盖**：`openai_compat`（capture/live/live_stub）产物一律不删；
  live/live_stub 命中已有 `turn_*` 证据目录直接 `PlanError` 拒覆盖（重生成写新目录或走
  `reports_from_batch` 副本，原始输出不动）。守卫：`LIVE_REFUSE_OVERWRITE`。
- **A2 计数来自真实网关事件**：`real_dispatched / stub_dispatched / mock_dispatched` 由
  control 账本 `attempt_started` 统计；真实 live 分支不再报 0，逐 slot 附**实际 attempt_id**
  （非计划标签拷贝）。守卫：`LIVE_BRANCH`（persisted=真、stub=48、real=0、每 slot 有 attempt_id）。
- **A3 整批首发前预检 48**：`execute_plan` 在构造任何 `IsolatedRun` 前用同一
  `AuthorizationBudget.ensure_batch(total_attempts)`；额度不足/已关闭/账本损坏 → 零派发拒绝。
  守卫：`INSUFFICIENT_BATCH`（额度 8 跑 48 计划 → 首发前拒，替身记 0 派发）。
- **A4 联合注入预算门禁改对**：改用候选**自身同一装配**的 `new_seg_est`（mechanism notes）对齐
  1800 码元；跨臂 user 总长差降为诊断 `cross_arm_user_len_diff_*`，不再当门禁（修 reviewer
  "父 5000/候选回忆 2005 却报 added=0" 的误杀）。
- **A5 评审绑定运行快照**：`step_one_turn` 把生成当时消费的 `case_input_sha256` 落进
  manifest；`exam_layered_score` 优先用运行快照、仅历史批次回落工作树并标注来源；报告逐轮
  暴露 `case_input_sha_source`（新批全 `run_snapshot`）。守卫：`REPORT_WIRING` 断言
  snapshot_bound=True（评审不再读活动题面）。

A 演练结果（零网络）：48 次计划序一致可复读、额度不足首发拒绝、失败 5/4/1 保留证据、
真实分支计数来自事件、评审无需活动题面——全绿。**有有效新授权即可直接跑当前 48 次父本/v2
原清单并连续做全量初审。** 本批无授权 → 当前 48 清单标 `待授权`（见
`CALL_MANIFEST_48_20261024.json`），直接转 B—E。

### (2) v3_system_slim 实际状态
- 身份（note 信息边界修正 + format 旧事回扣正向保护后）重冻结：**`c1944e1d931c0747`**（17 文件）。
- 删减范围（UTF-16 码元，非 token）：system 父本 8295 → v3 **2598**（−68.7%）；
  per-asset core 3010→696、naturalness 356→132、redline 465→142、format 4316→1480。
- 公开/私下信息边界：`core.md` 改四类分明（真实消息 / 可公开事实**允许入正文** /
  私下目标情绪只调口吻 / 未发草稿·推测不升级为既成事实），修掉此前"备注一概当私下"
  误伤位置·日程等可公开事实的缺陷；`format.md` 反重复改正向保护（服务当下、带新意的
  旧事回扣鼓励，只挡无关占题与机械复读）。异题材正反例见
  `V3_INFO_BOUNDARY_CASES_20261024.md`（5 例，避考题词，作未来真实语义评审材料，非 mock 成绩）。
- 单变量核验：`tools/test_v3_slim_replay.py` 录制回复重放 **24/24 user prompt 逐字节同、
  三故事记忆写回逐字节同、system 每轮更短**——资产包装器没偷改装配/记忆（2/2 PASS）。
- 待实测主张（未证，留 §4）：删文本后准确性/自然度/主动性/推进/真实 token·延迟/可归因失败。
  见 `V3_SYSTEM_SLIM_FREEZE_20261024.json`。

### (3) 最小 harness 对照 v0_minimal_events
- 已实现真实代码：`candidates/v0_minimal_events/`，自包含简单事件记忆（只存实际发送 HER/ME，
  **生成回复是草稿绝不写回历史**），画像取 `ep_meta.persona`，目标只取当轮 note/intent、
  不建长期意图层；无 topic/ongoing/scene 多层摘要、无自动重估/反思、无辅助生成；
  当前输入 + 当前目标优先，旧史按整条消息单位、UTF-16 码元 ≤1400 裁。
- 复用经 B 修订的精简 system（自带同一份）；共享 `rc.utf16_len/build_system_prompt`
  走父本闭包。params.parent=reference_product_v1，身份闭包**含父本全部文件**（防漂移）。
- 离线夹具 `tools/test_v0_minimal.py` **8/8 PASS**：防漂移守卫(v0 system==v3 system 逐字节)、
  故事内真实消息存+下轮读、故事隔离、snapshot/restore 往返一致且恢复后继续、草稿不入真史、
  关闭/空 note 轮不续存旧目标、40 条长历史下当前输入仍保留且历史段≤预算、notes 报声明单位。
- 真实链加载：mock 适配器下 E001 跑通 8/8（mean 为 mock 伪值，非质量）。身份 **`60bfd8c713917831`**。
- 机制差异表 + 真实实验假设 C1/C2/C3 见 `candidates/v0_minimal_events/MECHANISM.md` 与
  `V0_MINIMAL_FREEZE_20261024.json`。允许 v0 真实对照失败，不为其加回原版层级。

### (4) 新开发题：已完成 vs 规划（分列）
- **已完成并机械校验**：E004/E005/E006 三条故事 × 8 位 = **24 位真实台词** + **24 张逐位评审卡**
  （families/evidence/allowed/failures/contrast/next_check/ambiguities）。作者按 §D 骨架落地，
  末条必 HER、角色仅 HER/ME、ts/clock 真实且单调、含 note/intent/may_decline 与公开/私下边界。
  `import_pilot_questions` 结果 **IMPORT_OK**（episodes=3, requests=24, hard_fail=0, warn=3
  全为中性编号建议）。E004 已可被 harness 加载跑通。文件：`benchmark/episodes/E004-6.json`、
  `benchmark/dev_expansion_v01/03_GRADING_CARDS_E004_E006.json`、报告
  `IMPORT_DEV_EXPANSION_E004_E006.json`。
- **规划未完成**（不冒充已建）：E007–E012 六条（48 位）+ 各自评审卡，达 12 故事/96 位目标。
  当前 A 实跑不等全部扩题；扩题不是当前开测门槛。`NEXT_MAINLINE_BATCH` 的九条骨架为设计种子，
  非已建题——本轮只把前三条做成可导入、已核验的真实题。

### (5) 下一次真实比较：唯一首选方案 + 精确预算 + 评审分工
- **首选实验**：资产删减对照 **v3_system_slim vs reference_product_v1**（同记忆机制、同输出契约，
  仅 system 变量），用**未参与回忆调参的代表性完整故事 E004/E005/E006**。
- **预算**：`24 位 × 两臂 = 48 生成 attempt`，独立授权/独立持久账本，与当前 48 回忆清单分开；
  逐 slot 交错、失败即停、不补配对、首发前整批预检。清单：
  `CALL_MANIFEST_V3_DELETION_20261024.json`（身份父本 `6e65b6…`、v3 `c1944…`）。
- **可选第三臂**：v0_minimal_events（`60bfd8…`）另列独立预算，本首选不含。
- **评审分工**：worker 用 `tools/build_review_worksheet.py` 从真实答卷生成工作表
  （case/arm/run/harness/输入散列/输出散列/槽位/原文/证据/语义标签/互动质量/严重错误/理由），
  **语义与互动质量一律留空待评、绝不预填 pass**；程序分独立；互动维度
  意图执行/承接/主动入口/情绪·调侃分寸/自然可用性，**推进·钩子仅当轮目标适用才评**，
  合法不回与短句不天然低分。独立复复核关键结论与争议；用户口味票
  `pref_vote` 留用户不代投。已对 exam1 真实答卷验证生成器：184 槽行程序分全填、
  语义预填=0（`WORKSHEET_exam1_realrun.json`）；48 规划位全 PENDING（`WORKSHEET_48_planned.json`）。

## 1. 回归（全部零真实调用）

| 套件 | 覆盖 | 结果 |
|---|---|---|
| `exam_interleave_plan --all` | capture/mock/failfast/重复/坏冻结/乱序前驱/live无授权/live无效/live替身/**live分支持久计数**/**整批预检**/**拒覆盖**/报告接线 | 13/13 |
| `test_execute_plan` | 逐 slot 交错/order/bijection/注入门禁/attempt_id/failfast不补/6隔离/live三门/报告/缺散列 | 11/11 |
| `test_annotation_binding` | R1/B(含缺题面散列拒绝)/R2 三态/真实绑 22 rated 21 | 16/16 |
| `test_v2_recall` | 检索 P1-P4/R3 联合预算/UTF-16 截断 | 15/15 |
| `test_v3_slim_replay` | 录制回复单变量：24/24 装配同+记忆写回同 | 2/2 |
| `test_v0_minimal` | 最小 harness 边界 + 防漂移守卫 | 8/8 |
| `import_pilot_questions`(E004-6) | 机械 schema/角色/ts/单调/末HER/卡齐全双向绑定 | IMPORT_OK |

## 2. 局限

离线演练/固定回复只证明"可运行、单变量、边界、指标可解释"，**不等于新生成质量或真实
token/延迟/口味收益**。三+三故事的公开开发集仍非独立充分样本，不能据此宣布新架构/泛化/
降成本成功。真实结论只在 §4 授权内交错实跑 + 逐位人评后给出。
