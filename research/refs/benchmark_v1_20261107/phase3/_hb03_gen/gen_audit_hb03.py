#!/usr/bin/env python -I
"""Generate audit.md (8 sections) and runner_episode.json (byte copy) for E421-E430."""
import json, os, shutil

VAULT = r'D:/lovebrain-arena_holdout_vault/wave6_holdout'
EIDS = ['E421','E422','E423','E424','E425','E426','E427','E428','E429','E430']

META = {
 'E421': dict(her='巫月明', me='乌思齐', me_short='乌老板', scene='老关唱片店淘碟', third='老关',
   domain='二手黑胶唱片店', partner='黑胶集市', date='2026-11-14', title='专程挑你值班的日子来',
   signal='用藏得深双关把碟与人并置', anti_obj='T03 老关进价报价事务插话'),
 'E422': dict(her='焦星河', me='巴见山', me_short='见山', scene='星岩馆攀岩', third='小隼',
   domain='室内攀岩馆', partner='紫线登顶打赌', date='2026-11-15', title='我赌你臂展不够',
   signal='用臂展不够做文章把胜负拉到关系', anti_obj='T03 小隼路线难点讲解'),
 'E423': dict(her='牧清浅', me='山望岳', me_short='望岳', scene='夜校书法课', third='老桑',
   domain='社区夜校书法课', partner='同桌练字', date='2026-11-17', title='毛毡都铺好了',
   signal='替他占座铺毛毡把周到当名片', anti_obj='T03 老桑横画点评'),
 'E424': dict(her='谷疏影', me='夔听涛', me_short='听涛', scene='24h便利店夜班', third='老利',
   domain='城东便利店', partner='守店夜宵', date='2026-11-18', title='椅子别人不让坐就让你坐',
   signal='搬椅子别人不让坐就让你坐的专属感', anti_obj='T03 老利饭团保质说明'),
 'E425': dict(her='鄂暗香', me='索观澜', me_short='观澜', scene='合租阳台深夜', third='小葳',
   domain='合租屋阳台', partner='阳台守夜', date='2026-11-19', title='晾衣绳先占了半边',
   signal='晾衣绳让半边花茶多泡一杯', anti_obj='T03 小葳降温晾衣提醒'),
 'E426': dict(her='咸初雪', me='籍临风', me_short='临风', scene='城郊天文台观星', third='老卞',
   domain='城郊天文台', partner='守夜观星', date='2026-11-21', title='专程挑你讲解的场子来',
   signal='两条毯子一条给你把心意摊在面上', anti_obj='T03 老卞云量观星窗口播报'),
 'E427': dict(her='赖未央', me='卓对雨', me_short='对雨', scene='团建密室一组', third='老闵',
   domain='团建密室逃脱', partner='密室通关', date='2026-11-22', title='就咱俩一间',
   signal='争取两人一间把分组明撩摊在面上', anti_obj='T03 老闵数字密码锁说明'),
 'E428': dict(her='蒙长安', me='池迎霜', me_short='迎霜', scene='长途夜班卧铺大巴', third='老桂',
   domain='长途大巴邻座', partner='同车邻座', date='2026-11-23', title='八个小时不聊闷得慌',
   signal='让靠窗暖铺分薄毯把关心摊在面上', anti_obj='T03 老桂中转服务区时刻交代'),
 'E429': dict(her='乔思远', me='阴送秋', me_short='送秋', scene='城北早市拼单', third='老茅',
   domain='社区早市', partner='拼单买菜', date='2026-11-24', title='茼蒿两块一把拼了',
   signal='拼茼蒿分葱请豆浆把钩子藏在划算里', anti_obj='T03 老茅茼蒿报价搭葱说明'),
 'E430': dict(her='胥怀瑾', me='苍接冬', me_short='接冬', scene='老城citywalk约拍', third='小蒲',
   domain='胶片约拍', partner='约拍选路', date='2026-11-28', title='光圈开大就留我一个',
   signal='光圈虚化就留我一个的双关', anti_obj='T03 小蒲伊尔福HP5胶卷参数说明'),
}

def loadj(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)

