package com.lovebrain.app.model

/**
 * 今日锦囊缓存的形状（JSON + 日期 + 库 id + 上下文指纹 + prompt 版本）。
 *
 * 原来内嵌在 `data/SecurePrefs` 里；[com.lovebrain.app.domain.port.SettingsStorePort] 要把
 * `loadSuggestion()` 暴露给页面侧，端口不能 import data，所以这个纯数据形状搬到 model——
 * 字段与顺序逐字照搬，`?.let { (json, date) -> … }` 的解构依赖这个顺序。
 */
data class SuggestionCache(
    val json: String,
    val date: String,
    val kbId: String,
    val contextFingerprint: String,
    val promptVersion: String
)
