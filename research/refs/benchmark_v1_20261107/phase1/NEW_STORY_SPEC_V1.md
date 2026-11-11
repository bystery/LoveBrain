# NEW_STORY_SPEC_V1 — 新造题工艺规范（Benchmark V1 Phase 2 用）

> 日期：2026-11-07 ｜ 状态：草案（DRAFT，供 Phase 2 出题与校验执行）
> 归属：Benchmark V1 第七/八工作包。本文件是**工艺规范**：定义新篇的结构、校验、
> 隔离与质量门槛；不修改任何既有题库/评分器/封存集。
> 样例依据（已实读）：`benchmark/stories_v2/wave3/E117/episode.json`（wave 系，
> 含 feedback/intent/note 全通道）与 `benchmark/stories_v2/stage4/E061/episode.json`
> （stage 系，无 feedback 的历史形态）。
> 机制依据：`tools/validate_story_v2.py`（机械校验全检查项）；
> `benchmark/stories_v2/GOLD_SCHEMA.md`（gold sidecar 契约）；
> `refs/experiment_protocol_20261023/global_harness_program_20261030/W03-05_CUSTODY_PROTOCOL_20261031.md`
> （隔离与保管）；`refs/dataset_review_20261107/C_GOLD_AND_SCORING.md` §4.1
> （账目/时间陷阱勘误台账先例）。
> **数量占位**：凡依赖 Phase 1 盘点的数量写 `<INV:xxx>`，由
> `refs/benchmark_v1_20261107/phase1/DATASET_INVENTORY.csv` 填充（盘点工作包二已于
> 2026-11-07 交付该 CSV 与 `TOPIC_COVERAGE_MATRIX.csv`，实测工具
> `tools/benchmark_v1/inventory.py`；填充责任=盘点工作包，本稿保持占位口径）。

---

## 0. 修订 2026-11-08（Phase 2 §8.1：谱系组隔离规则统一）

> **裁定（指导书 §8.1）：使用记录逐篇记，独立性资格按谱系组算。** 本规范中凡
> "组内任一篇被消费不隐含同组其他篇暴露状态""组消费≠全组暴露"的表述，**就使用
> 记录/账本粒度仍然成立**（custody 事件、experiments_used 仍逐篇记）；但它们
> **不再隐含"逐篇独立保留未见资格"**——未见资格（B 线 / holdout / UNTOUCHED
> 角色）的判定单元已统一为谱系组：**组内任一篇被消费 → 整组失去 unseen 资格**；
> 母本/续写、CF 孪生/基线不得跨 DEV/SELECTION/HOLDOUT 分区。权威规则文本见
> `SPLIT_AND_CUSTODY_PLAN.md` 头部"修订 2026-11-08"节；机械核查工具
> `tools/benchmark_v1/lineage_check.py`。正文 §3.5 与 §6.1 涉及处已就地标注
> **[SUPERSEDED 2026-11-08]**，其余条款不受影响。

---

## 1. episode.json 结构（顶层与 turn 字段）

### 1.1 顶层键（硬白名单）

```json
{
  "episode_id": "E1xx",
  "persona": { ... },
  "initial_kb": { ... },
  "turns": [ ... ]
}
```

- `validate_story_v2.py` 硬校验：`set(ep.keys()) == {"episode_id","persona","initial_kb","turns"}`
  （validator L92），`episode_id` 必须与目录名一致，`persona`/`initial_kb` 必须为 dict。
  **任何新增登记字段不得进 episode.json 顶层**（见 §6.1：primary_topic / lineage_group_id
  登记在 manifest 侧）。

### 1.2 persona / initial_kb

- `persona`：dict。两种既有形态皆合法——stage 系为字符串字段
  （E061：`"me"`/`"her"` 各一段人设）；wave 系为结构化
  （E117：`her: {name, traits[], boundaries[]}` / `me: {name, traits[]}` /
  `relationship_stage`）。V1 新篇统一采用 **wave 系结构化形态**（边界可判、
  便于 T10 边界探针引用）。
