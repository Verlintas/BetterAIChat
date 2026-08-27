package com.betteraichat.tools

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.betteraichat.core.db.AppDatabase
import com.betteraichat.core.db.AutomationEntity
import com.betteraichat.core.engine.ToolRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Calendar

class AutomationScheduler(
    private val context: Context,
    private val db: AppDatabase,
    private val runnerProvider: () -> ToolRunner
) {

    companion object {
        const val ACTION_TRIGGER = "com.betteraichat.action.AUTOMATION_TRIGGER"
        const val EXTRA_ID = "automation_id"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val batteryThresholds = HashMap<Long, Pair<String, Int>>()
    private val running = java.util.concurrent.ConcurrentHashMap<Long, Boolean>()
    @Volatile
    private var receiverRegistered = false

    fun scheduleAll() {
        scope.launch {
            val automations = db.automationDao().getEnabled()
            automations.forEach { schedule(it) }
            registerBatteryReceiverIfNeeded()
        }
    }

    fun schedule(automation: AutomationEntity) {
        when (automation.triggerType) {
            "time" -> scheduleTime(automation)
            "battery" -> registerBattery(automation)
        }
        if (automation.triggerType == "battery") registerBatteryReceiverIfNeeded()
    }

    fun reschedule(automation: AutomationEntity) {
        cancelAlarm(automation.id)
        if (automation.enabled) schedule(automation)
    }

    fun cancel(automation: AutomationEntity) {
        cancelAlarm(automation.id)
        synchronized(batteryThresholds) {
            batteryThresholds.remove(automation.id)
        }
    }

    private fun registerBatteryReceiverIfNeeded() {
        if (receiverRegistered || batteryThresholds.isEmpty()) return
        receiverRegistered = true
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        runCatching {
            context.registerReceiver(batteryReceiver, filter)
        }.onFailure {
            receiverRegistered = false
        }
    }

    private fun scheduleTime(automation: AutomationEntity) {
        val parts = automation.triggerValue.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = alarmIntent(automation.id)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
        }
    }

    private fun alarmIntent(id: Long): PendingIntent {
        val intent = Intent(context, AutomationAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelAlarm(id: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmIntent(id))
    }

    private fun registerBattery(automation: AutomationEntity) {
        val value = automation.triggerValue.trim().lowercase()
        val parts = value.split(":")
        if (parts.size != 2) return
        val direction = when (parts[0]) {
            "low" -> "low"
            "high" -> "high"
            else -> return
        }
        val threshold = parts[1].toIntOrNull()?.coerceIn(1, 99) ?: return
        synchronized(batteryThresholds) {
            batteryThresholds[automation.id] = direction to threshold
        }
    }

    fun ensureBatteryReceiver() {
        if (batteryThresholds.isEmpty()) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            if (level < 0) return
            val snapshot: Map<Long, Pair<String, Int>>
            synchronized(batteryThresholds) {
                snapshot = batteryThresholds.toMap()
            }
            snapshot.forEach { (id, config) ->
                val (direction, threshold) = config
                val hit = if (direction == "low") level <= threshold else level >= threshold
                if (hit && running.putIfAbsent(id, true) == null) {
                    scope.launch {
                        try {
                            executeAutomation(id)
                        } finally {
                            running.remove(id)
                        }
                    }
                }
            }
        }
    }

    suspend fun executeAutomation(id: Long) {
        runCatching {
            val automation = db.automationDao().getEnabled().firstOrNull { it.id == id } ?: return
            if (!daysMatch(automation.days)) return
            db.automationDao().setLastRun(id, System.currentTimeMillis())
            runCatching {
                val actions = json.decodeFromString<List<ActionSpec>>(automation.actionsJson)
                val results = actions.mapNotNull { spec ->
                    val result = withTimeoutOrNull(60_000) {
                        runCatching { runnerProvider().run(spec.tool, spec.args.toString()) }
                            .getOrElse { e -> "执行失败：${e.message}" }
                    } ?: "执行超时（60s）"
                    "${spec.tool}: $result"
                }
                val summary = results.joinToString("\n")
                sendDoneNotification(automation.name, summary)
            }.onFailure { e ->
                sendDoneNotification(automation.name, "执行失败：${e.message}")
            }
            if (automation.triggerType == "time") {
                db.automationDao().getEnabled().firstOrNull { it.id == id }?.let { schedule(it) }
            }
        }
    }

    private fun daysMatch(days: String): Boolean {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val dayNum = (today + 5) % 7 + 1
        return days.isBlank() || days == "all" || dayNum.toString() in days.split(",")
    }

    private fun sendDoneNotification(name: String, summary: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("betteraichat_automation", "自动化执行", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val notification = Notification.Builder(context, "betteraichat_automation")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("自动化「$name」已执行")
            .setContentText(summary.take(120))
            .setStyle(Notification.BigTextStyle().bigText(summary))
            .setAutoCancel(true)
            .build()
        nm.notify(name.hashCode(), notification)
    }
}

@kotlinx.serialization.Serializable
data class ActionSpec(
    val tool: String,
    val args: JsonObject = JsonObject(emptyMap())
)

class AutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AutomationScheduler.EXTRA_ID, -1L)
        if (id < 0) return
        val app = context.applicationContext as com.betteraichat.BetterAIChatApp
        val pendingResult = goAsync()
        kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
            try {
                val days = runCatching {
                    app.container.db.automationDao().getEnabled().firstOrNull { it.id == id }?.days
                }.getOrNull()
                if (!days.isNullOrBlank() && days != "all") {
                    val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                    val dayNum = (today + 5) % 7 + 1
                    if (dayNum.toString() !in days.split(",")) return@launch
                }
                app.container.automationScheduler?.executeAutomation(id)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
