# PROVENANCE-INDEX（provenance 五件文档导航）

- 落位目标：`research/provenance/PROVENANCE-INDEX.md`。
- 定位：一页导航件——回答"五件 provenance 文档各自管什么、关键章节在哪、互相怎么引用、外部读者从哪读起"。本文件只指路，不复制正文。
- 版本：2026-11-12（对应 staging worktree 实树；各文档自身状态以其卷首版本行为准）。
- 纪律声明：路径一律仓库相对路径；完整性口径 = 文件数 + 字节量（sha256 及一切哈希校验已按用户纪律全线停用，本文件零哈希值）；不载密钥、真实聊天正文、私有治理信息；不引用内部任务代号。

---

## 五件文档一览

| 文件 | 形态/体量 | 一句话职责 |
| --- | --- | --- |
| `SOURCE_MAP.md` | 终稿，48,689 B | 迁移登记总账：资产从哪个仓库/分支来、落到 `research/` 哪里、什么不迁、数字对没对上 |
| `MIGRATION_MANIFEST.csv` | 机读台账，27,363,053 B（登记 74,121 数据行） | 每文件一行的逐件 provenance 权威登记（22+1 字段），含 privacy/license/处置/核验状态列 |
| `PRIVACY_REVIEW.md` | 终稿，42,429 B | 隐私与密钥审查终稿：扫描范围与规则、各源命中、脱敏决策、敏感资产处置、遗留风险与总体判定 |
| `LICENSE_AND_RIGHTS.md` | 草稿（待用户终审定稿），30,061 B | 许可与权利登记：双许可适用范围、权利归属、八项记录、逐域权利落位、RIGHTS_REVIEW 状态与待决清单 |
| `EVIDENCE-INDEX.md` | 改写件，13,591 B | 对外证据索引：29 项旧索引逐条改写为公开路径，五元组登记并逐条实测存在性 |

---

## 配套登记（provenance 域外，2026-11-12 增补）

以下登记对象不在 `provenance/` 目录，属各研究域的配套文档与登记事项；本索引只登记落位与一句话职责，正文以其卷首版本行为准。

| 对象 | 落位（仓库相对路径） | 一句话登记 |
| --- | --- | --- |
| `STATISTICS.md` | `research/benchmark/STATISTICS.md` | benchmark 域公开统计口径定稿：八个公开口径数字逐数字版本化（208 / 221 / 259 / 299 / 4,082 / 9,646 / 317 / 248，每数字带口径/统计时间/来源/边界），恒等式闭合（§二）、六条使用纪律（§三）、与 `research/README.md` §2.4/§2.5 口径链交叉核对一致（§四） |
| `ARCHITECTURE.md` | `research/architecture/ARCHITECTURE.md` | 正式架构总览：三层结构（产品/研究/治理）、产品生产调用链、Arena 研究架构、接线三态红线（不把计划画成已启用）；含机制研究叙事（公开口径定稿）；全部引用限本仓可核验，三图节点逐项实测（§5） |
| KNOWN_FAILURES 双落位 | 权威 = `research/refs/KNOWN_FAILURES.md`；引用 = `research/arena/KNOWN_FAILURES.md` | 已知故障回归门禁：refs 侧为唯一权威副本（与源仓原件逐字节一致；v0.2 门禁 9 失败族、活跃）；arena 侧为 harness 代码域挂的位置引用说明——门禁正文不在该侧，登记新失败族一律改 refs 侧，防双源漂移 |
| `PAPER.md` 迁移译注块 | `research/PAPER.md` 卷首 | 正文为源件原样迁移（零改动）；卷首并列口径译注十条——三条版本标签（数据集总量为 2026-11-03 口径、已被 v2 重建取代，现行权威见 `benchmark/STATISTICS.md`；B 线叙述为 2026-11-03 时点事实记录，现行安排以用户决策后口径为准；W22 判决数字以 REV2 判决为口径）+ 两条实质差异注记 + 五条简要注记；引用 PAPER 数字前先读译注 |

