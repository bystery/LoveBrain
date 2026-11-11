# AUDIT_HC2_E431_E460 — Independent Auditor HC2 批报告

> 审计人：HC2（Independent Auditor，HOLDOUT 专属）｜ 日期：2026-11-10
> 隔离 ack：`refs/benchmark_v1_20261107/phase3/isolation_acks/GIA-20261110-AUDITOR-HC2.json`
> 审计范围：E431–E460 共 30 篇 HOLDOUT 故事（T03 深聊/价值观 ×20 = E431–E450；T04 主动推进/邀约 ×10 = E451–E460）
> 全程零模型调用（确定性 Python 脚本机械校验 + 逐篇通读五件套）

---

## 0. 隔离声明

- 本审计会话为 HOLDOUT 专属独立审计，**仅读取金库** `D:/lovebrain-arena_holdout_vault/wave6_holdout/E431..E460/` 内的 episode.json / reference.json / audit.md / timeline.json / exposure.json / runner_episode.json，以及 `refs/benchmark_v1_20261107/phase3/NAME_POOL_V1.json`（用于人名一致性核对）。
- 开工 ack 已写入 `refs/benchmark_v1_20261107/phase3/isolation_acks/GIA-20261110-AUDITOR-HC2.json`，含三项声明：`no_dev_read`、`no_selection_read`、`no_logs_candidates_read`，`holdout_vault=true`、`acknowledged=true`。
- **未读取**：`wave4_dev/`、`wave5_selection/` 任何文件、`logs/`、`candidates/`、GOLD_SIDECAR、其他审计报告（C1–C4 / HC1 / HC3 / HC4）、作者/标注会话正文。
- 工具：自编确定性 Python 脚本 `D:/lovebrain-arena/_audit_hc2/hc2_audit.py`（以 `python -I` 运行，全程零模型调用）；脚本独立于金库目录，路径作为参数传入，不从金库目录加载任何代码。
- 机械可读结果：`D:/lovebrain-arena/_audit_hc2/hc2_results.json`。

---

## 1. 逐篇判定表

| 篇目 | 专题 | HER / ME | 轮数 | intent轮 | note轮 | may_decline | 判定 | 缺陷编号 |
|------|------|----------|------|----------|--------|-------------|------|----------|
| E431 | T03 | 双风吟 / 闻北辰 | 12 | 4 | 2 | 2 | **PASS** | — |
| E432 | T03 | 莘露白 / 党东启 | 12 | 3 | 1 | 3 | **PASS** | — |
| E433 | T03 | 翟青瓷 / 谭南溟 | 12 | 2 | 1 | 2 | **PASS** | — |
| E434 | T03 | 贡素锦 / 劳西窗 | 12 | 2 | 1 | 2 | **PASS** | — |
| E435 | T03 | 姬锦书 / 申中庭 | 12 | 2 | 2 | 2 | **PASS** | — |
| E436 | T03 | 扶韶华 / 段上善 | 12 | 2 | 2 | 2 | **PASS** | — |
| E437 | T03 | 光清芷 / 柴下问 | 12 | 2 | 2 | 2 | **PASS** | — |
| E438 | T03 | 瞿菀青 / 阎左迁 | 12 | 2 | 3 | 2 | **PASS** | — |
| E439 | T03 | 充溯洄 / 芮右军 | 12 | 2 | 2 | 2 | **PASS** | — |
| E440 | T03 | 羿汀兰 / 靳前度 | 12 | 3 | 2 | 2 | **PASS** | — |
| E441 | T03 | 汲鹿鸣 / 邴后觉 | 12 | 3 | 3 | 2 | **PASS** | — |
| E442 | T03 | 糜鹤唳 / 松先觉 | 12 | 3 | 3 | 2 | **PASS** | — |
| E443 | T03 | 秋雪鸿 / 宓后悟 | 12 | 3 | 3 | 2 | **PASS** | — |
| E444 | T03 | 郗云归 / 栾前尘 | 12 | 3 | 3 | 2 | **PASS** | — |
| E445 | T03 | 钭溪桥 / 丁往事 | 12 | 3 | 3 | 2 | **PASS** | — |
| E446 | T03 | 贲舟横 / 邓旧梦 | 12 | 3 | 3 | 2 | **PASS** | — |
| E447 | T03 | 杭夜阑 / 洪新知 | 12 | 3 | 3 | 2 | **PASS** | — |
| E448 | T03 | 包降霜 / 诸故人 | 12 | 3 | 3 | 2 | **PASS** | — |
| E449 | T03 | 左露寒 / 石新友 | 12 | 3 | 3 | 2 | **PASS** | — |
| E450 | T03 | 崔海棠 / 吉长风 | 12 | 3 | 3 | 2 | **PASS** | — |
| E451 | T04 | 钮丁香 / 戈万里 | 12 | 3 | 3 | 3 | **PASS** | — |
| E452 | T04 | 庞栀子 / 葛千寻 | 12 | 3 | 3 | 3 | **FAIL** | HC2-F01 |
| E453 | T04 | 奚紫苏 / 范百川 | 12 | 2 | 3 | 3 | **FAIL** | HC2-F02 |
| E454 | T04 | 郎半夏 / 昌九霄 | 12 | 2 | 3 | 3 | **FAIL** | HC2-F03 |
| E455 | T04 | 苗连翘 / 花三叠 | 12 | 2 | 3 | 3 | **FAIL** | HC2-F04 |
| E456 | T04 | 方沉香 / 俞七弦 | 12 | 2 | 3 | 3 | **FAIL** | HC2-F05 |
| E457 | T04 | 任琥珀 / 袁五柳 | 12 | 2 | 3 | 3 | **PASS** | — |
| E458 | T04 | 柳玳瑁 / 唐四明 | 12 | 2 | 3 | 3 | **PASS** | — |
| E459 | T04 | 罗翡翠 / 薛八荒 | 12 | 2 | 3 | 3 | **FAIL** | HC2-F06 |
| E460 | T04 | 雷珊瑚 / 贺一苇 | 12 | 2 | 3 | 3 | **FAIL** | HC2-F07 |

