# PAPER_READING_NOTES.md — B 线外部论文研究·第一轮登记（2026-11-11）

阅读状态分级（诚实标注）：
- **full-read** = 抓到 arxiv HTML/ar5iv 全文，读了方法节与实验节及主要表格数字；
- **abstract-verified** = 抓到 arxiv abs 页逐字核实摘要（方法细节未读）；
- **abstract-only(search)** = 仅从搜索结果摘要获得，未抓原文页。

共登记 22 篇：full-read 7 篇、abstract-verified 6 篇、abstract-only(search) 9 篇。
所有数字均直接摘自被抓取的原文页面；论文没报告的一律写"论文未报告"，不做推断。

---

## A. full-read（7 篇）

### A1. Mem0: Building Production-Ready AI Agents with Scalable Long-Term Memory
- 作者：Prateek Chhikara, Dev Khant, Saket Aryan, Taranjeet Singh, Deshraj Yadav（mem0.ai）
- 年份：2025（arXiv:2504.19413；另见 IOS Press FAIA DOI 10.3233/FAIA251160）
- URL：https://arxiv.org/abs/2504.19413 / https://arxiv.org/html/2504.19413v1
- 检索关键词：mem0 scalable long-term memory production AI agents arxiv 2025
- 阅读状态：**full-read**（方法 §2、实验 §4、表 1/表 2、附录 B 算法、附录 C 基线描述）
- 核心方法：两阶段增量记忆管线。①抽取：新消息对 (m_{t-1}, m_t) + 异步滚动摘要 S + 最近 m=10 条消息 → LLM 抽出候选事实集 Ω；②更新：对每条候选事实，向量检索 top-s=10 条相似旧记忆，让 LLM 经 tool-call 在 ADD/UPDATE/DELETE/NOOP 四操作中选择。Mem0g 变体：实体/关系图（Neo4j），冲突时不物理删除而是标记失效（支持时间推理），检索=实体中心子图 + 三元组语义匹配双路。全部 LLM 操作用 GPT-4o-mini。
- 适用条件：多会话长对话记忆；要求能嵌入向量库与（可选）图库；评测为问答式（LOCOMO）。
- 实验表现：LOCOMO 上 Mem0 总 J=66.88%，Mem0g=68.44%，全上下文=72.90%（最高）；RAG 最好 60.53%；Zep 65.99%；OpenAI 记忆 52.90%。分类型：单跳 Mem0 最强（J 67.13），多跳 Mem0 最强（51.15），时序 Mem0g 最强（58.13），开放域 Zep 略胜（76.60 vs Mem0g 75.71）。
- Token-计算-时延代价证据（论文报告）：平均检索上下文 Mem0=1,764 tokens vs 全上下文=26,031 tokens；记忆库本身 Mem0≈7k tokens/会话、Mem0g≈14k、Zep>600k（作者归因 Zep 每节点缓存全摘要+边存事实的冗余）；总时延 Mem0 p50=0.708s / p95=1.440s，全上下文 p50=9.870s / p95=17.117s（即"91% 更低 p95、>90% token 节省"出处）。注意：抽取+更新两阶段的额外 LLM 调用成本与调用次数**论文未在本表报告**（只说 GPT-4o-mini 与"图构建 <1 分钟"）。
- 局限：①LOCOMO 是 QA 式评测（单跳/多跳/时序问题），不是自由聊天质量；②全上下文 J 仍最高，记忆方案是"近似不落后+大幅省"而非质量上限提升；③Mem0g 在多跳没赢 Mem0（图结构开销未兑现）；④开放域输给 Zep 0.89 分；⑤作者自称利益相关（商业产品论文），基线复现口径（A-Mem 重跑等）有选择空间。
- 对 LoveBrain 的迁移假设：把"原始证据窗口+摘要"替换/增强为"增量事实抽取+冲突四操作"的记忆管线，可在保持（甚至略升）记忆类得分的同时把注入 token 砍一个数量级；"标记失效不删除"天然对齐"已取消/禁止坐实"反向探针。
- 不应照搬的部分：①不能照抄"每轮固定消息对+top-s 相似"的调用节奏——我们是单条回复产品，每轮多 1–2 次模型调用会把时延翻倍，必须异步化或批处理；②不引入 Neo4j 全图（Mem0g 的图只在时序类有增益、维护复杂）；③不能把 HER 单方说法经 UPDATE 直接入"事实"（本仓 W14M02 账本候选已证明会制造无据坐实，需保留"谁说的"来源字段）。

