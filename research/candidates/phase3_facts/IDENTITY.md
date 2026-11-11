# IDENTITY.md — phase3_facts 候选身份声明

| 项 | 值 |
|---|---|
| 候选名 | phase3_facts |
| 父版本 | candidates/phase2_expr（A3）；机制 base = candidates/phase1_retr_idf（检索 v2.2，已冻结） |
| 唯一主变量 | 事实出处与纠正状态（v12 独立复核 §6-C 执行令） |
| 机制变更 | ① 证据支持门：事实文本英数 token 必须逐字出现在所引消息原文∪其时间戳；source_ids 无效即拒；违例事实拒绝入库；② 全局来源键持久化（case_id:mid + 原文/时间/说话人，摘要与证据分开）；③ 纠正追踪：更正证据点名旧编号 → superseded → 注入排除；库内有更正事件 → 其余编号事实注入附警示；④ 状态推导 offer/intent/event（确定性，不新增模型字段）；⑤ 相对时间锚定注入标注 |
| 与父版本一致部分 | 检索算法（base._retrieve 原函数调用，无副本、无参数改动）、温度/网关/输出槽位/评分协议、params.json、assets 15 文件（含 expression.md）、生成后四族词面审计 |
| 新增资产 | assets/system_prompt/facts_evidence.md（生成侧证据边界提示，只服务机制假设，无逐题词表） |
| 契约 | scene_facts 仍为 text+source_ids，不要求模型新增任何输出字段（params.json 逐字节一致） |
| 记忆开关 | 支持（读侧关 + 写侧关，与 base notes 声明语义对齐） |
| 追踪 | retrieval_trace.jsonl（含 fact_gate 节：accepted/rejected/corrections）+ expr_trace.jsonl（继承 A3） |
| 回归 | tools/test_phase3_facts.py（14 项）+ phase1/phase2/场景装配全套回归 |
| 已知边界（登记） | CJK 语义级编造（钟点/数量/地点）机制层不判，靠生成侧提示 + 审计；更正证据未点名旧编号时不建 supersession 链（只做注入警示）；ongoing item/state 不做证据门；HER/ME 归属检索层不可分（继承 base） |
| 状态 | 开发候选——正式晋级冻结；效果须由 52 槽父子对照（另获生成授权）证明，不因机制已写入而宣称 |
