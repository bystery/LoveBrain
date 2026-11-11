# C 线实验日志（EXPERIMENT_LOG）

格式：每条实验一个条目（批次/授权/入口/规模/三指标结果/判决/分类）。第 0/1/2 级对应宪章 C6。

| # | 日期 | 授权 | 级别 | 入口 | 规模 | 质量 | Token | 端到端时延 | 判决与分类 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P0 | 2026-11-11 | RESEARCH-P1-G0-20261111-01（零模型调用） | 第 0 级静态检查 | prototype_runner plan+mock | smoke_24 × 3 臂 = 72 位，全通；gold 泄漏 grep 零命中 | mock（无真实质量） | kotlin_len（mock） | mock 毫秒级 | 管道/消毒/评分链路验证 PASS；`veto=unknown_payload_field` 为已登记轻量契约假象（universal_scorer 口径 NOT_APPLICABLE） |
| P0-b | 2026-11-11 | AUTH-RP1-SMOKE-20261111-01（2 次调用） | RH-B-11 能力探测 | probe_prefix_cache.py | 同前缀 × 2 次调用 | 不适用（能力探测） | prompt 1,039 tokens/次 | —— | **端点前缀缓存能力存在**（`prompt_tokens_details.cached_tokens` 0→1024）→ RH-B-11 本地可行，进待实测清单 |
| P1 | 2026-11-11 | AUTH-RP1-SMOKE-20261111-01（cap 400，实消耗约 96） | 第 1 级 smoke_24 | prototype_runner live | smoke_24 × rh_b04/rh_b06/rh_b09，72/72 ok 0 error | wave5 同位：b04 4/14、**b06 2/14**、b09 4/14（DIRECT 基线 5/14、COT 5/14、CURRENT 4/14） | 1,289–1,345 tok/位（CURRENT 同位 4,246） | p50 16.7–26.6s（跨批不可比，同批内 b09<b04<b06） | 探索性信号：b06 severe 减半但升级率 45.8% 超预登记（intent/note 触发过敏感）；全部为程序层口径，无自然度判定；详见 FIRST_PROTOTYPE_RESULTS.md |

**首批实现选型说明**（宪章 C5"由研究结论自主确定"）：首批 = RH-B-04（装配排布类）+ RH-B-06（条件计算类）+ RH-B-09（上下文选择类），三类机制互异且都能以独立 v2 候选最小实现。RH-B-03（草稿契约）的正确实现对象是现有复杂契约的 harness（其假设针对内部冗长字段），在轻量契约上实现会构成稻草人——登记为第二批（与 RH-B-01/07 记忆管线同批）；RH-B-11 已由 P0-b 证明本地可行。此排序由实现可行性与机制类别覆盖决定，非固定清单。
