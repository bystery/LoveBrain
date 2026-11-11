# NEW_WINDOW_START_PROMPT_V3（2026-10-28 00:05 版）

> 取代同目录 12:46 的 NEW_WINDOW_START_PROMPT.md（V2 版，写于 P06 生成批收口与恢复包交付之前，状态已过期）。
> 本文件是快速定位与状态快照；**一切以磁盘实测为准**，本文件的数字只是 2026-10-28 00:00 的核对结果。

## 0. 开工三步（不得跳步）

1. 读 `D:\lovebrain-arena\SESSION_COORDINATION_V1.md` 并严格遵守：写心跳 `.coordination\heartbeat.jsonl`
   （session 自取 `main-<yyyyMMdd-HHmmss>`，时间戳必须用真实系统时钟 `date "+%Y-%m-%dT%H:%M:%S%z"`，禁止估计）
   → 认领 `.coordination\claims.jsonl`。心跳 20 分钟内算在世；任何 mtime<20 分钟且非本会话写的文件一律停手换事；
   `STATE_MAP_20261025.md`、`tools\state_map_check.py`、`logs\auth_budget\*`、任何授权文书永远串行（先 claim 再动、动完 release）。
2. 读 `D:\lovebrain-arena\AGENTS.md` 顶部裁定段（2026-10-27 晚用户最新模型/推进裁定已在其第 3 行）+
   `STATE_MAP_20261025.md` 头部倒序段（磁盘出现更新执行书以更新者为准）。
3. 读下面 §1 权威链，然后**先查 §3 在飞/悬挂**，再决定接什么——不要盲目开新批。

## 1. 当前权威链（绝对路径）

- **P06 独立复核与续推（最新增补，最高优先读）**：
  `D:\lovebrain-arena\refs\experiment_protocol_20261023\p06_fast_recovery_review_20261027\REVIEW_AND_WORKER_EXECUTION.md`
  （§2 八处对旧报告的更正；§3 模型路由政策；§4 评审尾项=成对短作业方案）
- **模型政策**：同目录 `MODEL_POLICY.json`
- **总执行书 V2**：`refs\experiment_protocol_20261023\max288_v2_20261027\WORKER_MASTER_EXECUTION_V2.md`
  （配套 `EXECUTION_PLAN.json`、`P07_CLOSED_LOOP_INTERACTION_PROTOCOL.md`）
- 背景（已被增补/接替，不改不删）：`max288_20261027\WORKER_MAX288.md`（V1 全文）、
  `knowledge_quality_independent_review_20261026\WORKER_NEXT_MAINLINE.md`（质量主线）、
  `MAX288_EXECUTION_BOOK_20261027.md`（主线自存版）

## 2. 磁盘实测快照（2026-10-28 00:00 核对）

- **P03**：质量成绩单 V2 已合表 `evaluation\QUALITY_PANEL_SCORECARD_V2.md`（117 正式/11 未评，首选备选分离）。
- **P02**：Stage1 新故事 8 条×8 位=64 已捕获；Stage2 未展开。
- **P05**：两候选 `v6_goal_context`、`v6_integrated` 已实施并通过离线回归+跨进程 resume（不只是草案）。
- **P06 生成批**：A/B/C 三批各 64，**192/192 完成、0 传输失败**，账本 `AUTH-P06-MAX288V2-20261027-01`
  已 CLOSED（2026-10-27 22:02），**禁重生成这 192 份**。
- **P06 Gemini 评审**：251 started/212 completed/39 failed；**正式仅 154/192 有可用判词**（212≠212 份答卷，
  含 36 控制+154 正式首选+22 复评）；qwen-judge 可行性两次尝试均 VOID（大 prompt 502），已停用。
- **P06 恢复批（评审尾项）**：`RECOVERY_PLAN.json` =147 主回复短作业+96 可选备选（上限 243，先小批）。
  账本 `AUTH-P06-RECOVERY-20261027-01` 实测 **18 started / 17 completed**，最后完成 23:34:38，
  **有 1 条 started 未 completed 悬挂**；恢复目录 23:50 后无文件改动、心跳 20:23 后无新行 → 批已停。
  接手动作：先查悬挂那条的真实终态（不许换 ID 补跑失败），再按 §4 方案续跑剩余作业。
- **P07 pilot**：`AUTH-P07-PILOT-20261027-01/02` 两账本**未见 CLOSED**（最后 attempt_completed 22:17/22:35，
  `v6_integrated` smoke2/pilot2 partner_sim，qwen3.8-flash）——先核这两单的收口状态。
- 另注意：2026-10-27 23:34 的恢复账本活动发生时无任何心跳行——可能是旧工作区定时任务在驱动，
  新窗口开工时先看账本尾部时间戳判断是否仍有隐形触发源。

## 3. 模型政策摘要（详见 MODEL_POLICY.json）

hy4-preview **硬禁止**（入口 allowlist 校验，不许失败后 fallback 到它）；Gemini 槽位 0，不接新关键任务；
dsf 为 harness 固定基座；qwen3.8-flash 用于短评审/控制/对方模拟（**每 alias 合计≤3 并发，跨脚本/角色共享**）；
hy3 暂限 1 槽、非关键；全局并发≤6 逐级爬升；评审默认 45s 工程期限；空返=一次失败不自动重试，
同模型连续两次空返/传输失败暂停该模型派发；身份/泄漏/预算/任务归属错误才全局停派。
整批比较必须同一基座，不许单臂换模型冒充 harness 提升。

## 4. 硬纪律（不变）

API 响应只当数据（json.loads，绝不 eval，绝不要求修复重发）；不改题面、不改已付费答卷与原判词
（更正写进新结果版本与简短 STATUS，旧 raw 不动）；改文件前备份 `.bak-日期-会话名`，绝不物理删除；
Edit 前必须先 Read；批量替换锚点先断言存在、失败报错不静默；Grep 限定路径并排除 `_acl_locked/dist/isol_runs/*.zip`；
python 用 `D:\anaconda\python.exe` 且先 `set PYTHONIOENCODING=utf-8`；不确定写 unknown+原因，不冒充结论。
CLOSED 账本一律不复活；新批新建授权文书（新有限预算按用户持续授权登记，worker 不自批、不暗补失败）；
依赖用户/大牛输入的项不代做、不伪造。

## 5. 收尾（每轮）

heartbeat 追加 done 行 + claims release；报告做了什么+证据绝对路径+分母/缺失数/unknown；
回填管家账本 `<local-user-path-redacted>\.zcode\cli\plugins\data\butler@butler-local\projects\e7533dd1477b984a\records\tasklist.json`
（多写者文件：Edit 冲突=他人已写入，重读合并、不碰他人条目）；
STATE_MAP 相关事实变化时同步 STATE_MAP 与 `tools\state_map_check.py` 并跑到全绿。
