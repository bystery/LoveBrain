# LICENSE_AND_RIGHTS.md（草稿）

> 状态：**许可与权利登记草稿（DRAFT）**——第1份指导书 `01_ASSET_PRESERVATION_AND_MIGRATION.md` §10.5（L1863-1873）五份 provenance 文档之一；随晨间用户终审后定稿为 `LICENSE_AND_RIGHTS.md`。
>
> 纪律声明：
> - 全文**零真实姓名、零对话原文、零密钥形态值**。`dataset/` 下以真实姓名拼音首字母命名的 KB 目录，本文件统一以 **KB-g** 代指（别名约定同 PRIVACY_REVIEW-DRAFT.md 卷首说明；实际路径仅登记于内部明细 `dataset-privacy-review.csv`，不外发）。
> - 全程只读审查；本文件是本任务唯一新增产物；未修改任何许可文件（章程红线："不得未经核实就更改代码许可"）。
> - 所有判定均引用来源文件与章节；用户决策一律引用 `migration-decisions-20261112.md`（证据优先级：用户最新明确决定 > 指导书 > 仓库实际状态）。
> - 输入来源：[license-review.md](license-review.md)（d9）、[dataset-privacy-summary.md](dataset-privacy-summary.md)（d2）、[PRIVACY_REVIEW-DRAFT.md](PRIVACY_REVIEW-DRAFT.md)、[migration-decisions-20261112.md](migration-decisions-20261112.md)、[gold-dual-track-plan.md](gold-dual-track-plan.md)、第1份指导书 §8.4/§10.5/§11。

------------------------------------------------------------------------

## 0. 记录结构对照（§10.5 八项）

| §10.5 要求记录项 | 本文件章节 |
| --- | --- |
| LoveBrain 原有双许可的适用范围 | §1.1 |
| Arena 原创代码的权利归属 | §1.2 |
| `framework-guanjia` 的迁移边界 | §1.3 |
| 数据集的来源 | §1.4 |
| Gold 的构造来源 | §1.5 |
| 公开实验结果的授权边界 | §1.6 |
| 外部材料的引用和许可 | §1.7 |
| 不能公开的具体资产及原因 | §1.8 |

配套章节：§2 权利边界逐域落位（七域）、§3 RIGHTS_REVIEW 状态登记、§4 "许可不明确"纪律落点确认（§8.4 L1721-1727）、§5 结论与待决清单。

------------------------------------------------------------------------

## 1. 八项记录（每项按"来源 / 许可现状 / 公开许可动作 / 阻塞项"四字段）

### 1.1 LoveBrain 原有双许可的适用范围

- **来源**：`D:\LoveBrain`（公开产品仓库，HEAD e2ebde1 实测）仓库根四处许可结构：`LICENSE`（34,523 B，AGPL-3.0 官方全文，逐字未改）、`LICENSING.md`（1,344 B，双许可说明层：选项 1 AGPL-3.0 / 选项 2 商业许可，商业选项经 GitHub Issues 联系作者）、README.md §开源许可（L75-79，与 LICENSING.md 一致）、`scripts/license-policy.txt`（运行时依赖须与商业选项下二进制分发兼容，默认 deny 强 copyleft 依赖）。（license-review §1.1）
- **许可现状**：双许可（使用者任选其一）。许可历史发生过 **MIT → AGPL-3.0（双许可）** 实质变更：`616e82d` Initial public release 时 LICENSE 为 MIT（21 行，Copyright (c) 2026 LoveBrain contributors）；`af8c266` Version 1.3.0 将 LICENSE 替换为双许可说明文本；`d851581` 改回 AGPL-3.0 官方全文并拆出 LICENSING.md。**MIT 时代已发布版本对已获得的拷贝永远保持 MIT**，此历史事实不可抹除；当前 HEAD 两文件关系自洽（d851581 提交信息与文件内容互证）。（license-review §1.2）
- **公开许可动作（适用范围落位）**：
  1. 仓库级许可伞：研究资料并入 `bystery/LoveBrain` 后，根 LICENSE+LICENSING.md 名义覆盖全树。AGPL-3.0 是**软件**许可——对代码文件（arena/、tools/ 的 .py）有效；对**数据文件**（episode/gold/答卷 JSON）、文档、图片**不自动提供充分权利声明**，数据类资产在 research/ 内自带 provenance/权利声明（stories_v2 的 exposure.json 机制直接复用）。（license-review §1.3 第 1 条）
  2. AGPL-3.0 §13 网络条款（LICENSE L540）：research/ 含可独立运行的 harness 运行时，公开即受网络条款约束——这正是产品保持双许可（商业选项规避 §13）的原因。（license-review §1.3 第 2 条）
  3. 双选项再许可门槛：进入公开仓库的内容必须**同时可按 AGPL-3.0 与商业选项再许可**——只允许自有原创资产或兼容许可的第三方资产；`license-policy.txt` 的强 copyleft 默认 deny 线同样作为 research/ 引入代码的参照线。（license-review §1.3 第 3 条）
  4. 章程 §9 保护边界：research/ 不接收、不覆盖产品生产代码与产品 Prompt 资产；产品资产继续受仓库许可覆盖，但其"当前生产"身份不得被研究副本冒充。（license-review §1.3 第 5 条）
