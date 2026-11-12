# 图表数据底稿（charts-data）

> 用途：`research/benchmark/charts/` 全部图表的数据底稿。数字全部由脚本程序化解析自仓库内 CSV/JSON/scoring.json，生成脚本与源数据均在仓内；坐标轴不截断；不同口径不相加、不互改。
> 数据截至：2026-11-12。全部复跑命令在仓库 clone 根目录下可直接执行（纯 Python 标准库，无第三方依赖）：

```bash
python -I research/benchmark/charts/generate_chart_a.py            # 图表 A
python -I research/benchmark/charts/generate_selection_charts.py   # 图表 D/E/F
```

---

## 图表 A：D1–D7 方向覆盖条图（chart-A-d1d7.svg）

- **生成脚本**：`research/benchmark/charts/generate_chart_a.py`（纯标准库；nine_steps 权威行与 d1_d7 节逐方向交叉核对，不一致即报错退出；重跑输出不变）
- **源数据**：`research/benchmark/dataset-final-reconciliation.csv` —— ① `nine_steps` 节 `8_d1_d7` 行（最终回填口径）；② `d1_d7` 节 7 行（claimed 与去重回填明细）
- **数据截至**：2026-11-12（CSV 自标"本脚本 2026-11-12"）

### A-0 数据源说明

该 CSV 源自私有研究工作区的数据集对账流程，内容为**纯聚合统计**（数量、恒等式、出入登记，无原始回复、无用户标识、无密钥），收入公开仓 `research/benchmark/dataset-final-reconciliation.csv`（18,826 字节，65 数据行 + 表头）。脚本读仓库内相对路径，clone 后即可复跑。

### A-1 口径说明

- 任务括号中的"D1 27"是 **claimed 值**；最终回填（nine_steps/8_d1_d7 行）取 **D1 枚举去重 29**，27 与 29 的出入在对账 CSV `discrepancies` 节登记为 **X1**（"无法从审计文本分解出 27；回填采用枚举去重值 29 并标注 27 为审计内部未解释项"）。本图按回填口径画 29，并在图内脚注标注 claimed 27（X1）。
- D3 = C14 143 + C8 42 两段**分色堆叠**（C14 底段、C8 顶段），各自标注数值，**不合并成一个总数标注**，避免"相加"暗示。
- **D4 程序化 0**（基线方块标记 + "0" 标签，柱高为零）；judge-only 200 为人工评判口径，单列注记、不计入程序化覆盖。
- Y 轴自 0 起至 200（最大数据值即上限），无截断。
- 口径说明（对账 CSV `six_cannot_rules` 第 3 条）：各方向样本可重叠（同一 Episode 可属多个方向），**柱高禁止相加**，唯一池 = GOLD 覆盖 299。

### A-2 数据表（最终回填，按 Episode 去重）

| 方向 | 回填值（图中所画） | claimed（审计原值） | 出入登记 | 备注 |
| --- | --- | --- | --- | --- |
| D1 长程事实保持 | **29** | 27 | X1 | 回填 = 枚举去重（LRE048/LRE060 + 16 LRE117-148 + E337/E338/E374 + E061-E068）；缺口：stage4 8 篇弱信号慎用，真信号 18 篇 |
| D2 记忆提取写入 | **200** | 200 | 一致 | 回填分解 96+16+78+2+8；缺口：stage4 全 false、wave4_dev 仅 51/1,350 |
| D3 记忆更新纠错 | **C14 143 + C8 42**（堆叠两段） | ~102(C14) + 31(C8) | X2 / X3 | 回填 = 分波枚举 143 / 42；缺口：corrected/resolved/suspended 149 原子 unrated、146 原子无 markers |
| D4 检索相关性 | **0（程序化）** | 0 programmatic / 200 judge-only | 最大缺口 | forbidden 字段系统性零值缺口；judge-only 200 单列不计入本图 |
| D5 未知不确定性 | **130** | ~130(C15) | 一致 | 回填分解 45+12+33+37+3；缺口：纯字符串 unrated 2,649/3,116=85.0%，百分比口径见 X5 |
| D6 进行中事项追踪 | **123**（C10） | ~123(C10) + 31(C8) | C8 见 X3 | C8 信号（claimed 31 / 枚举 42）计入 D3，不在 D6 重复计；缺口：C10 覆盖约 8.7%（355/4,082）、stage4 零 intent |
| D7 上下文预算/输出契约 | **200** | 200 | 一致 | 与 D2 同池（200），不代表独立新增；缺口：token 预算需外挂工具 |

