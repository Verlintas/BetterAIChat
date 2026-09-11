package com.betteraichat.core.chat

object MarkdownNormalizer {

    private val TRIPLE_STAR = Regex("""\*\*\*([^*]+)\*\*\*""")
    private val UNDERSCORE_BOLD = Regex("""(?<![\w])__([^_\n]+?)__(?![\w])""")
    private val INTRAWORD_BOLD = Regex("""(?<=[\p{L}\p{N}])\*\*([^*\n]+?)\*\*(?=[\p{L}\p{N}])""")
    private val INTRAWORD_ITALIC = Regex("""(?<=[\p{L}\p{N}])(?<!\*)\*([^*\n]+?)\*(?!\*)(?=[\p{L}\p{N}])""")
    private const val HAIR_SPACE = "\u200A"

    fun normalize(content: String): String {
        val tableFixed = content.lines().map { line ->
            if (line.contains('｜') && line.count { it == '｜' } >= 2) {
                line.replace('｜', '|')
            } else line
        }.joinToString("\n")
        val converted = tableFixed
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
        val rawRows = mutableListOf<List<String>>()
        var separator: List<String>? = null
        md.lines().forEach { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("|")) return@forEach
            val cells = splitRow(trimmed)
            if (cells.isEmpty()) return@forEach
            if (cells.all { TABLE_SEPARATOR_REGEX.matches(it) }) {
                separator = cells
                return@forEach
            }
            rawRows.add(cells)
        }
        if (rawRows.isEmpty()) return null
        val columnCount = rawRows.maxOf { it.size }
        val headers = rawRows.first().padded(columnCount)
        val rows = rawRows.drop(1).map { it.padded(columnCount) }
        val alignments = (0 until columnCount).map { i ->
            val sep = separator?.getOrNull(i) ?: ""
            val left = sep.startsWith(":")
            val right = sep.endsWith(":")
            when {
                left && right -> TableAlign.CENTER
                right -> TableAlign.END
                else -> TableAlign.START
            }
        }
        return TableData(headers, rows, alignments)
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
                    current.append(ch)
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
