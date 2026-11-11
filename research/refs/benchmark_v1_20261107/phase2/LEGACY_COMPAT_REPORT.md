# LEGACY_COMPAT_REPORT.md — LEGACY_COMPAT_R9 输入适配候选交付报告

- 日期：2026-11-08 ｜ 依据：Benchmark V1 Phase 2 指导书 §9 ｜ 状态：交付（离线，零模型调用）
- 结论先行：**LEGACY（r9_slim）在 wave 系 dict persona 上 init 即崩的故障已用最小输入边界适配解决**；
  str persona 行为与原版逐字节等价（有测试断言），dict persona 语义信息全保留进入旧闭包
  字符串通道（规则写死、有注释、有测试）；原候选 `candidates/action_policy_r9_slim`
  一个字节未改（UNTOUCHABLE 哈希守护 + 身份哈希复算一致）。

---

## 1. 崩溃点（适配的必要性）

- 被适配对象：`candidates/action_policy_r9_slim`（LEGACY；运行时闭包
  r9_slim → r2 → r1 → v6_goal_context → v5 → reference_product_v1）。
- 故障：wave1/wave1_ext/wave2/wave3/wave3_ext 的 episode `persona.me`/`persona.her`
  为 dict（实测全库 239 篇中 178 篇；字段域见 §2）。旧闭包
  `action_policy_r1/mechanism/mechanism.py:270` `_seed_facts` 对其调 `.strip()`
  （`_seed_block` L319 同型消费）→ `AttributeError: 'dict' object has no attribute 'strip'`，
  init 即崩（`arena/lb_runner.py` run_episode L540 无捕获，直接上抛，答卷零轮产出）。
- 影响：LEGACY 臂无法参加任何 wave 系 episode 的对照；stage 系（字符串 persona）
  不受影响。

## 2. persona 实际字段结构（先读后设计，全库实扫）

2026-11-08 对 `benchmark/stories_v2` 全部 239 篇 episode.json 的实扫（工具内联完成，
样例基准 `benchmark/stories_v2/wave3/E191/episode.json`）：

| 位置 | 形态 | 计数 | 字段 |
|---|---|---|---|
| persona 顶层 | `{her, me}` | 61 | — |
| persona 顶层 | `{her, me, relationship_stage}` | 178 | relationship_stage 恒为 str |
| `me` | dict | 171+7 | `{name: str, traits: [str,…]}`（171）；`{name, traits, boundaries: [str,…]}`（7） |
| `her` | dict | 178 | `{name, traits, boundaries}` |

- 实测库中**不存在** `age`/`occupation` 字段；转换模板仍预留（通用性），出现即按序渲染。
- 旧闭包只对 `persona.me`/`persona.her` 做字符串消费（`_seed_facts`/`_seed_block`）；
  `relationship_stage` 等 dict 顶层键旧闭包本就整体透传，不触碰。

## 3. 适配代码（薄包装，`tools/benchmark_v1/baseline_candidates/legacy_compat_r9/`）

| 文件 | 说明 |
|---|---|
| `mechanism/mechanism.py` | 唯一逻辑文件。importlib 按绝对路径加载原候选 mechanism（模块名 `legacy_compat_r9_target_action_policy_r9_slim`），复用其**完整运行时闭包**（原闭包内部按各自 `__file__` 级联加载 r2/r1/v6/v5/base，全部落在 candidates/ 原位，非副本）。 |
| `params.json` | `role=baseline, arm_id=LEGACY_COMPAT_R9, output_contract=null`（与 r9_slim 原值对齐：原 params 即 `output_contract: null` → `load_contract` 返回 None，语义一致）；note 明确"经输入适配的历史版本，非原版"。 |
| `IDENTITY.md` | 身份卡：适配点清单、与原版差异、哈希、禁令。 |
| `assets/system_prompt/*.md`（4 件） | **占位指针文件**：仅为满足 `lb_runner.require_candidate_assets` 的入口完整性 fail-fast；真实资产由 `mechanism.init` 把 assets_root 一律转发到 `candidates/action_policy_r9_slim/assets`（唯一来源，不复制不漂移）。 |

接口转发面（无第二处逻辑）：`init`（内联 adapt+assets 转发）；
`run_turn/snapshot/restore/set_memory_access/build_system_prompt` 直接绑定原机制模块对象。

