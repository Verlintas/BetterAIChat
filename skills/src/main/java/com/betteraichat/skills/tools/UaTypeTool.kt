package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class UaTypeTool : DeviceTool {

    override val name = "ua_type"
    override val description = "通过无障碍服务向当前聚焦的输入框写入文本（可写中文）。需要先在系统设置中开启「BetterAIChat 无障碍控制」。"
    override val readOnly = false
    override val parameters = schemaOf(
        "text" to stringProp("要输入的文本"),
        required = listOf("text")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val bridge = context.accessibility ?: return "ERROR:无障碍服务未开启，请到系统设置 → 无障碍 → 开启 BetterAIChat"
        val text = arguments["text"]?.jsonPrimitive?.content ?: return "text 参数无效"
        return bridge.typeText(text)
    }
}
