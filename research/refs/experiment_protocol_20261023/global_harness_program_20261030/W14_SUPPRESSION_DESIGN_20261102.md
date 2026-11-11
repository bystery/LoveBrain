# W14 机制候选 window_expand_b2 设计：被取代口径主动压制（2026-11-02，判据跑前冻结）

日期：2026-11-02 ｜ 故障素材：LD1 cand 确认 severe LRE117_T14（复活已作废"五十五"口径+
虚构两人+坐实未定集合时间）+ CF1 E152 partial（"外借-归还"框架被"放你那儿/先不急着还"弱化）
基座：window_expand_b（LD1 KEEP，>24 轮域冠军）｜ 变异层：M2（检索注入）单变量

## 1. 主假设（可证伪）

**H_SUP_main**：在扩窗装配上增加"被取代口径压制段"——运行时对全部历史做确定性更正检测
（复用 ledger_rules 的 supersede 纯函数，零额外模型调用），把检测到的
"旧口径→新口径"对以 ≤240 CU 的【口径基线】段注入在画像之后、历史之前（高优先前置），
能显著降低"复活已作废口径"与"坐实未定项"类 severe（≥1 例→0），同时 ≤24 轮域九维
最差维 ≥−0.3、severe 不升、prompt ≤ window_expand_b+8%。

**H_SUP_null**：压制段无效（同类 severe 仍 ≥1）或引发新 severe（如压制段被模型当成
"当前有效事实"复述旧值）→ 候选淘汰，不调参重跑。

## 2. 机制规格（delta 仅一处）

- 复制 window_expand_b → `candidates/window_expand_b2/`；其余装配逐字不动。
- 新增 `_suppression_pass(h)`（每轮 run_turn 内、装配 parts 前调用）：
  1. 对 h["events"] 全量跑 ledger_rules.extract_facts（规则抽取，零调用）；
  2. 跑 update_pass（supersede/cancel 判定：更正线索+bigram_ov≥0.42+数字冲突）；
  3. 收集全部 superseded/cancelled 条目 → 行格式
     `- 「{旧值截断≤24}」已作废（{turn}说）→ 现行：「{新值截断≤24}」`；
  4. 未定项保护：对含"如果/要是/还不确定/再看"线索的 clause 不入压制段（防把未定坐实——
     LRE117_T14 的"八点"错误方向是坐实未定，压制段只处理"已定→被改"，不处理未定）；
  5. 段标题 `# 【口径基线：以下旧说法已作废，勿再沿用】`，≤240 CU，超限按新→旧丢；
     无 superseded 条目则整段省略（≤24 轮短剧多数轮为空段，成本近零）。
- snapshot/restore 增加 `suppressions` 字段（update_pass 结果缓存，每轮重算亦可，取缓存）。
- notes 增加 `supp_cu=N supp_pairs=M`。

## 3. 批计划（两段，判据冻结）

### 3.1 回归批（≤24 轮）
- E061（EXPOSED 复用）：window_expand_b2 vs r9_slim，48 attempts，AUTH 另册。
- 门：九维最差维 ≥−0.3、severe(b2) ≤ severe(parent)（亲读账）、prompt ≤ r9_slim；
- 同时对拍 window_expand_b 存档（E061 上 b2 ≈ b 行为，压制段近空）。

### 3.2 判决批（>24 轮新长剧池）
- 池：10 篇 SEALED 长剧（LRE121/123/126/127/128/138/139/142/145/148/… 取 4 篇域分散）。
- 臂：window_expand_b2 vs window_expand_b（冠军对冠军，隔离单变量）+ r9_slim 锚（可选半量）。
- 规模：4 篇 ×~144 轮 ×2 臂 ≈ 288 attempts（不含锚）；cap 340。
- 判据：
  - KEEP：同类 severe（复活已作废口径/坐实未定）b2 = 0 且九维最差维 ≥−0.5 且
    T25-32 真实保持 ≥0.85 且 severe 总账 b2 ≤ b（亲读）；
  - 淘汰（H_SUP_null）：同类 severe ≥1 或 severe 总账 b2 > b；
  - 不确定带：同类 severe=0 但九维最差维 <−0.3 → 报"压制有效但互动面代价"，不补跑。

## 4. 成本与工作量

- 实现：mechanism.py delta ~70 行（suppression pass + notes）；ledger_rules 复用不改；
  params/IDENTITY/MECHANISM ~40 行。
- 回归 48 + 判决 ~288 attempts；评审另册。

## 5. 状态

设计冻结；实现即时进行，回归批派发前登记 AUTH。

## 6. §2.2 实现修订记录（2026-11-02，live 派发前；判据 §3 不变）

实现中发现冻结规格的三处真实缺口（对着银行故障素材 LRE117 原句验证），做受控修订：

1. **E1 中文数字**：r7 `_digits` 只认 ASCII，"五十五/八十"全盲 → b2 的 ledger_rules
   副本加 `_cn2int`，中文数字一并入数字集（通用能力，非素材特化）。
2. **E3 线索-数值分 clause**：更正口语常见"看错了，是五十八一张"——纯线索 clause
   不可分类被旧逻辑丢弃 → 含线索但不可分类的 clause 与同消息下一个含数字 clause
   逐字合并成一条事实；下一 clause 无数字则照旧弃。
3. **E4 通用口语取消/更正词 + 两类新行**：LRE117 实际取消句式（"五十五那页翻过去哈"
   /"就当彻底没存在过"/"说错了"）不在冻结词表，且与旧条目的 bigram overlap <0.30
   （值与其取消语句字面不连续），update_pass 全链打不中 → 词表补通用口语
   （翻篇/翻过去/不作数/不算数/不搞了/就当没…/说错），并新增两类直出行：
   - **值名取消**：clause 自含数字+取消线索 → `已声明作废：「{clause}」（T{x}）`
     （不经账本匹配，逐字引用，不虚构链接）；
   - **回归现行**：消息含变回/还按/恢复/回到线索 → 其数字 clause 出
     `现行口径以「{clause}」（T{x}）为准`。
4. **E2 引旧排除**："不是五十五，是五十八"形态下旧值出现在新文本中，数字冲突判定
   失效 → `_digit_conflict` 把新文本中「不是X」引述的数字从新值集剔除。
5. **压制段只算旧史**：当轮更正已在当前输入逐字可见，压制段对 prior events 计算，
   更正次轮起入段（无重复注入）。

以上均为实现层修订；§3 回归/判决判据与规模一字不动。机制级 sanity 测试
（8 轮合成序列含 LRE117 原句式）已通过：superseded 对、cancelled 对、值名取消、
回归现行、未定排除、预算守恒、快照往返字节一致、历史事件不被改写。
