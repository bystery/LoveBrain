# Phase 1 · 基线身份报告（BASELINE_IDENTITY_REPORT）

> 四组身份在运行前冻结（哈希见 `ARM_REGISTRY.json`；由 plan 模式生成并经 capture 校验"哈希固定"检查）。

## 四组定义

| arm_id | 名称 | 代码来源 | 身份要点 |
|---|---|---|---|
| DIRECT | Direct | `tools/benchmark_v1/baseline_candidates/direct_baseline`（Phase 1 新建轻量候选） | 最小任务提示+可见信息+轻量契约 `{action,recommended}`；自累积可见真实聊天历史；无 LoveBrain 记忆/阶段/风格机制；单次调用 |
| COT | CoT-style | `.../cot_baseline` | 与 DIRECT 逐字同构（自包含副本），唯一差异=system 末尾一行固定思考提示（`mechanism.py` COT_INSTRUCTION，指导书原文）；出站白名单无 reasoning 开关通道 |
| LEGACY | Historical Harness | `candidates/action_policy_r9_slim` | 完整旧 harness 生命周期（init→装配→记忆→调用→解析→状态更新），**非 prompt 替身**；运行时闭包 r7_supersede→r2→r1→v6_goal_context→…→reference_product_v1 |
| CURRENT | LoveBrain Harness | 双域冠军，按故事总轮数**运行前**分流 | ≤24 轮→`window_expand_b_norecap_guard`；>24 轮→`window_expand_b_norecap_uc` |

本批 pilot 的实际分流：E032(12)/E037(8)/E191(12)→guard；LRE117(40)→uc。
`reference_product_v1` 登记为可选第五组（原版产品参考），Phase 1 不入主对照（保真边界见 `refs/arena_v1/BASELINE_PARITY.md`，"整体优于原版仍未证明"为其原文）。

## 身份哈希与核验

- 每候选身份哈希 = 目录内全部文件（排除 `__pycache__`/`*.pyc`）的 (relpath, sha256) 有序清单的整体 sha256（`four_arm_runner.candidate_identity_sha256`）。
- capture 校验按 (arm×episode) 的实际路由候选重算比对（CURRENT 双域分流下按篇取对应候选）——9 项检查全 PASS。
- LEGACY 不得引用旧实验分数：本批 LEGACY 成绩全部来自本轮实际运行（见 PILOT_EXECUTION_REPORT.md）。

## 重大运行发现（LEGACY × wave3+ 剧集不兼容）

- 现象：LEGACY 在 E191/LRE117（wave3/wave3_ext，dict 型 persona）初始化即崩溃：
  `AttributeError: 'dict' object has no attribute 'strip'`，堆栈位于 r1（运行时闭包成员）
  `mechanism.py:270 _seed_facts` —— `((persona or {}).get(key) or "").strip()`。
- 根因：wave3+ 题库升级为结构化 persona（dict），r1 时代代码只接受字符串 persona；
  r9_slim 历史批次只跑过 stage3 及更早（字符串 persona）素材，从未暴露此边界。
- 处置（Phase 1）：**如实登记，不修历史 harness**（修改会改变 LEGACY 身份并触碰
  "不修改既有 harness 机制"纪律）。LEGACY 本轮有效产出 = E032/E037 共 20 位。
- Phase 2 选项（见 PHASE2_EXECUTION_QUEUE.md Q2）：①LEGACY 对比限定在字符串 persona
  时代的剧集；②显式登记最小兼容补丁+身份重注册（哈希更新+勘误说明）后重跑。
- 同场对照组不受影响：CURRENT/DIRECT/COT 在全部 4 篇上完整运行（72/72/72/72 中
  DIRECT/COT/CURRENT 均 72 位完成，LEGACY 20 位）。

## 输出契约差异（设计使然，非缺陷）

- DIRECT/COT：轻量契约 `{action, recommended}`（指导书 §3.2）；harness 评分器协议键
  `{action,response,directions,analysis,decline_reason,reason}` 不含裸 `recommended`，
  其 veto 对轻量臂**不适用**（记录为 `scorer_veto` 审计字段，不判死）。
  判据=归一化规则（normalize_v1，指导书 §3.5）。
- LEGACY/CURRENT：LoveBrain 契约；`lb_scorer` veto 即契约失败。
