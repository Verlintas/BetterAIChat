package com.betteraichat.skills.tools

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class GetAppUsageTool : DeviceTool {

    override val name = "get_app_usage"
    override val description = "查询最近一段时间内各应用的前台使用时长（需要「使用情况访问」权限）。用于回答「我今天用了多久手机」「哪个应用用得最多」等问题。"
    override val readOnly = true
    override val parameters = schemaOf(
        "hours" to intProp("统计最近多少小时，默认 24，最多 168（7 天）"),
        "limit" to intProp("返回前几个应用，默认 10，最多 20")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val appContext = context.appContext
        if (!hasUsageAccess(appContext)) {
            return "ERROR:缺少「使用情况访问」权限，请到 设置 → 权限 → 使用情况访问 中授权后重试"
        }
        val hours = (arguments["hours"]?.jsonPrimitive?.content?.toIntOrNull() ?: 24).coerceIn(1, 168)
        val limit = (arguments["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 10).coerceIn(1, 20)
        val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - hours * 3600_000L
        val stats = usm.queryAndAggregateUsageStats(start, end)
        val pm = appContext.packageManager
        val entries = stats.values
            .filter { it.totalTimeInForeground > 0 && it.packageName != appContext.packageName }
            .sortedByDescending { it.totalTimeInForeground }
            .take(limit)
        if (entries.isEmpty()) {
            return "最近 $hours 小时内没有可用的应用使用记录（可能刚授权，系统需要一段时间收集数据）"
        }
        val totalMinutes = stats.values.sumOf { it.totalTimeInForeground } / 60_000
        return buildString {
            appendLine("最近 $hours 小时的应用使用情况（前台时长）：")
            entries.forEachIndexed { i, s ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString()
                }.getOrDefault(s.packageName)
                val minutes = s.totalTimeInForeground / 60_000
                appendLine("${i + 1}. $label：${formatDuration(minutes)}")
            }
            append("合计约 ${formatDuration(totalMinutes)}（含系统界面）")
        }
    }

    private fun formatDuration(minutes: Long): String = when {
        minutes >= 60 -> "${minutes / 60} 小时 ${minutes % 60} 分钟"
        else -> "$minutes 分钟"
    }

    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
