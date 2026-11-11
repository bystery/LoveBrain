<!-- historical worker document, sanitized; not current -->
# LoveBrain 完整 Harness 交接文档（2026-10-28 22:22）

> 这是**交接快照**，不是新总计划，不替代 `WORKER_CURRENT_RUNBOOK.json` 这个当前入口。
> 事实冲突时，优先级：原始账本/答卷/`reply_raw.txt` > 绑定与评分证据 > 本文件 > 早于 2026-10-28 20:00 的旧总结。
> 本文件没有新增模型调用，没有改写旧答卷、评分器、授权或预算账本。

---

## 0. 60 秒摘要

- 项目根：`<arena-root>`；参考原版：`<LoveBrain-repo>`；产品接入暂停。
- 截止：`2026-10-29`（具体时刻未给）；内部目标：10-28 22:00 已有实物与初判，10-29 做必要复验与最终判决。
- 今天七批真实对照已完成：**260/260 次物理请求，0 传输失败**；130 个位点，118 个可配对，12 个因合法不回/卡面矛盾不评分。
- 最强正效应在**练过的题**：原版 vs r1 为 32:4:12（S1）、9:0:1（S2）；原版 vs r2 为 7:0:3（S2R）。
- 但**增量与泛化不成立**：r1 对直接父本 v6 只有 2:3:5（S3）；r2 对新材料只有 5:3:5（D1）；最新 r3 对 r2 为 0:1:20（R3A），对原版 1:0:5（R3B）。
- FIX-9 把过度沉默从 2 格降到 0，但 r3 的合法不回使用为 **0/30**，并保留 1 处协议失败（F2，`directions` 给 5 条）。
- 判决：**保留“不回表达”修复；候选整体“改进中，不签收全面超越”；泛化主张不成立。**
- 今天两处继承缺口已离线修好：判词绑定回填 **130/130 位点 VERIFIED_ALL**；输出修复器改为继承旧归一器，260 份真实答卷 **0 回归**。
- **没有任何数据被删除或重置**：260 份 live 答卷、260 份报告副本、7 个授权账本、旧成绩表与判词备份都在。

---

## 1. 当前整体现状

### 1.1 真实派发总账（可重算）

| 批次 | 内容 | 请求 | 两臂身份 | 传输失败 | 原始协议失败 |
|---|---|---|---:|---|---|
| S1 | 4 连续故事×12 位×2 臂 | 96 | reference_product_v1 `501bc426…` / action_policy_r1 `6e450c8f…` | 0 | 原版 2/48、候选 3/48 |
| S2 | 12 反馈锚点×2 臂 | 24 | 同上 | 0 | 原版 0/12、候选 3/12 |
| S2R | 同 12 锚点×2 臂（修复后复验） | 24 | 原版 / action_policy_r2 `352c0c06…`（新身份） | 0 | 原版 0/12、候选 1/12（+2 格合法静默） |
| S3 | 父子归因 v6→r1，同 12 锚点 | 24 | v6_integrated `ac2672ff…` / r1 | 0 | 父本 2/12、候选 3/12 |
| D1 | 2 个新材料故事×8 位×2 臂 | 32 | 原版 / r2 | 0 | 0/16 两臂 |
| R3A | D01/D02 全 16 位 + D03 6 位 + AK09/AK12，2 臂 | 48 | r2 `352c0c06…` / r3 `e400275f…`（新身份） | 0 | 父 0/24、候选 1/24（F2） |
| R3B | D03 6 位×2 臂（原版 vs r3） | 12 | reference_product_v1 / r3 | 0 | 0/6 两臂 |
| **合计** | — | **260** | — | **0** | **15/260 格** |

账本：`logs/auth_budget/AUTH-P08-{S1,S2,S2R,S3,D1,R3A,R3B}-20261028-01.jsonl`。一物理请求一 attempt，事前占额，失败也记账；无免费重试、无复活旧单。

### 1.2 质量主结果

| 批次 | 对照 | 可配对 n | 胜/负/平 | 严重错 |
|---|---|---:|---|---:|
| S1 | 原版 vs r1 | 48 | **32 / 4 / 12** | 0 |
| S2 | 原版 vs r1（反馈锚点） | 10 | **9 / 0 / 1** | 1 |
| S2R | 原版 vs r2（新身份） | 10 | **7 / 0 / 3** | 0 |
| S3 | v6 vs r1（父子归因） | 10 | **2 / 3 / 5** | 3 |
| D1 | 原版 vs r2（新材料） | 13 | **5 / 3 / 5** | 3（更正后净 2） |
| R3A | r2 vs r3（FIX-9） | 21 | **0 / 1 / 20** | 0 |
| R3B | 原版 vs r3（D03） | 6 | **1 / 0 / 5** | 0 |

读法：**对原版的五批全部胜多负少，没有一批整体更差**；唯一负比较是 S3（r1 对直接父本 v6）；R3A 是最新候选对上一版打平。跨批基线不同，不能把 56/11/51 当成一次比较。严重错合计：**原始登记 7 处（S2 1、S3 3、D1 3），按 `JUDGMENT_CORRECTIONS_20261028.json` 更正后净 6 处**（D02_T06 改判题卡矛盾；旧记录保留不删）。

九维要点（0—3，只打该位点卡的适用维度）：

- S1（r1）：`memory_use` 2.419→2.871、`initiative` 2.333→2.778、`emotion_relation` 2.200→2.567、`interaction` 2.605→2.930；退化：`boundary` 2.812→2.750、`time_state` 2.875→2.812。
- S2R（r2）：`initiative` 2.0→3.0、`interaction` 2.222→3.0、`emotion_relation` 2.4→3.0、`memory_use` 2.5→3.0；`time_state` 2.5→2.833。
- D1（r2）：`interaction` 2.0→3.0、`emotion_relation` 2.5→3.0、`memory_use` 2.5→2.875；`initiative` 2.9→2.8。
- R3A（r3）：九维几乎全 3.0；`time_state` 3.0→2.923；无正向增量。
- R3B（r3）：`factuality` 2.5→3.0、`boundary` 2.8→3.0；其余持平。

