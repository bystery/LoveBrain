# 交接文档独立评审 · 2026-10-23

评审对象：`EXPERIMENT_PROTOCOL.md` 与 `TRAINING_SESSION_START_PROMPT.md`（`DELIVERY_MANIFEST.json` 列出的两份）。
评审方式：只读，交叉核对一手证据（`refs/fair_baseline_20261023/*`、`refs/exam1_independent_review_20261023/REVIEW.md`、`refs/arena_v1/BASELINE_PARITY.md`、`logs/exam1_20261023/*`、`benchmark/dev_pilot_v02/*`、`benchmark/rubric.md`、`candidates/v2_recall/mechanism/mechanism.py`、`tools/test_v2_recall.py`、`logs/v2_recall_result.json`、`logs/auth_budget/AUTH-EXAM1-20261023-01.jsonl`）。零 API 调用、零代码改动、未修改被评审文件。

## 0. 一句话结论

协议**大方向诚实、不夸大**，可交接；但存在 11 处会在执行时被稀释或与其它一手证据发生口径冲突的点，训练会话按现 prompt 直接跑可能"照协议办事却得出协议禁止的结论"。建议合并离线收口阶段一并修协议/prompt 与 `rubric.md` 后再启动。

## 1. 协议按节核对（论断 → 一手证据 → 结论）

| 协议位置 | 论断 | 一手证据 | 独立判决 |
|---|---|---|---|
| §2 表 · 开发材料 | 3×8=24 位与 24 卡已可用 | `benchmark/dev_pilot_v02/DEV_EXPOSED_MANIFEST.json` 9/9 hash 一致；`00_DELIVERY.md` 与 `04_COVERAGE_AND_AUDIT.md` 明写"未运行项目验证器"，"考生可见投影、持久记忆与重启"待本地适配核验 | **支持**，但需加"卡本身未实跑校验、仅供人工监督语义审查"（详见 §A） |
| §2 表 · 先导实验 | 24 次真实调用已完成 | `EXAM_INDEX.json` 三条 run 全 DONE，`COST_LATENCY.json` 24/24 attempts、173,208/6,688 tokens，`api_usage` 口径 | **支持** |
| §4.4 "程序 gold 为空" | 现有程序分不能推断卡内语义满足 | `E001.json` 全部 8 turn `gold={}`；`LAYERED_SCORE.json` E001 8 位程序均分 10.0 但 T06 语义 fail、T04 语义 edge；`03_GRADING_CARDS.json` 24 位 `required_reply_phrase` 全 null | **支持且被严重低估**（详见 §3-1） |
| §5.4 24 位不独立 | 属 3 故事嵌套，不能当 24 独立样本 | `04_COVERAGE_AND_AUDIT.md` "24 个 case_id 唯一、每故事 8 位…评审族交叉引用不新增请求位…选择/B 均 0 位" | **支持**，`04` 已明确 |
| §8 "程序 9.31 不是语义总分" | 语义由另一路核验 | `LAYERED_SCORE.json` 程序 9.31、语义 20/2/1/1；两处口径完全脱钩 | **支持但表述太轻**（详见 §3-1） |
| §8 "20 过…主要是 worker 初审" | 非独立盲评 | `EXAM_REPORT.md` §3 表头："事实语义 / 互动质量＝逐位对照原始题面与原始回复的**人工初审**"；`REVIEW_SHEET.md` 每 episode 段落为工程搬运、非逐位盲评；`review_status` 在 24 卡全部 = `author_revised_v0.2_not_user_rated_not_model_run` | **支持** |
| §8 v2 有输入侧证据 | 无新真实 A/B 答卷 | `tools/test_v2_recall.py` c1-c5 全部 PASS；`logs/v2_recall_result.json` 5/5，无 API 调用；`MECHANISM.md` §5 明确"不能由这些断言推出的结论：v2 的回复分数会更高" | **支持，但遗漏 v2 已知负面离线证据**（详见 §3-2、§B） |
| §9 授权 24 次已用完并关闭 | 不能复活旧额度 | `logs/auth_budget/AUTH-EXAM1-20261023-01.jsonl` 尾行 `kind=authorization_closed reason=trusted batch close` | **支持**；但 `EXAM_INDEX.json` 里 `budget.closed=false` 与账本尾态**不同步**（详见 §3-11） |

