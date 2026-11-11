# -*- coding: utf-8 -*-
"""BATCH-V5-CTX-20261025 收口产物（D 包）。

输入：V5_EXTRACTED_OUTPUTS.json（48 首选+全部备选原文）、
V5_INDEPENDENT_RESULTS.json（独立复算资源/等待/遵守率）、三张题卡副本。

输出：V5_PAIRING_LAYERS.json（逐对四层判定，可审计）
      V5_VERDICT.md（判决+资源/等待配对+维护负担+口径）
      V5_KEY_PUBLIC_GROUPS.md（<=6 组公开对照，全文不截断，含必要前情）

判定层彼此独立，不合并成一个 supports：
  layer1 primary_content —— 只判首选正文（事实/主体/状态、视角、目标反馈动作、
                              下一回合入口、适时收尾）
  layer2 delivery_contract —— 首选能否原样发送（schema/veto/可发性）
  layer3 backup_risk —— 备选槽的越界/编造/归属/军师指令/语气风险（带严重度）
  layer4 resource_wait —— 该位真实 token 与等待（程序取数，非人工）
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REF = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                   "v5_next_batch_20261025")
OUT = os.path.join(REF, "V5_PAIRING_LAYERS.json")
VERDICT = os.path.join(REF, "V5_VERDICT.md")
GROUPS = os.path.join(REF, "V5_KEY_PUBLIC_GROUPS.md")

rows = json.load(open(os.path.join(REF, "V5_EXTRACTED_OUTPUTS.json"),
                      encoding="utf-8"))
ind = json.load(open(os.path.join(REF, "V5_INDEPENDENT_RESULTS.json"),
                     encoding="utf-8"))
IDX = {(r["case_id"], r["arm"]): r for r in rows}
CARDS = {ep: json.load(open(os.path.join(ROOT, "logs", "v5_ctx_live_20261025",
                                         "parent_" + ep, ep + ".copy.json"),
                            encoding="utf-8"))
         for ep in ("E007", "E008", "E009")}

# ---------------------------------------------------------------- 人工判定
# layer1：首选正文（winner ∈ parent/candidate/tie）
PRIMARY = {
    "E007_T01": ("tie", "两臂都先接情绪、未抛约会；v5 多问一句“打算往哪跑”仍贴话题。"),
    "E007_T02": ("tie", "两臂都按她的口味筛场并交回选择权；无催定。"),
    "E007_T03": ("parent", "两臂都没假装买票，但 v5 首选把那次现场声学说成已知（“中间还能听见彼此说话”），"
                           "parent 停在曲风层（“基本没人声，一层一层铺上来”），断言强度更低。"),
    "E007_T04": ("parent", "note 要求记成暂定别催；parent“你看着来，定了跟我说一声”纯交回，"
                           "v5“真要来我这边把票弄好”多加一层我来办的承接，接近替她决定。"),
    "E007_T05": ("tie", "旧目标未被绑架：两臂首选都顺着新话题答周末，没把 livehouse 拽回来。"),
    "E007_T06": ("tie", "两臂都接住“票我自己买”并只提新建议（v5 自己买一张、parent 回头给两个选项）；"
                        "v5 首选夹带“后摇那种一开场就安静下来”的曲风断言，属轻度外推。"),
    "E007_T07": ("tie", "都是轻松收尾不再前推；parent 程序分 8.33 扣在 style_diversity（首选与 warm 相近），"
                        "不是内容缺陷，不计入本层胜负。"),
    "E007_T08": ("tie", "两臂首选都把同事的话当第三方观点、就事论事答耳塞，没安到她头上。"),
    "E008_T01": ("tie", "都按“妹妹的事”回应心意，未越界安排。"),
    "E008_T02": ("tie", "都没替她认领“太惯着”，也未贬其妹。"),
    "E008_T03": ("tie", "两臂都没编读书会，也没借用 T04 的“同事介绍”答案；parent 说“这话我没说过”"
                        "偏窗口可核，v5 说“我跟小林不是那么认识的”略进一步自证相识方式，"
                        "仍属否定，判平并列观察。"),
    "E008_T04": ("candidate", "note 要“可领情但别当成她本人表态”：v5 首选明确分开来源并问她“你自己觉得呢”，"
                             "parent 首选把人情记到小林账上（来源正确）但没回到她本人立场。"),
    "E008_T05": ("tie", "都当作改期而非拒绝，无阴阳。"),
    "E008_T06": ("candidate", "她连说两遍“就我 不带我妹”：v5 首选原样点出重复并确认主体，再问时间；"
                             "parent 首选“就你一个”主体正确但把她强调的话复述得较硬。"),
    "E008_T07": ("tie", "两臂首选都安抚“破费”且未自作主张摆桌。"),
    "E008_T08": ("tie", "两臂首选都接住再改期、不把“约别人也行”当邀请、后续交给她主导。"),
    "E009_T01": ("candidate", "parent 首选把“2026-11-14或2026-11-15”原样写进要发给她的话（日期由时钟推得正确，"
                             "但机器格式直发不自然）；v5 首选“你周六还是周日在家”同样有据且可直接发送。"),
    "E009_T02": ("tie", "都停在暂定，未擅自定地点。"),
    "E009_T03": ("tie", "都如实松绑，既不阴阳也不硬撑“我随时都行”。"),
    "E009_T04": ("tie", "都按“这次真定下来”确认周日并推进细节；v5 提出先备镜像属新提议。"),
    "E009_T05": ("tie", "语气歧义位：两臂都按“不用讲究”轻松承接，未擅自安排带东西，也未被同事转述牵着认领。"),
    "E009_T06": ("tie", "都据回顾完成、不邀功不顺势表白。"),
    "E009_T07": ("tie", "都是轻松收尾、无过度承诺。"),
    "E009_T08": ("tie", "都把新事项主体分清是妹妹（借她的口），答应但不加码成她本人的求助。"),
}

# layer3：备选槽风险（arm -> [(case, slot, 严重度, 说明)]）
BACKUP_RISK = {
    "parent": [
        ("E007_T08", "directions[1]", "high",
         "“那天阵容里有两支偏躁的，但也不是金属那种”——本场阵容无任何输入支持，属具体事实编造"),
        ("E007_T08", "directions[2]", "medium",
         "“我一般不带，后摇想听清每个层次”——自称既往现场习惯，输入无此经历"),
        ("E009_T01", "directions[3]", "medium",
         "“要不2026-11-14下午”——机器日期直接进可发消息"),
        ("E007_T06", "directions[1]", "low",
         "“后摇配不上太吵的地方”——曲风层概括断言，轻度外推"),
    ],
    "candidate": [
        ("E007_T03", "directions[2]", "medium",
         "“想跟你一起去听一次，这话我记挺久了”——自称早已存在的内心想法，输入无支持"),
        ("E008_T03", "directions[3]", "medium",
         "“昨天你问小林说你太惯你妹”——把“她转述小林的话”压成易误读的归属句"),
        ("E008_T07", "response.playful", "medium",
         "“小林真要来，我也只给他点杯白水”——note 要求别把小林拉进这场，玩笑仍把第三方拉进来"),
        ("E008_T08", "response.bad_boy", "medium",
         "“约别人也行？行，那我先记着这句”——接近赌气，note 明确禁止阴阳"),
        ("E009_T08", "directions[0]", "medium",
         "“就让她妹直接跟我讲”——主体错位（要讲话的就是妹妹本人），指代混乱"),
        ("E009_T05", "response.bad_boy", "low",
         "“同事都这么说了”——把第三人转述当成既定依据轻带一步"),
        ("E008_T08", "directions[1]", "low",
         "“监考站一天挺熬人的”——把未给定的时长当已知"),
    ],
}

# layer2：交付契约（veto 事实，程序侧）
CONTRACT_FAIL = {
    "parent": [("E007_T05", "illegal_source_id:t4_1",
                "首选正文本身可发；整位被判 0 分来自 analysis.scene_facts 引了历史轮标记 id"
                "（<!-- round:msgIds:t4_0,t4_1 -->），而声明的来源 id 集合只有本轮 <chat> 的 m0。")],
    "candidate": [("E007_T08", "json_schema_broken",
                   "键名被写成 bad_bo+U+200B+y（零宽空格），JSON 可解析但必需键缺失→整位判 0；"
                   "八槽内容都完整，analysis={} 仍守约。")],
}

GROUP_IDS = ["E007_T03", "E007_T05", "E007_T08", "E008_T04", "E008_T07", "E009_T01"]


def slot_of(case, arm, want):
    r = IDX[(case, arm)]
    for s in r["all_texts"]:
        if s["slot"] == want:
            return s["text"]
    return None


def prior_lines(case):
    ep = case.split("_")[0]
    for t in CARDS[ep]["turns"]:
        if t["case_id"] == case:
            ti = t["input"]
            head = ["[%s] %s" % (m.get("ts"), ("她：" if m["role"] == "HER" else "我：") + m["text"])
                    for m in ti["messages"]]
            return ti.get("note"), ti.get("clock"), head
    return "", "", []


def earlier_turns(case):
    """同故事此前轮次——两臂提示词里都作为历史出现（原版【最近对话】/v5【真实历史消息】），
    公开对照必须带上，否则读者看不到判定所依据的上下文。"""
    ep = case.split("_")[0]
    out, idx = [], [t["case_id"] for t in CARDS[ep]["turns"]].index(case)
    for t in CARDS[ep]["turns"][:idx]:
        for m in t["input"]["messages"]:
            out.append("- %s ｜ %s ｜ %s" % (
                t["case_id"], m.get("ts"),
                ("她：" if m["role"] == "HER" else "我：") + m["text"]))
    return out


# ---------------------------------------------------------------- 组装四层
pairs = []
for ep in ("E007", "E008", "E009"):
    for t in CARDS[ep]["turns"]:
        cid = t["case_id"]
        p, c = IDX[(cid, "parent")], IDX[(cid, "candidate")]
        winner, why = PRIMARY[cid]
        pveto = [x for x in CONTRACT_FAIL["parent"] if x[0] == cid]
        cveto = [x for x in CONTRACT_FAIL["candidate"] if x[0] == cid]
        pairs.append({
            "case_id": cid,
            "episode": ep,
            "note": t["input"].get("note"),
            "layer1_primary_content": {"winner": winner, "rationale": why},
            "layer2_delivery_contract": {
                "parent": {"score10": p["score10"], "veto": p["veto"],
                           "sendable_recommended": not p["veto"],
                           "detail": pveto[0][2] if pveto else ""},
                "candidate": {"score10": c["score10"], "veto": c["veto"],
                              "sendable_recommended": not c["veto"],
                              "detail": cveto[0][2] if cveto else ""},
            },
            "layer3_backup_risk": {
                "parent": [x for x in BACKUP_RISK["parent"] if x[0] == cid],
                "candidate": [x for x in BACKUP_RISK["candidate"] if x[0] == cid],
            },
            "layer4_resource_wait": {
                "parent": {"prompt": p["tokens"]["prompt"], "completion": p["tokens"]["completion"],
                           "total": p["tokens"]["prompt"] + p["tokens"]["completion"],
                           "latency_ms": p["latency_ms"], "primary_chars": p["primary_chars"]},
                "candidate": {"prompt": c["tokens"]["prompt"], "completion": c["tokens"]["completion"],
                              "total": c["tokens"]["prompt"] + c["tokens"]["completion"],
                              "latency_ms": c["latency_ms"], "primary_chars": c["primary_chars"]},
            },
            "bind": {"parent_raw_sha256": p["raw_sha256"], "candidate_raw_sha256": c["raw_sha256"],
                     "parent_run_id": p["run_id"], "candidate_run_id": c["run_id"],
                     "case_input_sha256_parent": p["case_input_sha256"],
                     "case_input_sha256_candidate": c["case_input_sha256"]},
        })

cnt1 = {k: sum(1 for x in pairs if x["layer1_primary_content"]["winner"] == k)
        for k in ("parent", "candidate", "tie")}
cnt3 = {arm: {"high": 0, "medium": 0, "low": 0} for arm in ("parent", "candidate")}
for x in pairs:
    for arm in ("parent", "candidate"):
        for it in x["layer3_backup_risk"][arm]:
            cnt3[arm][it[2]] += 1

summary = {
    "batch_id": "BATCH-V5-CTX-20261025",
    "authorization_id": "AUTH-V5-CTX-20261025-01",
    "arms": {"parent": "reference_product_v1@6e65b6bc",
             "candidate": "v5_purpose_aligned@ef855510"},
    "real_model_calls": 48,
    "layers": {
        "layer1_primary_content_counts": cnt1,
        "layer2_delivery_contract_counts": {
            "parent_fail": len(CONTRACT_FAIL["parent"]),
            "candidate_fail": len(CONTRACT_FAIL["candidate"]),
            "failure_families": {"parent": "analysis 来源 id 越界（被删的那层）",
                                 "candidate": "必需键名混入零宽字符（协议输出层）"}},
        "layer3_backup_risk_counts": {arm: {"tagged": sum(cnt3[arm].values()), **cnt3[arm]}
                                      for arm in cnt3},
        "layer4_resource_wait": ind["metrics"],
    },
    "shared_observations": {
        "coach_instruction_leak": {"parent": 0, "candidate": 0,
                                   "note": "r2 曾出现的“你可以这样发”类指令在本批两臂 0 命中（正则族扫描），"
                                           "不能把它记作 v5 单独修好的功劳。"},
        "legal_null_never_exercised": {"parent": 0, "candidate": 0,
                                       "note": "48/48 两臂都填满四风格+四方向，合法不回选项一次都没用；"
                                               "不能宣称任一臂更尊重 null 语义。"},
        "iso_date_in_sendable_text": {"parent": 2, "candidate": 0},
        "empty_analysis_compliance": ind["empty_analysis_compliance_v5"],
        "primary_mean_chars": {"parent": ind["metrics"]["parent"]["primary_mean_chars"],
                              "candidate": ind["metrics"]["candidate"]["primary_mean_chars"]},
    },
    "not_counted_as_evidence": [
        "程序均分（parent 9.51 / candidate 9.58）与逐位程序胜负（parent1/candidate2/tie21）"
        "不作为质量胜负依据，只作契约层参考。",
        "candidate illegal_source_slots=0 主要是因为不再输出 scene_facts，属作用域变化非能力胜出。",
        "两臂在不同故事上跑过（r2=E007/E010/E012，v5=E007/E008/E009），不做跨批数字相减。",
        "首选字符数 +50.45% 不构成优劣（本批不设回复长度目标）。",
    ],
    "pairs": pairs,
}
json.dump(summary, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("WROTE", OUT)
print("L1 counts", cnt1)
print("L2 fails  parent=%d candidate=%d" % (len(CONTRACT_FAIL["parent"]),
                                            len(CONTRACT_FAIL["candidate"])))
print("L3 counts", json.dumps(summary["layers"]["layer3_backup_risk_counts"],
                              ensure_ascii=False))
m = ind["metrics"]
print("L4 totals parent=%d candidate=%d  pct=%s" % (
    m["parent"]["total_tokens"], m["candidate"]["total_tokens"],
    json.dumps(ind["candidate_vs_parent_percent"])))

# ---------------------------------------------------------------- 公开对照
GROUP_LABEL = {
    "E007_T03": "口味回扣：谁把没查过的现场声学说成已知",
    "E007_T05": "交付契约失败族①：原版 analysis 引用历史轮 id",
    "E007_T08": "交付契约失败族②：v5 必需键名混入零宽字符（同位原版还编了阵容）",
    "E008_T04": "第三人转述：把小林的话和她本人的表态分开",
    "E008_T07": "备选越界：note 说别把小林拉进来，玩笑还是拉进来了",
    "E009_T01": "可发性：机器日期原样写进要发给她的话",
}

lines = ["# v5 批重点公开对照（6 组，供你裁语气）", "",
         "2026-10-25，Asia/Shanghai。全文不截断；含该位必要前情与两臂全部槽位原文。",
         "选择规则：每组都指向一个已在四层判定里记录的实际分歧，两臂各占优的方向都收进来，"
         "不是只挑 v5 好看的。程序分只列不作结论。", ""]
for cid in GROUP_IDS:
    note, clock, head = prior_lines(cid)
    p, c = IDX[(cid, "parent")], IDX[(cid, "candidate")]
    w, why = PRIMARY[cid]
    ep = cid.split("_")[0]
    lines += ["## %s · %s" % (cid, GROUP_LABEL[cid]), "",
              "题卡 %s 人设：我=%s ｜ 她=%s" % (
                  ep, CARDS[ep]["persona"]["me"], CARDS[ep]["persona"]["her"]),
              "公开种子 initial_kb：%s" % json.dumps(
                  CARDS[ep]["initial_kb"], ensure_ascii=False),
              "", "同故事前情（此前轮次原文；两臂提示词都带这段历史）："]
    lines += (earlier_turns(cid) or ["- （本题第一轮，无前情）"])
    lines += ["", "本轮输入（当前窗口，按序）："]
    lines += ["- " + h for h in head]
    lines += ["", "军师备注：%s" % note, "虚拟当前时间：%s" % clock, "",
              "| 臂 | 程序分 | veto | prompt/completion tok | 等待 ms | 首选字数 |",
              "|---|---|---|---|---|---|",
              "| 原版 reference_product_v1 | %s | %s | %d/%d | %d | %d |" % (
                  p["score10"], p["veto"] or "—", p["tokens"]["prompt"],
                  p["tokens"]["completion"], p["latency_ms"], p["primary_chars"]),
              "| v5_purpose_aligned | %s | %s | %d/%d | %d | %d |" % (
                  c["score10"], c["veto"] or "—", c["tokens"]["prompt"],
                  c["tokens"]["completion"], c["latency_ms"], c["primary_chars"]),
              "", "**首选内容层判定：%s** —— %s" % (w, why), "", "原版全部输出："]
    lines += ["- `%s` %s" % (s["slot"], s["text"]) for s in p["all_texts"]]
    lines += ["", "v5 全部输出："]
    lines += ["- `%s` %s" % (s["slot"], s["text"]) for s in c["all_texts"]]
    tag = [x for x in (BACKUP_RISK["parent"] + BACKUP_RISK["candidate"])
           if x[0] == cid]
    if tag:
        lines += ["", "本位已记录的备选风险："]
        lines += ["- %s ｜ %s ｜ %s ｜ %s" % t for t in tag]
    cf = [x for x in (CONTRACT_FAIL["parent"] + CONTRACT_FAIL["candidate"])
          if x[0] == cid]
    if cf:
        lines += ["", "本位已记录的契约失败："]
        lines += ["- %s：%s（%s）" % (x[0], x[1], x[2]) for x in cf]
    lines.append("")
open(GROUPS, "w", encoding="utf-8").write("\n".join(lines))
print("WROTE", GROUPS, len(lines), "lines")

# ---------------------------------------------------------------- 判决书
M, C = ind["metrics"]["parent"], ind["metrics"]["candidate"]
PC = ind["candidate_vs_parent_percent"]
L1, L3 = cnt1, summary["layers"]["layer3_backup_risk_counts"]
tail = sorted((r for r in rows if r["arm"] == "candidate"),
              key=lambda r: -r["latency_ms"])[:4]
tail_txt = "、".join("%s（%s ms）" % (r["case_id"], format(r["latency_ms"], ","))
                    for r in tail)
parent_max = max(r["latency_ms"] for r in rows if r["arm"] == "parent")
V = f"""# BATCH-V5-CTX-20261025 收口判决：v5_purpose_aligned vs 原版

