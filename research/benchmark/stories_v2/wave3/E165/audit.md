# E165 返程被妈妈安排了（出行拼车 · 第三方类）审计单

批次：W3-T2-B1-20261103 ｜ 会话：隔离故事生成会话 T2-B1（重试） ｜ author=AI ｜ real_user_material=false
隔离声明：本会话未读 logs/、未读 candidates/、未读任何 VERDICT/REVIEW/AUDIT/评审 JSON、未读 B3 队列；仅读模板、防撞 manifest 字段与校验器。

## 0. 性质与暴露
- 本篇为 AI 合成标准 12 轮故事，无任何真实用户材料（real_user_material=false）。
- 暴露 readers=["author"]，frozen_at=null，experiments_used 为空。
- 人物 韩雨薇 / 顾北 / 豆豆 / 王叔 及全部机构、地点、电话均为虚构合成，不含真实人名、电话、住址。

## 1. 生活动机
- 母亲自作主张定下返程车、表哥托带外甥回乡，构成周末回乡拼车里最常见的人情与安全两难。
- 关系阶段：普通朋友偏熟（前同事）；HER 特质：规矩清楚、不欠人情、孩子安全不含糊。

## 2. 连续性与回扣
- 冲突型：第三方类；主题集：出行拼车、周末回乡顺风车、长辈第三方代订返程、儿童增高垫安全、单程油费重算。
- 跨轮引用原子共 4 处（T04←T01 返程取消、T11←T03 免费口径作废、T09←T04 返程归属、T12←T10 辣酱回扣）。
- 事件与修订链见 timeline.json：返程更替、出发提前、免费口径作废三段。

## 3. ME 历史与边界
- ME 顾北跑过两趟清源顺路单，对人托带的活儿谨慎、对安全较真。
- HER 边界：车费按规矩结清、孩子乘车安全不含糊。

## 4. no_reply / 媒体局限
- T05、T10、T12 允许不接或缓接；T08 集合轮必须短回复。
- 台词零评审术语；母亲来电、表哥说法均为 HER 转述呈现，不冒充第一手。

## 5. 参考卡质量
- 每轮 current_action 单值 + acceptable_actions 多选（≥2），applicable_dimensions ≥3。
- 冲突型落实轮（T03/T04/T07/T11）原子带 status 变化或 negation；private_guidance 仅影响口吻。
- unknowns 明示未定项，不虚构补全。

## 6. 已知风险
- 第三方主体多（母亲、王叔、表哥、豆豆），裁判需区分转述与当事人行为，防归因串线。

## 7. 出口检查
- 全部 source.quote 做逐字子串核验（message 与 initial_kb 双通道）。
- 时钟与消息时间戳单调校验通过；quote 自核通过后入库。
- 机械校验以 tools/validate_story_v2.py --stage wave3 --turns 12 --count 4 --manifest-name STAGE1_MANIFEST_T2B1.json 为准。