### 1.3 费用与等待（候选相对父臂）

| 批次 | 总 token Δ | p95 时延 Δ | 备注 |
|---|---:|---:|---|
| S1 | −28.5% | −22.1% | prompt −30.8%，completion +31.7% |
| S2 | −28.3% | +78.8% | 总 token 降，尾延迟明显升 |
| S2R | −16.1% | +8.6% | 同上，幅度小 |
| S3 | +4.6% | −39.6% | 归因批反而多耗 token |
| D1 | −10.5% | −17.4% | 新材料上费用与尾延迟都降 |
| R3A | +4.5% | −18.1% | r3 比 r2 略贵，尾延迟降 |
| R3B | −9.4% | −29.5% | r3 比原版省 token、尾延迟降 |

省的是 prompt，不是输出；多数候选批 completion 长 25%–42%（R3A 仅 +2.5%，S3 反而 −7.3%）。**不能把“省 token”单独当质量或效率胜利。**

### 1.4 当前候选谱系

`v6_goal_context ← v6_integrated ← action_policy_r1（FIX-1..5）← action_policy_r2（FIX-6/7/8）← action_policy_r3（FIX-9）`

- `action_policy_r1`：五项提示/装配修订；离线合同 10/10；真实效果在 S1/S2/S3 有数据。
- `action_policy_r2`：三项交付/沉默修复；S2R 7:0:3，D1 5:3:5；但新材料上 3 格过度沉默（更正后 2 格）。
- `action_policy_r3`：FIX-9 收窄“可以不回”的范围；把 D01_T08/D02_T08 的过度沉默修到 0，但未净增质量；合法不回 0/30；1 处 F2。

### 1.5 当前数据与证据完整性

- live 原始答卷：`find logs -path 'logs/p08_*_live_20261028/*/out/turn_*/reply_raw.txt' | wc -l` = **260**（另有 `_reports/EXAM_*` 报告副本 260 份；合计 520 个文件，不是 520 个样本）。
- 账本：7 个，**260 started / 260 completed / 0 failed**。
- 七批成绩：`refs/experiment_protocol_20261023/s1_action_live_20261028/*_SCORECARD.{json,md}`。
- 判词绑定：`*_SCORE_BINDING.json`；七批 **130 位点 / 260 格 VERIFIED_ALL**。
- 今天两处离线修复的证据：
  - `refs/experiment_protocol_20261023/deadline_quality_20261028/BINDING_BACKFILL_20261028.json`
  - `refs/experiment_protocol_20261023/deadline_quality_20261028/SCORE_BINDING_GUARD_20261028.json`
  - `refs/experiment_protocol_20261023/deadline_quality_20261028/OUTPUT_REPAIR_PARITY_20261028.json`
- 备份：`<local-backup-scores-20261028-path>`（7 个判词模块）、`.../backup_scorecards_20261028/`（14 个旧成绩表）。
- 当前判决：`refs/experiment_protocol_20261023/deadline_quality_20261028/FINAL_VERDICT_20261028.md`（§0–§11）。

### 1.6 五问现状（用户交付口径）

| 问题 | 现在能回答的 | 还缺什么 |
|---|---|---|
| 效果 | 练过题有正效应（S1 32:4、S2R 7:0）；新材料缩水（D1 5:3）；r3 无净增（0:1:20） | 独立裁判；更多新材料；逐项消融 |
| 可靠 | 260/260、0 传输失败；P 失败 15/260；F2 1/24 | P/C 全链路、产品消费者、失败重试边界 |
| 等待 | p95 混合：S1 −22.1%、S2 +78.8%、D1 −17.4%、R3A −18.1% | 分段等待、并发/负载、尾延迟原因 |
| 费用 | 多批总 token 下降（S1 −28.5%、S2R −16.1%、D1 −10.5%）；completion 变长 | 单价未知，金额恒 null；不能只报 token |
| 接入 | 产品暂停；C 层由评分器+留损近似 | `classify_reply` 未接线；W 列仍 unknown |

---

## 2. 什么是已经做过的，什么没做过

### 2.1 已完成，有实物

| 项 | 结果 | 证据 |
|---|---|---|
| 旧 P06 | 192 次生成保留，正式判词 154/192；没有重跑 | `morning_resume_v4_20261028/MORNING_REVIEW.md` |
| Stage1 | 8 故事、64 位、0 错误 | `benchmark/stories_v2/stage1/`、`VALIDATION_*.json` |
| Stage2 | 16 故事×12 位=192 位，机械验证 16/16、0 错误 | `benchmark/stories_v2/stage2/` |
| D1 新材料 | 2 故事×8 位；32 次真实生成；13 个可配对 | `D1_SCORECARD.json` |
| D03 新材料 | 6 位；R3A/R3B 两批复用；作者侧暴露已标 | `benchmark/stories_v2/stageD/D03/`、`exposure.json` |
| P07 输入修复 | 意图与已发 ME 回放独立复跑 26/26 | `LOGIC_CHECKS.json`、`test_p07_env_v2` |
| 27/18 漏记 | 旧恢复账本更正并 CLOSED；27 次物理消耗按实计 | `AUTHORIZATION_INCIDENT_CORRECTION_20261022.md` |
| 七批真实对照 | 260/260，0 传输失败；公平性逐字段核过 | 上文账本与 `*_INPUT_FAIRNESS_*.json` |
| 九维判词 | 118 配对位点；首选与非空备选逐位读；严重错/更正分列 | 七批 `*_SCORECARD.json`、`JUDGMENT_CORRECTIONS_20261028.json` |
| FIX-9 | 过度沉默 2→0；r3 在 D01_T08/D02_T08 复现修复 | `PROBE_R3_FIX9_20261028.json`、`R3_BEFORE_AFTER_20261028.json` |
| 继承修复 | 判词绑定接回；输出修复器改为继承旧归一器 | `BINDING_BACKFILL_20261028.json`、`OUTPUT_REPAIR_PARITY_20261028.json` |
| 最终判决 | 明确“改进中，不签收全面超越；泛化不成立” | `FINAL_VERDICT_20261028.md` |

