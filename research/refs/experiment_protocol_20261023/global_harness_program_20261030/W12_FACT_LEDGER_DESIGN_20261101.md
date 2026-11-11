# W12 机制设计：fact_ledger_v1（FK-LR2-01 修复候选 A）＋ 对照臂 B 最小规格

日期：2026-11-01 ｜ 故障族：FK-LR2-01（长剧域逐字事实保持塌陷，refs/KNOWN_FAILURES.md 尾部）
证据基线：refs/experiment_protocol_20261023/long_probe_20261030/LR2_VERDICT_20261101.md、
logs/longprobe_lr2_live_20261101/_reports/WINDOW_KEEP_LR2.json（T25-32 机械保持 47.6%/50.4%，
亲读真实保持 55.7%/38.6%，r9_slim 记忆行仅 +2~6pp）
本文为纯设计文档：零模型调用、不改任何现有代码。执行前须按预注册风格另立 AUTH 与 preregistration。

## 1. 机制假设（主假设，可证伪）

**H_FL_main**：在 capture 时把真实消息按原子粒度拆成逐字事实入账，召回时按
"类型权重 × 输入相关度 × 时间衰减" 注入 top-k 事实行，能在 40 轮域把 T25+ 段
真实窗口保持率从 ~50% 提到 ≥85%，同时 ≤24 轮域九维不退化（最差维 ≥−0.3）、
prompt usage ≤ r9_slim +15%。

**H_FL_null**：注入行不提升真实保持（Δ ≤ +5pp）或触发九维任一维 ≤−0.5
（如注入行挤占注意力致 interact/轻松互逗退化）→ 候选淘汰，不调参重跑到赢。

机制归因：塌陷成因是双臂共用"persona 种子 + 最近 12 条源消息滑窗"（LR2 判决 §决定性发现），
记忆行概括粒度不足以承载逐字召回。本候选把保持职责从滑窗容量移到结构化账本，
属 M1（存储结构）+M2（检索注入）变异，非提示词缝补。

## 2. 候选 A：fact_ledger_v1 完整规格

### 2.1 基座与目录契约

- 目录：`candidates/fact_ledger_v1/`，含 `mechanism/mechanism.py`、`mechanism/ledger_rules.py`、
  `params.json`、`MECHANISM.md`、`IDENTITY.md`，满足 agents.md §2 契约。
- 基座 = `candidates/v0_minimal_events/mechanism/mechanism.py`（LR2 实测 prompt 比 r9_slim
  省 50.3%，且 v0 在 ≤24 轮域地位不变——在它上面叠账本，隔离"账本贡献"这一单变量）。
  复用方式与 v0 加载 reference_product_v1 相同：importlib 加载 v0 模块，复用其
  `init`/`snapshot`/`restore` 骨架与 `run_turn` 的 parts 装配顺序（v0 mechanism.py L130–203），
  在历史段之前插入账本段；不 monkeypatch，显式复制装配并注明父本（IDENTITY.md 声明）。
- system 资产沿用 v0 自带的精简资产，不改（控制变量：本候选只动 M1/M2）。

### 2.2 账本条目 schema

```json
{
  "fact_id": "F012",
  "text": "逾期赔房东违约金",            // 逐字原文（clause 级 verbatim，不概括、不改写）
  "subject": "HER|ME|SHARED|THIRD|UNKNOWN", // 与 r1 层 SUBJECTS 集对齐
  "kind": "promise|date|time|number|amount|event|other",
  "digits": ["14"],                      // 复用 r7 _digits 抽取，供冲突判别与相关度加成
  "capture_turn": 5,
  "ts": "2026-11-06 09:12",              // 消息时间戳（复用消息自带 ts）
  "valid_time": "2026-11-07",            // 规则可解析出的未来时点，缺省 ""
  "src_uids": [17],                      // 复用 v0 _uid_for 的稳定 uid
  "status": "asserted|superseded|cancelled",
  "superseded_by": "",                   // 继承 r7 supersede 语义
  "origin": "message|seed"               // seed=persona/initial_kb，永不被降级（r7 规则）
}
```

