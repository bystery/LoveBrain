#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""C2 arithmetic & date/weekday verification for E349-E358.
Extracts numbers (money/quantity) and weekday/date refs from episode text,
cross-checks against python datetime for date->weekday consistency.
Read-only, no model calls.
"""
import json, os, re, datetime

ROOT = "D:/lovebrain-arena/benchmark/stories_v2/wave5_selection"

def load(p):
    with open(p,"r",encoding="utf-8") as f: return json.load(f)

CN_WD = {"周一":0,"周二":1,"周三":2,"周四":3,"周五":4,"周六":5,"周日":6,
         "星期一":0,"星期二":1,"星期三":2,"星期四":3,"星期五":4,"星期六":5,"星期日":6}

def weekday_cn(d):
    return ["周一","周二","周三","周四","周五","周六","周日"][d.weekday()]

def scan_story(eid):
    d = os.path.join(ROOT, eid)
    ep = load(os.path.join(d,"episode.json"))
    findings = []
    # collect all message text per turn with ts/clock
    for t in ep["turns"]:
        cid = t["case_id"]
        clk = t["input"]["clock"]
        text = "\n".join(m["text"] for m in t["input"]["messages"])
        # weekday mentions
        for w,idx in CN_WD.items():
            if w in text:
                # find any date YYYY-MM-DD near
                dates = re.findall(r"(20\d\d[-/年]\d{1,2}[-/月]\d{1,2})", text)
                findings.append((cid,"weekday",w,clk,dates))
        # relative date refs
        for kw in ["今晚","明晚","今晚","昨天","今天","明天","前天","后天","下周","这周","上周"]:
            if kw in text:
                findings.append((cid,"rel",kw,clk,None))
        # money/quantity: 块/元/万/仟/百/吨/公里/米/分钟/小时/岁
        for pat,label in [(r"(\d+(?:\.\d+)?)\s*块","块"),(r"(\d+(?:\.\d+)?)\s*元","元"),
                          (r"(\d+(?:\.\d+)?)\s*万","万"),(r"(\d+)\s*岁","岁"),
                          (r"(\d+)\s*分钟","分钟"),(r"(\d+)\s*小时","小时"),
                          (r"(\d+)\s*公里","公里"),(r"(\d+)\s*米","米"),
                          (r"(\d+)\s*趟","趟"),(r"(\d+)\s*天","天"),
                          (r"(\d+)\s*遍","遍"),(r"(\d+)\s*回","回"),
                          (r"(\d+)\s*份","份"),(r"(\d+)\s*条","条"),
                          (r"(\d+)\s*罐","罐"),(r"(\d+)\s*期","期"),
                          (r"(\d+)\s*批","批"),(r"(\d+)\s*年","年"),
                          (r"(\d+)\s*次","次")]:
            for m in re.finditer(pat,text):
                findings.append((cid,"num",label,m.group(1),clk))
    # date->weekday cross-check: scan clock dates
    date_wd = []
    prev = None
    for t in ep["turns"]:
        clk = t["input"]["clock"]
        dt = datetime.datetime.strptime(clk,"%Y-%m-%d %H:%M")
        date_wd.append((t["case_id"],clk,weekday_cn(dt)))
        if prev and dt < prev:
            date_wd.append((t["case_id"],"CLOCK_BACKWARDS",clk))
        prev = dt
    return findings, date_wd

def main():
    stories = ["E349","E350","E351","E352","E353","E354","E355","E356","E357","E358"]
    out = {}
    for eid in stories:
        f, dw = scan_story(eid)
        out[eid] = {"findings": f, "date_weekday": dw}
        print(f"\n=== {eid} ===")
        print("clock->weekday:")
        for r in dw:
            print("  ", r)
        nums = [(c,l,v,k) for (c,k2,l,v,k) in f if k2=="num"]
        if nums:
            print("numbers found:")
            for c,l,v,k in nums:
                print(f"   {c} {l}={v} (clock {k})")
        rels = [(c,l,k) for (c,k2,l,k,d) in f if k2=="rel"]
        if rels:
            print("relative date refs:")
            for c,l,k in rels:
                print(f"   {c} {l} (clock {k})")
        wds = [(c,w,k,ds) for (c,k2,w,k,ds) in f if k2=="weekday"]
        if wds:
            print("weekday mentions:")
            for c,w,k,ds in wds:
                print(f"   {c} {w} (clock {k}) near dates {ds}")
    with open("D:/lovebrain-arena/refs/benchmark_v1_20261107/phase3/_audit_c2_arith.json","w",encoding="utf-8") as fo:
        json.dump(out,fo,ensure_ascii=False,indent=1,default=str)
        fo.write("\n")
    print("\nWrote _audit_c2_arith.json")

if __name__=="__main__":
    main()
