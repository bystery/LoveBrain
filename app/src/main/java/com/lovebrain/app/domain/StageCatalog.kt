package com.lovebrain.app.domain

/**
 * 关系阶段目录：八阶段白名单，全带"期"后缀。
 * 所有阶段值的写入（onboarding 推断 / 向量建议 / reflect 画像更新）与匹配
 * （stage.md / suggest.md 小节提取）一律经此归一化——根治 / 的三套命名打架。
 * 邀约期已废除（动作非状态）：九阶段 → 八阶段。
 */
object StageCatalog {

    val ALL: List<String> = listOf(
        "初识期", "破冰期", "暧昧期",
        "热恋期", "磨合期", "稳定期", "危机期", "修复期"
    )

    const val UNKNOWN = "待确定"

    /**
     * 归一化：去空白；若为合法阶段但缺"期"后缀则补上（兼容旧数据/AI 偶发少字）。
     * @return 白名单内的规范阶段名；不在白名单返回 null（调用方应拒绝写入）
     */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed in ALL) return trimmed
        if ("${trimmed}期" in ALL) return "${trimmed}期"
        return null
    }

    /** 归一化或回落 UNKNOWN（读侧展示用，不拒写场景） */
    fun normalizeOrUnknown(raw: String): String = normalize(raw) ?: UNKNOWN
}
