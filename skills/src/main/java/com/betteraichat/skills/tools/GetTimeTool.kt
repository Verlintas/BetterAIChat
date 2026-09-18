package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GetTimeTool : DeviceTool {

    override val name = "get_time"
    override val description = "查询当前日期时间（含星期与时区），可用 days_offset 计算相对日期（如 3 天后、10 天前是几号星期几）。只读工具。"
    override val readOnly = true
    override val parameters = com.betteraichat.skills.schemaOf(
        "days_offset" to com.betteraichat.skills.intProp("相对今天偏移的天数（可选，如 3 = 3 天后，-7 = 7 天前）")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val offsetDays = (arguments["days_offset"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
            .coerceIn(-3650, 3650)
        val now = Date()
        val tz = TimeZone.getDefault()
        val dateFmt = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("日期: ${dateFmt.format(now)}")
            appendLine("时间: ${timeFmt.format(now)}")
            if (offsetDays != 0) {
                val target = Date(now.time + offsetDays * 86_400_000L)
                appendLine("${if (offsetDays > 0) "$offsetDays 天后" else "${-offsetDays} 天前"}: ${dateFmt.format(target)}")
            }
            val offsetMin = tz.getOffset(now.time) / 60000
            val offsetText = buildString {
                append("UTC")
                if (offsetMin < 0) append('-') else append('+')
                val abs = kotlin.math.abs(offsetMin)
                append(abs / 60)
                if (abs % 60 != 0) append(":${abs % 60}")
            }
            appendLine("时区: ${tz.id}（$offsetText）")
            append("Unix 时间戳: ${now.time / 1000}")
        }
    }
}
