package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SetPowerSaverTool(private val isShizukuGranted: () -> Boolean) : DeviceTool {

    override val name = "set_power_saver"
    override val description = "切换系统省电模式（需要 Shizuku）：on 开启省电、off 关闭省电。"
    override val readOnly = false
    override val parameters = schemaOf(
        "state" to stringProp("on 开启省电模式 / off 关闭省电模式"),
        required = listOf("state")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val state = arguments["state"]?.jsonPrimitive?.content ?: return "state 参数无效"
        val result = when (state) {
            "on" -> ShizukuExec.run("cmd power set-mode 1", 10, isShizukuGranted)
            "off" -> ShizukuExec.run("cmd power set-mode 0", 10, isShizukuGranted)
            else -> return "state 无效，可选：on / off"
        }
        return if (result.startsWith("OK:")) {
            "省电模式已${if (state == "on") "开启" else "关闭"}"
        } else {
            result
        }
    }
}
