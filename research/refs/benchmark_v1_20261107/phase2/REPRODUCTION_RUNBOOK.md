# REPRODUCTION_RUNBOOK · Benchmark V1 Phase 2 机械验收复现手册

> 生成：2026-11-08（复核就绪工程师）。目的：外部复核者按本手册逐条重跑本阶段**全部机械验收**。
> 快照基线：PHASE2_ACCEPTANCE.md（12:02）+ 本手册起草时点实跑。pilot 三进程 live 运行中——§6 数据为 live 运行时快照。
> 本手册只描述只读校验与派生物再生成；除 §6/§7 按设计刷新 run 目录内派生汇总外，不改任何题目/评分数据。

## 0. 前置条件

- **工作目录**：仓库根 `D:\lovebrain-arena`（全部命令相对根目录；工具经 `__file__` 自定位 ROOT，不依赖绝对盘符）。
- **Python**：`python -I`（隔离模式：忽略 PYTHONPATH、不预挂脚本目录）。本手册全部命令在 **3.12.4** 实跑验证；相关工具仅用标准库+argparse/f-string，3.8+ 均可。
- **依赖带同目录兄弟模块的工具**：`aggregate_pilot.py`、`dedupe_wave.py`、`build_human_review_queue.py` 自带 `sys.path.insert` 引导，直接 `python -I tools/...` 可跑；`build_gold_sidecar.py` 例外（引导路径指向仓库根而非 tools/），须用 §2 的 `__file__` 垫片单行。
- **UNTOUCHABLE_HASHES**：冻结清单=`refs/benchmark_v1_20261107/phase1/UNTOUCHABLE_HASHES.json`（72 文件）。凡命中文件发生**授权变更**，必须在 `repin_log` 追加条目（现已有 1 条：2026-11-08 W27-01 Phase 2，build_gold_sidecar 重建使 GOLD_SIDECAR/*.json 哈希重钉，含理由与文件清单）。复核时：跑 §5 的 `test_untouched_integrity` / `test_r9_slim_untouchable_hash`，FAIL 时先查 repin_log 是否登记，未登记即违约。
- **隔离纪律（本手册同款）**：不读 `benchmark/b_line/` 正文、`logs/`、`candidates/`；`runs/` 只读不写（live 在写）；`GOLD_SIDECAR` 内容不作复核输入（§2 的 dry-run 由工具自算，不经人转抄）。

## 1. 数据校验：validate_story_v2 ×3（wave4_dev 三分片 manifest）

注：`--out-name` 会把校验报告**写回 wave 目录**（即原建流程刷新派生快照 `W4_VALIDATION_*.json`；不改题目源文件）。预期输出=各命令末行。

```bash
python -I tools/validate_story_v2.py --stage wave4_dev --turns 12 --count 36 --manifest-name W4_MANIFEST_12T.json --out-name W4_VALIDATION_12T.json --allow-frozen-at
python -I tools/validate_story_v2.py --stage wave4_dev --turns 24 --count 1  --manifest-name W4_MANIFEST_24T.json --out-name W4_VALIDATION_24T.json --allow-frozen-at
python -I tools/validate_story_v2.py --stage wave4_dev --turns 32 --count 1  --manifest-name W4_MANIFEST_32T.json --out-name W4_VALIDATION_32T.json --allow-frozen-at
```

预期（沙盒实测=盘上快照一致）：`stories=36 passed=36 unique_cases=432 errors=0`、`stories=1 passed=1 unique_cases=24 errors=0`、`stories=1 passed=1 unique_cases=32 errors=0`（合计 488 case，0 错误）。`--allow-frozen-at` 必带（wave4_dev 的 exposure.frozen_at 非空；缺省会在 E309 起报 "frozen_at should be null"）。

## 2. Gold sidecar：build_gold_sidecar.py

- 输出位置：`benchmark/stories_v2/GOLD_SIDECAR/`（GOLD_INDEX.json + GOLD_<wave>.json ×11 + GOLD_QC_SAMPLE.json）。
- **QC 门禁 = Marker QC verbatim_violations=0**（官方门禁；修复前 43 → 修复后 n=1370, violations=0，见 `MARKER_QC_REPORT.json` / `REPAIR_MARKER_FIX_1.md`）。
- 只统计不落盘（复核推荐，本手册实测通过，输出与门禁一致）：

```bash
python -I -c "import sys; sys.path.insert(0,'tools'); g={'__name__':'__main__','__file__':'tools/build_gold_sidecar.py'}; exec(open('tools/build_gold_sidecar.py',encoding='utf-8').read(),g)" --dry-run
```

预期：`wave4_dev turns=488 atoms=1350 strict_hit=1350`；全库 totals atoms=7953 strict 7953/7953；`QC sample n=40 strict_all_pass=40`；`Marker QC: n=1370 verbatim_violations=0`。
- 全量重建（落盘+custody 账本 append 事件；**重建后 GOLD_SIDECAR 哈希变化须按 §0 repin_log 惯例重钉**——同上单行去掉 `--dry-run`）。

## 3. 谱系 / 封存：lineage_check 与 custody_split_guard（角色差异）

- `lineage_check.py`：**谱系组隔离机械核查**——组=资格单元（跨 split 迁移、B 线成员/关联、HOLDOUT 消费、孪生跨 split 均在此判）。只读、零写入。

```bash
python -I tools/benchmark_v1/lineage_check.py --stories-root benchmark/stories_v2 --split-csv refs/benchmark_v1_20261107/phase2/LINEAGE_SPLIT_PROVISIONAL.csv
```

预期：`lineage_check: 组=26 篇(CSV)=38 B线成员=0 违规=0 警告=0`（exit 0）。
- `custody_split_guard.py`：**正式分派×保管账本×分区强制片**——按 `DATASET_V1_FINAL_ALLOCATION.csv`（240 行）核对已定稿/PENDING 计数、custody 账本登记、组/孪生不跨 split。只读。

```bash
python -I tools/benchmark_v1/custody_split_guard.py --expected-pending 157
```

预期：`篇(分派表)=240 已定稿=83 PENDING=157 组=62 孪生组=2 违规=0 警告=38`；38 条 WARNING 均为"新题 E301–E338 未登记于 custody 账本（待 seed）"，属预期。角色差异一句话：**lineage 管谱系资格与隔离，custody 管分派表落地与消费账本**；二者以 `--json` 可取完整机读报告。

## 4. 近重扫描：dedupe_wave.py

```bash
python -I tools/benchmark_v1/dedupe_wave.py --wave wave4_dev
```

默认 `--out` 写 `refs/benchmark_v1_20261107/phase2/DEDUPE_wave4_dev.json`（覆盖既有 QC 工件=原建流程；只观察可加 `--out <临时路径>`，本手册已用临时路径实测）。预期：`scanned=222 wave_stories=38 clusters_all=2 involving_wave=2`；两簇=`E333/E334`、`E335/E336`（max_jaccard 0.71，**T10 预期孪生组**，0 意外近重），与盘上 `DEDUPE_wave4_dev.json` / `DEDUPE_REPORT.json` 一致。

## 5. 离线测试（当前全绿基线，2026-11-08 实跑）

```bash
python -I -m pytest tools/benchmark_v1/tests -q
```

当前基线 **39 passed**（test_benchmark_v1_offline 21 + test_custody_split_guard 16 + test_fix_wave4_schema_v1 2；含 `test_untouched_integrity`、`test_r9_slim_untouchable_hash`、legacy_compat×3、lineage 实库测试）。扩展套件（可选）：`python -I -m pytest tools/test_gold_sidecar_scoring.py -q` → 10 passed。合计 49 全绿。（历史注记：验收起草时点曾有 2 败=lb_gateway 漂移+并发断言，均已按 §9 版本化解决并恢复全绿。）

## 6. pilot 复算（数据为 live 运行时快照）

三进程 run 目录：`runs/pilot2_wave4_live_b`、`runs/pilot2_wave4_live_c`、`runs/pilot2_oldT09_live`（授权 `PILOT2_AUTHORIZATION.json` cap_max_total_api_attempts=1500；子集 `PILOT2_SUBSET.json` 40 篇）。**复核者运行时以当时 run 目录实际落盘为准，本节命令已在本机沙盒（mock 副本）逐条验证。** aggregate 的 `deliver_dir` 约定=run 目录本身（mock 与 live 同款：PILOT_RESULTS.csv / pilot_summary.json / PILOT_EXECUTION_REPORT.md 落在 run 目录内，故 aggregate 会刷新 run 目录内派生物）：

```bash
python -I tools/benchmark_v1/aggregate_pilot.py runs/pilot2_wave4_live_b runs/pilot2_wave4_live_b
python -I tools/benchmark_v1/aggregate_pilot.py runs/pilot2_wave4_live_c runs/pilot2_wave4_live_c
python -I tools/benchmark_v1/aggregate_pilot.py runs/pilot2_oldT09_live runs/pilot2_oldT09_live
python -I tools/benchmark_v1/merge_pilot_parts.py --parts runs/pilot2_wave4_live_b runs/pilot2_wave4_live_c runs/pilot2_oldT09_live --out-dir refs/benchmark_v1_20261107/phase2/pilot2_merged
python -I tools/benchmark_v1/pilot_analysis.py --csv refs/benchmark_v1_20261107/phase2/pilot2_merged/PILOT_RESULTS_MERGED.csv --out refs/benchmark_v1_20261107/phase2/pilot2_merged/PILOT_ANALYSIS.json
```

要点：merge 从各 part 读 `PILOT_RESULTS.csv`+`run_records.json`，并扫 `<part>/*/*/gateway_events.jsonl` 统计 attempt_started（cap 对账 1500）；若 part 无 PILOT_RESULTS.csv 会打 WARN——先对该 part 跑 aggregate。pilot_analysis 缺省 topics 映射=wave4_dev exposure 目录 + `phase1/DATASET_INVENTORY.csv`（旧篇 E063/LRE121 走 inventory）。mock 链路参照物：`phase1/PILOT_RESULTS.csv`、`phase2/mock_aggregate_test/`。产出供 `PHASE2_PILOT_REPORT.md` 引用，**差异/判别力结论归 Worker，不出自本工具**。

## 7. 人工审核锚点包：build_human_review_queue.py

```bash
python -I tools/benchmark_v1/build_human_review_queue.py --run-dir <live run 目录>
```

`--run-dir` 按 `<arm>/<EID>/<turn>/` 布局读取；缺省 `--out-dir` = 本 phase2 目录（**会刷新正式 `HUMAN_REVIEW_QUEUE.json/.md`**——live 收口后的既定刷新义务）；观察模式加 `--out-dir <临时目录>`（本手册沙盒实测：40 锚，分布 T02×12/T03×8/T04×6/T01×4/T05×4/T07×2/T08×2/T09×1/T10×1，`--max-per-episode` 缺省 2，确定性步长公式见输出 md 头，无随机源）。三进程分片时对每个 live 分片各跑一次（--out-dir 分开），由主会话按篇合并进正式包；旧 T02 四锚（A01–A04）在 pilot2 子集外，DIRECT/CURRENT 格显式"待"。用户未填=NOT_COLLECTED，AI 不代投。

## 8. 证据链索引（数字 → 载体）

| 数字 | 载体（可复核文件/命令） |
|---|---|
| 488 case / 0 错误 | §1 三命令 stdout + `wave4_dev/W4_VALIDATION_{12T,24T,32T}.json` |
| 原子 1350、quote strict 1350/1350、全库 7953/7953 | §2 dry-run stdout；`GOLD_QC_REPORT.md`；PHASE2_ACCEPTANCE #6 |
| Marker QC n=1370 violations=0 | §2 stdout；`MARKER_QC_REPORT.json`；`REPAIR_MARKER_FIX_1.md` |
| 谱系 组=26/38、B线=0、违例=0 | §3 lineage stdout；`LINEAGE_SPLIT_PROVISIONAL.csv`（38 行全 dev） |
| 240/83/157、孪生组=2 | §3 custody stdout；`DATASET_V1_FINAL_ALLOCATION.csv`；`STORY_CUSTODY_LEDGER.json` |
| 近重 222 篇 / 2 簇（0.71） | §4 stdout；`DEDUPE_wave4_dev.json`、`DEDUPE_REPORT.json` |
| 测试 39+10 全绿 | §5 stdout；`BATCH1_AGENT_REPORTS_DIGEST.md`（历史 23/23 为修复时点快照） |
| 旧题 45 行（42+1+2、REJECT 0） | `PHASE2_OLD_STORY_AUDIT.csv`、`OLD_STORY_FINAL_DISPOSITION.md` |
| Gold 冲突 93 行全裁决 | `GOLD_CONFLICT_RESOLUTION.csv` |
| 隔离 ack 18 条（7+6+3+2） | `GENERATION_ISOLATION_LEDGER.json` |
| 四臂身份哈希（DIRECT/COT/LEGACY/CURRENT） | `ARM_REGISTRY.json`（arm_registry_v1）、`LEGACY_COMPAT_REPORT.md` |
| pilot 授权/子集/cap1500/40 篇 | `PILOT2_AUTHORIZATION.json`、`PILOT2_SUBSET.json`、§6 merge_summary.json |
| 40 锚分布 | `HUMAN_REVIEW_QUEUE.json` + §7 stdout |
| 冻结哈希与重钉 | `phase1/UNTOUCHABLE_HASHES.json`（files=72 + repin_log） |
| 12 组跨作者重名 | `R3_RENAME_PLAN.md`（本目录，python -I 全量扫描复现） |

## 9. 边界声明

本手册全部命令 2026-11-08 在本仓库实跑核验（§6 以沙盒副本验证，未触碰 live run 目录）；validate ×3 另经临时副本双口径（含/不含 `--allow-frozen-at`）验证失败面。未读取 b_line/logs/candidates/GOLD_SIDECAR 内容；未写入 runs/ 与任何题目数据文件。验收总判定（PARTIAL 及升级条件）以 `PHASE2_ACCEPTANCE.md` 为准，本手册不复述判决。
