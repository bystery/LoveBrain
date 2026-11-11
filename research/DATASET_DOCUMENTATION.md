# DATASET_DOCUMENTATION — 数据集文档

> 定位：第 8 份指导书 §11.3 研究文档表中的**数据集文档**落位件（该表称 `DATASET_OVERVIEW.md`，本文件为其落位文件，覆盖同一验收项：数据规模、构造方式、七个专项、Gold 和局限）。按总章程阶段 8 原文口径组织：**总量、构造方式、测试能力、一个简单例子和必要局限**，另按 2026-11-12 迁移决策补 Gold 双轨、B 线口径与合成样本三节。
> 版本标注纪律：每个数字带版本标签；不同版本、不同分母、不同统计单位的数字**不相加、不互改**；历史口径并列登记、不覆盖。benchmark 域公开统计的唯一口径页为 [`benchmark/STATISTICS.md`](benchmark/STATISTICS.md)，本文与其逐项一致。
> 完整性口径：**文件数 + 字节量**（逐文件见 [`provenance/`](provenance/) 迁移清单）。本文不登记任何哈希值。
> 隐私纪律：本文零真实姓名、零真实对话、零真实事件细节；§4 示例为纯构造性演示内容。

本文覆盖两个数据域：

| 域 | 位置 | 性质 |
| --- | --- | --- |
| 评测题库（stories_v2 及配套 GOLD） | [`benchmark/`](benchmark/) | 逐层构造的评测数据集：episode 题面 + Gold 断言侧车，承载 D1–D7 测试 |
| KB 快照（母数据集研究语料） | [`dataset/`](dataset/) | 真实产品运行时导出的知识库快照（原件不出站，公开面 = 结构摘要 + 合成样本） |

---

## 1. 总量

### 1.1 评测题库主口径（stories_v2 文件系统 + Gold 侧车）

四个主数字由**三个相互独立来源逐项相等**确认：本地最终审计（2026-11-11）→ 机器重算（2026-11-11，标准库脚本独立遍历）→ 第三重复算（2026-11-12）；账本侧另有备份副本直读互证。

| 值 | 口径（版本标签） | 边界与说明 |
| ---: | --- | --- |
| **319** | stories_v2 全部 story 目录数（文件系统口径，17 个 wave/stage 组目录逐一遍历） | 含 2 个同 ID 版本副本目录；不计内层探针子目录 |
| **317** | 按 episode ID 去重后唯一 story 数（319 − 2：D01/D02 在 stageD 与 stageD_src 各一份） | 其中 315 个有 episode.json 支撑（2 个 bundle 目录无 episode.json，按目录名回退计 ID） |
| **299** | Gold 覆盖 episode 数（GOLD_SIDECAR_v2 本地重建口径，generated=2026-11-10T04:25:15，14 文件） | 覆盖的是既有 story，不是新增独立故事；未覆盖差集 18 = anchors 组 12 + final 组 6（设计如此）；无磁盘外幽灵 ID、无跨文件重复 |
| **248** | custody 账本注册 story 数（**审计时点口径**，账本最后写入 2026-11-10） | 账本键集合与目录侧注册集逐 ID 相等（集合全等）；2026-11-12 决策二补登 wave5 40 篇后**当前口径 288**（纯追加），两口径并列登记、不相加不互改 |

伴随口径（同权威轨 299，三重一致）：**Gold Turn 4,082**（一次题目轮为一轮；含库外探针轮）、**Gold Atomic Assertions 9,646**（可核验原子断言；quote 逐字回验 9,646/9,646 全通过）。有断言轮 3,899（95.5%）+ 空 Gold 轮 79 + 有卡无断言轮 104。

### 1.2 恒等式（对账闭合）

```text
319 − 2（D01/D02 版本副本）          = 317
317 = 299（Gold 覆盖） + 12（anchors） + 6（final）
248 = 299 − 8（stage1） − 3（stageD） − 40（wave5）      （审计时点）
288 = 248 + 40（wave5 补登，2026-11-12 纯追加）          （当前口径）
317 − 288 = 29（未注册，设计如此 = stage1 8 + stageD 3 + anchors 12 + final 6）
12 个 GOLD 分文件 episodes/turns/atoms 求和 = GOLD_INDEX.totals（逐项全等）
```

