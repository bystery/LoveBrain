# PHASE1_ACCEPTANCE · 第一阶段验收清单（对照指导书 §10 逐项）

| # | 验收项 | 状态 | 证据 |
|---|---|---|---|
| 1 | 已读取治理文件与运行器，明确未修改范围 | ✅ | SOURCE_AND_SCOPE.md；UNTOUCHABLE_HASHES.json(72 文件) |
| 2 | 已核对仓库真实 commit 与目录映射 | ✅ | 起点由ster/arena@75ab97e（ls-remote 实测）；映射 harness-lovebrain/ |
| 3 | 208/2,944/221/3,102/6,603 口径可复算 | ✅ | `python -I tools/benchmark_v1/inventory.py`（脚本实测，非抄 PAPER） |
| 4 | 十专题分类有实际数字/来源/不确定标记 | ✅ | DATASET_INVENTORY.csv(annotation_basis/confidence)、TOPIC_COVERAGE_MATRIX.csv；高/中/低置信 39/17/152 如实 |
| 5 | B 线及关联来源已排除于开发运行 | ✅ | capture 校验 b_line_and_sealed_excluded PASS；pilot 政策门 |
| 6 | 长剧/反事实/母本谱系已建立 | ✅ | 26 组 lineage_group_id（LINEAGE_LEAKAGE_REPORT.md） |
| 7 | 旧题真实可复用数量不凭预算冒充 | ✅ | DATASET_REUSE_AUDIT.md：45 可用，缺口 115（"160 不可信"结论） |
| 8 | 四组身份与源码哈希冻结 | ✅ | ARM_REGISTRY.json；capture candidate_hash_pinned PASS |
| 9 | Direct/CoT 模板已实现并版本化 | ✅ | 两候选 assets+mechanism（COT 行哈希随 mechanism.py） |
| 10 | Legacy 运行完整旧 Harness 非 prompt 替身 | ✅（附重大发现） | r9_slim 全生命周期运行；wave3+ dict persona 崩溃如实登记（BASELINE_IDENTITY_REPORT.md） |
| 11 | CURRENT 长短域分流正确 | ✅ | resolve_current_candidate 单测 + plan 分流表（E032/E037/E191→guard，LRE117→uc） |
| 12 | 四组输入公平、无未来信息泄漏 | ✅ | capture: direct_cot_input_equivalence / no_future_turn_leak / no_evalside_gold_leak 全 PASS |
| 13 | 首选回复按固定规则归一化 | ✅ | normalize_v1 + 反挑拣单测（test_normalize_no_cherry_pick） |
| 14 | 真实 API 模型 ID 已核对、未擅自替换 | ✅（BLOCKED） | provider_check_v1：deepseek-flash-2 未核验成功即 BLOCKED，未用其他模型 |
| 15 | Key 未进入提交文件或日志 | ✅ | redact 单测 + run 目录扫描 PASS |
| 16 | mock/capture/离线单测通过 | ✅ | 14/14 pytest；capture 9/9 检查 PASS |
| 17 | Pilot 使用指定四条已暴露旧题 | ✅ | PILOT_CASE_MANIFEST.json（custody EXPOSED 核验）；E032/E037/E191/LRE117 |
| 18 | 实际生成调用未超 320 | ✅（0 次真实调用） | LIVE BLOCKED 登记在案；mock 0 网络请求 |
| 19 | Pilot 每组 completion/usage/失败/时延落盘 | ✅（mock 口径） | PILOT_RESULTS.csv(236 行)+gateway_events.jsonl；live 待补跑 |
| 20 | 已产出可填的匿名盲评包 | ✅ | PILOT_BLIND_REVIEW.md（12 位全可用）；映射不进交付目录 |
| 21 | 未将 AI judge 冒充人工偏好 | ✅ | 全部 NOT_COLLECTED；未做任何 judge 判决 |
| 22 | Phase 2 建题与评分计划具体可执行 | ✅ | PHASE2_EXECUTION_QUEUE.md(Q0–Q8) + 三个计划文件 |
| 23 | 论文方法章节骨架建立 | ✅ | PAPER_V1_OUTLINE.md（§6/7 空骨架+禁止填入声明） |
| 24 | 无既有题库/Gold/B 线/harness 未授权改动 | ✅ | test_untouched_integrity 全绿 |
| 25 | 已同步 GitHub 并核验新 commit 可读 | ⏳ | 本次推送后回填 SHA（见 PHASE1_README 更新） |

## 偏离与诚实声明

- **live pilot BLOCKED**：Key 未配置（旧 key 泄露须用户撤销重发）。全部离线工作照常完成，
  未用 mock 冒充真实成绩——PILOT_EXECUTION_REPORT 明确标注 mock 伪影。
- **LEGACY 有效位 20/72**：wave3+ dict persona 与 r1 运行时闭包不兼容（真实缺陷，非本批引入）；
  未修改历史 harness，处置选项交 Phase 2/用户。
- **mock 下 harness 组 veto 为台本伪影**、tokens 为字符估算——均在报告头与指标定义中显式声明。
