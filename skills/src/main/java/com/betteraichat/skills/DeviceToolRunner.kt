package com.betteraichat.skills

import com.betteraichat.core.engine.ToolRunner
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
            .getOrElse { return "工具参数解析失败：${it.message}" }
        return tool.execute(context, args)
    }
}
