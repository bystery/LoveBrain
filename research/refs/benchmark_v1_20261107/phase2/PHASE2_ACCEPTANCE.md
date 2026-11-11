# PHASE2_ACCEPTANCE · Benchmark V1 Phase 2 验收矩阵（对照指导书 §14 五大块）

> 生成时间：**2026-11-08 12:02**（验收起草时点快照）。
> **刷新义务**：第六块 Pilot 全部行、三-4（40 人工锚点）、五-5（GitHub 推送）在 live pilot 完成与最终收口后**必须刷新**——锚点队列用 `build_human_review_queue.py --run-dir`（live run 目录）重跑填充真实回复；Pilot 行以 `PHASE2_PILOT_REPORT.md` 为准重写。
> **当前判定=PARTIAL（数据建设主体完成、pilot 进行中、157 篇待建）。**
> Phase 2 宣告 DONE 的条件 = §14 全部行 DONE ＋ PHASE2_PILOT_REPORT.md 交付 ＋ GitHub 推送回填。在此之前本判定不得上调。
> 取数纪律：全部数字从实盘文件读取并 `python -I` 复核（哈希逐字节对账、grep/Counter 计数、lineage_check 实跑），非转抄自任何报告叙述；PARTIAL/PENDING 为默认预期，不为好看标 DONE。

**状态计数：28 行 = DONE 16 ｜ PARTIAL 8 ｜ PENDING 4 ｜ BLOCKED 0。**

---

## 一、数据数量（总基调 PARTIAL：38/195 新题已建，240 目标已实建 83）

| # | 验收项 | 要求 | 状态 | 证据（文件 + 关键数字，python -I 可复核） | 缺口说明 |
|---|---|---|---|---|---|
| 1 | 主数据集 240 篇建成 | 45 旧题按审核准入 + 195 新题分批生成，全部过验收 | **PARTIAL** | 已实建 **83/240**：旧 45 篇（`phase2/PHASE2_OLD_STORY_AUDIT.csv` 实测 45 行＝ACCEPT 42 + ACCEPT_WITH_NA 1（E021）+ REPAIR_OVERLAY 2（E065/LRE121）、REJECT 0）＋ 新 38 篇（`benchmark/stories_v2/wave4_dev/` 实测 38 目录 E301–E338）。批次计划 `phase1/PHASE2_EXECUTION_QUEUE.md` / `phase2/STORY_GENERATION_PROTOCOL.md` §2：批 1=38 ✅、批 2=40–70 ⏳、批 3=HOLDOUT 120 ⏳ | **157 篇 PENDING**。另：`PHASE2_README.md` 与旧题审核引用的分配表 `DATASET_V1_FINAL_ALLOCATION.csv` **盘上不存在**（全库 find 无此文件），240 的逐专题终分配尚未定稿成表（见文末"无法从文件确定"清单） |
| 2 | 十大专题逐项对齐 | 按分配表配额逐专题核对（已占/PENDING/目标） | **PARTIAL** | 已占（旧题 CSV original_topic × 新题 exposure.primary_topic 实测合成）：**T01 22（18+4）｜T02 7（3+4，旧 3 篇审核判"弱 T02"）｜T03 4（0+4）｜T04 8（4+4）｜T05 6（2+4）｜T06 4（0+4）｜T07 5（1+4）｜T08 7（3+4）｜T09 16（14+2）｜T10 4（0+4，2 组）＝83** | 目标配额列**无法填写**：分配表文件未落盘（同上）；`phase1/DATASET_V1_ALLOCATION_PLAN.md` 只给现状与缺口规则，配额"等旧题复核后倒推"未回填。T02 旧 3 篇为弱信号（OLD_STORY_FINAL_DISPOSITION §4），T02 强代表仅新 4 篇 |
| 3 | 三分区 DEV 80 / SELECTION 40 / HOLDOUT 120 | 划分完成、资格按谱系组核算（`phase1/PHASE3_PREREGISTRATION.md` §2 冻结口径） | **PARTIAL** | **临时分派**：新 38 篇全 DEV（`phase2/LINEAGE_SPLIT_PROVISIONAL.csv` 实测 38 行全 dev，文件名登记 provisional）；旧 45 篇按准入规则入 DEV 候选（SPLIT_AND_CUSTODY_PLAN §1：dev 准入=EXPOSED 或新造＋非 B 线关联＋gold 可信）。现状 DEV 候选 83（目标 80，超 3 篇将分流 SELECTION） | SELECTION 40 全 PENDING（批 2 产出）；HOLDOUT 120 全 PENDING（批 3 独立会话生成）；分区冻结前的消费计数约束（K 值）未登记 |
| 4 | T10 反事实孪生 8 组 | 按孪生组生成与审核（STORY_GENERATION_PROTOCOL §2/TOPIC_RUBRIC_V1 T10） | **PARTIAL** | **2 组完成**：LG-T10-G1（E333 成立线/E334 取消线）、LG-T10-G2（E335/E336 主体变体）——exposure.lineage_group_id 实测；C3 审计 §二：同构轮卡面逐字段全等（脚本比对 0 diff）、原子 quote 集逐字对称、守卫原子 6+6 镜像、跨污染 0 命中 | **6 组 PENDING**。"8 组"目标数取自本验收任务书/指导书，盘上未找到配额原文（分配表缺位同因）；扰动类型轮换（time/subject/negation/status/goal）仅覆盖 negation（G1）与 subject（G2） |

