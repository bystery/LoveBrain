# 根目录整理归档清单（2026-11-07，W24-01）

执行者：主控会话。方式：**只移动、不删除**，所有原件保留在 archive/ 下；移动前经引用核查（治理文件 + .coordination + tools/arena 代码）。

## 移动明细

| 原位置（根目录） | 新位置（本目录下） | 数量/大小 | 引用核查结论 |
|---|---|---|---|
| `*_acl_locked_*`（arena_/benchmark_anchors_/benchmark_cases_/benchmark_episodes_/refs_ 五组） | `acl_locked_snapshots_20261021/` | 130 个目录，约 70–75MB | 治理文件与代码 0 引用；2026-10-21 ACL 锁定事件的历史快照，内文件保留原 Deny ACL 未动 |
| `_ACL_LOCKED_LEDGER.json` | `acl_locked_snapshots_20261021/` | 1（161 条锁定路径索引台账） | 治理文件 0 引用；tools/state_map_check.py 期望已同步更新 |
| `lovebrain_arena_phase1a_*.zip`、`lovebrain_review_v*.zip` | `delivery_zips_root/` | 14 个，61.1MB | 包名 0 引用（make_review_package.py 只产出新 zip 不读旧 zip）；权威交付包在 refs/ 下未动 |
| `probe_fz/`、`drill_v2/`、`dryrun_v2/`、`t_l3b66_test_j0cqwha3_clean/`、`workspace_contrast_items.json`、`baseline/`（空）、`.pytest_cache/` | `drafts_and_caches/` | 6 目录+2 文件 | 0 引用（演练/探针/一次性测试草稿/缓存） |
| `_archive_v1/`（上一轮归档整体） | `_archive_v1_from_root/` | 159K | 0 引用 |
| `WORKER_HANDOFF_W22_20261104.md` | `handoffs/` | 8.2K | 0 引用（已被 WORKER_NEW_WINDOW_START.md 取代） |

## 明确不动的（及理由）

- `RUN_STATE.json`、`TASK_BOARD.md`：**tombstone 防污染标记**（deprecated_registry / 废止横幅），audit_workspace_hygiene.py 靠它们防止旧注册表复活，留在原地。
- `WORKER_CURRENT_RUNBOOK.json`、`WORKER_NEW_WINDOW_START.md`、`WORKER_LOOP_PROMPT_20261103.md`、`WORKER_EXPLORATION_GUIDE_20261103.md`、`WORKER_HANDOFF_20261028_2222.md`、`VERSION_LINEAGE_V2.json`、`指导书.md`：活动指针/治理文件，被 RUNBOOK/TASK_QUEUE 绝对路径引用。
- `W13-03 新领域验证批…`、`W14 判决批…` 两个中文目录：0 引用但属批次工作区仅存痕迹，且 closeout 未合并——**延迟到 closeout 合并后再归档**。
- `dist/`、`best/`、`runs/`、`evaluation/`、`dataset/`、`integration_preview/`、`backups/`、`isol_runs/`、`logs/`（约 2.1GB 逐调用原始答卷，独立复核证据，保留）、`arena/`、`benchmark/`、`candidates/`、`tools/`、`refs/`、`archive/`、`.coordination/`：活动基础设施/证据。
- `benchmark/b_line/`：**物理封存**，整理全程未触碰。

## 配套变更

- `tools/state_map_check.py`：ACL_LOCKED_DIR_EXPECT 130→0、ROOT_ZIPS_EXPECT 14→0、NOISE_EXISTS 与历史文件路径三处同步（文件内带 2026-11-07 注记）；复跑结果 **0 MISMATCH**（2 条合法增长 WARN：logs 346、refs 92）。
- 效果：根目录顶层条目 184 → 29（+archive）。
