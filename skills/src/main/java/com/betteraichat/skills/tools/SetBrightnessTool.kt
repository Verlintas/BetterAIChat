package com.betteraichat.skills.tools

import android.content.Context
import android.provider.Settings
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.numberProp
import com.betteraichat.skills.schemaOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SetBrightnessTool : DeviceTool {

    override val name = "set_brightness"
    override val description = "调整屏幕亮度。提供 percent（0-100，百分比）或 value（0-255，原始值）之一。"
    override val readOnly = false
    override val parameters = schemaOf(
        "percent" to intProp("亮度百分比，0-100，与 value 二选一"),
        "value" to intProp("亮度原始值，0-255，与 percent 二选一")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val appContext = context.appContext
        if (!Settings.System.canWrite(appContext)) {
            return "没有「修改系统设置」权限，无法调节亮度。请到应用设置页点击「授权修改系统设置」。"
        }
        val target: Int = when {
            arguments["percent"] != null -> {
                val p = arguments["percent"]!!.jsonPrimitive.content.toDoubleOrNull()
                    ?: return "percent 参数无效"
                (p.coerceIn(0.0, 100.0) * 255 / 100).toInt()
            }
            arguments["value"] != null -> arguments["value"]!!.jsonPrimitive.content.toIntOrNull()
                ?.coerceIn(0, 255) ?: return "value 参数无效"
            else -> return "需要提供 percent 或 value 参数"
        }
        Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        val ok = Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, target)
        return if (ok) "屏幕亮度已调整为 ${(target * 100 / 255)}%" else "亮度调整失败"
    }
}
