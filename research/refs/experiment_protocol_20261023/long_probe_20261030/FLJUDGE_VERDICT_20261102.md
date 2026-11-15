# FLJUDGE 终判决（2026-11-02）：window_expand_b 于 >24 轮域 KEEP，承接 FK-LR2-01 修复归属

判决批：BATCH-FLJUDGE-LRE060LRE048-20261101 ｜ AUTH-FLJUDGE-20261101-01（cap 168）
plan_sha256=7d1226e9ed63692434901057a1bc25c25f2beedd5b78e573a081dd257c0c74a1（现场重算一致）
臂身份 sha：parent=2099c12f…（r9_slim）/ candidate=e233ce31…（window_expand_b），G1–G7 全过。
预注册判据跑前冻结（W12 设计 §4.2），零重试零补跑。
故事：LRE060(40轮)/LRE048(32轮)，EXPOSED（LR2 已消费，跨代对比口径，不称"未见"）。

## 1. 批执行

144/144 完成、0 失败 0 停批；4 residents、distinct_run_ids=4、双臂状态真实隔离。
usage（calls.json 真实记账）：parent prompt=482,846 / candidate prompt=280,628 →
**candidate −41.9%**；completion 40,544 / 35,390；p50 时延 30.7s / 30.3s、p95 68.0s / 63.3s（持平略优）。
程序分（mean10）双臂 10.0。

## 2. 窗口保持率（tools/measure_lr2_window_keep.py，机械口径）

| 段 | parent LRE060 | cand LRE060 | parent LRE048 | cand LRE048 |
|---|---|---|---|---|
| T1-6 | 1.0 | 1.0 | 1.0 | 1.0 |
| T7-12 | 1.0 | 1.0 | 1.0 | 1.0 |
| T13-18 | 0.832 | 1.0 | 0.865 | 1.0 |
| T19-24 | 0.650 | 1.0 | 0.596 | 1.0 |
| **T25-32** | **0.493** | **1.0** | **0.509** | **1.0** |
| T33-40 | 0.401 | 0.924 | —(终T32) | —(终T32) |

parent 复现 LR2 塌陷形态（T25+ 腰斩）；candidate 全程无塌陷。

## 3. miss 亲读分类（candidate 终态 22 条，全在 LRE060 T33-40；LRE048 0 条）

tally：**equivalent 18 / true_loss 2 / stale_legit 2 / artifact 0**
（logs/longprobe_fljudge_live_20261101/_reports/FLJUDGE_MISS_CLASSIFICATION.json）

- true_loss 2：A17"总指挥"角色指派（recap 仅存"明早八点"时点）；A18 搬家日电梯两遍电话确认事件。
- stale_legit 2：A13（周日早八点二单方案已被 T07/T08 终案取代）；A21（押金违约金风险已闭环）。
- 机械器漏判主因：其搜索范围=末12条滑窗+recap，而多数等价证据位于 3600 CU 完整历史段内模型实际可见——即机械口径系统性低估 candidate。

**真实保持率**：T25-32 段=**1.0**（该段 0 miss）；终态 T40=(150−2)/150=**0.987**。

## 4. 九维评审（run_lp_review_generic.py，AUTH-FLJUDGE-REVIEW-20261101-01，72 判位+swap8+真双序补判12）

diff=candidate−parent：

| 维 | goal | fact | attrib | time | boundary | memory | interact | init | emotion |
|---|---|---|---|---|---|---|---|---|---|
| diff | **−0.194** | +0.111 | +0.029 | +0.031 | **+0.044** | −0.070 | −0.181 | **+0.167** | −0.153 |

- severe：**cand 1 / parent 2**（B3 队列 3 位：parent T29 编造已发生事实 P1、parent T22 无据确定化 P3、cand T27 编造追尾细节 P3）。
- swap 一致 5/8（3 处分歧如实登记；判据未设 swap 门，不作门禁，但评审稳定性弱于 LR2 批的 4/4，见 §6）。

## 5. 冻结判据逐条核对（W12 设计 §4.2）

