# W09-01 ARCHITECTURE_MAP V1（2026-10-30，main-zcode-20261030-0052）

- 依据：本轮实证（三臂同入口干跑 PASS、FS1 两批 live 链路、params/MECHANISM/IDENTITY 亲读、VERSION_LINEAGE_V2.json、BASELINE_SCOPE_V2、网关 KB）。只画**真实运行依赖**，不以文件数或版本链当设计优点。

## 1. 运行时调用图（一次两臂对照批的真实数据流）

```
benchmark/stories_v2/stage2/<S>/  ──(compiler 已编译)→  benchmark/episodes/<S>.json   [题面，v1 单文件]
        │
tools/exam_batch_configs/<批>.BATCH_CONFIG.json                                        [批配置：arm_map/episode_order/cards]
        │ exam_interleave_plan.emit_plan()
        ▼
<批>.INTERLEAVE_PLAN.json      [attempts 铺排(逐位交错/臂序平衡) + frozen{两臂闭包,题面/卡/评分器/网关/runner hash} + plan_sha]
        │
tools/run_max288_batch.py  ── G1 plan_sha → G2 字段 → G3 verify_frozen_identity → G4 臂身份 → G5 授权预算
        │                          → G6 网关/输出目录 → G7 执行入口 sha        [任一不符 = 零派发]
        ▼
arena/isolation/trusted_controller.IsolatedRun        [可信控制器：候选机制只在不可信宿主进程导入；控制器零候选执行]
   ├─ staging: candidates/<arm>/ → _iso_run_<批>/staged（执行前拷贝，防在位漂移）
   ├─ 每轮：candidate mechanism.init(assets, ep_meta, kb, workdir) → run_turn(turn, gw)
   │         · gw = arena/lb_gateway.py → POST 127.0.0.1:17770/v1 (dsf)   [强制记账 calls.json/attempt 账本]
   │         · 回复文本只当数据（json.loads，不执行不导入——api_candidate.py 边界）
   ├─ 可信侧评分：arena/lb_scorer.py v0.9.2（层1契约/红线；语义归层2另判）
   └─ 落盘：scoring.json / calls.json / manifest / turn_* 目录（断点续跑检查点）
        │
logs/auth_budget/<AUTH>.jsonl                           [attempt_started/completed/failed 逐次记账，cap 硬门]
```

**可见输入边界（事件边界，v1.3）**：候选只见 HER/ME 消息 + IDEA 备注 + intent + 时间戳 + 种子(initial_kb/persona)；gold/评分卡/未来轮永不进候选输入；候选输出（草稿）不写回后续轮已发送消息。

## 2. 状态与记忆（三臂机制对比——"复杂度买来了什么"的结构图）

| 臂 | 记忆状态 | 每轮进请求 | 写回 |
|---|---|---|---|
| **B0** reference_product_v1 | 多层记忆（topic/ongoing/scene 摘要 + 画像，product_memory.py；Kotlin 谱系移植） | 种子块 + 意图块 + 备注块 + 相关记忆投影 + 预算内历史 + 当前输入 + 时间戳 | 摘要/状态由机制维护 |
| **Bmin** v0_minimal_events | 有序真实消息事件表（role\|text\|ts 整条去重） | 同上五块，但历史=原始事件流水（UTF-16 码元 ≤1400 裁剪，当前输入完整保留） | 只追加真实消息；回复=草稿绝不入史 |
| **E3** action_policy_r4_guard_e3 | 证据记忆（事实条目带 source_id/主体/状态）+ 行动策略层 + contract_guard + output_repair(E3 规则) | 同 B0 五块 + 证据投影 | 事实条目带来源写回，主体/状态守卫 |

三者共用：assets/system_prompt 四件（core/format/naturalness_check/redline，各自副本）、同一网关/模型/采样、同一题面/卡/评分器。差异只剩**内部组织**（记忆+行动+守卫+修复）——这正是首筛变量。

## 3. 谱系继承 vs 必要依赖（删减实验的靶点标注）

| 组件 | 谱系来源 | 初步分类（供 W11 消融，非结论） |
|---|---|---|
| 多层记忆/摘要（B0） | Kotlin 原版移植 | **待消融**：Bmin 对照即其删除实验 |
| 选择器 v1.1/v1.2 修复（B0） | cron15 P04-01/P04-04 增强 | 增强基线的一部分（BASELINE_SCOPE_V2 已标注，不算候选收益） |
| E3 行动策略层 | r1–r4 迭代 | **待消融**（W11-01：关行动层 vs 只注入意图句） |
| E3 evidence_memory 主体/成员守卫 | P04-02 修复 + v1.2 种子 | **待消融**（W11-03） |
| E3 _vendor/output_repair（E3 规则：散对象解包+单 mismatched 括号） | F5 E3 修复 | 回放等价 71/72+1 修复——**必要性已有一份证据**，组合贡献待 W11-06 |
| contract_guard | r4 系 | **待消融** |
| 网关别名层/流式计时 | 基础设施 | **固定模型服务，不是 harness 的一部分**（不进比较，不计收益） |

## 4. 身份闭包机制（防"改名当进步"）

- `harness_identity(candidate)` = 候选自有文件 + 父本 superset 闭包 sha256（api_trial 实现）；父本升级会合法改变闭包（v0：94611447→eb35df9a，v5：ef855510→7c6b51ce，先例一致）。
- 每批派发前：`verify_frozen_identity`（闭包+题面+卡+评分器+网关+runner 全文件 hash）+ 授权单绑定 plan_sha/臂身份/执行入口 sha（G1–G7）。
- 当前三臂：B0 `501bc426…`、Bmin `eb35df9a…`、E3 `17fd2160…`（本轮重算+干跑实证）。

## 5. 已知薄弱点（本轮亲历，供 W12/W14）

- 网关上游依赖外部脚手架登录态（案例 61 事故：桌面端自动更新即可全灭 dsf）——**长批前必须探活**（/v1/models + 最小 completions），批内 fail_fast 落账。
- 直连腿版本常量与 CLI 版本耦合（11128 风控）——上游再升级会复发，恢复路径已写知识库。
- 评审批（judge）不在本图内：judge 走独立授权与 cohort（W04-03），与生成分账。