状态机：`asserted → superseded`（更正，旧条目保留不删，继承
`candidates/action_policy_r7_supersede/mechanism/mechanism.py` `_supersede_pass` 语义）、
`asserted → cancelled`（作废）。superseded/cancelled 条目不再注入，但留在账本与 snapshot 里可审计。

### 2.3 capture 触发时机与零额外调用

- 触发点：`run_turn` 内、本轮真实消息经 `_uid_for` 入 events 之后、组装 parts 之前，
  对本轮新增 HER/ME 消息逐条跑 `_capture_facts`。模型草稿永不入账（v0 L195 边界继承）。
- **抽取为零额外模型调用**：运行时每轮只有一次 `gw.generate(..., tag="main")`（v0 L192），
  本候选不增加任何 generate 调用；抽取全部规则化（见 2.4）。
  不设计"让模型在 analysis 里报原子"的方案——那是 r1 链路的形态，会绑定 action-policy 输出契约，
  且本候选基座 v0 无该契约。

### 2.4 入账抽取规则（规则优先，ledger_rules.py）

clause 切分：消息 text 按 `[，。！？；\n]` 切 clause，clause ≤60 UTF-16 码元，超长弃。
仅当 clause 命中以下任一信号才入账；`fact_id` 递增；问句（`?/？`结尾且无 R3 信号）不入账。

| 规则 | 信号（正则/模板） | kind | 示例（LRE060 GT 对齐） |
|---|---|---|---|
| R1 数字+单位 | `[0-9]+|[零一二三四五六七八九十百]+` 且 2 字内出现 箱/点/元/块/米/号/层/分钟/人/件/栋/单元 | number/amount | "18个箱子我全编号了" |
| R2 日期/时点 | 今天/明天/后天/周[一二三四五六日天]/X月X日/X点(半)?/上午/下午/晚上/凌晨 | date/time | "明天上午九点货车到楼下" |
| R3 承诺/约定/分工 | 说好/答应/约定/负责/记得/一定/保证/赔/违约金/合同/钥匙/押金 | promise | "逾期赔房东违约金" |
| R4 变更/作废 | 改到/改成/延期/延至/作废/取消/不…了/换到 | event（并触发 supersede pass） | "货车改到十一点半" |
| R5 兜底 | 含数字但未命中上述模板 | other（低权重） | — |

subject 判定：HER 消息默认 HER，遇第三方词表（复用 lb_assembly.py `EXTERNAL_PARTY`，L141）
→ THIRD；ME 消息默认 ME，双方共同事务（R3 命中且含"我们/一起/咱"）→ SHARED；其余 UNKNOWN。
valid_time：R2/R4 命中时用消息 ts + 相对词解析出目标日期（明天=ts+1d 等），解析不出留 ""。

**上线前置（离线、零调用）**：对 LRE060/LRE048 的 episode.json 消息离线跑抽取器，
对照 benchmark/stories_v2/wave1_ext/LRE060/reference.json 的 `required_memory_atoms[].source.quote`
算逐字覆盖率；覆盖率 <80% 则先离线补模板再进实验（候选运行时看不到 reference，
此审计仅用于设计期校准，不进运行时路径）。

### 2.5 注入策略（每轮 top-k）

- 注入位置：v0 parts 序列中【画像种子块之后、历史消息段之前】（高优先、前置于窗口，
  防 F5.2 Lost-in-the-Middle）；块标题 `# 【事实账本（逐字原文，非本库内容勿虚构）】`。
- 候选池：status=asserted 且 capture_turn < 当前轮（本轮消息已在当前窗口，注入即重复）。
- 排序函数：`score = w_type + 0.8*rel + 0.6*exp(-Δturns/τ)`，
  其中 rel = r7 `_bigram_ov(text, 当前输入文本)`（当前输入=本轮 HER/ME+note+intent 拼接）
  加数字加成（f.digits 与当前输入数字集相交 → rel+0.3，E 段探针主攻数字/日期）；
  w_type：promise 1.0、date/time/number/amount 0.9、event 0.6、other 0.4；τ=16 轮。
