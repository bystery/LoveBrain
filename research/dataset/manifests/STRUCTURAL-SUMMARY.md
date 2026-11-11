# dataset/ KB 快照结构摘要（结构层公开件）

> **定位说明**：本文件为 `dataset/` 5 个真实 KB 快照的**结构摘要**——原件为真实聊天导出的知识库快照，含真实姓名、对话与健康信息，属"无法安全脱敏"档，**本地保留、永不出站**（2026-11-12 迁移决策三：原件 100% 不进公开仓库，含任何脱敏形式；公开形式 = 本结构摘要 + [`synthetic_samples/`](../synthetic_samples/) 合成替代样本）。本摘要仅含结构层信息，不含任何原件正文。

- 依据：内部收敛指导书第 1 份 §3.2 末款（"无法安全脱敏的数据：只保留内部原件和哈希/数量/字段结构摘要，不上传正文"）+ §8.2 第 4/5 条（生成合成替代样本 / 保留原件本地 SHA 与审查记录）。
- 源：真实 KB 快照原件目录（实测 84 文件 / 735,202 字节 / 5 个 KB 快照目录；`du` 块口径 865KB，字节口径 718KiB≈735KB，与指导书 §3.2 "约735KB"一致）。
- 配套口径：逐文件敏感性判定（64 文件 PRIVATE_SENSITIVE / 20 文件 PUBLIC_SANITIZED）属内部隐私审查记录，不随本摘要公开；本文仅承接其数量口径。
- **内容纪律：本文仅包含键名、产品固定章节名、计数、字节数与掩码格式骨架（字母/汉字/数字一律以 × 代替）。零真实姓名、零对话、零事件细节。** 本文按 §3.2 末款属"字段结构摘要"性质，可进入公开仓库；原件不公开已按 2026-11-12 决策三收口。

**目录代号**：其中一个 KB 目录名为真实姓名拼音首字母缩写，本文以 **KB-g** 代指。其余四个为产品生成的无含义随机 ID，直接使用：**mu11** = `kb_mu11oc9u_ac`、**mu20i8** = `kb_mu20i8p7_6d8`、**muihgls** = `kb_muihgls3_3u2`、**munm** = `kb_munm0rk6_6ys`。

---

## 一、标准 KB 目录结构模板

### 1.1 目录树模板（19 种文件名，84 个实例）

```
kb_<id>/
├── kb.json                          # 关系元数据            必有 5/5
├── .schema_version                  # schema 版本号(1字节)  可选 3/5
├── memory/
│   ├── counseling_log.md            # 谈心记录              5/5
│   ├── lessons.md                   # 经验提取库            5/5
│   ├── raw_chat.md                  # 聊天尾片段(轮次级)    5/5（1 份 0 字节）
│   ├── raw_scene.md                 # 事件时间线摘要        5/5（4 份 0 字节）
│   ├── raw_topic.md                 # 逐字话题档案(主体)    5/5
│   ├── reflect_history.md           # 画像更新流水          5/5
│   ├── vector_history.md            # 向量重估流水          5/5
│   └── .lessons.pre-tidy.md         # tidy 前备份           可选 1/5
├── moment/
│   ├── plan.md                      # 事项计划              5/5
│   ├── recent.md                    # 最近聊天尾片段        5/5
│   ├── scene.md                     # 单行场景摘要          5/5（3 份 0 字节）
│   ├── topic.md                     # 单行话题名            5/5
│   ├── .round_commit_state.json     # 轮次提交状态          可选 3/5
│   └── ongoing_cooldown.json        # 事项注入冷却          可选 2/5
└── understand/
    ├── her.md                       # 对方画像              5/5
    ├── me.md                        # 用户画像              5/5
    └── warmth.md                    # 关系状态              5/5
```

注：`.round_commit_state.json` 与 `ongoing_cooldown.json` 实际位于 `moment/` 子目录下，不是 KB 根（本文按实际位置记录）。

### 1.2 文件角色与字段/章节模板

**kb.json**（5/5，169–200 B，JSON 对象，7 键以内）

