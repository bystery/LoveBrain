# E056 豆包的四天排班表 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + stage2/stage3 manifest 的 story_id/title/themes 字段）。
- 全部人物（小鹿、阿岚、父母）、猫、价格、航班均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：宠物；主线：HER 赴外地参加婚礼四天，猫豆包寄养需多人接力——朋友小鹿上门、母亲到岗看护、父亲过敏约束、自动喂食器补空档，最终四天排班无缝闭环并沉淀为模板。
- 生活动机真实可信：短期出差/参加婚礼的宠物寄养分工、家人与朋友的时间窗拼合，是常见多主体协作场景。

## 2. 连续性与回扣
- T01 四天担忧 → T02 小鹿全包承诺 → T04 父母张力（过敏硬约束）→ T05 分工初案+清单委托 → T06 小鹿变卦（status=cancelled, negation）→ T07 母亲时间窗（13日上午仍空）→ T08 定稿补位（空档消除，negation 型 confirmed）→ T10 首日执行回扣 T08 → T11 父亲过敏约束执行回扣 T04 → T12 完整落地回扣 T01（担忧解除，negation）。
- ME 角色线：T05 清单委托 → T09 清单核对（喂食量与 initial_kb 日常口径一致）→ T12 模板化。

## 3. ME 历史与边界
- ME 记录豆包日常口粮口径与 HER 行程（initial_kb.me），行为边界：记录与核对，不替她拍板人选、不联系第三方。
- 冲突型=第三方类：三个第三方（小鹿、母亲、父亲）诉求与变动均有原子 status 变化或 negation（承诺 broken、空档 negation、约束 confirmed）。
- ME 不虚构第三方对话，不代第三方承诺。

## 4. no_reply / 媒体局限
- 全部为文本消息，无语音/图片；照片仅口头提及（T11）。
- 无 no_reply 轮；T10/T11 为报平安轮，ME 以轻量确认回应即可。

## 5. 参考卡质量
- 每轮 acceptable_actions ≥2、applicable_dimensions ≥3 且均在九维白名单。
- 原子 quote 均为来源台词/initial_kb 逐字子串，message-kind 均带 message_id。
- 跨轮引用原子 7 条（A06/A14/A16/A17/A19/A21/A23）≥3。
- unknowns 明示：喂食器到货、定稿是否同步给第三方、备用钥匙等未定项。

## 6. 已知风险
- 排班细节（喂食器一顿、母亲住到15日早与15日中午接盘的衔接）存在生活化的松散缝，属 HER 自述"全堵上"的主观口径，评审时注意这是人物视角而非客观担保。
- 第三方人数较多（小鹿/阿岚/父母），已通过 atom subject=THIRD 明确归属。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 字段齐、real_user_material=false、readers=["author"]
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(宠物, 寄养分工+第三方变卦+清单协作, 第三方类) 与既有篇三元组不同
