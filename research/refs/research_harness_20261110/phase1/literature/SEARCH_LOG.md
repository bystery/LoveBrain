# SEARCH_LOG.md — B 线外部论文研究·第一轮检索日志

- 执行者：B 线 Research Worker（独立子代理）
- 日期：2026-11-11（所有检索均在当天完成）
- 工具：WebSearch（网页搜索）、WebFetch（摘要页/HTML 全文抓取）、webReader MCP（arxiv HTML 全文转 markdown）
- 诚实声明：下表逐条对应真实工具调用。"没找到"如实登记；并发限制与 404/网络错误如实登记。

## 统计

| 项 | 数 |
|---|---|
| WebSearch 调用 | 25 次（23 成功，2 次并发限制失败并重试成功） |
| 全文/摘要页抓取 | 14 次（11 成功，1 次网络错误 500、1 次 404，均换 URL 或重试解决） |
| 精读（方法+实验节全文） | 7 篇 |
| 摘要级核实 | 6 篇（arxiv abs 页） |
| 仅搜索摘要级登记 | 9 篇 |

## 一、WebSearch 逐条日志（按时间顺序）

| # | 关键词（原样） | 结果 |
|---|---|---|
| 1 | MemoryBank long-term memory AI companion Ebbinghaus forgetting arxiv | 找到：MemoryBank arXiv:2305.10250（AAAI 2024），艾宾浩斯遗忘机制，SiliconFriend 陪伴场景，GitHub 代码 |
| 2 | mem0 scalable long-term memory production AI agents arxiv 2025 | 失败：user concurrency limit exceeded（并发限制） |
| 3 | A-MEM agentic memory LLM agents arxiv 2025 | 失败：user concurrency limit exceeded（并发限制） |
| 4 | mem0 scalable long-term memory production AI agents arxiv 2025（重试） | 找到：Mem0 arXiv:2504.19413，作者 Chhikara 等，~26% 相对提升，Mem0g 图变体，GitHub mem0ai/mem0 |
| 5 | A-MEM agentic memory LLM agents arxiv 2025 Zettelkasten（重试） | 找到：A-MEM arXiv:2502.12110，NeurIPS 2025 poster，三机制：note construction / link generation / memory evolution，arxiv HTML 可用 |
| 6 | LLMLingua-2 data distillation prompt compression arxiv | 找到：LLMLingua-2 arXiv:2403.12968，token 分类式压缩，3-6x 更快，llmlingua.com 项目页 |
| 7 | RECOMP improve retrieval-augmented LLMs with context compression selective compression arxiv | 找到：RECOMP arXiv:2310.04408（ICLR 2024），抽取式/生成式双压缩器，压缩率可低至 6% |
| 8 | "lost in the middle" how language models use long contexts arxiv Liu 2023 | 找到：arXiv:2307.03172（TACL 2024），U 型曲线，首尾优于中间 |
| 9 | FrugalGPT how to use large language models while reducing cost cascade arxiv | 找到：arXiv:2305.05176（TMLR），LLM cascade，匹配 GPT-4 性能最多省 98% 成本 |
| 10 | RouteLLM learning to route LLMs preference data arxiv 2024 | 找到：LMSYS RouteLLM（2406.18665），偏好数据训练路由器，最多省 85% 成本保持质量；相关 Meta-Router 2509.25535 |
| 11 | Adaptive-RAG learning to adapt retrieval-augmented LLM question complexity arxiv | 找到：arXiv:2403.14403（NAACL 2024），按问题复杂度路由 无检索/单步/多步 |
| 12 | Self-RAG self-reflection tokens retrieval critique arxiv Asai | 找到：arXiv:2310.11511（ICLR 2024），reflection tokens 按需检索+自我批评 |
| 13 | Towards Emotional Support Dialogues ESConv support strategy ACL 2021 arxiv | 找到：ESConv arXiv:2106.01144（ACL 2021），8 种支持策略 + 三阶段 helping process，1,053 篇对话 |
| 14 | CharacterGLM customizable conversational AI characters role-play arxiv | 找到：arXiv:2311.16832，6B–66B，CharacterDial；后续 role-play survey 2407.11484 |
| 15 | ChatHaruhi reviving chat role-playing LLM character arxiv | 找到：arXiv:2308.09597，32 角色 54k 对话，prompt+角色记忆抽取控制角色扮演 |
| 16 | InCharacter evaluating personality fidelity role-play LLM psychological instruments arxiv | 找到：arXiv:2310.17976（ACL 2024），访谈式人格保真评测，首个 RPA 人格评测基准 |
| 17 | Chain of Draft thinking faster by writing less arxiv 2025 token reduction | 找到：arXiv:2502.18600（Zoom），每步 ≤5 词草稿，token 最低 7.6% 保持精度 |
| 18 | Self-Refine iterative refinement self-feedback arxiv Madaan | 找到：arXiv:2303.17651（NeurIPS 2023），同 LLM 自反馈迭代精炼，平均 +20% |
| 19 | CacheBlend fast RAG KV cache prefix reuse arxiv 2024 latency | 找到：arXiv:2405.16444（EuroSys'25 Best Paper），非前缀 KV 复用，TTFT 降 2.2–3.3x |
| 20 | GPTCache semantic cache LLM latency reduction arxiv | 找到：GPTCache（OpenReview, Bang）+ GPT Semantic Cache arXiv:2411.05276（API 调用最多减 68.8%）+ MeanCache 2403.02694 |
| 21 | MemGPT towards LLMs as operating systems memory paging arxiv | 找到：arXiv:2310.08560，虚拟上下文管理/分页，后继 Letta |
| 22 | large language models distractors irrelevant context overshadowing prompt arxiv | 找到：Shi et al. ICML 2023 arXiv:2302.00093，无关上下文显著降低准确率 |
| 23 | "prompt overshadowing" language models extra context harm arxiv 2024 | 未找到同名论文；找到近邻：Shi 2302.00093、knowledge overshadowing（Han 2024）、Mind Your Step 2410.21333（verbal overshadowing）。如实记录：无 2024 年同名论文 |
| 24 | relationship chatbot LLM proactivity long-term relationship maintenance evaluation paper 2024 2025 | 找到：PaRT arXiv:2504.20624（SIGIR'25，社交聊天机器人个性化实时检索+主动开场）、PCQPR、CHI 2025 长距离关系 HCI 研究 |
| 25 | PCQPR proactive conversation planning reflection LLM EMNLP 2024 arxiv | 找到：arXiv:2410.01363（EMNLP 2024 Main），结论驱动的对话问题生成 + MCTS 式前瞻 + 反思 |