| 键 | 类型 | 说明 |
| --- | --- | --- |
| `name` | string | KB 目录名（KB-g 中与目录同名缩写；其余为 kb_ 随机 ID） |
| `displayName` | string | 对方显示名（真实内容敏感点，公开侧一律以合成占位符替代） |
| `updatedAt` | string | 时间戳字符串（实测 25 字符，含时区形态） |
| `stage` | string | 关系阶段枚举短串（实测出现：稳定期、暧昧期） |
| `turnCount` | int | 累计轮数 |
| `topicCount` | int | 累计话题数 |
| `active` | bool | 可选键，仅 mu20i8 出现（1/5） |

**memory/counseling_log.md**（5/5，138 B–18.3 KB，7–133 行）
- 固定骨架：`# 谈心记录` 与 `# 军师分析` 两个一级标题，各带 1 条固定 HTML 用法注释（产品模板自述，无个人内容）。
- 有内容时：前节下插入 `## [YYYY-MM-DD HH:mm] 谈心` 条目（正文为 `说话人前缀：…` 段落）；后节下插入 `## [ts] {{摘要标题}}` 条目（正文为分析段落）。
- 2/5（mu11、muihgls）为**纯模板**（仅 2 个 H1 + 2 条注释），两份逐字节相同（MD5 一致），隐私审查已判 PUBLIC_SANITIZED，可作合成底版参考（原件本身不出站）。

**memory/lessons.md**（5/5，约 0.7–11.4 KB，36–197 行）
- 顶部 1 条固定用法注释；批头 `# [YYYY-MM-DD HH:mm] 第N次提取`；批内 `## {{主题}}` 节；正文为 `- {{要点}}` bullet 列表（单文件 8–64 条）。

**memory/.lessons.pre-tidy.md**（1/5，mu20i8，10,562 B，203 行）
- 见 §3.4。固定节名：`# 经验库` + `## 测试接法 / 做对的 / 加分项 / 踩过的坑 / 阶段转换`，另有带时间戳的批头 H1。

**memory/raw_chat.md**（5/5，1 份 0 字节，非空 0.3–3.9 KB，10–89 行）
- 轮次结构：`- [YYYY-MM-DD HH:mm]` 轮次头 → `<!-- round:msgIds:{{UUID}},… -->` 标记 → 逐字对话行（`她：…` / `我…：…`）。
- 单文件 1–9 轮。掩码骨架：
  ```
  - [××××-××-×× ××:××]
  <!-- round:msgIds:×××…,×××… -->
  ×：×
  ×：×
  ```

**memory/raw_scene.md**（5/5，4 份 0 字节；唯一非空的 KB-g 实例 752 B，2 行）
- 2 条 `- {{事件摘要}}` bullet 行。

**memory/raw_topic.md**（5/5，9.9–319 KB，197–4,316 行；占全库 72.8%）
- 文件头：1–2 条固定格式自述注释 + 占位样例标题 `# [yyyy-MM-dd HH:mm] 话题名`（产品自带的格式说明行；KB-g 旧快照无此自述头）。
- 每话题三段式标题（各快照严格 1:1:1 配对）：
  ```
  # [{{ts}}] {{话题名}}
  ## [{{ts}}] 状态变化
  ### [{{ts}}] 对话记录
  ```
- 正文 bullet 行：`- [{{ts}}] {{说话人}}：…`（对话记录节）与状态要点行；轮次间夹 `<!-- round:msgIds:… -->` 注释（mu20i8 35 条、munm 40 条、muihgls 8 条、KB-g 2 条）。
- munm 含 1 个迁移标记标题 `# [legacy] 以下为无法解析时间戳的历史内容`（其后为旧格式内容）。