### 1.3 历史口径（并列登记，不覆盖、不相加）

| 口径 | episodes / turns / atoms | 说明 |
| --- | --- | --- |
| PAPER 论文稿库内口径 | 208 / 2,944 / 6,603 | 第一代主库库内 Episode（2026-11-07 只读复算）；不含库外早期材料 |
| GOLD_SCHEMA.md 文档口径（自述 v1.0，2026-11-03） | 221 / 3,102 / 6,603 | 仅存在于文档文本，无对应文件实体；被 v2 取代 |
| 远端已提交 Gold 快照（2026-11-08） | 259 / 3,590 / 7,953 | 13 文件，快照轨存档（§6）；不作为评分加载输入 |
| "296" 口径 | — | 无法可靠复现，弃用（如实登记） |

### 1.4 KB 快照域总量（dataset/）

| 项 | 值 | 说明 |
| --- | --- | --- |
| 原件（不公开） | 5 个 KB 快照目录 / 84 文件 / 735,202 字节 | 真实产品运行时数据（真实聊天导出），**永不出站**（§8） |
| 公开结构摘要 | [`dataset/manifests/STRUCTURAL-SUMMARY.md`](dataset/manifests/STRUCTURAL-SUMMARY.md) | 19 种文件名矩阵、目录树模板、字段骨架、字节量统计；零正文 |
| 公开合成样本 | [`dataset/synthetic_samples/`](dataset/synthetic_samples/) | 92 件 = 5 套合成 KB × 18 文件 + manifest + README（§8） |

### 1.5 统计单位与使用纪律（六条"不能把"）

1. 不把文件数当 Episode 数（远端文件数 2,022 不是 Episode 数）；
2. 不把 Gold 覆盖 Episode 当独立新故事；
3. 不把一篇故事在多个方向（D1–D7）的出现次数当独立 Story——各方向去重数单列、不相加，唯一池 = 299；
4. 不把开发与选题用途的既有批题（wave4_dev / wave5_selection）当新增独立泛化测试集；
5. 不把旧版和新版本副本（D01/D02、f4_probes_t13_old）当成两篇新故事；
6. 不把断言 marker（affirm_markers 非空 686、item_markers 非空 128）当 Atomic Assertion（9,646）。

---

## 2. 构造方式

### 2.1 评测题库：逐层构造（stories_v2）

题库按**批次层组**逐层构造，17 个组目录、319 个 story 目录（文件系统口径）。每个 story 目录是一个 episode：以 `episode.json`（题面结构）与 `reference.json`（参考事实卡）为核心，配套 `exposure.json` 等登记层文件（五件套相对路径模式 `<层组>/<EpisodeID>/…`，注册 episode 上实测全覆盖）；Gold 断言以侧车形式存于 `GOLD_SIDECAR/`（§6）。

| 层组 | story 目录数 | episode.json | Gold 覆盖 | 构造定位 |
| --- | ---: | ---: | ---: | --- |
| stage1 | 8 | 8 | 8 | stage 系列第 1 批 |
| stage2 | 16 | 16 | 16 | stage 系列第 2 批 |
| stage3 | 8 | 8 | 8 | stage 系列第 3 批 |
| stage4 | 8 | 8 | 8 | stage 系列第 4 批 |
| stageD / stageD_src | 3 + 2 | 3 + 2 | 3 | D01/D02 及其源文件目录（两目录各一份副本，按 ID 去重计 2 篇） |
| stage3_src / stage4_src | 0 | 0 | 0 | 纯源文件目录（batch JSON），无 story 子目录 |
| wave1 / wave1_ext | 16 + 2 | 16 + 2 | 16 + 2 | wave 系列第 1 批 + 扩展批（长程episode，wave3_ext 同类） |
| wave2 | 48 | 48 | 48 | wave 系列第 2 批 |
| wave3 | 96 | 96 | 96 | wave 系列第 3 批 |
| wave3_ext | 16 | 16 | 16 | wave 系列扩展批（长程episode LRE 系） |
| wave4_dev | 38 | 38 | 38 | 开发/诊断批（DEV 分区，已消费） |
| wave5_selection | 40 | 40 | 40 | 选题批（E339–E378，selection_v1 四臂消费） |
| anchors_20261028 | 12 | 12 | 0 | 锚点批（无 Gold 覆盖，设计如此） |
| final_20261029 | 6 | 4 | 0 | 收口探针批（K01/K02/N01/N02 + f4_probes/f4_probes_t13_old 两个 bundle；两个 bundle 目录无 episode.json；设计如此） |