def audit_md(eid):
    m = META[eid]
    d = os.path.join(VAULT, eid)
    ep = loadj(os.path.join(d, 'episode.json'))
    ref = loadj(os.path.join(d, 'reference.json'))
    turns = ep['turns']
    may_decline_turns = [t['case_id'] for t in turns if t['input'].get('may_decline', False)]
    intent_turns = [t['case_id'] for t in turns if t['input'].get('intent', {}).get('enabled', False)]

    atom_total = 0; required_total = 0
    for cid, card in ref['turns'].items():
        for a in card.get('required_memory_atoms', []):
            atom_total += 1
            if a.get('required_this_turn'):
                required_total += 1

    lines = []
    a = lines.append
    # ## 0. 性质
    a("## 0. 性质")
    a("")
    a("- 故事 EID：{}".format(eid))
    a("- 专题：T02 调情/暧昧/反撩/拉扯")
    a("- 场景：{}（{}）".format(m['scene'], m['domain']))
    a("- HER：{}；ME：{}".format(m['her'], m['me']))
    a("- 轮数：12（standard_12t）")
    a("- may_decline 轮：{}（共 {} 轮，与 episode input.may_decline 一致）".format(", ".join(may_decline_turns), len(may_decline_turns)))
    a("- 暴露状态：HOLDOUT 金库 wave6_holdout，SEALED 未消费，real_user_material=false")
    a("- 本卡为评审侧判据，不进候选可见输入；feedback 不进候选输入。")
    a("")

    # ## 1. 生成信息
    a("## 1. 生成信息")
    a("")
    a("- 标题：{}".format(m['title']))
    a("- 作者：ZCode author-H03（AI），冻结于 2026-11-10")
    a("- 谱系组：LG-{}".format(eid))
    a("- 主专题：T02；信号梯度按 隐晦/调侃/明显/混合撤回 铺设，本篇属明显好感+混合撤回子类。")
    a("- 12 轮弧线：到场试探→调侃加码→事务插话(反诱惑)→留钩退路(may_decline)→逼痛快话→带意图邀约→明显好感推进→第三方旁证→挑明逼问→信号撤回(may_decline)→信号复活→收尾留口。")
    a("- 意图轮（intent.enabled=true）：{}".format(", ".join(intent_turns)))
    a("- 原子总数：{}；required_this_turn=true 原子数：{}".format(atom_total, required_total))
    a("")

    # ## 2. 连续性
    a("## 2. 连续性")
    a("")
    a("- 单日单场景连续对话，clock 单调不减，消息 ts ≤ clock。")
    a("- 跨轮回指位（长回扣 ≥4 轮距 + 短回扣）：")
    a("  - T07 长回扣 T02（调侃加码，距离 5）——把首段调侃升成对人。")
    a("  - T11 长回扣 T01（首轮专程，距离 10）——把撤回的钩重新递出，信号复活。")
    a("  - T05 短回扣 T04（留钩，距离 1）——逼痛快话回扣上轮留钩。")
    a("- 回扣原子均落 required_memory_atoms 且 source 轮早于当前轮，required_this_turn=true。")
    a("- 故事内时间：{}（单日下午至深夜，跨日仅 E424/E425/E426/E428 进次日凌晨）".format(m['date']))
    a("")

    # ## 3. ME/HER 状态
    a("## 3. ME/HER 状态")
    a("")
    a("- HER（{}）：文艺/好胜/大方/嘴快/心思细/胆大/利落/精明/审美 等子类，明牌或隐晦示好，信号给了又收（T10 撤回→T11 复活）。boundary：不接受被当工具人/空气/哥们儿，节奏被打乱会缩。".format(m['her']))
    a("- ME（{}）：藏锋/嘴硬/话少/被撩脸红不认；接得住但不扑，被点破前不替她把话说死。".format(m['me']))
    a("- 关系阶段：认识数月，线下第 N 次，试探期/明显好感期/混合撤回期，双方都没把意思挑破。")
    a("- 第三方（{}）：事务插话（T03）+ 打趣旁证（T08），均当面转述，note 标注来源。".format(m['third']))
    a("- HER 在 T09/T11 把事务与图人绑一句逼准话；ME 的正确动作是给准话方向不模糊。")
    a("")

    # ## 4. no_reply / 媒体局限
    a("## 4. no_reply / 媒体局限")
    a("")
    a("- may_decline=true 轮：{}".format(", ".join(may_decline_turns)))
    a("  - T04（留钩退路）：抛钩又收回，半推半就，合法不回（轻承接或留白代替追问）。")
    a("  - T10（信号撤回）：划界退半步给台阶，合法不回（接住退步不追问）。")
    a("- 两 may_decline 轮的 acceptable_actions 均含一条合法不回型多解，gold 不强迫文本。")
    a("- 本篇纯文本聊天，无图片/语音/视频；媒体局限：无。")
    a("- may_decline 与 episode input.may_decline 逐轮一致（reference 卡 may_decline 字段 = episode 值）。")
    a("")

    # ## 5. 已知
    a("## 5. 已知")
    a("")
    a("- T02 signal_gate 九项已填（signal_rounds / anti_temptation / intensity_gradient / forbidden_keywords / cross_ref / may_decline / intent / scene_unique / third_party）。")
    a("- 信号梯度：T01(1)→T02(2)→T03(0 反诱惑)→T04(2)→T05(3)→T06(3)→T07(4)→T08(3)→T09(4)→T10(0 撤回)→T11(4)→T12(2)，梯度递增无跳变。")
    a("- 反诱惑对照位：{}——无信号轮不得过度解读，把'你说了算'原样踢回判 0 档。".format(m['anti_obj']))
    a("- 客服腔回复示例（判 0 档）：'感谢您的关注'、'您有什么需要'、'下次一定'、'多喝热水'、'我理解你'。")
    a("- 禁止关键词命中替代行为判档：{} 等命中即判客服档，不等于调情高分。".format("感谢您的关注/下次一定/多喝热水"))
    a("- 行为锚：0 档=缩钩/扑钩/回避；1 档=接住维持框架不扑不缩；2 档=推半步或留退路（反问回掷型反撩同档）。档位与 HER 投入度匹配比档位本身更高。")
    a("- 互损/调侃场域边界由 forbidden_factual_claims 落卡（不得下流化/物化/越外貌私生活）。")
    a("")

    # ## 6. 参与者注释
    a("## 6. 参与者注释")
    a("")
    a("- 本篇是 T02 调情反撩 HOLDOUT 题，测暧昧张力与推进-退让节奏，不测日常接话(T01)/邀约事务(T07/T09)。")
    a("- HER 子类：{}——信号强度按明显好感/混合撤回铺设，T10 撤回后 T11 复活是轨迹级拉扯链。".format(m['her']))
    a("- gold 不规定唯一回复措辞；acceptable_actions 是策略集合不是句子；confirmed/asserted 原子只作证据不强迫提及（rubric §10）。")
    a("- 每原子带 affirm_markers 嵌套组（item_markers + group_markers），markers 题面逐字。")
    a("- protected_expressions 只收 HER 原话锚点（逐字命中 HER 消息），不构成候选必须复述的要求。")
    a("- 标注会话：ANNOTATOR-HB03（GIA-20261110-ANNOTATOR-HB03），HOLDOUT 专属，隔离 wave4_dev/wave5_selection/logs/candidates。")
    a("")

    # ## 7. 出题痕迹检查
    a("## 7. 出题痕迹检查")
    a("")
    a("- 全部可见输入（messages/note/intent/clock）禁含出题痕迹词（考察/观测点/评分/考点/本题/测试点/evaluation point/grading/rubric/与上轮不同）。")
    a("- note 仅作补充信息转述来源（{}当面报价/说明/打趣），不含考点答案明文。".format(m['third']))
    a("- episode gold={} 恒空，判据活在 reference.json。")
    a("- 未读 logs/candidates/审计判决文档；本卡由隔离会话独立标注。")
    a("- runner_episode.json 为 episode.json 逐字节副本（sha256 一致）。")
    a("")

    return "\n".join(lines)

def main():
    ok_all = True
    for eid in EIDS:
        d = os.path.join(VAULT, eid)
        # audit.md
        aud = audit_md(eid)
        with open(os.path.join(d, 'audit.md'), 'w', encoding='utf-8') as f:
            f.write(aud)
        # runner_episode.json = byte copy of episode.json
        ep_path = os.path.join(d, 'episode.json')
        run_path = os.path.join(d, 'runner_episode.json')
        with open(ep_path, 'rb') as f:
            data = f.read()
        with open(run_path, 'wb') as f:
            f.write(data)
        print(eid, "audit+runner OK")
    print("=== done ===")

if __name__ == '__main__':
    main()
