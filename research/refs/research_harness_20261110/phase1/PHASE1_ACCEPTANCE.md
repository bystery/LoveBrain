# RESEARCH-P1 Phase 1 验收核对表

最后更新：2026-11-11（G0 启动批）

规则：缺项如实列阻断，不把部分完成包装成全部通过。

## G0 启动门 — ✅ 达成（2026-11-11）

| # | 项 | 证据 |
| --- | --- | --- |
| 1 | 核实 GitHub 分支与文件 | `bystery/arena` 分支 `phase3-sync-20261110` HEAD `947770edad719877401b6c5883cbb064b0a22ec9`，克隆 `D:/tmp/arena-sync-20261107`（含 example-LoveBrain / example-guanjia / framework-guanjia / harness-lovebrain 四子项目，本线只动 harness-lovebrain 对应内容） |
| 2 | 保存原三阶段历史成果 | 冻结 manifest / SELECTION 结果 / 校准包 / 曝光账本原样保留（见 README 继承清单）；TASK_QUEUE.json 与 RESEARCH_STATE.json 修改前快照存 `_snapshots/` |
| 3 | 标记 SELECTION/HOLDOUT 曝光 | SELECTION：wave5_selection 40 篇 exposure.json 已有（11-10）；HOLDOUT：新增 `phase3/HOLDOUT_EXPOSURE_MARK_20261111.json`（E401–E423 段 4 臂消费登记 + 停写状态） |
| 4 | 研究目录与三线交付位置 | `refs/research_harness_20261110/phase1/{benchmark,literature,harness}/` 已建 |
| 5 | 启动 Benchmark Worker 与 Research Worker | 两个独立后台子代理（2026-11-11 派发） |
| 6 | 统一输入/输出/计量接口整理 | `harness/COMMON_INTERFACE.md`（本批完成差距分析） |

## G1 原型实验许可门 — ✅ 达成（2026-11-11，附两项第 0 级前置）

| # | 项 | 状态 | 证据位置 |
| --- | --- | --- | --- |
| 1 | 60 位 DEV 快速评估集已确定 | ✅ | `benchmark/DEV_FAST_SET.json`（sha256 主会话复核一致；规则固化可复现） |
| 2 | 无未来信息/Gold 泄漏 | ✅（集级） | DEV_FAST_SET `leakage_check: PASS_for_DEV_DIAGNOSTIC`（只含指针与元数据，不内嵌题面/gold）；候选输入边界的字段级白名单表并入每个原型的第 0 级静态检查（C1-a） |
| 3 | 主要评分维度已写明 | ✅ | `benchmark/COVERAGE_AND_VALIDITY.md`（universal_scorer v1 口径 + Q1–Q5 映射） |
| 4 | Direct 可比较基准结果 | ✅（探索性） | 复用 SELECTION 四臂 live DIRECT 结果（2026-11-10，同模型同协议）——标记为 exploratory 比较；不满足时新跑再登记 AUTH |
| 5 | Token 与延迟计量可用 | ✅ | four_arm_runner/universal_scorer/FOUR_ARM_SUMMARY 既有口径；usage 聚合与 retries 入账核验列入第 0 级检查（C2-a/C3-a） |
| 6 | Research Worker 真读 ≥5 篇外部论文 | ✅ | 7 篇 full-read（≥5 达标）；22 篇登记可核 URL（`literature/PAPER_READING_NOTES.md`） |
| 7 | ≥3 个研究依据、机制不同的可实现方案 | ✅ | 11 条候选（`harness/MECHANISM_REGISTRY.md`），差异审核 PASS；**实际实现首批（2026-11-11 勘误，以 EXPERIMENT_LOG 为准）**：RH-B-04 证据锚定排布 / RH-B-06 小步快答两档级联 / RH-B-09 确定性抽取瘦身（三类机制互异且可独立 v2 实现）；初稿曾写 RH-B-04/03/02，实现时改为 04/06/09——原因：RH-B-03 在轻量契约上实现会构成稻草人（其假设针对复杂契约的内部冗长字段）、RH-B-06 比 RH-B-02 更可独立落地（白名单升级门零训练），改动已记录于 EXPERIMENT_LOG 选型说明，非静默更换 |
| 8 | API 配置与密钥安全检查 | ✅ | provider_check OK；Key 环境变量读取零落盘 |

**G1 判定：达成 → 按宪章立即开始首批原型实验（W28-05）。** 第 0 级静态检查（泄漏白名单 + usage 聚合核验）在每个原型冒烟前执行，不构成 G1 阻塞。

## G2 第一大阶段验收门 — ⏳ 未开始

全部项见宪章第四部分。当前登记两条已知缺口（透明起见）：

- 人工偏好锚点包（用户 40 锚点）尚未收集 — 用户侧动作，AI 不代投（W04-05 既有登记）。
- 20 篇可读聊天示例（CHAT_REVIEW.html）未开始 — 排在 DEV_FAST_SET 之后。

## 本批 AUTH 登记

- 批次：RESEARCH-P1-G0-20261111-01
- 目的：宪章落盘、三线启动、统一接口差距分析、曝光标记、状态登记
- 模型 API 调用：**0**（文献检索走通用联网搜索；DEV 集本地构造）
- 下一次 AUTH：G1 达标后的首批原型冒烟（预算届时按 C6 第 1 级规模登记，24 位 × 臂数）