**memory/reflect_history.md**（5/5，0.5–23.8 KB，14–162 行）
- 两种条目交替：`### [{{ts}}] 五维向量变化`（向量行 `{{维度}}：{{值}}｜{{维度}}：{{值}}｜…`，5 维以全角｜分隔）与 `## [{{ts}}] 画像更新建议`（正文段）。
- 1/5（mu11）含 1 个 ``` 围栏代码块（内嵌 JSON 画像对象）。

**memory/vector_history.md**（5/5，0.2–11.2 KB，8–160 行）
- 条目：`## [{{ts}}] 向量重估`，每条下 5 行 `- {{维度}}：{{分值}} (+{{Δ}})`。
- 五维枚举（产品词表）：亲密、激情、承诺、信任、安全。

**moment/plan.md**（5/5，0.2–3.9 KB，8–22 行）
- 固定骨架：`# 事项计划` / `## 进行中` / `## 已结束`。
- 条目为非 bullet 整行：`{{事项名}} | {{状态词}} | [{{ts}}]{{叙述}}…（{{结局注记}}）`——竖线三段式，时间戳内联叠加。

**moment/recent.md**（5/5，8–19 行）
- 与 raw_chat.md 同构（轮次头 + msgIds 注释 + 对话行），单文件 1–2 轮；另有产品草稿行形态 `（{{注记}}，{{字段}}：…`（"用户偏好，未确认发送"性质）。

**moment/scene.md**（5/5，3 份 0 字节；非空为单行）
- 单行 `- [{{ts}}] {{场景摘要}}`。

**moment/topic.md**（5/5，29–46 B，恒 1 行）
- 单行 `- [{{ts}}] {{话题名}}：{{补充}}`。

**understand/her.md**（5/5，18–52 行）
- 成熟版 8 节 H2（KB-g 与 mu11 节名同序同形）：`基本信息 / 依恋类型 / 沟通密码 / 决策风格 / 核心恐惧 / 行为模式 / 爱的表达方式 / 雷区`；mu20i8 为 6 节变体；muihgls/munm 为 2 节早期版。
- 节内为 `- {{键}}：{{值}}` bullet 字段。

**understand/me.md**（5/5，16–40 行）
- 成熟版 6 节 H2（KB-g/mu11/mu20i8 同形）：`基本盘 / 性格画像 / 性格弱点（军师要时刻防着我犯） / 底线（不可违背） / 关系目标 / 当前任务`；模板版 2 节：`基本信息 / 性格画像`（muihgls 实例为纯模板，全部字段为"待了解/待积累"占位，隐私审查判 PUBLIC_SANITIZED）。
- 末行为 `**{{字段}}**: {{值}} | **{{字段}}**: {{值}}` 页脚。

**understand/warmth.md**（5/5，26–64 行）
- 成熟版 7 节 H2（KB-g 与 mu11 同形）：`当前状态 / 状态向量 / 阶段核心任务 / 阶段策略 / 互动模式 / 关键节点时间线 / 背景细节`；mu20i8 为 8 节变体；muihgls/munm 为 3 节早期版。
- 状态向量节使用与 vector_history 相同的五维词表，行格式为 `{{维度}}：{{值}}｜…`。

---

## 二、数量与体量统计

### 2.1 每 KB 目录汇总（真实字节）

| KB | 文件数 | 字节 | 根 | memory | moment | understand | 机制文件 | 0字节文件 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| KB-g | 15 | 105,889 | 1 / 169 | 7 / 92,553 | 4 / 2,754 | 3 / 10,413 | 0 | 0 |
| mu11oc9u_ac | 15 | 175,093 | 1 / 177 | 7 / 161,231 | 4 / 8,791 | 3 / 4,894 | 0 | 2 |
| mu20i8p7_6d8 | 19 | 397,730 | 2 / 201 | 8 / 378,150 | 6 / 4,449 | 3 / 14,930 | 4 | 2 |
| muihgls3_3u2 | 17 | 19,795 | 2 / 182 | 7 / 14,551 | 5 / 1,847 | 3 / 3,215 | 2 | 2 |
| munm0rk6_6ys | 18 | 36,695 | 2 / 185 | 7 / 26,217 | 6 / 6,687 | 3 / 3,606 | 3 | 2 |
| **合计** | **84** | **735,202** | 5 / 914 | 36 / 672,702 | 25 / 24,528 | 15 / 37,058 | 9 | 8 |

