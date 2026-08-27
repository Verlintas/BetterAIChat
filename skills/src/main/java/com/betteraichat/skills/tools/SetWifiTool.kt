package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SetWifiTool(private val isShizukuGranted: () -> Boolean) : DeviceTool {

    override val name = "set_wifi"
    override val description = "开关 WiFi（需要 Shizuku）：on 开启、off 关闭。"
    override val readOnly = false
    override val parameters = schemaOf(
        "state" to stringProp("on 开启 WiFi / off 关闭 WiFi"),
        required = listOf("state")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val state = arguments["state"]?.jsonPrimitive?.content ?: return "state 参数无效"
        val result = when (state) {
            "on" -> ShizukuExec.run("cmd wifi set-wifi-enabled enabled", 10, isShizukuGranted)
            "off" -> ShizukuExec.run("cmd wifi set-wifi-enabled disabled", 10, isShizukuGranted)
            else -> return "state 无效，可选：on / off"
        }
        return if (result.startsWith("OK:")) {
            "WiFi 已${if (state == "on") "开启" else "关闭"}"
        } else {
            result
        }
    }
}