2026-10-25，Asia/Shanghai。真实调用 48 次已用尽并关闭授权；本文件是这一批的结果与判决，
不是准备清单。所有数字来自实跑账本的独立复算（`verify_real_batch_v5.py`，
`failed_checks=[]`），不是 capture 罐头、不是夹具。

## 0. 一句话判决

**保留 v5 方向（效率型开发候选），不替换原版。** 省资源成立、中位等待改善成立、
尾部等待没有改善；首选内容层两臂接近（{L1['parent']}:{L1['candidate']}，其余 {L1['tie']} 平），
两臂各出一次交付契约失败、且属不同故障族。所以是"质量相当、资源实质改善"的保留，
不是"新版整体更强"。

## 1. 这一批实际跑了什么

| 绑定项 | 值 |
|---|---|
| 批次 | BATCH-V5-CTX-20261025 |
| 授权 | AUTH-V5-CTX-20261025-01（用户 2026-10-25 09:37 原文批准，硬上限 48） |
| 原版臂 | reference_product_v1 @ 6e65b6bc…（身份未改，公平基线） |
| 候选臂 | v5_purpose_aligned @ ef855510…（mechanism 422ec227…，format fe5221b5…） |
| 题目 | E007/E008/E009 各 8 位 ×2 臂 = 48 位，独立故事、独立候选状态、逐位交错 |
| 端点 | http://127.0.0.1:17770/v1 · model=dsf · temperature=0.7（48/48 逐次核对） |
| 结果 | 48 started / 48 completed / 0 failed；顺序与冻结计划一致；无重试、无补跑、无第三臂、无自动 judge |
| 收口 | 账本已追加 authorization_closed；`AUTHORIZATION.json` 标 CLOSED |

