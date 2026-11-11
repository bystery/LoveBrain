# Phase 0 验收报告

> 日期：2026-10-17。任务书：用户《LoveBrain 斗兽场 — Phase 0 架构核验与改造任务》。
> 前置文档：refs/AUDIT_PHASE0.md（审计表）、refs/REFACTOR_PLAN_PHASE0.md（改造计划）。
> 纪律：每项结论附可运行命令与日志路径；无运行证据的能力不标注"已完成"。

## 一、改造清单（代码变更与版本）

| 文件 | 变更 | 说明 |
|---|---|---|
| `arena/lb_gateway.py` | **新增** | 统一模型网关 + 三适配器（mock/openai_compat/echo）。候选经网关自主 N 次调模型，每次调用强制记账（tag/延迟/token/完整入参落盘） |
| `arena/lb_runner.py` | **重写 v2** | Harness 生命周期 v2（init/run_turn/snapshot/restore，v1 三函数兼容回退）；消毒边界（gold/台本剥除）；checkpoint + --resume/--stop-after；轮型协议 turn_type |
| `arena/lb_scorer.py` | v0.5 | may_decline 契约（静默轮）；soft_keywords_any（0 分顾问检查，堵关键词人机化激励）；expect_decline 断言 |
| `arena/lb_assembly.py` | 意图兼容 | build_intent_block 接受 str（题库自由文本意图）与 dict（App 形态）——修复审计能力 5 崩溃点 |
| `arena/lb_epgen.py` | **新增** | 长程 episode 生成器（探针台本化） |
| `arena/lb_verify_phase0.py` | **新增** | 验收断言器（V2-V6 全自动，证据取自落盘文件） |
| `candidates/phase0_alpha/` | **新增** | 候选 α：dict/JSON 存储 + 单阶段调用 + 全量保留记忆 |
| `candidates/phase0_beta/` | **新增** | 候选 β：文件型 KV 存储 + LCS 检索注入 + 两阶段网关编排（memory→main） |
| `AGENTS.md` | v1.3 | §4 新增 B 线封存条款 + 事件边界条款 |
| `benchmark/b_line/` | **新增** | 封存目录骨架（题库内容按用户令暂缓未生产） |

## 二、验收矩阵结果

### V1 两种内部存储与检索架构不同的 Harness 均可接入 — **PASS**

```
python arena/lb_runner.py --episode benchmark/episodes/SMOKE_EP_001.json \
  --candidate candidates/phase0_alpha --out logs/phase0/v1_alpha_smoke --adapter mock
python arena/lb_runner.py --episode benchmark/episodes/SMOKE_EP_001.json \
  --candidate candidates/phase0_beta  --out logs/phase0/v1_beta_smoke  --adapter mock
```
α（内存 dict/JSON）与 β（磁盘文件 KV + 检索 + 两阶段）均 4/4 轮跑通，lifecycle=v2。

### V2 200 轮以上连续 Episode — **PASS（220 轮）**

α：220/220 complete，mean 9.99，veto 0（logs/phase0/alpha_full/episode_manifest.json）。
β：220/220 complete，mean 10.00，veto 0（logs/phase0/beta_full/episode_manifest.json）。
空记忆启动（initial_kb={}），每轮窗口仅最近 6 条消息——长程信息全靠机制记忆承载。

### V3 远距召回 + 事实更新 — **PASS（且考场具有架构判别力）**

| 探针 | α（全量保留） | β（检索式） | 说明 |
|---|---|---|---|
| t160 召回：150 轮前"九价"事实 | PASS | PASS | 两架构都把远距事实注入了 prompt |
| t140 更新：爬山取消被记住 | PASS | PASS | 事实更新链路（写入→覆盖→再注入）通 |
| t080 抑制：水杯旧事不得重提 | **FAIL** | PASS | α 全量注入旧事→污染被抓；β 只注入相关记忆 |

α 的 FAIL 是**判别力证据而非故障**：同一考场把"全量保留 vs 检索式"两种记忆架构真实区分开（9.99+污染1/1 vs 10.00+污染0/1）。这正是 Phase 0 要的考场能力。

### V4 意图对照（同历史 × 调情/普通朋友/保持克制/无意图） — **PASS**

t200 调情意图→调情信号命中；t202 克制意图→克制信号命中；t203 无意图→调情词零命中；t210 静默轮"不建议发送"（may_decline 契约）被正确接受。意图 str/dict 双形态在装配层与评分层均已打通。

### V5 Checkpoint 恢复 — **PASS（字节级等价）**