- `initial_kb`：**键值全为字符串、单键 ≤1000 字符**（validator L102-107），
  键域 `ALLOWED_KB = {"me","her","warmth","style","stage","lessons"}`
  （validator L43；runner 侧运行时白名单更宽，见 `arena/lb_runner.py`
  `KB_VISIBLE_KEYS`，出题侧从严）。
- `initial_kb.lessons` 是判据外的"她/关系 lessons"通道：深聊/调情锚（W08-04 判据）
  常引用它，必须与剧情一致且不得包含考点答案明文。

### 1.3 turn 字段（episode 内）

```json
{
  "case_id": "E1xx_T0k",
  "turn_type": "reply",
  "gold": {},
  "input": {
    "messages": [ {"role": "HER|ME", "text": "...", "ts": "YYYY-MM-DD HH:MM"} ],
    "clock": "YYYY-MM-DD HH:MM",
    "may_decline": false,
    "note": "<可选：补充信息通道>",
    "intent": {"enabled": true, "status": "explicit", "text": "<意图文本>"}
  },
  "feedback": {"状态": "...", "变化": "..."}
}
```

| 字段 | 规则 | 依据 |
|---|---|---|
| `case_id` | `E1xx_T0k`，**全库唯一**（validator 跨篇查重 L117-119） | validate |
| `turn_type` | 新篇一律 `reply`（rewrite/silent 属 runner 能力，出题侧不新增） | validate L120-121 |
| `gold` | **episode 内恒为 `{}`**（判据分离：gold 活在 reference.json/sidecar） | validate L122-123 |
| `input` 键 | 只允许 `{messages, clock, may_decline, intent, note}`（validator L125 白名单，白名单外报错） | validate |
| `messages[].keys` | 恰为 `{role, text, ts}`；role ∈ {HER, ME}；text 非空 | validate L140-145 |
| 时间纪律 | `messages[].ts ≤ clock`；`clock` 跨轮单调不减；同轮消息 ts 递增 | validate L131-151 |
| `note` | 可选补充信息（"消息转述自导航软件实时路况"型，E117_T09 例）；**禁出题痕迹词** | validate BANNED 表 |
| `intent` | 可选；`enabled` 必须为 bool、`status`/`text` 为 str | validate L156-163 |
| `feedback`（wave1+） | wave 系新篇必带；`{状态, 变化}` 两键短句，描述 HER 侧剧情推进——**这是出题侧叙事账，不是判据**，不进候选可见输入（runner 白名单不含 feedback，`arena/lb_runner.py` `TURN_VISIBLE_KEYS`） | E117 样例 + runner 白名单 |

**四通道输入**（每轮候选可见输入 = 此四通道，无第五通道）：
1. 聊天记录 `input.messages`（HER/ME 历史行）；
2. 备注 `input.note`（补充信息/IDEA，可空缺）；
3. 意图 `input.intent`（部分轮带，配 gold 侧 intent_contract）；
4. 时间戳 `input.clock`（含相对 deadline 信息，时间陷阱的载体）。
白名单一致性由 `arena/lb_runner.py` 消毒边界保证（`TURN_VISIBLE_KEYS` /
`INPUT_VISIBLE_KEYS` / `MESSAGE_VISIBLE_KEYS` / `INTENT_VISIBLE_KEYS` /
`KB_VISIBLE_KEYS`，白名单外一律不给候选）。

---

## 2. 配套五件套（每篇目录）

每篇一个目录（`benchmark/stories_v2/<stage或wave>/<E1xx>/`），含：

