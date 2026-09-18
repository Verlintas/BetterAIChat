package com.betteraichat.skills.tools

import android.content.Context
import android.media.session.MediaSessionManager
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class MediaControlTool : DeviceTool {

    override val name = "media_control"
    override val description = "控制正在播放的媒体（音乐/视频）：action 为 play 播放、pause 暂停、next 下一曲、previous 上一曲。无需特殊权限。"
    override val readOnly = false
    override val parameters = schemaOf(
        "action" to stringProp("操作：play / pause / next / previous"),
        required = listOf("action")
    )

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val action = arguments["action"]?.jsonPrimitive?.content ?: return "action 参数无效"
        val manager = context.appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = try {
            manager.getActiveSessions(null)
        } catch (e: Exception) {
            return "ERROR:无法读取媒体会话（Android 11+ 需在系统设置开启「通知使用权」才能控制其他应用的播放）"
        }
        val controller = sessions.takeIf { it.isNotEmpty() }?.firstOrNull()
        val controls = controller?.transportControls
        if (controls == null) {
            val flat = android.provider.Settings.Secure.getString(
                context.appContext.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""
            val hasAccess = android.os.Build.VERSION.SDK_INT < 31 || flat.split(':').any {
                android.content.ComponentName.unflattenFromString(it)?.packageName ==
                    context.appContext.packageName
            }
            return if (hasAccess) {
                "当前没有正在播放的媒体会话，无法控制"
            } else {
                "ERROR:未发现媒体会话。Android 11+ 需在系统设置开启 BetterAIChat 的「通知使用权」才能控制其他应用的播放"
            }
        }
        when (action) {
            "play" -> controls.play()
            "pause" -> controls.pause()
            "next" -> controls.skipToNext()
            "previous" -> controls.skipToPrevious()
            "status" -> {
                val meta = controller.metadata
                val state = controller.playbackState?.state
                val stateText = when (state) {
                    android.media.session.PlaybackState.STATE_PLAYING -> "正在播放"
                    android.media.session.PlaybackState.STATE_PAUSED -> "已暂停"
                    android.media.session.PlaybackState.STATE_STOPPED -> "已停止"
                    android.media.session.PlaybackState.STATE_BUFFERING -> "缓冲中"
                    else -> "空闲"
                }
                val title = meta?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                val artist = meta?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                val album = meta?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM)
                val duration = meta?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                val position = controller.playbackState?.position ?: 0L
                if (title.isNullOrBlank() && artist.isNullOrBlank()) {
                    return "当前媒体会话：$stateText（未提供曲目信息）"
                }
                return buildString {
                    append("当前：$stateText")
                    if (!title.isNullOrBlank()) append("｜曲目：$title")
                    if (!artist.isNullOrBlank()) append("｜艺术家：$artist")
                    if (!album.isNullOrBlank()) append("｜专辑：$album")
                    if (duration > 0) {
                        append("｜进度：${formatMs(position)} / ${formatMs(duration)}")
                    }
                }
            }
            else -> return "action 无效，可选：play / pause / next / previous / status"
        }
        return "已发送媒体控制：$action"
    }
}