| 门 | 要求 | 实测 | 判 |
|---|---|---|---|
| KEEP·保持 | T25-32 真实保持 ≥0.85 | 1.0 | ✅ |
| KEEP·保护维 | goal/boundary/init 最差 ≥−0.5 | −0.194/+0.044/+0.167 | ✅ |
| KEEP·severe | 不高于 parent | 1 ≤ 2 | ✅ |
| 淘汰·保持 | 真实保持 <0.80 | 1.0 | 未触发 |
| 淘汰·保护维 | 任一 ≤−0.5 | 无 | 未触发 |
| 淘汰·成本 | prompt > parent+15% | −41.9% | 未触发 |
| 不确定带 | 0.80–0.85 | — | 不适用 |

**判决：KEEP。** window_expand_b（M2 扩窗+确定性逐字摘录，~60 行 delta、零额外调用）在 >24 轮域
同时达成保持满分与成本 −41.9%，无任何淘汰条件触发。**FK-LR2-01（长剧逐字事实塌陷）修复归属
判给 window_expand_b。** r9_slim 在 ≤24 轮域冠军地位不变（回归批已验：最差维 −0.167、
prompt −51.5%、severe 1/1、swap 4/4）。

## 6. 诚实登记（unknowns / 边界）

1. **swap 5/8 偏低**：3 处分歧中 T20/T08/T16 方向不一；九维 diff 幅度普遍在 ±0.2 内，
   语义评审对该批的判别力有限——本判决的支撑主证据是机械保持率（1.0 vs 0.49，量级差距），
   九维只承担"无退化"门槛，不承担"更优"主张。
2. **cand 标记 1 例 severe（T27）经 B3 亲读 rejected**（细节逐条有源属接梗复述，见 §7.1）；
   评审 severe 账以亲读为准修正为双臂 0/0，评审器引文幻觉问题单列 §7.2 改进项。
3. **true_loss 2 原子**均为"事件级细节"（角色指派/电话确认动作），recap 首 clause 摘取规则
   抓不住；若后续要 100%，需 R 规则补"指派/确认动作"信号——属增量优化，不影响本判决。
4. 跨代对比口径：LRE060/LRE048 已 EXPOSED，parent 数字与 LR2 批同源故事，允许但标注；
   候选与 parent 同批同判据，结论不依赖跨批可比性。
5. wave-2 新题材迁移未测：本判决限搬家域两篇；推广到 wave-2/反事实属 W13 轨新批。
6. **勘误（2026-11-02）**：candidate LRE060_T03 曾有程序 veto（illegal_source_id:m_src[1]），
   系评分器契约缺口 FK-SCORE-01（模型引用 prompt 内真实存在的历史标签被误判）；修复后离线
   重评=10.0/veto 清除（SCORER_MSRC_REEVAL_20261102.json）。KEEP 判决不变。

## 7. 下一批主线

1. ~~B3 亲读：LP_FLJUDGE_B3_QUEUE.json 3 位~~ **已完成（LP_FLJUDGE_B3_READRESULT.json）：3/3 rejected**——
   parent T29"编造"均有源（T03 电梯被装修订走、T10 六点半全上楼）且处反事实推演内；parent T22
   "压在你回来的点上"是复述她的未定项非确定化；cand T27 四条细节逐条有源（T04/T08/T10/T03）属接梗复述。
   **实际 severe：双臂 0/0**，§4 的 severe 1/2 为评审误报，KEEP 判决方向不变且更强。
2. **评审基建改进项（新登记）**：九维评审 note 出现引文幻觉（3 例中 2 例 key_quote 不在回复原文内）——
   severe 判定工具链须加"引文必须逐字存在于被评回复"硬校验（b3_queue.py 扩展），未过校验的 note
   降权为线索不入 severe 账。此为评审器问题，不动本题判据。
3. W13-02 反事实轨启用（48 篇 wave-2 原封未动）；新长剧 SEALED 池判别批另行 AUTH。
4. RESEARCH_STATE / TASK_QUEUE 同步本判决；FK-LR2-01 在 KNOWN_FAILURES.md 标注 RESOLVED(cand)
   并保留塌陷事实记录。