| 文件 | 内容要点 |
|---|---|
| `episode.json` | §1 结构 |
| `reference.json` | `{"story_id", "turns": {case_id: gold 卡}}`——判据侧，§4 定义 |
| `timeline.json` | `story_id` + `turn_clocks`（=轮数）+ `message_index`（每条 `{case_id, message_id, speaker, quote, ts}`，与 episode 消息逐字一致）+ `events[]`（`source_turn` 必须存在）+ `revisions[]`（`case_id` 必须存在） |
| `exposure.json` | 必填：`author / author_type / readers / frozen_at / experiments_used / dev_exposed / real_user_material / hashes / runner_copy`；`real_user_material` 恒 `false`；新篇 `experiments_used` 恒 `[]`；`frozen_at` 冻结前为 `null`；`hashes` 对五件套记 sha256；`runner_copy` 指向 episode 副本且内容与正本逐字一致 |
| `audit.md` | 固定八节标题（缺一即 fail，validator L305-307 逐节查）：`## 0. 性质与暴露 / ## 1. 生活动机 / ## 2. 连续性与回扣 / ## 3. ME 历史与边界 / ## 4. no_reply / 媒体局限 / ## 5. 参考卡质量 / ## 6. 已知风险 / ## 7. 出口检查` |

manifest（`STAGE<w>_MANIFEST.json` / wave manifest）登记五件套文件 sha256
（validator 对 manifest hash 逐文件对账）。runner 副本路径：新篇在 stories_v2
下直接以 `runner_episode.json` 形态随目录交付（E117 样例形态），validator 经
`exposure.runner_copy` 对账。

---

## 3. 隔离生成纪律（custody 协议关键条款，引用）

出处：`refs/experiment_protocol_20261023/global_harness_program_20261030/W03-05_CUSTODY_PROTOCOL_20261031.md`。

1. **生成隔离（防污染铁律）**：新故事生成会话**只允许读**：`stage1/`、`stage*_src/`
   规格、既有故事的主题清单（仅标题/主题字段）。**禁止读**：`logs/` 下任何批次输出、
   `candidates/` 失败记录、评审 JSON 的失败原句、错误分类表。
   理由：候选失败原句进入生成视野 → 新故事"恰好绕开"该失败模式 → 泛化指标虚高。
2. **执行动作**：生成会话开工前在事件流记录 `generation_isolation_ack`；
   **凡读过 logs/ 的会话不得当轮产出新故事**。
3. **生命周期单向**：`SEALED ──(任一候选批跑过)──> EXPOSED`，不可逆；
   不存在"重新封存"；账本 `lifecycle` 字段是唯一权威。
4. **暴露账本**：`tools/story_custody.py` 注册（story_id、stage、title/themes、
   episode+reference sha256）；新批开跑前 `expose --story E1xx --batch <批名>
   --arms <臂清单>`，跑完与实际 candidate 目录对账；批次 DONE 报告必须附
   "本次消费 SEALED 清单"。
5. **V1 新增要求**：`lineage_group_id`（新组，§6.1）与 custody 注册并挂——
   新组内任一篇被消费不隐含同组其他篇的暴露状态；暴露仍逐篇记账。
   **[SUPERSEDED 2026-11-08（Phase 2 §8.1）]** 上句仅就**使用记录/账本粒度**
   成立；"逐篇独立保留未见资格"的隐含读法作废——**独立性资格按谱系组算**：
   组内任一篇被消费 → 整组失去 unseen 资格。见本文件 §0 修订节与
   `SPLIT_AND_CUSTODY_PLAN.md` 头部修订节。

---

## 4. gold 卡（reference.json turns[case_id]）

### 4.1 validate_story_v2 硬校验的 9 个必填字段

（validator L177-181 `required_fields`，与真实卡 E117 一致；字段语义出自
`refs/experiment_protocol_20261023/independent_validation_handoff_20261025/card_schema.md`
与 `C_GOLD_AND_SCORING.md` §1）

