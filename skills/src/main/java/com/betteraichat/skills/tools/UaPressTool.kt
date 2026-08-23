package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class UaPressTool : DeviceTool {

    override val name = "ua_press"
    override val description = "通过无障碍服务执行系统按键：home 回到桌面、back 返回、recents 最近任务、notifications 通知栏、quick_settings 快捷设置、search 搜索。"
    override val readOnly = false
    override val parameters = schemaOf(
        "key" to stringProp("按键：home / back / recents / notifications / quick_settings / search"),
        required = listOf("key")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val bridge = context.accessibility ?: return "ERROR:无障碍服务未开启，请到系统设置 → 无障碍 → 开启 BetterAIChat"
        val key = arguments["key"]?.jsonPrimitive?.content ?: return "key 参数无效"
        return bridge.pressKey(key)
    }
}
