package com.betteraichat.skills.tools

import android.content.ClipboardManager
import android.content.Context
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import kotlinx.serialization.json.JsonObject

class GetClipboardTool : DeviceTool {

    override val name = "get_clipboard"
    override val description = "读取系统剪贴板当前的文本内容。"
    override val readOnly = true
    override val parameters = com.betteraichat.skills.schemaOf()

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val cm = context.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return "剪贴板为空"
        val text = (0 until clip.itemCount).joinToString("\n") { i ->
            clip.getItemAt(i).coerceToText(context.appContext).toString()
        }
        return if (text.isBlank()) "剪贴板为空" else "剪贴板内容：\n$text"
    }
}
