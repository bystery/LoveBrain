# ARCHIVE_LEDGER · 研究批次归档账本

- 版本：2026-11-15（W3 research 三层拆分波）；分支 `research-migration-20261112`
- **W4 打包源提交：`4836b017`**（4836b017c3c74738fd3243ccfbd8675e3db6d7a6）——拆分前末位提交。除 P-09/P-10（已随 W1 撤支）与 P-14（w3-originals）外，各包位一律从该提交 `git archive` 直出切包，无需另备工作树。
- 完整性口径：**文件数 + 字节量**（git blob 实测；全流程禁哈希，存量 `.sha256` 随池原样封存）。
- 载体与存放（D25 裁决）：批次 zip → 私有归档区 `D:\LoveBrain-archive\packages\`；③层直弃单元 → `w3-discard\`；降层原件 → `w3-originals\`；W1 隐私撤支原件 → `w1-originals\`。公开侧仅放脱敏证据抽样包。
- 门状态沿用 X10 门号：G2 敏感清单（D5/D10/D11 处置结果）/ G3 注册表留 Git / G4 协议根部 28 件升① / G5 冠军臂留 Git / G6 checkpoint 随池 / G7 冒烟·probe 降③ / G8 GOLD 双快照择一 / G9 MIGRATION 降层。

## 包位总表（P-01~P-14）

<a id="p-01"></a>
**P-01 · EXP-PILOT2** — `lovebrain-runs-EXP-PILOT2-20261108.zip`
范围 `research/experiments/runs/pilot2_*`（6 单元）｜19,893 件 / 88,856,070 B｜来源：W4 打包源提交 `4836b017`｜门：—（G2 过 D5/D10/D11）

<a id="p-02"></a>
**P-02 · EXP-SELECTION** — `lovebrain-runs-EXP-SELECTION-20261111.zip`
范围 `runs/selection_v1*`（5+1 单元）｜11,780 件 / 76,550,226 B｜来源：`4836b017`｜门：§1.3-2（LB 独有 `universal_scores_guardv2.csv`，arena 侧 11,779 件为子集）；**展示链白名单 9 件豁免留 Git（CURRENT/DIRECT E340 turn_10 两轮，22,791 B），打包时剔除**

<a id="p-03"></a>
**P-03 · EXP-HOLDOUT** — `lovebrain-runs-EXP-HOLDOUT-20261108.zip`
范围 `runs/holdout_v1`｜4,281 件 / 21,319,936 B｜来源：`4836b017`｜门：—

<a id="p-04"></a>
**P-04 · EXP-ATK** — `lovebrain-runs-EXP-ATK-20261101.zip`
范围 `runs/_atk*、_atk2*`（137 单元）｜4,230 件 / 21,104,368 B｜来源：`4836b017`｜门：G2（authorization 记录与 media_secret 按 D10/D11 处置）

<a id="p-05"></a>
**P-05 · EXP-LC** — `lovebrain-runs-EXP-LC-20261101.zip`
范围 `runs/_lc_*`（69 单元）｜**实测 2,416 件 / 19,935,959 B**（X10 计划 2,516/19,953,156；差 −100/−17,197 = W1 staged/sandbox 邻接面撤支，原件在 w1-originals）｜来源：`4836b017`｜门：—

<a id="p-06"></a>
**P-06 · EXP-RP1** — `lovebrain-runs-EXP-RP1-20261111.zip`
范围 `runs/rp1_*`（30 单元）｜6,092 件 / 14,300,934 B｜来源：`4836b017`｜门：—

<a id="p-07"></a>
**P-07 · EXP-ISORUN** — `lovebrain-runs-EXP-ISORUN-20261021.zip`
范围 `runs/{_g11_standin*,_iso_run*}`（含 7 件顶层授权散件）+ `experiments/isolated_runs/`（23 单元）｜**实测合计 1,015 件 / 12,696,016 B**（X10 计划 1,051/12,704,377；差 −36/−8,361 = W1 同上；isolated_runs 422/4,193,833 全额在源提交内）｜来源：`4836b017`｜门：G2、G6

<a id="p-08"></a>
**P-08 · EXP-SMOKE-MISC（③层直弃，不打包）**
范围 `runs/(_ph3*,_w19*,_smoke*,rh_*,g11_real*)`｜**实测 11 单元 / 776 件 / 3,414,769 B**（L12 YAML 行 13/806/3,469,559 与其自身家族表 8+1+2 单元不自洽，差 2 单元/30 件/54,790 B 树内无对应单元，按 scope 模式实测执行）｜载体：`D:\LoveBrain-archive\w3-discard\research\experiments\runs\`（776 件已复制后删）｜门：G7

<a id="p-09"></a>
**P-09 · REF-PROTOCOL-BULK** — `lovebrain-refs-REF-PROTOCOL-BULK-20261028.zip`
范围 `refs/experiment_protocol_20261023/` 除根部 28 件（long_probe 判决等）｜计划 19,100 件 / 191,243,686 B｜来源：**已随 W1 整目录撤支，原件在 `w1-originals`（19,128 件 / 192,209,415 B，blob 精确）**；W4 不再从 `4836b017` 切此包｜门：G4（根部 28 件同处可回补；**展示链白名单 4 判决 FLJUDGE/LD1/W16_UNC/W17B 已自历史回补留 Git**）

<a id="p-10"></a>
**P-10 · ARCH-DELIVERY** — `lovebrain-archive-ARCH-DELIVERY-20261107.zip`
范围 `archive/.../delivery_zips_root`（5 个历史交付 zip）｜11 件 / 38,276,562 B｜来源：**已随 W1 D5 撤档，原件在 `w1-originals`**｜门：D5（内嵌真实聊天回放语料，公开侧禁入）

<a id="p-11"></a>
**P-11 · ARCH-HISTORICAL** — `lovebrain-archive-ARCH-HISTORICAL-20261107.zip`
范围 `archive/` 其余（historical_assets、historical_worker_guides、integration_preview）｜**在源提交内 51 件 / 457,643 B（W4 切包）；另 17 件已随 W1 撤支入 `w1-originals`（root_docs 6 + worker_guides 9 + integration_preview 2）**｜来源：`4836b017` + w1-originals｜门：—

<a id="p-12"></a>
**P-12 · CAND-HISTORY** — `lovebrain-candidates-CAND-HISTORY-20261106.zip`
范围 `candidates/` 除冠军两目录（44 个历史版本）｜**实测 503 件 / 2,476,130 B**（X10 计划 504/2,479,076；差 −1/−2,946 = `reference_product_v1/IDENTITY.md` 随 W1 撤支）｜来源：`4836b017`｜门：G5（冠军 `window_expand_b_norecap_guard` 11 件 + `window_expand_b_norecap_uc_guard` 12 件留 Git）

<a id="p-13"></a>
**P-13 · ARENA-SUPP** — `lovebrain-arenasupp-ARENA-SUPP-20261111.zip`
范围 arena 独有：dataset 84 + refs 62 + archive 22 + integration_preview 6｜174 件 / ≈1.46 MB（打包实测修正）｜来源：**arena 仓 `research-p1-sync-20261111@7f0d189e` 独立切源（本分支无此内容）**｜门：G2（dataset 隐私，D9）

<a id="p-14"></a>
**P-14 · PROV-MIGRATION（降层原件，不打包）**
范围 `provenance/MIGRATION_MANIFEST.csv`｜1 件 / 27,298,958 B（blob 口径）｜来源：W1 前即在树，随 W3 拆分降层；**原件已复制 `D:\LoveBrain-archive\w3-originals\provenance\`**（磁盘 27,373,142 B = blob + 行数 74,184，CRLF 级差）｜门：G9 裁决 = ②归档 + ①留轻量索引（见 `research/provenance/PROVENANCE-INDEX.md` §2026-11-15 增补，含 anchors 勘误登记）

## 散件归档段（非包位，均随 W3 拆分退场；W4 可从 `4836b017` 直出）

| 段 | 文件 / 字节 | 处置依据 |
| :-- | ---: | :-- |
| `benchmark/stories_v2/GOLD_SIDECAR_v1_remote_snapshot/` | 14 / 11,584,914 | G8：与现行金标 GOLD_SIDECAR（14/14,088,650，留 Git）重复，双快照择一 |
| `benchmark/stories_v2/_remote_v1_snapshots/` + `_templates/` | 87 / 173,642 | L10 §4：远端快照与模板，剔出冻结数据集口径 |
| `evaluation/` 过渡件（interim/mock/dev/preview/buggy/partial） | 8 / 271,968 | L10 §4-7：非冻结结论；第 9 件 `A3_CALIBRATION_ANALYSIS_INTERIM.json` 已随 W1 撤支（w1-originals） |

## W1 隐私撤支区（2026-11-15，非本账本包位）

<a id="w1-privacy"></a>
W1（W2R1 单元）按 D1/D5/D10/D11/D12 整目录撤支并入档 `D:\LoveBrain-archive\w1-originals\`：19,461 件 / 234,329,913 B（磁盘口径；含 protocol 整目录 19,128、批次 A 交付 ZIP 11、anchors 9、version_closeout 29、staged/sandbox 136、残余私有引用 141 等，逐单元明细见 W2R1 报告）。**本页同时是以下已退场深链的登记入口**：`research/refs/VERDICT-INDEX.md`（判决索引，原 53 项）、`research/evaluation/user_value_v1/protocol.md`（60 案例评测协议）——均随残余私有引用清理撤支，原件在 w1-originals，历史按提交 SHA 仍可达（D3/D27 口径）。

## 勾稽

- runs 池闭合：P-01~P-06 + P-07 runs 侧 593 件 + P-08 = **50,061 件 / 253,984,445 B**，与拆分前 runs 实测全等（另 +136 件 staged/sandbox 已随 W1 撤支，4716ca12 基线 50,197/254,010,131 对账闭合）。
- 正式层留 Git：`research/` 现行 4,342 件 / 69,087,055 B（含展示链白名单 13 件/40,438 B 与 runs-registry.csv + raw_logs/INDEX.json 2/109,733）。
