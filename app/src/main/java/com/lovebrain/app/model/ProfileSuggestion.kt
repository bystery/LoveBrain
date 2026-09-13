package com.lovebrain.app.model

/** 画像更新建议（KBG-02：绑定 originating KB 身份，防串库） */
data class ProfileSuggestion(
    val kbName: String,
    val display: String,
    val rawJson: String
)
