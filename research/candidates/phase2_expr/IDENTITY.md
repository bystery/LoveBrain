# IDENTITY.md — phase2_expr 候选身份声明

| 项 | 值 |
|---|---|
| 候选名 | phase2_expr |
| 父版本 | candidates/phase1_retr_idf（检索 v2.2，已冻结） |
| 唯一主变量 | 表达/选择策略（assets/system_prompt/expression.md 四规则 + 生成后词面审计） |
| 与父版本逐字节一致部分 | 检索实现（mechanism.py 直接复用父版本模块，无副本）、其余 assets/（15 文件）、params.json、存储形态、写回政策、单阶段编排、memory-off 语义 |
| 机制差异文件 | mechanism/mechanism.py（expression.md 注入 system + expr_trace.jsonl 审计落盘） |
| 新增资产 | assets/system_prompt/expression.md（正文视角/经历出处/他人动机/旧事主体） |
| 契约 | string\|null（与父版本一致，params.json 逐字节一致） |
| 记忆开关 | 支持（委托父版本） |
| 追踪 | retrieval_trace.jsonl（父版本）+ expr_trace.jsonl（本候选新增，评估专用，不入快照、不注入请求、不改写回复） |
| 回归 | tools/test_expr_candidate.py + 父版本全套回归 |
| 审计口径 | 词面检测=可疑标记非定罪（与 retr_report.py 同政策）；F4 主体核对登记为 manual_review |
| 状态 | 开发候选——正式晋级冻结；真人感改善须由三臂对照生成结果证明，不因提示已写入而宣称 |
| 受控未做 | JSON 结构保障（重试/约束解码）未实施；调岗类"失效/替代"写回未实施（append-only 维持） |
