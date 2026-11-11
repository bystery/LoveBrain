# -*- coding: utf-8 -*-
"""test_baseline_parity.py — 工作包 B：冻结原产品主链路对拍夹具（G04）。

≥24 个确定性对拍夹具（≥8 个多轮/恢复），覆盖 §4-B3 能力清单。
预期值来源 = 冻结原仓库（D:/LoveBrain）源码逐字常量与其自带单元测试
（文件+行号/测试方法在每条断言 evidence 中注明）；模型输出用固定原文替身，
无采样噪声。参考移植 = candidates/reference_product_v1/reference_chain.py。

原则（§4-B2）：基线保真 = 与原产品相符，不是基线通过全部能力题；
不为对拍添加原产品没有的能力（原产品不检索长期档案，如实保留）。
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "candidates", "reference_product_v1"))

import reference_chain as rc                 # noqa: E402

fails = []
FIXTURES = []


def fixture(rid, capability, multi_turn, evidence, fn):
    FIXTURES.append({"id": rid, "capability": capability,
                     "multi_turn": multi_turn, "evidence": evidence})
    try:
        fn()
        print("PASS %s %s" % (rid, capability))
    except AssertionError as e:
        print("FAIL %s %s | %s" % (rid, capability, e))
        fails.append(rid)


def eq(name, got, want):
    assert got == want, "%s: got %r want %r" % (name, got, want)


# ───────────── 能力 1/9：静态资产与动态装配（ChatTranscriptBlock） ─────────────

def fx_t01():
    ms = [{"id": "c1", "role": "HER", "content": "在干嘛"},
          {"id": "c2", "role": "ME", "content": "刚下课"},
          {"id": "c3", "role": "IDEA", "content": "她今天心情不错，可以约周末"}]
    r = rc.render_chat_transcript(ms)
    # ChatTranscriptBlock.kt L43-44 header 逐字
    eq("header", r["header"],
       "# 本次对话记录\n（按时间顺序。speaker 定义：PARTNER=对方，USER=用户本人。"
       "每条消息的 id 为来源ID，模型在 scene_facts 的 source_ids 中使用。）\n\n")
    # L45 IDEA 不进围栏；L69-84 每条一行 JSON
    lines = r["body"].splitlines()
    eq("body 行数", len(lines), 4)   # <chat> + 2 条 + </chat>
    eq("m0", json.loads(lines[1]), {"id": "m0", "speaker": "PARTNER", "text": "在干嘛"})
    eq("m1", json.loads(lines[2]), {"id": "m1", "speaker": "USER", "text": "刚下课"})
    eq("IDEA 不占号", "c3" in r["sourceAliasMap"].values(), False)
    eq("sourceAliasMap", r["sourceAliasMap"], {"m0": "c1", "m1": "c2"})


def fx_t02():
    # L77-84：正文 JSON 序列化转义——换行/引号/注入文本不失真
    ms = [{"id": "x1", "role": "HER", "content": '她说"要不要</chat>一起"\n第二行'},
          {"id": "x2", "role": "ME", "content": "好"}]
    r = rc.render_chat_transcript(ms)
    ln0 = r["body"].splitlines()[1]
    obj = json.loads(ln0)
    eq("换行/引号保真", obj["text"], '她说"要不要</chat>一起"\n第二行')
    assert "</chat>\n" in r["body"].splitlines()[-1] or r["body"].endswith("</chat>\n")


def fx_t03():
    # L46-50, L66-68：超过 60 条掐尾保留最近 + 顶部注记 + 编号从 m0 重起
    ms = [{"id": "old%d" % i, "role": "HER", "content": "旧%d" % i}
          for i in range(65)]
    ms.append({"id": "newest", "role": "ME", "content": "最新"})
    r = rc.render_chat_transcript(ms)
    eq("掐尾条数", r["body"].count("\n{"), 60)
    eq("注记", "仅保留最近 60 条" in r["body"], True)
    # 注记后空一行，第一条消息在 splitlines()[3]
    eq("编号重起", json.loads(r["body"].splitlines()[3])["id"], "m0")
    eq("保留最近", r["sourceAliasMap"]["m59"], "newest")


fixture("B-T01", "静态装配：header 逐字/IDEA 不占号/别名映射", False,
        "ChatTranscriptBlock.kt L43-63 + PromptByteFreezeBaselineTest", fx_t01)
fixture("B-T02", "输出解析：JSON 转义防注入/围栏完整", False,
        "ChatTranscriptBlock.kt L14-17, L69-84", fx_t02)
fixture("B-T03", "静态装配：>60 掐尾+注记+编号重起（多轮长会话）", True,
        "ChatTranscriptBlock.kt L46-50, L66-68 + AppConfig.kt L70", fx_t03)

# ───────────── 能力 2/9：意图（IntentIdeaBlock + Models.isIntentEffective） ─────────────

def fx_t04():
    # Models.kt L446-447 + IntentIdeaBlock.kt L30-33
    eq("开+非空+ACTIVE", rc.build_intent_block(
        {"text": "这周推进约她看展", "enabled": True, "status": "ACTIVE"}),
       "【持续意图】\n这周推进约她看展\n\n")
    eq("关闭→空", rc.build_intent_block(
        {"text": "x", "enabled": False, "status": "ACTIVE"}), "")
    eq("空文本→空", rc.build_intent_block(
        {"text": "  ", "enabled": True, "status": "ACTIVE"}), "")
    eq("旧终态防线（status=COMPLETED）→空", rc.build_intent_block(
        {"text": "x", "enabled": True, "status": "COMPLETED"}), "")


def fx_t05():
    # IntentIdeaBlock.kt L44-54：军师备注逐字全量注入（展示端省略过，发给模型不许裁）
    note = "她上周提过想去湖边   \n别提前任。"
    b = rc.build_advisor_note_block(note)
    eq("header", b.startswith(rc.NOTE_BLOCK_HEADER + "\n「"), True)
    eq("正文逐字（含内部空格）", "「她上周提过想去湖边   \n别提前任。」" in b, True)
    eq("空正文→空串不留空标题", rc.build_advisor_note_block("   "), "")


fixture("B-T04", "意图：有效性判据四态 + 区块逐字", False,
        "Models.kt L446-447 + IntentIdeaBlock.kt L30-33 + IntentPolicyTest", fx_t04)
fixture("B-T05", "军师备注：逐字全量/空正文不留空标题", False,
        "IntentIdeaBlock.kt L36, L44-54", fx_t05)

# ───────────── 能力 3/9：来源（IDEA 不冒充；已并入 B-T01）+ 场景（CurrentSceneInjection） ─────────────

def fx_t06():
    # CurrentSceneInjection.kt L38-42：收尾只看最后一条 HER，词表冻结
    eq("收尾", rc.infer_scene([{"role": "HER", "content": "我先去洗澡了"},
                               {"role": "ME", "content": "好"},
                               {"role": "HER", "content": "明天再说 晚安"}]), "收尾")
    # L16-18 优先级即语义：随便你(争执) + 周末有空吗(邀约) → 争执
    eq("优先级碰撞→争执", rc.infer_scene(
        [{"role": "HER", "content": "随便你"}, {"role": "HER", "content": "周末有空吗"}]),
       "争执")


def fx_t07():
    # L19, L49-51：认错只看 ME 本人说的话；HER 说对不起不算（不判解释或认错）。
    # L54-57：邀约只看最后一条 HER——最后 HER 提"周末" → 主动邀约。
    eq("HER 道歉≠认错", rc.infer_scene(
        [{"role": "HER", "content": "对不起，是我没做好"},
         {"role": "ME", "content": "没事"},
         {"role": "HER", "content": "周末去看展吗"}]), "主动邀约")
    eq("ME 道歉→解释或认错", rc.infer_scene(
        [{"role": "HER", "content": "嗯。"},
         {"role": "ME", "content": "我错了，不应该忘"}]), "解释或认错")


def fx_t08():
    # L30-33：无真实消息（只有想法行）→ 空串，场景段整段省略
    eq("只有 IDEA→空", rc.infer_scene(
        [{"role": "IDEA", "content": "她好像不太开心"}]), "")
    # L60-65 默认链
    eq("互逗", rc.infer_scene([{"role": "HER", "content": "哈哈哈 笑死"}]), "轻松互逗")
    eq("认真", rc.infer_scene([{"role": "HER", "content": "最近压力大，有点焦虑"}]), "认真沟通")
    eq("默认日常分享", rc.infer_scene([{"role": "HER", "content": "今天吃了螺蛳粉"}]), "日常分享")


def fx_t09():
    # block() L75-78 逐字 + 空场景整段省略
    eq("场景块逐字", rc.scene_block("收尾"),
       "# 【当前场景】（本轮属性，不改变长期阶段档案）\n场景：收尾\n\n")
    eq("空场景→空", rc.scene_block(""), "")


fixture("B-T06", "场景：收尾词表冻结 + 优先级碰撞", False,
        "CurrentSceneInjection.kt L16-18, L38-46 + SceneChainInjectionTest", fx_t06)
fixture("B-T07", "场景：认错只看 ME 本人（SpeakerSubject 语义）", False,
        "CurrentSceneInjection.kt L19, L49-51 + SpeakerSubjectRegressionTest", fx_t07)
fixture("B-T08", "场景：无真实消息→空 + 默认链全分支", False,
        "CurrentSceneInjection.kt L30-33, L60-68", fx_t08)
fixture("B-T09", "场景块：标题逐字/空场景省略", False,
        "CurrentSceneInjection.kt L75-78", fx_t09)

# ───────────── 能力 4/9：纠正（MemoryCorrectionPolicy + KnowledgeMemoryStore） ─────────────

class MemStorage:
    """KnowledgeMemoryStore.kt L20-36 MemoryStorage 六能力 in-memory 替身。"""

    def __init__(self, exists=True, refuse_write=False):
        self.files = {}
        self.notes = []
        self._exists = exists
        self._refuse = refuse_write
        self.last_tx_written = False
        self._ts = 0

    def note(self, msg):
        self.notes.append(msg)

    def kb_exists(self, kb):
        return self._exists

    def timestamp(self):
        self._ts += 1
        return "2026-10-21 12:00:%02d" % self._ts

    def read(self, kb, rel):
        return self.files.get(kb + "::" + rel, "")

    def write_transaction(self, kb, records, revision, store):
        self.last_tx_written = False
        if self._refuse:
            return                       # L9：false = 一个字节都没落
        self.files[kb + "::" + store.CORRECTIONS_FILE] = json.dumps(
            records, ensure_ascii=False)
        self.files[kb + "::" + store.MEMORY_REVISION_FILE] = str(revision)
        self.last_tx_written = True

    def decode_corrections(self, text):
        return None

    def encode_corrections(self, recs):
        return json.dumps(recs, ensure_ascii=False)


def fx_t10():
    # MemoryCorrectionPolicy.kt L22-23 + Test L23-39：只有 MUTED+THIS_ROUND 瞬时
    for a in rc.ACTIONS:
        for d in ("THIS_ROUND", "UNTIL_RESTORE"):
            want = a == "MUTED" and d == "THIS_ROUND"
            eq("%s+%s" % (a, d), rc.is_transient_round_mute(a, d), want)


def fx_t11():
    # MemoryCorrectionPolicy.kt L41-46 + Test L57-65：四种动作四条文案，互不相同
    notices = [rc._APPLIED_NOTICES[a] for a in rc.ACTIONS]
    eq("四条互不相同", len(set(notices)), 4)
    assert all(n.strip() for n in notices)
    # Test L68-76：失败文案不与任何成功文案相同
    fails_ = [rc.APPLY_FAILED, rc.UNDO_FAILED]
    succ = notices + [rc.UNDONE, rc.ROUND_MUTE_APPLIED, rc.ROUND_MUTE_UNDONE]
    for f in fails_:
        assert f not in succ, "失败文案与成功文案撞车"


def fx_t12():
    # MemoryCorrectionPolicy.kt L62-81 + Test L81-94：快照是副本
    st = rc.RoundCorrectionStore()
    assert st.is_empty()
    st.put("m-1", {"action": "MUTED"})
    snap = st.snapshot()
    st.put("m-2", {"action": "MUTED"})
    eq("发出时那份不再变", len(snap), 1)
    eq("本体已更新", len(st.snapshot()), 2)
    eq("remove 二次 False", (st.remove("m-1"), st.remove("m-1")), (True, False))
    st.clear()
    eq("clear 后 remove False", st.remove("never-there"), False)


def fx_t13():
    # KnowledgeMemoryStore.kt L72-95：save 递增持久化 revision（非剩余 max）
    st = MemStorage()
    store = rc.ReferenceMemoryStore(st)
    assert store.save("kb", "mem-1", "WRONG")
    assert store.save("kb", "mem-2", "MUTED")
    eq("revision=2", store.revision_of("kb"), 2)
    assert store.undo("kb", "mem-2")     # L100-102：撤销也递增（0→1→0 防线）
    eq("撤销后 revision=3", store.revision_of("kb"), 3)
    assert store.save("kb", "mem-3", "FINISHED")
    eq("撤销后再新增仍递增=4", store.revision_of("kb"), 4)
    eq("剩余记录 2 条", len(store.corrections("kb")), 2)
    assert "mem-2" not in store.corrections("kb")
    # L103-110：撤销不存在的记录 → False
    eq("撤销不存在→False", store.undo("kb", "nope"), False)
    # 库不在 → False（L80/L104）
    st2 = MemStorage(exists=False)
    store2 = rc.ReferenceMemoryStore(st2)
    eq("库不在 save→False", store2.save("kb2", "m", "WRONG"), False)


def fx_t14():
    # L118-128：纠正文件写失败时绝不写 revision + note 留痕（多步恢复场景）
    st = MemStorage(refuse_write=True)
    store = rc.ReferenceMemoryStore(st)
    eq("只读保护→False", store.save("kb", "m1", "WRONG"), False)
    eq("revision 未写", st.files.get("kb::memory/.revision"), None)
    eq("note 留痕", any("write refused" in n for n in st.notes), True)
    # 坏 JSON → 空表不异常（L130-134）
    st2 = MemStorage()
    st2.files["kb::memory/corrections.json"] = "{oops"
    store2 = rc.ReferenceMemoryStore(st2)
    eq("坏 JSON→空表", store2.corrections("kb"), {})
    eq("坏 JSON 后仍可 save", store2.save("kb", "m", "WRONG"), True)


fixture("B-T10", "纠正：瞬时 mute 判定矩阵（8 组合）", False,
        "MemoryCorrectionPolicy.kt L22-34 + Test L23-39", fx_t10)
fixture("B-T11", "纠正：四动作文案互异/失败文案不撞车", False,
        "MemoryCorrectionPolicy.kt L41-53 + Test L57-76", fx_t11)
fixture("B-T12", "纠正：RoundCorrectionStore 副本语义 + remove 二次（多步）", True,
        "MemoryCorrectionPolicy.kt L62-81 + Test L81-107", fx_t12)
fixture("B-T13", "纠正：revision 单调（save/undo/再 save 多轮）+ 库不在", True,
        "KnowledgeMemoryStore.kt L39-51, L72-110 + 单调性承诺", fx_t13)
fixture("B-T14", "纠正：写失败不写 revision + 坏 JSON 恢复（异常/恢复）", True,
        "KnowledgeMemoryStore.kt L118-134 + AtomicWriteRegressionTest", fx_t14)

# ───────────── 能力 5/9：ongoing（OngoingContextSelector） ─────────────

class MemRepo:
    def __init__(self, plan=""):
        self.plan = plan
        self.files = {}

    def read_plan_active(self, kb):
        return self.plan

    def read_file(self, kb, rel):
        return self.files.get(rel, "")

    def write_file(self, kb, rel, content):
        self.files[rel] = content


PLAN = "周末见面|进行中|t0 新出现\n"

# 对照 OngoingContextSelectorTest：同一夹具跑多轮序列


def fx_t15():
    # Test L55-61: empty plan yields empty result
    sel = rc.ReferenceOngoingSelector(MemRepo(""))
    r = sel.select_for_injection("kb", [{"role": "HER", "content": "在吗"}], 1)
    eq("空 plan→空结果", (r["eligibleItems"], r["injectedItemNames"]), ([], set()))


def fx_t16():
    # Test L73-84: first unseen item does not inject into unrelated conversation
    sel = rc.ReferenceOngoingSelector(MemRepo(PLAN))
    r = sel.select_for_injection("kb", [{"role": "HER", "content": "今天好累"}], 1)
    eq("首见不相关不注入", len(r["eligibleItems"]), 0)


def fx_t17():
    # Test L88-100: unrelated message does not inject after first appearance（多轮）
    repo = MemRepo(PLAN)
    sel = rc.ReferenceOngoingSelector(repo)
    sel.select_for_injection("kb", [{"role": "HER", "content": "周末见面安排上了"}], 1)
    r2 = sel.select_for_injection("kb", [{"role": "HER", "content": "哈哈哈"}], 2)
    eq("冷却中且不相关不注入", len(r2["eligibleItems"]), 0)


def fx_t18():
    # Test L112-127: 「周末还要加班」不许召回「周末见面」（滑动窗口撤除）
    sel = rc.ReferenceOngoingSelector(MemRepo(PLAN))
    r = sel.select_for_injection("kb", [{"role": "HER", "content": "周末还要加班 烦"}], 1)
    eq("词面重叠≠相关", r["eligibleItems"], [])
    # Test L128-138: 整词出现照常命中
    r2 = sel.select_for_injection("kb", [{"role": "HER", "content": "那周末见面聊"}], 1)
    eq("整词命中", [i["name"] for i in r2["eligibleItems"]], ["周末见面"])


def fx_t19():
    # Test L147-166: 意图点名注入但不伪造消息证据（不刷新 lastEvidenceTurn）
    repo = MemRepo(PLAN)
    sel = rc.ReferenceOngoingSelector(repo)
    r1 = sel.select_for_injection(
        "kb", [{"role": "HER", "content": "嗯嗯"}], 1,
        effective_intent={"text": "把周末见面的事定下来", "enabled": True,
                          "status": "ACTIVE"})
    eq("意图相关注入", [i["name"] for i in r1["eligibleItems"]], ["周末见面"])
    cd = json.loads(repo.files["moment/ongoing_cooldown.json"])
    e = [x for x in cd if x["name"] == "周末见面"][0]
    eq("注入轮记 1", e["lastInjectedTurn"], 1)
    eq("证据轮不因意图刷新", e.get("lastEvidenceTurn", -1), -1)
    # Test L183-198: ReplyDirective 提及注入
    r2 = sel.select_for_injection("kb", [{"role": "HER", "content": "好"}], 2,
                                  reply_directive={"text": "记得提周末见面"})
    eq("想法相关注入", len(r2["eligibleItems"]), 1)


def fx_t20():
    # Test L198-213: date proximity alone does NOT inject
    sel = rc.ReferenceOngoingSelector(MemRepo(PLAN))
    r = sel.select_for_injection("kb", [{"role": "HER", "content": "日期快到了"}], 1)
    eq("日期临近不单独授权", r["eligibleItems"], [])
    # extractEventDate L337-347：仅从事项名提取
    eq("eventDate", rc.parse_plan_items("9-21见面|进行中|x")[0]["eventDate"],
       "2026-09-21")


def fx_t21():
    # Test L214-245: DORMANT after 5 turns without evidence；再相关即复活（多轮）
    repo = MemRepo(PLAN)
    sel = rc.ReferenceOngoingSelector(repo)
    sel.select_for_injection("kb", [{"role": "HER", "content": "周末见面那些事再说"}], 1)
    sel.select_for_injection("kb", [{"role": "HER", "content": "周末见面 提醒我一下"}], 2)
    # t2 有真实证据 → lastEvidenceTurn=2；t7 时 7-2=5 ≥ 5 → DORMANT 且不相关
    r7 = sel.select_for_injection("kb", [{"role": "HER", "content": "今天天气不错"}], 7)
    eq("t7 不相关 DORMANT 不注入", len(r7["eligibleItems"]), 0)
    eq("t7 内部状态 DORMANT", [i["itemStatus"] for i in r7["allItems"]], ["DORMANT"])
    # Test L231-243: DORMANT 但关键词命中 → 注入
    r8 = sel.select_for_injection("kb", [{"role": "HER", "content": "说到周末见面"}], 8)
    eq("DORMANT 复活", len(r8["eligibleItems"]), 1)


def fx_t22():
    # Test L247-271: FINISHED/CANCELLED 永不注入
    for status in ("已完成", "已取消"):
        sel = rc.ReferenceOngoingSelector(MemRepo("饭局|%s|t0 x\n" % status))
        r = sel.select_for_injection("kb", [{"role": "HER", "content": "饭局去不去"}], 1)
        eq("%s 不注入" % status, r["eligibleItems"], [])


def fx_t23():
    # L325-333 状态推导 + L300-321 解析（itemId~ 前缀兼容）
    items = rc.parse_plan_items("o1~9-21见面|进行中|t0 新出现|t3 推进\n")
    eq("itemId 剥离", items[0]["name"], "9-21见面")
    eq("status", items[0]["status"], "进行中")
    eq("chain", items[0]["chain"], "t0 新出现|t3 推进")
    eq("ACTIVE", items[0]["itemStatus"], "ACTIVE")
    eq("未知状态→ACTIVE", rc.derive_item_status("别的"), "ACTIVE")
    eq("新出现→ACTIVE", rc.derive_item_status("新出现"), "ACTIVE")


def fx_t24():
    # L323-324 + L128-139：DORMANT 不在 derive 推导；证据未注入不得误入 DORMANT
    repo = MemRepo(PLAN)
    sel = rc.ReferenceOngoingSelector(repo)
    # t1 证据（注入）；t2-t3 只证据不注入（消息提关键词、但 relevant → 注入…改为
    # 直接验证：证据轮刷新后即便注入轮停在 t1，DORMANT 用证据轮计算）
    sel.select_for_injection("kb", [{"role": "HER", "content": "周末见面 提醒我"}], 1)
    # t4: 4-1=3 < 5 未 DORMANT；且冷却 (4-1)=3 ≥3 过冷却；不相关 → 不注入但不 DORMANT
    r4 = sel.select_for_injection("kb", [{"role": "HER", "content": " unrelated"}], 4)
    eq("t4 未 DORMANT", [i["itemStatus"] for i in r4["allItems"]], ["ACTIVE"])
    cd = json.loads(repo.files["moment/ongoing_cooldown.json"])
    eq("证据轮=1（冷却期内无新证据）",
       [x for x in cd if x["name"] == "周末见面"][0]["lastEvidenceTurn"], 1)


def fx_t25():
    # L20-21, L100-102 fail closed + L362-367 冷却文件坏 → 读侧空、选择仍 fail closed
    repo = MemRepo(PLAN)
    repo.files["moment/ongoing_cooldown.json"] = "not-json{{{"
    sel = rc.ReferenceOngoingSelector(repo)
    r = sel.select_for_injection("kb", [{"role": "HER", "content": "周末见面吧"}], 1)
    eq("坏冷却文件不崩溃", len(r["eligibleItems"]), 1)   # 证据相关仍走正常门
    # 空 plan 行/无 | 行跳过
    eq("杂行跳过", rc.parse_plan_items("普通文字\n\n|残缺\nx|y\n"), [])


fixture("B-T15", "ongoing：空 plan→空结果", False,
        "OngoingContextSelector.kt L108-109 + Test L55-61", fx_t15)
fixture("B-T16", "ongoing：首见不相关不注入（默认拒绝）", False,
        "OngoingContextSelector.kt L25-33 + Test L73-84", fx_t16)
fixture("B-T17", "ongoing：冷却中且不相关不注入（多轮）", True,
        "OngoingContextSelector.kt L176-187 + Test L88-100", fx_t17)
fixture("B-T18", "ongoing：滑窗撤除/整词命中", False,
        "OngoingContextSelector.kt L234-241 + Test L112-138", fx_t18)
fixture("B-T19", "ongoing：意图/想法注入但不伪造证据（多轮冷却账本）", True,
        "OngoingContextSelector.kt L77-79, L147-166, L379-397 + Test L147-198", fx_t19)
fixture("B-T20", "ongoing：日期临近不单独授权 + eventDate 提取", False,
        "OngoingContextSelector.kt L335-347 + Test L198-210", fx_t20)
fixture("B-T21", "ongoing：DORMANT 5 轮推导与复活（多轮）", True,
        "OngoingContextSelector.kt L128-139 + Test L214-243", fx_t21)
fixture("B-T22", "ongoing：FINISHED/CANCELLED 永不注入", False,
        "OngoingContextSelector.kt L161-164 + Test L247-271", fx_t22)
fixture("B-T23", "ongoing：plan 解析（itemId 前缀/chain）+ 状态推导", False,
        "OngoingContextSelector.kt L300-333", fx_t23)
fixture("B-T24", "ongoing：证据轮独立于注入轮（不误入 DORMANT，多轮）", True,
        "OngoingContextSelector.kt L124-127, L370-377", fx_t24)
fixture("B-T25", "ongoing：fail-closed + 冷却文件损坏恢复（异常/恢复）", True,
        "OngoingContextSelector.kt L100-102, L362-367", fx_t25)

# ───────────── 能力 6/9：时效与长期边界（AppConfig 常量冻结） ─────────────

def fx_t26():
    # AppConfig.kt L61-62, L70：场景链 2h/2 条、消息 60 条掐尾
    eq("SCENE_CHAIN_MAX_HOURS=2", rc.SCENE_CHAIN_MAX_HOURS, 2)
    eq("SCENE_CHAIN_MAX_ENTRIES=2", rc.SCENE_CHAIN_MAX_ENTRIES, 2)
    eq("REPLY_MAX_MESSAGES=60", rc.REPLY_MAX_MESSAGES, 60)
    # OngoingContextSelector.kt L407-412
    eq("COOLDOWN=3", rc.ONGOING_COOLDOWN_TURNS, 3)
    eq("DORMANT=5", rc.ONGOING_DORMANT_TURNS, 5)


fixture("B-T26", "时效常量：2h/2 条/60 条/冷却 3/休眠 5 冻结", False,
        "AppConfig.kt L61-70 + OngoingContextSelector.kt L407-412", fx_t26)

# ───────────── 汇总 ─────────────

n_multi = sum(1 for f in FIXTURES if f["multi_turn"])
n_assert = len(FIXTURES)  # fixture 级；断言级见各函数内 eq 计数
print("== fixtures: %d (multi-turn %d) | FAIL: %s ==" % (len(FIXTURES), n_multi,
                                                         fails or "none"))
assert len(FIXTURES) >= 24, "夹具数 %d < 24" % len(FIXTURES)
assert n_multi >= 8, "多轮/恢复夹具 %d < 8" % n_multi
assert not fails, "存在 FAIL"
print("BASELINE PARITY PASS: %d fixtures (%d multi-turn), all expectations from frozen Kotlin sources"
      % (len(FIXTURES), n_multi))

# 落证据清单
ev = {"date": "2026-10-21", "fixtures": FIXTURES,
      "reference_port": "candidates/reference_product_v1/reference_chain.py",
      "frozen_source": "D:/LoveBrain (Kotlin), hashes in BASELINE_PARITY.md",
      "model_outputs": "fixed-text substitutes（无采样噪声）",
      "note": ("预期值来自原仓库源码逐字常量与自带单元测试（文件+行号已注明）；"
               "基线保真=与原产品相符，不是通过全部能力题。")}
json.dump(ev, open(os.path.join(ROOT, "refs", "arena_v1",
                                "baseline_parity_fixtures.json"), "w",
                   encoding="utf-8"), ensure_ascii=False, indent=1)
