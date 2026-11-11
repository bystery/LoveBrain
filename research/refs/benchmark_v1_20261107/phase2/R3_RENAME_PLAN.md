# R3_RENAME_PLAN · wave4_dev 跨作者重名改名计划（只读规划）

> **本计划只读：不改任何文件。** 12 组重名对单篇评分无影响（C2 结论：故事记忆实例互相隔离，单篇评分不受影响），登记为冻结前打磨项，非阻塞。
> 来源：`STORY_BATCH_REPORTS/AUDIT_C1_E301-E316.md` MAJOR-5（姜宁组+波次级 10 组登记）、`AUDIT_C2_E317-E332.md` MAJOR-2（9 对含沈聿三重+22 篇内部补充）、`BATCH1_AGENT_REPORTS_DIGEST.md` R1/R2 移交（"重名 12 组全部跨 R2 边界，移交全库统一改名批次"）。
> 先例：R1 已按"正文零牵连优先"改 E313 HER 姜宁→简宁（正文无人名牵连，E308 保留姜宁）——本计划沿用同一判据。
> 全部数字 python -I 实算（2026-11-08；复现脚本见 §7 附录）。

## 0. 机械盘点（python -I 实算 wave4_dev 全部 38 篇 persona）

38 篇 persona（her+me）共取 **62 个唯一人名**；其中 **12 个名字重复出现 = 12 组**（与 C1/C2/digest 的 12 组移交完全对齐；姜宁组已被 R1 消解，故不在现存量内）：

- 跨故事重名 9 组：苏叶(E303/E326)、蒋一楠(E304/E328)、韩东(E304/E332)、沈聿(E305/E318/E333/E334，三重)、周衍(E309/E324)、程野(E313/E322)、何蔓(E314/E327)、阮清(E316/E329)、韩澈(E320/E338)。
- 孪生组内共享 3 名：乔安(E333/E334)、闻笛(E335/E336)、陆言(E335/E336)——E333/E334=LG-T10-G1、E335/E336=LG-T10-G2（exposure.lineage_group_id 实读），孪生=同一故事的单变量反事实变体，**同名是设计要求**（C3 已验证同构轮卡面逐字段全等；custody guard 有 twin 同 split 专项测试）。

## 1. 改名侧选择标准（按优先级）

1. **正文零牵连优先**（R1 先例）：改名侧的该人名不得出现在正文消息里——消息文本被原子 quote 逐字锚定，动正文牵连 quote 同步。
2. 零牵连两侧都满足时取**牵连面总计数最小**的一侧（六面合计：persona/initial_kb/正文消息/原子 value/原子 quote/卡面其余+timeline/audit）。
3. HER 名与 ME 名：同名两侧角色相同时不构成 HER/ME 取舍；ME 名牵连通常更小（C2 建议同款）。
4. 避免二次扰动：刚经 R1/审计修复的篇（如 E313）不再选为改名侧，除非它是唯一零牵连侧。

## 2. 12 组处置总表（9 改名 + 3 豁免）

| # | 名字 | 涉及篇目(角色) | 改名侧 | 改名侧牵连面（python -I 实算） | 不改侧牵连面 | 建议新名 |
|---|---|---|---|---|---|---|
| 1 | 苏叶 | E303(her) / E326(her) | **E303** | persona1+kb1+audit2=**4**（msg0 quote0 卡面0） | 40（val16+卡面15+tl3+audit4…） | 邵叶 |
| 2 | 蒋一楠 | E304(her) / E328(her) | **E304** | persona1+kb1+timeline1+audit2=**5**（msg0 quote0） | 25（val9+卡面10+tl2…） | 尹楠 |
| 3 | 韩东 | E304(me) / E332(me) | **E304** | persona1+kb2+timeline2+audit2=**7**（msg0 quote0） | 17（msg1+val4+卡面4+tl3…） | 卢东 |
| 4 | 沈聿 | E305(me) / E318(me) / E333(me) / E334(me) | **E305**（孪生两篇保留） | persona1+kb2+timeline8+audit3=**14**（msg0 quote0） | E318=10 但含 msg1（E318_T01 HER）；孪生=设计内 | 傅聿 |
| 5 | 周衍 | E309(me) / E324(me) | **E309** | persona1+kb1+value3+卡面8+timeline2+audit4=**19**（msg0 **quote0**） | 17 但 msg2+**quote1**（quote 同步风险） | 杜衍 |
| 6 | 程野 | E313(me) / E322(me) | **E322** | persona1+kb3+msg1(E322_T01)+timeline1+audit3=**9**（quote0） | 25（msg1+val6+卡面7+tl3…），且 E313 刚经 R1 改名 | 邱野 |
| 7 | 何蔓 | E314(her) / E327(her) | **E314** | persona1+kb2+卡面12+timeline1+audit4=**20**（msg0 quote0） | 37（val15+卡面12+tl4…） | 甘蔓 |
| 8 | 阮清 | E316(her) / E329(her) | **E316** | persona1+kb1+value1+卡面12+timeline7+audit3=**25**（msg0 quote0） | 26（val10+卡面12+audit2…） | 冉清 |
| 9 | 韩澈 | E320(me) / E338(me) | **E338** | persona1+kb2+msg1(E338_T01)+timeline1+audit2=**7**（quote0） | 8（msg1+tl1+audit3…） | 桂澈 |
| 10 | 乔安 | E333(her) / E334(her) | **豁免**（LG-T10-G1 孪生设计内共享） | — | — | — |
| 11 | 闻笛 | E335(her) / E336(her) | **豁免**（LG-T10-G2 孪生设计内共享） | — | — | — |
| 12 | 陆言 | E335(me) / E336(me) | **豁免**（LG-T10-G2 孪生设计内共享） | — | — | — |