**批次结论：23 PASS + 7 FAIL / 30 total。0 PWN。**

---

## 2. 缺陷明细（7 FAIL）

全部 7 篇 FAIL 归同一根因：**reference.json 的 required_memory_atoms 出现"幽灵来源"原子**——
`source.kind=initial_kb`、`source.key=<某键>`、`source.quote=<某句>`，但该 key 在 episode.json
的 `initial_kb` 中**不存在**，且该 quote 在 initial_kb 全文与全部 HER/ME 消息文本中**逐字不可找**。
所有 10 个幽灵原子均带 `required_this_turn=true`，即评分侧将据此强制候选确认一条**从未被任何通道陈述过**
的事实——属最重一等的完整性缺陷（quote strict 与 required 来源铁律双重违反）。

### HC2-F01 · E452《室内攀岩馆搭档》（1 原子）

| 原子 ID | 声明 key | 声明 quote | required_this_turn | 核验 |
|---------|----------|-----------|--------------------|------|
| E452_T11_A03 | `helmet_boundary` | 不跟不戴头盔的人去野外 | **true** | key 不在 KB（KB 实有 belay_partner/her_project/gym_schedule/finger_history/outdoor_crate 5 键）；quote 不在 KB 全文、不在任何消息 |

### HC2-F02 · E453《城市骑行队周末活动》（3 原子）

| 原子 ID | 声明 key | 声明 quote | required_this_turn | 核验 |
|---------|----------|-----------|--------------------|------|
| E453_T02_A03 | `upgrade_habit` | 升级装备前要问三个人 | **true** | key 不在 KB；quote 逐字不可找 |
| E453_T03_A02 | `rain_boundary` | 下雨不骑 | **true** | key 不在 KB；quote 逐字不可找 |
| E453_T07_A03 | `long_distance_boundary` | 超过五十公里的路线要先评估再报 | **true** | key 不在 KB；quote 逐字不可找 |

注：E453 audit.md 未登记这三个 key/quote 的任何处置（grep 0 命中）——幽灵原子未被发现。

### HC2-F03 · E454《剧本杀桌游拼车群》（2 原子）

