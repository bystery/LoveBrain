# 身份

candidates/action_policy_r7_supersede：E3 链（活引擎 r1）+ 引擎侧取代 pass。唯一机制改动：r1._merge_facts 包装，每次合并后执行矛盾扫描——新 confirmed reported fact 带更正线索词（实际是/看错/改成/延期/作废等）且与旧 confirmed reported fact 双字 gram 重叠≥0.42 时，旧 fact 降级 status=superseded（历史保留、updates 记 auto_supersede）。种子（kbin:/persona:）永不降级；每新 fact 至多降级 1 个旧 fact。附带 e3_memdump 的 notes 快照钩子供离线重放验证。flag 关 = 逐字节复现 E3。本文件不写自身 sha256（外部冻结 JSON 登记）。