- **阻塞项**：无（现状自洽）；前提是 MIT→AGPL 变更史如实登记——本节即登记点，落盘时写入迁移说明或 CHANGELOG（license-review §七.1）。

### 1.2 Arena 原创代码的权利归属

- **来源**：`D:\lovebrain-arena` 的 `arena/`（18 个 .py）+ `tools/`（308 个 .py）+ `arena/isolation/` 包，全部项目自产。
- **许可现状**：Arena 仓库（本地与远端）**无任何 LICENSE/COPYING/NOTICE 文件**，整体处于无许可声明状态（默认版权保护）；全库 390 个 .py **0 个 SPDX 标识、0 个 Copyright 行、0 个 All rights reserved**——作者声明的存在形式是数据侧 exposure.json 而非代码版权头。权利事实上属于项目贡献者（worker 会话 + 用户），但缺乏成文许可授予。第三方排查：AST 解析 326 个顶层 .py 的 import，非标准库导入全部解析到本地模块，**零外部第三方包**；拷贝痕迹唯一命中为自有文件自拷贝（output_repair.py 复制进 9 个候选目录 _vendor），非第三方。（license-review §2.1/§2.2）
- **公开许可动作**：迁入 LoveBrain 公开仓库时，以 LoveBrain contributors 名义**补研究资产的版权与许可声明，随仓库双许可伞（AGPL-3.0 或商业选项）入仓**——属"新增声明"而非"更改既有许可"（Arena 本身无既有许可可被更改）；迁移说明如实写明"Arena 仓库原无 LICENSE"。（license-review §2.1 结论、§五.2/8）
- **阻塞项**：无实质阻塞；前提=§1.1 许可变更史如实登记；若复现确需引用 framework-guanjia 模块，见 §1.3 / R8（待决）。

### 1.3 framework-guanjia 的迁移边界

- **来源**：远端 Arena 仓库根一级目录 `framework-guanjia/`，定性为"通用 Arena/管家框架、运行时、评分器和工具"——**另一个项目的独立资产**（指导书 §8.4 L1700、§11 迁移矩阵 L1919"仅必要部分、依赖/授权核验后，不得整仓搬入"）；本地 harness 工作树未发现对其代码的 import 依赖。（license-review §2.2）
- **许可现状**："是否允许复制"**未获授权记录**（§8.4 L1700/L1712）——不可默认"都是自己项目所以没有许可问题"。
- **公开许可动作**：默认不迁移、不整仓搬入；仅当研究复现说明确需引用时，按指导书 §3.4 第 9 条把依赖关系写进复现说明，并在完成依赖清单核验 + 权利确认后按最小集迁移。
- **阻塞项**：**R8 待用户决策**（本文件 §3 登记"待决"）。关联边界：`example-guanjia/`（管家技能包，另一被测对象）不属于 LoveBrain 研究成果，**不迁移**（§11 L1923），不得混入 LoveBrain；本地 `guanjia/` 仅运行时状态（PRIVATE_OPERATIONAL），与 framework 代码无关。（license-review §2.2）

### 1.4 数据集的来源

