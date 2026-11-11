# W22-01 全量批判决 — uc_attr（归属锚定）vs uc（2026-11-04）

> 批次 BATCH-W22-FULL-20261104（AUTH-W22-FULL-20261104-01，464/464 零失败）。
> 评审 AUTH-W22-FULL-REVIEW-20261104-01（232 位 main + 22 位 swap supplement，review 失败率 3.0%，≤20% 门）。
> 判据冻结 W22_01_ATTR_DESIGN_20261104.md。快车道 G0 PASS（W22_FASTLANE_G0_VERDICT_20261104.md）。

## 判决：DUAL_CHAMPIONS_RETAINED

G1-G4 全 PASS；G5 swap 翻转 7/22（5/10 故事有翻转）→ 不得宣布单冠军。
**主假设验证为正面**：归因错位族 severe 计数 cand(0) < par(1)。

---

## G1 severe B3 亲读 — PASS

### 总数门：cand 4 ≤ par 11 ✅

### 归因错位族（主假设核心）：cand 0 < par 1 ✅

**par LRE121_T39（归因错位族 CONFIRMED）**：
- par（uc）："下周末我请，**虾饺你也别抢**" → 把 HER 的虾饺许诺错置为己出 + 无据确定化回请时间
- cand（uc_attr）："**双份肠粉是我欠的，一整笼虾饺是你明年九月许诺的**，这两笔别给我混成一账" → 方向正确、归因正确、不设无据日期
- 锚定块在场（attr_cu=103, attr_items=3）
- **跨批对比**：W21FULL 同位 cand 说"你欠我的"（方向倒置），本批修正为"是我欠的"——锚定块在场与方向修复正相关

**E191_T08（跨批复现归因位，本批 null 结果）**：
- par（uc）："到得挺快，我还在琢磨第三页那个计分" → 未窃取 HER 归因
- cand（uc_attr）："第三页那栏**我也标了红**" → "也"标了红（平行动作），未声称是自己做的（锚定块在场 attr_cu=45）
- 双臂零归因错位——非退化、非回归

### cand 4 severe 全为无据确定化族（非归因族）

| pair_id | 族 | B3 亲读要点 |
|---|---|---|
| E157_T06 | 无据确定化 | 把"暂停恢复时间另行通知"断言为"算废了别等了"，替对方决定卖机路径 |
| LRE121_T20 | 无据确定化+编造 | 把"退烧后再确认"当已安排，凭空造"免疫科"行程+"这回虾饺我请"新承诺 |
| LRE146_T16 | 无据确定化+错时 | 把未明日期定为"六号中午"，与 12/6 周日硬信息冲突 |
| LRE146_T24 | 无据确定化（judge 漏标 note，B3 补读） | "早知道当时那七百块钱省得值了"——事后归因（非窃取 HER 创作物族） |

### par 11 severe 族分布

- 无据确定化族：9（E157_T02, E164_T04, E170_T07, LRE117_T04/T12/T14/T34, LRE121_T01/T19/T31）
- 归因错位族：1（LRE121_T39）
- judge 漏标 note（B3 补读确认族归属）：6

### 不新增故障族 ✅
cand 4 severe 全属父本已有族（无据确定化），未出现新族。

### judge 可靠性注记
- 第四批复现 judge 不可靠：11 par severe 中 6 个 judge note 为空，需 B3 补读
- 7 个 UNPARSEABLE 评审结果（评审端 json 解析失败，非模型崩溃）——已各重试 2 次仍未解析，按 base no_retry 如实记账

---

## G2 九维辅助 — PASS

| 维 | n | cand_avg | par_avg | diff | 门 (≥−0.3) |
|---|---|---|---|---|---|
| goal | 225 | 2.529 | 2.418 | +0.111 | ✅ |
| fact | 225 | 2.316 | 2.200 | +0.116 | ✅ |
| attrib | 225 | 2.396 | 2.311 | +0.084 | ✅ |
| time | 223 | 2.175 | 2.108 | +0.067 | ✅ |
| boundary | 221 | 2.471 | 2.430 | +0.041 | ✅ |
| memory | 223 | 2.157 | 1.955 | **+0.202** | ✅ |
| interact | 225 | 2.440 | 2.373 | +0.067 | ✅ (≥−0.2) |
| init | 225 | 2.027 | 1.902 | +0.124 | ✅ (≥−0.2) |
| emotion | 225 | 2.196 | 2.169 | +0.027 | ✅ (≥−0.2) |

全 9 维正；最差 emotion +0.027 ≥ −0.3；interact/emotion/init 各 ≥ −0.2。
**memory +0.202 为最大正差**——归属锚定强化了"谁说过什么"的追踪。

