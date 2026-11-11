# P06_JUDGE_INTERRUPTION_AND_MODEL_SWITCH

记录日期：2026-10-27。对象：P06 三批真实对照的评审链中断与考生/裁判模型切换。本文件只登记事实、
时间线、额度与可比性影响，不替原文判词下质量结论。

## 1. 时间线与证据

| 时刻(+08) | 事件 | 证据 |
|---|---|---|
| 13:06–14:59 | P06 harness 生成（考生=**dsf**）A→B→C 各 64/64 完成，failed=0，链正常退出 | `logs/p06_{A,B,C}_live_20261027.DONE.json`；账本 `AUTH-P06-MAX288V2-20261027-01` 192/192/0 |
| 15:05 | 评审批启动，裁判=dsf，非流式，concurrency=2 | `logs/p06_judge_live_20261027/`，授权 `AUTH-P06-JUDGE-20261027-01`（cap 256，planned 251） |
| 15:05–15:14 | seq 0–14 正常出正文（九维+备选，p50≈50s） | `RATINGS_*.jsonl` |
| ~15:14 起 | dsf 上游转为 200 空正文，延迟升到 139s；seq 15–30 全空 | 同上；`usage.completion_tokens=0`，raw 空 |
| 15:16 | 外部停批保额度；此时 31 completed（13 有效、18 未评：16 空+1 非法JSON+1 槽覆盖不符） | 进程 46056 已停 |
| 15:22 | 加固裁判运行器：空正文=交付失败按传输失败 fail-fast；resume 探测再消耗 2（全空）即停 | `resume1` stopped=transport_fail_fast |
| 15:33 | grant-1 定版：35 started / 31 completed / 2 failed，保留为中断证据，**不复用、不补跑** | 账本 `AUTH-P06-JUDGE-20261027-01` |
| 15:3x | 用户指令：考生 dsf 不可用，改用 **qwen3.8-flash**；网关 liveness 正常（`/health` dsf 路由在，但真实生成空） | curl/`/health` |
| 15:3x | qwen3.8-flash 可用（返回正文，网关别名→上游 `qfmodel`）；但为推理模型，评审大 prompt 慢 | 单条 curl；probe3 三条 240s 全超时 |
| 现在 | 新建裁判授权 grant-2（qwen3.8-flash，非流式，cap 256，planned 251），整批 live，timeout 900s，fail-fast | 账本 `AUTH-P06-JUDGE-QWEN-20261027-02` |

## 2. 额度与授权分账

- 生成（考生）：`AUTH-P06-MAX288V2-20261027-01` 192/192，已用尽规划额度，未失败，不复用。
- 裁判 attempt-1（dsf）：`AUTH-P06-JUDGE-20261027-01` cap 256，用 35（31 完成、含 18 未评、2 传输失败）。
  因上游失效导致的中断，**不补跑、不退款、不换 ID 伪装**；该批判词作废。
- 裁判 attempt-2（qwen3.8-flash）：`AUTH-P06-JUDGE-QWEN-20261027-02` cap 256，planned 251（191 首选
  +24 复评+36 控制），为实际生效的 P06 质量评审。
- 周期管理上限 16,384 是帽不是目标；以上均为按实验登记的分账。旧 CLOSED 单（含 P03 的 173/192）不复活。

## 3. 运行器加固（不改评分尺度，仅改交付判定）

`tools/run_p06_quality_judge.py`：live 下 `res['text']` 为空/全空白 → 记 `transport_ok=False`、
`attempt_failed`，触发 fail-fast，不再对死上游连烧额度。此改动只影响“传输/交付失败”判定，
不改九维尺度、题包、控制或配对规则；`--model/--packet/--aid` 参数化以便按新模型建新批。

## 4. 可比性影响（必须随 P06 成绩一起声明）

1. **裁判换了模型**：P06 用 qwen3.8-flash 裁判，P03 用 dsf 裁判。九维“绝对水平”不可跨 P03/P06 直接比；
   P06 内部用 36 控制 + 24 复评自证裁判可信度，配对只在同批两臂间比。
2. **考生仍是 dsf 生成**：P06 的 192 份答卷是 dsf 考生的既有结果；换裁判不改答卷。后续**新一轮候选生成**
   将改用 qwen3.8-flash 作基座模型——那与本轮 dsf 答卷不同底座，**不能跨模型把分数涨跌说成 harness 收益**，
   必须同模型重设基线再比（V2 §0：不改用更强模型冒充 harness 收益）。
3. 采样固定：本批 temperature 0.7、非流式 `api_cfg:openai_compat_stream_false_v1`；流式/非流式表不拼。

## 5. 若 qwen3.8-flash 评审仍不可用

按 V2：登记精确阻断条件（超时/空正文/非法 JSON 计数与样本），转做不依赖裁判的主线
（生成侧交付可靠性已单独收口），保留 grant-2 未耗额度，不空转、不谎称已评。

## 6. 裁判最终落点：gemini-3.8-flash（feasibility 探针驱动）

探针（非派发、直连同一适配器、temp0.7 非流式）结果：

| 裁判模型 | 控制样本(无备选) | 7 备选大节点 |
|---|---|---|
| qwen3.8-flash | 27–119s，正文合规 JSON ✅ | ~410s → **HTTP 502**（推理+长输出超上游时限）❌ |
| gemini-3.8-flash | 30s，合规 JSON ✅ | **36s**，completion 1798、正文合规 JSON、finish=stop ✅ |

判定：qwen3.8-flash 能判短样本，但整节点（首选+最多 7 备选）会触发上游 502，整批评审不可用；
**改用 gemini-3.8-flash 作 P06 裁判**，新建独立授权：

- `AUTH-P06-JUDGE-GEM-20261027-01`（cap 256，planned 251=191 首选+24 复评+36 控制，
  非流式 `api_cfg:openai_compat_stream_false_v1`，temp 0.7），题包
  `refs/experiment_protocol_20261023/p06_judge_gem_20261027/`。live concurrency=3、timeout 300s、
  空正文/502 触发 fail-fast。

**裁判≠考生是允许且更合规的做法**：V2 §7.4 明确“同服务多次评审不是多个独立模型、同模型自评非独立”。
用 gemini 评 dsf 生成的答卷，降低同模型自评偏置；但仍是同一 AI 家族的开发性评审，不是人类判决。

## 7. 三份裁判授权的分账（都不退款、不补跑、不复活）

- `AUTH-P06-JUDGE-20261027-01`（dsf 裁判）：35 用 / 31 完成(18 未评) / 2 传输失败。**作废留证**。
- `AUTH-P06-JUDGE-QWEN-20261027-02`（qwen 裁判）：2 用 / 2 失败(502)。**作废留证**，不 resume。
- `AUTH-P06-JUDGE-GEM-20261027-01`（gemini 裁判）：P06 实际生效判词来源。
- 生成 `AUTH-P06-MAX288V2-20261027-01`（dsf 考生）：192/192，已完成。

## 8. 考生基座模型（后续轮次）

dsf 已失效；qwen3.8-flash 生成可用（harness 侧非长推理、此前小 curl 4s）但作长评审超时；gemini-3.8-flash
作候选也需先做同基座公平基线再比。**下一轮候选生成先在同一直连可用模型上重建 reference 基线**，
绝不跨模型把分数涨跌当 harness 收益（V2 §0）。本轮 P06 的 192 份是 dsf 考生的既有快照，只判不再生成。

