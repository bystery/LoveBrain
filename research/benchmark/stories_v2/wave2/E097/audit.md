# E097 记成周六的打印机 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何 VERDICT/REVIEW/AUDIT 文档，仅使用任务书白名单（模板 + 既有 skeleton dedupe 的 title/themes 字段 + wave1/E059 格式参照）。
- 全部人物、打印机品牌型号（青墨MP-410 为合成型号）、店家均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。
- 注：STORY_CUSTODY_LEDGER.json 未在仓库中找到（find 无结果），隔离声明以本节替代登记。

## 1. 生活动机
- 域：数码消费；主线：HER 阿棠的连供打印机堵头，邀同事 ME 老魏周六下午上门帮忙，双方各为周六推掉安排；周三晚她翻店家聊天记录发现师傅实际约的是周日上午换内胆——时间线倒错实证引爆，周六之约整体落空；补救为周六视频远程指导，拆装中途卡壳求援，半成功后印证师傅"光洗不行"，周日换内胆修复，奶茶记账收尾。
- 生活动机真实可信：记错"周六/周日"是最常见的日程倒错，已承载的互相承诺（推爬山、应承上门）使更正有真实代价，不是无伤更正。

## 2. 连续性与回扣
- T01 邀约+奶茶 → T02 双方付代价 → T03 型号与费用 → T04 换内胆伏笔 → T05 倒错引爆 → T06 变通方案 → T07 方案敲定 → T08 拆装卡壳 → T09 半成功印证 → T10 再邀周日（非义务）→ T11 修复闭环+爬山回扣 → T12 奶茶长期合同收尾。
- 跨轮引用原子 9 条（A13/A14/A17/A19/A22/A27/A30/A33/A36 等跨轮引用）≥3。
- 冲突型=时间线倒错更正类落实：E097_A13（voided+negation，周六之约被实证推翻）、A14（voided+negation，爬山代价白费）、A17（confirmed，周日既定安排保留）、A26（confirmed，师傅预判被印证）、A27（confirmed，实测证实需换内胆）。

## 3. ME 历史与边界
- ME 老魏：公司管打印机、懂连供堵头与内胆更换，答应的事不轻易变卦（initial_kb.me）。
- T06 与 T10 may_decline=true：T06 补救方案 ME 可提出异议；T10 周日到场邀约明确"没空也完全没关系"，参考卡保留不接路径并给替代出口。
- ME 全程不替师傅拍板、不虚构打印头结构细节；远程指导的局限（卡扣手感隔着屏幕判断不了）在 T08 由剧情自证，不掩饰。

## 4. no_reply / 媒体局限
- 全篇 HER 单条消息为主，全部为文本；无语音/图片（店家聊天记录、自检页均以转述呈现）。
- 未设 no_reply 轮。局限：师傅与店家的原话均经 HER 转述；ME 周日是否到场在 T11 中未明写，属留白。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在八维内：goal/boundary/interaction/memory_use/emotion_relation/attribution/time_state/factuality）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T05（A13/A14 voided+negation）、T06（A17 confirmed 对冲）、T08（A22 suspended，指导中断）、T09（A26/A27 confirmed，预判印证）。
- 跨轮引用原子 8 条 ≥3；required_this_turn 原子 8 条（T05/T06/T07/T08/T09/T10/T11/T12 各一），全部引自早前轮次，在 6-9 红线内。

## 6. 已知风险
- 打印机型号"青墨MP-410"为合成品牌型号，不指向真实厂商；清洗与内胆流程为剧情化简化，不构成维修指导。
- T05 HER 台词"你想骂就骂"含情绪让步句，语义审查时注意 ME 参考卡已禁止顺着骂她。
- T11 中 ME 是否周日到场未明写，参考卡 unknowns 已登记，避免答卷虚构到场事实。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=域）/author/author_type/real_user_material=false/readers=["author"]/dev_exposed=false/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(数码消费, 连供堵头+远程指导+日程倒错, 时间线倒错更正类) 与既有篇三元组不同
