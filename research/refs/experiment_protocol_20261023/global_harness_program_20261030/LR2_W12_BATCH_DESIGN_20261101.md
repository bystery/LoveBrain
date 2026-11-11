# LR2（>24 轮长剧域扩测）+ W12 素材批 可执行设计（2026-11-01）

> 状态：设计稿（纯设计，零模型调用）。执行前须按 §7 清单逐项确认 UNKNOWN。
> 上游判据继承：`LR1_VERDICT_W1005_20261031.md`、`G1_VERDICT_20261031.md`、`W09-06_FREEZE_RULING_20261031.md`（保护维 ≥−0.5、severe 不增、veto ≤3×）。
> 停止条件继承 agents.md §8；隔离纪律继承 `W03-05_CUSTODY_PROTOCOL_20261031.md`。

## 0. 背景与唯一未决轴

- LR1（`logs/longprobe_lr1_live_20261030`，48/48 零失败）：v0_minimal_events 在 E061（24 轮）零塌陷，prompt −52.2%，九维 8/9 正向；但 T20–T24 已现首条最老事实滑出（18/19）。>24 轮是否反超/塌陷，双方均无证据（RESEARCH_STATE.json `W14-05/06` 条目：唯一未决轴=>24 轮长剧域）。
- 素材前提：W03-06 wave-1 语义审查已完成（`W03-06_WAVE1_SEMANTIC_REVIEW_20261101.md`）：16 篇修复后全部可开测，0 阻塞。
- SEALED 池实为 28 篇（STORY_CUSTODY_LEDGER.json lifecycle=SEALED）：wave1 16（E045–E060）+ stage2 8（E022/E023/E025/E028/E029/E030/E035/E036）+ stage3 4（E038/E040/E043/E044）。

## 1. 实验假设与判决标准

### 1.1 假设

- **H-LR2（主）**：v0 的原文窗随故事长度线性增长（LR1 实测 ~220→1600 字符），到 32–40 轮时最老事实将滑出窗口，v0 窗口保持率在 T25+ 段显著低于 r9_slim 的投影簿记，长剧域优势反转。
- **H-LR2-null**：v0 窗继续扩容保持 ≥90% 保持率且九维不劣 → v0 使用域扩至全长度，r 系簿记保留理由进一步收缩至跨会话/作废语义（LR1 终局已预告此后果）。

### 1.2 配对设计：两臂 v0 vs r9_slim（不设 r7 第三臂）

理由：(a) G1 已证 r9_slim≈r7 行为（64/64 零失败、九维 severe 0/0、最差 init −0.097 远超 −0.5 线、prompt −3.8%）；(b) RESEARCH_STATE.json 判据纪律明写"对照需作废证据时才召 r7"；(c) 两臂 144 次已够本域判决，三臂 216 次对未决轴无增量信息。
**条件唤回**：仅当 v0 与 r9_slim 在 T25+ 段同时塌陷（两臂窗口保持率同落 <80%）时，加派 r7 臂小批（1 篇 40 轮×2 序=80 attempts，另立 AUTH 单）判"基座族 vs 簿记层"。

### 1.3 指标

一级（按锚点段分层）：
1. **窗口保持率**：逐轮用 `calls.json` 内实际 system+user 全文（每次调用落盘，见 `logs/longprobe_lr1_live_20261030/_reports/EXAM_candidate/E061/turn_21_E061_T21/calls.json`）机械判定 19 条 GT 消息事实是否仍在窗（LR1 同法，仅判 prompt 文本包含/等义改写需亲读复核）。
2. **九维语义评审**：Goal/Fact/Attrib/Time/Boundary/Memory/Interact/Init/Emotion（0–3 尺，`run_lp_review_generic.py` 产出 LP_LR2_NINEDIM_REVIEW.json + B3 队列自动附）。
3. **severe 计数**（双臂分列，B3 亲读裁定，沿用 ERRATUM-SEVERE-SWAP 修复后脚本）。
4. **事实断言面**：每锚点段的 GT 原子 Capture/Retrieval/Use/Contamination 六段记分（MASTER §3.2 口径）。

