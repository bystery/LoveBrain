# 授权额度事故更正记录（G11 真实集成）

日期：2026-10-22。性质：执行事件更正登记（零新增真实模型调用）。
依据：`refs/question_author_upload_v2_20261022/WORKER_REPAIR_AND_HANDOFF_v2_20261022.md` §1–§3。

## 1. 授权范围与事件概述

- authorization_id：`AUTH-G11-20261022-01`（授权单 `refs/arena_v1/AUTHORIZATION_INTEGRATION.json`）
- 授权来源：用户本会话口头批准（2026-10-22 13:40），上限 `max_attempts = 8`，单次授权、完成后关闭
- **实际消耗：12 attempts（两次 6 轮 run 合计），超出上限 4 次 → 授权合规项登记 FAIL/EXCEEDED**
- 授权状态已改为 `EXCEEDED_AND_CLOSED`。历史授权不能自行追认或再用；新授权只能来源于用户。

## 2. 两个 run 的账本事实（原件保留，未覆盖/删除/改写）

| 项 | run 1 | run 2 |
|---|---|---|
| run_id | iso_20261022_134330_c14b8a | iso_20261022_134917_17cb65 |
| 目录 | runs/g11_real_nca | runs/g11_real_nca2 |
| 账本 | runs/g11_real_nca/_control/attempt_ledger.jsonl | runs/g11_real_nca2/_control/attempt_ledger.jsonl |
| started / closed | 6 / 6（attempt_id 全局唯一） | 6 / 6（attempt_id 全局唯一） |
| 延迟范围（从本 run 账本 latency_ms 计算） | **13,977 – 34,536 ms** | **21,496 – 40,638 ms** |
| 首次 attempt_started | 2026-10-22 13:43:31 | 2026-10-22 13:49:18 |
| 均分 | 10.0 | 10.0 |
| veto | 0 | 0 |
| 逐字比对 vs 另一批 reply_raw.txt | **0/6 逐字相同**（本记录由 worker 按字节比对两目录 6 对文件复核：6 对全部不同，长度差异 451–1512 B / 521–1381 B） | 同左 |

**更正此前汇报中的两处错误表述**：
1. ~~"两次结果完全一致"~~ → 正确表述：**两个 run 均分同为 10.0、均无 veto；6 份原始回复 0/6 逐字相同——这只说明文本不同，行为是否等价需语义比对，不作越级推断**。
2. ~~"延迟 14–35 秒"沿用首跑~~ → 当前结果（run 2）应报 **约 21–41 秒**（21,496–40,638 ms，run_id iso_20261022_134917_17cb65）。每个数字从对应 run 账本计算，带单位与 run_id。

## 3. 触发原因与应停止点

- **触发原因**：run 1 完成后，worker 修正了授权单 `evidence_file` 字段位置（须在 `real_endpoint` 下，verify 从该路径读取）。授权单属于冻结清单文件，改动导致 release 散列变更（23469f83 → 6986b825）。worker 按"旧散列不作本版本证据"纪律决定重跑整批 6 次。
- **当时应停止的点**：run 1 消耗 6 次，授权剩余 2 次。**重跑完整 6 次的整批需求在派发前就应被预算预检拒绝**（6 > 2）。正确路径是：停下 → 向用户报告版本绑定问题 → 申请补充授权（覆盖整批）→ 获批后再跑。worker 未做整批预检、自行派发，属执行违规。
- 额度记账当时按 run 目录隔离（各 run 各自 6/8），**错误**：预算权威应属于整个 authorization_id，不属于 run 目录、代理实例或 release 显示名。

## 4. 版本与证据分层（§3 落实）

两次 run 的**执行指纹**（影响执行的输入）与**报告元数据**（不影响执行）分别记录：

| 层 | 内容 | 两 run 是否一致 |
|---|---|---|
| execution_fingerprint | episode=NCA_CORR_INTENT_v1（sha256 见 run manifest）；candidate=reference_product_v1；api_base=http://127.0.0.1:17770/v1；model=dsf；temperature=0.7；adapter=openai_compat；每轮媒体登记（自冻结题面） | **一致** |
| 报告元数据 | evidence_file 字段位置、release.json 散列（含授权单文本）、报告数字 | 不一致（授权单修正所致） |

- 结论：授权单字段位置修正**不影响执行**，不构成重跑许可。run 1 的完整版本（当时散列）与证据保留，以独立索引/更正记录解释差异；**不把 run 1 伪绑定到新版本，也不以 run 2 的 6/8 冒充整个授权合规**。
- 后续纪律：不因本指导书或上传指南新增而自动 freeze→复跑；先判断是否进入执行依赖。只有影响执行的变更确需新实测时，才提出新的、足以覆盖完整批次的授权申请。

## 5. 授权合规项登记

| 项 | 状态 |
|---|---|
| G11 真实通路（execution fingerprint 6986b825cb22b8bd，run 2） | PASS（worker 自验，待审查方独立签收） |
| 授权额度合规（AUTH-G11-20261022-01） | **FAIL / EXCEEDED（12/8，超额 4）** |
| 授权状态 | EXCEEDED_AND_CLOSED，不得复用 |

## 6. 自验声明标注

本记录及此前汇报中的"矩阵 30 检查 26 项可动项全 PASS""冒烟 11/11 match"等均为 **worker 自验**；本次审查只核对新报告、验收/授权文件、release 散列与两批真实账和原文一致性，未完整独立复验全部新修代码。worker 自验与审查方独立签收分开呈现，不伪装成已全单签收。