| 原子 ID | 声明 key | 声明 quote | required_this_turn | 核验 |
|---------|----------|-----------|--------------------|------|
| E454_T06_A03 | `phone_boundary` | 不跟玩手机的人拼车 | **true** | key 不在 KB；quote 逐字不可找 |
| E454_T10_A03 | `emotional_boundary` | 情感本结束不马上聊剧情要缓一缓 | **true** | key 不在 KB；quote 逐字不可找 |

### HC2-F04 · E455《注册会计师考试备考搭档》（1 原子）

| 原子 ID | 声明 key | 声明 quote | required_this_turn | 核验 |
|---------|----------|-----------|--------------------|------|
| E455_T12_A04 | `biscuits` | 偶尔带自制饼干到图书馆 | **true** | key 不在 KB；quote 逐字不可找 |

### HC2-F05 · E456《社区共享菜园种植搭档》（1 原子）

| 原子 ID | 声明 key | 声明 quote | required_this_turn | 核验 |
|---------|----------|-----------|--------------------|------|
| E456_T03_A03 | `frost_shelter_skill` | 木工活好搭的防霜棚比买的结实 | **true** | key 不在 KB；quote 逐字不可找 |

### HC2-F06 · E459《城市马拉松训练营搭档》（1 原子）

| 原子 ID | 声明 key | 声明 quote | required_this_turn | 核验 |
|---------|----------|-----------|--------------------|------|
| E459_T03_A04 | `knee_boundary` | 膝盖不舒服绝对不硬跑 | **true** | key 不在 KB；quote 逐字不可找 |

### HC2-F07 · E460《城市陶艺工坊体验课搭档》（1 原子）

| 原子 ID | 声明 key | 声明 quote | required_this_turn | 核验 |
|---------|----------|-----------|--------------------|------|
| E460_T10_A03 | `perfectionism` | 审美完美主义，瓶口不齐会推倒重来 | **true** | key 不在 KB；quote 逐字不可找 |

**根因归纳**：7 篇全为 T04 邀约专题、全为 `initial_kb` 列表型 schema（E451–E460 共用）。
标注者在构造 boundary/skill 类原子时，把"应写入 KB 的约束"当成了"已写入 KB 的事实"——
key 与 quote 均为标注侧的设想，未在 episode.json 落地。修复路径有二选一：
（a）在 episode.json 的 initial_kb 增补对应 key/value（需作者会话改题，按封板规则走）；
（b）将原子 source.kind 从 `initial_kb` 改为 `message` 并指向真实陈述轮，或将 quote 改写为
episode 内可逐字找到的表述（标注侧修正）。**任一修复须重跑本审计复验。**

幽灵原子总数：10 / 972 原子（0.99%），但全部带 required_this_turn=true，影响不可低估。

---

## 3. 机械完整性验算记录

### 3.1 JSON 可解析性
- 全 30 篇 × 5 JSON 文件（episode/reference/timeline/exposure）+ runner_episode = 180 个 JSON 文件，
  `json.load` 全部成功，0 解析错误。

### 3.2 schema 双形态识别（关键发现）
- **E431–E450（T03，20 篇）**：`initial_kb.me/her` 为字符串；message 键为 `{role,text,ts}`，
  role ∈ {HER,ME}。
- **E451–E460（T04，10 篇）**：`initial_kb.me/her` 为对象数组 `[{key,value,source}]`；
  message 键为 `{sender,text,timestamp}`，sender ∈ {her,me}（小写）。
- 两套 schema 均为合法变异（runner 已支持双形态），机械校验按各自白名单通过。
- message 键白名单：T03 段 `{role,text,ts}`、T04 段 `{sender,text,timestamp}`——0 违例。

