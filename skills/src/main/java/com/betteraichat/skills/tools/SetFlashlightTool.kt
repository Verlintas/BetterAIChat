package com.betteraichat.skills.tools

import android.content.Context
import android.hardware.camera2.CameraManager
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.boolProp
import com.betteraichat.skills.schemaOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SetFlashlightTool : DeviceTool {

    override val name = "set_flashlight"
    override val description = "打开或关闭手机闪光灯（手电筒）。需要相机权限。"
    override val readOnly = false
    override val parameters = schemaOf(
        "on" to boolProp("true 打开，false 关闭"),
        required = listOf("on")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val on = arguments["on"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
            ?: return "on 参数必须是 true 或 false"
        val cm = context.appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "设备没有可用的闪光灯"
        return try {
            cm.setTorchMode(cameraId, on)
            if (on) "闪光灯已打开" else "闪光灯已关闭"
        } catch (e: SecurityException) {
            "没有相机权限，无法控制闪光灯。请到设置页授予相机权限。"
        } catch (e: Exception) {
            "操作闪光灯失败：${e.message}"
        }
    }
}
