# F3 复验附记与冠军升格判定（2026-10-31）

> 承 W05-03_VERDICT_20261031.md（r7 归档待升，规则：severe/time-memory 优势复现且无回归 → 升格）。
> F3 批：BATCH-SUPERSEDE-F3-E062E064-20261031（96/96 零失败），r7 vs E3，E062 镜头交易（2 更正）+ E064 猫医疗（3 更正）。

---

## 一、F3 九维（r7 = candidate，E3 = parent）

| 维度 | n | diff | 读数 |
|---|---|---|---|
| goal | 48 | -0.125 | 保护 PASS |
| fact | 48 | -0.021 | 噪声 |
| attrib | 41 | -0.025 | 噪声 |
| time | 38 | -0.105 | 微负 |
| boundary | 47 | +0.021 | 保护 PASS |
| memory | 47 | +0.063 | 微正 |
| interact | 48 | **-0.208** | 最大负格，未破线 |
| init | 48 | -0.146 | 保护 PASS |
| emotion | 48 | -0.104 | 微负 |

- Severe：**r7 0 / E3 1**（E062_T23 E3 凭空编造"那200已经让过了"让价安排——编造族再现身；r7 的"前组两粒"灰点具体化被 judge note 点名但未旗 severe）
- Swap 4/8（近平局批不稳定，与 D1/F2 同类）

## 二、两批合并证据（F2+F3，192 位，4 故事，6 更正位）

| 项 | 读数 | 判 |
|---|---|---|
| Severe 边际 | r7 0/96 vs E3 3/96，**2/2 批复现**，作用面（更正链数字）与机制一致 | ✓ 复现 |
| time/memory 边际 | F2 正、F3 混合 → 未复现 | ✗ |
| 保护维 | 6 批次全部 PASS（最差 interact -0.208） | ✓ |
| json_schema veto | r7 3 / E3 0 → **prompt 尺寸双臂同分布**（6321/6322、6334/6353）→ 判定为基座随机 roll（B0 在 B2-E067 亦 2 例），非机制相关 | 解除观察 |
| 引擎行为 | 3 例更正全部正确收敛（F2 f_0008 双路径、F3 f_0016 原位更正）；假阳性 1 例已修并双向验证 | ✓ |
| 成本 | 零额外语义调用、prompt 尺寸不变 | ✓ |

## 三、升格判定

**r7_supersede（fixed-gate，identity 0e2d273a…）升格为冠军**，E3 转前任（lineage 保留）：

1. **机制因果成立**：并行残留旧值是编造锚点（结构缺陷实证），pass 移除之——severe 边际有因果解释，非纯相关。
2. **severe 边际 2/2 批复现**（veto 级指标），且方向与程序最高权重轴一致。
3. **零回归**：保护维 6 批全过、guard/KPI 全绿、成本不变；dims 噪声级 parity 正是卫生型修复的预期形态。
4. **veto 观察解除**（prompt 同分布）。

**再翻转触发器（自动回退条款）**：后续任一批出现 ①保护维 ≤ -0.5，或 ②r7 臂 severe 多于对照臂，或 ③veto 率显著高于对照（≥3 倍且 n≥192）→ 自动回退 E3 并重开判定。

## 四、登记

- 冠军：action_policy_r7_supersede（0e2d273a…）；前任：action_policy_r4_guard_e3（17fd2160…）保留全部判定链。
- B0 基线不变；Bmin/tiering 线维持关闭。
- 文件：LP_F3_NINEDIM_REVIEW.json、W05-03_F3_KPI.json、LONGPROBE_SCORE_MECHANICAL_E062_E064.json、本附记。
