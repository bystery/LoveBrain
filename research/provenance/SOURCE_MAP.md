# SOURCE_MAP（终稿）

- 落位目标：`research/provenance/SOURCE_MAP.md`（第1份指导书 §10.2 要求的必备 provenance 文档）。
- 版本：FINAL（2026-11-12）。取代前期草案；前期草案中的"待 Phase C/E 定"条目已按逐路径对账与用户最新决策收口。
- 证据链（同目录配套）：`MIGRATION_MANIFEST.csv`（每文件一行，22+1 字段）、`PRIVACY_REVIEW.md`、`LICENSE_AND_RIGHTS.md`、交付完整性总账（文件数+字节量口径，替代原 SHA256SUMS.txt）、Gold 快照两侧 `SNAPSHOT_README.md` 与 `REMOTE_V1_SNAPSHOT_MANIFEST.json`。内部审查底稿（对账十态 CSV、逐域对账报告、隐私/许可扫描底稿）留存于审计工作区，本文件引用其文件名作为 evidence_ref。
- 纪律声明：本文件只描述迁移事实与登记口径；不承载任何密钥、真实聊天正文、私有治理信息；路径一律为仓库相对路径。
- 完整性纪律（2026-11-12 用户更新）：sha256 及一切文件不变性校验为防御性分析，全线停用；本文件不载任何哈希值或哈希比对结论，完整性口径 = **文件数 + 字节量**；manifest 中 sha 字段保留列位但标注停用（§12）。
- 用户决策基线（2026-11-12，权威记录，优先级高于指导书与仓库现状）：
  1. **B 线 24 篇密封题直接合并公开**：按普通研究资产处理，不加任何防御性标签（EXPOSED/SEALED/已曝光类标记一律不出现），映射表无特殊状态列。
  2. **wave5_selection 40 篇补登 custody 账本**：纯追加登记（已执行，见 §11.3 provenance 记录条目）。
  3. **dataset 含真实隐私 KB 原件永不出站**：公开部分 = AI 学习真实 KB 结构后生成的 synthetic 合成样本（生成方式必须注明），原件本地保留 + 完整性登记（文件数+字节量）。

---

## 1. 来源仓库与分支（§10.2 第 1 项）

| 项 | 值 |
| --- | --- |
| source_repo（研究资产） | `bystery/arena`（私有研究工作区；顶层非 git 仓库，逐文件盘点与对账按文件系统口径执行，远端侧以 git 树为基准） |
| source_branch（对账基准） | `research-p1-sync-20261111`（全仓最新提交，2026-11-11 16:31:26；根层含 `refs/`、`tools/` 无前缀内容，仅此头存在） |
| source_branch（参照） | `main`（2026-11-08，基准头的祖先；13 个 GOLD 文件在两 ref 下内容等价）；`phase3-sync-20261110`（中间历史头，不作为任何域的对账基准） |
| target_repo | `bystery/LoveBrain`（唯一公开面试展示仓库） |
| target_branch（迁移落位） | `research-migration-20261112`（staging worktree，基于 closeout 分支头；禁直写产品 main） |
| TeamWorkspace | `61808d7`（私有治理仓库，全程只读核验，零迁入，见 §9） |
| 源侧规模（实测） | 本地文件系统 318,842 文件 / 2.11 GiB；远端树 harness 前缀文件对象 47,517 + 非前缀 1,893（entries 2,209） |

## 2. 来源 commit SHA（§10.2 第 2 项）

| 头 | SHA | 用途 |
| --- | --- | --- |
| `research-p1-sync-20261111` | `7f0d189eb69b2b97cd4ba594d691f831ab4348ac` | **全部迁移行的 source_commit 基准**（唯一含根层 `refs/`、`tools/` 与远端独有研究批次的头） |
| `main` | `387c55ace6a020b8e73670623824d480f30adea8` | 远端已提交快照轨的内容等价参照（GOLD 13 件在两 ref 下逐件相等） |
| 本地工作树 | `N/A(fs)`（fs-local-only 行） | 本地未提交/远端未跟踪行（如 GOLD_wave5_selection.json、rubric_v0_6_draft.md、round_0_baseline/ 等）填 `N/A(fs)` |

- 远端源行（快照轨 93 件、远端独有恢复件）登记 `source_commit=7f0d189e`（或内容等价的 `main`），取回位置（远端对象位置）在对账 CSV 固化，恢复后以文件数+字节量逐件核对。
- LoveBrain 侧基线：`main = 40c1f12`（v1.4.2 / versionCode 11）、`closeout-20261106 = e2ebde1`；staging 分支基于 closeout。远端 arena 树中的 `example-LoveBrain/` 经逐件内容比对 = LoveBrain main `40c1f12` 的 `app/src` 完整快照（702/702 一致），不作迁移（§9，复现引用 git 提交 40c1f12）。

## 3. 主要目录映射总表（§10.2 第 3 项）

映射规则：远端 `harness-lovebrain/<X>` ↔ 本地 `<X>`（前缀映射，5 域 22,752 件路径级 100% 成立）；远端根层无前缀 `refs/`、`tools/` 同路径并入 harness 内容清单；远端 `harness-lovebrain/<根文件>` ↔ 本地根文件。目标统一前缀 `research/`。

执行状态标记（2026-11-12）：**已完成** = 复制记录与双侧完整性核对（条数+字节量）齐全；**执行中** = 计划行已定、复制进行或待收尾；**待执行** = 计划已定、尚未开始。

### A. benchmark（第 2 类：Benchmark 和评分器）——已完成（快照轨待执行）

| 源路径 | 目标路径 | 对账状态 | 处置 |
| --- | --- | --- | --- |
| `benchmark/`（2,398 文件 / 37.4 MiB） | `research/benchmark/` | 共同 2,375 + 本地独有 23（20 LOCAL_AHEAD + 3 UNTRACKED） | 完整迁移（Git）：2,377 件 VERIFIED；21 件 ACL 锁定不可读，如实登记保持锁定状态 |
| `benchmark/stories_v2/`（2,023 文件 / 34.2 MiB，19 子目录 + GOLD_SCHEMA.md） | 同名随迁 | 层级完整、341 episode 五件套 100% 命中 | 目录名/episode ID/manifest 原样，不合并版本、不静默去重 |
| `benchmark/stories_v2/GOLD_SIDECAR/`（14 文件，v2 重建） | `research/benchmark/stories_v2/GOLD_SIDECAR/` | 13 BOTH_DIFFERENT + 1 本地独有 | **权威轨**，本地字节纯复制（已完成）；远端已提交 13 件快照轨落 `GOLD_SIDECAR_v1_remote_snapshot/`（待执行，§10 D-4） |
| `benchmark/stories_v2/*/exposure.json`（80 件实质差异） | 原位 `<layer>/<EID>/exposure.json` | BOTH_DIFFERENT | 权威轨=本地补录版（已完成）；远端提交版快照轨落 `_remote_v1_snapshots/exposure/…`（待执行，§10） |
| `benchmark/b_line/`（26 文件：MANIFEST v2 + README v1 + 24 episode） | `research/benchmark/b_line/` | 24/24 逐件对应（条数+字节量与封存登记一致） | **普通资产完整迁移（已完成，用户决策一）**：无任何特殊标记；README v1 vs MANIFEST v2 冲突随行登记原样保留 |
| `benchmark/episodes/ releases/ dev_pilot_v02/ anchors/ annotations/ cases/ dev_expansion_v01/ tasks/ media/ c4_screen_20261026/ interaction_env_20261026/ positive_capability_20261025/ _templates/ _acl_locked_episodes(_probe2)/` 及顶层 7 文件 | 同名随迁 | 与远端一致或本地独有未跟踪 | 完整迁移；`_acl_locked_*` 保持锁定清单；`releases/arena_v1/review_loop/mapping_secret.json` 经全文审查为盲测揭盲映射表（非凭据），随迁 |
| `benchmark/benchmark/` 空目录壳 | — | — | 不迁（脚本残留，登记待清理） |

