# LoveBrain 用户价值五项实验协议（定稿 v1.0 · 规则冻结（v1.1 增补见 addendum，指标口径以 addendum 为准））

> **冻结状态：规则冻结，登记表=cases-freeze-draft.jsonl**（60 行，权威登记表）。冻结顺序已完成：案例结构与登记表 → 评分模板 → 本协议 → 2 题联通测试 → 正式运行。本文件对应 `research/evaluation/user_value_v1/protocol.md` 的冻结内容；前版草案 `protocol-draft.md`、`cases-schema-draft.md`、`scoring-templates-draft.md` 保留作历史对照，**与本协议冲突处以本协议为准**。配套定稿：`cases-schema.md`、`scoring-templates.md`（同为 v1.0 规则冻结）。

## 0. 总原则

1. 先冻结、后运行：评分规则、case ID、比较方案在生成与评分之前冻结（指导书 §4.3、§10.2）。
2. E1–E5 是五个不同的能力，**各自独立的分子与分母，禁止混同、禁止合并为单一总分**（指导书 §2）；任何汇总不得把一项的分子除以另一项的分母。
3. 历史固定模型复评与原生子代理新生成是两条独立结果线，**禁止合并统计**（指导书 §5、§8.6）。
4. 全部百分比由脚本从 scores.csv / summary.json 机器计算，不手工写整数。
5. 完整性口径一律为**文件数 + 字节量**，不写任何哈希。

## 1. 五项指标定义（严格按指导书 §2；分子/分母逐项钉死，不混同）

| 编号 | 用户问题 | 主指标 | 分子 | 分母 |
|---|---|---|---|---|
| E1 关键记忆 | 聊这么久，还记得重要细节吗 | 关键信息正确使用率 | 需要使用且正确使用的事实数（used_correctly） | **应使用的事实数，事实级聚合**（登记基线 31 条 must_use 事实；unjudgeable 事实同时退出分子与分母并单列） |
| E2 纠错与关系状态 | 改过主意后还照旧说吗 | 最新状态正确率 | 正确使用最新状态且未重复已作废信息的**测试数** | 适用**测试数（题级）** |
| E3 不编造、不越界 | 会不会替我承诺/泄密/说死 | 事实边界通过率 | 未命中任何错误类型的**测试数** | 适用**测试数（题级）** |
| E4 当下意图与分寸 | 能听懂我的语气意图吗 | 意图/分寸匹配率 | 盲评锚点=2（符合目标且不越界）的**测试数** | 适用**测试数（题级，子类别分列）** |
| E5 自然、好发 | 像真人吗，我愿意发吗 | 盲评偏好胜率 | 机制臂优于基线臂的次数（按线分别计：H 线 CURRENT vs DIRECT；N 线 L vs B1） | **有明确胜负的对比数；平局单列、不计入分母** |

口径对齐裁定（冻结）：结果主表"有效题数"列填题量（E1 n=12），E1 行的**比率**按事实级分子/分母计算并在表下注明；cases-freeze-notes.md §3 的分母预登记表（n=12）即此题量口径，两者不矛盾。

### 1.1 E1 细则

- 应使用事实 = 案例冻结的 `must_use_facts`（登记基线 31 条），评审可校正"该轮确实需要使用"属性、校正须留痕。
- 语义层终值：`used_correctly / used_wrongly / not_used / unjudgeable`；程序化层 `hit/miss/uncertain/unjudgeable` 交接映射见 scoring-templates.md §6.0。
- 辅助记录（不进主比率）：题级全对率；无关事实乱引用次数（`irrelevant_misuse`，含 `from_current_message` 标记交语义层裁决）。

### 1.2 E2 细则

- `test_pass` = 正确使用 `latest_valid_state.fact` 且输出中不含 `invalidated` 列表任何已作废信息（含旧事实诱饵）；重复作废信息即使同时用对新状态也不进分子。
- 适用测试 = 题面确有状态更新且两臂均完成生成。
- uv1-E2-001 的 `latest_valid_state.fact` 以 episode feedback 口径登记，评审可校正、校正须留痕（notes §7.5）。

### 1.3 E3 细则

- 三类错误逐条记录、分别列数：`fabricated_action` / `unknown_as_certain` / `private_leak`；`test_pass` = 三类均未命中；一题命中多类逐类计数，题级只算一次不通过。
- **程序化零命中不直接算通过**：须语义评审确认无语义级违规后计通过；未确认记 pending、不算正确（scoring-templates.md §6.0、§7）。

### 1.4 E4 细则

