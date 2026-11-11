# BENCHMARK_AND_REVIEW — 题库、划分与人工评审（G05/G06a/G07 证据索引）

日期：2026-10-21。范围见 `SCOPE_AND_STATUS.md`。本文件是索引与规则，
明细证据指向下列 JSON/MD 与生成命令（全部可重跑）。

## 1. 题库与覆盖（G05）

- Episode 库：`benchmark/episodes/`（20 个 JSON，含 dev/ 4 条、archive/ 旧版）。
  媒体：`benchmark/media/pilot/`（4 张，允许公开的开发用图）。
- 覆盖矩阵：`benchmark/releases/arena_v1/coverage_matrix.json`
  生成：`python tools/gen_coverage_matrix.py`
  = **12 能力族 × 36 观察点，全部反查到真实 case_id（全局身份 episode:case），
  0 缺失**；19 个 episode 的字段机械审计（schema/时间/媒体/may_decline/
  gold 键位）**0 issue**。
- 观察点含正/反对照（success / control），记忆更正、编号 token、
  媒体不可见、may_decline、no_reply、ongoing 支持门等族均双面覆盖。
- 自然度：题面文本来自真实聊天风格改写的开发题（NCA/NCB 家族含
  时间/实体密集轮次）；验收不宣称"自然度达标"，只登记覆盖与审查证据。

## 2. 划分、暴露与封存（G06a/G06b）

- split_manifest：`benchmark/releases/arena_v1/split_manifest.json`
  生成：`python tools/gen_split_manifest.py`
  = 5 家族（dev_exposed / core_exposed / archive / selection_set /
  b_line_sealed）× 31 文件，逐文件 sha256。
- 暴露记录：dev_exposed 与 core_exposed 均在开发全程暴露（如实标注
  EXPOSED）；**selection_set.status = NOT_READY**（无独立出题/保管身份，
  外部缺件，见 SCOPE_AND_STATUS §6）。
- 封存接口：`refs/arena_v1/seal_fixture/`（合成 canary
  SYNTH_SEALED_001，三重校验：文件存在 / LOCK==payload 散列 / kind 断言 /
  真实 B 线目录不存在）。校验：`python tools/verify_seal_fixture.py`。
- **真实 B 线内容未在本仓库落盘**；发布包不含密封题不等于缺件。

## 3. 人工评审闭环（G07）

- 工具：`tools/review_loop.py`（gen → ingest → aggregate → lookup）。
- 演练：`refs/arena_v1/review_drill_spec.json` 12 份材料（正常/失败/
  双差/信息不足/不回/缺标签等状态）→ 材料生成 + 绑定散列（原文 sha16）
  → 11 收 1 拒（缺 reviewer 身份被正确拒绝）→ 分层汇总（C6 口径）
  → 缺陷反查到 case。
- 录入表强制 binding_sha256_16 与材料原文绑定；语义维度七项 +
  可发性两臂 + 对比 + 缺陷 field/evidence 结构。
- 统计分母规则：失败/拒绝/解析失败计入分母，不丢弃不重生（与
  failure_persistence 一致）。

## 4. 可重跑命令

```powershell
python tools/gen_coverage_matrix.py        # G05
python tools/gen_split_manifest.py         # G06a 清单
python tools/verify_seal_fixture.py        # G06a 封存三重校验
python tools/review_loop.py gen refs/arena_v1/review_drill_spec.json
python tools/review_loop.py ingest benchmark/releases/arena_v1/review_loop/entries
python tools/review_loop.py aggregate
```
