# 大牛审查简报 — LoveBrain 斗兽场 Arena V1（2026-10-22）

> 给审查者：这是一份"考场/评测迭代系统"的全量审查包。你面前的系统已经跑通
> OFFLINE_READY 总验收（EXIT=0），现在请你以最挑剔的眼光找结构性缺陷。
> 权威顺序更正（V14 复核）：**用户明确指令 > 一切**；历次大牛意见是已采纳的
> 技术裁决记录，审查者意见本身不构成高于用户的执行授权。
> 成本口径更正（V14 复核）：质量门禁优先，成本如实记账；用户批准的调用
> 额度是硬上限，"成本唯一排序"的旧表述作废。

## 0. 一句话背景

为 LoveBrain（Android Kotlin 恋爱军师 App）打造的行为型 benchmark：候选 harness
（"军师"）在 200 道上下文关联剧情题上被考，评分只看最终输出行为；候选可自主设计
机制（记忆/检索/意图路由/上下文组织），机制 >>> prompt 调优；质量门禁优先，
成本如实记账（用户批准额度硬上限）。

## 1. 包内容速览

| 目录 | 内容 |
|---|---|
| `arena/` | 评分器 lb_scorer（10检查点+2veto+C10意图契约）、PromptBuilder 字节级复刻、ModelGateway、隔离宿主 host_runtime、可信控制器 trusted_controller（文件级 ACL deny） |
| `benchmark/` | 封板题库 episodes（含 dev/）、口味锚点 anchors/59条、rubric、划分清单 split_manifest、评审演练现场 releases/arena_v1/review_loop |
| `candidates/` | reference_product_v1（冻结基线）、phase0–phase3 候选、_attack_fixture（攻击夹具，故意含越权探针——不要当 bug 报） |
| `tools/` | 全部守卫：verify_arena_v1（总验收）、test_isolation_matrix（18项）、test_scoring_authority（五类篡改）、test_verify_strict_negative（7反例）等 |
| `refs/` | 宪法与设计文档、历次大牛意见落地记录、REAL66 匿名数据（匿名化，**不含**再识别钥匙与原始聊天） |
| `logs/arena_v1_acceptance/20261021_r3closeout_FINAL/` | 正式验收：ACCEPTANCE.json + 报告 + 隔离矩阵日志 |

**未入包**：`dataset/`（原始聊天导出，含隐私）、`refs/REAL66_ANON_KEY_*.md`（再识别
钥匙）、`dist/`（构建产物）、`runs/ isol_runs/`（运行现场）、`*_acl_locked_*`（ACL
事故残骸，26 组锁死副本，事故记录见 KNOWN_FAILURES 与 SCOPE_AND_STATUS）。需要时另给。

## 2. 当前状态（截至 2026-10-22）

- **总验收：OFFLINE_READY，EXIT=0**（logs/arena_v1_acceptance/20261021_r3closeout_FINAL）。
  G01–G05、G06a、G07–G10 全 PASS；干净目录解包后跑**同一个完整 offline 入口**复验通过。
- G06b（独立出题/保管身份）：NOT_READY——外部缺件，需用户指定保管身份。
- G11（真实上游集成）：离线模式 REFUSED；本地 OpenAI 兼容替身走同一条隔离链演练
  已 PASS（tools/test_g11_standin.py），真实批次 ≤8 attempts 待批。
- 真实端到端已验：本地端点（deepseek-v4.1-flash 路由）4 题 ×10.0。

## 3. R3 收口六项加固（2026-10-21 深夜）

1. **G03 真断言**：候选返回值夹带伪造 score/gold/checks（forge_auth）必须被无视、
   不泄漏进评分产物；评分与原题权威评分逐项全量比对。
2. **review_loop rv2**：评审材料内容绑定（篡改展示原文必拒）、重复表单拒/revision
   显式替代、票数/已审材料数/未审数分母分离、episode 顶层 persona/initial_kb 不丢。
3. **coverage**：跨轮时钟倒退、轮内 clock<ts、message.image 媒体通道、布尔失败进 issues。
4. **封存访问实测**：deny 后候选子进程必须留下 `BLOCKED:<异常类型>` 正向证据；
   授权+/reset 后读到且 sha 一致。"B 目录不存在"降级为范围声明，不算隔离证据。
5. **发布包**：干净目录复验升级为同一完整 offline 入口；freeze_release 与打包白名单
   同源（224 文件级冻结）。
6. **G11 替身演练**：真实 HTTP、模型/温度/Bearer/图片 data URL/完整 usage 入账/
   9000 字符原文不截断/attempt 账本 7started7收口。

## 4. 已知真实缺陷与事故（不用你重新发现）

- 零宽空格 U+200B 污染真实模型风格键 → 已被评分正确拒绝，修复方向域无关（NFKC）。
- icacls (R) 掩码含 READ_CONTROL 会锁死 icacls 自身 → 改 (RD)；26 组 `*_acl_locked_*`
  残骸目录无法在线还原，已绕开未删。
- 沙箱批量删除守卫/ACL 操作会让验收子工具假败 → **验收必须非沙箱前台执行**。
- 全部清单见 `refs/KNOWN_FAILURES.md` 与 `refs/arena_v1/SCOPE_AND_STATUS.md` §R3。

## 5. 请你重点裁决的问题

1. **评分体系**：10 检查点+2 veto+C10 意图契约是否足以抓住"她/我/想法三类消息
   混淆"？有没有更高杠杆的行为断言我们漏了？（`arena/lb_scorer.py`、`benchmark/rubric.md`）
2. **机制演化压力**：M1–M5 变异配额 + 帕累托门禁，会不会把候选逼进局部最优？
   200 题上下文关联剧情题的信息量够不够养出"monster"？（`benchmark/episodes/`、
   `refs/arena_v1/BENCHMARK_AND_REVIEW.md`）
3. **隔离架构**：文件级 ACL deny + 可信代理 + 账本，攻防面还有哪些洞？
   （`arena/isolation/`、`tools/test_isolation_matrix.py` 18 项）
4. **口味锚点方法**：59 条用户亲手勾选的真实回复做锚点，"清醒17/俏皮9、中位19字"，
   这个画像怎么进评分而不产生针对性过拟合？（`benchmark/anchors/`、`refs/用户口味画像_v1.md`）
5. **HITL 晋级链**：晋级第 6 步才上人工对齐，时机是否太晚？（`refs/arena_v1/SCOPE_AND_STATUS.md`）

## 6. 快速复核命令（可选）

```bash
# 全量离线总验收（非沙箱前台，约 3-6 分钟；含干净目录嵌套复验）
python -B -X utf8 tools/verify_arena_v1.py --mode offline --release refs/arena_v1/release.json --out logs/_expert_rerun
# 单项
python -B -X utf8 tools/test_isolation_matrix.py
python -B -X utf8 tools/test_scoring_authority.py
```

依赖：Python ≥3.10、Windows（icacls 文件级 deny）。无第三方包依赖。
