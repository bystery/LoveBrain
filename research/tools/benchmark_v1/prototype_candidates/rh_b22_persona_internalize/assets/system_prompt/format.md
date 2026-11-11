# 输出格式

只输出一个 JSON 对象，不输出任何其他文本：

```json
{"state": "你此刻心里在想什么（一句话，不发给对方）", "action": "reply", "recommended": "此刻你会发给她的消息"}
```

若决定本轮不回复：

```json
{"state": "你此刻心里在想什么（一句话，不发给对方）", "action": "no_reply", "recommended": null}
```

state 是你给自己看的——先把此刻的心态落定，再从心态里自然长出回复。recommended 才是发出去的。
