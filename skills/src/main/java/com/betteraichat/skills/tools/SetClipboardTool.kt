package com.betteraichat.skills.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SetClipboardTool : DeviceTool {

    override val name = "set_clipboard"
    override val description = "将指定文本写入系统剪贴板。适合复制文本、验证码、地址等。"
    override val readOnly = false
    override val parameters = schemaOf(
        "text" to stringProp("要复制到剪贴板的文本"),
        required = listOf("text")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val text = arguments["text"]?.jsonPrimitive?.content ?: return "缺少 text 参数"
        val cm = context.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("betteraichat", text))
        return "已复制到剪贴板：${text.take(80)}${if (text.length > 80) "…" else ""}"
    }
}