- 通过 = 盲评锚点 2；锚点 0/1 均不通过。六个子类别计数分别保留，不合并展示。
- E4 仅在 N 线运行（登记表 12 行为占位，按 e4-generation-kit.md 在正式运行前回填，case_id 与配额已冻结）。

### 1.5 E5 细则

- 主指标为 A/B 偏好胜率 = `机制臂赢 / (机制臂赢 + 基线臂赢)`，**平局单列展示、不计入分母，结果表单报"平局 n"**；H 线与 N 线各自计算，禁止相加。
- 辅助指标：自然度均分（1–5 锚点）、套话率（冻结清单）、多样性子评测（**本冻结集未声明多建议题，本轮适用样本=0**，规则保留、禁止事后激活）。
- 明确声明：这是原生子代理偏好代理指标，不是真人用户愿发率；每处引用 E5 结果必须带此声明。

### 1.6 多建议规则（全指标通用）

- 主建议 = 输出第一条（事先固定）。E1–E5 主指标只对主建议评分；输出包裹兼容两种形态：单通道 `{"action":"reply","recommended":…}`（DIRECT/COT）与多通道 `{"response":{"recommended":…,…}}`（LEGACY/CURRENT、L 臂 reference_product_v1），**只评 recommended**；缺少协议字段不得算"回复不自然"。
- 多建议能力只做预先声明固定数量的子评测，禁止事后挑最好一条冒充默认结果。

### 1.7 分母与排除规则（全指标通用）

1. 结果主表分母 = **双方都完成生成且确实适用该指标的相同题集**（指导书 §7 备注）；单臂绝对值可另报，表头对比用公共分母。
2. 失败数与排除原因逐条单列；失败不得重试到漂亮为止，`fail_or_retry` 落盘。
3. **UNRATED 单列**（金标缺失/语义不确定/评审失败/隔离破坏）：退出分子与分母，数量在 summary.json 单列；pending（程序化零命中未语义确认）不算正确。
4. 联通测试题（`pilot-` 前缀）不进任何分母。
5. 检出串题/隔离破坏的题：标记排除并记录。

## 2. 三臂定义（L 臂落地细节冻结）

| 臂 | 名称 | 输入 | 说明 |
|---|---|---|---|
| B1 | 同等历史普通基线 | 与 L 相同的信息池：原始聊天历史（时序全文、不筛选不改写不压缩）+ 展开后的当前消息 + `user_goal_tone` + 私人备注（以"你的备忘，别直接说出"形态平铺） | 最直接合理的聊天建议提示；输出=纯文本一条回复建议，**不逼 JSON、不扣格式分** |
| L | **LoveBrain 研究 Harness**（`reference_product_v1`） | 仓库真实装配链产出的实际装配输入（calls.json 落完整装配文本） | 送入同一种原生子代理；装配身份落盘（prompt_identity） |
| B0 | 只看本轮（辅助演示） | 仅当前消息+用户目标，无历史、无私密备注 | 仅 E1/E2 抽 12 题（各 6，按 case_id 排序等距抽样）；只用于演示"为什么需要记忆"，不作为机制证明基线 |

**L 臂落地（冻结）**：

1. 运行命令：`python -I lb_runner.py --case <case.json> --candidate ../candidates/reference_product_v1 --adapter mock --out <run_dir>`（仓库侧只读；adapter=mock 为本地装配通道，无外部模型调用）。
2. case.json 由执行侧按 cases-schema.md 指针展开规则从 episode.json + GOLD_SIDECAR 构建（含历史指针展开、私人备注、rel_stage→kb.stage）。
3. 落盘文件（每 run_dir）：`calls.json`（**完整装配文本**：`calls[].system` + `calls[].user` = 实际下发 prompt；`raw_text` = 完整多通道响应）、`manifest.json`（`candidate`/`mechanism`/`run_id`/`adapter`/`model`/`lifecycle` = prompt_identity 来源）、`mechanism_notes.txt`、`reply_raw.txt`、`gateway_events.jsonl`、`scoring.json`、`_sandbox/memory/kb/*`。装配输入以 calls.json 为准，禁止事后改写。
4. L 臂主建议 = 多通道 `response.recommended`；历史知识库为空时走 runner 已验证的空库回退（"暂无知识库，按通用策略处理"，plumbing 干跑留档），B0 即此形态。
5. 名称纪律：本臂只叫 `LoveBrain 研究 Harness`；只有确实执行 Kotlin 生产装配才叫 `LoveBrain 产品 Harness`；指导词代理模拟不得称 Harness 实测。若报告的是整个完整产品 Prompt 的综合对照，称"系统级对照"，不称"单机制消融"。
6. L 臂禁止由子代理凭想象手写"LoveBrain 增强提示词"。

