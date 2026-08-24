package com.betteraichat.skills.tools

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import kotlinx.serialization.json.JsonObject

class GetScreenStateTool : DeviceTool {

    override val name = "get_screen_state"
    override val description = "查询屏幕状态：屏幕是否亮着、是否锁屏。只读工具。"
    override val readOnly = true
    override val parameters = com.betteraichat.skills.schemaOf()

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val appContext = context.appContext
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val screenOn = pm.isInteractive
        val km = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val locked = km.isKeyguardLocked
        return buildString {
            appendLine("屏幕状态: ${if (screenOn) "亮屏" else "熄屏"}")
            append(if (screenOn) "锁屏状态: ${if (locked) "已锁屏" else "未锁屏"}" else "")
        }
    }
}
