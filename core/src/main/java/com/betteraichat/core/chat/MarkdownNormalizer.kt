package com.betteraichat.core.chat

object MarkdownNormalizer {

    private val TRIPLE_STAR = Regex("""\*\*\*([^*]+)\*\*\*""")
    private val UNDERSCORE_BOLD = Regex("""(?<![\w])__([^_\n]+?)__(?![\w])""")
    private val INTRAWORD_BOLD = Regex("""(?<=[\p{L}\p{N}])\*\*([^*\n]+?)\*\*(?=[\p{L}\p{N}])""")
    private val INTRAWORD_ITALIC = Regex("""(?<=[\p{L}\p{N}])(?<!\*)\*([^*\n]+?)\*(?!\*)(?=[\p{L}\p{N}])""")
    private const val HAIR_SPACE = "\u200A"

    private val HEADING_NO_SPACE = Regex("^(#{1,6})([^\\s#])", RegexOption.MULTILINE)
    private val DASH_LIST_NO_SPACE = Regex("^([-+])([^\\s\\-+])", RegexOption.MULTILINE)
    private val ORDERED_LIST_NO_SPACE = Regex("^(\\d{1,3}\\.)([^\\s\\d])", RegexOption.MULTILINE)
    private val QUOTE_NO_SPACE = Regex("^>(?![\\s>])", RegexOption.MULTILINE)

    fun normalize(content: String): String {
        val tableFixed = content.lines().map { line ->
            if (line.contains('｜') && line.count { it == '｜' } >= 2) {
                line.replace('｜', '|')
            } else line
        }.joinToString("\n")
        val starsFixed = tableFixed
            .replace('＊', '*')
        val spacingFixed = starsFixed
            .replace(HEADING_NO_SPACE, "$1 $2")
            .replace(DASH_LIST_NO_SPACE, "$1 $2")
            .replace(ORDERED_LIST_NO_SPACE, "$1 $2")
            .replace(QUOTE_NO_SPACE, "> ")
        val converted = spacingFixed
            .replace(TRIPLE_STAR, "**$1**")
            .replace(UNDERSCORE_BOLD, "**$1**")
        return fixIntrawordEmphasis(converted)
    }

    private fun fixIntrawordEmphasis(text: String): String {
        var out = text
        if (INTRAWORD_BOLD.containsMatchIn(out)) {
            out = INTRAWORD_BOLD.replace(out) { m ->
                "$HAIR_SPACE**${m.groupValues[1]}**$HAIR_SPACE"
            }
        }
        if (INTRAWORD_ITALIC.containsMatchIn(out)) {
            out = INTRAWORD_ITALIC.replace(out) { m ->
                "$HAIR_SPACE*${m.groupValues[1]}*$HAIR_SPACE"
            }
        }
        return out
    }

    enum class TableAlign { START, CENTER, END }

    data class TableData(
        val headers: List<String>,
        val rows: List<List<String>>,
        val alignments: List<TableAlign>
    )

    private val TABLE_SEPARATOR_REGEX = Regex("^\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*$")

    fun parseTable(md: String): TableData? {
        val lines = md.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return null
        var sepIdx = -1
        for (i in 1 until lines.size) {
            if (!lines[i].contains('|')) continue
            val cells = splitRow(lines[i])
            if (cells.isNotEmpty() && cells.all { TABLE_SEPARATOR_REGEX.matches(it) }) {
                sepIdx = i
                break
            }
        }
        if (sepIdx <= 0) return null
        val separator = splitRow(lines[sepIdx])
        val headers = splitRow(lines[sepIdx - 1])
        if (headers.isEmpty()) return null
        val rows = lines.drop(sepIdx + 1)
            .takeWhile { it.contains('|') }
            .map { splitRow(it) }
        val columnCount = (headers.size).coerceAtLeast(separator.size)
        val alignedHeaders = headers.padded(columnCount)
        val alignedRows = rows.map { it.padded(columnCount) }
        val alignments = (0 until columnCount).map { i ->
            val sep = separator.getOrNull(i) ?: ""
            val left = sep.startsWith(":")
            val right = sep.endsWith(":")
            when {
                left && right -> TableAlign.CENTER
                right -> TableAlign.END
                else -> TableAlign.START
            }
        }
        return TableData(alignedHeaders, alignedRows, alignments)
    }

    private fun splitRow(line: String): List<String> {
        val trimmed = line.trim()
        val inner = if (trimmed.startsWith("|")) trimmed.drop(1) else trimmed
        val body = if (inner.endsWith("|")) inner.dropLast(1) else inner
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        body.forEach { ch ->
            when {
                escaped -> {
                    if (ch == '|' || ch == '\\') {
                        current.append(ch)
                    } else {
                        current.append('\\')
                        current.append(ch)
                    }
                    escaped = false
                }
                ch == '\\' -> escaped = true
                ch == '|' -> {
                    cells.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        cells.add(current.toString().trim())
        return cells
    }

    private fun List<String>.padded(count: Int): List<String> =
        if (size >= count) this else this + List(count - size) { "" }

    val CODE_BLOCK_STRIP_REGEX = Regex("```[^`\\n]*\\n[\\s\\S]*?```")

    fun stripCodeBlocks(content: String): String =
        CODE_BLOCK_STRIP_REGEX.replace(content, "")

    val CODE_BLOCK_REGEX = Regex("```[^`\\n]*\\n([\\s\\S]*?)```")

    fun extractCodeBlocks(content: String): List<String> =
        CODE_BLOCK_REGEX.findAll(content).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()

    val LINK_REGEX = Regex("\\[([^\\]]*)\\]\\(((?:https?|ftp)://[^\\s)]+)\\)")

    fun extractLinks(content: String): List<String> =
        LINK_REGEX.findAll(content).map { it.groupValues[2].trimEnd(')') }.distinct().toList()
}