- **来源**：`dataset/` 84 文件（约 865KB）= 5 个真实产品运行时 KB 快照（KB-g、kb_mu11oc9u_ac、kb_mu20i8p7_6d8、kb_muihgls3_3u2、kb_munm0rk6_6ys），全部为**真实聊天导出的记忆/知识库快照**，不是合成数据或构造测试场景：synthetic/sanitized/origin=/mock/fake/placeholder 标记全库 0 命中；逐字对话格式 + 产品内部消息标识（round/msgIds UUID）+ 草稿行等结构证据确凿。（dataset-privacy-summary §一）
- **许可现状**：真实聊天导出，仓库内**无任何公开授权记录**（§3.2 第 6 项无法从仓库证据判定）；不受 AGPL 自动覆盖（license-review §3.4）。隐私判定：64 文件 PRIVATE_SENSITIVE（高 33 / 低 31）/ 20 文件 PUBLIC_SANITIZED；KB-g 与 kb_mu20i8p7_6d8 为"无法安全脱敏"档（双方真实姓名级信息）。（dataset-privacy-summary §二/§五；PRIVACY_REVIEW-DRAFT §4.2）
  原数据集级 RIGHTS_REVIEW（R1）**已由用户决策三关闭**（migration-decisions-20261112.md §决策三；此前"晨间待决项 3"就此关闭）。
- **公开许可动作（决策三执行口径）**：
  1. **原件 100% 不出站**：5 个真实 KB 快照原件本地保留 + SHA 登记，正文永不出站，无悬念、不可协商。（决策三第 1 条）
  2. **公开部分 = synthetic 合成样本，注明生成方式**（AI 原生子代理学习真实 KB 结构后生成）：ZCode 原生子代理学习 5 个 KB 的结构、话题分布、对话格式，生成结构相似、内容全假、不含任何真实人物/真实聊天片段/真实健康信息的合成 KB 样本，进公开树作展示样例。（决策三第 2/5 条）
  3. 合成样本硬性要求：零真实姓名残留、零真实对话片段、零可还原特征；synthetic=true 三层标记（库级 manifest / kb.json 键 / 每文件首行注释）+ 占位符词典。（决策三第 3 条）
  4. 生成后强制隐私扫描：隔离区生成 → 扫描确认零真实信息泄露 → 才复制进公开树 research/dataset/。（决策三第 4 条）
  5. 结构摘要（目录树 + 字段模板 + 行数/字节统计）可公开，不含正文。（dataset-privacy-summary §五.4）
- **阻塞项**：权利链阻塞已解除（决策三）；剩余为执行项（合成样本生成 + 强制隐私扫描），非用户决策项。

### 1.5 Gold 的构造来源

- **来源**：GOLD_SIDECAR 由本地工具链从 stories_v2 合成题库构建，无外部数据导入步骤（license-review §3.5）。三口径（gold-dual-track-plan §0）：① v1.0 文档口径——仅存在于 GOLD_SCHEMA.md 文本，无文件实体留存；② 远端已提交快照——2026-11-08 构建，13 文件，main `387c55ac` 与 `7f0d189e` 两 ref 的 13 个 GOLD blob SHA 实测完全相等；③ 本地 v2 重建（**权威轨**）——2026-11-10，14 文件（新增 GOLD_wave5_selection.json）。
- **许可现状**：Gold 数据随 stories_v2 合成题库（315/315 exposure.json `real_user_material=false`、author 全部 AI、provenance 内建，license-review §3.1）——自产、权利链完整。wave5_selection 40 篇（E339–E378）已按用户决策二补登 custody 账本（248→288，纯追加不改既有记录，题文件零改动）。（migration-decisions §决策二）
- **公开许可动作**：按双轨方案公开（gold-dual-track-plan §1.1）：权威轨 `GOLD_SIDECAR/`（本地 v2 重建 14 文件字节纯复制）+ 快照轨 A `GOLD_SIDECAR_v1_remote_snapshot/`（远端已提交快照 13 文件，从镜像恢复，附 SNAPSHOT_README 来源 ref/blob 对照）+ 快照轨 B `_remote_v1_snapshots/`（exposure.json 远端版 80 文件平行树 + REMOTE_V1_SNAPSHOT_MANIFEST.json 93 行统一登记）+ GOLD_SCHEMA.md 原样复制（SHA 不变，注记走迁移侧）。零覆盖、分别记 SHA、写明哪份进入正式结果，不允许静默"选择看起来更新的版本"。（指导书 §3.6 纪律，经 gold-dual-track-plan）
- **阻塞项**：无权利阻塞；双轨落位与账本补登为执行项（决策二已定）。