| # | 字段 | 硬校验 |
|---|---|---|
| 1 | `case_id` | 与轮 case_id 完全一致 |
| 2 | `current_action` | 非空——本轮"事态推进"一句话 |
| 3 | `acceptable_actions` | **≥2 条、不重复**——多解表达清单（不是唯一句子） |
| 4 | `applicable_dimensions` | 非空、⊆ 九维 `DIMS`、**≥3 维**（DIMS = goal/factuality/attribution/time_state/boundary/memory_use/interaction/initiative/emotion_relation，validator L40） |
| 5 | `required_memory_atoms` | 原子断言数组，§4.2 |
| 6 | `private_guidance` | 非空——评审侧引导（不进候选输入） |
| 7 | `unknowns` | list——"不确定不当确定"清单 |
| 8 | `protected_expressions` | list——她/剧本的原话锚点，**每条必须逐字出现在该轮 HER 消息里**（validator L204-209） |
| 9 | `forbidden_factual_claims` | list——自然语言禁令（"不得把…"型） |

另有惯常第 10 项 `may_decline`（真实卡均有）：validator 不强制其存在于卡内，
**评分侧一律以 SEALED episode 的 `input.may_decline` 为准**（`C_GOLD_AND_SCORING.md`
§2.2 冲突裁决条款；卡面与 episode 冲突时登记 `may_decline_conflict_with_card`）。

### 4.2 required_memory_atoms（原子断言）逐字段

以 `benchmark/stories_v2/wave3/E117/reference.json` E117_T01 真实原子为准：

```json
{
  "atom_id": "E1xx_T0k_Axx",
  "subject": "SHARED|HER|ME|THIRD|...（受控五类，见 §7.2）",
  "relation": "拼车计划",
  "value": "HER 周六搭乘 ME 车辆回临州参加表姐婚礼",
  "valid_time": "2026-11-07",
  "status": "asserted",
  "negation": false,
  "source": {"kind": "message|initial_kb", "case_id": "...", "message_id": "m0",
             "quote": "周六你回临州那次还空位吧"},
  "required_this_turn": false
}
```

- `atom_id` 命名沿既有体系（wave 系 `E1xx_Axx` 型；stage 系 `S0xx_Fxx` 型）；
- `source.kind=message`：`quote` 必须**逐字**命中来源消息（validator L235-239，
  含 `message_id` "m%d" 与首个命中位一致性）；来源轮不得晚于当前轮（L228-229，
  禁未来来源）；`required_this_turn=true` 的原子不得来源于当前轮（L230-231）；
- `source.kind=initial_kb`：`key` 必须在 initial_kb 且 `quote` 逐字命中该键值
  （L240-245）；
- 跨篇引用合法：来源 case_id 属同批次更早 story（validator L220-227 按 STORY_ORDER
  解析），引用文件为该 story 的 episode.json；
- `status` 词表按 `GOLD_SCHEMA.md` §3 五基态（`confirmed|tentative|cancelled|invalid|unknown`）
  撰写；取消族（cancelled/superseded/retracted/voided/revoked/invalidated/negated/
  erroneous 或 `negation:true`）→ 层 1 must-not-affirm 判分，**该类原子在 Phase 2
  出题时直接给 `affirm_markers`**（词组数组：组内全词同现=坐实；跨组任一命中=坐实；
  单短语 <4 字不实例化——"提及≠坐实"，宁 unrated 不误判；GOLD_SCHEMA §8.1）；
- `value` 原文与 `source.quote` **永不直接参与判分**（不逐字匹配纪律，
  GOLD_SCHEMA §6）。

### 4.3 unknowns / forbidden / protected 约定（引用 GOLD_SCHEMA）

- **unknowns**：每条 Phase 2 直接送**对象形态** `{"topic": "...", "affirm_markers":
  [["≥4字逐字子串"]]}`——纯字符串形态层 1 只能诚实 unrated（GOLD_SCHEMA §8.3；
  现库 2649 条纯字符串仅 189 条升级的教训：出题时就给信号，别留给 enrichment 猜）；
  anchors 取 topic 与本轮题面的 ≥4 字逐字公共子串。
