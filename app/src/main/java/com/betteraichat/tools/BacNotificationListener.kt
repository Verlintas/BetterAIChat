package com.betteraichat.tools

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationCache {
    private val maxSize = 30
    private val items = ArrayDeque<CachedNotification>()
    private val lock = Any()

    fun push(sbn: StatusBarNotification, pm: PackageManager) {
        val notification = sbn.notification
        val title = notification.extras.getString(Notification.EXTRA_TITLE).orEmpty()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val label = appLabel(pm, sbn.packageName)
        synchronized(lock) {
            items.addFirst(
                CachedNotification(
                    packageName = sbn.packageName,
                    appName = label,
                    title = title,
                    text = text,
                    time = sbn.postTime
                )
            )
            while (items.size > maxSize) items.removeLast()
        }
    }

    private val labelCache = HashMap<String, String>()
    private fun appLabel(pm: PackageManager, pkg: String): String = labelCache.getOrPut(pkg) {
        runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull() ?: pkg
    }

    fun snapshot(limit: Int): String {
        val snapshot: List<CachedNotification>
        synchronized(lock) {
            snapshot = items.toList()
        }
        if (snapshot.isEmpty()) return "暂无通知记录（需先在系统设置中开启 BetterAIChat 的「通知使用权」）"
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        snapshot.take(limit).forEach { n ->
            sb.appendLine("【${n.appName}】${fmt.format(Date(n.time))}")
            if (n.title.isNotBlank()) sb.appendLine("  ${n.title}")
            if (n.text.isNotBlank()) sb.appendLine("  ${n.text}")
        }
        return sb.toString().trim()
    }
}

data class CachedNotification(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val time: Long
)

class BacNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationCache.push(sbn, packageManager)
    }
}