**REAL66 族剔除登记（2026-11-12 处置闭环）**：REAL66 族（逐字真实对话 + 真实 persona 身份要素）已从公开树全部剔除——剔除量按处置口径为 16 路径 + 3 交付 zip（第一批次 = refs 侧 15 文本件 + v12_real66.zip 共 16 路径；第二批次 = v13_wave15.zip + v14_offline_ready.zip；另 W14 判决记录 ARCHIVED 件曾按判决链需要同 blob 复位、复核纠错后补剔出树，净计 18 路径）。`MIGRATION_MANIFEST.csv` 相应 18 行改判 `PRIVATE_SENSITIVE` / `EXCLUDED_PRIVATE`（行数不变）；原件在 arena 私有研究区安全保留、零删除。注意：HEAD 可达历史中上述路径仍可恢复，公开出站方式为推送前用户决策项（登记在案）；处置独立复核详见审计工作区 `real66-remediation-verify.md` / `real66-reverify.md`。

---

## 逐件详述

### 1. SOURCE_MAP.md（终稿）

- **职责**：迁移事实与登记口径的唯一总账。回答"资产从哪来（源仓库/分支/commit）、怎么映射落位、哪些明确不迁移、源远端差异怎么收敛、总量数字是否闭合"。
- **关键章节指针**：
  - §1 来源仓库与分支 / §2 来源 commit SHA——来源基准（`bystery/arena` → `bystery/LoveBrain`，落位分支 `research-migration-20261112`；TeamWorkspace 只读零迁入）
  - §3 主要目录映射总表（A–L 十二域逐域登记：源路径 → 目标路径 → 对账状态 → 处置）
  - §9 排除清单与排除原因（四元组登记规范 + 排除登记表，evidence_ref 回链审计底稿）
  - §10 远端与本地差异的最终解释（十态对账总账、D-1~D-7 逐项收敛、93 个实质差异文件处置）
  - §11 数据集版本与候选谱系入口（GOLD 三代口径、wave5 账本补登、`research/candidates/VERSION_LINEAGE_V2.json`）
  - §12 如何复核某个文件（四步复核法）+ **MIGRATION_MANIFEST.csv 22+1 字段定义**（manifest 字段口径的唯一权威定义点）
  - §13 数字对账表（源侧实测 → 目标侧实测 → 差额归因；全树 74,040 文件 / 527.62 MiB，全量字节核验零不符）
  - §14 未决事项清单（权利类 R2/R6/R8、执行类 P1–P11、smoke test T1–T11）
  - 附：验收对照速查（常见验收问题 → 对应章节）
- **引用关系**：卷首"证据链"列举同目录配套件（`MIGRATION_MANIFEST.csv`、`PRIVACY_REVIEW.md`、`LICENSE_AND_RIGHTS.md`、交付完整性总账、Gold 快照两侧说明件）；排除件 evidence_ref 回链审计工作区底稿；§14.1 权利类三项与 LICENSE_AND_RIGHTS §3 同表登记。

### 2. MIGRATION_MANIFEST.csv

- **职责**：逐件权威台账——每一迁入/排除/登记文件一行，覆盖出处、落位、分类、隐私与许可状态、处置方式与核验结论；是行级复核的数据底座，也是"交付完整性总账"的载体之一（完整性口径 = 文件数 + 字节量，SHA256SUMS.txt 已停用）。
- **字段速览**（完整定义见 SOURCE_MAP §12）：`asset_id` / `source_repo` / `source_branch` / `source_commit` / `source_path` / `source_size_bytes` / `source_git_status` / `target_repo` / `target_branch` / `target_path`（不出站件填 `INTERNAL_ONLY`）/ `target_size_bytes` / `classification` / `privacy_status` / `license_status` / `exposure_status`（GIT / LFS / RELEASE / INDEX_ONLY / INTERNAL_ONLY）/ `version_identity` / `duplicate_of` / `transformation`（none / sanitized-copy / structure-summary / synthetic-replacement / volumed-release / renamed-historical）/ `verification_status` / `notes` / `transformation_note`。两列 `source_sha256` / `target_sha256` 保留列位但已停用、不填值。
- **引用关系**：
  - 字段口径回指 SOURCE_MAP §12（唯一权威定义）；
  - `privacy_status` 枚举（PUBLIC / SANITIZED / PRIVATE_SENSITIVE / SEALED / RIGHTS_REVIEW / INTERNAL_COORDINATION）是 PRIVACY_REVIEW 脱敏决策的逐件落地；
  - `license_status` 枚举（original-owned / needs-review / third-party-restricted）对齐 LICENSE_AND_RIGHTS 的判定结果。

