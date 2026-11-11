# FOUR_ARM_PROTOCOL_V1 · 四组统一对照协议（Phase 1 冻结版）

## 1. 统一任务信息（输入公平性）

四组面对同一聊天任务时的可见输入由 `arena/lb_runner.sanitize_turn` 白名单统一给面
（不是各组自取）：

- **允许**：本轮与此前真实聊天消息（HER/ME，含 ts/image）、用户显式意图或备注、
  `may_decline`（合法不回许可）、当前时间 clock、persona 与 initial_kb（显式可见入口）、
  turn_type/turn_index。
- **禁止（评估侧保留，永不进入候选可见面）**：gold、reference 标准答案、grading cards、
  audit 结论、专题/故障标签、未来消息与未来状态、其他组输出、`directive`（测试替身台本）。
- 实现边界：`TURN_VISIBLE_KEYS`/`INPUT_VISIBLE_KEYS`/`MESSAGE_VISIBLE_KEYS`/
  `EP_META_VISIBLE_KEYS`（lb_runner.py:33-35）按白名单**重组**而非黑名单过滤；
  逐轮 deepcopy 评估侧原题快照，候选对上下文的任何修改不影响评分权威。
- 批级验证：`capture_and_validate` 九项检查（未来轮不可见/eval 侧字段零泄漏/
  DIRECT≡COT 输入等价/哈希固定/状态隔离/密钥零落盘/B 线排除/attempt 一致性）。

## 2. 各组输出与主比较规则

| 组 | 输出 | primary_text 抽取（normalize_v1 固定规则） |
|---|---|---|
| DIRECT/COT | `{"action":"reply"\|"no_reply","recommended":str\|null}` | `recommended` |
| LEGACY/CURRENT | LoveBrain 契约（4 风格+4 方向+analysis，decline 走 no_reply） | `response.recommended`（decline→no_reply） |

- **绝不看备选挑优**：其他风格/方向仅计数（secondary_channels），不参与主比较；
  归一化有反挑拣单测。
- 空串/解析失败/契约失败如实落 `error_type`；拒答与合法不回=独立状态，不算高质量。

## 3. 两种测试模式（指导书 §5.1）

- **模式 A 固定上下文回复**：同位首选回复对照（从模式 B 的同位产物提取做 A 式分析）。
- **模式 B 连续多轮状态**（Phase 1 重点）：每臂从第 1 轮跑到最后一轮；剧本消息固定，
  臂内轮序严格连续；臂间状态完全隔离（独立 sandbox/事件账/输出目录）；
  DIRECT/COT 只累积可见真实聊天历史，不获取未发送候选回复与未来信息。
  **这是脚本化回放，不是真人自由互动——论文必须区分。**

## 4. 运行纪律

- 模式：`--plan`/`--capture`/`--mock` 零网络；仅 `--live` + 授权文件 + 环境变量 Key +
  provider 检查 OK 才允许真实调用。
- attempt=一次真实端点生成请求（含连接冒烟与内部附加调用），`lb_gateway` 派发前硬落账；
  全局计数超授权 cap 即停批，不补跑；`retry_on_quality_failure=false`。
- 顺序执行（并发=1）；臂间轮换 = 组 i 从 episodes[i%n] 起跑（不破坏臂内连续状态）。
- 失败故事记录缺失轮次，不伪造完整结果（本轮 LEGACY×2 剧集即按此处理）。
- 模型固定 `deepseek-flash-2` @ temperature 0.7 / stream false；禁止静默换模型
  （check_provider 三态 OK/BLOCKED/FAIL + 授权文件模型校验）。
- Key：仅环境变量 `LB_API_KEY`；异常输出强制 redact（有单测）；run 目录全文件扫描无落盘。

## 5. 版本绑定

本协议绑定的可复现身份：runner `four_arm_runner_v1`、归一化 `normalize_v1`、
指标 `pilot_metrics_v0`、诊断 `benchmark_v1_gold_diag_v0`、provider 检查
`provider_check_v1`、plan_sha256=`253c2be6e48684c5…`（plan.json 全文）、
候选身份哈希=ARM_REGISTRY.json。任何规则改动必须升版本并声明。
