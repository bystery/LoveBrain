> ⚠️ **收口补充件 REV2（本文件）**：**§二「NEEDS_CHECK 4/4 tier 优于 base / 方向得到支持」的偏乐观结论已被 `_REV3.md` 撤销**——第 2 独立抽样未复现该改善（属单抽样噪声），assertion_tiering 最终判为 **NOT-CONFIRMED**。
> 本件对 cost「含 C1 的 554/545」部分仍准确，但 **C2 复测后再 +19/+19 → 573/564/failed6**，token 全口径见 `C_CONTRAST2_COST_20261029.json`。当前以 `F_BCD_CLOSEOUT_ADDENDUM_20261029_REV3.md` 为准。正文原样保留。（横幅追加于 2026-10-29 整理 pass。）

# 收口补充件 REV2：把 C 对照批并入提交口径（2026-10-29 晚）

本件是 `F_BCD_CLOSEOUT_ADDENDUM_20261029.md`（REV1，16:00–17:10 写）之后的**版本追加**，
**不回改**任何原始答卷、判词、冻结身份、REV1 补充件、`STATE_AND_ERRATA.json`、`F6_COST_INDEX.json`。
可交口径不变：《固定基础模型下的聊天 Harness 重构原型及开发集对照实验》，**总体判决 REWORK**。

REV1 写就时 C 段为"实现 NOT_STARTED、对照批未登记未派发"。此后（17:20 登记、17:30 实跑、17:42 关账）
C 已真正建成新身份并跑完对照批——本件据此更新 C，并同步 cost。凡与 REV1/STATE 文字冲突处，以本件与原始证据为准。

## 一、撤销与状态更正（逐条，附一手证据）

- **撤销：任务描述"数据 …\logs\c_contrast_live_20261029_164332 与 173054 两个目录"。**
  原因：`c_contrast_live` 实测只有 `173054` 一个目录；`164332` 是 B 批 `kb_probe_live_20261029_164332`，非 C。
- **更正：STATE `phases.C_major_reclass_hypothesis.status=analysis_done_dispatch_not_registered`、
  `next_actions` 第 1 条"对照批 ≤24 生成未登记未派发"、REV1 §C 末"未登记未派发"——均已过时。**
  一手证据：`C_CONTRAST_AUTHORIZATION_20261029.json`（registered_at 2026-10-29T17:20:08，
  candidate=action_policy_r5_tier，identity=ceb8008f…）、`F_C_CONTRAST_REGISTRATION.json`、
  `logs/c_contrast_live_20261029_173054/`（24 格 reply_raw.txt + C_CONTRAST_RESULT.json done=24 ok=24）、
  `logs/auth_budget/AUTH-C-CONTRAST-20261029-01.jsonl`（24 started/24 completed/末事件 authorization_closed）。
- **登记缺陷（新）：`C_CONTRAST_AUTHORIZATION_20261029.json` 的 `"status": "ACTIVE"` 是派发前写入、跑完未回写；
  账本已关单（末事件 authorization_closed）。** 按"不复活不挪用不补跑"纪律，该单视同已关闭；JSON 字段属陈旧显示、不回改。
- 新增身份：`action_policy_r5_tier`（从 e3 `17fd2160…` 另建，`ceb8008f…`）。相对 e3 的真实代码差异
  ＝仅新增 `_TIER` 分层文本 + 打开 `assertion_tiering` 开关（IDENTITY/MECHANISM.md 文档随之变），
  其余机制/资产/contract_guard 逐字节未动——符合"从 e3 另建新身份、只改行动块分层文本"，旧身份 e3 未被就地改。
- 机器校验（本窗口独立复算）：三身份 `identical=true`——e3（对 F5 冻结件 --flat）、r5_tier（对 F_R5 冻结件 --flat）、
  guard_final（对 FINAL 冻结件，非 --flat，因该冻结件无 files 映射、--flat 会 KeyError，属工具限制非漂移）。

## 二、C 对照批结果（详见 `F_C_CONTRAST_GRADES_20261029.{md,json}`）

方法已独立复核：两臂同一条真实付费 prompt，tier = base 把 `_HYGIENE` 换成 e3文本+292码元"依据分层"段，
`H5.startswith(H3)=True`、12 组 base/tier sha 逐一匹配、only_diff=292、24 格全落地、transport 24/24、JSON 24/24 可解析。

逐格语义判定（引原文，非词表/正则）：

- NEEDS_CHECK 4/4 位点 tier **首选位**优于 base：K01_T06 补"进屋就难受还是挨着猫才犯"的分支+谁核实；
  N02_T03 把"你开完会直接过来"（把未定出席当既定）改为"开到十一点**的话**…要是能出校门"；
  K01_T19 去掉"14号那晚本来就在里面，什么都不用改"的无据确定化；
  K01_T20 把"对，三晚都住才村"（越出她"两晚"问句范围）改回"两晚…吧"+以未定项收尾。
