<!-- historical worker document, sanitized; not current -->
# 交接文档 — W22-01 全量批收口（2026-11-04）

> 写给下一棒执行主控。上一棒（本会话）犯了严重错误：评审进程在 172/464 位死亡后未察觉，
> 干等了数小时。以下是完整当前状态和精确接续路径。

---

## 一句话状态

W22-01 全量批 live 464/464 已完成（零失败、prompt +1.77%），但**评审只跑到 172/464 就死了**，
进程已不存在。评审脚本支持从 partial.jsonl 续跑。**第一件事就是重启评审续跑，不要干等。**

---

## 已完成（不需要重做）

| 项 | 状态 | 证据 |
|---|---|---|
| W22-01 设计冻结 | ✅ | `long_probe_20261030/W22_01_ATTR_DESIGN_20261104.md` |
| 候选构建 uc_attr | ✅ | `candidates/window_expand_b_norecap_uc_attr/`（mechanism/attribution_rules.py 纯函数，零调用） |
| L0 三件 | ✅ | prompt 逐字节等价、contract 单测、mock replay 全绿 |
| 快车道 G0 | ✅ PASS | `long_probe_20261030/W22_FASTLANE_G0_VERDICT_20261104.md`（104 attempts 四门全过，触发面 34.6%） |
| 全量批 live | ✅ 464/464 零失败 | `logs/w22_full_live_20261104/`（10 故事 × 双臂 × 各轮） |
| 崩坏检测 | ✅ 已跑 | `long_probe_20261030/W22_FULL_RETRY_PLAN.json`（parent 4 unparseable / candidate 7 unparseable，0 empty） |
| 评审（部分） | ⚠️ 172/464 死亡 | `long_probe_20261030/LP_W22FULL_PARTIAL.jsonl`（停在 09:45，进程已死） |

---

## 未完成（你需要做的，按顺序）

### 第一步：重启评审续跑（最高优先，立刻做）

评审脚本 `tools/run_lp_review_generic.py` **支持从 partial.jsonl 续跑**——它会读已完成的 verdict 跳过，只跑剩余的。

**精确命令**（从 `<arena-root>/` 执行）：

```bash
python tools/run_lp_review_generic.py \
  --batch-dir logs/w22_full_live_20261104 \
  --stories benchmark/stories_v2 \
  --auth refs/experiment_protocol_20261023/global_harness_program_20261030/AUTH-W22-FULL-REVIEW-20261104-01.json \
  --tag W22FULL \
  --cand-name window_expand_b_norecap_uc_attr \
  --cards benchmark/stories_v2/wave3/GRADING_CARDS_W19_CROSS.json \
  --quote-check-dirs logs/w22_full_live_20261104
```

- auth 文件：`refs/experiment_protocol_20261023/global_harness_program_20261030/AUTH-W22-FULL-REVIEW-20261104-01.json`
- auth ledger：`logs/auth_budget/AUTH-W22-FULL-REVIEW-20261104-01.jsonl`（418 条已用）
- **用后台跑，但每隔 15 分钟检查 partial.jsonl 行数是否在增长**。上一棒就是没检查才浪费了一晚上。

### 第二步：评审完成后提取数据

从 `LP_W22FULL_NINEDIM_REVIEW.json` 提取：
1. **severe 位列表**（candidate + parent 各自的 severe positions）
2. **九维分域**（main-only 最差值、interact/emotion/initiative 各值）
3. **swap 诊断**（翻转计数 / 总 swap 数）
4. **引文硬校验**（`LP_W22FULL_QUOTE_CHECK.json`，脚本自动跑）

### 第三步：B3 亲读所有 severe 位

**这是不可协商的约束**（用户原始接管 prompt）：severe 必须 B3 亲读——主会话亲自读原文+评分卡+双臂回复+引文逐字核验，judge 不可信。

**主假设核心检验**：归因错位族 severe 计数 cand < par？

从已有 172 位的 14 个 severe 中，关键位已识别：
- **LRE121_T39（归因错位族，par severe）**：par 把 HER 的"看心情——骗她的，早茶我肯定去"错置为虾饺许诺 + 回请时间无据确定化。cand 未 severe。**主假设方向正面**。
- **E157_T06（cand severe）**：无据确定化（"暂停恢复"→"算废了别等了"）——非归因族
- **LRE121_T20（cand severe）**：无据确定化+编造安排——非归因族
- **LRE146_T16（cand severe）**：无据确定化+错时——非归因族
- 其余 11 位均为 par severe

**注意**：这只是 172/464 的结果，剩余 292 位可能有更多 severe。必须等评审全完再做统计。

### 第四步：G1-G5 判决

判据冻结在 `W22_01_ATTR_DESIGN_20261104.md` §2：

