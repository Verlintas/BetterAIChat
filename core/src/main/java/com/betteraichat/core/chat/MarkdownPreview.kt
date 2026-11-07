package com.betteraichat.core.chat

/** Strips common Markdown markers for one-line conversation previews. */
fun markdownPreview(raw: String): String {
    if (raw.isBlank()) return ""
    return raw
        .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("```[\\s\\S]*?```"), "")
        .replace(Regex("[#*_>`~]+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
