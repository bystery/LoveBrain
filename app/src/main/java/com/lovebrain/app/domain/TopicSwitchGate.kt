package com.lovebrain.app.domain

import com.lovebrain.app.util.TimeFmt

/**
 * F2.1 话题切换加固门——"topic_status=new 不再单独触发清空/归档"。
 *
 * 背景（refs/version_closeout_20261106/T5_KNOWN_FAILURES_AUDIT.md F2.1）：
 * 旧判据 [TopicRecorder] 只看模型单轮输出的 `topic_status=new`（TopicRecorder.kt:123），
 * 模型误判一次就把 moment/scene.md 与 moment/recent.md 清空归档，场景记忆整体丢失。
 *
 * 新规则：`topic_status=new` 只是**主张**，必须有**至少一个佐证信号**才允许旋转
 * （佐证全部来自既有数据字段，不新增模型调用）：
 *
 *  - [SIGNAL_SCENE_EMPTY]：moment/scene.md 已无可解析条目 **且** moment/recent.md 也没有
 *    当前层内容——没有场景记忆可丢，误判的清空代价为零，不阻塞真实切换。
 *    **审计 #09 修正**：recent.md 仍留着当前话题内容时**不算**"无可丢记忆"
 *    （旋转会把 recent 一并清空归档，那里有真实可丢的对话记忆）。
 *  - [SIGNAL_TIME_GAP]：scene 链最新证据距本轮（取本轮最新消息时刻与当前时刻较大者）
 *    已超过 [MIN_SCENE_GAP_MS]——话题自然冷却，切换可信。
 *  - [SIGNAL_LABEL_CHANGE]：新 topic_label 与当前话题标签不同——模型自己在说"换了个话题"。
 *    **R5 佐证独立性修正（refs/version_closeout_20261106/R5_TOPIC_GATE_INDEPENDENCE.md）**：
 *    该标签与 `topic_status=new` 出自**同一次模型输出**，模型一起错时 LABEL_CHANGE
 *    反而会"佐证"错误切换（同源共误）。因此它降级为**模型源信号**：单独命中不再触发
 *    切换，只能与其他**非模型源信号**共同出现（锦上添花，不能定案）。
 *  - [SIGNAL_USER_MARKER]：本轮 HER/ME 消息里出现 [EXPLICIT_NEW_TOPIC_MARKERS] 中的
 *    显式换话题表述。这是**确定性白名单、只做佐证**，与已废弃的"关键词列表判场景"不是
 *    一回事：命中是充分佐证之一，未命中不扣任何分。
 *
 * **R5 定案规则**：触发切换需要至少一个**非模型源信号**。模型源信号（目前只有
 * [SIGNAL_LABEL_CHANGE]）永不独立定案。
 *
 * **审计 #09 弱信号规则**：[SIGNAL_SCENE_EMPTY] 与 [SIGNAL_TIME_GAP] 是**弱非模型信号**——
 * 单独一个不得定案，需要第二个非模型信号（旧场景空/证据久都不代表本轮真换了话题，
 * 但**用户显式说换话题**是确定性的）。[SIGNAL_USER_MARKER] 仍可单独定案。
 *
 * 隐私纪律：本类产出的诊断文本只含信号名、毫秒/分钟数与布尔结论，
 * 不含消息正文与话题标签正文（调用方 [TopicRecorder] 直接落 L.w 日志）。
 *
 * 本类是纯函数对象，不碰任何端口——判据本身单独可测（TopicSwitchGateTest），
 * 带落盘后果的端到端行为在 TopicSwitchHardeningTest 里走真实仓库验证。
 */
internal object TopicSwitchGate {

    /** scene 链最新证据与本轮的最小间隔：超过即视为"话题已自然冷却"的佐证 */
    const val MIN_SCENE_GAP_MS: Long = 60 * 60_000L

    /** 用户显式换话题标记（保守白名单；只做佐证信号，不做场景判定） */
    val EXPLICIT_NEW_TOPIC_MARKERS: List<String> = listOf(
        "换个话题", "换话题", "说点别的", "聊点别的", "说别的", "聊点其他", "新话题", "不聊这个"
    )

    const val SIGNAL_SCENE_EMPTY = "SCENE_EMPTY"
    const val SIGNAL_TIME_GAP = "TIME_GAP"
    const val SIGNAL_LABEL_CHANGE = "LABEL_CHANGE"
    const val SIGNAL_USER_MARKER = "USER_MARKER"

    /**
     * 佐证结论。
     * @param confirmed 是否允许话题旋转（归档 + 清空此刻层）
     * @param signals 命中的佐证信号名（确认时非空）
     * @param rejectionReason 被否决时的原因（只含信号名与数值，不含正文）
     */
    data class Decision(
        val confirmed: Boolean,
        val signals: List<String>,
        val rejectionReason: String?
    )