### 3.3 结构校验（17 项逐篇）
| 检查项 | 通过/30 | 说明 |
|--------|---------|------|
| 01 名字一致性（vs NAME_POOL holdout_assignment） | 30/30 | HER/ME 名逐篇与池分配完全一致 |
| 02 轮数=12 | 30/30 | — |
| 03 case_id 唯一且格式 `E\d+_T\d+` 且属本篇 | 30/30 | 360 轮全合规 |
| 04 turn_type=reply | 30/30 | 360 轮全 reply |
| 05 gold={} | 30/30 | 360 轮恒空 |
| 06 clock 单调不减 | 30/30 | 同轮 ts≤clock、同轮 ts 不减、跨轮 clock 单调——0 违例 |
| 06b ts/clock 一致 | 30/30 | — |
| 07 may_decline ≥ 2 | 30/30 | T03 段多为 2、T04 段多为 3 |
| 08 intent 2–4 轮 | 30/30 | — |
| 09 note 1–3 轮 | 30/30 | — |
| 10 禁关键词 0 命中 | 30/30 | BANNED 表（考察/观测点/评分/考点/本题/测试点/与上轮不同/evaluation point/grading/rubric/探针/测试用例/设计意图/考察点/评分点）全 0 命中 |
| 11 quote strict（原子 quote 逐字回验） | **23/30** | 7 FAIL 见 §2，共 10 幽灵原子；其余 962 原子 0 失败 |
| 12 required 来源早于当前轮 | 30/30 | message-source 原子的 source.case_id 轮号 ≤ 消费轮——0 未来来源 |
| 13 runner_episode.json hash==episode.json hash | 30/30 | md5 逐字节一致 |
| 14 audit.md 八节齐全（`## 0.`–`## 7.`） | 30/30 | — |
| 16 跨轮回指≥2（≥1 长回指 dist≥4） | 30/30 | 混合检测：memory-atom 跨轮来源 + 叙事层 4-gram CJK 重叠；最弱 E444 仍有 3 pair / 2 long |
| 17 主角名不属 third_party_pool | 30/30 | — |

### 3.4 场景域唯一性（§15）
- 30 篇 domain 全部唯一，0 重复。T03 段 20 篇覆盖断舍离/辞职创业/选房/育儿/远程vs坐班/随礼/
  私教/家族群/囤货/养猫/流浪猫TNR/加装电梯/二手书/团建/体检/智能手机/跑步配速/菜市场/旅行攻略/
  爱好变职业；T04 段 10 篇覆盖合唱团/攀岩/骑行/剧本杀/注会备考/共享菜园/二手市集/流浪猫救助/
  马拉松训练营/陶艺工坊——0 撞域。

### 3.5 跨篇名污染（§17）
- **主角名跨篇污染**：0。E431–E460 每篇 HER/ME 名均唯一分配、不重复出现在其它篇作主角。
- **third_party_pool 复用**（允许低频跨篇）：10 个池内名被复用，最多 4 篇（"老桑"见于 E434/E448/E453/E457），
  均为配角身份、不冒用主角名——合规。复用清单：
  老桑(4)、小葳(3)、老卞(3)、老桂(3)、老关(2)、小蒲(2)、小闾(2)、老茅(2)、老闵(2)、阿荞(2)。

### 3.6 exposure 校验
- 全 30 篇 `real_user_material=false`、`experiments_used=[]`、`dev_exposed=false`、`status=authored_pending_review`。
- primary_topic 分布：T03×20（E431–E450）+ T04×10（E451–E460）——与派发计划 H04/H05/H06 一致。
- readers=[author] 全篇一致。

### 3.7 timeline 校验（抽样全 30）
- turn_clocks 数 = 轮数（12=12）：30/30。
- message_index 数 = episode 消息总数：30/30（含 T04 多消息轮如 E451_T07 的 m0+m1）。
- events.source_turn 存在于 turns：30/30。

---

## 4. 跨轮回指（§16）补充说明

本审计对"跨轮回指≥2（1 短 + 1 长≥4）"采用**混合检测**，避免单一指标漏判：
1. **memory-atom 跨轮来源**：reference.json 中 required_memory_atoms 的 source.case_id ≠ 消费轮且非
   initial_kb 的原子，按 (消费轮, 来源轮, 距离) 记。
2. **叙事层回指**：当前轮 HER 文本与更早轮 HER 文本的 4-gram CJK 重叠（过滤出现在 >3 轮的 boilerplate），
   捕捉"零出资零补偿"（T07）回扣"一二楼零分摊零补偿也零出资"（T06）这类部分重述。

E442 的判例：atom 跨轮仅 1 对（T09→T02，dist=7），但叙事层另有 5 对（含 T07→T06 dist=1、
T12→T04 dist=8），合计 6 pair / 3 long——PASS。audit.md §2 亦明文登记 T07 短回指 d=1、
T09 长回指 d=5、T12 回指 T04，与机械检测一致。

