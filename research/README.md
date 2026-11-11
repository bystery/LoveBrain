# research/ 研究资产总览与环境说明

> 状态：迁移落盘中（2026-11-12 快照）。本文如实登记当前实测状态与已验证边界，"待实测"逐项标注（§4），**不承诺任何未验证的能力**。零真实密钥、零真实聊天内容；API 配置一律占位符；文内 `research/...` 均为本仓相对路径。
> 完整性口径：**文件数 + 字节量**（逐域见 §1，逐文件见 `research/provenance/` 迁移清单；基于哈希的公开清单与复算流程在本版**防御性分析停用**）。

## 1. 目录导览（十二区，文件数为迁移 worktree 实测，2026-11-12）

| 区域 | 一句话说明 | 文件数 | 状态 |
| --- | --- | ---: | --- |
| `benchmark/` | 题库与评测基准：stories_v2、GOLD 侧车双轨、公开开发集 episode、rubric 两版 | 2,476（另 21 个源锁定件仅登记未复制） | 完整迁移 |
| `dataset/` | 母数据集公开面：结构摘要 + 合成样本（原件不出站，见 §3） | 92（合成样本 + README） | 完整迁移 |
| `candidates/` | 50 个候选目录 + 版本谱系（champion/historical/negative_control/ablation/template/reference 全保留） | 527 | 完整迁移 |
| `evaluation/` | 评审判决版本链（FINAL/SUPERSEDED/INTERIM/BUGGY 等全保留，错误版不删） | 97（另 3 件密件排除；原 3 件审查保留件已收口迁入） | 完整迁移 |
| `experiments/` | runs 批次 + 隔离测试 + 原始日志索引 | 50,621（另 16 件源锁定件仅登记未复制） | 完整迁移 |
| `arena/` | 考场运行时框架（纯标准库，含 isolation 隔离子包） | 29 | 完整迁移 |
| `refs/` | 判决书/实验协议/独立评审/失败分析（保留历史层级） | 19,694（含 W13-03/W14 批次登记 2 件） | 完整迁移 |
| `archive/` | 历史归档（移植原型、交付快照、脱敏指南，均附索引说明） | 81（含 14 个交付快照包；另 27 件登记未复制：9 件 Q8 待定、15 件 TRANSIENT_REVIEW、1 件待脱敏、2 件目录级排除） | 完整迁移 |
| `provenance/` | 迁移证据（映射表、迁移清单、隐私与许可审查） | 5（迁移清单 74,183 行） | 完整迁移 |
| `tools/` | 评测/审计/重评分工具与测试（含 benchmark_v1 在研区） | 507 | 完整迁移 |
| `architecture/` | 架构文档（以 `arena/` 实际代码与 refs 材料撰写） | 1（ARCHITECTURE.md 含机制研究叙事） | 已落位 |
| `PAPER.md` | 研究论文稿（成绩表述须与 runs/判决书互查） | 1 | 完整迁移 |
| 合计（十一区 + `PAPER.md`） | 落盘实测 74,130 件（不含本 README）；迁移清单 74,183 行口径 = 已落盘 73,962 行 + 登记未复制 221 行；另 62 件 evil_link 物化件已落盘（清单记录路径待同步）、106 件清单外新增落盘 | 74,130（74,183 清单行口径） | 完整迁移 |

体量参考（源实测口径）：benchmark ≈37.4 MiB、candidates/evaluation/tools 各 ≈2.6–4.5 MiB、arena ≈0.36 MiB、refs ≈194 MiB、runs ≈243 MiB；原始日志原件约 1.38–1.55 GB 按 run 边界切十卷走 Release/LFS，`raw_logs/` 只放索引与脱敏副本；dataset 原件 735,202 B 不出站。

## 2. 研究环境说明（十四项）

### 2.1 Python 版本

- **CPython 3.12，本机实测 3.12.4 全链导入通过**（`python -B -I`；四处 `__pycache__` 均为 cpython-312 佐证）。
- 语法地板低（无 walrus、无 match-case），但隔离/DPAPI 路径按 Windows 语义写死，"更低版本可运行"未验证——**不写"3.8+ 即可"，只认实测口径 3.12**。
- 依赖面：`arena/` 全部 31 个 .py **零第三方依赖**（纯标准库，模型调用用 `urllib.request`）；`tools/` 仅 12 个文件用 Pillow，其余全标准库。Windows 专属 stdlib（`msvcrt`、`ctypes.wintypes`）；跨平台运行未验证。
- 源仓库无 requirements/pyproject 声明文件；迁移补一份环境声明（Python 3.12 + Pillow，其余标准库，Windows 语义注记）——**补文件动作待执行，补齐前以本节登记为准**。

