# _remote_v1_snapshots — exposure.json 远端已提交快照（快照轨 B）

**身份句：本目录 = 80 个 `exposure.json` 的远端已提交版（2026-11-08 提交波次定格），经 `arena-remote-mirror` `git cat-file blob <blob_sha1>` 恢复，字节与远端 blob 一致（逐件核验=字节数比对：`git cat-file -s` 输出字节数 == `cat-file blob` 输出字节数 == 落盘字节数 == `reconcile-status.csv` remote_size；零 sha256 口径）。**

## 路径镜像规则

```text
_remote_v1_snapshots/exposure/<layer>/<EID>/exposure.json   ← 本目录（快照轨）
stories_v2/<layer>/<EID>/exposure.json                       ← 源路径（权威轨，episode 五件套之一）
```

`<layer>/<EID>/exposure.json` 是经 c1 §1.3 验证 100% 命中的五件套相对路径模式；快照采用集中平行树镜像该相对路径，不在 episode 目录本身内放置备份件，避免破坏该模式或被遍历工具误当作正式层文件。下划线前缀沿用 stories_v2 既有惯例（`_templates/` = 非 episode 层目录）。

分层分布：stage2×8 / stage3×3 / stage4×6 / wave3×17 / wave3_ext×8 / wave4_dev×38 = 80。

## 变更性质摘要（引用 gold-dual-track-plan.md §2.2，80/80 全量程序化比对）

| 变更键 | 覆盖 | 性质 |
| --- | --- | --- |
| `frozen_at` | 80/80 | 2026-11-10 10:17:55 冻结期限登记（→ `2026-11-10T23:59:00Z`；远端多为 `null` 或旧值） |
| `hashes` | 80/80 | 哈希登记字段更新（episode/reference SHA 不变，登记格式/附加键更新） |
| `status` / `reconciled_at` / `audit_evidence` / `experiments_used` / `dev_exposed` / `reconciliation_key` | 38/80（全部 wave4_dev） | phase3 对账回填：`experiments_used []→[pilot2_wave4_live_{b,c}_20261108]`、`dev_exposed false→true`、`status annotated_pending_audit→audited_accepted` |
| `split_reclass` | 2/80 | 分箱重分类字段 |

即：本目录定格的是**回填/冻结登记之前**的台账状态（wave4_dev 38 份的"未回填"状态正是 `wave5-batchname-confirmation.md` §4 论证 wave5 双缺口的对照证据）。

## 权威声明

正式结果以 episode 五件套中的本地版 `exposure.json` 为准；本目录为回填/冻结登记前的历史台账快照，与 `STORY_CUSTODY_LEDGER` 补登说明（migration-decisions-20261112.md 决策二）互为证据链。台账的权威状态与历史状态分离后，账本补登与 exposure 登记的先后关系可逐字节复核。

## 统一登记（字节数口径）

93 行统一登记（本目录 80 件 + GOLD 快照 13 件交叉引用）见 **`REMOTE_V1_SNAPSHOT_MANIFEST.json`**，字段：`source_remote_path / snapshot_path / remote_blob_sha1 / remote_size / disk_size / diff_class / authoritative / note`（diff_class：SUBSTANTIVE_EXPOSURE_BACKFILL = wave4_dev 38，SUBSTANTIVE_EXPOSURE_FROZEN = 其余 42，SUBSTANTIVE_GOLD_V2 = GOLD 13）。`disk_size` 为快照件落盘字节数，逐件等于 `remote_size`；核验=字节数比对：`git cat-file -s` 输出字节数 == `cat-file blob` 输出字节数 == 落盘字节数 == `reconcile-status.csv` remote_size（零 sha256 口径）。

## 加载器提示

本目录不进入任何加载器消费路径：episode 数据访问按五件套相对路径直读 `<layer>/<EID>/` 目录，不会命中 `_remote_v1_snapshots/`；`arena/gold_sidecar.py` 按传入目录消费，本目录不应作为 `sidecar_dir` 传入。