### 1.6 公开实验结果的授权边界

- **来源**：`runs/`（远端 33.7MB / 5,400 文件 / 289 批次）+ `logs/`（2.1GB 本地原始逐调用答卷）+ `evaluation/`（98 文件）+ 判决/消融/失败分析（refs/）。（license-review §四 R2、§五.4/5）
- **许可现状**："模型生成内容可以无条件公开"不成立（§8.4 不自动证明清单）；模型服务商服务条款对输出的再分发约束**未核实**；答卷上下文可能嵌入真实用户素材。（license-review §四 R2）
- **公开许可动作**：评分聚合、账本结构、calibration 分析、判决书与协议文档可公开（自产研究记录）；原始答卷原件本地保留（logs/ 仅本地保留不入库纪律不变，license-review §六.4）；公开形态 = 结构摘要 + 评分结果聚合 + 脱敏副本；logs/ 按 §11 矩阵走**脱敏副本 Release/LFS + raw_logs/ 索引**，不上传密钥/真实隐私；isol_runs/ 审查后公开且不得漏掉授权和隔离说明（§11）。
- **阻塞项**：**R2 待用户决策**（模型服务商输出再分发条款核实 + 答卷正文脱敏审查后，方可决定公开副本形态）；R3（evaluation 嵌原文件脱敏副本）、R4（密件原件不公开）、R9（archive 本地独有件逐件审查）为审查会话推进项（§3）。

### 1.7 外部材料的引用和许可

- **来源与许可现状**（三类）：
  1. **代码域**：零第三方拷贝——非标准库导入全部为本地模块，网络能力全部经 urllib 直连本地模型网关；grep vendored/copied from/adapted from 等唯一命中为自有文件自拷贝；外部数据集导入痕迹 0（huggingface/dataset-download 0 命中）。（license-review §2.2/§3.5）
  2. **灵感引用域**：`refs/REFERENCES.md` 登记 4 处本地外部仓库路径作为"借鉴点/灵感库"——性质为指针与批注，未发现其内容被复制进 arena 代码；公开保持"引用不复制"。（license-review §2.2 第 3 层、§五.6）
  3. **论文笔记域**：`refs/research_harness_20261110/phase1/literature/` 22 篇 arxiv 论文笔记——抽样形态为带署名/年份/arXiv ID/URL 的摘要与数据引用、非全文复制，但**逐篇 verbatim 比例未系统复核**（R5）。（license-review §四 R5）
- **公开许可动作**：外部灵感来源保持指针引用；论文笔记逐篇做 verbatim 复制比例复核（阈值建议：连续逐字段落不超过引用合理范围），合格后公开、不合格篇目改写为引用+自述。
- **阻塞项**：R5（worker 审查会话推进）；**R6 待用户决策**——产品 README 引用的 `images/demo.gif`（1,582,660 B，v1.3.x 真机录制、界面已改版、无录制素材权利记录）与 `images/owerview.jpg`（1,757,732 B，架构示意图无制作记录、作者与素材来源无档案）素材权利无档案（license-review §四 R6）；R7（example-LoveBrain Kotlin 快照对应的 LoveBrain Git SHA 未核对，若属 MIT 时代则按当时许可仍为 MIT，引用须注明版本许可——指导书 §1.4"先核对是否对应某个 LoveBrain Git SHA"）。

### 1.8 不能公开的具体资产及原因