二级：prompt/completion usage（API 实数）、p50/p95/max 时延、失败数（全部自动入 DONE 报告，LR1 DONE 行为样例见 `logs/_lr1_run_console.log`）。

### 1.4 晋级/淘汰阈值（预注册，跑前冻结进 PREREGISTRATION 文件）

| 判决 | 条件（全部满足=KEEP >24 域） |
|---|---|
| v0 KEEP >24 域 | ① T25–32 段窗口保持率 ≥90%；② 保护维（goal/boundary/init）最差 ≥−0.5；③ severe(cand) ≤ severe(parent)；④ 九维无任何维 ≤−0.5；⑤ prompt usage 仍 ≤ r9_slim |
| v0 淘汰出 >24 域 | ① T25–32 段窗口保持率 <80%；或 ② 任一保护维 ≤−0.5；或 ③ severe(cand) > severe(parent) 且差 ≥2 位 |
| 不确定带 | 保持率 80–90%、或 severe 打平但九维互有胜负 → 报"保留不确定"，v0 使用域维持 ≤24 轮不动；不补跑到赢 |
| r9_slim 侧 | r9_slim 为默认冠军，本批只作对照臂，不存在淘汰问题；若 r9_slim 自身 T33+ 塌陷属新故障族，登记 KNOWN_FAILURES 不当场改判 |

机械层任一臂 veto 率 ≥3× 另一臂（n≥72）→ 只记账不判语义（LR1/F3 已证 veto=基座随机）。

## 2. 素材方案（LR2 素材 = W12-01 素材批）

### 2.1 构造路线：SEALED 12 轮故事原位续写扩展（非跨故事拼接）

- **否决跨故事拼接**：wave1 各篇 persona 独立（E045/E048/E050/E060 的 her/me 姓名与边界互不相同，实测 episode.json persona 字段），硬拼违反故事连续性与 §4 题库纪律。
- **采用原位续写**：选中篇目保持原 T1–T12 逐字不动（hash 前缀可验），由隔离生成会话按统一分段规格续写至 32/40 轮，落盘为新故事条目（目录 `benchmark/stories_v2/wave1_ext/`，episode_id=LRE0xx，账本新注册、原篇 SEALED 状态不动）。
- **选中篇目**（全部来自 W03-06 审查干净/已修复集，题材适配多日跨度）：
  - **LRE060**（源 E060 搬家三次改期，→40 轮）：连环改期+硬死线+危机救援弧，审查评"本批质量最高之一"。
  - **LRE048**（源 E048 拼团券差一百二，→32 轮）：费用链全闭环+归属裁定。
  - 备选第 3 篇 LRE054（源 E054，已修复卡面算术，→32 轮）：仅当前两篇无塌陷信号需加密度时启用（第 2 决策点）。

### 2.2 分段规格（W12 六段 × 轮数锚点矩阵）

六段为 MASTER §3.2 知识六段（Capture/Fidelity/Update/Retrieval/Use/Contamination）在各轮数锚点的测量矩阵；锚点=6/12/18/24/32/40（32 轮篇缺 40）。

| 段 | 轮 | 素材要求（隔离会话按此写） |
|---|---|---|
| A | T1–T6 | 早期事实密集建立：数字/日期/承诺/偏好（Capture 原子清单登记进 reference） |
| B | T7–T12 | **原篇 12 轮逐字保留** |
| C | T13–T18 | 跨日过渡聊天（W12-03：相对时间"下周三/改天"、周期安排、临时变更） |
| D | T19–T24 | 更正/取消/条件接受/多人归属交错（W12-04；Update 压力位） |
| E | T25–T32 | 出窗召回探针：质询 A 段早期事实（原文此时已必然不在 24 轮内），W12-01"后续问题来源出窗" |
| F | T33–T40（仅 40 轮篇） | 旧事复活陷阱+污染探针（旧目标/已更正数字不得复活）+自然收尾 |

每段配额：每段 ≥3 个可评分 GT 原子、≥1 个出窗探针位（E/F 段）；reference.json/timeline.json/audit.md/exposure.json 按 wave1 同 schema（validate_story_v2 校验器接受任意 --turns 整数，`tools/validate_story_v2.py` L96–98 只查长度一致、L326–334 参数化）。

