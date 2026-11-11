# GOLD_SIDECAR_v1_remote_snapshot — 远端已提交 Gold 快照（快照轨 A）

**身份句：本目录 = 远端已提交 Gold 快照（2026-11-08 构建波次，13 文件），经 `arena-remote-mirror` `git cat-file blob <blob_sha1>` 按 blob SHA1 恢复，字节与远端 blob 一致（逐件核验=字节数比对：`git cat-file -s` 输出字节数 == `cat-file blob` 输出字节数 == 落盘字节数 == `reconcile-status.csv` remote_size；零 sha256 口径）。**

## 权威声明

正式结果以 `../GOLD_SIDECAR/`（本地 v2 重建，14 文件，2026-11-10）为准；本目录仅作版本历史与审计对照，**不是评分消费输入**。依据：`asset-audit/gold-dual-track-plan.md` §2.1（权威轨选择依据是证据链——本地版为 2026-11-10 账本补录 + Gold 重建批次的实际产物，远端版为该批次之前的历史定格；两版都保留，不允许静默"选择看起来更新的版本"）。

## 三口径表（引用 gold-dual-track-plan.md §0）

| 口径 | 实体位置 | 生成时间（GOLD_INDEX generated） | 字面 schema_version | totals（episodes/turns/atoms） | 文件数 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| ① v1.0 文档口径 | 仅存在于 `../GOLD_SCHEMA.md` 文本（§0/§1 布局 4） | 2026-11-03 前后 | "GOLD_SIDECAR_v1"（文档称谓） | 221 / 3,102 / 6,603 | —（无文件实体留存） | gold agent 首版产物；被 ② 取代，全仓与远端 HEAD 均无其文件 |
| ② 远端已提交快照（本目录） | 远端 git 历史（main `387c55ac` 与 `7f0d189e` 两 ref blob 等价）+ 镜像可恢复 | 2026-11-08T06:00:43 | GOLD_SIDECAR_v2 | 259 / 3,590 / 7,953 | 13 | 远端最后一次提交的 Gold 构建波次（11-08 Benchmark V1 Phase 2 提交） |
| ③ 本地 v2 重建（权威） | `../GOLD_SIDECAR/`（未提交） | 2026-11-10T04:25:15 | GOLD_SIDECAR_v2 | 299 / 4,082 / 9,646 | 14（新增 GOLD_wave5_selection.json） | 11-10 账本补录 + Gold 重建批次，本地工作树现状 |

## 来源登记（恢复日期 2026-11-12）

- 恢复命令样例：`git -C asset-audit/arena-remote-mirror/arena cat-file blob <remote_blob_sha1> > <本目录>/<原文件名>`（blob 句柄取自 `asset-audit/reconcile-status.csv` remote_sha 列，与方案附录 A 逐一相等）。
- ref 说明：远端 main = `387c55ac`（2026-11-08）与镜像检出 HEAD `7f0d189e`（research-p1-sync-20261111，2026-11-11）两 ref 下 13 个 GOLD blob 完全等价（13/13 实测比对）。
- 逐件核验（字节数口径，用户纪律 2026-11-12）：`git cat-file -s <blob_sha1>` 输出字节数 == `cat-file blob` 输出字节数 == 落盘字节数 == `reconcile-status.csv` remote_size（93/93 一致；零 sha256）。
- 统一登记（字节数口径）：见 `../_remote_v1_snapshots/REMOTE_V1_SNAPSHOT_MANIFEST.json`（93 行，含本目录 13 件交叉引用行）。

### blob SHA1 对照表（gold-dual-track-plan.md 附录 A）

| 文件 | 远端 blob SHA1 | 远端 size (B) | 本地 v2 size (B) |
| --- | --- | ---: | ---: |
| GOLD_INDEX.json | `8f03be34f655126e5aa17b651f6143b3c7cb46c7` | 9,817 | 10,836 |
| GOLD_QC_SAMPLE.json | `fba689aa939706dbd9d741e59e36a90be0ac4e79` | 5,450 | 5,799 |
| GOLD_stage1.json | `d6a2cddef83ef0339f2ed8ae65d381e5650bfd3d` | 160,097 | 166,374 |
| GOLD_stage2.json | `90d3040d7a31f610e311d7bf46bb3eeefc545f7b` | 405,182 | 420,589 |
| GOLD_stage3.json | `9dbc3361aa70eb267bd257a136479e4b1f229783` | 139,858 | 145,320 |
| GOLD_stage4.json | `9f28bef261d0c4b7a332d1cc00c6de5c56dd0779` | 426,418 | 442,582 |
| GOLD_stageD.json | `a3345ab09a7321a3bbc3a341b7a7f7ae67bd63ad` | 87,344 | 90,419 |
| GOLD_wave1.json | `085c8d0489bf2eb5ac862034d627ff4fa83dad0b` | 659,453 | 683,386 |
| GOLD_wave1_ext.json | `bf561bdd98eee31f73a012224565ba026d2c20ad` | 249,266 | 258,409 |
| GOLD_wave2.json | `ce6e05db6a5bf7eeaa12375f68261daa7747868e` | 2,079,157 | 2,152,480 |
| GOLD_wave3.json | `c137509a3d432f4447419b20ef8c30f091c36137` | 3,615,591 | 3,748,112 |
| GOLD_wave3_ext.json | `13c4ff1d69a745bc37b69ae8bc32d60f4283b369` | 1,811,769 | 1,877,755 |
| GOLD_wave4_dev.json | `38df42329bbe33dd1b8110f24079261f376b5ddc` | 1,930,630 | 1,999,141 |

## 文档滞后说明

`../GOLD_SCHEMA.md` 自述 v1.0（2026-11-03 前后，口径① totals 221/3,102/6,603），滞后于 ②③ 两代数据；按迁移纪律**原样保留不修改**（滞后本身是历史证据）。注记另见 provenance 侧 `MIGRATION_MANIFEST.csv` 的 GOLD_SCHEMA.md 行与 `SOURCE_MAP.md` benchmark 域条目。

## 加载器提示

`arena/gold_sidecar.py` 的 `find_turn_gold(sidecar_dir, …)` 只对**传入的** `sidecar_dir` 做 `os.listdir`（过滤 `GOLD_*.json`），不扫描兄弟目录；**本目录不应作为 `sidecar_dir` 传入**（评分输入恒为 `../GOLD_SIDECAR/`）。本目录文件名与权威轨一一对应但分目录存放，零覆盖。
