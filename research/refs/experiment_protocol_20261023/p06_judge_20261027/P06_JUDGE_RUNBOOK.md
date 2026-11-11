# P06_JUDGE_RUNBOOK — 评审冻结、演练与真实派发步骤

日期：2026-10-27。前置：P06 A/B/C 三批 live 全部 `DONE` 64/64、failed=0、stopped=null；
三批生成账本 `AUTH-P06-MAX288V2-20261027-01` 已用到 192（不得重跑/补配对）。

## 1. 冻结题包

```
cd /d/lovebrain-arena
D:\anaconda\python.exe tools\build_p06_judge_packet.py --mode build
```

产物（`refs/experiment_protocol_20261023/p06_judge_20261027/`）：

- `P06_NODES.jsonl`：首选 + 复评节点（私有 `_meta` 不进入判词）
- `P06_CONTROLS.json`：32 旧开发控制 + CTL33–36
- `P06_REFERENCE_CARDS.json`：32 张语义参考卡
- `P06_PRIVATE_BINDINGS.json`：node→batch/arm/case/repeat_of
- `P06_ANSWER_INVENTORY.json` / `P06_PROTOCOL_ANOMALIES.json`：解析/槽位异常与未评账
- `QUALITY_MANUAL_V1_1.md`：判词手册冻结副本
- `AUTHORIZATION_P06_JUDGE.json`：独立裁判授权（cap 256）
- `P06_MANIFEST.json` / `P06_QUALITY_PLAN.json`：三批 DONE/计划/身份散列、派发顺序、输入散列

门禁：若任一 DONE 不完整、计划散列或臂身份不符、账本已存在非空、输入散列不符，构建直接拒绝。

## 2. 零派发自检

```
D:\anaconda\python.exe tools/run_p06_quality_judge.py --check
```

检查：计划长度=planned、类别计数=冻结计数、每个 item 可解析、控制/节点存在、payload 不含
arm/batch/candidate/expectation、失败即非零退出。

## 3. 演练（绝不占真实单）

```
D:\anaconda\python.exe tools/run_p06_quality_judge.py --mode mock --limit 12 --tag r1
```

演练账本在 `logs/auth_budget_rehearsal/`；验证落盘、schema 校验、停批逻辑。

## 4. 真实派发（独立裁判账）

```
set LB_ALLOW_P06_JUDGE_LIVE=1
set PYTHONIOENCODING=utf-8
D:\anaconda\python.exe tools/run_p06_quality_judge.py --mode live --tag 20261027
```

- 端点/model/temperature 由授权单绑定：`http://127.0.0.1:17770/v1` / `dsf` / `0.7`；
- 非流式；禁止 `LB_STREAM_TIMING`；
- 失败消耗不退款、停批不补跑；schema/parse 失败留原文标未评；
- 并发 2，单调用超时 240s。

监控：
```
grep -c attempt_started logs/auth_budget/AUTH-P06-JUDGE-20261027-01.jsonl
grep -c attempt_completed logs/auth_budget/AUTH-P06-JUDGE-20261027-01.jsonl
ls logs/p06_judge_live_20261027/JUDGE_BATCH_SUMMARY.json
```

若中断：`--resume-outdir logs/p06_judge_live_20261027`，已落盘/失败/孤儿不补跑。

## 5. 分析与判决

```
D:\anaconda\python.exe tools/analyze_p06_judge.py --ratings logs/p06_judge_live_20261027
```

输出 `evaluation/P06_QUALITY_RESULTS.json`（逐臂九维、同批配对、控制、复评、引文待核、
预注册规则机械判决）。随后必须 worker 亲读原文与异常清单，落 `evaluation/P06_RESULTS.md`，
逐候选给保留/修改/淘汰；不得只凭均分或自动判决。

## 6. 收口

- 核对评审账本 started/completed、schema/parse 失败清单、控制/复评结果；
- 关闭裁判授权（追加 `authorization_closed`），不复活、不补跑；
- 更新 `RUN_STATE.json`、`TASK_BOARD.md`、`STATE_MAP`（按项目规则）；
- 报五问：效果、可靠性、等待、费用、能否接入（本周期不接入）。