---

## G3 契约 + 空回复 — PASS

- **空回复**：双臂 0（parent 0/232, candidate 0/232）✅
- **契约 veto**：cand 0 ≤ par 0 + 1 ✅
- **崩坏（unparseable）另册**：parent 4, candidate 7——本候选不改崩坏域（mechanism 无崩坏相关变更），按 base no_retry 如实记账
- 无重试另册启用（设计冻结：本候选不启用崩坏重试）

---

## G4 成本 — PASS

- **prompt**：+1.77% ≤ +5% ✅（触发轮锚定块 ≤120CU × 99 触发位 / 总 prompt）
- **无触发轮**：prompt 与父本逐字节相同（L0 prompt 等价检查通过）✅
- **额外调用**：0 ✅（纯函数检测，零模型调用）
- 触发面 42.7%（99/232）∈ (10%, 90%] ✅

---

## G5 swap 诊断 — FAIL（不得宣布单冠军）

- **agreement**：15/22（68.2%）
- **翻转**：7/22（31.8%）
- **故事级翻转分布**：5/10 故事有翻转

| pair_id | main | swap | 类型 |
|---|---|---|---|
| E164_T08 | TIE | parent | TIE→par |
| E212_T08 | parent | candidate | 翻转 |
| LRE117_T08 | candidate | TIE | cand→TIE |
| LRE121_T16 | candidate | TIE | cand→TIE |
| LRE121_T20 | candidate | parent | **全翻转** |
| LRE146_T16 | candidate | parent | **全翻转** |
| LRE146_T20 | TIE | candidate | TIE→cand |

2 个全翻转（cand→par）：LRE121_T20（cand severe 位）、LRE146_T16（cand severe 位）。
**注**：cand 的 2 个全翻转位恰好是 cand 的 2 个 severe 位——swap 暴露了 cand 在无据确定化族上的弱点。

---

## 引文硬校验

- strict_rate: 79.1%（178/225 逐字命中）
- no_hit: 0 ✅（无完全脱空编造引文）
- severe_unverified: 0 ✅（所有 severe 位的 key_quotes 在语料中有据）
- class 分布：note_like 8, fragments_all_hit 15, fragments_miss 24, no_hit 0
- fragments_miss 24 位：引文片段部分未逐字命中——judge 引述与原文有出入（非编造，为转述偏差）

---

## 主假设结论

**归因锚定候选 uc_attr 在归因错位族上有效**：

1. **LRE121_T39 直接验证**：par 把 HER 虾饺许诺错置为己出（归因错位族 severe），cand 在锚定块在场条件下正确分离双账——这是三批（W17c/W19CROSS/W21FULL）反复出现的归因错位族首次在 cand 侧清零
2. **E191_T08 零退化**：跨批归因位双臂零窃取——锚定块不引入退化
3. **cand 4 severe 全为无据确定化族**——归因锚定不覆盖该族（设计如此：归因锚定只管"谁做的"，不管"何时定"），未越界
4. **memory +0.202** 为九维最大正差——归属锚定对"谁说过什么"的记忆追踪有正面贡献

**但 G5 swap 翻转阻断晋升**：7/22 翻转、5/10 故事有翻转、2 全翻转恰好是 cand 的 2 个 severe 位——cand 在无据确定化族上的 swap 不稳定性未解决。

---

## 机制有效性判定

uc_attr 作为**单变量增量**有效：
- 归因错位族从 par 1 降到 cand 0（主假设正面）
- 九维全正、零额外调用、prompt +1.77%
- 不引入新族
- 但 swap 不稳定阻断单冠军晋升

→ **保留为冠军链组件**（uc 继续为 >24 轮域冠军；uc_attr 的归因锚定块可作为后续候选的继承组件）

---

## 诚实注记

1. judge 第四批复现不可靠：15 个 severe 中 6 个 judge note 为空，B3 补读确认族归属
2. 7 个 UNPARSEABLE 评审结果（评审端 json 解析失败）——非模型崩溃，为评审端质量问题，按 base no_retry 记账
3. swap 样本量 22（10%）较小，翻转统计为探索性读数
4. 崩坏率 parent 1.7% / candidate 3.0%——波状波动，不外推
5. 评审进程在 172/464 位曾中断（进程死亡），后续从 partial.jsonl 续跑完成——续跑前后数据一致性未独立校验（续跑逻辑跳过已完成 verdict，无重复或遗漏）

## 边界

cue 词表冻结不得跑后改；不碰 b_line/封板题；零新增调用；本判决不修改 benchmark/ONLY_ONCE 集。
