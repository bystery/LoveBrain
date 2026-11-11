# LoveBrain 斗兽场 · Phase 1A 第四波交付报告（供大牛全面检查）

> 日期：2026-10-18 上午 ｜ 撰写：Worker ｜ 状态：等独立复审
> 本报告承诺：只写真实发生过的事；凡未做的，明确写"未做"。

## 当前状态（唯一口径，2026-10-19 晚第三次更新——历史段落按时间线阅读，以此表为准）

| 项 | 当前状态 | 时间界限 |
|---|---|---|
| 评分权威（P0） | **已签收**（第七波判决 §2：四类篡改 illegal_source_id、时钟/媒体同证） | 2026-10-19 |
| 动作协议/类型安全（P0） | 指定反例已签收；**嵌套类型**（scene_facts[0].text=对象等）v0.9 补收口：评分与写回共用 analysis_type_errors，坏嵌套=格式失败+无非法记忆+第二轮可审计（test_output_structure PASS） | 2026-10-19 晚 |
| 开放输出契约 | v0.9：extra_text_fields 显声明的扩展字段并入协议键（不再误杀）；未声明非空字符串仍拒绝；IO_CONTRACT_PILOT 升 v2 与代码同口径 | 2026-10-19 晚 |
| 输入 schema（P1） | **部分完成→补全**：容器错型（messages 字符串/字符串列表、intent 列表、clock 对象、may_decline 字符串）入口拒绝；legacy 字符串 intent 版本协议保留（test_input_boundary 7a-7f） | 2026-10-19 晚 |
| 冻结源防错（P1） | v4 编排器：turns_done==4+episode/candidate 身份+前史产物校验，六轮旧源实测拒绝并落 rejection.json；后测身份锁定（manifest.run_id 绑定+attempt 事件）；--memory-off 无能力候选明确拒绝；--pairs/--order 交错编排就绪（未跑真实对） | 2026-10-19 晚 |
| 生成源可再生成（P1） | build_pilot_json 三处旧文案已修，再生成产物与交付 JSON 语义一致（P1 项关闭） | 2026-10-19 晚 |
| 历史盲评展示（P1） | R2 改用运行时冻结题面快照（archive/PILOT_L1_v1__pilot_r3.json，sha256=7c26566239f64f6c），绑定自检 12 节点×2 候选全命中当时请求；材料头标明 run+评分版本；新题面审核走双视图 | 2026-10-19 晚 |
| 审计生命周期（P1） | v3 已修（run_id 不可覆盖、INDEX 追加、子进程 pid、restore 清单、attempt 三态）；v4 增冻结源/后测身份校验 | 2026-10-19 |
| 审稿包 | v2 白名单包：无自引用 manifest（包内=payload 清单、sidecar=整包 sha256）；补 rubric.md + judge_calib.json；dataset/_archive_v1 零成员断言 | 2026-10-19 晚 |
| L3B 有限真实复跑 | **暂缓**（第七波判决准入：本轮离线收口完成后，16 请求方案 --pairs 4 --order AB,BA,BA,AB 已备好待授权） | 待授权 |
| L3B 长期记忆 | **未验收**（n=1 窄证据；纠错/过期/隔离未测）；冻结分叉原型可留 | 持续冻结 |
| Judge | **不上岗**（保留率未过 80%；归因未确定；无独立验证集） | 持续冻结 |
| 自动进化 | **冻结** | 持续冻结 |
| 交付包 | v4/v5 全量包**不外发**（含 dataset/_archive_v1，本地留证）；外发一律用白名单审稿包 | 2026-10-19 |
| L3B 真实调用账 | 8 条唯一调用可核（前史 4+后测 4）；13:46 被覆盖的前史 4 条**无法恢复，如实缺账** | 2026-10-18 |

---

## 〇、给评审的三句话摘要

