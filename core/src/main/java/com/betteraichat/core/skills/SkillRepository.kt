package com.betteraichat.core.skills

import android.content.Context
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.Locale

data class SkillToolDef(
    val name: String,
    val description: String,
    val parameters: JsonObject = JsonObject(emptyMap()),
    val actionType: String,
    val config: Map<String, String> = emptyMap()
)

data class Skill(
    val name: String,
    val description: String,
    val content: String,
    val allowedTools: List<String> = emptyList(),
    val tools: List<SkillToolDef> = emptyList(),
    val fileName: String = ""
)

class SkillRepository(private val context: Context) {

    private val dir = File(context.filesDir, "skills")
    private val yaml = Yaml()

    fun loadAll(): List<Skill> {
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension.lowercase(Locale.US) == "md" }
            ?.mapNotNull { f -> parse(f.name, f.readText()) }
            ?.sortedBy { it.name } ?: emptyList()
    }

    fun loadByName(name: String): Skill? = loadAll().firstOrNull { it.name == name }

    fun import(fileName: String, content: String): Result<Skill> {
        return runCatching {
            val skill = parse(fileName, content)
                ?: throw IllegalArgumentException("无法解析 Skill 文件：缺少 frontmatter（name / description）")
            if (skill.name.isBlank() || skill.name.contains(" ") || skill.name.any { !it.isLetterOrDigit() && it != '_' && it != '-' }) {
                throw IllegalArgumentException("Skill 名称只能包含字母、数字、下划线或连字符")
            }
            if (skill.description.isBlank()) {
                throw IllegalArgumentException("Skill 缺少 description")
            }
            val toolNames = skill.tools.map { it.name }
            if (toolNames.distinct().size != toolNames.size) {
                throw IllegalArgumentException("Skill 中定义了重复的工具名")
            }
            if (toolNames.any { it in BUILTIN_TOOL_NAMES }) {
                throw IllegalArgumentException("Skill 工具名与内置工具冲突：${
                    toolNames.filter { it in BUILTIN_TOOL_NAMES }.joinToString()
                }")
            }
            if (loadAll().any { it.name == skill.name }) {
                throw IllegalArgumentException("已存在同名 Skill：${skill.name}")
            }
            if (!dir.exists()) dir.mkdirs()
            val safeName = skill.name + ".md"
            File(dir, safeName).writeText(content.trim() + "\n")
            skill.copy(fileName = safeName)
        }
    }

    fun delete(name: String): Boolean {
        val skill = loadByName(name) ?: return false
        return File(dir, skill.fileName).delete()
    }

    private fun parse(fileName: String, content: String): Skill? {
        val lines = content.trim().lines()
        if (lines.firstOrNull()?.trim() != "---") return null
        val endIndex = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (endIndex < 0) return null
        val frontmatter = lines.drop(1).take(endIndex).joinToString("\n")
        val body = lines.drop(endIndex + 2).joinToString("\n").trim()

        val map = runCatching {
            yaml.load<Any?>(frontmatter) as? Map<*, *>
        }.getOrNull() ?: return null

        val name = map["name"]?.toString()?.trim()
        if (name.isNullOrBlank()) return null
        val description = map["description"]?.toString()?.trim() ?: ""
        val allowedTools = (map["allowed-tools"] as? List<*>)?.mapNotNull { it?.toString()?.trim() } ?: emptyList()
        val tools = parseTools(map["tools"])

        return Skill(
            name = name,
            description = description,
            content = body,
            allowedTools = allowedTools,
            tools = tools,
            fileName = fileName
        )
    }

    private fun parseTools(raw: Any?): List<SkillToolDef> {
        if (raw !is List<*>) return emptyList()
        return raw.mapNotNull { item ->
            if (item !is Map<*, *>) return@mapNotNull null
            val name = item["name"]?.toString()?.trim() ?: return@mapNotNull null
            val description = item["description"]?.toString()?.trim() ?: ""
            val parameters = (item["parameters"] as? Map<*, *>)?.let { it.toJsonElement() as? JsonObject }
                ?: JsonObject(emptyMap())
            val action = item["action"] as? Map<*, *>
            val actionType = action?.get("type")?.toString()?.trim() ?: "notification"
            val config = (action?.get("config") as? Map<*, *>)
                ?.mapKeys { it.key.toString() }
                ?.mapValues { it.value?.toString() ?: "" } ?: emptyMap()
            SkillToolDef(name, description, parameters, actionType, config)
        }
    }

    private fun Map<*, *>.toJsonElement(): JsonElement = buildJsonFrom(this)

    private fun buildJsonFrom(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to buildJsonFrom(it.value) })
        is List<*> -> JsonArray(value.map { buildJsonFrom(it) })
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value.toDouble())
        else -> JsonPrimitive(value.toString())
    }

    companion object {
        val BUILTIN_TOOL_NAMES = setOf(
            "open_app", "send_notification", "set_brightness", "set_volume",
            "device_info", "take_screenshot", "web_search", "web_read", "load_skill",
            "set_clipboard", "get_clipboard", "set_alarm", "set_flashlight",
            "open_settings", "set_screen_timeout", "run_shell", "speak_text"
        )
    }
}