- **stories_v2 目标侧验收账目**：2,023（本地原样）+ 13（GOLD 快照）+ 80（exposure 快照）+ 3（两个 SNAPSHOT_README + REMOTE_V1_SNAPSHOT_MANIFEST.json）= **2,119 文件**；benchmark 域 manifest 行 = 2,398（本地源）+ 93（远端源）= **2,491 行**。
- GOLD_SCHEMA.md 按原字节复制不改（9,359 B），文档滞后事实以注记登记（§11.2）。

### B. dataset（第 1 类：数据集和 Gold）——登记已完成（公开侧合成件执行中）

| 源路径 | 目标路径 | 对账状态 | 处置 |
| --- | --- | --- | --- |
| `dataset/` 原件 84 文件（64 PRIVATE_SENSITIVE 真实聊天导出 + 20 PUBLIC_SANITIZED 结构参考件；718 KiB） | **无公开目标路径（INTERNAL_ONLY）** | 84 件与远端共同（内容比对 73 一致 + 11 本地较新） | 原件 100% 不出站（用户决策三，无悬念）：84 行完整性登记完成（文件数+字节量），target=INTERNAL_ONLY（已完成） |
| （公开侧新建）`research/dataset/manifests/` | 结构摘要：目录树 + 字段模板 + 行数/字节统计 | — | 新建（不含正文） |
| （公开侧新建）`research/dataset/public_fixtures/` | synthetic=true 合成 KB 样本 5 个 | — | AI 原生子代理学习真实 KB 结构后生成；隔离区生成已完成，**强制隐私扫描（零真实姓名/零真实对话片段/零可还原特征）通过后**才进公开树（执行中）；synthetic=true 三层标记（库级 manifest / kb.json 键 / 每文件首行注释）+ 生成方式注明 |

### C. candidates（第 3 类：Baseline 与候选机制）——已完成

| 源路径 | 目标路径 | 对账状态 | 处置 |
| --- | --- | --- | --- |
| `candidates/`（50 目录，526 文件 / 2.5 MiB） | `research/candidates/` | 远端共同 510 + 本地独有 16（`round_0_baseline/` 全部 16 件，远端树未跟踪） | 完整迁移（Git）：527 行（526 + 谱系件）全部 VERIFIED；保留所有候选身份与目录名（champion 9 / historical 32 / neg 4 / ablation 3 / template 1 / reference 1）；C2/C3 parent 缺口原样带走；pyc 运行产物排除 |
| `VERSION_LINEAGE_V2.json` | `research/candidates/VERSION_LINEAGE_V2.json` | 与远端一致 | 完整迁移；候选身份谱系不得重写 |

### D. evaluation（第 5/6 类：scoring·授权账本 / 判决·消融·复现）——已完成

| 源路径 | 目标路径 | 对账状态 | 处置 |
| --- | --- | --- | --- |
| `evaluation/`（98 文件 / 4.5 MiB） | `research/evaluation/` | 内容比对 33 一致 + 65 本地较新（0 例反向） | 完整迁移 94 件 VERIFIED；FINAL 79/SUPERSEDED 6/INTERIM 5/BUGGY 3/PREVIEW 3/MOCK 1/DEBUG 1 版本链不删错误版 |
| `evaluation/SEALED_KEY.json`、`SEALED_KEY_V1_1.json`、`USER_W_SAMPLE_BINDINGS_V1.json` | **不入公开仓** | — | 排除（SEALED 密件 2 + 禁分发件 1），manifest 登记 exclusion（已完成） |
| `KNOWLEDGE_ATOM_AUDIT_V1.md`、`C4_FOUR_ARM_REVIEW_PACK_V1.md/.json` | 脱敏副本或节选+索引登记 | — | HELD_REVIEW：内嵌真实历史消息原文片段，待脱敏形态定稿后单独执行，公开件完整性另记 |
| `_audit_hc2/`（2 文件） | `research/evaluation/audits/hc2/` | 与远端一致 | 完整迁移（正式审计工具） |

### E. runs（第 4 类：已完成实验原始答卷）——执行中

| 源路径 | 目标路径 | 对账状态 | 处置 |
| --- | --- | --- | --- |
| `runs/`（50,198 文件 / 242.6 MiB，290 批次） | `research/experiments/runs/` | 远端共同 23,805；本地独有新路径 13,254 + 本地未跟踪 2；内容重复组 13,137 | 全量迁移（执行中）：按 run 目录为单位；**不得把部分 run 写成完整**；体量头部批次 selection_v1（10,090 文件 / 70.3 MB）、pilot2_wave4_20261108（25.4 MB）、pilot2_all（21.7 MB）、holdout_v1（21.3 MB）；13,137 个内容重复件（相同内容不同路径）以去重引用登记，原件不删 |
| `runs/` 授权账本（ledger.json 及历史自校验伴生件） | 随各 run 目录 | — | 与授权与隔离说明一并随迁（禁漏） |

### F. isol_runs（第 4/5 类）——执行中

| 源路径 | 目标路径 | 对账状态 | 处置 |
| --- | --- | --- | --- |
| `isol_runs/`（375 文件 / 3.6 MiB） | `research/experiments/isolated_runs/` | 远端共同 360 + 本地独有 15；远端独有 62 | 审查后迁移（执行中）：**授权与隔离说明必须随迁（禁漏）**；2 个 evil_link junction 不穿透不复制仅登记；远端独有 62 件为 junction 在远端提交时解析出的授权/发布材料（AUTHORIZATION_*、APPROVAL_REQUEST_*、release.json），属"授权与隔离说明"组成部分，随本域审查定性恢复（§10） |