- **forbidden**：`forbidden_factual_claims`（自然语言禁令）之外，凡需要层 1 C6 判分的
  禁令**必须另给可在 `analysis.scene_facts[].text` 出现的子串**（GOLD_SCHEMA §8 末条：
  自然语言禁令无法子串判定）；`must_not_keywords` 只在有确定性来源时给，
  **不造关键词**（build_gold_sidecar v2 enrichment 同纪律）。
- **protected_expressions**：只收 HER 原话锚点（逐字命中 HER 消息是硬校验）；
  用于评审侧识别"她说过的话"，不构成候选必须复述的要求（rubric §10）。

---

## 5. validate_story_v2 校验要求（新篇过检清单）

运行形态：`python tools/validate_story_v2.py --stage <stage> --turns <n> --count <m>`
（stage 配置化：默认 stage1/8 轮/8 篇；新 wave 按实际参数，E117 型 wave3 为 12 轮）。
任一失败 exit nonzero 并落 JSON 报告。机械校验覆盖（全部离线零模型调用）：

1. 五件套齐全 + episode_id/目录一致 + 顶层键白名单；
2. initial_kb 标量 ≤1000 字符、键域 ALLOWED_KB；
3. 轮数 = 配置值；case_id 全库唯一；turn_type=reply；episode 内 gold={}；
4. input 键白名单；message 键白名单 + role 合法 + text 非空；
   时间纪律（ts≤clock、clock 单调、同轮 ts 递增）；
5. **出题痕迹词扫描**：`note` 与全部可见输入字符串禁含 BANNED 表
   （考察/观测点/评分/考点/本题/测试点/"与上轮不同"/evaluation point/grading/rubric）；
6. reference 9 字段齐全；acceptable_actions ≥2 且不重复；dimensions ≥3 且 ⊆ DIMS；
   protected_expressions 逐字命中 HER 消息；原子断言字段齐全 + quote 逐字命中 +
   message_id 一致 + 无未来来源 + required 不自证；
7. timeline：turn_clocks 数=轮数；message_index 与 episode 消息逐字三元组一致；
   events.source_turn / revisions.case_id 存在；
8. exposure：9 字段齐全、real_user_material=false、experiments_used 空、
   frozen_at 规则、hashes 逐文件对账、runner_copy 与正本逐字一致；
9. audit.md 八节标题齐全；
10. manifest hashes 逐文件对账。

RESEARCH_STATE 记录的既有过检先例（机械校验有效性的证据）：wave3 T4 终扫
"56/56 validate、seed 刷新、verify OK、152 篇 0 近重簇"
（`C_GOLD_AND_SCORING.md` §3）。

---

## 6. 外部指导书新增的每篇必带项

### 6.1 primary_topic 与 lineage_group_id（登记在 manifest 侧）

- **登记位置**：wave/stage manifest 的每篇条目（**不进 episode.json**——validator
  顶层键白名单已封死；如确需进 episode 必须先扩 validator 白名单并版本化，
  旧答卷不受影响才合法）。
- `primary_topic`：T01–T10 十大专题之一（名称与分类口径以用户指导书 §2.3 为准，
  与 Phase 1 盘点 `TOPIC_COVERAGE_MATRIX.csv` 的分类一致；判据工作定义见
  `TOPIC_RUBRIC_DRAFT_V1.md`）。既有库逐篇标注含 `primary_topic` /
  `secondary_topics` / `annotation_basis` / `annotation_confidence` 列
  （DATASET_INVENTORY.csv）——新篇沿用同一 schema；单篇可多专题覆盖，但主标签
  只有一个，其余作次标签。