### 2.2 依赖安装方式

- `research/arena/`：无需任何第三方包，标准 Python 3.12 环境即可。
- `research/tools/`：安装唯一第三方依赖 Pillow（源仓库无版本锁定，如实登记、不虚构版本号）：

  ```bash
  python -m pip install pillow
  ```

  （安装后 12 个工具可正常运行——待迁移落盘后实测。）
- **平铺导入布局约束**：arena 模块间为平铺导入（`import lb_assembly` 形态）+ `sys.path.insert` 自举；`isolation/` 包要求 arena 目录与 isolation 包同时在 `sys.path`。迁移已整体平移 arena/ 与 tools/ 的相对目录关系，不得拆包或移动单文件。tools 引用 arena 的文件 46 个、引用 `isolation.*` 33 个。
- **跨目录内部依赖**：7 个 tools 文件依赖历史集成预览模块、9 个依赖候选参考链模块——落位后这些 import 是否可解析**待实测**；未验证前不声称 tools 全量可运行。

### 2.3 运行入口（arena/ 13 个入口点）

1. `lb_runner.py` — 考场运行时 v2（生命周期驱动，checkpoint/resume；`--case`/`--episode` 二选一、`--candidate`、`--out`、`--adapter` 默认 mock、`--stop-after`、`--resume` 等）
2. `scorer.py` — 前置代际确定性对账器 v3（`--task`、`--sandbox`、`--out`）
3. `init_sandbox.py` — 沙箱初始化器 CLI（`--task`、`--out`）
4. `lb_epgen.py` — 长程 Episode 生成器（脚本内常量）
5. `lb_verify_phase0.py` — Phase 0 验收断言器（`--episode`、`--run`、`--run2`）
6. `lb_judge_calib.py` — Judge 校准器（≥80% 人评一致率上岗；锚点文件路径）
7. `lb_judge_rerank.py` — Judge 位置偏差控制实验（RUNS/SEED 常量）
8. `lb_judge_2x2.py` — Judge 判决不稳定归因，温度×位置（实验参数常量）
9. `lb_vote_sim.py` — Judge 多数投票离线模拟，零新模型调用（读现有日志）
10. `lb_phase05_discrimination.py` — 评分器判别力报告生成器（读现有产物）
11. `isolation/_candidate_host.py` — 隔离宿主进程入口，被控制器 spawn（`--staged-root`、`--candidate`）
12. `isolation/candidate_host.py` — 受限候选宿主（旧版，同上形态）
13. `isolation/wincred.py` — 凭据加密往返自测（无参数）

非入口库模块（只被 import）：`api_candidate`、`protocol_public`、`gold_sidecar`、`lb_assembly`、`lb_mechanism`、`lb_stream_timing` 及 isolation 包内 5 个运行时模块。tools/ 入口面：根级 308 个 .py + 2 个历史运维 .ps1 + `benchmark_v1/`（33 个 .py，在研活跃区）+ `exam_batch_configs/`；确定性可迁核心约 30 个，测试类 67 个 `test_*` 归 `research/tools/tests/`（mock 类离线可跑——基于源形态，迁移副本待实测），一次性探针/单批次执行器约 150+ 按历史归档。运行方式如 `python research/arena/lb_runner.py ...`；**上列参数来自源仓库静态盘点与实测导入；"迁移副本上端到端可运行"待实测，本文只保留实测通过的命令示例（当前为空）。**

### 2.4 数据集路径与版本口径

迁移后路径：

| 内容 | 路径 | 口径 |
| --- | --- | --- |
| 题库（stories_v2 / anchors / annotations / cases / tasks / releases / 三个集合定义 JSON） | `research/benchmark/` | 完整迁移，2,473 文件已落盘 |
| 公开开发集 episode | `research/benchmark/episodes/`（149 文件）与 `dev_*` 子目录 | 以实际 ID + manifest 统计为准 |
| benchmark 内 `b_line/`（24 篇 episode 与清单/说明件，26 文件） | 随 benchmark 随迁 | 普通研究资产；清单件与说明件的版本差异按原样保留 |
| dataset 原件与公开侧 | 见 §3 三口径 | **迁移执行中** |

