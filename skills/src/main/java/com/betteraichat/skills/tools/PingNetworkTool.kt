package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

class PingNetworkTool : DeviceTool {

    override val name = "ping_network"
    override val description = "测试网络连通性与延迟：ping 一个主机（默认 8.8.8.8）返回延迟毫秒；也支持测试 HTTP 接口（http/https 地址）返回状态码与耗时。用于诊断网络问题。"
    override val readOnly = true
    override val parameters = schemaOf(
        "target" to stringProp("目标：IP 或域名（如 8.8.8.8、baidu.com）或 http(s) 地址，默认 8.8.8.8"),
        required = emptyList()
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String =
        withContext(Dispatchers.IO) {
            val target = arguments["target"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() } ?: "8.8.8.8"
            if (target.startsWith("http://") || target.startsWith("https://")) {
                runCatching {
                    val start = System.currentTimeMillis()
                    val conn = URL(target).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    conn.instanceFollowRedirects = true
                    try {
                        conn.connect()
                        val code = conn.responseCode
                        val ms = System.currentTimeMillis() - start
                        "HTTP ${code} · 耗时 ${ms}ms · $target"
                    } finally {
                        conn.disconnect()
                    }
                }.getOrElse { e -> "ERROR:无法连接 $target：${e.message}" }
            } else {
                runCatching {
                    val start = System.currentTimeMillis()
                    val addr = InetAddress.getByName(target)
                    val reachable = addr.isReachable(10_000)
                    val ms = System.currentTimeMillis() - start
                    if (reachable) {
                        "网络连通 · $target（${addr.hostAddress}）延迟约 ${ms}ms"
                    } else {
                        "ERROR:$target 不可达（超时）"
                    }
                }.getOrElse { e -> "ERROR:无法解析 $target：${e.message}" }
            }
        }
}