1. **第三轮判决（2026-10-17 深夜《第三轮独立复核》）的六条指令已全部执行**，每条落点见 §三。
2. 本波抓到并修复了 **两个真实工程 bug**（网关 temperature=null、runner 裸候选名静默失败），均有存档证据。
3. **第一份真实模型匿名盲评材料（9 组）已产出**——这是本项目第一次让真实候选在固定历史下生成可读回复，等您盲评。

---

## 一、项目背景（30 秒版）

培育一套回复质量胜过用户产品 LoveBrain 的微信聊天辅助 AI（"Monster"）。方法论：**机制 >> prompt 调优，真人感第一，考场（评测体系）先于进化**。

- 候选 = harness 机制 + 资产（非 prompt 包装）。当前候选：`round_0_baseline`（LoveBrain 复刻）、`phase0_beta_single`（文件 KV + LCS 检索 + 原子写，单次调用）、`phase0_beta`（同上加二次记忆调用，消融显示暂无收益已默认关闭）。
- 三层评分：lb_scorer（确定性 C0-C13）→ judge 评审团（**未上岗**）→ 用户 HITL。
- 当前阶段：**Phase 1A（真实聊天质量 Pilot + Judge 可信度校准）**，大规模自动进化冻结中。

## 二、历史阶段与三轮评审结论（速查）

| 阶段 | 核心交付 | 评审结论 |
|---|---|---|
| Phase 0 | 架构核验 + 生命周期改造（init/run_turn/snapshot/restore）+ 220 轮 echo 考场 + α/β 双架构 | 通过（附条件） |
| Phase 0.5 | 负对照判别力 ×3、基线保真审计、调用预算消融、严格意图对照、强杀恢复、judge 校准首轮 | 有条件通过 |
| 第一轮评审 | PHASE1A_JUDGE_RERANK.md（重排实验）+ Pilot 大纲 v1 + L1/L2 台词 v1 | 部分退回：Judge 归因过早、L3 返工、台词 ME 占位 |
| 第二轮评审 | 2×2 归因实验 + 大纲 v2 + 台词 v2 + HUMAN_FLAVOR_AUDIT + 证据包复算 + IO 契约 + L3 来源包 | 本轮不判 PASS，执行令 §7 六步 |
| 第三轮评审（最新） | 投票模拟 + L1/L2 JSON 化 + 双视图 + L3 台词 + 盲评 9 组 | **本报告即对该轮执行的交付** |

关键已撤回结论（防误引用）：
- ~~"temperature=0.7 是判决翻转头号根因"~~（撤回：旧日志出站 temperature 实为 null，且服务端行为未核验）
- ~~"A 类 5 条 = 真 Judge 错误实锤"~~（降级为"稳定拒绝，标签待终审"）
- ~~"位置效应独立于内容、两倍差距"~~（降级为观察性差异）
- SPLIT_v1 的 val 6 题 ≠ 独立验收集（已降级为回归留出集）

## 三、第三轮判决六条指令 → 落点对照

| # | 判决指令 | 落点 | 状态 |
|---|---|---|---|
| 一 | Judge：投票模拟先行、逐选项翻转统计、不刷旧 20 题、不上岗 | `refs/PHASE1A_JUDGE_2X2.md` 附录五 + `arena/lb_vote_sim.py` + `logs/phase1a/vote_sim.json` | ✅ |
| 二 | L1：ME 真实化、笔误、间隔口径、图片资产、t09/t10 时间冲突、t12 短告别 | `tools/build_pilot_json.py` → `benchmark/episodes/PILOT_L1_v1.json`；校验器 PASS | ✅ |
| 三 | L2：宣传腔、单方推动、排班、"分晓自见"、14:57 口径、3号场来源、反手密度、回笼觉、t11 不计请求 | 同上 → `PILOT_L2_v1.json`（v3 重写，22 请求位） | ✅ |
| 四 | 数据协议：已发送历史/待回复消息/意图/时间戳/gold 隔离/可接受范围 | `refs/IO_CONTRACT_PILOT.md`（七类事件 + 时间三分 + action 位 + 防泄露自检） | ✅ |
| 五 | L3 来源包 + 试写；L3B 不可无限延期 | `refs/L3_SOURCE_PACK.md`（S1/S2/S3 出处原文）+ `refs/PILOT_DIALOGUES_L3_v1.md`（过敏段 5 节点 + 约饭段 6 节点，**待审未入库**） | ✅（L3B 协议已立，**实施未开始**） |
| 六 | 双视图同源（A 纯聊天 / B 结构化） | `tools/render_pilot_views.py` → `refs/PILOT_VIEWS_A_clean.md` / `PILOT_VIEWS_B_structured.md` | ✅ |
| 七 | 时间线自动校验 | `tools/validate_pilot_timeline.py`（跨节点单调 / 资产存在 / 防泄漏禁词），L1+L2 全 PASS | ✅ |
| — | 真实模型回复 8–10 组 | `refs/BLIND_SAMPLES_PILOT_R1.md`（9 组，见 §五） | ✅ |

