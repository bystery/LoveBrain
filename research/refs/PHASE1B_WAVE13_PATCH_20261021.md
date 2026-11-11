# Wave13：strict_attempt_pairing 补丁落地 + 执行准入自证 + 66 次调用授权请求

日期：2026-10-21。对象：v11 复核判决 §2/§3（配对函数只验数量未验终态身份，P1 审计漏检）。
**本批零真实模型调用。** 检索/表达/剧本/采样顺序/失败策略/额度零改动——仅按审查方补丁
替换执行器单函数 `_verify_slot_attempts`。

## 1. 补丁落地与散列链

| 件 | SHA256 | 来源 |
|---|---|---|
| 补丁前执行器 | `005493d0deb14d33e435ff918f230024c206cd5dbd2ec0cbf7e8a54721ec643a` | 与审查方基线逐字节一致（落地前实测核对） |
| 补丁文件本身 | `3e54ec28296465f0f4fabc3d5ba3e7b22abf9a39893e3eae04672867251d36bb` | PATCH_METADATA.json |
| **补丁后执行器** | **`2d3e572499b8917f0ab6fe278a72ea871f4f88c3cdfe63984c9e2fa84cc68849`** | **落地后实测精确吻合**（git apply 后 CRLF→LF 归一；语义与审查方逐字节一致） |
| 补丁后 batch_config 记录 | 同上 | dryrun_v4 配置账实测 |

补丁材料已归档：`refs/v11_strict_pairing_patch/`（patch + 10 项验收工具 + PATCH_METADATA）。

## 2. 验收与自证结果（全部通过）

1. **审查方 10 项配对验收**：10/10 通过（valid / wrong_terminal_attempt /
   wrong_terminal_case / terminal_before_start / missing_ids / missing_terminal /
   duplicate_terminal / gateway_failed / extra_start / other_run_ignored）——
   原函数漏掉的 4 个错误用例现全部拒绝。
2. **执行器回归（test_three_arm_executor.py）**：PASS——干净 66 槽恰 66 真实
   started 逐槽配对 verified_complete、网关内部超时停批/重启阻断/恢复禁用/无隐藏
   补跑、occupied 阻断、checkpoint 三反例生成前拒绝、第 67 请求发出前拒绝、
   四类不回分列、adapter/端点漂移拒绝。
3. **全量 13 套回归**：13/13 PASS。
4. **补丁后全量干净演练 `logs/l3b_66_dryrun_v4/`**：66 槽 / 66 started /
   66 completed / 0 failed / 严格配对 66/66 / verified_complete=true；
   batch_config 记录实际执行器散列=补丁后散列、endpoint_env_name=LB_API_BASE。

## 3. 冻结件状态

- 三臂方案 v3（`refs/L3B_THREE_ARM_PLAN_20261020.md`）**原样未动**：补丁只改
  审计校验函数，不改采样顺序、失败策略、题目、候选或额度，未超出 v11 判决边界；
  执行器对方案散列的冻结校验在 dryrun_v4 中验证仍吻合。
- params×3 / expr_audit / 剧本 / 候选：散列未变（13 套回归 + dryrun_v4 配置账）。

## 4. 66 次真实调用授权请求（一次性提交，待用户明确授权）

按 v11 判决 §4 与三臂方案 v3 执行：

- **规模**：三个候选（A1 父 / A2 检索 v2.2 / A3 检索+表达）× 两条聊天
  （NCA 6 位 + NCB 5 位）× 两次重复 = **66 次，硬上限，无重试额度**；
  额度按真实网关 attempt_started 计，失败也消耗。
- **执行**：沿用组序轮换 A1A2A3→A2A3A1→A3A1A2、fail-fast（坏 JSON 留证继续，
  传输/配对失败立即终止且不得换 batch_id 重开补跑）。
- **交付（一次交齐）**：全量质量初审（66 首选逐条 + 备选四族缺陷扫描，失败行留
  在分母）、三层结果并列（调用/格式/合法动作、目标事实与检索轨迹、原样愿发/
  需改/不想发）、6–8 组匿名三臂对照（明显差异/关键纠正/普通闲聊/收尾覆盖）、
  三臂结论（A2 vs A1 事实运用、A3 vs A2 军师口吻/编造/场景、改善是否伴随退化）。
- **边界**：不宣称真人可发送性（用户保留个人语气与愿发裁决）；A1 为开发父版本，
  不作原产品保真基线胜负宣称；正式晋级继续冻结。

## 5. 结论

v11 判决给出的工程准入条件已全部满足：补丁按原样落地、散列精确吻合、10 项验收
全过、回归与全量演练自证通过、实际执行器散列已入配置账。**待用户对 66 次调用
明确授权后即执行固定对照**；下一次交付为真实回复质量结果包。