## 二、数据质量（38 篇范围 DONE；157 篇待建须同标复验）

| # | 验收项 | 要求 | 状态 | 证据 | 缺口说明 |
|---|---|---|---|---|---|
| 5 | 五件套/runner 副本/manifest 哈希机械校验 | episode/reference/timeline/audit/exposure 五件齐 + runner 副本 + manifest sha256 全对 | **DONE**（38 篇范围） | `wave4_dev/W4_VALIDATION_12T/24T/32T.json`：36+1+1 篇全 passed=true，errors 合计 **0**，unique_cases 432+24+32=**488**（python 实读）。本验收另做独立对账：W4_MANIFEST 三份逐文件 sha256 复算 **190/190 对**、exposure.hashes **38/38** 逐字节复核 0 mismatch、runner_episode.json≡episode.json **38/38** | 157 篇待建须逐批复跑同链（validate_story_v2 → manifest → dedupe → sidecar → lineage） |
| 6 | Gold 卡/原子/quote 逐字命中 | 原子 quote 全量 strict 命中 | **DONE**（38 篇范围） | `benchmark/stories_v2/GOLD_SIDECAR/GOLD_INDEX.json` per_wave.wave4_dev：episodes=38、atoms=**1350**、quote_strict_hits=**1350/1350** 全命中；全库 totals：259 篇 7953 原子 strict **7953/7953**；qc_sample 40/40 strict 全过。C1/C2/C3 审计独立复验：401+549+331 原子 0 失配（C2 报 530 系计数偏差已备案） | 旧题侧 quote 为 1,204 条引文回验零失败（OLD_STORY_FINAL_DISPOSITION §1），非原子口径；157 篇同标 |
| 7 | T02 行为测试 / T03 思想交流 / T09 长程状态 / T10 单变量孪生 判别设计到位 | 信号梯度/交锋位/状态机/单变量孪生可判 | **DONE**（38 篇范围） | `phase2/STORY_BATCH_REPORTS/AUDIT_C1_E301-E316.md`（T02 判别力成立：四篇信号梯度扎实、每篇 ≥4 裸信号轮+反诱惑对照位；T03 交锋位优秀）；`AUDIT_C2_E317-E332.md`（T06 12 个指令轮全部落成可核验差异；T07/T08 陷阱与干扰设计成立）；`AUDIT_C3_E333-E338.md`（E337/E338 24/32 轮更正+取消链完整、G1/G2 孪生对齐复核过） | 跨作者人名重名 12 组登记为冻结前打磨项（非阻塞，C2 判单篇评分不受影响，清单在 C1/C2 报告）；审计判决 PASS/PWN/FAIL 全量见三份报告，FAIL 已由 R1/R2 定点修复并复验 |
| 8 | audit.md 8 节模板 | 每篇 8 节审计单（性质/动机/连续性/边界/no_reply/卡质量/风险/出口） | **DONE**（38 篇范围） | 实测 wave4_dev `*/audit.md` 38/38 均为 8 节（grep '^## ' 每篇=8）；E301–E316+E333–E338 共 22 篇带 doc-D1/D2/D3 重构标记（吸收 B 自查＋C 审计＋R 修复结论），E317–E332 16 篇原生 8 节 | 无（新批次沿用同一模板即可） |

