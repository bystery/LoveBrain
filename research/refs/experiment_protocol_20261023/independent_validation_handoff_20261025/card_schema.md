# 评审卡 schema（独立验证用，评估侧，不进考生输入）

沿用现有 `04_GRADING_CARDS_*` 字段结构，不新建评分框架。每请求位一张卡：
```
{
  "episode_id": "V0xx", "case_id": "V0xx_T0k", "split": "independent_holdout",
  "content_revision": "<独立集版本>",
  "families": ["目标启停|第三人归属|跨话题事实|自然收尾|..."],   // 考点族，不含候选专用规则
  "evidence": [ {"case_id","message_index_zero_based|field","role|field","text","ts","visibility"} ],
  "allowed_behaviors": ["多种有效表达：确认/新提议/商量/玩笑/调情/合理回扣/合适短答/合法不回"],
  "failure_conditions": ["谎称已同意/已发生/已查明；无据现场/路线/设施/天气；冒领他人话；已完成后机械复活；军师指令混入可发槽；无关旧事占题"],
  "required_reply_phrase": null,          // 不预写唯一参考措辞
  "contrast_design": "<正向机会与边界并存>",
  "next_behavior_check": "<下一回合入口>",
  "unresolved_ambiguities": "<允许澄清/不确定的点>",
  "review_status": "authored_pending_review",
  "review_guidance": "<允许多解，不逼命中关键词；语气歧义单列存疑不硬判失败>"
}
```

分层评审口径（依 arena AGENTS §10 / v5_independent_review §6）：程序合同 / 事实语义 / 互动质量 /
用户愿发偏好**分列**；程序 10 分 ≠ 愿发；合法 no_reply 与普通回复分开审；空 analysis 遵守率是实现遵从、
非聊天胜负。