- `lineage_group_id`：**新组**——新篇的谱系组 id（用于 CF 孪生/变体追踪与
  同组管理）。约束：
  - 新造篇**不得挂在既有 lineage 组下**（防蹭既有组的暴露/评审状态）；
  - 同组内 CF 孪生共享 `lineage_group_id`，互为单变量对照；
  - 组登记进 custody 事件（`tools/story_custody.py` 注册项旁路字段），
    暴露状态仍**逐篇**记账（组消费≠全组暴露）。
    **[SUPERSEDED 2026-11-08（Phase 2 §8.1）]** "组消费≠全组暴露"仅就使用
    记录成立；**资格判定=组**：组内任一篇被消费 → 整组失去 unseen 资格，
    且 CF 组不得跨 DEV/SELECTION/HOLDOUT 分区。见本文件 §0 修订节。

### 6.2 intent 契约

- 带意图的轮：episode `input.intent` + reference 卡意图方向 + gold sidecar
  `intent_contract`（`keywords_any`/`must_not_keywords` 由 build_gold_sidecar
  确定性 enrichment 从题面派生，出题侧不手造关键词——现库 117 意图契约中 114
  拿到关键词组，`C_GOLD_AND_SCORING.md` §2.3）。
- 每篇至少 `<INV:TP-INTENT-POOL>` 个意图轮（配额由盘点确认）。

### 6.3 ≥2 个跨轮回指位

- 每篇**至少 2 个跨轮回指位**：本轮正确回复需引用 1 轮以前的信息（其一须 ≥4 轮
  距离，长短距搭配）；回指内容落 `required_memory_atoms`（`source.case_id` 为
  既有轮，validator 天然校验"非未来来源"）。
- 回指位在 reference 卡 `applicable_dimensions` 必须含 `memory_use`。

### 6.4 ≥1 个合法不回应候选位（如适用）

- `may_decline=true` 的轮 ≥1（**如适用**：纯推进型短篇可豁免，豁免须在 audit.md
  §4 说明理由）；
- 该位 reference 卡 `acceptable_actions` 必须含一条"合法不回"型多解
  （rubric §10 合法不回自由度）；gold 侧不强迫文本（层 1 走
  `decline_appropriateness` 分支，GOLD_SCHEMA §6）。

---

## 7. 已知工艺手法（引勘误台账先例，防再犯）

### 7.1 账目陷阱 / 时间陷阱

出处：`refs/dataset_review_20261107/C_GOLD_AND_SCORING.md` §3/§4.1 勘误台账。

- **账目陷阱（正向工艺）**：HER 主动报错数 → 卡面引导指出 → **gold 不背书错误
  算术**。先例：LRE128_T35（"省60"实为超60）、E164_T05（"三十七块七"与自报 24 次
  除数不符）——改造落在 reference 卡 `unknowns`（除数口径未知）与
  `forbidden_factual_claims`（"不得把三十七块七背书为正确换算结果"）。
  新篇造账目轮直接按此范式出题，不留"gold 背书错数"的隐患。
- **时间陷阱**：多处更正级联（E117：T04/T06/T07/T08/T11 多处变更级联——
  人数/费用/空间/时间/回程，episode feedback 自记"第二处更正/第三次微更正/当日
  第四轮变更"）；
  陷阱的判据载体是原子断言（旧口径 must-not-affirm + affirm_markers），
  不是题面隐藏答案。
- **已踩过的坑（负向清单，不得复现）**：
  - 时钟×卡面矛盾（E-MAT-E021-T07：周三时钟 vs "今晚有球"设计）——出题后
    **逐轮对时钟核卡面**；
  - 时间视野跳变（E029-T07：12 月时钟问 9 月假期）——询问对象的"过去/未来"
    方向必须与时钟一致；
  - 孪生篇措辞 vs 时间戳不一致（E149_T05"明天/后天"与 +1 天）——CF 孪生的
    扰动位唯一且自洽；
  - 剧本自相矛盾数字（E065-T21"15200 vs 14600"）——**设计内矛盾可以，前提是
    gold 明确"不背书任何一侧错误"**；无意的算术错误不可以。

### 7.2 主体与状态词表纪律

