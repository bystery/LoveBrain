"""attribution_rules.py — W22-01 归因锚定检测（纯函数，零模型调用）。

触发：HER 消息（历史+本轮）含第一人称创作/承诺/决定表述（cues 命中 + 句长约束），
取最新 ≤MAX_ITEMS 条、整段 ≤BUDGET CU（len 近似 utf16，中文字符≈1 单位）、逐字引用；
无触发返回空串 → prompt 与父本逐字节相同。
 cue 词表为保守起点，触发面门 (10%,90%] 在快车道/全量批实测报告，不得跑后改词表换触发面。
"""
import re

CUES = ("做完了", "写好了", "做好了", "我做的", "我写的", "我定的", "我答应",
        "我请", "我整理", "我查好", "我买了", "我办了", "我改了", "我加了",
        "我做完了", "我排好了", "抽出来了", "定日子了", "报名成功", "我做了一")
BUDGET = 120
MAX_ITEMS = 3


def _sentences(text):
    return [x.strip() for x in re.split(r"[。！？!?；;\n]", text or "") if x.strip()]


def build_block(prior_events, cur, seq):
    """返回 (block_str, kept_lines, cu)。"""
    her_msgs = [e for e in (prior_events or [])
                if str(e.get("role") or "").upper().startswith("HER")]
    her_msgs += [m for m in (cur or [])
                 if str(m.get("role") or "").upper().startswith("HER")]
    items, seen = [], set()
    for e in reversed(her_msgs):  # 最新优先
        text = e.get("text") or ""
        turn = e.get("turn")
        for sent in _sentences(text):
            if not any(c in sent for c in CUES):
                continue
            if len(sent) < 6 or len(sent) > 80:
                continue
            key = sent[:40]
            if key in seen:
                continue
            seen.add(key)
            if e in (cur or []):
                tn = "本轮"
            elif turn:
                tn = "T%d" % turn
            else:
                tn = "历史"
            items.append((sent, tn))
            break  # 每条消息最多取一句
        if len(items) >= MAX_ITEMS:
            break
    if not items:
        return "", [], 0
    kept, cu = [], 0
    for sent, tn in items:
        line = "- 「%s」（%s）" % (sent, tn)
        c = len(line)
        if cu + c > BUDGET:
            break
        kept.append(line)
        cu += c
    if not kept:
        return "", [], 0
    head = "# 【归属锚定：以下事项的作者/承诺人/决定人以 HER 原话为准，不得移为己出】"
    tail = "口径：提及上述事项时保持 HER 为作者/承诺人/决定人；你的参与只以“我帮/我陪/我建议”表述。"
    return "\n".join([head] + kept + [tail]), kept, cu
