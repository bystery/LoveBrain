# REPAIR_R1_E301-E316 — C1 审计发现项修复报告（repairer-R1，2026-11-08）

> 依据：`AUDIT_C1_E301-E316.md`（auditor-C1）。范围严格限定 E301-E316 的
> **episode.json / reference.json 内容级缺陷**；timeline.json / exposure.json / audit.md 未触碰。
> 开工 ack：GIA-20261108-REPAIRER-R1（已入 GENERATION_ISOLATION_LEDGER.json）。
> 修复纪律：clock/ts/轮序/case_id/may_decline/required_this_turn/unknowns/markers/acceptable_actions
> 一律未动（备份 deep diff 逐路径核验）；修复后全量逐字复验 470/470 PASS，ERRORS=0。

---

## 1. 修复清单表（发现项 → 位置 → before → after → 复验）

### MAJOR（C1 共 8 项；7 项内容级已修，1 项评分侧移交）

| # | 发现项 | 位置 | before | after | 复验 |
|---|---|---|---|---|---|
| M1 | MAJOR-1 E306_T10 "明天"与年会 11-28 错位 | E306/episode.json · T10 m0 text | 睡不着，**明天**主持稿背了八遍，就怕念到你那段卡壳。 | 睡不着，**后天就年会了**，主持稿背了八遍，就怕念到你那段卡壳。 | PASS（ts 2026-11-26 周四冻结 → 后天=11-28 周六年会 ✓；选 C1 选项 b，因 ts/clock 属冻结字段不可走选项 a） |
| M1s | MAJOR-1 连带 value 残留 | E306/reference.json · E306_T10_A01.value | HER 怕**明天主持**念到 ME 那段卡壳 | HER 怕**年会主持**念到 ME 那段卡壳 | PASS（同一错位在 value 的残留；quote「就怕念到你那段卡壳」不含改动段，未动；vt 2026-11-26 未动） |
| M2 | MAJOR-2 intent 与卡面"年会前夜"错置 | E306/episode.json · T07 intent.text | **年会前夜**H5出显示事故，帮我又快又不掉价地把场面圆过去 | **年会前一周**H5出显示事故，…（后同） | PASS（11-20 距 11-28 年会 8 天） |
| M2s | MAJOR-2 卡面同步 | E306/reference.json · E306_T07.current_action | **年会前夜**H5事故已由 ME 远程二十分钟改好… | **年会前一周**H5事故已由 ME 远程二十分钟改好… | PASS |
| M3 | MAJOR-3 E307_T12 "下个月圣诞市集"日历不成立 | E307/episode.json · T12 m0 text | 反正**下个月**诊所圣诞市集，我的摊子还缺个"苦力兼吉祥物"… | 反正**月底**诊所圣诞市集，我的摊子还缺个"苦力兼吉祥物"… | PASS（12-12 说"下个月"=2027-01 办圣诞市集不成立；改后与 A03 vt=2026-12 一致） |
| M3s | MAJOR-3 原子随动 | E307/reference.json · E307_T12_A03 quote / value | quote「**下个月**诊所圣诞市集」；value「**下个月**诊所圣诞市集、HER 给 ME 留摊位档期」 | quote「**月底**诊所圣诞市集」；value「**月底**诊所圣诞市集、HER 给 ME 留摊位档期」 | PASS（正文改动连带：quote 必须逐字命中新正文；value 仅同步错误两字保持 value/quote 自洽——C1 写"value 不变"，但正文改后 value 原样会与自家 quote 矛盾，故按"只改 value 错误部分"纪律处理；原子结构/来源定位/vt=2026-12 未动） |
| M3s2 | MAJOR-3 卡面与 unknown note 同步 | E307/reference.json · E307_T12.current_action / unknowns[0].note | 卡面「预约**下月**诊所圣诞市集」；note「她只说**下个月**，候选不得坐实具体日期」 | 卡面「预约**月底**诊所圣诞市集」；note「她只说**月底**，候选不得坐实具体日期」 | PASS（unknown 本体与 markers 未删未改语义） |
| M4 | MAJOR-4 E308 骑行日两说 + 闭店理由错日 | E308/episode.json · T04 m0 text（ts 2026-11-15 周日 09:30 冻结） | 肠粉没吃上，**那家周日不开门，周一才开**，我查岔了，这顿先欠着。不过**今天**下坡你在后面压着速度帮我挡风，我知道。… | 肠粉没吃上，**骑完过去人家刚卖完收摊**，我查岔了**周末的营业时间**，这顿先欠着。不过**昨天**下坡你在后面压着速度帮我挡风，我知道。… | PASS（取 C1 推荐 (a) 的正文侧变体：T04 保持周日晨间改为复述昨日骑行，昨天=2026-11-14 周六，与 T03"周六加场"、KB"观澜只能周六加场"一致；闭店理由改当日售罄收摊类（C1 给定示例措辞），与 T10"它每天都开门…上次查岔了"、T12 周日局"开门"全部相容；T03 未动） |
| M4s | MAJOR-4 卡面与 value 同步 | E308/reference.json · E308_T04.current_action / A01.value / A02.value | 卡面「（那家周日不开门、周一才开、自己查岔了）…点破今天下坡…」；A01「肠粉没吃上（那家周日不开门、她查岔了）、这顿先欠着」；A02「HER 点破今天下坡 ME 在后面压速度帮她挡风、她知道」 | 卡面「（骑完过去人家刚卖完收摊、自己查岔了周末营业时间）…点破昨天下坡…」；A01「肠粉没吃上（骑完过去人家刚卖完收摊、她查岔了周末营业时间）、这顿先欠着」；A02「HER 点破昨天下坡 ME 在后面压速度帮她挡风、她知道」 | PASS（A01/A02/A03 三个 quote 均不含改动段，逐字命中复核通过；vt 2026-11-15 未动） |
| M5 | MAJOR-5 E308×E313 HER 重名"姜宁" | E313/episode.json + E313/reference.json（E308 保留姜宁） | E313 HER=**姜宁**（与 E308 冲突） | E313 HER=**简宁**（连姓改，C1 建议名） | PASS（选 E313 改名：剧情牵连小——正文消息不含人名、无 quote 牵连、timeline 仅 1 处残留 vs E308 方案残留 10 处；改名贯穿 persona.her.name + initial_kb.me×2 + initial_kb.her×1 + reference 12 处 current_action，共 17 处；E301-E316 全篇 grep「简宁」唯一，E308 仍为姜宁且篇内一致） |
| M6 | MAJOR-6 E310_T03 "瞒了半个月"与 10-20→11-17=28 天矛盾 | E310/episode.json · T03 m0 text | …切了个息肉，病理良性，全家瞒了我**半个月**。 | …切了个息肉，病理良性，全家瞒了我**快一个月**。 | PASS（10-20→11-17=28 天=快一个月；与 T08"早知道一个月"、T05"瞒了一个半月"（辞职 10-10 起）互洽） |
| M6s | MAJOR-6 原子随动 | E310/reference.json · E310_T03_A02 quote / value | quote「全家瞒了我**半个月**」；value「全家瞒了她**半个月**，妈妈是昨天电话里自己说漏的（生日是半个由头）」 | quote「全家瞒了我**快一个月**」；value「全家瞒了她**快一个月**，妈妈是昨天电话里自己说漏的（生日是半个由头）」 | PASS |
| M7 | MAJOR-7 E311_T01_A02 gold 写错 ME 人名 | E311/reference.json · E311_T01_A02.value | …发球全下网，**周衍**（ME）当时说了'没事，热身'… | …发球全下网，**韩江**（ME）当时说了'没事，热身'… | PASS（E311 ME=韩江，与同卡 A03 一致；quote 为 HER 原话未动；E311 全篇 reference 无"周衍"残留） |
| M8 | MAJOR-8 affirm_markers 否定/疑问误伤（评分侧） | — | — | — | **移交评分侧**（见 §3；gold markers 按红线未动） |

