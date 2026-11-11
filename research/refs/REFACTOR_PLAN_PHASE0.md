# Phase 0 架构缺口及改造计划

> 依据：refs/AUDIT_PHASE0.md（同日审计）。目标：把考场从"单次调用的 prompt 装配评测"升级为
> "开放 Harness 生命周期考场"。考场只统一四样东西：外部输入输出、真实事件边界、模型网关、评测协议。
> 内部知识库形态、存储格式、检索算法、推理编排全部下放给候选。

## 设计总则

1. **考场管边界，候选管内部**。考场给候选的只有：本轮真实事件（HER/ME/IDEA/意图/时间）、一个模型网关、上轮快照。候选交回来的只有：最终输出文本（按声明契约）+ 新快照。
2. **兼容不破坏**：v1 三函数接口（init_state/build_prompts/update_state）继续可用——基线对拍需要它。v2 生命周期是超集。
3. **所有新能力必须带可运行验收**（Phase 0 验收矩阵，见 §6）。

## 缺口 → 改造映射

| 缺口（审计 #） | 改造件 | 落点 |
|---|---|---|
| 3 多阶段推理 D | **Harness 生命周期 v2**：候选持有模型网关，自主决定调几次模型、怎么编排 | arena/lb_mechanism2.py + runner |
| 1 存储自由 C | 快照协议 `snapshot()/restore()`，state 可为任意对象（JSON/sqlite/自定义类） | 同上 |
| 4 长程 C | episode 断点续跑 + 200 轮压测 + 状态增量落盘 | runner + 生成器 |
| 5 str 意图崩 C | 装配层意图兼容（str/dict 同权重） | lb_assembly.py |
| 6 gold 敞口 C | adapter 只见消毒后的 case（剥 gold/mock_response） | runner |
| 6 轮型模糊 C | episode 轮型协议：reply（答她）/rewrite（改草稿）/silent（不建议发） | episode schema |
| 7 关键词人机化 C | gold 关键词分级：factual（硬分）vs casual（仅证据不计分，`soft_keywords_any`） | lb_scorer.py + 题库 |
| 7 judge D | 层 2 仍列后续阶段（需题库与真人样本先行），Phase 0 不假装完成 | judge_panel.py 占位不实现 |
| 8 B 线 D | 目录 + 访问纪律落宪法；题库生产暂缓（用户令），只建隔离框架 | benchmark/b_line/ + AGENTS.md |

## 1. Harness 生命周期 v2（arena/lb_mechanism2.py）

```
候选可暴露（v2，全可选，缺省回退 v1）：
  init(assets_root, episode_meta, gateway) -> harness_obj
      gateway: ModelGateway（见 §2），候选可用它自主调模型 N 次
  run_turn(harness, turn_ctx) -> {"reply": str, "calls": [CallLog], "notes": str}
      turn_ctx: 消毒后的本轮输入（事件、意图、时间）——不含 gold
  snapshot(harness) -> bytes          # 任意字节：JSON/sqlite dump/pickle 安全子集
  restore(snapshot_bytes) -> harness  # 快照恢复，支持断点续跑
v1 回退：无 init/run_turn 时，runner 用旧三函数路径（build_prompts→单次网关调用→update_state），
  并自动包一层默认 snapshot（deepcopy）。
```

- 多阶段推理：候选在 run_turn 内可 gateway.generate() 任意次（检索→起草→批判→修订），每次调用记 CallLog（prompt 长度/completion/延迟），runner 汇总进 token 账——**自主编排成为一等能力**。
- 存储自由：harness 是任意 Python 对象；快照是任意 bytes；sqlite/文件型存储由候选在 snapshot 里 dump。
- 隔离：每候选每 episode 独立 harness 实例；恢复测试保证字节等价。

## 2. 模型网关（arena/lb_gateway.py）

- `generate(system, user, images=None, temperature=None) -> {text, usage, latency_ms}`。
- 网关内部持有 adapter（mock/openai_compat），**记账在网关**：每次调用追加 CallLog（时间戳、token、延迟、用途标签候选可传 tag）。
- 候选拿不到 case、拿不到 gold、拿不到未来——只能拿到本轮 turn_ctx。

## 3. Episode 协议 v2（schema + runner）

- 轮型 `turn_type`：`reply`（默认，最后真实事件是 HER/IDEA）/ `rewrite`（最后是 ME 草稿，考改写）/ `silent`（考"不建议发送"，评分允许输出 recommended=null 之外的"不发"信号——契约里加 `may_decline` 开关）。
- 事件显式分类：input.messages 保持 HER/ME/IDEA + `image`；`intent` str/dict 皆可。
- **消毒边界**：runner 在把 turn 交给机制/网关前剥掉 `gold/mock_response`；adapter 不再收原始 case。
- **断点续跑**：每轮末写 `checkpoint.json`（轮指针 + harness 快照 bytes 落 `checkpoint.bin`）；`--resume` 从最近 checkpoint 续跑；恢复后先跑一次等价校验（restore→snapshot 应与存档一致）。
- **意图对照**：episode 支持 `intent_contrast` 组——同一 history 窗口 × [调情/普通朋友/保持克制/无意图] 四个意图变体轮，gold 分意图断言；考"策略是否被输入控制"。
- 200 轮：题库生产暂缓（用户令），验收用**生成器**（arena/lb_epgen.py）产 200+ 轮合成长剧（含远距召回/事实更新/旧事抑制探针），先验证协议承载力。

## 4. 评分协议调整（最小改动）

- gold 新增 `soft_keywords_any`：命中只进 evidence 不计分（给旧 must_keywords 的迁移路径）。
- 契约新增 `may_decline: true` 时，`response.recommended = null` + `decline_reason` 也算合法输出（silent 轮）。
- 其余检查点不动——分级原则已定：硬约束只管客观可验证错误，自然度归层 2（后续阶段做真人校准盲评，Phase 0 不做假实现）。

## 5. B 线隔离框架

- `benchmark/b_line/` 目录 + `.sealed` 标记文件 + AGENTS.md 条款：开发迭代（含所有自动化）禁止读取 b_line 内容；仅最终验收运行。题库内容暂缓生产。

## 6. Phase 0 验收矩阵（每项=可运行命令+日志）

| # | 验收项 | 测试 |
|---|---|---|
| V1 | 两种不同存储架构的 Harness 均接入 | 候选 α（纯 dict/JSON 快照）+ 候选 β（文件型 KV + 多阶段网关调用）跑同一 episode |
| V2 | 200+ 轮连续 Episode | 生成器产 220 轮，mock 全程 |
| V3 | 远距召回 + 事实更新 | 220 轮内埋：t10 事实→t160 召回探针；t60 计划→t100 变更→t140 过期计划不得复活 |
| V4 | 意图对照 | 同 history × 4 意图变体，gold 分意图断言 |
| V5 | Checkpoint 恢复 | 跑到 t100 中断（kill），--resume 续到 t220，全量结果与一口气跑完一致（mock 确定性） |
| V6 | 事件边界不污染 | 断言：候选 reply 不出现在后续轮 prompt；gold 不出现在任何 prompt/网关入参 |
| V7 | 基线对拍 | 同模型（dsf 若在线）跑 round_0_baseline vs 候选 β 同 episode（暂缓到验收日执行，端点不在线则挂起如实报告） |

## 7. 明确不做（Phase 0 边界）

- 不做大规模剧本生产（用户令暂缓）。
- 不做 judge 层 2 实现（需真人样本校准，防止假完成）。
- 不做自动进化/变异轮次（Phase 0 通过后启动）。
