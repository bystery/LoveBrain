# KNOWN_FAILURES.md —— 位置引用说明（本文件不是门禁内容）

> 本目录（`research/arena/`）是 harness 代码域。按 c2 随迁建议，已知故障回归门禁
> 在本域挂引用说明；**门禁正文不在本文件**，请勿在本文件登记新故障。

## 唯一权威副本

- **路径**：[`../refs/KNOWN_FAILURES.md`](../refs/KNOWN_FAILURES.md)（本 worktree 内，随 F6 落位）
- **源**：`D:\lovebrain-arena\refs\KNOWN_FAILURES.md`（arena 私有研究区）
- **一致性**：diff 零差异（2026-11-12 核对），字节大小 10,079，mtime 保留源侧
  2026-11-04 10:42:11 +0800。
- **更新纪律**：内容只在 `../refs/` 一侧维护；登记新失败族改 refs 侧，本文件不复制正文，
  防止双源漂移。

## 为什么在 harness 代码域挂此引用

已知故障回归是晋级流程第 2 步的**硬门**（源仓库 `AGENTS.md`："已知故障回归
（refs/KNOWN_FAILURES.md）是硬门"），harness 侧约束包括：

1. 任何候选不得重新引入门禁登记的真实事故；回归门不通过不得晋级。
2. 每处机制变异的 mechanism DNA 须标注 `refs/KNOWN_FAILURES.md` 故障号。
3. 每道题 design_dna 的 `F#.#` 标注由回归门优先核对该故障族。

因此该门禁与 `research/arena/` 的 harness 代码（lb_runner / lb_scorer /
isolation 等）构成一个消费整体，随 harness 代码域随迁。

## 当前登记的失败族（仅条目名，正文见 refs 侧）

v0.2 门禁共 9 个失败族条目：

1. **F1** 角色错位（三元组混淆）
2. **F2** 话题污染
3. **F3** 记忆污染
4. **F4** 表达缺陷
5. **F5** 装配缺陷
6. **F6** 现实一致性盲区（v0.3 扩充为五子族 F6.1–F6.5）
7. **FK-LR2-01** 长剧域逐字事实保持塌陷（2026-11-01 登记）
8. **FK-SCORE-01** 评分器 source_ids 契约缺口（2026-11-02 登记并修复；含 W19-01
   隔离路径复现修复附录，2026-11-03 关闭）
9. **FK-INJECT-01** 追加注入块 → 输出契约漂移（2026-11-04 登记，W22FULL 判决 REV2）

最后更新 2026-11-04（FK-INJECT-01 登记日），门禁活跃。

## 相关审计

- 产品源码逐项审计：`D:\lovebrain-arena\refs\version_closeout_20261106\T5_KNOWN_FAILURES_AUDIT.md`
  （对 `D:\LoveBrain` 分支 closeout-20261106 的 STILL_PRESENT / FIXED 判决）。
