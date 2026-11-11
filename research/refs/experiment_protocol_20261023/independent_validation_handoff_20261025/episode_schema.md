# 故事文件 schema（独立验证出题用）

顶层对象：
```
{
  "episode_id": "V0xx",              // 中性编号，勿泄漏考点
  "persona": { "me": "<string>", "her": "<string>" },   // 关系起点画像（故事侧）
  "initial_kb": {                     // 只放双方均消费的支持字段；值必须标量/平面
     "me": "<string>", "her": "<string>", "warmth": "<string>",
     "style": "...", "stage": "...", "lessons": "...",
     "ongoing_plan": [ {"name": "<日期+事件>", "status": "进行中|暂定|已完成"} ]
  },
  "turns": [ <8 个请求位> ],
  "gold": {}                          // 顶层 gold 属评估侧，一般空对象；断言挂评审卡
}
```

每个请求位：
```
{
  "case_id": "V0xx_T0k",              // k=1..8
  "turn_type": "reply",
  "input": {
    "messages": [ {"role": "HER|ME", "text": "<string>", "ts": "YYYY-MM-DD HH:MM"}, ... ],
    "clock": "YYYY-MM-DD HH:MM",      // 冻结时间源，必填
    "may_decline": true|false,        // 本轮是否允许不回
    "note": "<string 可选>",          // 军师私密备注，候选可见、不外发
    "intent": {"enabled": bool, "text": "<string>", "status": "ACTIVE|CLOSED"}  // 可选
  },
  "gold": {}                          // 轮级 gold 空对象
}
```

机械约束（由 `tools/import_pilot_questions.py` 硬校验）：
- 末条真实消息必须是 HER（除明确允许不回的外，仍建议末条 HER）。
- `role` 只能 HER/ME；IDEA/备注走 `input.note`；`speaker` 由机制映射（HER=PARTNER/ME=USER）。
- `initial_kb` 值必须标量（嵌套/容器键拆平面）；dotted key（如 `her.偏好`）原版机制不消费，禁止用作共享资料。
- 时间单调、时钟格式 `YYYY-MM-DD HH:MM`；case_id 唯一非空。
- 相对时间（明天/下周二）用 clock 解析，事件所指日期不等于发言日。
