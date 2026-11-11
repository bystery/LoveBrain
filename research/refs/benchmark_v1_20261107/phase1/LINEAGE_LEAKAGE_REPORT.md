# LINEAGE_LEAKAGE_REPORT.md — 谱系隔离审计（Benchmark V1 Phase 1 / 工作包二）

- 审计对象：库 208 集 + 库外 wave1_ext 2 篇中的全部"同内容衍生"关系（长剧原位续写、CF 反事实孪生、B 线副本、stage 内重写排查）。
- 审计日期：2026-11-07。
- 方法：`tools/benchmark_v1/inventory.py` 实测（manifest/保管账/GOLD sidecar 元数据 + 文件 sha256）；另做一次只读抽查——**LRE117 前 12 轮 input（messages/clock/may_decline/note/intent）与 E117 逐字段相等 = True（12/12）**，case_id 前缀 `E117_T*`→`LRE117_T*`，总轮数 40。
- 边界纪律：`benchmark/b_line/` 只读 `B_LINE_MANIFEST.json` 元数据（source_path/source_sha256/copy_sha256/stage）与 README；**episode 正文零读取**。B 线副本的"逐字节复制"以 manifest 声明 + 本脚本对**库侧源文件** sha256 的实算对账为证（24/24 匹配；封存副本侧未重算，属授权边界，见 §5）。

---

## 1. 谱系关系总类（26 组，50 篇库内成员 + 2 篇库外成员）

| 类别 | 组数 | 机制 | 证据 |
|---|---|---|---|
| 长剧原位续写（wave3_ext） | 16 | LRExxx = Exxx 的原位续写：T1-T12 逐字保留、case_id/atom_id 改名，扩为 32/40 轮 | 各 `MANIFEST_LRE*.json`（stories[0].source）；工艺声明 `W03-06_WAVE3_SKELETON_PLAN_20261102.md` §2；账本事件 `lr2_material_seeded`/`wave3_ext_g2_seeded`（"T1-T12 逐字继承由生成代理程序化深度相等校验"）；本脚本抽样复算 LRE117↔E117 12/12 逐字相等 |
| 长剧原位续写（wave1_ext，库外） | 2 | LRE048/E048、LRE060/E060 同上 | `wave1_ext/MANIFEST_LRE048.json`、`MANIFEST_LRE060.json`（标题即注明"E048 原位续写 32 轮扩展版"）；账本 `lr2_material_*` 事件 |
| CF 反事实孪生 | 8 | E149-E156 = 基线篇 + 单变量扰动（time/subject/negation/status/goal） | `wave3/STAGE1_MANIFEST_CF.json`：E149←E077(time)、E150←E105(time)、E151←E086(subject)、E152←E072(negation)、E153←E084(negation)、E154←E094(status)、E155←E090(status)、E156←E087(goal) |
| B 线副本 | 24 篇（跨上述组与独立篇） | `b_line/episodes/<id>/episode.json` 为库内源文件逐字节复制 | B_LINE_MANIFEST v2（count=24：标准 16 + 长剧 4 + CF 4）；source_sha256 与库文件实算 sha256 **24/24 全对上**（脚本自检） |
| stage 内重写（stage4 ↔ E021-E044） | **0（未发现）** | 排查结果：stage4 为 E061-E068，与 stage2/3 的 E021-E044 ID 空间不重叠；stage2/3/4 全部 audit.md/manifest 关键词扫描（改写/重写/改编/源篇/母本）零命中；dedupe 普查（184 标准篇）near_dup_clusters=0 | 本次扫描 + `W03-06_SKELETON_DEDUPE_20261031.json`；诚实限定：该普查口径是"（域，主题集，冲突型）三元组至少一元不同 + bigram<0.4"，内容级相似性未做更强验证；dev E001-E012 不在该普查分母内 |

CSV 中每组编号 = `lineage_group_id = LIN-<母本篇 ID>`；库内入组成员 50 篇（16 长剧组×2 + 8 CF 组×2 + E048/E060），库外成员 2 篇（LRE048/LRE060）。

## 2. 26 组逐组清单（成员暴露状态以保管账 lifecycle 为准）

图例：S=SEALED，E=EXPOSED；**B**=该成员在 B 线 v2 中。

