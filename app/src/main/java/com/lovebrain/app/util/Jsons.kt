package com.lovebrain.app.util

/**
 * 统一 JSON 提取与转义工具（DRY：消除多处正则/截取拷贝）。
 *
 * extractJsonObject 使用括号深度扫描，正确处理：
 * - 字符串内的括号（`"key": "val}ue"` 不误匹配）
 * - 转义字符（`\"` `\\` 等）
 * - 外围说明文字（`这是建议：{...}` 可提取）
 * - 代码围栏包装（```json ... ```）
 * - 截断 JSON（括号不配对时返回 null）
 *
 * 不处理多个对象歧义：只返回第一个完整对象。
 */
object Jsons {

    /**
     * 从模型原文中提取第一个完整的 JSON 对象字符串。
     *
     * 流程：
     * 1. 去除代码围栏包装（```json ... ``` 或 ``` ... ```）
     * 2. 从第一个 `{` 开始，逐字符扫描，跟踪字符串上下文和括号深度
     * 3. 深度归零时截取完整对象
     * 4. 截断（未配对）返回 null
     *
     * @return 完整 JSON 对象字符串，或 null
     */
    fun extractJsonObject(raw: String): String? {
        var s = raw.trim()

        // 去除代码围栏
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            val fenceEnd = s.indexOf("```")
            if (fenceEnd >= 0) {
                s = s.substring(0, fenceEnd).trim()
            }
        }

        // 扫描第一个完整 { ... }
        val start = s.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escape = false
        var end = -1

        for (i in start until s.length) {
            val c = s[i]

            if (inString) {
                if (escape) {
                    escape = false
                } else when (c) {
                    '\\' -> escape = true
                    '"' -> inString = false
                    // 其他字符在字符串内，不影响括号计数
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            end = i
                            break
                        }
                        if (depth < 0) return null // 结构错误
                    }
                }
            }
        }

        return if (end >= 0) s.substring(start, end + 1) else null
    }

    /**
     * 旧接口兼容：等同于 extractJsonObject。
     * @deprecated 使用 [extractJsonObject] 代替。
     */
    fun extractJsonBlock(raw: String): String? = extractJsonObject(raw)

    /** JSON 字符串转义：顺序 反斜杠→双引号→换行（与 unescapeJsonString 严格互逆） */
    fun escapeJsonString(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    /** JSON 字符串反转义：单遍扫描处理 \\ \" \n 三种转义。
     *  禁止链式 replace——顺序耦合会把字面 "\n"（如 C:\new）误还原成真换行 */
    fun unescapeJsonString(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> { append('\\'); i += 2 }
                    '"'  -> { append('"');  i += 2 }
                    'n'  -> { append('\n'); i += 2 }
                    else -> { append(c);    i += 1 }
                }
            } else { append(c); i += 1 }
        }
    }
}
