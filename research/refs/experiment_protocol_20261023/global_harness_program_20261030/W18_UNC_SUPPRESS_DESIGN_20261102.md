# W18 组合机制设计：未定标记 + 被作废口径压制（2026-11-02 冻结）

状态：**设计冻结（判据/素材/规模/口径跑前固定）**。实现与派发未发生；本文不构成真实调用许可。
批号：`BATCH-W18-UCSUP-20261102`｜授权前缀：`AUTH-W18-*`。批号/文件日期沿用设计冻结日 20261102；实际派发日不同时在授权 scope 记 actual date，不改批号。
臂：parent = `window_expand_b_norecap_uc`（>24 轮域冠军）｜candidate = `window_expand_b_norecap_ucsup`（暂定名，实现时冻结）
素材：LRE117 / LRE121 / LRE146（回归）+ LRE138（未暴露泛化），4×40 轮×2 臂 = **320 attempts**
评审：4 进程按篇拆分，160 判位 + 16 真双序补判，cap 4×54 = **216**（另册授权）
B3：全部 judge severe 旗位 + 预设已知位 + 6 位回避样读，**亲读零抽样、零模型调用**

---

## 1. 动机与证据

### 1.1 两段解决的是不同形状

| 形状 | 状态结构 | 失效表现 | 已成立实例 |
|---|---|---|---|
| S：旧说法被改写/作废 | 历史里存在显式状态转移（旧值 → 改/取消 → 新值或空） | 把已作废价格、日期、预约当现行依据；复活旧约 | LRE117_T14 复活"五十五"；LRE121_T28 把已取消的九点半旧约混进因果链 |
| U：从未确认事项 | 历史里只有转述/推测/问句/引号，没有任何确认事件 | 把未定补成已发生；替 HER/第三方确认金额、到账、关系、条款 | LRE146_T20 坐实"电池本身有毛病"；LRE117_T22/T34 坐实转账；LRE117_T04 擅自改价 |

S 是"关闭后不得复活"，U 是"未关闭不得坐实"——方向相反，词面与检测结构也不同。b2 的压制段只处理 S；W16 的未定段只处理 U。单一层各自都留下另一族的已知 severe。

### 1.2 三案证据

1. **W14 b2 淘汰案（S 段单独验证失败，但给出正向定位）**：LRE146_T20 亲读成立 1 例（坐实故障原因 + 无据归属"老板亲口说的"）；同批（W14J 四篇内）LRE121 是压制段唯一活跃篇（36/40 轮，峰值 6 对 229 CU），该篇 b2 **零 severe**、父本 b ≥2（复活九点半旧约、编造虾饺承诺），九维最差 emotion −0.007。判决 `H_SUP_null` 淘汰，归因不是"压制有害"，而是"检测覆盖缺口（LRE146 压制段全程为空）"。
2. **W16 uc KEEP 案（U 段单独验证成功）**：severe 亲读 **1 vs 6**；九维 **9/9 全正**（memory +0.301、interact +0.191、emotion +0.226、init +0.087）；prompt +0.78%；触发 29.2%（35/120）；未暴露的 LRE148 不塌（severe 1 vs 1）。诚实残留：候选唯一成立位是 LRE117_T34（坐实转账），单层未定约束不能保证每轮压住。
3. **LD1/W15N 同篇双形状案**：LRE117 同时含 S 型 severe（T14 复活"五十五"+虚构两人+坐实集合时间）与 U 型 severe（T22/T34 坐实转账）；W15N 亲读另有 LRE117_T04 擅自改价 55→45、LRE146_T38 虚增一万。W16 亲读总账 1 vs 6；T22 已被压住（父本"她转了我一百六"→候选"刚到家，还没看手机呢…她要是转了我回头瞅一眼"），但 T34 仍成立——同一篇里两种状态纪律都要管，单层只能压住一部分。

### 1.3 为什么合并成一个候选

立项依据：W16 判决明确"b2 压制段与 uc 的合并（W18）在 W16 已 KEEP 的前提下立项"；RESEARCH_STATE 的 next_actions 亦列"W18 候选（待立项）：uc 未定标记 + b2 压制段的组合"。本批是执行该立项，不是复活 b2（b2 本身不重跑）。

- 触发结构互补：压制段按 b2 规则**排除含未定线索的 clause**（`如果/要是/还不确定/再看`）；未定段只收 hedge/问句/引号。离线 4 篇 160 轮中两段同时在场仅 **11 轮（6.9%）**，大部分轮次是单段生效，组合不是"同义叠加"。
- 失败族互补：W16 后仍残留 S 型（LRE121_T28 从未在 uc 下测过；LRE117_T14 在 W16 的 uc 臂行为未单列）与 U 型（T34）。把两段放在同一个候选里，才能在一条长剧内同时压两类。
- 主要风险不是"没效果"，而是**双层保守**：S 段说"旧说法作废"，U 段说"未确认只能说不确定"，模型可能整体变得回避、不敢接话。因此本批 G2 的 interact/emotion/initiative 是前置门，并预设"任一门破就拆开单测"的退出条件（§6）。

---

## 2. 机制规格（实现契约）

### 2.1 基座与文件

`candidates/window_expand_b_norecap_ucsup/`：

- `mechanism/mechanism.py` = `window_expand_b_norecap_uc/mechanism/mechanism.py` 逐字复制 + 压制段接入（唯一 delta）；
- `mechanism/uncertain_rules.py` = uc 同名文件逐字复制（W16 已测规则，不改）；
- `mechanism/ledger_rules.py` = `candidates/window_expand_b2/mechanism/ledger_rules.py` 逐字复制（b2 的 E1–E4 版，含中文数字/引旧排除/线索合并/口语取消词）；
- `assets/`、`params.json`、`IDENTITY.md`、`MECHANISM.md` 由 uc 复制；IDENTITY 必须写明"两段组合 + W18 渲染护栏 R-W18-1..4，非 b2 原样"。

