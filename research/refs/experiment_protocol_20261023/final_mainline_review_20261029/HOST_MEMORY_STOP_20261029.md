# HOST_MEMORY_STOP_20261029 — 因主机内存压力停止 F1/F3 批处理的账本

时间：2026-10-29 10:22（停止动作）；记录 10:23。

## 1. 我启动了什么

| 批次 | 入口 | 模型/并发 | 计划 | 启动时间 |
|---|---|---|---|---|
| F3 新材料两臂生成 | `tools/run_f3_new_material.py --mode live --tag live_20261029` | dsf / 0.7 / 非流式 / 3 并发 | 144 次 | 10:01:35 |
| F1 历史盲评续跑 | `tools/run_f1_blind_review.py --mode live --resume logs/f1_blind_review_20261029_094731` | hy3 / 0.0 / 非流式 / 3 并发 | 142 次剩余 | 10:07:45 |

两者都走本机网关 `http://127.0.0.1:17770/v1`（PID 49328，
`D:\桌面\各大网站\脚手架网关\gateway.js`）。

## 2. 发现的内存机制

- 我们的 Python 进程合计约 57 MB（F3 编排 7 MB、F1 10 MB、iso_run 20 MB、
  candidate host 20 MB），不是 Python 侧泄漏。
- 网关 49328 会为请求派生 `codebuddy` CLI 子进程；运行中它名下直接子进程
  28 个、RSS 约 3.6 GB；系统 WorkBuddy/codebuddy 相关 node 进程总 RSS 最高
  约 6.39 GB。我们的 API 调用也经过该网关，因此对这波子进程增长有贡献。
- 停止前：物理内存 98.1% 已用、可用 0.29 GB、swap 29.11/37.21 GB。
- 停止后（10:23）：物理内存 83.5% 已用、可用 2.51 GB、swap 26.72 GB；
  网关直接子进程 22 个、RSS 3.58 GB。仍未回到安全水位。

## 3. 停止动作（只动我自己的进程树）

```
taskkill /PID 3144 /T /F    # F3 编排（连带 iso_run 52940 / host 58484）
taskkill /PID 13588 /T /F   # F1 判词进程
```

未停：网关 49328、WorkBuddy 应用、Firefox、QoderWork、或其他用户程序。

## 4. 断点与已花额度

- F1：`logs/f1_blind_review_20261029_094731/F1_RATINGS.jsonl` 34 行
  （16 控制 + 18 历史）；授权 `AUTH-F1-BLIND-20261029-01` 保持 ACTIVE，
  可 `--resume` 同一目录继续。
- F3：完成 42 轮，失败 5 次（上游 timed out），1 次在飞被终止；
  授权账本 `AUTH-F3-NEWMAT-20261029-01` 记录 48 started / 42 completed /
  5 failed（另有 1 个 orphan started，因进程被终止）。
  输出目录 `logs/f3_newmat_live_live_20261029` 保留，未删除任何 turn 目录。
- F3 已形成的完整链：N01 parent 12、N02 parent 12、candidate N01 10、
  candidate N02 2、K02 candidate 4、K01 parent 2；其余为 0 或失败。
  这些是真实付费证据，但不足以构成 72 对完整配对。

## 5. 再次启动前的条件

1. 网关直接子进程数量与 RSS 回落到安全水位（建议物理可用 > 4 GB、
   网关子进程 < 10 个或总 RSS < 1.5 GB）。
2. 与网关/WorkBuddy 的 A/B/C 处置错开，不同时启动浏览器/GUI 重活。
3. 建议把并发降到 1（最多 2），避免再次同时派生多条 codebuddy 子进程。
4. F3 若要重跑，应登记新的目的 scope；旧单失败位置不静默重试、不挪用旧额度。