```
# 中断：--stop-after 100 → checkpoint.bin（β 文件型存储快照）
# 续跑：--resume → resumed_from=100, 220/220 complete
python arena/lb_verify_phase0.py --episode ... --run logs/phase0/beta_full --run2 logs/phase0/beta_resume
```
断言：续跑与全程运行 220 轮逐轮 reply_raw.txt + scoring.json **字节一致**。真实失败案例：无（首次即过）。

### V6 事件边界不污染 — **PASS**

- V6a：第 i 轮候选 recommended 文本在所有 >i 轮网关入参中零出现（无冒充已发送）。
- V6b：7 个 gold 标记字段名（stale_fact_must_not/must_keywords_any/mock_response 等）在全部 440 次网关调用的完整入参（calls.json 落盘）中零出现。
- 证据：arena/lb_verify_phase0.py 自动断言 + 每轮 calls.json 全量落盘可复核。

### V7 同模型基线对拍 — **PASS**

同模型（本地 dsf 端点）同 episode（A_ch01 20 轮）：

| 候选 | 机制 | mean_score10 | veto | 污染 |
|---|---|---|---|---|
| round_0_baseline（LoveBrain v1 复刻） | v1 默认装配 | 9.10 | 1 | 0/1 |
| phase0_beta（检索式+两阶段） | v2 | **9.59** | **0** | 0/1 |

日志：logs/phase0/duipai_baseline/、logs/phase0/duipai_beta/。同模型条件下候选 β 优于原版基线——机制增益与模型增益开始解耦。

## 三、能力终判（对照审计表 8 项）

| # | 能力 | 改造前 | 改造后 | 证据 |
|---|---|---|---|---|
| 1 | 存储结构自由 | C | **A** | β 文件 KV 接入 + 快照字节等价恢复（V1/V5） |
| 2 | 记忆策略自主 | B/C | **A** | α 全量保留 vs β 检索式同场竞争并被区分（V3） |
| 3 | 多阶段推理自主 | D | **A** | β 两阶段编排（memory→main）calls.json 可查；网关无调用上限 |
| 4 | 空记忆 200+ 轮 | C | **A** | 220 轮 α/β 双跑通 + checkpoint（V2/V5） |
| 5 | 每轮完整输入 | A/C | **A** | str 意图修复 + 意图对照 4 变体通过（V4） |
| 6 | 泄漏/冒充/共享 | C | **A** | 消毒边界 + V6 双断言 440 次调用零违规 |
| 7 | 关键词人机化 | C | **C→部分** | soft_keywords 分级落地；**judge 层 2 仍 0%（如实声明，未假装完成）** |
| 8 | B 线封存 | D | **框架 A / 内容 D** | 目录+宪法条款就位；80 轮题库内容按用户令暂缓未生产 |

## 四、明确未完成（不假装）

1. **Judge 层 2**：无任何实现。需真人校准样本，属后续阶段。
2. **B 线 80 轮题库**：只有封存框架，无内容（题库生产按用户令暂缓）。
3. **echo 替身局限**：V3/V4 验证的是"机制组织逻辑"（存储→检索→注入→prompt→回复链路），不是模型智商——真实模型质量评估待正式题库 + judge。
4. **大规模剧本生产与自动进化**：按用户令暂缓，Phase 0 通过后解锁。

## 五、复现命令汇总

```bash
PY=python
# 回归（v1 兼容）
$PY arena/lb_runner.py --case benchmark/cases/SMOKE_NOTE_TRAP_001.json --candidate candidates/round_0_baseline --out logs/phase0/reg_single --adapter mock
# V1 双架构
$PY arena/lb_runner.py --episode benchmark/episodes/SMOKE_EP_001.json --candidate candidates/phase0_beta --out logs/phase0/v1_beta_smoke --adapter mock
# V2-V4 长程
$PY arena/lb_epgen.py 220 benchmark/episodes/PHASE0_GEN_220.json
$PY arena/lb_runner.py --episode benchmark/episodes/PHASE0_GEN_220.json --candidate candidates/phase0_beta --out logs/phase0/beta_full --adapter echo
# V5 断点
$PY arena/lb_runner.py --episode ... --candidate candidates/phase0_beta --out logs/phase0/beta_resume --adapter echo --stop-after 100
$PY arena/lb_runner.py --episode ... --candidate candidates/phase0_beta --out logs/phase0/beta_resume --adapter echo --resume
# 全量断言
$PY arena/lb_verify_phase0.py --episode benchmark/episodes/PHASE0_GEN_220.json --run logs/phase0/beta_full --run2 logs/phase0/beta_resume
```