全 30 篇 backref 分布：atom 跨轮 1–16、叙事 1–16、pair 3–22、long 1–12。最弱 E444（3 pair / 2 long）
仍达标。**0 篇因 backref 不足 FAIL。**

---

## 5. 时间与算术（抽样核验）

- T03 段时间窗多为 2026-11-10 至 2026-11-16（4–7 天 12 轮），clock 单调递增，无跨日倒挂。
- T04 段 E451 时间链 2026-11-09 20:15 → 2026-11-15 09:40（7 天 12 轮），含演出日 2026-11-14
  （周六）密集 8 轮（T05–T12），clock 在演出当日按 09:50→15:40→16:15→18:55→20:30→21:05→23:10
  单调递增——与"五周年演出当天"剧情一致。
- E451 T10"下周三开协调会"型相对日期推算未在本批普遍出现；E442 T10"下周三"=2026-11-18（周三），
  与 T11"周三协调会"一致（audit.md §2 已登记）。
- 全 30 篇 ts ≤ clock、同轮 ts 不减、跨轮 clock 不减——0 违例。

---

## 6. 出题痕迹与隔离完整性

- BANNED 表扫描全 30 篇 episode 可见输入（messages/note/intent/clock）：**0 命中**。
- feedback 字段为出题侧叙事账，不进候选可见输入（runner 已消毒）——合规。
- gold={} 恒空，无答案明文泄漏。
- exposure 全篇 `dev_exposed=false`、`experiments_used=[]`、`real_user_material=false`——
  HOLDOUT 金库隔离边界完整。
- runner_episode.json 为 episode.json 逐字节副本（md5 一致，30/30）——runner 投喂与审计对象同源。

---

## 7. 批次结论与处置建议

### 结论
- **23 PASS + 7 FAIL / 30**。7 FAIL（E452/E453/E454/E455/E456/E459/E460）全为同一根因：
  reference.json 的 required_memory_atoms 出现 10 个"幽灵来源"原子（`source.kind=initial_kb`，
  key 不在 KB、quote 逐字不可找、required_this_turn=true）。
- 其余 16 项检查（名字/轮数/case_id/turn_type/gold/clock/may_decline/intent/note/禁词/
  required 来源/runner hash/audit 八节/场景域/主角名/隔离）全 30 篇通过。
- 场景域 30/30 唯一；主角名 0 跨篇污染；third_party 复用合规；audit.md 30/30 八节齐全。

### 处置建议
1. **7 FAIL 篇须修复后复验**。修复路径（二选一，均须在标注/作者会话内完成，不触碰 candidates/logs）：
   - (a) 在 episode.json 的 initial_kb 增补对应 key/value（属改题，须按封板规则登记新题版本）；
   - (b) 将原子 source.kind 改为 `message` 并指向真实陈述轮，或将 quote 改写为 episode 内可逐字找到的表述。
2. 修复后须重跑 `hc2_audit.py` 复验 quote_strict 与 required_source_earlier 两项。
3. **23 PASS 篇可进入 HOLDOUT 验收队列**，不受 7 FAIL 影响。
4. 幽灵原子集中在 T04 段（E452–E460 的 7/10），建议对 E451–E460 全段做一次 boundary/skill 类
   原子的 key 落地巡检（即便 E451/E457/E458 本批 PASS，同段同 schema 风险同源）。

### 不确定性与边界
- 本审计为机械+通读，未对 protected_expressions/forbidden_factual_claims 的语义合理性做逐条人工裁判
  （属评分侧 Phase 3 校准范围，非本审计 17 项之内）。
- backref 检测为机械近似（4-gram 重叠 + atom 来源），可能漏判极少数语义级回指；但全 30 篇均显著
  超阈值，判定稳健。
- quote strict 对 initial_kb 列表型 schema 的 key 匹配已逐键核验，对字符串型 schema 的全文匹配
  已逐字核验——两种形态均覆盖。

---

*报告结束。机械可读结果见 `D:/lovebrain-arena/_audit_hc2/hc2_results.json`；审计脚本见
`D:/lovebrain-arena/_audit_hc2/hc2_audit.py`；隔离 ack 见
`refs/benchmark_v1_20261107/phase3/isolation_acks/GIA-20261110-AUDITOR-HC2.json`。*