### 3. PRIVACY_REVIEW.md（终稿）

- **职责**：隐私与密钥维度终局审查——全仓密钥三级扫描收口、dataset 真实 KB 快照与真实个人内容处置、脱敏决策表、敏感资产逐项处置、遗留门禁与风险的如实登记。
- **关键章节指针**：
  - §2 扫描范围（域 × 文件数 × 体量总表）与内容级覆盖状态
  - §3 扫描规则（密钥 15 类模式、隐私八条 + 八项逐文件判定维度、授权状态核查）
  - §4 各源命中统计（密钥三级扫描及其收口判定；dataset 逐文件判定；refs 域、特殊目录域、关联域；终局判定 = 全仓 0 真实密钥）
  - §6 脱敏决策表（按处置类别）与 §7 敏感资产处置总表（逐项）
  - §8 十项字段汇总（与 manifest 各列逐项对应）
  - §9 遗留开放项与未解决风险（已关闭项；待用户决策三项 R2/R6/R8；两项执行门禁；未解决风险 6 项如实登记）
  - §11 结论：总体判定（主体可公开、负面清单必须隔离、三项用户决策已收口）
- **引用关系**：
  - 按文件名引用审计工作区扫描底稿：`dataset-privacy-summary.md` + `dataset-privacy-review.csv`（dataset 隐私逐文件判定）、`secrets-scan-report.md` + `secrets-scan-hits.csv` / `secrets-gap-scan.md` / `secrets-final-sweep.md`（密钥三级扫描）、`refs-summary.md` + `refs-classification.csv`、`special-dirs-summary.md` + `special-dirs-classification.csv`、`evaluation-summary.md`、`custody-ledger-analysis.md`、`mapping-secret-review.md`；
  - R 编号对齐 LICENSE_AND_RIGHTS §3；sha 停用口径按 SOURCE_MAP §12；
  - dataset 公开侧路线 = AI 学习真实 KB 结构后生成的合成样本（synthetic 标记 + 生成方式注明；门禁文本见 §9.3，扫描收口与 92 件落位 `research/dataset/synthetic_samples/` 登记于 SOURCE_MAP §14.2 P7）。

### 4. LICENSE_AND_RIGHTS.md（草稿）

- **职责**：许可与权利维度登记——LoveBrain 双许可（AGPL-3.0 / 商业选项）对 research/ 的适用范围、Arena 原创代码权利归属、数据集与 Gold 的来源与权利链、不能公开资产及原因、RIGHTS_REVIEW（R1–R9）状态与待决清单。**注意：文件头状态为草稿，定稿前提 = 晨间用户终审（§5.3）。**
- **关键章节指针**：
  - §0 记录结构对照（指导书八项 ↔ 本文件章节映射）
  - §1 八项记录（每项按"来源 / 许可现状 / 公开许可动作 / 阻塞项"四字段）：1.1 双许可适用范围（含 MIT→AGPL 变更史登记义务）、1.2 Arena 原创代码（零第三方依赖、原无 LICENSE，入仓补声明）、1.3 framework-guanjia 迁移边界、1.4 数据集来源、1.5 Gold 构造来源、1.6 公开实验结果授权边界、1.7 外部材料引用、**1.8 不能公开的具体资产及原因**
  - §2 权利边界逐域落位（七域）
  - §3 RIGHTS_REVIEW 状态登记（R1–R9：R2 模型输出再分发条款、R6 产品图片素材、R8 framework-guanjia 授权为待用户决策）
  - §5 结论（可公开权利链完整性判定、待决清单、定稿前提）
