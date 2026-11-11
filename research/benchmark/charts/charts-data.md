# 章程阶段 8 图表数据底稿（charts-data）

> 用途：章程阶段 8 图表的真实数据底稿。纪律：从真实 CSV/JSON/MD 生成，保留绘图脚本和源数据；不截断坐标轴制造虚假差异；不手工输入想要的结果、不凭空补数；不同口径不相加、不互改；本文零哈希值（完整性口径 = 文件数 + 字节数，见 STATISTICS.md §五）。
> 数据截至：2026-11-12（全部源文件为 2026-11-11 夜间至 2026-11-12 凌晨产物，逐文件截至时间见文末清单）。
> 本目录（chart-materials/）新增文件：`chart-A-d1d7.svg`（图表 A）、`generate_chart_a.py`（图表 A 生成脚本，纯标准库可复跑）、`charts-data.md`（本文，图表 B/C 表格 + 全部源数据引用）。

---

## 图表 A：D1–D7 方向覆盖条图

- **SVG**：`D:\桌面\lovebrian\asset-audit\chart-materials\chart-A-d1d7.svg`
- **生成脚本**：`D:\桌面\lovebrian\asset-audit\chart-materials\generate_chart_a.py`（纯 Python 标准库，零第三方依赖，脚本内零硬编码数字）
- **复跑命令**：`python -I "D:\桌面\lovebrian\asset-audit\chart-materials\generate_chart_a.py"`（脚本程序化解析源 CSV，解析失败即报错退出；nine_steps 权威行与 d1_d7 节逐方向交叉核对，任何不一致即失败；无时间戳写入，重跑字节不变）
- **源数据**：`D:\桌面\lovebrian\asset-audit\dataset-final-reconciliation.csv` —— ① `nine_steps` 节 `8_d1_d7` 行（version_label 列，批次 A 最终回填紧凑口径）；② `d1_d7` 节 7 行（claimed 与去重回填明细）
- **数据截至**：2026-11-12（CSV 最后修改 2026-11-12 02:59；CSV 自标"本脚本 2026-11-12"）

### A-1 口径决定（重要，如实登记）

- 任务括号中的"D1 27"是 **claimed 值**；批次 A 最终回填（nine_steps/8_d1_d7 行）按任务主导条款"以批次 A 最终回填为准"为 **D1 枚举去重 29**，27 与 29 的出入已在对账 CSV `discrepancies` 节登记为 **X1**（"无法从审计文本分解出 27；回填采用枚举去重值 29 并标注 27 为审计内部未解释项"）。本图按回填口径画 29，并在图内脚注标注 claimed 27（X1）。
- D3 = C14 143 + C8 42 两段**分色堆叠**（C14 底段、C8 顶段），各自标注数值，**不合并成一个总数标注**，避免"相加"暗示。
- **D4 程序化 0 如实画出**（基线方块标记 + "0" 标签，柱高为零，不做任何美化）；judge-only 200 为人工评判口径，单列注记、不计入程序化覆盖。
- Y 轴自 0 起至 200（最大数据值即上限），无截断。
- 六不能纪律（对账 CSV `six_cannot_rules` 第 3 条）：各方向样本可重叠（同一 Episode 可属多个方向），**柱高禁止相加**，唯一池 = GOLD 覆盖 299。

### A-2 数据表（批次 A 最终回填，按 Episode 去重）

| 方向 | 回填值（图中所画） | claimed（审计原值） | 出入登记 | 备注 |
| --- | --- | --- | --- | --- |
| D1 长程事实保持 | **29** | 27 | X1 | 回填 = 枚举去重（LRE048/LRE060 + 16 LRE117-148 + E337/E338/E374 + E061-E068）；缺口：stage4 8 篇弱信号慎用，真信号 18 篇 |
| D2 记忆提取写入 | **200** | 200 | 一致 | 回填分解 96+16+78+2+8；缺口：stage4 全 false、wave4_dev 仅 51/1,350 |
| D3 记忆更新纠错 | **C14 143 + C8 42**（堆叠两段） | ~102(C14) + 31(C8) | X2 / X3 | 回填 = 分波枚举 143 / 42；缺口：corrected/resolved/suspended 149 原子 unrated、146 原子无 markers |
| D4 检索相关性 | **0（程序化）** | 0 programmatic / 200 judge-only | 最大缺口，如实登记 | forbidden 字段系统性零值缺口；judge-only 200 单列不计入本图 |
| D5 未知不确定性 | **130** | ~130(C15) | 一致 | 回填分解 45+12+33+37+3；缺口：纯字符串 unrated 2,649/3,116=85.0%，百分比口径见 X5 |
| D6 进行中事项追踪 | **123**（C10） | ~123(C10) + 31(C8) | C8 见 X3 | C8 信号（claimed 31 / 枚举 42）计入 D3，不在 D6 重复计；缺口：C10 覆盖约 8.7%（355/4,082）、stage4 零 intent |
| D7 上下文预算/输出契约 | **200** | 200 | 一致 | 与 D2 同池（200），不代表独立新增；缺口：token 预算需外挂工具 |

