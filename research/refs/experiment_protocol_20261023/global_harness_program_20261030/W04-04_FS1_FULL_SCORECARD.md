# W04-04 FS1 FULL_SCORECARD（2026-10-30）

| 批 | 候选 | 有效配对 | T解析失败 | P veto | C九维(候选vs B0，均值差) | S严重(候选/parent) | W愿发 |
|---|---|---|---|---|---|---|---|
| FS1_A | E3 | 48 | 0 | 3 | goal 0.5; fact -0.043; attrib 0.065; time 0.0; boundary 0.083; memory 0.532; interact 0.562; init 0.75; emotion 0.479 | 2 / 0（修正后，ERRATUM-SEVERE-SWAP；原 0/2 为映射 bug 值） | UNKNOWN（12 对人工样本待用户，AI 不代投） |
| FS1_B | Bmin | 48 | 0 | 2 | goal 0.625; fact -0.021; attrib 0.104; time 0.302; boundary 0.0; memory 0.886; interact 0.604; init 0.708; emotion 0.562 | 3 / 0（修正后，ERRATUM-SEVERE-SWAP；原 0/3 为映射 bug 值） | UNKNOWN（12 对人工样本待用户，AI 不代投） |

程序分: {"FS1_A": {"parent": 9.38, "candidate": 10.0}, "FS1_B": {"parent": 9.79, "candidate": 9.79}}
prompt token: {"FS1_A": {"parent": 355482, "candidate": 289628}, "FS1_B": {"parent": 355917, "candidate": 129629}}

资格降级处置：fact/time 维结论以 B3 亲读+程序层红旗为准（W04-03_JUDGE_QUAL_REPORT PARTIAL_QUALIFIED）
---

## 修正补记（2026-10-31）

- S_severe 两行已按 ERRATUM-SEVERE-SWAP-20261030 修正（臂标签互换）：FS1_A E3 2/B0 0（其中 E021_T07 为已裁决素材缺陷位，剔除口径 1/0）；FS1_B Bmin 3/B0 0。原表中 0/2、0/3 为映射 bug 产出的错臂值。
- 判定影响与全量核算见 W04-02_ERRATA_SEVERE_SWAP_20261030.md、FOURTH_ROUND_VERDICT_20261031.md（终裁 E3 复职 KEEP 冠军）。
- JSON 版 S_severe 已同步刷新（S_severe_reported_buggy 保留原值）。
