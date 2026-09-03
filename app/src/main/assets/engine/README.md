# assets/engine 资产说明

本目录存放全部提示词资产。资产路径的唯一出口是代码侧 `domain/AssetRegistry`，
任何资产的新增、改名、搬家都必须先改 AssetRegistry 再动文件。
知识库结构模板在 `assets/schema/` 目录（建库时由 KnowledgeRepository.loadSchema 按模板写入，一句话指引见该目录各文件头注释，本文件不展开）。

## 全局改动约束（适用于本目录所有资产）

1. **标记名/字段名/阶段名变更必须同步解析代码 + 契约单测**：
   资产里的解析标记（如 `===分析===`、`===REASON===`、`===STAGE===`）、JSON 字段名
   （如 `new_stage`、`stage_changed`）、阶段枚举名被代码正则/解析器硬引用；改这些 =
   必须同步改对应解析代码，并跑 `./gradlew testDebugUnitTest` 保证契约单测绿。
2. **阶段表述一律走 `StageCatalog`**：资产中出现的阶段名必须与
   `StageCatalog.ALL` 当前枚举一致（八阶段全带"期"），禁止引入白名单外阶段值。
3. 触发频率、上下文窗口等数值由代码侧 `AppConfig` 常量唯一决定；资产文案若提及频率，
   必须与常量当前值一致（改常量时同步检查资产文案，反之亦然）。

## 回复系 system 组件（engine/system_prompt/）

| 资产 | 用途 | 读者 | 改动约束 |
|---|---|---|---|
| `core.md` | 回复主人格与核心行为准则，回复系 system 的首段 | 大模型 | 组装顺序即缓存锚点顺序（见 AssetRegistry 注释），禁乱序；改内容跑契约单测 |
| `stage.md` | 按阶段分节的相处策略（八阶段） | 大模型 | 阶段节标题必须与 `StageCatalog` 一致；extractStageSection 按节提取 |
| `redline.md` | 红线与安全约束 | 大模型 | 涉安全边界，改动需主人口径 |
| `naturalness_check.md` | 自然度自检清单 | 大模型 | 纯文案，改动跑构建与契约单测 |
| `aggressive.md` | 激进风格附加段 | 大模型 | 纯文案，改动跑构建与契约单测 |
| `format.md` | 回复排版格式约束 | 大模型 | 纯文案，改动跑构建与契约单测 |

## 流程资产（engine/）

| 资产 | 用途 | 读者 | 改动约束 |
|---|---|---|---|
| `counseling.md` | 谈心流程全文 system（含输出裁剪规则与 `===分析===` 分析块标尺） | 大模型 | `===分析===` 标记名被 GenerationEngine.splitCounselingAnalysis 硬引用；分析块标尺变更须同步检查存档读取侧 |
| `suggest.md` | 锦囊流程全文 system（按阶段分节） | 大模型 | 阶段节与 `StageCatalog` 一致 |
| `polish.md` | 润色流程全文 system | 大模型 | 纯文案，改动跑构建与契约单测 |

## 知识引擎资产（engine/knowledge_prompt/）

| 资产 | 用途 | 读者 | 改动约束 |
|---|---|---|---|
| `lessons.md` | 经验提取引擎提示词（触发频率与上下文窗口见 AppConfig） | 大模型 | 输出以一级标题分节，编号计数侧见 KnowledgeTriggerCoordinator；改格式先核对计数逻辑 |
| `reflect.md` | 画像更新引擎提示词（输入构成与 warmth 特殊规则） | 大模型 | 输出 JSON 字段名被解析器硬引用；阶段建议经 `StageCatalog` 归一；输入行描述必须与 PromptBuilder.buildReflectUserPrompt 实际拼装一致 |
| `vector.md` | 五维向量重估引擎提示词 | 大模型 | 输出标记 `===REASON===`/`===STAGE===` 被硬引用；向量标签中文键与 KnowledgeRepository 正则一致 |
| `onboarding.md` | 建库问卷分析提示词 | 大模型 | 阶段枚举必须与 `StageCatalog.ALL` 一致 |

## 登记说明

本文件（`engine/README.md`）已在 `AssetRegistry.ENGINE_README` 登记并列入 `ALL`，
受启动自检与契约单测的存在性守卫；它是纯说明文档，无代码读取点，改文案不需要跑解析契约，
但改动本文件所在目录的资产结构时请同步更新本说明。
