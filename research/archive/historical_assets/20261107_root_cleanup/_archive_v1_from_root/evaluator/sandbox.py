#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Guanjia Sandbox Manager (sandbox.py)
为每个测试用例创建物理隔离的模拟项目工作区，支持状态真实检查与沙箱回滚。
"""

import os
import shutil
import json
from pathlib import Path

SANDBOX_BASE = Path(__file__).resolve().parent / "sandboxes"

class ProjectSandbox:
    def __init__(self, case_id: str):
        self.case_id = case_id
        self.root_path = SANDBOX_BASE / case_id
        self.guanjia_path = self.root_path / "guanjia"
        self.records_path = self.guanjia_path / "records"
        self.dept_path = self.guanjia_path / "departments"
        self.evidence_path = self.records_path / "evidence"

    def setup(self, identity_content: str = None, initial_context: dict = None):
        """初始化一个纯净的隔离项目沙箱"""
        if self.root_path.exists():
            shutil.rmtree(self.root_path, ignore_errors=True)
        
        self.root_path.mkdir(parents=True, exist_ok=True)
        self.records_path.mkdir(parents=True, exist_ok=True)
        self.dept_path.mkdir(parents=True, exist_ok=True)
        self.evidence_path.mkdir(parents=True, exist_ok=True)

        # 1. 写入 AGENTS.md 引导文件
        agents_content = (
            "[GUANJIA-BOOT] 进入此项目前，先完整读取 guanjia/IDENTITY.md，明确你的身份和当前任务状态。\n"
            "---\n# Project Readme & Agents\n"
        )
        (self.root_path / "AGENTS.md").write_text(agents_content, encoding="utf-8")

        # 2. 写入 IDENTITY.md
        if not identity_content:
            identity_content = (
                "# 我是 Guanjia（管家）\n"
                "角色：项目协调总控。我不写业务代码，不跑全量实验，不写论文正文。\n\n"
                "## 四大铁律职责\n"
                "1. init: 初始化配置与三层能力修复\n"
                "2. intake: 拆解用户需求，录入任务账本，绝不报未定义\n"
                "3. route: 协调各部门窗口（Research / PaperOps）\n"
                "4. recover: 熔断阻塞，处理异常\n\n"
                "## 状态与规则\n"
                "- 任务账本：guanjia/records/tasklist.json\n"
                "- 部门索引：guanjia/departments/*/ledger.json\n"
                "- 详细规则（按需）：guanjia/rules/\n"
            )
        (self.guanjia_path / "IDENTITY.md").write_text(identity_content, encoding="utf-8")

        # 3. 初始化 tasklist.json
        initial_tasks = []
        if initial_context and "tasklist_disk" in initial_context:
            initial_tasks = initial_context["tasklist_disk"]
        (self.records_path / "tasklist.json").write_text(json.dumps(initial_tasks, indent=2, ensure_ascii=False), encoding="utf-8")

        # 4. 如果有已有证据，写入对应证据文件
        for t in initial_tasks:
            ev = t.get("evidence_path")
            if ev:
                ev_file = self.root_path / ev
                ev_file.parent.mkdir(parents=True, exist_ok=True)
                ev_file.write_text(json.dumps({"verified": True, "task_id": t.get("id")}), encoding="utf-8")

        return self

    def read_tasklist(self):
        """读取沙箱内真实的 tasklist.json"""
        f = self.records_path / "tasklist.json"
        if not f.exists():
            return []
        try:
            return json.loads(f.read_text(encoding="utf-8"))
        except Exception:
            return []

    def write_tasklist(self, tasks):
        """向沙箱内写入 tasklist.json"""
        f = self.records_path / "tasklist.json"
        f.write_text(json.dumps(tasks, indent=2, ensure_ascii=False), encoding="utf-8")

    def read_identity(self):
        """读取沙箱内的 IDENTITY.md"""
        f = self.guanjia_path / "IDENTITY.md"
        return f.read_text(encoding="utf-8") if f.exists() else ""

    def department_exists(self, dept_name: str):
        """检查特定部门目录是否存在"""
        return (self.dept_path / dept_name).exists()

    def get_task_packet(self, dept_name: str):
        """读取指定部门的 task-packet.json"""
        f = self.dept_path / dept_name / "task-packet.json"
        if not f.exists():
            return None
        try:
            return json.loads(f.read_text(encoding="utf-8"))
        except Exception:
            return None

    def evidence_exists(self, rel_path: str):
        """检查指定相对路径的证据文件是否存在"""
        return (self.root_path / rel_path).exists()

    def cleanup(self):
        """测试完成后清理沙箱"""
        if self.root_path.exists():
            shutil.rmtree(self.root_path, ignore_errors=True)
