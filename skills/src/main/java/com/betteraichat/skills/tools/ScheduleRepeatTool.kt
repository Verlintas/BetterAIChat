package com.betteraichat.skills.tools

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.betteraichat.skills.AlarmReceiver
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Calendar

class ScheduleRepeatTool : DeviceTool {

    override val name = "schedule_repeat"
    override val description = "设置周期性提醒任务。interval 可选 daily（每天）/ weekly（每周）/ hourly（每隔几小时）。daily 需提供 time（HH:mm）；weekly 需提供 time 和 weekday（1-7 对应周一到周日）；hourly 可提供 every_hours（默认 1）。提醒到达时发系统通知，通知上可一键停止该提醒。"
    override val readOnly = false
    override val parameters = schemaOf(
        "interval" to stringProp("daily / weekly / hourly"),
        "time" to stringProp("HH:mm 格式，如 09:00（daily/weekly 必填）"),
        "weekday" to intProp("1-7 对应周一至周日（weekly 用）"),
        "every_hours" to intProp("每隔多少小时（hourly 用，默认 1）"),
        "content" to stringProp("提醒内容"),
        "title" to stringProp("提醒标题，默认「AI 提醒」"),
        required = listOf("interval", "content")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val interval = arguments["interval"]?.jsonPrimitive?.content?.lowercase()
            ?: return "缺少 interval 参数"
        val content = arguments["content"]?.jsonPrimitive?.content
            ?: return "缺少 content 参数"
        val title = arguments["title"]?.jsonPrimitive?.content ?: "AI 提醒"
        val am = context.appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        val triggerAt: Long
        val intervalMs: Long
        val label: String
        when (interval) {
            "daily" -> {
                val time = arguments["time"]?.jsonPrimitive?.content
                    ?: return "daily 需要 time 参数（HH:mm）"
                val hm = parseTime(time) ?: return "time 格式应为 HH:mm"
                triggerAt = nextDaily(now, hm.first, hm.second)
                intervalMs = 24 * 3600_000L
                label = "每天 $time"
            }
            "weekly" -> {
                val time = arguments["time"]?.jsonPrimitive?.content
                    ?: return "weekly 需要 time 参数（HH:mm）"
                val hm = parseTime(time) ?: return "time 格式应为 HH:mm"
                val weekday = arguments["weekday"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: return "weekly 需要 weekday 参数（1-7）"
                if (weekday !in 1..7) return "weekday 需在 1-7 之间"
                triggerAt = nextWeekly(now, weekday, hm.first, hm.second)
                intervalMs = 7 * 24 * 3600_000L
                label = "每周周$weekday $time"
            }
            "hourly" -> {
                val every = (arguments["every_hours"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1)
                    .coerceIn(1, 24)
                triggerAt = now + every * 3600_000L
                intervalMs = every * 3600_000L
                label = "每 $every 小时"
            }
            else -> return "interval 只能是 daily / weekly / hourly"
        }

        val requestCode = (now % Int.MAX_VALUE).toInt()
        val pi = PendingIntent.getBroadcast(
            context.appContext,
            requestCode,
            Intent(context.appContext, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_REPEAT)
                .putExtra("request_code", requestCode)
                .putExtra("title", title)
                .putExtra("content", content),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val exact = if (android.os.Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
        if (exact) {
            am.setRepeating(AlarmManager.RTC_WAKEUP, triggerAt, intervalMs, pi)
        } else {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerAt, intervalMs, pi)
        }
        val precision = if (exact) "" else "（非精确模式，可能延迟数分钟）"
        return "已创建定时提醒：$label$precision\n内容：$title：$content\n到达时会发通知，通知上可点击「停止」取消。"
    }

    private fun parseTime(s: String): Pair<Int, Int>? {
        val parts = s.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h to m
    }

    private fun nextDaily(now: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun nextWeekly(now: Long, weekday: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var target = weekday + 1
        if (target > 7) target = 1
        val current = cal.get(Calendar.DAY_OF_WEEK)
        var diff = target - current
        if (diff <= 0) diff += 7
        cal.add(Calendar.DAY_OF_YEAR, diff)
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 7)
        return cal.timeInMillis
    }
}
