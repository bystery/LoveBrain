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
    const val GENERATE_MAX_ATTEMPTS = 4   // 主生成总尝试次数（1 次初始 +3 次重试； 预算 3→4 使候选④none 可达）
    // 主生成总超时的可选档位住在本文件尾部的 [GenerationTimeoutTier]：
    // 默认档就是上面的 GENERATE_TIMEOUT_MS，逐工单可调，连接/写超时不跟着放开。
    // 三条生成链路（主回复 / 谈心 / 主动开场）**共用**这一张快照上的预算，
    // 也就是共用 [GenerationTimeoutTier] 这一把尺——不再有任何一条留自己的固定秒数。

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
    // 面板背景的透明程度**不在这一段**：那是用户可设的持久化项，不是尺寸常量，
    // 住在文件尾部的 [PanelBackdropOpacity]。

    // ═══ 悬浮球（纯主球形态：单击开面板、拖拽吸附、闲置降透明）═══
    // 球的**直径**不在这一段：那是用户可设的三档，住在文件尾部的 [BubbleSizeTier]，
    // 窗口宽高、图标比例、角标偏移、clamp 与吸边全从那一颗推（M04）。
    const val BUBBLE_EDGE_MARGIN = 2         // 吸附后距屏幕边缘留白（2dp，近乎贴边又不被系统手势区遮挡）
    const val BUBBLE_SNAP_MS = 250           // 边缘吸附动画时长——去掉吸附震动，保留平滑滑向动画
    const val BUBBLE_DRAG_THRESHOLD_DP = 20  // 点击 vs 拖拽判定阈值（累计位移≥20dp 才算拖拽，否则抬起=点击）

    // ═══ 悬浮球：闲置降遮挡 + 入场动画 ═══
    const val BUBBLE_IDLE_DIM_MS = 4000L     // 闲置 4s 无交互 → 半透明（AssistiveTouch 降遮挡思路）
    // 闲置那一格降多少、降到哪里为止，不在这里：它跟着用户设的百分比走，
    // 算式在 [PanelBackdropOpacity.effectiveAlpha]（BUBBLE_IDLE_ALPHA 就是那把尺的降幅锚点）。
    const val BUBBLE_IDLE_ALPHA = 0.78f      // 闲置降遮挡的锚点：完全不透明那一端降到 0.78（保持 3:1 对比度下限）
    const val BUBBLE_ENTRANCE_STIFFNESS = 300f   // 入场 spring 刚度（慢而稳的浮入）
    const val BUBBLE_ENTRANCE_DAMPING = 0.7f     // 入场 spring 阻尼（轻微过冲）

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
    // ⚠ 这一颗**不跟着下面那格一起收敛**：它是"长按 ↔ 菜单"的事件配对预算，不是重复合并。
    const val PENDING_MAX_AGE_MS = 30_000L
    // 洪峰去重窗口：仅拦 ≤300ms 内**同一文本**的重复事件（同一次手势的系统连发），不拦用户主动重捕。
    // 含义只有这一条，不是"所有消息的统一冷却"：判据（含"不同文本一律放行"）住在
    // `service/ClipBurstDedup`，300ms 这一格的边界钉在 `test/.../data/EventBusCaptureTest.kt`。
    // （1500→300 是用户要的那一格捕获手感；延时、请求超时、待捕获事件寿命、长按识别阈值都不吃这颗数）
    const val BURST_DEDUP_WINDOW_MS = 300L

    // ═══ 知识库更新触发 ═══
    const val LESSON_TRIGGER_INTERVAL = 5    // 每积累 5 个话题触发一次经验提取
    const val LESSON_CONTEXT_TOPICS = 25      // 经验提取时从话题档案倒取最近 25 个话题
    const val REFLECT_TRIGGER_INTERVAL = 5   // 每积累 5 个话题触发一次画像更新
    const val REFLECT_CONTEXT_TOPICS = 5     // 画像更新时从话题档案倒取最近 5 个话题
}