### G. logs（第 4/5 类，大文件域）——登记完成（打包待执行）

| 源路径 | 目标路径 | 对账状态 | 处置 |
| --- | --- | --- | --- |
| `logs/`（229,514 文件 / 1.47 GiB，本地独有未上传） | `research/experiments/raw_logs/`（索引 + 脱敏副本）+ Release/LFS 分卷原件 | 远端为 0 件（既定上传口径，非丢失） | 不逐文件复制：目录级登记 + `logs/INDEX.json`（88 KB）为 raw_logs 索引基底（登记完成）；原件按 V1–V7 十卷 zip 打包走 Release/LFS（Phase G 待执行，§14 门禁） |

### H. refs（第 5/6 类）——执行中

| 源路径 | 目标路径 | 对账状态 | 处置 |
| --- | --- | --- | --- |
| `refs/`（19,662 文件 / 193.9 MiB） | `research/refs/`（保留历史相对层级） | 远端共同 19,655（内容比对 14,320 一致 + 5,335 本地较新）+ BOTH_DIFFERENT 12 + 远端独有 30 | 合规部分全迁（执行中）；>10MB 单件注记 LFS/Release 评审；不得因目录大而丢弃（`experiment_protocol_20261023/` 19,142 件占 95%） |
| 远端独有 30 件：`refs/research_harness_20261110/phase1/experiments/2026-11-11_*`（7 批 run_records/run_summary/smoke csv）+ `overnight/FIRST_PROTOTYPE_RESULTS.md` + `phase1/harness/LIVE_LOOP_LOG.md` | 恢复至 `research/refs/` 对应路径 | REMOTE_ONLY（唯一明确反向项） | 远端→本地恢复（取回位置在对账 CSV 留存，约 0.5 MiB）（待执行） |
| `refs/KNOWN_FAILURES.md` | `research/refs/KNOWN_FAILURES.md` | 与远端一致 | 直迁；已知故障索引，不得删除失败族 |
| `refs/experiment_protocol_20261023/global_harness_program_20261030/STORY_CUSTODY_LEDGER.json` | 随 refs 域原样随迁 | 本地较新（11-10 补录 + 11-12 补登） | 数据集 custody 口径权威账本（§11.3）；远端旧版在远端历史可取回 |
| 判决书/消融/失败分析（原件在 refs/） | `research/experiments/decisions|ablations|failures/` 设薄索引 | — | 薄索引，不为美观改路径 |

### I. archive 与特殊目录（第 6/7 类）——执行中

| 源路径 | 目标路径 | 对账状态 | 处置 |
| --- | --- | --- | --- |
| `archive/`（4,397 文件 / 124.8 MiB，本地独有 4,357） | `research/archive/historical_assets/` | 远端仅 22 件共同 | 每个归档必须有索引；zip 族可走 Release（计划 115 行：60 复制 / 46 审查 / 9 排除·登记·改名） |
| `archive/20261107_root_cleanup/delivery_zips_root/`（14 个 zip） | 待逐包核对放行 | — | 逐包核对 MANIFEST 条目放行前不复制（REVIEW_REQUIRED 门禁） |
| `archive/20261107_root_cleanup/acl_locked_snapshots_20261021/` | 零触碰 | — | ACL 残骸：零触碰零深读零移动，仅目录级登记 |
| `integration_preview/`（6 文件） | `research/archive/integration_preview_20261025/` | 与远端一致 | 完整迁移；标注 2026-10-25 历史移植原型，不冒充当前生产接线 |
| `AGENTS.md` | `research/archive/historical_worker_guides/AGENTS-v1.6.md` | 本地较新 | 迁移 + 历史标注（唯一允许改名件：renamed-historical） |
| `WORKER_*` 5 件 + `指导书.md`（V14 收口交接书） | `research/archive/historical_worker_guides/` | 本地较新 | 脱敏后迁移，标注非当前执行指令 |
| `OBJECTIVE.md` / `RUN_STATE.json` / `TASK_BOARD.md` / arena `README.md` / `_hb10_verify.py` | `research/archive/historical_assets/` | 与远端一致或本地独有 | 迁移 + 历史标注 |
| `API_COMPATIBILITY_REPORT.md` / `provider_check.json`（本地独有 2 件） | 前者 `research/archive/historical_assets/`；后者审查后同规则或内部保留 | LOCAL_AHEAD | 含内部 endpoint 与模型名（无密钥材料，已核实）；公开前脱敏 endpoint |
| `W13/W14` 批次目录、`analysis/`（7 文件 / 4.2 MiB 含 DATASET_FINAL_AUDIT_20261111 三件套） | 随 manifests/ 或 refs/historical | 本地独有 | 有唯一统计与实验材料，随迁（analysis 三件套为数据集口径收敛输入件，§11.1） |
| `.coordination/`（远端 2 件：heartbeat/claims） | **无公开目标** | REMOTE_ONLY | 排除：内部心跳/协调不是研究证据 |

### J. arena 与 tools（第 2/8 类：评分器 / 可复用运行脚本）——执行中

| 源路径 | 目标路径 | 对账状态 | 处置 |
| --- | --- | --- | --- |
| `arena/`（29 文件 / 362.5 KiB） | `research/arena/` | 与远端一致 | 完整迁移：平铺 import 布局整体平移；两代评分器并存（scorer.py v3 + lb_scorer.py v0.7）；禁入产品 app/ |
| `tools/`（507 文件 / 4.5 MiB） | `research/tools/`（+ tests 67 件单独归 `research/tools/tests/`） | 远端共同 459 + 本地独有 16 + 未跟踪 10 + 重复 22 | 确定性核心 ~30 + 测试 67 + `benchmark_v1/` 整目录随迁；一次性探针 ~150+ 归 historical_tools；跨目录依赖（engine_adapter / reference_chain）落位后 import 可解析性待实测（§14 T3） |
| 远端根层无前缀 `tools/benchmark_v1/`（16 件） | 并入同路径清单 | 仅基准头存在 | 并入 harness 内容登记；1 个 .pyc 不复制 |

### K. 根文件与研究论文（第 7 类）——执行中

| 源路径 | 目标路径 | 对账状态 | 处置 |
| --- | --- | --- | --- |
| `PAPER.md` | `research/PAPER.md` | 与远端一致 | 完整迁移；禁：不得把候选父子提升写成全面优于原版 |
| 根文件其余 16 件（README/RUN_STATE/TASK_BOARD/VERSION_LINEAGE_V2/.gitignore 等） | 按 §3.I 与 historical 规则 | 15 件远端共同（内容比对 9 一致 + 6 本地较新）+ 2 件本地独有 | 见 §3.I 行项 |
| 远端根 README（斗兽场全集说明） | 不迁，登记 | 与本地 README 同源 | 其"未上传内容说明"已陈旧（runs/refs 实际已在远端），对账以实际树为准 |