### 2.2 W01–W12：没有全部完成

| 工作包 | 状态 | 缺口/说明 |
|---|---|---|
| W01 调用漏记、评审绑定、消费丢内容 | **部分** | 漏记已更正、绑定已接回、输出全文保全已继承；P07 旧消费者仍只认 `analysis.action_taken`，顶层 `action` 未接线（产品暂停） |
| W02 意图、真实 ME、闭环 pilot | **部分** | 输入 26/26；pilot 6 条完整、7 条环境失败、仅 3 格配对；未收口 |
| W03 行动、钩子、调情、深聊、反馈 | **部分** | 行动策略 r1/r2/r3 已实现并真实对照；钩子/调情/深聊未单列实测 |
| W04 证据知识、归属、纠正、状态 | **部分** | v6 已有字段与纠正链；W04 评估判定“不新建分支”；语义主体错换仍未闭合 |
| W05 上下文选择、简化、污染抑制 | **未做** | 只有 S2 的“考点笔记”污染单例 |
| W06 新故事、可判定参考、开发/验证分离 | **部分** | Stage1/2、D1、D03 已有；语义参考未独立审完；开发/验证分离不完整 |
| W07 新候选共同面板真实对照 | **完成（本轮范围）** | 7 批 260 请求、118 配对、逐维成绩与判决 |
| W08 长历史、跨日、纠正、重启 | **未做** | 只有 6–8 轮连续短史，无重启/跨日恢复实测 |
| W09 反馈分支、正向互动 | **部分** | S2/S2R 12 锚点完成；慢模拟器 W09 双臂 0 配对（原版 8、v6 0），已停止补齐 |
| W10 组件消融、删除无收益资产 | **未做** | 判决 §7 明列缺逐项消融；r2 三项修复无法归因到单项 |
| W11 等待、费用、可靠性优化 | **部分** | 有真实 usage、p95、0 传输失败；只测量，未做优化实验 |
| W12 研究级结论、保留/淘汰、接续 | **部分** | 截止判决完成；288 小时总研究结论未完成 |

### 2.3 截止五项

| 项 | 状态 | 说明 |
|---|---|---|
| 真实比较 | **完成** | 七批、260 请求、118 配对；同题/同模型/同条件 |
| 完整质量审查 | **完成，但为自评** | 九维、首选与全部非空备选由实施 worker 亲读；**没有独立裁判** |
| 修复后实测 | **完成** | R3A/R3B 新身份；FIX-9 真实复验 |
| 新材料复验 | **部分** | D1 13 位、D03 6 位；n 小，作者侧暴露；不能宣称广泛泛化 |
| 最终判决 | **完成** | 结论是否定的：不签收“全面超过原版” |

---

## 3. 踩过的坑（按类别）

> 每条格式：**现象 → 原因 → 规则**。这些坑已经真实发生过，下一窗口不要再踩。

### 3.1 身份 / 冻结 / 执行器

1. **候选身份无故漂移** → `_stage_candidate_closure` 用 `copytree` 拷整棵候选树，**`__pycache__/*.pyc` 也计入哈希**；任何一次 import 改变 pyc 头里的源 mtime，身份就变 → 下一轮把 `__pycache__` 排除并作为“新身份代”统一重算，**不要中途偷改哈希算法**，否则已冻结的计划不可比。
2. **转发型候选启动报 AttributeError** → r2/r3 没有顶层 `rc`，而计划工具在 recall 段直接取 `spec.rc.utf16_len` → 用 `_spec_rc`：有顶层 `rc` 用它，否则退到 `base.rc`；它只服务 recall 计量。
3. **更深候选无法开跑** → staging 依赖深度上限 4，r2 已超 4 层 → 放宽到 12 时先做逐文件哈希等价证明；cap4/cap12 对既有候选的拷贝集合必须一致，新增文件只能来自候选自身目录。
4. **计划哈希漂移** → 同一条链在 D1 与 R3A 之间 `39b49a4c…`→`c27d9600…` → 任何执行器改动都要有等价性测试（`test_staging_depth_equivalence.py`、`test_rc_resolver_equivalence.py`）；没有等价证明就不要跨批比较。
5. **`emit-plan` capture 目录不能放在 live 目录** → 已有 `turn_*` 证据时它会拒绝覆盖 → capture 产物放 `logs/p08_*_capture_*`，plan 留在 live 目录；这是既有布局，不要“图省事”合并。
6. **`IsolatedRun` 按 (arm, episode) 隔离** → attempt id 必须从真实控制账本读，不能自己编；两臂共享输入但状态隔离。

### 3.2 预算 / 派发

