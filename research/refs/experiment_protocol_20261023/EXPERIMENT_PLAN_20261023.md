# LoveBrain · 合并工作包 · 冻结实验清单与预登记 2026-10-23 (rev R5.2)

本文件是父本 `reference_product_v1` 与候选 `v2_recall` 进入真实两臂对照前的**冻结规范**。
所有主假设、指标、退化保护、可接受差异、运行顺序与停止条件均在此预登记；
未经本清单交付并交独立会话复核，**不得发出任何真实生成调用**。

> R5.2 修订要点（相对旧 v1）：
> (a) **配对基线**：候选必须与**同批新跑的父本**逐位对照，不再拿旧 24 位答卷上的
>     0.87 合格率当"父本基线"——旧答卷产生自 R1/R3/R5.1 修好前的失败实现，
>     其绝对数不可跨批沿用；
> (b) **边缘 ≠ 正确回忆**：edge 只代表"接近通过但未达 pass 判据"，H1 判决要
>     pass 才算数；
> (c) **其余 23 位真实分布**（R1 hash-bound + B 归属修正后）：20 pass + 0 edge
>     + 1 fail（E001_T06）+ 3 unrated（E003_T02 格式坏 + E001_T04/E003_T04 首选未独立复核）；
>     候选必须对齐这 23 位；
> (d) **+20% token / +15% p95 时延**降级为**工程警戒线**（超线只触警报、
>     不构成 H1 拒绝）；主指标仍是逐位语义配对；
> (e) **交错顺序预固定**：E001 父先子后、E002 子先父后、E003 父先子后，
>     删除"（或反向）"含糊；顺序落 `logs/exam2_*/INTERLEAVE_PLAN.json`；
> (f) **无自动重试、无补跑**：一次 attempt = 一次调用；失败与解析错都留在分母；
>     ~~两臂同位一并重跑~~ 作废（见下方 ABC 收口：补跑另列独立预算=重复采样）；
> (g) **fail-fast 传输错**：任何一次网关 4xx/5xx/超时/连接重置都停批并汇报，
>     旧"连续 5 次 5xx"阈值删除。
>
> **第二轮 ABC 收口（2026-10-23 独立复核 r1r5_independent_review）**：
> (A) **演练接回正式执行链**：`tools/exam_interleave_plan.py::execute_plan` 改用
>     `IsolatedRun`（宿主+TrustedProxy+ModelGateway attempt 账本+可信侧评分+泄漏/白名单边界），
>     mock/capture/live 共用同一入口只换适配器；派发前 `validate_plan`（唯一/对应/前驱/禁重复缺轮）
>     与 `verify_frozen_identity`（候选闭包 + 题面/评分卡/评分器/执行器/工具/配置逐文件比对）
>     任一不过 → 零派发拒；fail-fast 演练账本 = 5 started/4 completed/1 failed 且不补另一臂；
>     状态隔离用 6 个独立 run_id + 隔离哨兵证明，新增段预算从 calls.json 真实出站 prompt 测。
> (B) **批注上下文绑定**：`_match_annotation` 除文本/槽位外，再校验 run_id + harness_identity +
>     题面内容散列（case_input_sha256）；缺任一绑定=上下文未验证→拒、不跨批静默复用；
>     修正 E001_T04/E003_T04 把备选槽判词移给首选的旧记录（primary→unrated，provenance 留痕），
>     primary_coverage_ratio 因此 0.958→0.875，不再当有效首选审查覆盖。
> (C) **UTF-16 预算统一**：候选侧截断/计数全部按 UTF-16 码元、不切 emoji 代理对；
>     reviewer 的 1200-emoji run_turn 反例（旧留 2400 码元 > 1800）已修，最终新增段实测 ≤ 1800。
> 计划四句同步：候选不得看父本输出（顺序仅调度）；两臂同 pass 记"未观察到优势"、
> 不得改用输入侧断言补 H1；新批逐槽重审、低覆盖不得宣布未退化、未批注不是途中停批错误；
> 48 上限无重试无补跑、严重错误口径统一为"不增"、工程警戒线与用户交易分开。