（"机制文件"=.schema_version / .round_commit_state.json / ongoing_cooldown.json / .lessons.pre-tidy.md 之和：KB-g 0、mu11 0、mu20i8 4、muihgls 2、munm 3。）

### 2.2 全库文件类型矩阵（19 种文件名 × 84 实例）

| 文件 | KB 覆盖 | 实例数 | 总字节 | 行数范围 |
| --- | --- | --- | --- | --- |
| kb.json | 5 | 5 | 911 | — |
| .schema_version | 3 | 3 | 3 | — |
| memory/counseling_log.md | 5 | 5 | 23,299 | 7–133 |
| memory/lessons.md | 5 | 5 | 30,940 | 36–197 |
| memory/.lessons.pre-tidy.md | 1 | 1 | 10,562 | 203 |
| memory/raw_chat.md | 5 | 5 | 6,914 | 0–89 |
| memory/raw_scene.md | 5 | 5 | 752 | 0–2 |
| memory/raw_topic.md | 5 | 5 | 535,496 | 197–4,316 |
| memory/reflect_history.md | 5 | 5 | 43,176 | 14–162 |
| memory/vector_history.md | 5 | 5 | 21,563 | 8–160 |
| moment/plan.md | 5 | 5 | 14,627 | 8–22 |
| moment/recent.md | 5 | 5 | 2,752 | 8–19 |
| moment/scene.md | 5 | 5 | 928 | 0–1 |
| moment/topic.md | 5 | 5 | 335 | 1 |
| moment/.round_commit_state.json | 3 | 3 | 4,202 | — |
| moment/ongoing_cooldown.json | 2 | 2 | 1,684 | — |
| understand/her.md | 5 | 5 | 11,162 | 18–52 |
| understand/me.md | 5 | 5 | 11,610 | 16–40 |
| understand/warmth.md | 5 | 5 | 14,286 | 26–64 |

### 2.3 全库总口径

- **84 文件 / 735,202 字节**（≈718 KiB；`du` 磁盘块口径 865 KB；指导书 §3.2 "约735KB" 与字节口径一致）。
- 体裁构成：`.md` 71 个 = 728,402 B（99.07%）；JSON 机制 13 个 = 6,800 B（0.93%）（10 个 `.json` 后缀 + 3 个无后缀点文件）。
- 体量集中度：`memory/raw_topic.md` 5 件合计 535,496 B = **72.8%**；其中三大件 mu20i8 318,985 B、mu11 131,513 B、KB-g 59,354 B（合计 69.3%）。次大件：mu20i8 reflect_history 23,761 B、KB-g counseling_log 18,325 B。
- 标记扫描：`synthetic` / `sanitized` / `origin=` / `mock` / `fake` / `placeholder` 全库 **0 命中**（§8.2 第 1–3 条合成标记约定在真实快照中均未落实）。
- 0 字节文件共 **8 个**：raw_chat ×1（mu11）、raw_scene ×4（除 KB-g 外全部）、moment/scene ×3（mu20i8、muihgls、munm）。

---

## 三、schema 与机制文件说明（键结构层）

### 3.1 `.schema_version`
- 分布 3/5（mu20i8、muihgls、munm），KB-g 与 mu11 无此文件。
- 每份恒 **1 字节、内容 `3`**。
- 作用推断：KB 快照结构版本号；与 `.round_commit_state.json` 的出现范围完全重合，二者属同一代快照机制；KB-g/mu11 为无版本标记的早期代。

### 3.2 `moment/.round_commit_state.json`（3/5，共 4,202 B）
键结构（三份完全同构）：

```json
{
  "committedRounds": ["{{32位hex}}", "…共 N 条（实测 35/12/47）"],
  "projections": {
    "topicRotate": "{{32位hex}}",
    "topicSet":    "{{32位hex}}",
    "recent":      "{{32位hex}}",
    "scene":       "{{32位hex}}",
    "plan":        "{{32位hex}}",
    "count":       "{{32位hex}}"
  }
}
```

