# SESSION_COORDINATION_V1 — 并发会话协调协议（在本仓库开工的每个会话，第一件事读我）

> 建立：2026-10-27 00:42，起因：主对话与"每15分钟例行推进"定时会话（automation-67c79de5-9b9b-40af-ac5f-e58b371cb38d）
> 同时工作。目的：不互相覆盖、不争抢同一件事、不重复劳动。本文件是仓库级纪律，与 STATE_MAP 同级可见；
> 与 AGENTS.md 冲突时以 AGENTS.md 为准，但"防撞车机制"本身不得跳过。

## 0. 当前登记的并发会话

| 会话 | 触发 | 角色 |
|---|---|---|
| 主对话（用户当前窗口） | 人工 | 执行大牛执行书主线大块：批次派发、账本动账、评审材料、STATE_MAP 权威链维护 |
| 定时例行 cron15 | 每 15 分钟（automation-67c79de5-9b9b-40af-ac5f-e58b371cb38d） | 挑主线没在做的小件：草案/背版单件/工具缺陷/只读核验 |

## 1. 心跳登记（谁活着）

- 文件 `D:\lovebrain-arena\.coordination\heartbeat.jsonl`。开工第一行：`{"ts":"<ISO时间>","session":"<唯一名>","role":"main|cron15|human|side","intent":"<本轮打算做什么，一句话>"}`；收尾追加一行同 session、带 `"status":"done"`。
- **在世判定**：某 session 最后心跳 < 20 分钟 = 在世。≥20 分钟视为离线，其认领可被接管（接管方必须在自己心跳里写明接管原因与被接管 session 名）。
- 每轮开工、以及每次完成一个大动作（改完一个文件/跑完一个批次）都应刷新心跳。

## 2. 任务认领（防撞车）

- 文件 `D:\lovebrain-arena\.coordination\claims.jsonl`。动任何**会被别人也想要的东西**之前先追加：`{"ts","session","claim":"<范围，如 STATE_MAP / tools/state_map_check.py / logs/auth_budget/AUTH-xxx / FACT_CHECKPOINTS_V1 草案>","intent":"..."}`；完成追加 `"status":"release"`。
- 认领规则：某范围已有**在世**会话持有 → 本轮换一件事或等；离线会话的认领可接管。
- **永远串行的东西**：`logs\auth_budget\*.jsonl` 各账本（同 authorization_id 同时只允许一个会话动）、`STATE_MAP_20261025.md`、`tools\state_map_check.py`、任何授权文书文件。

## 3. 文件写入纪律（不争吵的底线）

1. 只新增文件永不冲突——优先"新建自己的输出文件"而不是"改公共文件"。
2. 必须改既有文件时：先 Read 当前版（不信缓存）→ 备份 `.bak-<日期>-<session名>` → 小步改 → 涉及 STATE_MAP/checker 的同步跑 `tools\state_map_check.py` 到全绿。
3. 看到目标文件 mtime < 20 分钟且不是自己会话写的 → **停手换事**，等它 release。
4. 绝不物理删除任何文件；绝不覆盖他人刚产出的文件；绝不把别会话在跑批次的输出目录当自己的输出位。
5. 例行会话（cron15）的输出：放自己新建的带时间戳目录，或 `evaluation\` 下带 `cron15_` 前缀的文件名，避免与主对话产物重名。

## 4. 分工倾向（减少重叠，不是硬边界）

- **主对话**：大牛执行书 `refs\experiment_protocol_20261023\knowledge_quality_independent_review_20261026\WORKER_NEXT_MAINLINE.md` 的主线大块（评审派发、判词回收、账本、执行书更新）。
- **cron15**：FACT_CHECKPOINTS_V1 / JUDGE_RUBRIC_V1 / PANEL_IDENTITY_V1 草案、`evaluation\BACKLOG_M1_TO_M2_20261026.md` 单件、评审复核指出的测量缺陷修复（字面命中率≠知识正确率、主体错换检测、C3 confirmed 库成员验证缺失）、只读核验报告。发现主线在做某件，立刻换下一件。
- 两边都不做：改题面、动已付费答卷、复活 CLOSED 账本、代做用户/大牛输入依赖项。

## 5. 冲突发生时

- 发现内容被覆盖：立即停止写入，从 `.bak` 恢复自己那部分到**新文件**，在 heartbeat 写 `conflict` 事件，等用户裁决；不得互相反覆盖。
- 协调文件本身被改坏：以 `.bak-` 最新版 + claims/heartbeat 记录为准重建，重建动作要在 heartbeat 登记。

## 6. 时间戳纪律（2026-10-27 01:24 新增，因实测异常）

- **写心跳/认领前，必须先用 Bash `date` 命令读取真实系统时间**（建议格式：`date "+%Y-%m-%dT%H:%M:%S%z"`），
  把读到的字符串原样写入 ts 字段。**禁止凭记忆估计、禁止取整到整分、禁止写"预期"的未来时间。**
- 起因：2026-10-27 巡检实测 cron15 会话心跳系统性超前约 +10 分钟（01:18 vs 本地 01:07、01:32 vs 01:22），
  直接破坏 §1 的 20 分钟判活口径。
- 判活兜底规则（在时间戳可信之前适用）：**mtime 优先**——判断某会话是否在世，以其最近一次文件写入的
  文件系统 mtime（`stat`/`ls -l` 实测）辅助校验；心跳 ts 与 mtime 矛盾时以 mtime 为准，并在心跳里登记
  `clock_skew_suspected` 事件。
- 交付完成即补认领 release 行；会话收尾 done 心跳与 release 必须成对出现，巡检员只登记缺失、不代写。