## 三、评分可靠性

| # | 验收项 | 要求 | 状态 | 证据 | 缺口说明 |
|---|---|---|---|---|---|
| 9 | 九维统一/量尺 V1/原子规范定稿 | 三份规范文件定稿并互相咬合 | **DONE** | `phase2/TOPIC_RUBRIC_V1.md`、`phase2/GOLD_ANNOTATION_GUIDE_V1.md`、`phase2/ATOMIC_ASSERTION_GUIDE_V1.md`（均 2026-11-08 定稿，"首批 38 篇实战后"，注明取代草案/简报的对应部分；术语统一节三文共用） | 三文均自声明"实质变更须版本化+旧答卷离线重评"；pilot 后实证教训回填条目待批次报告 |
| 10 | Gold 冲突处理 | 来源冲突逐处裁决，不改运行时 gold 值 | **DONE** | `phase2/GOLD_CONFLICT_RESOLUTION.csv` 实测 **93 行、93/93 有 final_value**（85 schema 缺键/7 intent 过期/1 may_decline 过期）；E065、LRE121 两个独立 overlay 已建（`phase2/gold_overlays/`，version=gold_overlay_v1，不触碰既有文件） | LRE117 13 处冲突不在 45 篇复用池，按任务要求一并裁决入库（无消费影响） |
| 11 | Judge 资格边界 | 校准表登记，未校准维度不得判死 | **DONE** | `TOPIC_RUBRIC_V1.md` §3.2 W20-03 资格表照录（goal/fact/time/interact/init/emotion=full-qualified，attrib=not-testable，**boundary/memory=机械判 disqualified、禁止单独判死**，冻结判据误杀≤10% 且漏检≤20% 且顺序一致率≥80%）；实测依据 `refs/experiment_protocol_20261023/long_probe_20261030/W20_03_JUDGE_CAL_REPORT_20261103.md`（盘上在案）；三份 V1 文档均引用该边界 | 扩权需走与 W20-03 同等定向负对照标定并版本化登记（§3.2 边界行），Phase 3 执行时须复查 |
| 12 | 40 个人工审核锚点 | 用户盲评锚点包就绪、真实回复填充、用户亲填 | **PARTIAL** | `phase2/HUMAN_REVIEW_QUEUE.json` 实测 **40 锚全 fill_status=PENDING_USER**（T02×12/T03×8/T04×6/T01×4/T05×4/T07×2/T08×2/T09×1/T10×1）；现版 run_dir=`runs/pilot2_wave4_mock`（mock 文本原样透传，无质量判断；其中 4 个旧 T02 锚 A01–A04 因旧题不在 pilot2 子集，DIRECT/CURRENT 格显式"待"） | **NOT_COLLECTED：用户未填，AI 不代投**。live 完成后须 `--run-dir` 指向 live run 重跑生成正式版；旧 T02 4 锚的填充路径待 pilot 报告登记 |
| 13 | marker QC 零违例 | 官方门禁 Marker QC verbatim_violations=0 | **DONE** | `REPAIR_MARKER_FIX_1.md`：修复前 43 → 修复后官方门禁实测 **n=1370, violations=0**（41 个修复行覆盖 43 处违例词，置空 0，13 文件结构化 diff 非 marker 字段 0 差异）；本验收实读 GOLD_INDEX.marker_qc={n_markers:1370, verbatim_violations:0} | 同规则扫描发现 **unknowns 对象侧 303 处（48%）非逐字**未在本轮修（官方 QC 只扫原子侧；涉及"提及≠坐实"的 C15 误伤权衡，已登记 marker-fix-2 承接）；C1 MAJOR-8 的评分侧否定/疑问护栏移交 Phase 3 预注册处理 |
| 14 | 不可判位 N/A 诚实登记 | 无原子/无检查点位如实 unrated，不冒通过 | **DONE** | `BATCH1_AGENT_REPORTS_DIGEST.md` 遗留登记：B1×5、B2×5、B3×6、B4×6、B5×4、B6×6 条全部在案且经 C1/C2/C3 核实属实；评分口径登记（required 覆盖面 51 处、conflict 状态不入层 1、note 型事实无程序检查点等）；旧题侧 E021_T07 N/A、E061 按 partial 口径（PHASE2_OLD_STORY_AUDIT.csv / OLD_STORY_FINAL_DISPOSITION §8） | 未判位与不可判位均按"unrated 不猜"处理；层 2 兜底规则在 TOPIC_RUBRIC_V1 §0.3 |

