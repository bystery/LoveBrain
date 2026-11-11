# PHASE1_CLOSEOUT_ADDENDUM — Phase 1 收口后 Phase 2 发现/修正事项清单

> 基线：Phase 1 收口（基线 9acb013a）之后的增补台账（§13 交付件，2026-11-08）。
> 只登记 Phase 1 收口时点之后由 Phase 2 发现或修正的事项；Phase 1 既有事实原样引用、不改写。
> 证据全部取自 `refs/benchmark_v1_20261107/phase2/` 落盘文件；行内路径相对 `refs/benchmark_v1_20261107/`。
> 编制纪律：未读 logs/、candidates/、B 线正文（E048 仅引用 Phase 1 报告与 Phase 2 验收的既有结论）；
> 未改任何 gold/正文/分区/账本。格式：事项 | Phase 1 状态 | Phase 2 动作 | 证据路径。

## 1. Gold 修正

| 事项 | Phase 1 状态 | Phase 2 动作 | 证据路径 |
|---|---|---|---|
| E065_T20 卡面背书错误算术（现卡"验算确认…15200 ✓"二次加错并被原子坐实） | 收口时未知，45 篇审核中发现 | REPAIR_OVERLAY：按 E164_T05 先例改陷阱口径——申报 15200 登记 unknown（relation=到账申报（账目陷阱））、forbidden 双向禁背书 15200/14600；独立 overlay 不触碰原卡，base_sha 保留 | `phase2/gold_overlays/E065_gold_overlay_v1.json`（version=gold_overlay_v1，base_sha256=6387266e…e6b8）；`phase2/PHASE2_OLD_STORY_AUDIT.csv` E065 行；`phase2/OLD_STORY_FINAL_DISPOSITION.md` §3-1 |
| LRE121_T23 契约残留（may_decline=False 但 acceptable_actions 留"婉拒并建议改约"） | 收口时未知（sidecar↔reference 全库 diff 仅有的 1 处实质分歧） | overlay 过滤残留"婉拒"项，三路动作改为应下确认/细化流程/打趣报账；may_decline=False 裁决同批入库（final=episode input） | `phase2/gold_overlays/LRE121_gold_overlay_v1.json`（base_sha256=4c8e3ed7…0274 保留）；`phase2/GOLD_CONFLICT_RESOLUTION.csv` LRE121 40 行；disposition §3-2 |
| LRE117 13 处来源冲突一并裁决入库 | Phase 1 勘误阻断（T04/T05 GT 瑕疵待修，eligible_for_reuse=false，未裁决） | 93 处冲突全量裁决时把不在 45 池的 LRE117 13 处一并入库：LD1 schema 缺键判 final=修订链值、intent 过期判 final=W19_CROSS 显式 intent，93/93 有 final_value、无一 N/A 降级 | `phase2/GOLD_CONFLICT_RESOLUTION.csv`（93 行=LRE117 13+LRE121 40+LRE146 40，python 实读复核）；`phase2/PHASE2_ACCEPTANCE.md` #10；disposition §2 |

## 2. 分类修正

