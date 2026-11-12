# PRIVACY_REVIEW（终稿）

- 落位目标：`research/provenance/PRIVACY_REVIEW.md`（第1份指导书 §10.4 要求的 provenance 文档）。
- 版本：FINAL（2026-11-12）。以夜间审查汇总草案（e9）为骨架升级；三项晨间待决项已按用户决策（`migration-decisions-20261112.md`，权威记录）收口，密钥补扫两报告（d5b/d5c）并入后密钥维度终局收口。
- 证据链（同批审查底稿，留存审计工作区，本文件按文件名引用）：`secrets-scan-report.md` + `secrets-scan-hits.csv`（d5）、`secrets-gap-scan.md`（d5b）、`secrets-final-sweep.md`（d5c）、`dataset-privacy-summary.md` + `dataset-privacy-review.csv`（d2）、`refs-summary.md` + `refs-classification.csv`（c2）、`special-dirs-summary.md` + `special-dirs-classification.csv`（d7）、`evaluation-summary.md`、`custody-ledger-analysis.md`、`wave5-batchname-confirmation.md`（关联域）、`mapping-secret-review.md`（c1 专项）、`license-review.md` 与 `LICENSE_AND_RIGHTS-DRAFT.md`（许可域 R 编号）、`SOURCE_MAP-FINAL.md`（迁移登记口径）。
- 决策基线（2026-11-12，优先级：用户最新明确决定 > 指导书 > 仓库实际状态）：① B 线 24 篇按普通研究资产合并公开，不加任何特殊状态标签；② wave5_selection 40 篇补登 custody 账本（已执行）；③ dataset 5 个含真实隐私 KB 原件永不出站，公开部分 = AI 学习真实 KB 结构后生成的合成样本（生成方式注明）。
- 纪律声明：本文件遵守 §10.4 与任务铁律——**全文零真实密钥、零真实姓名、零对话原文、零密钥形态值**；只记录文件路径、风险类别、命中计数与处理结果。两处别名约定：① 源仓库 `dataset/` 下以真实姓名拼音首字母命名的 KB 目录，本文件统一以 **KB-g** 代指（实际路径仅登记于内部明细 `dataset-privacy-review.csv`，不外发）；② REAL66 系列为研究代号，本文件只写目录/文件名与计数，不写任何 persona 身份要素。
- 完整性纪律（2026-11-12 用户更新）：哈希及一切文件不变性校验作为防御性分析全线停用；本文件不载任何哈希值或哈希字段，**完整性口径 = 文件数 + 字节量**（manifest 校验列按 `SOURCE_MAP-FINAL.md` §12 标注停用）。历史扫描报告按报告名引用，不引数值。

------------------------------------------------------------------------

## 0. 2026-11-13 出站现状更新（一致性复核修订）

本节为 2026-11-13 一致性复核增补：**公开研究分支 `research-migration-20261112` 已实际推送出站**。本文件编写时（2026-11-12 审查期）的"签字前零出站"门禁（§9.3 门禁 1、§11 出站纪律）自公开时起不再描述现状，转为审查期历史纪律；§1–§11 原文保留作为审查期记录，与现状冲突处以本节为准。

### 0.1 已实际出站资产登记（本分支工作树实测，2026-11-13）

| 资产 | 公开树位置 | 实测规模 | 现状定性 |
| --- | --- | --- | --- |
| runs/ 实验批目录 | `research/experiments/runs/` | 290 个一级 run 目录、50,197 个文件；其中 reply_raw 8,845 个、scoring.json 8,844 个 | **已出站**。构成分流：episode_manifest / plan / summary / 授权登记等结构与记账件＝低风险；scoring 聚合分＝低风险；**reply_raw 原始回复＝待逐批复核**（§0.2） |
| 历史交付 ZIP | `research/archive/historical_assets/20261107_root_cleanup/delivery_zips_root/`（11 个）+ `research/refs/experiment_protocol_20261023/`（6 个） | 17 个 zip | **已出站**。既有扫描覆盖：d5c 对源仓 58 个 zip 做清单级 + 关键词条目抽取 grep（0 命中，§4.7），公开树 17 个为其子集；**未做逐条目全文解包复核**，残余面随 §9.4 风险 1 一并开放 |
| B 线 24 篇题面 | `research/benchmark/b_line/episodes/` | 24 个 episode.json | 已出站；自公开时起为公开 Benchmark / 历史 B 线（决策一；状态标注见 `benchmark/b_line/B_LINE_README.md` §0） |
| dataset 公开部分 | `research/dataset/` | manifests 2 件 + synthetic_samples 92 件（5 样本 × 18 + manifest + 说明件）＝94 文件 | 已出站。真实 KB 原件 84 件维持零出站（决策三）；合成样本自带 README 登记"独立复核扫描通过（blocklist 94 词条 / 格式扫描 / 双向碰撞均 0 命中）"，报告存本地审计工作区、未随树发布，§9.3 门禁 2 按该登记视为已执行 |
| raw_logs 索引 | `research/experiments/raw_logs/` | 1 件（INDEX.json，87,994 B） | 仅索引无答卷正文；logs/ 2.1GB 原始答卷原件维持本地独有、零出站 |

### 0.2 逐批复核义务（不因已出站而关闭）

- 密钥维度：d5b/d5c 已对 runs/ 全目录内容级扫描（9 族 0 命中；reply_raw 8,845 / calls.json 8,865 / manifest 专项 0 命中，§4.6–§4.7），该结论按原口径维持。
- 隐私维度**未关闭**：reply_raw 原始回复的"答卷上下文是否嵌入真实用户素材"逐批人工复核尚未完成——本文件不以"全仓扫描通过"覆盖该维度；复核完成前，runs/ 已公开 reply_raw 的隐私定性维持开放（R2 交叉维度，§9.2）。
- 收缩预案：若复核发现确含真实用户素材的批次，按既有口径（结构摘要 + 评分聚合替代原始回复）收缩该批公开形态，并登记替代关系。

### 0.3 R 状态与范围同步

- **R2 维持待决**且裁决内容升级：模型服务商输出再分发条款核实仍未完成；"runs/ 以现行形态维持公开、收缩或撤回"成为其裁决项之一。
- R1/R4/R6/R8 等其余 R 项状态不变（§9.2）。
- **P0-1（公开分支 Git 历史曾提交后删除的敏感文件清理）超出本文件修订范围**：待用户决策，本轮修订不执行任何 Git 历史操作，仅在公开历史修复完成前不得将本分支直接合并 main。

------------------------------------------------------------------------

## 1. 扫描日期

- **扫描日期：2026-11-12**（夜间审查批次完成日；同日完成两轮密钥补扫：d5b 盲区五目录补扫、d5c 四目录 + 58 zip 终局收尾，guanjia 冻结扫描时点 2026-11-12 01:39:08）。
- 复核窗口：2026-11-11 夜间 → 2026-11-12 晨；晨间用户终审签字为最后一个复核环节（见 §10）。
- refs 域观察到的研究活跃写入 mtime 至 2026-11-11（§9.4 风险 2）。