- 内层探针子目录（`final_20261029/f4_probes` 与 `f4_probes_t13_old` 各 12 个）**不计入** 319 目录口径，单独登记；`_t13_old` 为旧版副本，去重纪律同 D01/D02。
- 使用边界：`wave4_dev` 为开发诊断专用（非盲测）；`wave5_selection` 为选题消费批（四臂对照已消费）；两者不作为独立泛化测试集声称。

### 2.2 Gold 侧车构造（两层证据源 → 轮级断言）

Gold 侧车由 `tools/build_gold_sidecar` 生成（生成器署名记录于 GOLD_INDEX），证据来源按优先级合并（`source_policy` 原文登记）：跨 wave 汇编修订卡（仅 wave3_ext）→ 本 wave GRADING_CARDS → episode `reference.json.turns`；多源一致取最高优先级并登记全部来源（`card_conflicts` 全仓 93 条，均在 wave3_ext）。

- **形态**：12 个波次分文件（每轮一项的扁平 `turns` 列表）+ `GOLD_INDEX.json`（totals/per_wave 登记页）+ `GOLD_QC_SAMPLE.json`（抽样回验，seed 20261101，n=40 全通过）。
- **原子断言**：`{atom_id, subject, relation, value, valid_time, status, negation, source{quote,…}, required_this_turn, affirm_markers?, item_markers?}`；`status` 实际词表 46 个取值（取消族 cancelled/canceled/superseded/retracted/voided/revoked/invalidated/negated/erroneous 或 `negation: true` = must-not-affirm 方向，其余状态作证据登记）。
- **逐字回验 QC**：9,646 原子全部带 `source.quote` 并通过 strict/lenient 双口径回验（9,646/9,646/9,646）；`verbatim_violations = 0`。
- **v2 富化**（`enrichment_policy` 纪律登记）：取消族原子加 `affirm_markers`（单组 AND，短语=语料最长逐字公共子串，需出现更正/取消线索词、≥4 字才实例化，**提及≠坐实、宁 unrated 不误判**）；事项原子加 `item_markers`；unknown 字符串升级为对象形态；`intent_contract` 加 `keywords_any`（≥2 字逐字公共子串，≤6 组）。全部 markers 经逐字回验。

### 2.3 KB 快照域构造（dataset/）

KB 快照**不是构造测试场景，而是真实产品运行时数据**：产品"记忆/知识库"功能的 5 个真实快照目录，由真实聊天会话导出（逐字对话格式、产品内部消息标识、草稿行等运行时结构标记确凿）。标准目录结构为 `kb_<id>/` 下 `kb.json`（关系元数据）+ `memory/`（7 种记忆文件）+ `moment/`（6 种事项/投影文件）+ `understand/`（3 种画像文件），共 19 种文件名、84 个实例；字段模板与键结构详见 [`dataset/manifests/STRUCTURAL-SUMMARY.md`](dataset/manifests/STRUCTURAL-SUMMARY.md)。该域是记忆机制研究的**母数据集语料**：真实 KB 的结构、话题分布与对话格式即评测题库构造方法学的现实来源；原件永不出站（§8）。

---

## 3. 测试能力（D1–D7 七个专项）