## 二、全文/摘要页抓取日志

| # | URL | 工具 | 结果 |
|---|---|---|---|
| 1 | https://arxiv.org/html/2504.19413v1 | webReader | 成功，全文（方法 §2 + 实验 §4 + 表 1/2 + 附录算法）→ 精读 |
| 2 | https://arxiv.org/html/2403.12968v2 | webReader | 成功，全文（方法 §3–4 + 表 1–5 + 限制节）→ 精读 |
| 3 | https://arxiv.org/html/2403.14403v3 | webReader | 失败：网络错误 500 |
| 4 | https://arxiv.org/abs/2403.14403 | WebFetch | 成功（摘要级：方法概述、分类器训练、无具体数字） |
| 5 | https://arxiv.org/html/2403.14403v3 | WebFetch | 失败：404 Not Found |
| 6 | https://arxiv.org/html/2403.14403v2 | WebFetch | 成功（全文方法+表 1/3/4+混淆矩阵数字）→ 精读 |
| 7 | https://arxiv.org/html/2502.18600v1 | WebFetch | 成功（全文方法+四任务精确数字）→ 精读 |
| 8 | https://ar5iv.labs.arxiv.org/html/2106.01144 | WebFetch | 成功（全文方法+自动/人工评测+局限）→ 精读 |
| 9 | https://arxiv.org/html/2504.20624v1 | WebFetch | 成功（全文方法+表 1–4+在线 A/B）→ 精读 |
| 10 | https://ar5iv.labs.arxiv.org/html/2307.03172 | WebFetch | 成功（全文设置+精确数字+作者建议）→ 精读 |
| 11 | https://arxiv.org/abs/2305.05176 | WebFetch | 成功（摘要级；scoring function 细节页外，已标注） |
| 12 | https://arxiv.org/abs/2303.17651 | WebFetch | 成功（摘要级：方法、任务、~20% 平均改进） |
| 13 | https://arxiv.org/abs/2310.08560 | WebFetch | 成功（摘要级：虚拟上下文管理；无具体数字，如实标注） |
| 14 | https://arxiv.org/abs/2311.16832 | WebFetch | 成功（摘要级：属性/行为两类配置，人工评测胜 GPT 系列；无具体数字） |

## 三、检索覆盖的研究方向

1. 低成本长期记忆与记忆整固（Mem0/Mem0g、A-MEM、MemoryBank、MemGPT、LOCOMO 生态）
2. 上下文压缩与选择（LLMLingua-2、RECOMP）
3. 条件计算 / 难度路由 / 级联（Adaptive-RAG、Self-RAG、FrugalGPT、RouteLLM）
4. 陪伴/情感对话质量机制（ESConv、CharacterGLM、ChatHaruhi、InCharacter、PaRT、PCQPR）
5. 测试时计算与内部草稿 token 削减（Chain of Draft、Self-Refine）
6. 时延与缓存机制（CacheBlend、GPTCache/GPT Semantic Cache、Lost-in-the-Middle 的排布启示）

## 四、检索空白与未决项（如实）

- 未找到 2024 年名为 "prompt overshadowing" 的论文（#23）；以 Shi 2023 无关上下文论文 + Lost in the Middle 作为"多塞上下文有害"的证据基础。
- Role-play 方向的 InCharacter/ChatHaruhi/CharacterGLM 仅摘要级；第一轮不精读（对"质量—token—时间"权衡的证据弱于其他方向，优先级让位给成本机制类论文）。
- speculative decoding / KV cache 类服务端机制（CacheBlend、prompt caching）在 OpenAI-compatible 固定端点下 harness 不可直接控制，只作为"排布启发"登记，未精读 CacheBlend 正文。
- 未检索 Google Scholar 专页/ACL Anthology 站内搜索（用 WebSearch 覆盖即可）；Semantic Scholar API 未直接调用。
- 联网全程可用，无 RESEARCH_BLOCKERS.md 需要建立的阻断。