- 作用推断：moment/ 目录的**两阶段提交状态**。`committedRounds` 为已提交会话轮的哈希列表（幂等去重）；`projections` 为各投影文件的内容指纹——`recent/scene/plan` 与 moment/ 三个 md 一一对应，`topicRotate/topicSet` 对应 topic.md 的两个投影维度，`count` 无对应文件、应为话题计数的投影。用途：重启恢复时校验投影文件是否与已提交状态一致。
- 敏感性：全部值为内容派生哈希，隐私审查判 PUBLIC_SANITIZED（3 份均无个人内容）。

### 3.3 `moment/ongoing_cooldown.json`（2/5：mu20i8、munm，共 1,684 B）
键结构（顶层数组）：

```json
[
  {
    "name": "{{事项名}}",
    "lastInjectedTurn": 0,
    "lastInjectedTime": "{{YYYY-MM-DD HH:MM}}",
    "lastEvidenceTurn": 0
  }
]
```

- 作用推断：**事项注入冷却表**——对 plan 中的进行中事项记录最近一次注入轮次/时间与最近证据轮次，避免同一事项每轮重复注入对话。`name` 与 plan.md 条目名对应（该字段即真实事项名，隐私审查判低危敏感）。
- 机制注记：冷却表条目与 plan.md 中疑似跨会话混入条目对应，说明该表的条目来源可能跨会话共享（机制判定属 harness 代码域，dataset 单侧无法定论）。

### 3.4 `.lessons.pre-tidy.md` 备份现象（1/5：mu20i8）
- 命名即语义：`lessons.md` 执行 tidy（整理）操作前的自动备份。
- 证据：与现行 `lessons.md` 同顶部固定用法注释、同体裁（`# 批头` + `## 主题节` + bullet），但**内容不同**（203 行/10,562 B vs 现行 197 行/11,405 B），且保留更旧的固定节组织（`# 经验库` + 测试接法/做对的/加分项/踩过的坑/阶段转换五节）——即备份≠副本，tidy 对经验条目做过重组。
- 迁移含义：迁移时两个文件都保留原样（"不删除历史版本"精神）；合成替代件可只生成 `lessons.md`，pre-tidy 备份标记为可选文件。

### 3.5 HTML 注释机制（三类）
1. **文件头固定用法注释**：counseling_log（2 条，解释两个 H1 的填写规则）、lessons/pre-tidy（1 条）、understand 三件（1 条"由系统生成"类说明）、raw_topic（1–2 条格式说明）、reflect_history（1 条）。均为产品模板自述，无个人内容。
2. **轮次标记** `<!-- round:msgIds:{{UUID}},… -->`：出现在 raw_chat/recent（每轮 1 条）与 raw_topic（话题/轮次间），记录该轮消息 ID，供回溯定位。
3. **迁移标记** `# [legacy] …` 标题（munm raw_topic）：标识无法解析时间戳的历史内容段。

---

## 四、合成替代样本（synthetic KB）生成规范

用于指导公开示例数据生成（对真实聊天生成合成替代样本，不发布脱敏原件）。目标：任何目录/文件被单独拷走仍能自证合成。

> 落位注记：实际公开落位见 [`synthetic_samples/`](../synthetic_samples/)（目录名 `synthetic_kb_01`–`synthetic_kb_05`，库级清单为该目录下 `SYNTHETIC_MANIFEST.json`，共 92 文件 = 5 套合成 KB 90 件 + 库级 README 与清单 2 件，落树前已通过强制隐私扫描）。本节为生成规范底稿，与实际落位不一致处以落位实现为准。

### 4.1 目录与文件清单模板（每合成 KB 一套）

```
kb_synthetic_demo_<NN>/
├── README.md                        # 【新增】说明本目录为合成样本
├── _synthetic_manifest.json         # 【新增】合成清单（见 4.3）
├── kb.json
├── .schema_version                  # 内容固定 "3"
├── memory/  (counseling_log / lessons / raw_chat / raw_scene / raw_topic / reflect_history / vector_history)
├── moment/  (plan / recent / scene / topic / .round_commit_state.json / ongoing_cooldown.json)
└── understand/  (her / me / warmth)
```

