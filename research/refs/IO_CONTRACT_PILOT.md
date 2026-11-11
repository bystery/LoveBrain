# IO_CONTRACT_PILOT — Pilot 输入输出契约 v2（2026-10-19 第七波判决 §5 同步版）

> 依据：独立审核判决 §4.3/§7-3（v1）+ 第七波判决 §5（v2 修订）。
> 适用：Pilot 全部节点与后续 JSON 化。**候选可见 / 隐藏的边界按本契约为准。**
> v2 修订：第三节 no_reply 表达方式与评分器 v0.9 对齐（旧 v1 的 `action+text` 形态
> 与"no_reply 给可选参考正文"已废止——说不回就不许夹带任何待发正文，含扩展字段）。

## 一、七类事件定义

| 类型 | 定义 | 是否产生回复请求 | 是否评分 | 克制加分？ |
|---|---|---|---|---|
| **gap** | 时间推进，无任何消息 | 否 | 否 | **否**（无请求无生成，不能因"没发言"给克制加分） |
| **普通** | HER 最后一条消息等待回应，用户正常要建议 | 是 | 是 | — |
| **延迟请求** | HER 消息时间早于当前请求时间（用户隔了一段时间才处理） | 是（request_time 显式记录） | 是 | 延迟本身不是考点；延迟后的过度道歉/找借口才是 |
| **可不回（may_decline）** | HER 发了道别/表情等，用户要建议但**允许**模型建议不回 | 是 | 是 | 不回与简短收尾都可为正解；**may_decline 是许可，不是强制** |
| **用户明确不发** | 用户可见输入明确要求"这条不用回" | 是 | 是（考遵守） | **必须不回**；隐藏 gold 里写"不回"而用户没说 = 违规出题 |
| **图片/视频** | HER 消息含媒体；图片走已实测的 image_url 通道，**视频链接按 URL 文本传递、不做视觉实测主张** | 是（若末条 HER） | 是 | — |
| **ME 历史** | 已发送的我方消息（固定历史回放用） | 否（只是上下文） | — | — |

## 二、时间契约

- 每个节点显式区分三个时间：`her_time`（HER 最后一条消息的真实时间）/ `me_times`（已发送 ME 各条时间）/ `request_time`（本次生成请求的 clock）。
- 延迟回复**必须**用 `her_time ≠ request_time` 表达，禁止只在标题写"3 小时后"。
- gap 节点只有起止时间，无 her_time，不套"末条必须 HER"规则。

## 三、"不回"的表达方式（接口层，v2 与评分器 v0.9 对齐）

**动作语义（唯一有效契约，取代 v1 的 `action+text` 形态）：**

- 模型输出单 JSON，顶层协议键 = `action / response / directions / analysis / decline_reason / reason` + 候选契约 `extra_text_fields` **显式声明**的扩展文本字段。
- `action`：`"reply"`（建议发送）或 `"no_reply"`（建议不回复）。缺省时按旧形态推断（非空 `decline_reason` = no_reply）。
- **互斥纪律**：`no_reply` 时不得携带任何待发正文——response/directions/已声明扩展字段一律不得非空；显式 `action=reply` 与非空 `decline_reason` 并存 = 动作冲突（评分 veto=action_conflict）；说不回又夹带正文（含扩展字段）= veto=decline_with_text。**v1 的"no_reply 给可选参考正文"废止**：参考正文（reason）只能是说明性理由字符串，绝不作为可发送文本。
- 动作三分判定：**合法性**（越权不回/矛盾动作/非法 action 值 = veto）、**恰当性**（本轮 may_decline 或 gold.expect_decline 才计分）、**任务完成**（正向文本检查不因不回豁免）。
- 类型安全：response/directions/analysis 坏类型（外层或**嵌套**，如 scene_facts[].text 为对象）= 格式失败（json_schema_broken），机制写回消费同一份校验结果，不提交非法记忆、不中止回放。
- 未知字段政策：**未声明**顶层键携带非空字符串 = veto=unknown_payload_field（防夹带正文）；其余未知键登记 schema_anomalies，消费侧不得当作协议字段。需要扩展文本字段必须在候选 params.json `output_contract.extra_text_fields` 显式声明。
- 输入侧版本协议：`intent` 允许字符串（legacy，题库直书）或对象 `{"enabled":bool,"text":str}`；`may_decline` 必须布尔；messages 必须为对象列表、role ∈ HER/ME、text/ts/image 为字符串；clock 为字符串。容器错型入口一律拒绝。

