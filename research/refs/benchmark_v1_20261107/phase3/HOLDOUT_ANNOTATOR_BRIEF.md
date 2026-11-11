# HOLDOUT_ANNOTATOR_BRIEF · 批 3 标注简报（wave6_holdout，H 段，2026-11-09）

> 适用：HOLDOUT 120 篇的 **Gold Annotator（角色 B·H 段）**。标注规范全文沿用
> `phase3/SELECTION_ANNOTATOR_BRIEF.md`（必读），本文件只列 HOLDOUT 差异项。

## 0. H 段隔离（在 SELECTION 简报 §0 之上追加）

- 你是 HOLDOUT 专属会话；**禁读 wave4_dev/、wave5_selection/ 的任何文件**（含 reference/audit）、
  stage/wave 各篇正文、logs/、candidates/、一切审计/判决/对账文档、GOLD_SIDECAR。
- 允许读：你名下篇目的 episode/timeline/exposure（金库内）、SELECTION_ANNOTATOR_BRIEF、
  三份 V1 规范、TOPIC_RUBRIC_V1 你的专题条目。
- ack：`phase3/isolation_acks/GIA-20261109-ANNOTATOR-H<xx>.json`（role=gold_annotator，
  declarations 四项 + holdout_vault=true）。

## 1. 位置与五件套

- 读写都在金库：**`D:/lovebrain-arena_holdout_vault/wave6_holdout/<EID>/`**；
- 你产出：`reference.json`（规则同 SELECTION 简报 §1，**每原子必带 affirm_markers 嵌套组**）
  + `audit.md`（8 节）+ **`runner_episode.json`**（episode.json 的逐字节副本——
  金库篇的 runner_copy 用绝对路径登记进 exposure）；
- T02 篇（E406–E430）：`t02_signal_gate` 九项必填（同 SELECTION 简报 §2）；
- T09 长剧（E505–E512）：状态机原子逐轮登记，日期 quote 全验算；
- 孪生篇（E513–E520）：组内两篇的卡面**逐字段对称**（除扰动位），审计将机械比对。

## 2. 专项密度提醒（120 篇配额下）

- E406–E430 是 25 篇 T02：禁止 25 篇同质化（场景域已预分配互不相同）；
  信号强度梯度、反诱惑位、退路设计逐篇独立设计，不得复制模板句。
- E431–E450 是 20 篇 T03：立场差必须真实（不是假分歧）；交锋位可判。
- E487–E496 是 10 篇 T07：归属陷阱类型轮换（报错数/除数未知/三手转述/口头vs书面/
  预估vs终价/人物归属错误+更正/双版本口径…），每篇至少 2 类。
- E497–E504 是 8 篇 T08：多线程+干扰位，旧事复用位与"不该提位"并存。

## 3. 自查与交付报告

quote strict 回验 0 失败；may_decline 与 episode 一致；T02 门齐全；runner 副本逐字节一致
（`python -c "import json,hashlib;print(hashlib.sha256(open(a,'rb').read()).hexdigest()==hashlib.sha256(open(b,'rb').read()).hexdigest())"`）；
8 节齐全。交付报告同 SELECTION 简报 §5，外加 runner 副本校验行与隔离声明。
