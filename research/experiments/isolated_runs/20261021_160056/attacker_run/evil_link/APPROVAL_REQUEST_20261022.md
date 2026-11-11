# 待批准事项（只有一件）：首场 24 次 API 调用

日期：2026-10-22 20:20。依据：`refs/harness_api_mainline_20261022/HARNESS_API_MAINLINE_GUIDE_20261022.md` §6/§10C。
状态：**待用户批准。未批准 = 0 次真实调用。**

## A 项：系统变更——**已撤回，不再是待批事项**

原「G08 账户 + 防火墙 + 主仓 ACL 部署」整项撤回（`G08_DEPLOYMENT_PROPOSAL_20261022.md`
页首已标 WITHDRAWN；`tools/g08_deploy.py --apply` 硬拒绝，exit 3）。
理由：参赛对象是 harness（本项目自己写），本批不承诺抗恶意第三方代码隔离；
把 OS 部署当试考前置是跑偏。**不需要管理员、不需要重开应用、不需要重启。**
本轮系统变更 **0 项**（账户 0、防火墙 0、DACL 0、软件安装 0）。

## B 项：首场调用预算（唯一待批）

| 项 | 值 |
|---|---|
| 参赛 harness | `reference_product_v1`（原版保真基线；身份散列见清单） |
| 数据 | v0.2 E001/E002/E003，各 8 位 = **24 位**（DEV_EXPOSED，无媒体） |
| API | `http://127.0.0.1:17770/v1/chat/completions`，非流式 |
| model / 温度 | `dsf` / **0.7**（实际出站值落账） |
| **总 attempt 硬上限** | **24**（每位 1 次生成，辅助调用 0） |
| 额度权威 | `logs/auth_budget/<authorization_id>.jsonl`，跨 run/进程/重启累计，不继承旧授权 |
| 失败策略 | 传输/预算异常 → 停批，报「已完成/失败/未运行」；正常但答错 → 记为失败样本，不补问；**无自动重试** |
| 执行命令 | `python -B -X utf8 tools/api_trial.py --live --authorization <授权单>` |
| 成本 | 本地端点，无云计费；24 次、单次约 20–40 s |

清单原文（24 位逐条）：`refs/arena_v1/CALL_LIST_EXAM1_20261022.md`
机器可读：`logs/api_trial_plan/CALL_MANIFEST.json`（`--plan` 零网络生成）

## 批准前已经完成、可复查的离线证据

| 检查 | 结果 | 证据 |
|---|---|---|
| 24 位清单 | 24 位 / 24 attempts（`--plan`，零网络） | `logs/api_trial_plan/CALL_MANIFEST.json` |
| 出站请求捕获（不含真实调用） | **24/24 捕获**，无 gold/未来轮次/评分卡泄漏，请求键仅 4 个、无工具通道，model=dsf、温度=0.7 | `logs/pilot_capture_mainline/REQUEST_CAPTURE.json` + `requests/pos_*_body.json` |
| 返回物边界反例 | **8/8**（代码不执行、命令只是数据、超长/非 JSON 留存、越界请求体拒绝、泄漏探测器负对照） | `tools/test_api_boundary.py` |
| 授权预算反例 | **17/17**（跨进程累计、整批预检、关闭不复活、坏账停批、无预算零派发） | `tools/test_authorization_budget.py` |
| 输入边界 | PASS（meta/gaps/gold 零泄漏、深拷贝、persona/ts/may_decline 到达模型） | `tools/test_input_boundary.py` |
| 24 题 + 24 卡绑定 | **IMPORT_OK**（3 episodes / 24 requests / hard_fail=0） | `logs/pilot_v02_import_check.json` |
| 评审接线 | 3 组自造正反回复走真实 gen→ingest→aggregate→verify 全通过 | `tools/review_wiring_drill.py` |
| 离线全量门禁 | G01–G05/G06a/G07–G10 **全 PASS**；G06b NOT_READY（外部缺件）；G11 NOT_RUN（无新授权） | `logs/arena_v1_acceptance/20261022_mainline_offline_final/ACCEPTANCE.json` |
| G08 | **PASS（新语义）**：矩阵结构 + 边界反例；OS 部署 N/A 项原始实测保留 | 同上 G08 账 |
| 本轮真实模型调用 | **0 次** | — |

## 批准方式

回复「**按清单执行首场 24 次**」即可。要改数量/model/温度就按你的字面执行，并重新生成清单。

## 批准后我会连着做完

`--live` 跑 24 位 → 原始答卷 + 账本 → `ANSWER_SHEET.md` → 24 卡逐位工程初审
（程序合同/事实语义/互动质量三层分开）→ 故障族归纳 → 下一个 harness 改进假设。
需要你个人口味裁决的材料攒一批单独交你，不逐条打断。