- Pin 规则：valid_time 非空且 ≥ 当前日期的 promise/date 条目强制入注（上限 6 条），
  其余按 score 填满 k=12 行；同义去重（`_norm_cn` 后相同 → 单行，防 F3.4 重复证据）。
- 预算：账本段 ≤800 UTF-16 码元（~12 行 × ~45 码元 + 标题），行格式
  `- [F012|承诺|T05|11-06] 逾期赔房东违约金（HER）`；超预算按 score 从低往高丢弃，
  pinned 最后丢。账本段在 v0 各段裁剪之外（v0 整段历史预算独立，账本不占 1400 CU）。
- 账本容量帽：条目 >120 时按 kind=other 最旧优先淘汰；promise/date 类 asserted 永不淘汰。

### 2.6 更新/作废处理

capture 后运行 `_supersede_pass`（移植 r7 `_supersede_pass` + `_digit_conflict` +
`_SUPERSEDE_CUES`，纯函数复制并注明出处；r7 因依赖 r2/r1 全链不整体 import）：
新 asserted 条目命中更正线索、与旧条目 bigram overlap ≥0.42（同主体 ≥0.30）且数字冲突
→ 旧条目 status=superseded、superseded_by=新 fact_id、写 updates 日志（F3.2/F3.3 回流阻断：
superseded 条目不再注入）。R4 取消线索 + overlap → cancelled。seed 条目（origin=seed）永不降级。

### 2.7 与 lb_assembly / runner 的对接点

- 候选不经 `arena/lb_assembly.py` 的 `build_reply_prompts`（那是基线装配；机制层按 §3
  通过候选 mechanism 接管装配，基线对拍豁免权仅属 round_0_baseline）。
- mechanism 对外接口与 v0 相同四件：`init(assets_root, episode_meta, gateway, sandbox)` /
  `run_turn(h, ctx, gw)` / `snapshot(h)` / `restore(sandbox, snap)`；账本存 `h["ledger"]`、
  `h["updates"]`、`h["fact_seq"]`，全部进 snapshot（v0 L206–212 同法）。
- 每轮 notes 追加 `ledger_injected=k ledger_cu=N superseded_this_turn=m`，进 mechanism_notes 可复核。
- calls.json / 预算记账走现有 runner 与 gateway，不改 runner。

## 3. 对照臂 B：扩窗+摘要混合（最小规格）

- 目录 `candidates/window_expand_b/`：复制 v0 mechanism，两处改动。
  ① `MAX_EVENT_HISTORY_CU` 1400 → 3600（≈覆盖 32–40 轮全部源消息，LR1 实测范围
  220–1600 字符/故事的容量外推，具体覆盖轮数以离线 episode.json 计量为准，UNKNOWN 待量）。
  ② 确定性摘取式 recap：超出预算被裁掉的消息，每轮保留首个含 R1–R4 信号的 clause 成一行
  （零模型调用、逐字原文），拼在历史段尾 `# 【更早轮次摘录】`，recap 段 ≤400 CU。
- 成本估算：历史 3600 + recap ~400 CU → 每轮 prompt ≈ v0(3.3k tokens)+~2.0k ≈ 5.3k tokens，
  仍低于 r9_slim 实测 ~6.7k tokens/轮（483,393/72）。
- B 的角色：检验"容量本身够不够"——若 B 与 A 同分，账本排序/相关度无独立贡献。

## 4. 实验计划（两段，判据跑前冻结）

### 4.1 第一段：≤24 轮回归批

- 故事：E061（benchmark/stories_v2/stage4/E061，24 轮；LR1 已消费=EXPOSED，
  按 LR2 判决同一纪律：回归/跨代对比可复用，报告须标注 EXPOSED，不得称"未见"）。