7. **同一预留内多次 generate 不另计费** → 物理请求与 attempt 不是一回事 → **一物理请求一 attempt，事前占额；失败也消耗预算**；没有“免费重试”。
8. **重试到赢** → 旧 W09 同格从 r1 补到 r5，还是 timeout → 停止“补到成功”；环境失败记录为环境失败，不占语义胜负；不在同格无限 repeat。
9. **为元数据重跑真实调用** → G11 两轮各 6 次，输入完全相同，仅授权文书字段位置变化 → **报告元数据与执行身份分离**；修报告/表头/绑定一律离线，不重跑模型。
10. **覆盖完整轨迹** → W09 的完整 I1 positive v6 r1 被重派覆盖，原 ctx 已损失 → 只用**追加式目录 + 显式单元列表**；不要用 `repeat=1..N` 加人工 skip；任何覆盖事故不可假装恢复。
11. **慢模拟器锁住主线** → W03 的验证被排在 W09 三相之后 → 固定连续故事比较走主轨，模拟器单独 epoch，不阻塞不依赖它的实验。
12. **模型策略** → `hy4-preview` 禁用；Gemini 不进关键路径；同一 alias 跨角色合计最多 3 并发，从 1 起爬；不要用并发上限当吞吐承诺。
13. **公平性审计传错目录** → `audit_pair_input_fairness.py --out .../batch` 会报“缺 turn_XX 的 calls.json” → 它要的是 **live 根目录**；capture 审计单独传 capture 根。
14. **跳过 precheck/capture 直接 live** → `run_max288_batch.py` 的 G1–G7 是派发前守卫；precheck/capture/live 三模式不要混；冻结闭包、题卡、身份哈希缺一不可。

### 3.3 评分 / 判词

15. **把 P/C/S 混成一个“好坏”** → 协议失败但正文完好、合法不回但语义正确，都会被误判 → 五层分列：T 传输、P 原始协议、C 消费后可交付、S 语义、W 用户愿发（恒 unknown）。
16. **空对象/不回/动作冲突** → 评分器认顶层 `action:"no_reply"` + 空正文；未授权 no_reply 是 veto；有正文 + decline_reason 是 action_conflict；`directions` 不是 4 条就是 `json_schema_broken` → 不要用“正文看着不错”掩盖协议失败，也不要用协议失败折算语义胜负。
17. **卡面自相矛盾** → D02_T06 的 current_action 写“可不回”，private_guidance 写“不能玩消失”；先判候选过度沉默，后改判题卡矛盾 → 判分前先逐字读 current_action / private_guidance / acceptable / required_this_turn；同一证据两读时不得只取对候选不利的一读；更正写进 `JUDGMENT_CORRECTIONS_*`，不删旧判词。
18. **把“可接受”判成“不该”** → D03_T02 的 acceptable#1 明写“谈完需要拿主意随时喊”，初判却把“拍给我看一眼”当“换个方式贴上去” → 扣分前先对齐 acceptable；r2 在同卡面下也用了同一形式，口径要一致。
19. **把静默当默认好** → r2 在 D01_T08/D02_T08 该回不回；r3 修好后合法不回又变成 0/30 → 沉默只在卡面**明确允许**且她**没有新成果/新请求**时成立；不要把“少说”当美德。
20. **自评冒充独立** → 九维判词是实施 worker 亲读手填，没有第三方裁判；报告里不能写“独立评审” → 下一窗口先做独立裁判校准（见计划 P1）；校准前所有分数标“worker 初评”。
21. **判词按 case_id 静默套用** → 旧汇总器只按 case_id 取 `SCORES`，换答案+清零散列仍保留旧 12 分 → 现在必须带 `--binding`；绑定校验 run_id / case_input_sha256 / raw_reply_sha256 / primary_sha256 / 磁盘 `reply_raw.txt` / 两臂身份；不符的位点不评、rc=3。
22. **表头 `批=None`、两臂身份 `{}`** → 旧代码拿 harness 名拼 `EXAM_<harness>/EXAM_INDEX.json`，实际目录按 role 命名 → 现在按 `EXAM_parent` / `EXAM_candidate` 读；不要再按 harness 名找目录。
23. **“48 位点全部亲读”模板** → 12 位批也复用了 48 的硬编码 → 现在按实际判定位点数动态生成；任何报告模板都要带分母。
24. **严重错原始登记 vs 更正口径** → D1 原始 `SEVERE_ERRORS` 仍列 3 条（含已改判的 D02_T06），净严重错 2 → 汇总时看 `JUDGMENT_CORRECTIONS_*`，不要直接加总旧列表，也不要把旧记录删掉。
25. **跨批加总胜负** → 七批父臂不同（原版/v6/r2），56/11/51 不是一次比较 → 只报每批配对，不把跨批总数当总胜负。
26. **12 个未配对待遇** → 合法不回/卡面矛盾不进胜负 → 单独列“未配对”，不能算胜、不能算负、不能填成通过。
27. **单样本泛化** → D03_T01 在不同批次间翻转；D1 只有 13 个可审位点 → 不宣布“泛化成立”；新材料必须另立 batch/identity，旧成绩不套用。
28. **D03 作者暴露** → D03 由实施 worker 定向创作，`exposure.json` 已标 `AI_worker_targeted_retest` → 不能写成“独立人类封存”或“盲化新题”；只能叫“定向复验材料”。

### 3.4 输出修复

29. **另写一套归一器，丢掉旧保全** → 新 `output_repair.py` 曾把冲突文本截到 80 字、额外方向截到 120 字，而旧 `normalize_reply_contract.py` 保留全文 → 现在直接 import 旧器（I1–I4），只保留 E1（去零宽字符的键名归一）和 E2（提前闭合 `}` 重拼）；新增功能必须标“扩展”，不能并行重写旧职能。
30. **消费侧替候选补 action** → R4（空正文+全 null 方向）只能登记 `no_reply_expression_missing`，不能由修复器补 `action` → “不回”是模型决策，消费侧代补等于替候选说话。
31. **把 13 份不可发当成修复失败** → 它们全是显式 `no_reply`/空正文（合法静默或无话可说）→ 合法静默与格式损坏要分列；`output_repair` 260 份对账：旧可发 246、新可发 247、0 回归。
32. **把 520 个 `reply_raw.txt` 当 520 个样本** → `_reports/EXAM_*` 是副本 → 只数 `*/out/turn_*/reply_raw.txt`（260）；报告副本用于审计，不是新样本。

