package com.betteraichat.skills.tools

import android.content.Intent
import android.provider.CalendarContract
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale

class CreateCalendarEventTool : DeviceTool {

    override val name = "create_calendar_event"
    override val description = "创建日历日程：打开系统日历应用并预填标题/时间/地点，用户在日历中确认保存。start_time 格式「yyyy-MM-dd HH:mm」。适合「帮我安排明天下午三点的会议」这类请求。"
    override val readOnly = false
    override val parameters = schemaOf(
        "title" to stringProp("日程标题"),
        "start_time" to stringProp("开始时间，格式 yyyy-MM-dd HH:mm，例如 2026-09-20 15:00"),
        "duration_minutes" to intProp("时长（分钟），默认 60"),
        "location" to stringProp("地点（可选）"),
        "description" to stringProp("备注（可选）")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val title = arguments["title"]?.jsonPrimitive?.content?.trim().orEmpty()
        val startRaw = arguments["start_time"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (title.isBlank()) return "title 不能为空"
        if (startRaw.isBlank()) return "start_time 不能为空，格式 yyyy-MM-dd HH:mm"
        val duration = (arguments["duration_minutes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 60)
            .coerceIn(5, 24 * 60)
        val start = parseTime(startRaw)
            ?: return "start_time 格式不正确：请使用 yyyy-MM-dd HH:mm（如 2026-09-20 15:00）"
        val end = start + duration * 60_000L
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            arguments["location"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            arguments["description"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.appContext.startActivity(intent)
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            "已打开日历并预填日程「$title」（${fmt.format(java.util.Date(start))}，$duration 分钟），请在日历中确认保存。"
        } catch (e: Exception) {
            "无法打开日历应用：${e.message ?: "未安装日历应用"}"
        }
    }

    private fun parseTime(raw: String): Long? {
        val patterns = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm")
        patterns.forEach { p ->
            runCatching {
                return SimpleDateFormat(p, Locale.getDefault()).parse(raw)?.time
                    ?: throw IllegalStateException()
            }
        }
        return raw.toLongOrNull()
    }
}