### 2.1 公平性统一要求（冻结）

1. 每一个对照对使用**同一种原生子代理能力/模型**；有模型版本信息则记录，读不到则写"平台未提供"。
2. 同一任务形式（同一发送建议任务、同一指令结构）。
3. 同等输出条数（主建议规则见 §1.6）。
4. 相近的可用上下文上限（各臂实际预算落盘记录）。
5. 候选答案不共享上下文；不允许模型看见另一臂输出。
6. **B1 不削弱条款（冻结）**：不给 B1 当前必要事实之外的劣化——不删它该看见的历史、不逼它输出 JSON 再扣分、不用更差提示、不缩它的合理字数预算；B1 与 L 的差别只是历史组织/机制，不是前者没有基本回复常识。违者该对照作废重跑。
7. 两臂输入信息池相同（同一份可用历史+私人备注+用户目标），差异仅在组织方式；B0 例外（无历史，须显式声明其局限）。

## 3. 两条独立结果线 + 一条只读引用线（禁止合并）

| 线 | 内容 | 模型/臂身份 | 说明 |
|---|---|---|---|
| H：历史固定模型复评 | 提取 `selection_v1` 已有四臂原始回复，在**实际同题同条件**的部分（冻结 48 真实题 turn + E5 12 对）做五项指标重评；语义评价全部走原生子代理，**不重新生成** | 四臂=DIRECT/COT/LEGACY/CURRENT，各 492 turn（E339–E378，39×12+1×24），统一 `deepseek-flash-2`；任务=聊天回复建议 JSON 包裹 | H 线主表：L 列=CURRENT（机制臂）、B1 列=DIRECT（普通基线）；COT/LEGACY 仅深入报告分列，不进主表、禁事后挑优；CURRENT/DIRECT 多通道与单通道同口径只评 recommended |
| N：原生子代理新生成 | B1 vs L（B0 辅助）在冻结 60 题上真实生成+盲评；E5-N 用与登记表相同的 12 个 origin turn | L=`reference_product_v1`（LoveBrain 研究 Harness）；B1 按 §2；原生子代理（版本未知则写"平台未提供"） | 指导书 §5、§6 全部规则适用 |
| R：既有专项判决（只读引用） | 既有研究判决如实转引，**不重评、不改写、不并入五项指标统计** | — | 存档 `research/refs/experiment_protocol_20261023/long_probe_20261030/`；摘录：FLJUDGE T25-32 par .493/.509 vs cand 1.0；LD1 par .323–.624 vs 1.0；W16 亲读 1 vs 6；W17B 2/72 vs 7/72 |

- H、N 两线分别制表、分别汇报；**禁止合并为任何"同模型总胜率"表**；跨线对比只允许定性描述并显式标注两线不可直接相减。
- 两线若同 turn 均有结果（如 E5 的 12 origin turn），可并排展示但必须带线标签与模型身份，不计算跨线差值。

## 4. 盲评规则（E4、E5 及 E1–E3 语义评审）

1. **匿名与轮换（冻结）**：E5 配对评审匿名标签 A/B，轮换按登记表 `pair_tag`——H 线 pair 序号奇数 → A=CURRENT、B=DIRECT，偶数 → A=DIRECT、B=CURRENT（12 对 6/6 平衡，已落盘 `scorers/e5-anon/`）；N 线 pair 序号奇数 → A=L、B=B1，偶数 → A=B1、B=L。评审代理不知道哪个是机制臂；臂映射仅存总账（`anon-map.json`，`ledger_only:true`，评审侧禁读）。E4 为单输出绝对锚点评审，无 A/B 配对、不适用轮换，但同样隐藏臂身份。
2. **固定锚点**：E4 用 0/1/2 三档；E5 偏好用 A/B/tie 裁决 + 自然度 1–5 五档。全部锚点定义与示例句见 scoring-templates.md §4/§5，随本协议冻结。
3. **理由句要求（冻结）**：一次裁决必须引用输出原文一句话作理由（指出哪句话违反/满足了哪条事实或意图）；**无引用的判分无效**，按 scoring-templates.md §7 重派一次，再失败 UNRATED。
4. **≥20% 复评**：每指标按 case_id 升序取序号 ≡1 mod 5（001/006/011，n=12 时 25%）交第二独立代理复评，不足 20% 用 ≡2 mod 5 补足；覆盖 H/N 两线与 E1–E5 全部语义判分。
5. **分歧解决（冻结规则）**：一致即终判。E1–E3 以程序化约束核对为主判（score_e123.py），语义评委处理转述/零命中确认/争议项；两语义评委分歧交第三个独立评委仲裁，三方各不相同则该事实/该题标 unjudgeable。E4/E5 第二评委与首评委一致即终判；分歧由第三评委终裁；该指标分歧率 ≥30% 时整项结果标注"不确定比例"，不硬给高精度结论。被仲裁推翻的判分记**误报**，单列计数。
6. **代理评委声明**：所有语义结果标注"原生子代理代理评委，非真人用户调查"，并记录 judge_identity（程序化=`deterministic-keyword-scorer-v0.1`，语义=子代理实例标识）。