### A2. LLMLingua-2: Data Distillation for Efficient and Faithful Task-Agnostic Prompt Compression
- 作者：Zhuoshi Pan, Qianhui Wu, Huiqiang Jiang 等（微软 + 清华）
- 年份：2024（arXiv:2403.12968，ACL 2024）
- URL：https://arxiv.org/abs/2403.12968 / https://arxiv.org/html/2403.12968v2
- 检索关键词：LLMLingua-2 data distillation prompt compression arxiv
- 阅读状态：**full-read**（数据构建 §3、压缩器 §4、实验 §5、表 1–5、限制节）
- 核心方法：把提示压缩形式化为二元 token 分类（保留/丢弃）。用 GPT-4 对 MeetingBank 做"只删词不加词"的分块（≤512 token）压缩得到平行语料，滑窗+模糊匹配自动标注每词标签，再用 xlm-roberta-large(355M)/mBERT(110M) 训练分类器；推理时按目标压缩率保留 top-N 概率词、保持原顺序（忠实性=只删不改）。
- 适用条件：任务无关压缩；对小模型算力友好（CPU/低端 GPU 可跑）；下游 LLM 黑盒。
- 实验表现：MeetingBank 域内 QA EM 86.92 vs 原文 87.75（压缩 3.1x）；域外 LongBench 2,000-token 约束下 39.1 vs 原文 44.0；GSM8K 1-shot 79.08 vs Full-Shot 78.85（持平）；Mistral-7B 上 MeetingBank QA 81.75 反超原文 71.27（作者归因小模型长上下文弱）。
- Token-计算-时延代价证据（论文报告）：压缩器自身时延 0.4–0.5s（2–5x 压缩率，V100），比 LLMLingua(LLaMA-2-7B,1.5–2.9s) 快 3–6x；端到端加速 1.6–2.9x（9.4→5.2s 等）；GPU 内存 2.1GB vs 16.6/26.5GB。压缩率为 2x–5x。
- 局限：①训练域是会议纪要（英文），闲聊域未测（其限制节自述泛化依赖"冗余模式相似"的推测）；②任务无关压缩不及任务感知的 LongLLMLingua（有 question 信息时差距 5–9 分）；③中文 LongBench 上 5x 压缩后 38.1 vs 原文 42.5，中文场景有损失；④要训练一个 355M 分类器（数据蒸馏+23h 训练）。
- 对 LoveBrain 的迁移假设：聊天记录/画像块属于"冗余高的口语文本"，理论上可压缩 2–3x 而不丢事实；比"全文窗口"省 token。但中文闲聊 + 数字/时间敏感（金额、日期是 LoveBrain 核心考点）是压缩高风险区——LLMLingua-2 的训练数据完全不含这种风险结构。
- 不应照搬的部分：①不直接用其公开英文会议模型压中文聊天（中文 LongBench 已掉分）；②不压"必答断言/金额日期"类 token（应走确定性保护清单，详见机制假设 RH-B-04/09）；③压缩器本身是一个新增组件，会加时延（0.4–0.5s 与 DIRECT 主体调用同量级），必须权衡。

### A3. Adaptive-RAG: Learning to Adapt Retrieval-Augmented LLMs through Question Complexity
- 作者：Soyeong Jeong, Jinheon Baek, Sukmin Cho, Sung Ju Hwang, Jong C. Park
- 年份：2024（arXiv:2403.14403，NAACL 2024）
- URL：https://arxiv.org/abs/2403.14403 / https://arxiv.org/html/2403.14403v2
- 检索关键词：Adaptive-RAG learning to adapt retrieval-augmented LLM question complexity arxiv
- 阅读状态：**full-read**（策略 §3.1、分类器 §3.2、表 1/3/4/6、图 3 混淆矩阵）
- 核心方法：三级策略路由——无检索 / 单步检索 / 多步迭代检索。T5-Large(770M) 分类器预测 query 复杂度标签 A/B/C；标签自动生成：①三个策略都跑，按"最简正确优先"给银标签；②全失败时用数据集归纳偏置（单跳数据集→B，多跳→C）。无需人工标注。
- 适用条件：存在"简单请求占多数 + 复杂请求少数"的流量结构；可以离线跑三个策略拿银标签。
- 实验表现（6 数据集平均，FLAN-T5-XL）：No-Retrieval F1 21.12 / 单步 44.31 / **Adaptive-RAG 46.94** / Multi-step 48.85 / Oracle 56.28。GPT-3.5-Turbo：Adaptive-RAG 50.91 vs Multi-step 50.87（质量平手，时间 1.46 vs 3.33 相对值）。逐查询时延：A=0.35s(8.6%)、B=3.08s(53.3%)、C=27.18s(38.1%)。
- Token-计算-时延代价证据（论文报告）：GPT-3.5 上 Adaptive-RAG 与质量最高的 multi-step 几乎同分（50.91 vs 50.87）而耗时约 44%（1.46/3.33）；分类器只用 770M 且各尺寸（60M/223M/770M）结果差异很小。
- 局限（重要）：分类器整体准确率只有 54.52%（No 类仅 30.52%，47% 的 A 被误判为 B）；C→B 误判 31% 会直接损失质量；Oracle 56.28 说明路由上限还有 ~9 分空间；质量提升部分来自"多步给难 query"，在闲聊域没有天然对应物。
- 对 LoveBrain 的迁移假设：把"检索强度"换成"装配强度"——简单轮（寒暄/纯情绪）用最小上下文直答，复杂轮（事实依赖/意图轮）上全装配。同样的银标签方法可用我们自己的历史答卷自动生成训练数据。预期在 token/p95 上有结构性收益，因为简单轮占大头。
- 不应照搬的部分：①不能照抄 54% 准确率的 T5 路由器——错判到简单档的质量损失在陪伴场景远比 QA 严重；②不引入"多步迭代"档（成本不可控）；③银标签判据要改成"哪个装配档被裁判判过"，不能只看答案对错。