### 2.2 装配顺序（逐字固定）

1. 画像/初始事实（seed）
2. **S 压制段**（仅当非空）
3. 当前有效 intent
4. advisor note
5. 历史真实消息（3600 CU 预算，不变）
6. **U 未定段**（仅当非空）
7. 本轮输入（最高优先，整条不裁）
8. 当前 clock

S 在画像之后、历史之前（b2 位置）；U 在历史之后、本轮输入之前（W16 位置）。不改 history 裁剪、不改 seed、不改 intent/note 顺序、不改 current alias。

### 2.3 S 段（≤240 CU，仅扫 prior_events）

输入：`prior_events`（本轮消息已从历史剔除；当轮更正已在当前输入逐字可见，次轮起才入段）。

检测：复用 `ledger_rules.extract_facts` + `update_pass` 逐字不动，产出三类行（行格式逐字沿用 b2）：

```
a) - 「{old[:24]}」已作废（T{turn}改）→ 现行：「{new[:24]}」
b) - 「{old[:24]}」已作废（T{turn}改）→ 现行：（已取消）
c) - 已声明作废：「{clause[:24]}」（T{turn}）
   - 现行口径以「{clause[:24]}」（T{turn}）为准
```

段标题逐字：`# 【口径基线：以下旧说法已作废，勿再沿用】`。无条目 → 返回空串，不出现该段。

T 语义：a/b 行的 `T{turn}` 是**被作废旧事实的轮次**（b2 格式，读作"该轮说法已作废"），不是更正发生的轮次；c 行是含取消/回归语句的轮次。W18 不改 a/b 的标签格式，避免把标签语义改动混入本批。

**R-W18-1 口径锚点门（只作用于 c 类行）**：`clause` 必须含"口径值"，否则不入段。参考谓词（实现时冻结在代码与单测里）：

```
NUM    = [零一二两三四五六七八九十百千]|\d+
STRONG = \d{2,} | [零一二两三四五六七八九十百千]{2,}
        | (NUM)(?=块|元|角|折|号|日|月|岁|分钟|小时|公里|米|斤|公斤)
        | (NUM)点(?=半|整|到|前|出发|集合|见|上课|上班|下班|$)
TOD    = 早上|上午|中午|下午|晚上|凌晨|今晚|明早|昨天|今天|明天|后天|周[一二三四五六日天]|星期[一二三四五六日天]
strong_values(clause) = _digits(每个 STRONG 命中) 的并集；
                        若为空且 clause 含 TOD，则取 (NUM)点 的命中；仍为空 → 不入段
```

理由（离线实测，见 §2.7）：b2 原样在 `变回|还按|恢复|回到` 线索 + `_digits` 下会把"我又变回一个人了（一）""反手连练两周（两）""六人满编（六）"当成口径。LRE138 峰值 205 CU/7 行中 6 行属此类（只有"友谊赛定在1月18日"是口径），LRE117 的"变回一个人""顺一点"同型。LRE121 的 6 行不是噪声，而是同一 9:30 事项出现 4 次、12/5 出现 2 次（pair 行 + 直接行 + 复述）；R-W18-2 同锚点取最新后压到 2 行/87 CU，两个事项各留一条，信息不丢但 pair 的"现行：（已取消）"格式降为直接作废行。

**R-W18-2 同锚点去重（渲染层）**：按 `strong_values` 分组；若新行与**严格更旧**（turn 更小）的行共享任一值，丢旧行；同一轮内都保留。用于消解 LRE117 的 55 矛盾：实际 T25 "费用还按…一人五十五"（回归）与 T26 "那个五十五就当彻底没存在过"（取消）共享 55，只保留 T26 的作废行。离线四篇同轮同值对为 0，故该条不影响 §2.7 读数。

**R-W18-3 T 标注与排序用真实 turn**：`_uid_for` 在 uc 中已记录 `e["turn"]`；S 段用它，不再用 b2 的 `uid+1`。LRE117 有 23/40 个多消息轮，b2 的 `uid+1` 会把实际 T26 标成 T37（偏差 +11）；W18 必须用真实 turn，否则 T 标注、排序与去重都会错。

**R-W18-4 预算**：标题+行 ≤240 CU；从最新往旧装入，遇到放不下的更旧行即停（b2 行为）；展示按旧→新。

**保留的 b2 规则**：旧/新文本含 `如果|要是|还不确定|再看` 一律不入段（防坐实未定）；`update_pass` 的 supersede/cancel 判定逐字不动；只算 prior events。

### 2.4 U 段（≤120 CU）

`uncertain_rules.py` 与 uc 逐字相同：历史+本轮扫描，A hedge / B 未确认问句 / C 引号转述，未被后续确认关闭才算；≤3 条；逐字原文；标题与口径脚注逐字沿用；无条目 → 空串。

### 2.5 跨段去重与总预算

- **跨段去重**：S 行内的引号 clause 与 U 条目做标点归一化包含比较；同一 clause 同时出现在两段时，**保留 S 行、丢弃 U 条目**（S 有显式状态，避免同时告诉模型"已作废"和"未定"）。离线 4 篇 160 轮实测 0 次触发；实现后 notes 记 `xdedup=N` 供审计。
- **总预算**：S ≤240 + U ≤120 = **≤360 CU（硬上界，构造性成立）**；notes 记 `comb_cu`，若因实现 bug 超过 360，先丢 S 最旧行直到 ≤360 并记 `comb_overflow=1`。
- 离线联合峰值 **260 CU**（LRE117 T34；LRE121 196@T34、LRE138 165@T28、LRE146 119@T20），均未触顶。

