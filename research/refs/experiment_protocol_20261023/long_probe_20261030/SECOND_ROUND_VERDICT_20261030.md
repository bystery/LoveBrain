# 第二轮汇总判定报告·修正版（2026-10-30）

> 会话：main-zcode-20261030-0052 | 授权：AUTH-GLOBAL-CONTINUOUS-20261030-01
> **本版替代 2026-10-30 上午版**：severe 臂映射反转 bug 已修正（ERRATUM-SEVERE-SWAP-20261030），
> 九维 dims/winner 结论不受影响，severe 结论方向反转。
> 生成侧：B1 144/144、B2 144/144、C1 96/96 全零失败；评审侧 B1 72位 / B2 72位 / C1 48位全完成。

---

## 〇、一句话结论

**E3 相对 B0 的对话能力优势全面复现（B1 全 9 维 / B2 7 维领先），但它携带系统性的"编造过去事件"严重错误族（9 位 vs B0 3 位），触发第一轮预注册 severe 阻断（修正后 ELIMINATE）；r5_tier（E3 + assertion_tiering）对 B0 直批九维全正、severe 归零、成本更低——冠军候选继位线 B0 → E3 → r5_tier，头对头终裁由 D1 批（E061 双杀原位）完成。**

---

## 一、B1（E3 vs B0，E063/E064/E065，72 位）

| 维度 | n | diff | E3 | B0 | 保护 |
|---|---|---|---|---|---|
| goal | 72 | +0.736 | 2.722 | 1.986 | PASS |
| fact | 71 | +0.146 | 2.535 | 2.389 | |
| attrib | 68 | +0.204 | 2.704 | 2.500 | |
| time | 56 | +0.318 | 2.586 | 2.268 | |
| boundary | 72 | +0.223 | 2.806 | 2.583 | PASS |
| memory | 67 | +0.857 | 2.529 | 1.672 | |
| interact | 72 | +0.903 | 2.806 | 1.903 | |
| init | 72 | +1.028 | 2.667 | 1.639 | PASS |
| emotion | 72 | +0.639 | 2.486 | 1.847 | |

- severe（修正后）：**E3 4 / B0 1**。E3 的 4 个全部是编造过去事件族（断头路旧约/实习标引语/猫箱旧事/毛线箱场景）；B0 的 1 个（E064_T12 编造接送）。B0 另有 E065_T15/T16 机械已证复活被 judge 漏旗标（问句逃逸）。
- Swap agreement: 11/12；机械分 parent 10.0 / cand 9.98。

## 二、B2（E3 vs B0，E066/E067/E068，72 位）

| 维度 | n | diff | E3 | B0 | 保护 |
|---|---|---|---|---|---|
| goal | 72 | +0.569 | 2.611 | 2.042 | PASS |
| fact | 72 | -0.084 | 2.347 | 2.431 | |
| attrib | 68 | -0.044 | 2.471 | 2.515 | |
| time | 58 | +0.147 | 2.475 | 2.328 | |
| boundary | 72 | -0.056 | 2.708 | 2.764 | PASS |
| memory | 69 | +0.681 | 2.507 | 1.826 | |
| interact | 72 | +0.709 | 2.778 | 2.069 | |
| init | 72 | +0.847 | 2.528 | 1.681 | PASS |
| emotion | 72 | +0.473 | 2.542 | 2.069 | |

- severe（修正后）：**E3 5 / B0 2**。B3 抽验 E067_T10：她原话"脑子嗡了一下"，E3 写成"七天前你说'我不知道该不该高兴'"——引语+时间戳双编造，实锤。
- fact/attrib/boundary 微负（均 <0.09）与 E3 的 severe 位分布一致（severe 位相关维判 0 拉低均值），不是独立退化信号。
- Swap agreement: 12/12；B0 另有 E067 两处 json_schema veto、E066_T09 安全错误（judge 漏旗标，B3 已登记）。

## 三、C1 挑战批（r5_tier vs B0，E063/E064，48 位）

