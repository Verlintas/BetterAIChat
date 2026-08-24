package com.betteraichat.tools

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.betteraichat.skills.ScreenshotProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream

object ScreenshotBridge {
    private val lock = Any()
    private var pendingCapture: CompletableDeferred<String>? = null

    fun registerCapture(): CompletableDeferred<String> = synchronized(lock) {
        pendingCapture?.complete("ERROR:截屏请求被新的请求覆盖")
        CompletableDeferred<String>().also { pendingCapture = it }
    }

    fun completeCapture(result: String) {
        synchronized(lock) {
            pendingCapture?.complete(result)
            pendingCapture = null
        }
    }
}

class ScreenshotManager(private val context: Context) : ScreenshotProvider {

    private var resultCode: Int = 0
    private var resultData: Intent? = null

    fun setProjectionResult(code: Int, data: Intent) {
        resultCode = code
        resultData = data
    }

    fun clearProjection() {
        resultData = null
    }

    fun hasProjection(): Boolean = resultData != null

    fun isServiceRunning(): Boolean =
        ScreenshotProjectionService.projection != null

    fun stopProjectionService() {
        context.startService(
            Intent(context, ScreenshotProjectionService::class.java)
                .setAction(ScreenshotProjectionService.ACTION_STOP)
        )
    }

    override suspend fun capture(): String {
        val data = resultData ?: return "ERROR:尚未授权截屏，请到应用设置页点击「截屏授权」"
        if (ScreenshotProjectionService.isBroken()) {
            clearProjection()
            return "ERROR:截屏授权已失效，请到设置页重新授权"
        }
        val deferred = ScreenshotBridge.registerCapture()
        try {
            val intent = Intent(context, ScreenshotProjectionService::class.java)
                .putExtra("resultCode", resultCode)
                .putExtra("data", data)
            context.startForegroundService(intent)
        } catch (e: Exception) {
            ScreenshotBridge.completeCapture("ERROR:无法启动截屏服务（后台启动限制），请回到应用后重试")
            return deferred.await()
        }
        return withTimeoutOrNull(60_000) { deferred.await() }
            ?: "ERROR:截屏超时，请重新授权后重试"
    }
}

class ScreenshotProjectionService : Service() {

    companion object {
        const val ACTION_STOP = "com.betteraichat.action.STOP_PROJECTION"
        @Volatile
        var projection: MediaProjection? = null
            private set
        @Volatile
        private var projectionBroken = false

        fun isBroken(): Boolean = projectionBroken
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("betteraichat_screenshot", "截屏服务", NotificationManager.IMPORTANCE_LOW)
        )
        val notification: Notification = Notification.Builder(this, "betteraichat_screenshot")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("BetterAIChat")
            .setContentText("AI 截屏服务运行中（用于屏幕分析）")
            .setOngoing(true)
            .build()
        startForeground(101, notification)

        when (intent?.action) {
            ACTION_STOP -> {
                stopProjectionInternal()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (projection == null || projectionBroken) {
            stopProjectionInternal()
            val code = intent?.getIntExtra("resultCode", 0) ?: 0
            val data = if (Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra("data", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra("data")
            }
            if (data == null) {
                ScreenshotBridge.completeCapture("ERROR:截屏授权数据丢失，请重新授权")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            val created = createProjection(code, data)
            if (!created) {
                ScreenshotBridge.completeCapture("ERROR:截屏授权已失效，请到设置页重新授权")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        scope.launch {
            val result = captureOnce()
            ScreenshotBridge.completeCapture(result)
        }
        return START_NOT_STICKY
    }

    private fun createProjection(resultCode: Int, data: Intent): Boolean {
        return try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = mpm.getMediaProjection(resultCode, data) ?: return false
            p.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        projectionBroken = true
                        projection = null
                    }
                },
                Handler(Looper.getMainLooper())
            )
            projection = p
            projectionBroken = false
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun stopProjectionInternal() {
        projection?.stop()
        projection = null
        projectionBroken = false
    }

    private suspend fun captureOnce(): String {
        val p = projection
        if (p == null) return "ERROR:截屏服务未就绪，请重新授权"
        return withContext(Dispatchers.IO) {
            runCatching {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val metrics = DisplayMetrics()
                wm.defaultDisplay.getRealMetrics(metrics)
                val width = metrics.widthPixels
                val height = metrics.heightPixels
                val density = metrics.densityDpi
                val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                var virtualDisplay: VirtualDisplay? = null
                var bitmap: Bitmap? = null
                var crop: Bitmap? = null
                try {
                    virtualDisplay = p.createVirtualDisplay(
                        "BetterAIChatShot",
                        width, height, density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        reader.surface, null, null
                    )
                    if (virtualDisplay == null) {
                        return@runCatching "ERROR:无法创建投影（截屏授权可能已失效，请重新授权）"
                    }
                    var image = reader.acquireLatestImage()
                    var waited = 0
                    while (image == null && waited < 5000) {
                        delay(100)
                        waited += 100
                        image = reader.acquireLatestImage()
                    }
                    if (image == null) return@runCatching "ERROR:获取屏幕画面超时"
                    image.use {
                        val plane = it.planes[0]
                        val buffer = plane.buffer
                        val pixelStride = plane.pixelStride
                        val rowStride = plane.rowStride
                        val rowPadding = rowStride - pixelStride * width
                        bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                        )
                        bitmap!!.copyPixelsFromBuffer(buffer)
                        crop = Bitmap.createBitmap(bitmap!!, 0, 0, width, height)
                        val dir = File(cacheDir, "screenshots").apply { mkdirs() }
                        val file = File(dir, "shot_${System.currentTimeMillis()}.png")
                        FileOutputStream(file).use { out ->
                            crop!!.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        "截屏成功：${file.absolutePath}（${width}x${height}）"
                    }
                } finally {
                    bitmap?.recycle()
                    crop?.recycle()
                    virtualDisplay?.release()
                    reader.close()
                }
            }.getOrElse { e -> "ERROR:截屏失败：${e.message}" }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
