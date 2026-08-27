package com.betteraichat.skills

import android.content.Context
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun interface ScreenshotProvider {
    suspend fun capture(): String
}

interface OcrProvider {
    suspend fun ocrScreenshot(): String
    suspend fun ocrImageFile(path: String): String = "ERROR:当前版本不支持图片 OCR"
}

interface AccessibilityBridge {
    fun connected(): Boolean
    fun windowTitle(): String?
    suspend fun typeText(text: String): String
    suspend fun pressKey(key: String): String
    suspend fun tap(x: Int, y: Int): String
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): String
}

interface AutomationBridge {
    suspend fun create(
        name: String,
        triggerType: String,
        triggerValue: String,
        days: String,
        actionsJson: String
    ): String

    suspend fun list(): String
    suspend fun delete(id: Long): String
}

data class ToolContext(
    val appContext: Context,
    val screenshotProvider: ScreenshotProvider,
    val ocrProvider: OcrProvider? = null,
    val accessibility: AccessibilityBridge? = null,
    val screenRecorder: ScreenRecorderBridge? = null
)

interface DeviceTool {
    val name: String
    val description: String
    val readOnly: Boolean
    val parameters: JsonObject

    suspend fun execute(context: ToolContext, arguments: JsonObject): String
}

fun schemaOf(
    vararg props: Pair<String, JsonObject>,
    required: List<String> = emptyList()
): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        props.forEach { (k, v) -> put(k, v) }
    })
    if (required.isNotEmpty()) {
        put("required", JsonArray(required.map { JsonPrimitive(it) }))
    }
}

fun stringProp(description: String): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
}

fun intProp(description: String): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

fun numberProp(description: String): JsonObject = buildJsonObject {
    put("type", "number")
    put("description", description)
}

fun boolProp(description: String): JsonObject = buildJsonObject {
    put("type", "boolean")
    put("description", description)
}
