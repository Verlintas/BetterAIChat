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

class WebReadTool : DeviceTool {

    override val name = "web_read"
    override val description = "抓取指定网页并提取正文内容。适合读取 web_search 找到的文章、文档、新闻页面。"
    override val readOnly = true
    override val parameters = schemaOf(
        "url" to stringProp("要读取的网页完整 URL（http/https）"),
        "max_chars" to intProp("返回正文的最大字符数，默认 4000，最大 8000")
    )

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String =
        withContext(Dispatchers.IO) {
            val url = arguments["url"]?.jsonPrimitive?.content?.trim()
                ?: return@withContext "缺少 url 参数"
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return@withContext "url 必须是 http/https 开头的完整地址"
            }
            val maxChars = (arguments["max_chars"]?.jsonPrimitive?.content?.toIntOrNull() ?: 4000).coerceIn(500, 8000)
            try {
                val doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .followRedirects(true)
                    .timeout(20_000)
                    .maxBodySize(2 * 1024 * 1024)
                    .get()
                doc.select("script, style, noscript, iframe, nav, footer, header, form, .ad, .ads, .advertisement, .cookie, [aria-hidden=true]").remove()
                val title = doc.title().trim()
                val text = doc.body()?.text()?.trim().orEmpty()
                    .replace(Regex("\\s{2,}"), " ")
                if (text.isEmpty()) {
                    return@withContext "无法从 $url 提取正文（可能是 JS 渲染页面或访问被拒绝）"
                }
                val excerpt = text.take(maxChars) + if (text.length > maxChars) "\n…（内容已截断）" else ""
                "页面标题：$title\n页面地址：$url\n正文：\n$excerpt"
            } catch (e: Exception) {
                "读取失败：${e.message ?: "网络错误"}"
            }
        }
}
