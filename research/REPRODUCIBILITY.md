# research/ 复现说明（REPRODUCIBILITY）：环境、入口点、数据与复现范围

> 状态：2026-11-12 首版（迁移落盘快照口径）。本文回答四个问题：**环境怎么搭、入口在哪、数据怎么读、哪些能复现哪些不能**。"待实测"逐项标注（§10）。
> 纪律：零真实密钥、零真实聊天内容；API 配置一律占位符；文内 `research/...` 均为本仓相对路径；完整性口径 = **文件数 + 字节量**（见 §9）。
> 关联文档：环境说明全文见本文 §1–§5；dataset 三口径见 `research/DATASET_DOCUMENTATION.md` §8；机制与架构见 `research/architecture/ARCHITECTURE.md`。本文与 README 口径一致；离线 smoke test 的实测进展以本文 §7 为最新记录（十项设计口径见本文 §7）。

## 1. 研究环境（实测口径）

- **Python：CPython 3.12，本机实测 3.12.4**（2026-11-12，`python -B -I` 全链导入通过；源仓库四处 `__pycache__` 均为 cpython-312 佐证；迁移落盘副本同日首跑复证模块级导入，见 §7）。
- 语法地板低（无 walrus、无 match-case），但隔离/DPAPI 路径按 Windows 语义写死；"更低版本可运行"未验证——**不声称"3.8+ 即可"，只认实测口径 3.12**。
- 依赖面：
  - `research/arena/`：全部模块**零第三方依赖**，纯标准库实现（模型调用用 `urllib.request`，无 SDK）。
  - `research/tools/`：507 文件中仅 **12 个**使用第三方库 Pillow (PIL)，其余全部标准库。
- 平台语义：Windows 专属标准库 `msvcrt`（文件锁）、`ctypes.wintypes`（DPAPI / 跨账户进程拉起）；POSIX `fcntl` 在平台分支内，Windows 下不触发。**跨平台运行未验证。**
- 环境声明文件：**已落盘（2026-11-12）**——`research/arena/pyproject.toml`（`requires-python = ">=3.12"`、运行时 `dependencies = []` 零第三方、`[project.optional-dependencies] tools = ["Pillow"]`）与 `research/tools/requirements.txt`（Pillow 一项，版本不锁定——源仓库从未声明过版本，不虚报）。两文件各自声明其域，口径与本节登记一致（Python 3.12 + Pillow，其余标准库；Windows 语义注记写在文件头注释）。

## 2. 依赖安装

- `research/arena/`：无需安装任何第三方包，标准 Python 3.12 环境即可。
- `research/tools/`：安装唯一第三方依赖 Pillow（源仓库无版本锁定文件，如实登记、不虚构版本号）：

  ```bash
  python -m pip install pillow
  ```

  （安装后 12 个 PIL 工具可正常运行——**待实测（T2）**，实测后补版本锁定。）
- **平铺导入布局约束**：arena 模块间为平铺导入（`import lb_assembly` 形态）+ `sys.path.insert` 自举；`isolation/` 包要求 arena 目录与 isolation 包同时在 `sys.path`。arena/ 与 tools/ 的相对目录关系已整体平移，**不得拆包或移动单文件**。tools 引用 arena 的文件 46 个、引用 `isolation.*` 33 个、`exam_interleave_plan` 被 16 个文件引用。
- **跨目录内部依赖（T3）**：7 个 tools 文件依赖历史集成预览模块、9 个依赖候选参考链模块——落位后这些 import 是否可解析**待实测**；未验证前不声称 tools 全量可运行。

## 3. 运行入口点

以下入口面来自对本仓 `research/arena/` 与 `research/tools/` 的只读静态盘点与本机实测导入（2026-11-12）。

### 3.1 arena/ 入口点（13 个，`if __name__ == "__main__"` / CLI 形态）

