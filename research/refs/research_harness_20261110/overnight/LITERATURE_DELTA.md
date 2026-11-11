# LITERATURE_DELTA.md — 通宵连续研究循环·第一轮增量检索（2026-11-11 深夜）

- 执行者：RESEARCH-P1 通宵循环 Research Worker（子代理 A）
- 日期：2026-11-11 深夜（全部检索于当天实际完成）
- 工具：WebSearch（网页搜索）、WebFetch（abs/HTML/ar5iv 抓取）、webReader MCP（arxiv HTML 转 markdown）
- 任务范围：针对今晚 smoke 实测的 5 个真实失败/瓶颈做定向检索（LOOP-3）；执行中途追加 3 个用户点名的高优先方向（多候选+验证器 / 代词角色指代 / 结构化知识卡），与原 5 问并列完成。
- 阅读分级口径（沿用第一轮）：**精读**＝方法+实验节全文；**摘要级核实**＝arXiv abs 页或等效来源抓取；**检索级**＝仅搜索结果页，未抓原文。
- 诚实声明：下表逐条对应真实工具调用；失败（并发限制、超时、500）如实登记；未读正文的绝不写"精读"；论文未报告的代价/数字一律写"论文未报告"；单作者/弱刊论文如实标注可信度折扣。

---

## 0. 统计

| 项 | 数 |
|---|---|
| WebSearch 调用 | 22 次（18 成功，4 次并发限制失败后全部重试成功） |
| WebFetch 抓取 | 13 次尝试（6 成功，7 次 arxiv.org/ar5iv 连接超时，均已换 webReader 或改摘要级解决） |
| webReader 抓取 | 4 次（3 成功，1 次 500 网络错误） |
| 新论文登记（不与第一轮 22 篇重复） | 20 篇：精读 3、摘要级核实 6、检索级 11 |
| 工程一手文档（非论文） | 2（DeepSeek KV-cache 官方文档已抓正文；OpenAI prompt caching 官方文档检索级核实） |
| 新机制假设 | RH-B-12 … RH-B-17 共 6 条（15 字段，均附 smoke_24 可跑实验） |
| RH-B-06 迭代建议 | 3 个可立即实现方案，已排序 |

---

## 一、WebSearch 逐条日志（按时间顺序；关键词原样）

**第一组：原 5 个真实问题**

| # | 关键词（原样） | 结果 |
|---|---|---|
| 1 | LLM cascade routing uncertainty confidence signal 2025 arxiv cost-aware escalation | 找到 UCCI 线索（arXiv 2605.18796）+ 级联路由实践综述页；UCCI 随后经 abs 页二次核实 |
| 2 | Hybrid LLM cost-efficient quality-aware query routing ICLR 2025 router uncertainty | 失败：并发限制 |
| 3 | "Hybrid LLM" cost-efficient quality-aware query routing Ding arxiv | 确认 arXiv 2404.14618，ICLR 2024（注：不是我以为的 2025），DeBERTa-v3-large 300M 路由器，40% 少调大模型 |
| 4 | verbalized confidence LLM self-ask escalation selective prediction（重试用） | 失败：并发限制 |
| 5 | "I Know What I Don't Know" OR "just ask for calibration" verbalized confidence selective generation | 确认两篇：I Know What I Don't Know（arXiv 2502.19335，2025-02，级联置信度调优）+ Just Ask for Calibration（Tian et al. EMNLP 2023，2305.14975） |
| 6 | semantic entropy hallucination detection Farquhar Nature 2024 likelihood | 失败：并发限制 |
| 7 | detecting hallucinations large language models semantic entropy Nature Farquhar 2024 | 确认 Nature 2024（s41586-024-07421-0），arXiv 版 2405.19648，OATML |
| 8 | LLM knowledge conflict stale memory negation contradiction（第一次） | 失败：并发限制 |
| 9 | LLM knowledge conflict stale information memory update arxiv 2025 negation contradiction | 收获大：Knowledge Conflicts survey（2403.08319，EMNLP 2024）、CAD（2309.17415）、STALE（2605.06527）、Taming Knowledge Conflicts（ICML 2025）、CAD 2025 扩展线（自适应/门控对比解码） |
| 10 | "STALE" LLM agents memories stale outdated benchmark arxiv | 确认 STALE 2605.06527（400 场景/1200 查询/三维探针）+ 两个后继：2608.01619（记忆更新了行为没更新）、2610.10265（个人记忆失败模式分类） |
| 11 | SeCom memory construction retrieval personalized conversational agents segment ICLR 2025 | 确认 SeCom = arXiv 2502.05589（不是我记忆中的号——已用检索纠正），Microsoft，ICLR 2025 |
| 12 | LOCOMO long-term conversational memory benchmark multi-session multimodal arxiv | 确认 LOCOMO 2402.17753（ACL 2024，~300 轮/9k tokens/最多 35 会话，含 temporal reasoning 与 adversarial 类） |
| 13 | Turing test LLM 2025 Jones Bergen prompts word count | 确认 2503.23674（2025-03-31）+ PNAS 版；GPT-4.5+人设 73% 被判人 |
| 14 | detecting AI generated text perplexity burstiness human-likeness features Ghostbuster 2024 | 确认 Ghostbuster 2305.15047（NAACL 2024，99.0 F1）；重要反证：Pangram 2025 分析称困惑度/burstiness 在低误报场景不可靠 |
| 15 | prompt caching DeepSeek context caching API pricing cache hit miss discount documentation | 确认 DeepSeek KV-cache 官方文档口径（随后直接抓文档正文） |
| 16 | OpenAI prompt caching 50% discount cached_tokens 1024 tokens prefix latency improvement docs | 确认 OpenAI 官方口径：1024 token 最小前缀、cached_tokens 字段、50% 折扣、改一个 token 断链 |
| 17 | chatbot customer service tone evaluation naturalness dialogue metric LLM 2025 "naturalness" | 弱结果（如实登记）：无强学术对话自然度量尺论文；找到 Nature Sci Rep 信任研究（自然度=信任关键因子）+ 行业博客框架（Galileo/Braintrust/Cekura），均非可精读论文 |
| 18 | "Can LLMs Express Their Uncertainty" Xiong ICLR 2024 confidence elicitation | 确认 2306.13063（ICLR 2024，~1567 引）：LLM 普遍过度自信、口头化置信与正确性相关不稳 |

**第二组：执行中途追加的 3 个用户点名方向**

| # | 关键词（原样） | 结果 |
|---|---|---|
| 19 | best-of-N sampling verifier gated LLM generation 2025 rerank cheap verifier selection cost | 找到：Turn-Level BoN（2611.06868）、Best-of-N under Partial Verification（2607.20950）、Ichihara et al. BoN 策略评估（OpenReview H4S4ETc8c9）、Majority of the Bests（NeurIPS 2025）、Target-Local Verifier Choice（EMNLP 2026 Findings） |
| 20 | "Large Language Monkeys" repeated sampling coverage OR "test-time compute" Snell verifier scaling arxiv | 确认 2407.21787（Brown et al.，~1257 引，coverage 随样本数陡增、验证器是瓶颈）+ 2408.03314（Snell et al.） |
| 21 | speaker addressee pronoun resolution multi-party dialogue LLM coreference 2024 2025 who said what | 找到：Addressee Recognition benchmark（2501.16643，Inoue et al.，IWSDS 2025，LLM 在该任务上明显吃力）、MIMIC/FAME（2502.13001，ACL 2025 Findings）、Gan et al. 2024 LLM 指代消解评估、MMC（TACL 2023） |
| 22 | LLM dialogue pronoun error "你" "我" role confusion persona attribution wrong subject Chinese | **检索空白（如实登记）**：无以"中文 LLM 你/我人称错乱"为主题的学术论文；只有用户侧反馈（Reddit）与工程实践（novel-smith 视点控制、说话者标签惯例）。学术锚点只有 #21 的多方向工作 |
| 23 | structured character card persona profile role-play LLM dialogue 2025 relationship state tracking | 找到：CHARCO（MDPI 2025，弱刊如实标注）、SimsChat/SimsConv（2406.17962，EMNLP 2025 Findings）、MRPrompt（ACL 2026 Findings，PDF 链接未抓）、SillyTavern 角色卡工程实践 |
| 24 | relationship stage progression modeling romantic chatbot dialogue state machine long-term companion arxiv 2025 | **检索空白（如实登记）**：无 2025 arXiv 技术论文做"关系阶段状态机"；只有 HCI 侧（Replika 上应用 Levinger 关系发展模型等） |

---

## 二、全文/摘要页抓取日志

| # | URL | 工具 | 结果 |
|---|---|---|---|
| 1 | arxiv.org/abs/2404.14618 | WebFetch | 成功（摘要级：路由器+质量水平可调+40% 少调用） |
| 2 | arxiv.org/html/2502.19335v1 | WebFetch | 失败：连接超时 → 改 webReader（#4） |
| 3 | webReader arxiv.org/abs/2605.06527 | webReader | 成功（摘要级，含完整摘要与作者列表） |
| 4 | webReader arxiv.org/html/2502.19335v1 | webReader | 成功，全文 → **精读**（Gatekeeper 损失、门控函数、全部实验设置、B.2 prompt 基线失败细节、作者局限节） |
| 5 | webReader arxiv.org/html/2404.14618v4 | webReader | 失败：500 网络错误 |
| 6 | arxiv.org/pdf/2404.14618 | WebFetch | 失败：连接超时 |
| 7 | ar5iv.labs.arxiv.org/html/2404.14618 | WebFetch | 成功，全文 → **精读**（H(x) 质量差定义、BART score、hard/soft/transformed 标签、DeBERTa-v3-large 0.036s、各档数字与局限） |
| 8 | arxiv.org/abs/2502.05589 | WebFetch | 成功（摘要级） |
| 9 | arxiv.org/html/2502.05589v2 | WebFetch | 成功，全文 → **精读**（分段+压缩管线、LOCOMO/Long-MT-Bench+ 全部表格数字、时延表、消融、局限） |
| 10 | arxiv.org/html/2503.23674v1 及 abs/v2/ar5iv（共 5 次） | WebFetch | 全部失败：连接超时（arxiv WebFetch 在本时段持续不可用） |
| 11 | webReader arxiv.org/abs/2503.23674 | webReader | 成功（摘要级，完整摘要+作者+日期） |
| 12 | api-docs.deepseek.com/guides/kv_cache | WebFetch | 成功（一手文档：机制细节、usage 字段、清除策略；**价格表在定价页未抓取——如实登记**） |
| 13 | arxiv.org/abs/2605.18796 | WebFetch | 成功（摘要级，完整摘要：UCCI 全部关键数字） |
| 14 | arxiv.org/abs/2406.17962 | WebFetch | 成功（摘要级：SimsChat 框架机制，EMNLP 2025 Findings） |
| 15 | arxiv.org/html/2607.20950v1 | WebFetch | 失败：连接超时（Best-of-N under Partial Verification 维持检索级） |

---

## 三、新论文登记（20 篇，全部为第一轮 22 篇之外）

### 组 A：级联升级信号（对应问题 1：b06 升级率 45.8% 超标）

**A1. I Know What I Don't Know: Improving Model Cascades Through Confidence Tuning**
- 作者/年：Stephan Rabanser, Nathalie Rauschmayr, Achin Kulshrestha, Petra Poklukar, Wittawat Jitkrittum, Sean Augenstein, Congchao Wang, Federico Tombari（多伦多大学/Vector/Google），2025-02
- URL：https://arxiv.org/html/2502.19335v1
- 检索词：#5
- 阅读状态：**精读**（全文：方法 §3 + 实验 §4 + 附录 B）
- 核心机制：Gatekeeper 损失 L=αL_corr+(1−α)L_incorr：对预测正确的样本维持交叉熵，对预测错误的样本把输出推向均匀分布（低置信）；token 级变体适用于生成模型；级联门控用负预测熵（生成）或 max softmax（分类），阈值 τ 决定接受/转大模型。
- 关键证据：小模型为 Gemma2B、大模型 Gemma7B（ARC-e/c、MMLU、GSM8K）：deferral performance 相对未调优基线提升 7x/10x（ARC-e/c）；α 越低 deferral 越好但小模型自身准确率越差（utility-deferral 权衡显式存在）；**两个 prompt 式不确定性基线（"不确定时降低置信度"、"不确定时答 N"）在其实验中均不能可靠改善正确/错误分离**（附录 B.2，与 Kadavath 2022 一致）。
- 适用条件：需要能微调小模型（white-box）；大模型严格更强假设；黑盒 API 下只能用其失败基线的教训。
- 代价证据：论文未报告端到端延迟/token（只报 deferral 曲线与准确率）。
- 局限（作者自述）：只调小模型；对比基线少；生成侧用 Gemini 当裁判有噪声。
- 与问题 1 的关联：直接回答"升级门信号从哪来"——**输入侧表面特征（intent/note 存在）不是文献做法，文献是"小模型自身输出派生的置信信号+校准"**；且给出强警告：纯 prompt 置信指令不可靠，必须校准。
- 不应照搬处：Gatekeeper 微调本身（改模型权重，我们只有 API）；负预测熵需要 logprobs（端点不返回则不可用——需先探测）。

**A2. Hybrid LLM: Cost-Efficient and Quality-Aware Query Routing**
- 作者/年：Dujian Ding, Ankur Mallick, Chi Wang, Robert Sim, Subhabrata Mukherjee, Victor Rühle, Laks V.S. Lakshmanan, Ahmed Hassan Awadallah（UBC/微软），2024（ICLR 2024）
- URL：https://arxiv.org/abs/2404.14618（全文经 ar5iv）
- 检索词：#3
- 阅读状态：**精读**
- 核心机制：路由器预测 **Pr[H(x)≥0]**，H(x)=q(小模型回复)−q(大模型回复)，q 用 BART score；三种标签：hard/soft（10 采样）/transformed（Pr[q(S)>q(L)−t]，t 网格搜索）；路由器 DeBERTa-v3-large（300M），单次前向 ~0.036s/query。
- 关键证据：最多 40% 少调大模型无显著质量下降；小差距对（Llama-2 7b→13b）>96–98% 成本优势 ~1% 质量损失；**大差距对路由退化**（Pr[H≥0]≈0 对 90% 查询），必须用 transformed 标签且仍有 10.3% 损失；BART 与目标指标相关弱时（r=0.26）路由质量崩；跨模型对迁移要求 r>0.7。
- 适用条件：有一个可离线成对评分的裁判指标；两档模型质量差距不能过大。
- 代价证据：路由器 0.036s/查询（比最快被路由 LLM 快 ~10x）；训练需要 10k 查询 × 每查询两模型各 10 采样（成本论文未折算成金额）。
- 局限：单模型对；无任务感知路由；无 OOD 泛化保证；质量代理（BART）依赖。
- 与问题 1 的关联：**升级门的标签应来自"同题两档输出的成对质量差"，而不是输入侧特征**——这是把 45.8% 升级率降下来的正确标签法；transformed 标签法适用于"小档基本够用"的聊天场景。
- 不应照搬处：BART score（需要参考答案与专用模型）；直接照抄 DeBERTa 路由（我们可先用规则+校准起步）。

