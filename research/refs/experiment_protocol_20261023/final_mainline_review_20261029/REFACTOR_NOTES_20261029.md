# 全局优化审计（2026-10-29 收尾）

站在全局回看这条主线的实现，逐条列出"哪里不优雅 / 影响什么 / 建议怎么改 / 本批动没动"。
原则：**证据产生器不返工**。已经产出付费结果的脚本、账本、答卷，字节改了就没法复现"当时到底是哪段代码跑出来的"，所以多数项只登记不动手；真正值得马上做且零风险的，我用新增文件的方式做掉。

## A. 本批已经做掉的整洁化（零/低调用，不破坏既有证据）

1. **F5 全套脚本参数化落地**：`tools/build_f5_authorization.py`、`tools/run_f5_recheck.py`（自校验 `LB_API_BASE/LB_MODEL` 与授权单一致，杜绝"忘了设环境变量导致 live 直接报错"）、`tools/build_f5_blind_materials.py`（`--swap/--suffix` 一份脚本同时出正向与换序材料，不再手改常量）、`tools/run_f5_judge.py`（`--materials/--aid/--cap/--auth/--purpose/--experiment`，不再每个 phase 复制一份 100+ 行）、`tools/analyze_f5_judge.py`（正向+换序一致性一份脚本算完）、`tools/freeze_f5_identity.py`（身份还原校验 + 新身份冻结 + 代码逐字节归属证明，三件事一次做完，缺一条直接 refuse 不落盘）。
2. **汇总层单一口径**：`tools/build_f6_tables.py` 的裁判控制对判定**显式复用** `analyze_f1_blind.py` 的口径（`tie_or_uncertain` 记对），并在输出里写 `criterion` 字段。我之前差点把 16/16 报成 14/16，就是两份实现口径漂移的典型后果。
3. **成本口径分桶且不重叠**：`tools/build_f6_cost_index.py` 把 token 分成"账本 usage 侧"（裁判 + F4 生成）与"答卷 calls.json 侧"（F3/F5 生成，长故事只数终链去重），并写明 F3 若按目录全计数会虚高到 1,425,755。以前手工拼数字最容易在这里出错。
4. **状态与勘误可重放**：`tools/update_state_errata.py` 只增键不删旧键，改前先备份 `STATE_AND_ERRATA.json` 到工作区；阶段结论与 9 条勘误从此可由脚本重生成，不再手改 JSON。
5. **身份纪律补救**：F5 一度把 E3 直接写进已冻结的 `action_policy_r4_guard_final`。已按冻结记录逐字节还原（重算身份一致），并另注册 `action_policy_r4_guard_e3`；复测证据通过 staged 副本 8/8 逐字节比对归属新身份，没有为改名重复花一次额度。

## B. 登记但**故意不动**的不优雅（动了会伤证据可复现性）

1. **裁判脚本三份近重复**：`run_f1_blind_review.py` / `run_f3_judge.py` / `run_f4_judge.py` 的主体（预算、热身失败即停、并发写盘、schema 校验）几乎一样，只差材料、额度与 addendum。建议抽 `tools/blind_judge_lib.py`（材料读取 + attempt 纪律 + 写盘），各 phase 只留 30 行配置。本批不动：这三份分别是 F1/F3/F4 付费结果的执行入口，`F*_AUTHORIZATION_*.json` 的 `execution_entry` 指向它们。
2. **聚合脚本三份近重复**：`analyze_f1_blind.py`、`analyze_f3_judge.py`、`analyze_f5_judge.py` 的九维累加、胜负解臂、major 统计重复。建议合 `tools/blind_aggregates.py`，参数化"解臂来源=bindings"。
3. **`run_f3_new_material.py` 结尾关单崩在 `ModuleNotFoundError: no module named 'arena'`**（从仓库根执行时未把 `arena/` 加进 sys.path）。该批授权单当时是手工关的，账本口径不受影响。建议：脚本头部统一 `sys.path.insert(0, os.path.join(ROOT,'arena'))`（`run_f5_recheck.py` 已按此写法）。本批不改该文件，因为它是 F3 144 次调用的执行入口。
4. **F3 `DONE.json` 留着 `failed_resume:1` 的历史计数**：那是编排器整批预检失败的记录，之后我以 `--expect-attempts 12` 手工续跑把 K02 候选补到 24/24，8 条链终态齐全。不改 `DONE.json`，改用新增说明文件（见 C 项）。
5. **iso_run 的 live 依赖环境变量**：端点/模型写在授权单里，却要求进程环境也设 `LB_API_BASE/LB_MODEL`，二者不一致时以报错收场。建议 `iso_run` 直接从授权单注入环境（并在文档写明"环境变量只是覆盖项"）。
6. **续跑的额度语义**：控制器按"整批 24"预检，不看续跑只需 12。`--expect-attempts` 是绕过手段，正解是让 `begin_run(resume=True)` 用 `turns_total - turns_done` 做 batch_need。
7. **复制 run 树导致 attempt_id 冲突**：F4 首轮 8 个 on 单元被网关以重复 id 拒派发（0 调用）。`run_f4_component_value.py` 已写唯一 `fork_run_id/run_id`；更彻底的做法是控制器在 resume/stage 时自动派生新 run_id 并保留 `parent_run_id`。
8. **`AuthorizationBudget.close()` 要求 scope 与首条记录逐字一致**：iso_run 写的 `authorization_scope` 是 `null`，任何带 scope 的关单对象都会被"范围不可变"拒。建议加 `AuthorizationBudget.close_static(aid, reason)`：从账本自读 scope 再关。本批用"先读回 scope 再 close"的两行替代（已在 F4/F5 关单里用过），不改 `arena/isolation/authorization_budget.py`——它在 `SHARED_FROZEN_FILES` 里，是量尺与预算的冻结源。
9. **盲材与实跑素材两处来源**：`benchmark/episodes/*.json` 与 `benchmark/stories_v2/final_20261029/*/episode.json` 内容同源、仅缩进/换行不同（已按 sort_keys 规范化散列逐篇验证相等）。建议实跑与盲材统一读 stories_v2 目录，删掉前者。
10. **长故事 token 去重靠约定目录**（`*_resume` 才算终链）：一旦命名变例会静默虚高。建议在 `RESULT.json` 里落一份 `final_chain_dirs` 清单，成本脚本只读它。

## C. 新增的收尾件（本批落地）

- `F3_RESUME_NOTE.json`：把"编排器预检失败 → 手工 --expect-attempts 12 续跑 → 8 链终态"的完整经过与终态 checkpoint 数字写成独立文件，避免误读 `DONE.json`。
- `tools/check_identity_drift.py`：身份漂移探测器——对给定候选重算身份并与冻结记录逐文件比对，任何 in-place 改动立刻可见。F5 的教训就是缺这个（我写完 E3 才想起该先建分支）。