### L. 明确不迁移（exclusion）——见 §9

## 4. 每类资产的用途（§10.2 第 4 项）

| # | 资产类（总章程阶段 2 八类） | 域 | 用途说明 |
| --- | --- | --- | --- |
| 1 | 数据集和 Gold | B + A(GOLD_SIDECAR) | 多角色对话研究数据集（结构摘要 + 合成样本公开，原件内部保留）；Gold 侧车为 episode 断言权威数据，v2 重建为正式评分输入 |
| 2 | Benchmark 和评分器 | A + J(arena) | 341 episode 题库（stage/wave/final/anchors 层级）+ b_line 24 篇 + 两代评分器 |
| 3 | Baseline 与候选机制 | C | 50 个候选目录 + 身份谱系，机制演进证据链 |
| 4 | 已完成实验原始答卷 | E + F + G | 290 个 run 批次 + 隔离测试 run + 原始逐调用日志（分卷原件） |
| 5 | scoring·calls·manifest·授权账本 | D + E/G 内伴生件 | 逐轮 scoring.json / calls.json（敏感面过扫描）/ manifest / 授权账本自校验对 |
| 6 | 判决书·消融·失败分析和复现材料 | H + D | refs 判决材料 + KNOWN_FAILURES + evaluation 版本链 |
| 7 | 研究论文和项目说明 | K | PAPER.md + 历史治理文档 + 收口交接书 |
| 8 | 可复用的运行脚本 | J(tools) | 确定性核心工具 + 测试 + benchmark_v1 构建器族 |

## 5. 完整迁移（Git 直迁，§10.2 第 5 项）

benchmark（去空壳）、candidates（去 pyc）、evaluation 常规件、arena、tools 常规件、refs 常规件、archive 索引与文档件、runs 全量（本地权威）、isol_runs、根研究文档。以上 target 载体 = `GIT`；复制后逐件以条数+字节量核对一致才标 VERIFIED（哈希比对已按用户纪律停用，§12）。

## 6. 只迁移脱敏副本 / 合成替代（§10.2 第 6 项）

| 对象 | 形态 | transformation |
| --- | --- | --- |
| dataset 公开部分 | synthetic=true 合成 KB 样本 5 个（隔离区生成 → 强制隐私扫描 → 复制进公开树）；**不发布脱敏原件** | `synthetic-replacement`（注明生成方式：AI 原生子代理学习真实 KB 结构后生成） |
| dataset 结构摘要 | 目录树 + 字段模板 + 统计，不含正文 | `structure-summary` |
| evaluation 敏感 3 件（KNOWLEDGE_ATOM_AUDIT / C4_REVIEW_PACK×2） | 脱敏副本或节选+索引登记（形态定稿中） | `sanitized-copy` / `INDEX_ONLY`（保留"机器判定 fuzzy 未经人工复核不得进对外结论"限定语） |
| logs 公开侧 | raw_logs 索引（复用 `logs/INDEX.json`）+ 脱敏副本按敏感面逐类处理 | `sanitized-copy` / `INDEX_ONLY` |
| WORKER_*/AGENTS/指导书 历史治理件 | 脱敏 + 历史标注 | `sanitized-copy`（AGENTS 另 `renamed-historical`） |

## 7. Release/LFS 载体（§10.2 第 7 项）

| 对象 | 体量 | 载体 | 门禁 |
| --- | --- | --- | --- |
| logs 原件 V1–V7（V4/V5/V6 各分 a/b，共 10 zip） | 毛字节 1,627,988,729 B ≈ 1.52 GiB | Release/LFS 分卷 | V1–V6 gate=public；**V7 gate=pending_user_release（夜间不上传，晨间用户放行；必带 --sanitize-manifest）**；上传门禁固化于 ASSET_BUNDLE_INDEX.json 的 upload_policy |
| runs 大批次（逐批定性） | 242.6 MiB 中 >10MB 件 | 逐批 Git/LFS/Release 评审 | 头部批次见 §3.E |
| refs 大 JSONL/zip、archive delivery_zips（逐包核对后） | 单件 >10MB | LFS/Release | 放行前逐包核对 |

## 8. 仍在内部（不出站，§10.2 第 8 项）

dataset 84 件原件、SEALED_KEY ×2、USER_W_SAMPLE_BINDINGS_V1.json、`.coordination/`、TeamWorkspace 全部、guanjia 系（本地运行产物 75 件 + 远端 framework-guanjia 包）、运行产物（__pycache__ / tmp / .pytest_cache / .zcode / dist / backups）、ACL 残骸、LoveBrain 产品区（app/、gradle/、images/、scripts/、keystore*、local.properties、_archive、_temp、dist、*.log，零触碰）。B 线 24 篇按用户决策一为普通资产，**不在**内部保留之列。

## 9. 排除清单与排除原因（§10.2 第 9 项）

### 9.1 四元组登记规范

每条 exclusion 必须四元组齐全：`source_path + reason_code + evidence_ref + decision_owner`。

reason_code 枚举：`PRIVACY_REAL_CHAT` / `SEALED` / `NO_AUTHORIZATION`(RIGHTS_REVIEW) / `INTERNAL_COORDINATION` / `OUT_OF_SCOPE` / `BUILD_ARTIFACT` / `PRODUCT_PROTECTION` / `VENDORED_UPSTREAM`(有上游、仅登记引用)。

evidence_ref 指向对应审查底稿；RIGHTS_REVIEW 类必须注明 decision_owner=user。TeamWorkspace 相关行 source_repo 固定 `TeamWorkspace(不迁移)`。

### 9.2 排除登记表