| 门 | 标准 | 已知 |
|---|---|---|
| G1 | 每域 cand ≤ par 且**归因错位族 severe 计数 cand < par**（主假设直接检验），不新增故障族 | 待评审全完 |
| G2 | 九维 main-only 最差 ≥−0.3，interact/emotion/initiative 各 ≥−0.2 | 待评审全完 |
| G3 | cand veto ≤ par+1；空回复按重试后口径 | 待评审全完 |
| G4 | prompt ≤+5%、无触发轮逐字节相同、0 额外调用 | **✅ prompt +1.77%、0 额外调用** |
| G5 | swap 翻转 → 不得宣布单冠军 | 待评审全完 |
| 触发面 | (10%,90%] | 快车道实测 34.6%，全量待确认 |

### 第五步：判决文书 + 三份状态文件收口

1. 写 `W22FULL_VERDICT_20261104.md`
2. 更新 `RESEARCH_STATE.json`（加 w22_full_verdict 段）
3. 更新 `TASK_QUEUE.json`（W22-01 状态 → DONE）
4. 更新 `WORKER_CURRENT_RUNBOOK.json`（latest_verdicts 加新判决）

---

## 关键路径速查

| 文件 | 路径 |
|---|---|
| 设计冻结 | `refs/experiment_protocol_20261023/long_probe_20261030/W22_01_ATTR_DESIGN_20261104.md` |
| 快车道判决 | `refs/experiment_protocol_20261023/long_probe_20261030/W22_FASTLANE_G0_VERDICT_20261104.md` |
| 批次配置 | `refs/experiment_protocol_20261023/long_probe_20261030/W22_FULL.BATCH_CONFIG.json` |
| 崩坏检测 | `refs/experiment_protocol_20261023/long_probe_20261030/W22_FULL_RETRY_PLAN.json` |
| Live 数据 | `logs/w22_full_live_20261104/`（10 故事 × 双臂目录） |
| 评审 partial | `refs/experiment_protocol_20261023/long_probe_20261030/LP_W22FULL_PARTIAL.jsonl`（172/464） |
| 评审 auth | `refs/experiment_protocol_20261023/global_harness_program_20261030/AUTH-W22-FULL-REVIEW-20261104-01.json` |
| 评审 ledger | `logs/auth_budget/AUTH-W22-FULL-REVIEW-20261104-01.jsonl`（418 条） |
| 评审脚本 | `tools/run_lp_review_generic.py`（支持 resume） |
| 引文校验工具 | `tools/quote_hard_check.py`（评审脚本自动调用） |
| 候选目录 | `candidates/window_expand_b_norecap_uc_attr/` |
| 研究状态 | `refs/experiment_protocol_20261023/global_harness_program_20261030/RESEARCH_STATE.json` |
| 任务队列 | `refs/experiment_protocol_20261023/global_harness_program_20261030/TASK_QUEUE.json` |

---

## 不可协商约束（用户原始接管 prompt，逐字保留）

- live 独占≈6 流；评审不与 live 并行；授权账本只由 AuthorizationBudget 写，先登记后派发
- no_retry/no_makeup，不为赢重跑；网关死了按事故登记→新 scope 整批重跑
- severe 必须 B3 亲读；目标族未实例化判决只写"无退化"；程序分与 fact_gold 分层读、不合并
- 未校准/低资格 judge 维只辅助；用户愿发（W 表）不代投；删除需用户确认
- 标题不动 benchmark/b_line/（ONLY_ONCE 盲测集）；题目封板不改题

---

## 上一棒的错误（避免重犯）

1. **评审进程死了没发现**：后台跑了评审，但在 context 压缩后丢失了对后台任务的引用，干等了数小时。
   **教训**：后台任务每隔 15 分钟检查 partial.jsonl 行数是否增长；不增长 = 进程死了 = 立即重启续跑。
2. **没有主动推进**：在有 172 位 partial 数据时就应该开始 B3 亲读已出的 severe 位，而不是等评审全完才动。
   **教训**：partial 中已有 severe 位可以边审边读，只要最终统计等评审全完再做。

---

## 用户常设授权

- AUTH-GLOBAL-CONTINUOUS-20261030-01：用户 2026-10-30 常设授权，地狱级连续工作无需逐次问许可
- "你自己理解吧，只要有未完成的任务就继续"：用户 2026-11-03/04 反复指令，有未完成任务就不停
- 管家账本：`<local-butler-tasklist-path>`

---

## 后续任务（W22-01 收口后）

| ID | 内容 | 状态 |
|---|---|---|
| W22-02 | 待规划 | PLANNED |
| W22-03 | 待规划 | PLANNED |
| W22-04 | 待规划 | PLANNED |
| W22-05 | 待规划 | PLANNED |
| W21-03 | memory/boundary 判别素材补建 | PLANNED |
| W21-04 | severe GT 扩容 | PLANNED |
| W19-06 | ENABLED（条件触发） | ENABLED |

按 LOOP_PROMPT（`WORKER_LOOP_PROMPT_20261103.md`）选下一批方向。
