# PHASE3_ACCEPTANCE · Phase 3 验收矩阵（对照指导书 §P3-13）

> 规则：每行给出要求/状态/证据/缺口；**判定不得在条件未满足时上调**；
> DONE=有可核验证据；PARTIAL=部分完成；BLOCKED=有实际阻断；NOT_TESTED=尚未验证；
> N/A=本轮不适用。状态计数随步骤推进刷新（本文件由收口会话整体复核后定稿）。

## 一、数据集

| # | 验收项 | 要求 | 状态 | 证据 | 缺口 |
|---|---|---|---|---|---|
| 1 | 240 篇主数据集 | 唯一 Episode、十专题配额完整 | PARTIAL | 已实建 123（DEV 83 + SELECTION 40）；待建 120（HOLDOUT） | 120 HOLDOUT（金库目录已建，名字池已修复 120/120 唯一） |
| 2 | 80/40/120 三分区 | 分派表闭合、guard 通过 | DONE | `DATASET_V1_ALLOCATION_V3.csv`（243 行）+ guard 0 违例（--expected-pending 120，V3 路径）；E339-E378 split 回写 SELECTION/BUILT_VALIDATED | HOLDOUT 120 建成后回写 |
| 3 | 五件套/轮卡/原子/来源/回验 | 每篇全链 | DONE（123 范围）/PENDING（120） | wave4：1,350 原子 strict 全命中；wave5：1,693 原子 strict 全命中、validate 40/40 PASS | HOLDOUT 同标复验 |
| 4 | 8 组反事实孪生 | 同组同分区、唯一扰动 | PARTIAL | G1/G2 DEV 已建；G3/G4 SELECTION 已建同构复核（E375-E378）；G5-G8 HOLDOUT 已分派（全在 H12） | G5-G8 待生成 |
| 5 | 无重复/无跨区谱系泄漏 | dedupe+lineage | DONE（123 篇） | dedupe_wave 223 篇 0 意外近重；lineage 0 违例（V3 split map） | HOLDOUT 复跑 |
| 6 | HOLDOUT 无消费 | once-only | DONE（当前） | 金库 120 篇空位已建目录+VAULT_README 纪律+runner 双份正文反查 | 生成后至开跑期间保持 0 |
| 7 | 账本与暴露同步 | 真实消费逐篇登记 | DONE | CUSTODY_EXPOSURE_RECONCILIATION（1,163 attempts 闭合）；wave5 40 篇 custody 待 seed（guard 40 警告不阻塞） | HOLDOUT 消费事件随跑随记 |
| 8 | Gold/评分版本固定 | 冻结哈希 | PARTIAL | universal_scorer_v1/semantic_guards_v1/gold_diag_v0 并存；build_freeze_manifest.py 已就绪（123 篇哈希可算）；ARM_REGISTRY_FROZEN 已落盘 | 第 3 步三层哈希（HOLDOUT 建成后） |

## 二、实验

| # | 验收项 | 要求 | 状态 | 证据 | 缺口 |
|---|---|---|---|---|---|
| 9 | 四组可真实运行 | 同模型/同输入 | DONE（基建）/NOT_TESTED（正式） | phase3_runner 四组 mock 冒烟全过；LEGACY_COMPAT_R9 完整 12 轮生命周期 | SELECTION/HOLDOUT 正式实跑 |
| 10 | Legacy 兼容身份明确 | 双录哈希/不称原版 | DONE | ARM_REGISTRY_FROZEN.json（LEGACY_COMPAT_R9+original 双哈希；adaptation_note） | — |
| 11 | 通用评分不偏向 JSON 结构 | 双层口径 | DONE | SCORER_PARITY_REPORT：1,104 位离线重评（DIRECT 1.0/CURRENT 0.9956，假象消除） | 正式批沿用 |
| 12 | 客观失败证据 | 通用层指标+守卫 | DONE（机制） | SEMANTIC_GUARD_CONTROLS 105/105；marker 通道根因修复+批 2 起强制 markers | 正式批实证 |
| 13 | Holdout 只跑一次 | once-only | NOT_TESTED | runner once-only 政策门实现+测试 | 第 8 步执行 |
| 14 | API 请求与成本记录完整 | 全局账本 | DONE（基建） | GLOBAL_ATTEMPT_LEDGER（reserve/register 测试过）；usage/时延落盘沿用 lb_gateway | 正式批实数 |

