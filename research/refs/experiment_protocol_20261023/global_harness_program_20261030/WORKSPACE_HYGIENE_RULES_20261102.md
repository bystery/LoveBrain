# 工作区卫生规则 v1（2026-11-02，体检后冻结）

> 起因：2026-11-02 污染体检发现入口链与状态散文腐化（RUNBOOK 仍写 `r4_guard_e3`、`RUN_STATE.json`
> 疑似活注册表、`main_incumbent` 停在 LD1 前口径、TASK_BOARD 与 TASK_QUEUE 争权威）。
> 本文件把"人肉记得"换成机械检查：`tools/audit_workspace_hygiene.py`（只读，零模型调用）。

## 一句话原则

**状态只活在两份 JSON（`RESEARCH_STATE.json` + `TASK_QUEUE.json`）。其余文件只有两种身份：
指针（指向那两份 JSON）或证据（带日期、开写即冻结）。任何文件都不许当第二份状态。**

## R1 单一状态源（check C1）

- 冠军/当前候选/在飞批只写 `RESEARCH_STATE.main_incumbent`；任务状态只写 `TASK_QUEUE.state`。
- `WORKER_CURRENT_RUNBOOK.json` 只许放指针 + 冠军名镜像；镜像必须与 `main_incumbent` 一致。
- `RESEARCH_STATE.as_of` 距今天 >2 天 = error（说明状态没人维护，接手者会读错）。
- 每次判决落盘后 10 分钟内同步两份 JSON（先改 state，再改 evidence，最后改 as_of）。

## R2 墓碑纪律（check C2）

- 注册表废止不许"删掉就算了"，必须留 tombstone：`{"kind":"deprecated_registry","deprecated_at":...,
  "superseded_by":"<现存路径>"}`，且 `superseded_by` 指向的文件必须真实存在。
- 叙述型看板（如 `TASK_BOARD.md`）废止时，头部 40 行内必须有"废止"横幅并把读者导向新注册表。
- 判据：新会话按入口链读下来，不应有任何一处把它当成活状态。

## R3 散文禁令（check C3）

- 根目录叙述文档、总书头部状态段**不得**自带"当前冠军/当前候选/当前状态/正在运行"这类会烂掉的状态声明；
  状态一律改成指向 JSON 的链接。
- 豁免：带日期快照（`WORKER_HANDOFF_*`、`HANDOFF_TO_NEXT_SESSION_*`）开写即冻结，允许保留当时状态；
  判决/设计文档允许描述"判决当时"的格局，但必须写明日期。
- 判据不是"有字就报"，而是"三个月后有人读到会不会被误导"。

## R4 证据路径必须可解析（check C4）

- `TASK_QUEUE.evidence` 里每条路径都必须能在磁盘上解析到；全路径优先。
- 历史裸文件名（相对包目录）仍可解析，但会报 warn 要求改全路径——裸名换目录即失联。
- 产物落在 `logs/` 的，evidence 必须写 `logs/...` 全路径（如 `WINDOW_KEEP_W15N.json` 实际在
  `logs/w15n_norecap_live_20261102/_reports/`，写进 long_probe 是错的）。

## R5 删除三段式（check C5）

1. 先列清单（路径 + 类别 + 影响），**不许边查边删**；
2. 用户明确点头后才动；
3. 优先"移到 `archive/<日期>/`"而不是真删；`__pycache__` 可直删（可再生）。
- 禁止：把"我以为是临时文件"作为删除依据；禁止对 `benchmark/`、`logs/<批次>/`、`refs/` 做批删。

## R6 冻结件不可改（check C6 + 人工铁律）

- 执行器 `tools/exam_interleave_plan.py` 的 sha256 被每份 `INTERLEAVE_PLAN` 绑定；
  任何编辑（包括"补一行 docstring"）都会让全部旧 plan 触发"配置漂移"，必须重新 emit 全部 plan。
- 同理：`arena/lb_scorer.py`、`benchmark/rubric.md`、已 SEALED 故事正文、已绑定 AUTH 的 scope/cap 一律不动。
- 修工具的正确顺序：先确认它在哪些 plan/auth 里被 hash 绑定 → 冻结期结束再改 → 全量重 emit + mock 复验。

## R7 日志只增不改（check logs/INDEX.json）

- `logs/<批次>/` 是历史证据，不整理、不重命名、不删中间产物。
- 一级索引由 `--write-log-index` 生成：`logs/INDEX.json`（目录/文件数/字节/最后修改），只增不改，
  用于"哪批产生了什么"的巡览；判决只认 `refs/` 下的 verdict 文档，索引不是判决。

## R8 候选依赖闭包必须可被 staging 看见（2026-11-03 增补）

- 隔离 staging 的依赖收集**只读 `mechanism/mechanism.py` 的源码文本**，按
  `"candidates", "<name>"` 或 `candidates/<name>` 两种字面形状识别；`os.path.join` 拆行、
  变量拼接、从其他文件间接 import 都不会被识别。
- 因此候选若依赖父本目录，必须在 `mechanism.py` 里留一条可被正则命中的字符串/注释，例如
  `# 依赖 candidates/reference_product_v1（staging 闭包按字符串收集）`；否则 live 时
  `FileNotFoundError: 候选目录不存在`，emit/precheck 阶段才会暴露。
- 判据：`python tools/exam_interleave_plan.py --emit-plan` mock 能通过，不等于 live 能 staging；
  新候选第一次 live 前先确认依赖名出现在 mechanism.py 文本中。

## 运行方式

```bash
python tools/audit_workspace_hygiene.py                 # 日常：打印 error/warn + 写 logs/hygiene_audit_<日期>.json
python tools/audit_workspace_hygiene.py --strict        # CI 式：有 error 退出码 1
python tools/audit_workspace_hygiene.py --write-log-index
```

跑点：新会话接手第一件事、每批判决落盘后、每次大改工具后。只读工具，不会改任何既有文件。

## 当前遗留（2026-11-02 体检结果：error 0 / warn 7）

| # | 项 | 处置 |
|---|---|---|
| 1 | `START_PROMPT.md:47` 自带"当前状态（2026-10-22）" | 随根目录死文档归档一并清 |
| 2 | `tools/*.bak-*` 15 个 | 待用户"删"→ 移 `archive/20261102/tools_bak/` |
| 3 | `__pycache__` 5 处 | 可直删（可再生），仍按 R5 一起列举 |
| 4 | 根目录死文档 6 篇（HANDOFF×2 / STATE_MAP / SESSION_COORDINATION / BASELINE_SCOPE_V2 / START_PROMPT） | 待用户"删"→ 移 `archive/20261102/root_docs/` |
| 5 | `audit_fact_coverage_v2.py` / `exam_review.py` / `f5_repair_replay.py` 无模块 docstring | 2026-11-03 已确认无 plan 绑定并补为模块 docstring（C6 清零） |

## 与污染源头的对应

体检确认 lovebrain 的腐化不是"文件多"，而是三条：①状态写进散文（R1/R3 治）；②死注册表不立碑（R2 治）；
③过程复刻冒充进展（已有 §0.1 纪律与"evidence 必须可解析"约束，R4 治）。工具化后这三条从"记得做"变成"跑一下就知道"。
