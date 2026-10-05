package com.lovebrain.app.domain

/**
 * 建库问卷 AI 输出的解析器（纯函数，零依赖）。
 *
 * 从 KnowledgeBaseActivity 下沉：Activity 只负责展示，「按 marker 切段 + 判定画像是否可用」
 * 属于领域规则，必须能在 JVM 单测里被真实调用。
 */
object OnboardingResultParser {

    const val MARKER_DISPLAY = "===DISPLAY==="
    const val MARKER_STAGE = "===STAGE==="
    const val MARKER_ME = "===ME==="
    const val MARKER_HER = "===HER==="
    const val MARKER_WARMTH = "===WARMTH==="

    /**
     * 一次问卷生成结果。
     *
     * [hasUsableProfile] 为真才算「画像生成成功」；任一必备段为空只是降级建库，
     * UI 必须据此区分完整成功与模板库，不得把降级当成功。
     */
    data class Parsed(
        val display: String,
        val stage: String,
        val me: String,
        val her: String,
        val warmth: String
    ) {
        val hasUsableProfile: Boolean
            get() = me.isNotBlank() && her.isNotBlank() && warmth.isNotBlank()
    }

    fun parse(raw: String): Parsed = Parsed(
        display = section(raw, MARKER_DISPLAY, MARKER_STAGE),
        stage = section(raw, MARKER_STAGE, MARKER_ME),
        me = section(raw, MARKER_ME, MARKER_HER),
        her = section(raw, MARKER_HER, MARKER_WARMTH),
        // WARMTH 是最后一段，没有结束 marker
        warmth = raw.substringAfter(MARKER_WARMTH, "").trim()
    )

    /** 取 [startMarker] 与 [endMarker] 之间的内容；marker 缺失或顺序错乱时返回空串 */
    fun section(text: String, startMarker: String, endMarker: String): String {
        val start = text.indexOf(startMarker)
        if (start < 0) return ""
        val contentStart = start + startMarker.length
        val end = text.indexOf(endMarker, contentStart)
        return (if (end > contentStart) text.substring(contentStart, end) else "").trim()
    }
}
