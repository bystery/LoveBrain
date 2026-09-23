package com.lovebrain.app.model

import java.util.UUID

/**
 * 画像更新建议（绑定 originating KB 身份，防串库）。
 *
 * 携带 suggestionId 供确认时幂等核验；携带已验证的 ProfileUpdate payload，
 * 生成摘要和确认写入使用同一个已校验类型化对象，不重复解析 raw。
 * 携带 correctionsRevision 供确认时核验版本未变。
 */
data class ProfileSuggestion(
    val suggestionId: String = UUID.randomUUID().toString(),
    val kbName: String,
    val display: String,
    val rawJson: String,
    /** 已验证的画像更新 payload；valid=false 时不可确认 */
    val profileUpdate: ProfileUpdate? = null,
    /** 生成时的纠正 revision，确认时核验是否过期 */
    val correctionsRevision: Int = 0
) {
    /** 是否可确认（payload 校验通过） */
    val canConfirm: Boolean get() = profileUpdate?.valid == true
}
