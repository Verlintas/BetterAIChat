package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class ReadNotificationsTool(
    private val reader: (limit: Int) -> String
) : DeviceTool {

    override val name = "read_notifications"
    override val description = "读取手机最近收到的通知（应用、标题、内容、时间）。需要系统「通知使用权」授权（设置页可开启）。用于了解未读消息、验证操作结果等。"
    override val readOnly = true
    override val parameters = schemaOf(
        "limit" to intProp("返回条数，默认 10，最多 20"),
        required = emptyList()
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val limit = (arguments["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 10).coerceIn(1, 20)
        return reader(limit)
    }
}