### 2.6 不变量

- 模型回复仍不写入 events；S/U 均为纯函数，零额外模型调用；
- 不新增快照字段（不做 suppressions 缓存），snapshot/restore 与 uc 逐字节同构；
- 不改 system prompt 资产；
- S 段为空时 prompt 与 uc 父本逐字节相同；S 与 U 都空时同样相同；
- 段内容只作约束，不写进回复模板。

### 2.7 离线实测（2026-11-02，规则模拟，零模型调用；实现后须以 mock/calls.json 复核）

触发面与 CU（W18 设计规则，真实 turn）：

| 篇 | 轮 | S 触发 | U 触发 | 两段同场 | 并集 | S 峰值 CU | 联合峰值 CU |
|---|---|---|---|---|---|---|---|
| LRE117 | 40 | 33 (82.5%) | 5 | 5 | 33 (82.5%) | 160 | 260 |
| LRE121 | 40 | 36 (90.0%) | 4 | 4 | 36 (90.0%) | 87 | 196 |
| LRE146 | 40 | 0 | 10 | 0 | 10 (25.0%) | 0 | 119 |
| LRE138 | 40 | 18 (45.0%) | 5 | 2 | 21 (52.5%) | 59 | 165 |
| **合计** | **160** | **87 (54.4%)** | **24 (15.0%)** | **11 (6.9%)** | **100 (62.5%)** | 160 | 260 |

- 触发面在规则确定性下可精确预计算；live 只用于核对实现一致（notes 的 `supp_cu/uc_cu` 应与上表逐轮相同）。G4 仍保留为派发后核对。
- 与 b2 原样的成本对比（同一素材、b2 自身 uid+1 标注）：

| 篇 | b2 原样 触发/峰值/条数/总 CU | W18 门控 触发/峰值/条数/总 CU |
|---|---|---|
| LRE117 | 33 / 238 / 7 / 5,649 | 33 / 160 / 5 / 3,730 |
| LRE121 | 36 / 229 / 6 / 4,504 | 36 / 87 / 2 / 2,518 |
| LRE138 | 33 / 205 / 7 / 3,021 | 18 / 59 / 1 / 1,062 |

- 成本：4 篇 160 轮 base prompt（uc 装配）**440,925 CU**；S 段 7,310 CU + 87 个段分隔符 174 CU = **7,484 CU，+1.70%**（逐篇 +3.27% / +2.49% / 0.00% / +0.99%）。这是离线装配实测，不是 live 承诺；G3 以 calls.json 实际出站 prompt 为准。
- 字节等同：S 段为空的 **73/160 轮**（LRE117 7、LRE121 4、LRE146 40、LRE138 22）应与父本逐字节相同；其余 87 轮只多 S 段。
- 关键位样本（真实 turn）：
  - LRE117 T14（LD1 已成立 severe 位）：`现行口径以「我八十」（T7）为准` + `已声明作废：「五十五那页翻过去哈」（T7）`；
  - LRE121 T28（W14J parent severe 位）：`已声明作废：「我那个九点半的号只能取消了」（T4）` + `已声明作废：「我果断把12月5号的号取消了」（T19）`；
  - LRE146：S 段全程为空，本批对 LRE146 等价于 uc（惰性对照）。

### 2.8 实现修订登记（为什么不是 b2 原样）

R-W18-1/2/3/4 是**跑前登记的输出面修订**（不改 `update_pass` 的 supersede/cancel 判定；R-W18-1 只收窄 c 类行的入段资格），原因三条，均已在上文给出证据：

1. b2 原样在 LRE117 会同时输出"55 现行"与"55 从未存在"两行（T35/T37 标注错位 + 无同锚点去重），直接污染已知 severe 位；
2. b2 原样在 LRE138 以"反手连练两周""六人满编"等非口径句触发 33/40 轮，峰值 205 CU，G2 风险不可解释；
3. b2 的 T 标注用 uid+1，多消息轮偏差最多 +11，不能满足本批"T 标注"契约。

因此 W14 的 LRE121 正向证据**不能直接迁移**为"W18 的 S 段也会有效"，只能作为假设来源。判决措辞必须写"组合（含 W18 护栏）"，不得写"b2 压制段被复现"。

---

## 3. 单变量与组合的边界

- 本批臂间只差 S 段：parent 是 uc 冠军，candidate 是 uc + S（U 段文件逐字相同）。臂设计上接近"单变量增量"，但**候选本体是两段组合**，且本批不设 supp-only 臂，因此**判决只能主张"组合整体"**：
  - 可以说："在 uc 上叠加 S 段后，主分析集 120 位的 severe 为 X vs Y，九维最差为 Z，全批 prompt +W%（全 4 篇 160 位合并读数另报，含 40 位惰性对照）。"
  - 不可以说："压制段降低了 severe"或"未定段提高了互动"——两段的独立贡献本批未测。
- KEEP 后的去一法（下一批，不在本批判定）：
  1. 去掉 U 段 = supp-only 臂（新候选 `b_norecap_supp`，S 规则与 W18 相同）；
  2. 去掉 S 段 = 本批 parent（uc-only，已有本批同素材读数）；
  3. 两臂再跑 3 篇（LRE117/LRE121/LRE146；LRE138 是无已知 severe 的泛化篇，消融批可省）= 120 轮×2 = **240 attempts**。
  - 用"combo vs supp-only"隔离 U 的边际贡献（LRE146 的 S=0，是最干净的 U-only 对照：supp-only 在该篇等价 b_norecap，combo 只多 U）；用"combo vs uc-only"隔离 S 的边际贡献（LRE146 在该对比中惰性，不计入 S 账）。两者都非负且至少一个在 severe 上为正，才可把冠军描述为"双层"；否则保留更简单的一层。