## 四、本波抓到的两个工程 bug（含证据）

**Bug 1：网关 temperature=null。** 适配器 `payload.get("temperature", 0.7)` 在显式传 None 时仍会发 `"temperature": null`（键存在，默认值不生效）——此前所有 judge 实验实际出站温度未知。修复：显式归一 None→0.7；CallLog 新增**实际出站 temperature + 原始响应全文（raw_text）落盘**（此前只有 usage，响应原文未存，这正是评审指出的"不可核验"项）。证据：`logs/_invalid/` 旧运行对比 + `arena/lb_gateway.py` 当前实现。

**Bug 2：runner 裸候选名静默失败。** `--candidate round_0_baseline`（裸名）时 assets_root 解析为相对路径 `round_0_baseline/assets`（不存在），`read_asset` 吞掉 OSError 返回空串 → **system prompt 整体为空（148 字符）→ 模型散文作答 → 12/12 假 veto**。修复：`resolve_candidate_dir` fail-fast（`arena/lb_runner.py`）。无效运行完整归档于 `logs/_invalid/pilot_r1_bare_candidate_bug/`（未删除，可复核）。

> 注：打包时安全删除护栏拦截了 `rm -rf`，改为 mv 归档——无效数据完好保留。

## 五、真实模型盲评材料（本轮最重要交付）

**制作方式**：L1 全 12 请求位，同冻结历史（候选不可改写已发生消息），固定 dsf 端点，`round_0_baseline` vs `phase0_beta_single` 同输入生成；9 组 A/B 匿名随机对（seed=20261018，密钥 `logs/pilot_r1/blind_key.json`）。

如实声明（2026-10-18 修订：采纳第四波判决 §4.2/§4.3）：
- 基线 3 轮失败**拆分口径**：t04 = JSON 语法错误、t07 = JSON 可解析但缺 `analysis`（schema 缺失）、t08 = JSON 语法错误——非笼统"JSON 碎裂"。**如实排除不冒充样本**；9 组为条件样本（非媒体、双候选均成功位），不能汇总成机制胜率，全请求可用性 = 基线 9/12 vs β_single 12/12 必须并列报告；
- 媒体轮 t07 的图片资产未注入请求（旧代码占位传递，**runner/机制两条路径均已修复并落盘 media_refs**），该轮当时不构成视觉理解测试；
- 盲评材料呈现 `response.recommended` 的结构化提取（非润色），18 条展示文本已获独立核对与原始 `recommended` 逐条对应；原始输出全文在 `logs/pilot_r1/*/turn_*/reply_raw.txt`；
- 本批全部为**开发反馈**，不进封存验收集。

初步观感（2026-10-18 修订：删除无效运行误引）——~~"t05 基线输出整段购机报告体"~~ **系引用错误**：正式运行的 t05 基线 `recommended` 实为短问句（"四五千现在主流就那几个，看你重拍照还是重流畅，说清楚我好给你圈范围"）；长篇分节购机报告仅存在于 `logs/_invalid/pilot_r1_bare_candidate_bug/baseline_L1/turn_05_L1_t05/reply_raw.txt`（裸候选名空 prompt 的已作废运行）。此错误已撤回，正式报告的每个质量样例须引用 run 目录 + case_id + 原文。评分器均值：基线 7.5 / β_single 10.0——**差额全部来自基线 3 个格式失败（0 分），不构成自然度差异**。

