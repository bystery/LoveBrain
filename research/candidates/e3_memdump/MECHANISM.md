# e3_memdump — 机制说明

父本：action_policy_r4_guard_e3 逐字节复制 + notes 通道 memdump 摘要钩子（调用后、guard sink 注入前）。
数据流：h["mem"] = {facts:{fid:{text,status,ts,relation}}, updates:[{action,fact_id,new_text}]}，每轮结束即时刻快照。
消费方：tools/analyze_memdump.py（W05-02 内容级 fidelity：per-fact capture/recall/precision vs stage4_src 事实表 + 更正 supersession 核验）。
边界：零额外语义行为；不改变提示、不改记忆写回逻辑、不参赛。
