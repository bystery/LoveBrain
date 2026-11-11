# -*- coding: utf-8 -*-
"""C1 deep content extractor: supporting chars, date-weekday pairs, money/numbers."""
import json, os, re, datetime

WAVE = "benchmark/stories_v2/wave5_selection"
SIDS = ["E339","E340","E341","E342","E343","E344","E345","E346","E347","E348"]
WEEKDAYS_ZH = ["周一","周二","周三","周四","周五","周六","周日"]

def load(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)

def all_text(ep):
    parts = []
    for t in ep["turns"]:
        for m in t["input"]["messages"]:
            parts.append(m["text"])
    for k,v in (ep.get("initial_kb") or {}).items():
        parts.append("[KB."+k+"] "+v)
    return "\n".join(parts)

WD = {"周一":0,"周二":1,"周三":2,"周四":3,"周五":4,"周六":5,"周日":6,
      "星期一":0,"星期二":1,"星期三":2,"星期四":3,"星期五":4,"星期六":5,"星期日":6}

for sid in SIDS:
    d = os.path.join(WAVE, sid)
    ep = load(os.path.join(d,"episode.json"))
    ref = load(os.path.join(d,"reference.json"))
    tl = load(os.path.join(d,"timeline.json"))
    txt = all_text(ep)
    print("\n========== %s ==========" % sid)
    # dates
    dates = set(re.findall(r"(?:20\d{2}年)?(\d{1,2})月(\d{1,2})日?", txt))
    print("dates found:", sorted(dates))
    # weekday mentions with year anchor from first clock
    yr = int(ep["turns"][0]["input"]["clock"][:4])
    print("clock year:", yr)
    wdpairs = []
    for m in re.finditer(r"(\d{1,2})月(\d{1,2})日?", txt):
        mm,dd = int(m.group(1)), int(m.group(2))
        try:
            that = datetime.date(yr, mm, dd)
            wdpairs.append((mm,dd,that.strftime("%a"), WEEKDAYS_ZH[that.weekday()]))
        except ValueError:
            pass
    print("date->actual-weekday:", wdpairs[:12])
    # weekday words in text
    wdw = [(w, WD[w]) for w in WD if w in txt]
    print("weekday-words in text:", wdw)
    # money/numbers
    money = re.findall(r"\d+(?:\.\d+)?(?:块|元|毛|分|万元|万|百|千|块)", txt)
    nums = re.findall(r"\b\d+(?:\.\d+)?\b", txt)
    print("money tokens:", money[:20])
    # supporting characters: names with title 老/小/阿 + 2-char surnames like 陈蕃
    sup = set()
    for pat in [r"老[^\s，。、：！？]{1,2}", r"小[^\s，。、：！？]{1,2}", r"阿[^\s，。、：！？]{1,2}",
                r"(?:陈|周|吴|郑|王|冯|蒋|韩|沈|曹)[^\s，。、：！？]{1,2}"]:
        for m in re.finditer(pat, txt):
            sup.add(m.group(0))
    print("potential supporting chars:", sorted(sup))
