# W22-01 全量批判决 REV2 — uc_attr vs uc（2026-11-04，主会话独立审计修订）

> 本文件取代同日 10:27 的 `W22FULL_VERDICT_20261104.md`（REV1，保留存档）。
> 背景：双实例并行收口。主会话（B3）在评审完成后对 REV1 做逐门红队审计，发现
> **G3 门读数与 live 评分数据直接矛盾**及 **G1 亲读账与 judge 旗未分离**两处实质错误，
> 依"severe 必须 B3 亲读、judge 不可信"的不可协商约束出具本修订。
> 数据底座不变：BATCH-W22-FULL-20261104 live 464/464 零失败；评审 232 main + 22 swap
> supplement 已完成（`LP_W22FULL_NINEDIM_REVIEW.json` / `_QUOTE_CHECK.json`）。

## 判决：KEEP_NO_PROMOTE（取代 REV1 的 DUAL_CHAMPIONS_RETAINED）

- **归因锚定机制有效性成立**（主假设正面，且比 REV1 更强：A 族 cand 0 vs par **2**）。
- **但契约门 FAIL + swap 翻转**，双重阻断：uc 不让位；uc_attr 不得作为组件直接继承。

---

## 对 REV1 的三处更正

### 1. G3 契约门：FAIL（REV1 记 PASS，读数错误）

REV1 声称"契约 veto：cand 0 ≤ par 0 + 1"。**live scoring.json 实测：par veto 19 / cand veto 29**
（逐位清单 `W22FULL_G3_VETO_20261104.json`）。REV1 的 0/0 疑似把"空回复双臂 0"误当 veto 计数
（空回复确实为 0，但判据中 veto 与空回复是两个分列项）。

构成拆解（`W22FULL_G3_DETAIL_20261104.json`）：

| veto 类型 | par | cand | 版本敏感性 |
|---|---|---|---|
| json_schema_broken | 11 | **22** | 无（对输出文本确定性判定） |
| illegal_source_id | 8 | 9 | 部分（FK-SCORE-01 修复前评分器产物，如 m10/m_src0 位；即使全部剔除仍 FAIL） |

抽查坐实（3 位亲验原文）：cand 出现**未声明槽位名**（`bad_your` E157_T03、`bad_girl` LRE146_T06），
par 为空字符串槽（`bad_evil:""` LRE121_T09）。json_schema_broken 翻倍（11→22，n=232/臂）显著，
**且跨批复现**：W18B 中 uc_supp（另一个追加注入块候选）同样契约劣于 uc 父本。

→ **G3 = FAIL（cand veto 29 > par 19+1）。** 契约失败率 4.7%→9.5%，属 AGENTS §5
"任一维度显著退化即阻断晋级"的契约退化。

### 2. G1 severe 总账：以 B3 亲读账取代 judge 旗（`LP_W22FULL_B3_READRESULT.json`）

REV1 直接采用 judge 旗（cand 4 / par 11）并把 15 位全部计入族分布，未对空注释旗做缺陷级核验。
主会话已对 **15/15 位全量亲读**（原文逐字核验，含 copy.json 全故事反查）：

| 判定 | cand（4 旗） | par（11 旗） |
|---|---|---|
| CONFIRMED | **3**：E157_T06（暂停→"算废了"，U）、LRE121_T20（"免疫科"虚构+"默认排上"，U）、LRE146_T16（"六号中午"坐实+"周六"错时，U+time） | **5**：E164_T04（臆造"÷14次"分母，U）、E170_T07（"工期不拖"被重开，U）、LRE117_T04（"不算司机"110 被算成 165，A-adj）、**LRE121_T31（"我不吃香菜"主体错置，A）**、LRE121_T39（"看心情"错置为虾饺许诺+无据定"下周末"，A） |
| DOWNGRADED | 1：LRE146_T24（REV1 计 U 族 severe——"那七百块钱"在 HER 消息出现两次（turn2/turn3），引文幻觉不成立，与 W18B 同位误旗一致） | 6：E157_T02、LRE117_T12/T14/T34、LRE121_T01/T19（judge 空注释旗，亲读无缺陷；LRE117_T12/T14/T34/T01/T19 的 parent 文本忠实或有 initial_kb 支撑） |

**关键更正**：REV1 把 LRE121_T31 归入"无据确定化族 9 例"。亲读判定其为 **A 族（归因错位）直接命中**：
parent bad_boy"到时候看你还记不记得**我**不吃香菜"——`copy.json` 全故事唯一香菜句是 HER 的
"老板都记得**我**不吃香菜"，特征主体被错置到 ME。

### 3. G1 门读数（修正后）