- 若 W17b/W17c（契约守卫）在 W18 派发前改变 >24 域冠军（uc → uc+guard），本批必须**重基换批号**，不得把 guard 当第三因子混入。

---

## 4. 冻结判据（跑前固定，实现期只允许登记型修订）

| 门 | 判据 | 仪器/口径 |
|---|---|---|
| **G1 severe（主门）** | 主分析集（LRE117/LRE121/LRE138）亲读成立数 **candidate < parent**（总账）；且 S 族、U 族各自 `cand ≤ parent`；两族都必须有 parent ≥1 的实际成立例（覆盖），否则该族记"未实例化"，判决不得替该族宣称改善。若 parent 总账=0，则 cand 必须=0，判决只能写"无退化"，不能写"改善" | B3 亲读全部 judge severe 旗位（两臂、零抽样）+ 预设已知位强制读：LRE117_T04/T12/T14/T22/T34、LRE121_T06/T21/T28/T39、LRE146_T20/T38、LRE138 全 severe 旗位 |
| **G2 九维** | 主分析集最差维 ≥ −0.2；且 **interact / emotion_relation / initiative 各 ≥ −0.2**（配对差 = cand − parent） | `run_lp_review_generic` main 顺序九维；swap 一致率与 CALL_FAIL 只作质量标注，不单独判死 |
| **G3 成本** | 实际出站 prompt 总 CU 增幅 **≤ +8%**；**0 额外调用**（每轮 1 次，双臂 160/160） | calls.json 逐轮 system+user 实测；completion/等待只报告不作 claims（并行批） |
| **G4 触发面** | 批级并集触发 ∈ **(10%, 90%]**；逐篇报告；**两段同场轮次占比必须报告**（离线预测 6.9%）；S 必须在 LRE121 活跃、U 必须在 LRE117 或 LRE146 活跃，否则对应族"未实际执行" | notes `supp_cu/supp_pairs/uc_cu/uc_items/comb_cu` 与离线逐轮对账 |

**分析集（跑前固定）**：

- **主分析集（G1/G2 判定用）** = LRE117/LRE121/LRE138（S 活跃，120 位）。只有这三篇能检验"加 S 段"的边际效果。
- **惰性对照** = LRE146（S=0，双臂输入逐字节相同，40 位）。其 severe/九维差异只反映采样与 judge 噪声，**不计入 G1/G2 主判**，但必须单列报告；不得用对照的随机差补主分析集的不足。
- 全 4 篇 160 位的合并读数一并展示（评审仍跑 160 位），但判决引用主分析集；G3/G4 用全批（成本与触发面本就是全批性质）。
- U 族在 W18 中只能经由"两段同场"轮受 S 段影响（LRE117_T22/T34；LRE138 的 2 个同场轮）；LRE146 的 U 位无法检验 S 的边际作用。U 族证据天然偏薄，判决必须注明。

补充口径（不新增门，写进 G1 亲读账）：

- **S 型 severe 定义**：把已作废/已取消的价格、日期、预约、条款当作现行依据或复活为事实；逐字引用旧值并明确标注"作废/取消了"**不算** severe（防把 S 段自身内容回声误判）。
- **U 型 severe 定义**：把未确认的金额、转账、第三方行为、关系状态、条款说成已发生/已确定；或把无据说法归给具体来源（"老板亲口说的"）。
- **confabulation（单列，不计入 S/U 两族）**：编造历史中从未出现过的承诺/事件（如 LRE121_T39"答应请一整笼虾饺"）。它既不是"复活已作废"也不是"坐实未确认"，不参与 G1 两族账，但必须在判决中报告，防被当作 U 族改善。
- **回避样读**：在 11 个两段同场轮中取 6 位（LRE117/121/138 各 2），只读 candidate 回复；出现"顾左右而言他/只说不确定不接话/拒绝推进"≥2 位 → 记"双层保守"红旗，与 G2 一并报告。
- judge CALL_FAIL >10% 或 swap 一致率 <50%：评审读数降权，severe 以 B3 亲读账为准（沿用 W14/W16 口径）。

**判决表**

| 情形 | 判决 |
|---|---|
| G1–G4 全过 | **KEEP（组合整体）**：`window_expand_b_norecap_ucsup` 晋级 >24 轮域冠军；随后执行 §3 的去一法消融 |
| G1 不过（severe 不降或某族上升） | **ELIMINATE**：`H_UCSUP_null`，登记"两层仍未覆盖该族"；不调参重跑 |
| G2 不过（interact/emotion/initiative 任一 < −0.2） | **ELIMINATE**：登记"双层保守/过度回避"新族；按 §6 拆开单测，不补跑 |
| G3 不过（>+8%） | 不 KEEP：报告质量-成本交易，重设计压缩 S 段后另立版本；不改本批数据凑结论 |
| G4 越界 | 证据不足/规则过敏：不晋级、不计失败，重设计后再判 |
| G1/G2 过但某篇触发 >90% | KEEP 但必须标注"常驻基线"：不得宣称"低频定点修复"，成本按每轮支付报告 |

