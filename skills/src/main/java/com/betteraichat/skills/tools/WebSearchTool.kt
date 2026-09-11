package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.boolProp
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
    override val description = "在互联网上搜索实时信息（Bing/百度/Brave/DDG/Mojeek/360 六引擎并发合并去重，同域名限 2 条，按关键词相关性排序）。" +
        "用法：query 写具体的关键词短语（如「2025 诺贝尔物理学奖 得主」，而不是「诺贝尔奖」这类宽泛词）；" +
        "需要多角度信息（对比、多主体、多子问题）时，query 可以直接传字符串数组（最多 3 个），一次搜索完成，不要分多次调用。" +
        "默认自动抓取前 1 个结果的正文（read_top 控制 0-3），多数问题一次搜索即可回答。查询结果有 5 分钟缓存；" +
        "追问时效性内容可传 refresh=true 绕过缓存。" +
        "结果为空或无关时：不要盲目用近似词反复重试，而是换表述、加限定词（时间/地区/英文关键词），最多再试 1-2 次。"
    override val readOnly = true
    override val parameters = schemaOf(
        "query" to stringProp("搜索关键词，尽量具体；也可传字符串数组一次多角度搜索（最多 3 个）"),
        "max_results" to intProp("返回结果条数 1-8，默认 5"),
        "read_top" to intProp("自动抓取前 N 条可成功抓取的正文（0-3，默认 1）。0 = 只要链接列表"),
        "refresh" to boolProp("是否强制绕过缓存重新搜索（默认 false）。时效性追问时用 true"),
        required = listOf("query")
    )

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"

    private data class SearchResult(val title: String, val url: String, val snippet: String)

    companion object {
        private const val CACHE_TTL_MS = 300_000L
        private val cache = LinkedHashMap<String, Pair<List<SearchResult>, Long>>(32)
        private val cacheLock = Any()
    }

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String =
        withContext(Dispatchers.IO) {
            val queries: List<String> = when (val el = arguments["query"]) {
                is kotlinx.serialization.json.JsonArray ->
                    el.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                is kotlinx.serialization.json.JsonPrimitive -> listOfNotNull(el.content)
                else -> emptyList()
            }.map { cleanQuery(it) }.filter { it.length >= 2 }.take(3)
            if (queries.isEmpty()) return@withContext "query 无效：请提供至少 2 个字符的具体关键词（或关键词数组）"
            val query = queries.first()
            val max = (arguments["max_results"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5).coerceIn(1, 8)
            val readTop = (arguments["read_top"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1).coerceIn(0, 3)
            val refresh = arguments["refresh"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true
            val cacheKey = "${queries.joinToString("|")}|$max|$readTop"
            val now = System.currentTimeMillis()
            val cached = if (refresh) null else synchronized(cacheLock) {
                cache[cacheKey]?.takeIf { now - it.second < CACHE_TTL_MS }?.first
            }
            val (results, engineNote) = if (cached != null) {
                cached to null
            } else {
                val perQuery = queries.map { q -> searchAllEngines(q) }
                val note = perQuery.mapNotNull { it.second }.firstOrNull()
                val merged = mergeAcrossQueries(queries, perQuery.map { it.first })
                if (merged.isNotEmpty()) {
                    synchronized(cacheLock) {
                        cache[cacheKey] = merged to now
                        if (cache.size > 40) {
                            cache.entries.removeAll { now - it.value.second > CACHE_TTL_MS }
                        }
                    }
                }
                merged to note
            }
            if (results.isEmpty()) {
                return@withContext "搜索没有返回任何结果。${engineNote ?: ""}" +
                    "建议：换个说法重试（更具体的关键词、加时间或地点限定、尝试英文关键词），不要用相近词反复搜索。"
            }
            buildString {
                val label = if (queries.size > 1) queries.joinToString("」「") else query
                appendLine("「$label」的搜索结果（${results.size} 条，多引擎合并）${if (refresh) "（已绕过缓存重新搜索）" else ""}：")
                val shown = results.take(max)
                shown.forEachIndexed { i, r ->
                    appendLine("${i + 1}. ${r.title.take(150)}")
                    appendLine("   链接：${r.url}")
                    if (r.snippet.isNotBlank()) appendLine("   摘要：${r.snippet.take(260)}")
                }
                engineNote?.let { appendLine()
                    append(it) }
                if (readTop > 0) {
                    var collected = 0
                    val perBody = 5000 / readTop
                    val attempts = shown.take(8)
                    val bodies = kotlinx.coroutines.coroutineScope {
                        attempts.map { r ->
                            async {
                                try {
                                    extractBody(r.url, perBody)
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

    private val NOISE_RE = Regex(
        "^(阅读量|收藏|赞|评论|关注|码龄|发布于|更新于|扫码|下载APP|未经.*(转载|许可)|本文.*(转载|来源)|" +
            "版权|Copyright|©|广告|红包|相关推荐|大家都在看|分享到|欢迎.*(关注|订阅)|更多.*(请|欢迎)|" +
            "字数|阅读\\s*\\d+|\\d+\\s*(人|位).*(赞|收藏|在看)|\\d+分钟|\\d+ 分钟前|\\d+小时前|\\d+天前)" +
            ".*"
    )

    private val TRACKING_PARAM_RE = Regex(
        "utm_source|utm_medium|utm_campaign|utm_term|utm_content|spm|from|ref|refer|referrer|source|" +
            "from_source|from_column|fr=|ncid|icid|gclid|fbclid|share_token|share_medium|share_source"
    )

    private fun cleanUrl(raw: String): String {
        var url = raw.trim()
        if (!url.startsWith("http")) return url
        val qIdx = url.indexOf('?')
        if (qIdx < 0) return url
        val base = url.substring(0, qIdx)
        val kept = url.substring(qIdx + 1).split("&")
            .filter { p -> !TRACKING_PARAM_RE.containsMatchIn(p) }
        return if (kept.isEmpty()) base else "$base?${kept.joinToString("&")}"
    }

    private fun extractBody(url: String, budget: Int): String {
        val doc = Jsoup.connect(url)
            .userAgent(userAgent)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .followRedirects(true)
            .timeout(15_000)
            .maxBodySize(3 * 1024 * 1024)
            .get()
        doc.select("script, style, noscript, iframe, nav, footer, header, form, aside, .ad, .ads, .advertisement, .cookie, [aria-hidden=true]").remove()
        val main = doc.selectFirst("article, main, [role=main]") ?: doc.body()
        val out = StringBuilder()
        var len = 0
        var last = ""
        main.select("p, h1, h2, h3, h4, h5, li, pre, blockquote").forEach { el ->
            if (len >= budget) return@forEach
            val t = el.text().trim().replace(Regex("\\s+"), " ")
            if (t.isEmpty() || t.length < 8) return@forEach
            if (NOISE_RE.matches(t)) return@forEach
            if (t == last || last.contains(t) || t.contains(last)) return@forEach
            out.append(t).append('\n')
            len += t.length + 1
            last = t
        }
        var text = out.toString().trim()
        if (text.length < 150) {
            text = main.text().trim().replace(Regex("\\s{2,}"), " ")
        }
        if (text.isEmpty()) return ""
        return text.take(budget) + if (text.length > budget) "\n…（正文较长已截断，可单独 web_read 读全文）" else ""
    }

    private data class EngineOutcome(val results: List<SearchResult>, val ok: Boolean)

    private fun mergeAcrossQueries(queries: List<String>, perQuery: List<List<SearchResult>>): List<SearchResult> {
        val merged = LinkedHashMap<String, SearchResult>()
        val domainCount = HashMap<String, Int>()
        val seenTitles = HashSet<String>()
        perQuery.forEach { list ->
            list.forEach { r ->
                val clean = cleanUrl(r.url)
                val domain = runCatching { java.net.URI(clean).host.orEmpty() }.getOrDefault("")
                val titleKey = r.title.lowercase().replace(Regex("[\\s\\p{Punct}]+"), "")
                if (domainCount[domain] ?: 0 >= 2) return@forEach
                if (titleKey.isNotBlank() && !seenTitles.add(titleKey)) return@forEach
                if (merged.putIfAbsent(clean, r.copy(url = clean)) == null) {
                    domainCount[domain] = (domainCount[domain] ?: 0) + 1
                }
            }
        }
        val terms = queries.flatMap { q ->
            q.lowercase().split(Regex("[\\s,，。、+]+")).filter { it.length >= 2 }
        }.distinct().ifEmpty { queries.map { it.lowercase() } }
        return merged.values.sortedByDescending { r ->
            val title = r.title.lowercase()
            val snippet = r.snippet.lowercase()
            terms.sumOf { t -> (if (title.contains(t)) 3 else 0) + (if (snippet.contains(t)) 1 else 0) }
        }
    }

    private suspend fun searchAllEngines(query: String): Pair<List<SearchResult>, String?> {
        val engines = listOf(
            ::searchBing,
            ::searchBaidu,
            ::searchBrave,
            ::searchDuckDuckGo,
            ::searchMojeek,
            ::search360
        )
        val outcomes = kotlinx.coroutines.coroutineScope {
            engines.map { engine ->
                async {
                    try {
                        EngineOutcome(engine(query), true)
                    } catch (e: Exception) {
                        EngineOutcome(emptyList(), false)
                    }
                }
            }.map { it.await() }
        }
        val merged = LinkedHashMap<String, SearchResult>()
        val domainCount = HashMap<String, Int>()
        outcomes.forEach { o ->
            if (merged.size >= 10) return@forEach
            o.results.forEach { r ->
                if (r.url.startsWith("http") && r.title.isNotBlank()) {
                    val clean = cleanUrl(r.url)
                    val domain = runCatching { java.net.URI(clean).host.orEmpty() }.getOrDefault("")
                    if (domainCount[domain] ?: 0 >= 2) return@forEach
                    if (merged.putIfAbsent(clean, r.copy(url = clean)) == null) {
                        domainCount[domain] = (domainCount[domain] ?: 0) + 1
                    }
                }
            }
        }
        val okCount = outcomes.count { it.ok }
        val note = if (okCount == 0) {
            "（六个搜索引擎全部不可用：网络受限或被限流，请检查网络后稍等再试）"
        } else if (okCount < 3) {
            "（提示：本次仅 $okCount/6 个搜索引擎可用，结果可能不完整，可稍后 refresh=true 重试）"
        } else null
        return merged.values.toList() to note
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

    private fun search360(query: String): List<SearchResult> {
        val doc = Jsoup.connect("https://www.so.com/s")
            .data("q", query)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .userAgent(userAgent)
            .timeout(8_000)
            .get()
        return doc.select("li.res-list, li.result").mapNotNull { el: org.jsoup.nodes.Element ->
            val a = el.selectFirst("h3 a[href]") ?: return@mapNotNull null
            var url = a.attr("href")
            if (!url.startsWith("http")) {
                url = runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)
            }
            if (!url.startsWith("http")) return@mapNotNull null
            val snippet = el.selectFirst(".res-desc, .res-rich, [class*=desc]")?.text()?.trim().orEmpty()
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