- **总数门**：cand 3 ≤ par 5 ✅
- **A 族（主假设核心）**：cand **0** < par **2** ✅（REV1 记 par 1，修正后更强）
- **U 族**：cand 3 > par 2 ⚠️（候选侧无据确定化未受益于锚定块——设计如此，锚定只管"谁做的"）
- **每域 cand ≤ par**：域级（>24：cand 2 vs par 3；≤24：cand 1 vs par 2）✅；
  故事级例外如实披露：E157（cand1/par0）、LRE146（cand1/par0）两故事为 cand-only severe
- **不新增故障族**：✅（cand 3 位全属既有 U 族）
- **LRE121_T39 直接验证保留**（REV1 该段无误）：par 错置虾饺归属，cand 分账正确，锚定块在场
  （attr_cu=103）；W21FULL 同位 cand 方向倒置 → 本批修正，跨批方向一致
- **E191_T08 双臂零窃取**：保留（跨批归因位非退化）

---

## 其余各门（与 REV1 一致或微调）

| 门 | 判定 | 读数 |
|---|---|---|
| G2 九维 | PASS | main-only 口径（n=199–203）：goal +0.103 / fact +0.123 / attrib +0.094 / time +0.065 / boundary +0.065 / **memory +0.193** / interact +0.049 / init +0.099 / emotion +0.015——全正；REV1 混入 swap 序 dims（n=225），方向不变，本判决按 W18B main-only 冻结口径重算 |
| G4 成本 | PASS | prompt +1.77%（821,643→836,192）；232 对非触发轮 token 零差异；0 额外调用（每轮恰 1 次调用，232/232 对核）；触发面 42.7%（99/232）∈(10%,90%] |
| G5 swap | FAIL（阻断单冠军） | 翻转 7/22（E164_T08、E212_T08、LRE117_T08、LRE121_T16、**LRE121_T20**、**LRE146_T16**、LRE146_T20）；2 全翻转恰为 cand severe 位 |
| 引文硬校验 | 记录 | strict 79.1%，no_hit 0，severe 位 key_quote 全有据（`LP_W22FULL_QUOTE_CHECK.json`） |
| 评审质量 | 降权标注 | 7 UNPARSEABLE + 评审引文幻觉风险旗若干（T20 note 引文检查器未扫 reply 文本，误报幻觉——亲读确认"免疫科"确在 cand 回复中）；severe 以 B3 账为准（已执行） |

---

## 机制判定与处置

1. **uc 保持 >24 轮域冠军**（不让位）。
2. **uc_attr 判 KEEP_NO_PROMOTE**：
   - 归因锚定块对目标族有效（A 族 par 2 → cand 0；memory +0.193 最大正差；prompt +1.77%）；
   - 但契约回归显著（veto 29 vs 19，槽位名漂移新失败模式）+ swap 不稳定，按 §5 阻断组件直接继承。
3. **新故障族登记（KNOWN_FAILURES）**：追加注入块 → 输出契约漂移（槽位名漂移 bad_your/bad_girl 型；
   W18B uc_supp 与本批 uc_attr 双批复现，contract 失败率均约翻倍）。该族正是 guard（输出契约守卫，
   W17c ≤24 域组件）的设计靶点。
4. **预登记后继候选**：`uc_attr_guard`（归因锚定 + 契约守卫单变量合并，沿 W19-C1 的 guard 合并工艺），
   在 A 族实例化素材上验证"锚定收益保住 + 契约回归修复"。未预登记不得开跑。
5. 判决表主张范围：本批**不得宣称**"归因锚定降低 severe 总数"（总账 cand 3 vs par 5 的差主要来自
   judge 旗噪音与 U 族双臂分布，n 小）；可宣称的仅有 A 族方向性证据 + 九维/成本非退化。

## 诚实边界

- 双实例并行收口：REV1（并行实例 10:27）与本判决（主会话 11:00 前后）并存，以本文件为准；
  分歧逐条列证据，可复核（G3 见逐位 veto 清单；G1 见 B3 亲读账 + copy.json 原文）。
- judge 第四批空注释 severe 6/11——B3 降权纪律再次必要；评审端 severe 判定引文硬校验
  （W20-06）仍是评审基建欠账。
- swap n=22 探索性；契约翻倍的因果（锚定块 vs 采样噪声）未做消融，只有跨批一致性佐证。
- LRE146_T24 的 judge 旗（fact=0，无注释）与原文证据矛盾，系 judge 不可靠第 4 批次复现案例。

## 边界

cue 词表冻结不改；不碰 b_line/封板题；本判决零新增模型调用（全部读数来自既有落盘数据）。