其它小节（§1、§3.2、§4.1、§4.2、§4.3、§5.1、§5.2、§5.3、§6、§7 主干）与一手证据一致，未见夸大或矛盾。

## 2. 启动 prompt 可执行性核对

| Prompt 位置 | 步骤 | 独立判决 |
|---|---|---|
| L18 | 步骤 1 "核销公平基线关键差异（预算、纠正过滤、主体、动态知识等）…冻结实际依赖" | **可执行**。`refs/fair_baseline_20261023/FAIR_BASELINE_AND_REFACTOR_GUIDE.md` §2 已把 4 项差异逐条列明（预算裁剪、知识过滤、主体/恢复、后台触发），可当 checklist |
| L19 | 步骤 2 "同批收口 v2…用至少三个不同生活领域的正反场景验证通用规律" | **有反向依赖 + 分工缺口**（详见 §3-4、§3-6、§3-8） |
| L20 | 步骤 3 "对齐评分…量尺变更单独版本化并离线重评所有既有答卷" | **可执行，但 rubric.md 与本 prompt 的口径冲突未先说明**（详见 §3-3） |
| L21 | 步骤 4 "固定主假设、主指标、退化保护、可接受差异、运行顺序和停止条件，提交原版/候选同场交错的逐项调用清单" | **可执行**。协议 §5.3 已提供模板要素 |
| L22 | 步骤 5 题库扩充与独立出题交接 | **可执行**。协议 §7 步骤 5-6 一致 |
| L26 | 授权条款 | **严密、无冒烟口子**（详见 §3-7） |
| L28 | "每次汇报明确区分输入改善 / 真实输出改善 / 独立迁移证据" | 好，与协议 §1 逐级证据链一致 |

## 3. 攻击点清单（按对研究结论的威胁度排序）

### 3-1（高）§4.4 严重低估"程序 gold 为空"的量纲影响

- **一手证据**：`E001.json` 全部 8 turn `gold={}`；`LAYERED_SCORE.json` 显示 E001 程序均分 10.0，其中 T06 语义失败、T04 语义边界；`03_GRADING_CARDS.json` 全部 24 位 `required_reply_phrase=null`；`benchmark/rubric.md` §2 里 12 个检查点中 `must_kw_*`、`must_not_kw_*`、`forbidden_fact_*`、`ongoing_*`、`cancelled_*`、`intent_kw_*`、`stale_kw_*`、`geo_must_not_*`、`topic_status/topic_label` 都直接依赖 gold 字段。gold 空 → 这些点无 assertion 可失败 → 自动"过"。程序分实际只由 `json_schema_valid(2)`+`source_ids_exist(2)`+`note_lcs(2)`+`ai_flavor(1)`+`style_diversity(1)` 五个契约/相似度检查决定。
- **风险**：训练会话看到"程序 9.31 → 修改后程序 9.8" 就以为是"改进"，实际两者都不含语义；也可能出现"改 rubric 阈值 → 程序分变化 → 误报为候选收益"。
- **建议**：协议 §4.4 明确加一句"当前程序分只覆盖五个契约/相似度检查（json_schema / source_ids / note_lcs / ai_flavor / style_diversity）；其它检查点因 gold={} 全部无 assertion。程序分变化本身不能推断语义收益。"

### 3-2（高）§8 "v2 只有输入侧证据"漏掉 4 项负面离线证据

