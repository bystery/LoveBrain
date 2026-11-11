# W16-05 补齐单 v2（2026-10-30，main-zcode-20261030-0052）

> 承 v1（W16-05_REPLENISH_LIST_20261030_v1.md）；本版按 ERRATUM-SEVERE-SWAP-20261030 与第二轮结果更新。

## 已完成（本轮关闭）

| 项 | 结果 |
|---|---|
| stories v2 长剧 8 篇（E061-E068）192 case 校验 | 全过，0 错误 |
| 长剧全量生成 B1/B2/C1（384 calls） | 全零失败 |
| B1/B2 九维评审 + C1 直批评审 | 全落盘（B1 72 位 / B2 72 位 / C1 48 位） |
| **severe 映射反转 bug**（三脚本） | 已修复 + 四报告原地修正（buggy 值保留可审计）+ errata 登记 |
| 第一轮判定按修正数据重跑 | E3 ELIMINATE / Bmin ELIMINATE（预注册执行器输出） |
| judge prompt v2 重资格 | STILL_PARTIAL（停止 prompt 迭代，v1+证据核读维持） |
| B2 微负维度初筛 | 37 负格中 21 格噪声级；余与 severe 位分布一致，非独立退化 |

## 新增待办（v2）

| # | 项 | 优先级 | 依赖 |
|---|---|---|---|
| 1 | **D1 头对头收口**：r5_tier vs E3（E061/E062）机械分 + 九维评审 + E061_T13 双杀原位 B3 专项读 | 高 | D1 生成（进行中） |
| 2 | 第三轮判定报告：r5_tier 继位终裁（头对头 + 直批 + B1/B2 旁证） | 高 | #1 |
| 3 | B3 prescreen 升级：把 judge note 引用句纳入旗标词（E026_T05 型盲区）；severe=True 位强制 B3 抽读 | 中 | 无 |
| 4 | judge 资格补充：问句形式复活（E065_T15/T16）与安全错误（E066_T09）漏检登记进 JUDGE_SCOPE 报告，fact/time/severe 维 B3 通道长期保留 | 中 | 无 |
| 5 | W04-04 scorecard markdown 版同步（json 已刷新） | 低 | 无 |
| 6 | E066_T24 swap 位 judge 内部矛盾复核（severe 与 winner 相互关系），纳入 judge 资格附件 | 低 | 无 |

## 判定状态快照（修正后）

- **E3**：ELIMINATE（第一轮 severe 阻断；长剧 9 位编造族）。对话能力优势真实存在——作为 r5_tier 父代保留，不作为独立冠军。
- **Bmin**：ELIMINATE（severe 3>0 + C1/C2 假设未过）。
- **r5_tier**：当前唯一存活冠军候选。对 B0 直批九维全正、severe 0、prompt 更省；头对头 E3 待 D1。
- **B0**：继续作为基线臂；其机械已证复活（E065/E066）与安全错误（E066_T09）已登记。
