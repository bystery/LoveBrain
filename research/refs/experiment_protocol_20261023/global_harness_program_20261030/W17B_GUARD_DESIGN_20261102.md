# W17b 输出契约守卫设计（2026-11-02 冻结，跑前不改）

批：`BATCH-W17B-GUARD-20261102`｜授权：`AUTH-W17B-20261102-01`（cap 144）
臂：parent = `window_expand_b_norecap`｜candidate = `window_expand_b_norecap_guard`
素材：E189/E190/E195/E199/E207/E208（与 W17 同六篇；**已见域**，判决须标注）
规模：6×12×2 = **144 attempts**；评审 72 位

## 1. 动机

W17 判决（`W17_MERGE_VERDICT_20261102.md`）确认：b_norecap 在 ≤24 轮已见域语义不差
（九维最差 −0.071、memory +0.132），但输出契约违规 **16/72 vs 4/72**（自造 `bad_but`/`bad_bo`/
`bad_young`、directions 数量不符、1 例空 response），属可用性退化。父本 r9_slim 有机制层
契约守卫（`output_repair` 家族 + 确定性修复），b_norecap 没有。

## 2. 候选与单变量

`window_expand_b_norecap_guard` = b_norecap 原样 + `mechanism/contract_guard.py`
（`_vendor/output_repair.py`、`normalize_reply_contract.py` 逐字节取自 r9_slim）。
只改"模型回复返回前"的确定性修复，不改 prompt、不改历史预算、不改注入、零额外模型调用。
无修复需求时逐字节返回原文本（prompt 等价已验：E189 12/12 轮逐字节相同）。

**离线预演（跑前证据，非 live）**：把守卫回放到 W17 已保存的 72 条 candidate 原始回复上，
用 `arena/lb_scorer.run_case_scoring_safe` 复评：契约违规 **16 → 4**（修复 12、无一条被改坏），
剩余 4 = 3 条原始不可解析 + 1 条空 response。

## 3. 冻结判据

| 门 | 判据 |
|---|---|
| G1 契约合规 | 违规位数 ≤ 父本 + 1（父本同批实测为准） |
| G2 空回复 | candidate **0 例**空 response（与 W17 的 E207_T08 对照） |
| G3 九维 | 最差维 ≥ −0.3；interact / emotion / initiative 各 ≥ −0.2 |
| G4 severe | 亲读后 candidate ≤ parent（不新增同类） |
| G5 成本 | prompt 增幅 ≤ +3%（守卫不改 prompt，预期 0）；0 额外模型调用 |

**判决表**

| 情形 | 判决 |
|---|---|
| G1–G5 全过 | **KEEP**：b_norecap_guard 成为 ≤24 轮候选，进入下一阶段合并复评（W17c） |
| G1 或 G2 破 | **ELIMINATE**：守卫不足，登记剩余漂移形状后重设计 |
| G3/G4 破 | **ELIMINATE**：守卫引入语义副作用（修复改变语义） |
| 任一臂 CALL_FAIL >10% | 该篇降权；有效篇 <4 判证据不足 |

- no_retry / no_makeup；臂顺序按篇交错；severe 全部旗亲读。
- 评审按篇并行，**边跑边评**（篇完成即开评）；评审断点续跑已内建。

## 4. 已知限制（诚实前置）

- 3 条"原始回复不可解析"无法确定性修复（截断/串内未转义引号/键名损坏），只能弃用；
- 空 response 只标记不补内容；
- `source_ids` 指向真实消息不在本层（未做来源校验）；
- 六篇均已暴露于 W17，本批是**机制回归**不是泛化证据；泛化需另选未暴露篇（W17c）。

## 5. 执行清单

1. 候选与离线测试：`python tools/test_contract_guard.py`（27 检查全过）
2. 等价核对：`python tools/check_prompt_equivalence.py --episode benchmark/episodes/E189.json
   --cand-a window_expand_b_norecap --cand-b window_expand_b_norecap_guard`（12/12 相同）
3. `tools/exam_interleave_plan.py --emit-plan --execute-mock --batch-config W17B.BATCH_CONFIG.json`
4. `AUTHORIZATION_W17B.json`（cap 144）→ `run_max288_batch.py --mode precheck`
5. `--mode live --workers 12`（满宿主并发）+ 边跑边评 watcher
6. 机械分/契约统计 → 九维评审 → B3 亲读 → 判决 → 账本同步
