# A2 契约矩阵（取证版）

生成：tools/gen_contract_matrix.py（每行 = 实际函数执行结果，非手写转述）。
证据：refs/arena_v1/contract_matrix_evidence.json。通过 16/16。

| ID | 条款 | 实现函数 | 输入 | 期望 | 实际 | 判定 |
|---|---|---|---|---|---|---|
| R1 | 契约可发送总数≥8（显式声明强制） | `normalize_contract` | style_keys=2, directions=3 → 总数5 | ValueError | output_contract 可发送候选总数 5 < 8（rubric v0.3 硬约束） | PASS |
| R2 | directions[i] = string|null；null=该方向不交付 | `output_structure_errors` | directions 全 null | 空（合法） | 空 | PASS |
| R3 | directions 非字符串拒绝 | `output_structure_errors` | directions[0]=1 | 非空错误 | directions[0]:int | PASS |
| R4 | scene_facts[].text 非字符串/null 拒绝 | `analysis_type_errors` | text=None | 非空错误 | ['analysis.scene_facts[0].text:NoneType'] | PASS |
| R5 | ongoing[].item/status 非字符串拒绝 | `analysis_type_errors` | item=1 | 非空错误 | ['ongoing[0].item:int'] | PASS |
| R6a | response 未声明文本字段拒绝 | `output_structure_errors` | response.secret=非空字符串 | 非空错误 | ['response.secret:未声明文本字段(非空字符串)'] | PASS |
| R6b | extra_text_fields 声明后合法（null=缺省放行） | `output_structure_errors` | reply='补充话术'（已声明） | 空 | 空 | PASS |
| R7 | 未知顶层键非空字符串 → veto=unknown_payload_field | `lb_scorer.run_case_scoring` | smuggled_text='待发正文' | veto 含 unknown_payload_field | unknown_payload_field | PASS |
| R8a | 不回动作与非空正文并存 → veto=decline_with_text | `run_case_scoring` | action=no_reply + response 非空 | veto 含 decline_with_text | decline_with_text | PASS |
| R8b | action=reply 与非空 decline_reason 并存 → action_conflict | `run_case_scoring` | action=reply + decline_reason | veto 含 action_conflict | action_conflict | PASS |
| R8c | 非法 action 值 → veto=invalid_action | `run_case_scoring` | action=maybe | veto 含 invalid_action | invalid_action | PASS |
| R9 | 轮级未允许时 no_reply → veto=unauthorized_no_reply（全不回不能白拿分） | `run_case_scoring` | may_decline=False + action=no_reply | veto 含 unauthorized_no_reply | unauthorized_no_reply | PASS |
| R10 | source_id 只证编号合法；伪编号 → illegal_source_id（不证语义支持） | `run_case_scoring` | source_ids=['m99']（未登记） | veto 含 illegal_source_id | illegal_source_id:m99 | PASS |
| R11 | gold.soft_keywords_any 命中 → 证据层 pts=0，不计硬分 | `run_case_scoring` | gold.soft_keywords_any=[["哈哈","笑死"]] 且正文命中 | soft_kw 检查 pts=0 + evidence=hit | [('soft_kw_0', 0, 'hit')] | PASS |
| R12 | 原始解析失败留证（parse fail），审计不中断 | `run_case_scoring_safe` | raw='这不是json{{{' | 结构化失败结果 | {"score10": 0.0, "veto": "json_schema_broken", "checks": [{" | PASS |
| R13 | response/directions 坏类型 → veto=json_schema_broken（类型先于语义） | `run_case_scoring` | response=str, directions=str | veto 含 json_schema_broken | json_schema_broken | PASS |

## 评分口径补充条款（登记，未逐例取证）

- 越权不回/矛盾动作/非法动作 = veto 级（合法性），恰当性另设计分检查——全不回不能靠格式满分被报告为质量冠军（见 R8/R9 取证）。
- source_id 存在只证引用编号合法，不证摘要受原文语义支持（R10）。
- AI 味词表/长度/LCS 是风格参考信号，不单独证明真人感（lb_scorer AI_FLAVOR_WORDS/STYLE_SIM_MAX，评分侧资产）。
- 软证据（soft_keywords_any）命中只进 evidence 不计硬分（R11）。
- 开放闲聊不用固定关键词逼标准答案：硬关键词只锚定客观事实。
- 既有 T、权重、veto 政策未为任何候选临时修改。
