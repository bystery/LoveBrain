# V14 独立复核证据

本目录为审查结果，不是考场已通过凭证。报告与执行书在父目录。

- INDEPENDENT_PROBES.json：七组独立控制流/实际宿主/本机替身探针。
- ADDITIONAL_PROBES.json：基线入口损坏、通用更正、覆盖和意图/主体诊断。
- SAFE_REGRESSIONS.json、*.py.log：10 套安全回归记录。
- INTEGRITY.json、SOURCE_SNAPSHOT.json：原包 484 文件散列与原项目核对。
- audit_v14.py：独立探针源码。仅能复制到一个新建、一次性审查目录运行；同目录需有从指定 v14 zip 安全提取的 package/。不要直接在本证据目录或主项目运行，不要覆盖旧证据。脚本用自己的合成候选/资料，ACL 方法替换为仅记录目标的安全守卫；本机替身不连接实际模型。

没有运行完整 ACL 总验收，没有读取真实 B 线、dataset 或真实 API 密钥，没有修改项目源文件。实际宿主原始运行证据在本会话审查目录 evidence/rt/saved_before_stage/，这里保留摘要与执行源码，避免把 staged 和绝对运行现场混为发布 payload。