- `subject` 收敛为受控五类（`HER / ME / 双方(SHARED) / 第三方(THIRD) / 关系(SHARED-REL)` 型）——
  现库中英混用（她/我 vs SHARED/THIRD/ME/HER）是已登记松散点
  （`C_GOLD_AND_SCORING.md` §4.3），新篇**不得新增混用**；
- `status` 按 GOLD_SCHEMA 五基态写；超集词（asserted/seed/suspended…）不新造；
- 域标签显式化（F_REDESIGN_DRAFT §2.2-3：32 篇派生域归属的教训——新篇 manifest
  直接写 `domain` 字段，不留派生）。

---

## 8. 质量门槛（全过才入库）

| # | 门槛 | 判定 | 依据/先例 |
|---|---|---|---|
| 1 | **near-dup = 0** | `tools/story_skeleton_dedupe.py` 全库普查 0 近重簇（含新篇后复跑） | 先例：184 篇标准 0 近重簇（累计 32→48→128→184 复跑均 0，RESEARCH_STATE updates） |
| 2 | **断言时间戳 100%** | 全部 `required_memory_atoms[].valid_time` 非空且与时钟/消息 ts 自洽 | 先例：gold sidecar 6603 原子 100% 带时间戳（`C_GOLD_AND_SCORING.md` §1.3） |
| 3 | **marker QC 0 违例** | build_gold_sidecar v2 enrichment `marker_qc.verbatim_violations = 0`（1099 markers 逐字回验先例） | `C_GOLD_AND_SCORING.md` §2.3 |
| 4 | validate_story_v2 全过 | exit 0 + JSON 报告留档 | §5 |
| 5 | custody 注册 + SEALED | sha256 双哈希入账，冻结前 `frozen_at=null`，冻结时 `story_custody.py` 只 append | W03-05 §3/§4 |
| 6 | gold 侧四类判定信号齐备 | must-not-affirm 原子 affirm_markers、cancelled 原子 item_markers、unknowns 对象化、意图轮 keywords_any（无信号处诚实 unrated，不造） | GOLD_SCHEMA §8 |
| 7 | 出题痕迹词 0 命中 | BANNED 表全输入扫描 | validate §5.5 |

入库即注册 SEALED；**任何新篇在被用于判决前禁止出现在任何 harness 可见输入中**
（含失误日志、few-shot 示例——F_REDESIGN_DRAFT §4-4 制度条款）。

---

## 9. 出题会话开工清单（Phase 2 执行入口）

1. 确认生成会话未读 logs/ 与 candidates/（读过的会话不得当轮产新故事）；
2. 事件流记录 `generation_isolation_ack`；
3. 读题材料限于：`stage1/`、`stage*_src/` 规格、既有主题清单（仅标题/主题）；
4. 按 §1–§4 出 episode + reference + timeline + exposure + audit 五件套；
5. `validate_story_v2 --stage <新stage> --turns <n> --count <m>` 全过；
6. `story_skeleton_dedupe.py` 复跑 near-dup=0；
7. manifest 登记（含 `primary_topic` / `lineage_group_id` / `domain`）；
8. `tools/story_custody.py` 注册 SEALED（hash 双账）；
9. gold sidecar 由 `tools/build_gold_sidecar.py` 离线构建（零模型调用），
   marker QC 0 违例后并入 GOLD_<wave>.json。

占位符汇总（填充责任=Phase 1 盘点工作包，出处 `DATASET_INVENTORY.csv` /
`TOPIC_COVERAGE_MATRIX.csv`，均由工作包二于 2026-11-07 交付）：
`<INV:TP-INTENT-POOL>` 每篇意图轮配额；`<INV:NEW-COUNT>` Phase 2 新篇总量与
分专题配额（分专题缺口与可复用现状直接读
`DATASET_COMPOSITION_ACTUAL.md` §5 缺口表——该表按"均配 24 篇"假设计算，
配比若指导书另有规定以指导书为准）；`<INV:DOMAIN-QUOTA>` 分域配额（12 域基线）。
