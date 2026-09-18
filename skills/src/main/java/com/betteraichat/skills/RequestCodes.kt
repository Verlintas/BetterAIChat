package com.betteraichat.skills

import java.util.concurrent.atomic.AtomicInteger

object RequestCodes {
    private val counter = AtomicInteger((System.currentTimeMillis() % 100_000).toInt())

    fun next(): Int = counter.updateAndGet { current ->
        if (current >= Int.MAX_VALUE - 1) 1 else current + 1
    }
}

internal fun isPublicHttpUrl(url: String): Boolean {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return false
    val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return false
    if (host.isBlank()) return false
    if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) return false
    if (host.contains(':')) {
        return !(host == "::1" || host.startsWith("fe80") || host.startsWith("fc") || host.startsWith("fd"))
    }
    val parts = host.split('.')
    if (parts.size != 4 || parts.any { it.toIntOrNull() == null }) return true
    val a = parts[0].toInt()
    val b = parts[1].toInt()
    return !(a == 0 || a == 127 || a == 10 ||
        (a == 192 && b == 168) ||
        (a == 172 && b in 16..31) ||
        (a == 169 && b == 254) ||
        (a == 100 && b in 64..127))
}
