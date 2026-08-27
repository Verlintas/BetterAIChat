package com.betteraichat.skills.tools

import android.content.Context
import android.content.Intent
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SendEmailTool : DeviceTool {

    override val name = "send_email"
    override val description = "拉起系统邮件客户端写信（不会直接发送，由用户在邮件应用中确认发送）。subject 为主题，body 为正文，to 为收件人（可选，多个用逗号分隔）。"
    override val readOnly = false
    override val parameters = schemaOf(
        "subject" to stringProp("邮件主题"),
        "body" to stringProp("邮件正文"),
        "to" to stringProp("收件人邮箱，多个用逗号分隔，可选"),
        required = listOf("subject", "body")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val subject = arguments["subject"]?.jsonPrimitive?.content ?: return "subject 参数无效"
        val body = arguments["body"]?.jsonPrimitive?.content ?: return "body 参数无效"
        val to = arguments["to"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val uri = android.net.Uri.Builder()
            .scheme("mailto")
            .apply { to?.let { appendQueryParameter("to", it) } }
            .build()
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject.take(200))
            putExtra(Intent.EXTRA_TEXT, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.appContext.startActivity(intent)
            "已打开邮件客户端（主题：${subject.take(40)}），请在邮件应用中确认发送"
        } catch (e: Exception) {
            "ERROR:设备上没有可用的邮件应用"
        }
    }
}