| 组 | 类别 | 成员（状态） | 组内已消费？ | B 线成员 |
|---|---|---|---|---|
| LIN-E117 | 长剧 | E117(E)、LRE117(E：LD1/W15N/W16-UNC/W18/W18B/W19B) | 是 | 无 |
| LIN-E119 | 长剧 | E119(S)、LRE119(S) | 否 | **LRE119** |
| LIN-E121 | 长剧 | E121(S)、LRE121(E：W14J/W18/W18B/W19B) | 是 | 无（v1 曾入、v2 已换出） |
| LIN-E123 | 长剧 | E123(S)、LRE123(S) | 否 | **LRE123** |
| LIN-E125 | 长剧 | E125(E)、LRE125(E：LD1/W15N) | 是 | 无 |
| LIN-E126 | 长剧 | E126(S)、LRE126(S) | 否 | 无 |
| LIN-E127 | 长剧 | E127(S)、LRE127(E：W14J) | 是 | 无（v1 曾入、v2 已换出） |
| LIN-E128 | 长剧 | E128(S)、LRE128(S) | 否 | 无 |
| LIN-E138 | 长剧 | E138(S)、LRE138(E：W18/W18B/W19B) | 是 | 无 |
| LIN-E139 | 长剧 | E139(S)、LRE139(S) | 否 | **LRE139** |
| LIN-E141 | 长剧 | E141(S)、LRE141(E：LD1/W15N) | 是 | 无 |
| LIN-E142 | 长剧 | E142(S)、LRE142(S) | 否 | **LRE142** |
| LIN-E145 | 长剧 | E145(S)、LRE145(E：W14J) | 是 | 无 |
| LIN-E146 | 长剧 | E146(S)、LRE146(E：W14J/W16-UNC/W18/W18B/W19B) | 是 | 无（v1 曾入、v2 已换出） |
| LIN-E147 | 长剧 | E147(S)、LRE147(E：LD1/W15N) | 是 | 无 |
| LIN-E148 | 长剧 | E148(S)、LRE148(S) | 否 | 无 |
| LIN-E077 | CF(time) | E077(S)、E149(S) | 否 | 无 |
| LIN-E105 | CF(time) | E105(S)、E150(S) | 否 | **E150** |
| LIN-E086 | CF(subject) | E086(S)、E151(S) | 否 | 无 |
| LIN-E072 | CF(negation) | E072(S)、E152(S) | 否 | **E152** |
| LIN-E084 | CF(negation) | E084(S)、E153(S) | 否 | **E153** |
| LIN-E094 | CF(status) | E094(S)、E154(S) | 否 | 无 |
| LIN-E090 | CF(status) | E090(S)、E155(S) | 否 | **E155** |
| LIN-E087 | CF(goal) | E087(S)、E156(S) | 否 | 无 |
| **LIN-E048** | 长剧(库外) | E048(S，**B 线 self**)、LRE048(库外，E：LR2/FLJUDGE 2026-11-01) | **是** | **E048** |
| LIN-E060 | 长剧(库外) | E060(S)、LRE060(库外，E：LR2/FLJUDGE 2026-11-01) | 是 | 无 |

汇总：**含已消费成员的组 11 个**（LIN-E048/E060/E117/E121/E125/E127/E138/E141/E145/E146/E147）；全密封组 15 个（CF 8 组全部 + 长剧 7 组）。

## 3. 跨 dev/holdout（未见面）使用的风险组

**风险机理**：长剧组内母本的前 12 轮与 LRE 逐字相同——凡消费过 LRE 的批次，其参战 harness 已经看过母本前 12 轮的全部题面。因此"母本本体 lifecycle=SEALED"不等于"内容未被该 harness 见过"。当前选篇/隔离规则只查**篇本体** lifecycle，不查谱系组兄弟节点——这是 E048 漏网的原因，也是下表所有"母本 S / LRE E"组的共同风险。

| 风险级 | 组 | 说明 |
|---|---|---|
| **CRITICAL（直通 B 线）** | LIN-E048 | B 线 v2 成员 E048（12 轮）的全部内容 = LRE048（32 轮）的前 12 轮；LRE048 已于 2026-11-01 被 BATCH-LONGRANGE-LR2 与 BATCH-FLJUDGE 消费（保管账 consumed_by 实录）。**凡参战过 LR2/FLJUDGE 的 harness，其 B 线 E048 答卷不具"未见"效力。** B 线 v2 封存（2026-11-03）晚于该消费，但重封时仅剔除了"本体 lifecycle=EXPOSED"的 6 篇（v1 事故篇 E117/E164/E191/LRE121/LRE146/LRE127），未按谱系组过滤，E048 因此留存 |
| 高（若用于未见面/holdout 角色） | LIN-E060、LIN-E121、LIN-E127、LIN-E138、LIN-E141、LIN-E145、LIN-E146、LIN-E147 | 母本 SEALED 但其 LRE 兄弟已被 W14J/W15N/W18 系/W19B 消费——对参战过对应批次的 harness，母本内容已暴露 12/12 轮 |
| 中 | LIN-E117、LIN-E125 | 母本与 LRE 双双 EXPOSED（本体制已烧），风险同上且更明确 |
| 低（当前干净） | 其余 15 组 | 全体成员 SEALED；其中 7 组已 committed 给 B 线（LIN-E119/E123/E139/E142 + 4 个含 B 线 CF 的组），剩余真正"干净且未承诺"= **7 组 14 篇**（LIN-E126/E128/E148 + LIN-E077/E086/E094/E087） |

