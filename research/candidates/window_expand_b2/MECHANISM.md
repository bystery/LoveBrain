# MECHANISM.md — window_expand_b2

**存储**：与父本 window_expand_b 完全一致——每故事一份简单、有序的真实消息事件记录（稳定 uid、仅实际发送的 HER/ME），模型草稿永不写回。

**检索**：父本两件套不变（3600 CU 整条预算历史段 + 被裁消息 R1–R4 逐字 recap ≤400 CU）；新增一处注入——口径基线压制段（本代唯一 delta）：每轮对全部旧史跑 ledger_rules 规则抽取+更正判定（extract_facts+update_pass，纯函数、零模型调用），产出四类行：superseded 对（更正线索+overlap+数字冲突 →「旧值」已作废→现行「新值」）、cancelled 对（取消线索+overlap → 已取消）、值名取消（clause 自含数字+取消线索 → 已声明作废）、回归现行（消息含变回/还按/恢复线索 → 现行口径行）。段标题 `# 【口径基线：以下旧说法已作废，勿再沿用】`，≤240 CU、超限保新丢旧，注入在画像之后、历史之前（高优先前置）；含未定线索（如果/要是/还不确定/再看）的 clause 一律不入段（防坐实未定）；当轮更正已在当前输入逐字可见，压制段只算旧史；无条目则整段省略。ledger_rules 为 b2 自有副本，含受控扩展 E1（中文数字）E2（"不是X"引旧排除）E3（纯线索 clause 与数字 clause 逐字合并）E4（通用口语取消/更正词），update_pass 主判定逐字不动（修订记录见设计文档 §2.2）。

**意图**：与父本完全一致——当前有效 intent 经 build_intent_block 独立分区逐字进入请求，只取当轮、关闭即清空；note 与 intent 分区。

**上下文**：装配顺序 = 种子 → 【口径基线】 → intent → note → 历史消息 → 【更早轮次摘录】 → 当前窗口 → 时钟；当前窗口整条不被裁；每轮 notes 记 `history_cu/recap_cu/recap_lines/supp_cu/supp_pairs` 可复核；snapshot/restore 含 suppressions 缓存字段，checkpoint 恢复后每轮重算结果与不中断运行一致（确定性纯函数）。