- **一手证据**：`refs/exam1_independent_review_20261023/REVIEW.md` §2 已列 4 项负面：
  - `is_recall_turn` 扫描当轮全部 HER/ME，HER 在同一 send 里"之前那件事已经结束了 + 今天午饭吃什么"仍 True。我在本窗口独立跑 `candidates/v2_recall/mechanism/mechanism.py:is_recall_turn` 复现，返回 True。**证据成立**。
  - `RecallSelector.select_for_injection` (`mechanism.py:164-166`) 只排除 `FINISHED/CANCELLED`，`DORMANT` 与未标 ACTIVE 项**全量注入**；MECHANISM.md §2 表却写"ACTIVE 全量注入"——**代码与文档不一致**。
  - `MAX_RECALL_CHARS=1200` 只裁归档段（`mechanism.py:144-147`），进行事项旁路不受限。
  - `restore()` (`mechanism.py:210-215`) 只返回 `base.restore()`，未重装 `RecallSelector`。
- **风险**：训练会话把 v2 直接接进 §5.2 两臂对照 → 假阳激活、DORMANT 灌入、restore 差异把候选推向协议 §6 "均分微升但污染与成本明显增加" 的失败判决，但直到付费才发现。
- **建议**：协议 §8 改写"v2 有 5 项正向输入证据 + 4 项已复现的负面行为；负面对照未收口前不允许进入实跑对照"。prompt 步骤 2 加一句"步骤 2 完成前，本窗口不允许 v2 进入 §5.2 两臂清单"（此句由本窗口把关，不是训练会话自评）。

### 3-3（高）`benchmark/rubric.md` 与本协议的口径直接冲突且未版本化

- **一手证据**：
  - `rubric.md` §1 "程序规则…晋级门禁唯一依据；可触发 veto"；`rubric.md` §5 "v0.3 起：成本不再作晋级门禁或排序指标"；§3 judge 权重 0.5/0.3/0.2 固定；§2 阈值 0.3 与 0.6 硬编码。
  - 本协议 §6 把成本、时延、污染、可维护性并列作晋级门禁；§4.4 "现行 rubric.md 仍有程序门禁唯一依据、固定 note_lcs 阈值和成本不作晋级标准等旧口径…将规则修订版本化"。
  - `refs/fair_baseline_20261023/FAIR_BASELINE_AND_REFACTOR_GUIDE.md` §5 已单方面声明"本轮采用多目标验收"覆盖 rubric §5，但 rubric.md 文件本身**未同步、未版本化**。
- **风险**：训练会话按 prompt 步骤 1-2 修代码后，如果调用 `lb_scorer.py`（rubric §1 声称唯一计分器）就默认沿用 rubric 门禁 → 得到与协议 §6 相反判决；或反过来直接改 rubric 阈值算作"候选收益"（协议 §4.4 明令禁止）。
- **建议**：把 rubric.md v0.5 修订列入 prompt 步骤 3 **最先**做（"先出 rubric v0.5 草案：成本/时延/污染/可维护性进入并列门禁；程序分不再是唯一门禁；judge 校准前只辅助；note_lcs/style_diversity 阈值改动附正反例；改完对既有 24 份答卷离线重评，两臂同尺"），草案交本窗口先审再冻结。

### 3-4（中）prompt 步骤 2 存在"运动员+裁判"

- **一手证据**：协议 §4.3 "worker 可做初审、归因和材料制作，但候选晋级不应仅由写候选的 worker 自己决定；对照材料隐藏候选身份…未通过的 judge 只辅助筛查"。prompt 步骤 2 "同批收口 v2…用至少三个不同生活领域的正反场景验证通用规律" — 未说明正反场景由谁出题、由谁审、以何判据冻结。若 v2 worker 自己写正反场景、自己判 PASS，就违反协议 §4.3。
- **建议**：prompt 步骤 2 明写"正反场景（假阳、DORMANT 灌入、总预算、restore 一致性）由本窗口（独立评审）先出草案并冻结判据；训练会话不得自定义通过判据；训练会话补的负向例先交本窗口 review"。

### 3-5（中）§4.2 "首选 / 备选分开评"当前无实现

