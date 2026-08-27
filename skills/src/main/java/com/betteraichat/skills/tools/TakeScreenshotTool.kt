package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import kotlinx.serialization.json.JsonObject

class TakeScreenshotTool : DeviceTool {

    override val name = "take_screenshot"
    override val description = "截取当前屏幕并保存为图片文件，返回文件保存路径。需要先在设置页完成「截屏授权」。截图内容无法直接阅读，只能获得文件路径。"
    override val readOnly = true
    override val parameters = com.betteraichat.skills.schemaOf()

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        return try {
            val path = context.screenshotProvider.capture()
            if (path.startsWith("ERROR:")) path else "截图已保存：$path"
        } catch (e: Exception) {
            "ERROR:截图失败：${e.message}"
        }
    }
}