| 资产 | 范围 | 不能公开的原因 | 依据 |
| --- | --- | --- | --- |
| dataset/ 5 个 KB 快照原件 | 84 文件（64 PRIVATE_SENSITIVE） | 真实聊天导出，含真实姓名/健康信息/可识别事件+时间组合；KB-g 与 kb_mu20i8p7_6d8 无法安全脱敏；公开授权无从判定 | 用户决策三第 1 条（原件永不出站）；dataset-privacy-summary §三/§五；§11"不得上传真实个人知识库" |
| KB-g 目录名 | 目录名本身 | 目录名即真实姓名拼音首字母，名称即重识别线索，公开材料一律以别名 KB-g 代指 | PRIVACY_REVIEW-DRAFT §7.3 |
| REAL66 族 | refs/ 一层散件 3 个 + REAL66_V12_INDEPENDENT_REVIEW_20261021/ 内同名副本，共 4 处 | 逐字真实对话 + 真实 persona 身份要素（姓名/城市/职业/院校），禁止任何公开出站 | PRIVACY_REVIEW-DRAFT §4.3/§7.1 |
| LOCAL_ASSETS_MAP.md | refs/ 一层散文件 | 指向真实对话 transcript 的本地绝对路径，路径即重识别线索 | PRIVACY_REVIEW-DRAFT §4.3/§6 |
| .coordination/ 2 blob | claims.jsonl + heartbeat.jsonl | 内部心跳/协调信息；远端树已跟踪，建公开仓库时必须硬性剔除 | PRIVACY_REVIEW-DRAFT §4.4/§7.6；§11"不得公开内部心跳/协调信息" |
| SEALED_KEY ×2、USER_W_SAMPLE_BINDINGS_V1.json | evaluation/CALIBRATION_PACKET_V1(_1)/、evaluation/ | 评审纪律密件（臂身份/原文哈希/作者既判，评审者不得读取）与文件头禁分发件；公开破坏盲评方法学可信度 | PRIVACY_REVIEW-DRAFT §4.5/§7.8/§7.9；license-review §四 R4 |
| backups/ 16 件 | 本地独有内部归档 | 台账原件不公开 | PRIVACY_REVIEW-DRAFT §4.4 |
| ACL 残骸 4,321 件 | archive/20261107_root_cleanup/acl_locked_snapshots_20261021/ | 内部事故证据，PRIVATE_OPERATIONAL，零触碰零深读，不迁移不公开 | PRIVACY_REVIEW-DRAFT §4.4/§7 |
| TeamWorkspace 全部 | 私有治理仓库 | 私有治理内容不迁移（§8.3） | PRIVACY_REVIEW-DRAFT §6/§7 |
| onsite_check 2 个 token 字段 | PROCESS_SNAPSHOT.json（165KB）内 2 字段 | 过期会话 token（UUID 与 64-hex 形态），真实凭据材料——删除/打码后其余内容可公开（B 档） | PRIVACY_REVIEW-DRAFT §4.3/§7.4 |
| logs/ 2.1GB 原始答卷原件、runs/ 原始回复 | R2 涉及范围 | 模型服务商输出再分发条款未核实 + 答卷上下文可能嵌入真实用户素材——裁定前原件仅本地保留 | license-review §四 R2 |

另有两处出站注意（脱敏点，非整件禁公开）：version_closeout 文档中指向 TeamWorkspace 私有签名材料的 keystore 绝对路径（公开前脱敏为占位描述，PRIVACY_REVIEW-DRAFT §9.7）；API_COMPATIBILITY_REPORT.md / provider_check.json 的内部网关 base URL 与模型名（无 key 材料，已逐字核实，公开前脱敏 endpoint，PRIVACY_REVIEW-DRAFT §4.4）。

------------------------------------------------------------------------

## 2. 权利边界逐域落位

### 2.1 arena/ 与 tools/ 代码

- 权利定性：自产原创、零第三方依赖（§1.2）。
- 许可伞关系：代码文件受仓库 AGPL-3.0 伞约束——迁入后按双许可授予（AGPL-3.0 或商业选项），满足"同时可按双许可再许可"门槛（license-review §1.3 第 3 条）；迁移动作 = 以 LoveBrain contributors 名义补版权与许可声明，并如实登记"Arena 仓库原无 LICENSE"。
- AGPL 对代码的约束落点：§13 网络条款随 harness 运行时公开生效；`license-policy.txt` 强 copyleft 依赖默认 deny 线继续适用。
- 公开形态：`research/arena/`（§11 矩阵 arena/ 行约束：不得放进 Android app）。

### 2.2 benchmark/（stories_v2 题库、各批次数据、media）

