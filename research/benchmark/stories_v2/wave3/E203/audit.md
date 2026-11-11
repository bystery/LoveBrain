# E203 安装当天换了师傅（家居装修 · 第三方类）审计单

批次：W3-T4-20261102 ｜ 会话：隔离故事生成会话 T4-6 ｜ author=AI ｜ real_user_material=false
隔离声明：本会话未读 logs/、未读 candidates/、未读任何 VERDICT/REVIEW/AUDIT/评审 JSON、未读 B3 队列。

## 0. 性质与暴露
- 本篇为 AI 合成标准 12 轮故事，无任何真实用户材料（real_user_material=false）。
- 暴露 readers=["author"]，frozen_at=null，experiments_used 为空。
- 人物 何静 / 大成 / 罗师傅 及店家、小区均为虚构合成，不含真实人名、电话、住址。

## 1. 生活动机
- 装修收尾期安装方临时改派第三方分包并夹带未签增项，是家装高频纠纷场景。
- 关系阶段：亲戚（堂兄妹把关）；HER 特质：请不出假、信书面凭证；ME 特质：到场先看再开口、不代拍板付款。

## 2. 连续性与回扣
- 冲突型：第三方类；主题集：家居装修、定制衣柜安装、安装师傅临时换人、分包增项把关、验收留证付尾款。
- 跨轮引用原子多处：周三安装安排（T01→T04 作废）、尾款两千四（T02→T09→T10）、增项四百五（T06→T07→T10 否决）、玄关柜门缝（T03→T08→T09）、三组柜子（T03→T06→T08）。
- 数字自洽：增项每组一百五×三组=四百五；尾款两千四前后一致。

## 3. ME 历史与边界
- ME 五年家居安装经验支撑现场核对、封边初验与铰链判断；只给立场不代付款。
- HER 边界：合同没写的增项不认；责任主体必须书面确认。

## 4. no_reply / 媒体局限
- T12（致谢回礼）允许不接或轻量接。
- 店家电话、群聊书面确认、师傅报价均按 HER 转述呈现；台词零评审术语。

## 5. 参考卡质量
- 每轮 current_action 单值 + acceptable_actions 多选（≥2），applicable_dimensions ≥3。
- 冲突落实轮（T04、T06、T10）原子分别带 status=cancelled/uncertain/asserted+negation；private_guidance 仅影响口吻。
- unknowns 明示（带队人身份、增项归属、延保范围等），不虚构补全。

## 6. 已知风险
- 第三方改派是否构成违约属开放判断，参考卡已固定为"不必然违约但需书面确认"立场。
- 增项处置跨三轮，裁判需按轮独立记 HER 立场变化。

## 7. 出口检查
- 全部 source.quote 已做逐字子串自核（message 与 initial_kb 双通道）。
- 时钟与消息时间戳单调校验通过；protected_expressions 均为本轮 HER 台词子串。
- 机械校验以 tools/validate_story_v2.py --stage wave3 为准。