**权威口径链（机器重算，2026-11-11，独立复算并与既有审计交叉核对六项全部一致）**：

| 数字 | 口径 | 复核结论 |
| --- | ---: | --- |
| 319 | stories_v2 全部 story 目录（17 组目录逐一遍历） | 一致 |
| 317 | 按 ID 去重后唯一 story（两个版本目录同 ID 各一份） | 一致 |
| 299 | GOLD 覆盖 episodes（4,082 turns / 9,646 atoms；12 个分文件求和与 INDEX totals 八项指标全部一致） | 一致 |
| 248 | custody 账本注册 story 数（账本键集合与目录侧注册集逐 ID 相等；三角式 248 = 299 − 8 − 3 − 40 一致） | 一致 |
| 315 | 仅 episode.json 支撑的唯一 ID（2 个 bundle 目录无 episode.json） | 一致 |

- GOLD 未覆盖差集 18 = anchors 组 12 + final 组 6（设计如此）；wave5_selection 40 篇的账本补登按用户决策执行中（完成后 248 → 288）。
- 历史口径并列登记、不改写：论文稿旧口径 208 episodes / 2,944 库内轮次（3,102 轮含库外探针）/ 6,603 atoms；GOLD_SCHEMA 文档口径 221 / 3,102 / 6,603；更早的"296"口径无法可靠复现，如实登记。
- 统计单位纪律：Story（版本目录不重复计数）/ Episode（副本不重复计数）/ Turn（一次题目轮）/ Atom（可核验原子断言）/ Gold coverage（未覆盖 episode 不得当 Gold 样本）。使用纪律：`wave4_dev` 不是正式测试集；`wave5_selection` 不是已验证正式集；stories_v2 的远端文件数（2,022）**不是 episode 数**。

### 2.5 Gold 加载路径与双轨三代口径

Gold 侧车随 stories_v2 题库存放，**三代口径并列，不混写、不静默覆盖**：

| 代际 | 生成时间 | 文件数 | totals（episodes/turns/atoms） | 落位 |
| --- | --- | ---: | --- | --- |
| 文档口径（schema 文档自述 v1.0） | 2026-11-03 | 无文件实体（仅文档） | 221 / 3,102 / 6,603 | `benchmark/stories_v2/GOLD_SCHEMA.md` 原样保留 |
| 远端已提交快照 | 2026-11-08 | 13 | 259 / 3,590 / 7,953 | `benchmark/stories_v2/GOLD_SIDECAR_v1_remote_snapshot/` |
| **本地 v2 重建（权威）** | 2026-11-10 | 14（新增 wave5_selection 分文件） | **299 / 4,082 / 9,646** | `benchmark/stories_v2/GOLD_SIDECAR/` |

- 正式结果以 `GOLD_SIDECAR/`（本地 v2 重建）为准；快照目录仅作版本历史与审计对照，**不作为评分加载输入**（加载器按传入目录消费，实测不扫描兄弟目录）。schema 文档自述 v1.0 布局滞后于两代数据，按迁移纪律**原样保留不修改**。历史备份目录（含当时版 GOLD 分文件）按重复指向随迁，不删除。
- 加载实现：`research/arena/gold_sidecar.py`（`find_turn_gold` / `apply_sidecar`；离线零模型调用；must-not-affirm 状态族；向 lb_scorer 喂 C14/C15）。生成器：`tools/build_gold_sidecar`。"迁移副本上 Gold 可加载"待 §2.10 smoke test 第 2 项实测。

### 2.6 rubric 版本

- 两版并存、完整迁移：`research/benchmark/rubric.md`（14 KB，现行版）；`research/benchmark/rubric_v0_6_draft.md`（9 KB，历史草案留档）。
- **批次↔rubric 版本对应关系待登记**（批次登记表现有 14 列不含版本列）；补齐前本文不声称"全部历史 run 的 rubric 版本已知"。

### 2.7 scorer 版本

- **现行权威：`research/arena/lb_scorer.py`，v0.7**（演化 v0.4→v0.7；C0–C15 检查点、veto 族、no_reply 评分绕过修复；经 `protocol_public` re-export 同一函数对象）。
- **前置代际并存保留**：`research/arena/scorer.py`（v3，读管家账本 + 回合回复文件的上一代评分器）——两代都迁移，文档标注代际关系，不互相冒充。
- tools 侧评分面：`exam_layered_score`（离线分层）、`tools/benchmark_v1/universal_scorer`（通用评分层 + 语义守卫）、`rescore_batch` / `rescore_offline`。
- 历史评分文件形态：run 内回合级 `scoring.json`（约 2.8 万份）与修正版 608 件——**重算必须与产出该文件的评分器版本对齐；"版本↔批次"对照表待登记，未登记前不声称任意历史 scoring 文件都能用 v0.7 重算出一致结果**。