### A-3 图内注记（已写入 SVG 脚注，与上表同源）

① D4 程序化 0 为最大缺口；judge-only 200 单列。② X1（D1 27 vs 29）、X2/X3（D3 ~102+31 vs 143/42）。③ D6 的 C8 计入 D3 不重复计；D7 与 D2 同池。④ Y 轴自 0 起；柱高禁止相加；唯一池 = 299（STATISTICS.md §三.3）。

---

## 图表 B：Gold 三代口径对比表（Markdown 表格，不画图）

**不画图的理由**：三代口径分母不同、实体不同，不可同轴对比，故以表格呈现。

- **源数据**：`research/benchmark/dataset-final-reconciliation.csv` 的 `historical_parallel` 节 3 行（① v1 文档口径 221/3,102/6,603；② 远端已提交快照 259/3,590/7,953；③ 本地 v2 重建见 `core` 节 299/4,082/9,646）与 `core` 节 `gold_covered_episodes / gold_turns / gold_atoms` 行；交叉源 `research/benchmark/STATISTICS.md` §一/§二/§四
- **数据截至**：2026-11-12（STATISTICS.md 自标 2026-11-12 定稿）

### B-1 三代口径并列表

| 口径 | 实体位置 | 生成时间 | 字面 schema_version | episodes | turns | atoms | 文件数 | 说明 |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | --- |
| ① v1.0 文档口径 | 仅存在于 `GOLD_SCHEMA.md` 文本（§0/§1 布局 4），无文件实体留存 | 2026-11-03 前后 | "GOLD_SIDECAR_v1"（文档称谓） | 221 | 3,102 | 6,603 | — | gold agent 首版产物；被 ② 取代；全仓与远端 HEAD 均无其文件 |
| ② 远端已提交快照 | 远端 git 历史（main 与研究分支两 ref blob 等价）+ 镜像可恢复 | 2026-11-08T06:00:43（GOLD_INDEX generated） | GOLD_SIDECAR_v2 | 259 | 3,590 | 7,953 | 13 | 远端最后一次提交的 Gold 构建波次（11-08 Benchmark V1 Phase 2 提交）；历史快照轨，仅作版本历史与审计对照，不作为评分加载输入 |
| ③ 本地 v2 重建（**权威**） | `research/benchmark/stories_v2/GOLD_SIDECAR/` | 2026-11-10T04:25:15（GOLD_INDEX generated） | GOLD_SIDECAR_v2 | **299** | **4,082** | **9,646** | **14**（新增 GOLD_wave5_selection.json） | 2026-11-10 账本补录与 Gold 重建产物；当前有效口径 |

### B-2 恒等式注释（禁止跨口径相加）

1. **①内部**：221 = 208（PAPER 库内）+ 11（stage1/stageD）+ 2（wave1_ext）【STATISTICS.md §一·221 行】。历史并列口径 208/2,944（PAPER 库内）与"296"（无法可靠复现，弃用）见 STATISTICS.md §一/§四与对账 CSV X6，不列入本表。
2. **②→③**：③ − ② 三项差值 = 299−259 = **40**、4,082−3,590 = **492**、9,646−7,953 = **1,693**，逐项等于 GOLD_wave5_selection.json 分文件（40/492/1,693）——两代差即 v2 新增第 14 文件（wave5）。
3. **③内部**：317 = 299 + 12（anchors）+ 6（final）；319 − 2（D01/D02 版本目录重复组）= 317；248 = 299 − 8（stage1）− 3（stageD）− 40（wave5）（审计时点）；288 = 248 + 40（wave5 补登，纯追加）【STATISTICS.md §二恒等式 + 对账 CSV `core/not_registered_by_design`】。
4. **③分文件闭合**：12 个 GOLD 分文件 episodes/turns/assertions 求和 = GOLD_INDEX.totals（4,082 / 9,646 逐项全等）【对账 CSV `nine_steps/3_turns`、`nine_steps/5_atoms` 行】。
5. **口径使用**：三个口径不相加、不互改（对账 CSV `historical_parallel` 各行同注）；①② 为历史轨，③ 为评分加载权威轨。

