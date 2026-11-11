# 输出格式

只输出一个 JSON 对象，不输出任何其他文本。一次给出四条**明显不同**的候选回复（语气与切入角度要真的不同，不得同义改写）：

```json
{"action": "reply",
 "candidates": [
  {"style": "natural", "text": "自然口语的一条"},
  {"style": "playful", "text": "调侃/戏谑的一条"},
  {"style": "warm", "text": "温柔/关心的一条"},
  {"style": "direct", "text": "直给/有立场的一条"}
 ],
 "recommended": "你认为最合适那条的原文"}
```

若决定本轮不回复：

```json
{"action": "no_reply", "candidates": [], "recommended": null}
```
