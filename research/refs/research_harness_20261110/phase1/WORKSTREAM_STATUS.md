# RESEARCH-P1 三线工作流状态板

最后更新：2026-11-11（G0 启动批）

## A 线：Benchmark 快速收敛 — ✅ 第一级完成（2026-11-11），第二级并行推进中

| 项 | 状态 | 说明 |
| --- | --- | --- |
| A1 复用现有成果 | ✅ | 冻结 manifest/评分器/校准包/曝光账本全部复用，未重建任何系统 |
| A2 第一级：60 位 DEV 快速评估集 | ✅ | `benchmark/DEV_FAST_SET.json`（sha256 `99e8fe68…`/`69b92cae…` 双哈希主会话复核一致）：wave4_dev 36 + wave5_selection 24，T01–T04 各 8 位（53% 加权），每专题 1 强项+1 弱项（依据两池 live 证据），固定 smoke_24（覆盖 T01–T10，18 弱项标记位）；`build_dev_fast_set.py` 固化规则、重跑字节级一致 |
| A2 第二级：正式评估体系 | ⏳ | 240 条谱系核对/100 位质量复核/20 篇可读案例等，按宪章在 G1 后并行推进（G2 前） |
| A3 五项通用质量指标 Q1–Q5 | ✅ 首版 | 覆盖映射写入 `benchmark/COVERAGE_AND_VALIDITY.md`（Q1–Q5 横跨十专题；T09/T10 覆盖薄已如实登记） |
| A4 判别力验证 | ⏳ | G2 前完成（须实际阅读旧输出与对照样本） |
| A5 缺陷分类 | ✅ 首批 | 第一类（阻塞）：lb_scorer `recommended` 键契约误触发 veto —— universal_scorer v1 口径已规避，本集明确排除该假象；另登记 5 项数据问题（曝光账本滞后未收录 E339–E378、E322 专题标注误导等） |
| 倾斜风险登记 | ✅ | 弱项位全部来自 wave5（证据更强）；缺 parse 失败/illegal_source_id 回归探针（未入集，按同规则扩容时补）；强项 tie-break 偏篇首（10 中 7）；非盲测仅作 DEV 诊断 |

## B 线：外部论文研究与自主机制发现 — ✅ 第一轮完成（2026-11-11）

| 项 | 状态 | 说明 |
| --- | --- | --- |
| B1 独立开展 | ✅ | 独立子代理完成；仅接受任务目标与约束，未读 harness 内部设计；零模型 API 调用 |
| B2 第一轮 ≥12 篇候选 / ≥5 篇精读 | ✅ | **22 篇登记（full-read 7 / abstract-verified 4 / abstract-only 11）、6 个研究方向、25 次检索+14 次抓取逐条记账**（`literature/SEARCH_LOG.md`） |
| B3 检索登记规范 | ✅ | 每篇含 URL/关键词/真实阅读状态/方法/代价证据；缺数据处一律"论文未报告"（含 mem0 写路径成本、缓存命中率等） |
| B4 论文→机制正向流程 | ✅ | 11 条候选全部由论文证据推导；H01–H15 未参与（子代理从未收到该清单） |
| B5 机制候选注册表 | ✅ | `literature/MECHANISM_HYPOTHESES.md` 11 条 × 15 字段齐全；已登记进 `harness/MECHANISM_REGISTRY.md` |
| B6 独立性审核 | ✅ 主会话差异审核 PASS | 无伪机制；相邻对 RH-B-02/06（路由 vs 级联）与 RH-B-04/11（质量锚定 vs 缓存前缀）已按互斥/组合关系登记 |
| B7 首批候选池 ≥8 | ✅ | 11 条（超额）；ESConv"策略预测错误拖累生成"负结果、CoD −4 分精度代价等不利证据均如实登记 |
| 撞墙流程 | 待命 | 尚无撞墙事件；规则已入宪章 P1-03 |

## C 线：实验基础设施与首轮原型 — 🔄 首批原型已实现并进入第 1 级冒烟

| 项 | 状态 | 说明 |
| --- | --- | --- |
| C1 统一输入接口 | ✅ | 白名单表（C1-a）交付于 `harness/COMMON_INTERFACE.md` §2a；三原型入口 gold 泄漏 grep 零命中 |
| C2 统一输出接口 | ✅ | 归一输出记录 + `normalize_output.py` 加法式扩展（LIGHT_PARSE_ARMS）；不强制轻量臂输出旧 JSON |
| C3 统一调用与计量 | ✅ | usage 聚合/重试记账/端点缓存探测核验完成（§3a）；RH-B-11 前缀缓存能力实测存在 |
| C4/C5 首批 ≥3 个论文驱动原型 | ✅ 已实现 | `tools/benchmark_v1/prototype_candidates/`：rh_b04_anchored_layout（RH-B-04）/ rh_b06_tiny_cascade（RH-B-06）/ rh_b09_extractive_slim（RH-B-09）——三类机制互异，独立 v2 候选；RH-B-03 登记为第二批（避免轻量契约上的稻草人实现） |
| 轮位级运行器 | ✅ | `tools/benchmark_v1/prototype_runner.py`（与 four_arm_runner 整集粒度互补；回放语义：目标轮前零网络驱动候选自身代码路径累积历史，目标轮完整执行+评估侧快照评分） |
| C6 第 0 级 | ✅ PASS | plan/mock 72/72 全通；gold 泄漏零命中；attempt 记账含重试 |
| C6 第 1 级 smoke_24 | ✅ 完成（2026-11-11） | 72/72 ok 0 error；三指标与 DIRECT 同位对照写入 `harness/FIRST_PROTOTYPE_RESULTS.md`：wave5 同位 severe b06 **2/14** vs DIRECT 5/14、CURRENT 4/14；b09 e2e p50 最低 16.7s；tokens 全部 ~1.3k/位（CURRENT 4,246）；b06 升级率 45.8% 超预登记（待迭代）；**全部为探索性信号，无自然度判定（已知缺口）** |
| HOLDOUT | ⛔ 本阶段不新增 | 既有实跑停写曝光已标记；E424–E520 封存 |

## G0 启动门进度

| G0 项 | 状态 |
| --- | --- |
| 1. 核实 GitHub 分支与实际文件 | ✅ `bystery/arena` @ `phase3-sync-20261110` / `947770ed`（克隆 D:/tmp/arena-sync-20261107） |
| 2. 保存原三阶段历史成果与状态 | ✅ 原样保留 + 状态文件快照 `_snapshots/` |
| 3. 标记 SELECTION/HOLDOUT 曝光记录 | ✅ `refs/benchmark_v1_20261107/phase3/HOLDOUT_EXPOSURE_MARK_20261111.json` |
| 4. 建立研究目录与三线交付位置 | ✅ 本目录 |
| 5. 启动 Benchmark Worker 与 Research Worker | ✅ 两个独立子代理已派发 |
| 6. Harness Worker 整理统一接口 | 🔄 `harness/COMMON_INTERFACE.md` |

**G0 判定**：全部 6 项启动、5 项已完成 → G0 达成后三线并行（A2 第一级与 B2 为 G1 前置，见 PHASE1_ACCEPTANCE.md）。
