package com.betteraichat.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

object MiniHighlighter {

    private val KEYWORDS = setOf(
        "fun", "val", "var", "if", "else", "when", "for", "while", "return", "class",
        "object", "interface", "data", "import", "package", "private", "public", "protected",
        "internal", "override", "suspend", "await", "async", "lambda", "null", "true", "false",
        "this", "super", "new", "def", "function", "const", "let", "var", "function", "async",
        "await", "yield", "from", "import", "as", "in", "is", "not", "and", "or", "static",
        "void", "int", "float", "double", "long", "char", "boolean", "string", "String", "let",
        "var", "func", "struct", "enum", "impl", "pub", "fn", "match", "use", "mod", "type"
    )

    private val KEYWORD_COLOR = Color(0xFF569CD6)
    private val STRING_COLOR = Color(0xFFCE9178)
    private val COMMENT_COLOR = Color(0xFF6A9955)
    private val NUMBER_COLOR = Color(0xFFB5CEA8)
    private val DEFAULT_COLOR = Color(0xFFD4D4D4)

    fun highlight(code: String): AnnotatedString {
        val builder = AnnotatedString.Builder()
        var i = 0
        val n = code.length
        while (i < n) {
            val c = code[i]
            when {
                // 行注释
                c == '#' || (c == '/' && i + 1 < n && code[i + 1] == '/') || (c == '-' && i + 1 < n && code[i + 1] == '-') -> {
                    val start = i
                    while (i < n && code[i] != '\n') i++
                    builder.pushStyle(SpanStyle(color = COMMENT_COLOR))
                    builder.append(code.substring(start, i))
                    builder.pop()
                }
                // 块注释 /* */
                c == '/' && i + 1 < n && code[i + 1] == '*' -> {
                    val start = i
                    i += 2
                    while (i + 1 < n && !(code[i] == '*' && code[i + 1] == '/')) i++
                    i = (i + 2).coerceAtMost(n)
                    builder.pushStyle(SpanStyle(color = COMMENT_COLOR))
                    builder.append(code.substring(start, i))
                    builder.pop()
                }
                // 字符串
                c == '"' || c == '\'' || c == '`' -> {
                    val quote = c
                    val start = i
                    i++
                    while (i < n && code[i] != quote) {
                        if (code[i] == '\\') i++
                        i++
                    }
                    i = (i + 1).coerceAtMost(n)
                    builder.pushStyle(SpanStyle(color = STRING_COLOR))
                    builder.append(code.substring(start, i))
                    builder.pop()
                }
                // 数字
                c.isDigit() -> {
                    val start = i
                    while (i < n && (code[i].isDigit() || code[i] == '.' || code[i] == 'x' ||
                            code[i] in 'a'..'f' || code[i] in 'A'..'F')) i++
                    builder.pushStyle(SpanStyle(color = NUMBER_COLOR))
                    builder.append(code.substring(start, i))
                    builder.pop()
                }
                // 标识符（关键词检查）
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < n && (code[i].isLetterOrDigit() || code[i] == '_')) i++
                    val word = code.substring(start, i)
                    if (word in KEYWORDS) {
                        builder.pushStyle(SpanStyle(color = KEYWORD_COLOR, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                builder.append(word)
                builder.pop()
                    } else {
                        builder.pushStyle(SpanStyle(color = DEFAULT_COLOR))
                builder.append(word)
                builder.pop()
                    }
                }
                else -> {
                    builder.pushStyle(SpanStyle(color = DEFAULT_COLOR))
                    builder.append(c.toString())
                    builder.pop()
                    i++
                }
            }
        }
        return builder.toAnnotatedString()
    }
}
