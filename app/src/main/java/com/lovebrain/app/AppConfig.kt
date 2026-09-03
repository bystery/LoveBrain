package com.lovebrain.app

/**
 * 全局配置常量。消除散布在各处的硬编码魔法数字。
 */
object AppConfig {

    // ═══ 网络 ═══
    const val API_BASE_URL = "https://api.deepseek.com/v1"
    const val CONNECT_TIMEOUT_SEC = 15L
    const val READ_TIMEOUT_SEC = 60L
    const val STREAM_READ_TIMEOUT_SEC = 120L
    const val WRITE_TIMEOUT_SEC = 15L
    const val GENERATE_TIMEOUT_MS = 120_000L
    const val SUGGEST_TIMEOUT_MS = 45_000L   // 锦囊独立超时：45s（比主生成短，避免"1 分钟还在转圈"）
    const val GENERATE_MAX_ATTEMPTS = 4   // 主生成总尝试次数（1 次初始 +3 次重试； 预算 3→4 使候选④none 可达）

    // ═══ 模型参数 ═══
    const val TEMPERATURE_MAIN = 0.7
    const val TEMPERATURE_RAW = 0.5
    const val DEFAULT_MODEL = "deepseek-v4-flash"

    // ═══ 面板尺寸 (dp) ═══
    const val PANEL_DEFAULT_W = 300
    const val PANEL_DEFAULT_H = 420
    const val PANEL_MIN_W = 260
    const val PANEL_MIN_H = 300
    const val PANEL_MAX_W = 350
    const val PANEL_MAX_H = 680

    // ═══ 悬浮球（纯主球形态：单击开面板、拖拽吸附、闲置半透明/半隐藏）═══
    const val BUBBLE_SIZE = 56               // 主球直径 56dp（M3 FAB 标准尺寸，触控达标）
    const val BUBBLE_EDGE_MARGIN = 2         // 吸附后距屏幕边缘留白（2dp，近乎贴边又不被系统手势区遮挡）
    const val BUBBLE_SNAP_MS = 250           // 边缘吸附动画时长（第 4 轮：去掉吸附震动，保留平滑滑向动画）
    const val BUBBLE_DRAG_THRESHOLD_DP = 20  // 点击 vs 拖拽判定阈值（累计位移≥20dp 才算拖拽，否则抬起=点击）

    // ═══ 悬浮球（第 2 轮迭代：闲置降遮挡 + 入场动画）═══
    const val BUBBLE_IDLE_DIM_MS = 4000L     // 闲置 4s 无交互 → 半透明（AssistiveTouch 降遮挡思路）
    const val BUBBLE_IDLE_ALPHA = 0.78f      // 闲置半透明 alpha（保持 3:1 对比度下限）
    const val BUBBLE_ENTRANCE_STIFFNESS = 300f   // 入场 spring 刚度（慢而稳的浮入）
    const val BUBBLE_ENTRANCE_DAMPING = 0.7f     // 入场 spring 阻尼（轻微过冲）

    // ═══ 悬浮球（第 3 轮迭代：侧边半隐藏 + 无障碍降动画）═══
    const val BUBBLE_HIDE_IDLE_MS = 8000L    // 闲置 8s（半透明之后）→ 滑出侧边半隐藏（QQ 悬挂思路）
    const val BUBBLE_HIDE_EDGE_DP = 12       // 半隐藏后露边宽度（可点击回弹）

    // ═══ 悬浮球角标（ 令牌化：spring 参数外放，数值不变）═══
    const val BUBBLE_BADGE_ENTER_DAMPING = 0.45f   // 角标入场 spring 阻尼（轻微弹跳）
    const val BUBBLE_BADGE_ENTER_STIFFNESS = 900f  // 角标入场 spring 刚度
    const val BUBBLE_BADGE_EXIT_DAMPING = 0.85f    // 角标退出 spring 阻尼（接近临界，无弹跳）
    const val BUBBLE_BADGE_EXIT_STIFFNESS = 800f   // 角标退出 spring 刚度
    const val BUBBLE_BADGE_POP_DAMPING = 0.5f      // 角标弹出 spring 阻尼（未读出现时 1.35→1 回弹）
    const val BUBBLE_BADGE_POP_STIFFNESS = 600f    // 角标弹出 spring 刚度

    // ═══ 话题管理 ═══
    const val MAX_TOPIC_TURNS = 2          // recent.md 只保留最近 2 轮，溢出→对话暂存
    const val SCENE_CHAIN_MAX_HOURS = 2    //  第 2 步：超过 2h 的状态条目移入状态暂存（原 6h，防场景链膨胀）
    const val SCENE_CHAIN_MAX_ENTRIES = 2  //  第 2 步：scene.md 最多保留 2 条，超出移入状态暂存（原 6 条）
    const val VECTOR_REESTIMATE_INTERVAL = 3 // 每 3 个话题转换重估一次五维状态向量
    const val VECTOR_CONTEXT_TOPICS = 5    // 向量重估时从话题档案倒取最近 5 个话题

    // ═══ Prompt 预算（字符数） ═══
    const val TOTAL_BUDGET = 9000

    // ═══ 上下文窗口保护 ═══
    const val REPLY_MAX_MESSAGES = 60          // 回复 prompt 超过 60 条消息时掐尾保留最近的消息
    const val COUNSELING_MAX_HISTORY_ROUNDS = 6 // 谈心追问只带最近 6 轮问答（防 context length）

    // ═══ 无障碍服务 ═══
    // pending 生命期（H2）：超过 30s 的旧暂存不再消费——慢菜单(>3s)照捕，只拦久远残留
    const val PENDING_MAX_AGE_MS = 30_000L
    // 洪峰去重窗口：仅拦 ≤1.5s 内同文本的重复事件（同一次手势的系统连发），不拦用户主动重捕
    const val BURST_DEDUP_WINDOW_MS = 1500L

    // ═══ 知识库更新触发 ═══
    const val LESSON_TRIGGER_INTERVAL = 5    // 每积累 5 个话题触发一次经验提取
    const val LESSON_CONTEXT_TOPICS = 25      // 经验提取时从话题档案倒取最近 25 个话题
    const val REFLECT_TRIGGER_INTERVAL = 5   // 每积累 5 个话题触发一次画像更新
    const val REFLECT_CONTEXT_TOPICS = 5     // 画像更新时从话题档案倒取最近 5 个话题
}
