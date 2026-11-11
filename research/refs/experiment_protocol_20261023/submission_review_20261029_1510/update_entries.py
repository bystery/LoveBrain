import datetime,json
from pathlib import Path
ROOT=Path(r'D:\lovebrain-arena');BASE=Path(__file__).parent
p=ROOT/'WORKER_CURRENT_RUNBOOK.json';d=json.loads(p.read_text(encoding='utf-8-sig'))
d.update({'updated_at':datetime.datetime.now().astimezone().isoformat(),'previous_master_runbook':d['master_runbook'],'master_runbook':str(BASE/'WORKER_CORE_CLOSEOUT_NEXT.md'),'latest_independent_review':str(BASE/'SUBMISSION_REPORT.md'),'audit_snapshot':str(BASE/'INDEPENDENT_CHECKS.json'),'execution_queue':str(BASE/'WORKER_CORE_CLOSEOUT_NEXT.md'),'delivery_bundle':str(BASE/'LoveBrain_research_submission_20261029.zip'),'status_source':'F0-F6 execution finished; overall goal REWORK. 144 F3 answers verified. F1 is mixed historical implementations, not r4 scores.','priority':'先准确交研究原型；剩余只补真实长知识检索与无据确定化，禁止重做F0-F6或为E3全量重抽','current_candidate':'action_policy_r4_guard_e3 (offline-equivalent/local-live evidence; F3 quality belongs to frozen r4)','overall_status':'REWORK_RESEARCH_PROTOTYPE_DELIVERABLE','new_model_calls_made_by_this_review':0})
p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
q=ROOT/'WORKER_NEW_WINDOW_START.md'
q.write_text('''# 新窗口唯一开启入口（2026-10-29下午独立复核后）

先读AGENTS.md、SESSION_COORDINATION_V1.md，登记心跳/认领，再读WORKER_CURRENT_RUNBOOK.json。
本轮总目标仍是固定模型优化完整Harness，质量一级，费用/等待二级，产品接入暂停。

**F0–F6执行已经完成，禁止从头重跑。总体REWORK，研究原型可交付，但长期记忆与错误确定化未达标。**

必读当前指针的latest_independent_review（SUBMISSION_REPORT.md）和audit_snapshot（INDEPENDENT_CHECKS.json），再执行master_runbook（WORKER_CORE_CLOSEOUT_NEXT.md）。旧凌晨任务包/早晨260状态只作历史。

关键继承：F3有144份真实答卷、72对匿名AI评分，候选38胜/10负/24平；这是4篇开发故事，不是独立未见验证。F1的127对来自不同旧版本，不能归给r4。两候选冻结身份一致。E3重放72份只有1份机械修复，其余71份对象不变；无需全量重抽历史补身份。本次研究原型包已在干净目录12轮mock完成；mock分数没有聊天质量意义。

下一步只做准确提交与两个核心缺口：真正离开原文的记忆on/off试验；无据确定化的通用修复。复用现有runner/状态/预算/材料，不重建平台、不美化旧判词、不按版本名宣布变强。持续授权下登记有限scope、核现有总帽，不复活关闭账本；hy4-preview硬禁。没有人工品味评价就留未知，不阻塞客观评估。
''',encoding='utf-8')
a=ROOT/'AGENTS.md';s=a.read_text(encoding='utf-8-sig')
header='> **下午独立复核更新（2026-10-29）**：F0–F6执行已结束，完整目标仍REWORK。当前入口以WORKER_CURRENT_RUNBOOK.json为准，最新复核=refs/experiment_protocol_20261023/submission_review_20261029_1510/SUBMISSION_REPORT.md；剩余执行=同目录WORKER_CORE_CLOSEOUT_NEXT.md。已实查F3 144生成/72对评审；F1历史127对不是r4成绩；E3只需如实继承离线等价与局部实跑证据，不为身份全量重抽。先交可运行研究原型，仅补真实长知识检索和无据确定化，禁止重做下方旧F0–F6队列。下方凌晨/早晨的260状态为历史，不是现状。\n\n'
pos=s.find('\n')+1;s=s[:pos]+'\n'+header+s[pos:];a.write_text(s,encoding='utf-8')
print('Current entries updated; backups retained; no original paid artifacts changed')
if __name__=='__main__':pass