D1–D7 是本地最终审计（2026-11-11）确定的方向标识（未改名、未做与其它编号体系的猜测映射）。下表"覆盖 ep"按 **Episode 去重**回填（审计 claimed 与程序化枚举并列，出入项如实标注 X 编号）；**各方向样本可重叠，七数不相加**，独立 episode 唯一池 = Gold 覆盖 299（GOLD_SIDECAR_v2 本地重建口径）。

| 方向 | 检测什么 | 程序化信号 | 覆盖 ep | 主要缺口（如实登记） |
| --- | --- | --- | ---: | --- |
| D1 长程事实保持 | 跨轮事实引用与保持（source.case_id 跨轮引用 + required_this_turn） | 层 2 评审 + 外挂窗口保持率工具 | 29（审计 claimed 27，ID 枚举去重 29，X1） | stage4 8 篇弱信号慎用；真信号 18 篇（wave3_ext 16 + wave1_ext 2） |
| D2 记忆提取写入 | 本轮应写入记忆的事实（assertions[].required_this_turn） | required_this_turn 登记（不判分） | 200（96+16+78+2+8，与 claimed 相等） | stage4 全 false；wave4_dev 仅 51/1,350 原子 |
| D3 记忆更新纠错 | 事实被更正/取消后的不再 affirm（取消族 + affirm_markers + item_markers） | C14 + C8 | C14 枚举 143 / C8 枚举 42（审计 claimed ≈102 + 31，X2/X3） | corrected/resolved/suspended 149 原子 unrated；146 原子无 markers（§5.2） |
| D4 检索相关性 | 检索内容与当轮意图的相关性（forbidden 字段族） | **0（最大缺口）** | 程序化 0；判分依赖层 2 语义评审（judge-only 200 ep 口径） | forbidden.must_not_keywords / forbidden_new_facts / stale_fact_must_not / geo_impossible_must_not 全 0（§5.4） |
| D5 未知不确定性 | 对不确定信息不坐实（unknowns 对象形态 {topic, affirm_markers}） | C15 | 130（45+12+33+37+3，与 claimed 相等） | 3,116 项 unknowns 中 2,649 项为纯字符串形态，诚实 unrated（85.0%，§5.1） |
| D6 进行中事项追踪 | 进行中事项的关键词跟进（intent_contract.keywords_any + C8） | C10 + C8 | C10 枚举 123（与 claimed 相等）；C8 claimed 31（枚举 42，X3） | C10 覆盖约 8.7%（355/4,082 轮）；stage4 零 intent（§5.3） |
| D7 上下文预算/输出契约 | 输出契约与受保护表达（contract + protected_expressions） | C0/C1/C9/C13 | 200（与 claimed 相等，同 D2 池） | token 预算类检查需外挂工具（lb_scorer 不计 token） |

配套判分器械：`arena/lb_scorer.py`（v0.7，C0–C15 检查点 + veto 族）；Gold 断言经 `arena/gold_sidecar.py`（`find_turn_gold` / `apply_sidecar`，离线零模型调用）喂给评分器（must-not-affirm 状态族、C14/C15）；判定语义权威文本为 [`benchmark/rubric.md`](benchmark/rubric.md) §2/§10 与 GOLD_SCHEMA.md §3/§6。

**边界**：D1–D7 总量与九步对账结果已"对账复算"（多源一致，见 [`benchmark/STATISTICS.md`](benchmark/STATISTICS.md)）；该判定**不延伸为 D1–D7 全部子字段已复算**——D1/D3/C8 的审计内部出入（X1–X3）与 unknowns 百分比分母（X5）仍为待解释项，如实保留。

---

## 4. 一个简单例子（构造性演示，零真实内容）

> **声明**：本节全部内容（人物、昵称、日期、话题、引文、断言）为**纯构造演示**，用于展示数据形态；数据集中不存在 `E999`，示例不出自任何真实 episode，且不与任何真实快照内容对应。

