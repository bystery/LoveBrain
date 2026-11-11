# C 线：统一输入 / 输出 / 计量接口（差距分析 v1）

日期：2026-11-11。原则（宪章 C3/C4）：**复用已有基础设施，只修必要兼容问题，不重造框架**。

## 1. 已有且直接复用的资产

| 资产 | 路径 | 作用 | 状态 |
| --- | --- | --- | --- |
| 模型网关 | `arena/lb_gateway.py`（11-10 更新，含 429 修复） | OpenAI-compatible 调用、逐调用记账 | ✅ 复用 |
| 四臂运行器 | `tools/benchmark_v1/four_arm_runner.py`（11-10 更新：并发+429重试+交叉调度） | 多候选同题对照、断点续跑 | ✅ 复用 |
| 统一评分器 | `tools/benchmark_v1/universal_scorer.py`（11-09） | 程序层指标；头部已登记轻量臂 `recommended` 键契约问题的处理 | ✅ 复用（唯一口径） |
| 题库/输入 | `benchmark/stories_v2/`（episode.json + GOLD_SIDECAR） | 标准任务输入 | ✅ 复用 |
| 曝光账本 | `exposure.json`（各集）+ `STORY_CUSTODY_LEDGER.json` | 消费纪律 | ✅ 复用 |
| 汇总 | `FOUR_ARM_SUMMARY.json` / `aggregate` 工具 | p50/p95、tokens、成功率汇总 | ✅ 复用 |
| 轻量臂基线 | `tools/benchmark_v1/baseline_candidates/`（direct_baseline、cot_baseline） | DIRECT 对照 | ✅ 复用 |

## 2. 统一输入契约（C1）

episode 轮位输入（候选可见，来自 `episode.json`）：

| 字段 | 说明 | 候选可见 |
| --- | --- | --- |
| `turns[].input.messages` | HER/ME 聊天消息（含本轮） | ✅ |
| `initial_kb` | 初始知识/画像/长期信息 | ✅ |
| `turns[].input.may_decline` | 本轮是否允许不回 | ✅ |
| 意图/note | 题面意图与补充信息（部分题） | ✅（题面内的） |
| 时间戳 | 时序上下文 | ✅ |
| `reference.json` 的 gold / required_memory_atoms / acceptable_actions / 未来轮 | —— | ⛔ 永不 |
| HOLDOUT E401–E520 题面（E401–E423 已曝光者也不得再用） | —— | ⛔ |

**待办 C1-a（下一批，Harness Worker）**：产出字段级白名单表（episode.json 每个字段 → 候选可见/仅评估侧），并写一次性静态泄漏检查脚本核对四个臂与全部 RESEARCH-P1 原型入口；特别核 `initial_kb` 中是否混入仅供出题构造的作者提示（宪章 C1 明确要求区分）。

## 2a. C1-a 交付：字段级白名单表（2026-11-11，随首批原型第 0 级检查一并完成）

候选可见面 = `arena/lb_runner.py` 消毒常量（代码即政策，白名单制不是黑名单制）：

| 层 | 可见键 | 不可见（留评估侧） |
| --- | --- | --- |
| episode 级（EP_META_VISIBLE_KEYS） | `episode_id` `persona` `initial_kb` | gaps/隐藏原因/布景 note 等其余一切 |
| turn 级（TURN_VISIBLE_KEYS） | `case_id` `input` `turn_type` `turn_index` | `gold`/`reference`/`mock_response`/`echo`（测试替身台本）/作者 meta |
| input 层（INPUT_VISIBLE_KEYS） | `messages` `clock` `intent` `may_decline` `note` `kb` | —— |
| message 层（MESSAGE_VISIBLE_KEYS） | `role`(HER/ME) `text` `ts` `image` | 消息内嵌的作者标注（深拷贝剥离） |
| intent 层（INTENT_VISIBLE_KEYS） | `enabled` `text` `status` `expiry` `now` | —— |
| kb 层（KB_VISIBLE_KEYS） | `me` `her` `warmth` `style` `stage` `topic` `topic_age_hours` `hours_since_last_exchange` `lessons` `recent` `scene_chain` `ongoing_plan` | gold 侧键一律不进 |

结构保证：`sanitize_turn` 对 messages/intent/kb 嵌套**重建+深拷贝**（不是黑名单过滤），藏在容器内部的作者元数据被剥掉；每轮 `turn_eval = deepcopy(turn)` 作为评估侧原题快照，评分只读它。

**本批原型入口的静态核查（已完成）**：`prototype_runner.py` 只向候选传 `sanitize_turn()` 产物 + `assets_root`/`sandbox`/`turn_index`；对 mock 冒烟全部 72 份 `calls.json` 报文 grep `affirm_markers|required_memory_atoms|acceptable_actions|private_guidance` = **零命中**。RESEARCH-P1 三原型均无记忆机制，`initial_kb` 仅作为显式可见入口原样进提示（与 direct_baseline 同口径）；"kb 中是否混入出题构造提示"的全库普查仍按原待办在下批做全量扫描。