### MINOR（C1 明确给出修法且属内容级的 2 项顺带修复）

| # | 发现项 | 位置 | before | after | 复验 |
|---|---|---|---|---|---|
| N3 | MINOR-3 E305 "十二月阳朔野爬"（T11）与"一月山里见"（T12）读法歧义 | E305/reference.json · E305_T12.current_action / T12_A02.value | 卡面「留了山里见或山下米粉的兜底选项」；value「HER 留了兜底：ME 档满的话在山下等她吃米粉也行」 | 卡面「留了山里见**（行程跨 12 月底至 1 月初）**或山下米粉的兜底选项」；value「…吃米粉也行**（阳朔行程跨 12 月底至 1 月初，'一月山里见'即返程段）**」 | PASS（取 C1 "卡 value 补一句"选项，消歧为跨年行程读法；正文与 vt=2027-01 均未动，无 quote 牵连） |
| N6 | MINOR-6 E310_T12 "下周三"（周一说）与 vt=2026-11-25 错位 | E310/reference.json · E310_T12_A01.valid_time | 2026-11-25 | 2026-12-02 | PASS（取 C1 给的 vt 侧选项：周一说"下周三"自然读法即下周三 2026-12-02，且 2026-12-02 实为周三；正文与 timeline（相对表述"下周三"）均不冲突） |