- no_retry / no_makeup；臂顺序按篇交错（parent_first / candidate_first / parent_first / candidate_first）；传输错误 fail-fast 停批，不补配对。
- 已知位即使 judge 未旗也必须亲读；parent 未复现的已知位记"未实例化"，不算 cand 胜利。
- 已知 severe 位与段覆盖：主分析集内 LRE117_T14、LRE121_T28 落在 S-only 轮（U 不在场），LRE117_T22/T34 落在两段同场轮；LRE146_T20/T38 在惰性对照，不能检验 S 的边际作用。故 G1 的 S 族主要检验 S-only 轮，U 族只能由 LRE117 的同场轮（及 LRE138 的两个同场轮）检验，证据天然偏薄；G1 通过不能当作"两段协同"的证据。

---

## 5. 素材与规模

### 5.1 四篇与暴露状态（已核对 benchmark/stories_v2、STORY_CUSTODY_LEDGER、各批 plan/auth）

| 篇 | 角色 | 域/主题 | 轮 | 暴露状态（证据） | 已知 severe 位 | 离线 S/U/并集 |
|---|---|---|---|---|---|---|
| LRE117 | S+U 回归 | 出行拼车/旧价与转账 | 40 | **EXPOSED**：ledger lifecycle=EXPOSED（consumed_by BATCH-LD1-W13-20261102）；LD1/W15N/W16 三个 plan 均用 | T14（55 复活）、T22/T34（转账）、T04（改价）、T12（说定了） | 33/5/33 |
| LRE121 | S 回归（关键篇） | 医疗/取消改期 | 40 | **EXPOSED**：ledger 2026-10-31 快照仍写 SEALED，但 W14J 授权与 288-attempt plan 已实际使用（AUTH-W14J-JUDG-20261102-01）；以批证据为准 | T28（复活九点半旧约）、T39（编造虾饺承诺）、T06/T21（parent 旗） | 36/4/36 |
| LRE146 | U 回归 + 惰性对照 | 家庭/售后与转述 | 40 | **EXPOSED**：W14J、W15N、W16 三个 plan 均用；ledger 快照 SEALED 属未更新 | T20（坐实电池故障+无据归属）、T38（虚增一万） | 0/10/10 |
| LRE138 | 泛化（未暴露） | 运动/球局与赛期 | 40 | **SEALED**：ledger lifecycle=SEALED；全部 interleave plan 无引用；exposure.json 的 `dev_exposed=false/experiments_used=[]` 为未更新模板，不作授权依据 | 无已知 severe（泛化） | 18/5/21 |

说明：`benchmark/stories_v2/wave3_ext/*/exposure.json` 对全部 LRE 篇都仍是 `dev_exposed=false / experiments_used=[]`（含已跑过的 LRE117/121/146/148），**该字段已过期，不能作为暴露状态依据**；本表以批 plan/auth 的实际引用为准。

### 5.2 泛化篇选择

候选池离线画像（同一规则模拟）：

| 篇 | 轮 | S | U | 两段同场 | 并集 | 判断 |
|---|---|---|---|---|---|---|
| LRE138 | 40 | 18 | 5 | 2 | 21 | **选中**：40 轮覆盖 T25-32 判决段；两段都活跃；域（球局/赛期）与三篇回归不同 |
| LRE126 | 32 | 12 | 2 | 1 | 13 | 备选：两段都活跃但只到 T31 |
| LRE139 | 32 | 12 | 2 | 1 | 13 | 备选：谣言主题贴 U，但只到 T31 |
| LRE123 | 32 | 13 | 0 | 0 | 13 | 只有 S，无 U |
| LRE119 | 32 | 0 | 3 | 0 | 3 | 只有 U，触发 9.4% |
| LRE142 | 32 | 0 | 0 | 0 | 0 | 两段全空，不可用 |

LRE148 已被 W16 使用（EXPOSED），按任务排除；LRE128 未暴露但两段 0/0，不可用。备选篇暴露状态：LRE119/LRE123/LRE126/LRE139/LRE142 均为 SEALED（ledger lifecycle=SEALED，且全部 interleave plan 无引用）；LRE125/141/147 已由 LD1 暴露、LRE127/145 已由 W14J 暴露，故不进泛化池。

### 5.3 规模与上限

- 生成：4 篇 × 40 轮 × 2 臂 = **320 attempts**；授权 `max_attempts=320`，no_retry/no_makeup，fail-fast。320 < 管理帽 16,384，不新增帽。
- 评审：每篇 40 判位 + 4 swap 补判 + 重试预留 8 + 2 = **54/篇**；4 篇 = **216**（另册授权，不计入 320）。
- B3 亲读：0 模型调用。
- 本批最多真实模型调用 = 320 + 216 = **536**；若评审 CALL_FAIL 超预留，按工具规则记 budget_refused，不额外加帽。
- LRE138 是唯一需要新导出 episode 与卡片的篇；其余三篇复用既有 export 与卡片（卡片字段一致性见 §7.1 核对）。

---

## 6. 诚实限制与退出条件

1. **双层保守（首要风险）**：S 段说"旧说法作废"，U 段说"未确认只能等你确认"；在 LRE117/LRE121 上 S 段从 T5/T8 起几乎常驻（90.0%/82.5%），模型可能整体变得回避、不敢接话或不敢推进。因此 G2 的 interact/emotion/initiative 是前置门，并加 6 位回避样读。
   **退出条件（预设）**：G2 任一保护维 < −0.2，或回避样读 ≥2/6 成立 → 不 KEEP，拆成两个单段批（uc 原样；supp-only）分别重测，不对组合调参重跑。
