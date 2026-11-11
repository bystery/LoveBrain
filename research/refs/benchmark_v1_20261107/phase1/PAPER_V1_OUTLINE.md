# PAPER_V1_OUTLINE — Benchmark V1 论文骨架（独立研究版本）

> 日期：2026-11-07 ｜ 状态：骨架（OUTLINE；第 6/7 章为空壳）
> **声明：本文件是 Benchmark V1 独立研究版本的骨架，不覆盖历史 `D:/lovebrain-arena/PAPER.md`**
> （历史论文保留原样；本文与它的比较口径、分组定义不同，互不继承结论）。
> 上位纪律：`D:/lovebrain-arena/AGENTS.md`（v1.6）＋ `benchmark/rubric.md` v0.5 ＋
> `OBJECTIVE.md` v1.4。评估现状依据：`refs/dataset_review_20261107/`（A–F 六份审查）。
> **数量纪律**：凡依赖 Phase 1 盘点的数量一律 `<INV:xxx>` 占位，由
> `refs/benchmark_v1_20261107/phase1/DATASET_INVENTORY.csv` 填充（填充责任=盘点工作包）；
> 既有库的引用数字均给出处，不虚构。
> **结果纪律：第 6/7 章在 Phase 3 实跑完成前禁止填入任何结果**（包括中间读数）。

## 研究问题（固定，全文对齐）

- **RQ1（数据集怎么构造）**：面向"完整聊天 harness"评测的连续剧情数据集如何构造，
  才能在参考答案自由度（不规定唯一措辞）与事实红线（不允许矛盾漂亮话过关）之间
  可验证地铺开十专题能力面？
- **RQ2（LoveBrain harness 提升了什么）**：在同一固定模型服务下，完整 LoveBrain
  harness（机制+资产）相对**直接提示（A）/推理提示（B）/历史 harness（C）**
  在质量、事实/污染、成本/等待上各自提升了什么、没有提升什么——程序分不得冒充
  回复质量，人工偏好（用户盲评）未收集时如实标注 NOT_COLLECTED。

---

## 1. Introduction（要点草稿）

- 问题设定：恋爱聊天助手的评测对象是**完整 harness**（提示资产＋意图处理＋记忆
  存取＋上下文组织＋调用策略），不是 API、不是单句提示词；模型服务固定
  （`http://127.0.0.1:17770/v1`，model `dsf`，温度 0.7；同批比较端点/model/采样
  完全一致——`OBJECTIVE.md`）。
- 核心主张：行为型考场（连续剧情+意图+gold 断言+三层评审）可以同时评
  "防错"与"正向互动"（调情/钩子/深聊/主动），并让效率（token/等待）与质量并列进门
  （rubric v0.5 多目标判决）。
- 与常规对话评测差异：①参考答案规定事实/意图/状态/边界而非唯一措辞（AGENTS §10）；
  ②取消族断言反向探针（不得坐实）与正向断言（只作证据不强迫复述）并存；
  ③首选/备选分审——一个好备选不能掩盖坏首选（rubric §4）。
- 贡献列表（占位，Phase 3 定稿）：数据集构造工艺（`<INV:CONTRIB-DATA>`）、
  十专题量尺（`TOPIC_RUBRIC_DRAFT_V1.md`）、四组对照协议、全部判决账本
  （含被自家门拦下的尝试）。

## 2. Related Work（要点草稿）

- 对话评估基准：多轮一致性/记忆型基准（LongMemEval、LoCoMo 等开源参照，按
  AGENTS §3.1 开源扫描义务在 `logs/round_N_scan.md` 登记）——本工作的差异点：
  **剧情连续性 + 状态生命周期 + 意图契约 + 反向探针**的组合。
- LLM-as-judge 可靠性：与本项目 judge 校准实证（W20-03 逐维资格表、severe 旗
  precision 0.062、引文幻觉 11.9%）对照——未校准 judge 不判死是制度差异点。
- 合成数据与污染：与"出题-评审同源"风险对照（全库 dev_exposed 的结构性限制，
  `D_EVALUATION_RELIABILITY.md` §6.1）。