---

## 2. 正文改动项（episode.json 消息文本，共 4 条）与随动原子

| 消息 | 改动 | 随动 quote（引用该消息的原子） | 随动 value |
|---|---|---|---|
| E306_T10 m0 | "明天主持稿背了八遍" → "后天就年会了，主持稿背了八遍" | 无需随动（T10 三个原子 quote「就怕念到你那段卡壳」「就冲我比个叉」「比心不行，我接不住」均不含改动段，逐字命中复核通过） | E306_T10_A01 |
| E307_T12 m0 | "下个月诊所圣诞市集" → "月底诊所圣诞市集" | E307_T12_A03（quote 随动） | E307_T12_A03 |
| E308_T04 m0 | 闭店理由改"骑完过去人家刚卖完收摊，我查岔了周末的营业时间"；"今天下坡" → "昨天下坡" | 无需随动（A01「这顿先欠着」、A02「你在后面压着速度帮我挡风，我知道」、A03「包装袋我留着装链条了」均不含改动段） | E308_T04_A01、E308_T04_A02 |
| E310_T03 m0 | "全家瞒了我半个月" → "全家瞒了我快一个月" | E310_T03_A02（quote 随动） | E310_T03_A02 |

被改 4 篇（E306/E307/E308/E310）已按纪律做**全篇全原子**逐字复验（非抽样）。

---

## 3. 未修项与原因（移交清单）

