package com.betteraichat.core.chat

object MarkdownNormalizer {

    fun normalize(content: String): String {
        val tableFixed = content.lines().map { line ->
            if (line.contains('｜') && line.count { it == '｜' } >= 2) {
                line.replace('｜', '|')
            } else line
        }.joinToString("\n")
        return tableFixed
            .replace(Regex("""\*\*\*([^*]+)\*\*\*"""), "**$1**")
            .replace(Regex("""(?<![\w])__([^_\n]+?)__(?![\w])"""), "**$1**")
    }

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
