package com.betteraichat.skills.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import kotlinx.serialization.json.JsonObject

class NetworkStatusTool : DeviceTool {

    override val name = "network_status"
    override val description = "查询网络状态：是否联网、网络类型（WiFi/移动数据）、WiFi 名称、网速信息等。只读工具。"
    override val readOnly = true
    override val parameters = com.betteraichat.skills.schemaOf()

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val appContext = context.appContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        val connected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return buildString {
            appendLine("联网状态: ${if (connected) "已联网" else "未联网"}")
            when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                    appendLine("网络类型: WiFi")
                    val wifi = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val ssid = wifi.connectionInfo?.ssid ?: "未知"
                    appendLine("WiFi 名称: ${ssid.removeSurrounding("\"")}")
                }
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                    appendLine("网络类型: 移动数据")
                }
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> {
                    appendLine("网络类型: 以太网")
                }
                else -> appendLine("网络类型: 其他")
            }
            if (caps != null) {
                appendLine(
                    "连接能力: " + listOfNotNull(
                        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "已验证" else null,
                        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "非计费" else "计费网络"
                    ).joinToString(" / ")
                )
            }
        }
    }
}
