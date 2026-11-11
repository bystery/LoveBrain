# LITERATURE_WATCHDOG — 外部论文检索合规看门狗（2026-11-11 用户强制要求）

> **文件地位**：RESEARCH-P1 执行宪章的强制合规层。AGENTS.md §-1②"每一轮必须真实外部检索论文或博客"的可执行落地。
> 违反此文件的任何 LOOP，其判决**自动作废**，不计入机制保留/淘汰证据，不进 SCOREBOARD。
> 用户原话："将看门狗里面加入关于没有读取外部论文的惩罚"——本文件就是那个看门狗。

---

## 一、为什么需要看门狗

通宵循环（LOOP-1..LOOP-9）实证暴露的违规：

| LOOP | 外部检索? | 实际做了什么 | 违规? |
|------|----------|-------------|------|
| LOOP-1 | ❌ 无 | 读 smoke_24 结果 + 亲读回复 | **违规** |
| LOOP-2 | ❌ 无 | b11 缓存 NULL + 计量修复 | **违规** |
| LOOP-3 | ⚠️ 委派 A 线子代理 | EXPAND 60 位 + 等待 A/B 线 | 灰色（委派非自己做） |
| LOOP-3.5 | ❌ 无 | 评分器修复 | **违规**（属工具修复，但按核心法该轮也应有检索） |
| LOOP-4 | ❌ 无 | b15 smoke | **违规** |
| LOOP-5 | ❌ 无 | DIRECT 基线补全 | **违规** |
| LOOP-6 | ⚠️ 引用 A 线已有 Hybrid LLM 精读 | b15b 选择器 | 灰色（用了已有文献，非本轮新检索） |
| LOOP-7 | ❌ 无 | b12 失效登记 | **违规** |
| LOOP-8 | ✅ A2 文献冲刺 14 篇 | b17 + pipe_v1 规格 | 合规 |
| LOOP-9 | ⚠️ 用 A2 已有文献 | pipe_v1 实现 | 灰色（本轮无新检索） |

**9 轮中 5 轮明确违规、2 轮灰色、仅 1 轮合规**——这是对核心法 §-1② 的系统性违反。

---

## 二、合规定义（什么算"本轮做了外部检索"）

### 每轮最低要求（三条全部满足才算合规）

1. **至少 3 次真实 WebSearch/WebFetch 调用**，关键词与本轮的失败归因或设计假设直接相关（不是泛搜"LLM memory"然后登记完事）。
2. **至少 1 篇新论文登记**（不在此前已登记的论文清单中），含：标题/作者/年/arXiv ID/阅读分级（精读/摘要级/检索级）/与本轮失败或设计的直接关联一句。
3. **在 LIVE_LOOP_LOG 该 LOOP 段落中显式引用至少 1 篇论文的机制或证据**（"依据 XXX 论文的 YYY 机制"——不是只挂在 LITERATURE_DELTA 里不动）。

### 撞墙时（P1-03 触发）的额外要求

撞墙 = 同方向连续两次无可信改善 / 结构性限制 / 质量升但成本无降路径 / 新失败模式只能叠例外。

撞墙时本轮额外要求（P1-03 已有，看门狗重申并加惩罚）：

4. **至少精读 5 篇与本瓶颈直接相关的新论文**（精读 = 方法+实验节全文，不是摘要页）。
5. **至少提出 2 条核心机制不同的替代方向**（不是同一机制的参数调整）。
6. 旧五篇不得重复登记冒充新检索（P1-03 原文）。

### 合规凭证落盘位置

- 论文登记：`refs/research_harness_20261110/overnight/LITERATURE_DELTA.md`（追加新节，标注轮次）
- 或独立文件：`refs/research_harness_20261110/phase1/literature/loop_N_search.md`
- LIVE_LOOP_LOG 该 LOOP 段落中显式引用

---

## 三、惩罚（用户强制要求）

### 惩罚 1：LOOP 判决作废

任何 LOOP 未满足§二第 1-3 条（每轮最低要求），其 KEEP/EXPAND/REWORK/PIVOT/ELIMINATE/UNCERTAIN 判决**自动标记为 INVALIDATED_BY_WATCHDOG**。

