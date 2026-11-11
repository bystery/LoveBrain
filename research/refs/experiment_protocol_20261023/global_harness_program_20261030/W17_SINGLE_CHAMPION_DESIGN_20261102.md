# W17-01 单冠军合并确认批设计（2026-11-02 冻结，跑前不改）

批号：`BATCH-W17-MERGE-20261102`  授权前缀：`AUTH-W17-*`
臂：parent = `action_policy_r9_slim`（≤24 轮域现役冠军）｜candidate = `window_expand_b_norecap`（>24 轮域现役冠军）
素材：6 篇 wave3 **未暴露** 12 轮标准故事（E189/E190/E195/E199/E207/E208）
规模：6×12×2 = **144 attempts**；评审 72 位 + swap 补判 ≈ 80 调用

## 1. 要回答的问题

双域冠军是否可合并为单冠军？即：在 ≤24 轮已见域，`window_expand_b_norecap`（最小事件窗架构）
能否在不劣于 `action_policy_r9_slim`（r7 系记忆/作废/意图引擎）的前提下承接短剧域。

## 2. 离线关键事实（决定本批的真实标签）

**首版估算（仅文本量）**：64 篇 12 轮故事逐轮累计文本码元峰值 952 CU（E162）、中位 564 CU，
看似全部 < 1400。

**实测修正（含 `m_src[uid] | ts | speaker | text` 行开销，2026-11-02 用
`tools/check_prompt_equivalence.py` 逐轮比对）**：6 篇素材共 72 位中 **71 位
`window_expand_b_norecap` 与 `v0_minimal_events` 的 system+prompt 逐字节相同**；
唯一差异 = **E189_T12**（b 多保留一条旧消息：2307 vs 2220 chars，first_diff@461）。

因此冻结口径改为：

- 扩窗在本域**仅在末轮个别篇触发**（1/72 位），recap（已由 W15N 删除）全程无物可摘；
- 本批**实质仍是最小事件窗架构 vs r9_slim** 的合并可行性判决，扩窗是 ≤1.4% 位的次要差异，
  不得把结果记成"扩窗的功劳"；
- 逐篇等价证据：`logs/W17_EQUIV_E189.json` … `W17_EQUIV_E208.json`（sha256 逐轮在册）。

## 3. 假设与判据（冻结，任何一项不得跑后修改）

**H_MERGE**：在 ≤24 轮已见域，candidate 相对 parent 满足全部门 → 合并为单冠军。

| 门 | 判据 | 说明 |
|---|---|---|
| G1 九维最差维 | ≥ −0.3 | 相对 parent 的配对差；fact/time 维 assistive |
| G2 后半段保持率 | T07–T12 keep ≥ 0.95×parent | 机械分口径；本域无 T25-32，用后半段替代 |
| G3 severe | 亲读后 candidate ≤ parent（无据确定化族不得新增） | B3 主会话亲读，不采信初判 |
| G4 成本 | 如实报告 prompt/token/等待；**不作门** | candidate 预期明显更低（参照 fs1_b −63.6%），仅用于说明交易 |

**判决表（跑前固定）**

| 情形 | 判决 |
|---|---|
| G1–G3 全过 | **MERGE**：单冠军 = `window_expand_b_norecap`；r9_slim 转历史（证据保留） |
| G1 ∈ [−0.5, −0.3) 且 G2/G3 过且成本显著更低 | **不合并**：登记"分区可用"，不得代用户接受质量换资源 |
| G1 < −0.5 或 G2 破 | **维持双冠军**：登记 r9_slim 机制增量价值（短剧域） |
| G3 破 | **维持双冠军 + 登记新故障族**（合并前提是 severe 不升） |
| 任一臂 CALL_FAIL > 10% | 该篇读数降权，不足 5 篇有效则该批判"证据不足"，不重跑 |

- `no_retry / no_makeup`：不补跑到达标；不因单篇结果改判据。
- 臂顺序按 E 编号交错（parent_first / candidate_first 交替），防顺序偏差。
- 延迟读数：本批与其它批并行时**不作为 claims**（共享网关）。

## 4. 素材（全部未暴露、SEALED 来源）

| 篇 | 域 | 标题 | 选它的理由 |
|---|---|---|---|
| E189 | 工作职场 | 内转答辩十二月见 | 长跨度承诺+时间口径 |
| E190 | 学习成长 | 熄灯前填错的科目 | 纠错/核对链（无据确定化的高发形状） |
| E195 | 宠物 | 车库里的冬季猫点 | 第三方边界/状态未定 |
| E199 | 家庭事务 | 换房计划按下暂停键 | 计划变更与搁置（"翻篇"语义） |
| E207 | 餐饮聚会 | 野餐垫上的暴雨预警 | 天气导致的时间/方案变更 |
| E208 | 出行拼车 | 五点四十还是六点整 | 纯时间口径，考记忆与复述精度 |

排除：E117/E122/E125/E131/E140/E166（W13-03）、E127/E128/E140/E179/E184/E205（W13-04）、
E149–E156（CF 孪生）、E061（W14 回归）、全部 LRE（>24 域）。

## 5. 执行清单（按序，缺一不开跑）

1. `tools/export_lr2_episodes.py` 导出 6 篇 → `benchmark/episodes/E1xx.json`（persona 扁平化）
2. `tools/build_stage2_grading_cards.py --manifest-name wave3` 生成 72 卡，零错校验
3. 离线字节核对（§2 末）并记录 sha256 到本文件附注
4. `tools/exam_interleave_plan.py --emit-plan` 生成 `W17.INTERLEAVE_PLAN.json`，复制到
   `refs/experiment_protocol_20261023/long_probe_20261030/W17.INTERLEAVE_PLAN.json`
5. 登记 `AUTHORIZATION_W17.json`（cap=144，冻结 bindings：plan_sha256/cards/两臂 identity）
6. `run_max288_batch.py --mode precheck`（G1–G7 全绿）
7. `--mode live --workers 6`
8. `tools/lb_scorer` 机械分 → `tools/measure_lr2_window_keep.py` 取 T07–T12 keep
9. 九维评审：`run_lp_review_generic.py --tag W17`（72 位，swap 子集 T08/T16/T20/T24→本域为
   T04/T08/T12 按工具默认子集自动对齐，评审工具按位置名匹配，不手改）
10. B3 亲读 severe（全部旗，不抽样）→ 判决 → 同步 TASK_QUEUE/RESEARCH_STATE

## 6. 预期与风险（诚实前置）

- 预期成本：candidate prompt 显著低于 parent（历史 fs1_b 口径 −63.6%）；若质量打平，这是
  双冠军合并之外的第二收益（维护面从两套机制降为一套）。
- 最大风险：短剧域恰恰是 r9_slim 的强项（记忆回扣/纠错/意图路由），最小架构可能在
  memory/factuality/interaction 三维同时掉分；若 G1 破，本批的产出就是"r9_slim 机制在短剧域
  的增量价值"的量化证据，同样算主线进展。
- 不得用"candidate 更省"单独立功：质量门先过，成本只做交易说明。
