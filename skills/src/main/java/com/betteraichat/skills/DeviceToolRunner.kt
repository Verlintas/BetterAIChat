package com.betteraichat.skills

import com.betteraichat.core.engine.ToolRunner
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

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
        return try {
            tool.execute(context, args)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            if (e.message?.contains("JsonPrimitive") == true) {
                "工具参数类型错误：请按工具说明提供参数——字符串值需加引号（如 \"query\":\"...\"），数字用整数，不要编造参数名。"
            } else {
                "工具执行异常：${e.message ?: e.javaClass.simpleName}"
            }
        } catch (e: Exception) {
            "工具执行异常：${e.message ?: e.javaClass.simpleName}"
        }
    }
}
