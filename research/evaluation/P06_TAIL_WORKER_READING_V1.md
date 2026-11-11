# P06 尾项 worker 证据阅读 V1（恢复批 small12 亲读）

日期 2026-10-28 05:00；读者=worker AI（main-20261028-002629 / watchdog30 接管轮，**同一 AI 来源，非独立评审**）；
对象=`logs/p06_recovery_review_20261027/REVIEW_RESULTS.jsonl`（12 逻辑单元，schema 12/12 可解析，账本 AUTH-P06-RECOVERY-20261027-01 已对账收口：物理 27/逻辑 18）。
模型调用 0；本阅读不改任何判词原文，只做可用性判断与风险登记。

## 1. 模式读数（12/12 亲读）

| profile | 模型 | 分数形态 | issues | 判断 |
|---|---|---|---|---|
| facts ×4 | dsf | 双侧全 [3,3,3,3]，零 issue，零方差 | 0 | 对本批短文本**可辩解但需监控**：FP001/FP005 的双侧答案确实不含事实断言（情绪承接/陪伴类文本），facts 维（factuality/attribution/time_state/boundary）给满有据；但 4/4 单元零方差=宽容度无法从本批排除。**后续真实长答案进来时必须重验 facts 判据的区分度** |
| task ×4 | qwen3.8-flash | 有区分（L 或 R 单维 2 分），3 条 minor issues | 3 | 判据在工作；issue 均指向"接住情绪后滑向分析/追问"（goal 维），与 B3 亲读发现的故障族一致 |
| interaction ×4 | qwen3.8-flash | 区分度最好（FP002 L=[2,1,2]；FP003 3 条 issues；FP005 R=[2,3,2]） | 6 | 判据在工作；低分理由与参考卡的 failure_conditions 对得上 |

## 2. 时延（单次，无重试口径）

facts（dsf）20.2–25.8s 全部稳定——印证"facts 单次 20–26s 可用"；task 7.5–166.5s、interaction 15.9–51.4s，方差大（FP005-task 166s 为本批最慢，均 <150s SLA 但接近）。**45s 评审期限对 task/interaction 偏紧**，若全量派发需按 profile 分设期限（属新测量 epoch，须换 run-tag 重新登记，不在旧单上改）。

## 3. 引文抽验

FP001-interaction 的 issue 引文"而且改到后面常常不是变好，是被改散了。他们到底想改哪儿？"与右侧答卷原文逐字一致（GATEWAY.jsonl 事件 raw_text 复核）；FP003-interaction 的 3 条 issue 引文格式完整（quote/evidence_id/dimension/severity 齐全）。

## 4. 结论与下一步

1. 12 单元判词**可用于探索性读数**（非验收）：facts 满分形态单薄、task/interaction 有效。
2. P06 尾项 147 主回复短作业的**全量派发条件**=（a）qwen 恢复稳定窗口；（b）task/interaction 的 SLA 按 profile 分设并换新测量 epoch 登记；（c）facts 判据在长答案上的区分度先抽 5–10 条真答案验证。
3. 本阅读记录同一 AI 来源（worker 自读），不冒充独立评审；原始判词与网关 raw 未动。
