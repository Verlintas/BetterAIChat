package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URL

class FetchRssTool : DeviceTool {

    override val name = "fetch_rss"
    override val description = "抓取 RSS/Atom 订阅源的最新条目（标题、链接、发布时间、摘要）。url 为订阅源地址，limit 为条数（默认 10，最多 20）。"
    override val readOnly = true
    override val parameters = schemaOf(
        "url" to stringProp("RSS 订阅源地址，如 https://example.com/feed.xml"),
        "limit" to intProp("返回条数，默认 10，最多 20"),
        required = listOf("url")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = arguments["url"]?.jsonPrimitive?.content ?: return@runCatching "url 参数无效"
                val limit = (arguments["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 10).coerceIn(1, 20)
                val doc = Jsoup.connect(url)
                    .timeout(15_000)
                    .userAgent("BetterAIChat/0.19 RSS reader")
                    .get()
                val channel = doc.selectFirst("rss > channel")
                val title = if (channel != null) {
                    channel.selectFirst("title")?.text() ?: "RSS"
                } else {
                    doc.selectFirst("feed > title")?.text() ?: "Atom"
                }
                val items = if (channel != null) {
                    channel.select("item")
                } else {
                    doc.select("feed > entry")
                }
                if (items.isEmpty()) return@runCatching "订阅源「$title」没有条目"
                val sb = StringBuilder()
                sb.appendLine("订阅源：$title（共 ${items.size} 条，展示前 $limit 条）")
                items.take(limit).forEachIndexed { i, it ->
                    val itemTitle = it.selectFirst("title")?.text() ?: "（无标题）"
                    val link = it.selectFirst("link")?.attr("href")
                        ?: it.selectFirst("link")?.text()
                        ?: ""
                    val pubDate = it.selectFirst("pubDate")?.text()
                        ?: it.selectFirst("updated")?.text()
                        ?: ""
                    sb.appendLine("${i + 1}. $itemTitle")
                    if (pubDate.isNotBlank()) sb.appendLine("   时间：$pubDate")
                    if (link.isNotBlank()) sb.appendLine("   链接：$link")
                }
                sb.toString().trim()
            }.getOrElse { e -> "ERROR:抓取失败：${e.message}" }
        }
}