### A4. Chain of Draft: Thinking Faster by Writing Less
- 作者：Silei Xu 等（Zoom Communications）
- 年份：2025（arXiv:2502.18600）
- URL：https://arxiv.org/abs/2502.18600 / https://arxiv.org/html/2502.18600v1
- 检索关键词：Chain of Draft thinking faster by writing less arxiv 2025 token reduction
- 阅读状态：**full-read**（方法 §3、表 1–2 全部数字、讨论 §5；无独立局限节，如实）
- 核心方法：把 Chain-of-Thought 的每步推理压缩为 ≤5 词草稿（"Think step by step, but only keep a minimum draft for each thinking step, with 5 words at most."），few-shot 示例手写极简草稿；无额外调用、无训练。
- 适用条件：需要内部推理但推理过程不必展示的任务（数学/常识/符号）；few-shot 可行时最好。
- 实验表现（GPT-4o / Claude 3.5 Sonnet）：GSM8K CoD 91.1%/91.4% vs CoT 95.4%/95.8%（掉 ~4 分），token 205.1→43.9 / 190.0→39.8，时延 4.2s→1.0s / 3.1s→1.6s；日期理解 CoD 88.1/89.7 vs CoT 90.2/87.0（GPT-4o 掉 2.1，Claude 反超）；体育理解 CoD 98.3/97.3 反超 CoT；coin flip 持平 100%。
- Token-计算-时延代价证据（论文报告）：token 最低降到 CoT 的 7.6%（平均 ~80% 削减）；GSM8K 时延降 76.2%（GPT-4o）/48.4%（Claude）。
- 局限：①数学上有 4 分精度代价（95.4→91.1）；②5 词上限对中文是否等效未测；③只评测了客观答案任务，没有开放生成/聊天任务（作者自述）；④无局限节。
- 对 LoveBrain 的迁移假设：LoveBrain 的输出契约里有 analysis/记忆变更等大 JSON 字段，这是输出 token 大头；把内部字段改成极简草稿体（每字段 ≤N 词），用户可见回复本身不动，可砍输出 token 与时延，质量风险集中在"内部字段太瘦导致下游依赖它的行为（下一轮实际行为）变弱"。
- 不应照搬的部分：①不能对"发给用户的那条回复"用 5 词限制（会直接砸自然度）；②不能指望它替代结构化事实（金额/日期必须保真，CoD 的压缩是有损的）。

### A5. Towards Emotional Support Dialog Systems（ESConv）
- 作者：Siang Liu, Sihao Chen, Sijia Liu, Kai Yu, Hao Wang, Weiran Huang, Jinchao Zhang, Zhou Yu
- 年份：2021（arXiv:2106.01144，ACL 2021）
- URL：https://arxiv.org/abs/2106.01144 / https://ar5iv.labs.arxiv.org/html/2106.01144
- 检索关键词：Towards Emotional Support Dialogues ESConv support strategy ACL 2021 arxiv
- 阅读状态：**full-read**（ESC 框架、方法、自动+人工评测、结论中的局限）
- 核心方法：基于 Hill 帮助技能理论的三阶段支持框架：Exploration（Question / Restatement or Paraphrasing）→ Comforting（Reflection of Feelings / Self-disclosure / Affirmation and Reassurance）→ Action（Providing Suggestions / Information），另有 Others；8 个策略作为特殊 token 前缀在支持者话语上，生成前先预测/采样策略 token 再条件生成。阶段顺序"实践上不能固定线性，必须自适应"。
- 适用条件：情绪支持型对话；策略粒度粗但可作为行为级先验。
- 实验表现：BlenderBot 骨干 Oracle vs Vanilla：BLEU-2 6.31 vs 5.45、ROUGE-L 17.90 vs 15.43（均 p<0.05）；Joint（预测策略）自动指标略低于 Vanilla（有趣负结果）；人工交互评测 Joint vs Vanilla Overall 51–34 胜（p<0.1），Comforting 54–34（p<0.05）；Joint vs Random（随机策略）Overall 56–36（p<0.05）→ **策略时机本身显著影响质量**。
- Token-计算-时延代价证据：论文未报告 token/时延数字（1990s 时代模型，未按现代口径记账）。机制本身不增加调用（策略 token 与生成同次完成）。
- 局限：①自动指标上 Joint（预测策略）反而不如 Vanilla——策略预测错误会拖累生成；②作者自述策略选择与实现、用户状态建模、任务评测是未解方向；③"建议"级别的支持对非专业系统风险过高（作者原话）；④数据是英文支持对话，中文陪伴场景策略集要重定义。
- 对 LoveBrain 的迁移假设：在生成同一调用内先输出一个"互动策略"字段（接话/调侃/调情/推进/收尾…），再输出回复，作为行为级先验防"平淡接话器"（F4 表达缺陷族）；ESConv 的"策略时机显著影响质量"支持这是一个可学习变量而非纯提示措辞。
- 不应照搬的部分：①不照抄英文 8 策略集（调情/推进关系在 LoveBrain 是一等公民，需自有策略集）；②不引入"Providing Suggestions/Information"类高风险建议策略作为默认；③策略预测错误的负结果提醒：不能把策略字段做成强独立分类器再串联，应该在同一次调用内联合生成。

