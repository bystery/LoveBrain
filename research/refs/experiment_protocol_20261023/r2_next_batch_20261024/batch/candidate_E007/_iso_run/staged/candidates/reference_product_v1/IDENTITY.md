# reference_product_v1 — 冻结原产品主链路参考基线（比较基准，非被测候选）

定位：ARENA_V1_CLOSEOUT 工作包 B（G04）。Python 参考移植，逐项引用
D:/LoveBrain Kotlin 原实现行为（文件+行号见 reference_chain.py 函数头）。
它是**比较基准**，不参加竞赛、不被训练、成绩不参与任何晋级。

- 范围：装配/意图/备注/场景/纠正/ongoing/时效常量（见 BASELINE_PARITY.md §2）
  **+ 跨轮记忆闭环**（2026-10-22 补齐：本轮 analysis → 六投影写回 →
  下一轮装配真读；见 BASELINE_PARITY.md §6、product_memory.py 文件头）
- 不纳入：谈心、主动开场、悬浮窗、复制发送 UI、完整发行
- 对拍：tools/test_baseline_parity.py（26 夹具 / 9 多轮）、
  tools/test_baseline_lifecycle.py（12 对逐字节）、
  tools/test_baseline_carryover.py（9 项跨轮闭环）

边界（不得被当成"候选能力"或"已证明优势"）：
- 写回是原产品本来就有的行为，**不是**本基线的改进；缺它才是削弱基线。
- 原产品的已知弱点如实保留：recent 只留最近 2 轮、scene 链 2h/2 条过期即不再注入、
  plan 只认模型报的 ongoing、无长期档案检索。低于这些上限是真实分数，不许补。
- 2026-10-23 基线纠错（不改能力，见 BASELINE_PARITY.md §7）：轮转归档补回对话正文、
  write_file 与 path 的 memory/ 前缀映射统一、消息 id 跨轮唯一。**身份已再次变更**，
  首场 24 位跑在上一身份上，其数字只作故障样本。