### 2.8 模型服务配置方式

- **统一网关**：`research/arena/lb_gateway.py`（考场宪法：候选调模型唯一通道，强制记账）。适配器 `ADAPTERS = {mock, openai_compat, echo}`；`mock`/`echo` 离线可用、无需任何环境变量；`openai_compat` 实例化时要求 `LB_API_BASE` 已设置，未设置抛错。
- tools 批执行器采用"授权单钉死环境"模式：先校验 `LB_API_BASE` 与授权单端点一致（不一致退出），再写入钉死端点；历史批次钉死端点为本地替身上游 `http://127.0.0.1:17770/v1`（**非公开服务，可用性见 §2.14**）。
- **危险开关**（真实判决批）：`LB_ALLOW_LIVE_JUDGE`、`LB_ALLOW_QUALITY_PANEL_LIVE`。其余环境变量读取面（如实登记）：`LB_STREAM_TIMING`、`LB_STREAM_USAGE`、`LB_API_TIMEOUT_S`、`LB_SANDBOX`/`LB_SANDBOX_USER`/`LB_SANDBOX_CRED`/`LB_SANDBOX_PYTHON`/`LB_SANDBOX_ROOT`（隔离沙箱，默认关闭、缺件即抛错）、`LB_REVIEW_WORK`、`HOLDOUT_VAULT`、`ARENA_SMOKE_MODE`、`ARENA_UNPACK_CONTEXT`、`EXAM_SCORING_FILE`、`DSF_API_KEY`（setdefault 空串 = mock 模式占位）。

### 2.9 API Key 环境变量设置（占位符）

权威实现点为 `arena/lb_gateway.py` 的 `OpenAICompatAdapter.__init__`：从环境变量读取 `LB_API_BASE` / `LB_API_KEY` / `LB_MODEL`（默认 `gpt-oss-120b`），`LB_API_BASE` 未设置即抛错。

```bash
# bash / Git Bash（PowerShell 等价写法：$env:LB_API_BASE = "<...>"，其余同名）
export LB_API_BASE="<your-openai-compatible-base-url>"   # 例：https://<host>/<vendor-prefix>/v1
export LB_API_KEY="<your-api-key>"                        # 仅存于环境变量，不写入任何文件
export LB_MODEL="<model-id>"                              # 未设置时网关默认 gpt-oss-120b
```

安全纪律：API Key 只通过环境变量提供，出站 `Authorization: Bearer` 仅在内存拼接；源仓库模式级扫描（`sk-`/`Bearer`/`AKIA`）0 命中；tools 中 setdefault 的 `"local"` 是本地替身上游占位值、**不是真实密钥**。公开文档示例禁止出现任何真实端点、真实 Key、真实模型供应商组合。

### 2.10 离线 smoke test（十项设计）

> 全程**不调用真实模型 API、不读取任何题面正文**（红线）。**总状态：十项均处"设计 + 部分前置实测"；迁移落盘后逐项实测并改写为实测结果。当前不宣称 smoke test 可运行。**

| # | 设计项 | 当前状态 |
| --- | --- | --- |
| 1 | Python 模块可导入 | 源仓库本机实测通过（3.12.4，9 个 arena 核心模块 + 隔离控制器 + tools 侧 9 个 + 通用评分器）；**迁移副本待实测** |
| 2 | Gold Sidecar 可加载 | 待实测（对象 `gold_sidecar.py`） |
| 3 | rubric 和 scorer 可加载 | 待实测 |
| 4 | 一个公开开发集 episode 可被解析 | 待实测 |
| 5 | 一个历史 run manifest 可解析 | 待实测 |
| 6 | 一个已保存 scoring 文件可被读取 | 待实测 |
| 7 | 一份判决材料能找到对应原始结果 | 待实测 |
| 8 | 数据统计脚本能运行 | 待实测（候选脚本面见 §2.12） |
| 9 | 完整性校验脚本能运行 | **停用**：基于哈希清单的公开校验流程在本版防御性分析停用，完整性以文件数 + 字节量口径登记（§头部） |
| 10 | 全过程不调用真实模型 API、不读取题面正文 | 全程红线，与第 1–8 项同时验收 |