| 项 | 归属 | 说明 |
|---|---|---|
| MAJOR-8 评分侧护栏 | 评分侧（lb_scorer.py C14/C15、gold_sidecar.py::_group_hit） | C1 主修法在评分侧：命中前加否定/疑问护栏（前 2 字窗口 含 不/没/别/吗/吧/呢/要是/如果/假如 不判坐实），pilot 前以 python -I 回归脚本验证。gold 侧可选最小修（E309_T01 markers 改断言式）未执行：markers/unknowns 属本角色红线保护区，且主修在评分侧。 |
| MAJOR-5 波次级重名 9 组 | 全库 dedupe 责任人 | E303↔E326 苏叶、E304↔E328 蒋一楠、E304↔E332 韩东、E305↔E318 沈聿、E309↔E324 周衍、E313↔E322 程野、E314↔E327 何蔓、E316↔E329 阮清、E320↔E338 韩澈（E308↔E313 本批已修）。 |
| timeline 4 处同步 | timeline 责任工具 | ① E306_T10 event「年会前夜约定台上卡壳暗号」→ 措辞改"年会前两天约定…"（ts 2026-11-26 23:30 保持）；② E307_T12 event「预告下月诊所圣诞市集」→「月底」；③ E308_T04 event「肠粉铺周日不开门改欠账」→「骑完过去铺子刚卖完收摊改欠账」；④ E310_T03 event「全家瞒半月」→「全家瞒快一个月」。 |
| audit.md 对应行 | audit 责任代理 | E308 audit.md T04 行（"周日不开门"表述）、E310 audit.md T03 行（"瞒半月"表述）需与正文同步；E316 audit.md 第 3 条（MINOR-11，遗留描述过期）一并交 audit 侧。 |
| MINOR 1/2/4/5/7/8/9/10/12 | 不修/他侧 | 1（E301 timeline 椰果记录性提前）归 timeline；2/4/5/8 无判分依赖、C1 未给修法；7（vt 口径统一说明）归标注侧文档；9（non-ISO valid_time 15 处）归评分侧确认 sidecar 容忍；10（exposure 缺 annotator 字段）归 exposure 责任工具；12（冻结轨迹预设类）C1 明示"沿用 B1 已登记说明，不必逐篇改"，归 scoring 侧。 |
| 附带笔误转告 | digest 维护方 | B1-5 核实属实但 digest 有一处篇目标签笔误："四十天→三十四天"天数链属 **E302**（考研倒计时），digest 误标为 E303。另：C1 报告头"401 个 gold 原子"与其 §4 自记分篇计数（E301-E308=256 + E309-E316=214 = **470**）不符，本次复验按 470 计，供 C1 侧更正。 |
| 定向复audit 建议 | 调度方 | C1 建议对 5 篇 FAIL（E306/E307/E308/E310/E313）做定向复audit（只查被修轮位 + quote 全量重跑）；本报告已提供全量复验结果可直接比对。 |

---

## 4. 交付前自查（python -I，脚本在仓库外 D:\tmp_backup_repairer_r1\verify_repair.py）

- **[1] quote 逐字复验**：E301-E316 全 16 篇全原子 **470/470 PASS**（E301-E308=256、E309-E316=214，与 B1/B2 自报完全一致）。
- **[2] 备份 deep diff**：与编辑前备份（仓库外）逐路径比对，改动路径**恰好等于** §1 清单所列 25 处；clock / ts / case_id / 轮序 / may_decline / required_this_turn / unknowns+markers（除 E307_T12 note 两字同步）/ acceptable_actions / status / subject / relation / protected_expressions **零变化**，无冻结字段命中。
- **[3] 定点断言**：11-26 周四且 11-28 周六（"后天"成立）；11-14 周六、11-15 周日（"昨天"成立、与 T03 同一骑行日）；10-20→11-17=28 天；2026-12-02=周三；各 vt/quote/value 精确值；E313 零"姜宁"且"简宁"在 E301-E316 唯一、E308 保留姜宁、E311 无"周衍"。全部通过。
- **[4] 星期词扫描**：被改 7 篇全部"周X"叙述与消息 ts 星期逐一相容（21 处，含未动的相对日表述）。
- **[5] 结构健全**：clock 单调不减、消息 ts ≤ clock、reference 与 episode 轮集合一致。
- **残留 grep**：被改文件中旧字符串（瞒了我半个月/下个月/年会前夜/周日不开门/今天下坡/周衍）0 残留。
- **ERRORS = 0**。
- ledger JSON 复解析 OK（17 acks；本角色 ack=GIA-20261108-REPAIRER-R1；另见并行 repairer-R2 ack，本报告与 E317-E338 无任何写入）。

**隔离声明**：本角色写入物仅三处——E305/E306/E307/E308/E310/E311/E313 的 episode.json 与 reference.json（其中 E305/E311 仅 reference）、GENERATION_ISOLATION_LEDGER.json 追加的自条目、本报告。未触碰任何 timeline.json / exposure.json / audit.md；未读取 logs/**、candidates/**、任何模型输出、GOLD_SIDECAR/**、其他 auditor 报告；未修改 E317-E338 的任何文件（人名查重仅为只读字符串匹配）。编辑前备份留存于仓库外 D:\tmp_backup_repairer_r1\。

— repairer-R1，2026-11-08