| 源 | reason_code | evidence_ref | decision_owner |
| --- | --- | --- | --- |
| `dataset/` 84 件原件（正文永不出站） | PRIVACY_REAL_CHAT | dataset-privacy-summary / dataset-privacy-review.csv | user（决策三） |
| `evaluation/SEALED_KEY.json`、`SEALED_KEY_V1_1.json` | SEALED | evaluation 分类底稿 | 维护者 |
| `evaluation/USER_W_SAMPLE_BINDINGS_V1.json` | SEALED（文件头明示禁随公开展示包分发） | evaluation 分类底稿 | 维护者 |
| TeamWorkspace 全部（AGENTS/TEAM_RULES/台账/签名材料/凭据等十类禁入，HEAD `61808d7`） | OUT_OF_SCOPE（私有治理） | tw-isolation-verification（十类禁入 10/10 未迁入验证） | user |
| `example-LoveBrain/`（= LoveBrain main 40c1f12 app/src 快照，702/702 一致，无独有内容） | VENDORED_UPSTREAM | remote-nested-copies §2 | 维护者 |
| `example-guanjia/`（独立已发布产品 guanjia 副本，MIT，上游 github.com/bystery/guanjia；`.tmp.driveupload` 483 件 Drive 残渣必剔） | VENDORED_UPSTREAM | remote-nested-copies §3 | 维护者 |
| `framework-guanjia/`（斗兽场本体，私有研究基础设施，无 LICENSE/README，溯源用户自产） | NO_AUTHORIZATION（整仓不复制，仅登记；若复现确需某机制文件按文件级清单单独评估） | license-review R8 | user |
| `.coordination/`（heartbeat/claims） | INTERNAL_COORDINATION | 对账十态 CSV | 维护者 |
| `__pycache__` / `*.pyc` / `*.pyo` / `tmp/` / `.pytest_cache` / `.zcode` | BUILD_ARTIFACT | 盘点底稿 | 维护者 |
| `dist/`（10,928 件 / 136.2 MiB 历史快照）、`backups/`（16 件） | BUILD_ARTIFACT | special-dirs 分类底稿 | 维护者 |
| `archive/…/acl_locked_snapshots_20261021/`（ACL 残骸） | BUILD_ARTIFACT（零触碰零深读零移动） | special-dirs 分类底稿 | 维护者 |
| LoveBrain 产品区（app/、gradle/、images/、scripts/、keystore*、local.properties、_archive、_temp、dist、*.log） | PRODUCT_PROTECTION | 第1份指导书 §9 产品保护清单 | user |
| 本地 `guanjia/`（75 件运行产物：runs/台账等） | BUILD_ARTIFACT（管家运行产物，非研究证据） | 对账十态 CSV | 维护者 |
| 远端根层 `tools/benchmark_v1/__pycache__/mechanism.cpython-312.pyc` | BUILD_ARTIFACT | remote-nested-copies §5.3 | 维护者 |

## 10. 远端与本地差异的最终解释（§10.2 第 10 项）

### 10.1 十态对账总账（2026-11-12 01:21，全量 318,842 本地文件 + 远端 47,517 harness 文件对象）

| 状态 | 数量 | 说明 |
| --- | ---: | --- |
| REMOTE_AND_LOCAL_SAME | 47,313 | 路径级一致（未覆盖完整性登记的文件按路径+尺寸判定；内容级复核结论见 §10.3） |
| LOCAL_AHEAD | 17,740 | 本地独有新路径（runs 13,254 / archive 4,357 / guanjia 54 / benchmark 20 / tools 16 / isol_runs 15 / backups 12 / analysis 7 / refs 3 / 根 2） |
| REMOTE_AHEAD | 0 | 本口径恒 0；全域核验 0 例"本地较旧"，远端为本地某时刻一致快照 |
| BOTH_DIFFERENT | 110 | benchmark 93（全部为 §10.3 实质差异）+ refs 12 + tools 5；全部"本地更大/本地较新"单向 |
| LOCAL_UNTRACKED | 19 | 本地独有但父目录已被远端跟踪（GOLD_wave5_selection.json 等） |
| LOCAL_IGNORED | 240,465 | logs / dist / 运行产物等忽略口径（logs 229,514 + dist 10,928） |
| DUPLICATE_HASH | 13,195 | 内容相同路径不同（mock/smoke 族重复件；8,708 重复组，原件不删、索引去重引用） |
| REMOTE_ONLY | 94 | refs 30（恢复清单）+ isol_runs 62（evil_link 提交时解析出的授权/发布材料）+ .coordination 2（排除） |
| MISSING_BOTH / UNVERIFIABLE | 0 / 0 | — |
| （补充）远端非前缀路径 | 1,893 件 / 2,209 entries | example 三副本 + 根层 refs/tools + 根 README，不参与映射（§3.J、§9） |

### 10.2 D-1 ~ D-7 逐项收敛

| # | 差异 | 最终解释（收口） |
| --- | --- | --- |
| D-1 | logs 体量：指导书 ≈2.1 GB vs 实测 1.47 GiB | 指导书数字为编写时毛口径粗快照（含 lock/pyc/__pycache__ 与账本伴生件）；实测净口径 229,514 件 / 1.47 GiB，打包毛字节 1,627,988,729 B ≈ 1.52 GiB（十卷全量口径与 survey 一致）。远端 logs 为 0 件，属"本地独有不上传"既定口径而非丢失。**无删减异常，归因闭合。** |
| D-2 | runs 增长 ~4 倍：本地 242.6 MiB vs 远端 141.9 MiB vs 指导书 ≈65 MB | 远端 HEAD 定格于 11-08 波次；11-10/11-11 新批次（selection_v1 70.3 MB、pilot2_wave4 25.4 MB、pilot2_all 21.7 MB、holdout_v1 21.3 MB、rp1_* 11-11 族）只存在于本地（LOCAL_AHEAD 13,254）。另 13,137 件 DUPLICATE_HASH 为 mock/smoke 族内容重复件（如 `_ph3_mock_smoke` scoring.json 重复 2,915 份），原件不删、迁移索引去重引用。**runs 域以本地为权威。** |
| D-3 | candidates 文件数：本地 526 vs 远端 510 | 526 = 远端共同 510 + 本地独有 16（`round_0_baseline/` 全部 16 件，远端树未跟踪）；85 个 pyc 运行产物不计文件账。目标侧 527 行（526 + VERSION_LINEAGE_V2.json）全部 VERIFIED。**无内容缺口。** |
| D-4 | GOLD 文件数：本地 14 vs 指导书"13" | 三代口径（见 §11.2 表）：① v1.0 文档口径（221/3,102/6,603，仅 GOLD_SCHEMA.md 文本）；② 远端已提交快照 13 文件（2026-11-08，totals 259/3,590/7,953）；③ 本地 v2 重建 14 文件（2026-11-10，totals 299/4,082/9,646，新增 GOLD_wave5_selection.json）。双轨处置：权威轨=③（已落盘），快照轨=②（待恢复）。**"13 vs 14"不是矛盾，是三代演进；指导书 §13.2 该项按双轨口径验收。** |
| D-5 | evaluation 字节差：本地 +123,325 B | 内容比对结论：98 件中 33 与远端一致、65 本地较新、0 例反向；差额即 65 个本地更新文件的单向字节增量（活迭代域本性，非行尾差异主因）。本地版为权威，94 件已复制 VERIFIED。**收口。** |
| D-6 | 三分支分歧并存 | 对账基准 = `research-p1-sync-20261111`（7f0d189e，全仓最新）；`main`（387c55ac）为其祖先且 13 个 GOLD 文件在两 ref 下内容等价；根层无前缀 `refs/`、`tools/` 25 件仅存在于基准头（同步脚本前缀规则漏网，并入 harness 内容清单登记）。十态 BOTH_DIFFERENT 110 行中 refs 12 + tools 5 件 = 本地较新的真实新增内容（research_harness 批次与 tools 更新），权威=本地版，远端旧版取回位置在对账 CSV 留存。**基准唯一化收口。** |
| D-7 | 本地独有根件 3 件 | `指导书.md`：远端已跟踪（本地较新版），V14 收口交接书，按 WORKER_* 同规则脱敏归 `historical_worker_guides/指导书-V14-20261022.md`；`API_COMPATIBILITY_REPORT.md` + `provider_check.json`：本地独有（LOCAL_AHEAD 2 件），provider_check_v1 检查产物，含内部 endpoint 与模型名、无密钥材料（已核实），前者脱敏后归 `historical_assets/`，后者 TRANSIENT_REVIEW 审查后同规则处理或内部保留。**逐件定性收口。** |

