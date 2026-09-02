package com.lovebrain.app.util

/**
 * 统一"剥 ``` 包装 + 提取首个完整 {..} 块"（DRY：消除 parseReplyResponse/parseSuggestJson 两份拷贝）。
 * 返回首个完整 JSON 对象字符串；找不到返回 null。
 */
object Jsons {
    fun extractJsonBlock(raw: String): String? {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```").trim()
            val e = s.lastIndexOf("```")
            if (e > 0) s = s.substring(0, e).trim()
        }
        val a = s.indexOf('{')
        val b = s.lastIndexOf('}')
        return if (a in 0 until b) s.substring(a, b + 1) else null
    }

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