## 三、研究可信度

| # | 验收项 | 要求 | 状态 | 证据 | 缺口 |
|---|---|---|---|---|---|
| 15 | 人工偏好真实填写 | NOT_COLLECTED 纪律 | PARTIAL | 盲评包工具（60位×3对，seed 固定）；40 校准锚全真实回复已备 | **用户填写（唯一外部依赖）** |
| 16 | Judge 资格真实复核 | 不得扩权 | DONE | JUDGE_CALIBRATION_V2：仅 interact=full；time/emotion/initiative/factuality 降级；recompute 脚本可复算 | 用户锚点回填后按维晋级 |
| 17 | 不可靠维度不擅自扩权 | 禁判死清单 | DONE | 同上 §2 操作后果三条 | 持续遵守 |
| 18 | 统计方法预先冻结 | episode/组聚类+Holm | DONE | stats_phase3 v1（B=2000，seed 20261109）；EXPERIMENT_PROTOCOL_FROZEN_V1 §P3-4.5 | 冻结生效章待第 3 步盖章 |
| 19 | 有效分母 | N/A 不计通过失败 | DONE（口径） | universal_scorer summarize（N/A 单列）；SEMANTIC_GUARD_CONTROLS_REPORT §4 | 报告执行 |
| 20 | 负面结果如实披露 | 不化妆 | DONE（纪律） | 本矩阵 PARTIAL/PENDING 如实；协议 §0 原则照录 | 持续 |
| 21 | 开发集不冒充独立泛化 | 分区纪律 | DONE | 政策门按 split manifest；曝光账本修正完成 | HOLDOUT 结果只来自第 8 步 |

## 四、工程

| # | 验收项 | 要求 | 状态 | 证据 | 缺口 |
|---|---|---|---|---|---|
| 22 | 现有 harness/评分器不被擅自修改 | 零改动 | DONE | arena/ 与 lb_scorer 零改动（新工具并存）；UNTOUCHABLE 测试在套件内 | — |
| 23 | 旧结果不覆盖 | 版本分离 | DONE | phase2 表/账本原地保留；账本修改前自动备份 | — |
| 24 | 测试全绿 | 离线套 | DONE | 64 passed（Fast Gate）；新增 25 项 phase3 工具测试；UNTOUCHABLE 哈希重钉 15 文件（GOLD_SIDECAR + build_gold_sidecar，repin_log 登记） | HOLDOUT 工具扩展随加随测 |
| 25 | 关键文件 SHA | 冻结哈希 | PARTIAL | ARM_REGISTRY_FROZEN（registry_sha256=ade9f265…）；DATASET_V1_FROZEN_MANIFEST 已算 123 篇三层哈希（HOLDOUT 待补）；UNTOUCHABLE 重钉完成 | HOLDOUT 建成后补算 + 第 3 步冻结 |
| 26 | GitHub 同步且远程可读 | SHA+远端验证 | PENDING | 第 10 步 | — |
| 27 | 复现说明可重建统计 | reproduce | PARTIAL | RUNBOOK_STEP7_8（命令级）；REPRODUCTION_GUIDE 待第 10 步 | — |

## 五、论文

| # | 验收项 | 要求 | 状态 | 证据 | 缺口 |
|---|---|---|---|---|---|
| 28 | 构造方法完整 | 十专题/样例 | PENDING | 第 10 步（数字全部来自冻结结果） | — |
| 29 | 四组对照结果 | 实测 | PENDING | 第 7/8/9 步 | — |
| 30 | 统计不确定性 | CI/Holm | PENDING | stats_phase3 就绪 | — |
| 31 | 局限与边界 | 诚实清单 | PARTIAL | SCORER_PARITY §5 / CONTROLS §3 / CALIBRATION §2 已登记实验层局限 | 论文层汇总 |
| 32 | 安全公开展示 | 脱敏/封存 | PARTIAL | HOLDOUT 金库机制；公开快照审查在第 10 步 | — |

---
**当前判定：PARTIAL（第 1/4/5 步 DONE；第 2 步 SELECTION 40/40 机械链全绿、独立审计 C1-C4 进行中；第 2b/3/6/7/8/9/10 步 PENDING）。**
