package com.lovebrain.app.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lovebrain.app.ui.theme.AppDimens
import com.lovebrain.app.ui.theme.MarkdownBodyFontSize
import com.lovebrain.app.ui.theme.MarkdownBodyLineHeight
import com.lovebrain.app.ui.theme.LoveBrainShape
import com.lovebrain.app.ui.theme.Spacing
import com.lovebrain.app.ui.theme.TextHint
import com.lovebrain.app.ui.theme.TextSecondary

/** ：4 个解析 Regex 上提为文件级常量（模式串逐字节不变；避免流式热路径每次重组现编译） */
private val RE_HTML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
private val RE_HR = Regex("^[-*]{2,}$")
private val RE_ORDERED_LIST = Regex("^\\d+[.、)]\\s.*")
private val RE_TABLE_SEPARATOR = Regex("^[-: ]+$")
/** Markdown 排版常量（ 外放：值不变，仅外放命名） */
private object MarkdownTypeDimens {
    val H1_FONT_SIZE = 15.sp            // 一级标题字号
    val H2_FONT_SIZE = 14.sp            // 二级标题字号
    const val H2_MARGIN_TOP_DP = 6      // 二级标题上边距
    const val H2_MARGIN_BOTTOM_DP = 3   // 二级标题下边距
    val TABLE_LINE_HEIGHT = 18.sp       // 表格行高
}

/** Markdown 布局常量（  令牌化：数值不变，仅外放命名） */
private object MarkdownDimens {
    const val BLOCK_GAP_DP = 6               // 分割线/空行块间距
    const val QUOTE_RADIUS_DP = 4            // 引用块圆角
    const val QUOTE_PAD_START_DP = 10        // 引用块左内边距
    const val QUOTE_PAD_VERTICAL_DP = 6      // 引用块上下内边距
    const val LIST_ROW_VPAD_DP = 1           // 列表行垂直内边距
    const val LIST_MARK_GAP_DP = 6           // 列表标记与文本间距
    const val TABLE_ROW_VPAD_DP = 3          // 表格行垂直内边距
    const val TABLE_DIVIDER_THICKNESS_DP = 0.5f // 表头分隔线厚度
}

