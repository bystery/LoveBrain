package com.lovebrain.app.model

import kotlinx.serialization.Serializable

/** 知识库元数据（对应 kb.json） */
@Serializable
data class KnowledgeBase(
    val name: String = "",             // 文件夹名
    val displayName: String = "",      // 显示名
    val updatedAt: String = "",
    val stage: String = "",
    val turnCount: Int = 0,
    val topicCount: Int = 0,
    val active: Boolean = false
)