- **引用关系**：卷首输入来源列举 `license-review.md`、`dataset-privacy-summary.md`、`PRIVACY_REVIEW-DRAFT.md`、`migration-decisions-20261112.md`（用户决策权威记录）、`gold-dual-track-plan.md`；KB-g 目录别名约定与 PRIVACY_REVIEW 同源；§1.8 不可公开表引 PRIVACY_REVIEW §6/§7 处置；R 编号与 PRIVACY_REVIEW §9.2、SOURCE_MAP §14.1 三处对齐。

### 5. EVIDENCE-INDEX.md

- **职责**：对外证据索引改写件——把旧内部证据索引 29 项逐条改写为公开版路径，每条登记【原索引项 → 旧路径 → 新路径 → 存在性核验 → 备注】五元组；引用某个研究资产时应给的公开路径以本件为准。
- **关键章节指针**：
  - §A LoveBrain 产品仓五项（产品仓相对路径，GitHub URL 不变）
  - §B 收口分支 champion 五项（closeout 分支限定至合并）
  - §C Arena（私有）→ `research/` 落位十二项（含 C7 根级"当前入口"不迁移、以 6 处脱敏历史副本代偿的如实登记）
  - §D TeamWorkspace 七项（不迁移结论性登记，私有治理不入公开树）
  - §E 附：建议新增条目（判决原文件目录、`research/refs/KNOWN_FAILURES.md`、批次级 runs 索引、evaluation 版本链、评分器两代并存）
  - §F 29 项存在性核验统计与归因（22 项可核验项全部实测在、0 真实缺项；唯一"缺"为设计使然；1 项"待"= `research/experiments/` 下 runs-registry.csv 尚未落位，工作副本留存审计工作区）
- **引用关系**：§C 落位路径映射依据 = SOURCE_MAP §3 各行（C7 归因引 §3.I/§9、C2 引用纪律引 §3.K、C6 引 §3.C、§E evaluation 版本链引 §3.D）；§D 是 SOURCE_MAP §9.2 排除四元组（OUT_OF_SCOPE，十类禁入验证）的对外改写；§B/§A 存在性在 staging worktree 实树逐条 stat 核验。

---

## 相互引用关系总图

```
SOURCE_MAP.md（总账枢纽）
  ├─ 卷首证据链列举 → MIGRATION_MANIFEST.csv / PRIVACY_REVIEW.md / LICENSE_AND_RIGHTS.md / 交付完整性总账
  ├─ §12 字段定义与四步复核法 → MIGRATION_MANIFEST.csv 的唯一口径来源
  ├─ §9 排除四元组（evidence_ref）→ 审计工作区底稿
  └─ §14.1 待决三项（R2/R6/R8）⇔ LICENSE_AND_RIGHTS §3 ⇔ PRIVACY_REVIEW §9.2（三处同表对齐）

MIGRATION_MANIFEST.csv（逐件权威登记）
  ├─ 字段口径 ← SOURCE_MAP §12
  ├─ privacy_status 枚举 ← PRIVACY_REVIEW 脱敏决策落地
  └─ license_status 枚举 ← LICENSE_AND_RIGHTS 判定落地

PRIVACY_REVIEW.md
  ├─ 按文件名引用扫描底稿（dataset-privacy-summary/review.csv、secrets 三级扫描报告、refs/special-dirs 分类件等）
  ├─ sha 停用口径 ← SOURCE_MAP §12
  ├─ R 编号 → LICENSE_AND_RIGHTS §3
  └─ 合成样本门禁（§9.3）→ 收口登记见 SOURCE_MAP §14.2 P7

LICENSE_AND_RIGHTS.md
  ├─ 输入来源：license-review.md、dataset-privacy-summary.md、PRIVACY_REVIEW-DRAFT、migration-decisions-20261112.md、gold-dual-track-plan.md
  ├─ KB-g 别名约定 ⇔ PRIVACY_REVIEW（同源约定）
  └─ §1.8 不可公开表 → PRIVACY_REVIEW §6/§7

EVIDENCE-INDEX.md（对外引用出口）
  ├─ 落位路径映射依据 ← SOURCE_MAP §3 各行（含 §3.C/§3.D/§3.I/§3.K/§9）
  ├─ §D 不迁移登记 ← SOURCE_MAP §9.2 四元组
  └─ 存在性核验 = staging worktree 实树逐条 stat（§F 统计）
```