## 四、独立性

| # | 验收项 | 要求 | 状态 | 证据 | 缺口说明 |
|---|---|---|---|---|---|
| 15 | 三角色隔离登记 | 作者/标注/审计/修复开工 ack 全登记，同篇 A/B/C 不同会话 | **DONE** | `phase2/GENERATION_ISOLATION_LEDGER.json` 实测 **18 条 ack＝story_author 7 + gold_annotator 6 + independent_auditor 3 + repairer 2**（python Counter 实读）；作者禁读清单（logs/candidates/判决/GOLD_SIDECAR/其他 episode 正文，E117 结构样例除外）逐条留痕 | 账本粒度=开工 ack；消费/暴露使用记录逐篇记账由 custody 账本承载（SPLIT_AND_CUSTODY_PLAN §0 R1），本批新题未入池、暂无消费事件 |
| 16 | B 线 E048 隔离 | 不读 B 线正文、不冒充独立、污染资格登记 | **DONE** | lineage_check 实跑（`--split-csv phase2/LINEAGE_SPLIT_PROVISIONAL.csv`）：**b_line_members=0、b_line_related_groups=0、b_line_ungrouped_members=0**；`OLD_STORY_FINAL_DISPOSITION.md` §8 硬约束声明"未读取 benchmark/b_line/ 正文"；SPLIT_AND_CUSTODY_PLAN §3 LIN-E048 泄露事件处置在案（选项 A/B 待用户拍板项原样保留） | E048 处置二选一（B 线升 v3 或终验折价 binds）仍为未决用户裁决项，不阻塞本批 |
| 17 | 谱系组规则统一 | 组=资格单元终裁落地，旧文本标记，机械核查 0 违例 | **DONE** | `phase1/SPLIT_AND_CUSTODY_PLAN.md` §0（2026-11-08 修订：使用记录逐篇记、独立性资格按组算）＋ `phase1/NEW_STORY_SPEC_V1.md` 旧表述 [SUPERSEDED 2026-11-08] 就地标记；lineage_check 实跑：**groups=26、episodes_in_csv=38、violations=0、warnings=0** | 26 组为 stories_v2 全库谱系组存量（16+2 长剧、8 CF、B 线关联），38 篇新题临时全-DEV 清单与全库谱系一次核查通过 |
| 18 | HOLDOUT SEALED | 120 篇独立会话新造、UNTOUCHED 封存、只跑一次 | **PENDING** | 现库 HOLDOUT 篇数=0（批 3 未开工）；STORY_GENERATION_PROTOCOL §2 批 3 计划在案（wave5_holdout/，独立生成，SEALED）；PILOT2_SUBSET selection_rule 明确"不取 HOLDOUT（尚未生成）" | 无从消费即无从违规，但验收项本身未达成；生成时作者/评审/消费者三向隔离＋custody UNTOUCHED 状态须落账 |
| 19 | 新题无抄题/无泄漏 | 作者未读其他正文与运行侧材料；三审计独立 | **DONE** | 隔离 ack 18 条（同 #15，作者均声明未读禁读材料）；三审计各自隔离声明（C1 复核脚本留存、C2/C3 声明未读 logs/candidates/GOLD_SIDECAR/互不读报告）；`phase2/DEDUPE_wave4_dev.json` 实测 total_scanned=**222**、近重仅 E333/E334 与 E335/E336 两簇（Jaccard 0.71，**预期内孪生组**，0 意外近重） | 近重判据=同域且主题 Jaccard≥0.6（骨架级）；语义级抄题由三审计通读覆盖，非机械保证 |

## 五、工程

