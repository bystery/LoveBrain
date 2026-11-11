# COVERAGE_AND_VALIDITY — DEV_FAST_SET_v1 覆盖与效力审计

> 日期：2026-11-11 ｜ 作者：A 线 Benchmark Worker（RESEARCH-P1）
> 集合：`DEV_FAST_SET.json`（set_id=`DEV_FAST_SET_v1`，version 1.0.0，60 位 + smoke_24）
> sha256：`99e8fe68acf293eb61190573c58cbf6a5aaa47be7b3624a6669005dec3dea2fe`
> 构建器：`build_dev_fast_set.py`（同目录；selection_rule 固化于脚本与 JSON，先规则后选位，零模型调用）

---

## 0. 定位声明（必读）

**这是 DEV 诊断集，不是独立泛化成绩。**

- 用途仅限宪章 A2 第一级：新 harness 原型的**快速筛选**——工作正常性（parse/契约）、
  回复质量比较、明显倒退检测、Token/延迟统计。
- 两池素材均已开发曝光（wave4 于 2026-11-08 pilot2、wave5 于 2026-11-10 selection_v1 四臂）。
  对曾在 pilot2/selection 上调过的候选，本集**不是盲测**；本集结果不得写成"独立泛化改善"，
  不得用于替代 SELECTION/HOLDOUT 正式比较。
- 集内只有 episode+turn 指针与元数据，**不内嵌题面正文与 gold 文本**（最长内嵌字符串为
  149 字符的证据注记）；文件 UTF-8 无 BOM，88.7 KB，可 diff。

## 1. 数据源与曝光

| 池 | 目录 | 规模 | 曝光证据 |
|---|---|---|---|
| wave4_dev（主池） | `benchmark/stories_v2/wave4_dev/E301–E338` | 38 篇 488 reply 位 | `runs/pilot2_wave4_live_b/`+`runs/pilot2_wave4_live_c/`（DIRECT/CURRENT，2026-11-08）；逐位 flag：`refs/benchmark_v1_20261107/phase2/pilot2_merged/PILOT_RESULTS_MERGED.csv`（1,104 行，含 2 篇旧 T09 曝光位） |
| wave5_selection（备池） | `benchmark/stories_v2/wave5_selection/E339–E378` | 40 篇 492 reply 位（含 E374 24 轮长篇） | `runs/selection_v1{,_current,_direct,_fast,_slow}/`（四臂，2026-11-10）；逐位 flag：`refs/benchmark_v1_20261107/phase3/experiments/selection_v1/FOUR_ARM_RESULTS.csv`（492 行 × 4 臂）+ `selection_v1_universal_scores.csv`（1,954 行） |

- 专题定义权威文本：`refs/benchmark_v1_20261107/phase1/TOPIC_RUBRIC_DRAFT_V1.md`（T01–T10 七要素）。
- 分区与篇级专题：`refs/benchmark_v1_20261107/phase3/DATASET_V1_FROZEN_MANIFEST.json`（240 篇，
  E301–E378 均在册；split=DEV/SELECTION）。
- Gold 引用（评估侧合法）：`benchmark/stories_v2/GOLD_SIDECAR/GOLD_wave4_dev.json`（488 轮
  1,350 原子）/ `GOLD_wave5_selection.json`（492 轮 1,693 原子）；两池 empty_gold=0。
- 硬禁区遵守：未读 `benchmark/b_line/`、`D:/lovebrain-arena_holdout_vault/`、E401–E520 任何材料。

## 2. 选取规则摘要（全文化见 JSON `selection_rule`，脚本为准）

| 条 | 内容 |
|---|---|
| R1 | 宇宙 = 两池全部 980 个 reply 位；其余数据一律不读 |
| R2 | 逐位专题：primary_topic 冻结 manifest 权威；派生 —— 显式 intent→+T06；unknown/must-not-affirm→+T07；CANCEL_FAMILY 原子→+T09；跨轮断言来源→+T08。T01–T05 仅经 primary_topic |
| R3 | live 证据绑定：wave4 两臂 + wave5 四臂；硬失败 = parse/契约失败/error_type/severe_error/cancel_affirm/真实 veto；软失败 = required 全中未满足（marker 匹配严格，只作难度提示）、不允许不回位的 no_reply；clean = 全臂无硬软失败且 action=reply |
| R3' | **轻量臂结构性假 veto `unknown_payload_field` 不计失败**（recommended 键契约已知问题，universal_scorer v1 已按层隔离为 NOT_APPLICABLE） |
| R4 | 配额（主专题）：T01–T04 各 8（合计 53%，宪章"占比适当提高"）、T05/T06/T07 各 6、T08 5、T09 3、T10 2 = 60；池配额合计 wave4_dev=36 / wave5_selection=24 |
| R5 | 每专题先选 W 槽（硬失败臂最多者，marker=expected_weakness；仅软旗时如实记 expected_weakness_soft）、再选 S 槽（全臂干净，marker=expected_strength）、其余按池缺口轮转填充（marker=probe）；每篇 episode 全局 ≤2 位 |
| R6 | position_id 按 (kind_rank, topic, episode, turn) 排序分配 D60-001..D60-060；smoke_24 = ID 前 24 位 |
| R7 | 选位盲：只用 episode/gold 元数据与 live 判定 flag，不读不比较任何候选输出文本；无随机数，可复现 |