- 引用清单：占位 `<INV:REFS>`（Phase 3 由开源扫描回填，不现在编造）。

## 3. Benchmark Construction（方法，按真实机制填写）

### 3.1 数据来源
- 既有库：`benchmark/stories_v2/`（10 个 stage/wave 目录；gold sidecar 布局
  221 episodes / 3,102 turns / 6,603 atoms——出处 `refs/dataset_review_20261107/C_GOLD_AND_SCORING.md`
  §1.1）；12 域、184 篇标准篇 0 近重簇、16 长剧、8 反事实孪生、24 篇封存 B 线
  （`benchmark/b_line/B_LINE_MANIFEST.json`，本工作未读其正文）。
- V1 集构成与专题覆盖：`<INV:V1-COMPOSITION>` / `<INV:TP-COVER>`（由
  `refs/benchmark_v1_20261107/phase1/DATASET_INVENTORY.csv` 与
  `TOPIC_COVERAGE_MATRIX.csv` 驱动——Phase 1 盘点工作包二已于 2026-11-07 交付，
  实测工具 `tools/benchmark_v1/inventory.py`，纯标准库零模型调用可复跑）。
- 材料性质声明：全部 `author_type=AI`、`real_user_material=false`、
  `validation_claim=false`（stage manifest 头，如 `benchmark/stories_v2/stage1/STAGE1_MANIFEST.json`）。
  **合成数据局限声明**：全库无一条具备"未见验证资格"（`W03_CENSUS_20261030.md`：
  dev_exposed=true、independent_review=false）；本文所有数据集结论均以"开发集证据"
  名义出具，独立泛化只能来自封存 B 线（未消费）与新 holdout（`F_REDESIGN_DRAFT.md`
  §2.3 N1，未执行）。

### 3.2 构造原则
- 四通道输入：聊天记录（messages）＋备注（note）＋意图（intent）＋时间戳（clock）；
  候选可见面由白名单封死（`arena/lb_runner.py` `TURN_VISIBLE_KEYS` /
  `INPUT_VISIBLE_KEYS` / `MESSAGE_VISIBLE_KEYS` / `INTENT_VISIBLE_KEYS` /
  `KB_VISIBLE_KEYS`——白名单外一律不给候选，含 feedback/audit 等出题侧字段）。
- 题面与判据分离：episode.json 内 `gold={}`，判据活在 reference.json/gold sidecar；
  封板后改题=舞弊（AGENTS §4）。
- 隔离生成：出题会话禁读 logs/失败原句（baseline contamination 防线）；
  `generation_isolation_ack` 事件；SEALED→EXPOSED 不可逆账本
  （`W03-05_CUSTODY_PROTOCOL_20261031.md`；`tools/story_custody.py`）。
- 工艺手法：账目陷阱（HER 报错数、gold 不背书错误算术——LRE128_T35/E164_T05
  范式）、时间陷阱（多处更正级联，E117 型六处变更）、CF 孪生单变量扰动。
  完整规范：`NEW_STORY_SPEC_V1.md`。

### 3.3 十专题定义
- 十专题名与分类口径以用户指导书 §2.3 为准（Phase 1 盘点已按其单选分类全库：
  `refs/benchmark_v1_20261107/phase1/TOPIC_COVERAGE_MATRIX.csv`，
  `tools/benchmark_v1/inventory.py`；每专题覆盖数由该 CSV 驱动 `<INV:TP-COVER>`）。
  判据/0-1-2 档行为锚/反事实变体/不可判例规则见
  `refs/benchmark_v1_20261107/phase1/TOPIC_RUBRIC_DRAFT_V1.md`（Phase 1 草案，
  本文引用不复制）。论文正文给出压缩表（专题×九维×证据通道映射，同文件 §3）。
- 关键设计点（正文必写）：T02 调情与 T03 深聊**禁止关键词命中判定**，用 0/1/2 档
  行为锚（W08-03 三档纪律、W08-04 三档锚点）；**永远保守/不敢推进也算缺陷**
  （OBJECTIVE.md 边界）；T06 定义 recommended 主槽与备份槽的角色边界
  （主比较只用 primary_text=recommended）。

