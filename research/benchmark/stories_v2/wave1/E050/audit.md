# E050 审计声明（wave1 隔离生成）

- story_id: E050
- title: 考期跨月改约
- domain: 学习成长（心理咨询基础证书备考）
- conflict_type: 取消改期类（11月2日考期取消，跨月改至12月14日）
- author: AI (ZCode isolation worker)
- author_type: AI
- real_user_material: false
- 隔离声明：本篇在干净上下文生成，未读 logs/ 目录、未读 candidates/ 目录、未读任何文件名含 VERDICT/REVIEW/AUDIT 的文档；仅依据 `_templates/W03-06_GEN_CHECKLIST.md`、episode/reference 模板与 STAGE2/3_MANIFEST 的 story_id/title/themes 字段防撞题。
- 人物声明：许清、小舟均为合成人物，无真实人名、电话、住址；台词中无评审术语与错误类型词汇。

## 0. 性质与暴露

本篇为 DEV 生活连续剧基准故事，全部由 AI 合成，real_user_material=false。exposure.json readers=["author"]，frozen_at=null，experiments_used=[]，dev_exposed=false。

## 1. 生活动机

HER 许清原定 11月2日 考心理咨询基础证书，10月29日 考试中心通知机房故障、场次取消并统一改到 12月14日（跨月改约），她经历删日历色块、自我怀疑、官网确认改约、把多出的40天重建为专项计划、顶住母亲"反正在家没事"的看店要求、用自约模考守住原考试日、又把跑不通的通勤音频计划降标修订。冲突型=取消改期类：T03 原考期计划原子 status=superseded + negation=true，T08 看店请求 status=rejected + negation=true，T11 计划 status=revised。

## 2. 连续性与回扣

- 跨轮引用原子 12 条：T02 引用 T01 冲刺期锚点（E050_A07）、T03/T04/T07/T12 反复引用 T03 改期事实与 T01 原计划（E050_A10/A13/A23/A38）、T05 引用 T03 取消原因（E050_A16）、T06 引用 T01 出差（E050_A20）、T08 引用 T07 看店请求（E050_A26）、T09 引用 T08 模考安排（E050_A29）、T10/T11 引用 T06 旧计划（E050_A32/A35）、T12 引用 T11 修订（含 E050_A38 日期闭环）。
- 收尾回扣：T12 "距离考试还有29天" 与 12月14日 改期闭环；火锅邀约回扣"老同学/学习搭子"的 initial_kb 关系种子。
- 时间线：2026-10-27 21:08 至 2026-11-15 17:45，单调递增；6天/29天/40天等日数口径与日期自洽。

## 3. ME 历史与边界

- ME 小舟：大学学习搭子出身、擅长拆待办、给选项不替人定。
- 边界：T10 求助轮 may_decline=true（她带方案来问，ME 可轻回应）；全程不替她定计划、不评判她母亲，private_guidance 明确"当同盟不当军师"。

## 4. no_reply / 媒体局限

本篇 12 轮全部为 HER 文本消息，未使用图片/语音/未读条数等媒体形态，未设置 no_reply 轮。局限：考试中心短信、母亲的话均为 HER 转述，属信息二传设计。

## 5. 参考卡质量

- 12 轮 reference 全量给出 current_action、acceptable_actions（各 3 条）、applicable_dimensions（各 4 个、均在九维白名单内）、required_memory_atoms（共 44 条，其中 required_this_turn=12 条）、private_guidance、unknowns、protected_expressions、forbidden_factual_claims。
- 全部 message 原子 quote 为对应轮 HER 台词逐字子串并带 message_id；value 均 ≥4 实义词。
- 跨轮引用原子 12 条，≥3 条红线达成。
- 冲突型落实：E050_A10（status=superseded, negation=true）、E050_A26（status=rejected, negation=true）、E050_A31/A35（status=stalled, negation=true）、E050_A33/A34（status=revised）。

## 6. 已知风险

- "40天"为 HER 口径（10-31 起算到 12-14 约为 44 天），按她说时点 10-31 前后取整，若语义审查较真可改为"多出来的一个多月"。
- 母亲、表弟均为合成第三方，无家庭背景展开，避免与真实家庭结构对号。
- 心理咨询基础证书的考试流程为剧情化简化，非真实考试机构。

## 7. 出口检查

- [x] episode.json / reference.json / timeline.json / audit.md / exposure.json 五件齐
- [x] 12 turns、case_id 全局唯一、clock/ts 单调、ts ≤ clock
- [x] quote 逐字核验由 tools/validate_story_v2.py 机械执行
- [x] 冲突轮原子含 status 变化或 negation
- [x] exposure readers=["author"]、real_user_material=false、hashes 与 runner_copy 由校验器核验