关闭后不可复活的证据：现在重建 `AuthorizationBudget(ledger, aid, 48)` 会在构造第 185 行
`_check_scope` 直接抛 BudgetRefused（"已关闭…不得复用"），不是等到派发时才拦。

## 2. 资源与等待（真实逐位取数）

| 指标 | 原版 | v5 | 变化 |
|---|---|---|---|
| prompt tokens | {M['prompt_tokens']:,} | {C['prompt_tokens']:,} | {PC['prompt_tokens']:.2f}% |
| completion tokens | {M['completion_tokens']:,} | {C['completion_tokens']:,} | {PC['completion_tokens']:.2f}% |
| 总 tokens | {M['total_tokens']:,} | {C['total_tokens']:,} | {PC['total_tokens']:.2f}% |
| 调用次数 | {M['n']} 位 × 1 次 | {C['n']} 位 × 1 次 | 相同（每位单请求） |
| 等待 p50 | {M['latency_ms']['p50']:,} ms | {C['latency_ms']['p50']:,} ms | {PC['latency_p50_percent']:.2f}% |
| 等待 p95 | {M['latency_ms']['p95']:,} ms | {C['latency_ms']['p95']:,} ms | +{PC['latency_p95_percent']:.2f}%（变差） |
| 等待 max | {M['latency_ms']['max']:,} ms | {C['latency_ms']['max']:,} ms | v5 尾部更高 |
| 48 位累计等待 | {M['latency_ms']['sum']:,} ms | {C['latency_ms']['sum']:,} ms | +{PC['latency_sum_percent']:.2f}% |
| 首选平均字数 | {M['primary_mean_chars']:.2f} | {C['primary_mean_chars']:.2f} | +{PC['primary_mean_chars']:.2f}%（不设长度目标，不计优劣） |