## 3a. C2-a/C3-a 核验结论（2026-11-11）

- **usage 聚合**：`lb_gateway.ModelGateway.turn_usage()` 对多调用（级联原型）逐调用求和；失败调用 `usage={"source":"error"}` 容错不冒充 0；`has_unknown_usage` 显式标注。mock 72/72 通过，live 以真实 API usage 为准。
- **retries 计量**：429 重试在适配器内部（退避 10/20/40s），每次尝试经 `attempt_started/failed/completed` 事件落账（`gateway_events.jsonl`），attempt 计数含重试——重试成本不入账缺口不存在。
- **RH-B-11 端点探测（2 次真实调用）**：USTC deepseek-flash-2 **前缀缓存能力存在**——同前缀第二次调用 `usage.prompt_tokens_details.cached_tokens = 1024`（首次 0）。RH-B-11「稳定前缀缓存排布」本地可行，进入待实测清单；若走缓存计费口径需以实际 usage 记账（宪章 C3）。
- **normalize 兼容扩展**：`normalize_output.py` 增 `LIGHT_PARSE_ARMS`（加法式，DIRECT/COT/LEGACY/CURRENT 行为逐字节不变），研究原型臂名加入轻量契约解析面；`universal_scorer.product_contract_fields` 对非 harness 臂名自动按轻量层处理，无需改动。

## 3. 统一输出契约（C2）

所有候选（无论内部结构多不同）在运行器层归一为同一条输出记录：

```
{
  "reply": <str|null>,          // null = 合法不回
  "declined": <bool>,
  "status": "ok|error|timeout",
  "usage": {calls, input_tokens, output_tokens, retries},   // 完整链路，非仅最后一次
  "e2e_ms": <int>,              // 用户请求到最终交付
  "candidate_id", "version_hash", "model", "turn_ref"
}
```

- 内部表示自由（旧 JSON 契约、多候选内部票选、反思草稿均可），归一层只取"实际交付给用户的主回复"。
- 评分只看主回复 + 程序层可验证字段；**不得因缺少旧 Harness 专用 JSON 字段判差**（universal_scorer 已按此处理轻量臂）。
- 已有的 `normalize_v1`（每组 preferred 首选）口径继续沿用。

**待办 C2-a**：在 four_arm_runner 归一层确认上述字段对 DIRECT/COT 两条轻量臂完整产出（尤其 `usage` 聚合与 `e2e_ms`），缺失即补（属宪章允许的"必要兼容性修复"）。

## 4. 统一计量契约（C3）

| 项 | 现状 | 差距 |
| --- | --- | --- |
| 逐调用记账 | `calls.json` / `gateway_events.jsonl` 逐轮落盘 | ✅ |
| 完整 Token | calls 聚合可得；FOUR_ARM_SUMMARY 有总量 | 待办：确认 retries 的 token 也入账（重试成本不得漏） |
| 端到端时延 | pilot/selection 已有 p50/p95 口径（如 CURRENT p50 68.4s vs DIRECT 12.2s） | ✅ 沿用同口径 |
| 缓存 | provider 未见 prompt cache | 登记：如未来启用缓存，单独记命中与计费口径（宪章要求） |
| 版本哈希 | ARM_REGISTRY_FROZEN.json 冻结臂身份；manifest.json 逐轮落盘 | 待办：确认 manifest 含候选代码版本哈希，缺则补 |
| Key 安全 | `LB_API_KEY` 环境变量读取；provider_check 逐份拷贝 | ✅ 沿用；Key 未配置时必须硬阻止真实调用 |

## 5. 原型接入方式（C4/C5）

- 每个机制原型 = 独立入口，注册进 `MECHANISM_REGISTRY.md`（版本 ID RH-B-xx 对应 B 线假设）。
- 允许两种实现形态：①`candidates/<rh-*>/`（走既有 mechanism/ 装配接口）；②`tools/benchmark_v1/` 下独立轻量入口（与 direct_baseline 同级）。
- 共享的只有：数据适配、lb_gateway、运行记录、universal_scorer（宪章 C4 中立设施）。
- 快速实验流程按宪章 C6：第 0 级静态检查（含 C1-a 泄漏脚本）→ 第 1 级 smoke_24 → 第 2 级 60 位 → 正式比较不在本阶段。

## 6. 明确不做的事

- 不重写 episode schema、不重建评分器、不动冻结 manifest 与 Gold；
- 不为本阶段低概率错误建庞大修补框架；
- HOLDOUT：本阶段零实验（曝光与停写状态见 `refs/benchmark_v1_20261107/phase3/HOLDOUT_EXPOSURE_MARK_20261111.json`）。
