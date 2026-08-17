package com.betteraichat.skills.tools

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class OpenSettingsTool : DeviceTool {

    override val name = "open_settings"
    override val description = "打开系统设置页面。page 可选值：wifi、bluetooth、sound、display、network、home。用于引导用户手动操作系统选项。"
    override val readOnly = false
    override val parameters = schemaOf(
        "page" to stringProp("wifi / bluetooth / sound / display / network / home"),
        required = listOf("page")
    )

    private val pages = mapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "sound" to Settings.ACTION_SOUND_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS,
        "network" to Settings.ACTION_WIRELESS_SETTINGS,
        "home" to "android.settings.SETTINGS"
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val page = arguments["page"]?.jsonPrimitive?.content?.trim()?.lowercase()
            ?: return "缺少 page 参数"
        val action = pages[page] ?: return "未知页面：$page（可选：${pages.keys.joinToString("/")}）"
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.appContext.startActivity(intent)
        return "已打开系统设置页面：$page"
    }
}