### 3.5 Windows / 工具

33. **控制台 GBK 打不出中文** → `print` 中文报 `UnicodeEncodeError` 或乱码 → 用 `PYTHONIOENCODING=utf-8`；复杂内容先写 UTF-8 文件再 `Read`，不要在命令行里硬打大段中文。
34. **git-bash heredoc 吞反斜杠** → Python 字符串里的 `\n`、Windows 路径被 shell 改写 → 用临时 `.py` 文件执行，或全部用正斜杠/原始字符串；不要在 heredoc 里塞复杂转义。
35. **`split("_")` 拆绝对路径** → `<arena-root>/logs/p08_r3a_live...` 被拆错，card 查不到，AK12 被误判过度沉默 → 用 `os.path.basename` 再拆。
36. **跨盘 `os.path.relpath` 抛 ValueError** → 临时目录在 C:、仓库在 D: → 用 `rel_any`（失败退回绝对路径）；`bind_answers.rel` 也要保留 try/except。
37. **`find` 重复计数** → 报告副本让 260 变 520 → 统计样本只认主 run 目录；任何“总数”先问“这是不是副本”。
38. **`--binding` 现在是硬门槛** → 旧的审计 `probe_regressions.py` 按旧接口调用，会被拒跑 → 这是有意保留的“修复前行为”证据；不要为了让它跑通而关掉绑定。
39. **`BINDING_DIGEST` 是判词的一部分** → 七个 `*_WORKER_SCORES.py` 顶部各有一行；改判词必须同步更新绑定，否则整批 rc=2；不要手改 digest。

### 3.6 文档 / 复用 / 数据

40. **把“任务书写完”当“研究做完”** → V3/V4 有 288 小时队列，但 W01–W12 大多数没执行 → 任务书只是 backlog；当天实物以账本、答卷、scorecard 为准。
41. **新入口重复造旧功能** → 汇总器、修复器都曾另起一套 → 每个新工作包先填四要素（资产/证据/差额/原验收）并标 `REUSE | INTEGRATE | VERIFY_DELTA | IMPLEMENT_DELTA`；默认复用，新增必须说明为什么旧资产不够。
42. **为报告字段重烧调用** → G11 是最贵的一课 → 任何“只改元数据/表头/绑定”的需求一律离线；真实调用只用于新的实验问题。
43. **删除/覆盖旧数据** → 本项目不允许；只追加、先备份；任何清理只动临时副本，旧答卷、账本、判词、审计证据永久保留。
44. **只看“测试全绿”** → 单测通过不等于真实答案质量；每轮必须形成“机制变化→真实答卷→逐维判决→保留/淘汰→下一实验”。
45. **40+ 份指导文档全都读** → 会再次陷入旧入口/旧快照 → 只读第 5 节列出的“当前必读”；旧 V3/V4、FIRST_8H、旧 start prompt 一律当历史。

---

## 4. 下一窗口完整计划

> 原则：质量一级，费用/等待二级；不新造总计划；所有新工作包先填四要素并标 `REUSE | INTEGRATE | VERIFY_DELTA | IMPLEMENT_DELTA`。
> 截止是 2026-10-29，具体时刻未给。以下计划按“10-29 白天完成可复核实物”设计；做不完就交“未完成 + 缺口”，不改尺子。

### 4.0 先做决定：这一轮要回答哪个问题

当前证据支持的最强主张只有一句：**“在练过的题上，r1/r2 相对原版有稳定正效应；但增量、泛化和最新版净增益都没有证明。”**
下一窗口不要再试图用“再跑一批”直接宣布全面超越；先决定：

- **A（推荐，截止友好）**：用独立裁判校准现有 118 个配对位点，确认/修正 worker 判词；不新增生成。若时间允许，再加 1 个新材料批。
- **B（更强主张）**：A 通过后，再跑 2 个新材料故事 ×8 位 ×2 臂（32 次生成）+ 独立裁判；用新增泛化证据决定是否签收。
- **C（不推荐）**：直接再跑大批量生成、不做裁判校准；会重复“分数自评、结论不可信”的老问题。

### 4.1 P0 现场核验（0 调用，30–60 分钟）

| 项 | 动作 | 通过标准 |
|---|---|---|
| 入口 | 读本文件、`WORKER_CURRENT_RUNBOOK.json`、`QUALITY_FIRST_DEADLINE_ORDER.md`、`FINAL_VERDICT_20261028.md` | 能说出当前入口与判决 |
| 进程 | 查是否有 `run_max288_batch` / pilot 在执行 | 无活体再开新批；有活体先接管/等待，不重派 |
| 答卷 | 数 `*/out/turn_*/reply_raw.txt` | 260；报告副本另计 |
| 账本 | 7 个 `AUTH-P08-*` 的 started/completed/failed | 260/260/0 |
| 成绩 | 七批 `paired_position_verdicts` | 与第 1.2 节一致 |
| 绑定 | 七批 `answer_binding.status` | 全部 VERIFIED_ALL |
| 离线闸门 | `test_score_binding_guard.py`、`test_output_repair_parity.py`、`test_staging_depth_equivalence.py`、`test_rc_resolver_equivalence.py` | 全 PASS |
| 结论 | 写 `STATE_CHECK_20261029.md` | 只记录，不改数据 |

**P0 若发现账本/成绩/绑定不一致：停止一切新调用，先离线修；修不好就报“现场不一致”，不要带病开跑。**

### 4.2 P1 独立裁判校准（142 次判官调用，0 次生成）

