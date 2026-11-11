# AUTHORIZATION_REQUEST_INTEGRATION — 最终真实集成授权清单（E4，一次性申请）

日期：2026-10-21。依据：ARENA_V1_CLOSEOUT_GUIDE §7-E4。
**本清单是请求，不是已获授权。** 授权口令与额度批准由用户明确给出后方可执行。
52 次候选对照与本清单**完全无关、另行授权**。

## 0. 前置状态

- 离线验收：**OFFLINE_READY**（G01–G05、G06a、G07–G10 全 PASS，证据
  `logs/arena_v1_acceptance/20261021_171859/ACCEPTANCE.json`）。
- G06b 独立选择未就绪（外部缺件，不阻塞本集成验证）。
- G11 验证目标：**只验证新隔离路径的设施通路**，不评价候选质量。

## 1. 执行器与版本

| 项 | 值 |
|---|---|
| 执行入口 | `python -B -X utf8 tools/verify_arena_v1.py --mode integration` |
| 控制器 | `arena/isolation/trusted_controller.py`（release.json 冻结 sha） |
| 候选 | 被测候选经代理走真实上游；比较基准 reference_product_v1 同路径 |
| 授权记录 | `refs/arena_v1/AUTHORIZATION_INTEGRATION.json`（authorized=true 后写入，含本清单文件散列） |

## 2. 端点与配置（待用户冻结）

| 项 | 值 |
|---|---|
| 端点 | 本地 vLLM（A100 或本机，用户指定 host:port） |
| 上游密钥 | vLLM 以 `--api-key` 启动；密钥只进控制器/代理 env，不进候选宿主 |
| model / 温度 | 用户冻结后填入执行参数；运行期不可改（配置漂移=停批） |

## 3. 调用算术：**≤ 8 次 attempt_started，无自动重试**

| 节点 | 验证目标 | 消耗 |
|---|---|---|
| N1 | 文本生成基线：单轮无媒体 → 原始响应保存 + 账本唯一 attempt 配对 | 1 |
| N2 | 真实时间注入：带 clock 轮 → 时间戳进 prompt 通路 | 1 |
| N3 | 画像/记忆注入：persona 轮 → L0/L1 通路 | 1 |
| N4 | 媒体实际出站：图片轮 → 媒体真实发给模型 | 1 |
| N5 | 跨轮状态：多轮第 1 轮 → 建立记忆状态 | 1 |
| N6 | 跨轮恢复：新进程快照恢复后第 2 轮 → 状态/恢复通路 | 1 |
| N7 | 密钥门禁真实上游：代理 local_tcp → 真实 vLLM 全链路 | 1 |

- 主调用 = N1–N7 共 7 次；辅助调用 = 0（参考链路比较用离线移植件，不消耗真实额度）。
- 预计总用量：7 × (prompt ≤4k + output ≤1k) tokens ≈ ≤35k tokens。
- no_reply / 异常分支 / 坏 JSON 计分：**同路径离线替身已完整验证（G02/G07/G09）**，
  不占用真实额度；模型坏格式是被测行为，保存并正确计分，**不丢弃重生成**。

## 4. 失败与停批政策

- 传输/配对/隔离/审计错误 → **立即停批**，不换批号补跑，出错误终态报告。
- 模型答错能力题/说得难听 → 不是集成失败，正常计分。
- R3#9 整改：原 N8（设施故障诊断备用调用）已删除——与 §4 "失败立即停批、
  无补跑" 冲突。设施故障按停批政策处理，出错误终态报告后重新申请。

## 5. 批准方式

用户明确授权（如"批准 8 次集成授权"）后，worker 将：
1. 用户冻结端点/model/温度/密钥交付方式；
2. 写入 AUTHORIZATION_INTEGRATION.json（authorized=true + 本清单 sha）；
3. 执行 integration 验证，出唯一收口结论（G11 PASS/FAIL）。
