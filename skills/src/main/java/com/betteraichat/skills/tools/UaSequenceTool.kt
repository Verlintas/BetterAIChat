package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.arrayProp
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class UaSequenceTool : DeviceTool {

    override val name = "ua_sequence"

    override val description = "一次调用按顺序执行多步 UI 操作（点击坐标 / 按文字点击 / 输入 / 系统按键 / 滑动 / 等待），减少来回调用次数。适合连续操作：打开应用后依次点击多个按钮、填表、翻页等。任一步失败立即停止，并返回已完成步骤；总时长上限 45 秒，最多 30 步。每步后可传 wait_ms 等待界面变化（默认 250ms）。Build 模式下整批只需确认一次。"

    override val readOnly = false

    override val parameters = schemaOf(
        "steps" to arrayProp(
            "有序步骤数组（1-30 步）。action 必填：tap / tap_text / type / press / swipe / wait。",
            stepSchema()
        ),
        required = listOf("steps")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val bridge = context.accessibility ?: return "ERROR:无障碍服务不可用"
        if (!bridge.connected()) return "ERROR:无障碍服务未连接，请到 设置 → 权限 开启「无障碍控制」"
        val steps = arguments["steps"] as? JsonArray ?: return "ERROR:steps 需为数组"
        if (steps.isEmpty()) return "ERROR:steps 不能为空"
        if (steps.size > MAX_STEPS) return "ERROR:steps 最多 $MAX_STEPS 步"

        val started = System.currentTimeMillis()
        val done = mutableListOf<String>()
        steps.forEachIndexed { index, element ->
            val step = element as? JsonObject
                ?: return fail(index, "steps", "步骤不是对象", done)
            if (System.currentTimeMillis() - started > MAX_TOTAL_MS) {
                return "ERROR:序列超过 ${MAX_TOTAL_MS / 1000} 秒上限，已执行 ${done.size} 步。\n${done.joinToString("\n")}"
            }
            val action = step["action"]?.jsonPrimitive?.content?.trim()?.lowercase()
                ?: return fail(index, "steps", "缺少 action", done)
            val result = when (action) {
                "tap" -> bridge.tap(
                    step.int("x") ?: return fail(index, action, "x 参数无效", done),
                    step.int("y") ?: return fail(index, action, "y 参数无效", done)
                )
                "tap_text" -> bridge.tapByText(
                    step.str("text")?.takeIf { it.isNotBlank() }
                        ?: return fail(index, action, "text 参数不能为空", done)
                )
                "type" -> bridge.typeText(
                    step.str("text")?.takeIf { it.isNotBlank() }
                        ?: return fail(index, action, "text 参数不能为空", done)
                )
                "press" -> bridge.pressKey(
                    step.str("key")?.takeIf { it.isNotBlank() }
                        ?: return fail(index, action, "key 参数不能为空", done)
                )
                "swipe" -> bridge.swipe(
                    step.int("x1") ?: return fail(index, action, "x1 参数无效", done),
                    step.int("y1") ?: return fail(index, action, "y1 参数无效", done),
                    step.int("x2") ?: return fail(index, action, "x2 参数无效", done),
                    step.int("y2") ?: return fail(index, action, "y2 参数无效", done),
                    step.int("duration_ms") ?: 300
                )
                "wait" -> {
                    val ms = (step.int("ms") ?: 500).coerceIn(1, MAX_STEP_WAIT_MS)
                    delay(ms.toLong())
                    "已等待 ${ms}ms"
                }
                else -> return fail(
                    index, action,
                    "未知 action，可选 tap / tap_text / type / press / swipe / wait", done
                )
            }
            if (result.startsWith("ERROR")) return fail(index, action, result.removePrefix("ERROR:"), done)
            done += "${index + 1}. $action: ${result.take(STEP_SUMMARY_CHARS)}"
            val waitMs = (step.int("wait_ms") ?: DEFAULT_STEP_WAIT_MS).coerceIn(0, MAX_STEP_WAIT_MS)
            if (waitMs > 0) delay(waitMs.toLong())
        }
        return "序列完成（${steps.size} 步）：\n" + done.joinToString("\n").take(OUTPUT_CHARS)
    }

    private fun fail(index: Int, action: String, reason: String, done: List<String>): String = buildString {
        append("ERROR:序列在第 ${index + 1} 步失败（$action）：$reason")
        if (done.isNotEmpty()) {
            append("\n已完成：\n")
            append(done.joinToString("\n").take(OUTPUT_CHARS))
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

    private fun stepSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("action", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    listOf("tap", "tap_text", "type", "press", "swipe", "wait").forEach { add(JsonPrimitive(it)) }
                })
                put("description", "操作类型")
            })
            put("x", intProp("tap 的横坐标"))
            put("y", intProp("tap 的纵坐标"))
            put("text", stringProp("tap_text 要查找的文字，或 type 要输入的文本"))
            put("key", stringProp("press 的按键：home / back / recents / notifications / quick_settings"))
            put("x1", intProp("swipe 起点横坐标"))
            put("y1", intProp("swipe 起点纵坐标"))
            put("x2", intProp("swipe 终点横坐标"))
            put("y2", intProp("swipe 终点纵坐标"))
            put("duration_ms", intProp("swipe 耗时毫秒，默认 300"))
            put("ms", intProp("wait 的等待毫秒，默认 500"))
            put("wait_ms", intProp("该步执行后等待界面变化的毫秒，默认 250；0 表示不等待"))
        })
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    private companion object {
        const val MAX_STEPS = 30
        const val MAX_TOTAL_MS = 45_000L
        const val MAX_STEP_WAIT_MS = 5_000
        const val DEFAULT_STEP_WAIT_MS = 250
        const val STEP_SUMMARY_CHARS = 120
        const val OUTPUT_CHARS = 4_000
    }
}