/**
 * Markdown 渲染组件：支持 #/##/### 标题、---/-- 分割线、- 无序列表、
 * 1. 有序列表、> 引用、**粗体**、*斜体*、~~删除线~~、`行内代码`。
 * 逐行解析 block 级元素，行内解析 inline 元素。
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color,
    // ：默认值接排版令牌（21→22 = MarkdownBodyLineHeight；调用方全显式传参，零影响）
    fontSize: TextUnit = MarkdownBodyFontSize,
    lineHeight: TextUnit = MarkdownBodyLineHeight,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    // 列表行默认单行截断（方案卡等紧凑场景）；
    // 知识库预览传 false → 列表完整换行显示，不再 ...截断
    listSingleLine: Boolean = true
) {
    // 去除 HTML 注释（<!-- ... -->），避免预览时显示为乱码
    val cleanText = text.replace(RE_HTML_COMMENT, "").trim()
    val lines = cleanText.lines()
    Column(modifier = modifier) {
        var i = 0
        var renderedLines = 0
        var truncated = false
        while (i < lines.size && renderedLines < maxLines) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                // --- 或 *** 或 -- 分割线
                trimmed.matches(RE_HR) -> {
                    Spacer(Modifier.height(MarkdownDimens.BLOCK_GAP_DP.dp))
                    HorizontalDivider(
                        thickness = AppDimens.BORDER_WIDTH_DP.dp,
                        color = TextHint.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(MarkdownDimens.BLOCK_GAP_DP.dp))
                    renderedLines++
                }

                // 标题 #{1,6}（容错：# 后无空格也能渲染）
                trimmed.length > 1 && trimmed.startsWith("#") -> {
                    val level = trimmed.takeWhile { it == '#' }.length
                    if (level in 1..6) {
                        val content = parseInline(trimmed.drop(level).trim())
                        val (size, top, bottom) = when (level.coerceAtMost(3)) {
                            1 -> Triple(MarkdownTypeDimens.H1_FONT_SIZE, Spacing.md, Spacing.sm)
                            2 -> Triple(MarkdownTypeDimens.H2_FONT_SIZE, MarkdownTypeDimens.H2_MARGIN_TOP_DP.dp, MarkdownTypeDimens.H2_MARGIN_BOTTOM_DP.dp)
                            else -> Triple(fontSize, Spacing.sm, Spacing.xs)
                        }
                        Spacer(Modifier.height(top))
                        Text(
                            text = content,
                            color = color,
                            fontSize = size,
                            fontWeight = FontWeight.Bold,
                            lineHeight = lineHeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(bottom))
                    } else {
                        Text(
                            text = parseInline(trimmed),
                            color = color,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    renderedLines++
                }

                // > 引用
                trimmed.startsWith("> ") || trimmed == ">" -> {
                    val content = trimmed.removePrefix("> ").removePrefix(">")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs)
                            .background(
                                TextHint.copy(alpha = 0.1f),
                                RoundedCornerShape(MarkdownDimens.QUOTE_RADIUS_DP.dp)
                            )
                            .padding(start = MarkdownDimens.QUOTE_PAD_START_DP.dp, end = Spacing.md, top = MarkdownDimens.QUOTE_PAD_VERTICAL_DP.dp, bottom = MarkdownDimens.QUOTE_PAD_VERTICAL_DP.dp)
                    ) {
                        Text(
                            text = parseInline(content),
                            color = TextSecondary,
                            fontSize = fontSize,
                            fontStyle = FontStyle.Italic,
                            lineHeight = lineHeight
                        )
                    }
                    renderedLines++
                }

                // - 或 * 无序列表（A2-11：与有序列表共用 ListRow）
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val content = trimmed.removePrefix("- ").removePrefix("* ")
                    ListRow("•", content, color, fontSize, lineHeight, listSingleLine)
                    renderedLines++
                }

                // 1. 有序列表（A2-11：与无序列表共用 ListRow，标记加粗为原行为）
                trimmed.matches(RE_ORDERED_LIST) -> {
                    val num = trimmed.takeWhile { it.isDigit() || it == '.' || it == '、' || it == ')' }
                    val content = trimmed.removePrefix(num).trim()
                    ListRow(num, content, color, fontSize, lineHeight, listSingleLine, FontWeight.Medium)
                    renderedLines++
                }

                // 空行
                trimmed.isEmpty() -> {
                    Spacer(Modifier.height(MarkdownDimens.BLOCK_GAP_DP.dp))
                }

                // 表格（| 开头的连续行）
                trimmed.startsWith("|") -> {
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        tableLines.add(lines[i].trim())
                        i++
                    }
                    i-- // 回退一步，外层 while 会 i++
                    if (renderedLines + tableLines.size > maxLines) {
                        truncated = true
                        i = lines.size
                    } else {
                        RenderTable(tableLines, color, fontSize)
                        renderedLines += tableLines.size
                    }
                }

                // 普通段落（默认不截断——谈心分析需完整显示；
                // 传入有限 maxLines 时按视觉行数截断，解决单段长文本只计 1 源行导致的挤压问题）
                else -> {
                    Text(
                        text = parseInline(trimmed),
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        maxLines = maxLines,
                        overflow = if (maxLines != Int.MAX_VALUE)
                            TextOverflow.Ellipsis
                        else
                            TextOverflow.Clip
                    )
                    renderedLines++
                }
            }
            i++
        }
        if (renderedLines >= maxLines && i < lines.size) {
            truncated = true
        }
        if (truncated) {
            Text(
                text = "…（内容较长已省略）",
                color = TextHint,
                fontSize = (fontSize.value - 2).sp,
                lineHeight = lineHeight
            )
        }
    }
}

/** A2-11：列表行（无序/有序共用）——标记 + 内容两段原逐字相同，抽出消重 */
@Composable
private fun ListRow(
    mark: String,
    content: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    listSingleLine: Boolean,
    markWeight: FontWeight? = null
) {
    Row(modifier = Modifier.padding(vertical = MarkdownDimens.LIST_ROW_VPAD_DP.dp)) {
        Text(
            text = mark,
            color = color,
            fontSize = fontSize,
            fontWeight = markWeight,
            modifier = Modifier.padding(end = MarkdownDimens.LIST_MARK_GAP_DP.dp)
        )
        Text(
            text = parseInline(content),
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            maxLines = if (listSingleLine) 1 else Int.MAX_VALUE,
            overflow = if (listSingleLine)
                TextOverflow.Ellipsis
            else
                TextOverflow.Clip,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 渲染 Markdown 表格（简易版：等宽列） */
@Composable
private fun RenderTable(tableLines: List<String>, color: Color, fontSize: TextUnit) {
    // 解析行 → 单元格列表，跳过分隔行（只含 - | : 空格）
    val rows = tableLines.mapNotNull { line ->
        val cells = line.trim().removePrefix("|").removeSuffix("|")
            .split("|").map { it.trim() }
        // 分隔行：所有单元格只含 -/:/空格
        if (cells.all { it.matches(RE_TABLE_SEPARATOR) }) null
        else cells
    }
    if (rows.isEmpty()) return

    val colCount = rows.maxOf { it.size }
    Spacer(Modifier.height(Spacing.sm))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TextHint.copy(alpha = 0.06f), LoveBrainShape.sm)
            .padding(Spacing.md)
    ) {
        rows.forEachIndexed { idx, cells ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MarkdownDimens.TABLE_ROW_VPAD_DP.dp)
            ) {
                for (c in 0 until colCount) {
                    val cellText = cells.getOrElse(c) { "" }
                    Text(
                        text = parseInline(cellText),
                        color = color,
                        fontSize = (fontSize.value - 1).sp,
                        fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Normal,
                        lineHeight = MarkdownTypeDimens.TABLE_LINE_HEIGHT,
                        modifier = Modifier.weight(1f).padding(end = Spacing.sm)
                    )
                }
            }
            // 表头下加分隔线
            if (idx == 0) {
                HorizontalDivider(
                    thickness = MarkdownDimens.TABLE_DIVIDER_THICKNESS_DP.dp,
                    color = TextHint.copy(alpha = 0.4f)
                )
            }
        }
    }
    Spacer(Modifier.height(Spacing.sm))
}

/** 解析行内 **bold**、*italic*、~~strikethrough~~、`code` 标记 */
private fun parseInline(raw: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = raw.length
        while (i < len) {
            when {
                // **bold**
                i + 1 < len && raw[i] == '*' && raw[i + 1] == '*' -> {
                    val end = raw.indexOf("**", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(raw.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        // 不成对：原文显示，避免裸 *
                        append("**")
                        i += 2
                    }
                }
                // ~~strikethrough~~
                i + 1 < len && raw[i] == '~' && raw[i + 1] == '~' -> {
                    val end = raw.indexOf("~~", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(raw.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append("~~")
                        i += 2
                    }
                }
                // `code`
                raw[i] == '`' -> {
                    val end = raw.indexOf('`', i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = TextHint.copy(alpha = 0.15f)
                        )) {
                            append(" " + raw.substring(i + 1, end) + " ")
                        }
                        i = end + 1
                    } else {
                        append(raw[i])
                        i++
                    }
                }
                // *italic*
                raw[i] == '*' -> {
                    val end = raw.indexOf('*', i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(raw.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(raw[i])
                        i++
                    }
                }
                else -> {
                    append(raw[i])
                    i++
                }
            }
        }
    }
}
