package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder

class WebSearchTool : DeviceTool {

    override val name = "web_search"
    override val description = "在互联网上搜索实时信息（Bing/百度/Brave/DDG/Mojeek 五引擎并发合并去重）。" +
        "用法：query 写具体的关键词短语（如「2025 诺贝尔物理学奖 得主」，而不是「诺贝尔奖」这类宽泛词）；" +
        "默认会自动抓取前 1 个结果的正文（read_top 控制 0-3），多数问题一次搜索即可回答，无需再调 web_read。" +
        "如果结果为空或无关：不要盲目用近似词反复重试，而是换表述、加限定词（时间/地区/英文关键词），最多再试 1-2 次。"
    override val readOnly = true
    override val parameters = schemaOf(
        "query" to stringProp("搜索关键词，尽量具体（可含时间、地点等限定）"),
        "max_results" to intProp("返回结果条数 1-8，默认 5"),
        "read_top" to intProp("自动抓取前 N 条结果的正文（0-3，默认 1）。0 = 只要链接列表；2-3 = 需要多角度信息时"),
        required = listOf("query")
    )

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"

    private data class SearchResult(val title: String, val url: String, val snippet: String)

    companion object {
        private val cache = LinkedHashMap<String, Pair<List<SearchResult>, Long>>(32)
        private val cacheLock = Any()
    }

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String =
        withContext(Dispatchers.IO) {
            val rawQuery = arguments["query"]?.jsonPrimitive?.content?.trim()
                ?: return@withContext "缺少 query 参数：请提供要搜索的具体关键词"
            val query = cleanQuery(rawQuery)
            if (query.length < 2) return@withContext "query 无效：请提供至少 2 个字符的具体关键词"
            val max = (arguments["max_results"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5).coerceIn(1, 8)
            val readTop = (arguments["read_top"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1).coerceIn(0, 3)
            val now = System.currentTimeMillis()
            val cached = synchronized(cacheLock) {
                cache[query]?.takeIf { now - it.second < 300_000 }?.first
            }
            val results = cached ?: searchAllEngines(query)
            if (cached == null && results.isNotEmpty()) {
                synchronized(cacheLock) {
                    cache[query] = results to now
                    if (cache.size > 30) {
                        cache.entries.removeAll { now - it.value.second > 300_000 }
                    }
                }
            }
            if (results.isEmpty()) {
                return@withContext "搜索没有返回任何结果。建议：换个说法重试（更具体的关键词、加时间或地点限定、尝试英文关键词），不要用相近词反复搜索。"
            }
            buildString {
                appendLine("「$query」的搜索结果（${results.size} 条，五引擎合并）：")
                val shown = results.take(max)
                shown.forEachIndexed { i, r ->
                    appendLine("${i + 1}. ${r.title.take(150)}")
                    appendLine("   链接：${r.url}")
                    if (r.snippet.isNotBlank()) appendLine("   摘要：${r.snippet.take(260)}")
                }
                if (readTop > 0) {
                    var collected = 0
                    val attempts = shown.take(6)
                    val bodies = kotlinx.coroutines.coroutineScope {
                        attempts.map { r ->
                            async {
                                try {
                                    extractBody(r.url)
                                } catch (e: Exception) {
                                    ""
                                }
                            }
                        }.map { it.await() }
                    }
                    attempts.forEachIndexed { i, r ->
                        if (collected >= readTop) return@forEachIndexed
                        val body = bodies.getOrNull(i).orEmpty()
                        if (body.isNotBlank()) {
                            appendLine()
                            appendLine("【第 ${i + 1} 条正文（${r.title.take(60)}）】")
                            append(body)
                            collected++
                        }
                    }
                    if (collected == 0) {
                        appendLine()
                        append("（正文抓取失败：目标网站反爬或 JS 渲染。以上摘要通常已足够，必要时换关键词重新搜索）")
                    }
                }
            }
        }

    private fun cleanQuery(raw: String): String {
        var q = raw
        if (q.length >= 2 && q.first() == '"' && q.last() == '"') q = q.drop(1).dropLast(1)
        return q.trim().take(120)
    }

    private fun extractBody(url: String): String {
        val doc = Jsoup.connect(url)
            .userAgent(userAgent)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .followRedirects(true)
            .timeout(15_000)
            .maxBodySize(2 * 1024 * 1024)
            .get()
        doc.select("script, style, noscript, iframe, nav, footer, header, form, aside, .ad, .ads, .advertisement, .cookie, [aria-hidden=true]").remove()
        val main = doc.selectFirst("article, main, [role=main]")
        val text = (main ?: doc.body()).text().trim().replace(Regex("\\s{2,}"), " ")
        if (text.isEmpty()) return ""
        return text.take(550) + if (text.length > 550) "…" else ""
    }

    private suspend fun searchAllEngines(query: String): List<SearchResult> {
        val engines = listOf(
            ::searchBing,
            ::searchBaidu,
            ::searchBrave,
            ::searchDuckDuckGo,
            ::searchMojeek
        )
        val engineResults = kotlinx.coroutines.coroutineScope {
            engines.map { engine ->
                async {
                    try {
                        engine(query)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }.map { it.await() }
        }
        val merged = LinkedHashMap<String, SearchResult>()
        engineResults.forEach { list ->
            if (merged.size >= 6) return@forEach
            list.forEach { r ->
                if (r.url.startsWith("http") && r.title.isNotBlank()) {
                    merged.putIfAbsent(r.url, r)
                }
            }
        }
        return merged.values.toList()
    }

    private fun fetch(url: String, query: String): Document =
        Jsoup.connect(url)
            .data("q", query)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", "https://www.google.com/")
            .userAgent(userAgent)
            .timeout(8_000)
            .followRedirects(true)
            .get()

    private fun searchBing(query: String): List<SearchResult> {
        val doc = fetch("https://cn.bing.com/search", query)
        return doc.select("li.b_algo").mapNotNull { el ->
            val h2 = el.selectFirst("h2") ?: return@mapNotNull null
            val a = el.selectFirst("h2 a[href]") ?: el.selectFirst("a:has(h2)")
            var url = a?.attr("href") ?: return@mapNotNull null
            url = decodeBingUrl(url)
            if (!url.startsWith("http")) return@mapNotNull null
            val snippet = el.selectFirst(".b_caption p")?.text()?.trim().orEmpty()
            SearchResult(h2.text().trim(), url, snippet)
        }
    }

    private fun searchBaidu(query: String): List<SearchResult> {
        val doc = Jsoup.connect("https://www.baidu.com/s")
            .data("wd", query)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .userAgent(userAgent)
            .timeout(8_000)
            .get()
        return doc.select("div.result").mapNotNull { el ->
            val h3 = el.selectFirst("h3") ?: return@mapNotNull null
            val a = h3.selectFirst("a[href]") ?: return@mapNotNull null
            val url = runCatching { URLDecoder.decode(a.attr("href"), "UTF-8") }.getOrDefault(a.attr("href"))
            if (!url.startsWith("http")) return@mapNotNull null
            val snippet = el.selectFirst(".c-span-last span, [class*=content-right] span")?.text()?.trim().orEmpty()
            SearchResult(h3.text().trim(), url, snippet)
        }
    }

    private fun searchBrave(query: String): List<SearchResult> {
        val doc = Jsoup.connect("https://search.brave.com/search")
            .data("q", query)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .userAgent(userAgent)
            .timeout(8_000)
            .get()
        return doc.select("div.snippet").mapNotNull { el ->
            val a = el.selectFirst("a[href]") ?: return@mapNotNull null
            val url = a.attr("href")
            if (!url.startsWith("http")) return@mapNotNull null
            val title = el.selectFirst(".snippet-title, .title")?.text()?.trim()
                ?: a.text().trim()
            val snippet = el.selectFirst(".snippet-description")?.text()?.trim().orEmpty()
            SearchResult(title, url, snippet)
        }
    }

    private fun searchDuckDuckGo(query: String): List<SearchResult> {
        val doc = Jsoup.connect("https://html.duckduckgo.com/html/")
            .data("q", query)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .userAgent(userAgent)
            .timeout(8_000)
            .get()
        return doc.select("div.result").mapNotNull { el ->
            val a = el.selectFirst("a.result__a") ?: return@mapNotNull null
            val url = decodeDdgUrl(a.attr("href"))
            if (!url.startsWith("http")) return@mapNotNull null
            val snippet = el.selectFirst(".result__snippet")?.text()?.trim().orEmpty()
            SearchResult(a.text().trim(), url, snippet)
        }
    }

    private fun searchMojeek(query: String): List<SearchResult> {
        val doc = Jsoup.connect("https://www.mojeek.com/search")
            .data("q", query)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .userAgent(userAgent)
            .timeout(8_000)
            .get()
        return doc.select("ul.results-standard li, .result").mapNotNull { el: org.jsoup.nodes.Element ->
            val a = el.selectFirst("a.title, h2 a") ?: return@mapNotNull null
            val url = a.attr("href")
            if (!url.startsWith("http")) return@mapNotNull null
            val snippet = el.selectFirst("p.s, .s")?.text()?.trim().orEmpty()
            SearchResult(a.text().trim(), url, snippet)
        }
    }

    private fun decodeBingUrl(raw: String): String {
        if (!raw.contains("/ck/a")) return raw
        val encoded = Regex("u=a1([0-9A-Za-z+/=]+)").find(raw)?.groupValues?.get(1) ?: return raw
        return runCatching {
            val decoded = String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)
            decoded.takeIf { it.startsWith("http") } ?: raw
        }.getOrDefault(raw)
    }

    private fun decodeDdgUrl(raw: String): String {
        val clean = raw.removePrefix("//")
        val encoded = Regex("uddg=([^&]+)").find(raw)?.groupValues?.get(1) ?: return clean
        return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(clean)
    }
}