- **一手证据**：`LAYERED_SCORE.json` 每 turn 只有单一 `semantic` 标签；`EXAM_REPORT.md` §3 未列"首选合格率 / 至少一个合格候选比例 / 含严重问题备选比例"三个统计。协议 §4.2 与 §6 明写这三个指标必备。
- **风险**：训练会话按 §5.2 跑出两臂 48 位后仍拿不到 §4.2 要求的分层数字，"至少一个合格候选比例"可能替代"首选合格率" → 违反协议"一个好备选不能掩盖坏首选"。
- **建议**：协议 §7 加"步骤 3 前置：`exam_layered_score.py` 输出必须新增 `primary_semantic` / `any_backup_semantic` / `backup_with_severe_error` 三字段；旧答卷重评时同时产出"；rubric v0.5 里也加对应字段定义。

### 3-6（中）prompt 步骤 1-2 存在隐含反向依赖

- **一手证据**：步骤 2 "snapshot→restore 行为一致"是 v2 收口项；但 v2 的 `restore()` 只包 `base.restore()`，`base` 是 `reference_product_v1`。步骤 1 若还没补齐基线主体解析、WAL 恢复、后台重估范围（`BASELINE_PARITY.md` §5-6、`fair_baseline` guide §2.4），则 v2 restore 一致性测试的"父本行为"本身还没冻结 → 无法判定 v2 差异是"继承基线缺陷"还是"v2 独有回归"。
- **建议**：步骤 2 拆两段：2a 独立于基线的三项保护（假阳、DORMANT 灌入、总预算）可并行；2b restore 一致性必须等步骤 1 冻结后启动。

### 3-7（低）授权条款本身严密，但快照与账本不同步会误读

- **一手证据**：`EXAM_INDEX.json` 中 `runs[2].budget.closed=false`（快照未刷新）；`logs/auth_budget/AUTH-EXAM1-20261023-01.jsonl` 尾行 `authorization_closed`。协议 §9 + prompt L26 表述准确，条款无漏洞。
- **风险**：训练会话若信任 `EXAM_INDEX.json` 快照，可能误判"还剩 0 未 closed → 可以重跑"。
- **建议**：prompt "先读"清单加"账本尾行 `kind=authorization_closed` 是硬判据；`EXAM_INDEX.json` 快照不作为授权凭据"。

### 3-8（中）"三领域正反场景"当前实际是"三领域正向"

- **一手证据**：`04_COVERAGE_AND_AUDIT.md` §12 族覆盖表，正向观察位充足；但"错人子类待补"、"MEDIA_PENDING"、"重启恢复未设计"、"合同与后续真实输出合同未验收"是明写缺项。dev_pilot 里"无关记忆反向"（E001_T02—T05/T07/T08）只算弱覆盖，不构成 v2 收口需要的 4 类保护（假阳、DORMANT 灌入、总预算、restore）正反例。
- **建议**：prompt 步骤 2 明确"正向可用 dev_pilot；四类负向保护必须新写离线反例并交本窗口先审；不能用同一 story_family 派生"。

### 3-9（低）§5.1 引用公平基线 guide 却漏 `BASELINE_PARITY.md`

- **一手证据**：`refs/arena_v1/BASELINE_PARITY.md` §4-5 明写"may_decline 归候选契约消费；基线对比不计该维度"；§5-6 登记跨轮补齐、id 撞车、路径映射修复。协议 §5.1 只引 guide 不引 `BASELINE_PARITY`。prompt L12 "先读"清单列了 `BASELINE_PARITY.md` 但说"用于对照而不是无条件采信"。
- **风险**：候选输出 no_reply 时若把基线未支持 no_reply 判为候选收益，`BASELINE_PARITY.md` §4 明确禁止，但协议正文没重复这条红线。
- **建议**：协议 §5.1 加一句"may_decline、no_reply、意图契约等适配差异以 `refs/arena_v1/BASELINE_PARITY.md` §4-5 为准；候选消费基线未消费的字段时须单列，不得归为基线劣势"。

