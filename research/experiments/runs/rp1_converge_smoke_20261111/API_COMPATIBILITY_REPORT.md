# API 兼容性检查报告（provider_check_v1）

- base: `https://api.llm.ustc.edu.cn/v1`
- 目标模型: `deepseek-flash-2`
- Key 状态: 已配置（内容不入任何报告）
- **结论: OK**


## step: list_models
- http: `200`
- n_models: `12`
- target_present: `True`
- target_matches: `['deepseek-flash-2']`

## step: minimal_chat
- http: `200`
- resp_model: `deepseek-flash-2`
- resp_id: `chatcmpl-1a37b3a6-5c98-4497-968b-0fa54956f3a7`
- finish_reason: `stop`
- usage_present: `True`
- content_chars: `2`

