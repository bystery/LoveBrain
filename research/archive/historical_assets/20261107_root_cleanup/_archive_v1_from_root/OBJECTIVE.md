# Guanjia Harness 核心循环目标与真实战场验收标准 (OBJECTIVE.md)

> 本文件是整个自进化循环的「北极星指标」。  
> 评测绝不是刷玩具题，核心分数由三大真实高难度项目战场决定：**题目决定了你应该有什么架构！**

---

## 一、终极目标定义 (What is the Goal?)

我们不是在写空洞的 Prompt，而是在构建一个**生产级 Guanjia Harness（管家自动化评测基准套件与核心引擎）**。

### 核心检验场景：
1. **考场 1：LoveBrain 11KB 复杂需求全量召回与代码锚定 (CHALLENGE_01)**
   - 包含 15 项交织着情绪、回滚至 131、气泡配色、滑动删除、军师注意、弹窗删除、模板泄漏修复等极度复杂需求。
   - 目标：需求召回率必须达到 100%（15/15 全部入账），逐字保真度 100%，深度代码理解与 Kotlin 仓库锚定。
2. **考场 2：ACL 2027 CFR 科研无训练推理机制调度与科研防火墙 (CHALLENGE_02)**
   - 目标：硬件严格隔离在 A100 GPU 2，严禁修改 frozen/ 实验数据，4 臂实验设计完备，跨部门规范派发。
3. **考场 3：Guanjia / AIOps 三层能力自愈与宿主环境自动对齐 (CHALLENGE_03)**
   - 目标：冷启动项目安全注入 [GUANJIA-BOOT] 且防覆盖，修复 config.json 与 cap() 漏洞，三层能力全部转绿。

---

## 二、真实战场复合目标函数 (Objective Function)

每一轮候选配置 $\theta$ 的综合得分 $J(\theta) \in [0, 100]$ 计算公式：

$$J(\theta) = 0.50 \times \text{RealChallengeScore}(\theta) + 0.25 \times \text{UnitAssertionScore}(\theta) + 0.15 \times \text{TokenEfficiency}(\theta) + 0.10 \times \text{Speed}(\theta)$$

### 1. 真实战场得分 $\text{RealChallengeScore}(\theta) \in [0, 100]$（占据 50% 核心权重！）
由三大真实项目（LoveBrain 15 项、CFR 科研 6 项、AIOps 自愈 5 项）的召回率与沙箱 Schema 校验分加权求和。  
任何在 LoveBrain 复杂需求中的漏项（如漏掉滑动删除或透明度滑块），将直接导致扣分并触发失败归因报告！

### 2. 基础架构断言得分 $\text{UnitAssertionScore}(\theta) \in [0, 100]$（占据 25% 权重）
由 25 个物理沙箱单元测试（每个 5 分）的代码级断言得分累加。

### 3. Token 效率得分 $\text{TokenEfficiency}(\theta) \in [0, 100]$（占据 15% 权重）
全量运行真实战场的总 Token 消耗量 $T$：
$$\text{TokenEfficiency}(\theta) = \max\left(0, \min\left(100, 100 - \frac{\max(0, T - 40000)}{600}\right)\right)$$

### 4. 响应速度得分 $\text{Speed}(\theta) \in [0, 100]$（占据 10% 权重）
全量真实战场总耗时 $S$（秒）：
$$\text{Speed}(\theta) = \max\left(0, \min\left(100, 100 - \frac{S}{15}\right)\right)$$

---

## 三、五大一票否决门禁 (Hard Constraints)

任何一轮候选方案若触碰以下红线，当轮得分直接判 0 分并立刻回滚：
1. **C1. 绝不报错未定义命令**：任何大白话输入下出现“未定义”或“undefined”。
2. **C2. 严禁无凭证假装结项**：没有沙箱内物理 EVR-*.json 证据文件，擅自将任务标为 done。
3. **C3. 严格瘦身预算**：`IDENTITY.md` 行数 > 15 行（防止小模型注意力漂移）。
4. **C4. 逐字原话保存**：`raw_input` 与用户真实输入字符相似度 < 1.0（禁止任何所谓的语义美化）。
5. **C5. 编码能力绝不开独立部门**：遇到代码需求擅自创建 `departments/coding/` 独立窗口（必须是 Layer 1 基础能力）。