## 3. 覆盖矩阵（专题 × 池 × 强弱项；主专题口径）

| 专题 | 配额 | wave4_dev | wave5_selection | 弱项位 | 强项位 | 探针位 | position_ids |
|---|---|---|---|---|---|---|---|
| T01 | 8 | 6 | 2 | 1 | 1 | 6 | D60-001, 011, 021–026 |
| T02 | 8 | 5 | 3 | 1 | 1 | 6 | D60-002, 012, 027–032 |
| T03 | 8 | 5 | 3 | 1 | 1 | 6 | D60-003, 013, 033–038 |
| T04 | 8 | 5 | 3 | 1 | 1 | 6 | D60-004, 014, 039–044 |
| T05 | 6 | 3 | 3 | 1 | 1 | 4 | D60-005, 015, 045–048 |
| T06 | 6 | 4 | 2 | 1 | 1 | 4 | D60-006, 016, 049–052 |
| T07 | 6 | 4 | 2 | 1 | 1 | 4 | D60-007, 017, 053–056 |
| T08 | 5 | 2 | 3 | 1 | 1 | 3 | D60-008, 018, 057–059 |
| T09 | 3 | 1 | 2 | 1 | 1 | 1 | D60-009, 019, 060 |
| T10 | 2 | 1 | 1 | 1 | 1 | 0 | D60-010, 020 |
| **合计** | **60** | **36** | **24** | **10**（7 硬 + 3 软） | **10** | **40** | — |

**派生专题再覆盖**（secondary tag，不占配额）：T06 +13、T07 +24、T08 +14、T09 +12 →
含派生后总探及位：T06=19、T07=30、T08=19、T09=15。T01–T05/T10 无派生通道（依赖篇级专题）。
另有 8 个 may_decline 位（合法不回判准）与 16 个显式 intent 位、3 个长篇位
（E374_T22、E374_T10、E337_T23）分散在难度标签中。

### 3.1 弱项/强项位证据明细（20 个证据位）

| ID | case_id | 专题 | marker | 硬失败臂 | 硬类别 |
|---|---|---|---|---|---|
| D60-001 | E339_T01 | T01 | weakness_**soft** | — | required_miss ×3 臂（软） |
| D60-002 | E341_T04 | T02 | weakness | DIRECT+LEGACY | severe_error, cancel_affirm |
| D60-003 | E347_T03 | T03 | weakness_**soft** | — | required_miss ×4 臂（软） |
| D60-004 | E351_T05 | T04 | weakness_**soft** | — | required_miss ×2 臂（软） |
| D60-005 | E355_T03 | T05 | weakness | LEGACY | contract, severe_error |
| D60-006 | E364_T02 | T06 | weakness | DIRECT+COT+CURRENT | severe_error, cancel_affirm |
| D60-007 | E369_T12 | T07 | weakness | DIRECT+COT+CURRENT | severe_error, cancel_affirm |
| D60-008 | E373_T07 | T08 | weakness | 全 4 臂 | severe_error, cancel_affirm |
| D60-009 | E374_T22 | T09 | weakness | 3 臂（24 轮长篇） | severe_error, cancel_affirm |
| D60-010 | E376_T04 | T10 | weakness | DIRECT+COT | severe_error, cancel_affirm |
| D60-011…020 | E301_T01 / E342_T10 / E309_T01 / E313_T01 / E317_T01 / E360_T01 / E366_T01 / E329_T01 / E374_T10 / E333_T01 | 各专题 | strength | — | 全臂 clean（契约各自层通过、无 severe/cancel/parse 失败） |

依据路径随位内嵌于 JSON `expected_capabilities[].evidence`（指向上述两份 CSV 与 runs 目录）。
诚实注记：T01/T03/T04 的弱项为**软证据**（required-atom 未全中；该通道 marker 匹配严格、
wave5 全库基线命中仅 50/232），确定性层在这三个专题**无硬失败记录**——弱项位是"难度提示"
而非"已证实失败"，语义层难度仍需 judge/B3 证实。