建议生成 2 套：**空模板套**（全部文件=模板态，含 0 字节的 raw_scene/scene 与空 counseling_log）+ **中等填充套**（raw_topic 3–5 个话题、200–400 行），覆盖真实库中出现的三种形态（0 字节 / 纯模板 / 满数据）。

### 4.2 各文件模板与占位符约定

- 骨架依据：已判 PUBLIC_SANITIZED 的 3 份真实模板的固定注释与章节名即产品骨架（原件本身不出站，仅以其骨架为参考，不复制原件）。
- 其余文件按 §1.2 骨架生成；正文占位符约定：

| 占位符 | 用于 | 约定 |
| --- | --- | --- |
| `{{KB_NAME}}` | kb.json.name、目录名 | `synthetic_kb_01` 形态 |
| `{{DISPLAY_NAME}}` | kb.json.displayName | 虚构昵称（不复用任何真实昵称/姓名） |
| `{{TS}}` | 全部时间戳 | `YYYY-MM-DD HH:mm`，使用虚构日期区间，间隔随机化 |
| `{{UUID}}` | round:msgIds | 随机 UUID，不得复用真实 msgIds |
| `{{TOPIC_TITLE}}` | raw_topic 话题名/topic.md | 中性合成话题（如"周末预算讨论"级），禁用地名/校名/日期组合 |
| `{{USER_LINE}}` / `{{COMPANION_LINE}}` | 对话行 | 说话人前缀保持产品格式 `她：…` / `我…：…`；内容为通用日常对话 |
| `{{DIM}}:{{SCORE}} (+{{DELTA}})` | 五维向量 | 维度固定用产品词表（亲密/激情/承诺/信任/安全），分值随机，不复制真实分值序列 |
| `{{ITEM_NAME}}` / `{{STATUS}}` | plan/ongoing_cooldown | 合成事项名（日常类虚构事项）；状态沿用真实行中的枚举词形态 |
| `{{STAGE}}` | kb.json.stage | 枚举词（真实库已见：稳定期、暧昧期） |
| `{{EVENT_SUMMARY}}` | raw_scene/scene | 一句合成事件摘要，零真实地名/人名 |

- kb.json 合成模板：
  ```json
  {
    "name": "{{KB_NAME}}",
    "displayName": "{{DISPLAY_NAME}}",
    "updatedAt": "{{TS}}",
    "stage": "{{STAGE}}",
    "turnCount": 0,
    "topicCount": 0,
    "active": true
  }
  ```
- .round_commit_state.json 合成值：32 位 hex 用随机占位（manifest 中注明"非内容派生哈希"）；ongoing_cooldown 条目 1–3 条即可。
- 结构自检清单：每 KB 13 文件齐备；raw_topic 话题三段标题 1:1:1 配对；vector_history 每条目恰 5 行 bullet；plan.md 三节骨架不变。

### 4.3 `synthetic=true` 标记位置（三层，缺一不可）

1. **库级**：`_synthetic_manifest.json`：`{"synthetic": true, "generator": "<脚本名+版本>", "generated_at": "{{TS}}", "replaces": null, "note": "全合成样本，无任何真实人物/对话/事件"}`（真实库无此文件，作为合成件专属标识）。
2. **文件级**：`kb.json` 顶层加 `"synthetic": true` 键；README.md 声明。
3. **行内级**：每个文件首行加 `<!-- synthetic=true origin=synthetic-generator -->`。
- 理由：真实快照未落实任何合成标记约定，说明产品运行时不会写标记；合成件必须自显式三层标记，防止单文件被拷出后失去身份。

### 4.4 禁区（生成时硬性校验）

- 不复用任何真实时间戳、话题名、事项名、昵称、msgIds、五维分值序列；
- 不出现真实地名/校名/机构名/平台私信起源叙述；
- 时间戳区间与真实快照日期区错开；
- 生成后按隐私扫描口径（synthetic 标记、手机号/邮箱/URL、机构名词表）反向自检，并抽查"含真实姓名 grep"应为 0 命中。
