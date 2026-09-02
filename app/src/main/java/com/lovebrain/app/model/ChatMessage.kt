package com.lovebrain.app.model

import java.util.UUID

/** 一条对话消息（带角色） */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Role(val label: String) {
        HER("她"),
        ME("我"),
        IDEA("想法")
    }
}