### 3.4 质量维度
- 一级质量：事实/主体/时间/状态、知识正确使用、目标达成与自然互动/主动/调情/
  深聊/反馈（OBJECTIVE.md）；二级：费用、等待、可靠性、复杂度。
- 十专题现有素材覆盖量：`<INV:TP-COVER>`（DATASET_INVENTORY.csv）。

### 3.5 Gold 与审核方法
- gold sidecar v2 管线：`tools/build_gold_sidecar.py`——离线、零模型调用、
  逐字搬运（assertions=required_memory_atoms 逐字拷贝）+ 确定性 enrichment
  （affirm_markers/item_markers/unknowns 对象化/intent keywords_any）+ 全量 QC
  （先例：1,099 markers 逐字回验 0 违例；`source.quote` 6,603/6,603 逐字命中）。
- 消费：`arena/gold_sidecar.py` 加载 → `arena/lb_scorer.py`（唯一计分器，rubric 层 1，
  v0.9.3 C14/C15 事实判分；gold 缺席诚实 unrated，不冒通过）。
- 审核收口链：机械校验 `tools/validate_story_v2.py`（出题痕迹词 BANNED 扫描/
  时间一致性/引文逐字/reference 完整性/exposure 不变量）→ custody 账本
  （sha256 双哈希）→ 语义审查代理批次（wave3 T1–T4 24/24 PASS 先例）→
  实跑暴露勘误（E-MAT 系列，修复后哈希级联）。
- **诚实边界（正文必写）**：gold 断言是 AI 断言、非人工真值（外部审计口径：
  3,102 轮中 2,919 位有断言、均为 AI 断言——`refs/version_closeout_20261106/X_HARNESS_RESTORE_AND_AUDIT_VERIFICATION_20261107.md`，
  转引自 `C_GOLD_AND_SCORING.md` §2.4）；
  语义层证据上限=AI 互评+确定性一致性检查+亲读兜底（`D_EVALUATION_RELIABILITY.md`
  §6.3/§7.3）。

## 4. Compared Methods（四组定义）

> 全部四组共用同一模型服务（端点/model/采样同批固定）；换模型=换条件，不是 harness
> 进步（OBJECTIVE.md）。四组的主比较只用各组首选回复（primary_text=recommended）。

| 组 | 定义 | 实现载体 | 现状 |
|---|---|---|---|
| **A Direct** | 直接提示基线：把 HER/ME 上下文（同白名单输入）直接交给模型要求生成回复，无记忆机制/无意图路由/无上下文工程；输出格式与四组合一（见 5.2） | Phase 3 新建提示基线（授权登记后实现） | 未实现未测，**论文只描述定义不给结果** |
| **B CoT-style** | 推理提示基线：同 A 输入，要求先显式分析（她说了什么/意图/状态）再给回复；analysis 与回复在同一输出契约内 | Phase 3 新建提示基线 | 同上 |
| **C LEGACY** | 历史 harness `r9_slim`（瘦体；曾为共役冠军，W17b+W17c MERGE 后转历史——`RESEARCH_STATE.json` main_incumbent 注记） | `candidates/action_policy_r9_slim/` | 已存在；跨代比较口径，不称"未见" |
| **D CURRENT** | 现役双域冠军：>24 轮域 `window_expand_b_norecap_uc` / ≤24 轮域 `window_expand_b_norecap_guard`（`RESEARCH_STATE.json` main_incumbent；W22FULL REV2 判决 KEEP_NO_PROMOTE，双域冠军不变——`W22FULL_VERDICT_REV2_20261104.md`） | `candidates/` 冠军链 | 已存在 |

- 分域注意：>24 轮/≤24 轮双域切换是 D 组内部路由规则；四组在同题集上跑，
  D 组按其声明路由（论文如实描述，不做"平均化"抹平）。
- **程序分不得冒充回复质量**（rubric v0.5 §1；AGENTS v1.4 ②）：组间主比较的
  质量结论必须来自语义评审（受资格限制）与用户盲评，程序分只证契约与红线。

## 5. Experimental Setup（方法）