牵连面口径：对每篇统计该名在 persona（episode.json persona 序列化文本）、initial_kb、正文消息（按消息 text 计并区分 HER/ME）、原子 value、原子 quote（source.quote，改名侧全为 0）、reference 卡面其余文本（current_action/acceptable_actions/forbidden 等，记"卡面"）、timeline.json、audit.md 的出现次数；上表"卡面 n"=卡面其余合计。9 个改名侧中 7 个正文零牵连；2 个（程野@E322、韩澈@E338）各有 1 处 T01 HER 消息提及，**quote 命中均为 0**（已实算），改名不需同步任何 quote。

## 3. 新名唯一性验证（python -I 实算，已通过）

- 池：wave4_dev 62 名 + **stories_v2 全库 216 篇实取 239 名**（stage1–stageD/wave1–wave3_ext 全波）。
- 9 个新名（邵叶/尹楠/卢东/傅聿/杜衍/邱野/甘蔓/冉清/桂澈）互不相同；对 239 名池零碰撞；对 38 篇 wave4_dev 全文本（episode/reference/timeline/exposure/audit，含正文与卡）零出现。
- 构名风格沿用 R1 先例（换姓保字：姜宁→简宁）：沈聿→傅聿、周衍→杜衍、程野→邱野、韩澈→桂澈、何蔓→甘蔓、阮清→冉清；苏叶→邵叶、蒋一楠→尹楠、韩东→卢东为全换（避免保留"苏/蒋/韩"姓与保留侧跨篇检索混淆）。

## 4. 残留与边界（诚实登记，不冒充已解决）

1. **E311 audit ×3"周衍"**：均为 MAJOR-7 修复的历史记录文本（"'周衍'→'韩江'"），非活数据；周衍(E309)改名后无需改动，audit 史料如实保留。
2. **E330 audit ×1"韩澈"**：跨作者重名登记原文（"移交 R3 待办"），史料保留。
3. **E314 第三方"程野"×7**：仅存在于 E314 reference 卡面（value/forbidden/current_action），**不在任何候选可见输入**（episode 正文零出现）；与 E313/E322 的 ME 程野构成第三方级重名，超出本批 12 组（persona 名）范围，登记为残留。改 E322 ME 不解决也不恶化它。
4. **简宁（R1 新名）与 wave3/E192(her) 简宁构成全库重名**：R1 只保证 wave4_dev 内唯一；全库级残留登记，不在本批范围（同 4.3 一并归全库改名政策）。
5. **库级历史重名**（wave3 内部、wave3↔wave3_ext/LRE 同故事镜像、旧 ME 通用名等 85 组）：多为旧波次/同故事镜像，属既有存量，不在本批 12 组范围。
6. **B 线不可核验**：按隔离纪律未读取 benchmark/b_line/ 正文，新名无法对 B 线人名核验（VERSION_LINEAGE_V2.json 无 persona 字段）；如需严格唯一，执行批须用授权的人名清单出口核对，不得读正文。

## 5. 执行批次建议（冻结批 R3-1，单批原子完成）

