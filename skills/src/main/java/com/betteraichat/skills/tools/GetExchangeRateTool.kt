package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.numberProp
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

class GetExchangeRateTool : DeviceTool {

    override val name = "get_exchange_rate"
    override val description = "查询实时汇率（免费数据源 open.er-api.com）。from 为源货币代码（如 CNY），to 为目标货币代码（如 USD），amount 为金额（可选，默认 1），返回换算结果。"
    override val readOnly = true
    override val parameters = schemaOf(
        "from" to stringProp("源货币代码，如 CNY / USD / EUR / JPY / HKD"),
        "to" to stringProp("目标货币代码"),
        "amount" to numberProp("金额，默认 1"),
        required = listOf("from", "to")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val from = arguments["from"]?.jsonPrimitive?.content?.uppercase() ?: return@runCatching "from 参数无效"
                val to = arguments["to"]?.jsonPrimitive?.content?.uppercase() ?: return@runCatching "to 参数无效"
                val amount = arguments["amount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0
                val conn = URL("https://open.er-api.com/v6/latest/$from").openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                try {
                    if (conn.responseCode != 200) {
                        return@runCatching "ERROR:汇率服务返回 ${conn.responseCode}"
                    }
                    val json = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = Json.parseToJsonElement(json).jsonObject
                    val rates = root["rates"]?.jsonObject ?: return@runCatching "ERROR:汇率数据解析失败"
                    val rate = rates[to]?.jsonPrimitive?.content?.toDoubleOrNull()
                        ?: return@runCatching "ERROR:不支持的货币代码：$to"
                    val result = amount * rate
                    val fmt = { v: Double ->
                        if (v == v.toLong().toDouble() && kotlin.math.abs(v) < 1e12) v.toLong().toString()
                        else "%.4f".format(v).trimEnd('0').trimEnd('.')
                    }
                    val timeText = root["time_last_update_utc"]?.jsonPrimitive?.content ?: ""
                    buildString {
                        appendLine("1 $from = ${fmt(rate)} $to")
                        appendLine("$amount $from = ${fmt(result)} $to")
                        if (timeText.isNotBlank()) append("更新时间：$timeText")
                    }
                } finally {
                    conn.disconnect()
                }
            }.getOrElse { e -> "ERROR:汇率查询失败：${e.message}" }
        }
}