- 权利定性：全合成、权利链完整——315/315 exposure.json `real_user_material=false`、author 全部 AI（35 种隔离生成会话的 worker 串）、source_notes 全部为"虚构人物与对话；不含真实用户素材"同义声明、外部数据集关键词 0 命中；生成工具链全本地（build_story_v2_stage1.py 本地构建五件套、build_stage2_stories.py 零模型调用装配、validate_story_v2.py 本地校验）。（license-review §3.1）
- b_line 26 文件作为 benchmark 数据集组成部分正常随迁公开：B_LINE_MANIFEST.json（b_line_v2）对每篇登记 source_path + source_sha256 + copy_sha256 双散列对账，provenance 完整回链 stories_v2 合成题库（license-review §3.2）。
- media 9 文件全部自产合成图片（纯色 PNG + 合成文字卡、虚构品牌，无真人照片、无水印、无第三方素材）。（license-review §3.3）
- 许可伞边界：故事/episode/gold 等**数据文件不被 AGPL 伞自动覆盖**——权利声明由 exposure.json provenance 内建承担（复用机制，license-review §1.3 第 1 条）；§11 矩阵约束：不得改 ID、不得合并版本。

### 2.3 dataset/

- 权利定性：真实产品运行时数据（非自产测试数据），与 stories_v2 合成题库性质完全不同，二者不得混同（license-review §3.4）。
- 公开部分 = **synthetic 合成样本（注明生成方式）+ 结构摘要**；原件本地保留不出站（用户决策三，§1.4 已详列执行口径）。
- 许可伞边界：不因进入 AGPL 仓库获得数据许可；不默认 CC BY 等数据许可——公开依据是用户决策三的合成替代授权 + synthetic 标记，不是许可推定（§4 纪律 2/3）。

### 2.4 logs/

- 权利定性：模型生成原始答卷（2.1GB 本地逐调用）——授权边界未闭环（R2 待决）。
- 公开形态：**脱敏副本 Release/LFS + raw_logs/ 索引**（§11 logs 行）；原件本地保留不入库；不上传密钥/真实隐私。
- 阻塞：R2（§3 待决）。

### 2.5 GOLD v1/v2 双轨

- 权利定性：**数据随 stories_v2**——Gold 构造来源为本地工具链对合成题库的聚合计算，权利链与 stories_v2 相同（§1.5）。
- 双轨落位：权威轨（本地 v2 重建 14 文件）+ 快照轨 A（远端已提交快照 13 文件）+ 快照轨 B（exposure.json 远端版 80 文件）；零覆盖、分别记 SHA、写明哪份进入正式结果（gold-dual-track-plan §1）。
- 许可伞边界：Gold JSON 为数据文件，AGPL 不自动覆盖——随 stories_v2 的 provenance 声明入仓；GOLD_SCHEMA.md 原样复制。

### 2.6 判决/协议文档（refs/ 与 evaluation/）

- 权利定性：**自产研究记录**（判决书 long_probe_20261030、KNOWN_FAILURES、BASELINE_PARITY、协议包、submission_review、calibration 与授权账本）——权利链完整；保留 supersede 链与原层级是方法学可信度的一部分，不合并不删 superseded 版本（license-review §五.5/6）。
- 例外件：R3 嵌真实原文件（KNOWLEDGE_ATOM_AUDIT_V1.md、C4_FOUR_ARM_REVIEW_PACK_V1）→ 制作脱敏副本后公开、原件保留；R4 SEALED_KEY ×2 + USER_W_SAMPLE_BINDINGS_V1.json → 原件不公开，仅保留"存在密件"的结构说明；refs 高危 4 处（REAL66 族）与 LOCAL_ASSETS_MAP.md 不出站（§1.8）。
- AUTH×127 授权登记件：登记结构无凭据（api_key_env 为本地占位形态），放行（PRIVACY_REVIEW-DRAFT §4.3）。

### 2.7 根文档（PAPER.md/README 等）