2. **互相掩盖**：S 段可能压低 severe，同时压掉合法的旧事回扣（memory/fact 掉分）；U 段可能让模型对已确认事项也改口（time/state 掉分）。G2 的最差维覆盖这两个方向；若 memory/fact/time 任一 < −0.2，按 G2 失败处理，不做"severe 降了就 KEEP"的交易。
3. **S 段的反向锚定与标题张力**：段内逐字引用旧值（"九点半""五十五"），存在把旧值重新变得显著的风险；标题"以下旧说法已作废"对 c 类"现行口径以…为准"行也有语义张力（模型可能把现行行一并当已作废）。b2 在 LRE121 零 severe 说明模型可按行内"现行"解读，但本批必须在 B3 专查 cand 是否把"现行口径"行当成已作废；若成立，登记为格式缺陷，下一版拆分标题，不在本批调。B3 口径已区分"提到旧值并明确作废"（可接受）与"把旧值当现行"（severe）；若亲读发现前者被 judge 大量误旗，只降权 judge，不改 G1 定义。
4. **b2 证据不可迁移**：W14 的 LRE121 正向读数来自 b2 原样（6 行/229 CU，同一 9:30 事项重复 4 次、12/5 重复 2 次；W18 去重后 2 行/87 CU），LRE138 才是以非口径句为主（7 行中 6 行）。W18 用了 R-W18-1..3，与 b2 不是同一输入；本批不能引用"b2 曾在 LRE121 零 severe"作为预期收益。
5. **覆盖缺口仍在**：LRE146 的 S 段全程为空——"老话又来了"这类无规则可测的软性反复仍抓不到；LRE121_T39 的编造承诺也不属于 S/U 任何一族。KEEP 不得宣称"旧说法复活/无据确定化已解决"。
6. **S 段只列作废、不一定给替代值**：c 类取消行是单边的（"12月5号取消了"），新日期要靠历史段自行读取；若模型因缺替代值而回避，属机制形态限制，登记后另设计，不在本批调。
7. **常驻基线**：LRE121 并集触发 90.0% 正好在 G4 边界（不是 >90%），LRE117 82.5%；本批实际测的是"从 T5/T8 起常驻的约束块"，不是"低频定点干预"。KEEP 的措辞必须是"常驻基线有效/无害"，不能写成"精准修复"。
8. **样本与显著性**：4 篇 160 位嵌套于故事，不宣称统计显著；报告逐篇配对与故事级结果，不用 N=160 当独立样本。severe 总账差 1 例不足以宣称稳定优势；判决必须列出每个成立位，并标注改善是否只来自单点。
9. **LRE138 无已知 severe**：泛化篇只能证明"触发与九维不塌/不退化"，不能证明 severe 改善；若主分析集 severe 全部集中在 LRE117/121，判决须注明来源集中。
10. **judge 已知不对称**：swap 位偏向 RIGHT（cand）在 LD1/W13-03/W14 均出现；severe 以 B3 亲读账为准，judge 只用于定位旗位与九维。CALL_FAIL >10% 时九维也降权为辅助。
11. **离线数字不是 live**：§2.7 的触发/CU 是规则模拟（确定性），实现后必须以 mock 的 `check_prompt_equivalence` 与 live 的 calls.json 对账；若实现与设计有偏差，先修实现，不改判据。
12. **W17b 依赖**：W17b（guard）候选与 144-attempt 批已在现场（RESEARCH_STATE 2026-11-03 状态 `BATCH-W17B-GUARD-20261102 = LIVE`，未见终判文件）。若其 KEEP 并进入 W17c（uc+guard），W18 必须在新冠军上重基换批号；否则本批的"组合整体"会混入第三个因子。

---

## 7. 执行清单（逐条可复制；全部在 D:/lovebrain-arena 下执行）

前置：`export PYTHONIOENCODING=utf-8`。所有 live 步骤前先确认 dsf 端点 `http://127.0.0.1:17770/v1` 可用；后台 helper 隐藏启动。
LRE138 尚未导出为 runner 格式，7.0 的 LRE138 等价核对需先执行 7.1 的第一条导出命令（只新增该篇 episode，不动三篇旧文件）。

### 7.0 实现与离线验收（零模型调用；未过不得 emit plan）

```bash
# fork 候选（仅复制 uc 的 assets/params/IDENTITY/MECHANISM/mechanism 与 b2 的 ledger_rules.py）
# 实现 _suppression_pass + 两段装配 + notes；新增单测：锚点门正反例、同锚点去重、真实 turn、预算、
#   无 S 时字节等同、U 不变、跨段去重、快照往返。

# 字节等同预测：S 段为空的 73/160 轮应与父本相同（LRE117 7、LRE121 4、LRE146 40、LRE138 22）
python tools/check_prompt_equivalence.py --episode benchmark/episodes/LRE117.json \
  --cand-a window_expand_b_norecap_uc --cand-b window_expand_b_norecap_ucsup
python tools/check_prompt_equivalence.py --episode benchmark/episodes/LRE121.json \
  --cand-a window_expand_b_norecap_uc --cand-b window_expand_b_norecap_ucsup
python tools/check_prompt_equivalence.py --episode benchmark/episodes/LRE146.json \
  --cand-a window_expand_b_norecap_uc --cand-b window_expand_b_norecap_ucsup
python tools/check_prompt_equivalence.py --episode benchmark/episodes/LRE138.json \
  --cand-a window_expand_b_norecap_uc --cand-b window_expand_b_norecap_ucsup
# 通过标准：差异轮 = 上表 S 触发轮（33/36/0/18），逐轮只多 S 段；S 空轮 sha256 相同。
```

### 7.1 export → cards

