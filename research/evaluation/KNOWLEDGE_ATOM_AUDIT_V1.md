# 知识原子命中审计 V1（S3 注入侧）

每行=某臂在某位点、某类区段里，该原子以 entry 级匹配命中的证据。
全部为机器判定；fuzzy 命中在人工复核前不得进对外结论。

| 臂 | 原子 | 说话人 | 位点 | 区段类别 | 区段 | 覆盖率 | 共享三元组 | 回指 | 命中条目 |
|---|---|---|---:|---|---|---|---:|---|---|
| evidence_memory_v1 | E006_T01#m0 | HER | T02 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T01#m0 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T01#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T01#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T01#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T01#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T01#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T02#m0 | ME | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T02#m0 | ME | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T02#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T02#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T02#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T02#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T02#m1 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T02#m1 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T02#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T02#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T02#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T02#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T03#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 11 | 是 | m_src[3] ／ 2026-11-06 22:33 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T03#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 11 | 是 | m_src[3] ／ 2026-11-06 22:33 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T03#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 11 | 是 | m_src[3] ／ 2026-11-06 22:33 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T03#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 11 | 是 | m_src[3] ／ 2026-11-06 22:33 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T03#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 11 | 是 | m_src[3] ／ 2026-11-06 22:33 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T04#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 6 | 是 | m_src[4] ／ 2026-11-07 12:35 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T04#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 6 | 是 | m_src[4] ／ 2026-11-07 12:35 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T04#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 6 | 是 | m_src[4] ／ 2026-11-07 12:35 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T04#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 6 | 是 | m_src[4] ／ 2026-11-07 12:35 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T04#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.789 | 14 | 是 | m_src[5] ／ 2026-11-07 12:38 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T04#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.789 | 14 | 是 | m_src[5] ／ 2026-11-07 12:38 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T04#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.789 | 14 | 是 | m_src[5] ／ 2026-11-07 12:38 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T04#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.789 | 14 | 是 | m_src[5] ／ 2026-11-07 12:38 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T05#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.913 | 15 | 是 | m_src[6] ／ 2026-11-07 13:08 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T05#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.913 | 15 | 是 | m_src[6] ／ 2026-11-07 13:08 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T05#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.913 | 15 | 是 | m_src[6] ／ 2026-11-07 13:08 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T06#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.8 | 15 | 是 | m_src[7] ／ 2026-11-07 13:22 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T06#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.8 | 15 | 是 | m_src[7] ／ 2026-11-07 13:22 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T06#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 17 | 是 | m_src[8] ／ 2026-11-07 13:28 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T06#m1 | HER | T07 | structured | 【暂定事项(未敲定，别当已约定催办)】 | 0.435 | 10 | 是 | · 她在考虑这次要不要跟领导掰扯一下（提出于2026-11-07 13:30） |
| evidence_memory_v1 | E006_T06#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 17 | 是 | m_src[8] ／ 2026-11-07 13:28 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E006_T06#m1 | HER | T08 | structured | 【暂定事项(未敲定，别当已约定催办)】 | 0.435 | 10 | 是 | · 她在考虑这次要不要跟领导掰扯一下（提出于2026-11-07 13:30） |
| evidence_memory_v1 | E006_T07#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 12 | 是 | m_src[9] ／ 2026-11-07 13:45 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T01#m0 | HER | T02 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T01#m0 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T01#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T01#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T01#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T01#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T01#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T02#m0 | ME | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T02#m0 | ME | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T02#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T02#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T02#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T02#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T02#m1 | HER | T03 | intent | 【当前目标(仅本轮有效，可指导行动)】 | 0.304 | 7 | 是 | 想约她看一场不太吵的 livehouse，主动一点、把话头接下来，但别把话说成已定死 |
| evidence_memory_v1 | E007_T02#m1 | HER | T03 | other | 【已确认事实/约定(带出处，可回扣；窗口外旧 | 0.391 | 8 | 是 | · 她愿意出来，但只想看livehouse，太吵的受不了（src=m_src[2] @2026-11-09 20:26） |
| evidence_memory_v1 | E007_T02#m1 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T02#m1 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T02#m1 | HER | T05 | other | 本轮输入(最高优先，整条不被裁；id=来源I | 0.304 | 7 | 是 | {"id": "m0", "source_id": 6, "speaker": "PARTNER", "ts": "2026-11-10 1 |
| evidence_memory_v1 | E007_T02#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T02#m1 | HER | T06 | intent | 【当前目标(仅本轮有效，可指导行动)】 | 0.304 | 7 | 是 | 她确认周六去 livehouse |
| evidence_memory_v1 | E007_T02#m1 | HER | T06 | other | 【已确认事实/约定(带出处，可回扣；窗口外旧 | 0.391 | 8 | 是 | · 她愿意出来，但只想看livehouse，太吵的受不了（src=m_src[2] @2026-11-09 20:26） |
| evidence_memory_v1 | E007_T02#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T02#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T02#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T03#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 7 | 是 | m_src[3] ／ 2026-11-09 20:38 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T03#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 7 | 是 | m_src[3] ／ 2026-11-09 20:38 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T03#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 7 | 是 | m_src[3] ／ 2026-11-09 20:38 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T03#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 7 | 是 | m_src[3] ／ 2026-11-09 20:38 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T03#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 7 | 是 | m_src[3] ／ 2026-11-09 20:38 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T04#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.583 | 5 | 是 | m_src[4] ／ 2026-11-10 12:00 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T04#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.583 | 5 | 是 | m_src[4] ／ 2026-11-10 12:00 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T04#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.583 | 5 | 是 | m_src[4] ／ 2026-11-10 12:00 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T04#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.583 | 5 | 是 | m_src[4] ／ 2026-11-10 12:00 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T04#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 8 | 是 | m_src[5] ／ 2026-11-10 12:08 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T04#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 8 | 是 | m_src[5] ／ 2026-11-10 12:08 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T04#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 8 | 是 | m_src[5] ／ 2026-11-10 12:08 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T04#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 8 | 是 | m_src[5] ／ 2026-11-10 12:08 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T05#m0 | HER | T06 | intent | 【当前目标(仅本轮有效，可指导行动)】 | 0.35 | 7 | 是 | 她确认周六去 livehouse |
| evidence_memory_v1 | E007_T05#m0 | HER | T06 | other | 【已确认事实/约定(带出处，可回扣；窗口外旧 | 0.35 | 7 | 是 | · 她愿意出来，但只想看livehouse，太吵的受不了（src=m_src[2] @2026-11-09 20:26） |
| evidence_memory_v1 | E007_T05#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 18 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T05#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 18 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T05#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 18 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T06#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.7 | 11 | 是 | m_src[7] ／ 2026-11-12 21:28 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T06#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.7 | 11 | 是 | m_src[7] ／ 2026-11-12 21:28 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E007_T07#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.7 | 6 | 是 | m_src[8] ／ 2026-11-12 21:40 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T01#m0 | HER | T02 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T01#m0 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T01#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T01#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T01#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T01#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T01#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T02#m0 | ME | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T02#m0 | ME | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T02#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T02#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T02#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T02#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T02#m1 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T02#m1 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T02#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T02#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T02#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T02#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T03#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 16 | 是 | m_src[3] ／ 2026-11-10 12:38 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T03#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 16 | 是 | m_src[3] ／ 2026-11-10 12:38 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T03#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 16 | 是 | m_src[3] ／ 2026-11-10 12:38 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T03#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 16 | 是 | m_src[3] ／ 2026-11-10 12:38 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T03#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 16 | 是 | m_src[3] ／ 2026-11-10 12:38 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T04#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.75 | 8 | 是 | m_src[4] ／ 2026-11-10 12:50 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T04#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.75 | 8 | 是 | m_src[4] ／ 2026-11-10 12:50 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T04#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.75 | 8 | 是 | m_src[4] ／ 2026-11-10 12:50 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T04#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.75 | 8 | 是 | m_src[4] ／ 2026-11-10 12:50 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T04#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 11 | 是 | m_src[5] ／ 2026-11-10 12:53 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T04#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 11 | 是 | m_src[5] ／ 2026-11-10 12:53 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T04#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 11 | 是 | m_src[5] ／ 2026-11-10 12:53 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T04#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 11 | 是 | m_src[5] ／ 2026-11-10 12:53 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T05#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 14 | 是 | m_src[6] ／ 2026-11-11 20:28 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T05#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 14 | 是 | m_src[6] ／ 2026-11-11 20:28 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T05#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 14 | 是 | m_src[6] ／ 2026-11-11 20:28 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T06#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.733 | 11 | 是 | m_src[7] ／ 2026-11-11 20:40 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T06#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.733 | 11 | 是 | m_src[7] ／ 2026-11-11 20:40 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T06#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.538 | 6 | 是 | m_src[8] ／ 2026-11-11 20:43 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T06#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.538 | 6 | 是 | m_src[8] ／ 2026-11-11 20:43 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E008_T07#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[9] ／ 2026-11-11 20:58 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T01#m0 | HER | T02 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T01#m0 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T01#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T01#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T01#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T01#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T01#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T02#m0 | ME | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T02#m0 | ME | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T02#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T02#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T02#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T02#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T02#m1 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T02#m1 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T02#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T02#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T02#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T02#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T03#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 14 | 是 | m_src[3] ／ 2026-11-09 20:58 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T03#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 14 | 是 | m_src[3] ／ 2026-11-09 20:58 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T03#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 14 | 是 | m_src[3] ／ 2026-11-09 20:58 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T03#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 14 | 是 | m_src[3] ／ 2026-11-09 20:58 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T03#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 14 | 是 | m_src[3] ／ 2026-11-09 20:58 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T04#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 6 | 是 | m_src[4] ／ 2026-11-09 21:08 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T04#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 6 | 是 | m_src[4] ／ 2026-11-09 21:08 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T04#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 6 | 是 | m_src[4] ／ 2026-11-09 21:08 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T04#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 6 | 是 | m_src[4] ／ 2026-11-09 21:08 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T04#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 10 | 是 | m_src[5] ／ 2026-11-09 21:10 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T04#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 10 | 是 | m_src[5] ／ 2026-11-09 21:10 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T04#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 10 | 是 | m_src[5] ／ 2026-11-09 21:10 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T04#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 10 | 是 | m_src[5] ／ 2026-11-09 21:10 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T05#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.727 | 9 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T05#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.727 | 9 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T05#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.727 | 9 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T06#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 15 | 是 | m_src[7] ／ 2026-11-11 19:58 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T06#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 15 | 是 | m_src[7] ／ 2026-11-11 19:58 ／ PARTNER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T07#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 5 | 是 | m_src[8] ／ 2026-11-11 20:10 ／ USER ／ <real-message-redacted> |
| evidence_memory_v1 | E012_T07#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.769 | 10 | 是 | m_src[9] ／ 2026-11-11 20:13 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T01#m0 | HER | T02 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T01#m0 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T01#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T01#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T01#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T01#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T01#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T02#m0 | ME | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E006_T02#m0 | ME | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E006_T02#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E006_T02#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E006_T02#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E006_T02#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E006_T02#m1 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T02#m1 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T02#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T02#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T02#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T02#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T03#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 11 | 是 | m_src[3] ／ 2026-11-06 22:33 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T03#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 11 | 是 | m_src[3] ／ 2026-11-06 22:33 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T03#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 11 | 是 | m_src[3] ／ 2026-11-06 22:33 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T03#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 11 | 是 | m_src[3] ／ 2026-11-06 22:33 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T03#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 11 | 是 | m_src[3] ／ 2026-11-06 22:33 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T04#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 6 | 是 | m_src[4] ／ 2026-11-07 12:35 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E006_T04#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 6 | 是 | m_src[4] ／ 2026-11-07 12:35 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E006_T04#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 6 | 是 | m_src[4] ／ 2026-11-07 12:35 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E006_T04#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 6 | 是 | m_src[4] ／ 2026-11-07 12:35 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E006_T04#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.789 | 14 | 是 | m_src[5] ／ 2026-11-07 12:38 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T04#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.789 | 14 | 是 | m_src[5] ／ 2026-11-07 12:38 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T04#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.789 | 14 | 是 | m_src[5] ／ 2026-11-07 12:38 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T04#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.789 | 14 | 是 | m_src[5] ／ 2026-11-07 12:38 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T05#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.913 | 15 | 是 | m_src[6] ／ 2026-11-07 13:08 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T05#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.913 | 15 | 是 | m_src[6] ／ 2026-11-07 13:08 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T05#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.913 | 15 | 是 | m_src[6] ／ 2026-11-07 13:08 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T06#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.8 | 15 | 是 | m_src[7] ／ 2026-11-07 13:22 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E006_T06#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.8 | 15 | 是 | m_src[7] ／ 2026-11-07 13:22 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E006_T06#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 17 | 是 | m_src[8] ／ 2026-11-07 13:28 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T06#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 17 | 是 | m_src[8] ／ 2026-11-07 13:28 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E006_T07#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 12 | 是 | m_src[9] ／ 2026-11-07 13:45 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T01#m0 | HER | T02 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T01#m0 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T01#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T01#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T01#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T01#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T01#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T02#m0 | ME | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E007_T02#m0 | ME | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E007_T02#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E007_T02#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E007_T02#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E007_T02#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E007_T02#m1 | HER | T03 | intent | 【当前目标(仅本轮有效，可指导行动)】 | 0.304 | 7 | 是 | 想约她看一场不太吵的 livehouse，主动一点、把话头接下来，但别把话说成已定死 |
| goal_action_v1 | E007_T02#m1 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T02#m1 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T02#m1 | HER | T05 | other | 本轮输入(最高优先，整条不被裁；id=来源I | 0.304 | 7 | 是 | {"id": "m0", "source_id": 6, "speaker": "PARTNER", "ts": "2026-11-10 1 |
| goal_action_v1 | E007_T02#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T02#m1 | HER | T06 | intent | 【当前目标(仅本轮有效，可指导行动)】 | 0.304 | 7 | 是 | 她确认周六去 livehouse |
| goal_action_v1 | E007_T02#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T02#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T02#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T03#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 7 | 是 | m_src[3] ／ 2026-11-09 20:38 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T03#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 7 | 是 | m_src[3] ／ 2026-11-09 20:38 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T03#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 7 | 是 | m_src[3] ／ 2026-11-09 20:38 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T03#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 7 | 是 | m_src[3] ／ 2026-11-09 20:38 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T03#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 7 | 是 | m_src[3] ／ 2026-11-09 20:38 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T04#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.583 | 5 | 是 | m_src[4] ／ 2026-11-10 12:00 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E007_T04#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.583 | 5 | 是 | m_src[4] ／ 2026-11-10 12:00 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E007_T04#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.583 | 5 | 是 | m_src[4] ／ 2026-11-10 12:00 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E007_T04#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.583 | 5 | 是 | m_src[4] ／ 2026-11-10 12:00 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E007_T04#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 8 | 是 | m_src[5] ／ 2026-11-10 12:08 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T04#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 8 | 是 | m_src[5] ／ 2026-11-10 12:08 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T04#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 8 | 是 | m_src[5] ／ 2026-11-10 12:08 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T04#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 8 | 是 | m_src[5] ／ 2026-11-10 12:08 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T05#m0 | HER | T06 | intent | 【当前目标(仅本轮有效，可指导行动)】 | 0.35 | 7 | 是 | 她确认周六去 livehouse |
| goal_action_v1 | E007_T05#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 18 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T05#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 18 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T05#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 18 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T06#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.7 | 11 | 是 | m_src[7] ／ 2026-11-12 21:28 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T06#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.7 | 11 | 是 | m_src[7] ／ 2026-11-12 21:28 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E007_T07#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.7 | 6 | 是 | m_src[8] ／ 2026-11-12 21:40 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E008_T01#m0 | HER | T02 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T01#m0 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T01#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T01#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T01#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T01#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T01#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T02#m0 | ME | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E008_T02#m0 | ME | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E008_T02#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E008_T02#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E008_T02#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E008_T02#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E008_T02#m1 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T02#m1 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T02#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T02#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T02#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T02#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T03#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 16 | 是 | m_src[3] ／ 2026-11-10 12:38 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T03#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 16 | 是 | m_src[3] ／ 2026-11-10 12:38 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T03#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 16 | 是 | m_src[3] ／ 2026-11-10 12:38 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T03#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 16 | 是 | m_src[3] ／ 2026-11-10 12:38 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T03#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 16 | 是 | m_src[3] ／ 2026-11-10 12:38 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T04#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.75 | 8 | 是 | m_src[4] ／ 2026-11-10 12:50 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E008_T04#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.75 | 8 | 是 | m_src[4] ／ 2026-11-10 12:50 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E008_T04#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.75 | 8 | 是 | m_src[4] ／ 2026-11-10 12:50 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E008_T04#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.75 | 8 | 是 | m_src[4] ／ 2026-11-10 12:50 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E008_T04#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 11 | 是 | m_src[5] ／ 2026-11-10 12:53 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T04#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 11 | 是 | m_src[5] ／ 2026-11-10 12:53 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T04#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 11 | 是 | m_src[5] ／ 2026-11-10 12:53 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T04#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 11 | 是 | m_src[5] ／ 2026-11-10 12:53 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T05#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 14 | 是 | m_src[6] ／ 2026-11-11 20:28 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T05#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 14 | 是 | m_src[6] ／ 2026-11-11 20:28 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T05#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 14 | 是 | m_src[6] ／ 2026-11-11 20:28 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T06#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.733 | 11 | 是 | m_src[7] ／ 2026-11-11 20:40 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E008_T06#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.733 | 11 | 是 | m_src[7] ／ 2026-11-11 20:40 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E008_T06#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.538 | 6 | 是 | m_src[8] ／ 2026-11-11 20:43 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T06#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.538 | 6 | 是 | m_src[8] ／ 2026-11-11 20:43 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E008_T07#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[9] ／ 2026-11-11 20:58 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T01#m0 | HER | T02 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T01#m0 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T01#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T01#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T01#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T01#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T01#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T02#m0 | ME | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E012_T02#m0 | ME | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E012_T02#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E012_T02#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E012_T02#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E012_T02#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E012_T02#m1 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T02#m1 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T02#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T02#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T02#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T02#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T03#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 14 | 是 | m_src[3] ／ 2026-11-09 20:58 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T03#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 14 | 是 | m_src[3] ／ 2026-11-09 20:58 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T03#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 14 | 是 | m_src[3] ／ 2026-11-09 20:58 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T03#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 14 | 是 | m_src[3] ／ 2026-11-09 20:58 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T03#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 14 | 是 | m_src[3] ／ 2026-11-09 20:58 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T04#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 6 | 是 | m_src[4] ／ 2026-11-09 21:08 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E012_T04#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 6 | 是 | m_src[4] ／ 2026-11-09 21:08 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E012_T04#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 6 | 是 | m_src[4] ／ 2026-11-09 21:08 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E012_T04#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 6 | 是 | m_src[4] ／ 2026-11-09 21:08 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E012_T04#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 10 | 是 | m_src[5] ／ 2026-11-09 21:10 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T04#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 10 | 是 | m_src[5] ／ 2026-11-09 21:10 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T04#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 10 | 是 | m_src[5] ／ 2026-11-09 21:10 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T04#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 10 | 是 | m_src[5] ／ 2026-11-09 21:10 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T05#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.727 | 9 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T05#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.727 | 9 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T05#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.727 | 9 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T06#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 15 | 是 | m_src[7] ／ 2026-11-11 19:58 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T06#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 15 | 是 | m_src[7] ／ 2026-11-11 19:58 ／ PARTNER ／ <real-message-redacted> |
| goal_action_v1 | E012_T07#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 5 | 是 | m_src[8] ／ 2026-11-11 20:10 ／ USER ／ <real-message-redacted> |
| goal_action_v1 | E012_T07#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.769 | 10 | 是 | m_src[9] ／ 2026-11-11 20:13 ／ PARTNER ／ <real-message-redacted> |
| reference_product_v1 | E006_T01#m0 | HER | T02 | raw | 最近对话 | 0.818 | 15 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E006_T01#m0 | HER | T02 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.318 | 7 | 是 | - [不到1小时前] 她方案被甲方打回：她今天方案被甲方打回，已经改了三版 |
| reference_product_v1 | E006_T01#m0 | HER | T03 | raw | 最近对话 | 0.818 | 15 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E006_T01#m0 | HER | T03 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.318 | 7 | 是 | - [不到1小时前] 她方案被甲方打回：她今天方案被甲方打回，已经改了三版 |
| reference_product_v1 | E006_T02#m0 | ME | T03 | raw | 最近对话 | 0.905 | 14 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E006_T02#m0 | ME | T04 | raw | 最近对话 | 0.905 | 14 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E006_T02#m1 | HER | T03 | raw | 最近对话 | 0.778 | 10 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E006_T02#m1 | HER | T04 | raw | 最近对话 | 0.778 | 10 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E006_T03#m0 | HER | T04 | raw | 最近对话 | 0.9 | 11 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E006_T04#m0 | ME | T05 | raw | 最近对话 | 0.6 | 6 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E006_T04#m1 | HER | T05 | raw | 最近对话 | 0.789 | 14 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E006_T05#m0 | HER | T06 | raw | 最近对话 | 0.913 | 15 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E006_T05#m0 | HER | T07 | raw | 最近对话 | 0.913 | 15 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E006_T06#m0 | ME | T07 | raw | 最近对话 | 0.8 | 15 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E006_T06#m0 | ME | T08 | raw | 最近对话 | 0.8 | 15 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E006_T06#m1 | HER | T07 | raw | 最近对话 | 0.826 | 17 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E006_T06#m1 | HER | T07 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.435 | 10 | 是 | - [不到1小时前] 纠结要不要跟领导掰扯：她说自己越想越不敢动 |
| reference_product_v1 | E006_T06#m1 | HER | T08 | raw | 最近对话 | 0.826 | 17 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E006_T06#m1 | HER | T08 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.435 | 10 | 是 | - [不到1小时前] 纠结要不要跟领导掰扯：她说自己越想越不敢动 |
| reference_product_v1 | E006_T07#m0 | HER | T08 | raw | 最近对话 | 0.857 | 12 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E006_T07#m0 | HER | T08 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.381 | 7 | 是 | - [不到1小时前] 没经历过却特别有感觉的事：她今天13:48问我有没有没经历过却特别有感觉的事 |
| reference_product_v1 | E007_T01#m0 | HER | T02 | raw | 最近对话 | 1.0 | 9 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E007_T01#m0 | HER | T02 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.333 | 3 | 是 | - [不到1小时前] 加班到想离家出走：她今天20:15还在抱怨最近加班太多，说想离家出走 |
| reference_product_v1 | E007_T01#m0 | HER | T03 | raw | 最近对话 | 1.0 | 9 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E007_T01#m0 | HER | T03 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.333 | 3 | 是 | - [不到1小时前] 加班到想离家出走：她今天20:15还在抱怨最近加班太多，说想离家出走 |
| reference_product_v1 | E007_T02#m0 | ME | T03 | raw | 最近对话 | 0.833 | 10 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E007_T02#m0 | ME | T03 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.417 | 5 | 是 | - [不到1小时前] 约看livehouse：我提议周末出来透透气 |
| reference_product_v1 | E007_T02#m0 | ME | T04 | raw | 最近对话 | 0.833 | 10 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E007_T02#m1 | HER | T03 | other | 【持续意图】 | 0.304 | 7 | 是 | 想约她看一场不太吵的 livehouse，主动一点、把话头接下来，但别把话说成已定死 |
| reference_product_v1 | E007_T02#m1 | HER | T03 | raw | 最近对话 | 0.826 | 14 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E007_T02#m1 | HER | T03 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.348 | 7 | 是 | 她今天20:26说想去，但只能看livehouse，吵的受不了 |
| reference_product_v1 | E007_T02#m1 | HER | T04 | raw | 最近对话 | 0.826 | 14 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E007_T02#m1 | HER | T04 | structured | 【此刻】场景上下文（仅供参考，以本次对话为准 | 0.304 | 7 | 是 | 当前话题：约看livehouse（⚠️ 此信息来自15小时前，可能已过时） |
| reference_product_v1 | E007_T02#m1 | HER | T05 | structured | 本次对话记录 | 0.304 | 7 | 是 | {"id": "m0", "speaker": "PARTNER", "text": "<real-message-redacted>"} |
| reference_product_v1 | E007_T02#m1 | HER | T06 | other | 【持续意图】 | 0.304 | 7 | 是 | 她确认周六去 livehouse |
| reference_product_v1 | E007_T02#m1 | HER | T06 | raw | 最近对话 | 0.304 | 7 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E007_T02#m1 | HER | T07 | raw | 最近对话 | 0.304 | 7 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E007_T02#m1 | HER | T07 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.348 | 8 | 是 | - [不到1小时前] 周六livehouse之约：她2026-11-12确认周六（2026-11-14）那场livehouse要去 |
| reference_product_v1 | E007_T03#m0 | HER | T04 | raw | 最近对话 | 0.895 | 7 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E007_T03#m0 | HER | T05 | raw | 最近对话 | 0.895 | 7 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E007_T04#m0 | ME | T05 | raw | 最近对话 | 0.583 | 5 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E007_T04#m0 | ME | T06 | raw | 最近对话 | 0.583 | 5 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E007_T04#m1 | HER | T05 | raw | 最近对话 | 0.857 | 8 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E007_T04#m1 | HER | T06 | raw | 最近对话 | 0.857 | 8 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E007_T05#m0 | HER | T06 | other | 【持续意图】 | 0.35 | 7 | 是 | 她确认周六去 livehouse |
| reference_product_v1 | E007_T05#m0 | HER | T06 | raw | 最近对话 | 0.9 | 18 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E007_T05#m0 | HER | T07 | raw | 最近对话 | 0.9 | 18 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E007_T05#m0 | HER | T07 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.35 | 7 | 是 | - [不到1小时前] 周六livehouse之约：她2026-11-12确认周六（2026-11-14）那场livehouse要去 |
| reference_product_v1 | E007_T06#m0 | HER | T07 | raw | 最近对话 | 0.7 | 11 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E007_T06#m0 | HER | T08 | raw | 最近对话 | 0.7 | 11 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E007_T07#m0 | ME | T08 | raw | 最近对话 | 0.7 | 6 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E008_T01#m0 | HER | T02 | raw | 最近对话 | 0.895 | 14 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E008_T01#m0 | HER | T02 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.316 | 6 | 是 | 她打算在妹妹答辩那几天陪她订饭，觉得其他方面帮不上 |
| reference_product_v1 | E008_T01#m0 | HER | T03 | raw | 最近对话 | 0.895 | 14 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E008_T02#m0 | ME | T03 | raw | 最近对话 | 0.833 | 10 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E008_T02#m1 | HER | T03 | raw | 最近对话 | 0.857 | 7 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E008_T03#m0 | HER | T04 | raw | 最近对话 | 0.889 | 16 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E008_T03#m0 | HER | T04 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.333 | 6 | 是 | - [不到1小时前] 问我和小林怎么认识：她今天中午问我是不是通过读书会认识小林的 |
| reference_product_v1 | E008_T03#m0 | HER | T05 | raw | 最近对话 | 0.889 | 16 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E008_T04#m0 | ME | T05 | raw | 最近对话 | 0.75 | 8 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E008_T04#m1 | HER | T05 | raw | 最近对话 | 0.867 | 11 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E008_T05#m0 | HER | T06 | raw | 最近对话 | 0.889 | 14 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E008_T05#m0 | HER | T07 | raw | 最近对话 | 0.889 | 14 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E008_T06#m0 | ME | T07 | raw | 最近对话 | 0.733 | 11 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E008_T06#m0 | ME | T08 | raw | 最近对话 | 0.733 | 11 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E008_T06#m1 | HER | T07 | raw | 最近对话 | 0.538 | 6 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E008_T06#m1 | HER | T08 | raw | 最近对话 | 0.538 | 6 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E008_T07#m0 | HER | T08 | raw | 最近对话 | 1.0 | 9 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E012_T01#m0 | HER | T02 | raw | 最近对话 | 0.778 | 7 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E012_T01#m0 | HER | T03 | raw | 最近对话 | 0.778 | 7 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E012_T02#m0 | ME | T03 | raw | 最近对话 | 0.6 | 3 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E012_T02#m1 | HER | T03 | raw | 最近对话 | 0.667 | 4 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E012_T02#m1 | HER | T03 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.667 | 4 | 是 | 她到20:45还没吃饭，正在纠结点哪家 |
| reference_product_v1 | E012_T03#m0 | HER | T04 | raw | 最近对话 | 0.833 | 14 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E012_T03#m0 | HER | T04 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.5 | 10 | 是 | - [不到1小时前] 约一起去吃汤锅：她今天21:00主动提出“找个时间一起去”吃我上次提过的那家汤锅，具体时间还没定 |
| reference_product_v1 | E012_T03#m0 | HER | T05 | raw | 最近对话 | 0.833 | 14 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E012_T04#m0 | ME | T05 | raw | 最近对话 | 0.667 | 6 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E012_T04#m0 | ME | T06 | raw | 最近对话 | 0.667 | 6 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E012_T04#m1 | HER | T05 | raw | 最近对话 | 0.867 | 10 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E012_T04#m1 | HER | T06 | raw | 最近对话 | 0.867 | 10 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E012_T05#m0 | HER | T06 | raw | 最近对话 | 0.727 | 9 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E012_T05#m0 | HER | T07 | raw | 最近对话 | 0.727 | 9 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E012_T06#m0 | HER | T07 | raw | 最近对话 | 0.895 | 15 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E012_T06#m0 | HER | T07 | structured | 场景状态链（条目后括号内为距今时间；同一事实 | 0.368 | 6 | 是 | - [不到1小时前] 周六吃饭确认：她说周六去得成，只是吃个饭，别搞得像面试 |
| reference_product_v1 | E012_T06#m0 | HER | T08 | raw | 最近对话 | 0.895 | 15 | 是 | 她：<real-message-redacted> |
| reference_product_v1 | E012_T07#m0 | ME | T08 | raw | 最近对话 | 0.778 | 5 | 是 | 我：<real-message-redacted> |
| reference_product_v1 | E012_T07#m1 | HER | T08 | raw | 最近对话 | 0.769 | 10 | 是 | 她：<real-message-redacted> |
| v5_purpose_aligned | E006_T01#m0 | HER | T02 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T01#m0 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T01#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T01#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T01#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T01#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T01#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.818 | 15 | 是 | m_src[0] ／ 2026-11-06 22:08 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T02#m0 | ME | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T02#m0 | ME | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T02#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T02#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T02#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T02#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.905 | 14 | 是 | m_src[1] ／ 2026-11-06 22:15 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T02#m1 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T02#m1 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T02#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T02#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T02#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T02#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 10 | 是 | m_src[2] ／ 2026-11-06 22:18 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T03#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 11 | 是 | m_src[3] ／ 2026-11-06 22:33 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T03#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 11 | 是 | m_src[3] ／ 2026-11-06 22:33 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T03#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 11 | 是 | m_src[3] ／ 2026-11-06 22:33 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T03#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 11 | 是 | m_src[3] ／ 2026-11-06 22:33 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T03#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 11 | 是 | m_src[3] ／ 2026-11-06 22:33 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T04#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 6 | 是 | m_src[4] ／ 2026-11-07 12:35 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T04#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 6 | 是 | m_src[4] ／ 2026-11-07 12:35 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T04#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 6 | 是 | m_src[4] ／ 2026-11-07 12:35 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T04#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 6 | 是 | m_src[4] ／ 2026-11-07 12:35 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T04#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.789 | 14 | 是 | m_src[5] ／ 2026-11-07 12:38 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T04#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.789 | 14 | 是 | m_src[5] ／ 2026-11-07 12:38 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T04#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.789 | 14 | 是 | m_src[5] ／ 2026-11-07 12:38 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T04#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.789 | 14 | 是 | m_src[5] ／ 2026-11-07 12:38 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T05#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.913 | 15 | 是 | m_src[6] ／ 2026-11-07 13:08 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T05#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.913 | 15 | 是 | m_src[6] ／ 2026-11-07 13:08 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T05#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.913 | 15 | 是 | m_src[6] ／ 2026-11-07 13:08 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T06#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.8 | 15 | 是 | m_src[7] ／ 2026-11-07 13:22 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T06#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.8 | 15 | 是 | m_src[7] ／ 2026-11-07 13:22 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T06#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 17 | 是 | m_src[8] ／ 2026-11-07 13:28 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T06#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 17 | 是 | m_src[8] ／ 2026-11-07 13:28 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E006_T07#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 12 | 是 | m_src[9] ／ 2026-11-07 13:45 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T01#m0 | HER | T02 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T01#m0 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T01#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T01#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T01#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T01#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T01#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[0] ／ 2026-11-09 20:12 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T02#m0 | ME | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T02#m0 | ME | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T02#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T02#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T02#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T02#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 20:20 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T02#m1 | HER | T03 | intent | 【当前目标(仅本轮有效，可指导行动)】 | 0.304 | 7 | 是 | 想约她看一场不太吵的 livehouse，主动一点、把话头接下来，但别把话说成已定死 |
| v5_purpose_aligned | E007_T02#m1 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T02#m1 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T02#m1 | HER | T05 | other | 本轮输入(最高优先，整条不被裁；id=来源I | 0.304 | 7 | 是 | {"id": "m0", "source_id": 6, "speaker": "PARTNER", "ts": "2026-11-10 1 |
| v5_purpose_aligned | E007_T02#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T02#m1 | HER | T06 | intent | 【当前目标(仅本轮有效，可指导行动)】 | 0.304 | 7 | 是 | 她确认周六去 livehouse |
| v5_purpose_aligned | E007_T02#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T02#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T02#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.826 | 14 | 是 | m_src[2] ／ 2026-11-09 20:24 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T03#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 7 | 是 | m_src[3] ／ 2026-11-09 20:38 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T03#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 7 | 是 | m_src[3] ／ 2026-11-09 20:38 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T03#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 7 | 是 | m_src[3] ／ 2026-11-09 20:38 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T03#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 7 | 是 | m_src[3] ／ 2026-11-09 20:38 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T03#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 7 | 是 | m_src[3] ／ 2026-11-09 20:38 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T04#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.583 | 5 | 是 | m_src[4] ／ 2026-11-10 12:00 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T04#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.583 | 5 | 是 | m_src[4] ／ 2026-11-10 12:00 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T04#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.583 | 5 | 是 | m_src[4] ／ 2026-11-10 12:00 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T04#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.583 | 5 | 是 | m_src[4] ／ 2026-11-10 12:00 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T04#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 8 | 是 | m_src[5] ／ 2026-11-10 12:08 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T04#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 8 | 是 | m_src[5] ／ 2026-11-10 12:08 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T04#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 8 | 是 | m_src[5] ／ 2026-11-10 12:08 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T04#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 8 | 是 | m_src[5] ／ 2026-11-10 12:08 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T05#m0 | HER | T06 | intent | 【当前目标(仅本轮有效，可指导行动)】 | 0.35 | 7 | 是 | 她确认周六去 livehouse |
| v5_purpose_aligned | E007_T05#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 18 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T05#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 18 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T05#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.9 | 18 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T06#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.7 | 11 | 是 | m_src[7] ／ 2026-11-12 21:28 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T06#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.7 | 11 | 是 | m_src[7] ／ 2026-11-12 21:28 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E007_T07#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.7 | 6 | 是 | m_src[8] ／ 2026-11-12 21:40 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T01#m0 | HER | T02 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T01#m0 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T01#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T01#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T01#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T01#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T01#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 14 | 是 | m_src[0] ／ 2026-11-09 19:06 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T02#m0 | ME | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T02#m0 | ME | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T02#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T02#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T02#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T02#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 10 | 是 | m_src[1] ／ 2026-11-09 19:20 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T02#m1 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T02#m1 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T02#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T02#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T02#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T02#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.857 | 7 | 是 | m_src[2] ／ 2026-11-09 19:23 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T03#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 16 | 是 | m_src[3] ／ 2026-11-10 12:38 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T03#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 16 | 是 | m_src[3] ／ 2026-11-10 12:38 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T03#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 16 | 是 | m_src[3] ／ 2026-11-10 12:38 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T03#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 16 | 是 | m_src[3] ／ 2026-11-10 12:38 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T03#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 16 | 是 | m_src[3] ／ 2026-11-10 12:38 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T04#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.75 | 8 | 是 | m_src[4] ／ 2026-11-10 12:50 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T04#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.75 | 8 | 是 | m_src[4] ／ 2026-11-10 12:50 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T04#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.75 | 8 | 是 | m_src[4] ／ 2026-11-10 12:50 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T04#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.75 | 8 | 是 | m_src[4] ／ 2026-11-10 12:50 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T04#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 11 | 是 | m_src[5] ／ 2026-11-10 12:53 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T04#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 11 | 是 | m_src[5] ／ 2026-11-10 12:53 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T04#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 11 | 是 | m_src[5] ／ 2026-11-10 12:53 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T04#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 11 | 是 | m_src[5] ／ 2026-11-10 12:53 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T05#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 14 | 是 | m_src[6] ／ 2026-11-11 20:28 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T05#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 14 | 是 | m_src[6] ／ 2026-11-11 20:28 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T05#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.889 | 14 | 是 | m_src[6] ／ 2026-11-11 20:28 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T06#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.733 | 11 | 是 | m_src[7] ／ 2026-11-11 20:40 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T06#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.733 | 11 | 是 | m_src[7] ／ 2026-11-11 20:40 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T06#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.538 | 6 | 是 | m_src[8] ／ 2026-11-11 20:43 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T06#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.538 | 6 | 是 | m_src[8] ／ 2026-11-11 20:43 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E008_T07#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 1.0 | 9 | 是 | m_src[9] ／ 2026-11-11 20:58 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T01#m0 | HER | T02 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T01#m0 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T01#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T01#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T01#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T01#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T01#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 7 | 是 | m_src[0] ／ 2026-11-09 20:28 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T02#m0 | ME | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T02#m0 | ME | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T02#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T02#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T02#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T02#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.6 | 3 | 是 | m_src[1] ／ 2026-11-09 20:40 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T02#m1 | HER | T03 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T02#m1 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T02#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T02#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T02#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T02#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 4 | 是 | m_src[2] ／ 2026-11-09 20:43 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T03#m0 | HER | T04 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 14 | 是 | m_src[3] ／ 2026-11-09 20:58 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T03#m0 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 14 | 是 | m_src[3] ／ 2026-11-09 20:58 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T03#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 14 | 是 | m_src[3] ／ 2026-11-09 20:58 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T03#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 14 | 是 | m_src[3] ／ 2026-11-09 20:58 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T03#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.833 | 14 | 是 | m_src[3] ／ 2026-11-09 20:58 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T04#m0 | ME | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 6 | 是 | m_src[4] ／ 2026-11-09 21:08 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T04#m0 | ME | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 6 | 是 | m_src[4] ／ 2026-11-09 21:08 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T04#m0 | ME | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 6 | 是 | m_src[4] ／ 2026-11-09 21:08 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T04#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.667 | 6 | 是 | m_src[4] ／ 2026-11-09 21:08 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T04#m1 | HER | T05 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 10 | 是 | m_src[5] ／ 2026-11-09 21:10 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T04#m1 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 10 | 是 | m_src[5] ／ 2026-11-09 21:10 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T04#m1 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 10 | 是 | m_src[5] ／ 2026-11-09 21:10 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T04#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.867 | 10 | 是 | m_src[5] ／ 2026-11-09 21:10 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T05#m0 | HER | T06 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.727 | 9 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T05#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.727 | 9 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T05#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.727 | 9 | 是 | m_src[6] ／ 2026-11-10 12:18 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T06#m0 | HER | T07 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 15 | 是 | m_src[7] ／ 2026-11-11 19:58 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T06#m0 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.895 | 15 | 是 | m_src[7] ／ 2026-11-11 19:58 ／ PARTNER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T07#m0 | ME | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.778 | 5 | 是 | m_src[8] ／ 2026-11-11 20:10 ／ USER ／ <real-message-redacted> |
| v5_purpose_aligned | E012_T07#m1 | HER | T08 | raw | 【真实历史消息：仅实际发送；模型回复是草稿， | 0.769 | 10 | 是 | m_src[9] ／ 2026-11-11 20:13 ／ PARTNER ／ <real-message-redacted> |