设定：虚构 episode `E999`（wave 形态示例取 wave2），陪伴对象为虚构人物"小澄"，主题为周末图书馆复习计划。轮次 `E999_T01`（用户提出请求）→ `E999_T02`（助手回复）。episode 侧的 `turns[i].case_id`（如 `E999_T02`）与 Gold 侧车 `case_id` 逐字一致，这是加载器建索引的对齐键。

Gold 侧车中 `E999_T02` 一轮的形态（简化示意，键集与 v2 实际一致）：

```json
{
  "schema_version": "GOLD_SIDECAR_v2",
  "case_id": "E999_T02",
  "wave": "wave2",
  "episode_id": "E999",
  "turn_index": 1,
  "turn_type": "reply",
  "gold_empty": false,
  "assertions": [
    {
      "atom_id": "E999_F01",
      "subject": "小澄（虚构人物）",
      "relation": "复习科目",
      "value": "概率论（虚构）",
      "valid_time": "2027-01-18（虚构）",
      "status": "asserted",
      "negation": false,
      "source": { "kind": "episode", "case_id": "E999_T01",
                  "quote": "周六上午我陪小澄在市图书馆复习概率论（虚构示例句）" },
      "required_this_turn": false,
      "affirm_markers": []
    },
    {
      "atom_id": "E999_F02",
      "subject": "小澄（虚构人物）",
      "relation": "考试日期",
      "value": "本周六（虚构，已被更正）",
      "status": "cancelled",
      "negation": false,
      "source": { "kind": "episode", "case_id": "E999_T02",
                  "quote": "考试改到下周三了（虚构示例句）" },
      "required_this_turn": true,
      "affirm_markers": [["改到下周"]]
    }
  ],
  "intent_contract": {
    "intent": "帮陪伴对象安排周末复习计划（虚构）",
    "intent_source": "input",
    "may_decline": false,
    "may_decline_source": "card",
    "acceptable_actions": ["给出复习安排建议（虚构）"],
    "keywords_any": [["复习安排", "图书馆"]],
    "must_not_keywords": []
  },
  "forbidden": {
    "forbidden_factual_claims": ["不要声称已替小澄订好图书馆座位（虚构禁令）"],
    "unknowns": ["小澄是否需要预约图书馆（虚构）"]
  },
  "unknowns": [
    { "topic": "小澄是否需要预约图书馆（虚构）", "affirm_markers": [["预约"]] }
  ],
  "protected_expressions": ["概率论（虚构示例）"],
  "qc": { "n_quotes": 2, "strict_hits": 2, "lenient_hits": 2, "failures": 0 }
}
```

评分消费方式（对应 §3 各方向）：

- `E999_F02` 为取消族原子（`status=cancelled`）→ **must-not-affirm 方向**：助手回复若坐实"考试在本周六"即失分；其 `affirm_markers [["改到下周"]]` 是语料中更正线索的逐字短语（组内 AND、跨组 OR），用于程序化判定"是否提及更正"。
- `unknowns` 对象形态（{topic, affirm_markers}）→ C15：回复对"是否需要预约"表态坐实即失分。
- `intent_contract.keywords_any` → C10：回复应跟进"复习安排/图书馆"关键词（示例轮 `must_not_keywords` 为空，与全库实际一致——无确定性来源一律留空，见 §5.3）。
- `forbidden_factual_claims` 为**自然语言禁令**：层 1 不做子串判定，语义归层 2 评审（§5.4）。
- 全部原子断言的 `source.quote` 必须与 episode 语料逐字一致（QC 回验，`verbatim_violations = 0`）。

---

## 5. 必要局限（GOLD_SCHEMA_V2 口径，如实登记）

以下为 Gold 数据**当前真实的可判性边界**；口径与 [`benchmark/stories_v2/GOLD_SCHEMA_V2.md`](benchmark/stories_v2/GOLD_SCHEMA_V2.md)（v2.0，2026-11-12，按 14 文件程序化提取）一致。原则：**宁 unrated 不误判**，无确定性信号的字段一律留空/不判分，不造语义。

