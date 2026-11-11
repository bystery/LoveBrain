# E129 audit(W3-T1-B 隔离生成会话)

## 0. 性质与暴露
- author: AI(隔离生成会话 W3-T1-B)
- author_type: AI
- real_user_material: false(全部人物/机构/价格为合成)
- readers: ["author"];frozen_at: null;experiments_used: []
- kind: standard_12t;domain: 家居装修;conflict_type: 第三方类
- 批次: W3-T1-20261102;隔离声明已记入 STORY_CUSTODY_LEDGER(generation_isolation_ack)。

## 1. 生活动机
封窗比好价后遇物业统一合作厂家政策变卦,以事实与差价筹码走个案审批,原方案合规落地。

## 2. 连续性与回扣
T1比价5200→T2自查规约→T3报备可行→T4传闻警觉→T5政策坐实报备暂停(第三方核心轮)→T6个案审批切口→T7差价3700筹码→T8批复押金500→T9重签5200排期→T10完工→T11押金退还攻略反哺→T12晒阳台收尾;第三方口径在T3/T5/T8三次变化。

## 3. ME 历史与边界
ME蔺舟做过两年建材销售,把关只给检查项;边界为不走灰色渠道,不给违规支招。

## 4. no_reply / 媒体局限
T11(攻略反馈闲谈)may_decline=true;无媒体消息。

## 5. 参考卡质量
T5轮报备口径原子superseded+negation、政策原子corrected;T1报价原子作为跨轮筹码在T7必选;unknowns逐轮明示。

## 6. 已知风险
报价与押金数额为合成;物业流程细节为常见通行做法。
- W03-06 T1-B 语义审查登记(2026-11-02):episode 侧缺陷只登记不改——T09(11-15 周日)称"下周五进场"(=11-20)而 T10(11-16 周一)称"今天封好了",进场日与完工日矛盾四天,已在 T10 unknowns 登记禁止圆场;建议冻结前由作者会话微修(T10 ts 改 11-20 或 T09"下周五"改"明天")。另 T07 图纸 11-11 交、审批窗口称五个工作日、11-13 即批复,早于承诺窗口属生活合理,不动。参考卡 A10 valid_time 保留 11-20 口径(与 T09 文本一致),A11 完工 11-16 口径并存,以 unknowns 为准。

## 7. 出口检查
- validate_story_v2 --stage wave3 --turns 12 --count 8(随分片 STAGE1_MANIFEST_B.json 全量机械校验)
- 冲突型落实:该型所在轮原子含 status 变化或 negation(见 revisions)。
- 台词零评审术语;quote 逐字自核;不虚构真实人名/电话/住址。
