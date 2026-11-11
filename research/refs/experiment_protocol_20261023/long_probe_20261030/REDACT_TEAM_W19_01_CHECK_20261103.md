# REDACT_TEAM_W19_01_CHECK_20261103 — 只读红队复核报告

复核人：红队 agent（只读，除本报告外零写入）。时间：2026-11-03。

## 1. W19-01 修复语义 — PASS
- `arena/isolation/trusted_controller.py` L62-77：`prior_real_hist_n(ep, ti)` 只读评估侧原题 `ep["turns"][:ti]` 的 HER/ME 消息计数，候选回报无通道进入；`prior_hist_id_map` 只发 `m_src[0..n-1]`→"HIST"。接线点 `step_one_turn` L757-767：`canonical_id_map(turn_eval)` 之后 `id_map.update(prior_hist_id_map(ep, i))`，与主 runner 口径一致。
- `tools/test_fk_score01_isolation.py` 实跑 4/4 PASS（正例 m_src[0] 修复后 pass、m_src[999] 仍 veto、逐轮累计一致、伪造回报不改变计数）。
- `arena/lb_runner.py` m_src 段 L334-338 原样（mtime 2026-11-02 01:59，早于今日工作；其 sha256 e540ad1b… 与 W19_CROSS 计划 config_files 冻结值一致）。

## 2. 重评抽样对抗 — PASS
- 全库扫描：三批共 13 个 illegal_source_id 位，逐一用 `benchmark/episodes/*.json` 独立重算 prior_real_n：
  - 清除 3 位全部合法：E183_T03（n=3，m_src[0/1]）、E191_T02（n=2）、E199_T07（n=7，m_src[5]）+ W17b E208_T05（n=5，m_src[3]）→ scoring_v05_fixed veto=null、score10=10，亲读原 scoring.json 确认原 veto 属实。
  - 保留 10 位全部真非法：E164_T04 m2（当轮仅 m0）、E157_T03 json_schema_broken、E189_T04 m4、E195_T06/T11、E189_T02 m2、E190_T03 m1、E207_T02 "1,1"、LRE117_T02 "1"、LRE121_T10 m8。
- W18B unchanged：LRE121_T06（candidate）与 LRE121_T02（parent）json_schema_broken 两侧 scoring 文件亲读一致，320/320 unchanged 声明成立（changed=0，与全库扫描 0 mismatch 互证）。
- 汇总数字核对：三批 144+144+320=608 位、changed=2/2/0，与 aggregate 一致。

## 3. 候选单变量 — PASS
- diff -rq：assets/ 与 uncertain_rules.py 逐字节相同（cmp 通过）；contract_guard.py、_vendor/output_repair.py、_vendor/normalize_reply_contract.py 与 `window_expand_b_norecap_guard` 逐字节相同；守卫接线块与 guard 候选 mechanism.py 逐字一致。
- mechanism.py 相对 uc 的 diff 仅含：头注释、_GUARD 加载、repair_and_audit 调用+审计落盘、notes 追加 guard_changed/guard_rules、返回键 contract_guard。无任何 prompt 构造改动。
- params.json parent=`window_expand_b_norecap_uc`、mechanism_flags.contract_guard_fix=true，符合 HANDOFF C1。

## 4. 计划/卡 — PASS
- 232 卡：case_id 全唯一（232/232），篇计数 6×12+4×40，与 GRADING_CARDS_W17C(72)+GRADING_CARDS_W18(160) 的 case_id 并集精确相等、无缺无多，stage=W19_cross。
- 计划：464 attempts；每篇 6×24 / 4×80；每槽恰 1 parent+1 candidate、无缺位；episode_order arm_order parent_first/candidate_first 严格交替（E164 确认 candidate 在前）。
- 冻结身份：parent 43 文件、candidate 55 文件闭包全部与磁盘 sha256 一致（0 mismatch/0 missing）；identity_sha256 用 `api_trial.harness_identity` 重算逐字吻合（5b3266a9…/aceb7d92…）。config_files 29 个全部与磁盘一致。
- 判据：G1–G5 与 HANDOFF §3 任务 C 跑前判据逐字一致；BATCH_CONFIG（batch_id/arm_map/episode_order/cards_path/recall_capable=false）与 C2 要求逐项吻合。

## 5. 污染检查 — PASS（含一条观察）
- `benchmark/stories_v2` 今日 14:00 后仅新增 GRADING_CARDS_W19_CROSS.json（本批交付物）；三批 608 个原 scoring.json mtime 全部早于 14:00（重评 14:54 只新增 scoring_v05_fixed.json 与 RESCORE_SUMMARY）。benchmark/episodes 今日修改 0。
- **观察（非 FAIL）**：benchmark/episodes 下 8 个文件 mtime 为今日 12:00（E157/E164/E170/E183/E191/E212/LRE138 12:00:17、LRE148 09:11），但逐个与批内运行时快照（`<EP>.copy.json`）及 episode_manifest.integrity.episode_sha256 比对**字节一致**（如 E183 17e43c3c6f…），内容未被改动；成因（同字节重存）未查明，建议后续排查是哪个工具重写了这些文件。

## 结论
五项全 PASS。未发现需要修复的错误；唯一遗留为第 5 条 mtime 观察（内容已证清白）。

报告路径：refs/experiment_protocol_20261023/long_probe_20261030/REDACT_TEAM_W19_01_CHECK_20261103.md
