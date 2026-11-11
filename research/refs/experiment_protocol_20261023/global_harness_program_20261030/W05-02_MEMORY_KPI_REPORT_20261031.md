# W05-02 知识引擎 KPI 实测报告（2026-10-31）

> 任务：实际测 Capture/Fidelity/Update，不用模型摘要自证摘要。
> 方法：全部复用已完成 5 批（B1/B2/C1/D1/E1，共 24 个 arm-story 运行 × 24 轮 ≈ 576 轮）的
> mechanism_notes.txt 引擎内部计数 + stage4_src 事实表真值，**零模型调用**。
> 工具：tools/measure_memory_kpis.py（双格式解析）｜数据：W05-02_MEMORY_KPI_MEASUREMENT_20261031.json

---

## 1. Update（更正应用）—— 最硬的指标

**更正轮对齐命中（当轮或次轮 updates_applied>0）：22/28 = 78.6%**

| 故事 | 更正数 | 命中 | 批 |
|---|---|---|---|
| E061 | 2 | 2/2 | D1（双臂同） |
| E062 | 2 | 1/2 | D1（双臂同） |
| E063 | 3 | 2/3 | B1/C1 |
| E064 | 3 | 3/3 | B1/C1 |
| E065 | 3 | 2/3 | B1/E1 |
| E066 | 3 | 2/3 | B2/E1 |
| E067 | 3 | 3/3 | B2 |
| E068 | 3 | 3/3 | B2 |

- 6 个未命中窗口可能是延迟应用（>1 轮）或真漏——与机械复活检测的少量真实复活（E065/E066）互洽：**记忆引擎的更正管线约 8 成及时，2 成延迟/漏**，延迟者构成复活风险的机制根源。
- `updates_rejected` 全 24 运行 = **0**：无误杀。

## 2. Guard 质量（入库卫生）

- `invalid_src` / `unsupported_src` 全 16 个 v6 系运行 = **0/0**：未核来源零入库。
- B0 `writeback ok` 190/190 轮（~100%），`recent=1.0`，`scene_injection=1.0`。

## 3. Capture / OverStore（存储活动 vs 事实表）

- capture_ratio（added_f 总和 / msg 源事实数）**1.2–3.07**：引擎每轮活跃入库，量级覆盖事实表。
- overstore_ratio（facts_store_end / 事实数）**1.9–3.92**：终态存储为事实表的 2–4 倍——含场景/派生事实。
- 过存储在语义层未见损（memoryUse 维四轮对 B0 +0.68~+0.86，E3 反而更会用）；但**内容级 Fidelity 无法从计数器判定**。

## 4. Fidelity 内容级 = 登记缺口

记忆态只在进程内，不落盘。内容级判定（存的值对不对）需要带 memory-dump 的 instrumented 批（E3 双臂 × 1 故事 × 24 轮 ≈ 48 calls，新预登记）。当前以三层代理覆盖：①记忆计数曲线 ②九维 memoryUse 语义评审（四轮 5 批）③机械复活检测。

## 5. 结论与交接

1. **引擎卫生面全绿**（guard 0、reject 0、writeback ~100%）。
2. **更正管线 78.6% 及时**——已在九维 time/memory 维与复活检测中有行为映射；改进杠杆在引擎侧更正窗口（当前 1 轮窗口 → 放宽到 2-3 轮回扫），属 W05-03 候选设计输入。
3. **capture/overstore 为活动量指标**，per-fact capture 与内容 fidelity 留待 instrumented 批（登记，不阻塞）。
4. Bmin 对照未纳入（FS1-B 运行为旧仪表格式且已 ELIMINATE，如需对照另补）。

**W05-02 = DONE**（本报告 + JSON + 工具）。下一候选：W05-03（通用事件/事实状态候选，输入=本报告第 2 条更正窗口发现）或 W06-02（意图技能接口）。
