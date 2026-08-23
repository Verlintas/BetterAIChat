package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GetTimeTool : DeviceTool {

    override val name = "get_time"
    override val description = "查询当前日期和时间：年/月/日/星期/时/分/秒，以及时区。只读工具。"
    override val readOnly = true
    override val parameters = com.betteraichat.skills.schemaOf()

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val now = Date()
        val tz = TimeZone.getDefault()
        val dateFmt = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("日期: ${dateFmt.format(now)}")
            appendLine("时间: ${timeFmt.format(now)}")
            appendLine("时区: ${tz.id}（UTC${tz.getOffset(now.time) / 3600000} 小时）")
            append("Unix 时间戳: ${now.time / 1000}")
        }
    }
}
