package com.betteraichat.skills.tools

import com.betteraichat.skills.AutomationBridge
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class CreateAutomationTool(private val bridge: AutomationBridge) : DeviceTool {

    override val name = "create_automation"
    override val description = "创建自动化：当条件满足时自动执行一系列工具操作，无需人工确认。trigger_type 为 time（定时，trigger_value 如 22:00）或 battery（电量，trigger_value 如 low:20 表示电量低于 20%，high:80 表示高于 80%）；days 为每周执行日（1=周一…7=周日，逗号分隔，空或 all 表示每天）；actions 为要执行的操作数组（工具名+参数），最多 10 步。执行完成会发送通知。示例：{\"trigger_type\":\"time\",\"trigger_value\":\"22:00\",\"actions\":[{\"tool\":\"set_volume\",\"args\":{\"percent\":0}},{\"tool\":\"set_dnd\",\"args\":{\"state\":\"on\"}}]}"
    override val readOnly = false
    override val parameters = schemaOf(
        "name" to stringProp("自动化名称，如「睡前模式」"),
        "trigger_type" to stringProp("触发类型：time 定时 / battery 电量"),
        "trigger_value" to stringProp("触发值：time 用 HH:mm 如 22:00；battery 用 low:20 或 high:80"),
        "days" to stringProp("每周执行日 1-7（1=周一），逗号分隔；all 或留空表示每天"),
        "actions" to stringProp("操作数组 JSON：[{\"tool\":\"set_volume\",\"args\":{\"percent\":0}},…]，最多 10 步"),
        required = listOf("name", "trigger_type", "trigger_value", "actions")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val name = arguments["name"]?.jsonPrimitive?.content ?: return "name 参数无效"
        val triggerType = arguments["trigger_type"]?.jsonPrimitive?.content ?: return "trigger_type 参数无效"
        val triggerValue = arguments["trigger_value"]?.jsonPrimitive?.content ?: return "trigger_value 参数无效"
        val actions = when (val raw = arguments["actions"]) {
            null -> return "actions 参数无效"
            is kotlinx.serialization.json.JsonPrimitive -> raw.content
            else -> raw.toString()
        }
        if (triggerType !in listOf("time", "battery")) return "trigger_type 无效，可选：time / battery"
        if (triggerType == "time") {
            val parts = triggerValue.split(":")
            if (parts.size != 2 || parts[0].toIntOrNull() !in 0..23 || parts[1].toIntOrNull() !in 0..59) {
                return "trigger_value 无效，time 类型需为 HH:mm，如 22:00"
            }
        } else {
            val parts = triggerValue.split(":")
            if (parts.size != 2 || parts[0] !in listOf("low", "high") || parts[1].toIntOrNull() !in 1..99) {
                return "trigger_value 无效，battery 类型需为 low:20 或 high:80"
            }
        }
        val days = arguments["days"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "all"
        if (days != "all") {
            val parsed = days.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (parsed.isEmpty() || parsed.any { it !in 1..7 } || parsed.toSet().size != parsed.size) {
                return "days 无效，应为 1-7 的逗号分隔（如 1,3,5）或 all"
            }
        }
        val stepCount = runCatching {
            Json.parseToJsonElement(actions).jsonArray.size
        }.getOrDefault(0)
        if (stepCount == 0) return "actions 不能为空"
        if (stepCount > 10) return "actions 最多 10 步"
        return bridge.create(name, triggerType, triggerValue, days, actions)
    }
}