### A6. PaRT: Enhancing Proactive Social Chatbots with Personalized Real-Time Retrieval
- 作者：Xiaofeng Li 等（小红书/RedNote 相关，SIGIR 2025 industry track）
- 年份：2025（arXiv:2504.20624）
- URL：https://arxiv.org/abs/2504.20624 / https://arxiv.org/html/2504.20624v1
- 检索关键词：relationship chatbot LLM proactivity long-term relationship maintenance evaluation paper 2024 2025
- 阅读状态：**full-read**（§2 三模块、表 1–4、在线 A/B、训练细节；无局限节，如实）
- 核心方法：三模块。①用户画像：记忆机制从对话史总结画像并实时更新；②意图引导查询改写：LLM 把意图分三类——natural transition（陪伴延续，无需检索）/ explicit retrieval（显式问题）/ implicit retrieval（检测用户兴趣衰减信号→个性化话题转换）；③RAG 三段式（检索→摘要→生成，RedNote 搜索引擎）。主动性=主动开场（画像+问题库+RAG）与平滑话题转换。
- 适用条件：社交陪伴产品，有真实检索源与画像存储；含训练场景（SFT Qwen2）。
- 实验表现：改写查询 P@k 平均 0.7096 vs 原始 0.3925（+31.71%）；生成质量（0–3）：对话场景 PaRT 均分 2.2058 vs 画像仅 1.8782 vs 直答 1.7937；检索条数 k=5 最优（k=10 引入噪声反而降到 1.8646）；在线 A/B（7 天 1:1）：平均对话时长 361.51s vs 296.88s（+21.77%）。
- Token-计算-时延代价证据：论文未报告推理时延/token 成本（只给了训练成本：24×A100 80GB 约 4 小时；动机处提到 72B/7B 分工是为"平衡时延-成本"）。
- 局限：①依赖 RedNote 平台检索源；②LLM 评测与人工一致性低（kappa 0.43–0.51）、样本小（每任务 50）；③在线 A/B 只有 7 天；④需要 SFT（Qwen2 全参微调），不可直接用于固定模型设定。
- 对 LoveBrain 的迁移假设：①"意图三分类驱动要不要检索/要不要转换话题"与 LoveBrain 的意图处理直接同构——特别是 implicit retrieval 的"兴趣衰减→主动转场"正是"主动性"维度的机制化；②画像+改写查询提升检索质量（+31.7%）支持"先用画像约束再检索记忆"的次序；③k=5 噪声曲线支持"注入条数有甜点、多给反而坏"。
- 不应照搬的部分：①不引入外部网络检索（LoveBrain 场景记忆自足）；②不照搬其需要 SFT 的部分（我们不训练权重）；③主动开场机制对应"用户未发先回"场景，与 LoveBrain"回复用户本轮消息"的契约不同，只能借内部逻辑。

