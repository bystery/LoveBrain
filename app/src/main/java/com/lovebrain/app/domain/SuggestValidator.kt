package com.lovebrain.app.domain

import com.lovebrain.app.model.DailySuggestion
import com.lovebrain.app.model.SuggestTip

/**
 * 锦囊最终解析校验器——产品合同验证。
 *
 * 规则：
 * - 6-8 条 tips（不足 6 条标记 partial；超过 8 条截断）
 * - 唯一 ID（重复 ID 去重，保留首次出现）
 * - action 非空（action 为空的条目被过滤）
 * - 分类合法（timingCategory 只允许三个标准值或空）
 * - action 去重（相同 action 只保留首次）
 */
object SuggestValidator {

    private val VALID_CATEGORIES = setOf("现在可用", "今天可准备", "有机会再做")

    /**
     * 校验并规范化锦囊结果。
     * @return 校验后的 DailySuggestion（可能标记 partial）
     */
    fun validate(suggestion: DailySuggestion): DailySuggestion {
        val seenIds = mutableSetOf<String>()
        val seenActions = mutableSetOf<String>()
        val validatedTips = mutableListOf<SuggestTip>()

        for (tip in suggestion.tips) {
            // action 必须非空
            val action = tip.action.trim()
            if (action.isBlank()) continue

            // action 去重
            if (action in seenActions) continue
            seenActions.add(action)

            // ID 去重——非空且已存在的 ID 直接跳过（保留首次出现）
            if (tip.id.isNotBlank() && tip.id in seenIds) continue
            // 空ID或新ID生成稳定ID
            val stableId = if (tip.id.isNotBlank()) tip.id else "tip-${validatedTips.size + 1}"
            if (stableId in seenIds) continue
            seenIds.add(stableId)

            // 分类合法化——不合法的归空
            val normalizedCategory = if (tip.timingCategory in VALID_CATEGORIES) {
                tip.timingCategory
            } else {
                ""
            }

            validatedTips.add(tip.copy(
                id = stableId,
                action = action,
                timingCategory = normalizedCategory
            ))

            // 最多 8 条
            if (validatedTips.size >= 8) break
        }

        val isPartial = validatedTips.size < 6 || suggestion.partial
        return suggestion.copy(
            tips = validatedTips,
            partial = isPartial
        )
    }
}
