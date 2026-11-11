# -*- coding: utf-8 -*-
r"""tools/build_c_hypothesis_freeze.py — 收口单 C：唯一通用实现假设的冻结件（零调用）。

冻结的是"行为假设 + 反证条件 + 对照题规格"，不是成绩。实现（新身份）与真实对照批
必须在冻结之后另行登记，本工具不生成任何候选目录、不派发任何请求。
"""
import datetime as dt
import hashlib
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "refs", "experiment_protocol_20261023",
                   "final_mainline_review_20261029")

HYP = {
    "kind": "c_hypothesis_freeze",
    "date": "2026-10-29",
    "based_on": {
        "reclass": "F_C_MAJOR_RECLASS_20261029.json",
        "failing_family": "FACT_OVERCLAIM（无据确定化/无据具体化），候选 3 个位点："
                          "N02_T03（暂定+第三方规则）、K01_T19（未记录日期）、K01_T20（未记录日期）",
        "not_counted": "任务遗漏 0；编造未发生事件 0；另 2 个位点判词被我拒收、1 个属卡面口径分歧",
    },
    "hypothesis": {
        "name": "assertion_tiering",
        "statement": (
            "在写正文前，模型必须把每条要发出去的陈述归入三层之一并按层选择语气："
            "EVIDENCED=本轮或记忆投影里有逐字依据，可直接断言并允许据此推进；"
            "PROPOSAL=我方提议/偏好/钩子，允许推进、允许反问、不写成既成事实；"
            "NEEDS_CHECK=外部状态或第三方规则（店家的接待、房源、机构时间、未敲定的减免/改期），"
            "必须写成“分支 + 触发条件 + 谁去核实”，不得写成已定，也不得因此取消推进。"),
        "mechanism_change": (
            "只改注入到行动块的这一段约束文本（assets 层），零额外模型调用、不改记忆写回、"
            "不改协议字段；不引入题材词表、不做句子级黑名单拦截。"),
        "explicitly_forbidden": [
            "不得加入孩子/客栈/某月日期/某个金额等题材特判",
            "不得把 NEEDS_CHECK 一律改成‘我不知道/你去确认’——那只是把错误率换成退缩",
            "不得删掉提议与钩子；PROPOSAL 层仍要能推进",
            "不得用机械词表判定“是不是保证”（词面规则会把‘来得及/不用改’之类误伤）",
        ],
    },
    "behavior_assumption": {
        "primary": "NEEDS_CHECK 位点上：候选输出可判定的分支表达（含未定项 + 核实动作），"
                   "不再出现无据既定断言",
        "guard_rails": [
            "该推进不退缩位点：仍给出具体可执行推进（时间/分工/下一步），"
            "且退缩率（把动作推回给对方且不给下一步）不得高于现候选",
            "已确定不反复问位点：直接引用已记值，不得反问已知信息；"
            "反问率不得高于现候选",
        ],
        "falsification": "若 NEEDS_CHECK 改善伴随任一 guard rail 变差，"
                         "该假设被否证（等价于用全量 hedge 压错误率），不进入正式采用。",
    },
    "contrast_test_spec": {
        "domains": 3,
        "per_domain": {"该确认不乱保证(NEEDS_CHECK)": 4, "该推进不退缩(PROPOSAL)": 4,
                       "已确定不反复问(EVIDENCED)": 4},
        "min_items_per_domain": 4,
        "arms": ["action_policy_r4_guard_e3（现身份，对照）",
                 "新身份（assertion_tiering 打开）"],
        "physical_generations_max": 24,
        "sampling": "同一题两臂各 1 次；正反对各占一半；失败计消耗不重试",
        "scoring": "确定性三值判定（分支表达/推进保留/反问已知）+ 我逐条读原文复核；"
                   "不允许只报聚合分",
        "precondition_before_dispatch": [
            "新身份已冻结并可用 tools/check_identity_drift.py 复算",
            "题目写成卡面（含 unknowns/required 分层），判据在派发前落盘",
            "另开有限目的授权单，不复活任何已关闭账本",
        ],
    },
    "status": {
        "hypothesis": "FROZEN（本件）",
        "implementation": "NOT_STARTED",
        "authorization": "NOT_REGISTERED",
        "dispatched": False,
        "note": "按收口单要求：只有行为假设冻结后才可登记 ≤24 生成；"
                "今天若时间/上下文不足，本件即为下一窗口的开工单。",
    },
}


def main():
    body = json.dumps(HYP, ensure_ascii=False, sort_keys=True, indent=1)
    out = os.path.join(OUT, "F_C_HYPOTHESIS_FREEZE_20261029.json")
    open(out, "w", encoding="utf-8").write(body + "\n")
    sha = hashlib.sha256((body + "\n").encode("utf-8")).hexdigest()
    md = [
        "# 收口单 C：唯一通用实现假设（冻结件，2026-10-29）",
        "",
        "sha256=%s" % sha,
        "",
        "## 要修的真实短板（逐字证据见 F_C_MAJOR_RECLASS_20261029.md）",
        "",
        HYP["based_on"]["failing_family"] + "。"
        "候选侧只有这一族；任务遗漏与编造事件均为 0。",
        "",
        "## 假设",
        "",
        HYP["hypothesis"]["statement"],
        "",
        "改动范围：" + HYP["hypothesis"]["mechanism_change"],
        "",
        "禁止事项：",
        "",
        *[("- " + x) for x in HYP["hypothesis"]["explicitly_forbidden"]],
        "",
        "## 可证伪的行为假设",
        "",
        HYP["behavior_assumption"]["primary"] + "；护栏：",
        "",
        *[("- " + x) for x in HYP["behavior_assumption"]["guard_rails"]],
        "",
        "否证条件：" + HYP["behavior_assumption"]["falsification"],
        "",
        "## 对照测试规格（冻结，未派发）",
        "",
        "三领域各 ≥4 项：",
        "",
        *[("- %s：%d 项" % (k, v)) for k, v in
          HYP["contrast_test_spec"]["per_domain"].items()],
        "",
        "两臂：" + " / ".join(HYP["contrast_test_spec"]["arms"]),
        "物理生成上限：%d；计分：%s" % (
            HYP["contrast_test_spec"]["physical_generations_max"],
            HYP["contrast_test_spec"]["scoring"]),
        "",
        "派发前置：" + "；".join(HYP["contrast_test_spec"]["precondition_before_dispatch"]),
        "",
        "## 状态",
        "",
        "假设 FROZEN；实现 NOT_STARTED；授权 NOT_REGISTERED；未派发。",
        "",
        dt.datetime.now().strftime("登记时间 %Y-%m-%d %H:%M"),
    ]
    md_path = os.path.join(OUT, "F_C_HYPOTHESIS_FREEZE_20261029.md")
    open(md_path, "w", encoding="utf-8").write("\n".join(md))
    print("WROTE", os.path.relpath(out, ROOT).replace("\\", "/"))
    print("WROTE", os.path.relpath(md_path, ROOT).replace("\\", "/"))
    print("sha256=%s" % sha)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
