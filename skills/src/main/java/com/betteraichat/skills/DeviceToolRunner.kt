package com.betteraichat.skills

import com.betteraichat.core.engine.ToolRunner
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class DeviceToolRunner(
    private val registry: ToolRegistry,
    private val context: ToolContext
) : ToolRunner {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun run(name: String, arguments: String): String {
        val tool = registry.findTool(name)
            ?: throw IllegalArgumentException("未知工具：$name")
        val args: JsonObject = runCatching { json.parseToJsonElement(arguments).jsonObject }
            .getOrElse { return "工具参数解析失败：$name 收到的参数不是有效 JSON（${it.message}）。请按工具说明重新构造参数。" }
        val (healed, fixes) = healArgs(tool.parameters, args)
        return try {
            val result = tool.execute(context, healed)
            if (fixes.isEmpty()) result
            else "（已自动修正参数：${fixes.joinToString("；")}）\n$result"
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            val fixNote = if (fixes.isEmpty()) "" else "（已尝试自动修正参数：${fixes.joinToString("；")}，仍失败）\n"
            if (e.message?.contains("JsonPrimitive") == true) {
                fixNote + "工具参数类型错误：请按工具说明提供参数——字符串值需加引号（如 \"query\":\"...\"），数字用整数，不要编造参数名。"
            } else {
                fixNote + "工具执行异常：${e.message ?: e.javaClass.simpleName}"
            }
        } catch (e: Exception) {
            "工具执行异常：${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun healArgs(schema: JsonObject, args: JsonObject): Pair<JsonObject, List<String>> {
        val props = schema["properties"]?.jsonObject ?: return args to emptyList()
        val fixes = mutableListOf<String>()
        val out = buildJsonObject {
            args.forEach { (rawKey, v) ->
                val key = when {
                    rawKey in props -> rawKey
                    else -> {
                        val match = props.keys
                            .filter { it.length >= 3 }
                            .minByOrNull { levenshtein(rawKey.lowercase(), it.lowercase()) }
                        if (match != null && levenshtein(rawKey.lowercase(), match.lowercase()) <= 2) {
                            fixes += "参数名 '$rawKey' 已纠正为 '$match'"
                            match
                        } else rawKey
                    }
                }
                val propType = props[key]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
                when {
                    propType == "string" && v is JsonPrimitive && !v.isString -> {
                        fixes += "参数 '$key' 已转为字符串"
                        put(key, JsonPrimitive(v.content))
                    }
                    propType == "string" && v !is JsonPrimitive -> {
                        fixes += "参数 '$key' 已从对象/数组转为字符串"
                        put(key, JsonPrimitive(v.toString()))
                    }
                    propType == "integer" && v is JsonPrimitive && v.isString -> {
                        v.content.toLongOrNull()?.let {
                            fixes += "参数 '$key' 已从字符串转为整数"
                            put(key, JsonPrimitive(it))
                            return@forEach
                        }
                        put(key, v)
                    }
                    else -> put(key, v)
                }
            }
        }
        return out to fixes
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
        }
        return dp[a.length][b.length]
    }
}
