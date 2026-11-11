# LoveBrain 现行 harness 能力基线（monster 必须超越的线）

> 来源：2026-10-16 子代理源码侦察（D:\LoveBrain，Kotlin/Android）。监考窗口出题与归因时以此为准。

## 一句话基准线

工程纪律很强（WAL 事务、幂等、冻结输入、画像 human-in-the-loop 确认制），但**记忆模型是"窗口+归档冷存储"，没有检索层，意图没有结构化**——monster 的主攻点：语义检索长期记忆、结构化目标追踪、token 级上下文预算。

## 关键事实

- **意图 = 自由文本**（≤200 字 + 开关，IntentConfig，存 moment/intent.json），**不是枚举**。无结构化、无语义匹配，参与事项召回只靠 lowercase contains 关键词。
- **没有向量检索**。vector_history.md 的"向量"是五维关系评分（亲密/信任/承诺/激情/安全感 0-100，±20 夹紧），不是 embedding。全项目无 embedding/余弦。
- **注入全靠窗口**：recent 只留 2 轮、scene 2 条/2h、lessons 只取最近 3 个 H1 块、消息上限 60 条；raw_*（raw_chat/raw_topic/raw_scene）是冷存储，**主生成链路从不读**，只喂后台引擎。
- **长期事实存活的窄路**：①每 5 话题一次的 reflect 摘要进画像（还要用户点确认）②经验提取进 lessons 且仍在最近 3 批窗口 ③被包进 plan.md 事项。"50 轮前她提过猫毛过敏→聊火锅时想起来"当前架构基本做不到。
- **plan.md 进行中事项**：itemId 稳定身份、状态链、幂等、防复活、DORMANT 休眠（5 轮无新证据）、冷却 3 轮；注入前过 OngoingContextSelector 关键词门控（默认拒绝）。
- **话题切换即清空**：topic_status=new（模型一句话判定）→ 四个源文件立即归档清空，上一话题可见度瞬间归零。
- **预算**：TOTAL_BUDGET=9000 字符（按字符不按 token），byBlocks 裁剪顺序：旧记忆→旧 recent→对话头部。
- **输出契约**：4 风格（推荐/清醒/俏皮/温柔）+ 4 方向（跟进/展开/表达/转向，可 null=本轮不适合）+ analysis（topic_status/topic_label/scene_facts 带 source_ids/ongoing 只报变化）。parse 失败不重试（reflect 有三级自愈而主链路没有）。
- **三通道输入**：她/我/补充（IDEA 通道独立成 ReplyDirective，不混入对话）；另有仅看本轮开关、进攻模式（aggressive.md）、谈心/主动发/改写三条旁路。
- **写回时机**：用户点"下一轮"才触发 WAL 六投影写回（话题归档/标签/recent/scene/plan/计数），崩溃可恢复。
- **记忆纠正**：WRONG/FINISHED/MUTED/WRONG_PERSON 四类 + revision 防迟到覆盖，注入前过滤。

## 三大短板（= monster 的得分区）

1. 记忆组织：没有检索只有窗口，长期事实无召回通道；
2. 意图处理：200 字自由文本打天下，冲突仲裁只靠 prompt 文案声明，代码层零保证；
3. 上下文管理：硬窗口+切换即清空，无语义压缩。
