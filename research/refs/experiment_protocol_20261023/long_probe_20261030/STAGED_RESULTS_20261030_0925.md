# 长剧探针·阶段性成果报告（2026-10-30 09:25）

> 会话：main-zcode-20261030-0052 | 授权：AUTH-GLOBAL-CONTINUOUS-20261030-01
> 保存时点：用户要求快速保存阶段性成果（时间快没了）

---

## 一、已完成且落盘的成果

### 1. 第一轮判定（E061/E062，96 calls）
- **E3 = KEEP**：九维全部非负，diff +0.21 到 +0.95；严重错误 0/2（candidate zero, parent zero）；程序分 10.0 vs 9.38；prompt -18.5%，p50 -16.2%，completion +34%。
- 更正链：candidate 零旧值复活；parent 在 E065 有真实复活（T15/T16 租金回退至 3800 并写入合同）。
- 出窗探针召回：candidate 显著更深。
- 报告：`LONGPROBE_VERDICT_20261030.md` + `LONGPROBE_NINEDIM_REVIEW.json` + `LONGPROBE_SCORE_MECHANICAL.json`

### 2. B1 全量生成（E063/E064/E065，144 calls）
- 144/144 完成，0 失败。程序分 parent 10.0 / candidate 9.98。
- 机械评分：`LONGPROBE_SCORE_MECHANICAL_E063_E064_E065.json`
- 证据读档：`B1_EVIDENCE_READ_20261030.md`
  - E065 parent 真实复活 ×2（T15/T16 租金回退 3800 并写入合同）
  - candidate 零复活，且捕获脚本算术错误（15200 vs 14600）
  - E-MAT-E065-T21 已登记（素材缺陷，不计胜负）

### 3. B2 全量生成（E066/E067/E068，144 calls）
- 144/144 完成，0 失败。程序分 candidate 10.0 / parent 9.4。
- 机械评分：`LONGPROBE_SCORE_MECHANICAL_E066_E067_E068.json`
  - E066 parent revival 3 / candidate 1
  - E067/E068 零复活（双臂）
  - parent E067 有 2 个 veto cells（json_schema_broken）
- 证据读档（部分）：E066_T09 parent 给出错误安全保证（链条尺寸相关，与卡面及后续交换矛盾）

### 4. B1 九维评审（PARTIAL）
- 72 main judgments + 2 supplements 完成，budget cap 在 74/84 时命中。
- 九维分数丢失（仅内存中，脚本崩前未写盘），但**方向数据已从 stdout log 恢复**。
- 恢复结果：`LP_B1_NINEDIM_REVIEW_PARTIAL.json`
  - main: candidate 52/60 (86.7%), parent 7/60 (11.7%), TIE 1/60 (1.7%)
  - swap: candidate 8, parent 4
  - 逐故事：E063 c16/p4；E064 c19/TIE1；E065 c17/p3
  - **E3 在三个长剧中方向一致且压倒性领先**
- 待重跑：cap=86（72 main + 12 supplements + 2 precheck）

---

## 二、正在进行

### C1 冠军挑战批（E3 vs r5_tier assertion_tiering，96 calls）
- 状态：DISPATCHING（已启动 3，完成 2）
- 冠军：E3 (action_policy_r4_guard_e3)
- 挑战者：r5_tier (action_policy_r5_tier)
- 故事：E063/E064
- 问题：r5_tier 的 assertion_tiering（EVIDENCED/PROPOSAL/NEEDS_CHECK 分层注入 system prompt）能否消除 E061_T13 型无据确定化双杀？
- Auth 修复：AUTHORIZATION_C1.json 中 candidate 名称从 r4_guard_e3 修正为 r5_tier（identity sha256 ceb8008f... 不变）

---

## 三、尚未开始

| 项 | 状态 | 前置 |
|---|---|---|
| B1 评审重跑（完整九维分） | 需 cap=86 重跑 | 无阻塞 |
| B2 九维评审 | 需独立 scope 账本 | 无阻塞 |
| C1 九维评审 | | C1 生成完成 |
| 第二轮汇总判定报告 | | 全部评审完成 |
| r5_tier 立功判定 | | C1 评审完成 |

---

## 四、关键发现汇总

1. **E3 稳定胜出**：跨 E061-E065 五个长剧（120 turns），E3 在方向上持续领先（86.7% main judgments）。更正链遵守：candidate 零旧值复活，parent 有真实复活。
2. **无据确定化双杀**（E061_T13 型）：两臂均把未知当确定说，归因于安抚压力位。→ r5_tier assertion_tiering 已作为挑战者进入 C1 批。
3. **素材缺陷处理**：3 个已登记（E021 时钟矛盾、E062 快门数类别错、E065 算术自相矛盾），全部裁定为不计胜负。
4. **Parent 安全错误**：E066_T09 parent 给出错误安全保证；E067 parent 2 个 veto cells。
5. **Budget cap bug**：B1 评审脚本 cap 设为 74，实际需要 84（72 main + 12 supplements）。方向数据已恢复，九维分待重跑。

---

## 五、文件清单（已落盘）

```
refs/experiment_protocol_20261023/long_probe_20261030/
├── LONGPROBE_VERDICT_20261030.md              # 第一轮判定
├── LONGPROBE_NINEDIM_REVIEW.json              # E061/E062 九维（56判）
├── LONGPROBE_SCORE_MECHANICAL.json            # E061/E062 机械分
├── LONGPROBE_SCORE_MECHANICAL_E063_E064_E065.json  # B1 机械分
├── LONGPROBE_SCORE_MECHANICAL_E066_E067_E068.json  # B2 机械分
├── B1_EVIDENCE_READ_20261030.md               # B1 证据读档
├── LP_B1_NINEDIM_REVIEW_PARTIAL.json          # B1 评审方向数据（恢复）
├── AUTHORIZATION_B1/B2/C1.json                # 授权单
└── LONGPROBE_*_PREREGISTRATION.json           # 预登记

refs/experiment_protocol_20261023/global_harness_program_20261030/
└── RESEARCH_STATE.json                        # 研究状态（已更新）
```

---

*下次续作入口：检查 C1 dispatch 完成 → 重跑 B1 评审(cap=86) → B2 评审 → C1 评审 → 第二轮汇总判定*
