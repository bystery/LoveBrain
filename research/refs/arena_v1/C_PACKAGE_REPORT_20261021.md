# ARENA V1 · C 包收口报告（题库/划分/评分/人工评审）

日期：2026-10-21。依据：`refs/ARENA_V1_CLOSEOUT_GUIDE_20261021.md` §5。
**本文件是执行登记，不是验收通过书。** 各门禁状态为 candidate-PASS，
最终以 verify_arena_v1（E 包）实测为准。

## 交付物清单

| 项 | 文件 | 说明 |
|---|---|---|
| C1/C2 覆盖矩阵 | `benchmark/releases/arena_v1/coverage_matrix.json` / `.md` | 12 能力族 × 36 观察点（应成功+对照成对）；逐点反查到真实 case_id，missing=0 |
| C2 字段审计 | 同上 `c2_fields_audit` | 19 个 episode 实测：全局身份唯一/时钟或 ts/may_decline 显式/媒体文件存在/隐藏字段分键；issues=0 |
| C3 划分登记 | `benchmark/releases/arena_v1/split_manifest.json` | 5 家族（dev_stories / anchors_split / real66_evidence / selection_set / b_line_sealed）× 31 文件散列，missing=0 |
| C3 封存接口 | `refs/arena_v1/seal_fixture/` + `tools/verify_seal_fixture.py` | 合成 canary 夹具三重校验 PASS（LOCK==payload / 身份 / 真实B线零读取） |
| C5 评审闭环 | `tools/review_loop.py`（gen/ingest/aggregate/lookup）+ `review_drill_spec.json` | 见下 |
| C5 演练产物 | `benchmark/releases/arena_v1/review_loop/`（materials×12 / forms / entries / mapping_secret / ingest_result / review_summary） | 12 份离线演练 |
| C4/C6 口径 | 覆盖矩阵 F12 + `review_summary.json` | 控制样本分层；汇总层见下 |

## C1 覆盖矩阵要点

- 36 观察点全部落到真实节点（全局身份 `episode:case`，PHASE05 系 case_id 跨文件重名
  已改用全局身份定位）；同一节点多标签标 `node_reuse_of`，不当独立样本。
- 自然连续故事（A_ch01 20 轮、PHASE05、PILOT、NCA/NCB、L3B、dev 4 条）与
  高密度机械反例（PHASE0_GEN_220 220 轮、SMOKE 陷阱）分开统计。
- 每族均含应成功 + 不应误杀/误用对照（如 F9：G7812 原样保留=契约 / 旧车次召回不误杀）。

## C3 划分登记要点（§5-C3）

- dev 集 = exposed（参加校准/调参，可持续开发诊断）；anchors = val6 明确降级为
  "留出回归子集，非独立隐藏集"（SPLIT_v1 原文引用）。
- **selection_set = EXPOSED / NOT_READY**：无独立出题/保管身份。缺口如实登记：
  ①无独立第三方出题者 ②无隔离冻结手续。最小执行方案（≥2 故事 × 8–12 请求位）
  已写入 notes，**不自动替代原选择 40 / B线 80 口径**，待用户裁定。
- b_line_sealed = NOT_CREATED（本仓库无真实 B 线内容），启封接口由合成夹具验证。
- 演练后 worker 已看过全部 12 份演练题面 → 这些题**不得打"未见验证"标签**；
  演练材料仅用于流程验证，不进入任何质量统计。

## C5 人工评审闭环演练（G07）

工具链四命令全部实测跑通：

| 环节 | 结果 |
|---|---|
| gen（材料生成） | 12 份：冻结题面+真实历史+本轮消息+意图+有效许可+原样输出+匿名顺序+绑定散列；映射另存 `mapping_secret.json`，材料零臂名零分数 |
| ingest（导入校验） | accepted=11 / rejected=1（entry_06 缺 reviewer 身份被拒，拒因落盘） |
| aggregate（汇总） | total=12（分母不消失）/ 已审=11 / 争议未审=1 / style_preference_tags=1 |
| lookup（反查） | MAT_08 乙 → arm_y 原文"我看过你的备注…"，映射与原文一致 |

- 12 份状态覆盖：正常×2 / 正常_带缺陷 / 解析失败 / 双方都不合格 / 信息不足 /
  不回更合适 / 备注泄露 / 旧值复活 / 编号改写 / 媒体不可见装看过 / 跨日事实答错 / 缺标签。
- 缺陷逐条指向原文字段+证据；1 条按规则标 `style_preference`（口癖偏油，非事实错误）。
- 全部录入 reviewer=fixture，**不伪造用户投票**；人工确认严重语义错误可阻止晋级，
  但保留 reviewer 身份/依据/规则版本，不冒充程序 veto。

## C6 汇总口径落实

`review_summary.json` 分层：process_layers（全部尝试/完成/解析失败/格式失败）与
manual_layers（已审/甲/乙/相当/两者都不合格/不回更合适/信息不足/争议未审）分列；
defect_fields 按原文字段聚合；无样本写 N/A；失败不退出主分母；演练标签 fixture。

## 门禁状态（candidate-PASS，待 verify 实测）

- **G05** 覆盖可核对：coverage_matrix 12 族 36 点 0 缺失。
- **G06a** 划分/暴露/封存接口：split_manifest + seal_fixture PASS。
- **G06b** 独立选择内容与保管：**NOT_READY**（外部缺件，见上）。
- **G07** 人工评审闭环：工具链 + 12 材料演练完成。

## 遗留与待用户裁定

1. G06b：指定独立保管身份，或批准最小执行方案（≥2 故事 × 8–12 请求位）——
   二选一后冻结；未裁定前整体状态上限 = ENGINEERING_READY。
2. 演练题面暴露给 worker 后的标签纪律已登记（不得称"未见验证"）。
3. C 包关闭标准逐条对照：范围可核对✓ / 节点审查登记✓ / 版本与曝光记录✓ /
   选择保管=NOT_READY（缺外部交付）/ 人工流程演练✓ / 报告可回溯✓。
