package com.betteraichat.skills.tools

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import kotlinx.serialization.json.JsonObject

class DeviceInfoTool : DeviceTool {

    override val name = "device_info"
    override val description = "查询设备信息：型号、系统版本、电池电量、存储空间等。只读工具。"
    override val readOnly = true
    override val parameters = com.betteraichat.skills.schemaOf()

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val appContext = context.appContext
        val battery = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPercent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val storage = StatFs(Environment.getDataDirectory().path)
        val total = storage.totalBytes / 1024 / 1024 / 1024
        val free = storage.availableBytes / 1024 / 1024 / 1024
        return buildString {
            appendLine("设备型号: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android 版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("电池电量: $batteryPercent%")
            appendLine("存储空间: 可用 ${free}GB / 共 ${total}GB")
            append("应用名称: BetterAIChat")
        }
    }
}