/**
 * 生成总超时的**有界白名单**：只有这四档，用户填不进别的数。
 * 主回复、谈心、主动开场三条链路共用它，谁的秒数都不另立一颗。
 * （来自用户反馈：非官方兼容服务的速度与内容长度都超过固定 120 秒）
 *
 * 为什么要档位而不是一个自由输入框：用户反馈的是非官方 OpenAI-compatible 服务
 * 的速度与内容长度都超过固定 120 秒；但把总超时交给用户随手写一个数，
 * 就等于允许"卡死的请求变成无限等待"——那正是复核点名不要的东西。
 *
 * 这一档**只放开读的那一侧**：
 * - 三条生成链路的总超时（主回复、谈心、主动开场的 `withTimeout`）都按档位取；
 *   它们吃的是同一份请求快照里的 `ProviderRequestConfig.generateTimeoutMs`，
 *   所以"这一张工单等多久"在全局只有一份答案，不存在按屏另配一套秒数的第二条尺；
 * - 流式 SSE 的读超时（[AppConfig.STREAM_READ_TIMEOUT_SEC]）跟着同一档走，
 *   否则总超时放开到 300 秒、而 120 秒没有新 token 就被 OkHttp 掐断，档位等于白给；
 * - [AppConfig.CONNECT_TIMEOUT_SEC] 与 [AppConfig.WRITE_TIMEOUT_SEC] **不跟着走**：
 *   联系不上服务器时该快速失败，不该让用户对着转圈等 300 秒。
 *
 * 主动开场**以前留着一颗固定 45 秒**，当时的理由是"这一屏不是她在等一条长回复
 * 的关键路径，坏服务上别多转半分钟"。这颗固定值本轮被删掉，改吃工单快照，理由是：
 * 用户要的是"超时时间"这一项设置对**所有生成**成立——同一张慢速工单下，主回复能等到 300 秒、
 * 主动开场却在 45 秒截断，等于设置页上那颗选择只有一半为真，而"哪一半为真"在界面上读不出来。
 * 副作用写在调用点注释里，不在这里藏。
 *
 * 档位挂在**每一张工单**上（`ProviderTicket.generateTimeoutSec`），不是全局唯一值：
 * 官方 Key 与自建慢服务可以各留各的等待预算。
 */
enum class GenerationTimeoutTier(val seconds: Int) {
    SEC_60(60),
    SEC_120(120),
    SEC_180(180),
    SEC_300(300);

    /** 这一档换算成毫秒——`withTimeout` 吃这个数。换算在本仓库只写这一处。 */
    val millis: Long get() = seconds * 1000L

    companion object {

        /** UI 画的就是这四颗（列表顺序即展示顺序） */
        val options: List<GenerationTimeoutTier> get() = listOf(SEC_60, SEC_120, SEC_180, SEC_300)

        /** 精确命中白名单才算有效：`null`（升级前的老数据里根本没写过这一项）与任何非档位值都是 null */
        fun fromSecondsOrNull(seconds: Int?): GenerationTimeoutTier? =
            if (seconds == null) null else options.firstOrNull { it.seconds == seconds }

        /**
         * 回落口——复核要求"在代码里能看出这条回落"指的就是这一行：
         * 脏数据 / 老数据 / 从没配过 ⇒ 默认档，绝不把原样照抄的数字用出去，
         * 也绝不因为读不出而退回 0 秒或无限等待。
         */
        fun fromSecondsOrDefault(seconds: Int?): GenerationTimeoutTier =
            fromSecondsOrNull(seconds) ?: DEFAULT

        /** 默认档跟着 [AppConfig.GENERATE_TIMEOUT_MS] 取：两处那个 120 不可能分家 */
        val DEFAULT: GenerationTimeoutTier =
            fromSecondsOrNull((AppConfig.GENERATE_TIMEOUT_MS / 1000).toInt()) ?: SEC_120
    }
}

