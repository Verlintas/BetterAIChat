package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GetWeatherTool : DeviceTool {

    override val name = "get_weather"
    override val description = "查询天气：城市名或拼音（如 北京、Shanghai），返回当前天气、温度、湿度、风速和未来 3 天预报。免费数据源，无需 API Key。"
    override val readOnly = true
    override val parameters = schemaOf(
        "city" to stringProp("城市名（中文或拼音），不填则默认北京"),
        required = emptyList()
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val city = arguments["city"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "北京"
                val encoded = URLEncoder.encode(city, "UTF-8")
                val conn = URL("https://wttr.in/$encoded?format=j1").openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent", "curl/8.0")
                if (conn.responseCode != 200) {
                    conn.disconnect()
                    return@runCatching "ERROR:天气服务返回 ${conn.responseCode}"
                }
                val json = try {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
                val root = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
                val cur = root["current_condition"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: return@runCatching "ERROR:天气数据解析失败"
                val temp = cur["temp_C"]?.jsonPrimitive?.content ?: "?"
                val feels = cur["FeelsLikeC"]?.jsonPrimitive?.content ?: "?"
                val humidity = cur["humidity"]?.jsonPrimitive?.content ?: "?"
                val desc = cur["weatherDesc"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("value")?.jsonPrimitive?.content ?: "?"
                val wind = cur["windspeedKmph"]?.jsonPrimitive?.content ?: "?"
                val pressure = cur["pressure"]?.jsonPrimitive?.content ?: "?"
                val sb = StringBuilder()
                sb.appendLine("城市：$city")
                sb.appendLine("当前：$desc，${temp}°C（体感 ${feels}°C）")
                sb.appendLine("湿度：$humidity% | 风速：${wind}km/h | 气压：${pressure}hPa")
                val forecast = root["weather"]?.jsonArray
                if (forecast != null) {
                    val days = listOf("今天", "明天", "后天")
                    forecast.take(3).forEachIndexed { i, day ->
                        val d = day.jsonObject
                        val date = d["date"]?.jsonPrimitive?.content ?: ""
                        val max = d["maxtempC"]?.jsonPrimitive?.content ?: "?"
                        val min = d["mintempC"]?.jsonPrimitive?.content ?: "?"
                        val dayDesc = d["hourly"]?.jsonArray
                            ?.take(6)?.mapNotNull { h ->
                                h.jsonObject["weatherDesc"]?.jsonArray?.firstOrNull()?.jsonObject
                                    ?.get("value")?.jsonPrimitive?.content
                            }?.distinct()?.joinToString("/") ?: "?"
                        sb.appendLine("${days.getOrElse(i) { date }}：${min}~${max}°C，$dayDesc")
                    }
                }
                sb.toString().trim()
            }.getOrElse { e -> "ERROR:查询失败：${e.message}" }
        }
}
