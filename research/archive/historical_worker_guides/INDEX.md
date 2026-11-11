<!-- historical worker document, sanitized; not current -->

# INDEX — research/archive/historical_worker_guides/（s15 落位脱敏副本域索引）

- 生成：2026-11-12（s15 后续编目动作）；生成方式：只读实测本目录 + 引用既有登记，未改动任何源文件。
- 本 INDEX 自身亦属历史编目材料，**不是执行指令**。

## 0. 统一口径（按 s15 既有标注口径引用，不新设口径）

1. **historical / not current**：本目录 8 件首行一律为统一标注 `<!-- historical worker document, sanitized; not current -->`（逐件实测 8/8，见 §3 自检）。全部内容为**历史 Worker 执行材料快照**，一律不是当前执行指令，不得作为任何现行任务的执行依据；其中的"当前入口/优先读/唯一执行依据"等字样均为历史时点语义（与工作区章程"过去版本仅作历史参考"同口径）。
2. **sanitized**：本目录全部为脱敏副本，**原件一律保留在内部仓库、不随副本外发**（s15 §2.1/§2.2 口径）。脱敏替换值沿用 s15 占位符词汇表（`sanitize-copies-report.md` §7：`<arena-root>`、`<LoveBrain-repo>`、`<TeamWorkspace-repo>`、`<legacy-local-workspace>`、`<fixed-model-endpoint>`/`<model-port>` 等）——文件内出现的尖括号占位符即脱敏标记，不是损坏。
3. **完整性口径**：sha256 停用纪律（2026-11-12 用户纪律）——本 INDEX 与本目录均**不含任何哈希值**；完整性以**文件数 + 字节数**为准（自检见 §3）。
4. **登记依据**：`MIGRATION_MANIFEST.csv` 行 ARC-00004 / ARC-00088 / ARC-00110…ARC-00115（target=bystery/LoveBrain，branch research-migration-20261112）；逐件脱敏点构成与变换台账见 `sanitize-copies-report.md` §2.1/§2.2 与 `_sanitize_scripts/transform-log.json`。落位 commit `4c453429`。

## 1. s15 46 件域 → 三分组对照（域口径总览）

s15 WORKER 族实测 46 件（45 个 WORKER_* 命名件 + 1 个同规则登记的 `指导书.md`，`sanitize-copies-report.md` §1）按其处置区块分三组：

| 分组 | 件数 | 位置 | 与本目录的关系 |
| --- | ---: | --- | --- |
| 根系交接件（arena 根） | 7 | **本目录全部落位**（§2 表 #1–#7） | 本 INDEX 的编目主体 |
| refs 历史执行件（experiment_protocol 各批次 WORKER_* 交接/执行/runbook 文档） | 25 | `refs/experiment_protocol_20261023/` 各批次目录**原位**（15 件含脱敏点原位覆盖脱敏 + 10 件零脱敏点原样保留） | **不在本目录**，不在本 INDEX 逐件清单内 |
| 批次执行件（s1_action_live_20261028 判词件） | 14 | 同上目录**原位**（判词代码 7 件 `*_WORKER_SCORES.py` 零脱敏点 + 字节码 7 件按迁移排除规则本就不进树） | **不在本目录**，不在本 INDEX 逐件清单内 |

- 另有 **AGENTS-v1.6.md 1 件**系 s15 任务 2（renamed-historical）随本目录落位，属 46 件域之外 → 本目录实测 **8 件**（§2）。
- refs 侧 39 件按 s15 口径由所在协议目录与 F6 records 承载历史属性，本目录只收根系件（避免同名碰撞），故本 INDEX 不为其设逐件行。

## 2. 文件清单表（实测 2026-11-12）

实测方式：`ls` 清点 + `stat -c %s` 逐件字节 + 逐件读取首行与标题行。**清单件数 8 = 目录实数 8**（无子目录、无隐藏文件）。字节数逐件与 MIGRATION_MANIFEST 对应行 target_size 一致（8/8）。

"首行标注"列 ✓ = 存在 §0-1 统一标注串（逐件核验）。"一句话定性" = 逐件标题行原样摘录（零内容复述）。