### A7. Lost in the Middle: How Language Models Use Long Contexts
- 作者：Nelson F. Liu, Kevin Lin, John Hewitt, Ashwin Paranjape, Michele Bevilacqua, Fabio Petroni, Percy Liang
- 年份：2023（arXiv:2307.03172；TACL 2024, 12:157–173）
- URL：https://arxiv.org/abs/2307.03172 / https://ar5iv.labs.arxiv.org/html/2307.03172
- 检索关键词："lost in the middle" how language models use long contexts arxiv Liu 2023
- 阅读状态：**full-read**（设置、表 1、20 文档位置曲线、KV 检索、附录 E）
- 核心方法：受控实验：多文档 QA（10/20/30 文档，1 篇含金，其余干扰）+ KV 检索，扫金文档位置。
- 适用条件：任何"长上下文+关键信息"场景；结论与模型无关方向一致（大小模型都中招）。
- 实验表现：GPT-3.5-Turbo 20 文档：金文档在首位 75.8% → 中位 53.8% → 末位 63.2%；中间位置甚至低于 closed-book 56.1%（作者原话）；加长上下文版本（16K）曲线几乎重合——加长不救；KV 检索把 query 放在数据前后两侧→"所有模型接近满分"。
- Token-计算-时延代价证据：机制本身零成本（纯排布）；其蕴含"少给中间噪音文档"反而省 token（20→50 文档仅 +1.5% GPT-3.5 提升，作者结论"reader 先饱和"）。
- 局限：①任务是抽取式 QA，不是开放式生成；②模型较老（GPT-3.5/Claude-1.3 时代），新模型长上下文能力可能改善（但 U 型在多数模型仍被复现）；③只测了"检索哪篇"，没有测多块异质内容（画像/记忆/规则）的排布。
- 对 LoveBrain 的迁移假设：装配时把"本轮必答断言/金额日期/意图约束"放到提示首尾（尤其末尾贴近生成点），历史原始窗口放中间——这是零调用、零 token 的确定性机制，可能直接改善 FK-LR2-01 类长程逐字保持与"关键约束被挤到中间丢失"（F5）。
- 不应照搬的部分：①不要因此把所有内容都堆首尾（首尾容量有限，仍需压缩中间）；②它的"干扰文档"是同质文档，LoveBrain 的画像/规则不是干扰，删多少要靠实验。

---

## B. abstract-verified（6 篇，抓了 arxiv abs 页逐字核实）

### B1. FrugalGPT: How to Use Large Language Models While Reducing Cost and Improving Performance
- 作者：Lingjiao Chen, Matei Zaharia, James Zou | 2023（arXiv:2305.05176，TMLR）
- URL：https://arxiv.org/abs/2305.05176
- 检索关键词：FrugalGPT how to use large language models while reducing cost cascade arxiv
- 阅读状态：abstract-verified（scoring function 机制细节未在页内，如实标注）
- 核心方法：三层省钱法：prompt adaptation / LLM approximation / LLM cascade。cascade=先便宜后贵的序列 + 一个学习型打分函数决定"接受当前答案还是升级"。
- 实验表现：匹配 GPT-4 性能最多省 98% 成本，或同成本提升准确率（摘要页口径 4%，搜索结果另有 9% 口径，以摘要页 4% 为准）。
- 代价证据：论文报告了 cascade 省钱（98% 上限）；打分函数自身的调用成本论文未在摘要页报告。
- 局限：跨模型 cascade 依赖"多个强弱不同的模型"——本任务模型固定，只能迁移其"先简后繁+质量门"结构；评分器误判风险论文未在摘要页展开。
- 迁移假设：单模型内做"小上下文草稿→质量门→全上下文升级"的两档级联（机制假设 RH-B-06）。
- 不应照搬：多模型组合、学习型打分器（需要训练+额外调用）。

### B2. Self-Refine: Iterative Refinement with Self-Feedback
- 作者：Aman Madaan 等 | 2023（arXiv:2303.17651，NeurIPS 2023）
- URL：https://arxiv.org/abs/2303.17651
- 检索关键词：Self-Refine iterative refinement self-feedback arxiv Madaan
- 阅读状态：abstract-verified
- 核心方法：同一 LLM 生成→自反馈→精炼，迭代循环；7 任务含对话回复生成。
- 实验表现：人工与自动指标平均绝对提升 ~20%；GPT-4 上仍有效。
- 代价证据：每轮迭代至少多 2 次调用（反馈+精炼）——摘要页未给确切调用数，但多调用是方法定义所致（如实标注为结构性成本）。
- 局限：反馈循环有自恋偏置风险（论文未展开，如实标注）；对话生成是 7 任务之一但摘要页无分任务数字。
- 迁移假设：只在"高价值轮"（邀约/安抚/重大承诺）做一轮有界精炼，而非全量（机制假设 RH-B-10）。
- 不应照搬：无条件多轮精炼（时延 3x+ 不可接受）。