### 3-10（低）§3.1 "已有独立复核签收" 未点明是内容签收非校准签收

- **一手证据**：24 卡全部 `review_status = author_revised_v0.2_not_user_rated_not_model_run`；`04_COVERAGE_AND_AUDIT.md` 尾段"本轮仅针对 v0.2 新文件执行命题方内容检查…检查范围：3 个 episode JSON 回读、字段、24 位计数与唯一 ID…E001 原始窗口无杯底答案泄漏、星期计算。**没有运行项目验证器**"。
- **建议**：协议 §3.1 措辞改为"内容层已由独立命题方复核并 9/9 散列一致；卡本身未经用户评分或模型盲评校准，属可用开发材料而非已校准 judge 训练材料"。

### 3-11（低）§4.3 judge 校准缺"锚点材料"路径

- **一手证据**：`rubric.md` §3 说 judge 校准需 20 锚点，落 `logs/judge_calibration.json`；但 `benchmark/anchors/` 目前状态未验证、协议正文未指路径与责任方。
- **建议**：协议 §4.3 补一句"judge 校准锚点由本窗口准备并保存至 `benchmark/anchors/`；训练会话不得自行生成锚点自评校准"。

## 4. §A dev_pilot_v02 题目卡独立审计

**结论：卡设计质量高，与协议 §4.2/§4.3 对齐良好，无发现致命缺陷。**

| 检查项 | 一手证据 | 判决 |
|---|---|---|
| 死编码单一答案 | 24/24 `required_reply_phrase = null`（`03_GRADING_CARDS.json`） | 通过 |
| 关键词 gold 强判 | `E001/E002/E003.json` 全部 `gold = {}` | 通过 |
| 未来剧情追加当前义务 | 遍历 24 卡的 `evidence` 字段，无 `case_id` 大于当前卡的证据条目；`next_behavior_check` 只作后续观察不作本位失败条件（如 E001_T06 "本位不证明重启恢复"） | 通过 |
| 允许多表达 | 每张 `allowed_behaviors` 至少两条并列；E001_T06 明写"可换说法"；E001_T04 "自然短句可以，机械复读长串哈哈是否好笑需人工看" | 通过 |
| 合法 null | E001_T05 `may_decline: true` 与卡 "允许不回。其他候选可为合法 null，不必凑满八句" 一致 | 通过 |
| 首选/备选分审 | 每卡 `review_guidance` 明写"首选与所有非空备选分别审查，合法 null 无需凑句" | 通过（协议 §4.2 落到卡上，但未落到 `LAYERED_SCORE.json`；见 §3-5） |
| 卡进入考生输入 | `04_COVERAGE_AND_AUDIT.md` "本批全文公开交付…考生只得到允许的开局背景与当前请求" + `DEV_EXPOSED_MANIFEST.json` `review_flow: 卡不进考生输入` | 通过（设计层，未实跑校验） |
| 覆盖 | 12 族交叉，但错人子类、MEDIA、长链、重启恢复明写待补 | 诚实标注，无虚报 |
| 唯一性 | 24 `case_id` 唯一；E002_T08 m0 重显 T07 原消息，`04` 已注明"同一真实消息，不另计聊天条数" | 通过 |

**唯一需要提示训练会话的点**：卡里的 `families` 是 10 项，但协议里 §3.2 提到"跨主体、纠正、长期/跨话题、否定、重启恢复及不同关系/意图的保护样本"—— 后三类在 dev_pilot 里覆盖较薄；prompt 步骤 5 扩题时应先补这三类**独立故事**而非 E001 派生。

## 5. §B v2 输入侧证据链独立核验

**结论：c1-c5 断言"输入侧事实递到了 prompt"成立；但 v2 当前同时有 4 项负面离线证据未收口，协议 §8 只引用 REVIEW.md 未强调。以下 4 项均在本窗口用一手代码路径复现。**