---

## 图表 C：wave5 补登前后 custody 状态对照（Markdown 表）

- **源数据**：`research/benchmark/dataset-final-reconciliation.csv`（`core/custody_records_asof` / `custody_records_current` / `custody_lifecycle` 行 + `partition_columns` 节 + `discrepancies` X8 行）；交叉源 `research/benchmark/STATISTICS.md` §一·248 行 + §二
- **数据截至**：2026-11-12（补登执行于 2026-11-12）

### C-1 补登前后对照表

| 指标 | 补登前（审计时点口径） | 补登后（当前口径） | 变化 | 说明 |
| --- | ---: | ---: | --- | --- |
| stories（账本注册 Story 数） | **248** | **288** | +40 | wave5 补登 40 条，纯追加，题面不变 |
| events（账本事件数） | **218** | **379** | +161 | = 160 消费事件（selection_v1 四臂 LEGACY/COT/DIRECT/CURRENT × 40 = 160 story×臂消费对，2026-11-10）+ 1 登记事件（wave5_selection_registered） |
| EXPOSED | **87** | **127** | +40 | EXPOSED ⇔ consumed_by 非空 1:1（+40 = wave5 补登） |
| SEALED | **161** | **161** | **0（不变）** | 零消费，补登前后不变；B_LINE 24 篇全部在其中 |
| 未注册（设计如此） | 69 | 29 | −40 | 补登前 69 含 wave5 40；补登后 29 = stage1 8 + stageD 3 + anchors 12 + final 6（各有独立不在账本原因） |

### C-2 恒等式

```text
248 = 299 − 8（stage1） − 3（stageD） − 40（wave5）        （审计时点，STATISTICS.md §二）
288 = 248 + 40（wave5 补登，纯追加）                        （当前口径）
317 − 248 = 69；  317 − 288 = 29（stage1 8 + stageD 3 + anchors 12 + final 6）
218 + 161 = 379（161 = 160 消费事件 + 1 登记事件）
EXPOSED 87 + 40 = 127；SEALED 161 → 161（零消费，不变）
```

### C-3 两口径并列说明

审计时点口径（248/218/87）与补登后当前口径（288/379/127）并列呈现、不相加；补登前数值取自对账 CSV `core/custody_records_asof` 行。

---

## 图表 D：上下文使用成本（chart-context-cost.svg）

- **生成脚本**：`research/benchmark/charts/generate_selection_charts.py`（纯标准库，重跑输出不变）
- **源数据**：`research/refs/benchmark_v1_20261107/phase3/experiments/selection_v1/selection_v1_universal_scores.csv`（四臂逐位评分，1,954 行）
- **交叉核对**：同目录 `FOUR_ARM_SUMMARY.json`（最终汇总，LEGACY 补跑后 492 位）单轮均值差 <1%，脚本自动核对、超差即失败
- **引用关系**：根 README §5 图 1 引用本图；口径解释以本节为权威，README 不重复展开

### D-1 口径与可比性

