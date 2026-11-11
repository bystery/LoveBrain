# W02-03 基线固定裁决（2026-10-30，main-zcode-20261030-0052）

## 裁决

1. **B0 = `reference_product_v1`，当前闭包 `501bc426977b…`**（与 BASELINE_SCOPE_V2 §6 声明精确一致，本轮重算验证）。性质标注：**repaired v1.2 增强基线**（选择器 v1.1 修复 + ADJUSTED 适配 v1.2）——它是唯一有真实跑批成绩的可执行"原版侧"参照；报告与预登记中必须携带"已修复/已增强"标签，不得写成原生原样。
2. **`reference_faithful`（7db3a371…）维持 NR**：仅作"哪些行为是原生局限"的归因参照（BASELINE_SCOPE_V2 §3 Kotlin 证据），不为首筛实跑（避免把调用花在已知有缺陷的移植体上；如后续需要"原生缺陷上限"证据，另立 scope）。
3. **Bmin = `v0_minimal_events`，当前闭包 `eb35df9a6cd6…`**。10-24 冻结的 `94611447…` 是旧父本（6e65b6bc）闭包；本轮验证：v0 自有文件无编辑痕迹（.bak=none）、文件数 17=17 一致、干跑 PASS——闭包变化完全由父本 10-27 升级解释（与 v5 先例同机制）。**新批次冻结按当前闭包重钉**（W10-01 执行）。
4. **主候选臂 = `action_policy_r4_guard_e3`，`17fd2160…` 与 F5 冻结逐字节一致**（本轮 check 复验 IDENTICAL）。
5. **共同输出契约**：B0 与 Bmin params 的 `output_contract` 均为 `null`（legacy 契约），契约对齐成立；E3 同为 null。不削弱 B0（资产/机制零改动，本轮全部只读+日志目录临时文件已清理）。

## 可执行入口验证（零真实调用，mock 网关，真实故事 E021）

三臂同一装载入口全部 PASS（`W02-03_BASELINE_ENTRY_CHECK_20261030.json`）：

| 臂 | 当前身份 | 干跑 |
|---|---|---|
| reference_product_v1 | `501bc426…`（=BASELINE_SCOPE_V2 声明） | PASS：T01 当前输入进 prompt、T02 历史回显、种子在请求内 |
| v0_minimal_events | `eb35df9a…`（自有文件未动，父本升级解释） | PASS（同上三项） |
| action_policy_r4_guard_e3 | `17fd2160…`（=F5 冻结 IDENTICAL） | PASS（同上三项） |

## 对后续的直接输入

- W10-01 预登记直接引用本裁决的三身份 + 契约对齐结论；派发前仍须按 §5 冻结规则走 `verify_frozen_identity`（cards/评分器/执行器/授权单绑定）。
- 首筛三臂 = B0 + Bmin + E3（≤4 臂上限内），主比较=各臂 vs B0 的九维配对 + 严重错误清单；Bmin 的"复杂度买来了什么"问题按其 MECHANISM 预登记假设 C1–C3 判读。