### 5.1 输入公平性白名单
- 四组候选可见输入统一由 `arena/lb_runner.py` 消毒边界重建（允许字段清单逐键
  白名单，白名单外一律剥除；消息/意图/kb 深拷贝重建，切断别名共享——runner 头注
  第四/五波判决条目）。配对输入公平性离线审计：`tools/audit_pair_input_fairness.py`。
- 题面（episode.json）四组逐字节相同；prompt 字节等价纪律与 capture 回放
  （先例：W21FULL 821,643=821,643，`W22FULL`/`W21FULL_VERDICT` G4 门——
  V1 批执行时按同标准核对）。

### 5.2 输出标准化（primary_text 规则）
- 每组每次调用产出候选输出集合（可发送候选总数 ≥8 硬约束，rubric §2"输出契约
  声明"／AGENTS v1.2 ②"输出契约开放"；候选可声明 output_contract 自定义）。
- **主比较统一取 primary_text = recommended 槽**（外部指导书；A/B 组在输出契约内
  同样声明主槽；C/D 组主槽按各自 params.json 声明）。
- 备份槽只参与分审指标：primary_semantic_pass_rate / at_least_one_pass_rate /
  severe_error_backup_rate（rubric §4 LAYERED_SCORE）；合法 null=该位不交付，
  不算不合格（lb_scorer v0.9.2 null 契约）。

### 5.3 成本统计口径
- 双账（rubric §5）：评分账=episode 全链真实 usage（生成+记忆+反思+修复+重试，
  失败不从分母删除）；法币账=可核单价折算，未知记"金额未知"不填零。
- **usage-unit 口径**：`usage.prompt` 不是 token 也不是字符（prompt/len 比 0.72–0.76
  p50≈0.73 实测，`benchmark/stories_v2/GOLD_SCHEMA.md` §7）；绝对值一律标
  usage-unit，检查脚本 `tools/check_usage_unit.py`；网关同时记录 prompt_chars
  （`arena/lb_gateway.py` ModelGateway——每次调用尝试成功/失败即刻 JSONL 记账，
  attempt_started 写失败零派发）。
- 等待口径：首内容到达≠首选可发送；p50/p95/超时+分段计时（gateway `timing` 字段）。
  跨组比较只做同批同条件配对差；跨批横比禁止（AGENTS §5）。

### 5.4 人工偏好测量计划
- **用户盲评**（rubric 层 3）：五选一（A 更愿意发/B 更愿意发/相当/两个都不合格/
  信息不足），四组两两成对、顺序互换；**AI/worker/judge 不代投**（W 表纪律）。
- 采样计划：每收口批固定 10 对"愿发二选一"轻量 W 表（`F_REDESIGN_DRAFT.md` §4-1
  设计）——V1 实际收集量 `<INV:PREF-N>`；**未收集时全部偏好结论记
  NOT_COLLECTED，不得冒填、不得以 judge 分数替代**。
- 判定链：程序（契约/红线）→ 语义评审（judge 九维按 W20-03 资格表限制：
  goal/time/interact/emotion full；fact/init 低证据辅助；attrib assistive-only；
  **boundary/memory 停用**）→ severe 只作 B3 亲读索引（precision 0.062）→
  引文逐字硬校验 `tools/quote_hard_check.py` → 用户盲评为最终裁决。
- swap（顺序交换）门：近平局批不得宣布单冠军（W19CROSS/W21/W22 三次真实阻断
  先例，`D_EVALUATION_RELIABILITY.md` §2.4）。

### 5.5 假设与不可证明边界（正文必写）
- 全部题集 dev_exposed：无"未见验证资格"结论；B 线 only_once 未消费前无盲测。
- gold=AI、judge=AI（同基座）、B3=主会话 AI：语义结论的证据上限封顶
  （`D_EVALUATION_RELIABILITY.md` §7.3 结构性缺口 1–3）。
- judge winner 单值噪声大（复读复现 6/12=50%），判决用逐维分不用 winner 单值。
- 嵌套结构：请求位嵌套于故事，不得当独立样本（AGENTS §5；rubric §6 统计纪律）。
- 请求位零严重错误时的报告：单侧 95% 上界（rubric §6），不虚报把握。