    /**
     * 评估一次 `topic_status=new` 主张。调用方仅在模型主张 new 时调用本函数。
     *
     * 定案规则（R5 + 审计 #09）：必须命中**可定案的非模型源信号组合**——
     *  - [SIGNAL_USER_MARKER]（确定性用户表述）单独即可定案；
     *  - [SIGNAL_SCENE_EMPTY]/[SIGNAL_TIME_GAP] 是弱信号，单独一个不得定案，
     *    需要第二个非模型信号；
     *  - [SIGNAL_LABEL_CHANGE] 与 status=new 同一次输出（同源共误），永不参与定案。
     *
     * @param topicLabel 模型给出的新话题标签（调用方已确认非空）
     * @param currentTopic 当前话题标签（[com.lovebrain.app.data.KnowledgeRepository.getCurrentTopic]
     *                     已经是纯标签——读法归 KbTextOps.topicLabel 单一所有者，这里只做 trim）
     * @param sceneContent moment/scene.md 原文
     * @param recentContent moment/recent.md 原文——审计 #09：scene 空但 recent 层仍有
     *                      当前话题内容时不许当"无可丢记忆"（必传，调用方必须明示这一事实）
     * @param messageContents 本轮 HER/ME 消息正文（仅用于显式标记匹配，不进日志）
     * @param latestMessageTimeMs 本轮最新消息时间戳（无消息传 0，回退用 nowMs）
     * @param nowMs 当前毫秒时刻
     */
    fun evaluate(
        topicLabel: String,
        currentTopic: String,
        sceneContent: String,
        recentContent: String,
        messageContents: List<String>,
        latestMessageTimeMs: Long,
        nowMs: Long
    ): Decision {
        if (topicLabel.isBlank()) {
            return Decision(confirmed = false, signals = emptyList(), rejectionReason = "topic_label 为空，无新话题可落")
        }

        val signals = mutableListOf<String>()
        val newestSceneMs = newestSceneTimeMs(sceneContent)
        val recentHasContent = hasRecentLayerContent(recentContent)

        // S1：scene 链已空 **且** recent 层也没有内容——真的无可丢失记忆（审计 #09）。
        //     recent.md 仍有当前话题内容时不发该信号：旋转会清空 recent，那里有可丢记忆。
        if (newestSceneMs == 0L && !recentHasContent) {
            signals.add(SIGNAL_SCENE_EMPTY)
        }

        // S2：时间间隔——scene 最新证据距本轮已超过阈值
        if (newestSceneMs > 0) {
            val anchor = maxOf(latestMessageTimeMs, nowMs).takeIf { it > 0 } ?: nowMs
            val gap = anchor - newestSceneMs
            if (gap >= MIN_SCENE_GAP_MS) {
                signals.add(SIGNAL_TIME_GAP)
            }
        }

        // S3：标签语义变化——模型给出的新标签与当前标签不同
        val oldLabel = normalizeLabel(currentTopic)
        if (oldLabel.isNotBlank() && normalizeLabel(topicLabel) != oldLabel) {
            signals.add(SIGNAL_LABEL_CHANGE)
        }

        // S4：用户显式换话题标记
        if (messageContents.any { content -> EXPLICIT_NEW_TOPIC_MARKERS.any { content.contains(it) } }) {
            signals.add(SIGNAL_USER_MARKER)
        }

        // 定案（R5 + 审计 #09 弱信号规则）：
        // - LABEL_CHANGE 是模型源信号，永不参与定案；
        // - SCENE_EMPTY / TIME_GAP 是弱非模型信号，单独一个不得定案；
        // - USER_MARKER 单独即可定案；两个弱信号同时在场（当前判据下互斥，规则按通用写）也可定案。
        val nonModelSignals = signals.filterNot { it == SIGNAL_LABEL_CHANGE }
        val weakSignals = nonModelSignals.filter { it == SIGNAL_SCENE_EMPTY || it == SIGNAL_TIME_GAP }
        val strongSignals = nonModelSignals.filter { it == SIGNAL_USER_MARKER }
        return if (strongSignals.isNotEmpty() || weakSignals.size >= 2) {
            Decision(confirmed = true, signals = signals, rejectionReason = null)
        } else {
            val gapMin = if (newestSceneMs > 0) (maxOf(latestMessageTimeMs, nowMs) - newestSceneMs) / 60_000L else -1L
            val reason = when {
                signals.contains(SIGNAL_LABEL_CHANGE) && nonModelSignals.isEmpty() ->
                    "仅 LABEL_CHANGE（与 topic_status=new 同一次模型输出，同源共误，不可独立佐证）；" +
                        "scene最新条目间隔=${gapMin}min<${MIN_SCENE_GAP_MS / 60_000L}min、无显式换话题标记"
                weakSignals.size == 1 ->
                    "仅 ${weakSignals.single()}（弱佐证，需第二个非模型信号：旧证据久/此刻层空" +
                        "不代表本轮真换了话题）；无显式换话题标记、" +
                        if (signals.contains(SIGNAL_LABEL_CHANGE)) "LABEL_CHANGE 与主张同源不计" else "topic_label未变化"
                else ->
                    "无佐证信号（scene最新条目间隔=${gapMin}min<${MIN_SCENE_GAP_MS / 60_000L}min、" +
                        "topic_label未变化、无显式换话题标记" +
                        (if (recentHasContent) "、recent层仍有当前内容" else "") + "）"
            }
            Decision(
                confirmed = false,
                signals = emptyList(),
                rejectionReason = reason
            )
        }
    }

    /** recent 层是否还有内容（任何非空行都算，含 round marker 注释行——旋转会把它们一并清掉） */
    fun hasRecentLayerContent(recentContent: String): Boolean =
        recentContent.lines().any { it.isNotBlank() }

    /** scene.md 原文中最新的条目时间（毫秒）；无可解析条目返回 0 */
    fun newestSceneTimeMs(sceneContent: String): Long =
        sceneContent.lines().mapNotNull { line ->
            entryTimeRegex.find(line.trim())?.groupValues?.get(1)
        }.map { TimeFmt.parse(it) }.filter { it > 0 }.maxOrNull() ?: 0L

    /**
     * 标签归一化：只去首尾空白。话题标记前缀的剥离归 KbTextOps.topicLabel 单一所有者
     * （TopicLineFormatTest 把该字面量钉死在 KbTextOps.kt 一个文件里），这里不再抄一份。
     */
    private fun normalizeLabel(label: String): String = label.trim()

    private val entryTimeRegex = Regex("^- \\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\]")
}
