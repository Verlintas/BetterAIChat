package com.betteraichat.skills.tools

import com.betteraichat.skills.AutomationBridge
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class DeleteAutomationTool(private val bridge: AutomationBridge) : DeviceTool {

    override val name = "delete_automation"
    override val description = "删除指定 id 的自动化（id 可通过 list_automations 查看）。"
    override val readOnly = false
    override val parameters = schemaOf(
        "id" to intProp("自动化 id"),
        required = listOf("id")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val id = arguments["id"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: return "id 参数无效"
        return bridge.delete(id)
    }
}