## 4. dict→str 转换规则（写死于 `persona_struct_to_str`，确定性）

- **适用面**：仅 `persona.me`/`persona.her` 中 `isinstance(dict)` 的单元；str 型原样透传（零改动）。
- **模板**（字段序固定；段间"，"、列表项间"、"）：
  `name，[age岁，][occupation，]traits 逐项[，底线：boundaries 逐项]`
- 实例（E191 真实结构）：
  - `me={"name":"阿树","traits":["德式桌游熟练工","转账准时记得清","提建议给台阶"]}`
    → `阿树，德式桌游熟练工、转账准时记得清、提建议给台阶`
  - `her={"name":"沈一诺","traits":[…4 项],"boundaries":[…3 项]}`
    → `沈一诺，桌游局固定组织者、会做规则速查表、…，底线：费用当周结清不留尾巴、…`
- **语义零丢失**：traits 全部逐项；boundaries 以"底线："前缀整体保留（类别信息不灭）；
  已知字段（name/age/occupation/traits/boundaries）之外的任何额外键以 canonical JSON
  （`ensure_ascii=False, sort_keys=True, 紧凑分隔符`）追加在末段——未知字段不丢。
- **空 dict → 空串**：落入 r1 `if not t: continue` 分支，与"persona 缺该键"行为完全一致。
- **不写回**：适配作用于 init 内部浅拷贝，调用方 episode 数据不被改写（有测试断言）。

## 5. 测试结果（`tools/benchmark_v1/tests/test_benchmark_v1_offline.py`，全离线零模型调用）

新增 7 项（全套件 21 passed，含 Phase 1 既有 14 项全绿）：

| # | 测试 | 断言要点 | 结果 |
|---|---|---|---|
| 1 | `test_legacy_compat_str_persona_equivalence` | str persona：init 产物（`snapshot` 的 mem/seed_fids/plan_items）与直接加载 r9_slim 语义等价；整集 2 轮 mock（`lb_gateway.MockAdapter`）运行每轮 `calls.json` 的 system+user **逐字节相等**（含资产转发后的 system 构建） | PASS |
| 2 | `test_legacy_compat_dict_persona_runs_two_turns` | wave3 真实结构 dict persona（E191 同构）：原 r9_slim 同集运行 `AttributeError`（崩溃复现，证明适配非恒真）；LEGACY_COMPAT_R9 完整跑通 2 轮；prompt 含 name/traits 逐项/"底线："boundaries/relationship_stage 透传；调用方数据未被改写 | PASS |
| 3 | `test_r9_slim_untouchable_hash` | UNTOUCHABLE_HASHES.json 中 `candidates/action_policy_r9_slim/*` 全部 10 文件逐一复算 sha256，零漂移 | PASS |
| 4 | `test_legacy_compat_registry_identity_consistent` | ARM_REGISTRY.json LEGACY_COMPAT_R9 登记哈希 = 实算；含"经输入适配"声明；原 LEGACY 记录原样保留 | PASS |
| 5 | `test_lineage_check_detects_cross_split_bline_and_holdout_consumption` | 合成谱系树：跨分区 / B 线关联组进 DEV / HOLDOUT 组已消费 三类违规全命中 | PASS |
| 6 | `test_lineage_check_clean_layout_passes` | 干净布置零违规；CLI `--json` exit 0/1 双向验证 | PASS |
| 7 | `test_lineage_check_real_stories_v2_inventory` | 真实 stories_v2 只读回归：26 组与 LINEAGE_LEAKAGE_REPORT 口径一致；B 线关联组 9 组；LIN-E048 跨分区复现 | PASS |

真实数据只读冒烟（非测试，另做）：E117(DEV)/LRE117(HOLDOUT) → `cross_split LIN-E117`
exit 1；空 CSV 输入错误 exit 2。

## 6. 哈希与登记

| 对象 | 哈希（`four_arm_runner.candidate_identity_sha256`，候选依赖闭包口径） |
|---|---|
| `tools/benchmark_v1/baseline_candidates/legacy_compat_r9` | `e09cd8774331feffb67f5f144b3efc095f8fa0a8fc8a4b105e7847aa1091b7dc` |
| `candidates/action_policy_r9_slim`（原候选，复算旁证未动） | `c57c093608060d740f16867604885daa6eef05d95f15d569fce0e2d04e55c7e8`（= ARM_REGISTRY 既有 LEGACY 登记值） |