### 10.3 93 个实质差异文件的处置（含 1,427 个 CRLF 伪差异消解）

benchmark 域共同 2,375 件内容比对结论：855 件与远端一致 + 1,427 件仅 CRLF↔LF 行尾差异（内容等价）+ 93 件实质差异。

| 组 | 数量 | 对账状态 | 权威轨（正式结果） | 快照轨（历史存档） | version_identity |
| --- | --- | --- | --- | --- | --- |
| GOLD_*.json | 13 | BOTH_DIFFERENT | 本地 v2 重建版 → `GOLD_SIDECAR/`（已落盘） | 远端已提交版 → `GOLD_SIDECAR_v1_remote_snapshot/`（待恢复） | `gold-v2-rebuild-20261110(authoritative) vs remote-committed-20261108(snapshot)` |
| exposure.json | 80 | BOTH_DIFFERENT | 本地补录版 → 原位 `<layer>/<EID>/exposure.json`（已落盘） | 远端已提交版 → `_remote_v1_snapshots/exposure/<layer>/<EID>/exposure.json`（待恢复） | `exposure-backfill-20261109~10(authoritative) vs remote-committed-20261108(snapshot)` |
| GOLD_wave5_selection.json | 1 | LOCAL_UNTRACKED | 随权威轨原样公开，**不加任何特殊标记（决策一）** | 无（远端从未跟踪） | `gold-v2-rebuild-20261110(new-in-v2)` |
| GOLD_SCHEMA.md | 1 | 与远端一致 | 原样复制（9,359 B 原字节不变） | 无需 | `schema-doc-v1.0(2026-11-03) lags data`（三处注记） |

- 权威轨选择依据：本地版是 2026-11-10"账本补录 + Gold v2 重建"批次的实际产物，远端版是该批次之前的历史定格（11-08 提交波次）；两版都保留，正式结果 = 本地版。快照恢复自远端镜像按对账 CSV 固化的取回位置执行，恢复后以文件数+字节量逐件核对（93 件与远端尺寸逐一相等）。
- 1,427 个 CRLF 伪差异：单轨本地字节纯复制（两侧文件数与字节量逐件一致），notes 记 `crlf-only-pseudo-diff`，**不建快照、不算版本双轨**；远端旧版已在远端历史与镜像中存在。
- 其余域 BOTH_DIFFERENT（refs 12 + tools 5）：真实新增内容，权威=本地版随迁，远端旧版取回位置留存于对账 CSV。

## 11. 数据集版本与候选谱系入口（§10.2 第 11 项）

### 11.1 数据集版本与口径

- 权威审计三件套：`analysis/DATASET_FINAL_AUDIT_20261111.md / .json / _coverage.csv`（本地独有，随 archive/historical 迁移）。
- 曝光/封存口径入口：`refs/experiment_protocol_20261023/global_harness_program_20261030/STORY_CUSTODY_LEDGER.json`（218→379 事件）+ `refs/benchmark_v1_20261107/phase1~3` 的 SPLIT_AND_CUSTODY_PLAN / CUSTODY_SPLIT_MANIFEST / CUSTODY_EXPOSURE_RECONCILIATION。
- 统计口径冲突（319/317/299/248 vs PAPER 208/3102/6603）在 README 终稿按实体定义重统计收敛（§14 T5）；本文件只登记入口不改数。

### 11.2 Gold 三代口径与文档滞后

| 口径 | 实体位置 | 生成时间 | totals（episodes/turns/atoms） | 文件数 |
| --- | --- | --- | --- | --- |
| ① v1.0 文档口径 | 仅 GOLD_SCHEMA.md 文本 | 2026-11-03 前后 | 221 / 3,102 / 6,603 | 无文件实体留存 |
| ② 远端已提交快照 | `GOLD_SIDECAR_v1_remote_snapshot/`（自镜像按对账 CSV 固化的远端取回位置恢复） | 2026-11-08T06:00:43 | 259 / 3,590 / 7,953 | 13 |
| ③ 本地 v2 重建（权威） | `GOLD_SIDECAR/` | 2026-11-10T04:25:15 | 299 / 4,082 / 9,646 | 14（含 GOLD_wave5_selection.json） |

GOLD_SCHEMA.md 原样保留不修改（滞后本身是历史证据）；滞后事实登记于：本节表格、`GOLD_SIDECAR_v1_remote_snapshot/SNAPSHOT_README.md`、manifest 的 GOLD_SCHEMA.md 行（`version_identity=schema-doc-v1.0 lags data`）。若需 v2 schema 文档，新写 `GOLD_SCHEMA_v2.md`，不在迁移中改写旧文档。

### 11.3 provenance 记录条目：STORY_CUSTODY_LEDGER 补登（用户决策二，已执行）

> **账本补登说明**
> - **日期**：2026-11-12。
> - **原因**：登记盲区——wave5_selection 40 篇（E339–E378）的消费与账本最后一次写入在同一会话完成，但漏登账本；11-10 的 exposure.json 批量落盘仅为冻结期限登记（`frozen_at=2026-11-10T23:59:00Z`），不含消费记录，四臂消费证据在 runs 侧逐集 manifest（`run_id=bm1_live_{ARM}_{EID}`）。
> - **范围**：40 篇 story 记录（248→288）+ 160 条消费事件 + 1 条登记事件（218→379 事件）。
> - **操作**：纯追加，不改既有 248 条记录一个字节；执行前先备份账本原件（备份件留存审计工作区），改后做 JSON 有效性校验 + 差异核对（既有记录字节级未变）。
> - **登记内容**：来源标注 wave5_selection；lifecycle=EXPOSED（按事实：已被 selection_v1 四臂于 2026-11-10 全量真实消费，批名 `selection_v1_direct_20261110` / `selection_v1_cot_20261110` / `selection_v1_legacy_20261110` / `selection_v1_current_20261110`，每臂 40/40 complete=true，160/160 消费记录与 40 篇题库逐一对应）。
> - **效力**：与 Gold 文件、exposure.json 互不改写；账本中 B 线的 SEALED 历史记录原样保留、不修改、不强调（决策一执行口径第 4 条）。

### 11.4 候选谱系

