# -*- coding: utf-8 -*-
"""expr_audit.py — 表达缺陷族词面审计（评估专用，零真实调用）。

检测对象 = 已解析回复 JSON 中的【可发送正文】：
  response.recommended / bad_boy / playful / warm + directions 非 null 字符串。

四族（与 refs/L3B_REPLY_DEFECTS_20261020.md 对齐，模式为通用词面、非话题词表）：
  F1 军师视角：教用户"怎么对她/对第三方说"的指令句式混入正文；
     ——v9 复核 §3.3 口径修正：HER 在问怎么应对第三方时，"你就跟她说你对猫毛
     过敏"可以是给 HER 的自然建议（"你"=收信人、"她"=第三方）。词面命中
     只作初筛，须按"收信人与代词指向是否一致"人工判读，不凭句式定罪。
  F2 ME 经历编造：已发生式陈述我的习惯/经历（词面可疑标记，语义出处需人工判读）；
  F3 他人动机断言：把第三方/她的动机、评价、过往行为写成断言；
  F4 旧事件主体错误：词面不可判定——登记为 manual_review 项，工具只提示存在
     旧事件引用（"之前/上次/上次"类回指 + 事件内容），由判读人工核对主语。

定位说明：词面重合提示 ≠ 因果判定（与 retr_report.py 同一口径）。
F2/F3 命中表示"需要人工确认的可疑表达"，不自动定罪。

用法：
  python tools/expr_audit.py <reply_raw.txt | 目录...>   # CLI，打印 JSON 结果
  import expr_audit; expr_audit.audit_parsed(obj)        # 库内调用
"""
import json
import os
import re
import sys

# 每族：[(正则, 简述)]。模式为通用语用标记，不含猫/过敏/食堂等话题实体。
FAMILIES = {
    "F1_advisor_voice": [
        (r"你就(说|跟她说|跟她讲|回她|回我?直接)",
         "建议或指令句式—收信人一致性需人工判读"),
        (r"你(先|要|得|直接)?跟(她|他)商量", "指导用户与第三方交涉"),
        (r"你打算怎么(跟|和|与)(她|他)说", "询问用户策略=军师视角"),
        (r"让她(去|自己|先|别|直接|把)", "调度第三方的指令"),
        (r"(定时|定期)(让|安排)", "对第三方的调度指令"),
        (r"你(就|要|先)?(直接)?(说|讲)(不|就行|就完)", "教用户开口句式"),
    ],
    "F2_me_fabrication": [
        (r"我(一般|通常|向来|从来|基本都是?|肯定)(?!说)", "已发生式习惯陈述"),
        (r"我(上次|上回|之前)(?!说|提|跟你)", "过往经历回指"),
        (r"我只?(吃|去|用|点)[^，。；]{0,8}(的|惯)", "排他式偏好陈述"),
        (r"我就(换|撤|不去|走)", "习惯性行为陈述"),
    ],
    "F3_motive_assertion": [
        (r"(她|他|室友)可(不管|不会管|哪管)", "第三方态度断言"),
        (r"(她|他|室友)(就是|明明|肯定|显然)(是|知道|故意|想)", "动机/知情断言"),
        (r"(是|属于)故意", "动机断言"),
        (r"(她|他)没(说|提)过.{0,6}(还|却)", "把未说过的话放回对方口中"),
    ],
}
F4_HINT = re.compile(r"(之前|上次|上回|那次)[^，。；]{0,20}(她|它|自己)")


def _sendable_texts(parsed):
    """从已解析 JSON 抽取可发送正文 [(field, text)]。"""
    out = []
    resp = parsed.get("response")
    if isinstance(resp, dict):
        for k in ("recommended", "bad_boy", "playful", "warm"):
            v = resp.get(k)
            if isinstance(v, str) and v.strip():
                out.append(("response." + k, v))
    dirs = parsed.get("directions")
    if isinstance(dirs, list):
        for i, v in enumerate(dirs):
            if isinstance(v, str) and v.strip():
                out.append(("directions[%d]" % i, v))
    return out


def audit_parsed(parsed):
    """对已解析 JSON 审计，返回 findings 列表。"""
    findings = []
    for field, text in _sendable_texts(parsed):
        for fam, pats in FAMILIES.items():
            for pat, label in pats:
                m = re.search(pat, text)
                if m:
                    findings.append({"family": fam, "field": field,
                                     "pattern": pat, "match": m.group(0),
                                     "label": label, "text": text})
        if F4_HINT.search(text):
            findings.append({"family": "F4_subject_manual_review",
                             "field": field, "pattern": F4_HINT.pattern,
                             "match": F4_HINT.search(text).group(0),
                             "label": "旧事件回指—主语需人工核对", "text": text})
    return findings


def audit_raw(raw):
    """对原始回复文本审计。坏 JSON → parse_error 结果（不抛异常）。"""
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError as e:
        return {"parse_ok": False, "parse_error": "%s (col %d)" % (e.msg, e.colno),
                "findings": [], "n_findings": 0}
    f = audit_parsed(parsed)
    return {"parse_ok": True, "parse_error": None,
            "findings": f, "n_findings": len(f)}


def _iter_targets(paths):
    for p in paths:
        if os.path.isfile(p):
            yield p
        elif os.path.isdir(p):
            for root, _dirs, files in os.walk(p):
                if "reply_raw.txt" in files:
                    yield os.path.join(root, "reply_raw.txt")


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    results = []
    for p in _iter_targets(argv[1:]):
        raw = open(p, encoding="utf-8").read()
        r = audit_raw(raw)
        r["path"] = os.path.relpath(p)
        results.append(r)
    n_bad = sum(1 for r in results if r["n_findings"])
    json.dump(results, sys.stdout, ensure_ascii=False, indent=1)
    print("\n", file=sys.stderr)
    print("audited=%d with_findings=%d total_findings=%d"
          % (len(results), n_bad, sum(r["n_findings"] for r in results)),
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
