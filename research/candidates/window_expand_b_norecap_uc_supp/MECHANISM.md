# MECHANISM — window_expand_b_norecap_uc_supp

四决策（相对父本 window_expand_b_norecap_uc 的**唯一变异 = 组合两段**：
在 uc 的 U 未定段之外，恢复 b2 形状的 S 压制段，并加 W18 渲染护栏）。

## 存储
同父本：`events` 有序真实消息 `{uid, role, text, ts, turn}`；模型回复不写入 events。
不新增快照字段（不做 suppressions 缓存），`snapshot/restore` 与 uc 逐字节同构。
S/U 均为纯函数（`mechanism/suppression_rules.py`、`mechanism/uncertain_rules.py`），零模型调用。

## 检索
同父本：历史整条消息按 UTF-16 预算 3600 CU 从新到旧保留，不改历史裁剪。
S 段只扫 `prior_events`（本轮消息已从历史剔除；当轮更正已在当前输入逐字可见，次轮才入段）。

## 意图
同父本：沿用共享 `build_intent_block`，本轮 intent 缺省/关闭即空，不留旧目标。
S/U 段不读 intent、note，不改变其顺序与内容。

## 上下文组织（唯一变异点：组合两段）
装配顺序（设计 §2.2 逐字固定）：画像/seed → **S 段**（仅非空）→ intent → note →
历史真实消息 → **U 段**（仅非空）→ 本轮输入 → clock。

S 段（被取代/已作废口径，≤240 CU，标题逐字 `# 【口径基线：以下旧说法已作废，勿再沿用】`）：

```
- 「<旧 clause>」已作废（T<旧事实轮>改）→ 现行：「<新 clause>」      # update_pass supersede
- 「<旧 clause>」已作废（T<旧事实轮>改）→ 现行：（已取消）          # update_pass cancel
- 已声明作废：「<clause>」（T<turn>）                              # 取消句
- 现行口径以「<clause>」（T<turn>）为准                            # 回归句
```

- 检测复用 `ledger_rules.extract_facts + update_pass`（b2 的 E1–E4 副本，逐字不动）；
- **R-W18-1 口径锚点门（只作用于 c 类行）**：clause 必须含 strong_values（数字/日期/时段口径：
  `\d{2,}`、≥2 中文数字、数字+单位、`NUM点`+时点词，或 TOD 时段的 `NUM点` 兜底），
  否则不入段——排除"变回一个人""反手连练两周""六人满编""顺一点"等非口径用法；
- **R-W18-2 同锚点去重**：strong_values 相交时丢更旧行，严格更旧才丢（同一轮内都保留）；
- **R-W18-3**：T 标注、排序、去重用真实 `turn`（不用 b2 的 uid+1）；
- **R-W18-4**：含标题 ≤240 CU，从新到旧装入、放不下即停，展示旧→新；
- 旧/新文本含 `如果|要是|还不确定|再看` 一律不入段（沿用 b2，防坐实未定）。

U 段（`uncertain_rules.py` 与 uc 逐字相同，≤120 CU）：A hedge / B 未确认问句 / C 引号转述，
未被后续确认关闭才算；≤3 条、逐字原文。

跨段去重与总预算（设计 §2.5）：
- S 行内「」clause 与 U 条目做标点归一化包含比较；同一 clause 同时出现时保留 S、丢弃 U；
- S ≤240 + U ≤120 = **≤360 CU（硬上界）**；notes 记 `supp_cu/supp_pairs/uc_cu/uc_items/
  xdedup/comb_cu/comb_overflow`；
- 无任何信号：两段都不出现 → prompt 与父本 uc 逐字节相同（`check_prompt_equivalence.py` 可验）。

## 边界
零模型调用；不读网络；不新增工具白名单；不改输出契约；不改 system prompt 资产。