### 2.3 素材与候选隔离纪律

- 续写由独立隔离会话执行（W03-06 wave-1 同款委派形态，见 STORY_CUSTODY_LEDGER.json events 的 generation_isolation_ack 条目）：作者只读白名单（原篇 episode/reference/timeline、本设计 §2.2、GEN_CHECKLIST），**禁读 logs/、candidates/、任何 VERDICT/REVIEW**。
- 候选运行时经 `arena/isolation/trusted_controller.py`：`deny_files([episode_path])` 已内建（L594），reference/timeline/cards 不进候选上下文；候选可见输入=真实 HER/ME+IDEA+intent+时间戳。
- 续写完成后先跑 `validate_story_v2 --stage wave1_ext --turns 32/40`，刷新 manifest/exposure/账本 hash、`story_custody.py verify`，然后才允许导出执行。

## 3. 批结构与记账

### 3.1 attempt 估算（管理帽 16384 不变；本批批级 cap 下述）

| 项 | 数 | 说明 |
|---|---|---|
| LRE060 40 轮 ×2 臂 | 80 | 主判据篇（T25+ 保持率+F 段污染探针） |
| LRE048 32 轮 ×2 臂 | 64 | 复证篇（不同域） |
| 交换序补测（swap） | 24 | 12 锚点位×2，用现成 `tools/longprobe_swap_supplement.py`（LR1 swap 4/4 同法） |
| **live 生成合计** | **≤168** | AUTH cap=192（+14% 缓冲，失败零重试零补跑） |
| 九维评审（另册） | 72 位 | 40+32=72 配对位全审；judge 调用走独立 AUTH-LR2-REVIEW cap=120 |
| 条件唤回 r7 臂（仅触发时） | 80 | 另立 AUTH 单，不预占 |

### 3.2 AUTH scope 登记（仿 `logs/auth_budget/AUTH-LONGRANGE-LR1-20261031-01.jsonl` 首行格式）

```
{"authorization_id":"AUTH-LR2-LONGRANGE-20261102-01","kind":"authorization_scope",
 "max_attempts":192,
 "scope":{"automatic_judge":false,
   "batch_ids":{"LR2":"BATCH-LONGRANGE-LR2-LRE060LRE048-20261102"},
   "cards":"benchmark/stories_v2/wave1_ext/GRADING_CARDS_W12.json",
   "episodes":["LRE060","LRE048"],
   "fail_fast":true,"no_makeup":true,"no_retry":true,
   "preregistration":"refs/experiment_protocol_20261023/long_probe_20261030/LONGPROBE_LR2_PREREGISTRATION.json",
   "purpose":"W10-04余项+W12-05: >24轮域 v0_minimal_events vs r9_slim 两臂配对; 判据=本设计§1.4; 素材=wave1_ext隔离续写",
   "route":"two_arm_batch_168","total_attempts":168},
 "ts":"<登记时刻>"}
```
继承 `AUTH-GLOBAL-CONTINUOUS-20261030-01` 常设授权；attempt_started/completed 事件由 AuthorizationBudget 自动续写同 jsonl（LR1 台账即此形态）。评审另册 AUTH-LR2-REVIEW-20261102-01。

### 3.3 落盘路径

- 计划/授权/预注册：`refs/experiment_protocol_20261023/long_probe_20261030/LONGPROBE_LR2.INTERLEAVE_PLAN.json`、`AUTHORIZATION_LR2.json`、`LONGPROBE_LR2_PREREGISTRATION.json`（仿 LR1 同名三件套）。
- 运行输出：`logs/longprobe_lr2_live_<date>/`（parent_LRE060 / candidate_LRE060 / … + `_reports/EXAM_*` + 每轮 calls.json）。
- 评审产物：`LP_LR2_NINEDIM_REVIEW.json` + `LP_LR2_B3_QUEUE.json`（`run_lp_review_generic.py` 自动附 B3，wired 于 W16-05）。
- 判决：`LR2_VERDICT_<date>.md` 写回本目录；W12-06 长期能力判决引用之。

### 3.4 失败处理

