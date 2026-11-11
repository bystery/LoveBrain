# W03-05：候选冻结后新故事的分布与保管协议

日期：2026-10-31 ｜ 授权链：AUTH-GLOBAL-CONTINUOUS-20261030-01
工具：tools/story_custody.py ｜ 账本：STORY_CUSTODY_LEDGER.json（append-only 事件流）

## 1. 生成隔离（防污染铁律）

新故事生成会话**只允许读**：`stage1/`、`stage*_src/` 规格、既有故事的主题清单（仅标题/主题字段）。
**禁止读**：`logs/` 下任何批次输出、`candidates/` 失败记录、评审 JSON 的失败原句、错误分类表。
理由：候选失败原句进入生成视野 → 新故事"恰好绕开"该失败模式 → 泛化指标虚高（baseline contamination）。
执行：生成会话开工前在事件流记录 `generation_isolation_ack`；凡读过 logs/ 的会话不得当轮产出新故事。

## 2. 生命周期（单向，消费后不再称封存）

```
SEALED ──(任一候选批跑过)──> EXPOSED   [不可逆]
```

- SEALED：未向任何候选批暴露，可作"未见泛化"测试面。
- EXPOSED：对该故事声称"候选未见"的资格**永久失效**；只可用于回归、回放、跨代对比、长程诊断。
- 不存在"重新封存"。账本 `lifecycle` 字段是唯一权威；口头/文档声称与账本冲突时以账本为准。

## 3. 暴露账本

- 注册项：story_id、stage、title/themes、episode+reference sha256（篡改可检出，`verify` 子命令对账）。
- 事件：`{story, batch, arms, date, note}` append-only，历史事件由 logs/ 扫描播种（52 件，2026-10-24..31）。
- 新批开跑前：`expose --story E0xx --batch <批名> --arms <臂清单>`；跑完补录与实际 candidate_* 目录对账。

## 4. 当前账面（2026-10-31）

- 注册 32 篇（stage2 16 + stage3 8 + stage4 8）；**SEALED 池 16 篇**：
  E022 E023 E025 E028 E029 E030 E035 E036（stage2）＋ E037–E044 全部（stage3）。
- EXPOSED 16 篇：stage4 全部（longprobe 系）＋ fs1/p08 系消费的 8 篇 stage2。
- 旧代故事（E004–E014，stories_v2 之前）不在本注册表；其暴露视为全量已消费（历史批次 v3/v5/r2/c4/p06 均在旧代）。

## 5. 分布纪律（与 W03-06 衔接）

- 下一批测试抽故事：优先 SEALED 池；SEALED 池 <8 篇时触发 W03-06 扩展期（生成→审查→入库→注册 SEALED）。
- 每 SEALED 故事首次消费即记录；批次 DONE 报告必须附"本次消费 SEALED 清单"，未附视作流程违规。
- 保管账 hash 校验进入 Fast Gate 可选项（故事文件被动过的批次，其结论仅对该 hash 版本有效）。