**明确交易：总资源 −57.65%、中位等待 −17.65%，但 p95 +26.95%、累计等待持平（+0.26%）。**
v5 的 p95 由 {tail_txt} 这四个尾位拉出；原版最慢一位是 {parent_max:,} ms。
我没有分段计时（前处理/排队/解码未拆），所以**不解释尾部变长的原因**，只报观测值。
同一模型别名 dsf 下的真实出站；底层供应商模型未核（`underlying_provider_model_verified=false`），
不把两臂差异归因到模型实例。

## 3. 空 analysis 遵守率（实现遵从，不是聊天胜负）

v5 出站 system 每轮都要求 `analysis={{}}`；24 份答卷 **24 空 / 0 非空 / 0 解析坏**（分母 24）。
即"删掉这份没人消费的内部摘要"这条被模型确实执行了，且没有连带把别的槽位删坏。
原版 24 份全部照常输出完整 analysis（`analysis_empty=0/24`），这是设计差，不是原版违规。

completion 只降 {PC['completion_tokens']:.2f}%（{M['completion_tokens']:,}→{C['completion_tokens']:,}），
低于早先按字符比例估的 ~41.87%：**那不是 token 承诺**，实测就是这个数。
省的大头在 prompt（{PC['prompt_tokens']:.2f}%），来自上下文组织改造，不来自删 analysis。