### A-3 图内注记（已写入 SVG 脚注，与上表同源）

① D4 程序化 0 为最大缺口如实保留；judge-only 200 单列。② X1（D1 27 vs 29）、X2/X3（D3 ~102+31 vs 143/42）。③ D6 的 C8 计入 D3 不重复计；D7 与 D2 同池。④ Y 轴自 0 起；柱高禁止相加；唯一池 = 299（STATISTICS.md §三.3）。

---

## 图表 B：Gold 三代口径对比表（Markdown 表格，不画图）

**不画图的理由**：三代口径分母不同、实体不同，禁止同轴对比；表格是最诚实形式。

- **源数据**：`D:\桌面\lovebrian\asset-audit\gold-dual-track-plan.md` §0 三口径表（编写日期 2026-11-12，"本方案编写时全部实测复核，非转抄"）；交叉源 `D:\桌面\lovebrian\asset-audit\episode-recount-results.md` §2/§6、`D:\LoveBrain-research-staging\research\benchmark\STATISTICS.md` §一/§四
- **数据截至**：2026-11-12（gold-dual-track-plan.md 最后修改 2026-11-12 01:26；STATISTICS.md 2026-11-12 03:11）

### B-1 三代口径并列表

| 口径 | 实体位置 | 生成时间 | 字面 schema_version | episodes | turns | atoms | 文件数 | 说明 |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | --- |
| ① v1.0 文档口径 | 仅存在于 `GOLD_SCHEMA.md` 文本（§0/§1 布局 4），无文件实体留存 | 2026-11-03 前后 | "GOLD_SIDECAR_v1"（文档称谓） | 221 | 3,102 | 6,603 | — | gold agent 首版产物；被 ② 取代；全仓与远端 HEAD 均无其文件（c1 §3.3） |
| ② 远端已提交快照 | 远端 git 历史（main `387c55ac` 与 `7f0d189e` 两 ref blob 等价）+ 镜像可恢复；已恢复至 `GOLD_SIDECAR_v1_remote_snapshot/`（13 文件） | 2026-11-08T06:00:43（GOLD_INDEX generated） | GOLD_SIDECAR_v2 | 259 | 3,590 | 7,953 | 13 | 远端最后一次提交的 Gold 构建波次（11-08 Benchmark V1 Phase 2 提交）；历史快照轨，仅作版本历史与审计对照，不作为评分加载输入 |
| ③ 本地 v2 重建（**权威**） | `D:\lovebrain-arena\benchmark\stories_v2\GOLD_SIDECAR\`（未提交）；随迁 `research/benchmark/stories_v2/GOLD_SIDECAR/` | 2026-11-10T04:25:15（GOLD_INDEX generated） | GOLD_SIDECAR_v2 | **299** | **4,082** | **9,646** | **14**（新增 GOLD_wave5_selection.json） | 11-10 账本补录 + Gold 重建批次；当前有效口径，经 2026-11-11 审计 + 2026-11-11 机器重算 + 2026-11-12 第三重复算三重一致 |

### B-2 恒等式注释（逐项有源，禁止跨口径相加）

1. **①内部**：221 = 208（PAPER 库内）+ 11（stage1/stageD）+ 2（wave1_ext）【STATISTICS.md §一·221 行】。历史并列口径 208/2,944（PAPER 库内）与"296"（无法可靠复现，弃用）见 STATISTICS.md §一/§四，不列入本表。
2. **②→③**：③ − ② 三项差值 = 299−259 = **40**、4,082−3,590 = **492**、9,646−7,953 = **1,693**，逐项等于 GOLD_wave5_selection.json 分文件（40/492/1,693，episode-recount-results.md §2）——两代差即 v2 新增第 14 文件（wave5）。
3. **③内部**：317 = 299 + 12（anchors）+ 6（final）；319 − 2（D01/D02 版本目录重复组）= 317；248 = 299 − 8（stage1）− 3（stageD）− 40（wave5）（审计时点）；288 = 248 + 40（wave5 补登，纯追加）【STATISTICS.md §二恒等式，逐项复算通过】。
4. **③分文件闭合**：12 个 GOLD 分文件 episodes/turns/assertions 求和 = GOLD_INDEX.totals（4,082 / 9,646 逐项全等）【episode-recount-results.md §2】。
5. **使用纪律**：三个口径分别登记、不相加、不互改（gold-dual-track-plan.md §0；STATISTICS.md 版本标注纪律）；双轨操作对象是 ② 与 ③，① 以文档原样 + 注记方式随迁。

---

## 图表 C：wave5 补登前后 custody 状态对照（Markdown 表）

- **源数据**：`D:\桌面\lovebrian\asset-audit\dataset-final-reconciliation.csv`（`core/custody_records_asof` / `custody_records_current` / `custody_lifecycle` 行 + `partition_columns` 节 + `discrepancies` X8 行）；直读核验两账本 JSON：补登前备份 `D:\桌面\lovebrian\asset-audit\ledger-backup-before-wave5-20261112.json`（2026-11-12 01:14）与当前账本 `D:\lovebrain-arena\refs\experiment_protocol_20261023\global_harness_program_20261030\STORY_CUSTODY_LEDGER.json`（2026-11-12 01:16）；交叉源 `D:\LoveBrain-research-staging\research\benchmark\STATISTICS.md` §一·248 行 + §二
- **数据截至**：2026-11-12（补登执行于 2026-11-12，决策二；CSV 最后修改 2026-11-12 02:59）

### C-1 补登前后对照表

| 指标 | 补登前（审计时点口径） | 补登后（当前口径） | 变化 | 说明 |
| --- | ---: | ---: | --- | --- |
| stories（账本注册 Story 数） | **248** | **288** | +40 | 决策二 wave5 补登 40 条，纯追加，题面零改动；补登前状态另有备份副本佐证（ledger-backup-before-wave5-20261112.json） |
| events（账本事件数） | **218** | **379** | +161 | = 160 消费事件（selection_v1 四臂 LEGACY/COT/DIRECT/CURRENT × 40 = 160 story×臂消费对，2026-11-10）+ 1 登记事件（wave5_selection_registered） |
| EXPOSED | **87** | **127** | +40 | EXPOSED ⇔ consumed_by 非空 1:1（直读核验：备份 87/87、当前 127/127 全部成立）；+40 = wave5 补登 |
| SEALED | **161** | **161** | **0（不变）** | 零消费，补登前后不变；B_LINE 24 篇全部在其中 |
| 未注册（设计如此） | 69 | 29 | −40 | 补登前 69 含 wave5 40；补登后 29 = stage1 8 + stageD 3 + anchors 12 + final 6（各有独立不在账本原因，见 custody-ledger-analysis §5） |

### C-2 恒等式

```text
248 = 299 − 8（stage1） − 3（stageD） − 40（wave5）        （审计时点，STATISTICS.md §二）
288 = 248 + 40（wave5 补登，纯追加）                        （当前口径）
317 − 248 = 69；  317 − 288 = 29（stage1 8 + stageD 3 + anchors 12 + final 6）
218 + 161 = 379（161 = 160 消费事件 + 1 登记事件）
EXPOSED 87 + 40 = 127；SEALED 161 → 161（零消费，不变）
```

### C-3 直读核验记录（2026-11-12，python -I 只读）

- 备份账本：stories=248、events=218、lifecycle 分布 {EXPOSED: 87, SEALED: 161}，EXPOSED 且 consumed_by 非空 = 87/87。
- 当前账本：stories=288、events=379、lifecycle 分布 {EXPOSED: 127, SEALED: 161}，EXPOSED 且 consumed_by 非空 = 127/127；事件类型中出现 `wave5_selection_registered` ×1（备份中无）。
- 核验结论：与对账 CSV core/custody_lifecycle 行、STATISTICS.md §一·248 行"补登后 288 stories / 379 events"逐项一致。
- 两口径并列登记、不相加不互改；"248"按审计时点口径解封（STATISTICS.md §五），"288"为补登后当前口径。

---

## 源文件清单与数据截至时间

| 文件 | 角色 | 数据截至（mtime / 文内自标） |
| --- | --- | --- |
| `D:\桌面\lovebrian\asset-audit\dataset-final-reconciliation.csv` | 图表 A 主源；图表 C 主源 | 2026-11-12 02:59（自标"本脚本 2026-11-12"） |
| `D:\LoveBrain-research-staging\research\benchmark\STATISTICS.md` | 八数字版本化口径页；B/C 交叉源 | 2026-11-12 03:11（自标 2026-11-12 定稿） |
| `D:\桌面\lovebrian\asset-audit\episode-recount-results.md` | 图表 B 交叉源（分文件 40/492/1,693、恒等式） | 2026-11-11T17:13:26+00:00 生成（mtime 2026-11-12 01:13） |
| `D:\桌面\lovebrian\asset-audit\gold-dual-track-plan.md` | 图表 B 主源（§0 三口径表） | 2026-11-12 01:26（自标编写日期 2026-11-12） |
| `D:\桌面\lovebrian\asset-audit\ledger-backup-before-wave5-20261112.json` | 图表 C 补登前直读源 | 2026-11-12 01:14 |
| `D:\lovebrain-arena\refs\experiment_protocol_20261023\global_harness_program_20261030\STORY_CUSTODY_LEDGER.json` | 图表 C 补登后直读源 | 2026-11-12 01:16 |

## 复跑指引

1. 图表 A：`python -I "D:\桌面\lovebrian\asset-audit\chart-materials\generate_chart_a.py"` —— 从源 CSV 重新解析并重写 `chart-A-d1d7.svg`；源 CSV 未变则输出字节不变。
2. 图表 B/C：本文 Markdown 表格即为产物；数字逐项抄录自上表所列源文件（引用行号/节号见各表"源数据"），其中图表 C 另经 python -I 直读两账本 JSON 独立核验（见 C-3）。
3. 全程零哈希登记；如需复核本底稿与源的一致性，逐项对照上表路径即可，不引入任何哈希值。