## 4. Q1–Q5 五项通用质量指标在本集上的覆盖说明

Q1–Q5（自然度/逻辑连贯/上下文承接/情境分寸/可发送性）**没有程序化判点**：
`universal_scorer` 通用层只做确定性检查（action/契约/cancel_affirm/required/severe/时延/token）。
本集为 Q1–Q5 提供的是**可复用的评审位与锚点**，正式语义评分须走 judge（受 W20-03 资格表限制）
+ B3 亲读；映射如下：

| 指标 | 主要行使位 | 说明 |
|---|---|---|
| Q1 自然度 | T01 8 位（D60-001/011/021–026）+ T02/T03 篇内自然轮 | 客服腔/连环追问/ai_flavor 程序侧可筛；幽默质量无程序判点 |
| Q2 逻辑连贯 | T03 8 位 + T07 事实链位（含派生 30 位） | 观点交锋/换挡跟随需 B3 档位判读；事实链有 required/cancel 机械证据 |
| Q3 上下文承接 | 派生 T08 19 位（跨轮回扣）+ T09 15 位（长篇/更正链） | 记忆取用正确性 judge memory 维停用 → B3+机械；跨轮指针位有 gold 断言来源可核 |
| Q4 情境分寸 | T02 8 位（推拉档位）+ T05 6 位（安慰边界）+ 8 个 may_decline 位 | 行为档位明确禁关键词判定 → 锚点对照 + 愿发票；may_decline 位走 decline_appropriateness 分审 |
| Q5 可发送性 | 7 个硬弱项位（severe/cancel 红线）+ T06 意图契约位 + 全部位 parse/契约通用层检查 | 红线类（编造已发生/取消事项复活/契约崩坏）通用层可拦截；语义可发送性仍需亲读 |

## 5. 评分管线声明与已知评分缺口（已读 `tools/benchmark_v1/universal_scorer.py` 核实）

**声明管线**：`universal_scorer_v1` + `normalize_v1`（primary_text 固定规则抽取，不挑备选）+
`semantic_guards_v1`（疑问/否定/条件/引用/过去式五守卫，坐实分类）+ gold_diag 原子族
（CANCEL_FAMILY/ACTIVE_FAMILY）。产品层分臂：LEGACY/CURRENT(LEGACY_COMPAT_R9) 用 lb_scorer
score10/veto 原样透传；DIRECT/COT 只用 light 契约，harness 形状 score10/veto 记 NOT_APPLICABLE。

已核实的缺口与处理：

1. **轻量臂 `recommended` 键契约**：lb_scorer `PROTOCOL_TOP_KEYS` 不含轻量臂 recommended 键 →
   DIRECT/COT veto 全量误触发（pilot 实测 DIRECT veto=544/552、score10 0.14 vs CURRENT 9.96，
   为结构假象非质量差）。universal_scorer v1 已按层隔离；**本集证据绑定同步排除
   `unknown_payload_field` 假 veto**（R3'）。新原型若为轻量臂形状，比较必须走 light 契约层。
2. **judge 九维资格**（W20-03）：boundary/memory disqualified（漏检 0.864/0.609）→ T10 边界与
   T08 记忆的判死必须 B3 亲读 + 机械证据；attrib assistive-only → 人物归属仅线索；
   fact/init 低证据辅助。T02/T03/T04/T05 行为档位无确定性判据 → 锚点对照 + B3 + 愿发票。
3. **required 通道基线偏高**：marker 严格匹配，wave5 全库 required 命中 50/232 →
   required_miss 作软信号（难度提示），不作硬判，不单独判死。
4. **语义守卫控制集**：guards 改动必须升版本 + ≥100 正负控制离线重评（P3-3.2）；
   本集证据引用 guards v1 读数，不再二次解释。
5. **gold 覆盖度**：两池 empty_gold=0、原子引文逐字命中 100%（sidecar stats），
   程序分覆盖完整；但程序分只证契约与红线，**不冒充回复质量**（rubric v0.5 §1）。
6. Q1–Q5 与 T01–T05 档位无程序判点（见 §4）——本集不产语义分，只产确定性读数与对照位。

## 6. smoke_24（第 1 级冒烟子集）