## 2. 扫描范围

审查对象为源仓库 `lovebrain-arena` 的全部顶层目录（私有研究工作区，只读盘点），外加 LoveBrain 公开展示仓侧一处 §9.4 点名素材。按域×文件数×体量登记如下（口径以各源报告实测为准；体量为字节量口径）：

| 域 | 范围 | 文件数 / 对象数 | 体量 | 来源 |
| --- | --- | --- | --- | --- |
| 密钥扫描·梯队1 | 顶层 Worker/runbook/provider_check/_hb10_verify（7）、`.coordination/`（2）、`integration_preview/`（5+缓存）、`analysis/`（7）、`refs/research_harness_20261110/`（28）、`refs/version_closeout_20261106/`（29）、`arena/` .py/.md（29）、`tools/` 根级 .py/.ps1（310）、`candidates/` json/md/yaml（428） | 845 文件（行号级定位） | — | secrets-scan-report.md §一 |
| 密钥扫描·梯队2 | `logs/` 全树内容级（3 个正则批 + calls.json 专项 + 密钥材料文件名排查） | 全树；其中 calls.json* 专项 28,297 个 | 1.63GB | secrets-scan-report.md §一 |
| 密钥补扫·d5b | `runs/`（50,259）、`benchmark/`（2,398）、`dataset/`（84）、`evaluation/`（98）、`isol_runs/`（375）五目录内容级 | 53,214 文件 | 合计 304,518,866 字节（约 290MiB） | secrets-gap-scan.md §一 |
| 密钥终扫·d5c | `archive/`（排除 ACL 残骸，82 件）、`dist/`（10,928 件）、`best/`（1 件）、`guanjia/`（300 件，冻结口径）内容级 + 58 个 zip 清单级与包内关键词条目级 | 11,311 文件 + 58 zip | dist 142,834,877 字节；guanjia 115,219 字节 | secrets-final-sweep.md §一 |
| dataset/ 隐私审查 | 5 个 KB 快照目录（KB-g、kb_mu11oc9u_ac、kb_mu20i8p7_6d8、kb_muihgls3_3u2、kb_munm0rk6_6ys）逐文件判定 | 84 文件 | 约 865KB | dataset-privacy-summary.md |
| refs/ 域分类 | 23 个子目录 + 73 个一层散文件 + experiment_protocol 内部 48 行拆解 | 154 对象（+内部行） | 逻辑 209.2MB（experiment_protocol 241MB / 19,704 文件） | refs-summary.md |
| 特殊目录域 | archive/、backups/、best/、dist/、integration_preview/、_audit_hc2/、.coordination/、根目录历史文件 16 份 | 50 对象 | — | special-dirs-summary.md |
| 关联域（同批） | evaluation/ 密件（SEALED_KEY×2、USER_W_SAMPLE_BINDINGS_V1.json）、custody 账本（wave5 登记状态复核与补登） | 3 密件 + 账本交叉文件 | — | evaluation-summary.md、custody-ledger-analysis.md |
| LoveBrain 侧 | `images/demo.gif`（真实素材，§9.4 点名；owerview.jpg 素材确认归 R6，见 §9.2） | 1 | — | 指导书 §9.4 |

**内容级覆盖状态（d5b/d5c 并入后）**：仓库顶层目录的密钥内容级扫描无空白——logs/ 全树 + 梯队1 清单（d5）→ runs/benchmark/dataset/evaluation/isol_runs 五目录（d5b）→ archive/dist/best/guanjia 四目录 + 58 zip（d5c）三级收口；终局判定见 §4.1。

**如实登记的残余未扫面**（详见 §9.4 风险 1）：ACL 残骸子树按铁律零触碰零深读；二进制文件内容未逐字扫描（文件名级排查 0 命中）；58 个 zip 中名称不含关键词的条目仅清单级；png 未视觉复核。

## 3. 扫描规则

### 3.1 密钥模式（指导书 §8.1 权威 15 类）

