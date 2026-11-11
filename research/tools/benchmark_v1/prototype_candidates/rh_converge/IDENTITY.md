# IDENTITY — RH-CONVERGE

**假设**：12 个候选全在裸装配上加生成技巧，丢了原版的 relevance gating + 场景推断 + 预算裁剪。移植原版 6 核心装配机制 + 复用 pipe_v1 三账本/段检索 + b20 示例锚定 = 一个主力候选。

**机制 DNA**：确定性零调用装配（OngoingSelector/SceneInjection/UncertainDetector/BudgetTrimmer/KnowledgeAssembler）+ pipe_v1 确定性账本（said_ledger/invalidated/段检索）+ b20 示例锚定（长度/风格控制）。1 次调用/轮 = DIRECT 成本。

**父本**：LoveBrain 原版 Kotlin 装配机制（6 文件逐字移植）+ rh_pipe_v1（3 组件复用）+ rh_b20_fewshot（示例锚定复用）。

**与 12 个候选的差别**：不再在裸装配上加生成层技巧（multicast/self-refine/strategy/fewshot-only）；把原版的确定性装配层完整移植过来，让模型看到结构化的、相关性门控的、场景感知的上下文。