- **残留缺陷（如实登记）**：tier 在 K01_T19/K01_T20 的 **directions 槽**仍会把"三晚含14号/住才村"再说成既定——
  分层主要修好首选正文，尚未彻底覆盖方向槽。
- PROPOSAL 护栏"该推进不退缩"：4/4 未回退（tier 未退化成"你自己去确认/都行"、未引入沉默）。
- EVIDENCED 护栏"已确定不反复问"：4/4 未回退（K01_T16/T21/T13/T18 两臂均直接引用开售/假期/住处/更正日期；
  tier warm 对退款截止正确写"11月10号"而非作废的 8 号）。

否证判定：写死规则为"NEEDS_CHECK 改善但任一护栏回退即否证"。实测 NEEDS_CHECK 改善且两护栏均未回退 →
**assertion_tiering 未被否证，方向得到支持**。

不得夸大为"已证明胜出"的边界：单臂单次抽样（temp 0.7、每格 1 次，无 B/F5 式双顺序复测）；改进为定性判读；
残留缺陷在 directions 槽；网关边界拼接、非端到端记忆写回；无人类 taste 校准；W 不在本批（见 D）。

## 三、cost 含 C（另算，不改旧件）

新增 C 桶（授权账本 attempt_completed 权威，0 格 unknown_usage，cache=null 记 unknown 未填 0）：
prompt 159,983 / completion 10,783 / **total 170,766**。

计数（在 STATE 已含 B 的 530/521 基础上再加 C 的 24/24）：
**started 554 / completed 545 / failed 5 / dispatch_or_batch_refused 9**；孤儿 4（F1 三 + F3 作废批一，口径不变）。

全口径 token（各桶相加，作废批不进结论）：
裁判+F4生成 1,078,663 ＋ F3/F5生成 1,153,468 ＋ B 探针 163,555 ＋ **C 对照 170,766** ＝ **2,566,452**。
金额 null（无单价依据）。

同批 C 二级（仅此批合法，不得搬 v5 旧数）：base 83,622 vs tier 87,144 total（+4.21%），
completion +7.64%，分层段每请求约 +260 prompt token；时延 base 中位 26.14s / tier 中位 22.23s（−14.9%）
但 p95 43.1s→78.4s（尾部更差）——**n=12 单次、受上游网络主导，不可归因于分层段、不得据此称更快**。

## 四、判决 + 五问（可交口径）

**总体判决：REWORK**（执行完成、验收未完成）。今天可交 = 固定基座下的聊天 Harness 重构原型 + 开发集对照实验，
B/C/D 为带时间戳补充。

- **效果**：r4（e3/guard 系）唯一质量证据是 F3 新材料 72 对：候选 38 胜 / 原版 10 / 平 24，九维配对增益为正；
  正向能力部分成立（邀约），转场/反撩/深聊/收尾无单项验收。B 首个"值确离窗"实测 Use on 11/12 vs off 0/12、
  Update 3/4、Attribution 4/4、污染 1/24。C 对照：assertion_tiering 把候选真实短板（无据确定化 3 位点）在首选位
  收敛、两护栏未回退，方向支持但未证明胜出。
- **可靠**：json_schema_broken 是跨臂共享的输出协议脆弱性、非候选独有；交付分 T/P/C/S/W 五列不得互冒充
  （本批 T24/P24/C24/S=逐格判读/W未知）；cache/usage 缺失记 unknown 不填 0；C 残留缺陷在 directions 槽。
- **等待**：F3 同批完成耗时中位数 +17.35%、p95 +28.22%；C 批中位数虽 −14.9% 但 p95 +81.9%、n=12 单次，
  不可归因；不得给出"更快"结论。
- **费用**：token 全口径 2,566,452（作废批不进），含 C 170,766；**金额 null（无单价）**；
  旧 v5"省 57%"不得算给当前身份。
- **接入**：未接入。产品接入与脚本美化一律排在实测之后；本轮只到"机制级小样本实测 + 判定"。

## 五、下一步（价值排序）

1. C-升级验证：把本次单次改成双顺序复测（同夹具、同 off=删三段口径），并**专门补 directions 槽**的分层验证，
   看残留无据断言能否消除——需新授权，不复活已关单。
2. D-人工评分：`F_D_HUMAN_PAIRS_WORKSHEET.md` 12 对仍未标注，W 保持未知，回收后才能算愿发率，禁止代投/自动分冒充。
3. 素材版本化：E-MAT-B 与 4 个卡面冲突走题库版本化，不为改素材重跑既有生成；尺子提案 RULER-C-01 待用户裁定，生效走离线重评。

禁止：重跑 F0–F6、为补身份全量重抽历史答卷、就地修改冻结候选、复活已关闭账本、产品接入/脚本美化排在实测之前。