- 入口：`research/candidates/VERSION_LINEAGE_V2.json`（12 节点；7 物理目录闭包磁盘在位；43 目录晚于 2026-10-27 快照未入谱——逐目录 `params.json` 的 parent 字段即局部谱系）。
- 身份口径：以目录构成与字节量为基准，mtime 仅提示、不作版本先后证据；F3 manifest 的 version_identity 列已按 lineage 状态逐件登记。

## 12. 如何复核某个文件（§10.2 第 12 项）

1. **行级**：`MIGRATION_MANIFEST.csv` 按 `source_path` 或 `asset_id` 查行 → 以 source_size_bytes / target_size_bytes 与两侧实际文件字节量核对，并纳入分域文件数总账核对（sha256 复算已按用户纪律停用，完整性口径=文件数+字节量；sanitized-copy / synthetic-replacement 件的完整性另记，不与源侧口径混用）。
2. **域级**：本文件对应资产域小节 → 核对 classification / privacy_status / exposure_status / transformation 是否与实际落位一致（GIT 常驻、LFS 指针、Release 附件、INTERNAL_ONLY）。
3. **排除级**：§9 排除登记四元组 → 沿 evidence_ref 回到审查底稿原始证据。
4. **公开侧完整性总账**：按文件数+字节量分域分卷登记（原指导书 §10.3 的 SHA256SUMS.txt 按 2026-11-12 用户纪律停用，以本总账替代；Release 大包与压缩包内部件各自保留独立清单；每次资产更新后重新生成）。

### MIGRATION_MANIFEST.csv 22 字段定义（每文件一行）

| # | 字段 | 口径 |
| --- | --- | --- |
| 1 | asset_id | `<域前缀>-<5 位序号>`（BM/DSC/CND/EVL/RUN/ISO/LOG/REF/ARC/ARN/TOL/DOC/PRV）；一经分配不重排；排除件也分配 |
| 2 | source_repo | `bystery/arena`；TeamWorkspace 相关行固定 `TeamWorkspace(不迁移)` |
| 3 | source_branch | `main` / `research-p1-sync-20261111` / `fs-local-only` |
| 4 | source_commit | 40 位源 SHA；fs-local-only 行填 `N/A(fs)` |
| 5 | source_path | arena 内相对路径（保留原名） |
| 6 | source_sha256 | **已按用户纪律停用（2026-11-12）**：保留列位、不填值；完整性口径=文件数+字节量（Release 分卷另留独立清单条目） |
| 7 | source_size_bytes | 源字节数 |
| 8 | source_git_status | `remote-local-match` / `remote-only` / `local-only` / `remote-local-diff`（以逐路径对账口径代替 git status） |
| 9 | target_repo | `bystery/LoveBrain` |
| 10 | target_branch | `research-migration-20261112` |
| 11 | target_path | `research/…` 目标路径；不出站件填 `INTERNAL_ONLY` |
| 12 | target_sha256 | **已按用户纪律停用（2026-11-12）**：保留列位、不填值；复制完整性=条数+字节量逐件核对（sanitized/synthetic 件另注） |
| 13 | target_size_bytes | 复制后实测 |
| 14 | classification | 资产域类别（benchmark/dataset/candidates/evaluation/runs/isol_runs/logs/refs/archive/arena/tools/root-doc/provenance） |
| 15 | privacy_status | `PUBLIC` / `SANITIZED` / `PRIVATE_SENSITIVE` / `SEALED` / `RIGHTS_REVIEW` / `INTERNAL_COORDINATION` |
| 16 | license_status | `original-owned` / `needs-review` / `third-party-restricted`（对齐 LICENSE_AND_RIGHTS.md） |
| 17 | exposure_status | `GIT` / `LFS` / `RELEASE` / `INDEX_ONLY` / `INTERNAL_ONLY` |
| 18 | version_identity | 版本身份口径（如 `gold-v2-rebuild-20261110(authoritative)` / `crlf-only-pseudo-diff` / `schema-doc-v1.0 lags data` / `lineage-v2:…`） |
| 19 | duplicate_of | 重复/替代指向权威原件 asset_id |
| 20 | transformation | `none` / `sanitized-copy` / `structure-summary` / `synthetic-replacement` / `volumed-release` / `renamed-historical` |
| 21 | verification_status | `PENDING` / `VERIFIED`（条数+字节量核对通过；原 HASH_* 比对枚举随 sha 字段一并停用） / `REVIEW_REQUIRED` / `EXCLUDED_CONFIRMED` |
| 22 | notes | 登记不一致、禁改项、evidence_ref |

## 13. 数字对账表（源侧实测 → 目标侧 Phase F 执行后实测 → 差额归因）

| 域 | 源侧（本地工作树实测） | 远端（基准头文件对象） | 目标侧（Phase F 后实测） | 差额归因 |
| --- | --- | --- | --- | --- |
| A benchmark | 2,398 / 37.4 MiB | 2,375 / 33.8 MiB | **复制 2,377（已完成）** + 21 ACL 锁定登记；快照轨 +93 + 3 说明件待执行 → stories_v2 账目 2,119 / 域 manifest 行 2,491 | −21 = ACL 锁定不可读（保持锁定登记）；+93+3 = 快照轨与说明件追加 |
| A-1 stories_v2 | 2,023（341 episode 配套 100%） | 2,022 | 2,022 已落位 + wave5 未跟踪 1 件 | +1 = GOLD_wave5_selection.json（local-only） |
| A-2 b_line | 26 | 26 | 26 已落位（无特殊标记） | 0 |
| B dataset | 84 / 718 KiB | 84 | 复制 0（84 行 INTERNAL_ONLY 完整性登记已完成）；公开侧 synthetic 5 KB 隔离区待扫描 | 84 全部不出站（决策三）；公开侧为 Phase E 新写件 |
| C candidates | 526 + 谱系 1 = 527 | 510 | **527 VERIFIED（已完成）** | +16 = round_0_baseline（本地独有）+1 = 谱系件；pyc 不计账 |
| D evaluation | 98 + hc2 2 = 100 | 98 + 2 | **94 VERIFIED（已完成）**；3 EXCLUDED + 3 HELD_REVIEW | −3 密件、−3 待脱敏形态定稿 |
| E runs | 50,198 / 242.6 MiB（290 批） | 23,805 / 141.9 MiB | 执行中（0 已落位） | +26,393 = 11-10/11-11 新批次（LOCAL_AHEAD 13,254 + 重复组 13,137 + 未跟踪 2） |
| F isol_runs | 375（+2 junction 登记） | 422 | 执行中 | 远端独有 62 = evil_link 提交时解析的授权/发布材料（随域审查恢复） |
| G logs | 229,514 / 1.47 GiB | 0 | 目录级登记完成；V1–V7 十 zip 打包待 Phase G | 本地独有不上传口径；原件走分卷 |
| H refs | 19,662 / 193.9 MiB | 19,685 / 192.8 MiB | 执行中（已落位 8,618） | 远端独有 30 = 11-11 research_harness 研究批次（恢复清单） |
| I archive/特殊目录 | 4,397 + analysis 7 + integration_preview 6 + W13/W14 2 + 根件族 | 22 + 6 + 2 | 执行中（计划 115 行：60 复制 / 46 审查 / 9 排除·登记·改名） | archive 本地独有 4,357（delivery_zips 14 包逐包核对门禁） |
| J arena + tools | 29 + 507 | 29 + 459(+16 根层无前缀) | 执行中（计划 537 行） | tools 本地独有 16 + 未跟踪 10 + 重复 22；pyc 不复制 |
| K 根文件 + PAPER | 17 + PAPER | 15 + PAPER | 执行中（PAPER 在 F7；根件在 F8） | 本地独有 2（API_COMPATIBILITY_REPORT / provider_check） |
| L 排除域 | dist 10,928 / backups 16 / guanjia 75 / .coordination 远端 2 / ACL 残骸 | — | 排除登记（零迁入） | — |
| 合计 | 318,842 文件 / 2.11 GiB | 47,517（harness）+ 1,893（非前缀） | 已落位 2,998（F1 2,377 + F3 527 + F4 94；F2 登记 84 行不占文件） | 十态总账见 §10.1 |