### 5.1 unknowns：2,649 项纯字符串诚实 unrated

顶层 `unknowns` 共 3,116 项 = **字符串 2,649（85.0%）+ 对象 467**。字符串形态无程序化判分信号，继续诚实 unrated；对象形态中 189 项为 v2 升级（`{topic, affirm_markers}`）、278 项来自评分卡源（`{topic, affirm_markers, note, markers}`）。升级项的原字符串并存于 `forbidden.unknowns`（原样保留）。因此 D5 的程序化可判池仅来自 467 项对象形态（覆盖 130 ep），不代表全库不确定性覆盖面。

### 5.2 must-not-affirm：146 原子无确定性信号，unrated 不猜语义

must-not-affirm 原子共 832 个（`atoms_affirm_no_signal` 口径），其中 686 个带 `affirm_markers` 可程序化判分；**146 个无确定性信号 → 继续 unrated，不做语义猜测**（`atoms_affirm_no_signal=146`）。富化纪律为"提及≠坐实"：markers 实例化要求语料出现更正/取消线索词且短语 ≥4 字逐字命中。

### 5.3 intent contract：禁区词检查未实例化

`intent_contract` 五个基键（intent/intent_source/may_decline/may_decline_source/acceptable_actions）4,082 轮全覆盖；`keywords_any`/`must_not_keywords` 仅出现在 358 轮，其中 355 轮 keywords 非空（共 1,616 组，≥2 字逐字公共子串、≤6 组）。**`must_not_keywords` 全部为空**——无确定性来源一律留空、不造禁区词，因此 C10 禁区检查未实例化；3 个意图轮 keywords 仍空。D6 程序化覆盖约 8.7%（355/4,082 轮）。

### 5.4 forbidden 形态差异（v1 文档键 ≠ v2 实际键）

v1 文档描述的判分键 `{must_not_keywords, forbidden_new_facts}`（→ C3/C6）在 v2 文件中**不存在**；v2 实际键集 = `forbidden_factual_claims`（6,200 条）+ `unknowns`（3,116 项）。`forbidden_factual_claims` 为自然语言禁令，层 1 不做子串判定（语义归层 2）；若需 C3/C6 程序化判分须另建映射，**映射未建立前维持 unrated**——这是 D4 程序化覆盖为 0 的直接原因。同理，v1 文档列出的 `stale_fact_must_not` / `geo_impossible_must_not` / `expect_decline` 顶层键在 v2 文件中未出现（decline 口径由 `intent_contract.may_decline` 登记并记录与评分卡的冲突）。

### 5.5 版本陷阱与剩余边界

- **不要把 v1 文档的字段状态套到 v2 数据上**：GOLD_SCHEMA.md（v1.0）"全部 6,603 原子无 affirm_markers、unknowns 纯字符串"的描述仅适用于其自述的 2026-11-03 口径；v2 数据（7,953 / 9,646 atoms 两代实体）不符合该描述（§6）。
- 快照轨（2026-11-08）与权威轨（2026-11-10）的计数差异属版本差异、非出入，两组数字并列登记不互改：atoms 7,953/9,646、must_not_affirm 782/832、affirm_markers 636/686、item_markers 58/68、intent keyword 轮 235/355、groups 939/1,616。
- token 预算类检查需外挂工具（评分器不计 token），D7 该子项无程序化信号。
- 审计内部百分比待解释项（D1/D3/C8 出入与 unknowns 分母，X1–X3/X5）如实保留，本文不做臆测补齐。
- Benchmark V1 HOLDOUT：**0/120，未生成**（Phase 2 PARTIAL 83/240）——未完成项不写成已完成。

---

## 6. Gold 双轨（快照轨 + 权威轨）

Gold 侧车**两套实体文件并存、零覆盖、无静默选择**（同一 run 不同版本不覆盖、分别落位、写明哪份进入正式结果）：