- 权利定性：自产原创，无外部全文复制（license-review §五.7）。
- 许可伞边界：文档不被 AGPL 自动覆盖，公开依据 = 自产事实 + 仓库许可说明；§11 矩阵约束：PAPER.md 不得把候选父子提升写成全面优于原版。
- 素材引用：README 引用的 demo.gif/owerview.jpg 素材权利无档案——**R6 待用户决策**（demo.gif 按指导书既有决定"制作合成演示后再替换公开引用"；owerview.jpg 补作者/素材确认记录后维持）。

------------------------------------------------------------------------

## 3. RIGHTS_REVIEW 状态登记（R1-R9）

状态口径沿用 Phase D：RIGHTS_REVIEW = "暂停该文件公开，保留本地并报告"。（license-review §四）

| 编号 | 资产 | 当前状态 | decision_owner | 说明 |
| --- | --- | --- | --- | --- |
| R1 | dataset/ 整目录（5 个 KB 快照，84 文件） | **已关闭（用户决策三）** | user（已裁定） | 原件永不出站 + 合成样本公开路线；migration-decisions §决策三 |
| R2 | 原始答卷正文（runs/ reply_raw、logs/ 2.1GB 原始逐调用答卷） | **待决** | user | 模型服务商输出再分发条款核实 + 答卷正文脱敏审查后，方可决定公开副本形态（license-review §四 R2） |
| R3 | evaluation/ 真实原文嵌入件（KNOWLEDGE_ATOM_AUDIT_V1.md、C4_FOUR_ARM_REVIEW_PACK_V1） | 推进项（脱敏副本路线已定） | worker 审查会话 | 制作脱敏副本后可公开；原件本地保留（license-review §四 R3） |
| R4 | SEALED_KEY ×2、USER_W_SAMPLE_BINDINGS_V1.json | 原件不公开（已定）；纪律例外存在与否待确认 | 用户确认纪律例外 | 公开时仅保留"存在密件"的结构说明（license-review §四 R4） |
| R5 | 外部论文笔记（22 篇 arxiv 阅读笔记） | 推进项 | worker 审查会话 | 逐篇 verbatim 复制比例复核，合格后公开、不合格改写为引用+自述（license-review §四 R5） |
| R6 | LoveBrain images/demo.gif 与 images/owerview.jpg | **待决** | user | demo.gif 按指导书既有决定制作合成演示替换公开引用；owerview.jpg 补作者/素材确认记录后维持（license-review §四 R6） |
| R7 | example-LoveBrain Kotlin 快照 | 推进项 | worker 审查会话 | 核对对应 LoveBrain Git SHA 与时代许可；优先 commit 引用替代复制（license-review §四 R7） |
| R8 | framework-guanjia 依赖部分 | **待决** | user | 完成依赖清单核验 + 权利确认后按最小集迁移；不得整仓搬入（license-review §四 R8） |
| R9 | archive/20261107_root_cleanup 本地独有件 | 推进项 | worker 审查会话 | 逐件/逐类审查后再定 PUBLIC_LARGE_ASSET 或 PRIVATE_OPERATIONAL；未审查前不公开（license-review §四 R9） |

澄清：本清单不含 benchmark 题库条目——stories_v2、media、Gold 权利链完整（§1.4/§1.5/§2.2），benchmark 数据集各批次（含 b_line 26 文件）按用户决策一作为普通研究资产随迁公开，与其他资产走同一套迁移 + SHA 登记 + provenance 记录（migration-decisions §决策一）。

------------------------------------------------------------------------

## 4. "许可不明确"纪律落点确认（§8.4 L1721-1727）