- 行为断言候选面（零依赖、确定性，全部待实测）：请求体白名单越界判定；输出协议有效/无效解析矩阵；对 1 条内联 gold 样例出分（含 may_decline/silent 分支）；Gold 侧车临时文件往返；Mock 适配器一轮 + 调用记账；`lb_runner --adapter mock` 最小 case。
- 边界纪律：确需 API 才能验证的能力，单独记录为"需要外部模型服务"，**不与资产迁移验收混在一起**。源仓库存在 mock/echo smoke 批次产物可作形态参照，但"迁移副本可复现"未验证。

### 2.11 如何读取已有实验结果

- **批次级索引**：`runs-registry.csv` 逐批次登记 14 列（run 名 / 文件数 / 字节量 / 最新修改时间 / 是否含集级清单 / 评分文件数 / 布局形态等）——随 `research/experiments/` 落位。
- **run 目录典型内容**：回合级 `manifest.json` / `scoring.json` / `reply_raw.txt` / `calls.json`（敏感面，含端点痕迹，公开前过扫描）/ `mechanism_notes.txt`；集级 `episode_manifest.json`、身份与机制说明件；run 级 `run_identity.json`、授权账本 `ledger.json`（含校验伴生件）与 `attempt_ledger.jsonl`；`checkpoint.json/bin`（bin 可再生）。**layout 三形态**：`out_layout` / `variant_episode` / `rh_batch`（另有残留形态，读前先看 registry 行）。
- **代表性批次**（体量头部）：selection_v1（10,090 文件 / 70.3 MB）、pilot2_wave4_20261108（4,701 / 25.4 MB）、pilot2_all（5,042 / 21.7 MB）、holdout_v1（4,281 / 21.3 MB）。
- **判决与评审材料**：`research/refs/`（`KNOWN_FAILURES.md` 为失败证据入口）+ `research/evaluation/`（98 文件版本链：FINAL 79 / SUPERSEDED 6 / INTERIM 5 / BUGGY 3 / PREVIEW 3 / MOCK 1 / DEBUG 1；错误版不删）+ `research/experiments/{decisions,ablations,failures}/` 薄索引。读取路径以迁移落盘后的实际索引为准；"任意批次可按此读通"未验证。

### 2.12 如何生成数据统计

- **已产出的统计样板**（盘点产物而非可复跑程序承诺）：dataset 结构摘要（84 文件 / 735,202 B / 19 种文件名矩阵）；runs 批次登记表；logs 家族分组与十卷方案。
- **可复用脚本面**：`tools/aggregate_*`（4 个）、`exam_layered_score`、`audit_*`（6 个）、构建器族、`tools/benchmark_v1/inventory`（208 集盘点）。
- **待实测标注**：以上脚本在迁移副本上的运行**均未验证**；本文只登记实测通过的统计脚本清单（当前为空，smoke test 第 8 项通过后逐项补入），不预填。

### 2.13 完整性核验口径

- **本版完整性口径 = 文件数 + 字节量**：逐域文件数见 §1 导览表；逐文件字节量随 `research/provenance/` 迁移清单登记（每文件一行）。
- 复核方式：按清单比对目录文件数与累计字节量；历史 run 的授权账本自带校验伴生件可逐对核对。
- 环境说明第 13 项原要求"验证文件内容指纹"：**防御性分析停用**——基于哈希清单的公开复算流程与工具指引在本版不提供；源文件与脱敏副本的对应关系以迁移清单的路径与字节量字段承载。如后续启用，将另行声明并更新本节。

### 2.14 复现范围声明

**需要真实模型 API 的功能**：`openai_compat` 驱动的全部 live 批次；Judge live 判决批（受 `LB_ALLOW_LIVE_JUDGE` / `LB_ALLOW_QUALITY_PANEL_LIVE` 危险开关控制）；连通性探测类工具——均属"需要外部模型服务"，与离线验收分开记录。
**可完全离线复核的功能（设计可行，全部待实测）**：arena 核心模块导入链（源仓库已实测，迁移副本待实测）；Gold Sidecar 加载；已保存 scoring 的确定性重评分；mock/echo 适配器全生命周期；Judge 离线投票模拟；端到端分段计时；mock 类 test_* 离线回归；通用评分器纯函数层 + 离线分层。

**七项范围声明（原文级诚实）**：