- **前置**：live pilot 收口后执行（9 个改名篇中 E303/E304/E305/E309/E314/E316/E322/E338 多数在 pilot2 子集内；改名会变更 E322_T01/E338_T01 两处候选可见输入，不得对在跑题目动正文）。
- **单批不拆**：8 篇 9 名一次改完+全链复验；拆批会制造中间态混合版本（§9 版本分离原则）。
- **步骤**：①备份 8 个篇目录（沿用 R1 惯例，改后 deep diff 零意外改动核验）→ ②逐篇替换六面文本（§2 明细；E322/E338 各 1 处消息文本）→ ③`cp episode.json runner_episode.json`（×8）→ ④`fix_wave4_schema_v1.py --apply --phase b`（exposure.hashes 终算）→ ⑤`build_wave_manifest.py --wave wave4_dev`（三份 W4_MANIFEST sha256 刷新）→ ⑥§6 验收全链 → ⑦GOLD_SIDECAR 全量重建 + repin_log 登记（E322/E338 消息哈希变化牵连 provenance）→ ⑧题库版本登记 W4-DEV-20261108-R3：pilot2 已产答卷按 §9 绑定改名前快照，不重烧。
- **政策选项（默认不执行）**：若用户裁定孪生组也须全库唯一，追加 R3-2 冻结批对 E333/E334、E335/E336 成对同改（两侧同步同名，保持同构），并重验 C3 的孪生对齐断言；牵连面另计（乔安 13、闻笛 10、陆言 14，含两侧 persona/kb/audit/timeline/msg）。

## 6. 改名后验收命令（quote 复验 + 人名唯一性全库扫描 + 全链）

```bash
# quote 复验（quote 未动 → strict 必须仍为全命中；validate 亦逐字核 source-quote）
python -I -c "import sys; sys.path.insert(0,'tools'); g={'__name__':'__main__','__file__':'tools/build_gold_sidecar.py'}; exec(open('tools/build_gold_sidecar.py',encoding='utf-8').read(),g)" --dry-run
python -I tools/validate_story_v2.py --stage wave4_dev --turns 12 --count 36 --manifest-name W4_MANIFEST_12T.json --out-name W4_VALIDATION_12T.json --allow-frozen-at
python -I tools/validate_story_v2.py --stage wave4_dev --turns 24 --count 1  --manifest-name W4_MANIFEST_24T.json --out-name W4_VALIDATION_24T.json --allow-frozen-at
python -I tools/validate_story_v2.py --stage wave4_dev --turns 32 --count 1  --manifest-name W4_MANIFEST_32T.json --out-name W4_VALIDATION_32T.json --allow-frozen-at
# 人名唯一性全库扫描（persona 口径；命令见 §7，预期 wave4_dev 剩余重名=3 个孪生设计组，跨故事组=0）
# 机械链回归
python -I tools/benchmark_v1/dedupe_wave.py --wave wave4_dev
python -I tools/benchmark_v1/lineage_check.py --stories-root benchmark/stories_v2 --split-csv refs/benchmark_v1_20261107/phase2/LINEAGE_SPLIT_PROVISIONAL.csv
python -I tools/benchmark_v1/custody_split_guard.py --expected-pending 157
python -I -m pytest tools/benchmark_v1/tests -q
```

预期：sidecar dry-run `wave4_dev atoms=1350 strict_hit=1350`、Marker QC 0；validate ×3 全绿 488 case；dedupe 仍仅 2 孪生簇；lineage/custody 0 违例；pytest 全绿。逐篇替换计数对账：§2 改名侧牵连面数字 = 实际替换处数（audit.md 同步行另计，按 D 阶段重构吸收）。

## 7. 附录：盘点复现脚本（python -I，只读）

```bash
python -I - <<'EOF'
import json,os,collections
ROOT='benchmark/stories_v2'; pool=collections.defaultdict(set)
for w in sorted(os.listdir(ROOT)):
    wd=os.path.join(ROOT,w)
    if not os.path.isdir(wd) or w in ('GOLD_SIDECAR','_templates'): continue
    for ep in sorted(os.listdir(wd)):
        p=os.path.join(wd,ep,'episode.json')
        if not os.path.isfile(p): continue
        try: d=json.load(open(p,encoding='utf-8'))
        except Exception: continue
        per=d.get('persona') or {}
        try: pool[per['her']['name']].add(w+'/'+ep); pool[per['me']['name']].add(w+'/'+ep)
        except Exception: pass
dups={n:sorted(v) for n,v in pool.items() if len(v)>1}
print('library unique names=',len(pool),'duplicate groups=',len(dups))
for n,v in sorted(dups.items()): print(n,v)
EOF
```

wave4_dev 口径（38 篇、62 名、12 组）与 §2 牵连面六面计数由同一脚本族实算得出（2026-11-08 快照：库级 216 篇 239 名 85 组，其中 wave4_dev 贡献 12 组=本计划对象）。
