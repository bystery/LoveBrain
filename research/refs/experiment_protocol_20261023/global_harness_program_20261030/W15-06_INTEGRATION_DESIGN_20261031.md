# W15-06 原产品接入设计：接口 / 迁移 / 回退（App 保持暂停，本文档只交设计）

日期：2026-10-31 ｜ 约束：不让设备/JDK/Android 缺件阻塞研究；不先美化接入
上游依据：W15-05 范围裁定（推荐候选 r9_slim）、W14-01/02/03 性能口径、W03-05 暴露纪律

## 1. 接口设计（App ↔ harness）

- **形态**：harness 以本地服务运行（现有 dsf 网关 + 两臂批管线外的单轮服务模式，api_candidate
  build_request_body 白名单不变）；App 只做两件事：上报对话上下文（persona/initial_kb/
  messages/clock，即 episode.json 的运行时等价物）、接收响应 JSON。
- **输出合同冻结点**：format.md 的 JSON Schema（response 四风格键名逐字 recommended/bad_boy/
  playful/warm + directions 四槽 + analysis 七键 + facts/tentatives/updates 写回）。键名漂移
  = 格式作废（既有裁判语义），接入层不得改写键名。
- **审计通道**：analysis 全字段 + usage + timing 分段随响应原样带回 App 侧落盘（评分与费用
  可核算的前件，W14-01 口径）。
- **记忆写回**：facts/tentatives/updates 由 harness 引擎侧处理（r1 簿记），App 不重复实现；
  跨会话持久化文件由 harness 沙箱持有。

## 2. 迁移路径（三阶段，每阶段独立可回退）

| 阶段 | 形态 | 判据 |
|------|------|------|
| S1 shadow | App 照常走原逻辑；harness 并行生成仅落盘不展示 | 对照九维 ≥ 原逻辑（judge 另册）；成本/延迟在 Pareto 表内 |
| S2 单风格 | 仅 recommended 替换原逻辑产出，directions 只做内部埋点 | 人工样本对（W 愿发 12 对）+ severe 不增 |
| S3 全量 | 四风格+directions 全接入 | S2 连续两周无回退触发 |

- 每阶段使用身份经 custody 对账（hash verify），防止"接入的身份不是评过的身份"。

## 3. 回退设计

- **特征开关**：App 侧单开关（原逻辑 ↔ harness），按轮可切；开关状态进审计。
- **回退触发器**（继承冠军触发器）：保护维 ≤−0.5 / severe 增多 / veto 率 ≥3 倍 n≥192 /
  p95 延迟超原逻辑 2 倍 / 生成失败率 >1%。
- **回退动作**：切回原逻辑零数据迁移（harness 只读上下文，App 数据主权不动）；harness 侧
  状态文件保留供事后归因。
- **升级通道**：身份更替（如 r9_slim→下一代）走 custody 注册 + 重放等价 + live 批三步，
  禁止热替换。

## 4. 明确不做（暂停纪律）

- 不改 App 代码、不建构建环境、不做 UI——用户解除暂停后本设计才进入实施排期。
- 不为接入美化任何评测数字：接入判据全部沿用程序内已预登记口径。
