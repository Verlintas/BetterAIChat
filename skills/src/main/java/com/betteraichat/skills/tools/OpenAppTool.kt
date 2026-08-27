package com.betteraichat.skills.tools

import android.content.Context
import android.content.Intent
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject

class OpenAppTool : DeviceTool {

    override val name = "open_app"
    override val description = "启动设备上已安装的应用。参数可以是包名（如 com.android.calculator2）或应用名称（如 计算器），支持模糊匹配。"
    override val readOnly = false
    override val parameters = schemaOf(
        "app" to stringProp("应用名称或包名，例如 计算器 或 com.android.calculator2"),
        required = listOf("app")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val query = arguments["app"]?.jsonPrimitive?.content?.trim() ?: return "缺少 app 参数"
        val pm = context.appContext.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(launchIntent, 0)
        val entries = resolved.map { info ->
            Triple(
                info.activityInfo.packageName,
                info.loadLabel(pm).toString(),
                info.activityInfo.name
            )
        }.distinctBy { it.first }

        val exact = entries.filter { it.first.equals(query, ignoreCase = true) }
        val byLabel = entries.filter { it.second.contains(query, ignoreCase = true) }
        val candidates = (exact + byLabel).distinctBy { it.first }

        if (candidates.isEmpty()) {
            val sample = entries.take(20).joinToString("\n") { "${it.second} (${it.first})" }
            return "未找到匹配「$query」的应用。可用应用示例：\n$sample"
        }
        val target = candidates.first()
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(target.first, target.third)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.appContext.startActivity(intent)
            val extra = if (candidates.size > 1) "（另找到 ${candidates.size - 1} 个相似应用，已启动第一个）" else ""
            "已启动应用：${target.second} (${target.first})$extra"
        } catch (e: android.content.ActivityNotFoundException) {
            "ERROR:无法启动应用 ${target.second}（可能已被卸载或停用）"
        } catch (e: Exception) {
            "ERROR:启动应用失败：${e.message}"
        }
    }
}
