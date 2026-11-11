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
- resp_id: `chatcmpl-c2d09287-a4b7-4a33-a647-d86345360f6e`
- finish_reason: `stop`
- usage_present: `True`
- content_chars: `2`