| # | 入口 | 用途 | 主要参数 |
| --- | --- | --- | --- |
| 1 | `arena/lb_runner.py` | 考场运行时 v2（Harness 生命周期驱动，checkpoint/resume） | `--case`/`--episode`（二选一）、`--candidate`、`--out`、`--adapter`（默认 mock）、`--stop-after`、`--resume`、`--intent-override`、`--memory-off`、`--run-id` |
| 2 | `arena/scorer.py` | 前置代际确定性对账器 v3 | `--task`、`--sandbox`、`--out` |
| 3 | `arena/init_sandbox.py` | 沙箱初始化器 CLI | `--task`、`--out` |
| 4 | `arena/lb_epgen.py` | Phase 0 长程 Episode 生成器 | 脚本内常量 |
| 5 | `arena/lb_verify_phase0.py` | Phase 0 验收断言器（V2–V6b） | `--episode`、`--run`、`--run2` |
| 6 | `arena/lb_judge_calib.py` | Judge 评审团校准器（≥80% 人评一致率上岗） | 锚点文件路径 |
| 7 | `arena/lb_judge_rerank.py` | Judge 位置偏差控制实验 | RUNS/SEED 常量 |
| 8 | `arena/lb_judge_2x2.py` | Judge 判决不稳定归因（温度×位置） | 实验参数常量 |
| 9 | `arena/lb_vote_sim.py` | Judge 多数投票离线模拟（零新模型调用） | 读现有日志 |
| 10 | `arena/lb_phase05_discrimination.py` | 评分器判别力报告生成器 | 读现有产物 |
| 11 | `arena/isolation/_candidate_host.py` | 隔离宿主进程入口（被控制器 spawn） | `--staged-root`、`--candidate` |
| 12 | `arena/isolation/candidate_host.py` | 受限候选宿主（旧版） | 同上形态 |
| 13 | `arena/isolation/wincred.py` | 凭据加密（DPAPI）往返自测 | 无 |

非入口库模块（只被 import）：`api_candidate`、`protocol_public`、`gold_sidecar`、`lb_assembly`、`lb_mechanism`、`lb_stream_timing` 及 isolation 包内运行时模块。

### 3.2 tools/ 入口面

- 根级 308 个 .py + 2 个历史运维 .ps1（一次性运维件）+ `tools/benchmark_v1/`（33 个 .py，在研活跃区）+ `exam_batch_configs/`。
- 确定性可迁核心约 30 个（`exam_interleave_plan`、`exam_layered_score`、`exam_review`、`rescore_batch`、`replay_*`、`aggregate_*`、`audit_*`、`verify_arena_v1`、`verify_repro_package`、`build_gold_sidecar` 等）；测试类 67 个 `test_*` 归 `research/tools/tests/`（mock 类测试离线可跑——基于源形态，迁移副本待实测）；一次性探针/单批次执行器约 150+ 按历史归档。
- 运行形态如 `python research/arena/lb_runner.py ...`。**上列参数来自静态盘点与实测导入；"迁移副本上端到端命令行可运行"待实测（T1）——本文只保留实测通过的命令示例，当前为空。**

## 4. 数据与 Gold 加载路径（简要）

- 题库：`research/benchmark/`（完整迁移）；公开开发集 episode 在 `research/benchmark/episodes/` 与 `research/benchmark/stories_v2/` 各开发波次目录；`research/benchmark/b_line/` 为**公开 Benchmark / 历史 B 线**（24 篇题目与对应 Gold 已随公开分支公开，自公开时起不再是未曝光盲测集，状态标注见 `benchmark/b_line/B_LINE_README.md` §0）；复现流程默认零读取它（smoke 十项执行口径，见 §7）。
- Gold 加载：`research/arena/gold_sidecar.py`（`find_turn_gold` / `apply_sidecar`；离线零模型调用；must-not-affirm 状态族；向 lb_scorer 喂 C14/C15）。权威数据根 = `research/benchmark/stories_v2/GOLD_SIDECAR/`（本地 v2 重建，14 文件）；三代口径（schema 文档自述 / 远端快照 13 文件 / 本地 v2 重建 14 文件）并列登记、不混写，详见 `research/DATASET_DOCUMENTATION.md` §6。
- rubric 两版并存：`research/benchmark/rubric.md`（现行版）+ `research/benchmark/rubric_v0_6_draft.md`（历史草案留档）。scorer 两代并存：`research/arena/lb_scorer.py`（v0.7，现行权威）+ `research/arena/scorer.py`（v3，前置代际，不互相冒充）。**重算必须与产出该文件的评分器版本对齐；"版本↔批次"对照表待登记（T7）。**
- 数据集统计口径：权威口径链 = 319 story 目录 / 317 唯一 story / 299 Gold 覆盖 episodes（4,082 turns / 9,646 atoms）/ 248 custody 注册 story / 315 仅 episode.json 支撑的唯一 ID（机器重算、交叉核对一致）；历史口径并列登记不改写，详见 `research/benchmark/STATISTICS.md`。
- dataset 原件（真实 KB 快照，84 文件 / 735,202 B）**不出站**；公开面 = 结构摘要 + 合成样本（三口径详见 `research/DATASET_DOCUMENTATION.md` §8）。