# 存储保真审计（S2 落盘侧）

每行=一条落盘记录：src 家族核「源消息能否解析、说话人是否标对、引用是否是锚点命中最多的那一轮」；plan 家族按同刻 ±10 分钟消息核「标题锚点是否有据」。锚点=在整段对话里出现 ≤3 条消息的内容 bigram。

| 臂 | 故事 | 文件 | 家族 | 条目 | 引用src/状态 | 锚点 | 需命中 | 引用命中 | 最佳轮 | 最佳命中 | 源说话人 | 判定 | 归属正确 |
|---|---|---|---|---|---|---:|---:|---:|---|---:|---|---|---|
| reference_product_v1 | E006 | moment/plan.md | plan | 她方案被甲方打回：她今晚22:35说真正在意的是被甲方在群里当众否定审美（当前） | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E006 | moment/plan.md | plan | 约饭（时间待定）：她答应但时间未定，说缓过来后主动找我（当前） | 新出现 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E006 | moment/scene.md | src | 约饭时间待定：我提出改天约饭继续聊 | t8_0 | 12 | 3 | 4 | 8_0 | 4 | ME | 有据 | 是 |
| reference_product_v1 | E006 | moment/scene.md | src | 她今天14:05答应约饭，但说等这阵子缓过来再主动找我 | t8_1 | 18 | 5 | 3 | 8_1 | 3 | HER | 弱据 | 是 |
| reference_product_v1 | E006 | raw_topic.md | src | 她方案被甲方打回：她今天方案被甲方打回，已经改了三版 | t1_0 | 10 | 3 | 7 | 1_0 | 7 | HER | 有据 | 是 |
| reference_product_v1 | E006 | raw_topic.md | src | 她今晚22:10还在为工作烦，说有点顶不住 | t1_0 | 9 | 3 | 1 | 1_0 | 1 | HER | 弱据 | 是 |
| reference_product_v1 | E006 | raw_topic.md | src | 她方案被甲方打回：她今天方案被甲方打回，已经改了三版 | t2_1 | 10 | 3 | 0 | 1_0 | 7 | HER | 弱据 | 是 |
| reference_product_v1 | E006 | raw_topic.md | src | 她今晚22:10明确说只想骂两句，不要分析和沟通建议 | t2_1 | 16 | 4 | 5 | 2_1 | 5 | HER | 有据 | 是 |
| reference_product_v1 | E006 | raw_topic.md | src | 方案被打回后被当众否定审美：她今晚22:35说最气的不是改稿，是甲方当着整个群说她审美不行 | t3_0 | 19 | 5 | 5 | 3_0 | 5 | HER | 有据 | 是 |
| reference_product_v1 | E006 | raw_topic.md | src | 我中午提醒她记得吃饭 | t4_0 | 6 | 2 | 4 | 4_0 | 4 | ME | 有据 | 是 |
| reference_product_v1 | E006 | raw_topic.md | src | 公司楼下新开的米线店：她今天中午说情绪好多了 | t4_1 | 13 | 4 | 5 | 4_1 | 5 | HER | 有据 | 是 |
| reference_product_v1 | E006 | raw_topic.md | src | 她发现公司楼下新开了一家米线店 | t4_1 | 10 | 3 | 6 | 4_1 | 6 | HER | 有据 | 是 |
| reference_product_v1 | E006 | raw_topic.md | src | 纠结做决定：她今天13:10问我做决定会不会纠结很久 | t5_0 | 12 | 3 | 2 | 5_0 | 2 | HER | 弱据 | 是 |
| reference_product_v1 | E006 | raw_topic.md | src | 纠结要不要跟领导掰扯：她说自己越想越不敢动 | t6_1 | 12 | 3 | 8 | 6_1 | 8 | HER | 有据 | 是 |
| reference_product_v1 | E006 | raw_topic.md | src | 她今天13:30问这次要不要跟领导掰扯一下 | t6_1 | 12 | 3 | 6 | 6_1 | 6 | HER | 有据 | 是 |
| reference_product_v1 | E006 | raw_topic.md | src | 没经历过却特别有感觉的事：她今天13:48问我有没有没经历过却特别有感觉的事 | t7_0 | 12 | 3 | 4 | 7_0 | 4 | HER | 有据 | 是 |
| reference_product_v1 | E007 | moment/plan.md | plan | 周末看livehouse：她表示想去，限定不吵的场（当前） | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E007 | moment/plan.md | plan | 约看livehouse：她今天20:40主动问起我提过的后摇乐队是否属于不太吵的类型（当前） | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E007 | moment/plan.md | plan | 2026-11-14看后摇乐队livehouse：邀约已发出，她说要看当天状态，暂未确定（当 | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E007 | moment/plan.md | plan | 2026-11-14周六看后摇乐队livehouse：她说要当天看状态，还没定（当前） | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E007 | moment/plan.md | plan | 计划2026-11-14去livehouse：她确认周六去，票自己买（当前） | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E007 | moment/plan.md | plan | 2026-11-14 livehouse之约：双方说定，我提前到门口等她，票她自己买（当前） | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E007 | moment/plan.md | plan | 2026-11-15 周六后摇现场见面：她已确认去、票自己买，我提前到门口等；本轮在聊要不要 | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E007 | moment/scene.md | src | 周六后摇演出·要不要带耳塞：她同事说周六那场会吵得头疼，她问要不要带耳塞 | t8_0 | 17 | 5 | 8 | 8_0 | 8 | HER | 有据 | 是 |
| reference_product_v1 | E008 | moment/plan.md | plan | 2026-11-09妹妹答辩期间陪订饭：她提到小林评价她太惯妹妹，问我的看法（当前） | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E008 | moment/plan.md | plan | 计划11月中旬周末咖啡见面：她说周末可能陪妹妹，咖啡改期（当前） | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E008 | moment/plan.md | plan | 周末咖啡改期：改到下周三，她下班早，明确只她一个人来（当前） | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E008 | moment/plan.md | plan | 2026-11-18 咖啡见面：她今天确认下周三下班早，且只她一个人来（当前） | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E008 | moment/plan.md | plan | 2026-11-14 周末咖啡：她今天说周末要陪妹妹，原定咖啡改期（当前） | 已取消 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E008 | moment/plan.md | plan | 计划11-18见面：她临时要监考，周三改不了，让我先约别人（当前） | 已取消 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E008 | moment/scene.md | src | 周三见面改期：她今天12:20说周三临时要监考，改不了 | t8_0 | 16 | 4 | 5 | 8_0 | 5 | HER | 有据 | 是 |
| reference_product_v1 | E008 | moment/scene.md | src | 她让我别等她，可以先约别人 | t8_0 | 6 | 2 | 4 | 8_0 | 4 | HER | 有据 | 是 |
| reference_product_v1 | E008 | raw_topic.md | src | 妹妹答辩期间陪订饭：她妹妹在读研，最近准备答辩 | t1_0 | 14 | 4 | 2 | 1_0 | 2 | HER | 弱据 | 是 |
| reference_product_v1 | E008 | raw_topic.md | src | 她打算在妹妹答辩那几天陪她订饭，觉得其他方面帮不上 | t1_0 | 12 | 3 | 3 | 1_0 | 3 | HER | 有据 | 是 |
| reference_product_v1 | E008 | raw_topic.md | src | 妹妹答辩期间陪订饭：她说小林评价她太惯着妹妹 | t2_1 | 12 | 3 | 1 | 1_0 | 2 | HER | 弱据 | 是 |
| reference_product_v1 | E008 | raw_topic.md | src | 她问我怎么看小林这句话 | t2_1 | 4 | 1 | 0 | 1_0 | 0 | HER | 无据 | 是 |
| reference_product_v1 | E008 | raw_topic.md | src | 问我和小林怎么认识：她今天中午问我是不是通过读书会认识小林的 | t3_0 | 14 | 4 | 6 | 3_0 | 6 | HER | 有据 | 是 |
| reference_product_v1 | E008 | raw_topic.md | src | 她把我没说过的话当成我说的，说我"上次说过"读书会 | t3_0 | 6 | 2 | 4 | 3_0 | 4 | HER | 有据 | 是 |
| reference_product_v1 | E008 | raw_topic.md | src | 小林夸我稳重：我告诉她说，我认识小林是以前同事介绍的，不是读书会。 | t4_0 | 12 | 3 | 7 | 4_0 | 7 | ME | 有据 | 是 |
| reference_product_v1 | E008 | raw_topic.md | src | 她承认自己记岔了。 | t4_1 | 5 | 2 | 1 | 4_1 | 1 | HER | 弱据 | 是 |
| reference_product_v1 | E008 | raw_topic.md | src | 她转述说，小林刚夸我稳重。 | t4_1 | 4 | 1 | 2 | 4_1 | 2 | HER | 有据 | 是 |
| reference_product_v1 | E012 | moment/plan.md | plan | 约一起去吃汤锅：她21:00主动提一起去，时间未定（当前） | 新出现 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E012 | moment/plan.md | plan | 约吃汤锅：她说周六可能有事，时间待定（当前） | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E012 | moment/plan.md | plan | 约汤锅见面：她觉得我催得太满有压力，时间未定（当前） | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E012 | moment/plan.md | plan | 11-14吃饭见面：她确认周六能到，要求别搞得太正式（当前） | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E012 | moment/plan.md | plan | 计划11-14周六吃汤锅：她确认周六能去，并说自己能订位、不想我全包（当前） | 进行中 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E012 | moment/plan.md | plan | 计划11-14周六汤锅见面：她反馈周六挺好，并主动提到下次不只饭点见（当前） | 已完成 | None | None | None | — | None | — | NR(抽象投影) | — |
| reference_product_v1 | E012 | moment/scene.md | src | 周六见面收尾与下次见面口风：她觉得周六这次见面挺好 | t8_0 | 12 | 3 | 2 | 8_0 | 2 | HER | 弱据 | 是 |
| reference_product_v1 | E012 | moment/scene.md | src | 她提出下次可以不只在饭点见 | t8_0 | 7 | 2 | 5 | 8_0 | 5 | HER | 有据 | 是 |
| reference_product_v1 | E012 | moment/scene.md | src | 她今天22:30说困了要先睡 | t8_0 | 7 | 2 | 0 | 1_0 | 1 | HER | 弱据 | 是 |
| reference_product_v1 | E012 | raw_topic.md | src | 打趣我没秒回：她今天晚上主动发消息，打趣我回消息慢 | t1_0 | 13 | 4 | 2 | 1_0 | 2 | HER | 弱据 | 是 |
| reference_product_v1 | E012 | raw_topic.md | src | 晚饭点哪家：我今晚开会，忙完才回她消息 | t2_0 | 12 | 3 | 2 | 2_0 | 2 | ME | 弱据 | 是 |
| reference_product_v1 | E012 | raw_topic.md | src | 她到20:45还没吃饭，正在纠结点哪家 | t2_1 | 10 | 3 | 4 | 2_1 | 4 | HER | 有据 | 是 |