- **可比前提**：同批（wave5_selection 40 集 × 4 臂 = 1,968 位计划）、同模型（deepseek-flash-2）、同采样、同一评分栈；臂定义见同目录 `ARM_REGISTRY.json`；批次 2026-11-10。
- **分母**：DIRECT/COT/CURRENT 各 492 位；LEGACY 在本评分文件快照中为 478 位（最终补跑后 492 位的总量见 FOUR_ARM_SUMMARY.json：3,384,362 tokens，单轮均值 6,879，与本快照均值 6,852 差 0.4%）。
- **画法**：prompt（上下文装配）与 completion（回复）为 token 构成的真实可加分解，堆叠并分别标注；右面板为同文件 latency_ms 均值。

### D-2 数据表（脚本实跑输出，一位小数为 CSV 精确均值；图内标签按 :.0f 取整）

| 臂 | n | prompt tokens/轮 | completion tokens/轮 | 合计 tokens/轮 | 平均延迟 s/轮 |
| --- | ---: | ---: | ---: | ---: | ---: |
| DIRECT（直接基线） | 492 | 1,077.0 | 401.7 | 1,478.6 | 31.7 |
| COT（CoT 基线） | 492 | 1,116.0 | 457.6 | 1,573.5 | 39.6 |
| LEGACY（历史 Harness） | 478 | 5,094.8 | 1,757.6 | 6,852.4 | 135.2 |
| CURRENT（LoveBrain 装配） | 492 | 2,522.2 | 1,614.6 | 4,136.8 | 120.4 |

结论数字由脚本计算：CURRENT/LEGACY 单轮 token = 60.4%（降 39.6%），平均延迟 = 89.0%（图标题按"60%"档标注）。**解读边界**：DIRECT/COT token 更低，但作为纯文本直接基线不实现结构化输出协议（见图表 E），不构成同机制公平对比；本图的同机制对比对象是 LEGACY → CURRENT。

---

## 图表 E：输出协议合规（chart-contract-quality.svg）

- **生成脚本**：`research/benchmark/charts/generate_selection_charts.py`（逐轮遍历 1,968 个 scoring.json 汇总，校验逐臂恰 492 轮）
- **源数据**：`research/experiments/runs/selection_v1/<臂>/<集>/<轮>/scoring.json`（层 1 确定性计分器的 `json_schema_valid` 检查与 `veto` 字段，10,090 文件的 run 内逐轮评分）
- **引用关系**：根 README §5 图 2 引用本图；口径解释以本节为权威，README 不重复展开

### E-1 口径与可比性

- 层 1 确定性计分器逐轮校验结构化回复协议（顶层键/动作契约）；四臂 × 40 集 × 12 轮 = 1,968 个 scoring.json 全覆盖，同批同模型同采样。
- 复评层（universal_scorer 轻量契约）交叉印证：1,954 位中仅 COT 1 位、LEGACY 1 位 `contract_ok=False`，其余全过（`research/refs/benchmark_v1_20261107/phase3/experiments/selection_v1/selection_v1_universal_scores.csv`）。

### E-2 数据表（脚本实跑输出）

| 臂 | 协议合规（json_schema_valid） | 否决（veto）轮数 | 说明 |
| --- | --- | ---: | --- |
| DIRECT | 4/492（0.8%） | 488（unknown_payload_field） | 纯文本基线不实现协议；低合规率是协议层有效拦截的证据，非同机制对比 |
| COT | 3/492（0.6%） | 489（488 同上 + 1 json_schema_broken） | 同上 |
| LEGACY | 491/492（99.8%） | 1（unauthorized_no_reply） | 唯一违例轮 = E355 T03（越权拒答且 schema 不合规，同轮） |
| CURRENT | 492/492（100%） | 0 | — |

同机制对比结论：LEGACY → CURRENT，协议违例 + 否决 2 项 → 0 项（n=492/492）。**解读边界**：该通道在两个完整 Harness 间已接近饱和，改善幅度小；本图同时展示轻量基线的对照，说明协议层有真实判别力。

---

## 图表 F：关键断言覆盖（chart-context-retention.svg，null 结果）

- **生成脚本**：`research/benchmark/charts/generate_selection_charts.py`
- **源数据**：`research/refs/benchmark_v1_20261107/phase3/experiments/selection_v1/FOUR_ARM_SUMMARY.json`（最终汇总口径）
- **引用关系**：根 README §5 图 3 引用本图；口径解释以本节为权威，README 不重复展开