## 5. 模型服务配置与 API Key 环境变量（占位符）

- **统一网关**：`research/arena/lb_gateway.py`（考场宪法：候选调模型唯一通道，强制记账）。适配器 `ADAPTERS = {mock, openai_compat, echo}`：`mock` / `echo` 离线可用、无需任何环境变量；`openai_compat` 实例化时要求 `LB_API_BASE` 已设置，未设置即抛错。
- **API Key 只通过环境变量提供**。权威实现点为 `arena/lb_gateway.py` 的 `OpenAICompatAdapter.__init__`：从环境变量读取 `LB_API_BASE` / `LB_API_KEY` / `LB_MODEL`，`LB_API_BASE` 未设置即抛错；`LB_MODEL` 未设置时网关默认 `gpt-oss-120b`。

  ```bash
  # bash / Git Bash（PowerShell 等价写法：$env:LB_API_BASE = "<...>"，其余同名）
  export LB_API_BASE="<your-openai-compatible-base-url>"   # 例：https://<host>/<vendor-prefix>/v1
  export LB_API_KEY="<your-api-key>"                        # 仅存于环境变量，不写入任何文件
  export LB_MODEL="<model-id>"                              # 未设置时网关默认 gpt-oss-120b
  ```

- 其余环境变量读取面（如实登记）：`LB_STREAM_TIMING`、`LB_STREAM_USAGE`、`LB_API_TIMEOUT_S`、`LB_SANDBOX`/`LB_SANDBOX_USER`/`LB_SANDBOX_CRED`/`LB_SANDBOX_PYTHON`/`LB_SANDBOX_ROOT`（隔离沙箱，默认关闭、缺件即抛错）、`LB_REVIEW_WORK`、`HOLDOUT_VAULT`、`ARENA_SMOKE_MODE`、`ARENA_UNPACK_CONTEXT`、`EXAM_SCORING_FILE`、`DSF_API_KEY`（setdefault 空串 = mock 模式占位）。**危险开关**（真实判决批）：`LB_ALLOW_LIVE_JUDGE`、`LB_ALLOW_QUALITY_PANEL_LIVE`。
- tools 批执行器"授权单钉死环境"模式：先校验 `LB_API_BASE` 与授权单端点一致（不一致退出），再写入钉死端点；历史批次钉死端点为本地替身上游 `http://127.0.0.1:17770/v1`（非公开服务，可用性见 §8）。
- 安全纪律：API Key 只存于环境变量，出站 `Authorization: Bearer` 仅在内存拼接；源仓库模式级密钥扫描 0 命中；tools 中 setdefault 的 `"local"` 是本地替身上游占位值、**不是真实密钥**。本文及全部公开文档示例禁止出现任何真实端点、真实 Key、真实模型供应商组合。

## 6. 复现步骤（当前实测边界）

- **离线复核路径（无需密钥、无需网络）**：标准 Python 3.12 环境 →（如需 tools）安装 Pillow → 按 §7 十项口径复核离线可加载/可解析面 → 按 §8.1 离线功能表逐项验证。状态：十项首跑已记录（§7）；tools 侧统计与重评分脚本逐项实测清单待建立（T9/T11）。
- **需要模型服务的路径**：设置 §5 三个环境变量 → 网关 `openai_compat` 适配器 → run 族入口。状态：**实测通过的端到端命令示例当前为空（T1）**；live 复现另受 §8.2 第 2–5 项约束（live 原始答卷不可重新生成、历史端点无可用性承诺）。
- 命令纪律：只登记实测通过的命令；未实测命令一律不写。

## 7. 离线 smoke test（十项）与首跑实测记录

> 设计口径与十项定义见本节 §7 十项表；本节为**实测记录**。首跑执行口径：全程不调用真实模型 API、未读取任何 B 线题面正文（第 10 项实测记录；B 线现为公开资产，该口径是首跑行为的如实记录，不再是保护性红线）。
> 首跑：2026-11-12，在本仓迁移落盘副本上以标准库脚本（`python -I` 运行，零第三方依赖、零网络、零子进程）只读执行，未向被测树写入任何文件。**汇总：PASS = 9 / FAIL = 0 / 停用 = 1。**