## 六、Judge 投票模拟结论（零新调用，附录五全文见 2X2 报告）

| 协议 | 聚合保留率 | 陷阱识别 |
|---|---|---|
| 三票多数 · temp=0 | 70.9% | 20/21 |
| 三票多数 · temp=0.7 | 65.8% | 21/21 |
| 五位置票 ×5 | 72.2% | 21/21 |

**结论（判决令口径）**：~~"缺口是系统性的（gold 争议 + 判罚标准），不是采样噪声"~~
**【2026-10-18 第五波判决 §9 原处撤回】**——上句属归因回退：2×2 复核后"服务端实锤/
系统性缺口/不是采样噪声"等结论证据不足，正式结论以本节末尾增补与
refs/PHASE1A_JUDGE_2X2.md 附录为准（保留率 61–73% 全条件未过 80% 为实测事实；
归因不回退到"系统性实锤"表述；temp=0 的 25/100 翻转只能记"**相同名义温度条件下观察到判决翻转**"（第七波判决 §8：同温度重复不一致不能证明改变温度的因果效果），
不可区分"参数未生效 vs 服务端非确定性"，旧日志无法补证）。**不上投票协议，
judge 继续不具晋级裁判资格**（此结论不变）。

## 七、诚实清单：未做 / 未完成（防"修订项全部落点"式虚报）

| 项 | 状态 | 说明 |
|---|---|---|
| Judge 独立真人标注验证集 | **未建** | 需新场景 + 真人标注 + 全程隔离；等 Pilot 盲评回流后启动 |
| 27 条误杀的标签终审 | **悬置** | 稳定拒绝 10 / 波动拒绝 17，全部待您/用户裁决，未动 gold 一字 |
| judge prompt / 评分标准修改 | **未动** | 按判决令禁止先改 |
| L3B 空状态自主记忆专项 | **协议已立，实施未开始** | 十条最低协议见 L3_SOURCE_PACK.md §L3B |
| 沙箱整改（进程隔离/文件 ACL/封存不可见） | **未实施** | 只在 IO 契约层定义了可见性，未做运行时隔离 |
| 基线保真补齐（记忆纠错四动作/来源校验） | **方案在 BASELINE_FIDELITY.md，未实施** | 当前 round_0_baseline 仍是缩水复刻 |
| B 线封存验收集 | **框架就绪，未启用** | benchmark/b_line/README_SEALED.md |
| L2 全线真实模型运行 | **未跑** | 仅 L1 跑了双候选 |
| 大规模自动进化 | **冻结中** | 等全部解锁条件 |

## 八、请求您重点审查的问题（按优先级）

1. **盲评 9 组**（`BLIND_SAMPLES_PILOT_R1.md`）：四选一 + 改写。请同时裁：盲评呈现格式是否可用、`response.recommended` 提取是否丢真。
2. **双视图连续阅读**（`PILOT_VIEWS_A_clean.md`，约 10 分钟）：v3 修订后还有没有"编剧式推进 / 单方推动 / 刻意造梗 / 旧事重提"？我们自查已按关口 2 六维度做过，但需要您的连续视角。
3. **L3 台词**（`PILOT_DIALOGUES_L3_v1.md`）：过敏段"提示版 A / 无提示诊断分叉 D-1"的拆分是否成立；S2/S3 本批不出现是否正确。
4. **盲评方法学**：同冻结历史 + 固定端点 + 随机匿名，这个设计对"第一次真实质量信号"是否足够？缺什么控制？
5. **bug 修复复核**：gateway temperature 归一 + runner fail-fast（§四），实现是否有漏洞。
6. **JSON 考题**：`PILOT_L1_v1.json` / `PILOT_L2_v1.json` 的 schema 是否达到"可入库"标准。

