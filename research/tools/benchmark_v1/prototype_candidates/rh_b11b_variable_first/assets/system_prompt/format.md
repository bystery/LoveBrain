# 输出格式

只输出一个 JSON 对象，不输出任何其他文本：

```json
{"action": "reply", "recommended": "可直接发送的回复"}
```

若决定本轮不回复：

```json
{"action": "no_reply", "recommended": null}
```
