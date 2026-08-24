package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class ManageAppTool(private val isShizukuGranted: () -> Boolean) : DeviceTool {

    override val name = "manage_app"
    override val description = "管理应用（需要 Shizuku）：force_stop 强制停止、disable 停用、enable 重新启用、clear_data 清除数据、uninstall 卸载。package 为应用包名。"
    override val readOnly = false
    override val parameters = schemaOf(
        "action" to stringProp("操作：force_stop / disable / enable / clear_data / uninstall"),
        "package" to stringProp("应用包名，如 com.android.settings"),
        required = listOf("action", "package")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val action = arguments["action"]?.jsonPrimitive?.content ?: return "action 参数无效"
        val pkg = arguments["package"]?.jsonPrimitive?.content ?: return "package 参数无效"
        if (!pkg.matches(Regex("[a-zA-Z0-9._]+"))) return "ERROR:package 参数无效（只允许字母、数字、点、下划线）"
        val cmd = when (action) {
            "force_stop" -> "am force-stop $pkg"
            "disable" -> "pm disable-user --user 0 $pkg"
            "enable" -> "pm enable --user 0 $pkg"
            "clear_data" -> "pm clear $pkg"
            "uninstall" -> "pm uninstall --user 0 $pkg"
            else -> return "action 无效，可选：force_stop / disable / enable / clear_data / uninstall"
        }
        val label = when (action) {
            "force_stop" -> "已强制停止"
            "disable" -> "已停用"
            "enable" -> "已重新启用"
            "clear_data" -> "已清除数据"
            "uninstall" -> "已卸载"
            else -> ""
        }
        val result = ShizukuExec.run(cmd, 30, isShizukuGranted)
        if (result.startsWith("退出码: 0")) return "$label：$pkg"
        return "操作失败：$result"
    }
}