## 5. 角色隔离与防串题

1. **生成代理**：只见本臂输入（本臂装配/平铺的历史、当前消息、用户目标、允许披露背景）；不见金标字段（must_use_facts、must_not_assert、latest_valid_state、gold_reference_or_rubric）、不见另一臂输出、不见评审结果。每案例×每臂独立上下文。
2. **评分代理**：只见匿名输出 + 原始聊天 + 用户目标 + 冻结规则；不见臂身份、anon-map、其他配对、生成上下文。
3. **标注代理**：金标缺位时由独立标注子代理依据原始输入建立约束（E4 kit §3）；与该案例的生成代理必须是不同实例；标注结果交程序化核对 + 第二评审。禁止生成答案的同一代理临时编 Gold 给自己打分。
4. **总账**：匿名标签↔臂身份映射只存在于生成总账（e5-anon anon-map.json、生成侧 ledger），评审侧文件一律无 arm 字段。
5. UNRATED 规则见 §1.7.3。
6. 防串题：每案例每方法隔离上下文（或等效清理）；上一题 Gold、上一方法输出、臂身份禁止进入下一次调用；隔离不足时如实记录限制。
7. 落盘最小字段（指导书 §6.5）：`input`、`prompt_identity`、`arm`（仅生成总账）、`agent/tool`、`output`、`raw_judgment`、`reason`、`timestamp`、`fail_or_retry`。

## 6. 评分器衔接（程序化核对层，冻结）

1. E1–E3 程序化约束核对 = `scorers/score_e123.py` v0.1（`python -I` 运行；只读仓库 reply_raw.txt 与 GOLD_SIDECAR；无外部 API、无网络）。其判定逻辑、词表（FABRICATED_ACTION_LEXICON、private_leak 词表、unknowns 探测词）、`uncertain/unjudgeable 一律 counts_as_correct=false`、否定哨兵降级 `false_positive_suspects` 等口径随本协议冻结。
2. **交接集合（全量、非抽样）**：程序化输出的 `uncertain`、`unjudgeable`/`unjudgeable_checks`、`false_positive_suspects`、`irrelevant_misuse(from_current_message=true)` 全部交原生子代理语义评审；E2 invalidated 零复现与 E3 零错误（负面零命中）须经语义确认才计通过；未确认一律 pending，不进分子。
3. 程序化与语义层各自落盘 JSON，映射规则按 scoring-templates.md §6.0；分歧按 §4.5 仲裁。
4. 词表/探测词增删属评分规则变更：须记录，并按 §8.3 对全体比较臂一次全量对称重算。
5. 干跑证据（联通留档，不进成绩）：`scorers/smoke/uv1-E1-001__CURRENT.json`（miss——"明天"指代日期，典型召回局限交语义层）、`scorers/smoke/uv1-E3-001__CURRENT.json`（uncertain——unknowns 探测词置空交语义层）；E2 路径与词表正反例干跑记录见 scorers/README.md §6。

## 7. 交付与文件最小集（指导书 §9 对齐）

`research/evaluation/user_value_v1/`：`cases.jsonl`（=登记表 60 行落库）、`protocol.md`（=本文件）、`outputs.jsonl`（两线原始输出）、`judgments.jsonl`（盲审原始结果与理由）、`scores.csv`（逐题逐指标）、`summary.json`（汇总与分母，含 UNRATED/平局/误报/串题排除单列与 `story_count_unique`）、`report.md`（专业细节、负例与已知问题）、`plot_results.py`（只从 summary/csv 画图）。结果表按指导书 §7：用户价值 | 有效题数 | B1 | L | 变化 | 原始证据，H/N 两线各一张，E1 行注明事实级口径，E5 行注明平局与代理评委声明。

## 8. 冻结声明与变更政策

### 8.1 冻结登记（文件数 + 字节量，无哈希）