## 4. 质量四层判定（逐位记录在 `V5_PAIRING_LAYERS.json`，可审计）

**层1 首选内容**（事实/主体/状态、正文视角、目标与反馈动作、有效下一回合入口、适时收尾）：
原版 {L1['parent']} 胜、v5 {L1['candidate']} 胜、{L1['tie']} 平。
v5 三胜：E008_T04（把小林转述与她本人表态分开并回问她）、E008_T06（原样接住她连说两遍的
"就我 不带我妹"）、E009_T01（原版把 2026-11-14/11-15 这种机器日期写进要发的话）。
原版两胜：E007_T03、E007_T04——都是"把没查过的说成已知/替她多承接一步"这一族。

**层2 交付契约**：各 1 次整位判 0，故障族不同。
原版 E007_T05 `illegal_source_id:t4_1`——analysis 引了历史轮标记里的 id，而声明可用的只有本轮
`m0`；首选正文本身可发，是**被删掉的那层**把它带进 0 分。
v5 E007_T08 `json_schema_broken`——键名被写成 `bad_bo`+U+200B+`y`，JSON 能解析、八槽内容都在、
`analysis={{}}` 仍守约，但必需键缺失→整位 0。这是**协议输出层的脆弱性**，属 v5 自己要面对的一族。
其余 46 个臂位（48 减两处，且两处落在不同位置：原版 E007_T05、v5 E007_T08）首选都可原样发送。