| # | 项目 | 首跑结果（2026-11-12） |
| --- | --- | --- |
| 1 | Python 模块可导入 | PASS——6 个 arena 核心模块（`lb_assembly` / `lb_runner` / `lb_gateway` / `lb_scorer` / `gold_sidecar` / `isolation.trusted_controller`）模块级导入全部成功；更广导入链（9 个 arena 核心模块 + tools 侧 9 个 + 通用评分器）已同日在源仓库实测通过，迁移副本扩展导入复跑随 T1 收口 |
| 2 | Gold Sidecar 可加载 | PASS——GOLD_SIDECAR 14 文件全部存在且可解析，12 个组文件 per_episode 数与 INDEX per_wave 逐组一致；totals.episodes = 299 为历史口径，登记不重算不改写 |
| 3 | rubric 和 scorer 可加载 | PASS——`research/benchmark/rubric.md`（13.5 KB）与 `rubric_v0_6_draft.md`（8.9 KB）在位；`research/arena/lb_scorer.py`（30.0 KB）模块级导入成功 |
| 4 | 公开开发集 episode 可解析 | PASS——抽样池 38（wave4_dev 开发波次，兜底 episodes/dev），实抽 3 个，均为 4 顶层键 / 12 轮结构（样例 `research/benchmark/stories_v2/wave4_dev/E301/episode.json`） |
| 5 | 历史 run manifest 可解析 | PASS——抽样池 753 个 manifest，实抽 3 个，均为 23 键集级清单且 complete=True |
| 6 | 已保存 scoring 文件可读取 | PASS——scoring 文件覆盖 231 个 run，实抽 3 个（含 veto 非 null 案例），分值、veto 与检查点字段均正常读出 |
| 7 | 判决材料能找到对应原始结果 | PASS——2 份判决件（`research/refs/experiment_protocol_20261023/` 下 `W09-02_SLIM_VERDICT_20261031.md` 与 `W02_EXIT_VERDICT.json`）合计抽出引用 8 条、树内命中 4 条，判决→原始结果对应链走通；未命中引用逐条随运行记录保留，不在本文推断原因 |
| 8 | 数据统计脚本能运行 | PASS——17 组全遍历：story 目录 319 / episode.json 317 / 去重 ID 315，与 §4 权威口径链一致；GOLD 组覆盖 12/12 |
| 9 | 完整性校验脚本（停用项） | **停用**——完整性核验以文件数 + 字节量清单承载（§9），本项不实现、不执行 |
| 10 | 全程零模型 API 调用、零 B 线题面读取 | PASS——B 线 26 文件仅做 stat 元数据级统计（未打开任何内容），护栏违规 0；脚本导入面无任何网络/子进程模块，未实例化网关与适配器 |

记录边界（诚实声明）：

1. 首跑验证的是**模块级导入与文件级解析**，不等价于"端到端命令行可运行"（T1），也不等价于"历史实验结果可重算"（§8.2 第 1 项）。
2. 结果绑定 2026-11-12 迁移落盘快照；`research/arena/` 域登记在案的后续变更落地后**须重跑十项**。
3. 运行脚本本身为一次性审计件（标准库实现），不随本仓分发；十项口径与判定以上表记录为准。

## 8. 复现范围声明

### 8.1 功能边界

**需要真实模型 API 的功能**：`openai_compat` 驱动的全部 live 批次；Judge live 判决批（受 `LB_ALLOW_LIVE_JUDGE` / `LB_ALLOW_QUALITY_PANEL_LIVE` 危险开关控制）；连通性探测类工具——均属"需要外部模型服务"，与离线验收分开记录。

**可完全离线复核的功能**（8 项，逐项状态如实登记）：

| 功能 | 状态 |
| --- | --- |
| arena 核心模块导入链 | 已实测（源仓库全链 + 迁移副本首跑；迁移副本扩展导入复跑随 T1） |
| Gold Sidecar 加载（`gold_sidecar.py`，离线零模型调用） | 已实测（迁移副本首跑 PASS，§7 第 2 项） |
| 已保存 scoring 的确定性重评分（`rescore_batch` / `rescore_offline` / `lb_scorer`） | 待实测 |
| mock/echo 适配器全生命周期（`lb_runner --adapter mock` / `echo`） | 待实测 |
| Judge 离线投票模拟（`lb_vote_sim`，零新模型调用） | 待实测 |
| 端到端分段计时（`lb_stream_timing`，零网络测量件） | 待实测 |
| mock 类 `test_*` 离线回归 | 待实测 |
| 通用评分器纯函数层 + 离线分层（`benchmark_v1/universal_scorer` + `exam_layered_score`） | 待实测 |

### 8.2 复现范围与边界