/**
 * 面板**背景层**的不透明度——设置页那颗滑杆背后唯一的真值。
 *
 * 量纲：整数百分比，越大越不透明（越"实"）。盘上存整数，`0f..1f` 那个浮点只在画的那一帧出现，
 * 是派生量不是持久量。选整数而不是 float 落盘的三条理由：
 * - 界面给用户看的本来就是"百分比"这个整数刻度，读盘 → 画滑杆 → 回写必须逐字往返；
 *   浮点存盘要么带回误差（`0.78f` 读回来还是不是 `0.78f`），要么退化成字符串再解析；
 * - 脏值判定（"这在不在合法区间内"）是整数比较，回落那一行肉眼可读；
 * - 与本仓已有的 [GenerationTimeoutTier] 同一种形状：有界区间 + 一个显式回落口，
 *   绝不把盘上读到的原样数字直接画出去。
 *
 * 区间 `60..100`，默认 `100`，刻度 5%：
 * - 默认 100% 就是今天的样子（面板底是一整块实色），这一笔上线时没人会发现界面变了；
 * - 下限取 60%：这是实施书对"面板背景不透明度"划的那条线（`0.6..1.0`）。再往下调，
 *   面板压在白色聊天界面上和压在深色照片上只差在底那一层，字与宿主的字会糊成一团；
 *   曾经放到 40% 是滑得爽但越过了这条线，现在按规格收回 60。
 *
 * ⚠ **压的是背景与大面积卡底，不是整扇窗**。把整棵 `ComposeView.alpha` 一起降下来会连正文一起洗淡
 * （文字先于背景失去可读性），而且那条 alpha 归面板的淡入淡出动画所有
 * （`service/OverlayPanelWindow` 里淡出结束与再次打开时都会把它复位）。
 * 作用点只有面板那两层底色（`ui/panel/LoveBrainPanelScreen.kt` 的根与设置页那一层），
 * 加上悬浮球整颗（主图标 + 未读角标）——**四处共用下面 [effectiveAlpha] 这一把尺**。
 * 只把面板根调淡、球不跟着走，等于"同一个百分比"只做了一半。
 * 底色自身的 alpha 通道与 `View.alpha` 是两层乘积：动画淡出时两者相乘，动画复位碰不到颜色。
 */
object PanelBackdropOpacity {

    /**
     * SecurePrefs 的键名（整数存盘）。键面只在这里出现一次，别处不许再抄字面量。
     *
     * 读写通路就一条，接法固定（`data/SecurePrefs.kt` 里加这一对，别再开第二个 getter）：
     * ```
     * var panelBackdropOpacityPercent: Int
     *     get() = PanelBackdropOpacity.snapPercent(
     *         prefs.getInt(PanelBackdropOpacity.PREF_KEY, PanelBackdropOpacity.DEFAULT_PERCENT))
     *     set(value) = prefs.edit()
     *         .putInt(PanelBackdropOpacity.PREF_KEY, PanelBackdropOpacity.snapPercent(value)).apply()
     * ```
     * 画的那一侧取 [alphaOf]，滑杆上显示的那个数取 [transparencyOf]。
     */
    const val PREF_KEY = "panel_backdrop_opacity_percent"

    const val MIN_PERCENT = 60
    const val MAX_PERCENT = 100

    /** 默认 = 完全不透明：面板外观与这一笔之前逐字相同，滑杆没被动过的用户不该看到变化 */
    const val DEFAULT_PERCENT = 100

    /** 滑杆刻度。存盘前对齐刻度，界面上的数字与盘上的数字才可能一模一样。 */
    const val STEP_PERCENT = 5

    /** 给滑杆用的合法取值序列（从小到大，展示顺序即此顺序）。 */
    val steps: List<Int> get() = (MIN_PERCENT..MAX_PERCENT step STEP_PERCENT).toList()

