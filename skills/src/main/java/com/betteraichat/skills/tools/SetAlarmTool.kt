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

class SetAlarmTool : DeviceTool {

    override val name = "set_alarm"
    override val description = "设定一个定时提醒：N 分钟后通过系统通知提醒用户。适合倒计时、提醒喝水、提醒开会等。"
    override val readOnly = false
    override val parameters = schemaOf(
        "minutes" to intProp("多少分钟后提醒（与 seconds 二选一，优先 minutes）"),
        "seconds" to intProp("多少秒后提醒（与 minutes 二选一）"),
        "title" to stringProp("提醒标题，默认「AI 提醒」"),
        "content" to stringProp("提醒内容")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val minutes = arguments["minutes"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val seconds = arguments["seconds"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val delayMs = ((minutes * 60 + seconds) * 1000).toLong()
        if (delayMs <= 0) return "需要提供有效的 minutes 或 seconds 参数"
        val title = arguments["title"]?.jsonPrimitive?.content ?: "AI 提醒"
        val content = arguments["content"]?.jsonPrimitive?.content ?: "设定的时间到了"
        val am = context.appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context.appContext,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            Intent(context.appContext, AlarmReceiver::class.java)
                .putExtra("title", title)
                .putExtra("content", content),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + delayMs
        val exact = if (android.os.Build.VERSION.SDK_INT >= 31) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
        val minutesText = if (minutes > 0) "%.0f".format(minutes) else "%.0f".format(seconds / 60.0)
        val precision = if (exact) "（精确）" else "（非精确，可能延迟几分钟，Android 12+ 请到系统设置允许精确闹钟）"
        return "已设定 $minutesText 分钟后的提醒$precision（$title：$content）"
    }
}
