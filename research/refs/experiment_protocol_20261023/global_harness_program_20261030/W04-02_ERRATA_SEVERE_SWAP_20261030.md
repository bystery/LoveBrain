# W04-02 ERRATA ADDENDUM：severe 臂映射反转（2026-10-30）

> 会话：main-zcode-20261030-0052 | 登记：ERRATUM-SEVERE-SWAP-20261030
> 严重级：**程序级**（影响第一轮判定方向 + 全部长剧批 severe 计数）

## 1. 缺陷本体

三个评审脚本在把 judge 的 `severe:{"L":bool,"R":bool}` 归一到 candidate/parent 臂时**映射写反**：

```python
# 错误（三脚本同源复制）：
"severe": {"candidate": sev.get("R" if not swap else "L"),
           "parent": sev.get("L" if not swap else "R")}
# 正确：
"severe": {"candidate": sev.get("L" if not swap else "R"),
           "parent": sev.get("R" if not swap else "L")}
```

构造为 main: left=cand/right=par；swap: left=par/right=cand（与 dims、winner 的归一一致）。severe 是唯一写反的字段——**dims 与 winner 归一全部正确**（已用 judge note 逐位验证连贯性）。

## 2. 发现路径（三重独立验证）

1. B2 九维评审自相矛盾位：E067_T10 parent severe=True 却拿 fact/attrib/time 全 3 且赢位；cand 该三维全崩。
2. judge note 原文裁决：E067_T10 note 明写"**LEFT** 把她从未说过的话当原话引用"（main: LEFT=cand）；E066_T24 note 明写"**RIGHT** 编造…试装手忙脚乱二十分钟"（swap: RIGHT=cand）。两例旗标均记在对方臂上。
3. 剧本+原始回复 B3 亲读复核（见 §4）。

## 3. 修正后的 severe 全表（修正值已回写各报告 JSON，原值保留在 severe_reported_buggy）

| 批 | 臂 | 修正前(buggy) | **修正后(实际)** | 明细 |
|---|---|---|---|---|
| FS1-A (E3 vs B0) | E3 | 0 | **2** | E021_T07（素材缺陷位，已裁决剔除）/ E026_T05（**真实**） |
| FS1-A | B0 | 2 | **0** | — |
| FS1-B (Bmin vs B0) | Bmin | 0 | **3** | E021_T07 / E021_T12 / E033_T12 |
| FS1-B | B0 | 3 | **0** | — |
| E061/E062 首轮 | 双臂 | 1/1 | 1/1（同位双杀，不受影响） | E061_T13 安抚压力双杀 ✓ |
| B1 (E063-65, E3 vs B0) | E3 | 1 | **4** | E063_T17 断头路 / E063_T20 实习标 / E064_T21 箱子里叫 / E065_T22 毛线箱——**全部编造过去事件族** |
| B1 | B0 | 4 | **1** | E064_T12（编造接送安排） |
| B2 (E066-68, E3 vs B0) | E3 | 2 | **5** | E066_T24 / E067_T10 / E067_T15 / E067_T19 / E068_T22 |
| B2 | B0 | 5 | **2** | E067_T17 / E068_T01 |
| C1 (r5_tier vs E3) | 双臂 | 0/0 | 0/0（不受影响） | **r5_tier 在 E3 犯 2 severe 的同两篇上零 severe** |

**judge 漏检登记**（与 PARTIAL_QUALIFIED 一致）：E065_T15/T16 B0 机械已证租金复活（3800 复活写合同），judge 因问句形式漏判 severe；E066_T09 B0 安全错误（链条尺寸）judge 给 fact=3 未旗标。B0 的 severe 实际比 judge 旗标更多。

## 4. B3 亲读裁决（关键位）

- **E026_T05（E3 真实 severe，维持）**：剧本 T03"一会儿你深蹲我给你拍"（未发生）→ T04 仍规划 → T05 她转话题。E3 回复"你**点评完**膝盖内扣就没下文了"把未来事件写成已发生且甩锅对方。成立：编造已发生安排。
- **E067_T10（E3 真实 severe，抽验维持）**：她 T09 原话"他挽留说年底有调薪 我脑子嗡了一下"；E3 回复"七天前你说'我不知道该不该高兴'"——引语+时间戳双编造。成立。
- E061_T13 双杀维持原判（双臂同位，映射反转不影响）。

## 5. 判定影响

### 第一轮（W04-03_FIRST_LOOP_VERDICT.json 已按修正数据重跑）
- **E3：KEEP → ELIMINATE**（severe 增多 2>0；剔除素材缺陷位后 1>0 同样成立）。原 KEEP 是映射 bug 的伪影。
- **Bmin：字面 REWORK/意图 KEEP → ELIMINATE**（severe 3>0，且 C1/C2 假设未过）。
- 保护维/成本/九维结论不变（dims 未受影响）。

### 第二轮（长剧）
- E3 相对 B0 的九维优势**维持**（B1 全 9 维正、B2 7 正 2 微负），但 E3 的 severe 族（编造过去事件/无据确定化）9 位 vs B0 3 位 + judge 漏检位——**E3 的语义失败不是噪声而是系统性家族**。
- **r5_tier（E3+assertion_tiering）C1 批全 9 维超 E3 且 severe 0/0**——正是对该族的直接消除。冠军继位线：B0 → E3 → **r5_tier**。

## 6. 脚本修复登记

已修复（2026-10-30）：`tools/run_fs1_semantic_review.py`、`tools/run_longprobe_review.py`、`tools/run_lp_review_generic.py`。修复后新评审自动正确。

## 7. 遗留

- W04-04 scorecard 的 S_severe 字段为 buggy 值（0/2、0/3）——重跑 scorecard 构建器即可刷新，判定链已不依赖它（verdict 直读 review summary）。
- FS1 B3 亲读档未抽到 E026_T05（prescreen 未旗标）——登记为 B3 抽样盲区，后续 prescreen 需把 judge note 引用句纳入旗标词。