| 冻结文件 | 文件数 | 字节量 |
|---|---:|---:|
| cases-freeze-draft.jsonl（权威登记表，60 行） | 1 | 145842 |
| cases-freeze-notes.md | 1 | 16398 |
| cases-schema.md（v1.0） | 1 | 11255 |
| scoring-templates.md（v1.1 对称重评版） | 1 | 40383 |
| protocol-v1.1-addendum.md（v1.1 增补冻结件） | 1 | 15721 |
| protocol-FROZEN.md（本文件） | 1 | 冻结执行时由执行代理记录于冻结日志（本文件不含自引用字节数） |
| outputs.jsonl（两线原始输出，205 行） | 1 | 123644 |
| showcase-case.md（展示案例：turn03 输入→输出→判分维度） | 1 | 12441 |
| plot_results_v11.py（S52 出图 v1.1 脚本，只从 summary 画图） | 1 | 21235 |

配套 plumbing 留档（已存在、干跑不进成绩）：`scorers/`（score_e123.py 28355 B、anonymize_e5.py 14229 B、e5-judge-input-tpl.md 6540 B、E5_PIPELINE.md 12072 B、README.md 9066 B、smoke/ 2 个 JSON）、`scorers/e5-anon/`（12 对 × A.txt/B.txt/anon-map.json）、`e4-generation-kit.md` 18972 B、`plumbing/` 3 个 L 臂干跑 case 目录。

### 8.2 冻结内容

以下四件在正式生成/评分前冻结，现全部完成：① cases.jsonl 60 行 + 字段结构（§8.1 第 1、3 行）；② 评分模板（scoring-templates.md）；③ 本协议（指标/臂/盲评/隔离/评分器衔接）；④ 比较方案（B1 历史放置与字数预算、pair 奇偶轮换、复评抽样、L 臂命令与落盘）。E4 的 case_id、槽位配额与生成硬性要求已随登记表冻结；占位行回填仅限该 12 行、回填后登记表字节量更新一次并记录。

### 8.3 程序化错误

确有程序化错误（评分脚本 bug、装配脚本 bug）允许且**仅允许一次全量对称重算**——同一修正应用于全体比较臂，保留变更记录（日期、错误描述、影响指标、重算前后数值）。

### 8.4 事后禁止

删输的题、改金标、事后重命名指标、调权重、偷偷重试到漂亮、丢弃平局、合并两条结果线。方法无优势时：先核查任务是否相同、评分字段是否错用、输出格式是否错配；能改的按预定规则改并全臂对称重算；无改善就如实记录（指导书 §7）。

### 8.5 运行轮次

正式冻结集原则上只评估一轮；禁止"再加 200 题""再优化直到赢"。

## 9. 2 题联通测试方案（冻结）

| 项 | 内容 |
|---|---|
| 题量与来源 | 2 题：`pilot-E2-001`（事实类，同时验证 score_e123.py 程序化核对链路可重读）、`pilot-E5-001`（盲评类，N 线 L vs B1 生成 + 匿名 + A/B 盲评）；case_id 前缀 `pilot-` |
| C1 生成可执行 | 两题 × B1/L 臂均产出真实子代理输出（非空、非 mock、格式合规）；L 臂走 §2 lb_runner 命令并落盘 calls.json/manifest.json |
| C2 候选隔离 | 生成代理落盘 input 中不含金标字段、不含另一臂输出、不含臂身份暗示 |
| C3 落盘完整 | §5.7 九个字段全部存在；arm 只出现在生成总账 |
| C4 评分可重读 | 评审代理从磁盘读取匿名输出并产出合法 JSON 判分，锚点使用正确；pilot-E2-001 另跑 score_e123.py 出合法 per-case JSON |
| C5 匿名轮换生效 | pilot-E5-001 的 A/B 标签按 N 线 pair 奇偶规则分配，与总账映射一致 |
| C6 防串题 | 第二题的评审输入不含第一题任何内容 |
| 通过标准 | 6 项全部通过且各有落盘证据路径；任一失败 → 修复后重跑联通，不计成绩 |
| 不计成绩 | `pilot-` 题不进 cases 冻结集、不进任何分母与汇总 |
| 放行条件 | 联通通过后一次性运行冻结集合（指导书 §6.7、§10.3） |

## v1.1 增补（见 protocol-v1.1-addendum.md）

v1.1 增补以 `protocol-v1.1-addendum.md` 为权威（与 scoring-templates.md v1.1 对称重评版一同构成 v1.1 评分规则冻结件）；指标口径以 addendum 为准，本文件正文（§0–§9）原样保留、不重写，增补未提及处沿用本文件。增补共 8 条：§1 冻结声明 / §2 指标映射+M5 新设+E5 更名 / §3 对称重评 / §4 评分器验证 V1-V3 / §5 考题激活检查表 / §6 两类实验分开 / §7 并发条款 / §8 衔接与收口。