- 臂：fact_ledger_v1 vs r9_slim（冠军 parent）vs v0（基座锚点），各 24 attempts，共 72。
- 判据（KEEP 门槛）：九维最差维 ≥−0.3（主假设）、severe(cand) ≤ severe(parent)、
  E061 T15-24 段窗口保持率 ≥ v0 同段实测、prompt usage ≤ r9_slim。
  任一不达 → 不进第二段，按失败归因登记；贴近阈值 → 报"不确定"，不补跑。

### 4.2 第二段：>24 轮判决批

- 故事：主臂复用 LRE060（40 轮）/LRE048（32 轮）（EXPOSED，跨代对比口径同上）；
  若登记时 wave-2 SEALED 池可启用新长剧（续写 32/40 轮，候选条目数以
  benchmark/stories_v2/wave2 实盘核对为准——本文不详写，启用属新 AUTH 事项）。
- 臂：fact_ledger_v1 vs r9_slim vs 臂 B，3 臂 × 72 轮 = 216 attempts，一次跑完，零重试零补跑。
- 判据（预注册风格，对齐 LONGPROBE_LR2_PREREGISTRATION.json thresholds 结构）：
  - KEEP：T25-32 段真实保持率（机械匹配 + miss 亲读扣等义/作废，artifact=0）≥0.85，
    且保护维 goal/boundary/init 最差 ≥−0.5，severe 不高于 parent；
  - 淘汰：真实保持 <0.80，或任一保护维 ≤−0.5，或 prompt usage > r9_slim+15%；
  - 不确定带：0.80–0.85 → 报"保留不确定"，账本候选与 r9_slim 均不判胜负，不补跑到赢。
- 测量：tools/measure_lr2_window_keep.py（确定性 GT 匹配）、tools/run_lp_review_generic.py
  （九维 + B3 队列）；72 位嵌套于 2 故事按故事分层报告，不套独立样本口径。

## 5. 成本与复杂度

- **注入成本**：12 行 × ~45 CU ≈ 540 + 标题 ≈ 600 CU/轮 ≈ +400~600 tokens/轮；
  v0 基座 ~3.3k tokens/轮 → ~3.8k，仍比 r9_slim 低 ~40%，远低于 r9_slim+15% 门。
- **实现工作量**：mechanism.py ~260 行 + ledger_rules.py ~110 行 + params/MECHANISM/IDENTITY
  ~80 行；臂 B delta ~60 行。复用 measure_lr2_window_keep.py / run_lp_review_generic.py 不改。
  合计 ~510 行新代码 + 离线抽取覆盖审计脚本 ~60 行。
- **风险**：①错账（clause 切分/subject 判错）→ 文本一律逐字原文，错账退化为噪声行而非虚构
  （F1/F3 不引入）；②重复注入（账本行与历史段同一事实双现）→ capture_turn < 当前轮 + 去重规则；
  ③与 contract_guard 冲突 → 基座 v0 无 GuardGateway，无冲突；若日后移植回 r9_slim 链，
  guard 只作用于输出侧，账本在输入侧，无交接面（移植属另行变异）；④注入行挤占语义注意力
  → 主假设九维门与 H_FL_null 显式覆盖，不达标即淘汰。

## 6. 边界与诚实登记（unknowns）

- 规则抓不住的事实类型（不入账，真实保持不改善）：无数字/日期/承诺线索的纯偏好事实
  （"她不吃香菜"）、条件承诺（"如果你有空就…"的触发语义）、隐含约定、代词主体的精确指代、
  negation 作用域（GT 有 `negation` 字段，规则只做 clause 级，非语义级）。
  这类 GT 原子在离线覆盖审计中单列报"规则未覆盖"，不冒充可召回。
- 抽取质量上限 UNKNOWN：模板对 wave-2 新长剧题材（非搬家域）的迁移率未测，
  依赖 4.1 的离线覆盖审计先行。
- 账本只提供逐字原文，模型是否正确使用原文属语义面——保持率与九维分开判，不互相抵扣。
- 本设计不改变 ≤24 轮域 r9_slim 冠军地位与 r7 唤回批（LR2R7）的判别逻辑；
  账本结果只竞争 FK-LR2-01 修复归属。