> **臂标签勘误**：C1 两臂为 r5_tier（candidate）vs **B0**（parent，reference_product_v1，manifest 已核实），
> 非头对头 E3。本节即第三轮计划的"r5_tier vs 基线直批"；与 E3 的头对头由 D1 批（E061/E062，双杀原位）补齐。
> 与 E3 的横比只作探索性读数（跨批同故事同卡，rubric §3.4）。

| 维度 | n | diff | r5_tier | B0 | 保护 |
|---|---|---|---|---|---|
| goal | 48 | +0.833 | 2.833 | 2.000 | PASS |
| fact | 48 | +0.270 | 2.708 | 2.438 | |
| attrib | 44 | +0.343 | 2.911 | 2.568 | |
| time | 39 | +0.344 | 2.523 | 2.179 | |
| boundary | 48 | +0.250 | 2.958 | 2.708 | PASS |
| memory | 48 | +0.916 | 2.708 | 1.792 | |
| interact | 48 | +1.105 | 2.938 | 1.833 | |
| init | 48 | +1.021 | 2.625 | 1.604 | PASS |
| emotion | 48 | +0.729 | 2.604 | 1.875 | |

- severe：**0/0**。同两篇上 E3 曾犯 2 个编造过去事件 severe（E063_T17/E063_T20，B1 批），r5_tier 零发生（跨批对照，探索性）。
- 与 B1 的 E3-vs-B0 边际横比（探索性）：r5_tier 对 B0 的九维边际在 memory/interact/init 上不低于 E3 对 B0 的边际，goal/fact 边际相当。
- Swap agreement: 7/8；机械分 B0 9.79 / r5_tier 9.93；零旧值复活双臂；r5_tier prompt 更省（318.8K vs 353.0K）。
- **相对基线立功条件满足**：九维全正、severe 零、成本更低。头对头继位判定待 D1。

## 四、综合判定

1. **E3（action_policy_r4_guard_e3）**：对话能力（interact/memory/init/emotion/goal）显著优于 B0 且可复现（跨 5 篇长剧 120 位）；但携带**系统性编造过去事件族**（9 位 judge 旗标 + B3 双抽验实锤），触发第一轮预注册 severe 阻断（修正后 ELIMINATE）。**处置：不作为独立冠军；作为 r5_tier 的父代保留。**
2. **Bmin**：ELIMINATE（severe 3>0 + C1/C2 假设未过）。
3. **r5_tier（action_policy_r5_tier）**：对 B0 直批九维全正、severe 零、成本更低（C1）；对 E3 的头对头继位判定由 D1（E061/E062 双杀原位）终裁。assertion_tiering（EVIDENCED/PROPOSAL/NEEDS_CHECK 写前自检）在九维全正的同时把 severe 压到零，且 prompt 成本更低。
4. **judge 资格补充登记**：severe 映射 bug 已修复；judge 对问句形式复活（E065_T15/T16）与安全错误（E066_T09）有漏检，fact/time/severe 的 B3 亲读通道继续保留。

## 五、下一步（第三轮）

| 项 | 设计 | 状态 |
|---|---|---|
| D1 双杀原位验证 | r5_tier vs E3 on E061/E062（含 E061_T13 安抚压力双杀位），96 calls | 待派发 |
| D1 评审 | 九维 + E061_T13 位专项 B3 亲读（assertion_tiering 是否消除双杀） | 待 D1 |
| r5_tier vs B0 直接批 | E061/E062 或 E065，确立相对基线 | D1 后 |
| 第三轮判定报告 | r5_tier KEEP / E3 ELIMINATE 终裁 | 数据齐后 |

## 六、文件与完整性

- 修正值已回写：`W04-03_FS1_NINEDIM_REVIEW.json`、`LONGPROBE_NINEDIM_REVIEW.json`、`LP_B1RERUN3_NINEDIM_REVIEW.json`、`LP_B2RERUN_NINEDIM_REVIEW.json`（每份含 `severe_reported_buggy` 原值 + erratum 标记，可审计）。
- `W04-03_FIRST_LOOP_VERDICT.json` 已按修正数据由预注册执行器重跑（E3 ELIMINATE / Bmin ELIMINATE）。
- 本文件取代同日早版（该版 severe 结论受 bug 影响；dims 部分仍有效）。