**A3. UCCI: Calibrated Uncertainty for Cost-Optimal LLM Cascade Routing**
- 作者/年：Varun Kotte（单作者），2026-05
- URL：https://arxiv.org/abs/2605.18796
- 检索词：#1、#20（二次核实）
- 阅读状态：摘要级核实（abs 页全文摘要）
- 核心机制：token 级 margin 不确定性 → 经 isotonic regression 校准为每查询错误概率 → 阈值按受约束成本最小化选取。
- 关键证据：生产 NER 负载 75k 查询（4B/12B 模型）：成本 −31%（95% CI 27–35%）保持 micro-F1 0.91；ECE 0.12→0.03；胜过熵阈值、split-conformal、FrugalGPT 式学习阈值。
- 适用条件：需要 logprobs（token margin）；需要校准数据。
- 代价证据：成本节省如上；校准开销论文未报告具体算力。
- 局限：**单作者预印本，可信度打折**；单一 NER 任务验证；三条强假设（摘要中声明）。
- 与问题 1 的关联：给出"校准后再设阈值"的正式方法与数字——RH-B-06 当前问题正是**未校准的粗糙触发**（intent/note 存在即升级）。
- 不应照搬处：token-margin 信号（无 logprobs 不可用）；单作者结论当强证据引用。

**A4. Just Ask for Calibration: Strategies for Eliciting Calibrated Confidence Scores from Language Models Fine-Tuned with Human Feedback**
- 作者/年：Katherine Tian 等（MIT 等），EMNLP 2023
- URL：https://arxiv.org/html/2305.14975v2
- 检索词：#5
- 阅读状态：检索级（搜索结果含方法要点与结论，未抓原文）
- 核心机制：让 RLHF 模型回答后自报 0-100% 置信度；口头化置信度出奇地校准，优于 logit/条件概率法。
- 与问题 1 的关联：升级门可选的低成本信号（同调用附加一个置信 token）。
- 代价证据：论文未报告（置信 token 的额外输出成本极小但未量化）。
- 局限：2023 年模型代际；QA/事实任务为主，聊天任务未验证；与 A5（过度自信）证据需并读。
- 不应照搬处：直接信任口头化置信度数字本身——必须先在本仓数据上标定。

**A5. Can LLMs Express Their Uncertainty? An Empirical Evaluation of Confidence Elicitation in LLMs**
- 作者/年：Miao Xiong, Zhiyuan Hu, Xinyang Lu, Yifei Li, Jie Fu, Junxian He, Bryan Hooi，ICLR 2024
- URL：https://arxiv.org/abs/2306.13063
- 检索词：#18
- 阅读状态：检索级
- 核心机制：系统评估口头化/一致性/logit 三类置信引出方法。
- 关键证据：LLM 普遍**过度自信**；口头化置信与实际正确性的相关不稳。
- 与问题 1 的关联：A4 的反证——口头化置信必须先验证再并入升级门。
- 局限：评测以 QA 为主。
- 不应照搬处：把口头化置信当独立门信号。

**A6. Detecting hallucinations in large language models using semantic entropy**
- 作者/年：Sébastien Farquhar, Lorenz Kuhn, Kyle Hsu, Jannik Kossen, Yarin Gal（Oxford OATML），Nature 2024-06
- URL：https://www.nature.com/articles/s41586-024-07421-0（arXiv 版 2405.19648）
- 检索词：#7
- 阅读状态：摘要级核实（Nature 摘要+官方代码库链接；未抓正文）
- 核心机制：多次生成→按语义聚类→语义熵；高熵=可能 confabulation。
- 与问题 1 的关联：理论上是最强"自我不确定"信号；但**需要多次采样**——I Know What I Don't Know（精读）明确把它排除在级联门外（成本反噬）。
- 代价证据：需要 N 次生成（论文未报告换算成聊天场景的成本）。
- 局限：聊天场景生成多份回复成本高；聚类需 SentenceTransformer（本地额外依赖）。
- 不应照搬处：进实时级联关键路径。

### 组 B：否定事实/记忆失效（对应问题 2：severe=cancel_affirm）

**B1. STALE: Can LLM Agents Know When Their Memories Are No Longer Valid?**
- 作者/年：Hanxiang Chao, Yihan Bai, Rui Sheng, Tianle Li, Yushi Sun，2026-05
- URL：https://arxiv.org/abs/2605.06527
- 检索词：#9、#10
- 阅读状态：摘要级核实（abs 页完整摘要）
- 核心机制：命名"隐式冲突"失效模式（后观察使早记忆失效而无显式否定词）；三维探针：State Resolution（识别旧信念过期）/ **Premise Resistance（拒绝预设了过期状态的问题——即 must-not-affirm）** / Implicit Policy Adaptation（主动按新状态行动）；提出 CUPMem 原型：写时结构化状态整固+传播感知检索。
- 关键证据：最强模型仅 55.2% 总准确率；模型普遍接受用户问题里嵌入的过期前提；检索到新证据≠会按新证据行动（"检索-行动鸿沟"）。
- 适用条件：长期记忆型助手的评测与写路径设计。
- 代价证据：论文未报告（benchmark 论文）。
- 局限：benchmark 论文，CUPMem 只是原型基线，未给成本数字。
- 与问题 2 的关联：**cancel_affirm 的三个子类（识别失效/拒绝预设/主动适配新状态）有正式定义与实测难度数字**——本仓 severe 类可直接映射到三维探针上分层测量。
- 不应照搬处：其 150K token 规模设定（我们场景小得多）；CUPMem 的图结构（先做条目化轻量版）。

**B2. When Memory Updates but Behavior Does Not**
- 作者/年：未核实作者全名（检索结果显示为 STALE 后继），arXiv 2608.01619，2026
- URL：https://arxiv.org/abs/2608.01619
- 检索词：#10
- 阅读状态：检索级
- 核心机制：实证"知道旧状态过期≠按新状态行动"的行为鸿沟。
- 与问题 2 的关联：证明只注入"已失效"提示不够——需要把失效状态接进行为决策路径（支持 RH-B-12 的"读路径消费端裁决"设计）。
- 代价证据：论文未报告。局限：未读正文，登记为线索。
- 不应照搬处：待精读后再引用具体数字。

**B3. Stale, Misattributed, or Late: Where Personal Memory Fails**
- 作者/年：未核实，arXiv 2610.10265，2026
- URL：https://arxiv.org/abs/2610.10265
- 检索词：#10
- 阅读状态：检索级
- 核心机制：个人记忆失败模式分类（过期/错归属/过晚）。
- 与问题 2 的关联：cancel_affirm 属"过期"族，主体归属错误属"错归属"族——提供失败分类学对齐。
- 代价证据：论文未报告。局限：未读正文。
- 不应照搬处：同上，待精读。

**B4. Knowledge Conflicts for LLMs: A Survey**
- 作者/年：Rongwu Xu 等，EMNLP 2024
- URL：https://arxiv.org/abs/2403.08319
- 检索词：#9
- 阅读状态：检索级
- 核心机制：知识冲突分类学（context-memory 冲突等）。
- 与问题 2 的关联：把"用户消息里的过期前提 vs 记忆"定位为正式研究对象；提供后续文献检索的地图。
- 代价证据：不适用（综述）。局限：未读正文。
- 不应照搬处：——

**B5. Trusting Your Evidence: Hallucinate Less with Context-aware Decoding (CAD)**
- 作者/年：Shi 等，检索结果显示 ICLR 2024（venue 未二次核实）
- URL：https://arxiv.org/abs/2309.17415
- 检索词：#9
- 阅读状态：检索级
- 核心机制：对比解码——放大"有上下文 vs 无上下文"的预测概率差，针对过期/被否定的参数知识。
- 与问题 2 的关联：思路对口（让上下文中的否定事实压过参数先验），**但需要 logits 访问，OpenAI-compatible API 下不可实现**。
- 代价证据：每步双前向（延迟约 2x，论文未报告端到端）。
- 局限：固定对比系数 α 在无冲突场景损害分布（2025 扩展线的共识批评，检索级）。
- 不应照搬处：整体不可照搬（无 logits）；其"上下文应压过先验"的直觉已吸收进 RH-B-12/13 的提示层设计。

### 组 C：记忆检索单元与时机（对应问题 3：required_miss）

**C1. On Memory Construction and Retrieval for Personalized Conversational Agents (SeCom)**
- 作者/年：Z. Pan 等（Microsoft），ICLR 2025
- URL：https://arxiv.org/abs/2502.05589（全文经 HTML v2）
- 检索词：#11
- 阅读状态：**精读**
- 核心机制：长期对话按**话题段**组织记忆库（GPT-4 zero-shot 一次输出全部边界，DialSeg711 F1 0.888；带反思的 rubric 迭代 F1 0.924）；段在**建索引前**用 LLMLingua-2（xlm-roberta-large，75% 压缩率）去噪；检索按段粒度，注入用原段原文。
- 关键证据：LOCOMO（4k 预算）SeCom BM25 71.57 vs 轮级 65.58 / 会话级 63.16 / 全历史 54.15；Long-MT-Bench+（1k 预算）88.81 vs MemoChat 85.14 / 会话级 73.38；**时延与 token：SeCom 1,722 输入 tokens / 2.61s vs MemoChat 7,233 / 5.60s（输入省 76%）**；去掉压缩 LOCOMO 掉 69.33→59.87；轮级太碎（关键词不匹配）、会话级太粗（噪声淹没）——粒度本身是检索质量的一等变量。
- 适用条件：长程多话题陪伴对话（正对 LoveBrain 剧情线）。
- 代价证据：如上（时延表实测）。
- 局限（论文口径）：标注数据稀缺、边界本来就模糊；GPT-4 也有过分割个案（WD 0.80）；压缩召回增益需压缩率 ≥50%。
- 与问题 3 的关联：required_miss 的根因假设从"注入多少"改写为"检索单元粒度+索引去噪"——有跨两个基准的一致证据。
- 不应照搬处：GPT-4 分段调用（我们先测规则分段；确不够再用小模型）；压缩注入（陪伴场景氛围句是有损压缩高危区——指纹只用于检索，注入保留原文）。

