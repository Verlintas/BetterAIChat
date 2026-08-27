package com.betteraichat.tools

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.betteraichat.skills.ScreenRecorderBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ScreenRecorder(
    private val context: Context,
    private val screenshotManager: ScreenshotManager
) : ScreenRecorderBridge {

    override suspend fun record(seconds: Int): String = withContext(Dispatchers.IO) {
        if (!screenshotManager.hasProjection()) {
            return@withContext "ERROR:尚未授权截屏/录屏，请到设置页完成「截屏授权」"
        }
        if (ScreenshotProjectionService.isBroken()) {
            screenshotManager.clearProjection()
            return@withContext "ERROR:录屏授权已失效，请到设置页重新授权"
        }
        val projection = ScreenshotProjectionService.projection
            ?: return@withContext "ERROR:录屏服务未就绪，请重新授权"
        val recorder = MediaRecorder()
        var virtualDisplay: VirtualDisplay? = null
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val metrics = android.util.DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            val fileName = "screen_${System.currentTimeMillis()}.mp4"
            val saveUri = createDownloadUri(fileName)
                ?: return@withContext "ERROR:无法创建视频文件"

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setVideoSize(width, height)
            recorder.setVideoFrameRate(30)
            recorder.setVideoEncodingBitRate(4_000_000)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(
                context.contentResolver.openFileDescriptor(saveUri, "w")?.fileDescriptor
                    ?: return@withContext "ERROR:无法打开视频输出文件"
            )
            recorder.prepare()

            virtualDisplay = projection.createVirtualDisplay(
                "BetterAIChatRecord",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface, null, null
            )
            if (virtualDisplay == null) {
                return@withContext "ERROR:无法创建录屏投影"
            }
            recorder.start()
            delay(seconds * 1000L)
            recorder.stop()
            return@withContext "录屏完成：下载/$fileName（${seconds} 秒，${width}x${height}）"
        } catch (e: Exception) {
            "ERROR:录屏失败：${e.message}"
        } finally {
            runCatching { virtualDisplay?.release() }
            runCatching { recorder.release() }
        }
    }

    private fun createDownloadUri(fileName: String): android.net.Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        return runCatching {
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull()
    }
}
