# reference_product_v1 — 冻结原产品主链路参考基线（比较基准，非被测候选）

> **v1.1（2026-10-27，P04-01 选择器修订）**：extract_keywords/extract_event_date 修订——括注整体移除、
> 全年日期先删、月-日加数字边界、动词前缀集去"约"。v1.0 忠实移植的 Kotlin 行为实测把
> "约饭（时间待定）"切成"饭时间待定"、"2026-11-14周六看后摇…"切成"20-14看后摇…"、全日期解析出
> 非法 2026-26-11，致【进行中事项】C4 面板 0/32 注入（QUALITY_FIRST_MAIN_REPORT §5.2）。
> 回归 tools/test_plan_selector_fix.py 8/8 PASS（零调用）；改前备份 reference_chain.py.bak-20261027-0450-cron15-p0401。
> v1.0 身份保留给已收口批历史答卷解释；v1.1 为下一批基线身份（基线变强对所有臂公平）。

> **v1.2（2026-10-27，P04-04 意图闸门修订）**：is_intent_effective 生效状态 = ACTIVE | ADJUSTED。
> v1.0 只认 ACTIVE，P001-P006 的 14/46 位点 status=ADJUSTED（用户调整目标）被判无效 → 目标块未注入，
> 两臂共同输入缺口（mainline_review_20261026 REVIEW §三.1）。修订语义：调整=生效（调整后 text 即当前目标）；
> 关闭终态（CLOSED/COMPLETED/EXPIRED/PAUSED）不松动。回归 tools/test_intent_gate_fix.py 11/11 PASS；
> 改前备份 reference_chain.py.bak-20261027-0505-cron15-p0404。Kotlin 侧（D:/LoveBrain-arena-e）同步归 E2 轮。

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