**层3 备选风险**（v5 {L3['candidate']['tagged']} 条 / 原版 {L3['parent']['tagged']} 条）：
原版 {L3['parent']['high']} 条高危——E007_T08 备选凭空给出"那天阵容里有两支偏躁的"（本场阵容无输入支持），
另有自称既往现场习惯（中）、机器日期进可发消息（中）、曲风概括外推（低）各 1 条。
v5 {L3['candidate']['high']} 条高危，{L3['candidate']['medium']} 条中危：E008_T07 玩笑把 note 明令别拉的
小林拉进来、E008_T08 "那我先记着这句"接近赌气、E009_T08 "让她妹直接跟我讲"主体指代错位、
E008_T03 归属句压成易误读、E007_T03 自称"这话我记挺久了"，另有 2 条低危外推。
读法：**v5 备选更常踩 note 的语气/边界，原版备选更常踩事实边界。** 两边都不能靠均分看见。

**共享观察（不许当任一臂的功劳）**：本批两臂"你可以这样发"类军师指令 0 命中（r2 出现过的那族没有复现，
原版也没犯，不能记作 v5 单独修好）；合法不回（null）两臂 48 位一次都没用，不能宣称谁更尊重 null 语义；
机器日期只在原版出现（2 处）。

**不作为证据**：程序均分原版 {M['program_mean10']:.2f} / v5 {C['program_mean10']:.2f}、逐位程序胜负
parent1/candidate2/tie21 只作契约层参考；v5 的 `illegal_source_slots=0` 主要因为不再输出 scene_facts，
属作用域变化不是能力胜出。

