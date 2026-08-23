package com.betteraichat.skills.tools

import android.content.Context
import android.content.Intent
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class ShareTextTool : DeviceTool {

    override val name = "share_text"
    override val description = "把一段文本（或链接）分享到其他应用（如微信、浏览器等），会弹出分享面板。"
    override val readOnly = false
    override val parameters = schemaOf(
        "text" to stringProp("要分享的文本内容"),
        required = listOf("text")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val text = arguments["text"]?.jsonPrimitive?.content ?: return "text 参数无效"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooser = Intent.createChooser(send, "分享到").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.appContext.startActivity(chooser)
        return "已打开分享面板（内容 ${text.length} 字）"
    }
}