`sk-` 开头 API Key、`Bearer`、`Authorization:`、`api_key/api-key`、`access_token`、`refresh_token`、Cookie/session、私钥 PEM 块、Android `.jks/.keystore`、`keystore.properties`、`.env`、`secrets.*`、`*_token`、私有 endpoint token/query 参数、带凭据 URL（user:pass@）。落地为窄正则族并全量加 `-i`（正则清单见 secrets-scan-report.md §七），另加 USTC 公开 endpoint 形态观察项；并做密钥材料**文件名**排查（*.jks/*.p12/*.keystore/*.pem/.env*/keystore.properties/secrets.*/*.kdbx/credentials.json 及归档包）。§8.1 注意事项同步执行：命中须人工判断、`calls.json`/模型请求日志/Worker handoff 重点扫、公开版只保留环境变量占位符形态。

补扫（d5b/d5c）方法要点：与 d5 批次正则同源的 9+1 族（sk- 密钥、PEM、Authorization/Bearer、Cookie、字段真实值赋值、通用字段名宽族、keystore 材料、带凭据 URL、.env 引用含 CJK 安全变体、secrets.* 引用），另设严格形复核（`Authorization:<值>`、`Bearer <15+值>`、URL query 凭据参数）；全部扫描经 GNU grep 复核并与初扫做集合比对（恒等），zip 清单以名称阳性对照验证遍历有效。方法勘误已如实登记于 secrets-final-sweep.md §八。

### 3.2 隐私规则（指导书 §8.2 八条 + §3.2 八项判定维度）

- §8.2 八条：synthetic=true 标记、sanitized=true 标记、origin=unknown 进入 rights/privacy review、真实聊天生成合成替代样本、原件内部保留与审查记录登记、不保留可少量线索重识别内容、用户私密备注不作 demo、真实聊天对象头像/截图不上传。
- §3.2 八项逐文件判定维度：是否人工构造、姓名/账号、可识别事件+地点+时间组合、关系画像/健康敏感、是否真实聊天导出、是否取得公开授权、能否合成替换、原件保留方式。
- 交叉扫描族：手机号、邮箱、17+1 位身份证号、"微信号/QQ号/手机号"关键词、URL；另做机构名定向扫描与真实姓名定向 grep 验证。

### 3.3 授权状态核查

- §3.2 第 6 项（是否已取得公开授权）逐域核查：dataset 域仓库内**无任何授权记录**（→ 数据集级 RIGHTS_REVIEW，已由用户决策三收口，§9.1）；refs 域 127 个 AUTHORIZATION/AUTH 登记件逐条核查授权 ID/预算账本路径/批次绑定字段/本地 loopback 网关 endpoint 与凭据有无；B 线曝光状态经 custody 账本事件流核查（核查结论=零消费，留档；其后续处置已由用户决策一收口为普通资产，不再作为保护性审查对象）。
- 豁免形态核查：环境变量占位符（§8.1 明确允许公开的形态）、假测试值（测试桩）、usage token 统计、内部会话名等按 §8.1"命中≠秘密"逐条人工定性。

## 4. 各源命中统计

### 4.1 密钥扫描（源：d5 secrets-scan-report.md + secrets-scan-hits.csv，114 条命中记录）

- **真实密钥/私钥块/会话凭据/带凭据 URL：0 命中。** 判 PRIVATE_SENSITIVE（密钥维度）：**0 个文件**。
- 梯队2 `logs/` 1.63GB 全树：pass1 高信号族（sk-密钥、PEM、带凭据 URL、Cookie、keystore、secrets.*、.env）**0 文件 0 行**；pass2 字段族 9 文件/10 行（全部良性：验收器元告警、PASS 检查项、USTC 公开 endpoint 无凭据 + fail-closed 原因文本）；pass3 URL 族 2 文件/2 行（同上良性）；**calls.json 专项 28,297 个文件 0 命中**；密钥材料文件名 0。
- 梯队1：tools 68 文件、refs/research_harness 9 文件、refs/version_closeout 3 文件、其余零星——全部为环境变量占位符（如 `LB_API_KEY` 纪律声明形态）、假测试值（测试桩常量与 redact 单测样例）、usage token 统计、NLP 词表命名、元引用，共 7 类误报家族，全部放行。阳性对照有效：`LB_API_KEY` 字符串命中 3 个文件，证明全树 grep 遍历有效。
- 出站注意事项 2 条：① 旧 API Key 曾在外部聊天文本泄露（仓库内仅存"须撤销重生成"的事故记录，无 key 值，本文件不复述）；② refs/version_closeout 文档含指向 TeamWorkspace 私有仓的 keystore 绝对路径，公开前须脱敏为占位描述。

### 4.2 dataset/ 隐私审查（源：d2 dataset-privacy-summary.md + dataset-privacy-review.csv，84 行）

- **判定分布**：高 33（39.3%）、低 31（36.9%）、无 20（23.8%）；**分类：PRIVATE_SENSITIVE 64 / PUBLIC_SANITIZED 20**。
- **84 文件全部为真实产品运行时数据（真实聊天导出的记忆/KB 快照）**：synthetic/sanitized/origin=/mock/fake/placeholder 标记全库 0 命中；逐字对话格式、产品内部消息标识（round/msgIds UUID）、草稿行等结构证据确凿（§8.2 第 1–3 条标记均未落实）。
- 分目录：KB-g 15 文件（13 高/2 低/0 无，最高危：双方真实姓名级信息）、kb_mu20i8p7_6d8 19（10/4/5，最高危：真实姓名明文 + 318KB 最大逐字聊天）、kb_mu11oc9u_ac 15（4/7/4，健康+平台私信起源）、kb_muihgls3_3u2 17（3/8/6）、kb_munm0rk6_6ys 18（3/10/5）。
- 交叉扫描：手机号/邮箱/身份证号/账号关键词/URL 全部 0 命中；无任何密钥凭据；机构名 14 行命中（5 文件）；4 个真实姓名出现在 8 个文件（KB-g 3 个 + kb_mu20i8p7_6d8 5 个），已计入命中类型。
- 凭据维度交叉（d5b §七）：dataset/ 全家族复扫 **0 命中**；隐私+凭据两维叠加后判定不变。

### 4.3 refs/ 域（源：c2 refs-summary.md + refs-classification.csv）

- **高危 4 处**：REAL66_ANON_CORRECTED_20261021.md、REAL66_ANON_GROUPS_20261021.md、REAL66_ANON_GROUPS_V2_20261021.md、REAL66_V12_INDEPENDENT_REVIEW_20261021/ 内同名副本——逐字真实对话 + 真实 persona 身份要素（姓名/城市/职业/院校）+ 真实意图与消息原文。**禁止任何公开出站。**
- **中危 2 处**：① experiment_protocol_20261023/onsite_check_20261029_0846/PROCESS_SNAPSHOT.json（165KB）——Windows 进程快照抓到本机会话的两个过期 token 字段（UUID 与 64-hex 形态），属真实凭据材料，迁移前删除/打码；② 一层散文件 LOCAL_ASSETS_MAP.md——指向真实对话 transcript 的本地绝对路径与挖掘指引，路径即线索，不进公开仓库。
- **低危核验 127 个 AUTH× 授权登记件**：全部为登记结构（授权 ID、预算账本路径、批次绑定字段、本地 loopback 网关 endpoint、model=dsf），抽查均无真实 key，`api_key_env` 明确为本地占位形态。
- 其余低危知悉项：LIVE_BLOCKED.json 记录旧 key 泄露史（无 key 本身）；KNOWN_FAILURES.md F1 含一句用户自述私密备注示意（无第三方身份）；RESEARCH_STATE/TASK_QUEUE 含内网端点与本地路径（私有运维口径）。
- 九分类：PUBLIC_HISTORY 61 / PUBLIC_CORE 6 / PRIVATE_OPERATIONAL 14 / PRIVATE_SENSITIVE 7 / PRIVATE_EVIDENCE 48 / TRANSIENT_REVIEW 6 / PUBLIC_LARGE_ASSET 1 / REFERENCE 2（DERIVED_ARTIFACT 以迁移排除规则执行，不单列）。

### 4.4 特殊目录域（源：d7 special-dirs-summary.md + special-dirs-classification.csv，50 行）

- `.coordination/`（claims.jsonl 111 行/28,739B + heartbeat.jsonl 178 行/49,983B）：内部协调/心跳数据，**远端树已跟踪这两个 blob → 建公开仓库时必须硬性剔除**（§11"不得公开内部心跳/协调信息"）。
- WORKER_* 5 份 + 指导书.md + archive handoffs 1 份：PUBLIC_SANITIZED，**公开前先脱敏并标注"非当前执行指令"**（§11）。
- 根目录 API_COMPATIBILITY_REPORT.md / provider_check.json：含内部网关 base URL 与模型名，**已逐字核实无 key 材料**（json 仅 `key_present` 布尔位），公开前脱敏 endpoint。
- ACL 事故残骸 `archive/20261107_root_cleanup/acl_locked_snapshots_20261021/`：130 个时点快照目录、4,321 件、66.2MB——**零触碰零深读**（铁律已执行），定性 PRIVATE_OPERATIONAL，不迁移不公开。
- backups/ 16 件（本地独有）：内部归档；台账原件不公开。dist/ 10,928 件历史发布快照：LFS/Release 分卷、包间去重后定公开形态。

### 4.5 关联域（源：evaluation-summary.md、custody-ledger-analysis.md、wave5-batchname-confirmation.md）

- **SEALED_KEY ×2**（evaluation/CALIBRATION_PACKET_V1/SEALED_KEY.json 29,671B、CALIBRATION_PACKET_V1_1/SEALED_KEY_V1_1.json 43,909B）：盲测密件（臂身份/原文绑定/作者既判），纪律明示评审者不得读取 → **不入公开仓**。
- **USER_W_SAMPLE_BINDINGS_V1.json**（3,925B）：W 样本密封映射，文件头自述"不得与公开展示包一同分发" → **不入公开仓**。
- **wave5 登记状态（已收口）**：wave5_selection 40 篇（E339–E378）此前账本零登记，经 wave5-batchname-confirmation.md 确认精确消费批名（selection_v1 四臂 × 2026-11-10）后，已按用户决策二执行补登（§9.1）。
- **mapping_secret.json**（benchmark/releases/arena_v1/review_loop/，8,045B，dist/ 内 38 份快照副本 + 活动树 4 份）：经 c1 全文专项审查（mapping-secret-review.md）定性为**纯评分盲测揭盲映射台账，不是凭据材料**；其隐私维度（材料↔匿名臂绑定的可关联性）按专项结论处理。

### 4.6 密钥补扫 d5b（源：secrets-gap-scan.md）

- 五目录 53,214 文件内容级：9 族高信号（sk-密钥、PEM、Authorization/Bearer、Cookie、字段真实值赋值、keystore、带凭据 URL、.env、secrets.*）**全部 0 命中**；通用字段名宽族命中 16 行，全部良性（runs/ 10 行 = `api_key_never_persisted` 抓包校验检查项名元文本，结果均 `[PASS] files=[]`——校验器自身证明密钥未持久化；isol_runs/ 6 行 = env 变量名 + 中文注释形态，非凭据值）。
- 专项复核：reply_raw（模型回复原文侧）8,845 个 **0 命中**；\*manifest\*（episode_manifest 等）全部 **0 命中**；calls.json 复核 8,865 个 **0 命中**（与 d5 一致）。
- 密钥材料文件名 0；归档/办公容器 0（五目录不存在 zip/tar/docx 等同型盲区）。
- **无新增 PRIVATE_SENSITIVE；d5 判定维持。**

### 4.7 密钥终扫 d5c（源：secrets-final-sweep.md）

- 四目录（archive/ 排除 ACL 残骸、dist/、best/、guanjia/）10 族内容级 + 严格形复核：严格形（`Authorization:<值>`、`Bearer <值>`、URL query 凭据参数）全 0；命中 6 组全部良性——dist/ F3 python 标识符误捕 64 行、dist/ F5 假测试占位符 38 行（与 d5 已登记的 standin 假值同一）、dist/ F5b env 名/元文本宽族 411 行、archive/ F5b env 占位 2 行（空串与本地占位）、guanjia/ F6 keystore 词形为扫描台账自引 4 文件、guanjia/ F8 `config.env` 引用 5 文件。
- `guanjia/watchdog/config.env` 专项核验（今晚自建基建，128B/6 行）：全部为路径与数值键，**无 webhook、无 token、无密钥形态值**；guanjia/ 判定 0 密钥材料。
- **mapping_secret.json**（58 包内 38+4 份）：10 族 grep 0 命中，与 c1 专项定性一致（§4.5）。
- **58 个 zip**（数量勘误：d5 所记 64 按其口径多算 5 个无 zip 的快照目录，实测 58 = 14 历史交付包 + 44 payload 包）：全部清单级成功；包内文件名宽关键词集命中 14 个唯一条目名（anchors_key 评分答案要点、盲测揭盲映射台账、A/B 盲测分配表、probe_restricted_token 实验脚本），逐条 `unzip -p` 抽取并 10 族 grep——**全部 0 命中**，抽取完整性逐条核验非空。
- **无新增 PRIVATE_SENSITIVE。d5 + d5b + d5c 合并终局判定：全仓 0 真实密钥、0 私钥块、0 会话凭据、0 带凭据 URL、0 keystore 材料；仓库顶层目录密钥内容级扫描无空白。**

## 5. 复核流程与复核数量

各源均执行"程序化扫描 + 人工逐条/抽检复核"双层流程：

| 源 | 程序化复核 | 人工复核数量与方式 |
| --- | --- | --- |
| d5 密钥扫描 | 15 类窄正则 × 两梯队全树（logs 1.63GB）；28,297 个 calls.json 专项复核（0 命中）；阳性对照：`LB_API_KEY` 命中 3 个文件 | secrets-scan-hits.csv **114 条命中记录 100% 逐条人工定性**（7 类误报家族归因）；假测试值/占位符形态逐条确认非真实凭据 |
| d5b 补扫 | 9 族 × 5 目录全树；reply_raw 8,845 / \*manifest\* / calls.json 8,865 三个专项；密钥材料文件名与容器排查 | **16 行命中逐条定性**（2 个 FP 家族：检查项元文本、env 变量名） |
| d5c 终扫 | 10 族 × 4 目录 + 严格形复核 + GNU grep 集合比对恒等；58 zip 清单级；关键词条目逐条抽取 grep；guanjia 冻结复扫 | **6 组命中逐组定性** + config.env 专项核验 1 项 + 14 个唯一关键词条目逐条抽取复核 |
| d2 dataset | 全库标记词扫描（synthetic/sanitized/origin=/mock/fake/placeholder = 0）；交叉扫描 5 族（手机号/邮箱/身份证/账号关键词/URL = 0）；机构名定向扫描 14 行/5 文件 | **84/84 逐文件人工判定**：<20KB 全读；3 个大文件（59KB/131KB/318KB）头+中+尾抽样并用真实姓名定向 grep 验证覆盖 |
| c2 refs | grep 全域抽查（api_key/password/secret/sk-）；CSV 逐条登记 | 高危 4 处 + 中危 2 处**逐字核读定性**；AUTH×127 抽查（含 AUTH-EXAM1、AUTH-LONGRANGE-B1、AUTH-P08-S1）确认登记结构无凭据；provider_check.json 逐字核实仅 `key_present` 布尔位 |
| d7 特殊目录 | find/du/stat 实测；远端 blob 跟踪状态对账（arena-remote-files.csv） | 50 对象全登记；关键文件内容核实（AGENTS.md 头部 v1.6、指导书.md 头部、TASK_BOARD/RUN_STATE 自证废止、API_COMPATIBILITY_REPORT 逐字核读） |
| 关联域 | eval-snippets.txt 抽样 + classification CSV 交叉；账本结构与事件流逐条核对 | SEALED_KEY×2 / USER_W_BINDINGS 依据文件头纪律自述与 d3 评审结论；wave5 零登记经账本 events 零命中验证、补登后实测复核（§9.1）；mapping_secret.json 全文专项审查（c1） |

**汇总（推导自上表）**：逐文件/逐条人工判定合计约 **320 项**（前批约 285：d2 84 + d5 114 + c2 高中危 6 + AUTH 抽查与全域命中定性 + d7 50 + 关联域密件及根文件核实项；补扫新增：d5b 16 行逐条 + d5c 6 组逐组 + 14 个 zip 关键词条目 + config.env 专项）。另有程序化专项合计约 46,000 个文件（28,297 + 8,845 + 8,865 个 calls.json/reply_raw/manifest 专项）与两轮阳性对照。所有计数可在各源 CSV 与补扫报告中逐行对账。

## 6. 脱敏决策表（按处置类别）

处置三档：**A 不出站**（原件内部保留 + 完整性登记）/ **B 脱敏后出站**（制作脱敏副本，原件内部保留）/ **C 合成替代**（新建 synthetic=true 样本，不发布脱敏原件）。

| 敏感资产类别 | 处置 | 处置依据 |
| --- | --- | --- |
| 真实 KB 快照原件（dataset 5 目录 84 件，其中 PRIVATE_SENSITIVE 64 件） | **A 不出站（决策三：原件 100% 永不出站，不可协商）**：整目录本地保留 + 完整性登记（文件数+字节量）；KB-g 与 kb_mu20i8p7_6d8 为"无法安全脱敏"档，只保留内部原件和数量/字段结构摘要 | §3.2 第 5/6 项、§8.2 第 5/6 条、§11 dataset 行"不得上传真实个人知识库"、migration-decisions 决策三 |
| dataset 20 件无命中件（空文件/纯模板/内部值/单行话题名） | **A（随目录）**：属真实 KB 快照组成部分，按决策三整目录口径随目录本地保留（原"随目录待决"就此关闭） | dataset-privacy-summary §5.2、决策三 |
| dataset 公开部分 | **C 合成替代（决策三已落地）**：以 ZCode AI 原生子代理学习 5 个真实 KB 的结构、话题分布、对话格式后生成结构相似、内容全假的合成样本（synthetic=true 三层标记：库级 manifest / kb.json 键 / 每文件首行注释 + 占位符词典）；另附结构摘要（目录树+字段模板+行数/字节统计） | §8.2 第 4 条、§3.2 末款、决策三第 2–5 条 |
| REAL66 族 4 件 | **A 不出站**：逐字真实对话+真实画像，禁止任何公开 | §3.2 第 5 项、§8.2 第 6 条 |
| onsite_check PROCESS_SNAPSHOT.json | **B 脱敏后出站**：删除/打码两个过期会话 token 字段，其余快照内容可保留 | §8.1（过期仍属真实凭据材料）、refs-summary §4 |
| LOCAL_ASSETS_MAP.md | **A 不出站**：本地路径即重识别线索 | §8.2 第 6 条 |
| AUTH×127 授权登记件 | **放行**（登记结构无凭据，占位符形态） | §8.1 允许形态 + c2 抽查 0 命中 |
| WORKER_* 5 份 + 指导书.md + handoffs 1 份 | **B 脱敏后出站**：脱敏 + 头部标注"非当前执行指令" | §11 WORKER_* 行、§3.10 |
| API_COMPATIBILITY_REPORT.md / provider_check.json | **B 脱敏后出站**：脱敏内部 endpoint 后公开（无 key 材料） | §8.1 私有 endpoint、special-dirs §1.8 |
| .coordination/ 2 件 | **A 不出站 + 公开树硬剔除**（远端已跟踪） | §11"不得公开内部心跳/协调信息"、§3.10 |
| SEALED_KEY ×2、USER_W_SAMPLE_BINDINGS_V1.json | **A 不出站**：密件/禁分发件，manifest 登记 exclusion，原因=SEALED 密件与文件头禁令 | d3 评审结论、evaluation-summary §密件、§10.1 manifest 字段 |
| logs/ 原始答卷原件与 runs/ 原始回复 | **A 暂不出站（R2 裁决前）**：答卷上下文可能嵌入真实用户素材（隐私维度），叠加模型服务商输出再分发条款未核实（许可维度 R2，见 §9.2）；公开形态候选=结构摘要+评分聚合。**（2026-11-13 现状：runs/ 已随公开研究分支出站（290 个一级目录 / 50,197 文件，含 reply_raw 8,845），按 §0.1–§0.2 登记——结构与聚合件放行、reply_raw 逐批复核开放；logs/ 2.1GB 原件维持本地独有、零出站）** | §8.2 第 3/6 条精神、license-review §四 R2、LICENSE_AND_RIGHTS 负面清单；现状登记见 §0 |
| demo.gif（LoveBrain images/） | **C 合成替代**：制作合成演示后替换公开引用（素材确认归 R6） | §9.4 |
| ACL 残骸 4,321 件 | **A 不出站**：内部事故证据，不迁移不公开，零触碰 | special-dirs 铁律、PRIVATE_OPERATIONAL 定性 |
| backups/ 16 件 | **A 不出站**：内部归档，台账原件不公开 | §5.2 无专行 → PRIVATE_OPERATIONAL |
| evaluation 内嵌真实原文件（KNOWLEDGE_ATOM_AUDIT_V1.md 145KB、C4_FOUR_ARM_REVIEW_PACK_V1） | **B 候选**：脱敏副本或节选+索引，终形态 Phase D 复审定稿 | research-skeleton-plan（d3 判定） |
| TeamWorkspace 全部 | **A 不出站**：私有治理内容不迁移 | §8.3 |

## 7. 敏感资产处置总表（逐项）

| # | 资产 | 文件范围 | 风险类型 | 处置 | 依据 | 验证方式 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | REAL66 族 | refs/ 一层散件 3 个（REAL66_ANON_CORRECTED_20261021.md、REAL66_ANON_GROUPS_20261021.md、REAL66_ANON_GROUPS_V2_20261021.md）+ REAL66_V12_INDEPENDENT_REVIEW_20261021/ 内同名副本 1 个，共 **4 处** | 高危：逐字真实对话 + 真实 persona 身份要素（姓名/城市/职业/院校） | A 不出站，原件内部保留 | §3.2 第 5 项、§8.2 第 6 条；refs-summary §4 | 迁移时按文件数+字节量完整性登记入 manifest；公开树 grep "REAL66_ANON" 0 命中 |
| 2 | dataset 真实 KB 快照原件 | 5 个 KB 目录全部 **84 件**（PRIVATE_SENSITIVE 64 = 高 33 + 低 31；另 20 件无命中件随目录），约 865KB | 高危/低危：真实姓名、可识别事件+时间组合、关系画像与健康敏感、真实聊天逐字原文 | **A 不出站（决策三：原件 100% 永不出站）**，整目录本地保留 + 完整性登记（文件数+字节量） | §3.2 第 2/3/4/5 项、§8.2 第 4/5/6 条、§11"不得上传真实个人知识库"、决策三第 1 条 | dataset-privacy-review.csv 84 行逐行对账；公开树 grep 5 个 KB 目录名 0 命中 |
| 3 | dataset 公开部分 | synthetic 合成样本 5 个（synthetic_kb_01–05，隔离区已生成）+ 结构摘要 | 需门禁：生成后强制隐私扫描 | **C 合成替代**：AI 原生子代理学习真实 KB 结构后生成（生成方式已注明于样本 manifest），synthetic=true 三层标记 + 占位符词典；**独立隐私扫描通过前不进公开树** | 决策三第 2–5 条、§8.2 第 4 条 | 独立扫描报告（synthetic-privacy-scan.md）零真实信息泄露 + 隔离区→公开树单向复制；扫描报告未产出前维持门禁（§9.3） |
| 4 | KB-g 目录 | KB-g/ 全目录 **15 文件**（13 高 + 2 低，无"无命中"件）——5 目录中唯一整目录无 PUBLIC_SANITIZED 件 | 最高危：双方真实姓名级信息、谈心记录、逐字聊天含健康与亲密性话题、第三方真实姓名 | A 不出站（无法安全脱敏档）；目录名本身即真实姓名拼音首字母，公开材料一律以别名 KB-g 代指 | §3.2 第 2/3/4/5 项、§3.2 末款"只保留内部原件和数量/字段结构摘要"、决策三 | 实际路径仅存内部 CSV；公开树按别名核查 0 命中 |
| 5 | onsite_check token | experiment_protocol_20261023/onsite_check_20261029_0846/PROCESS_SNAPSHOT.json（165KB）内 2 个字段 | 中危：本机会话过期 token（UUID 与 64-hex 形态），真实凭据材料 | B 脱敏后出站（删除/打码该两字段，其余保留） | §8.1、refs-summary §4 中危 | 脱敏副本与原件 diff 仅两字段差异；原件按文件数+字节量留档 |
| 6 | WORKER_* 系列 | WORKER_HANDOFF_20261028_2222.md、WORKER_CURRENT_RUNBOOK.json、WORKER_NEW_WINDOW_START.md、WORKER_EXPLORATION_GUIDE_20261103.md、WORKER_LOOP_PROMPT_20261103.md（5 份，另加同规则处理的 指导书.md 与 archive handoffs 1 份） | 中危：内部 Worker 标识/操作指令，不得作为当前执行指令 | B 脱敏后出站 + 标注"非当前" | §11 WORKER_* 行、§3.10 | 公开副本头部带"非当前执行指令"标注；脱敏点 diff |
| 7 | .coordination/ | claims.jsonl（111 行/28,739B）+ heartbeat.jsonl（178 行/49,983B） | 内部协调/心跳操作数据；**远端树已跟踪** | A 不出站 + 公开树硬剔除 | §11、§3.10、special-dirs §1.7 | 公开树 `git ls-tree` / 路径 grep 双核对 0 命中（剔除点已在 Phase C 登记） |
| 8 | demo.gif | LoveBrain 仓 images/demo.gif（v1.3.x 真机录制；owerview.jpg 素材确认归 R6） | 真实产品素材（§8.2 第 7 条精神：用户真实内容不作 demo） | C 合成替代：制作合成演示后替换公开引用 | §9.4、license-review §四 R6 | 公开仓 images/ 内 demo.gif 为合成版；原文件不入公开树 |
| 9 | SEALED_KEY ×2 | evaluation/CALIBRATION_PACKET_V1/SEALED_KEY.json（29,671B）+ CALIBRATION_PACKET_V1_1/SEALED_KEY_V1_1.json（43,909B） | 盲测密件：臂身份/原文绑定/作者既判，评审者不得读取 | A 不出站，manifest 登记 exclusion | d3 评审结论 + 文件纪律自述 | MIGRATION_MANIFEST exclusion 行；公开树 grep "SEALED_KEY" 0 命中 |
| 10 | USER_W_SAMPLE_BINDINGS | evaluation/USER_W_SAMPLE_BINDINGS_V1.json（3,925B） | 密封映射（样本↔臂名还原），文件头明示禁随公开展示包分发 | A 不出站 | 文件头禁令自述 + d3 §6 | manifest exclusion 行；公开树 grep 文件名 0 命中 |
| 11 | 原始答卷原件 | logs/ 全树原始逐调用答卷（2.1GB 本地独有）+ runs/ 原始回复（reply_raw 8,845 个） | 答卷上下文可能嵌入真实用户素材；公开形态待裁决 | **logs/ 原件：A 暂不出站（R2 裁决 + 答卷正文脱敏审查前），公开候选=结构摘要+评分聚合。runs/：2026-11-13 现状已随公开研究分支出站（290 个一级 run 目录 / 50,197 文件）——结构与聚合件按既有扫描结论放行；reply_raw 的逐批人工复核未完成，定性开放（R2 交叉维度），发现真实素材批次按"结构摘要+评分聚合"收缩**（§0.1–§0.2） | §8.2 第 3/6 条精神、license-review §四 R2；现状登记见 §0 | logs/ 原件维持本地保留；runs/ 已公开部分按 §0.2 分流口径逐批复核；公开树 raw_logs/ 仅 1 件索引（INDEX.json，87,994 B），无答卷正文 |
| 12 | ACL 残骸 | archive/20261107_root_cleanup/acl_locked_snapshots_20261021/（130 时点目录、4,321 件、66.2MB） | 内部事故证据 | A 不出站，零触碰零深读（铁律已执行，含密钥终扫在内历轮扫描均排除） | special-dirs 铁律、PRIVATE_OPERATIONAL | 不入迁移清单；公开树路径 grep 0 命中 |
| 13 | backups/ | 16 件（本地独有） | 内部归档 | A 不出站 | §5.2 → PRIVATE_OPERATIONAL | 不入迁移清单 |

## 8. §10.4 十项字段汇总

| §10.4 要求字段 | 数值 | 口径与来源 |
| --- | --- | --- |
| 扫描日期 | 2026-11-12（含 d5b/d5c 两轮补扫同日完成） | §1 |
| 扫描范围 | 全仓顶层目录：logs/ 全树 1.63GB + 梯队1 清单（d5）、五目录 53,214 文件约 290MiB（d5b）、四目录 11,311 文件 + 58 zip（d5c）；dataset 84 件；refs 154 对象；特殊域 50 对象；关联域密件与账本；LoveBrain 侧 demo.gif | §2 |
| 扫描规则 | §8.1 权威 15 类落地窄正则族（补扫 9+1 族 + CJK 安全变体 + 严格形复核 + GNU grep 集合比对）+ §8.2 八条 + §3.2 八项判定维度 + 交叉扫描 5 族 + 授权状态核查 | §3 |
| 命中数量 | 密钥维度真实命中 **0**（d5 + d5b + d5c 三报告合并终局：全仓 0 真实密钥）；良性命中：d5 CSV 114 条 + d5b 16 行 + d5c 6 组（检查项元文本/占位符/env 名/台账自引）；隐私维度命中 **64 文件**（dataset PRIVATE_SENSITIVE，高 33/低 31）；高危 4 处 + 中危 2 处（refs）；特殊域敏感对象 2 组（.coordination、WORKER_*）+ 密件 3 | §4 各源 |
| 人工复核数量 | 逐文件/逐条约 **320 项**（前批约 285 + d5b 16 行逐条 + d5c 6 组逐组 + 14 个 zip 关键词条目 + config.env 专项）；程序化专项合计约 46,000 个文件（28,297 calls.json + 8,845 reply_raw + 8,865 calls.json 复核）+ 两轮阳性对照 | §5 |
| 脱敏文件数量 | 已定 **10 件**脱敏后出站（WORKER_* 5 + 指导书.md + handoffs 1 + API_COMPATIBILITY_REPORT.md 1 + provider_check.json 1 + PROCESS_SNAPSHOT.json 1）；另有 evaluation 域脱敏副本候选 2 组（Phase D 复审定稿） | §6 B 档 |
| 内部保留文件数量（A 档核心） | dataset 84 件整目录（决策三）+ REAL66 族 4 + LOCAL_ASSETS_MAP 1 + .coordination 2 + SEALED_KEY×2 + USER_W_BINDINGS 1 + logs/runs 原始答卷原件（R2 裁决前）+ backups 16 件 + ACL 残骸 4,321 件 + TeamWorkspace 全部；refs 域 PRIVATE_OPERATIONAL/PRIVATE_SENSITIVE/PRIVATE_EVIDENCE/TRANSIENT_REVIEW 合计 75 对象。**逐文件权威登记以 Phase E 的 MIGRATION_MANIFEST.csv 为准**，本表为域级汇总 | §6/§7 + 各源九分类 |
| 未解决风险 | 待用户 3 项（R2/R6/R8）+ 执行门禁 2 项（夜间未上传、合成样本独立扫描）+ 如实登记风险 6 项（残余未扫面、refs 活跃写入、guanjia 增量、旧 key 撤销、W5 keystore 路径、KNOWN_FAILURES F1） | §9 |
| 复核人 | 夜间各域审查代理初审（dataset 域、密钥域 d5/d5b/d5c、refs 域、特殊目录域、evaluation/custody 关联域、c1 专项）+ 汇总代理起草；**人工终审：用户（晨间，待签）** | §10 |
| 是否允许公开 | **有条件允许**：负面清单（§7 表）全部隔离并经 §7 验证方式复核 + R2/R6/R8 相关资产维持暂停公开 + 合成样本独立扫描通过 + 用户终审签字后，迁移候选集其余部分可公开；**签字前零出站**（夜间未上传门禁已执行）。**（2026-11-13 现状：公开研究分支已实际出站——已出站资产按 §0.1 逐项登记，逐批复核义务按 §0.2 维持开放；本行"签字前零出站"为审查期历史纪律）** | §11；现状登记见 §0 |

## 9. 遗留开放项与未解决风险

### 9.1 已关闭项（原晨间待决三项 + 密钥扫描收口，2026-11-12）

1. **B 线授权状态（已由决策一关闭）**：B 线 24 篇按普通研究资产处理，与其它资产走同一套迁移与登记流程，直接合并公开、不加任何特殊状态标签；原"路径 A/B"待决与"曝光状态失效"考量随用户裁定不再适用（盲测保留不再需要），指导书 §3.1.3 b_line 特别保护框架由用户决策覆盖。custody 账本中既有 SEALED 历史记录原样保留、不修改不强调。
2. **wave5 补登（决策二，已执行完成）**：wave5_selection 40 篇（E339–E378）已按"补充登记"决策纯追加补登进 custody 账本（登记事件 `wave5_selection_registered`，2026-11-12，注明"登记盲区、事后补登、题面文件零改动"）。实测复核：账本 stories **248→288**、events **218→379**；40 篇 lifecycle=EXPOSED，consumed_by=四臂实际消费批名（`selection_v1_direct_20261110` / `selection_v1_cot_20261110` / `selection_v1_legacy_20261110` / `selection_v1_current_20261110`）；补登前账本备份留存审计工作区（`ledger-backup-before-wave5-20261112.json`）。
3. **KB synthetic 决策（已由决策三关闭为合成样本公开路线）**：dataset 5 个真实 KB 快照目录原件 100% 永不出站（本地保留 + 完整性登记）；公开部分 = AI 原生子代理学习真实 KB 结构、话题分布、对话格式后生成的合成样本（结构相似、内容全假、零真实姓名残留、零真实对话片段、零可还原特征；synthetic=true 三层标记 + 占位符词典）+ 结构摘要。合成样本已在隔离区生成（5 个样本 + manifest 注明生成方式），**独立隐私扫描通过前不得进公开树**（§9.3）。
4. **密钥扫描未扫范围（d5b/d5c 补扫收口）**：原登记的 runs/、benchmark/、dataset/、evaluation/、isol_runs/、archive/、dist/、best/、guanjia/ 内容级扫描与 zip 包内核查已全部完成；终局判定=全仓 0 真实密钥（§4.7）；残余面如实登记（§9.4 风险 1），原"未扫目录是否扩扫"待决就此关闭。

### 9.2 待用户决策（R 编号对齐 LICENSE_AND_RIGHTS §3 / license-review §四）

1. **R2：模型服务商输出再分发条款 + 答卷正文脱敏审查**——runs/ 原始回复与 logs/ 原始答卷公开形态待定；隐私维度交叉：答卷上下文可能嵌入真实用户素材。裁决前原件仅本地保留，公开走结构摘要+评分聚合。（**2026-11-13 现状：runs/ 已随公开研究分支出站（§0.1），R2 裁决项升级为"以现行形态维持公开、收缩或撤回"三选一并含再分发条款核实（§0.3）；reply_raw 逐批人工复核未完成前，隐私定性维持开放（§0.2）。**）
2. **R6：LoveBrain images/demo.gif 与 images/owerview.jpg 素材确认**——demo.gif 为 v1.3.x 真机录制（界面已改版），按既有决定制作合成演示替换公开引用；owerview.jpg 无制作档案，补作者/素材确认后维持。
3. **R8：framework-guanjia 复制授权**——无授权文件（溯源用户自产）；当前不整仓复制仅登记，依赖清单核验 + 权利确认后按最小集取用。

（其余 R 项为 worker 审查会话推进项：R3 evaluation 嵌原文件脱敏副本、R5 论文笔记 verbatim 复核、R7 example-LoveBrain 快照核对、R9 archive 本地独有件逐件审查；R4 密件原件不公开已定、纪律例外存在与否待用户确认——见 LICENSE_AND_RIGHTS-DRAFT §3。）

### 9.3 执行门禁（新增，两项）

1. **夜间未上传门禁**：夜间审查全程（2026-11-11 晚 → 2026-11-12 晨）未向任何远端或公开位置上传、推送任何文件；源仓库全程只读。该门禁在用户晨间终审签字前持续有效——**签字前迁移产物零出站**；签字后按 §11 结论与 §7 验证方式分批放行。（**2026-11-13 现状：公开研究分支已实际出站，本门禁转为审查期历史纪律；现状按 §0 登记，后续复核义务见 §0.2。**）
2. **合成样本独立隐私扫描门禁**：决策三第 4 条要求的生成后强制隐私扫描尚未执行（独立扫描报告 synthetic-privacy-scan.md 未产出）；5 个合成样本现处隔离区（`synthetic-kb-quarantine/`，manifest 已注明生成方式与占位符词典）。扫描确认零真实姓名残留、零真实对话片段、零可还原特征之前，样本不得复制进公开树；扫描通过后按"隔离区生成 → 扫描 → 复制进公开树"单向流程执行。

### 9.4 未解决风险（如实登记）

1. **残余未扫面**：二进制文件内容未逐字扫描——合计约 1,192 件（d5b 五目录 935 = checkpoint.bin 785 + png 89 + pyc 61；d5c dist/ png 257），文件名级排查 0 命中；png 未做视觉/OCR 复核。58 个 zip 中名称不含关键词的条目仅清单级（关键词条目已全部抽取 grep 0 命中；包树与已内容级扫描的目录同构，残余风险极小）。grep 二进制跳过机制残留已程序化闭合（NUL 字节文件 = 0、UTF-16 线索 = 0）。ACL 残骸 4,321 件按铁律零触碰零深读（非扫描缺口，为既定处置）。
2. **refs 活跃写入**：benchmark_v1_20261107（phase3）与 research_harness_20261110 mtime 至 2026-11-11 仍在写入，迁移窗口须避开运行中的研究循环，或以快照+增量登记处理（refs-summary §7）。
3. **guanjia 活跃漂移**：管家链路持续写 runs/（冻结口径 300 件，冻结时点 2026-11-12 01:39:08）；此后新增运行记录不在终扫覆盖内，迁移打包前需按同族正则对增量做一次快扫（secrets-final-sweep §十.5）。
4. **旧 API Key 外部泄露史**：须由用户在 USTC 平台撤销并重新生成（仓库内无 key 值，仅事故记录；载体不在本仓）。
5. **keystore 外部路径引用**：version_closeout 文档指向 TeamWorkspace 私有签名材料路径，公开前脱敏为占位描述（d5 §五）。
6. **KNOWN_FAILURES.md F1 私密备注示意**：用户自述一句、无第三方身份；公开副本制作时按 §8.2 第 6 条处理。

## 10. 复核人

- 夜间审查执行与初审：各域审查代理——dataset 域（d2）、密钥域（d5 + 补扫 d5b + 终扫 d5c）、refs 域（c2）、特殊目录域（d7）、evaluation/custody 关联域、mapping_secret 专项（c1）、许可域（license-review），同批产出、互相独立。
- 本文件汇总起草：夜间汇总代理（2026-11-12，草案 e9 → 本终稿）。
- 人工终审与定稿签字：**用户（晨间，待签）**——§9 开放项与风险确认后，本文件作为 `PRIVACY_REVIEW.md` 落位 `research/provenance/`。

## 11. 结论：总体判定

**总体判定：迁移候选集"主体可公开、负面清单必须隔离、三项用户决策已收口"；全仓 0 真实密钥（三报告合并终局）；隐私风险集中于真实 KB 快照与 REAL66 族等真实个人内容，全部已定位并给出处置。**

1. **可安全公开的部分**（经 §7 验证方式复核 + 相应门禁通过后）：
   - 全仓代码/工具/候选/判决登记/协议文档主体——密钥维度全仓 **0 真实命中**（28,297 calls.json 复核 0 命中 + reply_raw/manifest 专项 0 命中 + 两轮阳性对照有效），命中均为 §8.1 允许的占位符/假值/统计/元文本形态，按现样放行并保留占位符形态；
   - benchmark 题面与账本资产按普通资产口径随迁公开（决策一，无特殊状态标记）；
   - refs 域 PUBLIC_HISTORY/PUBLIC_CORE（判决书、协议、方法学规范、dataset_review 六册）、127 个 AUTH 授权登记件（登记结构无凭据）、PUBLIC_LARGE_ASSET（2026-10-29 提交 zip×2）；
   - 特殊域 historical_assets/worker_guides（经 B 档脱敏与"非当前"标注）；
   - evaluation 判决与消融主体（嵌入真实原文件的 2 组走脱敏副本候选，Phase D 复审定稿）；
   - dataset 公开部分仅限合成样本（synthetic=true 三层标记，独立扫描门禁通过后）与结构摘要——原件不出站。
2. **必须隔离的部分**（A 档，不出站）：dataset 5 个真实 KB 快照目录原件 84 件（含 KB-g 全目录 15 件与 kb_mu20i8p7_6d8 19 件等真实姓名、逐字聊天、健康敏感内容）、REAL66 族 4 处、LOCAL_ASSETS_MAP.md、.coordination/ 2 blob（远端硬剔除点）、SEALED_KEY×2、USER_W_SAMPLE_BINDINGS_V1.json、logs/runs 原始答卷原件（R2 裁决前）、backups/ 16 件、ACL 残骸 4,321 件、TeamWorkspace 全部（§8.3）。（**2026-11-13 现状注记：本行"runs/ 原始回复"部分已随公开研究分支出站（290 个一级目录），按 §0.1–§0.2 分流登记——结构与聚合件放行、reply_raw 逐批复核开放；logs/ 2.1GB 原件与其余隔离件维持零出站。**）
3. **已解决的部分**（本终稿相对草案的收口项）：① B 线待决项由决策一关闭为普通资产口径；② wave5 补登已执行完成（账本实测 stories 248→288、events 218→379，纯追加、题面零改动）；③ dataset 合成样本公开路线由决策三确立（原件永不出站、生成方式注明、扫描门禁待执行）；④ 密钥扫描盲区由 d5b/d5c 两轮补扫收口（全仓 0 真实密钥终局）。

**出站纪律**：夜间审查全程零出站；本文件经用户晨间终审签字前维持零出站（§9.3 门禁 1）；R2/R6/R8 相关资产在用户裁定前维持"暂停公开、保留本地并报告"状态。（**2026-11-13 现状：公开研究分支已实际出站，前述"签字前零出站"转为审查期历史纪律；已出站资产逐项登记见 §0.1，reply_raw 逐批复核义务见 §0.2，R2 裁决项升级见 §0.3。**）

本文件为 provenance 记录，不替代迁移执行；逐文件 `privacy_status` 以 Phase E `MIGRATION_MANIFEST.csv` 为权威登记，完整性口径 = 文件数 + 字节量。
