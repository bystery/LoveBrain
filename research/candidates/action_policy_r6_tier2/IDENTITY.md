# 身份

candidates/action_policy_r6_tier2：r5_tier + 依据分层文本 v2（单点改动，仅 mechanism/mechanism.py 的 _TIER 行）。v2 依据 D1 头对头两个失败模式：①来源包装（记忆里的来源只支撑"一般如此"，禁止升格"这次一定"，保留出处口吻）②细节具名化禁令（数量/位置/成色/编号/时间点输入没有的禁止具名化）。本文件不写自身 identity_sha256（改文档会自指循环），散列只登记在外部冻结 JSON。
