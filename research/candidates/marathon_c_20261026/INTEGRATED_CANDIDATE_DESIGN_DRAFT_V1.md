# INTEGRATED_CANDIDATE_DESIGN_DRAFT_V1 — 下一代整合候选设计草案（P05 预备，DRAFT）

日期：2026-10-27 06:35。**性质：草案，未实施、未冻结、未调用**；实施与命名裁决归主线 P05
（MAX-288：P05-01..06）。依据=今晚 173 批成绩单（`evaluation/QUALITY_PANEL_SCORECARD_V1.md` +
`analysis v1_1`）+ major 亲读复核（`evaluation/QUALITY_PANEL_MAJOR_REVIEW_V1.md`）+ P04 四件修复。

## 1. 数据支撑的组合假设（每条带出处分母）

| 机制来源 | 证据（首轮，AI 辅助口径） | 组合预期 |
|---|---|---|
| C2 goal-action 层 | primary interaction 3.0 / initiative 2.63 / emotion 2.76（四臂最高）；同批配对 vs 原版 4 胜全在 interaction 相关故事 | interaction/initiative/emotion 保持在 C2 水平 |
| C3 evidence-memory 层 | primary memory_use 2.89 / factuality 2.87；governed 投影未拖 token（六列 3476 vs 原版 7633） | memory_use/factuality 保持 C3 水平 |
| 基线 v1.2 修复 | plan 选择器 0/32→可注入；意图闸门 ADJUSTED=生效（14/46 位点解锁） | 意图/事项轴首次可被真正测试 |
| 契约统一（P04-05） | C2 9/32、C3 6/32 交空的疑似冲突源已消解 | analysis 交空率显著下降（待测） |

正交性论证：C2 改的是**动作决策与自评**（user 侧目标-动作层），C3 改的是**事实存储与投影**
（user 侧记忆层）；两者输入输出不重叠，v5 基座的四资产/历史预算机制两者共用。
**主要干扰风险（如实）**：同一次调用的 analysis 要同时产出动作三字段+facts/tentatives——
注意力挤占可能两头部做差；这正是 P06 对照要测的第一问题，不预写结论。

## 2. 继承图（实施时的逐字节来源）

- 基座：`reference_product_v1` v1.2（含 P04-01 选择器、P04-04 意图闸门）＋ v5 四资产预算骨架。
- 动作层：`goal_action_v1` 的 CAPABILITIES_BLOCK + OUTPUT_CONTRACT（三字段）+ 保守承接规则
  （P03 明确"切/哈哈不明→保守承接"仍保留，但按 C4 复核 §五.5 改为**结合语境的多动作候选**，
  不再把所有模糊反馈锁死为降温）。
- 记忆层：`evidence_memory_v1` **v1.1**（成员硬验证+引用支持度+unverified_src 审计态）+
  facts/tentatives 预算化投影（500/300CU）。
- 契约：system format.md 按 P04-05 模板扩为**单一合并 analysis 契约**（五键：
  feedback_signal/goal_category/action_taken/facts/tentatives），schema 同步；
  新 harness 身份=全部资产+mechanism 哈希重算。
- 新增硬规则（来自 major 复核 §1b/§1c）：亲历/既成断言逐条对历史（写进 core/format 资产），
  与裁判手册 V1_1 §0-7 同源同口径。

## 3. 护栏（直接来自今晚确认的故障族）

1. **无据既成断言族**（C2×1、原版×4、C3×4 major 均属此类或相邻）：机制层 unverified_src 不投影；
   资产层亲历规则；评审层 V1_1 §0-7+§2a。三层同名同源。
2. **主体错换族**（C3 小林案例）：v1.1 支持度检查的已知边界仍在（字面重合可穿过）——
   评审层 FACT_CHECKPOINTS 草案为主判据，机制层不做名字特判。
3. **首选槽触顶偏置**（v5 goal 3.0/C2 interaction 3.0）：下一批判把首选/备选**位置轮换**纳入设计
   （同一内容以首选与备选两种槽位各评一次的子样本），检验裁判槽位偏置。

## 4. 消融开关（P05-06，实施时预登记）

- A：关动作层（analysis 五键→只留 facts/tentatives；能力块不注入）；
- B：关事实投影（facts/tentatives 不入库不投影；动作三字段保留）；
- C：两者全关 = v5 基座行为（回归锚）。
消融不进首轮主对照（128 次四臂：原版/v5/整合候选/谁来第四臂由主线定——建议 C3 保留以测
v1.1 修复的独立增益），作为后续小批定位用。

## 5. 验收判据（实施前登记，禁止事后改）

- 逐维（AI 辅助口径+V1_1 控制）：interaction/initiative/emotion **不低于 C2 首选均分-0.2**；
  memory_use/factuality **不低于 C3 -0.2**；factuality major 数 **≤ v5**（首轮 v5=1/469）；
  analysis 交空率 **≤5%**（C2/C3 首轮为 28%/19%）。
- 同批严格配对 vs 原版与 v5；四臂横比带批次混杂声明。
- 未达标即按 MAX-288 处置：保留/修改/合并/淘汰，不为新版本号晋级。

## 6. 明确不做（本轮边界）

不做产品接入；不做 Android 侧同步（E2 轮）；不新增模型调用（本草案零调用）；
不把"整合=两个机制都保留"当结论——它只是待测假设。
