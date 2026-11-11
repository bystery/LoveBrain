# E180 审计（B 篇：《寿宴前一天的账》）

## 0. 性质与暴露
author=AI，real_user_material=false；readers=["author"]，frozen_at=null，experiments_used 空。全合成人物，与 E179 同一主角对，无真实人名电话住址。

## 1. 生活动机
寿宴前一天店方连环出错：订金登记口径错、松鹤厅被改期婚宴重复占用；温小满带家人情绪，阿岸凭书面凭证讨说法并完成改期与垫款结算，属真实的生活维权与收尾。

## 2. 连续性与回扣
B 篇含 7 个跨篇引用原子，均指向 A 篇已发台词（source.kind=message、case_id 用 A 篇轮 id）：T01→E179_T05（五百订金收据，E180_A02）、T03→E179_T11（撤菜，E180_A06）、T04→E179_T06（口味约束与鲈鱼，E180_A08）、T05→E179_T05（订金凭证复核，E180_A10）、T06→E179_T09（书面留厅被推翻，negation，E180_A12）、T07→E179_T09（收据背面加注，E180_A14）、T11→E179_T12（预估价，E180_A22）。更正机制三处：登记口径更正（T05）、留厅承诺否定翻转（T06）、日期改期（T09/T10）。B 篇不泄露 A 篇未来剧情（A 篇止于十月二十七日收束）。

## 3. ME 历史与边界
阿岸的凭证习惯在 B 篇两次救场；维权依据是书面确认而非情绪，话不难听；垫款结算守住'当面结清、不欠糊涂账'的边界，不回避谈钱。

## 4. no_reply / 媒体局限
无 may_decline 轮次：T01 求助、T06 报坏消息、T07 维权委托均需回应；本篇未使用图片/语音媒体。

## 5. 参考卡质量
每轮 current_action+多选 acceptable_actions，维度≥3；跨篇原子 required_this_turn 均指向更早故事轮；冲突型为更正类，相关原子带 status=corrected/negated 与 negation 标记；protected_expressions 仅取自当轮 HER 台词。

## 6. 已知风险
跨篇原子依赖 A 篇台词逐字 quote，若 A 篇文本变动需同步重算 B 篇 quote；改期后日期为十一月八号，候选若仍答七号属时间错误，参考卡已在 forbidden_factual_claims 限定。

## 7. 出口检查
12 轮、时间单调、gold={}、feedback 每轮有变化标注；与 E179 构成跨故事对（B 引用 A），manifest 顺序 A 先 B 后；机械校验由 tools/validate_story_v2.py 执行。
