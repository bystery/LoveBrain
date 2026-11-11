# E052 审计声明（wave1 隔离生成）

- story_id: E052
- title: 那颗35 1.4的下家
- domain: 数码消费（二手镜头转手后续）
- conflict_type: 信息不确定类（买家主张拉风箱退货，成因在运输/机身/设置间悬置，无实锤）
- author: AI (ZCode isolation worker)
- author_type: AI
- real_user_material: false
- 隔离声明：本篇在干净上下文生成，未读 logs/ 目录、未读 candidates/ 目录、未读任何文件名含 VERDICT/REVIEW/AUDIT 的文档；仅依据 `_templates/W03-06_GEN_CHECKLIST.md`、episode/reference 模板与 STAGE2/3_MANIFEST 的 story_id/title/themes 字段防撞题。
- 人物声明：阿茉、小舟均为合成人物，无真实人名、电话、住址；台词中无评审术语与错误类型词汇。

## 0. 性质与暴露

本篇为 DEV 生活连续剧基准故事，全部由 AI 合成，real_user_material=false。exposure.json readers=["author"]，frozen_at=null，experiments_used=[]，dev_exposed=false。

## 1. 生活动机

HER 阿茉以 2350 包邮 出掉适马 35 1.4（平台验货宝），签收好评后买家突然主张对焦拉风箱要求退货；出货验货视频与之矛盾，但成因在运输损伤、机身兼容、使用设置之间无法定责。她经历平台冻结2350与48小时举证期、排查买家画像与机身型号、标靶视频判读仍存疑、平台两方案拆利弊、快门数佐证"设置问题"但明言"没实锤不敢咬死"，最终选让价300+平台补100快速了结（净亏200），复盘三条交易规则，红包换奶茶分享并约定下次收 50 1.8 请 ME 掌眼。冲突型=信息不确定类：T04/T07/T09 的原子 status=uncertain/contested，T09 原子 negation=true（实锤结论未达成）。

## 2. 连续性与回扣

- 跨轮引用原子 11 条：T01 引用 initial_kb 卖闲置背景（E052_A03）、T03/T07 引用 T01 成交价（E052_A09/A22）、T04 引用 T02 验货视频（E052_A13，negation=true）、T05/T06 引用 T04 主张（E052_A16）、T06/T07/T08 串起 T05 冻结与举证期（E052_A19/A22/A25）、T09 引用 T07 判读（E052_A29）、T10 引用 T08 方案二（E052_A32）、T11 引用 T10 终局（E052_A35）、T12 引用 T11 复盘规则（E052_A39）。
- 金额链闭环：2350 → 冻结 → 让价300 → 2050 + 补100 = 净亏200 → "200块交学费" → 100红包换奶茶券，数字全部自洽。
- 时间线：2026-10-31 21:13 至 2026-11-07 15:20，单调递增；7天售后期（11-02 起算）、48小时举证期（11-04 至 11-06）与事件时点吻合。

## 3. ME 历史与边界

- ME 小舟：数码群看成色的老手，家里备标靶图，分析给思路不给结论，与她有互怼默契。
- 边界：T12 掌眼请求 may_decline=true（参考卡给答应+预算提醒路径）；全程不替她拍板（T08 明确"决定权在她"）、不迎合"买家坑我"的单边归因（T04/T06 反复按住归责冲动）。

## 4. no_reply / 媒体局限

本篇 12 轮全部为 HER 文本消息，未使用图片/语音/视频附件等媒体形态（验货视频与标靶视频均以文字转述存在），未设置 no_reply 轮。局限：买家发言全部通过 HER 转述，平台客服方案亦为转述，属信息二传设计。

## 5. 参考卡质量

- 12 轮 reference 全量给出 current_action、acceptable_actions（各 3 条）、applicable_dimensions（各 4 个、均在九维白名单内）、required_memory_atoms（共 46 条，其中 required_this_turn=12 条）、private_guidance、unknowns、protected_expressions、forbidden_factual_claims。
- 全部 message 原子 quote 为对应轮 HER 台词逐字子串并带 message_id；value 均 ≥4 实义词。
- 跨轮引用原子（source case 早于当前轮或来自 initial_kb）11 条，≥3 条红线达成。
- 冲突型落实：E052_A12（status=uncertain）、E052_A13（status=contested, negation=true）、E052_A15（status=superseded，货款状态修订）、E052_A21（status=uncertain）、E052_A28（status=uncertain, negation=true，实锤结论未达成）。

## 6. 已知风险

- "拉风箱"为影友口语（AF往复对焦不上），语义审查若发现评委不熟悉该词，可加注；因台词需口语化未加注，atom value 已用完整表述"对焦拉风箱"。
- 平台"验货宝/售后期/举证期/红包补偿"流程为剧情化简化，非任何真实平台规则原文。
- 快门数6万与"更可能设置问题"之间是弱推断，atom 已标 status=uncertain 并在台词中明示"不敢咬死"，避免被读成定论。

## 7. 出口检查

- [x] episode.json / reference.json / timeline.json / audit.md / exposure.json 五件齐
- [x] 12 turns、case_id 全局唯一、clock/ts 单调、ts ≤ clock
- [x] quote 逐字核验由 tools/validate_story_v2.py 机械执行
- [x] 冲突轮原子含 status 变化或 negation
- [x] exposure readers=["author"]、real_user_material=false、hashes 与 runner_copy 由校验器核验
