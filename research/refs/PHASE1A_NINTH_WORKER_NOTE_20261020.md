# 第八波判决执行记录（worker，2026-10-20）

> 范围：仅第八波判决 §3/§4 两处 P1 修复 + §5 版本标注纠正 + §7-3 判据登记。
> 按判决"不再做一轮泛泛全包审稿"，本记录只列本次改动与对应验收，不重述已签收项。
> 全程离线（mock/受控构造），**未调用真实模型**。

## 1. P1-1 后测事件"配对验证"补真实拒绝条件

改动：`tools/l3b_frozen_fork.py` `_verify_posttest_identity`（重写）。

新增拒绝条件：
- 事件日志逐行解析，坏行拒绝；
- 本 run attempt 事件按 `attempt_id` 分组：每组恰 1 started + 1 终态
  （completed/failed），终态与 started 的 case_id 一致（跨 case 配对拒绝），
  文件序与时间序上 started 先于终态；
- 覆盖检查：本 run 恰有 2 组且 case_id 恰为 L3B_t05/L3B_t06 各一次；
  其他 case 组（restore 记录补数）与多余组均拒绝；
- `manifest.audit_errors` 非空拒绝（审计写失败不得当作已验证成功）；
- `manifest.gateway_calls != 1` 拒绝（本次 beta_single 单阶段口径；
  多阶段调用须另立显式协议）；
- 配对终态 = failed → 拒绝，失败事件原文保留于 rejection.json
  （`checks.raw_attempt_events`），不删除、不悄悄重试。

验收：`tools/test_posttest_event_pairs.py`（新增）——空日志 / 孤立 started /
错误 case / restore-only / failed 对（保留验证）/ audit_errors / 多余组 /
跨 case 共 8 反例全拒绝并落 rejection.json；合法两节点四事件正例返回 2。
端到端：mock 冻结分叉 + `--freeze-from` 复用 + `--pairs 2 --order BA,AB`
交错全流程 posttest_identity_verified、报告正常写出（临时产物已清理）。

## 2. P1-2 结构门补空值与扩展字段值类型

改动：`arena/lb_scorer.py`
- `analysis_type_errors`：`scene_facts[].text` 必须 str——显式 null / 缺失键
  均判结构错误（此前 null 得 10 分、基线 `.strip()` 崩溃）。机制写回共用本门
  （baseline v1 `last_writeback_skipped` 留证、β_single notes 留证），
  非法记忆不提交、第二轮继续。
- `output_structure_errors`：已声明 `extra_text_fields` 值类型检查——
  合法字符串保持通过、显式 null（契约允许空值）放行、对象等非字符串判结构错误
  （`alternate={"text":…}` 不再 10 分）。
- 已核对全部 episode 指令 facts 生产者均带字符串 text（严格规则无误杀面）。

验收：`tools/test_output_structure.py` 扩充 D/E 组——null/缺失键单元反例
0 分格式失败；text=null 两轮端到端回放（基线不再崩溃、两候选第二轮正常、
写回跳过留证）；扩展对象拒绝、null 放行、字符串通过。

## 3. 版本标注纠正与判据登记（判决 §5/§7-3）

- worker 侧 v0.9 离线重评（工具 `tools/rereval_v09.py`，绑定冻结题面
  sha 7c26566239f64f6c…，未修改旧 manifest/scoring）：
  - baseline_L1：11/12（t01 json_schema_broken），均分 9.1667 →
    `logs/pilot_r3/rereval_v09_baseline_L1_20261020.json`
  - beta_L1：11/12（t06 json_schema_broken），均分 9.1667 →
    `logs/pilot_r3/rereval_v09_beta_L1_20261020.json`
  - 与独立复核数字一致。
- `refs/BLIND_SAMPLES_PILOT_R2.md`：分开标注**历史运行裁决**（运行时 manifest，
  旧评分版本）与**本次 v0.9 独立重评**（引用上述产物）；历史归档改标
  "历史归档，来源 v4 原题面，与当时 calls 核对"，不再表述为运行时快照。
- 新增 `refs/L3B_DEV16_PROTOCOL.md`：在第一条真实调用前登记 §6.3 语义判据表、
  16 尝试上限（`--pairs 4 --order AB,BA,BA,AB`，不自动补成功、重试另立计划）、
  报告结论上限与正式门禁独立性；采样开始后不改题/判据/成功定义。

## 4. 回归

七套测试 + 自检全 PASS：output_structure（含新增 D/E）、no_reply、
input_boundary、scoring_authority、memory_off、failure_persistence、
posttest_event_pairs（新增）、validate_pilot_timeline --selftest。

## 5. 下一步（待用户口令）

两处 P1 已修、判据已登记。16 次开发性尝试（`--pairs 4 --order AB,BA,BA,AB`，
冻结源 checkpoint sha256 3abc207c…）按 `refs/L3B_DEV16_PROTOCOL.md` 执行——
真实调用时间由用户决定。长期记忆、真人感与正式晋级继续独立验收。
