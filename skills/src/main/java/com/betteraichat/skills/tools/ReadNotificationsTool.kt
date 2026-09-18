package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class ReadNotificationsTool(
    private val reader: (limit: Int, hours: Int, app: String?) -> String
) : DeviceTool {

    override val name = "read_notifications"
    override val description = "读取手机最近收到的通知（应用、标题、内容、时间）。需要系统「通知使用权」授权（设置页可开启）。用于了解未读消息、验证操作结果等。"
    override val readOnly = true
    override val parameters = schemaOf(
        "limit" to intProp("返回条数，默认 10，最多 20"),
        "hours" to intProp("只看最近多少小时内的通知（可选，如 24）"),
        "app" to com.betteraichat.skills.stringProp("按应用名过滤（可选，如「微信」）"),
        required = emptyList()
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val limit = (arguments["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 10).coerceIn(1, 20)
        val hours = (arguments["hours"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0).coerceIn(0, 168)
        val app = arguments["app"]?.jsonPrimitive?.content?.trim()
        return reader(limit, hours, app)
    }
}
