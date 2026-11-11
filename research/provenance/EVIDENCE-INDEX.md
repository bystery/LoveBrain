# 证据索引改写件（02 §12 → 公开版映射，29 项逐条登记）

- 产出日期：2026-11-12
- 依据：`guide2-verification-report.md` §6（逐条映射建议）+ `SOURCE_MAP-FINAL.md`（落位路径实测）+ worktree `D:\LoveBrain-research-staging`（分支 `research-migration-20261112`）实树逐条 stat 核验
- 登记口径：每条给出【02 原索引项 → 旧路径 → 新路径 → 存在性核验结果 → 备注】五元组
- 纪律：零 sha256（完整性按文件数 + 字节量口径）；本件产出全程只读核验，除本件自身及其 worktree 副本外零写入
- "在" = worktree 实测目标路径存在；"缺" = 期望落位但实测不存在；"待" = 待后续阶段落位

## §A §12.1 LoveBrain 产品仓库（Public）——五项（产品仓路径）

> 处理口径（report §6.3）：保持 GitHub URL 指向公开仓库 main 不变，公开迁移后仍有效；与 research/ 无映射关系（产品代码不入 research/ 树）。新路径按任务要求登记为产品仓相对路径。存在性在 worktree 实树核验（worktree 产品区与 D:\LoveBrain closeout 分支同源）。

