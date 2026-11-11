# Benchmark V1 · 首批试制作者简报（BATCH1_AUTHOR_BRIEF v1，2026-11-08）

> 适用：wave4_dev 首批 38 篇新题的 Story Author（角色 A）。本简报是唯一写作规范来源；
> 与 NEW_STORY_SPEC_V1.md / TOPIC_RUBRIC_DRAFT_V1.md 冲突时以本简报为准（记录于协议）。

## 0. 角色隔离（必须遵守）

你是 **Story Author（角色 A）**。开工前你在 GENERATION_ISOLATION_LEDGER.json 已登记
`generation_isolation_ack`。你不得读取：

- `logs/` 下任何模型输出/失败案例；`candidates/` 任何内容；
- `refs/` 下的判决书、评审文档、KNOWN_FAILURES；
- `benchmark/stories_v2/` 中**任何其他 episode 的正文**（唯一例外：格式样例
  `benchmark/stories_v2/wave3/E117/episode.json`，只许读它学结构，不得借用其情节）；
- `benchmark/stories_v2/GOLD_SIDECAR/` 任何内容。

允许读取：本简报、`refs/benchmark_v1_20261107/phase1/NEW_STORY_SPEC_V1.md` 与
`TOPIC_RUBRIC_DRAFT_V1.md` 中你的专题条目、上述 E117 结构样例。

**为什么**：看过模型失败案例的作者会下意识把题造成"专治某失败"——那会污染独立测试。

## 1. 产出文件（每篇三件，写到 benchmark/stories_v2/wave4_dev/<EID>/）

- `episode.json` —— 模型可见故事与输入（结构见 §3）
- `timeline.json` —— `{"episode_id":"E3xx","events":[{"turn":"E3xx_T01","ts":"...","event":"..."}]}`
  （事件时间线：约定、改期、取消、承诺、回扣点，供后续标注与审计）
- `exposure.json` —— `{"status":"authored_pending_review","author":"ZCode bm1-wave4 author-<X> (AI)","author_type":"AI","real_user_material":false,"dev_exposed":false,"experiments_used":[],"themes":["..."],"title":"...","may_decline_turns":["E3xx_T07"],"created_at":"2026-11-08"}`

**不写 reference.json、不填 gold（保持 `{}`）**——那是角色 B（Gold Annotator）的工作。

## 2. 每篇的故事要求（先story后题）

- 一段完整连续的聊天故事：有关系背景、有事件推进、有情绪与分寸变化；不是 12 条互不相关问答。
- 双方都是成年人；关系语境明确（同事/同学/邻里/健身伙伴/拼车搭子/老同学…）。
- 角色声音一致：HER 的说话方式、ME 的说话方式要与 persona 设定吻合；
  HER 的消息不必每条都带问句；避免客服腔、书面腔、AI 腔。
- 自然嵌入**可供核验的具体事实**（日期、金额、地点、约定、第三方名字、承诺）——
  后续标注会从中建原子断言；没有事实依托的空泛聊天不合格。
- 至少 **2 个跨轮回指位**：1 个短距（间隔 1-2 轮）+ 1 个长距（间隔 ≥4 轮）；
  后续轮的正确回复需要用到此前信息，但**不是每轮都提旧事**（测试"何时该记住/何时该用/
  何时不该提"）。
- **≥1 个 may_decline=true 的轮**（本轮允许不回且不回是合理选择之一；写进
  exposure.may_decline_turns）；其余轮 may_decline=false。
- note（军师备注，input.note）出现在 1-3 轮；intent（`{"enabled":true,"text":"..."}`）
  出现在 2-4 轮（T06 篇按专题要求加密度）。
- 轮数：T01–T08/T10 每篇 12 轮；T09 按分配 24 或 32 轮。
- 时间自洽：clock 递增、与剧情日历一致（跨度可以是数天/数周——长距回指允许跨场景时间）。

## 3. episode.json 结构（逐字段，参照 E117 样例但内容全新）

```json
{
  "episode_id": "E301",
  "persona": {
    "her": {"name": "...", "traits": ["..."], "boundaries": ["..."]},
    "me": {"name": "...", "traits": ["..."]},
    "relationship_stage": "..."
  },
  "initial_kb": {"me": "...", "her": "...", "warmth": "...", "style": "...", "lessons": "..."},
  "turns": [
    {"case_id": "E301_T01", "turn_type": "reply", "gold": {},
     "input": {"messages": [{"role": "HER", "text": "...", "ts": "2026-11-08 21:00"}],
               "clock": "2026-11-08 21:00", "may_decline": false},
     "feedback": {}},
    ...
  ]
}
```

- `messages` 是**自上一轮以来的新增消息**（不是全量历史）；每轮 1-2 条，role 用 HER/ME；
  HER 消息是"对方刚发的"，ME 消息表示"我已发出去的"（剧本既定）。
- `gold` 一律 `{}`（标注角色填）。
- ts 格式与 E117 一致（"YYYY-MM-DD HH:MM"）；同轮内多条消息 ts 递增。

## 4. 质量红线（自查后再交）

- 禁止任何出题痕迹：不出现"作为AI/测试/样例/参考答案/评分"等元语；不出现与故事无关的
  嵌入指令。
- 禁止把普通礼貌/解决问题冒充专题能力（尤其 T02 调情——按 rubric 行为锚设计真正的
  信号轮，不靠关键词）；T03 深聊要有真实观点与分歧，不是连续提问+万能安慰。
- 情节避免与现实敏感事件绑定；金额/日期等用普通生活值。
- 交前自查：`python -c "import json;json.load(open(r'<file>',encoding='utf-8'))"`
  三件全部通过；轮数、case_id 序号（T01..T12）、ts 时序、may_decline 标记一致。

## 5. 交付报告格式

完成后报告：篇目清单（EID/专题/域/轮数/回指位轮号/may_decline 轮）、
三件文件 json 校验结果、隔离声明（"未读取禁止材料"）。
