# candidates/v3_system_slim/mechanism/mechanism.py
#
# 唯一变量 = system 提示资产精简（HARNESS_OPTIMIZATION §5.2 首个删除实验）。
# 父本 = candidates/reference_product_v1 —— 记忆读/写、检索、上下文拼接、输出契约
# 全部逐字转发父本实现，本候选不改机制、不改存储、不改措辞之外的任何东西，只把
# 长 core/format/redline/naturalness 资产换成最短可工作的任务+行为说明，用于回答：
#   - 删掉大量文本后，意图执行、事实、互动有没有变差？
#   - 原先被框架压平的主动性/调侃/关系推进有没有改善？
#   - 真实省了多少输入 token、等待随之如何变化？
#   - 哪些明确失败可归因于删除后缺失的任务信息？
#
# 注：控制器 set 的 ctx["assets_root"] 指向本候选目录下的 assets/，父本 build_system_prompt
# 因此读取本目录的精简资产；机制代码仍是父本同一份（memory I/O 字节一致）。
import importlib.util
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_CAND = os.path.dirname(_HERE)                       # candidates/v3_system_slim
_BASE = os.path.join(os.path.dirname(_CAND), "reference_product_v1")
sys.path.insert(0, _BASE)


def _load_base():
    spec = importlib.util.spec_from_file_location(
        "ref_product_v1_mechanism", os.path.join(_BASE, "mechanism", "mechanism.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


base = _load_base()
pm = base.pm
rc = base.rc

# 逐字转发父本接口——本候选不改机制，只改 assets/system_prompt/*.md
build_system_prompt = base.build_system_prompt
init = base.init
run_turn = base.run_turn
snapshot = base.snapshot
restore = base.restore