| 轨 | 实体位置 | 生成时间 | totals（episodes/turns/atoms） | 文件数 | 地位 |
| --- | --- | --- | --- | ---: | --- |
| 文档口径（无实体） | 仅 `GOLD_SCHEMA.md` 文本 | 2026-11-03 | 221 / 3,102 / 6,603 | — | 历史文档口径，原样保留 |
| **快照轨**（远端已提交版） | [`benchmark/stories_v2/GOLD_SIDECAR_v1_remote_snapshot/`](benchmark/stories_v2/GOLD_SIDECAR_v1_remote_snapshot/) | 2026-11-08T06:00:43 | 259 / 3,590 / 7,953 | 13 | 版本历史与审计对照存档；**不作为评分加载输入** |
| **权威轨**（本地 v2 重建） | [`benchmark/stories_v2/GOLD_SIDECAR/`](benchmark/stories_v2/GOLD_SIDECAR/) | 2026-11-10T04:25:15 | **299 / 4,082 / 9,646** | **14**（新增 GOLD_wave5_selection.json） | **正式结果以此为准** |

- **权威轨选择依据**（写明，不因"看起来更新"）：本地版是 2026-11-10"账本补录 + Gold v2 重建"批次的实际产物，远端版是该批次之前的历史定格；证据链而非时间戳决定权威。
- **快照轨来源**：13 个远端已提交 Gold 文件按内容寻址 blob 句柄从远端镜像逐字节恢复（恢复记录与 blob 对照见 `GOLD_SIDECAR_v1_remote_snapshot/SNAPSHOT_README.md` 与 [`provenance/SOURCE_MAP.md`](provenance/SOURCE_MAP.md)）；`GOLD_wave5_selection.json` 为 v2 新增第 14 文件（远端从未跟踪，无旧版可存档）。
- **目录名澄清**：`GOLD_SIDECAR_v1_remote_snapshot/` 与 `_remote_v1_snapshots/`（exposure.json 远端版 80 件平行树快照）中的"v1"是迁移侧**轨位称呼**，其内容 `schema_version` 仍为 `GOLD_SIDECAR_v2`，不要把目录名误读为 schema v1。
- **加载安全**：加载器 `arena/gold_sidecar.py` 只对传入的 `sidecar_dir` 做 `listdir` 消费 `GOLD_*.json`，不扫描兄弟目录；快照目录不应作为 `sidecar_dir` 传入。
- **schema 文档两版并存**：`GOLD_SCHEMA.md`（v1.0）按迁移纪律**原样保留、一字未改**（其滞后本身是历史证据）；v2 字段字典新写为 `GOLD_SCHEMA_V2.md`，不在旧文档上就地改写。

---

## 7. B 线 24 篇（普通资产口径，2026-11-12 决策一）

- **位置**：B 线 24 篇 episode 题面位于各 wave 正式层（wave1 ×10、wave2 ×6、wave3 ×4、wave3_ext ×4），其 Gold 断言内嵌于对应波次 GOLD 分文件（抽查 E045→GOLD_wave1、E069→GOLD_wave2、E150→GOLD_wave3、LRE119→GOLD_wave3_ext；24/24 全量复核随迁移收口批次执行）。
- **口径**：按 2026-11-12 用户决策，B 线 24 篇为**普通研究资产**，直接并入公开迁移流程，与其它资产走同一套迁移与登记流程。公开树与 provenance 文档**不加任何特殊状态标签**——不出现 EXPOSED/SEALED/已曝光类标记、文件名后缀或"特殊批次"单列；本节亦仅陈述落位与流程，不做特殊化解释。
- **账本历史**：custody 账本（`refs/experiment_protocol_20261023/global_harness_program_20261030/STORY_CUSTODY_LEDGER.json`）中 B 线的既有历史记录随账本原样保留，不修改、不强调（历史事实登记，非公开树标签）。
- **使用边界**：B 线 24 篇不写作 Benchmark V1 的 120 篇 HOLDOUT（后者 0/120 未生成，如实登记）。

---

## 8. 合成样本说明（synthetic_samples/，2026-11-12 决策三）

