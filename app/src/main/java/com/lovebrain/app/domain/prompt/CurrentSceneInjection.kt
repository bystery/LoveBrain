package com.lovebrain.app.domain.prompt

import com.lovebrain.app.model.ChatMessage

/**
 * 本轮场景判定与场景段注入（自 `domain/PromptBuilder.kt` 的「当前场景推断」行为块搬出）。
 *
 * 这块的职责只有一句话：**给定本轮的真实消息，说出这一轮属于哪一种场景，并把它写成
 * prompt 里那段「【当前场景】」**。它不读知识库、不读资产、不读时钟，也不持有任何状态，
 * 因此是纯函数——搬运前后对同一批输入必须给出逐字相同的字符串（字节证据见
 * `domain/prompt/PromptByteFreezeBaselineTest` 的冻结表）。
 *
 * 语义约束（搬运时一条都没改）：
 * - 场景是**本轮属性**，不永久改档案，只影响本轮生成策略；段标题那句「不改变长期阶段档案」
 *   是给模型看的契约，不是注释。
 * - 判定不叫模型，只按关键词和消息模式；**优先级即语义**：收尾 > 争执 > 解释或认错 >
 *   主动邀约 > 认真沟通 > 轻松互逗 > 日常分享。同时命中「随便你」和「周末有空吗」时给争执，
 *   这个先后顺序就是行为本身，不许顺手排「更合理」的序。
 * - 认错只看**用户本人**说的话，对方说「对不起」不算本轮要认错。
 * - 没有真实消息（只有想法行）时返回空串——场景段整段省略，不给模型一个猜出来的场景。
 */
object CurrentSceneInjection {

    /**
     * 从本轮消息内容推断当前场景。
     * 不调用模型，仅基于关键词和消息模式的简单规则判断。
     * 返回场景名称，供 prompt 注入。
     */
    fun infer(messages: List<ChatMessage>): String {
        val realMessages = messages.filter {
            it.role == ChatMessage.Role.HER || it.role == ChatMessage.Role.ME
        }
        if (realMessages.isEmpty()) return ""
        val lastHer = realMessages.lastOrNull { it.role == ChatMessage.Role.HER }
        val allText = realMessages.joinToString(" ") { it.content }.lowercase()

        // 收尾判断——对方说要睡、要忙、暂时不聊
        if (lastHer != null) {
            val herText = lastHer.content.lowercase()
            val closingKeywords = listOf("睡了", "睡觉", "晚安", "先忙", "去忙", "不聊了", "下次再聊", "明天再说", "去洗澡", "去洗漱", "先走了", "去吃饭")
            if (closingKeywords.any { herText.contains(it) }) return "收尾"
        }

        // 争执判断——语气冲突、负面情绪
        val conflictKeywords = listOf("生气", "烦死", "不想理", "随便你", "你总是", "你每次", "又来", "有意思吗", "懒得说", "你能不能", "为什么总是", "你到底", "不是你的错难道是我的错")
        if (conflictKeywords.any { allText.contains(it) }) return "争执"

        // 解释/认错判断——用户需要道歉
        val apologyKeywords = listOf("对不起", "抱歉", "我的错", "我错了", "原谅", "不应该", "是我不好", "是我没做好")
        val userMessages = realMessages.filter { it.role == ChatMessage.Role.ME }.joinToString(" ") { it.content }.lowercase()
        if (apologyKeywords.any { userMessages.contains(it) }) return "解释或认错"

        // 主动邀约判断——对方提出见面或活动
        if (lastHer != null) {
            val inviteKeywords = listOf("见面", "约", "一起", "出来", "去吃", "去看", "周末", "有空吗", "能不能", "方便吗")
            if (inviteKeywords.any { lastHer.content.lowercase().contains(it) }) return "主动邀约"
        }

        // 认真沟通判断——表达情绪、认真讨论
        val seriousKeywords = listOf("难过", "不开心", "压力大", "焦虑", "想哭", "委屈", "不知道怎么办", "纠结", "在想", "其实我", "说实话", "心里")
        if (seriousKeywords.any { allText.contains(it) }) return "认真沟通"

        // 轻松互逗判断——玩笑、表情
        val playfulKeywords = listOf("哈哈", "笑死", "233", "狗子", "笨蛋", "讨厌", "哼", "略略", "😏", "😂", "嘻")
        if (playfulKeywords.any { allText.contains(it) }) return "轻松互逗"

        // 默认——日常分享
        return "日常分享"
    }

    /**
     * 构建当前场景注入块。
     * 场景是本轮属性，不永久改档案，只影响本轮生成策略。
     */
    fun block(scene: String): String {
        if (scene.isBlank()) return ""
        return "# 【当前场景】（本轮属性，不改变长期阶段档案）\n场景：$scene\n\n"
    }
}
