# action_policy_r2 — 故障族修复（截止日质量执行单 §C 的产物）

主假设：**S1/S2 真实批暴露的候选侧协议故障不是模型随机噪声，而是三处可定位的合同/指令缺口；
补齐缺口（不换基座、不改评分器、不改题面与卡）即可消除相应故障族，且不牺牲积极互动。**

## 本分支唯一差异（父本 action_policy_r1 全量继承）

| 修复 | 根因（全部来自已付费真实答卷，非推测） | 机制改动 | flag |
|---|---|---|---|
| FIX-6 不回表达 | AK09_T01 / AK12_T01（两轮 may_decline=True 收尾轮）：`response` 四键全空串 + `directions=[null×4]` + `analysis.signal=收尾`，无 `action` → legacy 合同判 `json_schema_broken`，veto 0 分、**零可发正文**；父本同两轮给短收尾 12/12 干净。评分器 `arena/lb_scorer.py` v0.7/v0.8 第 240-247 行其实支持 `action=no_reply`（要求显式声明且正文为空），而 r1 的 FIX-1 只教模型"正文必须完全为空"——**机制教的和评分器认的不是同一种写法** | `CAP_RULES[收尾]/[停]` 与【发送合同】改为评分器认识的表达（`action:"no_reply"`+`decline_reason`+四键空串+directions 全 null），并明令禁止"空正文却不写 action"的中间态；`format.md` 增【JSON Schema（不回）】并限定只在能力清单出现 收尾/停 时可用 | `no_reply_expression_fix` |
| FIX-7 单 JSON 结构卫生 | AK02_T01：`directions` 之后提前闭合顶层 `}` 再补写 analysis（`raw_decode` 于 char330 截断，第一段仅 `response/directions`）→ 整轮 veto，四风格正文其实完好、analysis 记忆写回丢失；S1 E024_T02 style 键名混入零宽字符（`json.loads` 成功但缺必需键）；S1 E024_T04 / E034_T05 `directions` 给 5 条（合同固定 4） | 动作块固定追加三条可核对硬约束（右花括号只在 analysis 后出现一次／四个键名逐字照抄／directions 恰好四槽）；`format.md` 同步 | `protocol_hygiene_fix` |
| FIX-8 共同经历回扣有据 | AK07_T01 `directions[3]`"你上次说的那个考点笔记还在吗"——该锚点对话记录、persona、initial_kb 六字段、note 全无"考点笔记"（全锚点目录检索无"笔记"二字）；`core.md` 红线早已禁止编造共同经历，说明**只写"禁止"不足**，缺的是可自检判据 | 动作块追加【回扣有据】＋`core.md` 记忆段加一条可操作自检：回扣内容必须能在【对话记录】或【记忆行】里逐字找到，找不到就改问句 | `callback_grounding_fix` |

不改评分器、不改 rubric、不改题面/卡/金标、零额外模型调用。父本磁盘与已付费答卷冻结不动。

## 与父本故障族的分工

`json_schema_broken` 不是候选独有故障族：S1 里父本臂 E034_T11 同样 `json_schema_broken`、
E027_T06 `illegal_source_id`。本分支修的是**候选自己造成的那三处缺口**（合同表达、结构指令、
回扣判据），不声称能消除模型输出随机性；父本同族故障照常记在交付层，两臂分列。

## 消融与剩余缺口（诚实边界）

- 三个 flag 各自可独立关闭=回到 r1 行为；但**逐 flag 的真实消融本轮排程未做**，
  一次实测同时带着三项修复，因此只能证明"故障族是否消失"，不能单独归因到哪一项修复。
  单项归因需要 3×12 次额外真实调用，登记为截止后的第一批欠账。
- FIX-6 只在"本轮能力清单含 收尾/停"时开放不回；r1 已有的 `may_decline=false` 剔除逻辑保留，
  防止把不回变成万能挡箭牌（退化风险由复验批的 9 个非收尾位点直接盯）。
- 三项修复均未经真实轨迹验证；本节只声称"消除了三处已定位缺口"，不声称质量已提升。
  结论以新身份真实复验批为准，旧成绩不套用。