### F-1 口径与局限

- 指标：Gold 要求的原子断言（required）被回复覆盖的比例 = required_hits / required_total。
- 数据：DIRECT 49/232（21.1%）、COT 50/232（21.6%）、LEGACY 39/232（16.8%）、CURRENT 50/232（21.6%）；每臂仅 232 项 required 断言，约 193–198 轮适用（其余 294–299 轮 N/A）。
- **为什么是 null 结果图**：CURRENT 与 LEGACY 差约 +4.7 个百分点（11/232，与 SVG 图内标注一致），统计上不显著；且 universal_scorer_v2 记载该通道"required 标记僵化"（`research/refs/research_harness_20261110/overnight/RESUME_STATE.json` · scorer.known_gaps）。本图呈现 null 结果与通道局限，**不作为机制优势证据**。
- v1 评分快照（LEGACY 478 位）同通道为 38/220（17.3%），结论一致（`selection_v1_universal_scores.csv`）。

### F-2 长程事实保持为什么没有"效果图"

截至 2026-11-12，公开仓内尚无满足"同模型/同数据/同评分口径且统计可分"的长程事实保持机制效果图：

- 四臂实验 required 通道为 null（见上）；
- `holdout_v1` 仅 14.2% 部分执行（889/6,240 位），判决不存在（见 BENCHMARK_EVALUATION.md §6）；
- 机制-track（rh_b0x）各候选评分多为 PROVISIONAL/INVALIDATED（文献看门狗 LOOP 违规追溯，`refs/research_harness_20261110/overnight/CANDIDATE_SCOREBOARD.csv`），不得按已验证结果展示。

本方向先以 null 结果图（图表 F）呈现，效果结论待 holdout 轨与更细评分通道补充。

---

## 源文件清单（全部为仓库内相对路径）

| 文件 | 角色 | 数据截至 |
| --- | --- | --- |
| `research/benchmark/dataset-final-reconciliation.csv` | 图表 A 主源；图表 B/C 主源（18,826 字节） | 2026-11-12（自标） |
| `research/benchmark/STATISTICS.md` | 图表 B/C 交叉源 | 2026-11-12 定稿 |
| `research/refs/benchmark_v1_20261107/phase3/experiments/selection_v1/selection_v1_universal_scores.csv` | 图表 D 主源；图表 E/F 交叉源（1,954 行） | 2026-11-10 生成 |
| `research/refs/benchmark_v1_20261107/phase3/experiments/selection_v1/FOUR_ARM_SUMMARY.json` | 图表 D 交叉核对；图表 F 主源 | 2026-11-10 生成 |
| `research/refs/benchmark_v1_20261107/phase3/experiments/selection_v1/ARM_REGISTRY.json` | 臂定义 | 2026-11-10 |
| `research/experiments/runs/selection_v1/`（10,090 文件） | 图表 E 主源（其中 1,968 个逐轮 scoring.json） | 2026-11-10 |
| `research/refs/research_harness_20261110/overnight/RESUME_STATE.json` | 图表 F 通道局限佐证（scorer.known_gaps） | 2026-11-11 |

## 复跑指引（clone 后于仓库根目录执行）

1. 图表 A：`python -I research/benchmark/charts/generate_chart_a.py` —— 从 `research/benchmark/dataset-final-reconciliation.csv` 重新解析并重写 `chart-A-d1d7.svg`；源未变则输出不变。
2. 图表 D/E/F：`python -I research/benchmark/charts/generate_selection_charts.py` —— 解析四臂评分 CSV + 最终汇总 JSON + 逐轮遍历 1,968 个 scoring.json，写出 3 个 SVG；含交叉核对与规模校验（不一致即报错退出）。
3. 图表 B/C：本文 Markdown 表格即为产物；数字逐项抄录自上表所列源文件的对应节/行。