## 九、文件索引（本包内）

```
refs/                       全部报告与规范（含 archive/ 历史版本存档）
  ├─ 本报告                 REVIEWER_REPORT_2026-10-18.md
  ├─ 盲评材料               BLIND_SAMPLES_PILOT_R1.md            ★ 优先
  ├─ 双视图                 PILOT_VIEWS_A_clean.md / B_structured.md ★ 优先
  ├─ Judge 系列             PHASE1A_JUDGE_{RERANK,2X2,EVIDENCE}.md
  ├─ Pilot                  PILOT_OUTLINES_v2 / PILOT_DIALOGUES_{L1L2_v2,L3_v1}
  ├─ 契约与纪律             IO_CONTRACT_PILOT / HUMAN_FLAVOR_AUDIT / L3_SOURCE_PACK
  └─ 历史存档               archive/（v1/v2 报告原样保留）
benchmark/
  ├─ episodes/PILOT_L{1,2}_v1.json   正式考题 JSON（时间线校验 PASS）
  ├─ anchors/                        20 题锚点 + 密钥 + SPLIT_v1（已降级口径）
  ├─ media/pilot/                    4 张虚构布景图
  └─ b_line/                         B 线封存框架
arena/                      runner / gateway / mechanism / scorer / 全部实验脚本
  └─ lb_vote_sim.py, lb_judge_2x2.py, lb_judge_rerank.py, lb_judge_calib.py
tools/                      build_pilot_json / validate_pilot_timeline / render_pilot_views / make_blind_samples / mk_pilot_assets
candidates/                 全部候选（baseline / alpha / beta / beta_single / 负对照 / probe）
logs/
  ├─ phase1a/               judge 重排 + 2×2 + 轮换 + vote_sim 全部原始记录
  ├─ pilot_r1/              L1 双候选真实运行（含 reply_raw.txt 原始输出 + blind_key.json）
  ├─ _invalid/              bug 证据存档（裸候选名空 prompt 运行、mock smoke）
  ├─ phase05/               judge_calib.json + discrimination.json（其余大目录未入包）
  └─ decisions.md           决策日志
AGENTS.md / OBJECTIVE.md / 指导书.md   项目章程
```

> 未入包：logs/phase0、logs/phase05 的逐轮大目录（约 60MB，如需可补）、_archive_v1、dataset/（用户真实素材，避免外传）。

---

Worker 2026-10-18


---

# 【第五波增补】第四波判决执行结果（2026-10-18 上午，同日完成）

> 六步执行令全部落地。本节由 Worker 追加，供同一轮复审。

## §A P0 输入边界与事实通道（判决令 §12.1）

- `lb_runner.py`：SANITIZE_DROP 黑名单 → **白名单**（TURN_VISIBLE_KEYS / INPUT_VISIBLE_KEYS / EP_META_VISIBLE_KEYS）；meta/gaps/未来信息不再可能因"不在黑名单"泄漏。
- **共同可见入口**：episode.persona（城市/关系/身份）经白名单下发，默认机制与 β 均消费——L1 的"苏州/合肥/损友"首次真正进入模型请求。
- **消息时间通道**：render_chat 逐条携带 ts；timestamp block 补"最近一条消息时间"；两机制的时间差改真实时间戳计算（废除"每轮归零"/"固定+3"）。
- **反例验证**：tools/test_input_boundary.py（独特隐藏标记 XXHIDDENMARKERXX）PASS——meta/gaps/gold 零泄漏，persona/消息 ts/may_decline 均到达模型请求。

## §B no_reply 协议与媒体（§12.2）