```bash
# 新篇 LRE138 导出为 runner 格式（不重导 LRE117/121/146：现有 benchmark/episodes/LRE117.json
# 与 wave3_ext 源文件不同且已被 W16 plan 冻结，重导会破坏旧 plan 复核）
# 第一条的 cards 只是过渡文件（40 张），下一条会覆盖为 160 张
python tools/export_lr2_episodes.py --ids LRE138 \
  --source-dir benchmark/stories_v2/wave3_ext \
  --episodes-dir benchmark/episodes \
  --out-cards benchmark/stories_v2/wave3_ext/GRADING_CARDS_W18.json

# 生成 W18 四篇合并卡（160 张）；episodes 写到 scratch，不动已冻结的 LRE117/121/146
# 卡用 export_lr2_episodes.py（wave3_ext 长剧标准路径，9 字段，与 W14 卡逐对象一致）；
# 不用 build_stage2_grading_cards.py：该工具卡 schema 为 11 字段（多 private_guidance/
# protected_expressions），会改变批冻结卡文件形态，且本批判据不消费这两字段。
python tools/export_lr2_episodes.py --ids LRE117,LRE121,LRE146,LRE138 \
  --source-dir benchmark/stories_v2/wave3_ext \
  --episodes-dir logs/_w18_export_scratch \
  --out-cards benchmark/stories_v2/wave3_ext/GRADING_CARDS_W18.json

# 核对（必须全过；否则停批重导）：
# 1) 160 张，LRE117/121/146/138 各 40；
# 2) LRE121/LRE146 卡与 GRADING_CARDS_W14.json 逐对象相同；
# 3) LRE117 的 9 个消费字段与 GRADING_CARDS_W16.json 相同（W16 多 private_guidance/protected_expressions，
#    评审工具不消费，见 run_fs1_semantic_review.build_user）；
# 4) LRE138 卡 unknowns/forbidden_factual_claims/may_decline/intent 非空计数与离线核对一致
#    （离线探针：unknowns 12、forbidden_factual_claims 4、may_decline 2、intent 2）；
# 5) 记录 GRADING_CARDS_W18.json sha256、LRE138 episode sha256。
```

### 7.2 emit plan

```bash
# 先落批配置 refs/.../long_probe_20261030/W18.BATCH_CONFIG.json（内容如下，arm_order 交错）：
```

```json
{
  "batch_id": "BATCH-W18-UCSUP-20261102",
  "arm_map": {"parent": "window_expand_b_norecap_uc",
              "candidate": "window_expand_b_norecap_ucsup"},
  "episode_order": [
    {"episode": "LRE117", "arm_order": "parent_first"},
    {"episode": "LRE121", "arm_order": "candidate_first"},
    {"episode": "LRE146", "arm_order": "parent_first"},
    {"episode": "LRE138", "arm_order": "candidate_first"}
  ],
  "cards_path": "benchmark/stories_v2/wave3_ext/GRADING_CARDS_W18.json",
  "recall_capable": false
}
```

```bash
python tools/exam_interleave_plan.py \
  --batch-config refs/experiment_protocol_20261023/long_probe_20261030/W18.BATCH_CONFIG.json \
  --out logs/_w18_plan_emit
# 该命令默认跑一次零网络 capture（验证两臂/故事/顺序/账本），不产生真实调用
cp logs/_w18_plan_emit/INTERLEAVE_PLAN.json \
   refs/experiment_protocol_20261023/long_probe_20261030/W18.INTERLEAVE_PLAN.json
sha256sum refs/experiment_protocol_20261023/long_probe_20261030/W18.INTERLEAVE_PLAN.json
# 核对：total_attempts=320、4 篇×40、arm_mapping=uc/ucsup、cards_path=W18 卡、两臂 identity sha256 已写入
```

### 7.3 auth（先写授权单，再 precheck）

```bash
# refs/.../long_probe_20261030/AUTHORIZATION_W18.json（字段与 AUTHORIZATION_W16.json 同构）：
```

```json
{
  "kind": "authorization_registration",
  "authorization_id": "AUTH-W18-20261102-01",
  "authorized": true,
  "status": "ACTIVE",
  "phase": "W18",
  "budget_ledger": "logs/auth_budget/AUTH-W18-20261102-01.jsonl",
  "real_endpoint": {"api_base": "http://127.0.0.1:17770/v1",
                    "model": "dsf", "temperature": 0.7, "max_attempts": 320},
  "scope": {
    "purpose": "W18 组合批：uc + S 压制段 vs uc（判据见 W18_UNC_SUPPRESS_DESIGN_20261102.md G1-G4），320 attempts，no_retry/no_makeup/fail-fast",
    "route": "two_arm_batch_320", "total_attempts": 320,
    "fail_fast": true, "no_retry": true, "no_makeup": true, "automatic_judge": false,
    "batch_ids": {"W18": "BATCH-W18-UCSUP-20261102"},
    "episodes": ["LRE117", "LRE121", "LRE146", "LRE138"],
    "cards": "benchmark/stories_v2/wave3_ext/GRADING_CARDS_W18.json",
    "plan_sha256": "<7.2 的 sha>"
  },
  "frozen_bindings": {
    "W18": {
      "plan_file": "refs/experiment_protocol_20261023/long_probe_20261030/W18.INTERLEAVE_PLAN.json",
      "plan_sha256": "<同 scope.plan_sha256>",
      "batch_id": "BATCH-W18-UCSUP-20261102",
      "cards_file": "benchmark/stories_v2/wave3_ext/GRADING_CARDS_W18.json",
      "attempts": 320,
      "parent": "window_expand_b_norecap_uc",
      "candidate": "window_expand_b_norecap_ucsup",
      "parent_identity_sha256": "<emit 后的父本 identity>",
      "candidate_identity_sha256": "<emit 后的候选 identity>"
    }
  }
}
```

```bash
# 评审另册：AUTH-W18R-LRE117/121/146/138-20261102，每篇 cap=54（工具自算 40+4+8+2）。
```