登记：`refs/benchmark_v1_20261107/phase1/ARM_REGISTRY.json` — 原 LEGACY=r9_slim 记录
原样保留（generated_at 不改，新增 updated_at + revision_note）；LEGACY 条目追加
`compat_variant` 指引；新增 `arms.LEGACY_COMPAT_R9` 完整条目（compat_variant.of/target/
note/crash_fixed/report + identity_sha256）。

## 7. 诚实声明（不能保证的点，如实登记）

1. **"语义等价"只在 str persona 上有逐字节证明**（测试 #1）。dict persona 下原版
   崩溃、不存在可对拍的"原版 dict 行为"，本变体的保证是**语义信息无损进入旧闭包的
   字符串通道**（转换规则确定性 + 字段域实测 + canonical JSON 兜底），不宣称
   "dict 转换后的行为 = 某个从未存在过的原版行为"。
2. **字符串化丢失结构标签**：traits/boundaries 在旧闭包里变成同一段文本，
   旧机制本无字段级消费路径（只 .strip() 后整体入库），故功能无损失；但若未来
   出现需要结构化 persona 的评分断言（如"须引用 boundaries 字段"），应针对
   "底线："前缀文本核对，不得假设结构仍存在。
3. **assets 占位指针**：本候选目录内的 4 个 system_prompt 文件是转发指针（内容
   自明），真实资产在原候选处；因此本候选身份哈希 ≠ 原候选哈希属预期；`_sha_file`
   式运行记录若采集本目录资产，记录的是指针文件而非提示词内容（已在文件头注明）。
4. **未接线 four_arm_runner**：本批只做候选 + 登记 + 报告（§9 范围）；
   LEGACY_COMPAT_R9 尚未进入四臂运行器的 ARMS 分派，实际批次派发属 Phase 2 后续
   执行项（派发时必须在报告中标注"经输入适配的历史版本"）。
5. **wave1_ext 库外成员**（LRE048/LRE060）：lineage_check 经 manifest 元数据可
   发现其谱系边（title_pattern/id_pattern），但若其目录无 exposure.json，工具以
   warning 如实标注"无法核消费"，不当通过。
6. 全部验证为离线 mock（零模型调用）；真实 dsf 服务上的行为（含质量分）未测，
   不在本批范围，不得据此宣布任何对照结论。

## 8. 改动文件清单（本批全部）

**修改（3）**
- `refs/benchmark_v1_20261107/phase1/SPLIT_AND_CUSTODY_PLAN.md`（头部新增 §0 修订节，原文保留）
- `refs/benchmark_v1_20261107/phase1/NEW_STORY_SPEC_V1.md`（新增 §0 修订节；§3.5/§6.1 就地 SUPERSEDED 标注）
- `refs/benchmark_v1_20261107/phase1/ARM_REGISTRY.json`（LEGACY 追加 compat_variant；新增 LEGACY_COMPAT_R9；updated_at/revision_note）

**新建（10）**
- `tools/benchmark_v1/lineage_check.py`
- `tools/benchmark_v1/baseline_candidates/legacy_compat_r9/mechanism/mechanism.py`
- `tools/benchmark_v1/baseline_candidates/legacy_compat_r9/params.json`
- `tools/benchmark_v1/baseline_candidates/legacy_compat_r9/IDENTITY.md`
- `tools/benchmark_v1/baseline_candidates/legacy_compat_r9/assets/system_prompt/{core,format,redline,naturalness_check}.md`（4 件占位指针）
- `refs/benchmark_v1_20261107/phase2/LEGACY_COMPAT_REPORT.md`（本文件）

**测试追加（1 文件 +7 测试）**
- `tools/benchmark_v1/tests/test_benchmark_v1_offline.py`：21 passed（14 旧 + 7 新）

**未动（硬约束守护）**：`candidates/action_policy_r9_slim/**`（测试 #3）、`arena/*.py`、
`benchmark/b_line` 正文（B 线只读 manifest 元数据）。