| # | 02 原索引项 | 旧路径（GitHub main） | 新路径（产品仓相对路径） | 存在性核验 | 备注 |
| --- | --- | --- | --- | --- | --- |
| A1 | LoveBrain README | `bystery/LoveBrain/blob/main/README.md` | `README.md`（产品仓根） | 在（worktree 实测） | 产品 README 与 research/README.md（新撰研究总览）为两件，不互相替代 |
| A2 | Android 构建配置 | `…/blob/main/app/build.gradle.kts` | `app/build.gradle.kts` | 在（worktree 实测） | 产品仓路径 |
| A3 | 产品主生成引擎 | `…/blob/main/app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt` | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt` | 在（worktree 实测） | closeout 分支含 Shadow 接线（见 B5），main 版为公开基线 |
| A4 | 知识库仓储 | `…/blob/main/app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt` | `app/src/main/java/com/lovebrain/app/data/KnowledgeRepository.kt` | 在（worktree 实测） | 产品仓路径 |
| A5 | 知识上下文读取 | `…/blob/main/app/src/main/java/com/lovebrain/app/domain/KnowledgeContextReader.kt` | `app/src/main/java/com/lovebrain/app/domain/KnowledgeContextReader.kt` | 在（worktree 实测） | 产品仓路径 |

## §B §12.2 LoveBrain 收口分支——五项（champion 路径，保留 closeout 分支限定至合并）

> 处理口径：保留 GitHub URL 的 `closeout-20261106` 分支限定，直至该分支合并进 main；分支在 GitHub 侧的可达性属外部断言（arch 路核），本地实测 `D:\LoveBrain` 当前即检出在 `closeout-20261106`，且 `origin/closeout-20261106` 存在于远端分支列表。五项在 worktree 实树（含 closeout 血统的研究迁移分支）全部实测存在。

| # | 02 原索引项 | 旧路径（GitHub closeout-20261106） | 新路径（产品仓相对路径） | 存在性核验 | 备注 |
| --- | --- | --- | --- | --- | --- |
| B1 | ChampionEngine.kt | `…/blob/closeout-20261106/app/src/main/java/com/lovebrain/app/domain/champion/ChampionEngine.kt` | `app/src/main/java/com/lovebrain/app/domain/champion/ChampionEngine.kt` | 在（worktree 实测） | closeout 分支限定至合并；同目录另有 ChampionEvent.kt / EventHistoryAssembler.kt 未入 02 索引 |
| B2 | ChampionPromptAssembler.kt | `…/blob/closeout-20261106/…/champion/ChampionPromptAssembler.kt` | `app/src/main/java/com/lovebrain/app/domain/champion/ChampionPromptAssembler.kt` | 在（worktree 实测） | closeout 分支限定至合并 |
| B3 | ContractGuard.kt | `…/blob/closeout-20261106/…/champion/ContractGuard.kt` | `app/src/main/java/com/lovebrain/app/domain/champion/ContractGuard.kt` | 在（worktree 实测） | closeout 分支限定至合并 |
| B4 | UncertainBlockDetector.kt | `…/blob/closeout-20261106/…/champion/UncertainBlockDetector.kt` | `app/src/main/java/com/lovebrain/app/domain/champion/UncertainBlockDetector.kt` | 在（worktree 实测） | closeout 分支限定至合并 |
| B5 | GenerationEngine.kt Shadow 接线 | `…/blob/closeout-20261106/…/domain/GenerationEngine.kt` | `app/src/main/java/com/lovebrain/app/domain/GenerationEngine.kt` | 在（worktree 实测） | 与 A3 同一文件、不同分支视角；合并后 A3/B5 收敛为同一路径 |

## §C §12.4 Arena（Private）→ research/ 落位路径——十二项（实测存在性）

> 处理口径：私有 URL 一律改为 research/ 相对路径。映射依据 report §6.1 + SOURCE_MAP §3 各行；存在性按 worktree 实树逐条 stat。

| # | 02 原索引项 | 旧路径（GitHub arena，私有） | 新路径（research/ 相对路径） | 存在性核验 | 备注 |
| --- | --- | --- | --- | --- | --- |
| C1 | Arena README | `bystery/arena/blob/main/harness-lovebrain/README.md` | `research/README.md`（新撰研究资产总览取代源 README）；源 README 原件另落 `research/archive/historical_assets/README.md` | 两处均：在（worktree 实测） | 新撰总览登记"迁移落盘中"快照口径；源 README 带历史标注 |
| C2 | Arena PAPER.md | `…/blob/main/harness-lovebrain/PAPER.md` | `research/PAPER.md` | 在（worktree 实测） | 引用时须与 runs/判决书互查，禁写成全面优于原版（SOURCE_MAP §3.K） |
| C3 | Arena AGENTS.md | `…/blob/main/harness-lovebrain/AGENTS.md` | `research/archive/historical_worker_guides/AGENTS-v1.6.md` | 在（worktree 实测） | 唯一允许改名件（renamed-historical）；首行已带 historical worker 标注；worktree 内该件存在未提交改动（8+/7−，CRLF 警告），提交前须收口 |
| C4 | Arena OBJECTIVE.md | `…/blob/main/harness-lovebrain/OBJECTIVE.md` | `research/archive/historical_assets/OBJECTIVE.md` | 在（worktree 实测） | 历史标注 |
| C5 | 评分协议 rubric v0.5 | `…/blob/main/harness-lovebrain/benchmark/rubric.md` | `research/benchmark/rubric.md`（现行版）；历史草案并列 `research/benchmark/rubric_v0_6_draft.md` | 两处均：在（worktree 实测） | rubric.md 实测为 v0.5 宪法文本；v0.6 为草案留档不冒充现行 |
| C6 | 候选版本谱系 | `…/blob/main/harness-lovebrain/VERSION_LINEAGE_V2.json` | `research/candidates/VERSION_LINEAGE_V2.json` | 在（worktree 实测） | 候选身份谱系不得重写（SOURCE_MAP §3.C） |
| C7 | 当前研究运行入口 | `…/blob/main/harness-lovebrain/WORKER_CURRENT_RUNBOOK.json` | 根级"当前入口"不迁移：`research/WORKER_CURRENT_RUNBOOK.json` 实测不存在（如实登记）；登记 6 处历史副本：`research/archive/historical_worker_guides/WORKER_CURRENT_RUNBOOK.json` + `research/refs/experiment_protocol_20261023/` 下 5 处（`deadline_quality_20261028/WORKER_CURRENT_RUNBOOK.json.before`、`direction_check_20261030_0008/ENTRY_BEFORE/`、`final_mainline_review_20261029/ENTRY_BEFORE/`、`global_harness_program_20261030/ENTRY_BEFORE/`、`submission_review_20261029_1510/ENTRY_BEFORE/` 各 WORKER_CURRENT_RUNBOOK.json） | 6 处副本全部：在（逐一实测）；根级：缺（设计使然，非丢失） | 名为 CURRENT 仍须标注"非当前执行指令"；6 件均为 sanitized-copy（2026-11-12 已执行脱敏，分别 23/6/9/7/9/9 处）；索引建议改指历史副本，不虚指"当前入口" |
| C8 | Benchmark V1 Phase 1 | `…/tree/main/harness-lovebrain/refs/benchmark_v1_20261107/phase1` | `research/refs/benchmark_v1_20261107/phase1/` | 在（目录实测；30 文件） | refs 保留历史相对层级 |
| C9 | Benchmark V1 Phase 2 | `…/tree/main/harness-lovebrain/refs/benchmark_v1_20261107/phase2` | `research/refs/benchmark_v1_20261107/phase2/` | 在（目录实测；52 文件） | 同上 |
| C10 | Phase 2 Pilot 报告 | `…/blob/main/…/phase2/PHASE2_PILOT_REPORT.md` | `research/refs/benchmark_v1_20261107/phase2/PHASE2_PILOT_REPORT.md` | 在（worktree 实测） | — |
| C11 | Judge 校准报告 | `…/blob/main/…/phase2/JUDGE_CALIBRATION_REPORT.md` | `research/refs/benchmark_v1_20261107/phase2/JUDGE_CALIBRATION_REPORT.md` | 在（worktree 实测） | 引用时随 R4 快照滞后注记（report §6.1） |
| C12 | 数据集独立审查入口 | `…/blob/main/…/dataset_review_20261107/REVIEW_INDEX.md` | `research/refs/dataset_review_20261107/REVIEW_INDEX.md` | 在（worktree 实测） | — |

## §D §12.3 TeamWorkspace（Private）——七项（不迁移项结论性登记）

> 处理口径（report §6.3 + SOURCE_MAP §9.2）：TeamWorkspace 全部不进公开索引。SOURCE_MAP §9.2 排除登记行：reason_code=`OUT_OF_SCOPE`（私有治理），evidence_ref=`tw-isolation-verification`（十类禁入 10/10 未迁入验证），decision_owner=user；四元组中 source_repo 固定 `TeamWorkspace(不迁移)`；全程只读核验（HEAD `61808d7`）。公开版删除该节条目或改一句文字说明"内部治理仓库不公开"。七项不设 research/ 目标路径，不存在性核验对象；下表为结论性登记。

| # | 02 原索引项 | 旧路径（GitHub TeamWorkspace，私有） | 新路径 | 存在性核验 | 备注 |
| --- | --- | --- | --- | --- | --- |
| D1 | START_HERE.md | `…/blob/main/team/projects/lovebrain/START_HERE.md` | 无（不迁移） | 不适用（OUT_OF_SCOPE） | 内部治理文档，永不入公开树 |
| D2 | ARCHITECTURE.md | `…/blob/main/team/projects/lovebrain/ARCHITECTURE.md` | 无（不迁移） | 不适用 | 同上 |
| D3 | PRODUCT_SPEC.md | `…/blob/main/team/projects/lovebrain/PRODUCT_SPEC.md` | 无（不迁移） | 不适用 | 同上 |
| D4 | ACTIVE.md | `…/blob/main/team/projects/lovebrain/tasks/ACTIVE.md` | 无（不迁移） | 不适用 | 同上 |
| D5 | 1.5.0 收口批次记录 | `…/blob/main/team/projects/lovebrain/batches/LB-20261107-01.md` | 无（不迁移） | 不适用 | 同上 |
| D6 | 1.4.2 发布记录 | `…/blob/main/team/projects/lovebrain/releases/1.4.2.md` | 无（不迁移） | 不适用 | 同上 |
| D7 | 公开/私有资产边界 | `…/blob/main/team/ENGINEERING/PUBLISH_POLICY.md` | 无（不迁移） | 不适用 | 同上 |

## §E 附：report §6.2 建议新增条目（未计入 29 项，实测核验随附）

| 新增条目 | research/ 路径 | 存在性核验 | 备注 |
| --- | --- | --- | --- |
| 判决原文件目录（mechanisms E3/E19） | `research/refs/experiment_protocol_20261023/long_probe_20261030/`（W13–W22/FLJUDGE/LD1/CF1 各 *_VERDICT_*.md）+ `…/global_harness_program_20261030/`（W04-04_SCORECARD、LR1_VERDICT_W1005 等） | 在：long_probe 实测 27 个 VERDICT/SCORECARD 命名文件；global_harness 含 W04-04_FS1_FULL_SCORECARD.json/.md、LR1_VERDICT_W1005_20261031.md 等 | W22 引用 REV2（`W22FULL_VERDICT_REV2_20261104.md` 在，初版 `W22FULL_VERDICT_20261104.md` 亦在，引用以 REV2 为准） |
| 已知故障索引 | `research/refs/KNOWN_FAILURES.md` | 在（worktree 实测） | 失败证据入口，不得删除失败族 |
| 批次级 runs 索引 | `research/experiments/`（runs-registry.csv 逐批次登记） | 待：`research/experiments/` 下实测无 runs-registry.csv（登记口径：工作副本在 asset-audit，289 行含表头；`research/experiments/runs/` 实测 290 个批次目录已落位） | registry 文件落位为遗留待办，归因：属 provenance 件而非资产复制域，尚未执行复制步骤 |
| 评审判决版本链 | `research/evaluation/` | 在：实测 97 文件 = 98 分类件 − 3 排除件（SEALED_KEY ×2 + USER_W_SAMPLE_BINDINGS_V1.json，实测确认不存在）+ audits/hc2 2 件 | 版本链口径 FINAL 79 / SUPERSEDED 6 / INTERIM 5 / BUGGY 3 / PREVIEW 3 / MOCK 1 / DEBUG 1（=98）与 classification CSV 逐类一致；SOURCE_MAP §3.D 对 KNOWLEDGE_ATOM_AUDIT_V1.md / C4_FOUR_ARM_REVIEW_PACK_V1.md/.json 的"HELD_REVIEW 待脱敏"表述滞后：manifest 末列已登记 Q7 处置=脱敏副本（2026-11-12 执行，分别 538 处脱敏点），三件实测在树中为脱敏副本 |
| 评分器实现 | `research/arena/lb_scorer.py`（v0.7 现行权威） | 在（worktree 实测）；前置代际 `research/arena/scorer.py`（v3）亦在 | 两代评分器并存留档，禁入产品 app/ |

## §F 29 项存在性核验统计与归因

| 组 | 项数 | 在 | 缺 | 待 | 说明 |
| --- | --- | --- | --- | --- | --- |
| §A §12.1 产品仓路径 | 5 | 5 | 0 | 0 | worktree 产品区实测；GitHub main 可达性属外部断言，URL 保持不变 |
| §B §12.2 champion（closeout） | 5 | 5 | 0 | 0 | worktree 实测；closeout 分支限定至合并 |
| §C §12.4 research/ 落位 | 12 | 12（含 C7 以 6/6 副本落位） | 0 | 0 | C7 根级"缺"为设计使然（不迁移名为 CURRENT 的根件），以 6 处历史副本代偿，全部实测在 |
| §D §12.3 TW 不迁移 | 7 | — | — | — | 结论性登记，无路径核验对象（OUT_OF_SCOPE，零迁入 10/10） |
| **合计** | **29** | **22** | **0** | **0** | 22 项可核验项全部实测在；0 真实缺项 |

缺项归因：29 项内无真实缺项。唯一"缺"形态为 C7 根级 `research/WORKER_CURRENT_RUNBOOK.json`——归因：设计使然（根级当前运行入口按 SOURCE_MAP §3.I/§9 口径历史化登记，不迁移"名为 CURRENT"的根件；6 处历史副本 sanitized-copy 已执行并实测落位），不构成丢失。附表（§E）另登记 1 项"待"：`research/experiments/` 下 runs-registry.csv 尚未落位（工作副本 289 行在 asset-audit），归因：provenance/索引类文件尚未执行复制步骤，不影响 29 项核验结论。