## 0. 一句话

**唯一主变量 = M2 回忆型检索。** 父本与候选在**同题、同评分器、同端点、同采样**下
按预固定顺序交错跑，比较 E001_T06 从 fail→pass 且其余 23 位不劣化；
证据不越层解读，泛化不越权宣称。

## 1. 冻结身份（本批 · R5.1 closure）

身份由 `tools/freeze_identity.py` 生成，`harness_identity` 已按 R5.1 修好：
候选 identity = 自身目录 + 递归 parent 目录（`params.json.parent`）合并，
父本任何字节改动会同时翻转两臂 identity。

| 对象 | identity_sha256 前 16 | 闭包 | 内容 |
|---|---|---|---|
| 父本 `reference_product_v1` | `6e65b6bced2c2e3f` | [self] | mechanism.py + reference_chain.py + product_memory.py + params.json + assets/system_prompt/*.md（core/naturalness_check/redline/format）+ IDENTITY.md |
| 候选 `v2_recall` | `36dc54d6220c8099` | [v2_recall, reference_product_v1] | 父本 9 文件 + v2 mechanism.py（P1-P4 四保护 + R3 联合预算 `_fit_new_context` + **C 收口 utf16_truncate_* 码元截断**）+ v2 params.json (role=candidate, parent=reference_product_v1) + v2 侧 IDENTITY.md/MECHANISM.md + v2 assets 与父本一致 |
| 评分器 | 见 §1.1 表 | — | arena/lb_scorer.py（v0.5 + R4 note_lcs 降权）+ arena/protocol_public.py |
| 语义批注 | `benchmark/annotations/exam1_20261023_semantic.json` | — | R1 hash-bound + **B 上下文三绑**：case_id + slot + raw_reply_sha256 + slot_text_sha256 + **run_id + harness_identity + case_input_sha256（题面内容）** 全一致才命中；文本/题面/run/身份任一变 → 批注失效 → unrated；不跨批静默复用 |
| 题集 | dev_pilot_v0.2_20261022 | — | E001/E002/E003 各 8 位；24 卡全部 `dev_exposed`；`gold={}`、`required_reply_phrase=null` |
| 模型服务 | dsf `http://127.0.0.1:17770/v1` | — | temperature=0.7 固定；model 名 `dsf` |
| 评分协议 | benchmark/rubric.md v0.5 | — | AGENTS v1.4；note_lcs 权重 0 / 阈值 0.9；style_diversity 保留旧语义 |
| Layered score 工具 | `tools/exam_layered_score.py` | — | 逐槽 primary/backup + R2 三态汇总 + 覆盖率 + `annotations_sufficient_for_gate` |
| 离线重评工具 | `tools/rescore_offline.py` | — | 用于旧答卷新尺重评，不重烧 API |
| v2 测试套件 | `tools/test_v2_recall.py` | — | 12 checks（c1-c11 沿用 + c12 reviewer 20-extras 反例） |
| Baseline parity extra | `tools/test_baseline_parity_extra.py` | — | 10 checks（by_blocks + 纠正过滤 + timestamp）+ e11 emoji 边界（§7） |
| 语义绑定测试 | `tools/test_annotation_binding.py` | — | 9 checks：R1 四种篡改反例 + 无批注文件全 unrated + R2 三态与覆盖率 |
| Identity closure 测试 | `tools/test_identity_closure.py` | — | 6 checks：closure 顺序 / 父本 ⊆ 候选 / 父本字节变→候选 identity 变 / 候选自身变 / params.role=candidate+parent / 父本 closure 只含 self |
| note_lcs 诊断测试 | `tools/test_note_lcs_diagnostic.py` | — | 4 checks：合法引用 / 短文本豁免 / 整句照搬（hit 不扣分）/ 语义泄漏（LCS 不判） |

具体 sha256 前缀 16 位由 `logs/identity_freeze_<ts>.json` 在启动前生成并落盘；
本表中的 identity 是**离线冻结当前工作树**的值，任何后续改动都必须在下一份 freeze
里重新登记并交独立复核。

### §1.1 关键工具与资产的当前 sha256（前 16 位）

- `arena/lb_scorer.py`：见 `logs/identity_freeze_R51.json` 中 `arena` 未列（工具不入 candidate bundle），单独以文件哈希管理
- `tools/exam_layered_score.py`：`166252230d1908e4`… → 由本批重写覆盖，运行时 freeze 会重新登记
- 详细值以工具运行时的 `identity_freeze_<ts>.json` 为唯一事实来源

旧授权 `AUTH-EXAM1-20261023-01` 已关闭（24/24 用尽，账本尾行 `kind=authorization_closed`）；
本清单不复活旧额度、不新建账户、不改系统权限。

## 2. 主假设（预登记）

**H1（回忆检索）**：候选 `v2_recall` 在"回忆型提问位"（本 pilot 只有 E001_T06）
上的**主槽语义从 fail 升为 pass**；同时其余 23 位与**同批父本逐位对照**不劣化。

**判决规则**（严格）：
- pass 才算命中；**edge 不算成功回忆**（edge 表示"接近但语义判据未满足"，
  在旧批次常见形态是候选回答方向对但未接上具体事实）；
- 若同批父本 E001_T06 fail、候选升 pass → H1 支持；
- **若同批两臂都 pass → 该位记"未观察到候选优势"，H1 不因该位获得支持**；
  **不得**把 H1 转移成"事实进入输入/prompt 断言"来替代最终回复改善、硬凑一个成功结论。
  输入送达（机制层证据，c1/c4 类）与最终回复改善（结果层，逐位语义）严格分层：
  前者只证明"检索把事实递到了 prompt"，后者才是 H1 要证的"回复变好"；
- 若两臂同批都 fail → H1 拒绝（v2 主变量未生效）。

**机制**：v2 只在 `is_recall_turn=True` 且归档召回非空时追加【回忆】段；
`RecallSelector` 在回忆轮用 bigram 相关性从 `allItems` 挑相关事项（不整库灌入）；
`_fit_new_context` 按 **UTF-16 码元**统一预算，extras + recall body + 段头/提示/分隔
合计 ≤ `MAX_NEW_CONTEXT_CHARS=1800`（截断用 `utf16_truncate_*`，不切 emoji 代理对；
C 收口，取代旧 `OVERHEAD_RESERVE_CHARS` 魔法数）；非回忆轮与父本逐字节相同（c5 已证）。

**预期代价**：新增段增加 prompt 字符数 → 输入 token 略升；无输出 token 差异；
延迟与父本同级（未新增真实模型调用）。E001_T06 因 prompt 加长 300~1200 字，输入
token 上升幅度**预估 <20%（工程警戒线，不预登记为 H1 拒绝线）**。

## 3. 可接受差异与退化保护（预登记 · 以**同批父本**为基线）

### §3.1 主指标（H1 判决线）

| 指标 | 父本预期（**本批同跑**） | 候选预期 | 判决 |
|---|---|---|---|
| E001_T06 primary_semantic | 实测（不预设） | **pass** 且父本 fail | H1 支持 |
| 其余 23 位 primary_semantic 逐位对照 | 实测 | 允许 pass 保持 pass；不允许新增 fail | 若新增 fail → H1 拒绝 |

旧 24 位答卷的 R1 hash-bound 分布（供参考，**不作分臂基线**）：
20 pass + 2 edge + 1 fail（E001_T06）+ 1 unrated（E003_T02 parse_error）；
其余 23 位 = 20 pass + 2 edge + 1 unrated。**注意**：此分布产生于 R1/R3/R5 修好前的旧实现，
本批必须**重跑父本**取同批分布作配对基线；旧数字仅供 sanity check。

### §3.2 退化保护（**全部**同时通过才算不劣化）

1. **程序分**：每位 `program_score10` 不低于同批父本 -1.0；veto 数不增。
2. **严重错误备选（口径统一）**：`backup_with_severe_error == "true"` 位次数
   **不高于同批父本**——"严重错误不增"是唯一判据，取消旧"允许 +1"的含糊说法。
   父本基线为 0 则候选必须为 0；父本为 k 则候选 ≤ k；超出触发 H1 拒绝线。
3. **主槽 pass 数**（严格）：候选 `primary_semantic_pass` 数 ≥ 同批父本；
   edge 不计入 pass。
4. **至少一个合格候选**（R2 三态）：`at_least_one_pass_true` 数 ≥ 同批父本；
   `at_least_one_pass_false` 数 ≤ 同批父本。`unknown` 位（未评 backup）
   不进分母，只算 `backup_slot_coverage_ratio`。
5. **新批逐槽语义重审 + 覆盖率（不因旧批低覆盖而免评）**：
   - 新批次答卷**必须重新做逐槽语义审查**（primary + backup 分槽）；旧 160 backup
     槽未评**不是**新批次自动免评的理由；
   - 允许"先收集数据、批后集中审"——**"尚未批注"不得作为生成途中必须停批的错误**；
   - `unknown` 可如实保留，但**不得据低覆盖宣布"未退化"或"可晋级"**；
   - `primary_coverage_ratio` = 已评 primary / 总位；`backup_slot_coverage_ratio` =
     已评 backup / 总 backup 槽；晋级判定只以**已逐槽审过**的位次为准，未审位次计入分母记 unrated；
   - `annotations_sufficient_for_gate`（primary 全覆盖 **且** backup 全覆盖才 True）为
     False 时（本批默认如此，160 backup 槽仅 1 位 severe 批注），
     `severe_error_backup_lower_bound_rate`、`*_rate_on_decided` 只能标"下界 / 已判样本 / unknown"，
     **绝不写"通过率 X%"当权威结论**。

### §3.3 工程警戒线 vs 最终用户交易（**两者分开，不混称**）

**工程警戒线**（超线只触警报，**不作为 H1 拒绝线、也不自动等于用户已接受**）：

- **W1 · 输入 token**：episode 级 prompt token 相对同批父本上升 > **+20%** → WARNING；
- **W2 · p95 时延**：episode 级 p95 相对同批父本上升 > **+15%** → WARNING
  （本批不报首字，网关非流式）；
- **W3 · 新增段预算（单位统一，取消"1800 vs 2200"两套数）**：候选每轮从
  **真实最终请求**（calls.json 的出站 user prompt）测得的新增段
  （【进行中事项】v2 extras 行 + 【回忆】段 + 段头/提示/分隔，UTF-16 码元）
  必须 ≤ **`MAX_NEW_CONTEXT_CHARS = 1800`**（这就是唯一真实上限，截断按码元、不切 emoji 代理对）。
  覆盖：c12（20-extras）、c13（emoji 单位）、c14（1200-emoji run_turn）、
  c15（BMP+emoji 共同挤占）；execute_plan 从 calls.json 实测 `newseg_max_utf16`。
  装配期或演练期越界 → 不发真实调用。

**最终用户接受的交易**（性质不同，须用户显式裁定，工程侧不自批）：
主指标 §3.1 若 E001_T06 fail→pass 成立、却触发 W1/W2 的成本，是否作为"质量换成本"接受，
由用户裁定。**工程警戒线越线既不自动等于 H1 失败，也不自动等于用户已接受**。

### §3.4 保护性回归（每次冻结必过，不过不发调用）

- Baseline parity 26/26 + extra 11/11（含 e11 emoji 边界）+ carryover 9/9；
- v2 recall 15/15（c6-c9 P1-P4；c12 20-extras；c13-c15 C emoji 单位/共同挤占）；
- Annotation binding 15/15（R1 四反例 + R2 三态与覆盖率 + B 上下文三绑五反例）；
- Note lcs diagnostic 4/4（R4 权重 0 边界）；
- Identity closure 6/6（R5.1 闭包）；
- Execute plan 8/8（A：capture/mock 走 IsolatedRun 真实账本 48/48、fail-fast 5/4/1
  不补配对、重复/缺轮/错误冻结身份派发前拒、6 独立 run_id 隔离、live 无授权拒发、real_calls=0）。

## 4. 运行顺序与交错（**预固定，不发批次前落盘**）

交错顺序写死到 `logs/exam2_202610XX/INTERLEAVE_PLAN.json`，48 attempts 上限。
**不允许在批次里"或反向"**——顺序由本节预定：

- **E001**：**父先子后**（P, C, P, C, …）
- **E002**：**子先父后**（C, P, C, P, …）
- **E003**：**父先子后**（P, C, P, C, …）

理由：E002 换先手降"两个 episode 同一臂连续跑冷启动"混杂。**先后顺序只影响调度时段，
不构成任何信息可见性**——任何一臂都**不得**看到另一臂的输出或状态；两臂各自维护独立的
product-memory 状态，逐 episode 完全隔离（A 收口：execute_plan 用独立 run_id/out/control
目录 + 隔离哨兵证明互不串）。旧表述"使候选始终能看到父本同题输出"作废删除。

**批次排布（示例，具体以 INTERLEAVE_PLAN.json 为准）**：

| 序号 | 位置 | 臂 | 备注 |
|---|---|---|---|
| 1 | E001_T01 | 父 | 建立初始记忆 |
| 2 | E001_T01 | 子 | 同输入；候选独立 state |
| 3 | E001_T02 | 父 | |
| 4 | E001_T02 | 子 | |
| … | … | … | T01-T08 逐轮 P→C 相邻 |
| 17 | E002_T01 | 子 | E002 换先手 |
| 18 | E002_T01 | 父 | |
| … | … | … | |
| 33 | E003_T01 | 父 | E003 恢复父先 |
| … | … | … | |

**状态与配对约束**：
- 两臂在同一 episode 内**各自维护独立的 product-memory 状态**，绝不共享；
- 每对相邻 (P, C) attempts 视为一次"配对采样"；跨 episode 不做配对；
- 失败/解析错**不从分母删除**（协议 §6）；
- **48 次硬上限内无重试、无补跑**：一次调用 = 一次 attempt；任何传输错按 §5 立即停批，
  **不为"保配对"补跑另一臂**（旧"两臂同位一并重跑"表述作废——它会把补跑混进同一预算）。
  失败停批后，同批 execute_plan 的账本如实记 started/completed/failed（A 收口：
  fail-fast 演练 = 5 started / 4 completed / 1 failed，candidate 未补跑）；
- **重复采样另列**：若日后确需对某位重复采样以降方差，须作为**独立预算、独立前史状态**
  另开"重复采样"批次（显式登记所需 attempts 与前置记忆快照），不占用本 48 次硬上限，
  也不与主批混算；
- 每次 attempt 落 `logs/exam2_202610XX/{parent|candidate}/turn_XX_<case_id>/`
  含 `calls.json / manifest.json / scoring.json / scoring_v05.json`。

## 5. 停止条件（预登记）

任一命中即**停批并汇报**，不"跑到显著为止"：

- 累计 attempts 达授权上限（预期 48，具体以新授权书为准）；
- 授权账本 `remaining` 归零或 `authorization_closed`；
- **任何一次**传输错（HTTP 4xx/5xx、连接重置、超时、body 非 JSON）→
  立即停批并汇报，**不重试、不"连续 5 次"**；
- **任何一次**同臂程序 veto（json_schema_broken / illegal_source_id / unauthorized_no_reply）
  与旧基线不同 → 疑似移植或装配破坏，暂停诊断；
- **注意**：批注未评 / 覆盖率低**不是**生成途中的停批条件（允许先收集后集中审）；
  它只影响停批后的**晋级判定分母口径**（§3.2 第 5 条），不在批次运行中触发停止；
- **P1-P4 任一**在真实调用回归中出现（例：无关 DORMANT 灌入的 prompt 现场可见）→
  立刻停；
- 新增段现场越界（calls.json 实测 recall 段 UTF-16 > `MAX_NEW_CONTEXT_CHARS=1800`，
  或 final_prompt extras 行数 > `MAX_ONGOING_EXTRA_LINES`）→ 立刻停。

## 6. 停止后必出的产出

- `logs/exam2_202610XX/` 全部 attempts 原始 `calls.json / manifest.json / scoring.json`；
- `LAYERED_SCORE_v05_parent.json` 与 `LAYERED_SCORE_v05_candidate.json`（分臂 + R2 三态 + 覆盖率）；
- `PAIRED_DIFF.md`：逐位父→子 program / primary_semantic / backup severe / tokens / latency 变化；
- `EXAM_REPORT_v2.md`：三汇总指标 + 故事级差异 + 是否命中停止条件；
- `EVIDENCE_BOUNDARY.md`：本次能证的层级（协议 §1 1-5 中的哪一级），**不越级宣称**；
- `INTERLEAVE_PLAN.json` 实际执行序 vs 预定序的 diff（应为空）。

## 7. 本清单**不承诺**的事

- 不承诺"24 位足以证明泛化"（协议 §5.4：3 故事嵌套，24 位非独立样本；
  重复跑同 3 故事只增采样稳定性证据）；
- 不承诺"程序 9.31 → 9.8 就是候选改进"（v0.5 尺子变化不算收益；见 AGENTS v1.4 ④）；
- 不承诺覆盖 FactSubjectResolver / WAL / 后台重估 / 谈心模式 / 主动开场 / 润色
  （本批**主动限定为前台回复、排除后台**——见 BASELINE_PARITY §8 与 §7 说明）；
- 不承诺"输入侧修好了 → 回复会变好"（这是**协议 §1 层级 2 到层级 3 之间**的差距，
  必须实跑才能进入层级 3）；
- 不承诺 judge 校准；本批语义判决仍以**人工监督**为主，judge 只辅助筛查；
- 不承诺"批注全评完"：本批 160 backup 槽仅 1 位有 severe 批注（E003_T04 directions[2]），
  汇总数字必须显式标 lower bound / unknown。

## 8. 需要用户裁定的事（不自动决定）

- 若候选在 E001_T06 从 fail→pass 但触发 §3.3 工程警戒线 W1 或 W2：
  是否作为交易接受？
- 若两臂出现同一位一次 pass 一次 fail 的采样噪声翻转：本批 48 次硬上限内**不补第三臂、
  不重试**（§4）；若要降方差，须由用户批准另开"重复采样"批次（独立预算 + 独立前史状态），
  不与主批 48 次混算；
- 若扩题（协议 §5.5）需要另 48 attempts：预算分配与故事覆盖优先级由用户先定，
  不由工程侧推。
- 若某批 backup 槽人工补评（把 unrated 变 pass/fail）需要重跑 layered score：
  批注文件的 sha256 变化必须在 freeze 中重新登记。

## 9. 授权条款（重申）

- 本清单**不增加**任何真实调用额度；
- 无匹配本批的有效授权时，worker 只做：a) 完成离线冻结；b) 提交逐项调用清单；c) 等待。
  **不冒烟、不试探、不复活旧单**；
- 本清单本身不是授权文件；实际调用需 `AUTH-EXAM2-*` 新授权；
- 授权范围一旦包含 "48 attempts / 24 位×2 臂 / 交错" 三条即足以覆盖本批；
  不含其一不得发出。