- **目的**：验证 worker 的 118 个配对判词是否可被第三方复现；这是当前最大的评估缺口（Q4）。
- **输入**：现有 118 个配对位点的两臂首选 + 全部非空备选 + 卡面；不重新生成。
- **设计**：1 个位点 1 次判官调用（同时看 A/B 两臂），九维 0–3；随机抽 24 个位点做 A/B 换序对照；共 **118+24=142** 次。
- **判官**：`hy3` 真实负载门控，失败退 `dsf`；每维固定同一评者；Gemini 不进关键路径；`hy4-preview` 禁用。
- **复用**：先核对现有 `tools/run_judge_batch_v1_1.py`、`tools/run_quality_panel_v1.py`、`tools/judge_swap_v1_2.py` 的输入契约；能复用就复用，不新建判官平台；`judge_swap_v1_2.py` 正好用于换序控制。
- **指标**：胜负完全一致率、九维加权 kappa、严重错召回、A/B 顺序偏置。
- **停止条件**：先做 40 个位点；若一致率 <80% 或出现系统性方向偏置 → 停止，不追加生成；先修判据/样本。
- **交付**：`refs/.../deadline_quality_20261029/JUDGE_AGREEMENT_20261029.json`；若与 worker 判词冲突，把原判词标“provisional”，不删。

### 4.3 P2 新材料泛化（32 次生成 + 16 次判官，仅在 P1 通过后）

- **目的**：检验当前候选 r3 在**未见材料**上是否仍胜原版；现有 D1 13 位、D03 6 位不够。
- **材料**：2 个新故事 ×8 位，域与 D01–D03 不同；作者=下一窗口 worker，`--author-mode targeted_worker`，写 `exposure.json`；**只能标“定向复验材料”，不能称独立/盲化**。
- **设计**：`reference_product_v1` vs `action_policy_r3`，同 dsf/0.7/<model-port>，逐位交错，独立状态；32 次生成；新 batch id、新身份、新绑定；卡与题面冻结后再 capture。
- **复用**：`tools/build_stageD_dataset.py --author-mode targeted_worker`、`tools/validate_stageD_story.py`、`tools/merge_cards_for_batch.py`、`tools/exam_interleave_plan.py`、`tools/build_p08_authorization.py`、`tools/run_max288_batch.py`；照 `benchmark/stories_v2/stageD/D03` 的结构写 D04，不新建故事平台。
- **判官**：16 个配对位点 ×1 次 =16 次；与 P1 同一判者/同一 rubric。
- **停止条件**：传输失败 >2 → 停；协议失败 >3/16 → 停；候选在 ≥8/16 位点输 → 记录负结果并停，不扩样本。
- **交付**：`D04_SCORECARD.json/md`、原始答卷、账本、绑定、16 份判词。

### 4.4 P3 F2 定点修复（可选，6 次生成 + 3 次判官）

- **前提**：P2 干净且时间足够；否则不启动，把 F2 作为已知缺陷写进判决。
- **问题**：R3A 的 D02_T02 候选给了 5 条 `directions`，评分器判 `json_schema_broken`（F2 家族）。
- **动作**：只改 `action_policy_r3` 的格式合同（`format.md`/发送合同），新身份 r3.1；在 D02_T02 + 2 个对照位点上跑 2 臂 =6 次生成；3 次判官。
- **复用**：新建 `candidates/action_policy_r3_1` 转发壳，照 r3 import r2 的写法 import r3，只覆盖格式合同；`params.json.parent=action_policy_r3`；不要复制或修改 r3 原件。
- **停止**：F2 仍 >1/6 → 停，不建 r3.2；不要用消费侧补 `directions` 掩盖。
- **交付**：`R3_1_SCORECARD` + 绑定 + 差异说明。

### 4.5 P4 判决与交付（0 调用）

- 更新 `FINAL_VERDICT_20261029.md`（或 `FINAL_VERDICT_20261028.md` §12）：
  - 裁判一致率、新材料的 W/L/T、F2 状态、费用/等待；
  - 五问：效果、可靠性、等待、费用、能否接入；
  - 保留/淘汰：r1/r2/r3 各自保留什么、淘汰什么；不签收就写“未完成”。
- 所有新批次先建绑定，再聚合；`--binding` 不得省略；判决数字从 scorecard 现算，不手填。
- 不删除旧数据；新成绩另立目录；旧判词保留。

### 4.6 截止后 backlog（按优先级）

| 顺序 | 工作包 | 内容 | 估算 | 标签 |
|---|---|---|---|---|
| 1 | W10 | r2 三项修复逐项消融（FIX-6/7/8），各 12 锚点×2 臂=72 生成 | 72 生成 + 判官 | VERIFY_DELTA |
| 2 | W03 | 话题钩子、调情、深聊、反馈调整单列实测（每类 12 锚点×2=24） | 96 生成 | IMPLEMENT_DELTA + VERIFY_DELTA |
| 3 | W05 | 上下文选择/简化/污染抑制：长上下文、冲突记忆、污染探针 | 24–48 生成 | IMPLEMENT_DELTA |
| 4 | W08 | 长历史、跨日、纠正、重启恢复（2 故事×12 位×2 臂） | 48 生成 | VERIFY_DELTA |
| 5 | W04 | 语义主体错换：真实失分案例驱动的 subject 核验层 | 24 生成 + 回归 | IMPLEMENT_DELTA |
| 6 | W11 | 质量约束下的等待/费用优化（prompt/completion 分列，p50/p95） | 先测后改 | VERIFY_DELTA |
| 7 | W02/W09 | 闭环 pilot 收口；模拟器单独 epoch，不阻塞主轨 | 单独预算 | REUSE + VERIFY_DELTA |

### 4.7 预算与停止条件（总表）