    /**
     * 读盘与写盘共用的唯一一格：
     * - `null`（这台机器从没滑过 / 升级前的老数据里根本没这一项）⇒ [DEFAULT_PERCENT]；
     * - 越界的脏值 ⇒ 钳到最近的合法端点，而不是照抄出去。区间的两端本身就是
     *   "看得见"与"读得清"那两条线，所以钳到端点永远比原样用一个非法值安全；
     *   这一格与本仓 `thinkingMode` 读写两侧都 `coerceIn` 是同一个形状。
     * - 区间内的非刻度值 ⇒ 就近对齐到 [STEP_PERCENT]。
     *
     * 无论进来的是什么，出去的值一定落在 `steps` 里：0 与负数不可能变成"面板看不见"。
     */
    fun snapPercent(raw: Int?): Int {
        if (raw == null) return DEFAULT_PERCENT
        val clamped = raw.coerceIn(MIN_PERCENT, MAX_PERCENT)
        val tick = (clamped - MIN_PERCENT + STEP_PERCENT / 2) / STEP_PERCENT
        return (MIN_PERCENT + tick * STEP_PERCENT).coerceIn(MIN_PERCENT, MAX_PERCENT)
    }

    /** 画的那一步唯一的换算口：盘上的整数 → 颜色 alpha。非法值同路回落，最暗也就到 [MIN_PERCENT]。 */
    fun alphaOf(raw: Int?): Float = snapPercent(raw) / 100f

    /** 界面上那颗标着"透明度"的滑杆要显示的数字：不透明度 100% 就是透明度 0%。 */
    fun transparencyOf(raw: Int?): Int = MAX_PERCENT - snapPercent(raw)

    /** 反向换算口：界面交来的是"透明度百分比"时走这里。镜像关系只写这一处，别在界面上手算 `100 - x`。 */
    fun fromTransparencyPercent(transparency: Int?): Int =
        snapPercent(transparency?.let { MAX_PERCENT - it })

    // ═══ 有效 alpha：悬浮球 + 面板背景共用的那一把尺（指导书 2026-10-10 §1「同一百分比同时作用」）═══

    /**
     * 闲置降遮挡的**绝对降幅**，直接从原来那颗 [AppConfig.BUBBLE_IDLE_ALPHA] 推出来（1f − 0.78f = 0.22f）。
     * 之所以是"减法挪一格"而不是"再乘一层"：乘法是第二次衰减——用户已经调到 60% 时再乘 0.78 会掉到
     * 0.468，图标淡到找不着，而那既不是用户要的也不是原意图（原意图只在"完全不透明"这一端要求降到 0.78）。
     * 取默认档（100%）时本式算出来就是 0.78f，闲置观感与这一笔之前逐字相同。
     */
    val IDLE_DIM_DELTA: Float = 1f - AppConfig.BUBBLE_IDLE_ALPHA

    /**
     * 闲置态的可读下限：降到这里为止，不再往下。
     * 这一格是"降遮挡"与"球还在不在"那条线——比面板下限 60% 更宽，因为球没有正文要读，
     * 但它是唯一的手势入口，淡到点不中就等于功能消失。
     */
    const val IDLE_ALPHA_FLOOR: Float = 0.50f

    /**
     * **这一帧该画多浓**——悬浮球（主图标 + 未读角标）与面板背景都只从这一颗取值。
     *
     * 语义四条，缺一条就会长出对应的坏实现：
     * 1. 静止时**严格等于用户设置**（`dimmed = false` 就是 [alphaOf]，不额外加暗一档）；
     * 2. 闲置时按原有"降遮挡"意图变淡（[IDLE_DIM_DELTA]），但**永不低于 [IDLE_ALPHA_FLOOR]**；
     * 3. 闲置结束回到用户设置值，不会变得比用户设的更不透明（同一颗纯函数，进出对称）；
     * 4. 只算一次：结果里已经含了用户档位与闲置降档，画的那一侧**不许再乘** `BUBBLE_IDLE_ALPHA`
     *    或再乘一次本函数的结果（两次相乘正是 0.468 那一格）。
     *
     * 单调且不越过用户设置：`dimmed` 为真时结果 ≤ 静止值，且随用户档位升高而升高。
     */
    fun effectiveAlpha(opacityPercentRaw: Int?, idleDimmed: Boolean): Float {
        val rest = alphaOf(opacityPercentRaw)
        if (!idleDimmed) return rest
        return (rest - IDLE_DIM_DELTA).coerceAtLeast(IDLE_ALPHA_FLOOR)
    }
}