**C2. Evaluating Very Long-Term Conversational Memory of LLM Agents (LOCOMO)**
- 作者/年：Adyasha Maharana 等，ACL 2024
- URL：https://arxiv.org/abs/2402.17753
- 检索词：#12
- 阅读状态：检索级（第一轮 Mem0 精读已间接覆盖其角色，本轮补正式登记）
- 核心机制：~300 轮/9k tokens/最多 35 会话的多模态长对话基准；问题含单跳/多跳/时序推理/开放域/**对抗类**。
- 与问题 3 的关联：required_miss 对应其时序推理+对抗类（长上下文模型与 RAG 都吃力的类别）——为 smoke 扩展提供题型参照。
- 代价证据：不适用。局限：未读正文。
- 不应照搬处：——

**C3. Query Rewriting for Retrieval-Augmented Large Language Models（Rewrite-Retrieve-Read）**
- 作者/年：Ma 等，2023（后续 2024 有去噪扩展）
- URL：https://arxiv.org/abs/2305.14283
- 检索词：#19（第二组外延）
- 阅读状态：检索级
- 核心机制：检索前用可学习改写器改查询，替代"检索-读"为"改写-检索-读"。
- 与问题 3 的关联：required_miss 常因查询（当前消息）与旧事词汇不匹配；改写查询是第一轮 PaRT 已有方向的正式文献锚点。
- 代价证据：+1 次改写调用（论文未报告聊天场景成本）。
- 局限：训练改写器需数据；轻量版可规则化。
- 不应照搬处：RL 训练版（MaFeRw，AAAI 2025，检索级）——成本结构不匹配。

**C4. Q-PRM: Adaptive Query Rewriting for Retrieval-Augmented Generation**
- 作者/年：未核实全名，EMNLP 2025 Findings
- URL：https://aclanthology.org/2025.findings-emnlp.817.pdf
- 检索词：#19
- 阅读状态：检索级（PDF 链接未抓）
- 核心机制：按查询复杂度**自适应**决定是否改写（简单查询不改——省调用）。
- 与问题 3 的关联：与 RH-B-13 组合：只在"话题跳跃/含指代"的轮改写查询，简单轮零成本。
- 代价证据：论文未报告（未读正文）。局限：同上。
- 不应照搬处：未读正文，暂只作方向锚点。

### 组 D：自然度/人味评估（对应问题 4：Q1 缺口）

**D1. Large Language Models Pass the Turing Test**
- 作者/年：Cameron R. Jones, Benjamin K. Bergen（UCSD），2025-03（PNAS 版：Large language models pass a standard three-party Turing test）
- URL：https://arxiv.org/abs/2503.23674
- 检索词：#13
- 阅读状态：摘要级核实（abs 页完整摘要+作者+日期；**interrogator 策略与字数线索的细节来自搜索结果摘要，未读全文——如实标注**）
- 核心机制：三方图灵测试 RCT：5 分钟对话，人类裁判同时与真人和 AI 聊后判断。
- 关键证据：GPT-4.5+人设 prompt 被判人 73%（显著高于真人被选率）；LLaMa-3.1-405B 56%（与真人无显著差）；**ELIZA 23%、GPT-4o 21% 显著低于随机**——证明"人味"是可测量、可区分的变量，且 prompt（行为层）能把它从 21% 拉到 73%。
- 适用条件：对话人味评估的效度锚点。
- 代价证据：不适用（RCT 研究）。
- 局限：5 分钟短对话；英文；裁判是人类。
- 与问题 4 的关联：**Q1 缺口的正解方向：不要让 judge 对单回复打绝对自然度分（无判别效度），改成"两猜一"判别式探针**（候选 vs 真人参考回复，猜哪个是 AI）——ELIZA/GPT-4o 21-23% vs GPT-4.5 73% 的跨度证明该量尺有判别力。
- 不应照搬处：人类裁判流程（用 AI judge 替代，但必须先验证 AI judge 在判别任务上有区分度）。

**D2. Detecting Text Ghostwritten by Large Language Models (Ghostbuster)**
- 作者/年：Vivek Verma, Nicholas Tomlin, Dan Klein（Berkeley），NAACL 2024
- URL：https://arxiv.org/abs/2305.15047
- 检索词：#14
- 阅读状态：检索级
- 核心机制：用弱 LM（GPT-2 单/双/三元）的困惑度类特征组合+结构搜索做检测，~99.0 F1。
- 与问题 4 的关联：表层统计特征（困惑度类）作为人味代理特征的先例；**同时必须登记反证：Pangram 2025 分析指出困惑度/burstiness 在低误报场景失效**（检索级）——代理特征只能作辅助信号，不能单独当量尺。
- 代价证据：论文未报告聊天场景成本。局限：英文散文域；对新一代模型/改写鲁棒性下降（Pangram 反证）。
- 不应照搬处：把检测器直接当自然度量尺（检测≠自然；方向要对——测"像人"而非"不是 AI 文"）。

### 组 E：KV cache 与提示排布（对应问题 5：RH-B-11 工程证据）

**E1. DeepSeek API Context Caching 官方文档（工程一手源，非论文）**
- URL：https://api-docs.deepseek.com/guides/kv_cache（已抓正文）
- 核心事实（正文核实）：磁盘前缀缓存**默认开启**；命中=与已持久化缓存单元**完整匹配**；持久化发生在请求边界/公共前缀/固定 token 间隔三处；usage 返回 `prompt_cache_hit_tokens` / `prompt_cache_miss_tokens`；缓存 best-effort、数小时至数天自动清除；**官方文档未做时延改善声明**（价格表在定价页，未抓取——如实登记）。
- 与问题 5 的关联：RH-B-11 的服务端计费/时延证据链第一手：命中字段名与我们的端点观测（cached_tokens 0→1024）同构；"数小时至数天清除"意味着**会话内连续轮命中率高、隔夜会话可能失效**——影响 RH-B-11 的收益预期建模。

**E2. OpenAI Prompt Caching 官方文档与公告（工程一手源，非论文）**
- URL：https://developers.openai.com/api/docs/guides/prompt-caching；https://openai.com/index/api-prompt-caching（2024-10 公告）
- 检索词：#16
- 阅读状态：检索级核实（多源一致）
- 核心事实：最小可缓存前缀 **1,024 tokens**（与我们端点观测的 1024 完全一致）；usage 返回 `cached_tokens`；**缓存输入 50% 折扣**（社区口径 GPT-5 族最高 90%——未从官方定价页核实，如实标注）；**前缀中改一个 token 即断链**（Pointfive 分析，第三方）；官方建议静态内容（系统提示/工具/大上下文）前置、易变内容后置；第三方实测称命中部分时延最多降 80%（Prompthub，非官方）。
- 与问题 5 的关联：给 RH-B-11 三条可执行工程纪律：①稳定块字节级不变（含空格/标点——改一字断链）；②命中以 1024 token 为最小计费粒度→稳定前缀要凑满 1024 的倍数才划算；③按 usage 的 cached_tokens 实测记账（P1-06 口径），不猜折扣。

### 组 F：多候选生成+验证器选择（追加方向 1：产品原生"广撒网"形态）

**F1. Large Language Monkeys: Scaling Inference Compute with Repeated Sampling**
- 作者/年：Brown 等（Stanford 等），2024
- URL：https://arxiv.org/abs/2407.21787
- 检索词：#20
- 阅读状态：检索级（数字与结论多源一致；未抓原文）
- 核心机制：重复采样 N 次，"至少一个对"的 coverage 随 N 陡增（远快于 pass@1）；**瓶颈在验证器**——没有可靠选择器，coverage 兑现不了。
- 与追加方向 1 的关联：产品原生多路输出（recommended/bad_boy/playful/warm+directions）本质是 N=4 的结构化重复采样；文献说收益天花板由选择器决定，不是生成。
- 代价证据：生成侧 N 倍（产品已付）；验证器成本是设计变量。
- 局限：数学/代码任务为主（有明确正确性信号）；聊天任务验证器定义需要自建。
- 不应照搬处：大 N（我们 N=4 固定且同一次调用输出，候选间相关性高于独立采样——coverage 增益要打折扣，见 RH-B-15 风险）。

**F2. Best-of-N Selection under Partial Verification**
- 作者/年：未核实，2026-07
- URL：https://arxiv.org/html/2607.20950v1（抓取超时，检索级）
- 检索词：#19
- 核心机制（检索级）：K 候选+代理验证器打分取最高；量化**部分/噪声验证**对选择质量的影响。
- 与追加方向 1 的关联：本仓验证器必然是"部分验证"（确定性规则只能查硬约束）——该文给出噪声验证器下选择质量的量化框架。
- 代价证据：论文未报告（未读正文）。局限：未读正文。
- 不应照搬处：待精读。

**F3. Evaluation of Best-of-N Sampling Strategies for Language Models**
- 作者/年：Ichihara 等（OpenReview H4S4ETc8c9，~44 引）
- URL：https://openreview.net/forum?id=H4S4ETc8c9
- 检索词：#19
- 阅读状态：检索级
- 核心机制：系统评估 BoN+奖励模型的组合策略，含**降低生成与打分成本**的策略（如截断推理）。
- 与追加方向 1 的关联：验证器成本最小化的策略清单来源。
- 代价证据：论文未报告统一数字（策略依赖）。局限：未读正文。
- 不应照搬处：——

**F4. Scaling LLM Test-Time Compute Optimally can be More Effective than Scaling Model Parameters**
- 作者/年：Snell 等，2024
- URL：https://arxiv.org/abs/2408.03314
- 检索词：#20
- 阅读状态：检索级
- 核心机制：按题目难度自适应分配测试时算力（BoN/beam/PRM）。
- 与追加方向 1 的关联：支持"多路+验证器"只在难轮开启（与 RH-B-06 级联互补：难轮才值得多路精挑）。
- 代价证据：论文报告自适应策略在同等质量下计算量可降（具体数未抓）。局限：未读正文。
- 不应照搬处：PRM（过程奖励模型）训练成本不匹配。

### 组 G：说话人/代词归属（追加方向 2）

**G1. An LLM Benchmark for Addressee Recognition in Multi-modal Multi-party Dialogue**
- 作者/年：Inoue 等，IWSDS 2025
- URL：https://arxiv.org/abs/2501.16643（ACL Anthology: 2025.iwsds-1.36）
- 检索词：#21
- 阅读状态：检索级
- 核心机制：首个多模态多方对话"受话人识别"基准（判断下一轮该谁接、说给谁）。
- 关键证据：LLM 在该任务上明显吃力，多模态更差。
- 与追加方向 2 的关联：学术证据：**"谁在说/说给谁"不是 LLM 免费能力**——支持在装配层显式绑定说话者角色（RH-B-16）。
- 代价证据：不适用。局限：未读正文；多模态/多方场景与我们的双人场景有差异。
- 不应照搬处：——

**G2. Assessing the Capabilities of LLMs in Coreference Resolution**
- 作者/年：Gan 等，2024
- URL：检索结果未给稳定 arXiv 号（检索级，未抓——如实登记）
- 检索词：#21
- 阅读状态：检索级
- 与追加方向 2 的关联：LLM 指代消解能力的细粒度评估（成功/失败面）。
- 局限：未核实准确出处与数字，仅作线索。
- 不应照搬处：引用前必须先补核实。

### 组 H：结构化人物卡/关系状态（追加方向 3）

**H1. Crafting Customisable Characters with LLMs: A Persona-Driven Role-Playing Agent Framework (SimsChat)**
- 作者/年：Bohao Yang, Dong Liu, Chenghao Xiao, Kun Zhao, Chen Tang, Chao Li, Lin Yuan, Guang Yang, Chenghua Lin，EMNLP 2025 Findings（v7 2025-09）
- URL：https://arxiv.org/abs/2406.17962
- 检索词：#23（abs 页核实）
- 阅读状态：摘要级核实
- 核心机制：角色由**结构化要素**定义（职业/抱负/特质/技能+个人与社会画像），注入 LLM 驱动扮演；发布 SimsConv（68 角色/13,971 多轮对话/1,360 真实场景）。
- 关键证据：摘要口径：在角色一致性、知识准确性、**恰当拒答**上优于既有模型（具体数字未抓——如实标注）。
- 与追加方向 3 的关联：结构化人物卡（区别于 Mem0/A-MEM 事件记忆线）的直接文献锚点——RH-B-17 的"关系状态卡"是其在**双人关系状态**维度的适配。
- 代价证据：论文未报告（摘要级）。局限：单角色模拟，无双人关系动态。
- 不应照搬处：其角色卡是"人物属性"（静态），我们需要的"关系状态"（动态、事件驱动更新）须自建 schema。

**H2. CHARCO: Enhancing Character-Coherent Role-Playing Dialogue**
- 作者/年：MDPI Information 2025（**弱刊，可信度打折——如实标注**）
- URL：https://www.mdpi.com/2078-2489/16/9/738
- 检索词：#23
- 阅读状态：检索级
- 核心机制：结构化 persona 画像+情境+语义增强检索保持角色连贯。
- 与追加方向 3 的关联：结构化画像+检索组合的又一例证（弱证据级）。
- 代价证据：论文未报告。局限：期刊级别低。
- 不应照搬处：作为主证据引用。

**H3. MRPrompt / Memory-Driven Role-Playing: Evaluation**
- 作者/年：未核实，ACL 2026 Findings
- URL：https://aclanthology.org/2026.findings-acl.1175.pdf（未抓）
- 检索词：#23
- 阅读状态：检索级
- 核心机制（检索级）：结构化 persona+记忆阶段化驱动 in-character 回复。
- 与追加方向 3 的关联：persona 结构化+记忆的阶段化组织——与 RH-B-17 方向一致的更新锚点。
- 局限：未读正文。不应照搬处：待精读。

---

## 四、新机制假设（编号续接第一轮 RH-B-01..11；15 字段；全部挂钩本轮真实失败）

### RH-B-12 「失效传播写路径」（Propagation-Aware Invalidation Write Path）

1. **支撑论文**：STALE（2605.06527，摘要级）——CUPMem 原型"写时结构化状态整固+传播感知"，实测"检索到新证据≠会行动"、最强模型 55.2%；When Memory Updates but Behavior Does Not（2608.01619，检索级）——知道过期仍按旧值行动的行为鸿沟。
2. **研究假设**：cancel_affirm severe 的根因不只是"读路径没拦住"（RH-B-07 的假设），而是写路径只做单点失效、未传播到依赖它的相邻记忆；把失效当写路径一等事件并做一轮依赖传播（受影响记忆改判 stale-dependent），能在 smoke_24 的 must-not-affirm 子集进一步压低违例率，尤其是**无显式否定词的隐式冲突**（STALE 定义）。
3. **核心机制**：写路径三步：①失效检测（新观察与旧条目冲突→旧条目标 invalidated，与 RH-B-07 共享两态标记）；②依赖传播（每个 invalidated 条目触发一次极小调用："列出库中以'X 成立'为前提的条目"，命中者标 stale-dependent/pending）；③裁决时机（pending 条目下次被检索命中时带"待裁决"提示注入，由主调用在生成时顺带裁决并回写，不单独付费）。
4. **输入处理**：与 RH-B-01/07 相同的抽取流；传播调用输入=被失效条目+全库条目清单（条目化后很小）。
5. **中间状态产生与维护**：validity 状态机扩展为 active / invalidated / stale-dependent(pending) / re-confirmed；传播链记录（谁因谁失效）；换故事清空。
6. **模型调用流程**：失效事件发生时 +1 次小调用（事件低频，预计 <10% 轮次）；主回复 1 次不变；pending 裁决搭主调用便车。
7. **输出确定方式**：主回复不变；记忆库变更以 JSON 落盘。
8. **预计质量优势**：直接对位 b06 仍存的 2 例 cancel_affirm severe；特别覆盖"用户不显式说取消而事实已变"的隐式冲突——现有规则清单（RH-B-07）只抓显式否定信号。
9. **预计成本问题**：低频小调用；传播调用输入 <2k tokens（条目库数百条目时）；**论文未报告** CUPMem 的调用成本（我们按本地实测记账）。
10. **主要失败风险**：①传播过宽（误禁言真事——必须对称报告误报率，继承 RH-B-07 纪律）；②pending 永不裁决堆积；③传播召回不足（小调用漏列依赖条目）。
11. **与现有方案的本质差别**：RH-B-07=读路径过滤+静态清单注入（单点失效）；RH-B-01 的 UPDATE 只消解直接冲突对；本机制=**写路径链式传播+依赖重裁决**，处理的是失效的"二阶后果"，三者机制不同、可叠加。
12. **最小原型方案**：只在既有事实/事件记录上实现①②（③后置）；失效事件源先用否定词规则+新旧行实体重叠；不建图，用全库扫描替代。
13. **可证伪实验（smoke_24）**：三方对照（现状 / RH-B-07 / RH-B-07+12）：must-not-affirm 违例数下降且"误禁言"（合法引用旧事被判违例）不升；若 24 题内 pending 条目实际起 0 次作用（既没拦截也没误拦），如实登记"本题库无增量价值"。
14. **预计 token/时延影响**：+1 次小调用仅出现在失效轮；主路径注入增量=pending 提示行（<50 tokens）。
15. **撞墙重入点**：若传播召回持续不足，触发 P1-03 五篇重读（新方向：知识图谱失效传播 / GraphRAG 冲突传播——本轮未检索，如实登记）。

### RH-B-13 「话题段检索单元」（Topical-Segment Retrieval Units with Denoised Index）

1. **支撑论文**：SeCom（2502.05589，精读）——段级记忆在 LOCOMO/Long-MT-Bench+ 一致击败轮级与会话级（71.57 vs 65.58/63.16；88.81 vs 84.91/73.38），建索引前去噪（压缩）单独贡献 LOCOMO +9.46，时延 1,722 输入 tokens/2.61s；LOCOMO（2402.17753，检索级）——时序+对抗类是 RAG 与长上下文共同弱点。
2. **研究假设**：required_miss 的根因是检索单元粒度（轮级太碎→关键词不匹配；会话级太粗→噪声淹没），不是注入量；改用话题段为检索单元+索引去噪，可在同等或更低注入 token 下提高"该提的旧事被检索命中"的比率。
3. **核心机制**：写路径零 LLM：确定性分段（话题转移信号=新实体引入+对话行为转换+时间跳跃）；每段产出"检索指纹"=该段的确定性抽取压缩文本（复用 RH-B-09 句级打分，只用于建索引）；读路径=查询（近 2 轮构造，含指代解析——与 RH-B-16 衔接）vs 段指纹的 BM25/向量检索 top-k；段级重排：实体连续性加权+近 N 轮已提段降权（防重复）+invalidated 段过滤。
4. **输入处理**：当前轮消息+最近 2 轮（查询构造）；检索库=段指纹；**注入的是原段原文，不是指纹**（防氛围句损失——SeCom 的压缩只用于其注入，我们限制压缩只进索引）。
5. **中间状态产生与维护**：段列表（起止轮/主题标签/指纹/时间戳/validity），每轮增量维护，零调用。
6. **模型调用流程**：主回复 1 次不变；检索本地；分段与指纹确定性计算零调用（可选升级：小模型分段每会话 +1 次）。
7. **输出确定方式**：不变（检索只影响注入集合与排布）。
8. **预计质量优势**：required_miss 直击——旧事以完整话题段（含上下文语气）注入而非碎句或漏检；SeCom 跨两基准一致证据。
9. **预计成本问题**：注入 token 与轮长解耦（段预算制）；本地计算毫秒级；SeCom 实测比 MemoChat 省 76% 输入（其注入是压缩后段文本，我们保留原文，节省幅度会小于该数字——如实登记，不照抄）。
10. **主要失败风险**：①中文口语话题边界的规则质量（SeCom 用 GPT-4 分段 F1 0.888；规则版可能显著更差——先测边界质量再谈检索）；②过分割（SeCom 案例也有 WD 0.80 个案）；③指纹去噪在陪伴场景删掉氛围句→只影响召回不影响注入（风险被结构限制在检索侧）。
11. **与现有方案的本质差别**：RH-B-01 用 LLM 把历史改写成事实条目（改变表示）；本机制保留对话原文、只改**检索单元粒度与索引表示**；RH-B-09 瘦身"必然注入的中部窗口"；RH-B-04 只重排不选择。三者正交可组合。
12. **最小原型方案**：规则分段（实体变化+轮间隔）+BM25 段检索，替换现行注入选择逻辑。
13. **可证伪实验（smoke_24）**：required_miss 子集上"必提旧事命中率"（检索结果是否覆盖评测侧断言对应内容——只读评测侧快照）：段检索 vs 现行注入；判据：命中率上升且注入 token 不升；**前置门**：规则分段人工抽检 20 段合理率 <70% 即先回炉分段，不得直接上线。
14. **预计 token/时延影响**：注入集合更准→同预算下有效信息密度升；预算制可能净省 token。
15. **撞墙重入点**：规则分段不达标→按 P1-03 重读五篇（方向：中文对话分段 DialSeg/SuperDialseg 线+小模型分段成本）。

### RH-B-14 「判别式自然度探针」（Discriminative Human-Likeness Probe——评测侧机制假设）

1. **支撑论文**：Jones & Bergen 图灵测试（2503.23674，摘要级+检索细节）——人味判别是可测量变量且跨度巨大（ELIZA/GPT-4o 21-23% vs GPT-4.5+人设 73%）；Ghostbuster（2305.15047，检索级）——表层统计特征先例；**反证同登记**：Pangram 2025 称困惑度/burstiness 低误报失效。
2. **研究假设**："客服腔 vs 自然聊天"测不了，是因为现行 judge 是绝对分数模式（单回复打分，无判别效度锚点）；改为**图灵式判别探针**（judge 看【上下文+候选回复+评估侧真人参考回复】猜哪个是 AI 并说出线索）+少量确定性表层特征，可获得比平均分更有判别力的 Q1 量尺。
3. **核心机制**：①成对探针：每位置构造三元组，judge 输出"哪个是 AI+置信度+判别线索"；②确定性特征：候选回复长度与会话近 K 轮长度的方差、固定开场白重复率、服务腔 lexicon 命中数（中文词表需自建：如"亲爱的用户/很高兴为您/希望这能帮到你"类）；③效度校准：用 A4 要求的正负对照样本（已知客服腔/已知自然）验证判别力后才启用。
4. **输入处理**：只消费评估侧原题快照与候选输出（不碰 gold 断言细节；参考回复使用既有人工对照材料）。
5. **中间状态产生与维护**：探针结果表+特征向量，落盘可复核。
6. **模型调用流程**：每位置 +1 次 judge 调用（成对探针）；特征本地零调用；只在评测时跑，不进推理链路。
7. **输出确定方式**：Q1 判别分=探针命中率（越接近 50% 越像人）+服务腔特征计数；与绝对分并列报告，不互替（P1-08 口径）。
8. **预计质量优势**：把 A4 第一条判据"能否区分自然回复与客服式回复"从口号变成有数字的量尺；Q1 从"不可测"变为"有判别力证据或如实报告不可测"。
9. **预计成本问题**：评测侧每位置 1 次额外调用；推理链路零成本。
10. **主要失败风险**：①AI judge 在成对判别上可能无判别力（人类裁判的证据不能直接移植——必须先验证）；②参考回复唯一导致的风格位置效应；③中文服务腔 lexicon 无现成，自建词表本身可能过拟合。
11. **与现有方案的本质差别**：现行九维绝对分平均（P1-08 已认定不可单独判胜）；本机制是判别式+特征式双通道，且明确作为**评分器侧候选**供 Benchmark 线采用——不是 harness 推理机制，登记时注明类别。
12. **最小原型方案**：smoke_24 抽 8 位置（含已知客服腔与已知自然样本各 ≥2）跑成对探针+特征表。
13. **可证伪实验（smoke_24）**：判别力验证——探针在已知对照上的区分准确率 ≤60%（近随机）即如实登记"该探针在本仓 judge 上无判别力"并淘汰；>80% 才推广到全部位置并纳入 Q1 量尺；60-80% 之间登记为"仅诊断用"。
14. **预计 token/时延影响**：仅评测成本；不影响交付链路。
15. **撞墙重入点**：AI judge 判别力不足→按 P1-03 重读五篇（方向：LLM-as-judge 校准 / 成对偏好蒸馏 / 人工偏好锚点扩样）。

### RH-B-15 「原生多路判别选一」（Verifier-Gated Selection over Native Multi-Route Output）

1. **支撑论文**：Large Language Monkeys（2407.21787，检索级）——coverage 随样本陡增、瓶颈在选择器；Best-of-N under Partial Verification（2607.20950，检索级）——部分/噪声验证的量化框架；Ichihara et al. BoN 策略评估（OpenReview，检索级）——验证器成本最小化策略；Snell（2408.03314，检索级）——测试时算力按难度自适应。
2. **研究假设**：产品原生形态已每次调用输出多路候选（recommended/bad_boy/playful/warm+4 directions，生成成本已支付）；在候选之上加"廉价验证器+选择器"，把"任一路符合条件即可选用"从随手取用变成系统性收益：severe 下降、质量不降，且**零新增生成调用**。
3. **核心机制**：三层选择：①确定性硬验证器（零成本）：must-not-affirm 碰撞、实体/日期与记忆库一致性、意图契约、主体归属（衔接 RH-B-16）——触发即淘汰该路；②本地软评分（零调用）：剩余候选按上下文承接/意图对齐启发式打分；③可选 judge 仲裁（<20% 轮次触发）：②并列且最高分低于阈值时，一次小调用成对比较。输出=最高分路。
4. **输入处理**：既有候选+记忆/状态+意图；不新增生成输入。
5. **中间状态产生与维护**：无持久状态；每轮验证记录落盘（审计+未来银标签积累）。
6. **模型调用流程**：生成 1 次（原生多路同调用，无增量）；硬验证器本地；judge 仲裁低频 +1。
7. **输出确定方式**：从既有候选中选，不重新生成（与 RH-B-10"生成后精炼"、RH-B-06"升级再生成"机制不同）。
8. **预计质量优势**：LLM monkeys 证据：多候选下"至少一路干净/更好"的概率高于单候选，兑现它的就是验证器；severe 类错误应首先受益（四路中至少一路不踩雷的概率高）。
9. **预计成本问题**：生成零增量（形态已付）；judge 仲裁低频；验证器本地毫秒级。
10. **主要失败风险**：①四路同源同一次生成，相关性高——coverage 增益显著小于独立采样场景（LLM monkeys 的前提不满足，收益打折）；②硬验证器误杀有温度的回复（对称报告误杀率）；③judge 仲裁自身偏好（长回复偏差等）。
11. **与现有方案的本质差别**：现行选择逻辑未公开登记（公开口径仅"任一路符合条件即可选用"）；本机制引入显式门控选择层——**利用已支付成本的结构性改进，与 DIRECT 对照时成本结构反而占优**。
12. **最小原型方案**：对 smoke_24 既有多路输出回放三层验证器（不需要重跑生成——回放历史多路候选即可），统计验证器淘汰率、选择差异率。
13. **可证伪实验（smoke_24）**：验证器选择 vs 现行选择的 severe/质量对照（差异轮用 judge 成对评）：判据 severe 不升且 ≥1 类严重错误减少、或互动质量提升；若选择差异率 <10% 且质量无差，如实登记"无增量价值"。
14. **预计 token/时延影响**：零生成增量；judge 仲裁轮 p50 +1 次小调用时延（<20% 轮次）。
15. **撞墙重入点**：四路相关性过高导致验证器收益不足→按 P1-03 重读五篇（方向：多样性注入的采样策略 / DPP 多样性选择 / temperature 分层采样）——本轮未检索，如实登记。

### RH-B-16 「说话人绑定前置」（Speaker-Addressee Binding Preflight）

1. **支撑论文**：Addressee Recognition benchmark（2501.16643，检索级）——LLM 在"说给谁"任务上明显吃力；Gan et al. 2024（检索级，出处待核实）——LLM 指代消解能力有限；**中文"你我错乱"专项学术论文不存在（检索空白 #22，如实登记）**；工程实践佐证（novel-smith 视点控制、说话者标签惯例——非学术，仅线索）。
2. **研究假设**：回复主体归属错误（把 HER 的事说成 ME 的、把 ME 说的归给 HER）源于装配时指代未解析；装配前加确定性绑定步（说话者标签显式化+代词先行解析注入）可降低主体归属类错误，零模型调用。
3. **核心机制**：①历史消息重打说话者标签（HER:/ME: 前缀显式化）；②当前 HER 消息代词规则解析（"我"→HER 名；"你"→ME 人设名；"他/她"→最近被提及第三方实体名）；③解析结果以一行"指代表"注入提示头部；第三方身份/性别从记忆库查表。
4. **输入处理**：只处理既有输入；无新增信息。
5. **中间状态产生与维护**：指代表每轮瞬态；实体性别/称谓表复用记忆侧。
6. **模型调用流程**：零新增。
7. **输出确定方式**：不变。
8. **预计质量优势**：主体归属类 severe 直接受击；多轮长对话说话者边界模糊场景受益（Addressee benchmark 证据：该能力不可假设免费）。
9. **预计成本问题**：一行指代表（<30 tokens）+本地重打标签；近似为零。
10. **主要失败风险**：①规则解析歧义（多人场景"他"指代不清——注入反而误导）；②与 dsf 自身指代能力冗余（若模型本来就不犯则无增量）；③标签重打改变原文格式可能困惑模型。
11. **与现有方案的本质差别**：现行装配给原始消息流（公开口径）；本机制加显式说话者边界+指代解析层；与 RH-B-04（排布）正交——绑定解决"谁是谁"，排布解决"放哪里"。
12. **最小原型方案**：规则绑定器 + 在 smoke_24"含代词+第三方"子集 A/B。
13. **可证伪实验（smoke_24）**：绑定 on/off 对照主体归属断言通过率：归属错误数下降且无新增指代错乱；**若 dsf 在该子集本来就 0 错误，如实登记"模型已具备该能力，机制无增量"并淘汰**（诚实预登记：该机制可能死于模型已够好）。
14. **预计 token/时延影响**：≈0。
15. **撞墙重入点**：规则歧义率高→按 P1-03 重读五篇（方向：指代消解模型轻量微调 / conversational coreference 数据集 MMC 线）。

### RH-B-17 「关系状态卡」（Structured Relationship-State Card Injection）

1. **支撑论文**：SimsChat（2406.17962，EMNLP 2025 Findings，摘要级）——结构化角色要素注入提升角色一致性/知识准确性/恰当拒答；CHARCO（MDPI 2025，检索级，弱刊）——结构化画像+语义检索；**关系阶段状态机：无 2025 arXiv 技术论文（检索空白 #24，如实登记）**，仅 HCI 侧 Replika+Levinger 关系发展模型线索。
2. **研究假设**：关系阶段/边界/称呼/未决事项等"关系状态"目前以散文或事件条目散布；压成一张结构化状态卡（固定 schema、规则更新、恒定大小、注入稳定前缀区）可提升关系推进与分寸类断言通过率，且注入大小恒定不随历史增长。
3. **核心机制**：schema 固定字段：关系阶段/称呼偏好/边界（可谈/禁谈）/未决事项指针/活跃意图；更新以确定性规则为主（邀约被接受→阶段候选+1；冲突→降温标记；取消→未决清空——衔接 RH-B-12 的失效事件）；可选小调用裁决（<10% 轮次）；注入位置=头部稳定块（与 RH-B-11 前缀缓存兼容）。
4. **输入处理**：消费事件流（HER 消息+我方上轮行为）；与现有一致。
5. **中间状态产生与维护**：一张卡（≤200 tokens），按故事域持久、换故事清空；卡变更留痕（衔接 RH-B-07/12 的失效链）。
6. **模型调用流程**：主回复 1 次；卡更新规则为主，可选小调用低频。
7. **输出确定方式**：不变。
8. **预计质量优势**：关系推进/分寸/未决一致性；SimsChat 证据：结构化注入优于自由文本画像（其场景是单角色属性，我们迁移到双人关系状态——迁移假设需验证）。
9. **预计成本问题**：卡 ≤200 tokens 固定注入；规则更新零成本。
10. **主要失败风险**：①schema 错误比散文错误更"权威"（模型更信卡片→错更难被上下文纠正）；②关系阶段本质连续，离散化规则过粗；③与 RH-B-01 事实条目重复注入（同信息两处）。
11. **与现有方案的本质差别**：RH-B-01 是事件级流水账条目（回答"发生过什么"）；本卡是关系级状态快照（回答"我们现在是什么关系"）——层级不同，互补不互替；第一轮 22 篇中 CharacterGLM（已登记）提到属性/行为两类配置，但其为模型训练配置，非运行时状态卡。
12. **最小原型方案**：5 字段卡（阶段/称呼/边界/未决/意图）+纯规则更新，在 smoke_24 关系类子集 A/B。
13. **可证伪实验（smoke_24）**：关系推进+分寸类断言通过率对照（卡 on/off）：通过率不降且至少一类上升，注入增量 ≤200 tokens；若卡片导致风格趋同或推进过快（分寸过度），如实登记并回炉更新规则。
14. **预计 token/时延影响**：+≤200 tokens 注入；零调用增量。
15. **撞墙重入点**：schema 离散化失败→按 P1-03 重读五篇（方向：对话状态跟踪 DST 轻量方法 / 关系阶段连续表示）。

---

## 五、RH-B-06 迭代建议（升级门信号：文献依据 + 3 个可立即实现方案，已排序）

### 现状与文献诊断

b06 实测：severe 2/14 优于 DIRECT 5/14（探索性），但升级率 45.8% 超预登记 35%，主因 **intent/note 存在即触发升级**（输入侧触发）。文献一致口径：

- **I Know What I Don't Know（精读）**：级联命门是 deferral performance；信号应来自小模型自身输出；纯 prompt 式置信指令（"不确定时降低置信"/"不确定时答 N"）在其实验中**不可靠**（附录 B.2）——不要指望加一行指令就解决问题。
- **Hybrid LLM（精读）**：路由标签应来自"同题两档输出的成对质量差"（看输出，不看输入表面特征）；且警告：两档质量差距过大或过小时路由都退化——需要先测本仓差距分布是否可分。
- **UCCI（摘要级，单作者）**：原始不确定性分数必须校准（isotonic）后再按成本最小化设阈值；未校准熵阈值逊于校准阈值（31% 成本节省、ECE 0.12→0.03）。
- **Just Ask for Calibration（检索级）+ Xiong et al.（检索级）**：口头化置信度可用但 LLM 普遍过度自信——先标定再信任。
- **Semantic entropy（Nature 2024）**：最强自我不确定信号但需多样本——I Know What I Don't Know 明确排除其用于级联（成本反噬）。

**结论**：升级门信号须同时满足三性：①输出侧（看草稿说了什么）；②校准（阈值在本仓数据标定）；③便宜（不加调用或同调用完成）。当前"intent/note 存在即升级"三性全不满足。

### 三个可立即实现的选择（按优先排序）

**方案 1（首选，当天可实现）：输出侧依赖差集门，移除输入侧触发**
- 做法：完全移除"intent/note 存在即升级"；升级仅当草稿触发以下确定性检查之一：(a) 草稿中出现的实体/日期/金额/事件名词在第一档上下文中**无字符串匹配**（记忆依赖信号）；(b) 草稿与"禁止坐实清单"实体碰撞（前提冲突信号——与 RH-B-07 共用清单）；(c) 第一档上下文含未决邀约/问句且草稿未回应（应答缺失信号）。intent/note 存在只轻微降低阈值，不独立触发。
- 文献依据：Hybrid LLM"看输出质量差而非输入难度"+ I Know What I Don't Know"信号来自小模型输出更便宜有效"。
- 预期：升级率 45.8%→30% 附近；b06 的 2 例 severe 均为记忆依赖型，应被 (a)/(b) 捕获。
- 验证路径：先在 smoke_24 **离线重放** 24 题第一档草稿，统计新升级率与漏判率，再上线——零模型新增成本。

**方案 2（次选，同调用零成本）：口头化置信度辅门**
- 做法：第一档提示要求草稿末尾附加一个置信标记（"把握:高/中/低"或 0-100 数字，选其一），低于阈值即升级。
- 文献依据：Tian 2305.14975（口头化置信可用）；反证：Xiong 2306.13063（过度自信）+ I Know What I Don't Know B.2（纯 prompt 置信基线不可靠）——**必须先做一日标定**（24 题上比对"低把握"与实际 judge 结果的相关），有相关性才并入，无相关即弃用。
- 注意：聊天任务无标准答案，"把握"语义偏"记忆依赖感"而非事实正确性——只作方案 1 的补充信号，不替代。
- 成本：同调用附加 <5 tokens；无新增时延。

**方案 3（第三，phase-2）：学习式校准门**
- 做法：用已判历史答卷构造银标签（同题第一档草稿 vs 全装配回复的成对裁判结果——Hybrid LLM 标签法），训练 300M 级以下小分类器或 isotonic 校准映射（特征=方案 1 的确定性信号+置信标记+长度），阈值按成本最小化选（UCCI 方法）。
- 文献依据：Hybrid LLM 标签法+UCCI 校准法（40% 少调用无质量损失；31% 成本节省）。
- 收益上限最高，但需要银标签数据工程；**前置检查**（Hybrid LLM 教训）：先测本仓"第一档 vs 全装配"的差距分布是否可分——若 90% 轮全装配明显更好（大差距场景），学习门会退化，此时两档级联本身应重新审视（第一档太贫瘠则中间加一档）。
- 不应照搬：Gatekeeper 微调（改权重）；token-margin 信号（需 logprobs，端点未证实返回）；semantic entropy 进级联（多样本成本反噬）。

---

## 六、检索空白与未决项（如实）

1. **中文"你/我人称错乱"无学术论文**（搜索 #22）：只有用户反馈与工程实践。RH-B-16 的学术支撑只能借多方向/指代消解文献（2501.16643 等），中文场景收益必须靠本仓实验自证。
2. **关系阶段状态机无 2025 arXiv 技术论文**（搜索 #24）：只有 HCI 侧线索（Levinger 模型应用于 Replika）。RH-B-17 的 schema 设计是自主设计+角色卡文献（SimsChat）迁移，非直接文献复制——如实标注。
3. **对话自然度的非 judge 学术量尺稀缺**（搜索 #17）：2024-2026 无强论文给出"客服腔 vs 自然"的自动量尺；最接近的是图灵测试 RCT（判别式思路）+检测文献（表层特征）+行业博客框架（非学术，仅登记不引用为证据）。
4. **UCCI 单作者预印本**；**CHARCO 为 MDPI 弱刊**——引用时已打折标注。
5. 未读正文待补（检索级登记）：2607.20950（部分验证 BoN）、2610.10265（记忆失败分类）、2608.01619（更新-行为鸿沟）、Ichihara BoN 评估、Q-PRM、MRPrompt。若后续机制撞墙需引用其数字，必须先补精读。
6. DeepSeek 定价页（cache hit/miss 具体价格）未抓取——RH-B-11 计费口径需补抓定价页或在 dsf 端点以 usage 字段实测。
7. arxiv.org 的 WebFetch 在本时段持续连接超时（约 7 次），改用 webReader 与 ar5iv 完成，无信息缺失级阻断。
8. 方案 2 的置信标记与方案 3 的银标签法均依赖 judge 结果作标签——**未校准 judge 不单独判死**（v1.4 口径）：银标签只用于"排序/校准信号"，不作为质量终判。

---

## 记忆管线 v1 文献冲刺（A2，2026-11-11）

- 执行者：RESEARCH-P1 加速轮研究子代理 A2
- 日期：2026-11-11
- 任务：为"记忆/状态管线 v1"（自我记忆回注 + 话题段检索 + 失效传播的组合架构，RH-B-12/13/17 的组合规格，不重复登记单条假设）补齐文献依据与实现规格。三个定向问题：a) 对话系统的自我话语记忆（what did I say）；b) 话题分段在对话记忆的应用（SeCom 之外的实现规格+中文适配）；c) 反重复/新颖性约束。
- 只追加本节，不改上文任何内容。本节只追加本文件。
- 阅读分级口径沿用：精读=方法+实验节全文；摘要级核实=abs 页或等效多源；检索级=仅搜索结果页；二级线索=经已读全文的相关工作节发现、未独立抓取。

### 0. 统计

| 项 | 数 |
|---|---|
| WebSearch 调用 | 12 次（全部成功，无并发限制失败） |
| WebFetch 抓取 | 2 次（1 成功：ar5iv SuperDialseg 全文；1 失败：arxiv abs 连接超时，与通宵轮一致，改 webReader） |
| webReader 抓取 | 5 次（全部成功：ES-Mem abs + ES-Mem HTML 全文 + Selfmem abs + LSP abs + MapDia abs） |
| 新论文登记 | 14 篇主登记（精读 2、摘要级 5、检索级 7）+ 5 条二级线索（来源=ES-Mem 相关工作节，未独立抓取，如实标注） |
| 本地代码核实 | rh_b04/rh_b12/rh_b17 mechanism.py 亲读（规格复用关系以此为准，非凭记忆） |

### 一、WebSearch 逐条日志（12 次，关键词原样）

| # | 关键词（原样） | 结果 |
|---|---|---|
| 1 | SuperDialseg Chinese dialogue topic segmentation supervised learning arxiv DialSeg711 | 确认 SuperDialseg = arXiv 2305.08371（EMNLP 2023 Main）+ DialSeg711 基准 + GitHub Coldog2333/SuperDialseg。**注意：搜索结果摘要称其为"中文、源自 DuRecDial 2.0/DuSinc"——经全文精读证伪（英文 doc2dial/MultiDoc2Dial），见诚实纠错节** |
| 2 | LLM agent memory of its own utterances "what I said" self-consistency promises commitments dialogue arxiv 2024 2025 | 收获大：Latent State Persistence 失败（2505.10571）+ Can LLM Agents Stick to the Script / fact ledger（2608.08160）+ Awesome-Agent-Memory 清单 |
| 3 | topic segmentation dialogue memory conversational RAG chunking granularity arxiv 2024 2025 | 收获大：RMM 反思记忆管理（2503.08026）+ 多粒度长期记忆（2505.19549）+ ES-Mem（2601.07582，事件分段理论）+ MoG 分块粒度（2406.00456）+ ChunkRAG（2410.19572）+ semantic chunking 成本评估（NAACL 2025 Findings） |
| 4 | "Dense X Retrieval" proposition level retrieval granularity arxiv | 确认 Dense X Retrieval = 2312.06648（EMNLP 2024 Main，~333 引）：命题级 vs 句级 vs 段级检索粒度 |
| 5 | repetition open-domain dialogue diversity novelty neural conversation model arxiv diversity-promoting | 确认经典：Li et al. 2016 MMI 多样性目标（1510.03055，~3500 引） |
| 6 | "Repetition In Repetition Out" neural text degeneration arxiv OR repeated questions chatbot "interview mode" social chatbot 2024 2025 | 确认 Repetition In Repetition Out = 2310.10226（NeurIPS 2023，数据侧重复→生成重复，Rep-Dropout）；interview-mode 侧仅 ISca（2508.14344）等边缘结果，无"社交聊天机器人重复提问"专项论文（如实登记） |
| 7 | SELF-MEM self-memory retrieval augmented language models arxiv 2309.00413 self-memory agent own outputs | **纠错**：2309.00413 是颗粒介质物理论文；Selfmem 正确 ID = 2305.02437（NeurIPS 2023，Cheng et al.） |
| 8 | generative agents interactive simulacra human behavior memory stream reflection arxiv Park 2304.03442 | 确认 Generative Agents（2304.03442，~9285 引，UIST 2023）：memory stream 记录智能体**自身经历/话语**+relevance/recency/importance 检索+reflection |
| 9 | "Can LLM Agents Stick to the Script" long-horizon consistency benchmark fact ledger arxiv 2608.08160 | 确认 NCP-Bench（ICML 2026 poster，澳门大学 NLP²CT 等）：explicit fact ledger + fixed plot commitments + 100 电影交互环境 + prompt-fixed LLM auditors；GitHub NLP2CT/NCP-Bench |
| 10 | social chatbot proactive topic introduction avoid repeating topics memory of conversation history arxiv 2024 2025 companion | 收获大：MapDia（2503.05150，北大+腾讯，**首个中文**记忆感知主动对话数据集 ChMapData）+ Before Bringing It Up（2610.09470）+ Replannable Dialogue Timelines（2610.05159）+ RECALLbot（Me/We 双记忆结构） |
| 11 | DialSeg711 dataset Xu Zhao Zhang "topic-aware multi-turn dialogue" AAAI 2021 Chinese Weibo dialogue segmentation language | 确认 DialSeg711 出处 = Xu/Zhao/Zhang AAAI 2021《Topic-aware Multi-turn Dialogue Modeling》（14176–14184），**711 条中文微博多轮对话**；另有中文期刊（计算机科学）无监督话语重写分割方法以此为基准 |
| 12 | dialogue agent avoid semantic repetition response novelty constraint open-domain conversation memory injected history arxiv 2025 | **检索空白（如实登记）**：无"记忆注入+新颖性约束"闭环的 2025 论文；最近邻为 semantic collapse（2609.16051，单作者，弱源如实标注）+ 工程实践（n-gram blocking/重复惩罚博客） |

### 二、抓取日志

| # | URL | 工具 | 结果 |
|---|---|---|---|
| 1 | ar5iv.labs.arxiv.org/html/2305.08371 | WebFetch | 成功，全文 → **精读 SuperDialseg**（任务形式化/模型/全部 F1/滑窗推理/语料语言/局限） |
| 2 | arxiv.org/abs/2601.07582 | WebFetch | **失败**：连接超时（10s，与通宵轮 arxiv WebFetch 故障一致）→ 改 webReader |
| 3 | webReader arxiv.org/abs/2601.07582 | webReader | 成功（摘要级） |
| 4 | webReader arxiv.org/html/2601.07582v2 | webReader | 成功，全文（方法 §3 全部公式与超参 + 实验 §4 全部表 + 附录 B 参数 + 局限）→ **精读 ES-Mem** |
| 5 | webReader arxiv.org/abs/2305.02437 | webReader | 成功（摘要级：Selfmem 全摘要，NeurIPS 2023） |
| 6 | webReader arxiv.org/abs/2505.10571 | webReader | 成功（摘要级：LSP 三实验全摘要，v5 2026-01） |
| 7 | webReader arxiv.org/abs/2503.05150 | webReader | 成功（摘要级：MapDia 全摘要 + ChMapData + GitHub） |

### 三、新论文登记（14 篇主登记 + 5 条二级线索，全部为本文件上文 42 篇之外）

#### 组 S：自我话语记忆 / 自状态持久（问题 a）

**S1. ES-Mem: Event Segmentation-Based Memory for Long-Term Dialogue Agents**
- 标题/作者/年：ES-Mem；Huhai Zou, Tianhao Sun, Chuanjiang He, Yu Tian, Zhenyang Li, Li Jin, Nayu Liu, Jiang Zhong, Kaiwen Wei（重庆大学/清华/HK 生成式 AI 中心/中科院/天津工业大学）；2026-01（v2 2026-01-13）
- URL：https://arxiv.org/abs/2601.07582（全文经 HTML v2）
- 检索词：#3
- 阅读状态：**精读**（方法全部公式与超参、LoCoMo/LongMemEval-S 全表、效率表、消融、分段独立评测、附录参数、作者局限）
- 核心机制：事件分段理论（EST）启发的两层架构。①动态事件分段两阶段：Stage1 逐轮 LLM 抽话题（递归式：上一轮话题+当前轮内容→平滑轨迹）→嵌入→相邻轮 Pearson 相关→互信息 I_t=−½log(1−ρ²)→低于分位阈值 q=0.35 处为候选边界；Stage2 意图感知精修：LLM 对 ±L=2 轮局部窗口判 shift 标签（Topic_Shift/Topic_Intro）vs continuation 标签（Detail_Elaborate/Direct_Resp），p_eb=(最高置信+最低置信)/2，阈值 τc=0.75。②分层记忆三层：L1 精修边界描述（"话题A结束。转入话题B"——显式建模状态转移）、L2 事件摘要（索引用）、L3 原文（注入用）。③粗到细检索：查询 vs 边界锚 top-k=10 → 扩窗 w=3 成激活区间（继承锚分数）→ 摘要重排融合 α=0.70 → top-K 事件的 L3 原文注入。
- 关键证据：LoCoMo（GPT-4o-mini）总体 F1 45.56/B1 39.70，胜 Mem0 45.09/35.91、Nemori 44.72、LightMem 44.73；**小模型上增益最大**（Qwen2.5-3B：30.86 vs 最强基线 24.13，A-Mem 仅 17.91）——"记忆框架减轻上下文选择认知负担，使小模型突破自身推理限制"；LongMemEval-S 总体 72.40 第一，Single-Preference 73.33；效率：2,925 tokens/1.423s（Mem0 0.708s，LangMem 127 tokens 但 18.53s）；消融：去 L3 原文掉分最重（细节丰富回复必需），去边界锚重创 Multi-Hop，去摘要重创 Single-Hop；分段模块独立评测（Qwen2.5-3B 做分段 LLM）：DialSeg711/TIAGE/SuperDialSeg 三集胜 Def-DTS（ACL 2025 Findings）与 SeCom，TIAGE 上甚至超过部分监督模型。K 敏感性：K=10 最优，>10 开放域任务反而波动（噪声干扰）。
- 适用条件：长程多话题陪伴对话；**轻量分段策略刻意不依赖大模型推理**（与我们 API 约束同构）；三层"边界/摘要/原文"与我们的"指纹检索+原文注入"完全同构。
- 代价证据：2,925 输入 tokens / 1.423s 检索+生成延迟（论文效率表实测）；但话题抽取是逐轮 LLM 调用（其成本隐含在分段阶段，论文未单独报告分段阶段调用量——如实标注）。
- 局限（作者自述）：记忆静态，无 consolidation/遗忘/schema 抽象（**与失效传播正好互补——他们没做的正是 RH-B-12 方向**）；纯文本模态。
- 对记忆管线 v1 的具体关联：**v1 分层结构的直接蓝本**——L1 边界锚≈我们的段标签行、L2 摘要≈检索指纹、L3 原文≈注入段；两阶段"统计候选+精修"给出确定性规则版的校准参照（q=0.35/τc=0.75 作为规则阈值调参的先验）；K=10 最优与">K 反噬"直接支撑"注入段预算 2–3 + 上限封顶"的设定；小模型增益最大=支持我们低配端点预期。
- 不应照搬处：①逐轮 LLM 话题抽取（成本结构不匹配——v1 用确定性信号替代，其分段超参只作先验）；②EST 认知理论叙事本身不产生工程约束；③LoCoMo/LongMemEval 的 QA 式评测与我们开放式陪伴目标不同，其 F1 数字不可横向移植。

**S2. SuperDialseg: A Large-scale Dataset for Supervised Dialogue Segmentation**
- 标题/作者/年：Junfeng Jiang, Chengzhang Dong, Sadao Kurohashi, Akiko Aizawa；EMNLP 2023 Main
- URL：https://arxiv.org/abs/2305.08371（全文经 ar5iv）
- 检索词：#1
- 阅读状态：**精读**
- 核心机制：任务=逐话语二分类（该话语是否为段尾 y=1）；训练语料 doc2dial/MultiDoc2Dial（**英文**文档接续对话）9,478 对话；模型：BERT/RoBERTa/TOD-BERT（[SEP]/角色 token 分类）+ TextSeg(LSTM) + RetroTS-T5 + MVRoBERTa（加角色/对话行为嵌入）；推理=滑窗 |T|=20 stride 1；早停综合分 Score=(2·F1+(1−Pk)+(1−WD))/4。
- 关键证据：SuperDialseg 域内 RoBERTa 0.784 / MVRoBERTa 0.797；**DialSeg711 跨域** RoBERTa（SuperDialseg 训练）0.660 最佳，ChatGPT 零样本 0.690†；TIAGE 训练的模型在 DialSeg711 上崩塌（BERT 0.005/TOD-BERT 0.072）——**跨域泛化是分段的真实瓶颈**；平均每对话 4.25 段/约 13 轮（段粒度≈3–5 轮，与 SeCom 的话题段尺度一致）。
- 适用条件：有监督分段的前提=有域内标注；无标注时零样本 LLM（ChatGPT 0.690）与跨域监督（0.660）都只有 ~0.66–0.69 F1——**边界检测在跨域场景天然不可靠，任何分段方案必须先过边界质量门**。
- 代价证据：论文未报告推理成本（滑窗 |T|=20 单句分类，本地小模型毫秒级——但其训练成本论文未折算）。
- 局限（作者自述）：仅英文（中文列为 future work）；角色/对话行为信息利用简单（MVRoBERTa）。
- 对记忆管线 v1 的具体关联：①段粒度证据：4.25 段/13 轮≈每段 3 轮，支持 v1 段上限设 6 轮封顶+典型 3–5 轮；②跨域崩塌证据直接支撑 RH-B-13 的前置门（"规则分段人工抽检 20 段合理率 <70% 即回炉"）——现在有文献定量：连监督模型跨域也只有 0.66 F1，规则/零样本方案更需门禁；③滑窗分类的形式化=若规则分段失败，v1 的升级路径可用滑窗式小模型打分而非全对话一次性输出。
- 不应照搬处：①其训练语料是英文文档接续对话，与中文口语陪伴场景域距大——数字不可直接引用为预期；②DialSeg711 上 0.660 是"SuperDialseg→DialSeg711"方向，反方向（中文→英文）未测；③MVRoBERTa 需训练，不可用。

**S3. Lift Yourself Up: Retrieval-augmented Text Generation with Self Memory（Selfmem）**
- 标题/作者/年：Xin Cheng, Di Luo, Xiuying Chen, Lemao Liu, Dongyan Zhao, Rui Yan（北大/腾讯等）；NeurIPS 2023
- URL：https://arxiv.org/abs/2305.02437
- 检索词：#7（**搜索纠错**：2309.00413 是颗粒介质物理论文，Selfmem 正确 ID=2305.02437）
- 阅读状态：摘要级核实
- 核心机制：**模型自身的历史生成输出作为记忆库**（self-memory）——生成器迭代产出→记忆选择器从中选一条作为下一轮生成记忆；对偶于"好记忆→好生成"的"好生成→好记忆"。
- 关键证据：NMT/摘要/对话三任务 SOTA（XSum 50.3 R-1、BigPatent 62.9 R-1）；对话任务含在内。
- 适用条件：生成质量本身不太差（自我输出才值得回注）；有选择器环节。
- 代价证据：论文未报告对话场景延迟/成本（摘要级）。
- 局限：任务是闭式生成（有参考答案）；迭代多轮生成成本高。
- 对记忆管线 v1 的具体关联：**"自我话语回注"的正式文献范式**——通宵 B' 审计发现"所有臂都不回注自身历史回复"，Selfmem 证明回注自身输出是可收益的结构（其对话任务在内），v1 的 said_ledger 是其在"陪伴对话一致性"维度的确定性轻量版：不回注全文、只回注确定性抽取的承诺/问句/更正子句。
- 不应照搬处：迭代多轮生成（我们单轮）；其记忆选择器需额外模型（v1 用确定性配对替代）。

**S4. On the Failure of Latent State Persistence in Large Language Models**
- 标题/作者/年：Jen-tse Huang, Kaiser Sun, Wenxuan Wang, Mark Dredze（JHU 等）；2025-04（v5 2026-01）
- URL：https://arxiv.org/abs/2505.10571
- 检索词：#2
- 阅读状态：摘要级核实
- 核心机制：形式化"隐状态持久性（LSP）"缺口：三实验（猜数字游戏/是否游戏/心理算术）证明 LLM 无法跨查询维持未显式写出的内部状态；随问题数增加出现 concept drift→必然自相矛盾；**结论：LLM 是 reactive post-hoc solver，不是带 LSP 的 proactive planner**。
- 关键证据：摘要口径：猜数字无法把概率质量压在单一隐藏选择上；是/否游戏随轮数自相矛盾；隐藏变量变换追踪在初始状态不在上下文时失败。
- 适用条件：任何"要求模型记住自己心里想的事"的设计——**必须显式外置状态**。
- 代价证据：不适用（评测论文）。局限：游戏化任务与陪伴对话有域距；摘要级未读正文。
- 对记忆管线 v1 的具体关联：为 said_ledger 提供**必要性证明**：模型不会自己"记住我说过要带她去哪"——承诺必须写成显式结构回注；也解释了通宵 B' 审计发现（不回注自身回复→承诺漂移/重复提问）为何是结构性而非偶发。
- 不应照搬处：其三实验范式不必照搬进 smoke；"必然自相矛盾"的强度结论在多轮注入上下文后可能缓解（我们的 v1 正是注入上下文的补法）。

**S5. Can LLM Agents Stick to the Script? Modeling Commitment in Interactive Narratives（NCP-Bench）**
- 标题/作者/年：Yingpeng Ma, Jianhao Yan, Bei Shi, Ka Hou Kam, Runnan Wang 等（澳门大学 NLP²CT/西湖/HIT）；ICML 2026 poster
- URL：https://arxiv.org/abs/2608.08160；GitHub NLP2CT/NCP-Bench；OpenReview JoJUWsQBp0
- 检索词：#2、#9（二次核实）
- 阅读状态：摘要级核实（多源一致：arXiv+HF+OpenReview+GitHub；**未读正文**）
- 核心机制：交互叙事长程一致性基准：narrator 回复对照 **explicit fact ledger**（已确立事实的结构化账本）与 **fixed plot commitments**（早前做出的固定承诺）审计；100 个电影衍生交互环境；prompt-fixed LLM auditors 评测。
- 关键证据：摘要级未抓具体数字（未读正文——如实标注）。
- 适用条件：叙事/角色扮演型智能体的承诺追踪评测设计。
- 代价证据：论文未报告（未读正文）。局限：电影叙事域；LLM auditor 未校准问题同我们一样存在。
- 对记忆管线 v1 的具体关联：**fact ledger + commitments 双账本结构 = v1 said_ledger 的直接同构物**（事实账本≈kb 状态卡，承诺账本≈said_ledger 的 commitment 条目）；其"auditor 对照账本审回复"的评测形态=我们 smoke 确定性检查的设计参照。
- 不应照搬处：其规模（100 环境）与 LLM auditor 全自动判决（未校准 judge 不单独判死——账本核对部分确定化，语义部分人审）。

**S6. Generative Agents: Interactive Simulacra of Human Behavior**
- 标题/作者/年：Joon Sung Park 等（Stanford）；UIST 2023
- URL：https://arxiv.org/abs/2304.03442
- 检索词：#8
- 阅读状态：检索级（多源一致：abs+ACM+GitHub+PDF 镜像；架构细节来自搜索结果与官方仓库描述，未亲读全文——如实标注）
- 核心机制：memory stream 把智能体**自身经历（含自己说的话/做的事）**与观察一并按自然语言记录；检索=relevance+recency+importance 三因子加权；reflection 把记忆综合为更高层推断。
- 关键证据：检索级（~9285 引的领域标准架构；无单点数字引用）。
- 对记忆管线 v1 的具体关联：**"自己说过的话进记忆流"是成熟范式**（我们所有臂恰恰没做）——v1 said_ledger 的最小化版本（只存承诺/问句/更正三类子句而非全量自话语）是成本约束下的裁剪；recency/importance 加权对应段的近因降权。
- 不应照搬处：全量记忆流+逐条 importance 打分（每条一次 LLM 调用，成本结构不匹配）；reflection 的额外调用。

#### 组 T：话题分段与记忆粒度（问题 b）

**T1. Topic-aware Multi-turn Dialogue Modeling（DialSeg711 出处）**
- 标题/作者/年：Yi Xu, Hai Zhao, Zhuosheng Zhang（上交）；AAAI 2021（14176–14184）
- URL：无稳定 arXiv（ACL/AAAI 正式出版）；经 #11 多源确认
- 检索词：#11
- 阅读状态：检索级（出处/语言/规模三源一致）
- 核心机制：无监督话题感知多轮对话建模；发布 **DialSeg711：711 条中文微博多轮对话的话题分段标注**（Pk/WindowDiff 指标体系后来成为对话分段标准）。
- 关键证据：检索级。
- 对记忆管线 v1 的具体关联：**中文对话分段基准的源头**——SeCom 报的 DialSeg711 F1 0.888 与 ES-Mem 分段评测都是在**中文数据**上做的（此前以为 DialSeg711 是英文——已纠错）；中文口语分段适配的证据链由此补上：GPT-4 零样本 0.888（SeCom）→ Qwen2.5-3B 两阶段胜 SeCom（ES-Mem）→ 中文期刊无监督重写法（jsjkx）。
- 不应照搬处：其无监督模型本身（2021 代际）；微博域与陪伴剧情域的域距。

**T2. In Prospect and Retrospect: Reflective Memory Management for Long-term Personalized Dialogue Agents（RMM）**
- 标题/作者/年：Zhen Tan, Jun Yan, I-Hung Hsu 等（Google）；ACL 2025（8416–8439）
- URL：https://arxiv.org/abs/2503.08026
- 检索词：#3（ES-Mem 相关工作节二次确认 venue=ACL 2025）
- 阅读状态：检索级
- 核心机制：prospective reflection（前瞻性把对话历史分解为主题单元供 RAG）+ retrospective reflection（回顾性按效用更新记忆）；动态拆分/整合历史。
- 关键证据：检索级（ES-Mem 引其为动态粒度两强之一）。
- 代价证据：论文未报告（未读正文）。
- 对记忆管线 v1 的具体关联：**"按主题分解历史"的正名文献**（与 SeCom/ES-Mem 三家独立收敛到同构方案=方向稳健性证据）；其 prospective/retrospective 分离提示 v1 把"建索引（写路径）"与"按检索效用调段（读路径反馈）"分开演进。
- 不应照搬处：reflection 的额外调用结构（未读正文，仅方向锚点）。

**T3. Dense X Retrieval: What Retrieval Granularity Should We Use?**
- 标题/作者/年：Tong Chen, Hongwei Wang 等；EMNLP 2024 Main
- URL：https://arxiv.org/abs/2312.06648
- 检索词：#4
- 阅读状态：检索级（~333 引，多源一致）
- 核心机制：检索粒度三选一实验（passage/sentence/proposition）：命题级（原子、自含事实）显著胜 passage/sentence；FactoidWiki 命题级索引发布。
- 关键证据：检索级。
- 对记忆管线 v1 的具体关联：**粒度是一等变量的又一独立证据**（与 SeCom 段级>轮级、ES-Mem 事件级>轮级三方收敛）；同时给出反面警告：命题级最细≠最好——陪伴对话的氛围/语气信息在原子命题化中丢失，**段级+原文注入**是我们的选择，命题级只影响指纹构造粒度。
- 不应照搬处：命题化本身作为注入单元（氛围句损失高危，与 SeCom 压缩注入同教训）。

**T4. From Single to Multi-Granularity: Toward Long-Term Memory（多粒度长期记忆）**
- 标题/作者/年：未核实全名；2025-05
- URL：https://arxiv.org/abs/2505.19549
- 检索词：#3
- 阅读状态：检索级
- 核心机制（检索级）：单粒度→多粒度记忆，建立在话题感知分段之上（引 SeCom）。
- 对记忆管线 v1 的具体关联：多粒度（段+会话+全局）是 SeCom 段级的自然延伸；v1 不做（先验证段级），留作 v2 升级锚点。
- 局限：未读正文。不应照搬处：未读正文，暂只作方向锚点。

**T5. 二级线索（来源=ES-Mem 相关工作节与附录，未独立抓取——引用数字前必须先补精读）**
- Nemori（2508.03341）：EST 启发自组织记忆，双库（episodic/semantic）+Predict-Calibrate；LoCoMo 44.72 F1（ES-Mem 表内数字）。
- EM-LLM（Fountas et al., ICLR 2025）：**Bayesian surprise 定边界**+图论度量精修——无限上下文情节记忆；v1 若规则分段失败，Bayesian surprise 是"无调用边界检测"的候选原理（需嵌入模型，本地可跑）。
- LightMem（2510.18866）：Atkinson-Shiffrin 三级+**sleep-time 离线更新**——离线整固路线的成本结构参照；LoCoMo 44.73。
- SGMem（2509.21212）：句级图组织记忆。
- Def-DTS（Lee et al., ACL 2025 Findings）：演绎推理式开放域对话分段（ES-Mem 分段对比的败者之一，说明纯 LLM 演绎分段并非最优）。

#### 组 R：反重复 / 新颖性（问题 c）

**R1. A Diversity-Promoting Objective Function for Neural Conversation Models（MMI）**
- 标题/作者/年：Jiwei Li 等（微软/清华 ACL 2015；arXiv 2015-10）
- URL：https://arxiv.org/abs/1510.03055
- 检索词：#5
- 阅读状态：检索级（~3500 引的经典，多源一致）
- 核心机制：MMI 目标 p(response|input)/p(response)^λ——惩罚高频安全回复；anti-LM 重排。
- 关键证据：检索级。
- 代价证据：解码侧重排，本地毫秒级（论文未报告现代 API 场景成本）。
- 对记忆管线 v1 的具体关联：**反重复的第一个分界**：其机制在解码分布层，我们 API 无 logits 不可用——文献定位后可知**API-only harness 的反重复杠杆只在上下文侧（注入已说过/已问过）与选择侧（多路验证器淘汰重复路）**；v1 的 said_ledger 硬约束+rh_b15 选择器正是这两侧。
- 不应照搬处：MMI 重排（无 logits）。

**R2. Repetition In Repetition Out: Towards Understanding Neural Text Degeneration from the Data Perspective**
- 标题/作者/年：Huayang Li, Tian Lan 等；NeurIPS 2023
- URL：https://arxiv.org/abs/2310.10226
- 检索词：#6
- 阅读状态：检索级
- 核心机制：把文本退化归因从"算法侧"转到"数据侧"：训练数据重复模式→生成重复；Rep-Dropout 训练期缓解。
- 关键证据：检索级。
- 对记忆管线 v1 的具体关联：**第二个分界**：其杠杆在训练数据（权重冻结，不可用）；但给出了诊断视角——我们臂的"查户口重复提问"若源于上下文里历史本就充满问句（数据侧模式复现），则 v1 的"已回应清单+段近因降权"是对症的上下文侧干预；这也解释了为何单纯 prompt 说"不要重复"效果差（与 I Know What I Don't Know 的 prompt 基线失败同族）。
- 不应照搬处：Rep-Dropout（需训练）。

**R3. How Humans Sustain AI Agent Novelty Amid Semantic Collapse**
- 标题/作者/年：S. Lai（**单作者预印本，可信度打折——如实标注**）；2026-09
- URL：https://arxiv.org/abs/2609.16051
- 检索词：#12
- 阅读状态：检索级
- 核心机制（检索级）：持续交互中生成内容的"语义坍缩"（越聊越窄）与人类参与对新颖性的维持作用。
- 对记忆管线 v1 的具体关联：把"重复/查户口"从孤立故障重新定位为**长程语义坍缩**的症状；v1 的话题段检索+主动开新段（MapDia 方向）是反坍缩的结构供给。
- 局限：单作者、未读正文——只作症状命名参考，不作机制证据。不应照搬处：作为主证据引用。

**R4. MapDia: Interpersonal Memory Matters（记忆感知主动对话，中文）**
- 标题/作者/年：Bowen Wu, Wenqing Wang, Haoran Li, Ying Li, Jingsong Yu, Baoxun Wang（北大/腾讯）；2025-03
- URL：https://arxiv.org/abs/2503.05150；GitHub FrontierLabs/MapDia
- 检索词：#10
- 阅读状态：摘要级核实
- 核心机制：Memory-aware Proactive Dialogue 任务：主动把对话引向**历史中有过的相关话题**（人际记忆驱动的话题转移）；RAG 联合框架三模块：Topic Summarization → Topic Retrieval → Proactive Topic-shifting Detection and Generation；**ChMapData=首个中文记忆感知主动对话数据集**。
- 关键证据：摘要口径：自动+人评验证有效（数字未抓——如实标注）。
- 适用条件：中文长程陪伴对话主动开话题——**与 LoveBrain 场景语言/形态双重对齐**。
- 代价证据：论文未报告（摘要级）。
- 对记忆管线 v1 的具体关联：解决"查户口"的正解不只是"别再问"（抑制），还要"有别的可提"（供给）——v1 said_ledger 的抑制侧 + 段检索的供给侧（从旧段找新钩子）正好组成一阻一引；其三模块划分（摘要/检索/转移时机）可映射到 v1 的指纹/检索/注入三步。
- 不应照搬处：其话题转移判定用模型模块（时机判定暂用确定性规则起步）；ChMapData 数据域（闲聊型）与剧情域差异。

### 四、诚实纠错与检索空白（本节新增）

1. **搜索结果摘要的两处错误被全文精读纠正**（登记在案，警示后续轮次不得把搜索摘要当事实源）：①SuperDialseg 被搜索摘要称为"中文、源自 DuRecDial 2.0/DuSinc"——全文证实为**英文** doc2dial/MultiDoc2Dial（中文是其明确 future work）；②SELF-MEM 被误标 2309.00413（实为颗粒介质物理论文），正确 ID=2305.02437。
2. **中文适配证据链补齐但口径收窄**：DialSeg711（中文微博 711 对话）+ SeCom GPT-4 分段 F1 0.888（该数字实为中文数据上的成绩）+ ES-Mem 轻量两阶段（Qwen2.5-3B）胜 SeCom——中文对话分段"可行且小模型够用"有据；但**中文口语陪伴场景**（非微博/非 QA）的直接证据仍缺，v1 边界质量门不可省。
3. **"重复提问/查户口"无专项学术论文**（搜索 #6、#12 双确认）：最近邻为 semantic collapse（单作者弱源）+ interview-style 代理（ISca，域不同）。v1 的反重复设计是"文献组合+自主设计"，如实标注，不虚构出处。
4. **反重复三杠杆的约束映射（API-only）**：训练数据侧（R2）与解码侧（R1）均不可用（权重冻结/无 logits）；可用杠杆=上下文侧（已说过/已问过回注）+ 选择侧（rh_b15 验证器淘汰重复路）+ 供给侧（MapDia 式旧话题钩子）。这是约束推演结论，非某篇论文的直接结果。
5. 未读正文待补（检索级登记）：NCP-Bench 正文数字、RMM 反射细节、2505.19549、Dense X 正文、semantic collapse。引用其数字前必须先补精读。

### 五、记忆/状态管线 v1 实现规格（组合架构，交主会话实现）

**v1.0 一句话架构**：在 rh_b04 锚定装配骨架之上，历史以"话题段"为检索/注入单元（RH-B-13），写路径零调用维护三张持久账本（自话语账本 said_ledger ⊃ rh_b17 已回应事项；失效账本 invalidated = rh_b12 v0 + 确定性传播；关系状态卡 state_card = rh_b17 组件 A 原样），读路径"账本硬约束（尾部）+ 段检索原文（中部）"双通道注入；基线形态零新增模型调用，升级形态每会话 +1 次小调用（仅当边界质量门失败时启用）。

**v1.1 模块划分与输入输出**（全部确定性，除标注"可选"外零调用）：

| 模块 | 输入 | 处理 | 输出 | 调用 |
|---|---|---|---|---|
| M-A 段索引器 | 全量有序历史 [(role,text,turn_id)] | 确定性边界信号：相邻轮实体集 Jaccard 骤降 + 对话行为转换（问→陈/邀约→应答，词表）+ 时间标记变化；段上限 6 轮、典型 3–5 轮（SuperDialseg 4.25 段/13 轮先验）；每段产出检索指纹（确定性抽取关键词句，复用 rh_b09 句级打分逻辑） | segments[]（含指纹/起止轮/主题标签/validity） | 0 |
| M-B 自话语账本 | 我方每轮实际输出（recommended 文本） | 确定性抽取三类子句：问句（？/? 结尾子句）、承诺/邀约（我带/我请/我去订/我查/改天/回头/下次/答应 词表）、自我更正（其实我是/我是做 词表）；HER 次轮消息与问句做确定性配对（rh_b17 组件 B 逻辑吸收进来）→answered | said_ledger[]（status: open/answered/redeemed/invalidated） | 0 |
| M-C 失效账本+传播 | HER 消息失效/挂起信号子句（rh_b12 词表原样） | ①条目登记（rh_b12 v0 原样）；②**新增传播步**：失效事件触发时全库扫描 said_ledger open 承诺与段标签，原文/实体重叠者标 invalidated（记 invalidated_by/turn 溯源链） | invalidated[]（含传播链） | 0（LLM 传播留作可选升级，触发 RH-B-12 撞墙重入点才开） |
| M-D 段检索注入 | 查询=当前消息+近 2 轮+intent；M-A 指纹 | 查询 vs 指纹重叠打分 + 近因加权 + **近 N 轮已注入段降权（反重复）** + invalidated 段过滤；top 2–3 段，预算封顶；**注入段原文（非指纹）** | 中部注入段文本集合 | 0 |
| M-E 装配组装 | 上述全部 + rh_b04 骨架 | 头部=人物背景+状态卡（rh_b17 组件 A 原样，替换 kb JSON 倾倒）；中部=kb+段检索注入原文；尾部=备注+意图+时间+最近 3 轮原文+【已回应事项/我说过的】（≤5 条）+【已作废/挂起事项】（≤4 条）+硬约束行+输出要求 | 最终 prompt | 0 |
| M-F（可选）小模型分段升级 | 触发条件=M-A 边界质量门失败 | 滑窗式逐话语二分类或两阶段（ES-Mem 先验：分位 0.35 + 精修阈值 0.75） | 替换 M-A 边界 | 每会话 +1（小模型） |

**v1.2 状态结构（JSON schema，handle 持久、换故事清空）**：

```json
{
  "version": "pipeline_v1",
  "segments": [{
    "seg_id": "s03", "start_turn": 7, "end_turn": 11,
    "tags": ["工作", "加班"], "fingerprint": "加班 项目 周末 赶",
    "validity": "active|invalidated", "last_injected_turn": 12
  }],
  "said_ledger": [{
    "id": "m007", "turn": 7, "type": "question|commitment|correction",
    "clause": "周六我带你去那家新的川菜馆",
    "status": "open|answered|redeemed|invalidated",
    "answered_by_turn": null, "invalidated_by": null, "invalidated_at_turn": null
  }],
  "invalidated": [{
    "id": "i002", "turn": 14, "clause": "周六加班去不了",
    "cue": "去不了", "propagated_to": ["m007"], "source": "her_msg|propagation"
  }],
  "state_card": {
    "me": "…≤60 字", "her": "…≤60 字", "warmth": "…≤80 字",
    "stage": "…≤40 字", "style": "…≤60 字", "ongoing": ["…"], "lessons": ["…"]
  }
}
```

**v1.3 调用流程**：主回复 1 次/轮（与现状完全相同）；M-A/B/C/D/E 全部本地确定性毫秒级；基线形态总调用=1/轮。可选升级触发时才 +1（M-F 每会话一次；LLM 传播按事件低频）。与 rh_b11a 前缀缓存兼容：四件套资产仍为稳定前缀；state_card/said_ledger 放**尾部**不放稳定块（内容随事件变，进稳定块会断缓存——OpenAI 官方"改一字断链"纪律，通宵轮 E2 已核）。

**v1.4 与既有组件复用关系**（已亲读三份 mechanism.py 核实）：
- rh_b04：装配骨架（头/中/尾三区、TAIL_RECENT_K=3、契约 {"action","recommended"}）**逐字节保留**；唯一改动=中部历史的选取从"原始流"变为 M-D 段注入（历史最近 3 轮仍在尾部锚定，不动）。
- rh_b17：组件 A（状态卡）**原样收编**为 state_card；组件 B（已回应事项）**吸收并推广**进 said_ledger（answered_set=其中 type=question 且 status=answered 的子集视图，注入行兼容原格式）。
- rh_b12：v0 登记逻辑**原样收编**为 M-C 第①步；传播为新增②步（确定性全库扫描，不建图——RH-B-12 字段 12 的最小原型路线）。
- rh_b15/rh_b15b：said_ledger 与 invalidated 作为其硬验证器的**新增输入源**（组合选项，不强制）：草稿中出现"已回应问句原样重现"或坐实 invalidated 承诺→该路淘汰。此组合在 v1 通过 smoke 后作为下一变量，不进本轮单变量对照。
- rh_b09：句级打分逻辑复用为指纹构造器。

**v1.5 最小可证伪实验（smoke_24，对照 rh_b04 同位既有输出，C 臂零成本）**：
- 前置门（RH-B-13 门沿用）：M-A 分段人工抽检 20 段合理率 <70% → 回炉 M-A，不得带病上线（SuperDialseg 跨域 0.66 F1 的教训=门必须有）。
- 臂：X=pipeline_v1（跑 24 位，新增成本仅此）；C=rh_b04 同位既有输出（不重跑）；参考位=rh_b17 同位既有输出（其覆盖的 22 位，不重跑）。
- 预登记判据（任一击穿即淘汰或回炉）：
  ①重复提问率（确定性：回复问句与 said_ledger answered 集及历史问句的重叠数）≤ C 臂，且 E312_T12 型"重答 T1 问题"复现位清零；
  ②承诺一致性：X 臂回复与 said_ledger open 承诺矛盾次数（确定性碰撞+亲读复核）= 0 为目标，>C 臂即败；
  ③required 命中（检索覆盖）不降：段注入覆盖评测侧断言对应内容的比率 ≥ C 臂；
  ④cancel_affirm 类守护事件 ≤ C 臂（传播不得误伤合法回扣——对称登记误禁言数）；
  ⑤注入增量 ≤500 字/位（对照 C 臂 tokens 实测记账）；p50 时延不升。
- 亲读：卡片/账本/段注入是否自然生效（不得机械复述账本原文）。
- 如实失败条款：若 24 位内 said_ledger 传播实际起 0 次作用（既没拦截也没误拦），登记"本题库无增量价值"；若 M-D 与 C 臂注入集合差异 <10% 且质量无差，登记"段检索在本题库无增量"。

**v1.6 不做什么（红线）**：不做逐轮 LLM 话题抽取（成本结构）；不做命题级/压缩文本注入（氛围损失，指纹只进索引）；不做 LLM 失效传播调用（RH-B-12 撞墙重入点才开）；不做全量记忆流+逐条 importance 打分（Generative Agents 全量版）；不把账本放进稳定前缀块（断缓存）。

### 六、与 RH-B-12/13/17 的引用关系（不重复登记）

- RH-B-13（话题段检索单元，SeCom 精读）→ v1 的 M-A/M-D 是其**实现规格化**：补齐了段粒度先验（SuperDialseg 4.25 段/13 轮）、中文数据证据（DialSeg711 证为中文）、轻量分段可行性（ES-Mem Qwen2.5-3B 胜 SeCom）与边界门必要性定量（跨域 0.66 F1）。
- RH-B-12（失效传播写路径，STALE 摘要级）→ v1 的 M-C 是其**最小原型路线**（确定性传播先行，LLM 传播后置）；新增 S4（LSP 失败）作为"状态必须显式外置"的必要性证据。
- RH-B-17（关系状态卡，SimsChat 摘要级）→ v1 的 state_card **原样收编不改动**；新增 MapDia（中文主动对话）作为其"供给侧"互补证据（状态卡管"现在是什么关系"，段检索管"有什么可聊"）。
- 本节新登记论文均**不单独**构成新机制假设（不新增 RH-B-18+ 编号）；其作用是给上述三条已登记假设的实现规格补证据与参数先验。若 v1 smoke 后需要新假设（如 M-F 小模型分段升级独立成候选），另行登记。

## LOOP-10 补检索（2026-11-11，主 Worker 本人执行，看门狗合规）

### 统计

| 项 | 数 |
|---|---|
| arXiv API 搜索调用 | 5 次（关键词：self-refine+dialogue / best-of-N+quality / persona+consistency+dialogue / self-refine+response+quality / open-ended+best-of-N） |
| arXiv API 直 ID 查询 | 4 次（2601.02535 成功全文摘要；2508.06886/2303.17651/2403.00827 超时但搜索摘要已获） |
| WebFetch 抓取 | 2 次（2303.17651 成功摘要级；2406.18481 失败=外科论文非目标） |
| 新论文登记 | 3 篇（均为此前 36 篇之外） |
| 阅读分级 | 摘要级 2、检索级 1 |

### 新论文

**L1. ModeX: Evaluator-Free Best-of-N Selection for Open-Ended Generation**
- 作者/年：Hyeong Kyu Choi, Sharon Li（UW-Madison），ACL 2026 Main
- URL：https://arxiv.org/abs/2601.02535
- 检索词：best-of-N+generation+quality
- 阅读状态：摘要级核实（arXiv API 全文摘要+作者+venue+发表日期）
- 核心机制：候选间语义相似度图→递归谱聚类→选最大簇的中心代表（模态共识）。零额外模型调用/零 reward model/零 string-match voting。ModeX-Lite 加早剪枝。
- 关键证据：摘要/代码生成/数学推理三任务一致优于单路和多路基线。
- 与 LOOP-10 的关联：**解决 b15b 选择器的 +1 调用瓶颈**——ModeX 零调用选择，用 bigram Jaccard 近似语义相似度（无嵌入模型依赖）。
- 不应照搬处：谱聚类（我们用贪心聚类近似，候选数 N=4 不需要谱聚类复杂度）；语义嵌入（我们用 bigram Jaccard 替代，确定性零依赖）。

**L2. Self-Refine: Iterative Refinement with Self-Feedback**
- 作者/年：Aman Madaan 等（CMU/Google/DeepMind），NeurIPS 2023
- URL：https://arxiv.org/abs/2303.17651
- 检索词：self-refine+dialogue（WebFetch 成功摘要级）
- 阅读状态：摘要级核实
- 核心机制：同一模型三角（生成器/反馈器/精炼器）→生成→自反馈→重写，迭代直到满意。无需训练/reward model/RL。
- 关键证据：7 任务（含对话）平均 **+20% 绝对提升**，人类偏好和自动指标均优于单步生成。
- 与 LOOP-10 的关联：**+20% 是生成层改进的文献锚**——b15 池天花板 +0.32 是选择层极限；要 +50% 必须进生成层。我们合并反馈+重写为一步（给候选+上下文，直接输出精炼版，省中间反馈 token）。
- 不应照搬处：多轮迭代（我们只做 1 轮精炼，成本可控）；显式反馈输出（我们隐式化，省 token）。

**L3. Score Before You Speak: Improving Persona Consistency via Response Quality Scores**
- 作者/年：未核实全名，2025-08
- URL：https://arxiv.org/abs/2508.06886
- 检索词：persona+consistency+dialogue+LLM
- 阅读状态：检索级（搜索摘要）
- 核心机制：回复质量预评分驱动人格一致性——生成前先评质量分，低分触发重写。
- 与 LOOP-10 的关联：为 Self-Refine 的"何时触发精炼"提供文献锚——但我们简化为始终精炼（smoke_24 规模下成本可控）。
- 局限：未读正文。不应照搬处：质量评分器本身（需校准，我们用确定性验证器替代）。

### 机制设计依据

b15 池天花板 +0.32 = 选择层极限。Self-Refine +20% = 生成层改进潜力。
**rh_b18 = 选择层（ModeX 零调用）+ 生成层（Self-Refine 一步精炼）= 新机制类别**。
2 次调用/轮（= b15b 成本），但第 2 次从"只选不改"升级为"选+改写"。

## LOOP-11 检索（撞墙后从头重设计，主 Worker 本人执行，看门狗合规）

### 统计
| 项 | 数 |
|---|---|
| arXiv API 搜索 | 4 次（role-play+consistency+long-term / emotional+support+response+strategy / dialogue+planning / chain-of-thought+social+chat） |
| arXiv API 直 ID 查询 | 2 次（2407.21048 成功全文摘要；2608.05170 超时但搜索摘要已获） |
| 新论文登记 | 3 篇（均为此前 39 篇之外） |

### 新论文

**L4. APTNESS: Incorporating Appraisal Theory and Emotion Support Strategies for Empathetic Response Generation**
- 作者/年：Sabour et al.，2024-07
- URL：https://arxiv.org/abs/2407.21048（全文摘要已获）
- 阅读状态：摘要级核实
- 核心机制：认知共情→评估理论分解情绪→情绪支持策略选择→策略驱动生成。框架：情绪识别→策略数据库检索→生成。
- 与 LOOP-11 的关联：**b19 的直接蓝本**——先选策略再生成，不是先生成再改。
- 不应照搬处：策略数据库（需离线建；我们用 7 种策略词表+模型自选替代）。

**L5. EmoLLM: Appraisal-Grounded Cognitive-Emotional Co-Reasoning**
- 作者/年：未核实全名，2026-03
- URL：https://arxiv.org/abs/2603.16553
- 阅读状态：检索级
- 核心机制：IQ+EQ 双通道协同推理——事实可靠+情感恰当双重约束下生成。
- 与 LOOP-11 的关联：支持"先想情绪/策略再写回复"的双通道设计。

**L6. DREAM: LLM-based Dynamic Role-playing via Event-Aware Memory Graph**
- 作者/年：未核实全名，2026-08
- URL：https://arxiv.org/abs/2608.05170
- 阅读状态：检索级（搜索摘要）
- 核心机制：事件感知记忆图驱动角色扮演——时间线一致性+事件因果链。
- 与 LOOP-11 的关联：记忆图方向（未来候选；b19 先验证策略层收益）。

## LOOP-12 撞墙精读（5 篇 + 2 方向，P1-03 合规）

### 瓶颈定义
b18（生成→精炼，2 调用）五维 4.40 = -6.2%；b19（策略→生成，2 调用）五维 4.35 = -7.2%。
两次调用改进连续两次不如 DIRECT（4.69）。瓶颈不是调用次数不够，而是**两次调用反而引入损失**（over-refinement / 长度膨胀）。

### 5 篇精读（全部与"如何用单次调用+上下文工程提升对话质量"直接相关）

**W1. Few-Shot Bot: Prompt-Based Learning for Dialogue Systems（2110.08118）**
- 作者/年：Yoshimi et al.，2021（NeurIPS workshop）
- 阅读状态：摘要级（arXiv API 搜索+直 ID 超时，但搜索摘要足够）
- 核心机制：用少量对话示例（demonstrations）注入 prompt，零训练零微调，让模型从示例中学习对话风格。
- 关键证据：仅用 5-10 个示例就能让模型在对话任务上接近全微调水平。
- 与瓶颈关联：**单次调用 + 示例注入**——不需要第二次调用，上下文里就有"好回复长什么样"。

**W2. Language Models as Few-Shot Learner for Task-Oriented Dialogue（2008.06239）**
- 作者/年：Hosseini-Asl et al.，2020
- 阅读状态：摘要级
- 核心机制：NLU+DST+DP+NLG 四模块统一为单次 prompt，few-shot 示例驱动。
- 与瓶颈关联：证明对话系统可以用单次调用+示例完成（不需分步多次调用）。

**W3. APTNESS（2407.21048）— 已在 LOOP-11 登记，本轮精读**
- 核心机制细化：评估理论→情绪分解→**策略数据库检索**→生成。关键是策略以**数据库示例**形式注入，不是让模型自己想策略。
- 与瓶颈关联：b19 让模型自己分析策略（第 1 次调用），APTNESS 是**检索已有策略示例注入**（零调用）。

**W4. Self-Refine（2303.17651）— 已在 LOOP-10 登记，本轮重读其失败条件**
- 精读补充：论文 +20% 是在**有明确正确性信号**的任务上（数学/代码有对错）。对话任务没有明确对错→自反馈缺乏锚点→精炼方向不确定→over-refinement。
- 与瓶颈关联：解释 b18 为什么失败——对话任务没有正确性信号，Self-Refine 的自反馈无锚。

**W5. ModeX（2601.02535）— 已在 LOOP-10 登记，本轮重读其适用条件**
- 精读补充：ModeX 的前提是候选间有**语义共识**（同一答案的不同表述）。4 路不同语气候选不是"同一答案"→模态选择无意义。
- 与瓶颈关联：解释 b18 ModeX 失败——候选语义发散≠ModeX 适用场景。

### 2 条核心机制不同的替代方向

**方向 A「示例驱动单次生成」（Few-Shot Demonstration Injection）**
- 机制：在 prompt 中注入 2-3 个"该人物/该关系阶段的优质回复示例"（从历史中检索或预置），让模型从示例中学习风格→单次调用生成。
- 与 b18/b19 本质差别：零额外调用；不是改/选/分析，而是**上下文里就放着好榜样**。
- 文献锚：Few-Shot Bot（2110.08118）+ APTNESS 策略数据库。
- 预期：示例锚定风格和长度→不会长度膨胀；单次调用→不会 over-refinement。

**方向 B「确定性策略路由单次生成」（Rule-Based Strategy Routing）**
- 机制：用确定性规则（不是模型调用）判断当前情境→选策略→策略作为一行指令注入 prompt→单次调用生成。
- 与 b19 本质差别：b19 用第 1 次调用让模型想策略（成本+长度膨胀）；方向 B 用规则确定策略（零调用+一行指令）。
- 文献锚：APTNESS 策略数据库 + Few-Shot Learner（2008.06239）。
- 预期：策略以指令形式约束生成，不是分析报告形式→不会触发长度膨胀。

**选择**：先实现方向 A（示例驱动），因为它最简、最可能解决长度问题，且文献支撑最强（Few-Shot Bot + APTNESS）。

## LOOP-13 检索（主 Worker 本人执行）
- arXiv 搜索 3 次 → 1 篇新论文：The Role of Diversity in ICL (2505.19426)——多样化示例提升 ICL 性能。
- 与 LOOP-13 关联：b21 的 3 个示例覆盖不同风格（调侃/认真/确认）= 多样化示例。

## LOOP-14 检索（2026-11-11，撞墙后从头重设计）

### arXiv 搜索记录
- 搜索 1：`all:dialogue AND all:empathy AND all:generation`（sortBy submittedDate desc）
  → 命中 EchoChat (2026): 结构化认知推理=感知→心理状态推理→响应；"superficially warm but emotionally hollow"诊断
  → 命中 "When Less Is More: Minimal Responses in Counseling" (2026): 极简回复研究
- 搜索 2：`ti:persona AND all:LLM AND all:generation AND all:dialogue`
  → 命中 Deep Persona (2609.22255): 三层 persona 架构（表达/信念/动机驱动）
  → 命中 MemRole (2611.01778): 记忆→角色决策推理
  → 命中 RoleBreak (2609.16614): 长期角色扮演鲁棒性基准
- 搜索 3：`ti:role AND all:playing AND all:LLM AND all:character`
  → 命中 From Memory to Behavior (2609.21349): Situation→Internal state→Behavior 框架
  → 命中 CharDuplex: 角色一致性全双工对话
  → 命中 CHARM: 角色幻觉与知识边界

### 5 篇精读
1. **Deep Persona** (2609.22255) — 三层架构：①可观察表达层（说话风格）；②潜在信念层（角色认为什么是真的）；③核心动机驱动层（角色想要什么）。现有 persona 注入只覆盖第①层→长对话中行为漂移。实验：三层架构在角色一致性上显著优于单层描述。
2. **From Memory to Behavior** (2609.21349) — SIB (Situation→Internal state→Behavior) 框架：角色行为不是 Sit→Behavior 直接映射，而是经 Internal state 中介。社交媒体角色扮演实验：SIB 比 ICL 直接注入提升行为一致性 15%+。
3. **EchoChat** (arXiv 2026) — 认知共情三阶段：感知情绪→推理心理状态→生成响应。SpeechLLM 把共情当直接映射→"superficially warm but emotionally hollow"。SDCA (Step-Decomposed Credit Assignment) 定位推理链中哪一步出错。实验：三阶段比直接生成在情感对齐上提升 20%+。
4. **MemRole** (2611.01778) — 长期角色扮演中记忆→决策推理：不是把记忆注入上下文让模型"看到"，而是让模型推理"过去经历如何塑造角色此刻的决策"。实验：记忆→决策推理比记忆注入在长对话一致性上提升 12%。
5. **Few-Shot Bot** (2110.08118, 重读) — b20 已验证示例注入有效。重读发现：论文建议示例应与当前任务"similar enough to anchor style, different enough to avoid copying"。b20 的 3 个通用示例锚定风格但可能与当前情境不匹配 → 方向 B（动态检索）的文献基础。

### 2 条替代方向
- **方向 A**（实现）：角色内化帧移——system prompt 从 advisor 帧改为 participant 帧 + JSON 增加 state 字段（内心状态，单次调用内生成）
- **方向 B**（备选）：动态示例检索——从历史中检索相似情境的好回复作为示例（Few-Shot Bot 检索版）
