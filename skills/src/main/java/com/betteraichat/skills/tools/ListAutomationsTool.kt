package com.betteraichat.skills.tools

import com.betteraichat.skills.AutomationBridge
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import kotlinx.serialization.json.JsonObject

class ListAutomationsTool(private val bridge: AutomationBridge) : DeviceTool {

    override val name = "list_automations"
    override val description = "列出当前所有自动化（名称、触发条件、启用状态）。只读工具。"
    override val readOnly = true
    override val parameters = com.betteraichat.skills.schemaOf()

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String = bridge.list()
}