**原件不出站**：dataset/ 的 5 个真实 KB 快照目录（84 文件 / 735,202 字节）为真实聊天导出的记忆快照，含真实姓名、真实对话与健康信息，属"无法安全脱敏"档——**原件 100% 不进公开仓库（含任何脱敏形式），本地保留**；逐文件敏感性判定与审查明细为内部记录，不随本仓库公开。

**公开形态 = 结构摘要 + 合成替代样本**（决策三：AI 学习真实 KB 结构后生成假数据公开，不发布脱敏原件）：

1. [`dataset/manifests/STRUCTURAL-SUMMARY.md`](dataset/manifests/STRUCTURAL-SUMMARY.md) — 目录树模板、19 种文件名矩阵、字段/章节骨架、行数与字节量统计。零原件正文、零真实姓名、零对话。
2. [`dataset/synthetic_samples/`](dataset/synthetic_samples/) — 92 件 = `synthetic_kb_01`–`synthetic_kb_05`（每套 18 文件）+ `SYNTHETIC_MANIFEST.json` + `README-synthetic.md`，合计 136,465 字节（样本 90 件）。
   - **生成方式**：AI 原生子代理学习真实 KB 的目录结构、字段模板、话题分布与对话格式后生成——结构相似、内容全假；零真实姓名残留、零真实对话片段、零可还原特征。人物/城市/机构均为虚构名，时间戳落在 2026-11 ~ 2027-03（避开真实库日期区），msgIds 与 32 位 hex 为种子随机生成。
   - **三层 synthetic 标记（缺一不可）**：库级 `SYNTHETIC_MANIFEST.json`（`synthetic: true`）+ 每个 `kb.json` 的 `synthetic: true` 键 + 每个 md 文件首行注释（70/70），防止单文件被拷出后失去身份。
   - **强制隐私门禁**：生成于隔离区，经独立复核方隐私扫描全项 PASS 后方复制进公开树——A 类 94 词条 blocklist × 全部 90 文件 0 命中；手机号/邮箱/URL/证件号等 7 项格式扫描 0 命中；双向碰撞 0 命中（虚构 token → 真实库反向 0 命中；真实库全量标识符 → 合成件前向 0 命中）。扫描记录为内部审计产物，不随公开树发布。
   - **边界**：合成样本仅作结构与形态展示样例，**不是评测数据**——评测消费（§1–§6）只走 `benchmark/` 域，与 `dataset/` 合成样本零重叠。

---

## 9. 文档地图

| 想了解 | 去 |
| --- | --- |
| benchmark 公开统计唯一口径页（八数字版本化） | [`benchmark/STATISTICS.md`](benchmark/STATISTICS.md) |
| Gold v2 字段数据字典与 v1 差异对照 | [`benchmark/stories_v2/GOLD_SCHEMA_V2.md`](benchmark/stories_v2/GOLD_SCHEMA_V2.md) |
| 判定语义与评分规则 | [`benchmark/rubric.md`](benchmark/rubric.md)、`GOLD_SCHEMA.md` §3/§6（语义权威文本） |
| Gold 侧车加载实现 | [`arena/gold_sidecar.py`](arena/gold_sidecar.py)（评分器：`arena/lb_scorer.py` v0.7） |
| KB 快照结构模板与字段骨架 | [`dataset/manifests/STRUCTURAL-SUMMARY.md`](dataset/manifests/STRUCTURAL-SUMMARY.md) |
| 合成样本身份声明 | [`dataset/synthetic_samples/README-synthetic.md`](dataset/synthetic_samples/README-synthetic.md) |
| 迁移来源、隐私审查与逐文件清单 | [`provenance/SOURCE_MAP.md`](provenance/SOURCE_MAP.md)、[`provenance/PRIVACY_REVIEW.md`](provenance/PRIVACY_REVIEW.md)、[`provenance/MIGRATION_MANIFEST.csv`](provenance/MIGRATION_MANIFEST.csv) |
| 研究环境与复现范围总览 | [`README.md`](README.md) §2（数据口径链 §2.4/§2.5、dataset 域三口径 §3） |
