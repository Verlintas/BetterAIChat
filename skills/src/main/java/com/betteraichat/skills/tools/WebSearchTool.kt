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
import org.jsoup.nodes.Document
import java.net.URLDecoder

class WebSearchTool : DeviceTool {

    override val name = "web_search"
    override val description = "在互联网上搜索实时信息并返回结果列表（标题、链接、摘要）。适合查询新闻、实时数据、未知知识。使用后如需详细内容可再调用 web_read 读取网页。"
    override val readOnly = true
    override val parameters = schemaOf(
        "query" to stringProp("搜索关键词，尽量具体"),
        "max_results" to intProp("返回结果数量，1-8，默认 5")
    )

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"

    private data class SearchResult(val title: String, val url: String, val snippet: String)

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String =
        withContext(Dispatchers.IO) {
            val query = arguments["query"]?.jsonPrimitive?.content?.trim()
                ?: return@withContext "缺少 query 参数"
            val max = (arguments["max_results"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5).coerceIn(1, 8)
            val results = try {
                searchBing(query)
            } catch (e: Exception) {
                try {
                    searchDuckDuckGo(query)
                } catch (e2: Exception) {
                    return@withContext "搜索失败：${e2.message ?: "网络错误"}（Bing 与 DuckDuckGo 均不可用）"
                }
            }
            if (results.isEmpty()) {
                "未搜索到「$query」的相关结果，可尝试换关键词或稍后重试"
            } else {
                buildString {
                    appendLine("「$query」的搜索结果：")
                    results.take(max).forEachIndexed { i, r ->
                        appendLine("${i + 1}. ${r.title}")
                        appendLine("   ${r.url}")
                        if (r.snippet.isNotBlank()) appendLine("   ${r.snippet.take(200)}")
                    }
                    append("提示：可调用 web_read 读取某条结果的详细内容。")
                }
            }
        }

    private fun searchBing(query: String): List<SearchResult> {
        val doc = Jsoup.connect("https://cn.bing.com/search")
            .data("q", query)
            .data("setlang", "zh-hans")
            .userAgent(userAgent)
            .timeout(15_000)
            .get()
        return doc.select("li.b_algo").mapNotNull { el ->
            val h2 = el.selectFirst("h2") ?: return@mapNotNull null
            val a = el.selectFirst("a:has(h2)")
            val url = a?.attr("href") ?: return@mapNotNull null
            val snippet = el.selectFirst(".b_caption p")?.text()?.trim().orEmpty()
            SearchResult(h2.text().trim(), url, snippet)
        }
    }

    private fun searchDuckDuckGo(query: String): List<SearchResult> {
        val doc = Jsoup.connect("https://html.duckduckgo.com/html/")
            .data("q", query)
            .userAgent(userAgent)
            .timeout(15_000)
            .get()
        return doc.select("div.result").mapNotNull { el ->
            val a = el.selectFirst("a.result__a") ?: return@mapNotNull null
            val url = decodeDdgUrl(a.attr("href"))
            val snippet = el.selectFirst(".result__snippet")?.text()?.trim().orEmpty()
            SearchResult(a.text().trim(), url, snippet)
        }
    }

    private fun decodeDdgUrl(raw: String): String {
        val clean = raw.removePrefix("//")
        val encoded = Regex("uddg=([^&]+)").find(raw)?.groupValues?.get(1) ?: return clean
        return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(clean)
    }
}