| 阶段 | 生成 | 判官 | 合计 | 停止条件 |
|---|---:|---:|---:|---|
| P0 | 0 | 0 | 0 | 任何账本/成绩/绑定不一致 → 停 |
| P1 | 0 | 142 | 142 | 40 位点一致率 <80% 或系统性偏置 → 停 |
| P2 | 32 | 16 | 48 | 传输 >2、协议 >3/16、候选输 ≥8/16 → 停 |
| P3 | 6 | 3 | 9 | F2 >1/6 → 停 |
| **合计上限** | **38** | **161** | **199** | 失败也计入；无免费重试 |

管理帽 16,384 不是支出目标；实际约 199。所有调用按物理请求事前占额；失败照样记账；不复活旧单。

### 4.8 四要素核销（新派工格式）

| 包 | 资产（已有在哪） | 证据（已有结果） | 差额（缺什么） | 原验收（按什么收） | 标签 |
|---|---|---|---|---|---|
| P0 | 260 答卷、7 账本、7 成绩、绑定 | 1.2/1.5 节 | 无独立现场核验记录 | 截止单 Q0 | REUSE |
| P1 | 118 配对原文、卡面、判词 | 七批 scorecard | 无第三方裁判 | Q4 评估可信度 | INTEGRATE + VERIFY_DELTA |
| P2 | D01–D03 题卡/构建器 | D1 5:3、R3A 0:1 | 新材料只有 19 位 | Q3 泛化 | IMPLEMENT_DELTA + VERIFY_DELTA |
| P3 | r3 合同/格式 | R3A 1/24 F2 | directions 5 条未修 | Q1 交付 | IMPLEMENT_DELTA + VERIFY_DELTA |
| P4 | 全部证据 | 判决 §0–§11 | 10-29 终稿 | 截止五项 | INTEGRATE |

### 4.9 建议时间盒（按实际截止时刻压缩，不是承诺）

| 时间（Asia/Shanghai） | 阶段 | 产出 |
|---|---|---|
| 10-28 22:30–23:30 | P0 现场核验 | `STATE_CHECK_20261029.md` |
| 10-28 23:30–10-29 03:00 | P1 裁判校准（142） | `JUDGE_AGREEMENT_20261029.json`；40 位点闸门 |
| 10-29 09:00–12:00 | P2 新材料（48） | `D04_SCORECARD.*` + 绑定 |
| 10-29 12:00–14:00 | P3 F2（可选，9） | `R3_1_SCORECARD.*` 或“已知缺陷” |
| 10-29 14:00–17:00 | P4 判决 | `FINAL_VERDICT_20261029.md` + 五问 |
| 10-29 17:00 后 | 只复验/收尾 | 不再开新批 |

若截止更早：按 **P1 → P4** 保留，砍 P3，再砍 P2；**不要砍 P1 的裁判校准**，否则所有分数仍是自评。

---

## 5. 项目中很多很乱的交接文档，到底应该看什么

### 5.1 只看这些（当前有效）

按顺序：

1. **本文件** `WORKER_HANDOFF_20261028_2222.md`——最新交接快照。
2. `WORKER_CURRENT_RUNBOOK.json`——当前入口指针（以它为准；若它和本文件冲突，先查原始账本）。
3. `refs/experiment_protocol_20261023/deadline_quality_20261028/QUALITY_FIRST_DEADLINE_ORDER.md`——当前验收标准与执行顺序。
4. `refs/experiment_protocol_20261023/deadline_quality_20261028/FINAL_VERDICT_20261028.md`——判决、七批数字、FIX-9、两处离线修复（§11）。
5. `refs/experiment_protocol_20261023/s1_action_live_20261028/*_SCORECARD.md/json`——七批逐维成绩与配对；先看 `.md` 的头部与配对表。
6. 三份今天的新证据：
   - `BINDING_BACKFILL_20261028.json`
   - `SCORE_BINDING_GUARD_20261028.json`
   - `OUTPUT_REPAIR_PARITY_20261028.json`
7. `refs/experiment_protocol_20261023/reuse_audit_20261028_1946/REUSE_AUDIT_REPORT.md`——独立复核；读它是为了不重复“换入口丢旧保障”。
8. `refs/experiment_protocol_20261023/deadline_quality_20261028/WORK_PACKAGE_CHECKLIST.md`——五问/四要素的核销格式。
9. `AGENTS.md` 最上面的 3–4 条最新裁定（截止、V4、模型策略）；**下面的长历史不要从头读**。

### 5.2 只在需要时看（历史/局部）

| 文件 | 用途 | 注意 |
|---|---|---|
| `morning_resume_v4_20261028/MORNING_REVIEW.md` | 10:28 晨间复核：P06/Stage2/27-18/pilot/W09 | 是上午快照；不是当前 260 的现状 |
| `max288_v3_20261028/REVIEW_VERDICT.md` | V3 复核：输入/消费/预算问题 | 历史；这些问题部分已修 |
| `max288_v3_20261028/W04_BRANCH_ASSESSMENT.json` | 为什么不再建 `evidence_state_r1` | 读结论，不要重开分支 |
| `s1_action_live_20261028/*_WORKER_SCORES.py` | 逐位判词原文 | 不要只读摘要；改判词要同步绑定 |
| `logs/p08_*_live_20261028/**/reply_raw.txt` | 原始答卷 | 只读；不要覆盖 |

### 5.3 不要当“当前现状”读（计划/旧快照）

- `refs/.../max288_v3_20261028/MASTER_EXECUTION_V3_288H.md`
- `refs/.../max288_v3_20261028/FIRST_8H_WORK_ORDER.md`
- `refs/.../max288_v3_20261028/WORKER_START_V3.md`
- `refs/.../morning_resume_v4_20261028/MASTER_EXECUTION_V4_288H.md`
- `WORKER_NEW_WINDOW_START.md`（`WORKER_CURRENT_RUNBOOK.json` 的 `start_prompt` 仍指向它，但它是旧文案；本文件 §6 取代；runbook 的 `master_runbook`/`execution_queue` 仍以截止单为准）
- `refs/.../deadline_quality_20261028/DEADLINE_QUEUE.json`（状态停在 READY/PENDING，未随后续批次更新）
- `refs/.../deadline_quality_20261028/DELIVERY_RECORD.json`（10:56 的交付记录，不是 22:22 现状）
- 任何 2026-10-28 20:00 之前、未被 runbook 引用的“当前入口/总结”

