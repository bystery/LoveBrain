"""Activate the user's global program, retaining the narrower old entries as history."""
import datetime,json
from pathlib import Path
ROOT=Path(r'D:\lovebrain-arena');HERE=Path(__file__).parent
ap=ROOT/'AGENTS.md';old=ap.read_text(encoding='utf-8-sig')
marker='> 本文件是斗兽场的最高纪律。'
assert marker in old
tail=old[old.index(marker):]
head='''# AGENTS.md — LoveBrain 全局持续主线 v1.6（2026-10-30）

> **最新用户要求，覆盖旧的单问题收口限制**：固定基础模型，对完整Harness持续大规模迭代。不能把总任务缩成“仅修无据确定化”“仅做提交整理”或一个局部故障族；质量、意图、知识、上下文、正向互动、提示/架构、消融、泛化、性能与交付共同推进。原型交付不取消长期优化。

> **唯一当前入口**：先读WORKER_CURRENT_RUNBOOK.json。本轮总书=refs/experiment_protocol_20261023/global_harness_program_20261030/MASTER_CONTINUOUS_HARNESS_PROGRAM.md；需求回顾=同目录USER_REQUIREMENTS_TRACEABILITY.md；机读队列=TASK_QUEUE.json；恢复状态=RESEARCH_STATE.json；新窗口开启=WORKER_NEW_WINDOW_START.md。下方旧日期写的“当前/下一包”仅作历史，冲突时以本节与本轮总书为准。

> **完整主线**：公平原版和最小对照→故事与证据/自由度量尺→提示与机制竞争候选→真实共同对照→一级质量与严重错误→消融/删除/组合→新故事和长链验证→费用/等待/复杂度权衡→保留/淘汰→下一轮。不能只修工具、只省token、只防错误或只写报告。

> **多轨与执行量**：当前96项/16包，约144–288有效工程小时规划，完成后按实证补未来48–72小时可执行工作。数据/评估、架构/机制、实验/复现、独立核验按文件所有权并行；宿主支持时可委派子代理/外部实施。一个慢裁判、一个局部错误或用户未填W表不得冻结其它轨。不是无人值守288小时承诺，不凑工时，不虚报后台运行。

> **必须继承**：F0–F6与B/C执行已有结果，不从头重跑。保留E3；r5分层提示NOT-CONFIRMED不晋级。F3四篇72对38/10/24是r4开发集证据；F1混合旧身份不归r4。B24次受控知识实验和C24+19次已做，B11/12不是首选或全知识准确率。E3不因补身份全量重抽。已有runner/预算/冻结/绑定/归一器/compiler复用。

> **质量与自由度**：一级为事实/主体/时间/状态、知识正确使用、目标及自然互动/主动/调情/深聊/反馈；费用、等待、可靠性、复杂度二级。参考答案是证据与合理行动集合，不是唯一句子；新提议不作虚构过去，合法不回应评动作；首选与备选分审，不要求短回复。用户愿发由本人评价，AI不代投。

> **授权/模型**：既有持续实验授权内自行登记有限目的scope，核既有管理帽和未决，不反复每48次问口令，不复活关闭单/无限重试。原16,384帽不因任务书再新增。基座同批固定dsf/0.7/17770/v1；辅助允许hy3、dsf、qwen3.8-flash、gemini-3.8-flash，按代表性实际任务速度和资格选择；慢/空返路线退出关键路径，换裁判登记新cohort/必要桥接，不静默混尺。每alias跨角色合计≤3；hy4-preview硬禁。

> **范围/交付**：D:/LoveBrain只读比较，产品接入暂停，不做管理员/ACL/防火墙前置。后台helper隐藏启动。阶段原型与论文式结果可随时准确交付，不能把截止/文档整理变成ACCEPT，也不能因原型已打包就停止研究。每轮交全局状态、机制假设、真实质量/错误/代价、保留淘汰、下一轮与持久恢复入口。

'''
ap.write_text(head+tail,encoding='utf-8')
p=ROOT/'WORKER_CURRENT_RUNBOOK.json';d=json.loads(p.read_text(encoding='utf-8-sig'))
prev=d.get('master_runbook')
d.update({'updated_date':'2026-10-30','updated_at':datetime.datetime.now().astimezone().isoformat(),
 'authority':'用户2026-10-30要求回顾全局全部目标，大任务量、完整主线、长期连续迭代；覆盖上一轮仅做单问题的范围限制',
 'master_runbook':str(HERE/'MASTER_CONTINUOUS_HARNESS_PROGRAM.md'),
 'requirements_traceability':str(HERE/'USER_REQUIREMENTS_TRACEABILITY.md'),
 'execution_queue':str(HERE/'TASK_QUEUE.json'),'research_state':str(HERE/'RESEARCH_STATE.json'),
 'start_prompt':str(ROOT/'WORKER_NEW_WINDOW_START.md'),
 'previous_master_runbook':prev,
 'instruction_bundle':str(HERE/'LoveBrain_GLOBAL_CONTINUOUS_20261030.zip'),
 'overall_status':'REWORK_GLOBAL_OPTIMIZATION_CONTINUES',
 'priority':'多轨完整循环：公平原版/最小对照、数据评分、提示架构、意图/知识/上下文/正向互动、真实对照、消融、新故事与完整性能；单问题不得代表全主线',
 'status_source':'原始代码、模型答卷、评分证据、实际进程与预算；总书和计划小时不等于正在运行或实验完成',
 'planning_turn_new_model_calls':0,'new_global_budget_added':0,
 'global_scope_supersedes_single_fault_only_instruction':True})
d['latest_independent_review_note']='仅继承旧复核中的事实与指标；其中仅修一个问题的任务范围已被用户最新全局任务书覆盖。'
if 'user_deadline_date' in d:d['historical_submission_deadline_date']=d.pop('user_deadline_date')
p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(ROOT/'WORKER_NEW_WINDOW_START.md').write_text((HERE/'NEW_WINDOW_START_PROMPT.md').read_text(encoding='utf-8'),encoding='utf-8')
assert Path(d['master_runbook']).exists() and Path(d['execution_queue']).exists()
q=json.loads(Path(d['execution_queue']).read_text(encoding='utf-8'))
assert len(q['tasks'])==96
print('Global instructions active; old single-fault scope superseded; 96-task queue linked; no experiments started by planning')
