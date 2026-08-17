package com.betteraichat.skills.tools

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SendNotificationTool : DeviceTool {

    private val notificationIdCounter = java.util.concurrent.atomic.AtomicInteger(0)

    override val name = "send_notification"
    override val description = "在设备上发送一条系统通知提醒用户。适合提醒、闹钟类场景。"
    override val readOnly = false
    override val parameters = schemaOf(
        "title" to stringProp("通知标题"),
        "content" to stringProp("通知正文内容"),
        required = listOf("title", "content")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val title = arguments["title"]?.jsonPrimitive?.content ?: return "缺少 title 参数"
        val content = arguments["content"]?.jsonPrimitive?.content ?: return "缺少 content 参数"
        val nm = context.appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.areNotificationsEnabled()) {
            return "通知权限未开启，无法发送通知。请到应用设置页打开通知权限后重试。"
        }
        val channelId = "betteraichat_ai"
        nm.createNotificationChannel(
            NotificationChannel(channelId, "AI 通知", NotificationManager.IMPORTANCE_HIGH)
        )
        val notification = NotificationCompat.Builder(context.appContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .build()
        val id = notificationIdCounter.incrementAndGet()
        nm.notify(id, notification)
        return "通知已发送（标题：$title，内容：$content）"
    }
}
