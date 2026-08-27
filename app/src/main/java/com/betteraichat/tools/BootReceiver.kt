package com.betteraichat.tools

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.betteraichat.core.db.RepeatTaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.LOCKED_BOOT_COMPLETED") return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val db = com.betteraichat.core.db.AppDatabase.get(context.applicationContext)
                val tasks = db.repeatTaskDao().observeAll().first()
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val now = System.currentTimeMillis()
                tasks.forEach { task ->
                    val next = computeNext(task, now) ?: return@forEach
                    val pi = PendingIntent.getBroadcast(
                        context,
                        task.requestCode,
                        Intent(context, com.betteraichat.skills.AlarmReceiver::class.java)
                            .setAction(com.betteraichat.skills.AlarmReceiver.ACTION_REPEAT)
                            .putExtra("request_code", task.requestCode)
                            .putExtra("title", task.title)
                            .putExtra("content", task.content),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
                    val intervalMs = intervalMillis(task.interval, task.everyHours)
                    if (exact) {
                        am.setRepeating(AlarmManager.RTC_WAKEUP, next, intervalMs, pi)
                    } else {
                        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next, intervalMs, pi)
                    }
                }
                runCatching {
                    val app = context.applicationContext as com.betteraichat.BetterAIChatApp
                    app.container.automationScheduler.scheduleAll()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun computeNext(task: RepeatTaskEntity, now: Long): Long? {
        val base = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return when (task.interval) {
            "daily" -> {
                val hm = task.time.split(":")
                if (hm.size != 2) return null
                base.set(Calendar.HOUR_OF_DAY, hm[0].toIntOrNull() ?: return null)
                base.set(Calendar.MINUTE, hm[1].toIntOrNull() ?: return null)
                if (base.timeInMillis <= now) base.add(Calendar.DAY_OF_YEAR, 1)
                base.timeInMillis
            }
            "weekly" -> {
                val hm = task.time.split(":")
                if (hm.size != 2) return null
                var target = task.weekday + 1
                if (target > 7) target = 1
                base.set(Calendar.HOUR_OF_DAY, hm[0].toIntOrNull() ?: return null)
                base.set(Calendar.MINUTE, hm[1].toIntOrNull() ?: return null)
                var diff = target - base.get(Calendar.DAY_OF_WEEK)
                if (diff < 0) diff += 7
                if (diff > 0) base.add(Calendar.DAY_OF_YEAR, diff)
                if (base.timeInMillis <= now) base.add(Calendar.DAY_OF_YEAR, 7)
                base.timeInMillis
            }
            "hourly" -> now + task.everyHours * 3600_000L
            else -> null
        }
    }

    private fun intervalMillis(interval: String, everyHours: Int): Long = when (interval) {
        "daily" -> 24 * 3600_000L
        "weekly" -> 7 * 24 * 3600_000L
        "hourly" -> everyHours * 3600_000L
        else -> 24 * 3600_000L
    }
}