| 事项 | Phase 1 状态 | Phase 2 动作 | 证据路径 |
|---|---|---|---|
| 3 篇旧 T02（E032/E039/E042）复审判"弱 T02"→T02 新题基准上调 | T02=3 篇按 primary_topic 名义计入配额（低置信标注） | 45 篇审核逐篇判 T02 信号弱（渐暧昧仅在私有 kb/尾轮，对话面是事务/组队/改约），登记 T02 旧题实际空缺；新题基准上调落实：author brief 质量红线明令"按 rubric 行为锚设计真正信号轮，不靠关键词"、分配表 T02 新题保守口径 36（基准 33 上调），wave4 已建 4 篇新 T02 且 C1 审计判"判别力成立" | `phase2/PHASE2_OLD_STORY_AUDIT.csv` E032/E039/E042 行；`phase2/OLD_STORY_FINAL_DISPOSITION.md` §4/§6-1；`phase2/BATCH1_AUTHOR_BRIEF.md` §4 质量红线；`phase2/DATASET_V1_FINAL_ALLOCATION.csv` PENDING-T02-01…行（"基准33上调，保守口径36"）；`phase2/PHASE2_ACCEPTANCE.md` #2/#28 |
| 16 篇 EXPOSED 兜底题重分类（指导书 §2.3）核对落实 | 136 篇 T01 低置信兜底=120 SEALED（不动）+16 EXPOSED（可查看重分类），Phase 1 未复审 | 核对完成：16/16（E033/E117/E122/E125/E131/E157/E164/E170/E183/E189/E190/E195/E199/E207/E208/E212）全部已在 45 篇审核覆盖，逐篇引用 reviewed_topic（decision 全 ACCEPT、conf=low），不重复审；登记主专题维持 T01，真实测度注记=T07 链×7、T09 链×2、事务意图×1、T01 日常接话成立×6；120 SEALED 零接触 | `phase1/DATASET_INVENTORY.csv`（136 篇清单位，python 实读 120/16 拆分）；`phase2/PHASE2_OLD_STORY_AUDIT.csv` 16 行；`phase2/OLD_STORY_FINAL_DISPOSITION.md` §9（§EXPOSED_RECLASSIFICATION，本次追加） |

## 3. 谱系治理

| 事项 | Phase 1 状态 | Phase 2 动作 | 证据路径 |
|---|---|---|---|
| LIN-E048 污染登记 | Phase 1 §3 在案：B 线 v2 成员 E048 全部 12 轮经库外 LRE048 于 2026-11-01 被逐字消费、重封漏网，参战 LR2 臂的 E048 答卷不具"未见"效力；处置二选一（B 线升 v3 reseal vs 终验折价 binds=discounted_lineage）待用户拍板 | 不冒充独立/不作未见测试执行到位：lineage_check 实跑 b_line_members=0、b_line_related_groups=0 入池；Phase 2 硬约束声明未读 B 线正文；两处置选项原样保留为未决用户裁决项，不阻塞本批 | `phase2/PHASE2_ACCEPTANCE.md` #16（引用 `phase1/SPLIT_AND_CUSTODY_PLAN.md` §3 既有结论）；`phase2/OLD_STORY_FINAL_DISPOSITION.md` §8 |
| E125+LRE125 同池血缘对"只计其一判位"双标 | Phase 1 复用审计/分配计划未给同批双计规则（12 轮 E125 为 40 轮 LRE125 的 T1-T12 逐字前缀） | 45 篇审核登记同池规则并落进终分配表两行：同批使用只计其一的判位，避免同题双计 | `phase2/DATASET_V1_FINAL_ALLOCATION.csv` E125/LRE125 两行 allocation_note（"同批使用只计其一判位"）；`phase2/OLD_STORY_FINAL_DISPOSITION.md` §5/§6-3 |

## 4. Phase 1 遗留闭环状态对照