CF 组现状健康：8 组双成员全 SEALED；B 线 4 篇 CF（E150/E152/E153/E155）的基线伙伴（E105/E072/E084/E090）也全未消费，孪生对照的"未见"前提当前成立。附带提示：CF 的单变量扰动设计意味着**基线篇一旦被开发消费，其孪生 CF 的扰动点即半可推断**——故 4 个 CF 组的基线篇应与 CF 同列"不得单独消费"。

## 4. B 线关联组完整列表（reuse 审计的封锁集，32 篇）

- **self 24**：标准 16 = E045/E046/E048/E049/E052/E053/E055/E056/E058/E059（wave1）+ E069/E070/E071/E073/E074/E075（wave2）；长剧 4 = LRE119/LRE123/LRE139/LRE142；CF 4 = E150/E152/E153/E155。（以 `B_LINE_MANIFEST.json` v2 episodes 字典为准；注意 `B_LINE_README.md` §6 仍写 v1 口径——LRE121/LRE146/LRE139/LRE127 + E156，**README 未同步，以 manifest v2 为准**，这本身是一个文档面泄露隐患：照 README 开箱会跑错篇。）
- **lineage 8**：E105/E072/E084/E090（CF 基线伙伴）+ E119/E123/E139/E142（长剧母本）。
- both = 0（库内无"双 B 线成员"组）。
- 另有库外关联：LIN-E048 组的 LRE048 非 B 线成员，但构成 §3 的 CRITICAL 风险。

## 5. 本审计不能证明之事

1. **B 线封存副本侧的 sha256 未独立重算**（重算需读取 `b_line/episodes/` 内容，超出本工作包授权）；"逐字节复制"目前 = manifest 声明 + 库侧源文件 24/24 实算匹配 + manifest integrity.verified_at 字段（2026-11-03T21:51:25）。
2. "T1-T12 逐字保留"对 16 篇 wave3_ext 长剧只**抽样亲验了 LRE117**（12/12 相等）；其余 15 篇依据生成期"程序化深度相等校验"的账本声明（`lr2_material_seeded`/`wave3_ext_g2_seeded`）。
3. 内容级相似性排查依赖 manifest/audit 关键词与 dedupe 普查口径；比 bigram<0.4 更强的语义重写检测未做。
4. "凡参战过 X 批次的 harness 已看过题面"以保管账 consumed_by 为准；账本在 2026-11-03 前有漏记前科（54 条 backfill 补录），补录是否穷尽无法从账本自身证明（A 材料 §8.2/§10 同口径）。

## 6. 结论：必须整组隔离的组

1. **立即决策（B 线开箱前）**：**LIN-E048 整组隔离**——要么把 E048 换出 B 线 v2（以同构干净篇替补并重新封存记账），要么在验收绑定中显式登记"参战 harness 若含 LR2/FLJUDGE 参战版本，E048 位答卷按已暴露折价/剔除"。二者选其一，不能默认通过。
2. **规则修补（建议）**：未见面资格（B 线选篇、未来 holdout 线、UNTOUCHED 状态）的检查单元从"篇"改为"谱系组"：组内任一成员 lifecycle=EXPOSED（或 wave1_ext 等库外消费）→ 整组不得进入未见面角色。
3. **降级使用**：11 个含已消费成员的组的全部库内成员（E048/E060/E117/E121/E125/E127/E138/E141/E145/E146/E147 及对应 LRE）只可作 dev/回归用途，不得担任独立泛化载体。
4. **CF 保全**：4 个含 B 线 CF 的组（LIN-E105/E072/E084/E090）的基线篇与 B 线 CF 同等封存，不得在 B 线开箱前单独消费。
5. **文档同步**：更正 `B_LINE_README.md` 的 v1 口径（或加显眼过期声明），消除"照 README 开箱跑错篇"的实操风险。

---

*数据来源：STORY_CUSTODY_LEDGER.json（registered=210，events=173，含 lr2_material_*/wave3_ext_g2_seeded/B-LINE-SEAL-V2 等事件）；wave3/STAGE1_MANIFEST_CF.json；wave3_ext 与 wave1_ext 的 MANIFEST_LRE*.json；B_LINE_MANIFEST.json（b_line_v2，仅元数据）；GOLD sidecar provenance（source_conflicts 93 处全部位于 LRE117/LRE121/LRE146，即跨 wave 汇编卡机制本身是谱系组的伴生现象）。LRE117↔E117 逐字相等为本脚本 2026-11-07 只读复算。*