| # | 负面行为 | 复现证据 | 与协议/prompt 的关系 |
|---|---|---|---|
| 1 | 假阳激活 | `candidates/v2_recall/mechanism/mechanism.py:70-78 is_recall_turn` 只 join 当轮 `messages` 中 HER/ME 的 content 做子串匹配；本窗口直接调 `m.is_recall_turn([{'role':'HER','content':'之前那件事已经结束了'},{'role':'HER','content':'今天午饭吃什么'}])` → True | prompt 步骤 2 "旧窗口词不能无故激活当前回忆" |
| 2 | DORMANT 全灌 | `mechanism.py:164-166 extra = [it for it in allItems if itemStatus not in (FINISHED,CANCELLED) and name not in have]` — 只排除 FINISHED/CANCELLED；MECHANISM.md §2 表写"ACTIVE 全量注入"，代码与文档不一致 | prompt 步骤 2 "无关/过期/休眠事项不能全量灌入" |
| 3 | 总预算未管 | `mechanism.py:144-147 MAX_RECALL_CHARS` 只裁归档 body 段；进行事项旁路不裁；`_knowledge_with_recall` 也不设新知识段总长上限 | prompt 步骤 2 "新增上下文有总预算" |
| 4 | restore 掉 selector | `mechanism.py:210-215 restore(...) return base.restore(...)` — 未重装 `RecallSelector`；恢复后 `h["ongoing"]` 变回父本 selector，v2 旁路消失 | prompt 步骤 2 "snapshot→restore 行为一致" |

**另外发现**：`tools/replay_exam_episode.py` 产出的 `_replay_E001/prompt_tXX_*.txt` 仅 589 字符（`replay_report.json`），而原始 `calls.json` 里 `user` 字段 8885 字符。也就是说 `_replay_*` 只重放"当前机制的 user 段"，**不是**"原始请求的完整重建"；`prompt_sha_equal` 字段全为 null（`replay_report.json` 8 turn 全部），即 `prompt_sha_equal` 校验未实现。exam1_independent_review §1 已提这一点，v2 的 c1-c5 用同样的 `ReplayGateway.prompts` 数组，同样只覆盖当轮 user 段（`tools/test_v2_recall.py:34-37`）。**协议 §8 用"输入侧证据"是对的**，但训练会话容易把"输入侧证据"读成"完整请求重放" → 建议加"输入侧证据特指当前机制组装的 user 段；`prompt_sha_equal` 与原始 calls 未做逐字节对齐，不能宣称与当时状态一致"。

## 6. §C "20 过"分母独立核对

**结论：数字口径正确，但被引用方式容易被误读为"24 位都过了语义"，实际语义层是 20 pass + 2 edge + 1 fail + 1 unrated（veto 0 分位）。协议 §8 表述准确。**

- `LAYERED_SCORE.json` 逐 episode 语义分布：E001 6/1/1/0，E002 8/0/0/0，E003 6/1/0/1，总 20/2/1/1。求和 = 24 ✓。
- 那 1 位 fail = E001_T06；2 位 edge = E001_T04、E003_T04；1 位 unrated = E003_T02（格式 veto）。
- `REVIEW_SHEET.md` 只有 4 个 `## ` 段头（每 episode + 程序分分布），非逐位盲评；`EXAM_REPORT.md` §3 表头自标"人工初审"。协议 §8 "主要是 worker 初审，不是已经完成盲评校准" 描述准确。
- `_iso_run_E001/staged/` 与 `_replay_E001/`：前者是隔离运行时暂存（`_candidate_host.py` / `host_runtime.py`），后者是**当前机制 + 原始输出**的重放（不是当时真实 prompt 完整重建）。协议 §8 未直接引用两者，但训练会话若用 `_replay_*` 做父本对照，须先按 §B 尾段说明限制其口径。

## 7. 建议改写点（原文行号引用）

**EXPERIMENT_PROTOCOL.md**