MIGRATION_MANIFEST.csv 已合并行数：**3,109**（F1 2,398 + F2 84 + F3 527 + F4 100）；F5/F5b/F6/F7/F8 执行完成后并入。

## 14. 未决事项清单

### 14.1 权利类（RIGHTS_REVIEW，decision_owner=user）

| # | 事项 | 事实 | 收口条件 |
| --- | --- | --- | --- |
| R2 | 模型服务商输出再分发条款 + 答卷正文脱敏审查 | runs/ 原始回复与 logs/ 原始答卷公开形态待定；当前纪律=原件本地保留、公开走结构摘要+评分聚合 | 条款核实 + 脱敏审查后方可定公开副本形态 |
| R6 | LoveBrain images/demo.gif 与 owerview.jpg 素材确认 | demo.gif 为 v1.3.x 真机录制（界面已改版）、owerview.jpg 无制作档案 | 按既有决定制作合成演示替换公开引用；owerview.jpg 补作者/素材确认 |
| R8 | framework-guanjia 复制授权 | 无授权文件（溯源用户自产）；当前不整仓复制仅登记 | 依赖清单核验 + 权利确认后按最小集取用 |

### 14.2 执行类（Phase F/G 收尾）

| # | 事项 | 当前完成度 |
| --- | --- | --- |
| P1 | 快照轨恢复（GOLD 13 + exposure 80 + 两个 SNAPSHOT_README + REMOTE_V1_SNAPSHOT_MANIFEST.json） | 待执行（远端取回位置已在对账 CSV 固化） |
| P2 | F1 GOLD 行 notes 更新（Q1 旧文案 → 双轨收口文案；禁 B 线状态字样） | 待执行 |
| P3 | B 线 GOLD 内嵌 ID 普通化核对（决策一：24 篇断言在权威轨 GOLD_wave1/2/3/3_ext 完整） | 抽查 4/24 完成（E045→wave1、E069→wave2、E150→wave3、LRE119→wave3_ext）；**24/24 全量复核待快照轨收尾一并执行** |
| P4 | refs 30 件远端独有恢复 + isol_runs 62 件授权/发布材料定性恢复 | 待执行（随对应域复制收尾） |
| P5 | runs（F5）/ isol_runs（F5b）/ refs（F6）/ arena·tools·PAPER（F7）/ 特殊目录（F8）复制与逐域 --verify | 执行中 |
| P6 | delivery_zips 14 包逐包核对放行门禁 | 待执行（REVIEW_REQUIRED） |
| P7 | dataset 合成样本强制隐私扫描（零真实姓名/零真实对话片段/零可还原特征）→ 通过后复制进公开树 | 隔离区生成完成，扫描未执行 |
| P8 | logs V1–V7 十 zip 正式打包（Phase G）与上传 | dry-run 已完成；**V7 gate=pending_user_release（夜间不上传，晨间用户放行；必带 --sanitize-manifest）**；V1–V6 gate=public 但上传动作待打包后统一执行 |
| P9 | evaluation 敏感 3 件脱敏形态定稿与执行 | HELD_REVIEW |
| P10 | provider_check.json 唯一证据价值审查（TRANSIENT_REVIEW） | 待执行 |
| P11 | 21 个 ACL 锁定文件的处理（保持锁定或如实登记锁定清单） | 随 F1 收尾 |

### 14.3 smoke test / README 终稿待实测项（T1–T11）

| # | 待实测项 | 收口方式 |
| --- | --- | --- |
| T1 | 迁移副本上 13 个入口点命令行可运行 | 端到端跑通后写入实测命令示例 |
| T2 | Pillow 安装后 12 个 PIL 工具可运行 | 实测后补版本锁定 |
| T3 | engine_adapter / reference_chain 落位后 import 可解析 | tools 全量可运行声明前必须收口 |
| T4 | requirements/pyproject 环境声明文件补齐 | 文件落盘后替换登记 |
| T5 | 数据集口径 319/317/299/248 vs 208/221 重统计收敛 | 按实体定义重统计（episode 机器重算已完成，作输入） |
| T6 | GOLD_SIDECAR 14 vs 13 文件对账 | **已收口**（本文件 §11.2 三代口径 + §10.3 双轨处置） |
| T7 | 批次↔rubric/scorer 版本对照表 | 逐批次登记后补 |
| T8 | §12.2 离线 smoke test 十项在迁移副本全通过（不调用真实模型 API、不读取 B 线题面） | 十项逐项记录输出 |
| T9 | 统计脚本实测清单 | smoke 第 8 项通过后补 |
| T10 | 交付完整性总账（文件数+字节量口径）生成与复核抽检 | Phase E/F 执行 |
| T11 | 离线复核功能 8 项实测 | 逐项记录后才能写"可离线复核" |

---

## 附：验收对照速查

- "GOLD_SIDECAR 13 个文件已完整迁移" → 按双轨口径执行：远端 13 件快照轨 + 本地 14 件权威轨齐备（§10.3/§11.2）。
- "原始 Gold 未被修改" → 源文件零改动，以 benchmark 全量清单（2,398 行，条数+字节量）逐行核对。
- "208/221/299/317 历史统计冲突已解释" → §11.2 三代口径表。
- "TeamWorkspace 私有文档未迁入" → §9 排除表（十类禁入 10/10 未迁入验证）。
- "B 线 24 篇处置" → 普通资产完整迁移（决策一），无任何特殊标记；账本 SEALED 历史记录原样保留。