## 5. 故事级证据（3 个故事是 3 个样本单位，不是 24 个）

E007（后摇/livehouse，目标启停）：两臂都做到"她转向别的话题时不把旧邀约拽回来"（T05）、
"她重新接上时按她推进"（T06）、"说定后轻松收尾"（T07）、"同事的观点不安到她头上"（T08）。
两臂共同弱点集中在**现场/声学细节的外推**：T03 双方都把那次演说的更具体，v5 更硬；
T08 原版备选直接造了阵容。本批两臂各有一次整位 0 分都落在 E007（T05 原版、T08 v5）。

E008（妹妹/共同朋友/第三人归属）：T03 双方都没有编"读书会"，也没借用 T04 才出现的"同事介绍"答案；
T04 v5 首选是全场对"转述 vs 本人表态"处理最好的一处；T06 双方都接住了重复两遍的主体强调；
T07/T08 v5 各有一次备选踩 note。E008 没有出现冒领他人话语的严重错误。

E009（修电脑，事项状态机：暂定→回退→确定→完成→重开）：T02 都没把暂定当已定；T03 都没硬撑"我随时都行"；
T04 都按"这次真定下来"确认；T06 完成位都没邀功或顺势表白；T08 重开位都分清主体是妹妹。
本故事差异是原版 T01 的机器日期（首选+备选各一处）与 v5 T08 备选的指代错位。

## 6. 维护负担（按实际删掉的步骤说，不按文件数）

原版每轮出站后要走：解析 reply 取 `analysis` → 需要 analysis 才提交本轮
（`_extract_analysis` 返回 None 就 `writeback=skipped(no_analysis)`，什么都不写）→
`pm.record_round(...)` 把 recent/scene_facts/ongoing/topic 写进 memory 树 →
`ReferenceOngoingSelector.select_for_injection` 选【进行中事项】→ 渲染"场景状态链"→
维护 `alias_map` 给 source_ids 背书。

