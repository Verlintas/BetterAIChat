package com.betteraichat.skills.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

class GetLocationTool : DeviceTool {

    override val name = "get_location"
    override val description = "获取当前定位（经纬度、精度、提供方），需要定位权限。可让 AI 回答「我在哪」「附近有什么」等问题（配合 get_weather 查询当地天气）。"
    override val readOnly = true
    override val parameters = com.betteraichat.skills.schemaOf()

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val appContext = context.appContext
        val fineGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            return "ERROR:缺少定位权限，请到系统设置授予定位权限"
        }
        return withContext(Dispatchers.IO) {
            try {
                val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                    !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                ) {
                    return@withContext "ERROR:定位服务未开启（请在系统设置打开定位）"
                }
                val deferred = CompletableDeferred<android.location.Location>()
                val provider = if (fineGranted) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: android.location.Location) {
                        if (!deferred.isCompleted) deferred.complete(location)
                    }
                    @Deprecated("Deprecated in API 29")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                try {
                    runCatching { lm.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper()) }
                        .getOrElse { return@withContext "ERROR:无法获取定位：${it.message}" }
                    val loc = withTimeoutOrNull(15_000) { deferred.await() }
                        ?: return@withContext "ERROR:定位超时（请到室外或检查定位设置）"
                    buildString {
                        appendLine("纬度: %.6f".format(loc.latitude))
                        appendLine("经度: %.6f".format(loc.longitude))
                        appendLine("精度: ±${loc.accuracy}m")
                        appendLine("提供方: ${if (loc.provider == LocationManager.GPS_PROVIDER) "GPS" else "网络定位"}")
                        if (loc.time > 0) append("时间: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(loc.time))}")
                    }
                } finally {
                    runCatching { lm.removeUpdates(listener) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "ERROR:定位失败：${e.message}"
            }
        }
    }
}