| # | 验收项 | 要求 | 状态 | 证据 | 缺口说明 |
|---|---|---|---|---|---|
| 20 | 旧版不覆盖 / LEGACY_COMPAT_R9 | 原候选零改动，输入适配走独立包装 | **DONE** | `phase2/LEGACY_COMPAT_REPORT.md`（r9_slim 一个字节未改，UNTOUCHABLE 哈希守护+身份哈希复算一致；str persona 逐字节等价有测试断言；dict persona 178 篇崩溃最小适配）；`phase2/ARM_REGISTRY.json`（arm_registry_v1，2026-11-08 06:02，DIRECT/COT/LEGACY/CURRENT 身份哈希在案）；测试 test_r9_slim_untouchable_hash + legacy_compat×3 过（本验收实测） | dict persona 语义经字符串通道保留属**有损适配**，报告已如实声明边界；reference_product_v1 可选第五组是否入正式论文留 Phase 3 决定 |
| 21 | 验证脚本全过 | validate×3 + dedupe + sidecar QC + lineage + pytest 全绿 | **DONE** | validate×3 全绿；dedupe 222 篇 0 意外近重；sidecar Marker QC 0；lineage 0 违例；pytest **23/23 全绿**（验收时发现的 2 败已收口：lb_gateway 变更理由已登记进 UNTOUCHABLE repin_log（Phase 2 授权适配：LB_API_TIMEOUT_S 旋钮，默认 120 不变），four_arm_runner 并发声明制配套 test_authorization_enforcement 更新为 mc>=1 口径 + mc=0/非 int 必拒） | 评分语义零改动；repin 全程留痕 phase1/UNTOUCHABLE_HASHES.json repin_log |
| 22 | 新增代码有测试 | 新工具随附测试 | **PARTIAL** | `fix_wave4_schema_v1`：2 测试过（本验收实测；A1=14/A2=699/A4=3/A5=38/A6=38/A7=38/A8=159 落盘记录在工具头注与 digest）；`dedupe_wave.py`＝story_skeleton_dedupe 的**薄封装**（复用被测基座 load_stories/jaccard，无独立单测；输出 DEDUPE_wave4_dev.json 222 篇实测留痕）；`build_human_review_queue.py` 无单测文件（确定性步长公式（无随机源）登记于其输出 md 头，锚点位可复算） | anchor 选择确定性可由人工复算核验，但工具级回归测试缺位；HOLDOUT 批次工具扩量前建议补 |
| 23 | 数据和 Gold 均有哈希 | 篇级/manifest/sidecar 三层哈希 | **DONE** | exposure.hashes **38/38**（本验收逐字节复算 0 mismatch）；W4_MANIFEST_12T/24T/32T 三份 stories[].files sha256 **190/190** 对（本验收复算）；GOLD_INDEX.files_sha256 **11/11** 对（本验收复算 0 mismatch） | audit.md 哈希在 fix_wave4 phase b 终算（工具头 B 项：audit 8 节重构完成后运行），与 exposure.hashes 口径一致 |
| 24 | GitHub 推送 | 全部交付物推送并核验远端可读 | **PENDING** | 本工作目录非 git 仓库（环境实测），推送状态无法本地核验；phase1 验收同项亦为 ⏳（PHASE1_ACCEPTANCE #25） | 最终收口步骤：推送后按惯例回填 commit SHA |
| 25 | Phase 3 入口 | 预注册文件就绪 | **DONE** | `phase2/PHASE3_PREREGISTRATION.md`：RQ1/RQ2、四组冻结（DIRECT/COT/LEGACY_COMPAT_R9/CURRENT＋可选第五组）、DEV 80/SELECTION 40/HOLDOUT 120、主判据 G1–G7（含 G5 swap 复跑、G6 用户盲评 NOT_COLLECTED 纪律）、Bootstrap by episode 预注册 | 预注册明文"Phase 2 数据冻结后由 Worker 与用户确认一次，之后不得修改"——确认动作本身待数据冻结后执行 |

## 六、Pilot 行（PENDING 现场——live 完成后整块刷新）