---

## 建议阅读顺序

**SOURCE_MAP → STATISTICS → MIGRATION_MANIFEST → PRIVACY_REVIEW → LICENSE_AND_RIGHTS → EVIDENCE-INDEX → ARCHITECTURE**

1. **SOURCE_MAP**：先建全景——从哪来（§1/§2）、怎么放（§3）、什么没来（§9）、数字是否闭合（§13）。
2. **MIGRATION_MANIFEST**：落到行级——逐件出处、状态与处置；读法先看 SOURCE_MAP §12 字段定义，避免误读已停用的 sha 列。
3. **PRIVACY_REVIEW**：隐私维度——为什么有些件被脱敏/排除/不出站，负面清单在哪，风险与门禁剩什么。
4. **LICENSE_AND_RIGHTS**：权利维度——公开的许可依据、权利链完整性判定、哪些权利事项仍待用户裁定。
5. **EVIDENCE-INDEX**：对外引用——最终"引用某资产应写哪个路径"的公开出口，以及 29 项实测核验结论。

前三件回答"事实是什么"，第四件回答"凭什么可以公开"，第五件回答"对外怎么写路径"。

2026-11-12 增补两步（域文档插读）：**STATISTICS** 在 SOURCE_MAP 之后读——动用任何 benchmark 数字前先立口径尺（八数字版本化，不同版本/分母/统计单位的数字不相加不互改）；**ARCHITECTURE** 在五件叙事收尾之后读——架构总览含机制研究叙事，是研究面全景的域级收口。

---

## 外部读者入口（陌生人三问）

> 单一入口建议：**从 `research/provenance/SOURCE_MAP.md` 读起**。只查单一问题时，按下列指针直达。

**问一：这些研究资产从哪来？**
→ SOURCE_MAP §1（来源仓库与分支：私有研究工作区 `bystery/arena` → 公开展示仓 `bystery/LoveBrain`，私有治理仓全程只读零迁入）、§2（来源 commit 基准）、§3（目录映射总表，源路径 ↔ `research/` 落位路径逐域对照）。逐件出处查 `MIGRATION_MANIFEST.csv` 的 source_* 列；对外写引用路径用 EVIDENCE-INDEX。

**问二：这些资产是否可信？**
→ SOURCE_MAP §12（四步复核法：行级字节量核对 → 域级状态核对 → 排除级 evidence_ref 回溯 → 完整性总账）与 §13（数字对账表：源侧/目标侧实测与差额归因，全量字节核验零不符）；逐件核验结论看 `MIGRATION_MANIFEST.csv` 的 `verification_status` 列；独立抽验结论见 EVIDENCE-INDEX §F（22 项可核验项全部实测存在）。完整性口径为文件数 + 字节量（哈希校验已停用）。

**问三：什么不能公开？**
→ PRIVACY_REVIEW §7（敏感资产处置总表）与 §8（十项字段汇总）；许可侧不能公开及原因见 LICENSE_AND_RIGHTS §1.8；排除与不出站的登记口径见 SOURCE_MAP §9 与 manifest 中 `target_path=INTERNAL_ONLY` 行；尚未收口的权利事项（R2/R6/R8 相关资产）在裁定前维持暂停公开。TeamWorkspace 私有治理内容全部不迁移（EVIDENCE-INDEX §D 结论性登记）。

---

## 附：两处状态备注

1. 各文档版本状态以其卷首为准：SOURCE_MAP、PRIVACY_REVIEW 为终稿；LICENSE_AND_RIGHTS 仍为草稿（定稿前提 = 用户终审）；EVIDENCE-INDEX 为改写件。本索引只登记落位与结构，不代替各文档的版本行。
2. 扫描与对账底稿留存审计工作区，本目录五件按文件名引用其结论；`MIGRATION_MANIFEST.csv` 与 tree-audit 共同构成"交付完整性总账"载体（SOURCE_MAP §14.3 T10）。
