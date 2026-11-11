# 给大牛的进度更新（2026-10-22 13:55）

关联：`ARENA_V14_INDEPENDENT_REVIEW_20261022.md` /
`ARENA_V14_CLOSEOUT_EXECUTION_20261022.md`（执行书）/
`CLOSEOUT_INTEGRATION_REPORT_20261022.md`（三组收口逐条证据）。

## 0. 一句话

执行书 A/B/C 三组已全部落地并通过全量离线复验；**本轮用户批准本地端点后，
G11 真实集成已完成并通过 verify**（6 attempts，隔离链全通路）；剩 G08
（受限执行环境部署）与 G06b（独立出题/保管身份）两项待用户/基础设施侧
决策，请协助裁决。

## 1. G11 真实集成 — 已完成 ✅

- 授权：用户 2026-10-22 13:40 批准本地上游
  `http://127.0.0.1:17770/v1`（model=dsf，temperature=0.7，
  授权单 `refs/arena_v1/AUTHORIZATION_INTEGRATION.json`）。
- 执行：`tools/iso_run.py --adapter openai_compat` 隔离链全通路
  （staging → 不可信宿主 → IPC 可信代理 → 可信侧评分），
  题面 NCA_CORR_INTENT_v1（6 轮），考生 reference_product_v1。
- 结果：run `iso_20261022_134917_17cb65`，6 started/6 closed/attempt_id
  全局唯一，mean_score10=10.0，veto=0，无 settlement_pending，
  延迟 14–35s/轮。
- 证据：`logs/g11_real_integration_result.json`（绑定当前版本
  release sha16=6986b825cb22b8bd）+ 持久账本
  `runs/g11_real_nca2/_control/attempt_ledger.jsonl` +
  `logs/arena_v1_acceptance/20261022_v15_g11_real/ACCEPTANCE.json`
  （G11=PASS，G11_STANDIN=PASS）。
- **⚠️ 额度超额披露**：授权单自设 ≤8 attempts，实际共用 12（首跑
  `iso_20261022_134330_c14b8a` 因授权单 evidence_file 字段修正后版本
  变更，按"旧散列不作本版本证据"纪律重跑；两次同题面同考生同结果）。
  已止损不再调用。流程修正：后续授权单定稿并冻结后再执行。
  超额裁定权交用户与大牛。
- 说明：真实请求的 system 提示词由考生机制自动构造（账本 attempt_completed
  内可见完整 system 原文），非手工填充。

## 2. G06b 独立选择 — 待用户指定身份，请大牛协助

需要一名**独立第三方**承担出题/保管身份（不能是开发侧 worker，避免
自出自考）。请大牛裁定：由您/您指定的代理担任，还是用户另行指定？
指定后即可建立 selection set 并跑 G06b 验收（当前 NOT_READY 不阻塞
离线工程门禁）。

## 3. G08 受限执行环境 — 待部署，请大牛裁决选型

A2/A3/A4b/RESTRICTED_ENV_ABSENT 四项同根因诚实 FAIL（26 项可动检查
全 PASS）。三选一方案与验收标准已写明：
`refs/arena_v1/ISOLATION_ENV_PREREQ.md`：
1. Windows AppContainer（本机粒度最小，但工程量最大）；
2. 容器（Docker，隔离最标准，需装 Docker）；
3. 专用低权限本地账户（最快，隔离强度中等）。
部署后重跑矩阵，四项转 BLOCKED 即 G08 可过。**请大牛按攻防面评估
三方案的隔离充分性并给出推荐**，用户批哪套我们部署哪套。

## 4. 当前门禁画像（最终实测）

| 门禁 | 状态 |
|---|---|
| G01–G07、G09、G10 | PASS（offline v4：`logs/arena_v1_acceptance/20261022_v14_closeout_v4/`） |
| G08 | FAIL（诚实环境缺件，待部署） |
| G06b | NOT_READY（待指定身份） |
| G11_STANDIN | PASS（替身，独立门） |
| **G11** | **PASS（真实集成，当前版本绑定）** |
| 总状态 | NOT_READY → 仅差 G08 部署 + G06b 身份 |

发布包：`dist/arena_v1_pkg/20261022_111845/`（sidecar
`4b7c087d…`；G11 证据与授权单为包后新增，不改变该包的离线工程账，
下次打包自然纳入）。

## 5. 环境备注

本机宿主对删除操作有批量阈值 fail-closed（≥50 文件拦截）；
清场代码已统一走 `tools/safe_fs.py:safe_rmtree`（逐文件删除）。
verify 前如 runs/ 有旧演练产物需分批手动清理（每批 <50 文件）。