## 6. Results（空骨架）

> **Phase 3 实跑完成前禁止填入任何结果**（含中间读数、预览数、估计数）。

- 6.1 主比较总表（四组 × 十专题 × 三层证据通道）——`<P3:RESULTS-MAIN>`
- 6.2 首选/备选分审（primary_pass_rate / at_least_one_pass_rate /
  severe_error_backup_rate）——`<P3:RESULTS-LAYERED>`
- 6.3 事实与污染（C14/C15/stale/geo 命中；CF follow/沿用率）——`<P3:RESULTS-FACT>`
- 6.4 成本与等待（usage-unit 配对差、p50/p95、失败率）——`<P3:RESULTS-COST>`
- 6.5 用户盲评（若 NOT_COLLECTED 则如实标注并只报 AI 侧证据链）——`<P3:RESULTS-PREF>`

## 7. Analysis and Ablation（空骨架）

> **Phase 3 前禁止填入任何结果。** 消融设计预登记后才可执行（授权/预算/判据
> 跑前冻结，W04-03 预注册先例）。

- 7.1 机制组件贡献拆分（记忆/检索/意图路由/上下文组织/调用策略）——`<P3:ABL-MECH>`
- 7.2 A vs B：推理提示是否改变契约遵守/崩坏率（FK-INJECT-01 注入敏感性）——`<P3:ABL-COT>`
- 7.3 D 组双域路由的域内/域外差异（>24 轮 vs ≤24 轮）——`<P3:ABL-DOMAIN>`
- 7.4 已知故障族回归（`refs/KNOWN_FAILURES.md` 全量）——`<P3:ABL-KF>`

## 8. Limitations（预写已知条目，不待 Phase 3）

1. **合成数据**：全部题目 author_type=AI、real_user_material=false；0 近重簇≠
   分布等于真实用户输入；域是自报标签，无外部域效度验证
   （`D_EVALUATION_RELIABILITY.md` §6.4）。
2. **AI gold**：断言为 AI 生成非人工真值；gold 自身会错（E184 课时账/LRE128_T35/
   E164_T05 账目改造先例），无系统性 gold 质量度量。
3. **judge 资格限制**：boundary/memory 机械判停用（漏检 0.864/0.609）；severe 旗
   P=0.062 只作索引；引文 strict 命中 75–85%（fragments 拼装改写长期存在）；
   judge winner 单值不可用于判决。
4. **脚本化回放非真人互动**：HER 消息是固定脚本，分支对照是注入式（W08-05
   replace_m 范式）；"她"的积极回应不构成真实恋爱效果（OBJECTIVE.md 边界）。
5. **单模型单服务**：全部结论绑定 dsf@127.0.0.1:17770/v1 与其 usage-unit 口径；
   换模型=换条件，结论不可外推到其他模型服务。
6. **偏好样本小**：用户愿发至今 NOT_COLLECTED（W 表未投、AI 不代投）；计划中的
   轻量 W 表样本量小（每批 10 对），偏好结论只能定性；真人实发反馈不存在
   （`C_GOLD_AND_SCORING.md` §5"真实用户评价：未做"）。
7. **零未见验证资格素材**：全库 dev_exposed、B 线未消费——对外只能声明开发集
   证据（`D_EVALUATION_RELIABILITY.md` §6.1/§7.3-1）。
8. **swap 低功效**：顺序交换样本量小，只能阻断不能证明优劣
   （`W22FULL_VERDICT_REV2_20261104.md` 注记）。

## 9. Conclusion（骨架）

- 复述 RQ1/RQ2 的回答框架（占位 `<P3:CONCL>`，Phase 3 前不写结论句）。
- 制度性产物与结果并列呈现：全部判决账本、被自家门拦下的尝试、
  KNOWN_FAILURES 登记链——"没赢的尝试"与"赢的尝试"同等入文。
- 开源与复现承诺：题库快照 sha256 + rubric 版本 + scorer 版本三元组绑定每份答卷
  （`F_REDESIGN_DRAFT.md` §4-3）；B 线消费一次后解锁其描述。