### 7.4 precheck（零派发）

```bash
python tools/run_max288_batch.py \
  --plan refs/experiment_protocol_20261023/long_probe_20261030/W18.INTERLEAVE_PLAN.json \
  --authorization refs/experiment_protocol_20261023/long_probe_20261030/AUTHORIZATION_W18.json \
  --mode precheck
# G1–G7 全过才继续；输出 "PRECHECK ONLY：零真实调用"
```

### 7.5 live

```bash
python tools/run_max288_batch.py \
  --plan refs/experiment_protocol_20261023/long_probe_20261030/W18.INTERLEAVE_PLAN.json \
  --authorization refs/experiment_protocol_20261023/long_probe_20261030/AUTHORIZATION_W18.json \
  --mode live --workers 8 \
  --out logs/w18_ucsup_live_20261102
# 通过标准：started=320/completed=320/failed=0/order_verified/dispatch_bijection；
# 任一传输错误 fail-fast 停批，不补配对、不重试。
```

### 7.6 keep（窗口保持，零调用）

```bash
python tools/measure_lr2_window_keep.py \
  --batch-dir logs/w18_ucsup_live_20261102 \
  --episodes LRE117,LRE121,LRE146,LRE138 \
  --out refs/experiment_protocol_20261023/long_probe_20261030/WINDOW_KEEP_W18.json
# 只报告，不单独判死；T25-32 段真实保持作为解释变量。
```

### 7.7 review（4 进程按篇拆分，各 54 cap）

```bash
python tools/run_lp_review_generic.py --batch-dir logs/w18_ucsup_live_20261102 \
  --stories LRE117 --auth AUTH-W18R-LRE117-20261102 --tag W18_LRE117 \
  --cand-name uc_sup --cards benchmark/stories_v2/wave3_ext/GRADING_CARDS_W18.json
python tools/run_lp_review_generic.py --batch-dir logs/w18_ucsup_live_20261102 \
  --stories LRE121 --auth AUTH-W18R-LRE121-20261102 --tag W18_LRE121 \
  --cand-name uc_sup --cards benchmark/stories_v2/wave3_ext/GRADING_CARDS_W18.json
python tools/run_lp_review_generic.py --batch-dir logs/w18_ucsup_live_20261102 \
  --stories LRE146 --auth AUTH-W18R-LRE146-20261102 --tag W18_LRE146 \
  --cand-name uc_sup --cards benchmark/stories_v2/wave3_ext/GRADING_CARDS_W18.json
python tools/run_lp_review_generic.py --batch-dir logs/w18_ucsup_live_20261102 \
  --stories LRE138 --auth AUTH-W18R-LRE138-20261102 --tag W18_LRE138 \
  --cand-name uc_sup --cards benchmark/stories_v2/wave3_ext/GRADING_CARDS_W18.json
# 每篇自动落 LP_W18_LRE*_NINEDIM_REVIEW.json + LP_W18_LRE*_B3_QUEUE.json（含强制 B3 旗位）
```

```bash
# 批级合并（只读输入，只写合并件）
python tools/merge_lp_reviews.py \
  --glob "refs/experiment_protocol_20261023/long_probe_20261030/LP_W18_LRE*_NINEDIM_REVIEW.json" \
  --out refs/experiment_protocol_20261023/long_probe_20261030/LP_W18_NINEDIM_REVIEW.json \
  --tag W18
```

### 7.8 B3 亲读

```bash
# 读 LP_W18_LRE*_B3_QUEUE.json 全部旗位（两臂、零抽样）+ 预设已知位：
#   LRE117_T04/T12/T14/T22/T34、LRE121_T06/T21/T28/T39、LRE146_T20/T38、LRE138 全部 severe 旗位；
# 另读 6 位两段同场回避样本（LRE117/121/138 各 2）。
# 逐位填 refs/.../long_probe_20261030/LP_W18_B3_READRESULT.json（schema 同 LP_W14J_B3_READRESULT.json）：
#   {pair_id, judge_flag, personal_read, verdict ∈ {counted_severe_candidate_S/U,
#    counted_severe_parent_S/U, rejected, downgraded, not_instantiated, evasive_flag}}
# G1 账：cand_S < par_S? cand_U < par_U? 总账 cand < par? 两族是否都实例化?
```

### 7.9 verdict → 账本

```bash
# 1) 写 refs/.../long_probe_20261030/W18_UCSUP_VERDICT_20261102.md：
#    门逐项、逐篇配对、双族 severe 账、两段同场占比、回避样读、常驻基线标注、诚实注记、下一步；
# 2) 在 logs/decisions.md 追加本轮失败归因与保留/淘汰；
# 3) 账本自动落盘：logs/auth_budget/AUTH-W18-20261102-01.jsonl、
#    AUTH-W18R-*.jsonl、logs/w18_ucsup_live_20261102.{DONE,RESULT,REPORTS}.json；
# 4) RESEARCH_STATE/TASK_QUEUE 由主控在终审后更新（本设计不预改注册表）。
# 5) KEEP 才排 §3 去一法（240 attempts）；ELIMINATE 则保留 S 段代码与本文，不开补跑。
```

---

## 8. 冻结声明

- 冻结：G1–G4 定义与口径、四篇素材与角色、320 attempts 与 caps、两段预算与顺序、臂映射、判决表。
- 可登记修订（不影响判据）：实现 bug、单测补充、notes 字段拼写；任何改变检测语义或段预算的改动 → 新设计版本，不在本批生效。
- 本文不批准任何真实调用；派发以 §7.3 的授权单与 §7.4 的 precheck 为准。