### 5.4 冲突裁决规则

1. 原始账本/`reply_raw.txt`/`manifest.json` 最大；任何总结与它冲突，以原件为准。
2. 七批 scorecard 与绑定文件次之；`FINAL_VERDICT` 引用它们，不反过来。
3. 本文件是 22:22 的快照；之后的新批次以新 scorecard/绑定为准，不要拿本文件压新证据。
4. 旧 V3/V4/MAX288 文档只提供历史任务，不是当前授权，也不是当前进度。

---

## 6. 开启 prompt（复制即用）

```text
你接管 LoveBrain 完整 Harness 优化主线。实验目录 <arena-root>，原项目 <LoveBrain-repo>，产品接入暂停。截止 2026-10-29，质量标准不降；做不完就明确未完成，不改尺子。

第一步只读核验，不派发、不花钱：
1) 读 <arena-root>/WORKER_HANDOFF_20261028_2222.md（最新交接快照）
2) 读 <arena-root>/WORKER_CURRENT_RUNBOOK.json（当前入口）
3) 读 refs/experiment_protocol_20261023/deadline_quality_20261028/QUALITY_FIRST_DEADLINE_ORDER.md（验收标准）
4) 读 refs/experiment_protocol_20261023/deadline_quality_20261028/FINAL_VERDICT_20261028.md（判决与七批数字）
5) 读 refs/experiment_protocol_20261023/s1_action_live_20261028/ 下七批 *_SCORECARD.md 的头部与配对表
6) 读 refs/experiment_protocol_20261023/deadline_quality_20261028/ 下的 BINDING_BACKFILL_20261028.json、SCORE_BINDING_GUARD_20261028.json、OUTPUT_REPAIR_PARITY_20261028.json
不要读 V3/V4 的 288 小时总计划当现状；它们是历史 backlog。

核验并报告一张表：260 份 live 答卷是否在、7 个账本是否 260 started/260 completed/0 failed、七批 W/L/T 是否与判决一致、绑定是否 130/130 VERIFIED_ALL、离线闸门是否全过、当前是否有执行器在跑。没有核验完不要开新批。

然后按交接文档第 4 节执行：
- P1 先做独立裁判校准：118 个配对位点 + 24 个换序对照 = 142 次判官调用，不新增生成；前 40 个位点一致率 <80% 或系统性偏置就停。
- P1 通过后，P2 做 2 个新材料故事×8 位×2 臂 =32 次生成 +16 次判官；新 batch、新身份、新绑定；传输失败 >2、协议失败 >3/16、候选输 ≥8/16 就停。
- P3 只在 P2 干净且时间足够时做 F2 定点修复：D02_T02 + 2 对照×2 臂 =6 生成 +3 判官；修不好就写已知缺陷，不建 r3.2。
- P4 更新判决与五问（效果/可靠性/等待/费用/能否接入），保留/淘汰写清楚。

每个新工作包先填四要素：已有资产、已有证据、差额、原验收；并标 REUSE | INTEGRATE | VERIFY_DELTA | IMPLEMENT_DELTA。默认复用旧资产；新增实现必须说明为什么旧的不够。
真实调用只用于新的实验问题；改表头/绑定/报告字段一律离线，不重跑旧答卷。一物理请求一 attempt，事前占额，失败也记账；没有免费重试，不重试到赢，不覆盖旧答卷，不删除任何数据。
质量一级、费用等待二级；每轮必须形成“机制变化→真实答卷→逐维判决→保留/淘汰→下一实验”。不要停在“已了解、测试全绿、文档写完”。
```

---

## 7. 附录：关键路径与命令

### 7.1 关键路径

- 当前入口：`WORKER_CURRENT_RUNBOOK.json`
- 当前任务书：`refs/experiment_protocol_20261023/deadline_quality_20261028/QUALITY_FIRST_DEADLINE_ORDER.md`
- 当前判决：`refs/experiment_protocol_20261023/deadline_quality_20261028/FINAL_VERDICT_20261028.md`
- 七批成绩：`refs/experiment_protocol_20261023/s1_action_live_20261028/`
- 原始答卷：`logs/p08_{s1,s2,s2r,s3,d1,r3a,r3b}_live_20261028/*/out/turn_*/reply_raw.txt`
- 账本：`logs/auth_budget/AUTH-P08-*-20261028-01.jsonl`
- 审计：`refs/experiment_protocol_20261023/reuse_audit_20261028_1946/REUSE_AUDIT_REPORT.md`

### 7.2 核验命令（只读）

```bash
cd <arena-root>
date
find logs -path 'logs/p08_*_live_20261028/*/out/turn_*/reply_raw.txt' | wc -l
PYTHONIOENCODING=utf-8 <local-python> -c "import json,glob; [print(p, json.load(open(p,encoding='utf-8'))['paired_position_verdicts']) for p in sorted(glob.glob('refs/experiment_protocol_20261023/s1_action_live_20261028/*_SCORECARD.json'))]"
PYTHONIOENCODING=utf-8 <local-python> tools/test_score_binding_guard.py
PYTHONIOENCODING=utf-8 <local-python> tools/test_output_repair_parity.py
```

### 7.3 一句话交接

**已经做出真实对照和可信的负结果；还没有做出“全面超过原版”的正结果。下一窗口先校准裁判，再决定要不要用新材料和 F2 修复去补证据；不要重跑 260，不要删数据，不要把旧计划当现状。**