| §8.4 纪律原文 | 本文件落点 |
| --- | --- |
| 1. 不得自行套用 AGPL | 数据类资产（episode/gold/答卷 JSON、文档、图片）不因进入 AGPL 仓库就视为获得数据许可（license-review §1.3 第 1 条）；本文件未对任何数据资产推定 AGPL 适用；Arena 代码"迁入后按产品双许可授予"是待执行的迁移动作，本登记不视为已发生 |
| 2. 不得默认为 CC BY | dataset 公开部分依据 = 用户决策三的合成替代路线（synthetic=true 三层标记 + 强制隐私扫描），不推定任何数据许可（决策三第 2-4 条；license-review §六.2） |
| 3. 不得默认"都是自己项目所以没有许可问题" | §8.4 六项"不自动证明"逐条落位：Arena 脚本（§1.2 补声明动作）、framework-guanjia（R8 待决）、example-guanjia（不迁移）、第三方数据集（未发现导入）、模型生成内容（R2 待决）、外部论文材料（R5 复核中） |
| 4. 保留本地原件 | dataset 原件永不出站（决策三第 1 条）；logs/ 2.1GB 本地纪律不变；SEALED_KEY 密件与 USER_W_BINDINGS 本地保留；evaluation 嵌原文件原件不删（PRIVACY_REVIEW-DRAFT §6 A 档） |
| 5. 公开来源摘要和可公开部分 | dataset → 合成样本 + 结构摘要；evaluation 嵌原文件 → 脱敏副本；论文笔记 → 引用+自述改写；原始答卷 → 结构摘要与评分聚合先行（PRIVACY_REVIEW-DRAFT §6 B/C 档） |
| 6. 将该文件标记为 RIGHTS_REVIEW | §3 R1-R9 全量登记；R1 已由决策三关闭；待决类 decision_owner=user（R2、R6、R8）或 worker 审查会话（R3、R5、R7、R9）+ R4 用户确认纪律例外 |
| 7. 最终报告只列具体受阻资产，不因此隐藏整个研究项目 | 受阻资产均为点状（§1.8 负面清单 + §3 待决/推进项）；核心研究链（合成题库、Gold、评分器、候选机制、判决书、协议、论文）权利链完整可公开——不构成对整体公开路线的否决（license-review §六.7） |

------------------------------------------------------------------------

## 5. 结论

### 5.1 可公开权利链完整性判定

- **权利链完整、可直接公开**（随双许可伞 + provenance）：benchmark/stories_v2 全部题库（315/315 `real_user_material=false`）与 GOLD 双轨、benchmark 各批次数据（含 b_line 26 文件，provenance 回链 stories_v2）、benchmark/media 合成图、arena/ + tools/ 原创代码（零第三方依赖）、判决/协议/评估主体文档、根文档正文、candidates 全树。
- **权利链以"合成替代 + 结构摘要"路线闭环**：dataset/——原件隐私不可公开，公开权利链经用户决策三授权的合成样本（注明生成方式）+ 结构摘要实现。
- **授权边界未闭环、原件本地保留**：原始答卷（runs/ + logs/）R2 待决；产品图片素材 R6 待决；framework-guanjia R8 待决。
- **负面清单（不可公开）**：§1.8 所列——全部已定位、已登记处置（PRIVACY_REVIEW-DRAFT §6/§7）。
- **总判定：主体可公开、负面清单必须隔离、三项待决（R2/R6/R8）不阻塞核心研究链公开**——与 PRIVACY_REVIEW-DRAFT §11 总体判定一致，并叠加本文件许可维度：双许可再许可门槛（§1.1 第 3 条）对公开候选集全部满足（自有原创或合成替代资产；无强 copyleft 第三方依赖）。

### 5.2 待决清单

- **用户决策（decision_owner=user）**：R2（模型服务商输出再分发条款确认）、R6（demo.gif/owerview.jpg 素材确认）、R8（framework-guanjia 复制授权）——三项待决；R4 的"纪律例外是否存在"亦待用户确认（原件不公开已定）。
- **worker 审查会话推进项**：R3（evaluation 嵌原文件脱敏副本）、R5（论文笔记 verbatim 复核）、R7（example-LoveBrain 快照 SHA 核对）、R9（archive 本地独有件逐件审查）。
- **已定决策的执行项（非待决）**：dataset 合成样本生成 + 强制隐私扫描（决策三）；wave5 账本补登（决策二，248→288）；research/ 根许可说明落盘（Arena 原无 LICENSE + 双许可覆盖 + 数据 provenance 指向 exposure.json + AGPL 对数据不自动适用声明；MIT→AGPL 变更史写入迁移说明或 CHANGELOG，license-review §七.1）。

### 5.3 定稿前提

- 晨间用户终审（与 PRIVACY_REVIEW-DRAFT 同批定稿）。
- 本文件登记的许可事实未经核实不得更改（章程红线）；R2/R6/R8 在用户裁定前，相关资产一律维持"暂停公开、保留本地并报告"状态（Phase D RIGHTS_REVIEW 纪律）。
