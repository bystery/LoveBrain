# Phase 1A 第六波交付说明（2026-10-18，对第五波判决四项执行令的落点）

> 对应判决：PHASE1A_FIFTH_DELIVERY_REVIEW_20261018.md §11 执行令 1–4。
> 本波零真实模型调用（判决 §11-1：修展示不需调模型；§11-3：受控异常离线验证）。
> 有限真实调用档（L3B 分叉 4 请求）已就绪，待本地推理服务开启即跑。

## 一、执行令 1：堵评分与呈现两个 P0 ✅

### 1a. 评分器 no_reply 绕过（判决 §2）→ lb_scorer.py v0.7

动作三分落地：**合法性（veto）/ 恰当性（计分检查）/ 任务完成（正向检查不豁免）**。

| 反例（判决原文） | 修复后行为 |
|---|---|
| 普通问句 + 默认契约 + `{"action":"no_reply"}` | **veto=unauthorized_no_reply**（越权不回） |
| 对 L1 全 12 节点提交全部不回（两候选契约路径） | 仅 t12（may_decline 轮）得 10；其余节点 decline_appropriateness 计 0（恰当性未证实）+ 正向检查 fail → **总分 <120，无法刷满分** |
| no_reply + 非空待发正文 | **veto=decline_with_text**（矛盾动作） |
| action=reply + decline_reason + 正文 | veto=decline_with_text |
| action="block" 等非法值 | veto=invalid_action |
| 合法不回（may_decline 轮） | 显式进入不回分支：免风格/正文类检查，新计分检查 decline_appropriateness（gold.expect_decline 或轮级 may_decline 才得分） |
| baseline params 顶层 may_decline 只写未读 | load_contract 已接线（无 output_contract 且顶层 may_decline=true → 视为 `{"may_decline": true}` 契约），测试断言接线生效 |

验收：`tools/test_no_reply_protocol.py`（正夹具 18 组合 + N1–N5 负对照）**PASS**。

### 1b. 盲评不回展示（判决 §3）→ make_blind_samples.py v3

- 合法不回展示为"〔建议动作：**不回复**（无需发送文本）〕+ 模型理由（注明不作为待发正文）"，不再误标 extract_failed；
- 可用性改以 **runner 裁决（manifest.veto）**统计：**双方 11/12**；有效输出构成 = 基线 10 文字 + 1 不回动作，β_single 11 文字 + 0 不回；2 个真实格式失败如实标注；
- 展示层提取失败单独标注且**不改写**契约状态；
- 第 7 组补齐：**图片本物**（相对路径嵌入）+ **既往预算出处**（t05 她 2026-10-14 原话三行，未润色）；
- 未重新调用模型。产物：refs/BLIND_SAMPLES_PILOT_R2.md（R2 修订版）+ blind_key.json（同 seed 可反查）。

## 二、执行令 2：输入边界与未接字段 ✅

| 缺口（判决 §7/§8/§10） | 修复 | 验收 |
|---|---|---|
| 嵌套隐藏元数据（messages[i].meta / intent.hidden）放行 | sanitize_turn 按 **MESSAGE/INTENT/KB 允许字段重建**（白名单外剥除） | test_input_boundary 新增嵌套反例 **PASS**（零泄漏） |
| 候选改消息对象可改到评估侧原对象（共享可变对象） | 重建即深拷贝；kb 亦深拷贝 | 别名篡改反例 **PASS** |
| 媒体单向登记（登记未引用 → 图片静默丢失） | validate_media_mapping **双向校验 + 资产存在性**，fail-fast | 反例（仅登记）**PASS**（ValueError 拦截） |
| hours_since_last_exchange 写新读旧（仍渲染 topic_age） | build_knowledge_block 读侧接通；时间语义三分（交流间隔/话题陈旧度分开） | 判决独立反例（topic_age=72、gap=0.25）实测渲染"距上次对话：不到1小时前"+"话题来自72小时前" **PASS** |
| initial_kb 只缓存未消费 | beta 机制按 kb 形态字段实际消费（me/her/warmth/style/stage/lessons/scene_chain/ongoing_plan 进首轮知识，运行期状态照常覆盖） | test_memory_off：initial_kb 标记进入 prompt **PASS**；关记忆分支不消费 |
| 候选资产空目录/缺 core 返回 148 字符框架 | require_candidate_assets fail-fast（四文件存在且非空） | 代码路径接入 run_case/run_episode |
| 校验器缺跨请求时钟独立反例 | 新反例：各节点内部全合法、仅跨请求倒退 → 捕获；另补媒体单向登记反例 | validate_pilot_timeline --selftest 4 反例 **PASS** |

## 三、执行令 3：失败日志做成持久证据 ✅

