package com.betteraichat.skills

import com.betteraichat.core.engine.ToolRunner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class DeviceToolRunner(
    private val tools: List<DeviceTool>,
    private val context: ToolContext
) : ToolRunner {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun run(name: String, arguments: String): String {
        val tool = tools.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("未知工具：$name")
        val args: JsonObject = runCatching { json.parseToJsonElement(arguments).jsonObject }
            .getOrDefault(JsonObject(emptyMap()))
        return tool.execute(context, args)
    }
}
