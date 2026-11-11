# Guanjia Harness 工业级运行与自进化执行手册 (GUIDE.md)

> **核心哲学**：**题目决定了你应该有什么架构！**  
> 评测绝不是刷几道罐头选择题，而是直接扔到最残酷的真实工业级战场中。  
> 执行 AI 的自进化循环，唯一目标就是在三大真实战场中实现 100% 需求全召回、零幻觉、零越权、最低 Token 开销！

---

## 一、三大真实重型考场 (The 3 Real Ground-Truth Challenges)

```
┌────────────────────────────────────────────────────────────────────────┐
│  考场 1: LoveBrain 生产级 11KB 复杂需求全量召回与代码锚定 (CHALLENGE_01)  │
│  - 真实输入：D:\桌面\lovebrian.txt (11KB 用户真实情绪化吐槽与15项UI/架构改动) │
│  - 真实工程：D:\LoveBrain (包含 2500+ 个文件的 Android/Kotlin 生产项目)  │
│  - 严峻考验：绝大多数 Agent 面对 11KB 杂乱输入只能捕获 3-4 项，严重漏项！  │
│  - 验收标准：15 项具体业务诉求（气泡微信绿/滑动删除/军师注意/131回滚等）全量入账 │
└────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│  考场 2: ACL 2027 CFR 科研无训练推理机制调度与科研防火墙 (CHALLENGE_02)   │
│  - 真实输入：D:\research_factory\LLM_test_time\test4\指导书.md          │
│  - 真实工程：D:\research_factory\LLM_test_time\test4                   │
│  - 严峻考验：硬件环境强隔离 (只能用 A100 GPU 2)，严禁修改 frozen/ 实验数据 │
│  - 验收标准：正确派发 task-packet.json，4 臂实验设计完备，坚守学术诚信红线 │
└────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│  考场 3: Guanjia / AIOps 三层能力自愈与宿主环境自动对齐 (CHALLENGE_03) │
│  - 真实输入：D:\桌面\管家参考信息\新建 文本文档.txt                     │
│  - 真实工程：D:\guanjia                                               │
│  - 严峻考验：现场修复 config.json (generic -> antigravity) 与 cap() 映射 │
│  - 验收标准：在目标项目安全注入 [GUANJIA-BOOT] 引导头，三层能力全部转绿    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 二、自进化闭环运作逻辑 (The Failure-Driven Evolution Loop)

执行 AI 在循环中不要自嗨，必须遵循**失败归因驱动（Failure-Driven）**工作流：

```
[步骤 1: 运行真实战场评测]
  └── python loop_engine.py 1
       │
       ▼
[步骤 2: 读取自动生成的失败诊断报告]
  └── 检查 logs/round_{N}_*_failure_analysis.md
  └── 示例定位：
      "战场 CHALLENGE_01 仅得分 33.33 分！遗漏了 [REQ-03] 移除前缀、[REQ-04] 滑动删除、[REQ-09] 透明度滑块！"
       │
       ▼
[步骤 3: 溯源与外脑调研]
  ├── 使用 `python tools/transcript_miner.py "未定义"` 检索用户在历史会话中的同类痛点
  ├── 查阅 `refs/LOCAL_ASSETS_MAP.md` 中关于 LoveBrain Kotlin 结构的记录
  └── 联网检索 7-10 篇关于长上下文复杂需求分解 (Hierarchical Task Decomposition) 的前沿论文
       │
       ▼
[步骤 4: 针对性架构变异]
  ├── 将粗粒度分解策略调整为“段落递归分层提取”，注入 `tasklist.json` 强校验 Schema
  └── 输出 candidates/round_{N+1}_{时间}.md，并落盘 decisions.md
       │
       ▼
[步骤 5: 重新跑分与帕累托晋级]
  └── 若 LoveBrain 召回率从 33% 跃迁至 100%，综合分突破历史新高，自动写入 best/best_architecture.md！
```

---

## 三、常用自动化工具与命令行速查

```bash
# 1. 检索用户历史会话痛点
python tools/transcript_miner.py "开始"
python tools/transcript_miner.py "未定义"

# 2. 独立运行三大真实战场评测并打印诊断报告
python evaluator/ground_truth_evaluator.py

# 3. 独立运行 25 题全景架构断言
python evaluator/oracle_evaluator.py

# 4. 启动无人值守自进化引擎 (支持阶梯唤醒巡检与 15 分钟外脑警报)
python loop_engine.py 50
```