1. **可从公开数据离线重算的结果**：已保存 scoring 的确定性重算、mock/echo 批次重跑——须经 smoke test 与实测确认后才可写"可重算"；当前登记为"设计可行、待实测"。
2. **依赖历史模型服务响应的结果**：全部 live 批次的原始答卷——只能重放与重评分，**不能重新生成**。
3. **依赖特定模型 endpoint 的结果**：网关默认模型 `gpt-oss-120b`；历史批次钉死端点为本地替身上游 `http://127.0.0.1:17770/v1`；授权单↔端点一致性校验是 tools 批执行器硬门槛。
4. **可能已不可用的历史 API 服务**：本地替身上游（127.0.0.1:17770）非公开服务；Judge live 批次所用外部服务无任何可用性承诺。
5. **只保留原始答卷、无法保证重新调用得到相同结果的实验**：全部 live 批次（采样参数与上游行为不可复现）。
6. **有 Judge 器械效度限制的评审结果**：judge 校准以 ≥80% 人评一致率为上岗门槛（20 道人评锚点 + trap 负样本）；位置偏差（5 候选随机重排）与温度×位置 2×2 不稳定归因实验并存；机器判定 fuzzy 命中未经人工复核**不得进对外结论**。
7. **不是当前 Android 生产代码的候选**：`research/candidates/` 全部 50 个候选目录均为研究资产（champion 9 / historical 32 / negative_control 4 / ablation 3 / template 1 / reference 1）；`research/archive/integration_preview_20261025/` 是历史移植原型，**不冒充当前生产接线**；`best/score.json` 不作独立冠军判决；`PAPER.md` 成绩表述须与 runs/判决书互查。

历史模型实验的原始答卷和判决已保留；一键完全重跑不在当前复现范围内（见 §10 待实测清单）。

## 9. 完整性核验口径

- **本版完整性口径 = 文件数 + 字节量**：逐域文件数见 `research/provenance/SOURCE_MAP.md` §13 数字对账表；逐文件字节量随 `research/provenance/MIGRATION_MANIFEST.csv` 登记（每文件一行）。
- 复核方式：按清单比对目录文件数与累计字节量；历史 run 的授权账本自带校验伴生件可逐对核对。
- 环境说明原第 13 项"验证文件内容指纹"：**停用**——完整性以文件数 + 字节量口径登记；源文件与脱敏副本的对应关系以迁移清单的路径与字节量字段承载。

## 10. 待实测清单（发布前逐项收口）

| # | 待实测项 | 当前状态（2026-11-12） |
| --- | --- | --- |
| T1 | 迁移副本上 13 入口点命令行可运行 | 待实测——smoke 第 1 项只覆盖模块级导入；实测通过命令示例清单仍为空（§3.2） |
| T2 | Pillow 安装后 12 个工具可运行 | 待实测——实测后补版本锁定（§2） |
| T3 | tools 对历史集成预览模块与候选参考链的跨目录 import 可解析 | 待实测——tools 全量可运行声明前必须收口（§2） |
| T4 | 环境声明文件（requirements/pyproject）补齐 | **已收口**——`research/arena/pyproject.toml` 与 `research/tools/requirements.txt` 已落盘（2026-11-12），内容口径与 §1 登记一致 |
| T5 | 数据集权威口径链重算 | **已收口**——机器重算并与既有审计交叉核对一致；历史口径并列登记不消除（§4） |
| T6 | Gold 侧车 14 vs 13 文件对账 | **已收口**——双轨三代口径，本地 v2 重建 14 文件为权威 + 远端快照 13 文件并存（§4） |
| T7 | 批次↔rubric/scorer 版本对照表 | 待登记——逐批次登记后补（§4） |
| T8 | smoke test 十项在迁移副本全通过 | **已按停用口径首跑收口**——2026-11-12 首跑 9 PASS / 0 FAIL / 1 停用（第 9 项停用）；arena 域后续变更落地后须重跑（§7） |
| T9 | 统计脚本实测清单 | 待登记——smoke 第 8 项已通过，但 tools 侧统计脚本逐项实测清单尚未建立（§6） |
| T10 | 完整性清单（文件数 + 字节量口径）生成与复核抽检 | 待执行——迁移清单已落盘，逐域/抽行复核待跑；哈希版清单停用（§9） |
| T11 | 离线复核功能 8 项实测 | **部分收口（2/8）**——导入链与 Gold 侧车加载已由首跑覆盖；其余 6 项待实测（§8.1） |

*证据时效：本文全部实测记录取自 2026-11-12（迁移落盘快照）；`research/` 树前移后，引用路径与实测结论需重核。*
