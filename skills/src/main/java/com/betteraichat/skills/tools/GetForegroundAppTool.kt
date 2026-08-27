package com.betteraichat.skills.tools

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import kotlinx.serialization.json.JsonObject

class GetForegroundAppTool : DeviceTool {

    override val name = "get_foreground_app"
    override val description = "查询当前正在使用的应用（前台应用包名）。需要系统「使用情况访问权限」，在设置页可一键开启。只读工具。"
    override val readOnly = true
    override val parameters = com.betteraichat.skills.schemaOf()

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val appContext = context.appContext
        val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 120_000
        val events = try {
            usm.queryEvents(start, end)
        } catch (e: Exception) {
            return "ERROR:无法读取使用情况。请在系统设置中授予 BetterAIChat「使用情况访问权限」（可在应用设置页一键跳转）"
        }
        var top: String? = null
        var lastTs = 0L
        while (events.hasNextEvent()) {
            val e = UsageEvents.Event()
            events.getNextEvent(e)
            val foregroundType = when {
                e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND -> true
                e.eventType == UsageEvents.Event.ACTIVITY_RESUMED -> true
                else -> false
            }
            if (foregroundType && e.timeStamp >= lastTs) {
                lastTs = e.timeStamp
                top = e.packageName
            }
        }
        if (top == null) {
            return "ERROR:无法读取使用情况。请在系统设置中授予 BetterAIChat「使用情况访问权限」（可在应用设置页一键跳转）"
        }
        val label = runCatching {
            val pm = appContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(top, 0)).toString()
        }.getOrNull() ?: top
        return "当前前台应用：$label（$top）"
    }
}