1. **可从公开数据离线重算的结果**：已保存 scoring 的确定性重算、mock/echo 批次重跑——须经 smoke test 与实测确认后才可写"可重算"；当前登记为"设计可行、待实测"。
2. **依赖历史模型服务响应的结果**：全部 live 批次的原始答卷——只能重放与重评分，**不能重新生成**。
3. **依赖特定模型 endpoint 的结果**：网关默认模型 `gpt-oss-120b`；历史批次钉死端点为本地替身上游 `http://127.0.0.1:17770/v1`；授权单↔端点一致性校验是 tools 批执行器硬门槛。
4. **可能已不可用的历史 API 服务**：本地替身上游（127.0.0.1:17770）非公开服务；Judge live 批次所用外部服务无任何可用性承诺。
5. **只保留原始答卷、无法保证重新调用得到相同结果的实验**：全部 live 批次（采样参数与上游行为不可复现）。
6. **有 Judge 器械效度限制的评审结果**：judge 校准以 ≥80% 人评一致率为上岗门槛（20 道人评锚点 + trap 负样本）；位置偏差（5 候选随机重排）与温度×位置 2×2 不稳定归因实验并存；机器判定 fuzzy 命中未经人工复核**不得进对外结论**。
7. **不是当前 Android 生产代码的候选**：`research/candidates/` 全部 50 个候选目录均为研究资产（champion 9 / historical 32 / negative_control 4 / ablation 3 / template 1 / reference 1）；`research/archive/integration_preview_20261025/` 是历史移植原型，**不冒充当前生产接线**；`best/score.json` 不作独立冠军判决；`PAPER.md` 成绩表述须与 runs/判决书互查。

**底线句（原样保留）**：不承诺"所有历史模型实验可以一键完全重跑"，除非真的完成了可复现验证。

## 3. dataset 域口径（三口径）

1. **原件（真实 KB 快照，5 个目录 / 84 文件 / 735,202 B）**：本地保留，**永不出站**；仅登记文件数、字节量与结构角色。敏感件属"无法安全脱敏"档，任何形式的原件公开（含逐件脱敏）均不采用。
2. **公开结构摘要 `research/dataset/manifests/`**：目录树模板、字段/章节骨架、行数与字节量统计（19 种文件名矩阵），**不含任何正文**。
3. **公开合成样本 `research/dataset/public_fixtures/`**：生成方式 = **AI 原生子代理学习真实 KB 的结构、话题分布与对话格式后生成**——结构相似、内容全假。硬性要求：零真实姓名残留、零真实对话片段、零可还原特征；三层合成标记（库级 manifest / kb.json 键 / 每文件首行注释）+ 占位符词典；生成后强制隐私扫描通过才进入公开树。
- 状态：**迁移执行中**（合成样本生成与扫描落盘待完成；完成前 dataset 公开侧为 0 文件）。

## 4. 待实测清单（发布前逐项收口）

| # | 待实测项 | 收口方式 |
| --- | --- | --- |
| T1 | 迁移副本上 13 入口点命令行可运行 | 端到端跑通后写入实测命令示例（§2.3 当前为空） |
| T2 | Pillow 安装后 12 个工具可运行 | 实测后补版本锁定 |
| T3 | tools 对历史集成预览模块与候选参考链的跨目录 import 可解析 | tools 全量可运行声明前必须收口 |
| T4 | 环境声明文件（requirements/pyproject）补齐 | 文件落盘后替换 §2.1 登记 |
| T5 | 数据集权威口径链重算 | **已收口**：机器重算并与既有审计交叉核对一致（§2.4）；历史口径并列登记不消除 |
| T6 | Gold 侧车 14 vs 13 文件对账 | **已收口**：双轨三代口径（§2.5），本地 v2 重建 14 文件为权威 + 远端快照 13 文件并存 |
| T7 | 批次↔rubric/scorer 版本对照表 | 逐批次登记后补（§2.6/§2.7） |
| T8 | smoke test 十项在迁移副本全通过 | 十项逐项记录输出（§2.10；第 9 项按停用口径处理） |
| T9 | 统计脚本实测清单 | smoke 第 8 项通过后补（§2.12） |
| T10 | 完整性清单（文件数 + 字节量口径）生成与复核抽检 | provenance 迁移清单定稿时执行；哈希版清单停用 |
| T11 | 离线复核功能 8 项实测 | 逐项记录后才能写"可离线复核"（§2.14） |