/**
 * 悬浮图标大小的三档（指导书 2026-10-10 §1「大小调节」）。
 *
 * 只做三档、不做连续 1dp 调节：原话写明「三个档位已覆盖日常需求，也更容易保证布局和手势正确」。
 * 标准档（[STANDARD_DP] = 56dp）就是这一项出现之前屏幕上那颗球（原来的 `AppConfig.BUBBLE_SIZE`
 * 那颗单一常量已随这一笔退场，全仓不许再有第二个尺寸真值），所以 §1 验收里
 * 「默认外观与现有版本一致」由默认值成立，不靠改画。
 *
 * **单一真值**：系统窗口宽高、主球与内部图标比例、未读角标偏移、当前位置的边界修正、
 * 拖动吸边的落点、点击区、面板依据球位算出的贴边坐标，全都必须从同一颗数推出来。
 * 只对 Compose 用 `scale()` 而把窗口点击区留在 56dp，正是这一格要防的那件事
 * （书 §1：「不能只对 Compose 使用 scale()，而保持系统窗口原来的 56dp 点击区域」）。
 */
object BubbleSizeTier {

    /** 落盘键：读写都只在 `SecurePrefs.bubbleSizeDp` 这一格，画侧不许再发明第二颗键。 */
    const val PREF_KEY: String = "bubble_size_dp"

    const val SMALL_DP: Int = 48
    const val STANDARD_DP: Int = 56
    const val LARGE_DP: Int = 64

    /** 默认档 = 现有版本的外观（§1 验收第一条）。 */
    const val DEFAULT_DP: Int = STANDARD_DP

    /** 合法档位，从小到大；界面上「小 / 标准 / 大」三颗按这个顺序画。 */
    val ALLOWED_DP: List<Int> = listOf(SMALL_DP, STANDARD_DP, LARGE_DP)

    /**
     * 读盘与写盘共用的唯一一格：非法值**就近对齐**到相邻合法档（与 [PanelBackdropOpacity.snapPercent]
     * 同形状——越界的脏值不许变成"看不见的一颗球"或"点不中的窗口"）。
     * 落在两档正中间时取较小的那一档（[ALLOWED_DP] 从头扫、第一个最小者胜出，结果确定）。
     */
    fun snapDp(raw: Int?): Int {
        if (raw == null) return DEFAULT_DP
        return ALLOWED_DP.minByOrNull { kotlin.math.abs(it - raw) } ?: DEFAULT_DP
    }
}

/**
 * 回复结果卡片的排列方向（指导书 2026-10-10 §3）。
 *
 * 默认纵向；横向＝「保留现在的横向卡片排列」那一档。两档共用同一套卡逻辑
 * （稳定身份、复制/赞/踩/改写、自定义改写、已赞已踩筛选、流式与已完成内容），
 * 且切换方向**不重新请求 AI、不重新付费、不清空当前回复**。
 *
 * 落盘存枚举名，先例见 `viewmodel/GuideCursor.from` 与 `SecurePrefs.guideCursor`：
 * 脏值与"这台机器从没写过"都回落到 [DEFAULT]，**不许抛**。
 */
enum class ReplyCardLayout {

    /** 每张卡单独占一行，按当前方案顺序纵向排列（默认）。 */
    VERTICAL,

    /** 现有的横向卡片排列。 */
    HORIZONTAL;

    companion object {

        /** 落盘键：读写都只在 `SecurePrefs.replyCardLayout` 这一格。 */
        const val PREF_KEY: String = "reply_card_layout"

        /** 默认方向（§3「默认开启」＝纵向）。 */
        val DEFAULT: ReplyCardLayout = VERTICAL

        /** 解析口只有这一处：画侧与 VM 都不许再手写 `== "VERTICAL"` 那种第二次判断。 */
        fun from(raw: String?): ReplyCardLayout =
            raw?.let { name -> entries.firstOrNull { it.name == name } } ?: DEFAULT
    }
}
