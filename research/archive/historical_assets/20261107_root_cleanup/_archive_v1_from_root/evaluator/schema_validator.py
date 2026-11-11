#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Guanjia Task Ledger Schema Validator (schema_validator.py)
严格对齐 guanjia-architecture-report.md 中定义的小本本 (tasklist.json) 契约结构。
"""

import json
from typing import Tuple, List

REQUIRED_TOP_FIELDS = ["session_id", "raw_input", "tasks"]
REQUIRED_TASK_FIELDS = ["id", "raw_fragment", "understanding", "delivery_target", "status", "assigned_to", "evidence"]
ALLOWED_STATUSES = ["pending", "in_progress", "done", "blocked", "paused", "awaiting_clarification"]
ALLOWED_ASSIGNEES = ["self", "subagent", "research", "paperops", "devops", "security"]

def validate_tasklist_schema(data) -> Tuple[bool, List[str]]:
    """
    验证 tasklist.json 是否符合架构报告定义的结构规范:
    [
      {
        "session_id": "...",
        "raw_input": "...",
        "tasks": [
          {
            "id": "T-001",
            "raw_fragment": "...",
            "understanding": "...",
            "delivery_target": "...",
            "status": "pending|in_progress|done|blocked|paused|awaiting_clarification",
            "assigned_to": "self|subagent|research|paperops",
            "evidence": "..."
          }
        ]
      }
    ]
    """
    errors = []
    if not isinstance(data, list):
        return False, ["tasklist 顶层必须是列表 (List)"]

    if len(data) == 0:
        return True, [] # 空账本是合法的初始态

    for s_idx, session in enumerate(data):
        if not isinstance(session, dict):
            errors.append(f"Session[{s_idx}] 必须是字典 (Dict)")
            continue

        for rf in REQUIRED_TOP_FIELDS:
            if rf not in session:
                errors.append(f"Session[{s_idx}] 缺失必要字段: '{rf}'")

        if "raw_input" in session and not isinstance(session["raw_input"], str):
            errors.append(f"Session[{s_idx}].raw_input 必须是字符串")

        tasks = session.get("tasks", [])
        if not isinstance(tasks, list):
            errors.append(f"Session[{s_idx}].tasks 必须是列表")
            continue

        for t_idx, task in enumerate(tasks):
            if not isinstance(task, dict):
                errors.append(f"Session[{s_idx}].tasks[{t_idx}] 必须是字典")
                continue

            for tf in REQUIRED_TASK_FIELDS:
                if tf not in task:
                    errors.append(f"Task[{task.get('id', t_idx)}] 缺失必要字段: '{tf}'")

            st = task.get("status")
            if st and st not in ALLOWED_STATUSES:
                errors.append(f"Task[{task.get('id')}] 状态 '{st}' 非法，必须属于 {ALLOWED_STATUSES}")

            # 铁律：status 为 done 时，evidence 必须存在且非空
            if st == "done":
                ev = task.get("evidence")
                if not ev or str(ev).strip() == "" or ev is None:
                    errors.append(f"Task[{task.get('id')}] status 为 'done' 但 evidence 字段为空（违反凭证铁律）")

    return len(errors) == 0, errors

if __name__ == "__main__":
    sample = [
        {
            "session_id": "2026-10-02-session-A",
            "raw_input": "帮我把登录报错修了",
            "tasks": [
                {
                    "id": "T-001",
                    "raw_fragment": "帮我把登录报错修了",
                    "understanding": "修复登录报错",
                    "delivery_target": "单元测试通过",
                    "status": "done",
                    "assigned_to": "self",
                    "evidence": "guanjia/records/evidence/EVR-001.json"
                }
            ]
        }
    ]
    ok, errs = validate_tasklist_schema(sample)
    print("Schema Check:", "PASSED" if ok else f"FAILED: {errs}")
