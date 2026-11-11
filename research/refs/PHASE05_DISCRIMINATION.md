# Phase 0.5 评分器判别力报告

> 生成：arena/lb_phase05_discrimination.py；数据源：logs/phase05/*（echo 替身 220 轮）。

> 结论口径：echo 替身验证的是**协议层判别力**（机制有没有把正确记忆组织进回复链路），
> 模型层判别力（真人味、语义冲突处理）归 judge 校准阶段，本报告不冒认。

## 1. 总分对比（天花板效应修复前后）

| 候选 | 220轮均值 | 探针加权分(×10) | 探针轮均分 | veto | 污染探针 |
|---|---|---|---|---|---|
| phase0_alpha（α 内存全量保留（好记忆/坏抑制）） | 9.99 | 9.91 | 9.64 | 0 | 1/1 |
| phase0_beta（β 文件KV+检索（好记忆/好抑制）） | 10.0 | 10.0 | 10.0 | 0 | 0/1 |
| neg_a_amnesia（负对照A 无长期记忆） | 9.99 | 9.95 | 9.8 | 0 | 0/1 |
| neg_b_dumper（负对照B 全量复述/旧事重提） | 9.99 | 9.91 | 9.64 | 0 | 1/1 |
| neg_c_service（负对照C 客服化人机感） | 6.71 | 6.93 | 7.7 | 0 | 0/1 |

**解读**：220 轮均值对记忆能力差异不敏感（探针密度 5/220 太低，负对照 A 得 9.99 就是证据）；
客服腔负对照被 C13+AI 味词表拉开 3 分+。**探针轮均分**才是记忆能力的判别口径，见 §2 矩阵。

## 2. 逐探针矩阵（判别力核心证据）

| 候选 | GEN_t080 | GEN_t140 | GEN_t160 | GEN_t200 | GEN_t202 | GEN_t203 | GEN_t210 |
|---|---|---|---|---|---|---|---|
| phase0_alpha | FAIL(mem) | PASS | INVALID(泄漏) | PASS | PASS | PASS | PASS |
| phase0_beta | PASS | PASS | INVALID(泄漏) | PASS | PASS | PASS | PASS |
| neg_a_amnesia | PASS | FAIL(mem) | INVALID(泄漏) | PASS | PASS | PASS | PASS |
| neg_b_dumper | FAIL(mem) | PASS | INVALID(泄漏) | PASS | PASS | PASS | PASS |
| neg_c_service | FAIL(style) | FAIL(style) | INVALID(泄漏) | FAIL(style) | FAIL(style) | FAIL(style) | PASS |

探针含义：GEN_t010=软关键词(不计分)；GEN_t080=旧事抑制(水杯)；GEN_t140=远距召回(爬山)；
GEN_t160=召回(九价，**当轮窗口泄漏 → INVALID，测不到记忆**)；GEN_t200/t202=意图对照；
GEN_t205=must_not；GEN_t210=静默轮。

## 3. 判别力结论

- ✅ 召回探针（t140 爬山）把无记忆候选 vs 有记忆候选区分开
- ✅ 污染探针（t080 水杯）把全量复述候选抓出（hits=['stale_kw_0']）
- ✅ 客服腔候选被风格维度拉开（6.71，C13 趋同 + AI 味词表 v0.5.1）
- ⚠️ 总量层判别力受探针密度限制：GEN_220 仅 7 探针/220 轮（且 1 个泄漏失效）。
- 正式题库纪律新增：**探针密度 ≥ 1/10 轮，召回探针关键词不得出现在当轮 HER 消息里**（runner 已自动标 INVALID）。

## 4. 逐维度失分分布（各候选）

**phase0_alpha（α 内存全量保留（好记忆/坏抑制））**：满分轮 219/220；分布 {7: 1, 10: 219}
  失分维度：{'soft': 1, 'stale': 1}
  失败样例：turn_10_GEN_t010 soft_kw_0 miss
  失败样例：turn_80_GEN_t080 stale_kw_0 hits=recommended,directions[0]

**phase0_beta（β 文件KV+检索（好记忆/好抑制））**：满分轮 220/220；分布 {10: 220}
  失分维度：{'soft': 1}
  失败样例：turn_10_GEN_t010 soft_kw_0 miss

**neg_a_amnesia（负对照A 无长期记忆）**：满分轮 219/220；分布 {8: 1, 10: 219}
  失分维度：{'soft': 1, 'must': 1}
  失败样例：turn_10_GEN_t010 soft_kw_0 miss
  失败样例：turn_140_GEN_t140 must_kw_0 

**neg_b_dumper（负对照B 全量复述/旧事重提）**：满分轮 219/220；分布 {7: 1, 10: 219}
  失分维度：{'soft': 1, 'stale': 1}
  失败样例：turn_10_GEN_t010 soft_kw_0 miss
  失败样例：turn_80_GEN_t080 stale_kw_0 hits=recommended,directions[0]

**neg_c_service（负对照C 客服化人机感）**：满分轮 1/220；分布 {6: 212, 7: 7, 10: 1}
  失分维度：{'ai': 219, 'style': 219, 'soft': 1}
  失败样例：turn_01_GEN_t001 ai_flavor recommended:感谢您的分享;recommended:请问还有什么;recommended:为您整理;recommended:可以帮
  失败样例：turn_01_GEN_t001 style_diversity max=0.69 pair=('warm', 'playful')
  失败样例：turn_02_GEN_t002 ai_flavor recommended:感谢您的分享;recommended:请问还有什么;recommended:为您整理;recommended:可以帮

## 5. 归因声明

- 本报告全部 220 轮成绩来自 **echo 测试替身**（manifest.adapter=echo）：验证协议层（存储→检索→注入→回复→写回），
**不代表回复质量**。模型层质量证据另见 V7 对拍（openai_compat，dsf 真模型）与后续 judge 校准。
- mock 替身成绩（SMOKE 题 10.0）同理。
- AI 味词表 v0.5.1 新增客服腔 9 词，系 neg_c 实测漏判后修复（修复动作本身即判别力测试的产出）。