- 输出契约 v0.6：`action=no_reply` 与旧式 decline_reason 等价；**基线 params 补 may_decline=true + 三候选 format.md 同款条款**（同能力适配，变更已记账）；轮级 input.may_decline 注入 prompt。
- 协议测试 tools/test_no_reply_protocol.py：**3 候选 × {action, decline, normal} × {allow, required} 全 PASS**（违例必扣分）。
- 媒体：消息图片字段统一为 `image`（v1 的 image_ref 与读取侧不一致 = 媒体从未进请求的根因，又一个静默 bug）；runner v1/v2 两条路径均显式传网关 image_url 通道，manifest 记 media_refs。
- 媒体定位改口：虚构文字卡 = **OCR 能力测试**，非生活照片理解（B 视图已标注）。

## §C 校验器/视图/台词（§12.3）

- 校验器 v2：**三个反例自检（--selftest）全部捕获**（末条改 ME / 请求时钟倒退 / 意图藏标注）；新增请求时钟跨节点单调、唯一 id、字段类型、媒体映射、gap 区间、末条 HER、意图防泄漏（专有标注词，不泛禁单字——"考试"不违规）。校验器还顺手抓出 L2_t04/t21 媒体登记不一致（已修）。
- 双视图 v2：A 视图跨日显示日期 + 沉默区间 + gap 渲染（v1 的 gap 从未进入 events 的 bug 已修）；B 视图头部声明输入边界。
- 台词：L1 t06 虚构型号（星耀 12 / 12 Pro）；L2 t18 ME 与回嘴严格对应（高远球出底线）；L2 t07 判据更新（她主动点出没聊 → 允许轻松接话头）；L2 t04 商场图改"周三的万象城"；鞋图删无来源"依旧"。

## §D 证据与报告修正（§12.4）

- REVIEWER_REPORT：t05"购机报告体"误引**已撤回**（仅存在于无效运行 `_invalid/pilot_r1_bare_candidate_bug`）；基线 3 失败拆分（t04/t08 语法错、t07 schema 缺失）；"JSON 碎裂"表述废弃；EVIDENCE 改 attempt>0 口径 + "340 最终记录 ≥342 尝试"不冒认精确总账。
- 2X2 附录：删"不是采样噪声""服务端非确定性实锤""下次可区分"等因果回退——全部降级为观察描述 + "需新运行产生证据"。
- Judge 脚本：call_judge 现在把 evidence（出站温度/resp_model/resp_id/finish/raw_text）写进实验 JSON；网关失败调用也记账（此前异常时 calls 丢失）。

## §E 复跑与新盲评（§12.5）

- L1 v5 重跑（logs/pilot_r3，3 分钟）：**基线 11/12、β_single 11/12 契约通过**（失败轮不同且随机：基线 t01、β t06，均为 JSON 语法错——单次失败不能下稳定性结论）。
- **图片端到端生效**：t07 双候选均通过且基线输出"你不是说预算四五千吗，这个5999超了吧"——OCR 价格 + 关联历史预算（真实记忆证据，非预置）。
- **no_reply 端到端**：t12 基线输出 `{"action":"no_reply","reason":"对方已明确道别…"}` 得 10 分。
- 新盲评 refs/BLIND_SAMPLES_PILOT_R2.md：12 组（媒体轮已纳入）；含共同背景、A/B 分别三态判定 + 总选 + "不回更合适"选项、失败行保留对方输出、可用性并列（11/12 vs 11/12）。seed=20261019。

## §F L3 修源 + L3B 最小实跑（§12.6）

- 来源包修正：S2 改"最后已知计划"（不认定刚开课）；S3 精确日期降为编剧排期不入候选 + 星期勘误（10-13 周二/10-17 周六）；L3B 跨度改 44 天。
- **L3B 最小实跑**（refs/PHASE1A_L3B_MINI.md，n=1 如实声明）：空状态自主存储 / 窗口驱逐（t5 输入实测无"过敏"）/ 重启恢复（真实超时事故行使 resume）/ 44 天召回（"之前不是说不让进你房间吗"）/ 对照分支零猜中 / 无过度回提——**七项最低协议全过**；LCS 检索的词面局限如实入档。

## §G 状态

judge 不上岗 / 题库不封板 / 进化冻结 / 沙箱与基线保真未实施——均未变。等本轮复审。