v5 删掉的正是这一整条"每轮必生成、但 harness 不消费"的产出与写回链：不解析 analysis、
不做 record_round、不选 ongoing、不渲染状态链；状态只剩真实事件列表（仅实际发送的消息）、
种子承诺 plans、本轮目标/note，snapshot 是一份 JSON。
代价也如实：v5 并没有比原版"东西更少"——它仍带事件记忆、目标作用域、四类 note、三条装配护栏
和两块预算（历史 1400 / 目标 600 码元），只是把那圈摘要写回换成了这些。
观测到的直接后果：原版唯一的交付失败恰好出在被删的那层（source_ids 越界）。

## 7. 本批不成立/不宣称的

不宣称 v5 更会聊、更快、已证明质量不退化；不宣称单独删 analysis 带来这些收益（本批是整套 v5 对原版）；
不把 −57.65% 说成提速；不把 r2 与 v5 的跨故事数字相减；不拿 capture 码元数当实跑用量；
不宣称这三条公开故事是未见泛化（E007 在 r2 已用过，E008/E009 是新覆盖但不是独立验证集）；
不宣称底层模型实例一致；不把固定 HER 后续回复说成"她被这句聊动了"。

## 8. 判决与出口

**保留（效率型开发候选）。** 依据预登记的两条路线：能力线未见实质退化（首选层 2:3:19、
故事级关键位双方都过关），效率线总资源 −57.65%、中位等待 −17.65% 属实质改善，
按"质量在预定可接受范围内相当而资源/等待/维护复杂度实质改善即支持开发保留"成立。
**不替换原版**：尾部等待更高、两臂各有一次整位交付失败且故障族不同、v5 备选踩 note 更频繁。
是否接受这笔交易由你定，我不代投——需要你实际裁的是下面 6 组的语气。

## 9. 需要你拍板的两件事（我不擅自动手）

1. **零宽字符这族怎么处理。** 可选 (a) 在 format.md 里显式禁止键名/文本外的不可见字符并重冻重测；
   (b) 把评分器的必需键匹配改成可见字符规范化后判定——那是**评分尺改动**，
   按规矩必须版本化 + 离线重打分，不得计入 harness 收益；(c) 先不动，留给下一批看是否复现。
   我倾向 (c)+(a)：一次样本不足以定族，但不擅自改尺。
2. **下一步走哪条。** 三件事可并行但需分别授权：独立验证批（2 条新生活故事 ×8 位 ×2 臂=32 次，
   交接包已备好，需新额度）；接入原型的真机验证（需要 D:/LoveBrain 侧编译/Android 条件，
   本地未验证项已列在 `KOTLIN_INTEGRATION_MAP.md`）；或先按第 1 点做一版最小机制修复再对照。

## 10. 证据索引

| 内容 | 路径 |
|---|---|
| 冻结计划/身份闭包 | `INTERLEAVE_PLAN.json` |
| 授权单（已 CLOSED）+ 批准原文 | `AUTHORIZATION.json` |
| 真实账本（48 占用 + authorization_closed） | `logs/auth_budget/AUTH-V5-CTX-20261025-01.jsonl` |
| 批次完成标记 | `logs/v5_ctx_live_20261025.DONE.json` |
| 原始请求/答卷/计分（逐位） | `logs/v5_ctx_live_20261025/<arm>_<ep>/out/turn_*/` |
| 独立复算结果 | `V5_INDEPENDENT_RESULTS.json` |
| 全部首选与备选原文 | `V5_EXTRACTED_OUTPUTS.json` |
| 逐位四层判定 | `V5_PAIRING_LAYERS.json` |
| 6 组公开对照 | `V5_KEY_PUBLIC_GROUPS.md` |
| 复算/收口脚本 | `tools/verify_real_batch_v5.py`、`tools/consolidate_v5_batch.py`、`tools/close_v5_authorization.py` |
| 接入原型 + 回放夹具 | `integration_preview/`（`REPLAY_RESULT.json` 4/4） |
| 独立验证交接包 | `refs/experiment_protocol_20261023/independent_validation_handoff_20261025/` |
"""
open(VERDICT, "w", encoding="utf-8").write(V)
print("WROTE", VERDICT, len(V), "chars")

