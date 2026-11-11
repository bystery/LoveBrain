# W15 消融设计：window_expand_b_norecap（删除 recap delta ②，2026-11-02，判据跑前冻结）

日期：2026-11-02 ｜ 动机：主线"删除/组合——检验复杂度是否值得"（W11-06 精神）
基座：window_expand_b（>24 轮域冠军）｜ 变异：单变量删除 delta ②（确定性摘取式 recap）

## 1. 离线证据（消融的立案依据）

LD1 归档四篇 32–40 轮长剧、冠军 b 臂全部 152 轮 mechanism_notes 实测 recap 出线：

| 故事 | recap 非零轮 | 最大 recap_cu / 行 |
|---|---|---|
| LRE117（40轮） | 仅 T34–T40（7 轮） | 199 CU / 8 行 |
| LRE125（40轮） | 仅 T38–T40（3 轮） | 178 CU / 3 行 |
| LRE141（40轮） | 仅 T38–T40（3 轮） | 57 CU / 2 行 |
| LRE147（32轮） | **全程 0 轮** | — |

**T25-32 判决段 recap 全空** → 该段 norecap 与 b 的 prompt 逐字节相同；b 的 1.0 真实保持
来自 delta ① 扩窗。recap 全程承载 ≤199 CU ≈ 窗口的 ~5%。

## 2. 假设（可证伪）

**H_NORECAP**：在 3600 CU 扩窗装配下，recap 段无独立质量贡献——删除后
T25-32 真实保持 ≥0.95×父本、九维最差维 ≥−0.3、severe 总账不升（亲读）。
成立 → 冠军简化（少一段装配逻辑 + 少一份 ledger_rules 依赖文件）。
不成立（任一门破）→ recap 保留，登记"低频但有用"证据。

## 3. 批设计（判据冻结）

- 消融的有效面只在 recap 活跃轮（T34+），故选 recap 承载最重的故事：
  - 主判：LRE117（40 轮，recap 7 轮活跃，EXPOSED 复用）
  - 加判：W14J 消费后 recap 活跃度最高的 1 篇（从 LRE121/127/145/146 里按
    mechanism_notes 实测挑，判据同）
- 臂：window_expand_b_norecap（candidate） vs window_expand_b（parent）。
- 规模：2 × 40 轮 × 2 臂 = 160 attempts；cap 176。
- 测量：T25-32 keep（机械 G-3 + miss 亲读分类）+ 九维评审（另册）+ severe 亲读账。
- 注意：两臂在 recap 空轮输入逐字节相同 → 差异只能来自 recap 活跃轮与采样噪声；
  结论以 recap 活跃轮的 miss/severe 逐位亲读为准，不按全篇均分宣布。

## 4. 状态

设计冻结；候选已实现（candidates/window_expand_b_norecap/，import 通过）。
派发排 W14J、W13-03 之后；LRE117 EXPOSED 复用，第二篇在 W14J 收批后定。
