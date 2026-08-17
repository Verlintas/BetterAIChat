package com.betteraichat.skills

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.betteraichat.core.skills.SkillToolDef
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SkillActionExecutor(private val context: Context) {

    fun execute(def: SkillToolDef, args: JsonObject): String {
        return try {
            when (def.actionType) {
                "notification" -> doNotification(def, args)
                "clipboard" -> doClipboard(def, args)
                "alarm" -> doAlarm(def, args)
                "intent" -> doIntent(def, args)
                "settings" -> doSettings(def, args)
                else -> "未知动作类型：${def.actionType}"
            }
        } catch (e: Exception) {
            "动作执行失败：${e.message}"
        }
    }

    private fun render(template: String, args: JsonObject): String {
        var out = template
        Regex("\\{([a-zA-Z0-9_]+)\\}").findAll(template).forEach { m ->
            val key = m.groupValues[1]
            val value = args[key]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() } ?: ""
            out = out.replace(m.value, value)
        }
        return out
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("betteraichat_ai", "AI 通知", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun notify(title: String, content: String, id: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()) {
        ensureChannel()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, "betteraichat_ai")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .build()
        nm.notify(id, notification)
    }

    private fun doNotification(def: SkillToolDef, args: JsonObject): String {
        val title = render(def.config["title"] ?: "AI 通知", args)
        val content = render(def.config["content"] ?: def.description, args)
        notify(title, content)
        return "通知已发送（$title：$content）"
    }

    private fun doClipboard(def: SkillToolDef, args: JsonObject): String {
        val template = def.config["text"] ?: "{text}"
        val text = render(template, args)
        if (text.isBlank()) return "剪贴板内容为空"
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("betteraichat", text))
        return "已复制到剪贴板：$text"
    }

    private fun doAlarm(def: SkillToolDef, args: JsonObject): String {
        val minutes = args["minutes"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val seconds = args["seconds"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val delayMs = ((minutes * 60 + seconds) * 1000).toLong()
        if (delayMs <= 0) return "alarm 动作需要有效的 minutes 或 seconds 参数"
        val title = render(def.config["title"] ?: "AI 提醒", args)
        val content = render(def.config["content"] ?: "设定的时间到了", args)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            Intent(context, AlarmReceiver::class.java)
                .putExtra("title", title)
                .putExtra("content", content),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pi)
        val minutesText = if (minutes > 0) "%.0f".format(minutes) else "%.0f".format(seconds / 60.0)
        return "已设定 $minutesText 分钟后的提醒（$title：$content）"
    }

    private fun doIntent(def: SkillToolDef, args: JsonObject): String {
        val packageName = render(def.config["package"] ?: "", args)
        val action = render(def.config["action"] ?: "", args)
        val data = render(def.config["data"] ?: "", args)
        val intent = Intent()
        if (action.isNotBlank()) intent.action = action
        if (packageName.isNotBlank()) intent.setPackage(packageName)
        if (data.isNotBlank()) intent.data = android.net.Uri.parse(data)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pm = context.packageManager
        if (intent.resolveActivity(pm) == null) {
            return "没有可处理该 intent 的应用（action=$action, package=$packageName）"
        }
        context.startActivity(intent)
        return "已通过 Intent 启动：action=$action, package=$packageName"
    }

    private fun doSettings(def: SkillToolDef, args: JsonObject): String {
        val key = def.config["key"] ?: "brightness"
        val valueTemplate = def.config["value"] ?: "{value}"
        val value = render(valueTemplate, args)
        return when (key) {
            "brightness" -> setBrightness(value)
            "volume" -> setVolume(value)
            "screen_timeout" -> setScreenTimeout(value)
            else -> "不支持的设置项：$key"
        }
    }

    private fun setBrightness(value: String): String {
        if (!Settings.System.canWrite(context)) {
            return "没有「修改系统设置」权限，请到设置页授权后重试"
        }
        val p = value.toDoubleOrNull() ?: return "亮度值无效"
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            (p.coerceIn(0.0, 100.0) * 255 / 100).toInt()
        )
        return "亮度已设置为 ${p.coerceIn(0.0, 100.0)}%"
    }

    private fun setVolume(value: String): String {
        val p = value.toDoubleOrNull() ?: return "音量值无效"
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (max * p.coerceIn(0.0, 100.0) / 100).toInt(), 0)
        return "音量已设置为 ${p.coerceIn(0.0, 100.0)}%"
    }

    private fun setScreenTimeout(value: String): String {
        if (!Settings.System.canWrite(context)) {
            return "没有「修改系统设置」权限，请到设置页授权后重试"
        }
        val seconds = value.toLongOrNull() ?: return "超时秒数无效"
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, (seconds * 1000).toInt())
        return "屏幕超时已设置为 $seconds 秒"
    }
}

class SkillDefinedTool(
    val def: SkillToolDef,
    private val executor: SkillActionExecutor
) : DeviceTool {

    val skillName: String get() = def.name

    override val name = def.name
    override val description = def.description
    override val readOnly = false
    override val parameters = def.parameters

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String =
        executor.execute(def, arguments)
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "AI 提醒"
        val content = intent.getStringExtra("content") ?: "设定的时间到了"
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, "betteraichat_ai")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("betteraichat_ai", "AI 通知", NotificationManager.IMPORTANCE_HIGH)
        )
    }
}
