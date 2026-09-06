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

class WebReadTool : DeviceTool {

    override val name = "web_read"
    override val description = "抓取指定网页并提取正文。通常在 web_search 已自动附上正文（read_top≥1）时才需要；" +
        "当 web_search 的正文不足、或需要读取搜索结果列表中靠后的页面时使用。" +
        "url 必须是 web_search 返回的完整链接。读取失败多为反爬或 JS 渲染：不要重试同一个 url，改用另一个来源（如换个域名）或让 web_search 换关键词。"
    override val readOnly = true
    override val parameters = schemaOf(
        "url" to stringProp("要读取的网页完整 URL（http/https）"),
        "max_chars" to intProp("返回正文的最大字符数，默认 3000，最大 6000")
    )

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String =
        withContext(Dispatchers.IO) {
            val url = arguments["url"]?.jsonPrimitive?.content?.trim()
                ?: return@withContext "缺少 url 参数"
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return@withContext "url 必须是 http/https 开头的完整地址（请直接使用 web_search 返回的链接）"
            }
            val maxChars = (arguments["max_chars"]?.jsonPrimitive?.content?.toIntOrNull() ?: 3000).coerceIn(500, 6000)
            try {
                val doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .followRedirects(true)
                    .timeout(20_000)
                    .maxBodySize(2 * 1024 * 1024)
                    .get()
                doc.select("script, style, noscript, iframe, nav, footer, header, form, aside, .ad, .ads, .advertisement, .cookie, [aria-hidden=true]").remove()
                val main = doc.selectFirst("article, main, [role=main]")
                val raw = (main ?: doc.body())?.text()?.trim().orEmpty()
                val text = raw.replace(Regex("\\s{2,}"), " ")
                if (text.isEmpty()) {
                    return@withContext "无法从该页面提取正文（JS 渲染或访问被拒绝）。建议：换一个来源网站重试，或让 web_search 换关键词重新搜索。"
                }
                val title = doc.title().trim()
                val excerpt = text.take(maxChars) + if (text.length > maxChars) "\n…（正文已截断，可调大 max_chars 或分页）" else ""
                "页面标题：$title\n页面地址：$url\n正文：\n$excerpt"
            } catch (e: java.net.SocketTimeoutException) {
                "读取超时：该网站响应过慢。建议不要重试同一 url，改读其他来源（web_search 结果里的其他条目）。"
            } catch (e: Exception) {
                "读取失败：${e.message ?: "网络错误"}。建议换一个来源网站，或让 web_search 换关键词重新搜索。"
            }
        }
}