- L34（§2 末段）：加"不提供虚假的百分比进度"后一句"也不把 24 位实跑次数当作 24 个独立样本；详见 §5.4"。
- L44-45（§3.1 末段）：把"程序分主要测契约，不能靠程序 10 分推断卡里的语义要求都满足"改为"程序分实际只由 json_schema / source_ids / note_lcs / ai_flavor / style_diversity 五个契约/相似度检查决定；其它依赖 gold 的检查点因 gold={} 全部无 assertion 可失败。程序分变化本身不能推断语义收益。"
- L94-96（§4.4）：把"现行 rubric.md 仍有…"改为明确的收口 checklist："rubric v0.5 草案必须做四件事：①成本、时延、污染、可维护性进入并列门禁；②程序分不再是唯一门禁；③judge 校准前只辅助；④note_lcs/style_diversity 阈值改动附正反例并交独立评审先审。草案未冻结前，训练会话不得据程序分变化宣布候选收益。"
- L104（§5.1 中段）：追加一行"适配差异（may_decline、no_reply、意图契约）以 `refs/arena_v1/BASELINE_PARITY.md` §4-5 为准；候选消费基线未消费的字段时须单列，不得归为基线劣势。"
- L118（§5.3 主假设登记）：追加"每批登记必须包含'本批 24 位仍非独立样本，扩题到独立故事之前不宣称泛化'一句"。
- L120（§5.3 末段）：追加"候选晋级须同时报'首选合格率 / 至少一个合格候选比例 / 含严重问题备选比例'，缺任一不得判决"（对 §4.2 落地）。
- L164-168（§8）：把 v2 一句改成"v2_recall 有 5 项正向输入侧证据（c1-c5, `logs/v2_recall_result.json`）+ 4 项已独立复现的负面行为（`refs/exam1_independent_review_20261023/REVIEW.md` §2 本窗口独立复现），四组保护反例未收口前不允许进入 §5.2 两臂对照。"
- L172-174（§9 授权）：追加"账本尾行 `kind=authorization_closed` 是硬判据；`EXAM_INDEX.json` 快照不作为授权凭据"。

**TRAINING_SESSION_START_PROMPT.md**

- L13 "先读"清单：加一条"账本尾行 `logs/auth_budget/AUTH-*.jsonl` 中的 `authorization_closed` 是硬判据"。
- L18 步骤 1 结尾：加"步骤 1 完成前，步骤 2b（v2 的 restore 一致性）不得启动"。
- L19 步骤 2：拆成 2a/2b：
  - 2a（独立于基线，可并行）：`is_recall_turn` 假阳、`RecallSelector` DORMANT 灌入、总注入预算；正反场景由本窗口先出草案、训练会话补细节；训练会话不得自定义 PASS 判据。
  - 2b（依赖步骤 1）：snapshot→restore 行为一致。
- L20 步骤 3：改"对齐评分"→"先出 rubric v0.5 草案（本协议 §4.4 checklist）交独立评审先审 → 冻结 → 离线重评所有已有答卷"。
- L21 步骤 4：追加"输出清单必须包含 §4.2 三个分层字段（首选 / 至少一个合格候选 / 含严重问题备选）"。
- L22 步骤 5：追加"扩题优先补 dev_pilot 明写的缺项：错人子类、跨主体、重启恢复、长程跨话题、独立故事"。

## 8. 本评审的边界

- 只读；未修 `EXPERIMENT_PROTOCOL.md`、`TRAINING_SESSION_START_PROMPT.md`、`rubric.md`、v2 代码或 `logs/exam1_20261023/*`；未发出任何真实模型调用。
- 独立跑了一次 `is_recall_turn` 复现（内存内），未持久化产物；未重跑 `tools/test_v2_recall.py` 或 `tools/test_baseline_carryover.py`。
- 一手证据以文件散列、代码行号、JSON 字段路径为准；未采信任何 worker 自述的 PASS/FAIL 标签作为独立结论。
- 本评审不构成候选晋级或退回判决；晋级判决须按协议 §5.3 预登记 + §5.4 独立故事 + §6 多目标并列门禁 + §7 HITL 抽样。
