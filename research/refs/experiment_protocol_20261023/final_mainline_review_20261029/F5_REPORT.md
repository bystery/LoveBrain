# F5 修复与复测报告（2026-10-29）

## 1. 唯一确认的高价值缺陷

F3 正式批里候选有一次不可交付：**N01_T05**。模型原文（`logs/f3_newmat_live_restart_20261029/candidate_N01/turn_05_N01_T05/calls.json`，sha256 `21b61b05…`，526 字符）把 directions 数组的闭合符打成 `}`，并且在 `analysis` 外面多包了一层花括号：

```
…"directions":["…","…","…","空了就先睡，别熬"},{"analysis":{…}}
```

整篇因此不是合法 JSON：继承解析（I1）失败，已有的 E2（提前闭合重拼）也失败（报 `Expecting ',' delimiter: char 233`），确定性 guard 按"不编造修复"的原则原样放行，动作层记为 `format_undeliverable`、veto `json_schema_broken`，score10=0。正文其实完好（四条正文、四条方向、完整 analysis 都在），纯粹是括号问题。这是本主线唯一被证据确认、值得动用 F5 预留额度的缺陷。

## 2. 修复内容：只加一条窄规则 E3

`tools/output_repair.py` 及其 vendored 副本新增 E3，只在 I1 与 E2 都失败后启用，且必须同时满足五条：包裹对象第一眼就是 `"analysis":`；其配对 `}` 之后全文只剩空或一个 `}`；`[` 被 `}` 收尾的错配至多 1 处（出现第 2 处直接放弃）；重拼后 `json.loads` 成功；顶层同时含 response/directions/analysis。动作只有两种：把 1 个 `}` 归一为 `]`，删掉多余的一对 `{ }`。文字、键名、键值一律不碰。

留损记录（可审计）：`E3_stray_wrapper_object_removed` + `mismatched_closer_normalized`（cut_index 235，chars_moved 1+2）。

修复效果（同一份真实原文）：`response/directions/analysis` 三个子对象与原文逐字段相等（脚本核验 `==` 全 True，四条方向与事实引文逐字在原文中），ingest 由 `kind=text / top_level_keys=null` 变成 `kind=json / [response, directions, analysis]`，571 字符。

## 3. 身份处置：不覆盖冻结身份

我一开始直接把 E3 写进了已冻结的 `action_policy_r4_guard_final` 目录，这违反本项目"旧身份不改名不覆盖"的纪律，已纠正：

- `candidates/action_policy_r4_guard_final/` 的三个被改文件（`MECHANISM.md`、`params.json`、`mechanism/_vendor/output_repair.py`）按 `FINAL_CANDIDATE_FREEZE_20261029.json` 逐字节还原，重算身份 = `9b8f4e1eae4c68c0…`，与冻结记录一致；
- E3 版本另注册新身份 `action_policy_r4_guard_e3`，身份 `17fd2160e5306a23ba7ac52a2c5c716d4a58e345e5781d84890b69465fb76d17`，闭包 68 文件、父本仍是 `action_policy_r2`；
- 与基线的差异只有 4 个文件：vendored `output_repair.py`（E3）、`params.json`/`MECHANISM.md`/`IDENTITY.md`（登记与说明）；四份系统提示词资产与基线逐字节相同；
- 证据归属：F5 那 12 次付费复测实际加载的 staged 副本与新身份的 8 个机制/资产文件**逐字节相同**（`F5_CANDIDATE_FREEZE_20261029.json` 的 `code_byte_provenance.all_identical = true`），所以复测结果可直接归属到新身份，无需为改名字再花一次额度。

授权单 `AUTH-F5-RECHECK-20261029-01` 里登记的 `0053815a…` 是改名前的中间态身份（同一批字节、目录名不同），关闭事由里已写明这段历史，不做掩饰。

## 4. 零调用核销（证明修复生效且无回归）

| 检验 | 工具 | 结果 |
|---|---|---|
| 72 份已付费候选答卷重放 | `tools/f5_repair_replay.py` → `F5_REPLAY.json` | E3 命中 1（N01_T05）；harness 文本改动 1；非 E3 改动 0；修复后仍不可解析 0 |
| 260 份历史答卷奇偶保全 | `tools/test_output_repair_parity.py` | raw 260，old_sendable 246 → new_sendable 247，regressions 0，invariant_failures 0，多余方向/别名冲突仍全文保全 |
| guard 单元测试 | `tools/test_final_guard_candidate.py` | FINAL_GUARD_PASS 9/9（含干净原文逐字节透传） |

## 5. 实跑复测（真实调用 12 次）

`logs/f5_recheck_live2_20261029_133653/`：新代码整链重跑 N01 全 12 轮（含原失败位 T05），12/12 `ingest_kind=json` 且顶层三键齐全，mean_score10=10.0、veto_turns=0；额度 12/12 用尽即关。这一批模型原文都合法，E3 未触发，说明它不是"随手改写正常答卷"的后门。

## 6. 同臂重抽盲评（真实调用 24 次，12 对 ×2 顺序）

`tools/build_f5_blind_materials.py`（seed 20261029，正向 frozen 在前 8 对；`--swap` 整体互换 4 对），裁判 hy3/0.0/非流式，两次各 12/12 schema+transport 通过。

| 顺序 | frozen | repaired | tie |
|---|---|---|---|
| 正向 12 | 6 | 4 | 2 |
| 互换 12 | 4 | 3 | 5 |
| 合并 24 份 | 10 | 7 | 7 |

逐位两次同结论 9/12（75%），与 F1 的顺序一致性 10/12（83.3%）同量级，属裁判位置噪声，不是新现象。

必须说清的口径：A/B 两侧本是**同一 harness 的两次独立抽样**，所以"frozen 10 : repaired 7"不能读成"修复让质量下降"。E3 只在原文坏掉时动作，而这 12 轮两侧原文都合法（除 T05 的旧批）。唯一有方向性的位点是 N01_T05：旧批该位不可交付、两次顺序都判给复测侧（`format_fail_cases` 记录 `judge_penalized_undeliverable = true`），其余 11 个位点的胜负都是抽样波动。major_errors：frozen 2 / repaired 0，card_conflict 0。

## 7. 成本

36 次实调用（生成 12 dsf + 评审 24 hy3），token 合计 175,568（生成 82,299 = prompt 77,184 + completion 5,115；评审 93,269）。全 F0–F6 主线累计：attempt 506 次启动 / 497 完成 / 5 失败 / 9 次被拒（8 次重复派发 + 1 次整批预检拒绝），账本 usage 侧 1,078,663 tokens，答卷侧生成 1,153,468 tokens。

## 8. 结论与边界

缺陷已修好并有可复核证据：真实坏原文可解析、72 份答卷重放只改该改的那一条、260 份历史答卷零回归、新身份实跑 12/12 可交付。

边界：E3 是**括号级**修复，不解决"模型偶发写坏结构"这件事本身（那需要生成侧合同或采样层改动，属新实验，不在本批授权）；同臂重抽盲评只能用于"确认没有系统性变差"，不能当成组件收益证据；`action_policy_r4_guard_e3` 目前没有历史批次（S1–R3B）与 F3 全 72 位的表现数据，正式采用它需要一轮全量重跑，我在 F6 里按 REWORK/条件采用处理，不假装它已经等同于被验证过的 `action_policy_r4_guard_final`。
