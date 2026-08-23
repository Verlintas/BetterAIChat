package com.betteraichat.tools

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.betteraichat.skills.AccessibilityBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class BacAccessibilityService : AccessibilityService(), AccessibilityBridge {

    companion object {
        @Volatile
        var instance: BacAccessibilityService? = null
            private set

        fun connected(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun connected(): Boolean = instance != null

    override fun windowTitle(): String? {
        val root = rootInActiveWindow ?: return null
        val title = root.findAccessibilityNodeInfosByViewId("android:id/content").firstOrNull()?.text?.toString()
        return title ?: root.text?.toString()
    }

    override suspend fun typeText(text: String): String = withContext(Dispatchers.Main) {
        val root = rootInActiveWindow ?: return@withContext "ERROR:没有可操作的活动窗口"
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val target = focused ?: root
        val bundle = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        if (ok) "已向当前输入框写入 ${text.length} 字符" else "ERROR:未找到可输入的输入框（请先聚焦输入框）"
    }

    override suspend fun pressKey(key: String): String {
        val action = when (key) {
            "home" -> GLOBAL_ACTION_HOME
            "back" -> GLOBAL_ACTION_BACK
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> return "ERROR:key 无效，可选：home / back / recents / notifications / quick_settings"
        }
        return if (performGlobalAction(action)) "已执行按键：$key" else "ERROR:按键执行失败"
    }

    override suspend fun tap(x: Int, y: Int): String {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return if (dispatchGestureInternal(gesture)) "已点击 (${x}, ${y})" else "ERROR:手势分发失败"
    }

    override suspend fun swipe(
        x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int
    ): String {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val duration = durationMs.coerceIn(50, 5000)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration.toLong()))
            .build()
        return if (dispatchGestureInternal(gesture)) {
            "已滑动 (${x1},${y1}) → (${x2},${y2})，耗时 ${duration}ms"
        } else "ERROR:手势分发失败"
    }

    private suspend fun dispatchGestureInternal(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        cont.resume(false)
                    }
                },
                null
            )
        }
}