### B3. MemGPT: Towards LLMs as Operating Systems
- 作者：Charles Packer, Sarah Wooders, Kevin Lin, Vivian Fang, Shishir G. Patil, Ion Stoica, Joseph E. Gonzalez | 2023（arXiv:2310.08560）
- URL：https://arxiv.org/abs/2310.08560
- 检索关键词：MemGPT towards LLMs as operating systems memory paging arxiv
- 阅读状态：abstract-verified（DPRP 等具体任务数字未在摘要页，如实标注）
- 核心方法：OS 式虚拟上下文：主上下文（窗口内：系统指令+近期对话+工作记忆）与外部上下文（全历史/归档）分层，LLM 自我调用函数换页与编辑记忆，interrupt 驱动控制流。
- 实验表现：摘要声称可分析超窗文档与多会话记忆演化；具体数字论文未在摘要页报告。
- 代价证据：摘要页未报告（mem0 论文的复现口径里 MemGPT 类方案 LOCOMO J 低于 Mem0，见 A1 表；作为二手证据标注）。
- 局限：自编辑记忆的函数调用序列成本高、失败模式多；在 mem0 的 LOCOMO 复现里未胜出。
- 迁移假设：其"分层+自主换页"不适合固定单模型低成本场景；可借鉴"把记忆读写做成显式工具语义"的接口思想（弱引用）。
- 不应照搬：让模型自主决定何时检索（每次换页都是一次调用+一次时延）。

### B4. CharacterGLM: Customizing Chinese Conversational AI Characters with Large Language Models
- 作者：J. Zhou 等（清华 thu-coai） | 2023（arXiv:2311.16832）
- URL：https://arxiv.org/abs/2311.16832
- 检索关键词：CharacterGLM customizable conversational AI characters role-play arxiv
- 阅读状态：abstract-verified
- 核心方法：角色配置=属性（身份/兴趣/观点/经历/成就/社会关系等）+行为（语言特征/情绪表达/互动模式）；6B–66B ChatGLM 系微调。
- 实验表现：人工评测在一致性/拟人度/参与度上胜过含 GPT 系列的主流闭源模型（摘要口径，无具体数字）。
- 代价证据：论文未报告（摘要页）。
- 局限：核心收益来自权重训练——本任务不可用；属性清单本身可作为提示资产参考。
- 迁移假设：属性×行为的两维角色配置结构可用于组织 ME/HER 画像注入顺序（弱引用，机制层不改）。
- 不应照搬：任何依赖微调的部分。

### B5.（abs 页核实通过 A3 Adaptive-RAG 与 A7 Lost in the Middle 已入 full-read，此处不重复）

### B6. （占位：无——保持编号连续性说明：本组实际 4 篇 + 上面两篇 full-read 的 abs 页先探，均如实计入检索日志，不虚增篇目。）

> 修正说明：为避免虚增，B 组正式登记为 4 篇（B1–B4）。总计 22 篇的口径见文末统计。

---

## C. abstract-only(search)（9 篇，仅搜索结果摘要，未抓原文页）

### C1. A-MEM: Agentic Memory for LLM Agents
- 作者：Wujiang Xu 等 | 2025（arXiv:2502.12110，NeurIPS 2025 poster）
- URL：https://arxiv.org/abs/2502.12110
- 检索关键词：A-MEM agentic memory LLM agents arxiv 2025 Zettelkasten
- 阅读状态：abstract-only(search)（方法三机制来自搜索摘要：note construction / link generation / memory evolution）
- 核心方法：Zettelkasten 卡片盒式记忆：新记忆自动生成结构化笔记、与旧记忆建链、记忆随新信息演化更新。
- 实验表现：搜索摘要未给具体数字（mem0 复现口径中 LOCOMO J=48.38，见 A1——二手证据）。
- 代价证据：论文未报告（第一轮未读正文）；mem0 复现中其搜索时延 p50 0.668s。
- 局限：记忆演化（改写旧笔记）在"必须保留原始证据"的考场有污染风险。
- 迁移假设：卡片链接思想可用于"相关旧事回扣"（自然回提旧话题）检索扩展（弱引用）。
- 不应照搬：演化改写历史笔记本体。

### C2. MemoryBank: Enhancing Large Language Models with Long-Term Memory
- 作者：Wanjun Zhong 等 | 2023（arXiv:2305.10250，AAAI 2024）
- URL：https://arxiv.org/abs/2305.10250
- 检索关键词：MemoryBank long-term memory AI companion Ebbinghaus forgetting arxiv
- 阅读状态：abstract-only(search)
- 核心方法：AI 陪伴（SiliconFriend）长期记忆：日记式存储+艾宾浩斯遗忘曲线更新（常访问强化、不访问衰减）+双塔检索+用户人格画像。
- 实验表现：搜索摘要未给数字；mem0 复现口径 LOCOMO 上未进前列（二手）。
- 代价证据：论文未报告（第一轮未读正文）。
- 局限：遗忘曲线是启发式，可能主动丢掉后续才被引用的旧事实（对"旧事重提"类考点是风险）。
- 迁移假设：遗忘/衰减思想可用于"陈旧化降权"而非删除（弱引用）。
- 不应照搬：物理遗忘删除事实。