- `no_reply` 是否计入可发送性评分：**不计负面分**；恰当性按上表三分判定。
- 候选草稿**永不自动进入历史**：对拍采用固定历史回放——双方读取完全相同的已声明历史，只生成本节点建议。

## 四、可见性契约

| 信息 | 候选可见性 |
|---|---|
| HER/ME 消息原文与时间 | 可见（当轮窗口内按机制自取） |
| 用户意图（自由文本） | 可见，**是方向不是事实** |
| gold、探针族标注、正确策略、编剧原因、"no_reply 设计位"标签 | **隐藏** |
| 用户明确不发的输入 | 可见（它是真实用户输入） |
| 编剧隐藏事实（她的排班原因、她最终买了哪台等） | **不可见**，直到她在对话里亲口说出（自述后转为可见事实，记录可见起点） |

## 五、样例（六类各一，JSON 片段）

### 1. 普通
```json
{"node":"L2_t03","type":"normal","her_time":"2026-10-20 21:35","request_time":"2026-10-20 21:35",
 "visible":{"new_messages":[{"role":"HER","text":"才第5集？?"},{"role":"HER","text":"不行 你这周必须看完6"}],
            "intent":null},
 "hidden":["编剧原因：顺接埋点","探针族标注：无"]}
```

### 2. 延迟请求
```json
{"node":"L1_t09","type":"delayed","her_time":"2026-10-16 20:15","request_time":"2026-10-16 23:40",
 "visible":{"new_messages":[{"role":"HER","text":"[动画表情]"},{"role":"HER","text":"救命 我妈开始催婚了"}],
            "intent":null},
 "hidden":["正确策略：正常接话不道歉三连（不得下发）"]}
```

### 3. 可不回
```json
{"node":"L2_t06","type":"may_decline","her_time":"2026-10-20 22:05","request_time":"2026-10-20 22:05",
 "visible":{"new_messages":[{"role":"HER","text":"[动画表情]"}],"intent":null},
 "note":"表情内容=挥手拜拜的猫（资产待补）；不回或简短收尾均可为正解",
 "hidden":["不得因'设计上想考克制'强制唯一不回金标"]}
```

### 4. 用户明确不发
```json
{"node":"示例","type":"user_no_send",
 "visible":{"new_messages":[{"role":"HER","text":"不聊了 我去看剧了 拜拜"}],
            "intent":"这条不用回了"},
 "rule":"action 必须 = no_reply；此时给 reply 计违规"}
```

### 5. gap
```json
{"node":"L2_gap_thu","type":"gap","from":"2026-10-21 15:50","to":"2026-10-22 10:34",
 "rule":"无请求、无生成、无评分、不加分；仅推进时钟"}
```

### 6. 图片
```json
{"node":"L2_t07","type":"image","her_time":"2026-10-21 11:30",
 "visible":{"new_messages":[{"role":"HER","images":["assets/L2_t07_img1.png","assets/L2_t07_img2.png"]},
            {"role":"HER","text":"万象城 人从众"}]},
 "hidden":["图片为虚构布景素材；'人从众'等套语的全线密度已在台词审查登记"]}
```

## 六、防泄露自检（JSON 化时逐节点跑）

1. visible 集内不含 gold 关键词表、探针族、编剧备注；
2. 用户明确不发的意图文本可下发，"正确策略"不可下发；
3. ME 历史与候选草稿隔离（回放固定历史 ≠ 候选上轮输出）；
4. 视频链接只作为 URL 文本出现，评分布里不出现"视觉识别视频"计分项。
