<!-- synthetic=true, AI-generated sample, no real personal data -->

# synthetic_samples/ — 合成样本说明

本目录内容为 **synthetic 合成样本**（`synthetic_kb_01` ~ `synthetic_kb_05` 共 5 个合成 KB 快照 + `SYNTHETIC_MANIFEST.json`），**非任何真实会话数据**。生成方式：AI 原生子代理学习真实 KB 的目录结构、字段模板、话题分布与对话格式后生成，内容全部虚构（人名/昵称/城市/大学/公司均为虚构名，时间戳落在 2026-11 ~ 2027-03，round msgIds 与 32 位 hex 为种子随机生成）。三层 synthetic 标记齐全：库级 `SYNTHETIC_MANIFEST.json`（`synthetic: true`）/ 每个 `kb.json` 的 `synthetic: true` 键 / 每个 md 文件首行注释。

对应真实数据原件为 5 个真实 KB 快照目录，**100% 本地保留、不出站、不以任何形式公开**（含脱敏形式）；本目录仅作为结构展示样例公开。口径依据：迁移决策记录（2026-11-12）决策三第 4-5 条——合成样本经强制隐私扫描通过后方可进公开树；provenance 标注 synthetic 合成样本、原件本地保留不出站。

- 文件数：5 × 18 = 90 个样本文件 + 1 个 manifest
- 隐私复核：独立复核方扫描通过（blocklist 94 词条 0 命中、格式扫描 0 命中、双向碰撞 0 命中）；复核报告由本地审计工作区记录，不随公开树发布
