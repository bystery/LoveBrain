# E049 审计声明（wave1 隔离生成）

- story_id: E049
- title: 报名费的两种口径
- domain: 学习成长（在职备考班报名）
- conflict_type: 费用类（报名退费窗口口径反复变更）
- author: AI (ZCode isolation worker)
- author_type: AI
- real_user_material: false
- 隔离声明：本篇在干净上下文生成，未读 logs/ 目录、未读 candidates/ 目录、未读任何文件名含 VERDICT/REVIEW/AUDIT 的文档；仅依据 `_templates/W03-06_GEN_CHECKLIST.md`、episode/reference 模板与 STAGE2/3_MANIFEST 的 story_id/title/themes 字段防撞题。
- 人物声明：姜妍、小舟均为合成人物，无真实人名、电话、住址；台词中无评审术语与错误类型词汇。

## 0. 性质与暴露

本篇为 DEV 生活连续剧基准故事，全部由 AI 合成，real_user_material=false。exposure.json readers=["author"]，frozen_at=null，experiments_used=[]，dev_exposed=false。

## 1. 生活动机

HER 姜妍午饭时冲动报了 2980 的在职备考班并交 500 定金，销售先给"48小时全额退+内部通道"口径，次日改口为"正价课扣20%手续费"，随后负责人升级为"特价渠道单一律不退"，她走工单、合同条款核对、12315 投诉、拒签含放弃投诉条款的承诺书，最终 2980 原路全额到账。冲突型=费用类：冲突轮（T04/T08/T10/T11）的原子均带 status 变化（retracted / 口径变更）或 negation。

## 2. 连续性与回扣

- 跨轮引用原子 9 条以上：T03 引用 T02 口径（E049_A09）、T04 否定 T02 口径（E049_A11，negation=true, status=retracted）、T05/T12 引用 T01 报名与 T05 付款时间（E049_A15/E049_A36）、T06/T08 引用 T04 改口口径（E049_A18/E049_A24）、T07 引用 T05 证据留存（E049_A21）、T10 引用 T08 拒退主张（E049_A30）、T11 引用 T09 投诉在途（E049_A33）。
- 收尾回扣：T12 退款金额 2980 与 T01 报名总价闭环；奶茶邀约回扣长期熟人关系与 initial_kb 的截图核对习惯（玩笑式回扣）。
- 时间线：2026-10-26 12:31 至 2026-10-30 18:50，单调递增；付款 09:50 与"48小时后天上午到期"口径自洽。

## 3. ME 历史与边界

- ME 小舟：记性好、熟合同条款、先共情再给方案；initial_kb 明确她习惯把报价截图发我核对。
- 边界：T07 陪同请求 may_decline=true，参考卡给出答应+分工的接受路径与留台阶的处理；ME 全程不替她拍板（private_guidance 多次强调"不替她决定"）。

## 4. no_reply / 媒体局限

本篇 12 轮全部为 HER 文本消息，未使用图片/语音/未读条数等媒体形态，未设置 no_reply 轮。局限：机构侧（销售、负责人、客服、平台）发言全部通过 HER 转述呈现，属于刻意的信息二传设计，校验器亦不要求第三方直接发言。

## 5. 参考卡质量

- 12 轮 reference 全量给出 current_action、acceptable_actions（各 3 条）、applicable_dimensions（各 4-5 个、均在九维白名单内）、required_memory_atoms（共 36 条，其中 required_this_turn=11 条）、private_guidance、unknowns、protected_expressions、forbidden_factual_claims。
- 全部 message 原子 quote 为对应轮 HER 台词逐字子串并带 message_id；value 均 ≥4 实义词。
- 跨轮引用原子（source case 早于当前轮）10 条，≥3 条红线达成。
- 冲突型落实：E049_A11（negation=true, status=retracted）记录全额退口径被否定；E049_A10/A18/A22 记录口径两次变更。

## 6. 已知风险

- 金额口径多（2980 / 500 定金 / 减两百 / 20% 手续费），已按"定金 500 已付、尾款未付、退款=全款 2980 原路退"自洽，语义审查时可重点复核。
- "特价渠道单"与"官网原价"的矛盾是剧情设计（HER 主张 vs 负责人主张），atom 已分别挂在 THIRD 与 HER subject 下，避免混同。
- 12315 流程细节（三个工作日回复期）为剧情化简化，非法律咨询。

## 7. 出口检查

- [x] episode.json / reference.json / timeline.json / audit.md / exposure.json 五件齐
- [x] 12 turns、case_id 全局唯一、clock/ts 单调、ts ≤ clock
- [x] quote 逐字核验由 tools/validate_story_v2.py 机械执行
- [x] 冲突轮原子含 status 变化或 negation
- [x] exposure readers=["author"]、real_user_material=false、hashes 与 runner_copy 由校验器核验
