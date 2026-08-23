package com.betteraichat.skills.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class OpenDialerTool : DeviceTool {

    override val name = "open_dialer"
    override val description = "打开拨号界面并填入号码（不直接拨出，仅需用户确认）。"
    override val readOnly = false
    override val parameters = schemaOf(
        "number" to stringProp("电话号码"),
        required = listOf("number")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val number = arguments["number"]?.jsonPrimitive?.content ?: return "number 参数无效"
        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.appContext.startActivity(dial)
        return "已打开拨号界面，号码：$number"
    }
}
