# Benchmark V1 · Phase 2 Pilot 报告（PHASE2_PILOT_REPORT）

> 授权：AUTH-BM1-PHASE2-PILOT-20261108-01（cap 1500）｜模型 deepseek-flash-2 @USTC，temp 0.7，
> stream=false ｜执行日 2026-11-08 ｜**不进行冠军判决**（指导书 §10.2）——本报告只回答
> §10.3"新题质量验证"。

## 0. 执行概况

| 项 | 值 |
|---|---|
| 批次 | 三进程分片：part_b（E301–E319）/ part_c（E320–E338）/ oldT09（E063+LRE121） |
| 样本 | 40 篇（wave4 38 + 暴露旧 T09 2，PILOT2_SUBSET.json） |
| 轮位 | 552 轮 × 2 臂 = 1,104 位（DIRECT 552 / CURRENT 552） |
| API attempts | **1,163 / 1,500**（b=476, c=557, old=130；权威口径=三目录 gateway_events `attempt_started` 实数） |
| capture 校验 | 3 part 各 9/9 全过（含输入等价/gold 零泄漏/路由哈希/Key 零落盘） |
| 运行事故与处置 | 120s 读超时（长上下文）→ `LB_API_TIMEOUT_S=300` 环境旋钮（默认不变）；503 瞬态 → resume 自动循环扫尾至清零；records 被子集调用覆盖 → reconstruct_records.py 从 checkpoint 目录重建（目录为事实源）；全过程零质量重跑（折损均为基础设施类） |

## 1. §10.3 检查逐项判定

| # | 检查项 | 判定 | 证据 |
|---|---|---|---|
| 1 | 所有故事机械校验通过 | ✅ | validate ×3 全绿（488 case 0 错误）；sidecar strict 1,350/1,350；Marker QC 0 |
| 2 | Gold 来源核验通过 | ✅ | 1,350 原子 quote 逐字全命中 + 2,610 条独立复验 0 失败；conflict 0 |
| 3 | 专题标注与实际测试内容一致 | ✅（附辅助读数） | 三审计确认（C1：T02 信号梯度扎实判别力成立；C2：T06 12 指令轮均落成可核验差异）。词族粗匹配率低（T01 4%/T04 36%）为词族法弱证据——T01/T02 本无特征词，不作判据 |
| 4 | 评审能用行为锚区分 0/1/2 | ✅ 预审 + 用户终核 | C1 预审"判别力成立"（每篇 ≥4 裸信号轮+反诱惑对照位）；40 锚包已备，用户亲核前不终判 |
| 5 | 不可判位已修正或诚实 N/A | ✅ | B1–B6+审计遗留 22 条全登记（digest）；required 覆盖面口径、conflict 层 1 不判分等已定稿进 V1 规范 |
| 6 | 两组差异与失败案例记录 | ✅ | 本报告 §2/§3（PILOT_ANALYSIS.json 全量数字） |
| 7 | 不为 CURRENT 赢改题 | ✅ | 题目在 pilot 前全部冻结（五件套+manifest 哈希三层对账）；pilot 期间零改动 |

## 2. 臂间差异（证据，非结论）

**臂间零雷同**：543 对同轮位回复，identical=0、near_dup(ratio≥0.9)=0、无高雷同篇——
**§10.3"两组完全相同的机械答案"= 无**。

**协议可靠性（两臂都高）**：parse_ok 1,103/1,104（DIRECT 1 位 parse 失败：E325_T01）；
contract_ok：DIRECT 551/552、CURRENT 550/552。CURRENT 的 2 位 veto 为
`illegal_source_id`（E307_T02、E322_T02——analysis 引用了非法来源 ID，属真实协议边界
违例，层 1 正确拦截，是**有效判分**的实例）。

**⚠ score10/veto 列的口径警告（防误读）**：DIRECT veto=544/552、score10≈0，
CURRENT veto=2、score10≈10——这不是质量差异，是**轻量臂计分契约差异**（Phase 1 已知：
DIRECT/COT 的 `recommended` 输出键不在 lb_scorer 的 PROTOCOL_TOP_KEYS 白名单 →
scorer_veto 全量触发；normalize 对轻量臂不覆盖 contract_ok，但 score10/veto 列如实
记录了 veto）。**本 pilot 不据此宣布任何臂质量优劣**；评分器对轻量输出键的口径修正
按 §9 版本化纪律移交 Phase 3 预注册。

**资源成本（真实信号）**：

| 臂 | 时延 p50 | 时延 p95 | tokens(prompt+completion) | 合计 tokens |
|---|---|---|---|---|
| DIRECT | 12.2s | 33.9s | 613,845 + 190,446 | 804,291 |
| CURRENT | 68.4s | 131.1s | 1,426,349 + 823,006 | 2,249,355 |

harness 相对 light 的成本：时延 ≈5.6×（p50）、tokens ≈2.8×——与 CURRENT 每轮重建
全历史上下文的机制一致（这正是 120s 超时事故的根因）。**等待/tokens 是 Phase 3 正式
对照的一等指标**（此 pilot 读数为正式批的预算基线）。

**退化形态**：万能句 0/1,095；开头 8-gram 无模板化（DIRECT top share 0.55% 且为合理
复述，CURRENT top=1 次纯随机）；长度分布健康（DIRECT p50 56 字 / CURRENT 44 字）——
无客服腔、无复读机迹象。

**diag_required_hit_rate**：两臂 0/46——wave4 required 存活原子少（51 处集中在部分篇）
且 required 位多为语义强约束（层 1 关键词判不可靠），诚实 N/A 口径生效；required 命中
的严肃测量依赖 Phase 3 层 2+人工。

## 3. 折损位清单（供 Phase 3 归因）

| 位置 | 类型 | 性质 |
|---|---|---|
| DIRECT/E325_T01 | parse 失败 1 位 | 唯一轻量臂解析失败 |
| CURRENT/E307_T02、E322_T02 | veto illegal_source_id | 真实协议违例（层 1 有效拦截） |
| （历史）23 篇臂超时/503 折损 | 基础设施 | 已全部扫尾补齐，轮位数据完整；过程记录在 gateway_events |

## 4. 结论（pilot 范围内）

1. **新题可运行、可评分、可复现**：40 篇全部跑通两臂，1,104 位输出落盘可复核；
2. **无机械答案、无设计痕迹、无万能句**——§10.3 的"题是不是白给"检查无负面迹象；
3. **题目判别力**：程序层证据（雷同/退化/协议违例）全部健康；语义判别力（T02 0/1/2、
   T03 深聊质量）的最终确认依赖 40 锚用户亲核——锚点包已就绪；
4. **资源基线确立**：CURRENT 68s/2.25M tokens vs DIRECT 12s/0.80M tokens，为 Phase 3
   预算与等待指标提供实测基线；
5. **未竟事项**：评分器轻量键口径（Phase 3 版本化处理）；语义判别终核（用户锚点）。

## 5. 数据位置

- 合并底表：`pilot2_merged/PILOT_RESULTS_MERGED.csv`（1,104 行）+ `merge_summary.json`
- 判别力证据：`pilot2_merged/PILOT_ANALYSIS.json`（csv_sha256 绑定）
- 分片明细：`runs/pilot2_wave4_live_b|_c|_oldT09_live/`（aggregate 报告 + gateway_events 全量）