`fail_fast_on_transmission_error=true`、no_retry/no_makeup（计划字段 LR1 已用同值）；网关 /health 不通 G6 零派发；单臂传输失败即整批停、落 DONE 报告 stopped 字段；缺件不补跑，如实记失败。禁止复活旧单。

## 4. runner 命令序列（全部为现有脚本真实参数；胶水脚本单列 §4.3）

### 4.1 素材批（隔离会话内执行）

1. 续写产出 `benchmark/stories_v2/wave1_ext/LRE060|LRE048/{episode,reference,timeline}.json + audit.md + exposure.json`。
2. `D:\anaconda\python.exe tools\validate_story_v2.py --stage wave1_ext --turns 40 --count 1`（LRE048 用 --turns 32）。
3. `python tools\story_custody.py verify`（注册 LRE0xx、刷新 hash）。

### 4.2 live 批（主会话执行）

1. **导出胶水**（§4.3 G-1）→ 生成 `benchmark/episodes/LRE060.json`、`LRE048.json` 与 `benchmark/stories_v2/wave1_ext/GRADING_CARDS_W12.json`。
2. **建计划**：写 batch-config JSON（仿 `LONGPROBE.BATCH_CONFIG.json`：batch_id/arm_map={parent:action_policy_r9_slim,candidate:v0_minimal_events}/episode_order=[{episode:LRE060,arm_order:parent_first},{episode:LRE048,arm_order:candidate_first}]/cards_path/recall_capable:false），然后
   `python tools\exam_interleave_plan.py --emit-plan --batch-config refs\...\LR2.BATCH_CONFIG.json --out refs\...\LONGPROBE_LR2.INTERLEAVE_PLAN.json`
   （轮位数由题面自动推导，`exam_interleave_plan.py` L113–133 不写死 8）。
3. **授权**：写 `AUTHORIZATION_LR2.json`（含 frozen_bindings.plan_sha256、arm identity sha256；r9_slim=2099c12f…、v0=eb35df9a…，取自 `logs/_lr1_run_console.log` DONE 行）+ §3.2 jsonl。
4. **capture 预演**：`D:\anaconda\python.exe tools\run_max288_batch.py --plan ...LONGPROBE_LR2.INTERLEAVE_PLAN.json --mode capture --out logs\longprobe_lr2_capture_<date>`（零网络，验 G1–G7/数量/顺序/白名单）。
5. **live**：同命令 `--mode live --authorization refs\...\AUTHORIZATION_LR2.json --out logs\longprobe_lr2_live_<date>`。G1–G7 守卫、calls.json、DONE 报告自动（LR1 实跑同入口）。
6. **机械分**：`python tools\score_longprobe.py`（LR1 机械层同工具）。
7. **九维评审**：`python tools\run_lp_review_generic.py --batch-dir logs\longprobe_lr2_live_<date> --stories LRE060,LRE048 --auth <REVIEW auth 路径> --tag LR2 --cand-name v0_minimal_events`。
8. **窗口保持率**：§4.3 G-3 对 calls.json 确定性测量 + 亲读复核等义改写。
9. 判决文档 + 账本 expose 记账 + STORY_CUSTODY_LEDGER 消费登记。

### 4.3 缺失胶水（只列伪代码，不实际创建）

- **G-1 `tools/export_lr2_episodes.py`**：读 `stories_v2/wave1_ext/<id>/episode.json` → 改写 episode_id 为 LRE0xx → 写 `benchmark/episodes/LRE0xx.json`；读 reference.json turns → 逐 case 组装 {case_id, current_action, acceptable_actions, applicable_dimensions, required_memory_atoms, unknowns, forbidden_factual_claims, may_decline, intent} → 写 GRADING_CARDS_W12.json（schema 对齐 `benchmark/stories_v2/stage4/GRADING_CARDS_STAGE4.json`：{"kind":…,"cards":[…]}）。
- **G-2 `tools/run_lr2_segmented.py`（可选，服务 W12-02）**：`run_max288_batch.py` 无 --resume 暴露（已 grep 证实无 resume 参数），而 `arena/isolation/trusted_controller.py` L604–606 支持 checkpoint resume。伪代码：40 轮篇两臂同协议分段——进程1 `IsolatedRun(...).begin_run(ep)` 跑 T1–T24 → finish 前保持 checkpoint.json → 进程2 以 `resume=True` 重启跑 T25–T40；两臂分段点一致保证执行对称；产物与单进程批分目录，语义评审时标注 W12-02 证据位。若时间紧可降级：本批全单进程，W12-02 另立小批。
- **G-3 `tools/measure_lr2_window_keep.py`**：遍历 `logs/longprobe_lr2_live_*/_reports/EXAM_*/…/calls.json`，对每轮 prompt 串做 GT 原子（reference required_memory_atoms + 数字/日期清单）精确/归一化匹配，输出 {case_id: in_window} → 按锚点段汇总保持率；等义改写漏检位导出清单交亲读。

