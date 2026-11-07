package com.betteraichat.skills

import java.util.Locale

/**
 * Scores how well a visible label matches a user-provided query. Lower is
 * better; null means no reasonable match. Shared by accessibility tools so
 * "OK" prefers an exact button over a container that merely contains it.
 */
object UiTextMatch {

    fun score(label: String, query: String): Int? {
        val labelText = label.trim().lowercase(Locale.ROOT)
        val queryText = query.trim().lowercase(Locale.ROOT)
        if (labelText.isEmpty() || queryText.isEmpty()) return null
        if (labelText == queryText) return 0
        val strip = { value: String -> value.replace(Regex("[\\p{Punct}\\s]+"), "") }
        if (strip(labelText) == strip(queryText)) return 1
        if (labelText.startsWith(queryText)) return 2
        if (labelText.contains(queryText)) return 3
        if (queryText.contains(labelText)) return 4
        if (queryText.length >= 3 && distance(labelText, queryText) <= 2) return 5
        return null
    }

    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