| 事项 | Phase 1 状态 | Phase 2 动作 | 证据路径 |
|---|---|---|---|
| 45 篇旧题复用预算落实 | 复用审计裁定"160 条预算不可信、实际可复用 45"（缺口 115），逐篇准入未审 | 预算逐篇落实：ACCEPT 42 + ACCEPT_WITH_NA 1（E021）+ REPAIR_OVERLAY 2（E065/LRE121）+ REJECT 0＝42/1/2/0；无淘汰，配额名义保持，真实测度注记另列（§4） | `phase2/PHASE2_OLD_STORY_AUDIT.csv`（45 行）；`phase2/OLD_STORY_FINAL_DISPOSITION.md` §1；`phase1/DATASET_REUSE_AUDIT.md`（Phase 1 结论原样引用） |
| LEGACY dict persona 崩溃（LEGACY_COMPAT_R9 解决） | LEGACY（r9_slim）在 wave 系 dict persona 上 init 即崩，Phase 1 未解 | 输入适配交付：str persona 逐字节等价（测试断言）、dict persona 语义信息无损进入旧闭包字符串通道（规则写死+有测试）、原候选一个字节未改（UNTOUCHABLE 哈希复算一致）；ARM_REGISTRY 登记 LEGACY_COMPAT_R9（注明"经输入适配的历史版本"） | `phase2/LEGACY_COMPAT_REPORT.md`；`phase2/ARM_REGISTRY.json`；`phase2/PHASE2_ACCEPTANCE.md` #20 |
| 真实 API pilot 从 BLOCKED→运行中 | `phase1/LIVE_BLOCKED.json`：LB_API_KEY 未配置（旧 key 泄露须 USTC 撤销重发），live precheck 未过，pilot 只能 mock（latency p50=4ms、api_usage_rows=0） | 用户 2026-11-08 提供 USTC key（env:LB_API_KEY，setx 持久化、仓库零落盘）；PILOT2 授权+40 篇子集落盘（cap 1500、并发 4 如实登记、禁改题库/禁 B 线/禁 HOLDOUT）；live pilot 运行中——验收 12:02 快照 414 attempts 已落盘，LRE121 CURRENT 第 5 轮 TimeoutError 如实登记、不伪造完整结果 | `phase1/LIVE_BLOCKED.json`（Phase 1 状态）；`phase2/PILOT2_AUTHORIZATION.json`（AUTH-BM1-PHASE2-PILOT-20261108-01）；`phase2/PILOT2_SUBSET.json`；`phase2/PHASE2_ACCEPTANCE.md` #26 |
| 12 盲评位 Mock 占位→真实输出替代 | Phase 1 盲评包 12 位（E032×4/E037×2/E191×3/LRE117×3）全部 mock 文本占位，用户结果 NOT_COLLECTED | Phase 2 pilot 真实输出替代：live run 落盘真实回复，登记刷新义务——锚点队列以 build_human_review_queue.py --run-dir 指向 live 目录重跑填充；现行 40 锚包 run_dir 仍指 mock 版、40/40 PENDING_USER（AI 不代投），旧 T02 4 锚（A01–A04）因旧题不在 pilot2 子集显式"待" | `phase1/PILOT_BLIND_REVIEW.md`+`phase1/PILOT_BLIND_SELECTION.json`（12 位，Phase 1 状态）；`phase2/HUMAN_REVIEW_QUEUE.json`；`phase2/PHASE2_ACCEPTANCE.md` #12/#26 及头部刷新义务 |

## 5. 未修正 / 留待项

| 事项 | Phase 1 状态 | Phase 2 动作（=现状） | 证据路径 |
|---|---|---|---|
| 120 篇 SEALED 兜底题 | SEALED 封存，Phase 1 不动 | 继续零接触：本轮仅用盘点 CSV 元数据计数核对 120/16 拆分，未读任何正文；逐篇归位复核留 Phase 3 扩量前 | `phase1/DATASET_INVENTORY.csv`（计数位）；`phase2/OLD_STORY_FINAL_DISPOSITION.md` §6-2/§9 |
| C15 护栏等评分侧移交 Phase 3 | Phase 1 无该清单 | 评分侧遗留登记移交 Phase 3：C1 MAJOR-8 评分侧否定/疑问护栏、marker-fix-2 承接（unknowns 对象侧 303 处 48% 非逐字未修——官方 QC 只扫原子侧，涉 C15 误伤权衡） | `phase2/PHASE2_ACCEPTANCE.md` #13（移交去向在案）；`phase2/PHASE3_PREREGISTRATION.md`（Phase 3 入口） |
| arena/lb_gateway.py 漂移 + 离线测试基线 | 冻结哈希在案（UNTOUCHABLE_HASHES：56867a44…） | 验收实测 2026-11-08 11:43 被改（现 1ca5976e…），pytest 31 过/2 败；收口要求：pilot 结束后登记变更理由并按 §9 版本化（或回滚），恢复全绿后 #21 方可升 DONE | `phase2/PHASE2_ACCEPTANCE.md` #21 与"验收时新发现"节 |
| GitHub 推送 | Phase 1 验收同项 ⏳ | 仍 PENDING：工作目录非 git 仓库无法本地核验；推送后按惯例回填 commit SHA | `phase2/PHASE2_ACCEPTANCE.md` #24 |

---
编制：AI（Phase 2 文档与治理会话，2026-11-08）；全部数字 python -I 实读复核，非转抄叙述。
