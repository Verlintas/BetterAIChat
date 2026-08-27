package com.betteraichat.skills.tools

import android.content.Context
import android.content.pm.PackageManager
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class ListInstalledAppsTool : DeviceTool {

    override val name = "list_installed_apps"
    override val description = "列出手机上安装的应用（包名、名称、是否系统应用）。支持按关键词过滤（如 filter=微信）和 limit 限制条数。用于帮用户找应用、判断功能可用性。"
    override val readOnly = true
    override val parameters = schemaOf(
        "filter" to com.betteraichat.skills.stringProp("按名称或包名关键词过滤，可选"),
        "limit" to intProp("返回条数，默认 30，最多 100"),
        required = emptyList()
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val filter = arguments["filter"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
                val limit = (arguments["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 30).coerceIn(1, 100)
                val pm = context.appContext.packageManager
                val apps = pm.getInstalledApplications(0)
                val result = buildList {
                    apps.forEach { app ->
                        val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrNull() ?: app.packageName
                        if (filter != null && filter !in label && filter.lowercase() !in app.packageName.lowercase()) return@forEach
                        val isSystem = app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
                        add("$label（${app.packageName}）${if (isSystem) "· 系统应用" else ""}")
                    }
                }.sorted()
                if (result.isEmpty()) {
                    if (filter != null) "未找到包含「$filter」的应用" else "设备上没有检测到应用"
                } else {
                    buildString {
                        appendLine("共 ${result.size} 个应用${if (filter != null) "（关键词：$filter）" else ""}：")
                        result.take(limit).forEachIndexed { i, line ->
                            appendLine("${i + 1}. $line")
                        }
                        if (result.size > limit) append("…（共 ${result.size} 个，仅显示前 $limit 个）")
                    }
                }
            }.getOrElse { e -> "ERROR:获取应用列表失败：${e.message}" }
        }
}
