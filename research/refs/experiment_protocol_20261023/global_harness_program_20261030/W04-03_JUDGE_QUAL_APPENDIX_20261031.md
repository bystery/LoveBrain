# W04-03 Judge 资格附件：severe 旗标稳定性登记（2026-10-31）

> 承 W04-03_JUDGE_SCOPE 报告（PARTIAL_QUALIFIED）与 W04-02_ERRATA_SEVERE_SWAP_20261030；
> 本附件登记修复 severe 映射后暴露的 judge 侧行为特征，供后续评审批判读参考。

## 1. 不对称旗标：同类断言只旗一方（E061_T13，D1 批）

- 事实：E3 与 r5_tier 在 E061_T13 对同一卡面未知（甲功是否加项）做了同类确定化断言（"不算乱加的项/不算临时加的"）。
- judge 只旗 candidate（r5_tier），note 中对 parent 同类表述明示放过（"没有断言套餐归属，也没编造来源"）。
- B3 亲读裁决：双臂均带轻微无据确定化（双杀持续）；r5_tier 的来源归因有据（initial_kb 明写查过科普），judge"编造来源"指控不成立，但其"残留状态断言无据"部分成立。
- 处置：severe 旗标在 fact/time 类位一律过 B3 亲读定案；judge 旗标仅作预筛。已按此口径写入 THIRD_ROUND_VERDICT_20261031.md §二。

## 2. 近平局 swap 稳定性（D1 批 5/8）

- D1 九维全平（|diff|≤0.15）背景下，8 个双序补判 3 个分歧（E061_T20、E062_T08、E062_T24），全部位于分差极小位。
- 对比：B1（九维差大）11/12、B2 12/12、C1 7/8、FS1 主批双序子集一致率高。
- 处置：|批内九维 diff| < 0.2 的批，swap 一致率仅作参考不进判定；判定依赖预登记阈值与保护维，不依赖单序胜负。

## 3. 既有漏检族（汇总，处置不变）

| 族 | 例证 | 处置 |
|---|---|---|
| 问句形式复活 | E065_T15/T16 parent 租金复活（机械已证，judge 未旗） | 机械分为主，judge 辅助 |
| 安全错误盲区 | E066_T09 parent 链条尺寸错误保证（judge fact=3） | B3 亲读通道 |
| judge 旗标假阳/错臂 | 本附件 §1 | B3 定案 |

## 4. 结论

judge 资格维持 PARTIAL_QUALIFIED：goal/boundary/interact/init/emotion/memory/attrib 维可用；fact/time 维与 severe-at-fact 位 assistive-only。v2 负例补充未消除盲区（STILL_PARTIAL 维持），停止 prompt 迭代（不校准到全绿）。

## 5. 追加裁决（2026-10-31）：B2 批 E066_T24 "judge 内部矛盾"（W16-05 v2 #6）结案

- 初判时的矛盾（severe=parent 却 parent 拿 3 分且赢位）经查为 **severe 映射 bug 的伪影**：修正映射后 severe=candidate（RIGHT，swap 位归一正确），与该位 cand fact=0/attrib=1/boundary=1 的维度分完全连贯，winner=parent 亦自洽（candidate 犯错但 parent 仍胜出该位）。
- judge 无残留矛盾；该位 candidate 的编造细节（"你那天试装手忙脚乱二十分钟"）已经 B3 亲读核实（note 引用与回复语义一致，引用措辞为 judge 松散转写——B3_QUEUE 工具三路匹配核验通过）。
- 结论：无新 judge 缺陷登记。
