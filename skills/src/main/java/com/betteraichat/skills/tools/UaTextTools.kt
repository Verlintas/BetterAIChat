package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class UaTapTextTool : DeviceTool {

    override val name = "ua_tap_text"
    override val description = "在屏幕上查找包含指定文字的控件并点击（基于无障碍节点，比坐标点击更可靠）。点击按钮、菜单、开关等首选此工具。找不到时先用 ua_find_text 查看屏幕上的文字与位置。需要无障碍服务。"
    override val readOnly = false
    override val parameters = schemaOf(
        "text" to stringProp("要点击的元素上的文字（支持部分匹配，如「登录」「确定」）"),
        required = listOf("text")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val bridge = context.accessibility ?: return "ERROR:无障碍服务不可用"
        if (!bridge.connected()) return "ERROR:无障碍服务未连接，请到 设置 → 权限 开启「无障碍控制」"
        val text = arguments["text"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (text.isBlank()) return "text 参数不能为空"
        return bridge.tapByText(text)
    }
}

class UaFindTextTool : DeviceTool {

    override val name = "ua_find_text"
    override val description = "列出屏幕上带文字的元素及其坐标与可点击状态（基于无障碍节点）。用于了解当前界面有哪些可操作项；配合 ua_tap_text 点击或 ua_tap 按坐标点击。text 留空则列出全部可点击文字。需要无障碍服务。"
    override val readOnly = true
    override val parameters = schemaOf(
        "text" to stringProp("要查找的文字（可选；留空列出屏幕上全部可点击文字）")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val bridge = context.accessibility ?: return "ERROR:无障碍服务不可用"
        if (!bridge.connected()) return "ERROR:无障碍服务未连接，请到 设置 → 权限 开启「无障碍控制」"
        val text = arguments["text"]?.jsonPrimitive?.content?.trim().orEmpty()
        return bridge.findTextPositions(text)
    }
}