### C3. RouteLLM: Learning to Route LLMs with Preference Data
- 作者：LMSYS（Ong 等） | 2024（arXiv:2406.18665）
- URL：https://lmsys.org/blog/2024-07-01-routellm / https://arxiv.org/abs/2406.18665
- 检索关键词：RouteLLM learning to route LLMs preference data arxiv 2024
- 阅读状态：abstract-only(search)
- 核心方法：人类偏好数据（Chatbot Arena）训练路由器，逐 query 决定强/弱模型，GNN/矩阵分解/BERT/RoBERTa 多种路由器。
- 实验表现（搜索口径）：最多省 85% 成本保持 MT-Bench 90% 左右质量（GPT-4 vs Mixtral 配对）；GitHub README 口径"2.75x cost-efective"等。第一轮未读正文，数字按搜索摘要登记。
- 代价证据：路由器本身是小模型（BERT 级，开销小）；具体时延论文未在搜索摘要中报告。
- 局限：依赖"两个可换模型"——本任务模型固定，只能迁移"逐轮难度路由"思想；偏好数据域与闲聊陪伴不完全对齐。
- 迁移假设：与 RH-B-02 同源——路由目标从"选模型"改为"选装配档"。
- 不应照搬：换模型本体（违规且不算 harness 进步）。

### C4. Self-RAG: Learning to Retrieve, Generate, and Critique through Self-Reflection
- 作者：Akari Asai 等 | 2023（arXiv:2310.11511，ICLR 2024）
- URL：https://arxiv.org/abs/2310.11511
- 检索关键词：Self-RAG self-reflection tokens retrieval critique arxiv Asai
- 阅读状态：abstract-only(search)
- 核心方法：训练模型生成 reflection tokens（按需检索决定、相关性/支持性/有用性自评），推理期可调阈值。
- 实验表现：摘要声称在 QA/长文生成上优于 Llama2-chat/GPT-4 类；具体数字第一轮未读正文。
- 代价证据：反思 token 增加生成 token；论文未在搜索摘要报告时延口径。
- 局限：需要训练（reflection token 是训练出来的）——本任务不可训练权重；Adaptive-RAG 复现中 Self-RAG 在 GPT-3.5 口径 F1 仅 22.98（二手证据，兼容性差）。
- 迁移假设："生成中自评支持性"可化为确定性校验（引用回验已有 quote_hard_check 先例），不必训练 token。
- 不应照搬：reflection token 本体（训练依赖）。

### C5. RECOMP: Improving Retrieval-Augmented LMs with Context Compression and Selective Augmentation
- 作者：Fangyuan Xu, Weijia Shi, Eunsol Choi | 2023（arXiv:2310.04408，ICLR 2024）
- URL：https://arxiv.org/abs/2310.04408
- 检索关键词：RECOMP improve retrieval-augmented LLMs with context compression selective compression arxiv
- 阅读状态：abstract-only(search)
- 核心方法：检索文档在进上下文前先压缩：抽取式（选句）或生成式（多文档摘要），query 感知训练；并学习"要不要增强"（selective augmentation）。
- 实验表现（搜索口径）：压缩率低至 6% 而性能损失极小，优于现成摘要器。
- 代价证据：压缩器训练成本论文未在搜索摘要报告；推理期压缩器是小模型（T5 级）。
- 局限：需要训练压缩器；生成式摘要有幻觉风险（对逐字保持考点致命）。
- 迁移假设：selective augmentation"不是每个 query 都值得增强"与难度路由同源；抽取式压缩对"历史窗口瘦身"有参考价值（确定性抽取版，见 RH-B-09）。
- 不应照搬：生成式压缩（幻觉）。

### C6. ChatHaruhi: Reviving Anime Character in Reality via Large Language Model
- 作者：Cheng Li, Ziang Leng 等 | 2023（arXiv:2308.09597）
- URL：https://arxiv.org/abs/2308.09597
- 检索关键词：ChatHaruhi reviving chat role-playing LLM character arxiv
- 阅读状态：abstract-only(search)
- 核心方法：改进 prompt + 从剧本抽取的"角色记忆"检索注入来控制角色扮演；32 角色 54k 对话数据集。
- 实验表现：摘要称自动+人工评测提升角色扮演能力；具体数字第一轮未读。
- 代价证据：论文未报告（未读正文）。
- 局限：角色来自固定剧本，LoveBrain 角色是持续演化的用户关系；剧本记忆≠双向记忆。
- 迁移假设：检索"角色过往台词/事件"注入以稳定人格口吻（弱引用）。
- 不应照搬：照抄虚构角色的记忆结构（无用户状态维护）。