- 规则：position_id 升序前 24 位（D60-001..D60-024）。因 ID 按 (弱项=0/强项=1/探针=2, 专题,
  篇, 轮) 排序分配，前 24 位 = **10 个专题弱项位 + 10 个专题强项位 + 4 个 T01 探针位**，
  覆盖 T01–T10 全部专题，含 ≥1 相关能力强项（10 个）与 ≥1 预期弱项（10 个），满足宪章要求。
- 4 个冒烟探针位自带难度标签：E301_T02（纯日常）、E302_T01（unknown 纪律）、E302_T03
  （may_decline）、E303_T12（intent 轮 + unknown）。

## 7. 数据问题登记（选位过程中发现）

1. **曝光总账位置与覆盖偏差**：`STORY_CUSTODY_LEDGER.json` 实际位于
   `refs/experiment_protocol_20261023/global_harness_program_20261030/`（非仓库根）；
   收录 248 篇，E301–E338 有 `consumed_by=pilot2_wave4_live_b_20261108` 记录，
   **E339–E378 未入账**（0 篇）。wave5 的消费证据只存在于 runs/selection_v1* 与
   phase3/experiments/selection_v1/。
2. **wave5 逐篇 exposure.json 滞后**：E339–E378 的 exposure.json 仍为
   `status=authored_pending_review`、`dev_exposed=false`、`experiments_used=[]`，
   与"2026-11-10 SELECTION 实验已曝光"的事实不符（账本回填缺口，不阻塞本集，
   但引用曝光状态时应以 runs/phase3 实据为准）。
3. **wave4 轻量臂结构性假 veto**：PILOT_RESULTS_MERGED.csv 中 DIRECT 臂
   veto=unknown_payload_field、score10=0.0 为契约形状假象（缺口 §5-1），本集已排除。
4. **T09 素材天然稀薄**：两池 T09 主专题仅 3 篇（E337 24 轮、E338 32 轮、E374 24 轮），
   本集配额 3 已全部纳入；T10 主专题位仅 2 个配额位。
5. **旧 pilot2 分析的 topic 归属口径**：`PILOT_ANALYSIS.json.error_positions` 的
   E322_T02 属 T06 主专题篇（非 T02），E325_T01 属 T07——引用时以 frozen manifest 篇级专题为准。

## 8. 倾斜风险（如实登记）

1. **弱项位全部来自 wave5**：7 硬 + 3 软 W 槽均为 wave5 位（四臂证据强度天然高于 wave4 两臂，
   R5a 规则驱动）。wave4 的三个硬失败位 **E307_T02 / E322_T02（CURRENT veto illegal_source_id）、
   E325_T01（DIRECT parse 失败）未进入本集**——它们在 W 槽竞争中败于证据更强的 wave5 位，
   填充槽又按标签多样性/篇分散落选。**后果**：本集没有专门的 parse 失败与 illegal_source_id
   回归位；若新原型需要这两类契约回归探针，建议后续以同规则扩容位补入（不改已选 60 位）。
2. **强项位偏向篇首**：S 槽并列 tie-break 取集小轮前 → 10 个 S 位中 7 个是各篇第 1 轮。
   开场轮代表性成立但轮位多样性由 40 个探针位承担。
3. **T01 wave5 池约束**：wave5 仅 1 篇 T01 主专题（E339），篇配额 ≤2 → T01 池分布 6/2 倾斜
   wave4，已固化进池配额而非事后调整。
4. **T09/T10 覆盖薄**（3/2 位）：长程与对抗是本集最薄的两维；对抗/诱导族在本集只能做
   存在性回归（cancel_affirm/severe 是否复发），档位级判读必须 B3。
5. **非盲测**：两池均开发曝光（§0），对调过的候选只作回归对照。
6. **case_id 后缀是轮序号不是专题号**（E301_T01=第 1 轮）：跨文档引用时勿把 `_Txx` 当专题码。

## 9. 复现与校验

```bash
cd refs/research_harness_20261110/phase1/benchmark
python -I build_dev_fast_set.py        # 重建并覆写 DEV_FAST_SET.json（确定性，无随机数）
```

自校验：`sha256(canonical_json(payload 去掉 sha256 字段; sort_keys=True, separators=(',',':'),
ensure_ascii=False).encode('utf-8'))` == JSON 顶层 `sha256` 字段
（当前 `99e8fe68…dea2fe`）。UTF-8 无 BOM；换行 LF。

复核清单（2026-11-11 已过）：60 位唯一 case_id；52 篇、每篇 ≤2 位；池 36/24；
每专题 1 弱 + 1 强；smoke_24 与 ID 前 24 一致且含全 10 专题；turn_no 与 case_id 零错位；
最长内嵌字符串 149 字符（无题面/gold 文本）；禁区零读取；零模型 API 调用。
