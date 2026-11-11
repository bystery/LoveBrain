# E158 点位图上的两个插座（家居装修 · 更正类）审计单

批次：W3-T2A1-20261102 ｜ 会话：隔离故事生成会话 T2-A1（重试） ｜ author=AI ｜ real_user_material=false
隔离声明：本会话未读 logs/、未读 candidates/、未读任何 VERDICT/REVIEW/AUDIT/评审 JSON、未读 B3 队列。

## 0. 性质与暴露
- 本篇为 AI 合成标准 12 轮故事，无任何真实用户材料（real_user_material=false）。
- 暴露 readers=["author"]，frozen_at=null，experiments_used 为空。
- 人物 陶蔓青 / 邵一帆 及全部工长、门店均为虚构合成，不含真实人名、电话、住址。

## 1. 生活动机
- 装修中图纸版本被推翻、口头不算数，是家居改造里最高频的更正冲突。
- 关系阶段：渐暧昧（朋友介绍认识）；HER 特质：计划控、细节敏感；ME 有监理经验肯跑现场。

## 2. 连续性与回扣
- 冲突型：更正类；主题集：家居装修、厨房水电改造、点位图版本更正、岛台插座存废、书面补充单。
- 跨轮引用原子共 11 处，事件与修订链见 timeline.json。
- 更正链（立图-改期-翻案-举证-书面更正-规范虚惊-验收-固化）每步逐字可溯；T04 插座存废以否定原子记录，T06 以状态翻转记录。

## 3. ME 历史与边界
- ME 干过两年监理，只给可操作动作：留证据、要书面、分清规范与方案。
- HER 边界：改方案必须落书面；超预算先打电话再点头。

## 4. no_reply / 媒体局限
- T12（开灶邀约）允许缓办；冲突轮先接情绪再谈证据。
- 台词零评审术语；点位图、聊天记录、手写签名页均按 HER 转述呈现，不冒充第一手。

## 5. 参考卡质量
- 每轮 current_action 单值 + acceptable_actions 多选（≥2），applicable_dimensions ≥3。
- 冲突落实轮（T04/T06）原子带 negation 或 status 翻转；private_guidance 仅影响口吻。
- unknowns 明示未定项（新版图改动明细、补充单条款细节），不虚构补全。

## 6. 已知风险
- 裁判需区分"守书面底线的较真"与"得理不饶人"，HER 的目标始终是把方案落回图纸而不是压服工长。

## 7. 出口检查
- 全部 source.quote 已由生成器做逐字子串断言（message 与 initial_kb 双通道）。
- 时钟与消息时间戳单调校验通过；quote 自核通过后入库。
- 机械校验以 tools/validate_story_v2.py --stage wave3 --turns 12 为准。
