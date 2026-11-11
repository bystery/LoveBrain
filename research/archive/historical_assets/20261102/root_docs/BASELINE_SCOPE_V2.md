# BASELINE_SCOPE_V2 — 本轮“原版”到底是什么

日期：2026-10-27。范围：`D:/lovebrain-arena`。机器可读谱系见 `VERSION_LINEAGE_V2.json`。

## 0. 一句话

旧 C4 的 `6e65b6bc…` 是**历史付费身份**，只能解释旧答卷；当前 `reference_product_v1` 已改成 `501bc426…`（选择器增强 + ADJUSTED 输入适配），**不再等于“原版原样”**。P06 的比较基准是 `501bc426…`，不是 `6e65…`。版本先后不代表能力高低。

## 1. 三类基线必须分开

| 类 | 身份 | 是什么 | 能用来做什么 | 不能用来做什么 |
|---|---|---|---|---|
| `reference_c4_frozen` | `6e65b6bced2c…` | C4 实跑时的旧冻结身份：v1.0 选择器、intent 仅 ACTIVE；9 文件闭包 | 解释 C4 历史答卷与 QUALITY_PANEL_V1/V2 | 不能当作当前代码成绩；不能叫当前“原版” |
| `reference_faithful` | 行为等价 v1.0；当前文档配 04:50 `.bak` 重算为 `7db3a371…` | Kotlin 声明范围内忠实移植：保留原生选择器缺陷、ADJUSTED 不生效 | 说明“保真”的边界与原生局限 | 不能当作已实跑基线（NR） |
| `reference_repaired` | v1.1 `ee05db26…`；当前 v1.2 `501bc426…` | 另行增强/修正：v1.1 修 `extractKeywords`/`extractEventDate`；v1.2 接受 ADJUSTED | 作为 P06 的共同参照；明确标注“已修复/已增强” | 不能回写成原生一直有的能力；不能和 6e65 混用 |

补充：`v5_purpose_aligned` 历史 C4 身份是 `ef855510…`，当前是 `7c6b51ce…`；v5 自身文件没变，但闭包内 reference 从 6e65 变成 501bc4，所以身份必须重算。`v6_goal_context`=`4e9d454a…`，`v6_integrated`=`ac2672ff…`。

## 2. 身份对照（历史 vs 当前）

| 臂 | C4 历史 | 当前 | 关键变化 |
|---|---|---|---|
| reference_product_v1 | `6e65b6bc…` | `501bc426…` | IDENTITY.md 修订；reference_chain v1.1 选择器 + v1.2 ADJUSTED |
| v5_purpose_aligned | `ef855510…` | `7c6b51ce…` | v5 自身未变；父本 reference 变化传入闭包 |
| goal_action_v1 (C2) | `de600ff9…` | `fc296dd8…` | format.md 的 analysis 契约与机制消费一致化；机制代码同 |
| evidence_memory_v1 (C3) | `009d2425…` | `fddcdb51…` | v1.1 成员/引文硬校验 + v1.2 静态种子事实 |
| v6_goal_context | — | `4e9d454a…` | 新目标/动作分支；P06 B |
| v6_integrated | — | `ac2672ff…` | 目标+证据记忆整合；P06 C |

C2/C3 还有**声明闭包缺口**：`params.json` 没有 `parent`，`harness_identity` 只算自身 7 文件；实际 `import` 走 `v5_purpose_aligned → v4_intent_context_r2 → reference_product_v1`，行为闭包是 32 文件。历史实际闭包 hash 分别为 C2 `590209db…`、C3 `afa15b21…`；当前分别为 `01335a56…`、`b1e7737d…`。详见 `VERSION_LINEAGE_V2.json` 的 `declared_vs_actual_closure_gap`。P06 不使用 C2/C3；旧 C4 答卷按历史实际闭包解释，不追溯改分。

## 3. 原版三项争议的 Kotlin 证据与归类

### 3.1 `extractKeywords`

原生 `OngoingContextSelector.kt:266-294`：先删括号字符但保留内容，再用 `\d{1,2}[-/]\d{1,2}` 去日期，没有数字边界。因此“约饭（时间待定）”会留下“饭时间待定”，“2026-11-14…”会被日期规则咬坏。v1.0 逐语义移植，保留同一行为。**结论：原生局限；v1.1 的括注整体移除/全年月日先删是增强基线，不是保真修复。**

### 3.2 `extractEventDate`

原生 `OngoingContextSelector.kt:337-347`：正则 `(\d{1,2})-(\d{1,2})` 直接找；`2026-11-14` 会先命中 `26-11`，产出非法日期。v1.0 同样无数字边界。**结论：原生局限；v1.1 的数字边界是增强修复。**

### 3.3 `isIntentEffective`

原生 `Models.kt:446-447`：`enabled && text.isNotBlank() && status == IntentStatus.ACTIVE`；`IntentStatus` 枚举只有 `ACTIVE/PAUSED/COMPLETED/EXPIRED`，**没有 ADJUSTED**。v1.0 完全一致。实验题面里的 `ADJUSTED` 是输入层的“目标已调整”状态；v1.2 把它视为生效，属于**公共输入适配增强**，不是原生修复，也不能计为任何候选收益。关闭仍按终态（CLOSED/COMPLETED/EXPIRED/PAUSED 不生效）。

## 4. 前台有限范围（不冒充全功能移植）

本基线只覆盖实验需要的前台：系统/用户提示词装配、知识/场景/计划/note/chat、时间戳、byBlocks 裁剪、参考版记忆投影；不覆盖 App 的完整服务、UI、网络、通知等。`reference_faithful` 未直接实跑，标 NR；它的价值是定义“哪些行为是原生就有的、哪些是我们后来修的”。

## 5. 冻结与加载规则

任何新批必须同时冻结并通过 `verify_frozen_identity`：计划 sha、两臂闭包 hash、cards、评分器、执行器、授权单绑定。身份不匹配即拒跑，不用旧批次补配对、不重开已 CLOSED 授权。P06 当前绑定：A `8b10493a…`（ref `501bc426…` vs v5 `7c6b51ce…`）、B `183bc269…`（ref vs v6g `4e9d454a…`）、C `11d7d164…`（ref vs v6i `ac2672ff…`）。

## 6. 结论

“原版”在本轮只能指 **reference_repaired v1.2 `501bc426…`**；它是 P06 的共同参照，但必须标注“已修复/已增强”。历史 `6e65…` 仅用于解释旧答卷。任何把 6e65 的旧成绩贴到 501bc4 上、或把 501bc4 的选择器修复说成“原版本来就会”的说法，都不成立。