- INVALIDATED 判决不计入 CANDIDATE_SCOREBOARD.csv 的最终保留/淘汰依据。
- 候选不因违规 LOOP 的"KEEP"而保留——必须有至少 1 个合规 LOOP 的判决背书。
- 已靠违规 LOOP 保留的候选（b04/b09/b12/b17），其保留状态降级为 **PROVISIONAL**，须在下一个合规 LOOP 中复验。

### 惩罚 2：强制补检索

违规 LOOP 必须在下一个 LOOP 开始前补做外部检索（§二第 1-3 条），并在 LIVE_LOOP_LOG 中追加 `## LOOP-N (补检索)` 段落。不补不得开始新 LOOP。

### 惩罚 3：连续违规升级

- 连续 2 个 LOOP 违规 → 当前会话所有候选判决降级为 PROVISIONAL，须全部在合规 LOOP 复验。
- 连续 3 个 LOOP 违规 → 触发 P1-03 撞墙流程（即使没有技术撞墙，检索缺失本身就是方法论撞墙），强制精读 5 篇 + 2 条替代方向。

### 惩罚 4：灰色轮降级

"灰色"（委派子代理 / 用已有文献但本轮无新检索）降级为违规，按§三惩罚 1-2 执行。

**理由**：核心法原文是"每一轮必须真实外部检索"——不是"每轮必须有子代理在检索"、也不是"每轮要有论文可引用"。主 Worker 本人本轮做了检索才算合规。委派子代理是补充，不替代主 Worker 的检索义务。

---

## 四、检查器

确定性检查脚本：`tools/literature_watchdog_check.py`

运行方式：
```bash
python -I tools/literature_watchdog_check.py
```

检查逻辑：
1. 解析 `LIVE_LOOP_LOG.md`，提取每个 `## LOOP-N` 段落。
2. 解析 `LITERATURE_DELTA.md`（及 `phase1/literature/loop_*.md`），提取论文登记及其标注的轮次。
3. 对每个 LOOP，检查：
   - 是否有 ≥3 次 WebSearch/WebFetch 日志条目关联该轮（关键词与 LOOP 失败归因相关）。
   - 是否有 ≥1 篇新论文（不在此前轮次已登记集合中）。
   - LOOP 段落正文是否引用 ≥1 篇论文（关键词匹配：论文标题/arXiv ID/作者名）。
4. 输出每个 LOOP 的合规状态（PASS / FAIL / GREY→FAIL）+ 违规惩罚触发情况。
5. 输出 `WATCHDOG_VERDICT.json`（机读，供 RUNBOOK 引用）。

检查器不替代人判断论文与失败的相关性（那需要人读），但"有没有搜、有没有新论文、有没有在 LOOP 里引用"是确定性可查的。

---

## 五、追溯执行（本轮立即生效）

通宵循环已发生的违规，按§三惩罚 1 处理：

- LOOP-1/2/4/5/7 的判决标记 INVALIDATED_BY_WATCHDOG。
- b04 KEEP（靠 LOOP-3 EXPAND → LOOP-1/5 违规）→ 降级 PROVISIONAL。
- b09 KEEP（靠 LOOP-3 EXPAND → LOOP-1/5 违规）→ 降级 PROVISIONAL。
- b12 UNCERTAIN（LOOP-7 违规）→ INVALIDATED。
- b17 KEEP 候选（LOOP-8 合规）→ 有效保留。
- b15/b15b KEEP（靠 LOOP-4/6 灰色 → 降级违规）→ PROVISIONAL。
- pipe_v1（LOOP-9 灰色 → 降级违规，且 B5 审计子代理因 quota 失败未返回）→ PROVISIONAL + 质量未判定。

**补检索义务**：下一个 LOOP（LOOP-10）开始前，主 Worker 必须先补做至少 3 次 WebSearch + 1 篇新论文登记，覆盖"记忆/状态管线质量验证方法"与"多候选选择器天花板"两个方向（对应当前两个 PROVISIONAL 候选的复验需求）。

---

## 六、与 AGENTS.md 的关系

本文件是 AGENTS.md §-1② 的执行细则。冲突时以 AGENTS.md §-1 为准，本文件不降低 §-1 的要求，只细化执行和惩罚。