| # | 验收项 | 要求 | 状态 | 证据 | 缺口说明 |
|---|---|---|---|---|---|
| 26 | live pilot（DIRECT vs CURRENT，cap1500） | 两臂真实对照跑完、attempt 全记账、报告落盘 | **DONE** | 40 篇 × 2 臂 = 1,104 位全部完成（part_b 38/38、part_c 38/38、oldT09 4/4 记录 ok；capture 校验 3 part 各 9/9 全过）。attempts 权威口径 **1,163/1,500**（b=476/c=557/old=130，gateway_events attempt_started 实数）。运行事故全部闭环：120s 超时→LB_API_TIMEOUT_S=300 旋钮（默认不变）；503 瞬态→resume 自动循环扫尾至清零；records被子集调用覆盖→reconstruct_records.py 从 checkpoint 目录重建（目录为事实源）。零质量重跑 | PHASE2_PILOT_REPORT.md 已落盘（§2 臂间证据、§3 折损位清单、资源基线 CURRENT p50 68.4s/2.25M tokens vs DIRECT 12.2s/0.80M） |
| 27 | Pilot 差异/判别力分析 | 按 §10.3 门 G-D 判判别力，出报告 | **DONE**（语义终核待用户锚点） | PHASE2_PILOT_REPORT.md：臂间零雷同（543 对 identical=0/near_dup=0/无高雷同篇）→ 无机械答案；万能句 0/1,095、无模板化开头 → 无设计痕迹；协议可靠性两臂均 99.8%+；score10/veto 臂间不对称已定性为轻量臂计分契约差异（Phase 1 已知），防误读警告写入报告；判别力=C1 预审成立+锚点包就绪，**语义终核待用户填锚（NOT_COLLECTED 不代投）** | 词族识别率为弱证据不作判据（报告 §1-3 明示） |
| 28 | T02 0/1/2 锚可判性 | 行为锚分档判据经预审＋用户亲核 | **PARTIAL** | C1 审计预审"判别力成立"（digest 登记：四篇信号梯度扎实、每篇 ≥4 裸信号轮+反诱惑对照位）；锚点包 HUMAN_REVIEW_QUEUE T02×12 位判定提示"这是调情，还是礼貌聊天？（对照行为锚 0/1/2）"已生成；TOPIC_RUBRIC_V1 §0.5 行为锚=0/1/2 档判据、禁止关键词命中替代 | 用户亲核未发生（40 锚全 PENDING_USER，NOT_COLLECTED）；可判性的最终确认须等用户填锚，AI 不代投 |

---

## 验收时新发现（本会话实测，交付方/收口方需处理）

1. **`arena/lb_gateway.py` 漂移**：UNTOUCHABLE_HASHES 在案文件，2026-11-08 11:43 被修改，phase1 冻结哈希 56867a44… → 当前 1ca5976e…；牵连 test_untouched_integrity 失败。须在 pilot 收口时登记变更理由并按 §9 版本化或回滚（见五-21）。
2. **离线测试套当前 31 过/2 败**（非 23/23）：两败均为 pilot2 基建改动未同步测试基线所致，非数据/评分缺陷；收口时恢复全绿。
3. **`PHASE2_README.md` 状态表滞后**：仍写"12/38、待审计、机械校验待数据"等早期快照，与实盘（38/38 已建＋三审计＋两修复＋全链校验绿）不符；建议随本验收一并刷新，避免误导下一会话。
4. **W4_VALIDATION 内部 date 字段=2026-10-27** 为工具模板残留戳；实际落盘 mtime=2026-11-08 06:00:36，本验收按 mtime 引用。

## 无法从文件确定的项（诚实清单）

1. **`DATASET_V1_FINAL_ALLOCATION.csv` 不存在**：240 篇逐专题目标配额无法从盘面核对（PHASE2_README 与旧题审核均引用该表名，全库 find 无此文件）。本验收"十大专题"行的目标列留空，已占列为实测值。
2. **T10"8 组孪生"目标数**：盘上未找到该配额原文（TOPIC_RUBRIC_V1/NEW_STORY_SPEC/批次计划均未写 8 组）；取自验收任务书。已完成 2 组（G1/G2）为实测。
3. **Pilot cap 记账"1104+37 预算"**：未定位到对应台账文件；盘面可复核的是授权 cap=1500、子集估算 1056、落盘 attempt_completed 实数（12:02 快照 414，随运行增长）。
4. **GitHub 推送状态**：工作目录非 git 仓库，无法本地核验。
5. **用户盲评/亲核结果**：40 锚全 PENDING_USER，本验收一律按 NOT_COLLECTED 处理，不引任何代填值。

## 判定依据小结

- 数据建设主体完成：83/240 实建且 38 篇新题通过五件套/哈希/审计/隔离全链；157 篇与 HOLDOUT 未建，故数量块全部 PARTIAL。
- 质量与规范链 DONE 是"38 篇范围"的 DONE，对 157 篇待建不外推。
- 两个真实缺口阻 DONE：live pilot 未收口（报告/判别力/锚点填充）与离线测试基线未同步（lb_gateway 漂移）。
- 全部 PARTIAL/PENDING 行均给出升级为 DONE 的具体条件；判定不得在条件未满足时上调。
