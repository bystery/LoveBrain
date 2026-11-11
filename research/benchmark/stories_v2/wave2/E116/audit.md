# E116 全包两个字 — audit

## 0. 性质与暴露
- author=AI (ZCode isolation worker-AI)
- real_user_material=false
- 本篇在隔离会话生成：未读 logs/、未读 candidates/ 失败输出、未读任何评审 JSON，仅使用任务书白名单（模板 + 既有 dedupe 的 story_id/title/themes 字段 + wave1 E059 格式参照）。
- 全部人物、工作室、价目数据均为合成；无真实人名、电话、住址。
- exposure.json readers=["author"]，frozen_at=null。

## 1. 生活动机
- 域：学习成长；主线：HER 圆圆凭销售'材料全包'的口头承诺办了1280元陶艺季卡并立项给 ME 烧杯子，还把'闭眼办'安利给同事小蝶；进窑时被收30元/件釉烧费——承诺与收据除外条款的口径冲突引爆；她留痕未付款，按'先条款后承诺再比价'顺序与老板谈判，裁定期内烧制费全免、记录入档；随后不再替小蝶拍板、如实交底，小蝶自带计算器按总价自主办卡；裂纹杯出窑交接、转投社区教室80元全包、茶盘工程立项收尾。
- 生活动机真实可信：办卡被口头承诺钓鱼、安利翻车怕坑朋友、为'没说谎'自攒证据，是兴趣消费中最常见的费用口径课题。

## 2. 连续性与回扣
- T01 办卡+'全包'承诺+烧杯立项 → T02 二次安利埋雷 → T03 收费冲突引爆 → T04 双源凭证自查 → T05 算差价备谈判 → T06 期内全免落定 → T07 续卡诱惑与不再代拍板 → T08 交底卸责 → T09 裂纹杯出窑 → T10 交杯之约+转投社区教室 → T11 双轨稳态 → T12 茶盘工程收尾。
- 跨轮引用原子 6 条（A08/A17/A20/A22/A27/A29/A31）≥3。
- 冲突型=费用类落实：T03（A07 negation、A08 suspended+negation，'全包'被现场收费否认）；T06（A15 confirmed、A17 resolved，差价归属落定）；T08（A22 resolved，连带责任卸下）；费用数字全程可核（1280/8次、30元/件、剩6次约小两百、80元/节全包）。

## 3. ME 历史与边界
- ME 竹里：饭搭子、干过采购对合同口径敏感（initial_kb.me），先摆凭证再开口，不替人拍板。
- ME 全程只帮算账排顺序：T04 给'该较真但按证据顺序'的准话；T05 给三步谈判顺序；T07 拒绝替她决定续卡与否；小蝶的办卡决定完全归小蝶。
- 不虚构法律结论：收据条款效力不判定，只以店铺信用落定；'白拿裂纹杯'定性为人情不是赔偿。

## 4. no_reply / 媒体局限
- HER 以单条长消息为主，T03/T04 为同日两连发（引爆→盘证据），符合纠纷的真实节奏。
- 未设 no_reply 轮。局限：销售、前台、老板、小蝶的原话均经 HER 转述引用，属刻意的信息二传设计；聊天记录、收据、价目表均以转述呈现，无图片媒体。

## 5. 参考卡质量
- 每轮 current_action 1 个、acceptable_actions 3 条、applicable_dimensions 3-4 个（均在九维白名单内）。
- 原子 quote 全部为对应来源台词的逐字子串并带 message_id；value 均 ≥4 实义词。
- 冲突轮落实：T03（A08 suspended+negation）、T06（A15 confirmed、A17 resolved）、T07（A20 承诺信任被动摇）、T08（A22 resolved）、T10（A27 confirmed 比价路径兑现）。
- 跨轮引用原子 7 条，≥3 条红线达成；required_this_turn 原子 7 条（T03/T06/T07/T08/T10/T11/T12 各 1），全部来自早前轮次。

## 6. 已知风险
- 销售、前台、老板、小蝶的原话均出自 HER 转述引号，语义审查时注意引用层级不构成虚构第三方发言。
- 价格与条款为剧情化设定，不构成消费维权指导；裂纹杯'喝水没毛病'为 HER 主观判断，参考卡未采信为事实。
- 日期（2026-09-24 周四起、10-17 周六进窑）与真实日历一致。

## 7. 出口检查
- [x] episode.json 12 轮、clock/ts 单调、角色 HER、口语化台词
- [x] reference.json 每轮字段齐全、quote 逐字、unknowns/protected/forbidden 为列表
- [x] timeline.json 由 episode 机械派生，事件含冲突标注
- [x] exposure.json 含 kind/story_id/title/themes（首元素=学习成长）/author/author_type/real_user_material=false/readers=["author"]/hashes/runner_copy
- [x] 台词无评审术语与错误类型词汇
- [x] (域, 主题集, 冲突型)=(学习成长, 陶艺季卡+烧制费口径冲突+凭证谈判+安利交底, 费用类) 与既有 64 篇三元组至少一元不同
