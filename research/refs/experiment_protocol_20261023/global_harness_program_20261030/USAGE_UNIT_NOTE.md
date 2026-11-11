# USAGE_UNIT_NOTE.md — 成本/用量的单位口径说明（2026-11-03）

## 实测事实

对两批已付费批次 `logs/w17c_merge_live_20261103`（n=144 次调用）与
`logs/w18b_ucsupp_live_20261103`（n=320 次调用）逐调用实测
`calls.json` 中 `usage.prompt / (len(system)+len(user))`：

| 批次 | n | p10 | p50 | p90 |
|---|---|---|---|---|
| w17c_merge_live_20261103 | 144 | 0.713 | 0.724 | 0.759 |
| w18b_ucsupp_live_20261103 | 320 | 0.686 | 0.732 | 0.753 |

即比值稳定在 **0.72–0.76**：`usage.prompt` **不是字符数**（否则应为 1.0），
也**没有证据表明是模型 token**——它是上游 API 回报的计量单位（下称 **usage-unit**），
网关同时落盘的 `prompt_chars` 字段才是字符口径。两个口径并存且数值不等。

检查脚本（可随时复测）：`tools/check_usage_unit.py <batch_dir> ...`

## 成本门比较规则（rubric §5 成本双账的操作化）

- **有效（合法）**：同一 batch 内候选臂 vs 父本臂的**同单位配对差**——
  usage-unit vs usage-unit（或 chars vs chars）。比值稳定时，两种单位下的
  配对差结论一致，比较不受单位选择影响。
- **无效（禁止）**：跨单位比较（拿候选的 usage-unit 减父本的字符数）、
  把 `usage.prompt` 直接标成"token"或"字符"、用字符数 ÷ 某系数**猜算** token
  并当作真实 usage 报告。
- **今后落账纪律**：绝对值一律显式标注单位——`usage-unit`（usage.prompt 口径）
  或 `chars`（prompt_chars 口径）；无 usage 的失败调用计失败账，不填 0 冒充
  （rubric §5"不填 0 冒充"既有纪律）。

## 关联文件

- schema 侧同款简注：`benchmark/stories_v2/GOLD_SCHEMA.md` §7；
- 检查脚本：`tools/check_usage_unit.py`；
- 判定权威：`benchmark/rubric.md` §5（评分账/法币账、不填 0 冒充）。
