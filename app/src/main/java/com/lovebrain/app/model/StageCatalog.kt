package com.lovebrain.app.model

/**
 * 关系阶段目录：八阶段白名单，全带"期"后缀。
 * 所有阶段值的写入（onboarding 推断 / 向量建议 / reflect 画像更新）与匹配
 * （stage.md 小节提取）一律经此归一化——根治的三套命名打架。
 * 邀约期已废除（动作非状态）：九阶段 → 八阶段。
 *
 * 迁移说明（PackageDependencyTest 基线清零批，2026-10）：本尊原先住在 `domain` 包，
 * 而 `ProfileUpdate` / `ProfileUpdateSchema` 解析 new_stage 时必须用这份白名单——
 * model 撞 domain 在禁单上。选**整体搬进 model**而不是拆数据或开端口，理由：
 * 它本质是一张纯数据目录（常量表 + 一个无副作用的归一化纯函数，不碰 IO/时钟/Android），
 * 没有 domain 行为可留；domain→model 不在禁止列表里，domain 侧调用方
 * （KnowledgeTriggerCoordinator 等）跟着换 import，语义与取值一个都没变。
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