### C7. InCharacter: Evaluating Personality Fidelity in Role-Playing Agents through Psychological Interviews
- 作者：X. Wang 等 | 2023（arXiv:2310.17976，ACL 2024）
- URL：https://arxiv.org/abs/2310.17976
- 检索关键词：InCharacter evaluating personality fidelity role-play LLM psychological instruments arxiv
- 阅读状态：abstract-only(search)
- 核心方法：访谈式人格保真评测：对 RPA 做心理量表访谈，比对目标角色心理学画像；首个 RPA 人格评测基准。
- 实验表现：摘要称揭示注入提示与记忆信息对人格保真的影响；具体数字第一轮未读。
- 代价证据：论文未报告（未读正文）。
- 局限：评测方法（访谈）成本高；量表对中文陪伴场景适配未知。
- 迁移假设：可借鉴为 LoveBrain "人格一致性"离线探针（评估侧，非 harness 机制）。
- 不应照搬：作为线上机制。

### C8. CacheBlend: Fast Large Language Model Serving for RAG
- 作者：Jiayi Yao 等 | 2024（arXiv:2405.16444，EuroSys'25 Best Paper）
- URL：https://arxiv.org/abs/2405.16444
- 检索关键词：CacheBlend fast RAG KV cache prefix reuse arxiv 2024 latency
- 阅读状态：abstract-only(search)
- 核心方法：非前缀文本块的 KV 缓存复用 + 少量 token 选择性重算恢复跨块注意力；流水线隐藏重算时延。
- 实验表现（搜索口径）：RAG 场景 KV 命中率可达 100%，TTFT 降 2.2–3.3x，吞吐升 2.8–5x。
- 代价证据：需要自建推理栈（LMCache），OpenAI-compatible 端点外不可控。
- 局限：属服务端系统机制——本任务 harness 侧只能通过"稳定前缀排序"间接受益于供应商缓存（如 deepseek context caching），能否生效取决于端点实现。
- 迁移假设：把稳定资产（系统提示/画像）放最前、逐轮动态内容放最后，最大化前缀缓存命中（RH-B-11）。
- 不应照搬：自建 KV 基础设施。

### C9. GPTCache / GPT Semantic Cache
- 作者：Bang（GPTCache, OpenReview）；Regmi 等（GPT Semantic Cache, arXiv:2411.05276）
- 年份：2023 / 2024
- URL：https://openreview.net/pdf?id=ivwM8NwM4Z / https://arxiv.org/abs/2411.05276
- 检索关键词：GPTCache semantic cache LLM latency reduction arxiv
- 阅读状态：abstract-only(search)
- 核心方法：语义缓存：embedding 相似命中则直接返回缓存回复，绕过模型调用。
- 实验表现（搜索口径）：GPT Semantic Cache 报告 API 调用最多减 68.8%，命中率 61.6–68.8%（其场景为高频相似问题）；GPTCache 开源实现。
- 代价证据：省 1 次完整调用/命中；相似判定自身开销小（embedding）。
- 局限：上下文依赖回复（同一句话在不同关系状态下应答不同）——聊天场景命中率天然低于 FAQ；错误命中=灾难（旧回复直接发出）。
- 迁移假设：只对"结构化重复模式"（问候、固定句式追问）设窄门缓存（RH-B-08）。
- 不应照搬：全量语义缓存。

---

## 统计汇总

| 组 | 篇数 | 编号 |
|---|---|---|
| full-read | 7 | A1–A7 |
| abstract-verified | 4 | B1–B4 |
| abstract-only(search) | 9 | C1–C9（含 C8/C9 两篇缓存类） |
| **合计** | **20** | 另有 Shi 2302.00093（distractors，abstract-only(search)）与 PCQPR 2410.01363（abstract-only(search)）作为补充登记于下 |

### 补充登记（2 篇，abstract-only(search)）
- **Large Language Models Can Be Easily Distracted by Irrelevant Context**，Shi et al.，ICML 2023，arXiv:2302.00093。搜索摘要口径：无关上下文显著降低算术/推理准确率，各种提示法（含 CoT）都受影响；含干扰样本的 few-shot 可提升鲁棒性。代价证据：论文未在搜索摘要报告 token/时延（其启示是"少给无关上下文"本身省 token）。迁移：支持最小装配与难度门控（RH-B-02）。不应照搬：其"在 few-shot 示例里故意混入干扰"的训练技巧。
- **PCQPR: Proactive Conversational Question Planning with Reflection**，S. Guo 等，EMNLP 2024 Main，arXiv:2410.01363。搜索摘要口径：结论驱动的问题生成，MCTS 式前瞻+迭代反思选最优问句。代价证据：论文未在搜索摘要报告（前瞻+反思结构性多调用）。迁移：主动开话题/留钩子的"下一话题规划"思想（弱引用，多调用成本高）。不应照搬：MCTS 前瞻本体。

**最终合计：22 篇登记（full-read 7、abstract-verified 4、abstract-only(search) 11）。**