- 网关 v3：**每次调用尝试（成功/失败）即刻追加 JSONL 事件**（gateway_events.jsonl），失败记录含完整请求/错误类型/出站参数——不再依赖 runner 成功路径；
- runner：轮级 **try/finally**——异常路径也落盘 `calls.json` + `error.json` 后再上抛；
- usage 容错：失败 usage 记 `source=error`，汇总标 `has_unknown_usage`（未知≠0 成本），KeyError 修复；
- judge 三脚本（rerank/calib/2x2）：全部尝试逐条留证（evidence.attempts 含每次原始响应），事件日志同步落盘；
- 运行完整性：episode_manifest 新增 `integrity`（episode/核心代码/候选资产树 sha256）；
- **受控超时实测**（tools/test_failure_persistence.py，本地假服务端真实读超时，零外网）：事件日志/calls.json/error.json 全部落盘 **PASS**；judge 首次坏响应留证 **PASS**。

## 四、执行令 4：L3B 冻结分叉重做 ✅（离线档）/ ⏸（真实调用档待服务）

- **设计否决落实**：旧 CTRL（有前史/无前史）废弃；改为同前史冻结点 → 快照+sha256 → **新进程/新目录 restore** → 分支 A 完整记忆 / 分支 B `--memory-off`；
- **记忆渠道关闭清单**（机制级实现并逐轮落 notes）：kv_facts / kv_ongoing / topic 摘要 / 检索缓存 / last_clock / initial_kb；persona 共同画像保留；
- **后测分开计分**：t5 must_kw_0（过敏约束）/ must_kw_1（房间边界，关键词不在窗口无 leak）；t6 stale 确定性断言；L3B episode gold 已重写（63 天口径同步勘误进 build_l3b_mini.py）；
- **恢复证据链**：fork_log.jsonl 记事件/时间/pid/快照散列/sandbox 文件树；mock 离线全链路已跑通（logs/l3b_fork/，resumed_from=4 实录，A/B prompt 差异可见）；
- **协议级开关验收**：tools/test_memory_off.py——memory_on 六渠道全可见 / memory_off 全关闭 / persona 保留 **PASS**；
- **"七项全过"已删除**：PHASE1A_L3B_MINI.md 重写为**已有证据/待证据矩阵**（重启恢复、记忆对照两项改❌，63 天口径勘误）；
- 真实调用档（2 分支 × 2 轮 = 4 请求）：`python tools/l3b_frozen_fork.py --adapter openai_compat`——待本地 dsf 服务开启（本轮未启动任何服务，未花一分钱）。

## 五、报告修正（判决 §9 末段）

REVIEWER_REPORT_2026-10-18.md §六旧结论**原处划线撤回**（"系统性缺口/不是采样噪声/服务端实锤"降级为归因回退说明），不再同报告并存相反结论。

## 六、全量自检清单（本波实测）

| 自检 | 结果 |
|---|---|
| test_no_reply_protocol（正 18 组合 + N1–N5 负对照） | PASS |
| test_input_boundary（顶层+嵌套泄漏/深拷贝/媒体单向） | PASS |
| test_failure_persistence（受控超时 × 网关/runner/usage/judge） | PASS |
| test_memory_off（六渠道开关 + 时间读侧反例） | PASS |
| validate_pilot_timeline --selftest（4 反例）+ L1/L2/L3B 全集 | PASS |
| L3B 冻结分叉 mock 离线全链路 | PASS（真实调用档待服务） |
| 盲评 R2 修订版重生成（12 组，双方 11/12） | 完成 |

## 七、如实声明（不在本波宣称的事项）

1. 未调真实模型——现有 pilot_r3 运行记录未变；t12 评分口径变化（新 decline_appropriateness 检查）仅影响**未来**运行，旧 scoring.json 不回写。
2. L3B 分叉真实档、判罚键（27 条误杀终审）、judge 独立验证集、沙箱隔离、基线保真——维持原状未动。
3. L1 t08"B 高配"抽象指代等文案残留（判决 §10 列举）未在本波修——属题库文案，等冻结版本后按 §11-5 有限重跑一并处理，避免边跑边改资产。
4. 嵌套白名单只覆盖结构化容器；用户自由文本中的词不做扫描（判决 §7 口径遵守）。

## 八、增补（同日 13:45，真实调用档已完成）

上节"待本地 dsf 服务"不再悬置——服务实际在线（127.0.0.1:17770，model=dsf；此前探测端口有误，责任在我方探测清单不全）。已执行 `python tools/l3b_frozen_fork.py --adapter openai_compat`：

- **4 次真实调用全部完成**，resumed_from=4、冻结/恢复散列链一致（fork_log.jsonl）；
- **A 分支 t5 过敏约束真实命中**（"你要是过敏就别硬撑"），prompt 含过敏+边界+ongoing 实录；B 分支 prompt 记忆区全空、未提过敏——消融有效（详见 PHASE1A_L3B_MINI.md §四）；
- 如实记录：本轮找回的是**过敏**（旧跑找回的是**边界**），两次各命中一半，单次运行不外推；边界事实在 prompt 里但 A 回复未使用。
- 记录位置：`logs/l3b_fork/`（fork_summary.json / fork_log.jsonl / A_full、B_memory_off 各 turn_*/calls.json + gateway_events.jsonl）。
- 更新后的包：`lovebrain_arena_phase1a_20261018_v4.zip`（v3 之上新增本节与 L3B §四、真实档日志）。