## 5. 风险与回退

| 风险 | 处置 |
|---|---|
| 续写质量硬伤（如 E054 式算术错） | 隔离会话产出后必过 validate_story_v2 + 主会话语义审查（W03-03 同款）双关；审查在首次暴露前=封板前迭代合法（W03-06 wave-1 先例） |
| 续写作者污染 | 白名单隔离 + 账本 generation_isolation_ack 登记；作者未读任何候选行为证据 |
| v0 预期失败模式：T25+ 窗保持率线性下滑、E/F 段编造补漏（无据确定化族） | 该族为基座属性（FOURTH_ROUND 跨批结论），双臂同记不计臂间差；保持率<80% 即按 §1.4 淘汰出 >24 域，不调 v0 参数救场 |
| r9_slim 投影在长剧过度增长致时延爆炸 | 二级指标如实记 p95；不构成淘汰线，登记维护负担 |
| 批中止 | 继承 §8：预算帽触顶/网关异常/连续传输失败/素材校验不过 → 停批冻结现场（DONE stopped 字段），局部停批不冻结其它线 |
| 结果小 n | 72 位嵌套于 2 故事，按故事分层报告；不套 N=72 独立样本口径 |

## 6. 与 W13 泛化线的关系（holdout 保护）

以下故事**禁入** LR2/W12 素材批（W13-01_HOLDOUT_BATCH1_20261031.json）：
- holdout batch1：E038、E040、E043、E044、E022、E028、E029、E035；
- reserved batch2：E023、E025、E030、E036；
- 已暴露不可作未见材料：E037/E039/E041/E042（G1 消费）、E061–E068（stage4 全 EXPOSED）、E021/24/26/27/31–34。
- wave-2 新增 48 篇全数留 W13-02 反事实线。LR2 续写只动 E048/E060（及备选 E054），消费后按账本 expose；W13 泛化批继续用未动 holdout，独立性不受损。

## 7. UNKNOWN 与确认命令

1. `exam_interleave_plan.py --emit-plan --batch-config` 对新 cards_path/stage 是否零障碍：确认 `python tools\exam_interleave_plan.py --emit-plan --batch-config <LR2.BATCH_CONFIG.json> --out <plan> && python tools\run_max288_batch.py --plan <plan> --mode precheck`。
2. validate_story_v2 对 episode_id=LRE0xx 与目录名一致性校验细节：读 `tools/validate_story_v2.py` L79–100。
3. 评审 auth jsonl 首行精确 schema：读 `logs/auth_budget/AUTH-LONGRANGE-LR1-REVIEW-20261031-01.jsonl`。
4. G-2 分段执行与 execute_plan 的兼容性：读 `tools/run_max288_batch.py` dispatch 段 + `arena/isolation/trusted_controller.py` L574–620。
5. wave1 各篇 exposure.frozen_at 现值（影响 --allow-frozen-at）：`python -c "import json;print(json.load(open(r'benchmark/stories_v2/wave1/E048/exposure.json',encoding='utf-8')))"`。

## 8. 执行顺序总览

隔离素材批（§4.1+§2.3）→ 语义审查/修复/重校验 → 导出+计划+授权+capture 预演（§4.2.1–4）→ live 168（§4.2.5）→ 机械分+窗口保持率+九维评审+B3 亲读 → LR2_VERDICT（§1.4 阈值）→ W12-06 冻结长期能力判决；条件唤回 r7 臂仅在两臂同塌陷时另批触发。