| # | 分组 | 文件 | 实测字节 | 来源（ARC · arena 源路径 · 源字节 → 落位字节） | 脱敏点 | 首行标注 | 一句话定性（标题行摘录） |
| - | --- | --- | ---: | --- | ---: | --- | --- |
| 1 | 根系交接件 | WORKER_CURRENT_RUNBOOK.json | 6,523 | ARC-00110 · WORKER_CURRENT_RUNBOOK.json · 6,612 → 6,523 | 23 | ✓ | `"kind": "lovebrain_worker_current_runbook_pointer"`（updated_date 2026-11-06） |
| 2 | 根系交接件 | WORKER_EXPLORATION_GUIDE_20261103.md | 7,602 | ARC-00111 · WORKER_EXPLORATION_GUIDE_20261103.md · 7,536 → 7,602 | 5 | ✓ | "WORKER EXPLORATION GUIDE — 全面探索指导书（2026-11-03）" |
| 3 | 根系交接件 | WORKER_HANDOFF_20261028_2222.md | 38,990 | ARC-00112 · WORKER_HANDOFF_20261028_2222.md · 39,018 → 38,990 | 13 | ✓ | "LoveBrain 完整 Harness 交接文档（2026-10-28 22:22）" |
| 4 | 根系交接件 | WORKER_HANDOFF_W22_20261104.md | 8,192 | ARC-00088 · archive/20261107_root_cleanup/handoffs/WORKER_HANDOFF_W22_20261104.md · 8,216 → 8,192 | 2 | ✓ | "交接文档 — W22-01 全量批收口（2026-11-04）" |
| 5 | 根系交接件 | WORKER_LOOP_PROMPT_20261103.md | 4,816 | ARC-00113 · WORKER_LOOP_PROMPT_20261103.md · 4,755 → 4,816 | 2 | ✓ | "WORKER LOOP PROMPT — 继续任务指令（2026-11-03，可直接粘贴给新窗口）" |
| 6 | 根系交接件 | WORKER_NEW_WINDOW_START.md | 7,624 | ARC-00114 · WORKER_NEW_WINDOW_START.md · 7,576 → 7,624 | 8 | ✓ | "当前交接（2026-11-11，RESEARCH-P1 第一大阶段——优先读）" |
| 7 | 根系交接件 | 指导书-V14-20261022.md | 4,089 | ARC-00115 · 指导书.md（改名登记）· 4,029 → 4,089 | 0（仅标注） | ✓ | "指导书.md — Arena V14 收口交接书（2026-10-22）" |
| 8 | s15 任务 2 章程历史件（46 件域之外） | AGENTS-v1.6.md | 32,459 | ARC-00004 · AGENTS.md（v1.6，改名登记 renamed-historical）· 32,447 → 32,459 | 7 | ✓ | "AGENTS.md — LoveBrain 全局持续主线 v1.6（2026-10-30）" |

合计：8 件，110,295 B。

## 3. 自检记录（2026-11-12）

| 自检项 | 结果 |
| --- | --- |
| 清单件数 = 目录实数 | **8 = 8 PASS**（含 INDEX.md 前实测；目录无子目录/隐藏文件） |
| 首行 historical 标注 | **8/8 PASS**（统一串逐件核验，含 AGENTS-v1.6.md） |
| 零真实姓名（d2 词表抽 10 词条 grep） | **PASS**：取 d2 登记 token 5 件（KB-g 目录名 + 4 个 KB 目录 ID）+ 各 kb.json 身份字段 token 5 件，共 10 词条（全部脱敏副本目录 + 本 INDEX 回扫）→ **0 命中**；词条值按纪律不写入本 INDEX |
| 零 sha256 | **PASS**：本 INDEX 无任何哈希值（"sha256"字样仅出现于停用纪律表述，非哈希值）；编目过程未计算任何新哈希 |
| 字节数对账 | **8/8 PASS**：逐件实测字节 = MIGRATION_MANIFEST 对应行 target_size |

## 4. 关联登记

- 逐件登记：`bystery/LoveBrain` worktree `research-migration-20261112` → `research/provenance/MIGRATION_MANIFEST.csv`（ARC-00004/00088/00110…00115）；asset-audit 主件镜像同内容。
- 处置台账：`D:\桌面\lovebrian\asset-audit\sanitize-copies-report.md` §2.1/§2.2、`_sanitize_scripts/transform-log.json`。
- 编目动作记录：`D:\桌面\lovebrian\asset-audit\historical-worker-guides-index.md`。
