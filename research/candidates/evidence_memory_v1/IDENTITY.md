# evidence_memory_v1 — 证据记忆与上下文候选（C3，设计稿 C2_C3_CANDIDATE_DESIGN_V1.md §C3）

> **v1.1 资产修订（2026-10-27，P04-05）**：assets/system_prompt/format.md 的 analysis 契约与机制消费一致化（analysis={}（v5 继承） → facts/tentatives 记忆提取契约）；改前备份 format.md.bak-20261027-0535-cron15-p0405（与 v5 逐字节相同已验证）；v5 资产零改动。回归 tools/test_candidate_contract_consistency.py 12/12 PASS。旧批（C4 sweep）身份按 v1.0 资产解释。
> **v1.1（2026-10-27，P04-02 最小修复）**：`_merge_reported` 补①成员硬验证（src 非空且∈当前轮 uid；
> 此前任意裸 int/空 src 均曾入 confirmed 库——独立复核确认）②引用支持度（事实文本须被所引消息
> 子串或二元组≥0.15 支持，不足者 status=unverified_src 只留审计不投影）③notes 增加 unsupported_src。
> 已知边界：主体错换在字面高度重合时仍可能过 ②（如实登记），语义主体校验归评审层。
> 改前备份：mechanism.py.bak-20261027-0425-cron15-p0402 / IDENTITY.md.bak-20261027-0425-cron15-p0402。
> 回归：tools/test_evidence_memory_membership.py（离线，零调用）。v1.0 身份保留给已收口批
> AUTH-C4-SCREEN-20261026-01（sweep_b）的历史答卷解释。

主假设：**五分离存储 + 预算化投影**，治"60条掐尾丢窗外事实 / 全量注入高成本 / 摘要过期当新事实"。
基座：v5_purpose_aligned 闭包（四 system 资产逐字节继承；差异全在 user 侧记忆层，可单独归因）。

## 与 v5 的差异（整套比较）
1. 存储五分离（每条带出处元数据）：
   events=真实消息（uid/ts，同 v5）；facts=双方确认过的可核事实/约定（text+src_uids+ts+status）；
   tentatives=暂定事项（"再看/再说"类，带提出时间）；goals=目标启停历史；notes=本轮注入不沉淀。
   facts/tentatives 来源：initial_kb.ongoing_plan 种子（→facts）+ 模型每轮 analysis 报告
   （{"facts":[{"text","src_ids"}],"tentatives":[...]}，机制校验 src_uids∈本轮、按规范化文本哈希去重）。
2. 投影预算化：当前窗口(永不挤掉) > events 近窗(1400CU) > facts 相关(500CU) > tentatives(300CU) > goal(600CU)。
   facts 相关性=与(当前消息+目标)的字符二元组重合率（通用 n-gram，零领域词，无特判）；
   无当前信号时按新近度兜底。无关旧事被滤出不占预算；关闭目标不删 facts 里真实约定。
3. 窗外事实召回：facts 不在 events 掐尾窗口内——只要相关且预算内即注入（带 src/ts 供回扣）。
4. 维护负担记账：notes 记存储行数/投影各段 CU/相关性命中数（C4 对比 token÷C）。

诚实边界：V1 不做自动旧事实纠正翻转（corrections 状态翻转留待长故事测试暴露问题后按需加），
不为召回加任何特判词，不删光内容省 token。
